package spond.sink

import co.touchlab.kermit.Logger
import core.DataSink
import core.TimeSource
import core.di.ClubScope
import core.model.Match
import core.model.MatchId
import core.model.Team
import core.model.TeamId
import core.model.Time
import core.model.Triangle
import io.ktor.client.plugins.ClientRequestException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.Spond
import spond.data.event.Event
import spond.data.event.MatchScore
import spond.data.group.Group
import spond.data.group.SubGroup
import spond.data.group.SubGroupName
import spond.sink.service.EventBuilderService
import utils.Named

@Inject
@SingleIn(ClubScope::class)
class SpondSink(
  private val client: Spond,
  private val config: SpondSinkConfig,
  private val timeSource: TimeSource,
  private val eventBuilderService: EventBuilderService,
  private val subGroups: Map<TeamId, SubGroupName>,
  @Named("dry") private val dry: Boolean,
  logger: Logger,
) : DataSink<Event> {
  private val log = logger.withTag("SpondSink")
  private val groupFetching = AtomicBoolean(false)
  private var group: Deferred<Group>? = null

  private suspend fun getGroup(): Group {
    if (groupFetching.compareAndSet(expectedValue = false, newValue = true) && group == null) {
      val result = CompletableDeferred<Group>()
      this.group = result
      val group =
        client
          .listGroups()
          .onEach { log.v("Found group ${it.identity}") }
          .firstOrNull { it.name == config.group }
      checkNotNull(group) { "Unable to find Spond group \"${config.group}\"" }
      result.complete(group)
      return group
    } else {
      return checkNotNull(group).await()
    }
  }

  override fun listExistingMatches(
    team: TeamId,
    from: Time,
    until: Time,
  ): Flow<Pair<MatchId, Event>> = flow {
    client
      .listEvents(
        groupId = getGroup().id,
        subGroupId = getSubGroup(team).id,
        minStart = from.atSink,
        maxEnd = until.atSink,
        includeScheduled = true,
        includeHidden = false,
        includeRepeating = false,
        limit = 500u,
      )
      .filter(::eventFilter)
      .collect {
        val matchId = eventBuilderService.extractMatchId(it) ?: return@collect
        emit(matchId to it)
      }
  }

  private suspend fun getSubGroup(team: TeamId): SubGroup {
    val name = subGroups[team]
    return getGroup().subGroups.single { it.name == name }
  }

  private fun eventFilter(event: Event): Boolean {
    val description = event.description
    return event.matchInfo != null && description?.contains(config.events.descriptionByline) == true
  }

  override suspend fun cancelMatch(team: TeamId, existing: Event) {
    log.w { "[$team] Cancelling match ${existing.identity}." }
    client.cancelEvent(existing.id, quiet = true)
  }

  override suspend fun updateMatch(triangle: Triangle, match: Match, team: Team, existing: Event) {
    val subGroup = getSubGroup(team.id)
    log.v("[${match.id}] Preparing merged spond event data for source event ${existing.identity}.")

    val updatedSpondEvent =
      runCatching { eventBuilderService.updateEvent(triangle, match, team, existing, subGroup) }
        .getOrElse {
          log.e("[${match.id}] Failed to prepare merged spond event data.", it)
          return
        }
    log.d("[${match.id}] Merged ${existing.identity} with new data.")

    val isModified = eventBuilderService.isModified(existing, updatedSpondEvent)
    val resultsModified = areResultsModified(existing, updatedSpondEvent)
    if (!isModified && !resultsModified && !config.forceUpdate) {
      log.d(
        "[${match.id}] Skipping the update..." +
          " Updated spond event is the same as previous event ${existing.identity}." +
          " isModified=$isModified," +
          " resultsModified=$resultsModified," +
          " config.forceUpdate=${config.forceUpdate}"
      )
      return
    } else {
      log.i(
        "[${match.id}] Updating spond event with new data ${existing.identity}." +
          " isModified=$isModified," +
          " resultsModified=$resultsModified," +
          " config.forceUpdate=${config.forceUpdate}"
      )
    }

    try {
      if (
        timeSource.fromSink(updatedSpondEvent.start) > timeSource.now() + 1.hours ||
          resultsModified ||
          config.forceUpdate
      ) {
        if (dry) {
          log.i(
            "[DRY] Updating spond event ${existing.identity} for ${match.identity} to $updatedSpondEvent"
          )
        } else {
          client.updateEvent(updatedSpondEvent)
        }
      }
      if (resultsModified) {
        updateMatchResults(updatedSpondEvent)
      } else {
        log.d {
          "[${match.id}] Skipping the results update..." +
            " Updated spond event results are the same as previous event ${existing.identity}."
        }
      }
    } catch (e: ClientRequestException) {
      log.e("[${match.id}] Failed to persist spond event update ${updatedSpondEvent.identity}", e)
    }
  }

  override suspend fun createMatch(triangle: Triangle, match: Match, team: Team) {
    log.v("[${match.identity}] Preparing spond event data for source match.")
    val spondEvent =
      runCatching {
          val group = getGroup()
          val subGroup = getSubGroup(team.id)
          val subGroupMembers = group.members.filter { subGroup.id in it.subGroups }.map { it.id }
          eventBuilderService.createEvent(
            triangle = triangle,
            match = match,
            team = team,
            group = group,
            subGroup = subGroup,
            subGroupMembers = subGroupMembers,
          )
        }
        .getOrElse {
          log.e("[${match.id}] Failed to prepare mew spond event data.", it)
          return
        }
    log.d("[${match.id}] Prepared spond event data.")
    val event =
      try {
        if (dry) {
          log.i("[DRY] Creating new spond event for ${match.identity}: $spondEvent")
          return
        } else {
          client.createEvent(spondEvent)
        }
      } catch (e: ClientRequestException) {
        log.e("[${match.id}] Failed to persist new spond event creation ${spondEvent.identity}", e)
        return
      }
    val updatedEvent =
      runCatching {
          val subGroup = getSubGroup(team.id)
          eventBuilderService.updateEvent(triangle, match, team, event, subGroup)
        }
        .getOrNull() ?: return

    if (areResultsModified(event, updatedEvent)) {
      updateMatchResults(updatedEvent)
    }
  }

  private fun areResultsModified(old: Event, new: Event): Boolean {
    return config.syncResults &&
      run {
        log.v { "[${old.identity}] Diffing the results." }
        eventBuilderService.areResultsModified(old, new)
      }
  }

  private suspend fun updateMatchResults(event: Event) {
    val matchInfo = event.matchInfo
    if (matchInfo != null) {
      val score =
        MatchScore(
          teamScore = matchInfo.teamScore,
          opponentScore = matchInfo.opponentScore,
          scoresPublic = matchInfo.scoresPublic,
          scoresFinal = matchInfo.scoresFinal,
        )
      if (dry) {
        log.i("[DRY] Updating spond event ${event.identity} results to $score")
      } else {
        client.updateMatchScore(id = event.id, score = score)
      }
    }
  }

  private inline fun <T> runCatching(action: () -> T): Result<T> =
    try {
      Result.success(action())
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
}
