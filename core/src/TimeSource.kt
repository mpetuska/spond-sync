@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION", "DEPRECATION")

package core

import core.di.ClubScope
import core.di.Sink
import core.di.Source
import core.model.Time
import core.util.Duration
import kotlin.time.Clock
import kotlin.time.Instant
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * @property sourceOffset a duration to offset all time values coming from [DataSource] to a runtime
 *   time.
 * @property sinkOffset a duration to offset all time values going to [DataSink] to a runtime time.
 */
@Inject
@SingleIn(ClubScope::class)
class TimeSource(
  @Source sourceOffset: Duration = Duration.ZERO,
  @Sink sinkOffset: Duration = Duration.ZERO,
) {
  private val sourceOffset = sourceOffset.value
  private val sinkOffset = sinkOffset.value

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
