package volleyzone.source

import core.util.Instant
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.utils.io.readText
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.io.files.Path
import testing.Resource
import volleyzone.source.di.TestGraph

val VolleyZoneSourceTest by testSuite {
  test("Loads matches from VolleyZone") {
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          seasonStartAtSource = Instant.parse("2024-09-01T00:00:00Z"),
          httpClientEngine =
            MockEngine { request ->
              val path = Path("volleyzone/${request.url.fullPath.removeSuffix("/")}.html")
              val content = Resource.read(path)?.use { it.readText() }
              if (content != null) {
                respondOk(content)
              } else {
                respond("Resource $path not found.", HttpStatusCode.NotFound)
              }
            },
        )
    val log = component.logger.withTag("VolleyZoneSourceTest")
    val timeSource = component.club().timeSource
    val source = component.club().volleyZoneSource

    val from = timeSource.fromRuntime(Clock.System.now())
    val matches =
      source.listMatches(from = from, until = from + 365.days).onEach { log.v("$it") }.toList()

    assertEquals(404, matches.size)
  }
}
