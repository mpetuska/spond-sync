package dev.petuska.spond.sync.spond.sink.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.petuska.spond.sync.core.di.Source
import dev.petuska.spond.sync.core.util.ColourLogFormatter
import dev.petuska.spond.sync.spond.SpondCredentials
import dev.petuska.spond.sync.spond.sink.SpondSinkConfig
import dev.petuska.spond.sync.testing.TestLogWriter
import dev.petuska.spond.sync.utils.Named
import dev.petuska.spond.sync.core.model.TeamId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import kotlinx.serialization.json.Json

@DependencyGraph(AppScope::class)
interface TestGraph : ClubGraph.Factory {
  val logger: Logger
  val json: Json

  @Provides
  @SingleIn(AppScope::class)
  fun spondConfig(): SpondSinkConfig =
    SpondSinkConfig(
      group = "Test Group",
      api = SpondCredentials("test", "test"),
      subGroups = mapOf("Test Team" to TeamId("test-team")),
    )

  @Provides
  @SingleIn(AppScope::class)
  fun logger(minSeverity: Severity, logFormatter: MessageStringFormatter): Logger =
    Logger(
      config =
        loggerConfigInit(TestLogWriter, platformLogWriter(logFormatter), minSeverity = minSeverity),
      tag = "Test",
    )

  @Provides @SingleIn(AppScope::class) fun json(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  @Provides fun httpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine)

  @Provides @Named("dry") fun dry(): Boolean = false

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
