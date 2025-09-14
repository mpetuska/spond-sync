package core

import core.model.Match
import core.model.MatchId
import core.model.Team
import core.model.TeamId
import core.model.Time
import core.model.Triangle
import kotlinx.coroutines.flow.Flow

interface DataSink<out M> {
  fun listExistingMatches(team: TeamId, from: Time, until: Time): Flow<Pair<MatchId, M>>

  suspend fun updateMatch(triangle: Triangle, match: Match, team: Team, existing: @UnsafeVariance M)

  suspend fun createMatch(triangle: Triangle, match: Match, team: Team)
}
