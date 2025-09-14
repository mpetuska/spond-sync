package volleyzone.source.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import core.TimeSource
import core.di.ClubScope
import core.di.Sink
import core.di.Source
import core.model.Time
import core.util.Duration
import core.util.Instant
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.Url
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import testing.TestLogWriter
import volleyzone.source.VolleyZoneSource
import volleyzone.source.VolleyZoneSourceConfig

@SingleIn(AppScope::class)
@ContributesTo(AppScope::class)
interface TestComponent {
  @Provides
  @SingleIn(AppScope::class)
  fun volleyZoneConfig() =
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
}

@SingleIn(ClubScope::class)
@ContributesSubcomponent(ClubScope::class)
interface ClubComponent {
  @Source val seasonStartAtSource: Instant
  val timeSource: TimeSource
  val seasonStart: Time
    get() = timeSource.fromSource(seasonStartAtSource.value)

  val volleyZoneSource: VolleyZoneSource

  @Provides
  @Source
  fun sourceOffset(@Source seasonStart: Instant): Duration =
    Clock.System.now().daysUntil(seasonStart.value, TimeZone.UTC).days.let(::Duration)

  @Provides @Sink fun sinkOffset(): Duration = Duration.ZERO

  @ContributesSubcomponent.Factory(AppScope::class)
  interface Factory {
    fun club(): ClubComponent
  }
}
