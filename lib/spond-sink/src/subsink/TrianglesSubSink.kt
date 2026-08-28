package dev.petuska.spond.sync.spond.sink.subsink

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.DataSink
import dev.petuska.spond.sync.core.TimeSource
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.core.model.TriangleId
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.event.Event
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
class TrianglesSubSink(
  private val config: SpondSinkConfig,
  private val client: Spond,
  private val spondService: SpondService,
  private val eventBuilderService: EventBuilderService,
  private val timeSource: TimeSource,
  private val json: Json,
  @Named("dry") private val dry: Boolean,
  logger: Logger,
) : DataSink {
  private val log = logger.withTag("TrianglesSubSink")

  override suspend fun syncTeam(team: Team, from: Time, until: Time, triangles: List<Triangle>) {
    log.i("[${team.id}] Synchronising ${triangles.size} triangles.")
    val updates: MutableMap<TriangleId, Triangle> =
      triangles.associateBy { triangle -> triangle.id }.toMutableMap()
    log.v("[${team.id}] Updating existing triangles.")
    listExistingTriangles(team = team.id, from = from, until = until).buffer().collect {
      (triangleId, it) ->
      val update = updates.remove(triangleId)
      if (update == null) {
        log.w(
          "[${team.id}] Sink event $triangleId ${it.identity} was not found on source. Cancelling..."
        )
        cancelTriangle(team.id, it)
        return@collect
      }
      log.v("[${team.id}] Updating existing sink triangle ${it.identity}.")
      updateTriangle(
        triangle = update,
        team = team,
        existing = it,
      )
    }
    log.v("[${team.id}] Creating new matches.")
    val teamTriangles = updates.toList()
    for ((id, triangle) in teamTriangles) {
      updates.remove(id)
      log.v("[${team.id}] Creating new sink triangle ${triangle.identity}.")
      createTriangle(triangle = triangle, team = team)
    }
    for ((id) in updates.values) {
      log.w("[$id] Discarding triangle not having any teams of interest for team ${team.id}.")
    }
  }

  internal fun listExistingTriangles(
    team: TeamId,
    from: Time,
    until: Time,
  ): Flow<Pair<TriangleId, Event>> = flow {
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
        val triangleId = eventBuilderService.extractEventId(event)
        if (triangleId == null || triangleId.length != 4) return@collect
        emit(TriangleId(triangleId) to event)
      }
  }

  private fun eventFilter(event: Event): Boolean {
    if (event.type?.equals("EVENT", ignoreCase = true) != true) return false
    val description = event.description
    return description?.contains(config.events.descriptionByline) == true
  }

  suspend fun cancelTriangle(team: TeamId, existing: Event) {
    log.w { "[$team] Cancelling event ${existing.identity}." }
    client.cancelEvent(existing.id, quiet = true)
  }

  suspend fun updateTriangle(triangle: Triangle, team: Team, existing: Event) {
    log.v(
      "[${triangle.id}] Preparing merged spond event data for source event ${existing.identity}."
    )

    val updatedSpondEvent =
      try {
        eventBuilderService.updateTriangle(
          triangle = triangle,
          base = existing,
          owners = spondService.findOwners(team.id),
        )
      } catch (e: Exception) {
        log.e("[${triangle.id}] Failed to prepare merged spond event data.", e)
        if (e is CancellationException) throw e
        return
      }
    log.d("[${triangle.id}] Merged ${existing.identity} with new data.")

    val isModified = eventBuilderService.isModified(existing, updatedSpondEvent)
    val resultsModified = areResultsModified(existing, triangle)
    if (!isModified && !resultsModified && !config.forceUpdate) {
      log.d(
        "[${triangle.id}] Skipping the update..." +
          " Updated spond event is the same as previous event ${existing.identity}." +
          " isModified=$isModified," +
          " resultsModified=$resultsModified," +
          " config.forceUpdate=${config.forceUpdate}"
      )
      return
    } else {
      log.i(
        "[${triangle.id}] Updating spond event with new data ${existing.identity}." +
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
            "[DRY] Updating spond event ${existing.identity} for ${triangle.identity} to $updatedSpondEvent"
          )
        } else {
          client.updateEvent(updatedSpondEvent)
        }
      }
    } catch (e: ClientRequestException) {
      log.e(
        "[${triangle.id}] Failed to persist spond event update ${updatedSpondEvent.identity}",
        e,
      )
    }
  }

  suspend fun createTriangle(triangle: Triangle, team: Team) {
    log.v("[${triangle.identity}] Preparing spond event data for source match.")
    val spondEvent =
      try {
        val group = spondService.getGroup()
        val subGroup = spondService.getSubGroup(team.id)
        val owners = spondService.findOwners(team.id)
        val subGroupMembers = group.members.filter { subGroup.id in it.subGroups }.map { it.id }
        eventBuilderService.createTriangle(
          triangle = triangle,
          group = group,
          subGroup = subGroup,
          subGroupMembers = subGroupMembers,
          owners = owners,
        )
      } catch (e: Exception) {
        log.e("[${triangle.id}] Failed to prepare spond event data.", e)
        if (e is CancellationException) throw e
        return
      }
    log.d("[${triangle.id}] Prepared spond event data.")
    log.v { "[${triangle.id}] Prepared event:\n${json.encodeToString(spondEvent)}." }
    val event =
      try {
        if (dry) {
          log.i("[DRY] Creating new spond event for ${triangle.identity}: $spondEvent")
          return
        } else {
          client.createEvent(spondEvent)
        }
      } catch (e: ClientRequestException) {
        log.e(
          "[${triangle.id}] Failed to persist new spond event creation ${spondEvent.identity}",
          e,
        )
        return
      }
  }

  private fun areResultsModified(old: Event, new: Triangle): Boolean {
    return config.syncResults &&
      run {
        log.v { "[${old.identity}] Diffing the results." }
        eventBuilderService.areResultsModified(old, new)
      }
  }
}
