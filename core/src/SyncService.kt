@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package core

import co.touchlab.kermit.Logger
import core.di.ClubScope
import core.model.Match
import core.model.Team
import core.model.TeamId
import core.model.Time
import core.model.Triangle
import core.model.TriangleId
import core.util.toTriple
import kotlin.time.Instant
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.toList
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import utils.Identifiable

@Inject
@SingleIn(ClubScope::class)
class SyncService(
  private val timeSource: TimeSource,
  private val source: DataSource,
  private val sink: DataSink<Identifiable>,
  private val teams: Set<TeamId>,
  logger: Logger = Logger,
) {
  private val log = logger.withTag("SyncService")

  suspend fun syncMatches(from: Instant, until: Instant) {
    val fromTime = timeSource.fromRuntime(from)
    val untilTime = timeSource.fromRuntime(until)
    log.d("Starting match sync. from=$from, until=$until, teams=$teams.")
    val matches = source.listMatches(from = fromTime, until = untilTime).toList().distinct()
    log.d("Fetched ${matches.size} matches.")

    val triangles =
      matches.groupBy(Match::triangle).mapNotNull { buildTriangle(it.key, it.value) }.toSet()
    log.d("Built ${triangles.size} triangles.")

    log.v("Starting match synchronisation.")
    updateTriangles(triangles = triangles, teams = teams, from = fromTime, until = untilTime)
    log.v("Finished match synchronisation.")
  }

  private suspend fun updateTriangles(
    triangles: Set<Triangle>,
    teams: Set<TeamId>,
    from: Time,
    until: Time,
  ) {
    val updates =
      triangles
        .flatMap { triangle ->
          triangle.matches.toList().flatMap { match ->
            buildList {
              if (match.teamA.id in teams) add(Triple(triangle, match, match.teamA))
              if (match.teamB.id in teams) add(Triple(triangle, match, match.teamB))
            }
          }
        }
        .associateBy { (_, match, team) -> team.id to match.id }
        .toMutableMap()
    val noLongerPresentSinkMatches = mutableSetOf<Pair<TeamId, Identifiable>>()
    for (teamId in teams) {
      log.v("[$teamId] Updating existing matches.")
      sink.listExistingMatches(teamId, from, until).buffer().collect { (matchId, it) ->
        val update = updates.remove(teamId to matchId)
        if (update == null) {
          log.w("[$teamId] Sink match ${it.identity} was not found on source.")
          noLongerPresentSinkMatches += teamId to it
          return@collect
        }
        log.v("[$teamId] Updating existing sink match ${it.identity}.")
        sink.updateMatch(
          triangle = update.first,
          match = update.second,
          team = update.third,
          existing = it,
        )
      }
      // TODO: Clear no longer present matches from noLongerPresentSinkMatches

      log.v("[$teamId] Creating new matches.")
      val teamMatches = updates.filterKeys { (id, _) -> id == teamId }.values
      for ((triangle, match, team) in teamMatches) {
        log.v("[$teamId] Creating new sink match ${match.identity}.")
        updates.remove(team.id to match.id)
        sink.createMatch(triangle = triangle, match = match, team = team)
      }
    }

    for (match in updates.values.map { it.second }) {
      log.v("[${match.id}] Discarding match not having any teams of interest.")
    }
  }

  private fun buildTriangle(id: TriangleId, matches: List<Match>): Triangle? {
    if (matches.size != 3) {
      log.e("[$id] Triangle has ${matches.size} != 3 matches.")
      return null
    }
    val mostATeam = matches.groupBy { it.teamA }.maxBy { it.value.size }
    if (mostATeam.value.size != 2) {
      log.e(
        "[$id] Expected most frequent A team to be A team for 2 matches, was ${mostATeam.value.size}."
      )
      return null
    }
    val host = mostATeam.key
    val aVenues = mostATeam.value.map { it.venue }
    if (aVenues.distinct().size != 1) {
      log.e("[$id] Detected different venues for host ${host.identity}: $aVenues")
      return null
    }
    return Triangle(
      id = mostATeam.value.first().triangle,
      venue = aVenues.first(),
      start = matches.minOf(Match::start),
      end = matches.maxOf(Match::start),
      host = host,
      teams =
        matches.flatMap { setOf(it.teamA, it.teamB) }.distinct().sortedBy(Team::name).toTriple(),
      matches = matches.toTriple(),
    )
  }
}
