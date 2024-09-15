package worker.service

import co.touchlab.kermit.Logger
import kotlinx.datetime.*
import spond.data.event.Event
import spond.data.group.Group
import spond.data.group.SubGroup
import worker.data.SourceEvent
import worker.source.EventSource
import javax.inject.Inject

class SyncService @Inject constructor(
  private val source: EventSource,
  private val spond: SpondService,
  private val timeService: TimeService,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("SyncService")

  suspend fun sync(
    seasonStart: Instant,
    group: Group,
    teams: Map<String, SubGroup>,
  ) {
    log.d("Fetching source events from ${source.name}")
    val sourceEvents = source.provideEvents(
      club = group.name,
      teams = teams.keys,
      start = seasonStart,
    )
    log.i("Fetched source events from ${source.name} for ${sourceEvents.size} teams")
    for ((team, events) in sourceEvents) {
      syncTeam(seasonStart, group, teams.getValue(team), events)
    }
  }

  private suspend fun syncTeam(
    seasonStart: Instant,
    group: Group,
    team: SubGroup,
    events: List<SourceEvent>,
  ) {
    val eventQueue = events.associateBy(SourceEvent::name).toMutableMap()
    val unmatchedSpondEvents = mutableListOf<Event>()
    log.i("Processing spond events for team ${team.identity}")

    spond.listMatches(
      group = group,
      team = team,
      seasonStart = seasonStart,
    ).collect { spondEvent ->
      log.i("Processing existing spond event ${spondEvent.identity}")
      val sourceEvent = eventQueue[spondEvent.name]
      if (sourceEvent != null) {
        log.d("Matched spond event ${spondEvent.identity} to source event ${sourceEvent.identity}")
        eventQueue -= sourceEvent.name
      } else {
        log.e("Unable to match spond event ${spondEvent.identity} to source event")
        unmatchedSpondEvents += spondEvent
        return@collect
      }

      log.d("Updating spond event ${spondEvent.identity}")
      val updated = spond.updateEvent(team, spondEvent, sourceEvent)
      if (updated != null) {
        log.i("Updated spond event ${updated.identity}")
      } else {
        log.w("Failed to update spond event ${spondEvent.identity}")
      }
    }

    if (eventQueue.isEmpty()) return
    log.i("Creating new spond event for remaining ${eventQueue.size} source events")
    val subGroupMembers = group.members.filter { team.id in it.subGroups }.map { it.id }
    val today = timeService.now()
    for (sourceEvent in eventQueue.values) {
      if (sourceEvent.start > today) {
        log.i("Processing new source event ${sourceEvent.identity}")
      } else {
        log.i("Skipping new source event ${sourceEvent.identity} as it has already passed")
        continue
      }
      log.v("Creating a new spond event for source event ${sourceEvent.identity}")
      val created = spond.createEvent(group, team, subGroupMembers, sourceEvent)
      if (created != null) {
        log.i("Created a new spond event ${created.identity}")
      } else {
        log.w("Failed to create a spond event for source event ${sourceEvent.identity}")
      }
    }
  }
}
