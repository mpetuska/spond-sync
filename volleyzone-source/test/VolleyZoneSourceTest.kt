package volleyzone.source

import assertk.assertThat
import assertk.assertions.isEqualTo
import core.util.Instant
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.utils.io.readText
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import testing.Resource
import volleyzone.source.di.TestApp
import volleyzone.source.di.build

class VolleyZoneSourceTest {
  @Test
  fun check2024Season() = runTest {
    val component =
      TestApp::class.build(
        seasonStart = Instant.parse("2024-09-01T00:00:00Z"),
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

    assertThat(matches::size).isEqualTo(404)
  }
}
