package core.util

import kotlin.time.Instant as KInstant

/** Temporary wrapper over [kotlin.time.Instant] until kotlinx.datetime migration settles... */
class Instant(val value: KInstant) : Comparable<KInstant> by value {
  companion object {
    fun parse(string: String): Instant = Instant(KInstant.parse(string))
  }
}
