package worker.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDateTime
import sportpress.Sportpress
import sportpress.data.event.Event
import sportpress.data.season.Season
import sportpress.data.team.Team
import javax.inject.Inject

class SportpressService @Inject constructor(
  private val client: Sportpress,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("SportpressService")

  suspend fun findActiveSeason(start: LocalDateTime): Season? {
    val season = client.listSeasons(limit = 100u)
      .onEach { log.d { "Found season ${it.identity}" } }
      .filter { it.name.split("-").first().toInt() == start.year }
      .singleOrNull()
    return season
  }

  suspend fun findTeams(season: Season, club: String): List<Team> {
    val prefix = club.lowercase()
    return client.listTeams(seasons = listOf(season.id), limit = 100u)
      .filter { it.slug.startsWith(prefix) }
      .onEach { log.d { "Found team ${it.identity}" } }
      .toList()
  }

  suspend fun findEvents(
    season: Season,
    teams: Collection<Team>,
  ): Map<Team, List<Event>> {
    val teamMap = teams.associateBy(Team::id)
    val eventMap = teamMap.mapValues { mutableListOf<Event>() }
    client.listEvents(
      seasons = listOf(season.id),
      limit = 100u,
    ).collect { event ->
      event.teams.forEach { teamId ->
        if (teamId in teamMap) {
          log.d { "Found event ${event.identity} for team ${teamMap.getValue(teamId).identity}" }
          eventMap.getValue(teamId).addLast(event)
        }
      }
    }
    return eventMap.mapKeys { (k) -> teamMap.getValue(k) }
  }
}
