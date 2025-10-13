package volleyzone.source

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import core.DataSource
import core.TimeSource
import core.model.Match
import core.model.Match.Result
import core.model.Match.TeamResult
import core.model.Team
import core.model.TeamId
import core.model.Time
import core.model.TriangleId
import core.model.Venue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class VolleyZoneSource(
  private val config: VolleyZoneSourceConfig,
  private val timeSource: TimeSource,
  private val httpClient: HttpClient,
  logger: Logger = Logger,
) : DataSource {
  private val log = logger.withTag("VolleyZoneSource")

  override fun listMatches(from: Time, until: Time): Flow<Match> = flow {
    for ((name, url) in config.leagues.entries) {
      log.d("[$url] Fetching events for $name.")
      parseLeague(url).collect { match ->
        if (match.start >= from && match.end < until) {
          emit(match)
        } else {
          log.d(
            "[$url][${match.identity}] Discarding match since its time " +
              "${match.start.atSource}..<${match.end.atSource} does not fit into requested " +
              "time frame ${from.atSource}..<${until.atSource}."
          )
        }
      }
    }
  }

  private fun parseLeague(url: Url): Flow<Match> = flow {
    val document = httpClient.get(url).bodyAsText().let(Ksoup::parse)
    val fixturesTable = document.getElementById("fixtures_league")
    if (fixturesTable == null) {
      log.e("[$url] Unable to find fixtures.")
    } else {
      log.v("[$url] Parsing fixtures.")
      fixturesTable
        .getElementsByClass("table-body")
        .asSequence()
        .mapNotNull { parseMatch(url, it) }
        .forEach {
          log.d("[$url] Parsed fixture $it")
          emit(it)
        }
    }

    val resultsTable = document.getElementById("results_league")
    if (resultsTable == null) {
      log.e("[$url] Unable to find results.")
    } else {
      log.v("[$url] Parsing results.")
      resultsTable
        .getElementsByTag("ul")
        .toList()
        .filter { !it.hasClass("table-header") }
        .windowed(size = 2, step = 2)
        .mapNotNull { (row, scores) -> parseFinishedMatch(url, row, scores) }
        .forEach {
          log.d("[$url] Parsed result $it")
          emit(it)
        }
    }
  }

  /** Extract all available data from [row] and parse it into [Match]. */
  private fun parseMatch(source: Url, row: Element): Match? {
    val homeTeam = row.attr("data-hometeam").trim()
    val awayTeam = row.attr("data-awayteam").trim()
    val date = row.attr("data-date").trim()
    val time = row.attr("data-time").trim()
    val venue = row.attr("data-venue").trim()
    val venueExtra =
      row
        .getElementsByTag("li")
        .getOrNull(5)
        ?.getElementsByClass("data")
        ?.firstOrNull()
        ?.text()
        ?.takeIf { it.startsWith(venue, ignoreCase = true) }
    val comment = row.attr("data-comment")
    val id = comment.trim().let(MatchIdRegex::find)?.value
    if (id == null) {
      log.e(
        "Cannot find event ID: date=$date, time=$time," +
          " homeTeam=$homeTeam, awayTeam=$awayTeam," +
          " venue=$venue, venueExtra=$venueExtra," +
          " comment=$comment"
      )
      log.d("row=$row")
      return null
    }
    val order =
      when {
        id.endsWith('a') -> 1
        id.endsWith('b') -> 2
        id.endsWith('c') -> 3
        else -> {
          log.e(
            "[$id] Cannot determine match order: date=$date, time=$time," +
              " homeTeam=$homeTeam, awayTeam=$awayTeam," +
              " venue=$venue, venueExtra=$venueExtra," +
              " comment=$comment, row=$row"
          )
          return null
        }
      }
    val start = parseTime(date = date, time = time)
    val teamA = Team(id = TeamId(homeTeam), name = homeTeam)
    val teamB = Team(id = TeamId(awayTeam), name = awayTeam)
    return Match(
        source = source.toString(),
        triangle = TriangleId(id.dropLast(1)),
        id = id,
        order = order.toUInt(),
        title = "$homeTeam vs $awayTeam",
        venue = Venue(address = venue, alternativeAddress = venueExtra),
        start = timeSource.fromSource(start),
        end = timeSource.fromSource(start + TriangleDuration),
        teamA = teamA,
        teamB = teamB,
        result = null,
        lastUpdated = timeSource.fromSource(Clock.System.now()),
      )
      .resolveAddress()
  }

  private fun parseFinishedMatch(source: Url, row: Element, scores: Element): Match? {
    if (!row.hasClass("table-body") || !scores.hasClass("list-centered_bottom_league")) {
      return null
    }
    val match = parseMatch(source, row) ?: return null

    log.v("[${match.identity}] Parsing scores.")
    val homeSets = row.attr("data-homescore").trim().takeIf(String::isNotBlank)?.toUInt() ?: 0u
    val awaySets = row.attr("data-awayscore").trim().takeIf(String::isNotBlank)?.toUInt() ?: 0u
    var setScores =
      scores.getElementsByTag("li").mapNotNull { li ->
        li
          .getElementsByTag("span")
          .mapNotNull { it.text().trim().toUIntOrNull() }
          .takeIf { it.size >= 2 }
      }
    val wonSets = homeSets + awaySets
    var playedSets = setScores.size.toUInt()
    log.d(
      "[${match.identity}] Parsed scores: homeSets=$homeSets, awaySets=$awaySets, setScores=$setScores"
    )

    if (setScores.size > wonSets.toInt() && setScores.none { (a, b) -> a == b }) {
      val setsAsScores = listOf(homeSets, awaySets)
      val fixedScores = setScores.filter { it != setsAsScores }
      setScores =
        if (fixedScores.size == wonSets.toInt()) {
          log.i("[${match.identity}] Fixed invalid set scores.")
          playedSets = fixedScores.size.toUInt()
          fixedScores
        } else {
          log.e("[${match.identity}] Invalid set scores.")
          log.d(
            "[${match.identity}] Sets from final results were $wonSets, " +
              "however sets from scores were ${setScores.size}. " +
              "Discarding all scores."
          )
          playedSets = wonSets
          emptyList()
        }
    } else if (setScores.size < wonSets.toInt()) {
      log.w("[${match.identity}] Missing set scores.")
      log.d(
        "[${match.identity}] Sets from final results were $wonSets, however sets from scores were ${setScores.size}. "
      )
      playedSets = wonSets
    }
    val homeScores = setScores.map { it.first() }
    val awayScores = setScores.map { it.last() }
    return match.copy(
      result =
        Result(
          sets = playedSets,
          teamA = TeamResult(sets = homeSets, scores = homeScores),
          teamB = TeamResult(sets = awaySets, scores = awayScores),
        )
    )
  }

  private fun parseTime(date: String, time: String): Instant {
    val dateLocal =
      LocalDate.parse(
        input = date,
        format =
          LocalDate.Format {
            day()
            char(' ')
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            year()
          },
      )
    val timezone =
      if (
        dateLocal >= LocalDate(dateLocal.year, Month.MARCH, 25) &&
          dateLocal < LocalDate(dateLocal.year, Month.OCTOBER, 25)
      ) {
        BST
      } else {
        GMT
      }
    return dateLocal.atTime(LocalTime.parse(time)).toInstant(timezone)
  }

  /** Resolve match venue address into more detailed form. */
  private fun Match.resolveAddress(): Match {
    log.v("[${identity}] Resolving address.")
    val mapped =
      config.addresses.entries
        .firstOrNull { (k, _) ->
          venue.address.startsWith(k, ignoreCase = true) ||
            venue.alternativeAddress?.startsWith(k, ignoreCase = true) == true
        }
        ?.value
    return if (mapped != null) {
      log.d("[${identity}] Resolved $venue to mapped $mapped address.")
      copy(venue = venue.copy(address = mapped))
    } else {
      val fallbackVenue = venue.alternativeAddress ?: "$venue, England, United Kingdom"
      log.d(
        "[${identity}] Unable to map address $venue. Falling back to source venue $fallbackVenue"
      )
      copy(venue = venue.copy(address = fallbackVenue, alternativeAddress = venue.address))
    }
  }

  private companion object {
    val GMT = TimeZone.of("GMT")
    val BST = TimeZone.of("GMT+1")
    val TriangleDuration = 4.hours
    val MatchIdRegex = Regex("[A-Z]\\d{3}[a-z]")
  }
}
