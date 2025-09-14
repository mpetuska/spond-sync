package cli

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesTo(AppScope::class)
interface NativeComponent {
  @Provides
  @SingleIn(AppScope::class)
  fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Curl) {
      engine {
        //        endpoint {
        //          pipelineMaxSize = 2
        //          maxConnectionsPerRoute = 2
        //        }
      }
      config()
    }
}
