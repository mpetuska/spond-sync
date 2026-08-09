package dev.petuska.spond.sync.cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import dev.petuska.spond.sync.config.ClubGraph
import dev.petuska.spond.sync.core.di.Sink
import dev.petuska.spond.sync.core.di.Source
import dev.petuska.spond.sync.utils.Named
import dev.petuska.spond.sync.volleyzone.source.VolleyZoneSourceConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import kotlin.time.Duration
import kotlinx.serialization.json.Json

@DependencyGraph(scope = AppScope::class)
actual interface AppGraph : ClubGraph.Factory {
  actual val logger: Logger

  @Provides
  @SingleIn(AppScope::class)
  fun httpClient(): HttpClient =
    HttpClient(Curl) {
      engine {
        //        endpoint {
        //          pipelineMaxSize = 2
        //          maxConnectionsPerRoute = 2
        //        }
      }
    }

  @DependencyGraph.Factory
  actual interface Factory {
    actual fun create(
      @Provides volleyZoneConfig: VolleyZoneSourceConfig,
      @Provides @Source sourceOffset: Duration,
      @Provides @Sink sinkOffset: Duration,
      @Provides severity: Severity,
      @Provides @Named(value = "gitHubCi") gitHubCi: Boolean,
      @Provides json: Json,
      @Provides @Named(value = "dry") dry: Boolean,
    ): AppGraph
  }
}
