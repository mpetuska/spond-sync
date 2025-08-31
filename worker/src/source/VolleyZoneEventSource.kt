package worker.source

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import sportpress.data.team.TeamId
import utils.Identifiable
import worker.WorkerConfig
import worker.data.SourceEvent
import worker.service.TimeService
import worker.util.exit
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class VolleyZoneEventSource @Inject constructor(
  private val http: HttpClient,
  private val timeService: TimeService,
  private val config: WorkerConfig.Volleyzone,
  baseLogger: Logger,
) : EventSource {
  private val log = baseLogger.withTag("VolleyZoneSource")

  override val name: String = "VolleyZone"

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun provideEvents(
    club: String, teams: Collection<String>, start: Instant, end: Instant?
  ): Map<String, List<SourceEvent>> {
    val triangles = config.leagues.entries.asFlow().flatMapConcat { (name, url) ->
      log.d("Fetching events for $name from $url")
      val triangles = parseLeagueTriangles(url)
      log.i("Fetched ${triangles.size} triangles for $name")
      triangles.entries.asFlow()
    }.filter { (_, it) -> it.start >= start && it.end <= (end ?: it.end) }.toList().associate { (k, v) -> k to v }
    return teams.associateWith { team ->
      triangles.values.mapNotNull { triangle ->
        if (team !in triangle.teams) null else parseTriangle(team, triangle)
      }.flatten()
    }
  }

  private suspend fun parseLeagueTriangles(url: String): Map<String, Triangle> {
    val document = http.get(url).bodyAsText().let(Ksoup::parse)
    val fixturesTable = document.getElementById("fixtures_league")
    if (fixturesTable == null) {
      log.exit("Unable to find fixtures at $url")
    }
    val vzFixtures = fixturesTable.getElementsByClass("table-body").mapNotNull(::parseEvent)

    val resultsTable = document.getElementById("results_league")
    if (resultsTable == null) {
      log.exit("Unable to find results at $url")
    }
    val vzResults =
      resultsTable.getElementsByTag("ul").toList().filter { !it.hasClass("table-header") }.windowed(size = 2, step = 2)
        .mapNotNull { (row, scores) -> parseResult(row, scores) }

    val vzEvents = vzFixtures + vzResults

    val processedTriangles: List<Pair<Triangle?, List<VolleyZoneEvent>?>> =
      vzEvents.sortedBy(VolleyZoneEvent::id).groupBy(VolleyZoneEvent::triangleId).values.map { events ->
        if (events.size != 3) {
          val eventsStr = events.joinToString("\n\t", prefix = "\n\t", transform = VolleyZoneEvent::shortIdentity)
          log.w("Invalid triangle event count! Events:$eventsStr")
          null to events
        } else {
          buildTriangle(url, events) to null
        }
      }
    val correctTriangles = processedTriangles.map { it.first }.filterNotNull()
    val fuckedTriangles = processedTriangles.map { it.second }.filterNotNull()

    if (fuckedTriangles.isNotEmpty()) {
      log.w("Detected ${fuckedTriangles.size} broken triangles!")
    }

    val triangles = if (config.attemptToFixBrokenTriangles) {
      val correctedFuckedTriangles = correctFuckedTriangles(url, fuckedTriangles.flatten())
      correctedFuckedTriangles + correctTriangles
    } else {
      fuckedTriangles.forEach { events ->
        log.w("Discarding fucked triangle: ${events.map(VolleyZoneEvent::shortIdentity)}")
      }
      correctTriangles
    }

    return triangles.associateBy(Triangle::id)
  }

  private fun parseEvent(row: Element): VolleyZoneEvent? {
    val homeTeam = row.attr("data-hometeam").trim()
    val awayTeam = row.attr("data-awayteam").trim()
    val date = row.attr("data-date").trim()
    val venue = row.attr("data-venue").trim()
    val venueExtra = row.getElementsByTag("li").getOrNull(5)?.getElementsByClass("data")?.firstOrNull()?.text()
      ?.takeIf { it.startsWith(venue, ignoreCase = true) }
    val id = row.attr("data-comment").trim().let { comment ->
      ID_REGEX.find(comment)?.value ?: run {
        log.w(
          "Cannot find event ID: date=$date, homeTeam=$homeTeam, awayTeam=$awayTeam, " + "venue=$venue, venueExtra=$venueExtra, comment=$comment"
        )
        return null
      }
    }
    return VolleyZoneEvent(
      id = id,
      date = date,
      time = row.attr("data-time").trim(),
      homeTeam = homeTeam,
      awayTeam = awayTeam,
      venue = venue,
      venueExtra = venueExtra,
      homeSets = null,
      awaySets = null,
      homeScores = null,
      awayScores = null,
    )
  }

  private fun parseResult(row: Element, scores: Element): VolleyZoneEvent? {
    val event = parseEvent(row)
    if (event == null || !row.hasClass("table-body") || !scores.hasClass("list-centered_bottom_league")) {
      return null
    }
    val homeSets = row.attr("data-homescore").trim().takeIf(String::isNotBlank)?.toUInt()
    val awaySets = row.attr("data-awayscore").trim().takeIf(String::isNotBlank)?.toUInt()
    val results = scores.getElementsByTag("li").mapNotNull { li ->
      li.getElementsByTag("span").mapNotNull { it.text().trim().toUIntOrNull() }.takeIf { it.isNotEmpty() }
    }
    val totalSets = (homeSets ?: 0u) + (awaySets ?: 0u)
    val (homeScores, awayScores) = if (results.size != totalSets.toInt()) {
      log.w(
        "Invalid set count for event ${event.id}. Sets from final results were $totalSets, " + "however sets from scores were ${results.size}"
      )
      null to null
    } else {
      results.map { it.first() } to results.map { it.last() }
    }
    return event.copy(
      homeSets = homeSets,
      awaySets = awaySets,
      homeScores = homeScores,
      awayScores = awayScores,
    )
  }

  private fun correctFuckedTriangles(url: String, events: List<VolleyZoneEvent>): List<Triangle> {
    return events.groupBy { event -> "${event.date}${event.time}${event.venue}" }.values.mapNotNull { events ->
      if (events.size == 3) {
        buildTriangle(url, events)
      } else {
        log.e("Could not fix fucked triangle, still got events != 3: ${events.map(VolleyZoneEvent::shortIdentity)}")
        null
      }
    }
  }

  private fun buildTriangle(
    url: String, events: Collection<VolleyZoneEvent>
  ): Triangle {
    val sortedEvents = events.sortedBy(VolleyZoneEvent::id)
    val hostEvents = sortedEvents.groupBy { it.homeTeam }.maxBy { (_, v) -> v.size }.value
    val host = hostEvents.first()
    val triangleId = host.id.dropLast(1)
    if (sortedEvents.any { host.venue != it.venue || host.venueExtra != it.venueExtra }) {
      val venues = sortedEvents.joinToString("\n\t") {
        "${it.id}: ${it.homeTeam} - ${it.awayTeam} @ ${it.venueExtra ?: it.venue}"
      }
      log.w("Broken triangle $triangleId! Venues do not match:\n\t$venues")
    }
    if (sortedEvents.map { "${it.date}${it.time}" }.toSet().size != 1) {
      val eventsStr = sortedEvents.joinToString("\n\t", prefix = "\n\t", transform = VolleyZoneEvent::shortIdentity)
      log.w("Invalid triangle times for triangle $triangleId! Events:$eventsStr")
    }
    val start = parseTime(host)
    val address = resolveAddress(host)
    return Triangle(
      id = triangleId,
      url = url.removeSuffix("/"),
      host = host.homeTeam,
      address = address,
      start = start,
      end = start + TRIANGLE_DURATION,
      teams = sortedEvents.flatMap { listOf(it.homeTeam, it.awayTeam) }.toSet(),
      events = sortedEvents,
    )
  }

  private fun parseTriangle(team: String, triangle: Triangle): List<SourceEvent> {
    return triangle.events.filter { it.homeTeam == team || it.awayTeam == team }.map { event ->
      val homeTeamId = event.homeTeam.hashCode().toUInt()
      val awayTeamId = event.awayTeam.hashCode().toUInt()
      SourceEvent(
        provider = name,
        source = triangle.url,
        triangleId = triangle.id,
        id = event.id,
        name = "${event.id}: ${event.homeTeam} - ${event.awayTeam}",
        start = timeService.reset(triangle.start),
        end = timeService.reset(triangle.end),
        teamA = event.homeTeam,
        teamAId = homeTeamId,
        teamB = event.awayTeam,
        teamBId = awayTeamId,
        host = triangle.host,
        homeMatch = triangle.host == team,
        address = triangle.address,
        result = buildResult(event, homeTeamId = homeTeamId, awayTeamId = awayTeamId),
        lastUpdated = timeService.now(),
      )
    }
  }

  private fun resolveAddress(event: VolleyZoneEvent): String {
    val mapped = config.addresses.entries.firstOrNull { (k, _) -> event.venue.startsWith(k, ignoreCase = true) }?.value
    return if (mapped != null) {
      mapped
    } else {
      val venue = "${event.venueExtra ?: event.venue}, England, United Kingdom"
      log.w("Unable to map address ${event.venue} for event ${event.id}. Falling back to raw venue $venue")
      venue
    }
  }

  private fun buildResult(event: VolleyZoneEvent, homeTeamId: UInt, awayTeamId: UInt): SourceEvent.Result? {
    return if (event.homeSets == null || event.awaySets == null) {
      null
    } else {
      val teamAResult = TeamResult(id = homeTeamId, sets = event.homeSets, scores = event.homeScores)
      val teamBResult = TeamResult(id = awayTeamId, sets = event.awaySets, scores = event.awayScores)

      val (winner, loser) = if (teamAResult.sets > teamBResult.sets) {
        teamAResult to teamBResult
      } else {
        teamBResult to teamAResult
      }

      SourceEvent.Result(
        winnerId = winner.id,
        loserId = loser.id,
        sets = winner.sets + loser.sets,
        winnerSets = winner.sets,
        loserSets = loser.sets,
        winnerScores = winner.scores,
        loserScores = loser.scores,
      )
    }
  }

  private companion object {
    val GMT = TimeZone.of("GMT")
    val BST = TimeZone.of("GMT+1")
    val TRIANGLE_DURATION = 4.hours
    val ID_REGEX = Regex("[A-Z]\\d{3}[a-z]")

    fun parseTime(event: VolleyZoneEvent): Instant {
      val dateLocal = LocalDate.parse(
        input = event.date, format = LocalDate.Format {
          day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
        })
      val timezone = if (dateLocal >= LocalDate(dateLocal.year, Month.MARCH, 25) && dateLocal < LocalDate(
          dateLocal.year, Month.OCTOBER, 25
        )
      ) {
        BST
      } else {
        GMT
      }
      return dateLocal.atTime(LocalTime.parse(event.time)).toInstant(timezone)
    }
  }

  private data class VolleyZoneEvent(
    val id: String,
    val date: String,
    val time: String,
    val venue: String,
    val venueExtra: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeSets: UInt?,
    val awaySets: UInt?,
    val homeScores: List<UInt>?,
    val awayScores: List<UInt>?,
  ) : Identifiable {
    val triangleId: String = id.take(4)
    override val identity: String =
      "VolleyZoneEvent(id=$id, date=$date, time=$time, homeTeam=$homeTeam, awayTeam=$awayTeam)"
    val shortIdentity get() = "$id $date $time $homeTeam - $awayTeam"
  }

  private data class Triangle(
    val id: String,
    val url: String,
    val host: String,
    val address: String,
    val start: Instant,
    val end: Instant,
    val teams: Set<String>,
    val events: List<VolleyZoneEvent>,
  ) : Identifiable {
    override val identity: String
      get() = "Triangle(id=$id, address=$address, host=$host, teams=$teams, events=${
        events.map(
          VolleyZoneEvent::identity
        )
      })"
  }

  private data class TeamResult(
    val id: TeamId,
    val sets: UInt,
    val scores: List<UInt>?,
  )
}
