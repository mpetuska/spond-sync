package dev.petuska.spond.sync.core

import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.MatchId
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import kotlinx.coroutines.flow.Flow

interface DataSink<out M> {
  fun listExistingMatches(team: TeamId, from: Time, until: Time): Flow<Pair<MatchId, M>>

  suspend fun cancelMatch(team: TeamId, existing: @UnsafeVariance M)

  suspend fun updateMatch(triangle: Triangle, match: Match, team: Team, existing: @UnsafeVariance M)

  suspend fun createMatch(triangle: Triangle, match: Match, team: Team)
}
