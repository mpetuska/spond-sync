package dev.petuska.spond.sync.core

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.core.model.TriangleId
import dev.petuska.spond.sync.core.util.toTriple
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList

@Inject
@SingleIn(ClubScope::class)
class SyncService(
  private val timeSource: TimeSource,
  private val source: DataSource,
  private val sink: DataSink,
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
    for (teamId in teams) {
      log.d("[$teamId] Filtering triangles.")
      val teamTriangles = triangles.filter { teamId in it }
      val firstTriangle = teamTriangles.firstOrNull()
      if (firstTriangle == null) {
        log.w("[$teamId] Found no triangles.")
        continue
      }
      log.i("[$teamId] Found ${teamTriangles.size} triangles.")
      val team = firstTriangle.teamsList.single { it.id == teamId }
      sink.syncTeam(team, from, until, teamTriangles)
    }
  }

  private fun buildTriangle(id: TriangleId, matches: List<Match>): Triangle? {
    if (matches.size != 3) {
      log.e("[$id] Triangle must have exactly 3 matches. Instead had $matches.")
      return null
    }
    val host = findHost(id, matches) ?: return null
    val aVenues = matches.filter { host in it }.map { it.venue }.distinct()
    if (aVenues.size != 1) {
      log.e("[$id] Detected different venues for host ${host.identity}: $aVenues")
      return null
    }
    return Triangle(
      id = id,
      venue = aVenues.single(),
      start = matches.minOf(Match::start),
      end = matches.maxOf(Match::end),
      host = host,
      teams =
        matches.flatMap { setOf(it.teamA, it.teamB) }.distinct().sortedBy(Team::name).toTriple(),
      matches = matches.toTriple(),
    )
  }

  private fun findHost(id: TriangleId, matches: List<Match>): Team? {
    val mostATeam = matches.groupBy { it.teamA }.maxBy { it.value.size }
    if (mostATeam.value.size != 2) {
      log.e(
        "[$id] Expected most frequent A team to be A team for 2 matches, was ${mostATeam.value.size}."
      )
      return null
    }
    val firstMatch =
      matches.singleOrNull { it.id.endsWith('a') }
        ?: run {
          log.w { "[$id] No match id ending with `a`." }
          return mostATeam.key
        }
    val secondMatch =
      matches.singleOrNull { it.id.endsWith('b') }
        ?: run {
          log.w { "[$id] No match id ending with `b`." }
          return mostATeam.key
        }
    return if (secondMatch.teamA !in firstMatch) {
      secondMatch.teamA
    } else if (secondMatch.teamB !in firstMatch) {
      secondMatch.teamB
    } else {
      log.w {
        "[$id] Both teams from second match ${secondMatch.identity} also appear in first match ${firstMatch.identity}."
      }
      mostATeam.key
    }
  }
}
