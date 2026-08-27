package dev.petuska.spond.sync.core

import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle

interface DataSink {
  /**
   * Synchronise team events.
   *
   * @param team the team to sync
   * @param from the start of the time range to sync
   * @param until the end of the time range to sync
   * @param triangles the relevant triangles to sync
   */
  suspend fun syncTeam(
    team: Team,
    from: Time,
    until: Time,
    triangles: List<Triangle>,
  )
}
