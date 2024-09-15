package worker.service

import kotlinx.datetime.Instant
import spond.data.event.Event
import spond.data.event.MatchInfo
import spond.data.event.MatchType
import spond.data.event.NewEvent
import spond.data.event.Recipients
import spond.data.group.Group
import spond.data.group.MemberId
import spond.data.group.SubGroup
import spond.data.location.Location
import worker.WorkerConfig
import worker.data.SourceEvent
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

class EventBuilderService @Inject constructor(
  private val locationService: LocationService,
  private val timeService: TimeService,
  config: WorkerConfig.Spond,
) {
  private val maxAccepted = config.maxAccepted
  private val invitationDayBeforeStart = config.invitationDayBeforeStart
  private val rsvpDeadlineBeforeStart = config.rsvpDeadlineBeforeStart
  private val descriptionByline = config.descriptionByline
  private val opponentColourHex = config.opponentColourHex

  suspend fun SourceEvent.toEvent(base: Event, subGroup: SubGroup): Event {
    return base.copy(
      name = name,
      description = description(),
      matchInfo = matchInfo(subGroup.color),
      location = location(),
      start = timeService.reset(start),
      end = timeService.reset(end),
      inviteTime = inviteTime(),
      rsvpDate = rsvpDate(),
      maxAccepted = maxAccepted,
    )
  }

  suspend fun SourceEvent.toNewEvent(
    group: Group,
    subGroup: SubGroup,
    subGroupMembers: List<MemberId>,
  ): NewEvent {
    return NewEvent(
      name = name,
      description = description(),
      matchInfo = matchInfo(subGroup.color),
      location = location(),
      recipients = Recipients.New(
        group = Recipients.NewRecipientsGroup(
          id = group.id,
          subGroups = listOf(subGroup.id)
        ),
        groupMembers = subGroupMembers,
      ),
      start = timeService.reset(start),
      end = timeService.reset(end),
      inviteTime = inviteTime(),
      rsvpDate = rsvpDate(),
      maxAccepted = maxAccepted,
    )
  }

  private fun SourceEvent.inviteTime(): Instant {
    return timeService.reset(start - invitationDayBeforeStart.toInt().days)
  }

  private fun SourceEvent.rsvpDate(): Instant {
    return timeService.reset(start - rsvpDeadlineBeforeStart.toInt().days)
  }

  private fun SourceEvent.description() = buildString {
    appendLine(description)
    if (result != null) {
      val teamId = if (homeMatch) teamAId else teamBId
      val (scoresA, scoresB, sets) = if (result.winnerId == teamId) {
        Triple(result.winnerScores, result.loserScores, "${result.winnerSets}-${result.loserSets}")
      } else {
        Triple(result.loserScores, result.winnerScores, "${result.loserSets}-${result.winnerSets}")
      }
      appendLine("SETS ($sets):")
      for (set in scoresA.indices) {
        appendLine("  - ${scoresA[set].toString().padStart(2)}:${scoresB[set].toString().padStart(2)}")
      }
    }
    appendLine()
    appendLine("${PREFIX_EVENT_ID}${id}")
    if (descriptionByline != null) {
      appendLine(descriptionByline)
    }
  }

  private suspend fun SourceEvent.location(): Location {
    return checkNotNull(locationService.resolveSpondLocation(address)) {
      "Unable to resolve spond location for $identity"
    }
  }

  private fun SourceEvent.matchInfo(homeColourHex: String): MatchInfo {
    val base = MatchInfo(
      type = if (homeMatch) MatchType.Home else MatchType.Away,
      teamName = if (homeMatch) teamA else teamB,
      teamColour = homeColourHex,
      opponentName = if (homeMatch) teamB else teamA,
      opponentColour = opponentColourHex,
    )
    return if (result == null) {
      base
    } else {
      val teamId = if (homeMatch) teamAId else teamBId
      val (teamSets, opponentSets) = if (result.winnerId == teamId) {
        Pair(result.winnerSets, result.loserSets)
      } else {
        Pair(result.loserSets, result.winnerSets)
      }

      base.copy(
        scoresSet = true,
        scoresSetEver = true,
        scoresPublic = true,
        scoresFinal = true,
        teamScore = teamSets,
        opponentScore = opponentSets,
      )
    }
  }

  companion object {
    private const val PREFIX_EVENT_ID = "Event ID: "

    fun extractSourceEventId(event: Event): String? {
      return event.description
        ?.split("\n")
        ?.first { it.startsWith(PREFIX_EVENT_ID) }
        ?.removePrefix(PREFIX_EVENT_ID)
    }
  }
}
