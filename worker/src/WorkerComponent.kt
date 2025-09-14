package worker

import co.touchlab.kermit.*
import co.touchlab.kermit.Logger as KerLogger
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.SpondCredentials
import sportpress.SportpressCredentials
import utils.Named
import utils.tokens.FileTokenHandler
import utils.tokens.TokenHandler
import worker.source.EventSource
import worker.source.SportpressEventSource
import worker.source.VolleyZoneEventSource
import worker.util.exit
import kotlin.reflect.KClass

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class WorkerComponent(
  @get:Provides val config: WorkerConfig,
  @get:Provides val logSeverity: Severity,
  @get:Provides val logFormatter: MessageStringFormatter,
) {
  abstract val worker: SyncWorker

  @Provides
  fun logger(severity: Severity, logFormatter: MessageStringFormatter): KerLogger =
    KerLogger(
      config = loggerConfigInit(platformLogWriter(logFormatter), minSeverity = severity),
      tag = "Global",
    )

  @Provides fun spondConfig(config: WorkerConfig): WorkerConfig.Spond = config.spond

  @Provides
  fun sportpressConfig(config: WorkerConfig): WorkerConfig.Sportpress =
    checkNotNull(config.sportpress)

  @Provides
  fun volleyzoneConfig(config: WorkerConfig): WorkerConfig.Volleyzone =
    checkNotNull(config.volleyzone)

  @Provides
  @SingleIn(AppScope::class)
  fun spondCredentials(config: WorkerConfig.Spond): SpondCredentials =
    SpondCredentials(username = config.username, password = config.password, apiUrl = config.apiUrl)

  @Provides
  @SingleIn(AppScope::class)
  fun sportpressCredentials(config: WorkerConfig.Sportpress): SportpressCredentials =
    SportpressCredentials(apiUrl = config.apiUrl)

  @Provides
  @Named("spond")
  @SingleIn(AppScope::class)
  fun spondTokenHandler(json: Json, credentials: WorkerConfig.Spond): TokenHandler =
    FileTokenHandler(json = json, key = "${credentials.hashCode()}")

  @Provides
  @SingleIn(AppScope::class)
  fun eventSource(
    config: WorkerConfig,
    sportpress: () -> SportpressEventSource,
    volleyzone: () -> VolleyZoneEventSource,
    logger: co.touchlab.kermit.Logger,
  ): EventSource {
    return when (config.source) {
      "sportpress" -> sportpress()
      "volleyzone" -> volleyzone()
      else -> logger.exit("Event source ${config.source} is invalid.")
    }
  }

  @Provides
  fun httpClient(
    baseLogger: co.touchlab.kermit.Logger,
    severity: Severity,
    @Named("debug") debug: Boolean,
  ): HttpClient =
    HttpClient(CIO) {
      engine {
        endpoint {
          pipelineMaxSize = 2
          maxConnectionsPerRoute = 2
        }
      }
      install(Logging) {
        logger =
          object : Logger {
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
  @SingleIn(AppScope::class)
  fun json(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  @Named("debug") @Provides fun debug(config: WorkerConfig): Boolean = config.debug
}

@MergeComponent.CreateComponent
expect fun KClass<WorkerComponent>.build(
  config: WorkerConfig,
  logSeverity: Severity,
  logFormatter: MessageStringFormatter,
): WorkerComponent
