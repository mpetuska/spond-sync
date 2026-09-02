package dev.petuska.spond.sync.runtime.di

import dev.petuska.spond.sync.runtime.config.Config
import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig
import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig.Events
import dev.petuska.spond.sync.runtime.model.TeamId
import dev.petuska.spond.sync.runtime.model.Time
import dev.petuska.spond.sync.runtime.sink.spond.MatchesSpondSink
import dev.petuska.spond.sync.runtime.source.volleyzone.BvaDataSource
import dev.petuska.spond.sync.runtime.util.TimeSource
import dev.petuska.spond.sync.spond.SpondCredentials
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.serialization.json.Json

@DependencyGraph(AppScope::class)
interface TestGraph {
  val json: Json
  val bvaDataSource: BvaDataSource

  @Named("source") val seasonStartAtSource: Instant
  val timeSource: TimeSource
  val seasonStart: Time
    get() = timeSource.fromSource(seasonStartAtSource)

  @Provides
  @SingleIn(AppScope::class)
  fun spondConfig(config: Config): SpondSinkConfig = config.spond

  @Provides
  @SingleIn(AppScope::class)
  fun json(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
    allowTrailingComma = true
    allowComments = true
    isLenient = true
  }

  @Provides fun httpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine)

  @Provides @Named("dry") fun dry(): Boolean = false
  @Provides @Named("addresses") fun addresses(config: Config): Map<String, String> = config.addresses

  @Provides
  @Named("source")
  fun sourceOffset(@Named("source") seasonStart: Instant): Duration =
    Clock.System.now().daysUntil(seasonStart, TimeZone.UTC).days

  @Provides @Named("sink") fun sinkOffset(): Duration = Duration.ZERO

  @DependencyGraph.Factory
  interface Factory {
    fun create(
      @Provides
      @Named("source")
      seasonStartAtSource: Instant = Instant.parse("2024-09-01T00:00:00Z"),
      @Provides
      httpClientEngine: HttpClientEngine = MockEngine { respondError(HttpStatusCode.NotFound) },
      @Provides
      config: Config =
        Config(
          spond =
            SpondSinkConfig(
              group = "Test Group",
              api = SpondCredentials("test", "test"),
              subGroups =
                mapOf(
                  "Test Team" to
                    SpondSinkConfig.SubGroupConfig(
                      team = TeamId("test-team"),
                      sources = emptyList(),
                      hosts = emptyList(),
                      events = Events(),
                    )
                ),
            )
        ),
    ): TestGraph
  }
}
