package dev.petuska.spond.sync.cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import dev.petuska.spond.sync.runtime.config.Config
import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig
import dev.petuska.spond.sync.spond.SpondCredentials
import dev.petuska.spond.sync.utils.tokens.MemoryTokenHandler
import dev.petuska.spond.sync.utils.tokens.TokenHandler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

@ContributesTo(AppScope::class)
interface AppModule {
  @Provides fun spondSinkConfig(config: Config): SpondSinkConfig = config.spond

  @Provides fun spondCredentials(config: SpondSinkConfig): SpondCredentials = config.api

  @Provides
  @Named("spond")
  fun spondTokenHandler(json: Json, config: SpondSinkConfig): TokenHandler {
    //    return FileTokenHandler(json, config.api.toString())
    return MemoryTokenHandler
  }

  @Provides
  @Named("addresses")
  fun addresses(config: Config): Map<String, String> = config.addresses

  @Provides
  fun httpClientConfig(): HttpClientConfig<*>.() -> Unit = {
    install(Logging) {
      logger =
        object : io.ktor.client.plugins.logging.Logger {
          private val log = Logger.withTag("KTOR")

          override fun log(message: String) {
            log.v(message)
          }
        }
      level =
        when (Logger.config.minSeverity) {
          Severity.Verbose -> LogLevel.ALL
          Severity.Debug -> LogLevel.INFO
          else -> LogLevel.NONE
        }
    }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun coroutineContext(): CoroutineContext = Dispatchers.Default + CoroutineName("SpondSync")
}
