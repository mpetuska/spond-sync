package dev.petuska.spond.sync.spond.sink.subsink

import dev.petuska.spond.sync.core.DataSink
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.spond.sink.service.SpondService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(ClubScope::class)
class BothSubSink(
  private val spondService: SpondService,
  private val matchesSubSink: MatchesSubSink,
  private val trianglesSubSink: TrianglesSubSink,
) : DataSink {
  init {
    TODO("BothSubSink not supported yet!")
  }

  override suspend fun syncTeam(team: Team, from: Time, until: Time, triangles: List<Triangle>) {
    TODO("Not yet implemented")
  }
}
