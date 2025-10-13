package core

import co.touchlab.kermit.Logger
import core.model.Match
import core.model.MatchId
import core.model.Team
import core.model.TeamId
import core.model.Time
import core.model.Triangle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** A [DataSink] which dumps all updates to [logger]. */
@Inject
@SingleIn(AppScope::class)
class LogSink(logger: Logger) : DataSink<Unit> {
  private val log = logger.withTag("ConsoleSink")

  override fun listExistingMatches(
    team: TeamId,
    from: Time,
    until: Time,
  ): Flow<Pair<MatchId, Unit>> = emptyFlow()

  override suspend fun cancelMatch(team: TeamId, existing: Unit) {
    log.i("Received match cancellation ${team.value}.")
  }

  override suspend fun updateMatch(triangle: Triangle, match: Match, team: Team, existing: Unit) {
    log.i("Received match update ${team.identity}.")
    log.d("Full match update: triangle=$triangle, match=$match, team=$team, existing=$existing")
  }

  override suspend fun createMatch(triangle: Triangle, match: Match, team: Team) {
    log.i("Received match create ${team.identity}.")
    log.d("Full match create: triangle=$triangle, match=$match, team=$team")
  }
}
