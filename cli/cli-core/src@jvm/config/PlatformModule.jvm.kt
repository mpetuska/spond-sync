package dev.petuska.spond.sync.cli.config

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint

@ContributesTo(AppScope::class)
interface PlatformModule {
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
}
