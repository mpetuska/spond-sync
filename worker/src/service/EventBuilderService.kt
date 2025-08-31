package worker.service

import co.touchlab.kermit.Logger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import spond.data.event.*
import spond.data.group.Group
import spond.data.group.MemberId
import spond.data.group.SubGroup
import spond.data.location.Location
import worker.WorkerConfig
import worker.data.SourceEvent
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
      start = start(),
      end = end,
      inviteTime = inviteTime() ?: base.inviteTime,
      rsvpDate = rsvpDate() ?: base.rsvpDate,
      maxAccepted = maxOf(maxAccepted, base.acceptedCount),
      json = base.json.toMutableMap().apply {
        remove("responses")
      }.let(::JsonObject)
    )
  }

  private val Event.acceptedCount: UInt
    get() = try {
      json["responses"]?.jsonObject?.get("acceptedIds")?.jsonArray?.size?.toUInt() ?: 0u
    } catch (_: Exception) {
      0u
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
          id = group.id, subGroups = listOf(subGroup.id)
        ),
        groupMembers = subGroupMembers,
      ),
      start = start(),
      end = end,
      inviteTime = inviteTime(),
      rsvpDate = rsvpDate(),
      maxAccepted = maxAccepted,
    )
  }

  /**
   * Compares the two events and returns two if [new] has been updated.
   */
  fun modified(old: Event, new: Event): Boolean {
    val sameLocation = old.location?.address == new.location?.address && old.location?.feature == new.location?.feature
    val sameResult =
      old.matchInfo?.opponentScore == old.matchInfo?.opponentScore && old.matchInfo?.teamScore == new.matchInfo?.teamScore && old.matchInfo?.teamColour == new.matchInfo?.teamColour
    val sameInviteTime = old.inviteTime == null || old.inviteTime == new.inviteTime
    val same = old.start == new.start && old.end == new.end && old.description?.lines()
      ?.filter { !it.startsWith(PREFIX_LAST_UPDATED) } == new.description?.lines()
      ?.filter { !it.startsWith(PREFIX_LAST_UPDATED) } && sameLocation && sameResult && old.maxAccepted == new.maxAccepted && sameInviteTime && old.rsvpDate == new.rsvpDate
    return !same
  }

  private fun SourceEvent.start(): Instant = when {
    homeMatch && id.endsWith("c") -> start + 1.seconds
    !homeMatch && !id.endsWith("a") -> start + 1.seconds
    else -> start
  }

  private fun SourceEvent.inviteTime(): Instant? {
    return (start - invitationDayBeforeStart.toInt().days).atNoon().takeIf { it > timeService.now() }
  }

  private fun SourceEvent.rsvpDate(): Instant? {
    return (start - rsvpDeadlineBeforeStart.toInt().days).atNoon().takeIf { it > timeService.now() }
  }

  private fun Instant.atNoon(): Instant =
    toLocalDateTime(TimeZone.UTC).date.atTime(12, 0).toInstant(TimeZone.UTC)

  private fun SourceEvent.description() = buildString {
    appendLine("$id: $teamA vs $teamB")
    appendLine()
    appendLine("Triangle ID: $triangleId")
    appendLine("Host: $host")
    appendLine("Source: $source")
    if (result != null) {
      val (scoresA, scoresB, sets) = if (result.winnerId == teamAId) {
        Triple(result.winnerScores, result.loserScores, "${result.winnerSets}-${result.loserSets}")
      } else {
        Triple(result.loserScores, result.winnerScores, "${result.loserSets}-${result.winnerSets}")
      }
      if (scoresA != null && scoresB != null) {
        appendLine()
        appendLine("SETS ($sets):")
        for (set in scoresA.indices) {
          appendLine("  - ${scoresA[set].toString().padStart(2)}:${scoresB[set].toString().padStart(2)}")
        }
      }
    }
    appendLine()
    appendLine("${PREFIX_EVENT_ID}$id")
    appendLine("${PREFIX_LAST_UPDATED}$lastUpdated")
    appendLine(descriptionByline)
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

    @Suppress("UseCheckOrError") val opponent = when {
      teamA == team -> teamB
      teamB == team -> teamA
      else -> throw IllegalStateException(
        "Neither teamA=$teamA nor teamB=$teamB match the team=$team for source event $identity"
      )
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
      return event.description?.split("\n")?.first { it.startsWith(PREFIX_EVENT_ID) }?.removePrefix(PREFIX_EVENT_ID)
        ?.take(5)
    }
  }
}
