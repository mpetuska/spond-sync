package worker.service

import co.touchlab.kermit.Logger
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
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
  config: WorkerConfig,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("EventBuilderService")
  private val maxAccepted = config.spond.maxAccepted
  private val invitationDayBeforeStart = config.spond.invitationDayBeforeStart
  private val rsvpDeadlineBeforeStart = config.spond.rsvpDeadlineBeforeStart
  private val descriptionByline = config.spond.descriptionByline
  private val opponentColourHex = config.spond.opponentColourHex
  private val subGroupsToTeams = config.subGroups

  suspend fun SourceEvent.toEvent(base: Event, subGroup: SubGroup): Event {
    return base.copy(
      name = name,
      description = description(),
      matchInfo = matchInfo(subGroup),
      location = location(),
      start = timeService.reset(start),
      end = timeService.reset(end),
      inviteTime = inviteTime(),
      rsvpDate = rsvpDate(),
      maxAccepted = maxAccepted,
      json = base.json.toMutableMap().apply {
        remove("responses")
      }.let(::JsonObject)
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
      matchInfo = matchInfo(subGroup),
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

  /**
   * Compares the two events and returns two if [new] has been updated.
   */
  fun modified(old: Event, new: Event): Boolean {
    val sameLocation = old.location?.address == new.location?.address &&
      old.location?.feature == new.location?.feature
    val sameResult = old.matchInfo?.opponentScore == old.matchInfo?.opponentScore &&
      old.matchInfo?.teamScore == new.matchInfo?.teamScore &&
      old.matchInfo?.teamColour == new.matchInfo?.teamColour
    val sameInviteTime = old.inviteTime == null || old.inviteTime == new.inviteTime
    val same = old.start == new.start &&
      old.end == new.end &&
      old.description
        ?.lines()?.filter { !it.startsWith(PREFIX_LAST_UPDATED) } == new.description
      ?.lines()?.filter { !it.startsWith(PREFIX_LAST_UPDATED) } &&
      sameLocation &&
      sameResult &&
      old.maxAccepted == new.maxAccepted &&
      sameInviteTime &&
      old.rsvpDate == new.rsvpDate
    return !same
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
      val (scoresA, scoresB, sets) = if (result.winnerId == teamAId) {
        Triple(result.winnerScores, result.loserScores, "${result.winnerSets}-${result.loserSets}")
      } else {
        Triple(result.loserScores, result.winnerScores, "${result.loserSets}-${result.winnerSets}")
      }
      if (scoresA != null && scoresB != null) {
        appendLine("SETS ($sets):")
        for (set in scoresA.indices) {
          appendLine("  - ${scoresA[set].toString().padStart(2)}:${scoresB[set].toString().padStart(2)}")
        }
      }
    }
    appendLine()
    appendLine("${PREFIX_EVENT_ID}${id}")
    appendLine("${PREFIX_LAST_UPDATED}${lastUpdated}")
    if (descriptionByline != null) {
      appendLine(descriptionByline)
    }
  }

  private suspend fun SourceEvent.location(): Location? {
    val resolved = locationService.resolveSpondLocation(address)
    return if (resolved != null) {
      resolved
    } else {
      log.w("Unable to resolve location from address $address for $identity")
      null
    }
  }

  private fun SourceEvent.matchInfo(subGroup: SubGroup): MatchInfo {
    val team = subGroupsToTeams[subGroup.name]
    val opponent = when {
      teamA == team -> teamB
      teamB == team -> teamA
      else -> throw IllegalStateException("Neither teamA=$teamA nor teamB=$teamB match the team=$team for source event $identity")
    }
    val base = MatchInfo(
      type = if (homeMatch) MatchType.Home else MatchType.Away,
      teamName = subGroup.name,
      teamColour = subGroup.color,
      opponentName = opponent,
      opponentColour = opponentColourHex,
    )
    return if (result == null) {
      base
    } else {
      val teamId = if (teamA == team) teamAId else teamBId
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
    private const val PREFIX_LAST_UPDATED = "Last updated: "

    fun extractSourceEventId(event: Event): String? {
      return event.description
        ?.split("\n")
        ?.first { it.startsWith(PREFIX_EVENT_ID) }
        ?.removePrefix(PREFIX_EVENT_ID)
    }
  }
}
