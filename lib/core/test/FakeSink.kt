package dev.petuska.spond.sync.core

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle

class FakeSink(
  private val onSyncTeam: (team: Team, from: Time, until: Time, triangles: List<Triangle>) -> Unit =
    { _, _, _, _ ->
    }
) : DataSink {
  constructor(
    logger: Logger
  ) : this(
    onSyncTeam = { team, from, until, triangles ->
      logger.i("onSyncTeam(team=$team, from=$from, until=$until, triangles=$triangles)")
    }
  )

  override suspend fun syncTeam(team: Team, from: Time, until: Time, triangles: List<Triangle>) {
    onSyncTeam(team, from, until, triangles)
  }
}
