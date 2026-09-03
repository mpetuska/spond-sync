package dev.petuska.spond.sync.runtime.sink.spond

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig
import dev.petuska.spond.sync.runtime.model.Match
import dev.petuska.spond.sync.runtime.model.MatchId
import dev.petuska.spond.sync.runtime.model.Time
import dev.petuska.spond.sync.runtime.sink.spond.service.EventBuilderService
import dev.petuska.spond.sync.runtime.sink.spond.service.SpondService
import dev.petuska.spond.sync.runtime.util.TimeSource
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.event.Event
import dev.petuska.spond.sync.spond.data.event.MatchScore
import dev.petuska.spond.sync.spond.data.group.SubGroupName
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Named
import io.ktor.client.plugins.*
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

@AssistedInject
class MatchesSpondSink(
  private val rootConfig: SpondSinkConfig,
  private val json: Json,
  private val client: Spond,
  private val spondService: SpondService,
  eventBuilderService: EventBuilderService.Factory,
  private val timeSource: TimeSource,
  @Named("dry") private val dry: Boolean,
  @Assisted private val subGroup: SubGroupName,
  @Assisted private val config: SpondSinkConfig.SubGroupConfig,
  @Assisted private val from: Time,
  @Assisted private val until: Time,
) {
  @AssistedFactory
  fun interface Factory {
    fun create(
      subGroup: SubGroupName,
      config: SpondSinkConfig.SubGroupConfig,
      from: Time,
      until: Time,
    ): MatchesSpondSink
  }

  private val log = Logger.withTag("TrianglesSpondSink")
  private val eventBuilderService = eventBuilderService.create(config)

  suspend fun syncMatches(matches: List<Match>) {
    log.d("[$subGroup] Filtering matches.")
    val teamMatches = matches.filter { config.team in it }
    log.i("[$subGroup] Synchronising ${teamMatches.size} matches.")
    val updates: MutableMap<MatchId, Match> = teamMatches.associateBy { it.id }.toMutableMap()
    log.v("[$subGroup] Updating existing matches.")
    listExistingMatches().buffer().collect { (matchId, it) ->
      val update = updates.remove(matchId)
      if (update == null) {
        log.w(
          "[$subGroup] Sink match $matchId ${it.identity} was not found on source. Cancelling..."
        )
        cancelMatch(it)
        return@collect
      }
      log.v("[$subGroup] Updating existing sink match ${it.identity}.")
      updateMatch(
        match = update,
        existing = it,
      )
    }

    log.v("[$subGroup] Creating new matches.")
    val newMatches = updates.toList()
    for ((matchId, match) in newMatches) {
      updates.remove(matchId)
      log.v("[$subGroup] Creating new sink match ${match.identity}.")
      createMatch(match)
    }
    for (matchId in updates.keys) {
      log.w("[$matchId] Discarding match not having any teams of interest for team ${subGroup}.")
    }
  }

  private fun listExistingMatches(): Flow<Pair<MatchId, Event>> = flow {
    client
      .listEvents(
        groupId = spondService.getGroup().id,
        subGroupId = spondService.getSubGroup(subGroup).id,
        minStart = from.atSink,
        maxEnd = until.atSink,
        includeScheduled = true,
        includeHidden = false,
        includeRepeating = false,
        limit = 500u,
      )
      .filter(::eventFilter)
      .collect { event ->
        val matchId = eventBuilderService.extractEventId(event) ?: return@collect
        emit(matchId to event)
      }
  }

  private fun eventFilter(event: Event): Boolean {
    val description = event.description
    return event.matchInfo != null && description?.contains(config.events.descriptionByline) == true
  }

  private suspend fun cancelMatch(existing: Event) {
    log.w { "[$subGroup] Cancelling match ${existing.identity}." }
    if (dry) {
      log.i("[DRY] Cancelling match ${existing.identity}.")
    } else {
      try {
        client.cancelEvent(existing.id, quiet = true)
        log.i("[$subGroup] Cancelled event ${existing.identity}.")
      } catch (e: ClientRequestException) {
        log.w("[$subGroup] Failed to delete event ${existing.identity}.", e)
      }
    }
  }

  private suspend fun updateMatch(match: Match, existing: Event) {
    val subGroup = spondService.getSubGroup(subGroup)
    log.v("[${match.id}] Preparing merged spond event data for source event ${existing.identity}.")

    val updatedSpondEvent =
      try {
        eventBuilderService.updateMatch(
          match = match,
          base = existing,
          subGroup = subGroup,
          owners = spondService.findOwners(this.subGroup),
        )
      } catch (e: Exception) {
        log.e("[${match.id}] Failed to prepare merged spond event data.", e)
        if (e is CancellationException) throw e
        return
      }
    log.d("[${match.id}] Merged ${existing.identity} with new data.")

    val isModified = eventBuilderService.isModified(existing, updatedSpondEvent)
    val resultsModified = areResultsModified(existing, updatedSpondEvent)
    if (!isModified && !resultsModified && !rootConfig.forceUpdate) {
      log.d(
        "[${match.id}] Skipping the update..." +
          " Updated spond event is the same as previous event ${existing.identity}." +
          " isModified=$isModified," +
          " resultsModified=$resultsModified," +
          " config.forceUpdate=${rootConfig.forceUpdate}"
      )
      return
    } else {
      log.i(
        "[${match.id}] Updating spond event with new data ${existing.identity}." +
          " isModified=$isModified," +
          " resultsModified=$resultsModified," +
          " config.forceUpdate=${rootConfig.forceUpdate}"
      )
    }

    try {
      if (
        timeSource.fromSink(updatedSpondEvent.start) > timeSource.now() + 1.hours ||
          resultsModified ||
          rootConfig.forceUpdate
      ) {
        if (dry) {
          log.i(
            "[DRY] Updating spond event ${existing.identity} for ${match.identity} to $updatedSpondEvent"
          )
        } else {
          client.updateEvent(updatedSpondEvent)
        }
      }
    } catch (e: ClientRequestException) {
      log.e(
        "[${match.id}] Failed to persist spond event update ${updatedSpondEvent.identity}",
        e,
      )
    }
  }

  private suspend fun createMatch(match: Match) {
    log.v("[${match.id}] Preparing spond event data for source match.")
    val spondEvent =
      try {
        val group = spondService.getGroup()
        val subGroup = spondService.getSubGroup(subGroup)
        val owners = spondService.findOwners(this.subGroup)
        val subGroupMembers = group.members.filter { subGroup.id in it.subGroups }.map { it.id }
        eventBuilderService.createMatch(
          match = match,
          group = group,
          subGroup = subGroup,
          subGroupMembers = subGroupMembers,
          owners = owners,
        )
      } catch (e: Exception) {
        log.e("[${match.id}] Failed to prepare spond event data.", e)
        if (e is CancellationException) throw e
        return
      }
    log.d("[${match.id}] Prepared spond event data.")
    log.v { "[${match.id}] Prepared event:\n${json.encodeToString(spondEvent)}." }
    val event =
      try {
        if (dry) {
          log.i("[DRY] Creating new spond event for ${match.identity}: $spondEvent")
          return
        } else {
          client.createEvent(spondEvent)
        }
      } catch (e: ClientRequestException) {
        log.e(
          "[${match.id}] Failed to persist new spond event creation ${spondEvent.identity}",
          e,
        )
        return
      }
    val updatedEvent =
      try {
        val subGroup = spondService.getSubGroup(subGroup)
        eventBuilderService.updateMatch(
          match = match,
          base = event,
          subGroup = subGroup,
          owners = spondService.findOwners(this.subGroup),
        )
      } catch (e: Exception) {
        log.e("[${match.id}] Failed to update new spond event ${event.identity}", e)
        if (e is CancellationException) throw e
        return
      }

    if (areResultsModified(event, updatedEvent)) {
      updateMatchResults(updatedEvent)
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

  private fun areResultsModified(old: Event, new: Event): Boolean {
    return rootConfig.syncResults &&
      run {
        log.v { "[${old.identity}] Diffing the results." }
        eventBuilderService.areResultsModified(old, new)
      }
  }

  suspend fun cancelAllMatches() {
    client
      .listEvents(
        groupId = spondService.getGroup().id,
        subGroupId = spondService.getSubGroup(subGroup).id,
        minStart = from.atSink,
        maxEnd = until.atSink,
        includeScheduled = true,
        includeHidden = false,
        includeRepeating = false,
        limit = 500u,
      )
      .filter { event ->
        event.description?.contains(config.events.descriptionByline) == true
      }
      .collect {
        cancelMatch(it)
      }
  }
}
