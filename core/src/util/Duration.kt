package core.util

import kotlin.time.Duration as KDuration

/** Temporary wrapper over [kotlin.time.Duration] until kotlinx.datetime migration settles... */
class Duration(val value: KDuration) : Comparable<KDuration> by value {
  operator fun plus(other: Duration) = Duration(value + other.value)

  operator fun minus(other: Duration) = Duration(value - other.value)

  companion object {
    val ZERO = Duration(KDuration.ZERO)
  }
}
