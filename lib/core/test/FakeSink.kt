package dev.petuska.spond.sync.core

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.MatchId
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeSink<M>(
  private val onListExistingMatches:
    (team: TeamId, from: Time, until: Time) -> Flow<Pair<MatchId, M>> =
    { _, _, _ ->
      emptyFlow()
    },
  private val onUpdateMatch: (triangle: Triangle, match: Match, team: Team, existing: M) -> Unit =
    { _, _, _, _ ->
    },
  private val onCreateMatch: (triangle: Triangle, match: Match, team: Team) -> Unit = { _, _, _ ->
  },
  private val onCancelMatch: (team: TeamId, existing: M) -> Unit = { _, _ -> },
) : DataSink<M> {
  constructor(
    logger: Logger
  ) : this(
    onUpdateMatch = { triangle, match, team, existing ->
      logger.i("onUpdateMatch(triangle=$triangle, match=$match, team=$team, existing=$existing)")
    },
    onCreateMatch = { triangle, match, team ->
      logger.i("onCreateMatch(triangle=$triangle, match=$match, team=$team)")
    },
    onListExistingMatches = { team, from, until ->
      logger.i("onListExistingMatches(team=$team, from=$from, until=$until)")
      emptyFlow()
    },
  )

  override fun listExistingMatches(team: TeamId, from: Time, until: Time): Flow<Pair<MatchId, M>> =
    onListExistingMatches(team, from, until)

  override suspend fun cancelMatch(team: TeamId, existing: M) {
    onCancelMatch(team, existing)
  }

  override suspend fun updateMatch(triangle: Triangle, match: Match, team: Team, existing: M) {
    onUpdateMatch(triangle, match, team, existing)
  }

  override suspend fun createMatch(triangle: Triangle, match: Match, team: Team) {
    onCreateMatch(triangle, match, team)
  }
}
