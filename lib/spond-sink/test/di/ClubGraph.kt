package dev.petuska.spond.sync.spond.sink.di

import dev.petuska.spond.sync.core.TimeSource
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.di.Sink
import dev.petuska.spond.sync.core.di.Source
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.spond.sink.subsink.MatchesSubSink
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

@GraphExtension(ClubScope::class)
interface ClubGraph {
  @Source val seasonStartAtSource: Instant
  val timeSource: TimeSource
  val seasonStart: Time
    get() = timeSource.fromSource(seasonStartAtSource)

  val matchesSubSink: MatchesSubSink

  @Provides
  @Source
  fun sourceOffset(@Source seasonStart: Instant): Duration =
    Clock.System.now().daysUntil(seasonStart, TimeZone.UTC).days

  @Provides @Sink fun sinkOffset(): Duration = Duration.ZERO

  @ContributesTo(AppScope::class)
  @GraphExtension.Factory
  interface Factory {
    fun club(): ClubGraph
  }
}
