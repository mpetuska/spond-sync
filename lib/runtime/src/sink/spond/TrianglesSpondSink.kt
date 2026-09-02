package dev.petuska.spond.sync.runtime.sink.spond

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig
import dev.petuska.spond.sync.runtime.model.Match
import dev.petuska.spond.sync.runtime.model.Team
import dev.petuska.spond.sync.runtime.model.Time
import dev.petuska.spond.sync.runtime.model.Triangle
import dev.petuska.spond.sync.runtime.model.TriangleId
import dev.petuska.spond.sync.runtime.sink.spond.service.EventBuilderService
import dev.petuska.spond.sync.runtime.sink.spond.service.SpondService
import dev.petuska.spond.sync.runtime.util.TimeSource
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.event.Event
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
class TrianglesSpondSink(
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
    ): TrianglesSpondSink
  }

  private val log = Logger.withTag("TrianglesSpondSink")
  private val eventBuilderService = eventBuilderService.create(config)

  suspend fun syncMatches(matches: List<Match>) {
    val (friendlyMatches, leagueMatches) =
      matches.partition { it.id.contains("friendly", ignoreCase = true) }
    val triangles =
      leagueMatches
        .groupBy { TriangleId(it.id.dropLast(1)) }
        .mapNotNull { buildTriangle(it.key, it.value, friendlyMatches) }
        .toSet()
    log.d("Built ${triangles.size} triangles.")

    log.d("[$subGroup] Filtering triangles.")
    val teamTriangles = triangles.filter { config.team in it }
    log.i("[$subGroup] Found ${teamTriangles.size} triangles.")
    syncTeam(teamTriangles)
  }

  private fun buildTriangle(
    id: TriangleId,
    matches: List<Match>,
    friendlyMatches: List<Match>,
  ): Triangle? {
    val matches =
      if (matches.size == 3) {
        matches
      } else {
        val friendlies =
          friendlyMatches
            .filter { f -> matches.all { m -> m.start == f.start && m.venue == f.venue } }
            .distinct()
        matches.plus(friendlies).distinctBy { "${it.teamA} vs ${it.teamB}" }
      }
    if (matches.size != 3) {
      log.e("[$id] Triangle must have exactly 3 matches. Instead had ${matches.size} $matches.")
      return null
    }
    val host = findHost(id, matches) ?: return null
    val aVenues = matches.filter { host in it }.map { it.venue }.distinct()
    if (aVenues.size != 1) {
      log.e("[$id] Detected different venues for host ${host.identity}: $aVenues")
      return null
    }
    val invalidStartTimes = matches.filter { it.startTime.hour < 8 }
    if (invalidStartTimes.isNotEmpty()) {
      log.e("[$id] Detected matches with invalid start times: $invalidStartTimes")
      return null
    }
    val teams = matches.flatMap { setOf(it.teamA, it.teamB) }.distinct().sortedBy(Team::name)
    if (teams.size != 3) {
      log.e("[$id] Detected triangle with invalid number of teams: $teams")
      return null
    }
    return Triangle(
      id = id,
      venue = aVenues.single(),
      start = matches.minOf(Match::start),
      end = matches.maxOf(Match::end),
      host = host,
      teams = teams.sortedBy(Team::name),
      matches = matches.sortedBy(Match::order),
    )
  }

  private fun findHost(id: TriangleId, matches: List<Match>): Team? {
    val mostATeam = matches.groupBy { it.teamA }.maxBy { it.value.size }
    if (mostATeam.value.size != 2) {
      log.e(
        "[$id] Expected most frequent A team to be A team for 2 matches, was ${mostATeam.value.size}."
      )
      return null
    }
    val firstMatch =
      matches.singleOrNull { it.id.endsWith('a') }
        ?: run {
          log.w { "[$id] No match id ending with `a`." }
          return mostATeam.key
        }
    val secondMatch =
      matches.singleOrNull { it.id.endsWith('b') }
        ?: run {
          log.w { "[$id] No match id ending with `b`." }
          return mostATeam.key
        }
    return if (secondMatch.teamA !in firstMatch) {
      secondMatch.teamA
    } else if (secondMatch.teamB !in firstMatch) {
      secondMatch.teamB
    } else {
      log.w {
        "[$id] Both teams from second match ${secondMatch.identity} also appear in first match ${firstMatch.identity}."
      }
      mostATeam.key
    }
  }

  private suspend fun syncTeam(triangles: List<Triangle>) {
    log.i("[$subGroup] Synchronising ${triangles.size} triangles.")
    val updates: MutableMap<TriangleId, Triangle> =
      triangles.associateBy { triangle -> triangle.id }.toMutableMap()
    log.v("[$subGroup] Updating existing triangles.")
    listExistingTriangles().buffer().collect { (triangleId, it) ->
      val update = updates.remove(triangleId)
      if (update == null) {
        log.w(
          "[$subGroup] Sink event $triangleId ${it.identity} was not found on source. Cancelling..."
        )
        cancelTriangle(it)
        return@collect
      }
      log.v("[$subGroup] Updating existing sink triangle ${it.identity}.")
      updateTriangle(
        triangle = update,
        existing = it,
      )
    }
    log.v("[$subGroup] Creating new matches.")
    val teamTriangles = updates.toList()
    for ((id, triangle) in teamTriangles) {
      updates.remove(id)
      log.v("[$subGroup] Creating new sink triangle ${triangle.identity}.")
      createTriangle(triangle = triangle)
    }
    for ((id) in updates.values) {
      log.w("[$id] Discarding triangle not having any teams of interest for team ${subGroup}.")
    }
  }

  private fun listExistingTriangles(): Flow<Pair<TriangleId, Event>> = flow {
    client
      .listEvents(
        groupId = spondService.getGroup().id,
        subGroupId = spondService.getSubGroup(config.team).id,
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

  private suspend fun cancelTriangle(existing: Event) {
    log.w { "[$subGroup] Cancelling event ${existing.identity}." }
    if (dry) {
      log.i("[DRY] Cancelling event ${existing.identity}.")
    } else {
      try {
        client.cancelEvent(existing.id, quiet = true)
        log.i("[$subGroup] Cancelled event ${existing.identity}.")
      } catch (e: ClientRequestException) {
        log.w("[$subGroup] Failed to delete event ${existing.identity}.", e)
      }
    }
  }

  private suspend fun updateTriangle(triangle: Triangle, existing: Event) {
    log.v(
      "[${triangle.id}] Preparing merged spond event data for source event ${existing.identity}."
    )

    val updatedSpondEvent =
      try {
        eventBuilderService.updateTriangle(
          triangle = triangle,
          base = existing,
          owners = spondService.findOwners(config.team),
        )
      } catch (e: Exception) {
        log.e("[${triangle.id}] Failed to prepare merged spond event data.", e)
        if (e is CancellationException) throw e
        return
      }
    log.d("[${triangle.id}] Merged ${existing.identity} with new data.")

    val isModified = eventBuilderService.isModified(existing, updatedSpondEvent)
    val resultsModified = areResultsModified(existing, triangle)
    if (!isModified && !resultsModified && !rootConfig.forceUpdate) {
      log.d(
        "[${triangle.id}] Skipping the update..." +
          " Updated spond event is the same as previous event ${existing.identity}." +
          " isModified=$isModified," +
          " resultsModified=$resultsModified," +
          " config.forceUpdate=${rootConfig.forceUpdate}"
      )
      return
    } else {
      log.i(
        "[${triangle.id}] Updating spond event with new data ${existing.identity}." +
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

  private suspend fun createTriangle(triangle: Triangle) {
    log.v("[${triangle.identity}] Preparing spond event data for source match.")
    val spondEvent =
      try {
        val group = spondService.getGroup()
        val subGroup = spondService.getSubGroup(config.team)
        val owners = spondService.findOwners(config.team)
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

  private fun areResultsModified(
    old: Event,
    new: Triangle,
  ): Boolean {
    return rootConfig.syncResults &&
      run {
        log.v { "[${old.identity}] Diffing the results." }
        eventBuilderService.areResultsModified(old, new)
      }
  }

  suspend fun cancelAllTriangles() {
    client
      .listEvents(
        groupId = spondService.getGroup().id,
        subGroupId = spondService.getSubGroup(config.team).id,
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
        cancelTriangle(it)
      }
  }
}
