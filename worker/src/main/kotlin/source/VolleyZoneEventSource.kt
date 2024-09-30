package worker.source

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import sportpress.data.team.TeamId
import utils.Identifiable
import worker.WorkerConfig
import worker.data.SourceEvent
import worker.service.TimeService
import worker.source.VolleyZoneEventSource.Triangle
import worker.source.VolleyZoneEventSource.VolleyZoneEvent
import worker.util.exit
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.time.Duration.Companion.hours

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
    club: String,
    teams: Collection<String>,
    start: Instant,
    end: Instant?
  ): Map<String, List<SourceEvent>> {
    val triangles = config.sources.entries.asFlow()
      .flatMapConcat { (name, url) ->
        log.d("Fetching events for $name from $url")
        val triangles = parseLeagueTriangles(url)
        log.i("Fetched ${triangles.size} triangles for $name")
        triangles.entries.asFlow()
      }
      .filter { (_, it) -> it.start >= start && it.end <= (end ?: it.end) }
      .toList()
      .associate { (k, v) -> k to v }
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
    val vzEvents = fixturesTable.getElementsByClass("table-body").map { row ->
      val venue = row.attr("data-venue").trim()
      VolleyZoneEvent(
        id = row.attr("data-comment").trim(),
        date = row.attr("data-date").trim(),
        time = row.attr("data-time").trim(),
        homeTeam = row.attr("data-hometeam").trim(),
        awayTeam = row.attr("data-awayteam").trim(),
        homeScore = row.attr("data-homescore").takeIf(String::isNotBlank)?.toUInt(),
        awayScore = row.attr("data-awayscore").takeIf(String::isNotBlank)?.toUInt(),
        venue = venue,
        venueExtra = row.getElementsByTag("li").getOrNull(5)?.getElementsByClass("data")?.firstOrNull()?.text()
          ?.takeIf { it.startsWith(venue, ignoreCase = true) }
      )
    }
    vzEvents.sortedBy(VolleyZoneEvent::id).groupBy(VolleyZoneEvent::triangleId)

    val processedTriangles: List<Pair<Triangle?, List<VolleyZoneEvent>?>> =
      vzEvents.groupBy(VolleyZoneEvent::triangleId)
        .values
        .map { events ->
          if (events.size != 3) {
            val eventsStr = events.joinToString("\n\t", prefix = "\n\t", transform = VolleyZoneEvent::shortIdentity)
            log.w("Invalid triangle! Events:$eventsStr")
            null to events
          } else {
            buildTriangle(url, events) to null
          }
        }
    val correctTriangles = processedTriangles.map { it.first }.filterNotNull()
    val fuckedTriangles = processedTriangles.map { it.second }.filterNotNull()
      .flatten()
      .groupBy { event -> "${event.date}${event.time}${event.venue}" }
      .values
      .mapNotNull { events ->
        if (events.size == 3) {
          buildTriangle(url, events)
        } else {
          log.e("Could not fix fucked triangle, still got events != 3: ${events.map(VolleyZoneEvent::shortIdentity)}")
          null
        }
      }

    val triangles = if (config.attemptToFixBrokenTriangles) {
      fuckedTriangles + correctTriangles
    } else {
      fuckedTriangles.forEach { triangle ->
        log.w("Discarding fucked triangle ${triangle.identity}")
      }
      correctTriangles
    }

    return triangles.associateBy(Triangle::id)
  }

  private fun buildTriangle(
    url: String,
    events: Collection<VolleyZoneEvent>
  ): Triangle {
    val host = events.groupBy { it.homeTeam }.maxBy { (_, v) -> v.size }.value.first()
    val triangleId = host.id.dropLast(1)
    if (events.map { "${it.date}${it.time}" }.toSet().size != 1) {
      val eventsStr = events.joinToString("\n\t", prefix = "\n\t", transform = VolleyZoneEvent::shortIdentity)
      log.w("Invalid triangle times for triangle ${triangleId}! Events:$eventsStr")
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
      teams = events.flatMap { listOf(it.homeTeam, it.awayTeam) }.toSet(),
      events = events.sortedBy(VolleyZoneEvent::id),
    )
  }

  private fun parseTriangle(team: String, triangle: Triangle): List<SourceEvent> {
    val description = buildString {
      appendLine("Triangle ID: ${triangle.id}")
      appendLine("Host: ${triangle.host}")
      appendLine("Link: ${triangle.url}")
    }
    return triangle.events.filter { it.homeTeam == team || it.awayTeam == team }.map { event ->
      val homeTeamId = event.homeTeam.hashCode().toUInt()
      val awayTeamId = event.awayTeam.hashCode().toUInt()
      SourceEvent(
        provider = name,
        id = event.id,
        name = "${event.homeTeam} vs ${event.awayTeam}",
        start = triangle.start,
        end = triangle.end,
        description = description,
        teamA = event.homeTeam,
        teamAId = homeTeamId,
        teamB = event.awayTeam,
        teamBId = awayTeamId,
        host = triangle.host,
        homeMatch = triangle.host == team,
        address = triangle.address,
        result = buildResult(event, homeTeamId, awayTeamId),
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
    return if (event.homeScore == null || event.awayScore == null) {
      null
    } else {
      val teamASets = event.homeScore
      val teamBSets = event.awayScore
      val totalSets = (teamASets + teamBSets).toInt()
      val teamAScores = List(totalSets) { 0u }
      val teamBScores = List(totalSets) { 0u }
      val teamAResult = TeamResult(id = homeTeamId, sets = teamASets, scores = teamAScores)
      val teamBResult = TeamResult(id = awayTeamId, sets = teamBSets, scores = teamBScores)

      val (winner, loser) = if (teamASets > teamBSets) {
        Pair(teamAResult, teamBResult)
      } else {
        Pair(teamBResult, teamAResult)
      }

      SourceEvent.Result(
        winnerId = if (teamAResult.sets > teamBResult.sets) homeTeamId else awayTeamId,
        loserId = if (teamAResult.sets < teamBResult.sets) homeTeamId else awayTeamId,
        sets = teamAResult.sets + teamBResult.sets,
        winnerSets = winner.sets,
        loserSets = loser.sets,
        // TODO
        winnerScores = null,
        loserScores = null,
      )
    }
  }

  private companion object {
    val GMT = TimeZone.of("GMT")
    val BST = TimeZone.of("GMT+1")
    val TRIANGLE_DURATION = 4.hours

    fun parseTime(event: VolleyZoneEvent): Instant {
      val dateLocal = LocalDate.parse(
        input = event.date,
        format = LocalDate.Format {
          dayOfMonth(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year()
        }
      )
      val timezone = if (dateLocal >= LocalDate(dateLocal.year, Month.MARCH, 25) && dateLocal < LocalDate(
          dateLocal.year,
          Month.OCTOBER,
          25
        )
      ) BST else GMT
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
    val homeScore: UInt?,
    val awayScore: UInt?,
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
    val scores: List<UInt>,
  )
}
