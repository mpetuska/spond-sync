package worker.source

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import sportpress.Sportpress
import sportpress.data.event.Event
import sportpress.data.season.Season
import sportpress.data.team.Team
import sportpress.data.team.TeamId
import worker.data.SourceEvent
import worker.util.exit
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

class SportpressEventSource @Inject constructor(
  private val client: Sportpress,
  private val http: HttpClient,
  baseLogger: Logger,
) : EventSource {
  private val log = baseLogger.withTag("SportpressSource")

  override val name: String = "Sportpress"

  override suspend fun provideEvents(
    club: String,
    teams: Collection<String>,
    start: Instant,
    end: Instant?
  ): Map<String, List<SourceEvent>> {
    log.d("Looking for an active sportpress season")
    val season = findActiveSeason(start.toLocalDateTime(TimeZone.UTC))
    if (season != null) {
      log.i("Selected sportpress ${season.identity}")
    } else {
      log.exit("No active sportpress season found")
    }

    val teamData = fetchTeams(season, teams).toList()
    val actualTeamNames = teamData.map(Team::name)
    teams.filter { it !in actualTeamNames }.forEach {
      log.w("Sportpress team $it not found!")
    }

    val teamIds = teamData.map(Team::id)
    val events = client.listEvents(
      after = start,
      before = end,
      seasons = listOf(season.id),
      limit = 100u,
    ).filter { it.teams.any { id -> id in teamIds } }.toList()
    return teamData.associate { team ->
      log.d("Listing events for sportpress team ${team.identity}")
      val eventData = events.filter { event ->
        team.id in event.teams
      }.onEach { event ->
        log.v("Found event ${event.identity} for team ${team.identity}")
      }.map { event ->
        log.v("Building EventData for ${event.identity}")
        eventData(team, event).also {
          log.d("Built EventData for ${event.identity}")
        }
      }
      log.i("Listed events for sportpress team ${team.identity}")
      return@associate team.name to eventData
    }
  }

  private suspend fun eventData(team: Team, event: Event): SourceEvent {
    log.v("Extracting triangleId and host from ${event.day} for ${event.identity}")
    val (triangleId, host) = event.day.split("\\s+".toRegex(), limit = 2)
      .map { it.removePrefix("/") }
      .map(String::trim)
    log.d("Extracted triangleId=$triangleId, host=$host for ${event.identity}")

    log.v("Extracting teamA and teamB from ${event.name} for ${event.identity}")
    val (teamA, teamB) = event.name.split("&#8211;", limit = 2).map(String::trim)
    val homeMatch = teamA == team.name
    val (teamAId, teamBId) = event.teams
    log.v { "Extracted teamA=$teamA, teamA=$teamB, homeMatch=$homeMatch for ${event.identity}" }

    val startDate = event.dateGmt.toInstant(GMT)
    return SourceEvent(
      provider = name,
      source = event.link,
      triangleId = triangleId,
      id = "${event.id}",
      name = event.name,
      start = startDate,
      end = startDate + TRIANGLE_DURATION,
      teamA = teamA,
      teamAId = teamAId,
      teamB = teamB,
      teamBId = teamBId,
      host = host,
      homeMatch = homeMatch,
      address = resolveAddress(event),
      result = buildResult(event, teamAId, teamBId),
      lastUpdated = event.modifiedGmt.toInstant(GMT),
    )
  }

  private data class TeamResult(
    val id: TeamId,
    val sets: UInt,
    val scores: List<UInt>,
  )

  private fun buildResult(
    event: Event,
    teamAId: TeamId,
    teamBId: TeamId,
  ): SourceEvent.Result? {
    return if (event.results.isEmpty()) {
      null
    } else {
      val teamAScores = event.results.getValue(teamAId).run { listOfNotNull(one, two, three) }
      val teamBScores = event.results.getValue(teamBId).run { listOfNotNull(one, two, three) }
      val teamASets = teamAScores.indices.map { if (teamAScores[it] > teamBScores[it]) 1u else 0u }.sum()
      val teamBSets = teamBScores.indices.map { if (teamAScores[it] < teamBScores[it]) 1u else 0u }.sum()
      val teamAResult = TeamResult(id = teamAId, sets = teamASets, scores = teamAScores)
      val teamBResult = TeamResult(id = teamBId, sets = teamBSets, scores = teamBScores)

      val (winner, loser) = if (teamASets > teamBSets) {
        Pair(teamAResult, teamBResult)
      } else {
        Pair(teamBResult, teamAResult)
      }

      SourceEvent.Result(
        winnerId = if (teamAResult.sets > teamBResult.sets) teamAId else teamBId,
        loserId = if (teamAResult.sets < teamBResult.sets) teamAId else teamBId,
        sets = teamAResult.sets + teamBResult.sets,
        winnerSets = winner.sets,
        loserSets = loser.sets,
        winnerScores = winner.scores,
        loserScores = loser.scores,
      )
    }
  }

  private suspend fun resolveAddress(event: Event): String {
    log.v("Resolving address from ${event.link} for ${event.identity}")
    val html = http.get(event.link).bodyAsText()
    val address = Ksoup.parse(html)
      .getElementsByClass("sp-event-venue-address-row")
      .single { it.tag().name == "tr" }
      .child(0)
      .text()
    log.d("Resolved address=$address from ${event.link} for ${event.identity}")
    return address
  }

  private suspend fun findActiveSeason(start: LocalDateTime): Season? {
    val season = client.listSeasons(limit = 100u)
      .onEach { log.v("Found season ${it.identity}") }
      .filter { it.name.split("-").first().toInt() == start.year }
      .singleOrNull()
    return season
  }

  private fun fetchTeams(season: Season, teams: Collection<String>): Flow<Team> {
    return client.listTeams(seasons = listOf(season.id), limit = 100u)
      .filter { it.name in teams }
      .onEach { log.v("Found team ${it.identity}") }
  }

  private companion object {
    val GMT = TimeZone.of("GMT")
    val TRIANGLE_DURATION = 4.hours
  }
}
