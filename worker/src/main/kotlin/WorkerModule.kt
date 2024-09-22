package worker

import co.touchlab.kermit.Severity
import dagger.Lazy
import dagger.Module
import dagger.Provides
import io.ktor.client.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import kotlinx.serialization.json.Json
import spond.SpondCredentials
import sportpress.SportpressCredentials
import utils.tokens.FileTokenHandler
import utils.tokens.TokenHandler
import worker.source.EventSource
import worker.source.SportpressEventSource
import worker.source.VolleyZoneEventSource
import worker.util.exit
import javax.inject.Named

@Module
interface WorkerModule {
  companion object {
    @Provides
    fun spondConfig(config: WorkerConfig): WorkerConfig.Spond = config.spond

    @Provides
    fun sportpressConfig(config: WorkerConfig): WorkerConfig.Sportpress = checkNotNull(config.sportpress)

    @Provides
    fun volleyzoneConfig(config: WorkerConfig): WorkerConfig.Volleyzone = checkNotNull(config.volleyzone)

    @Provides
    fun eventSource(
      config: WorkerConfig,
      sportpress: Lazy<SportpressEventSource>,
      volleyzone: Lazy<VolleyZoneEventSource>,
      logger: co.touchlab.kermit.Logger,
    ): EventSource {
      return when (config.source) {
        "sportpress" -> sportpress.get()
        "volleyzone" -> volleyzone.get()
        else -> logger.exit("Event source ${config.source} is invalid.")
      }
    }

    @Provides
    fun spondCredentials(config: WorkerConfig.Spond): SpondCredentials = SpondCredentials(
      username = config.username,
      password = config.password,
      apiUrl = config.apiUrl,
    )

    @Provides
    fun sportpressCredentials(config: WorkerConfig.Sportpress): SportpressCredentials = SportpressCredentials(
      apiUrl = config.apiUrl,
    )

    @Provides
    fun httpClient(
      baseLogger: co.touchlab.kermit.Logger,
      severity: Severity,
      @Named("debug") debug: Boolean,
    ): HttpClient = HttpClient {
      install(Logging) {
        logger = object : Logger {
          private val log = baseLogger.withTag("KTOR")

          override fun log(message: String) {
            log.v(message)
          }
        }
        level =
          when {
            severity > Severity.Verbose -> LogLevel.NONE
            debug -> LogLevel.ALL
            else -> LogLevel.ALL
          }
      }
    }

    @Provides
    fun json(): Json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }

    @Named("debug")
    @Provides
    fun debug(config: WorkerConfig): Boolean = config.debug

    @Provides
    fun spondTokenHandler(json: Json, credentials: WorkerConfig.Spond): TokenHandler = FileTokenHandler(
      json = json,
      key = "${credentials.hashCode()}",
    )
  }
}


