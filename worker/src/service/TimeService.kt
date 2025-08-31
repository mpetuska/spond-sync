package worker.service

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import worker.WorkerConfig
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TimeService @Inject constructor(
  config: WorkerConfig,
) {
  private val offset = (365 * config.yearOffset).days

  fun offsetNow(): Instant = now() + offset

  fun now(): Instant = Clock.System.now()

  fun offset(time: Instant): Instant = time + offset

  fun reset(time: Instant): Instant = time - offset

  fun offset(time: LocalDateTime): LocalDateTime = (time.toInstant(TimeZone.UTC) + offset).toLocalDateTime(TimeZone.UTC)

  fun reset(time: LocalDateTime): LocalDateTime = (time.toInstant(TimeZone.UTC) - offset).toLocalDateTime(TimeZone.UTC)
}
