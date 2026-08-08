@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION", "DEPRECATION")

package core.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * An [Instant] in time adjusted for different time domains.
 *
 * @property raw the raw [Instant] used to build this time. It represents the real world time value.
 * @property atRuntime adjusted time for the runtime domain.
 * @property atSource adjusted time for the source domain.
 * @property atSink adjusted time for the sink domain.
 */
data class Time(
  val raw: Instant,
  val atRuntime: Instant,
  val atSource: Instant,
  val atSink: Instant,
) : Comparable<Time> {
  override fun compareTo(other: Time): Int = atRuntime.compareTo(other.atRuntime)

  override fun toString() = "$raw"

  fun toFullString() = "raw=$raw,atSource=$atSource,atRuntime=$atRuntime,atSink=$atSink"

  operator fun plus(other: Duration) =
    Time(
      raw = raw + other,
      atRuntime = atRuntime + other,
      atSource = atSource + other,
      atSink = atSink + other,
    )

  operator fun minus(other: Duration) =
    Time(
      raw = raw - other,
      atRuntime = atRuntime - other,
      atSource = atSource - other,
      atSink = atSink - other,
    )
}
