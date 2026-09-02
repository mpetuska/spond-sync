package dev.petuska.spond.sync.runtime.source.volleyzone

import co.touchlab.kermit.Logger
import de.infix.testBalloon.framework.core.testSuite
import dev.petuska.spond.sync.runtime.config.source.SourceConfig
import dev.petuska.spond.sync.runtime.di.TestGraph
import dev.petuska.spond.sync.runtime.model.SourceId
import dev.petuska.spond.sync.testing.Resource
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.io.files.Path

val BvaDataSourceTest by testSuite {
  test("Loads matches from VolleyZone") {
    val component =
      createGraphFactory<TestGraph.Factory>()
        .create(
          seasonStartAtSource = Instant.parse("2024-09-01T00:00:00Z"),
          httpClientEngine =
            MockEngine { request ->
              val path = Path("volleyzone/${request.url.fullPath.removeSuffix("/")}.html")
              val content = Resource.readOrNull(path)?.use { it.readText() }
              if (content != null) {
                respondOk(content)
              } else {
                respond("Resource $path not found.", HttpStatusCode.NotFound)
              }
            },
        )
    val log = Logger.withTag("VolleyZoneSourceTest")
    val timeSource = component.timeSource
    val source = component.bvaDataSource

    val from = timeSource.fromRuntime(Clock.System.now())
    val leagues = listOf("197456", "197491", "197768", "197773", "198564", "198601")
    val matches =
      leagues
        .flatMap { league ->
          source
            .listMatches(
              sourceId = SourceId(league),
              config =
                SourceConfig.BVA(url = Url("https://competitions.volleyzone.co.uk/league/$league")),
              from = from,
              until = from + 365.days,
            )
            .onEach { log.v("[$league] $it") }
        }

    assertEquals(404, matches.size)
  }
}
