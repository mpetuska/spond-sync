package cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import core.util.ColourLogFormatter
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import utils.Named
import kotlin.coroutines.CoroutineContext

@ContributesTo(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent {
  @Provides
  @SingleIn(AppScope::class)
  fun messageStringFormatter(@Named("gitHubCi") gitHubCi: Boolean): MessageStringFormatter =
    if (gitHubCi) GHAFormatter(ColourLogFormatter()) else ColourLogFormatter()

  @Provides
  @SingleIn(AppScope::class)
  fun logger(minSeverity: Severity, logFormatter: MessageStringFormatter): Logger =
    Logger(
      config = loggerConfigInit(platformLogWriter(logFormatter), minSeverity = minSeverity),
      tag = "SpondSync",
    )

  @Provides
  fun httpClientConfig(
    baseLogger: Logger,
    severity: Severity,
    @Named("gitHubCi") gitHubCi: Boolean,
  ): HttpClientConfig<*>.() -> Unit = {
    install(Logging) {
      logger =
        object : io.ktor.client.plugins.logging.Logger {
          private val log = baseLogger.withTag("KTOR")

          override fun log(message: String) {
            log.v(message)
          }
        }
      level =
        when (severity) {
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
