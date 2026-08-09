package dev.petuska.spond.sync.core

import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.di.Sink
import dev.petuska.spond.sync.core.di.Source
import dev.petuska.spond.sync.core.model.Time
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * @property sourceOffset a duration to offset all time values coming from [DataSource] to a runtime
 *   time.
 * @property sinkOffset a duration to offset all time values going to [DataSink] to a runtime time.
 */
@Inject
@SingleIn(ClubScope::class)
class TimeSource(
  @Source private val sourceOffset: Duration = Duration.ZERO,
  @Sink private val sinkOffset: Duration = Duration.ZERO,
) {

  fun now(): Time = fromRuntime(Clock.System.now())

  fun fromSource(real: Instant): Time {
    val atRuntime = real - sourceOffset
    return Time(raw = real, atRuntime = atRuntime, atSource = real, atSink = atRuntime + sinkOffset)
  }

  fun fromSink(real: Instant): Time {
    val atRuntime = real - sinkOffset
    return Time(
      raw = real,
      atRuntime = atRuntime,
      atSource = atRuntime + sourceOffset,
      atSink = real,
    )
  }

  fun fromRuntime(real: Instant): Time =
    Time(raw = real, atRuntime = real, atSource = real + sourceOffset, atSink = real + sinkOffset)
}
