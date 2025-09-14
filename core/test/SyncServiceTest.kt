package core

import assertk.assertThat
import assertk.assertions.hasSameSizeAs
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import co.touchlab.kermit.Severity
import core.di.TestApp
import core.di.build
import core.model.Match
import core.model.Team
import core.model.TeamId
import core.model.Time
import core.model.Triangle
import core.model.TriangleId
import core.model.Venue
import core.util.toTriple
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking

class SyncServiceTest {
  private val component = TestApp::class.build(severity = Severity.Verbose)
  private val timeSource = TimeSource()
  private val teams = List(6) { TeamId("Test Team ${it + 1}") }

  @Test
  fun updatesTrianglesWithoutResults() = runBlocking {
    val t1 = triangle("TriangleA", teams.take(3)).toList()
    val t2 = triangle("TriangleB", teams.take(3)).toList()

    val triangles = mutableListOf<Triangle>()
    val service = prepareService(t1 + t2) { triangle, _, _ -> triangles += triangle }

    service.syncMatches(
      from = component.club().seasonStart.atRuntime,
      until = component.club().seasonStart.atRuntime + 365.days,
    )
    val distinctTriangles = triangles.distinct()

    assertThat(distinctTriangles).hasSize(2)
    for ((triangle, expectedMatches) in
      mapOf(distinctTriangles[0] to t1, distinctTriangles[1] to t2)) {
      val actualMatches = triangle.matches.toList()
      assertThat(actualMatches).hasSameSizeAs(expectedMatches)

      for (m in actualMatches.indices) {
        assertThat(actualMatches[m]).isEqualTo(expectedMatches[m])
      }
    }
  }

  @Test
  fun updatesTrianglesWithResults() = runBlocking {
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
    val service = prepareService(t1 + t2) { triangle, _, _ -> triangles += triangle }

    service.syncMatches(
      from = component.club().seasonStart.atRuntime,
      until = component.club().seasonStart.atRuntime + 365.days,
    )
    val distinctTriangles = triangles.distinct()
    assertThat(distinctTriangles).hasSize(2)
    for ((triangle, expectedMatches) in
      mapOf(distinctTriangles[0] to t1, distinctTriangles[1] to t2)) {
      val actualMatches = triangle.matches.toList()
      assertThat(actualMatches).hasSameSizeAs(expectedMatches)

      for (m in actualMatches.indices) {
        assertThat(actualMatches[m]).isEqualTo(expectedMatches[m])
      }
    }
  }

  private fun prepareService(
    matches: Collection<Match>,
    sink: (triangle: Triangle, match: Match, team: Team) -> Unit,
  ): SyncService {
    return SyncService(
      timeSource = timeSource,
      source = component.fakeSource(matches),
      sink = FakeSink(onCreateMatch = sink),
      logger = component.logger,
      teams = matches.map { it.teamA.id }.toSet(),
    )
  }

  private fun triangle(name: String, teams: List<TeamId>, mod: (Match) -> Match = { it }) = run {
    require(teams.size == 3)
    triangle(name, teams[0], teams[1], teams[2], mod)
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
}
