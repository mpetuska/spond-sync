package dev.petuska.spond.sync.cli.config

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl

@ContributesTo(scope = AppScope::class)
actual interface PlatformModule {
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
}
