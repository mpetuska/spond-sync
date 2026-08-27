package dev.petuska.spond.sync.spond.sink.subsink

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.DataSink
import dev.petuska.spond.sync.core.TimeSource
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.MatchId
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.event.Event
import dev.petuska.spond.sync.spond.data.event.MatchScore
import dev.petuska.spond.sync.spond.sink.SpondSinkConfig
import dev.petuska.spond.sync.spond.sink.service.EventBuilderService
import dev.petuska.spond.sync.spond.sink.service.SpondService
import dev.petuska.spond.sync.utils.Named
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.plugins.*
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

@Inject
@SingleIn(ClubScope::class)
class MatchesSubSink(
  private val config: SpondSinkConfig,
  private val client: Spond,
  private val spondService: SpondService,
  private val eventBuilderService: EventBuilderService,
  private val timeSource: TimeSource,
  private val json: Json,
  @Named("dry") private val dry: Boolean,
  logger: Logger,
) : DataSink {
  private val log = logger.withTag("MatchesSubSink")

  override suspend fun syncTeam(team: Team, from: Time, until: Time, triangles: List<Triangle>) {
    log.i("[${team.id}] Synchronising ${triangles.size} triangles.")
    val matches: MutableMap<MatchId, Pair<Triangle, Match>> =
      triangles
        .flatMap { triangle ->
          triangle.matches.toList().filter { team in it }.map { triangle to it }
        }
        .associateBy { (_, match) -> match.id }
        .toMutableMap()
    log.v("[${team.id}] Updating existing matches.")
    listExistingMatches(team = team.id, from = from, until = until).buffer().collect { (matchId, it)
      ->
      val update = matches.remove(matchId)
      if (update == null) {
        log.w(
          "[${team.id}] Sink match $matchId ${it.identity} was not found on source. Cancelling..."
        )
        cancelMatch(team.id, it)
        return@collect
      }
      log.v("[${team.id}] Updating existing sink match ${it.identity}.")
      updateMatch(
        triangle = update.first,
        match = update.second,
        team = team,
        existing = it,
      )
    }
    log.v("[${team.id}] Creating new matches.")
    val teamMatches = matches.values.toList()
    for ((triangle, match) in teamMatches) {
      matches.remove(match.id)
      log.v("[${team.id}] Creating new sink match ${match.identity}.")
      createMatch(triangle = triangle, match = match, team = team)
    }
    for ((_, _, id) in matches.values.map { it.second }) {
      log.w("[$id] Discarding match not having any teams of interest for team ${team.id}.")
    }
  }

  internal fun listExistingMatches(
    team: TeamId,
    from: Time,
    until: Time,
  ): Flow<Pair<MatchId, Event>> = flow {
    client
      .listEvents(
        groupId = spondService.getGroup().id,
        subGroupId = spondService.getSubGroup(team).id,
        minStart = from.atSink,
        maxEnd = until.atSink,
        includeScheduled = true,
        includeHidden = false,
        includeRepeating = false,
        limit = 500u,
      )
      .filter(::eventFilter)
      .collect { event ->
        val matchId = eventBuilderService.extractMatchId(event) ?: return@collect
        emit(matchId to event)
      }
  }

  private fun eventFilter(event: Event): Boolean {
    val description = event.description
    return event.matchInfo != null && description?.contains(config.events.descriptionByline) == true
  }

  suspend fun cancelMatch(team: TeamId, existing: Event) {
    log.w { "[$team] Cancelling match ${existing.identity}." }
    client.cancelEvent(existing.id, quiet = true)
  }

  suspend fun updateMatch(triangle: Triangle, match: Match, team: Team, existing: Event) {
    val subGroup = spondService.getSubGroup(team.id)
    log.v("[${match.id}] Preparing merged spond event data for source event ${existing.identity}.")

    val updatedSpondEvent =
      try {
        eventBuilderService.updateEvent(
          triangle = triangle,
          match = match,
          team = team,
          base = existing,
          subGroup = subGroup,
          owners = spondService.findOwners(team.id),
        )
      } catch (e: Exception) {
        log.e("[${match.id}] Failed to prepare merged spond event data.", e)
        if (e is CancellationException) throw e
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

  suspend fun createMatch(triangle: Triangle, match: Match, team: Team) {
    log.v("[${match.identity}] Preparing spond event data for source match.")
    val spondEvent =
      try {
        val group = spondService.getGroup()
        val subGroup = spondService.getSubGroup(team.id)
        val owners = spondService.findOwners(team.id)
        val subGroupMembers = group.members.filter { subGroup.id in it.subGroups }.map { it.id }
        eventBuilderService.createEvent(
          triangle = triangle,
          match = match,
          team = team,
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
        log.e("[${match.id}] Failed to persist new spond event creation ${spondEvent.identity}", e)
        return
      }
    val updatedEvent =
      try {
        val subGroup = spondService.getSubGroup(team.id)
        eventBuilderService.updateEvent(
          triangle = triangle,
          match = match,
          team = team,
          base = event,
          subGroup = subGroup,
          owners = spondService.findOwners(team.id),
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
}
