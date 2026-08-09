package dev.petuska.spond.sync.core.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.petuska.spond.sync.core.FakeSource
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.util.ColourLogFormatter
import dev.petuska.spond.sync.testing.TestLogWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

@DependencyGraph(AppScope::class)
interface TestGraph : ClubGraph.Factory {
  val logger: Logger
  val fakeSource: (Collection<Match>) -> FakeSource

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

  @DependencyGraph.Factory
  interface Factory {
    fun create(
      @Provides @Source seasonStart: Instant = Instant.parse("2024-09-01T00:00:00Z"),
      @Provides severity: Severity = Severity.Warn,
      @Provides logFormatter: MessageStringFormatter = ColourLogFormatter(),
    ): TestGraph
  }
}
