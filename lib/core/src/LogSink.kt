package dev.petuska.spond.sync.core

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** A [DataSink] which dumps all updates to [logger]. */
@Inject
@SingleIn(AppScope::class)
class LogSink(logger: Logger) : DataSink {
  private val log = logger.withTag("ConsoleSink")

  override suspend fun syncTeam(team: Team, from: Time, until: Time, triangles: List<Triangle>) {
    log.i("Received sync request team=${team}, from=$from, until=$until, triangles=$triangles.")
  }
}
