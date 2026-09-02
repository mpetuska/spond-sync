package dev.petuska.spond.sync.runtime.source.volleyzone

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.nodes.Element
import dev.petuska.spond.sync.runtime.config.source.SourceConfig
import dev.petuska.spond.sync.runtime.model.Match
import dev.petuska.spond.sync.runtime.model.SourceId
import dev.petuska.spond.sync.runtime.model.Team
import dev.petuska.spond.sync.runtime.model.TeamId
import dev.petuska.spond.sync.runtime.model.Time
import dev.petuska.spond.sync.runtime.model.Venue
import dev.petuska.spond.sync.runtime.util.TimeSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalTime

@Inject
@SingleIn(AppScope::class)
class NvlDataSource(
  private val client: VolleyZoneClient,
  private val parser: VolleyZoneParser,
  private val timeSource: TimeSource,
) {
  private val log = Logger.withTag("NvlDataSource")

  suspend fun listMatches(
    sourceId: SourceId,
    config: SourceConfig.NVL,
    from: Time,
    until: Time,
  ): List<Match> {
    val url = config.url
    log.d("[$sourceId] Fetching events from $url.")
    val fixtures =
      client.fetchFixtures(sourceId, url) { row ->
        parseMatch(sourceId = sourceId, source = url, row = row)
      }
    val results =
      client.fetchResults(sourceId, url) { row, scores ->
        parseFinishedMatch(sourceId = sourceId, source = url, row = row, scores = scores)
      }

    return fixtures.plus(results).distinctBy(Match::identity).filter { match ->
      if (match.start < from || match.end > until) {
        log.d(
          "[$sourceId][${match.identity}] Discarding match since its time " +
            "${match.start.atSource}..<${match.end.atSource} does not fit into requested " +
            "time frame ${from.atSource}..<${until.atSource}."
        )
        false
      } else {
        true
      }
    }
  }

  private fun parseMatch(sourceId: SourceId, source: Url, row: Element): Match? {
    val homeTeam = parser.homeTeam(row)
    val awayTeam = parser.awayTeam(row)
    val date = parser.date(row)
    val time = parser.time(row)
    val venue = parser.venue(row)
    val venueExtra = parser.venueExtra(row, venue)
    val comment = parser.comment(row)
    if (date == null || time == null || homeTeam == null || awayTeam == null || venue == null) {
      log.e(
        "[$sourceId] Missing match data: date=$date, time=$time," +
          " homeTeam=$homeTeam, awayTeam=$awayTeam," +
          " venue=$venue, venueExtra=$venueExtra," +
          " comment=$comment"
      )
      log.d("row=$row")
      return null
    }

    val friendly = comment?.contains("friend", ignoreCase = true) == true
    val id =
      if (friendly) {
        "Friendly"
      } else {
        comment?.let(MatchIdRegex::find)?.value
      }
    if (id == null) {
      log.e(
        "[$sourceId] Cannot find event ID: date=$date, time=$time," +
          " homeTeam=$homeTeam, awayTeam=$awayTeam," +
          " venue=$venue, venueExtra=$venueExtra," +
          " comment=$comment"
      )
      log.d("row=$row")
      return null
    }
    val order = 1
    val startTime = LocalTime.parse(time)
    if (startTime.hour < 8) {
      log.w(
        "[$sourceId][$id] Invalid match start time: date=$date, time=$time," +
          " homeTeam=$homeTeam, awayTeam=$awayTeam," +
          " venue=$venue, venueExtra=$venueExtra," +
          " comment=$comment"
      )
      log.d("row=$row")
    }
    val start = parser.parseTime(date = date, time = startTime)
    val teamA = Team(id = TeamId(homeTeam), name = homeTeam)
    val teamB = Team(id = TeamId(awayTeam), name = awayTeam)
    return Match(
        source = source.toString(),
        id = id,
        order = order.toUInt(),
        title = "$homeTeam vs $awayTeam",
        venue = Venue(address = venue, alternativeAddress = venueExtra),
        start = timeSource.fromSource(start),
        startTime = startTime,
        end = timeSource.fromSource(start + MatchDuration),
        teamA = teamA,
        teamB = teamB,
        result = null,
        lastUpdated = timeSource.fromSource(Clock.System.now()),
      )
      .let(parser::resolveAddress)
  }

  private fun parseFinishedMatch(
    sourceId: SourceId,
    source: Url,
    row: Element,
    scores: Element,
  ): Match? {
    if (!row.hasClass("table-body") || !scores.hasClass("list-centered_bottom_league")) {
      return null
    }
    val match = parseMatch(sourceId, source, row) ?: return null

    log.v("[${match.identity}] Parsing scores.")
    val homeSets = parser.homeSets(row) ?: 0u
    val awaySets = parser.awaySets(row) ?: 0u
    var setScores = parser.setScores(scores)
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
        Match.Result(
          sets = playedSets,
          teamA = Match.TeamResult(sets = homeSets, scores = homeScores),
          teamB = Match.TeamResult(sets = awaySets, scores = awayScores),
        )
    )
  }

  private companion object {
    val MatchIdRegex = Regex("[A-Z]+\\d+-[A-Z]+\\d+")
    val MatchDuration = 2.hours
  }
}
