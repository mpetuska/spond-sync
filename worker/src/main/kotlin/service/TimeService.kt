package worker.service

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import worker.WorkerConfig
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

class TimeService @Inject constructor(
  config: WorkerConfig,
) {
  private val offset = (365 * config.yearOffset).days

  fun now(): Instant = realNow() + offset

  fun realNow(): Instant = Clock.System.now()

  fun offset(time: Instant): Instant = time + offset

  fun reset(time: Instant): Instant = time - offset

  fun offset(time: LocalDateTime): LocalDateTime = (time.toInstant(TimeZone.UTC) + offset).toLocalDateTime(TimeZone.UTC)

  fun reset(time: LocalDateTime): LocalDateTime = (time.toInstant(TimeZone.UTC) - offset).toLocalDateTime(TimeZone.UTC)
}
