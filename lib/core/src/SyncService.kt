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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

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

    val (friendlyMatches, leagueMatches) =
      matches.partition { it.id.contains("friendly", ignoreCase = true) }
    val triangles =
      leagueMatches
        .groupBy(Match::triangle)
        .mapNotNull { buildTriangle(it.key, it.value, friendlyMatches) }
        .toSet()
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

  private fun buildTriangle(
    id: TriangleId,
    matches: List<Match>,
    friendlyMatches: List<Match>,
    onInvalidTriangle: (TriangleId, List<Match>, ValidationError) -> Unit = { _, _, _ -> },
  ): Triangle? {
    val matches =
      if (matches.size == 3) {
        matches
      } else {
        val friendlies =
          friendlyMatches
            .filter { f -> matches.all { m -> m.start == f.start && m.venue == f.venue } }
            .distinct()
        matches.plus(friendlies).distinctBy { "${it.teamA} vs ${it.teamB}" }
      }
    if (matches.size != 3) {
      log.e("[$id] Triangle must have exactly 3 matches. Instead had ${matches.size} $matches.")
      return null
    }
    val host = findHost(id, matches) ?: return null
    val aVenues = matches.filter { host in it }.map { it.venue }.distinct()
    if (aVenues.size != 1) {
      log.e("[$id] Detected different venues for host ${host.identity}: $aVenues")
      onInvalidTriangle(id, matches, ValidationError.MultipleHomeVenues)
      return null
    }
    val invalidStartTimes = matches.filter { it.startTime.hour < 8 }
    if (invalidStartTimes.isNotEmpty()) {
      log.e("[$id] Detected matches with invalid start times: $invalidStartTimes")
      onInvalidTriangle(id, matches, ValidationError.InvalidStartTimes)
      return null
    }
    val teams = matches.flatMap { setOf(it.teamA, it.teamB) }.distinct().sortedBy(Team::name)
    if (teams.size != 3) {
      log.e("[$id] Detected triangle with invalid number of teams: $teams")
      onInvalidTriangle(id, matches, ValidationError.InvalidTeamCount)
      return null
    }
    return Triangle(
      id = id,
      venue = aVenues.single(),
      start = matches.minOf(Match::start),
      end = matches.maxOf(Match::end),
      host = host,
      teams = teams.toTriple(),
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

  suspend fun generateReport(from: Instant, until: Instant, out: Path) {
    val fromTime = timeSource.fromRuntime(from)
    val untilTime = timeSource.fromRuntime(until)
    log.d("Generating report to $out. from=$from, until=$until, teams=$teams.")
    val matches = source.listMatches(from = fromTime, until = untilTime).toList().distinct()
    log.d("Fetched ${matches.size} matches.")

    val errors = mutableListOf<Triple<TriangleId, List<Match>, ValidationError>>()
    val (friendlyMatches, leagueMatches) =
      matches.partition { it.id.contains("friendly", ignoreCase = true) }
    val triangles =
      leagueMatches
        .groupBy(Match::triangle)
        .mapNotNull {
          buildTriangle(it.key, it.value, friendlyMatches) { id, matches, err ->
            errors += Triple(id, matches, err)
          }
        }
        .distinct()
        .sortedBy { it.start }
    var sortedErrors = errors.toList().sortedBy { it.first.value }
    log.d("Built ${triangles.size} triangles.")

    val report = buildString {
      appendLine("# Match report")
      appendLine("Generated at ${timeSource.now().atSource}")
      appendLine()

      for (teamId in teams) {
        appendLine("## ${teamId.value}")
        val teamTriangles = triangles.filter { teamId in it }
        val invalidTriangles = sortedErrors.filter { it.second.any { match -> teamId in match } }
        sortedErrors = sortedErrors - invalidTriangles.toSet()
        appendLine("- Total Triangles: ${teamTriangles.size + invalidTriangles.size}")
        appendLine("- Total Matches: ${matches.count { teamId in it }}")

        appendLine("### Valid Triangles (${teamTriangles.size})")
        for (triangle in teamTriangles) {
          appendLine("#### ${triangle.id} (${triangle.start.atSource} - ${triangle.end.atSource})")
          appendLine("- Host: ${triangle.host.id}")
          appendLine("- Venue: ${triangle.venue.address}")
          appendLine("- Teams:")
          for (team in triangle.teamsList) {
            appendLine("  - ${team.id}")
          }
          appendLine("- Matches:")
          for (match in triangle.matches.toList()) {
            appendLine("  - ${match.id}: ${match.teamA.id} vs ${match.teamB.id}")
          }
        }
        appendLine()

        appendLine("### Invalid Triangles (${invalidTriangles.size})")
        for ((id, matches, error) in invalidTriangles) {
          appendInvalidTriangle(id, matches, error)
        }
        appendLine()
      }

      val hostedTriangles = triangles.filter { it.host.id in teams }
      appendLine("## Hosted Triangles (${hostedTriangles.size})")
      for (triangle in hostedTriangles) {
        appendLine(
          "- ${triangle.id} (${triangle.start.atSource} - ${triangle.end.atSource}): ${triangle.host.id}"
        )
      }
      appendLine()

      val hostedMatches =
        matches
          .groupBy { it.triangle }
          .filterValues { matches -> matches.any { match -> teams.any { team -> team in match } } }
          .filterValues { matches ->
            matches.filter { it.id.endsWith('a') }.none { match -> teams.any { it in match } }
          }
          .entries
          .sortedBy { (_, v) -> v.minOf { it.start } }
      appendLine("## Hosted Matches (${hostedMatches.size})")
      for ((triangleId, matches) in hostedMatches) {
        appendLine("### ${triangleId.value} (${matches.size})")
        for (match in matches.sortedBy { it.id }) {
          appendLine(
            "- ${match.id} ${match.start.atSource.toLocalDateTime(TimeZone.currentSystemDefault())}:"
          )
          appendLine("  - Teams: ${match.teamA.id} vs ${match.teamB.id}")
          appendLine("  - Venue: ${match.venue.address}")
        }
      }
      appendLine()

      val otherErrors = sortedErrors.toList()
      appendLine("## Other Invalid Triangles (${otherErrors.size})")
      for ((id, matches, error) in otherErrors) {
        appendInvalidTriangle(id, matches, error)
      }
      appendLine()

      appendLine("## Total: ${triangles.size} triangles, ${hostedTriangles.size} hosted.")
    }

    SystemFileSystem.sink(out).buffered().use { it.writeString(report) }
  }

  private fun StringBuilder.appendInvalidTriangle(
    id: TriangleId,
    matches: List<Match>,
    error: ValidationError,
  ) {
    appendLine("#### $id (${error.value})")
    when (error) {
      ValidationError.MultipleHomeVenues -> {
        for (match in matches) {
          appendLine(
            "- ${match.id} ${match.start.atSource}: ${match.teamA.id} vs ${match.teamB.id} at ${match.venue.address}"
          )
        }
      }
      ValidationError.InvalidStartTimes -> {
        for (match in matches) {
          appendLine(
            "- ${match.id} ${match.start.atSource}: ${match.teamA.id} vs ${match.teamB.id} at ${match.startTime}"
          )
        }
      }
      ValidationError.InvalidTeamCount -> {
        for (match in matches) {
          appendLine(
            "- ${match.id} ${match.start.atSource}: ${match.teamA.id} vs ${match.teamB.id}"
          )
        }
      }
    }
    appendLine()
  }

  private enum class ValidationError(val value: String) {
    MultipleHomeVenues("Multiple Home Venues"),
    InvalidStartTimes("Invalid Start Times"),
    InvalidTeamCount("Invalid Team Count"),
  }
}
