package volleyzone.source.di

import core.TimeSource
import core.di.ClubScope
import core.di.Sink
import core.di.Source
import core.model.Time
import core.util.Duration
import core.util.Instant
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import volleyzone.source.VolleyZoneSource

@GraphExtension(ClubScope::class)
interface ClubGraph {
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

  @ContributesTo(AppScope::class)
  @GraphExtension.Factory
  interface Factory {
    fun club(): ClubGraph
  }
}
