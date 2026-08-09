package volleyzone.source.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.petuska.spond.sync.core.di.Source
import dev.petuska.spond.sync.core.util.ColourLogFormatter
import dev.petuska.spond.sync.testing.TestLogWriter
import dev.petuska.spond.sync.volleyzone.source.VolleyZoneSourceConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.time.Instant

@DependencyGraph(AppScope::class)
interface TestGraph : ClubGraph.Factory {
  val logger: Logger

  @Provides
  @SingleIn(AppScope::class)
  fun volleyZoneConfig(): VolleyZoneSourceConfig =
    VolleyZoneSourceConfig(
      leagues =
        mapOf(
          "Mens Div 1" to Url("https://competitions.volleyzone.co.uk/league/197456/"),
          "Mens Div 2" to Url("https://competitions.volleyzone.co.uk/league/197491/"),
          "Mixed Div 1" to Url("https://competitions.volleyzone.co.uk/league/197768/"),
          "Mixed Div 2" to Url("https://competitions.volleyzone.co.uk/league/197773/"),
          "Womens Div 1" to Url("https://competitions.volleyzone.co.uk/league/198564/"),
          "Womens Div 2" to Url("https://competitions.volleyzone.co.uk/league/198601/"),
        ),
      addresses = mapOf(),
    )

  @Provides
  @SingleIn(AppScope::class)
  fun logger(minSeverity: Severity, logFormatter: MessageStringFormatter): Logger =
    Logger(
      config =
        loggerConfigInit(TestLogWriter, platformLogWriter(logFormatter), minSeverity = minSeverity),
      tag = "Test",
    )

  @Provides fun httpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine)

  @DependencyGraph.Factory
  interface Factory {
    fun create(
      @Provides @Source seasonStartAtSource: Instant = Instant.parse("2024-09-01T00:00:00Z"),
      @Provides severity: Severity = Severity.Warn,
      @Provides logFormatter: MessageStringFormatter = ColourLogFormatter(),
      @Provides
      httpClientEngine: HttpClientEngine = MockEngine { respondError(HttpStatusCode.NotFound) },
    ): TestGraph
  }
}
