package dev.petuska.spond.sync.core

import co.touchlab.kermit.Severity
import de.infix.testBalloon.framework.core.testSuite
import dev.petuska.spond.sync.core.di.TestGraph
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.core.model.TriangleId
import dev.petuska.spond.sync.core.model.Venue
import dev.petuska.spond.sync.core.util.toTriple
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

val SyncServiceTest by testSuite {
  test("Updates triangles without results") {
    val t1 = triangle("TriangleA", teams.take(3)).toList()
    val t2 = triangle("TriangleB", teams.take(3)).toList()

    val triangles = mutableListOf<Triangle>()
    val service = prepareService(t1 + t2) { _, _, _, actual -> triangles += actual }

    service.syncMatches(
      from = component.club().seasonStart.atRuntime,
      until = component.club().seasonStart.atRuntime + 365.days,
    )
    val distinctTriangles = triangles.distinct()

    assertEquals(2, distinctTriangles.size)
    for ((triangle, expectedMatches) in
      mapOf(distinctTriangles[0] to t1, distinctTriangles[1] to t2)) {
      val actualMatches = triangle.matches.toList()
      assertEquals(expectedMatches.size, actualMatches.size)

      for (m in actualMatches.indices) {
        assertEquals(expectedMatches[m], actualMatches[m])
      }
    }
  }
  test("Updates triangles with results") {
    val t1 =
      triangle("TriangleA", teams.take(3)) {
          it.copy(result = result(scoresA = listOf(25u, 25u), scoresB = listOf(12u, 21u)))
        }
        .toList()
    val t2 =
      triangle("TriangleB", teams.take(3)) {
          it.copy(result = result(scoresA = listOf(25u, 25u), scoresB = listOf(12u, 21u)))
        }
        .toList()

    val triangles = mutableListOf<Triangle>()
    val service = prepareService(t1 + t2) { _, _, _, actual -> triangles += actual }

    service.syncMatches(
      from = component.club().seasonStart.atRuntime,
      until = component.club().seasonStart.atRuntime + 365.days,
    )
    val distinctTriangles = triangles.distinct()
    assertEquals(2, distinctTriangles.size)
    for ((triangle, expectedMatches) in
      mapOf(distinctTriangles[0] to t1, distinctTriangles[1] to t2)) {
      val actualMatches = triangle.matches.toList()
      assertEquals(expectedMatches.size, actualMatches.size)

      for (m in actualMatches.indices) {
        assertEquals(expectedMatches[m], actualMatches[m])
      }
    }
  }
}

private val component = createGraphFactory<TestGraph.Factory>().create(severity = Severity.Verbose)
private val timeSource = TimeSource()
private val teams = List(6) { TeamId("Test Team ${it + 1}") }

private fun prepareService(
  matches: Collection<Match>,
  sink: (team: Team, from: Time, until: Time, triangles: List<Triangle>) -> Unit,
): SyncService {
  return SyncService(
    timeSource = timeSource,
    source = component.fakeSource(matches),
    sink = FakeSink(onSyncTeam = sink),
    logger = component.logger,
    teams = matches.map { it.teamA.id }.toSet(),
  )
}

private fun triangle(
  name: String,
  teams: List<TeamId>,
  mod: (Match) -> Match = { it },
): Triple<Match, Match, Match> {
  require(teams.size == 3)
  return triangle(name, teams[0], teams[1], teams[2], mod)
}

private fun match(
  triangle: String,
  teamA: TeamId,
  teamB: TeamId,
  host: TeamId,
  order: UInt,
  start: Time = component.club().seasonStart,
  end: Time = start + 4.hours,
  result: Match.Result? = null,
  title: String = "$teamA vs $teamB",
  venue: Venue = Venue("Test Venue address at $host", null),
  lastUpdated: Time = timeSource.fromSource(Clock.System.now()),
): Match {
  val ta = if (teamB == host) teamB else teamA
  val tb = if (teamB == host) teamA else teamB
  return Match(
    source = "Test builder",
    triangle = TriangleId(triangle),
    id = triangle + order,
    order = order,
    title = title,
    venue = venue,
    start = start,
    end = end,
    teamA = Team(ta, ta.value),
    teamB = Team(tb, tb.value),
    result = result,
    lastUpdated = lastUpdated,
  )
}

private fun triangle(
  name: String,
  t1: TeamId,
  t2: TeamId,
  t3: TeamId,
  mod: (Match) -> Match = { it },
): Triple<Match, Match, Match> {
  return listOf(t1, t2, t3, t1)
    .windowed(2)
    .mapIndexed { i, (a, b) ->
      match(triangle = name, teamA = a, teamB = b, host = t1, order = i.toUInt()).let(mod)
    }
    .toTriple()
}

private fun result(
  scoresA: List<UInt>,
  scoresB: List<UInt>,
  setsA: UInt = scoresA.withIndex().count { (i, it) -> it > scoresB[i] }.toUInt(),
  setsB: UInt = scoresA.withIndex().count { (i, it) -> it > scoresA[i] }.toUInt(),
  sets: UInt = scoresA.size.toUInt(),
): Match.Result {
  return Match.Result(
    sets = sets,
    teamA = Match.TeamResult(sets = setsA, scores = scoresA),
    teamB = Match.TeamResult(sets = setsB, scores = scoresB),
  )
}
