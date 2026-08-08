package cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import config.ClubGraph
import core.di.Sink
import core.di.Source
import core.util.Duration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import kotlinx.serialization.json.Json
import utils.Named
import volleyzone.source.VolleyZoneSourceConfig

@DependencyGraph(AppScope::class)
actual interface AppGraph : ClubGraph.Factory {
  actual val logger: Logger

  @Provides
  @SingleIn(AppScope::class)
  fun httpClient(): HttpClient {
    return HttpClient(CIO) {
      engine {
        endpoint {
          pipelineMaxSize = 2
          maxConnectionsPerRoute = 2
        }
      }
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
