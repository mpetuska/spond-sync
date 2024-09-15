package worker.service

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import spond.Spond
import spond.data.event.Event
import spond.data.event.MatchScore
import spond.data.group.Group
import spond.data.group.MemberId
import spond.data.group.SubGroup
import worker.WorkerConfig
import worker.data.SourceEvent
import javax.inject.Inject

class SpondService @Inject constructor(
  private val client: Spond,
  private val eventBuilderService: EventBuilderService,
  config: WorkerConfig.Spond,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("SpondService")
  private val descriptionByline = config.descriptionByline

  /**
   * Finds a group by name.
   */
  suspend fun findGroup(name: String): Group? {
    val group = client.listGroups()
      .onEach { log.v { "Found group ${it.identity}" } }
      .firstOrNull { it.name == name }
    return group
  }

  private fun eventFilter(event: Event): Boolean {
    val description = event.description
    return event.matchInfo != null && (descriptionByline == null || (description != null && description.contains(
      descriptionByline
    )))
  }

  fun listMatches(
    group: Group,
    team: SubGroup,
    seasonStart: Instant,
  ): Flow<Event> = client.listEvents(
    groupId = group.id,
    subGroupId = team.id,
    minStart = seasonStart,
    includeScheduled = true,
    includeHidden = false,
    includeRepeating = false,
    limit = 500u,
  ).filter(::eventFilter)

  /**
   * Cancels all group events.
   */
  suspend fun cancelAllEvents(group: Group) {
    client.listEvents(
      groupId = group.id,
      includeScheduled = true,
      includeHidden = false,
      includeRepeating = false,
      limit = 500u,
    )
      .filter(::eventFilter)
      .collect {
        log.d { "Canceling event ${it.identity}" }
        try {
          client.cancelEvent(it.id, quiet = true)
        } catch (e: ClientRequestException) {
          log.w("Failed to delete event ${it.identity}", e)
        }
        log.i { "Cancelled event ${it.identity}" }
      }
  }

  suspend fun updateEvent(team: SubGroup, event: Event, sourceEvent: SourceEvent): Event? {
    log.v("Preparing merged spond event data for source event ${sourceEvent.identity}")
    val updatedSpondEvent = runCatching {
      with(eventBuilderService) {
        sourceEvent.toEvent(event, team)
      }
    }.getOrElse {
      log.e("Failed to prepare merged spond event data for source event ${sourceEvent.identity}", it)
      return null
    }
    log.d("Prepared merged spond event data for source event ${sourceEvent.identity}")

    return try {
      val event = client.updateEvent(updatedSpondEvent)
      if (updatedSpondEvent.matchInfo?.scoresSetEver == true && event.matchInfo != updatedSpondEvent.matchInfo) {
        val matchInfo = checkNotNull(updatedSpondEvent.matchInfo)
        client.updateMatchScore(
          id = updatedSpondEvent.id,
          score = MatchScore(
            teamScore = matchInfo.teamScore,
            opponentScore = matchInfo.opponentScore,
            scoresPublic = matchInfo.scoresPublic,
            scoresFinal = matchInfo.scoresFinal,
          )
        )
      } else {
        event
      }
    } catch (e: ClientRequestException) {
      log.e("Failed to persist spond event update ${updatedSpondEvent.identity}", e)
      null
    }
  }

  suspend fun createEvent(group: Group, team: SubGroup, teamMembers: List<MemberId>, sourceEvent: SourceEvent): Event? {
    log.v("Preparing spond event data for source event ${sourceEvent.identity}")
    val spondEvent = runCatching {
      with(eventBuilderService) {
        sourceEvent.toNewEvent(group, team, teamMembers)
      }
    }.getOrElse {
      log.e("Failed to prepare spond event data for source event ${sourceEvent.identity}", it)
      null
    } ?: return null
    log.d("Prepared spond event data for source event ${sourceEvent.identity}")

    return try {
      client.createEvent(spondEvent)
    } catch (e: ClientRequestException) {
      log.e("Failed to persist new spond event creation ${spondEvent.identity}", e)
      null
    }
  }
}
