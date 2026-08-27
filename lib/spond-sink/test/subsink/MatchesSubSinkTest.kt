package dev.petuska.spond.sync.spond.sink.subsink

import de.infix.testBalloon.framework.core.testSuite
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.core.model.TriangleId
import dev.petuska.spond.sync.core.model.Venue
import dev.petuska.spond.sync.spond.data.event.Event
import dev.petuska.spond.sync.spond.sink.di.TestGraph
import dev.petuska.spond.sync.testing.Resource
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.toList
import kotlinx.io.files.Path

private fun MockRequestHandleScope.respondJson(content: String) =
  respond(
    content = ByteReadChannel(content),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
  )

val MatchesSubSinkTest by testSuite {
  test("listExistingMatches returns managed events") {
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          httpClientEngine =
            MockEngine { request ->
              when {
                request.url.fullPath.contains("groups") ->
                  respondJson(
                    Resource.readText(
                      Path("spond/groups.json"),
                      "spond-sink",
                    )
                  )
                request.url.fullPath.contains("sponds") ->
                  respondJson(
                    Resource.readText(
                      Path("spond/events.json"),
                      "spond-sink",
                    )
                  )
                else -> respondError(HttpStatusCode.NotFound)
              }
            }
        )
    val sink = component.club().matchesSubSink
    val timeSource = component.club().timeSource
    val from = timeSource.fromRuntime(Clock.System.now())
    val until = from + 30.days

    val existingMatches =
      sink.listExistingMatches(TeamId("test-team"), from, until).toList().toMap()
    assertEquals(1, existingMatches.size)
    assertEquals("m1234", existingMatches.keys.first())
  }

  test("createMatch creates a new spond event") {
    val requests = mutableListOf<HttpRequestData>()
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          httpClientEngine =
            MockEngine { request ->
              requests.add(request)
              when {
                request.url.fullPath.contains("groups") ->
                  respondJson(
                    Resource.readText(
                      Path("spond/groups.json"),
                      "spond-sink",
                    )
                  )
                request.url.fullPath.contains("locations/autocomplete") ->
                  respondJson(
                    """[{"placeId":"loc1","featureName":"Test Venue","addressLine":"Test Address"}]"""
                  )
                request.url.fullPath.contains("location/loc1") -> {
                  respondJson(
                    """{"featureName":"Test Venue","addressLine":"Test Address","#json":{}}"""
                  )
                }
                request.url.fullPath.endsWith("sponds") && request.method == HttpMethod.Post -> {
                  // Mock response for event creation
                  respondJson(
                    Resource.readText(Path("spond/events.json"), "spond-sink")
                      .trim()
                      .removePrefix("[")
                      .removeSuffix("]")
                  )
                }
                else -> respondError(HttpStatusCode.NotFound)
              }
            }
        )
    val sink = component.club().matchesSubSink
    val timeSource = component.club().timeSource

    val testTeam = Team(id = TeamId("test-team"), name = "Test Team")
    val opponentTeam = Team(id = TeamId("opponent-team"), name = "Opponent Team")
    val venue = Venue(address = "Test Address", alternativeAddress = null)

    val match =
      Match(
        source = "test",
        triangle = TriangleId("triangle1"),
        id = "m5678",
        order = 1u,
        title = "Match Title",
        venue = venue,
        start = timeSource.fromRuntime(Clock.System.now() + 10.days),
        end = timeSource.fromRuntime(Clock.System.now() + 10.days + 2.hours),
        teamA = testTeam,
        teamB = opponentTeam,
        result = null,
        lastUpdated = timeSource.now(),
      )
    val triangle =
      Triangle(
        id = TriangleId("triangle1"),
        venue = venue,
        start = match.start,
        end = match.end,
        host = testTeam,
        teams = Triple(testTeam, opponentTeam, Team(id = TeamId("team3"), name = "Team 3")),
        matches = Triple(match, match.copy(id = "m2222"), match.copy(id = "m3333")),
      )

    sink.createMatch(triangle, match, testTeam)

    assertTrue(requests.any { it.url.fullPath.endsWith("sponds") && it.method == HttpMethod.Post })
  }

  test("updateMatch updates an existing spond event") {
    val requests = mutableListOf<HttpRequestData>()
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          httpClientEngine =
            MockEngine { request ->
              requests.add(request)
              when {
                request.url.fullPath.contains("groups") ->
                  respondJson(
                    Resource.readText(
                      Path("spond/groups.json"),
                      "spond-sink",
                    )
                  )
                request.url.fullPath.contains("locations/autocomplete") -> {
                  respondJson(
                    """[{"placeId":"loc1","featureName":"Test Venue","addressLine":"Test Address"}]"""
                  )
                }
                request.url.fullPath.contains("location/loc1") -> {
                  respondJson(
                    """{"featureName":"Test Venue","addressLine":"Test Address","#json":{}}"""
                  )
                }
                request.url.fullPath.contains("sponds/event1") &&
                  request.method == HttpMethod.Post -> {
                  // Mock response for event update
                  respondJson(
                    Resource.readText(Path("spond/events.json"), "spond-sink")
                      .trim()
                      .removePrefix("[")
                      .removeSuffix("]")
                  )
                }
                else -> respondError(HttpStatusCode.NotFound)
              }
            }
        )
    val sink = component.club().matchesSubSink
    val timeSource = component.club().timeSource

    val testTeam = Team(id = TeamId("test-team"), name = "Test Team")
    val opponentTeam = Team(id = TeamId("opponent-team"), name = "Opponent Team")
    val venue = Venue(address = "Test Address", alternativeAddress = null)

    val match =
      Match(
        source = "test",
        triangle = TriangleId("triangle1"),
        id = "m1234", // matches ID in events.json (after take(5))
        order = 1u,
        title = "Updated Match Title",
        venue = venue,
        start = timeSource.fromRuntime(Clock.System.now() + 10.days),
        end = timeSource.fromRuntime(Clock.System.now() + 10.days + 2.hours),
        teamA = testTeam,
        teamB = opponentTeam,
        result = null,
        lastUpdated = timeSource.now(),
      )
    val triangle =
      Triangle(
        id = TriangleId("triangle1"),
        venue = venue,
        start = match.start,
        end = match.end,
        host = testTeam,
        teams = Triple(testTeam, opponentTeam, Team(id = TeamId("team3"), name = "Team 3")),
        matches = Triple(match, match.copy(id = "m2222"), match.copy(id = "m3333")),
      )

    val existingContent = Resource.readText(Path("spond/events.json"), "spond-sink")
    val existingEvent = component.json.decodeFromString<List<Event>>(existingContent).first()

    sink.updateMatch(triangle, match, testTeam, existingEvent)

    assertTrue(
      requests.any { it.url.fullPath.contains("sponds/event1") && it.method == HttpMethod.Post }
    )
  }

  test("updateMatch with results updates match score") {
    val requests = mutableListOf<HttpRequestData>()
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          httpClientEngine =
            MockEngine { request ->
              requests.add(request)
              when {
                request.url.fullPath.contains("groups") ->
                  respondJson(
                    Resource.readText(
                      Path("spond/groups.json"),
                      "spond-sink",
                    )
                  )
                request.url.fullPath.contains("locations/autocomplete") -> {
                  respondJson(
                    """[{"placeId":"loc1","featureName":"Test Venue","addressLine":"Test Address"}]"""
                  )
                }
                request.url.fullPath.contains("location/loc1") -> {
                  respondJson(
                    """{"featureName":"Test Venue","addressLine":"Test Address","#json":{}}"""
                  )
                }
                request.url.fullPath.contains("sponds/event1/matchUpdate") &&
                  request.method == HttpMethod.Post ->
                  respondJson(
                    Resource.readText(
                        Path("spond/events.json"),
                        "spond-sink",
                      )
                      .trim()
                      .removePrefix("[")
                      .removeSuffix("]")
                  )
                request.url.fullPath.contains("sponds/event1") &&
                  request.method == HttpMethod.Post -> {
                  respondJson(
                    Resource.readText(Path("spond/events.json"), "spond-sink")
                      .trim()
                      .removePrefix("[")
                      .removeSuffix("]")
                  )
                }
                else -> respondError(HttpStatusCode.NotFound)
              }
            }
        )
    val sink = component.club().matchesSubSink
    val timeSource = component.club().timeSource

    val testTeam = Team(id = TeamId("test-team"), name = "Test Team")
    val opponentTeam = Team(id = TeamId("opponent-team"), name = "Opponent Team")
    val venue = Venue(address = "Test Address", alternativeAddress = null)

    val match =
      Match(
        source = "test",
        triangle = TriangleId("triangle1"),
        id = "m1234",
        order = 1u,
        title = "Updated Match Title",
        venue = venue,
        start = timeSource.fromRuntime(Clock.System.now() + 10.days),
        end = timeSource.fromRuntime(Clock.System.now() + 10.days + 2.hours),
        teamA = testTeam,
        teamB = opponentTeam,
        result =
          Match.Result(
            sets = 2u,
            teamA = Match.TeamResult(sets = 2u, scores = listOf(25u, 25u)),
            teamB = Match.TeamResult(sets = 0u, scores = listOf(10u, 10u)),
          ),
        lastUpdated = timeSource.now(),
      )
    val triangle =
      Triangle(
        id = TriangleId("triangle1"),
        venue = venue,
        start = match.start,
        end = match.end,
        host = testTeam,
        teams = Triple(testTeam, opponentTeam, Team(id = TeamId("team3"), name = "Team 3")),
        matches = Triple(match, match.copy(id = "m2222"), match.copy(id = "m3333")),
      )

    val existingContent = Resource.readText(Path("spond/events.json"), "spond-sink")
    val existingEvent = component.json.decodeFromString<List<Event>>(existingContent).first()

    sink.updateMatch(triangle, match, testTeam, existingEvent)

    assertTrue(
      requests.any {
        it.url.fullPath.contains("sponds/event1/matchUpdate") && it.method == HttpMethod.Post
      }
    )
  }

  test("cancelMatch cancels an existing spond event") {
    val requests = mutableListOf<HttpRequestData>()
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          httpClientEngine =
            MockEngine { request ->
              requests.add(request)
              when {
                request.url.fullPath.contains("sponds/event1") &&
                  request.method == HttpMethod.Delete -> {
                  respondOk()
                }
                else -> respondError(HttpStatusCode.NotFound)
              }
            }
        )
    val sink = component.club().matchesSubSink

    val existingContent = Resource.readText(Path("spond/events.json"), "spond-sink")
    val existingEvent = component.json.decodeFromString<List<Event>>(existingContent).first()

    sink.cancelMatch(TeamId("test-team"), existingEvent)

    assertTrue(
      requests.any { it.url.fullPath.contains("sponds/event1") && it.method == HttpMethod.Delete }
    )
  }
}
