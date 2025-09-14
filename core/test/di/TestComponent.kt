package core.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import core.FakeSource
import core.TimeSource
import core.model.Match
import core.model.Time
import core.util.Duration
import core.util.Instant
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

@SingleIn(AppScope::class)
@ContributesTo(AppScope::class)
interface TestComponent {
  @Provides
  @SingleIn(AppScope::class)
  fun fakeSourceFactory(logger: Logger): (Collection<Match>) -> FakeSource = {
    FakeSource(it, logger)
  }

  @Provides
  @SingleIn(AppScope::class)
  fun logger(minSeverity: Severity, logFormatter: MessageStringFormatter): Logger =
    Logger(
      config =
        loggerConfigInit(TestLogWriter, platformLogWriter(logFormatter), minSeverity = minSeverity),
      tag = "Test",
    )
}

@SingleIn(ClubScope::class)
@ContributesSubcomponent(ClubScope::class)
interface ClubComponent {
  @Source val seasonStartAtSource: Instant
  val timeSource: TimeSource
  val seasonStart: Time
    get() = timeSource.fromSource(seasonStartAtSource.value)

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
