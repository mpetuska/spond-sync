package service

import co.touchlab.kermit.Logger
import core.TimeSource
import core.di.ClubScope
import core.model.Match
import core.model.Team
import core.model.Triangle
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.data.event.Event
import spond.data.event.MatchInfo
import spond.data.event.MatchType
import spond.data.event.NewEvent
import spond.data.event.Recipients
import spond.data.group.Group
import spond.data.group.MemberId
import spond.data.group.SubGroup
import spond.data.location.Location
import spond.sink.SpondSinkConfig.Events

@Inject
@SingleIn(ClubScope::class)
class EventBuilderService(
  private val locationService: LocationService,
  private val timeSource: TimeSource,
  private val config: Events,
  logger: Logger,
) {
  private val log = logger.withTag("EventBuilderService")

  suspend fun updateEvent(
    triangle: Triangle,
    match: Match,
    team: Team,
    base: Event,
    subGroup: SubGroup,
  ): Event {
    val homeMatch = triangle.host == team
    return base.copy(
      name = match.title,
      description = description(triangle, match),
      matchInfo = matchInfo(match, team, homeMatch, subGroup),
      location = location(match),
      start = start(triangle, match, team),
      end = match.end.atSink,
      inviteTime = inviteTime(match) ?: base.inviteTime,
      rsvpDate = rsvpDate(match) ?: base.rsvpDate,
      maxAccepted = maxOf(config.maxAccepted, base.acceptedCount),
      json = base.json.toMutableMap().apply { remove("responses") }.let(::JsonObject),
    )
  }

  suspend fun createEvent(
    triangle: Triangle,
    match: Match,
    team: Team,
    group: Group,
    subGroup: SubGroup,
    subGroupMembers: List<MemberId>,
  ): NewEvent {
    val homeMatch = triangle.host == team
    return NewEvent(
      name = match.title,
      description = description(triangle, match),
      matchInfo = matchInfo(match, team, homeMatch, subGroup),
      location = location(match),
      recipients =
        Recipients.New(
          group = Recipients.NewRecipientsGroup(id = group.id, subGroups = listOf(subGroup.id)),
          groupMembers = subGroupMembers,
        ),
      start = start(triangle, match, team),
      end = match.end.atSink,
      inviteTime = inviteTime(match),
      rsvpDate = rsvpDate(match),
      maxAccepted = config.maxAccepted,
    )
  }

  /** Compares the two events and returns two if [new] has been updated. */
  fun isModified(old: Event, new: Event): Boolean {
    val sameLocation =
      old.location?.address == new.location?.address &&
        old.location?.feature == new.location?.feature
    val sameResult =
      old.matchInfo?.type == new.matchInfo?.type &&
        old.matchInfo?.scoresFinal == new.matchInfo?.scoresFinal &&
        old.matchInfo?.opponentScore == new.matchInfo?.opponentScore &&
        old.matchInfo?.teamScore == new.matchInfo?.teamScore &&
        old.matchInfo?.teamColour == new.matchInfo?.teamColour
    val sameInviteTime = old.inviteTime == null || old.inviteTime == new.inviteTime
    val same =
      old.start == new.start &&
        old.end == new.end &&
        old.description?.lines()?.filter { !it.startsWith(PREFIX_LAST_UPDATED) } ==
          new.description?.lines()?.filter { !it.startsWith(PREFIX_LAST_UPDATED) } &&
        sameLocation &&
        sameResult &&
        old.maxAccepted == new.maxAccepted &&
        sameInviteTime &&
        old.rsvpDate == new.rsvpDate
    return !same
  }

  fun extractMatchId(event: Event): String? {
    return event.description
      ?.split("\n")
      ?.first { it.startsWith(PREFIX_EVENT_ID) }
      ?.removePrefix(PREFIX_EVENT_ID)
      ?.take(5)
  }

  private val Event.acceptedCount: UInt
    get() =
      try {
        json["responses"]?.jsonObject?.get("acceptedIds")?.jsonArray?.size?.toUInt() ?: 0u
      } catch (_: Exception) {
        0u
      }

  private fun start(triangle: Triangle, match: Match, team: Team): Instant {
    val homeMatch = triangle.host == team
    return when {
      homeMatch && match.order == 3u -> match.start + 1.seconds
      !homeMatch && match.order != 1u -> match.start + 1.seconds
      else -> match.start
    }.atSink
  }

  private fun inviteTime(match: Match): Instant? {
    return (match.start - config.invitationDayBeforeStart.toInt().days).atSink.atNoon().takeIf {
      it > timeSource.now().atSink
    }
  }

  private fun rsvpDate(match: Match): Instant? {
    return (match.start - config.rsvpDeadlineBeforeStart.toInt().days).atSink.atNoon().takeIf {
      it > timeSource.now().atSink
    }
  }

  private fun Instant.atNoon(): Instant =
    toLocalDateTime(TimeZone.UTC).date.atTime(12, 0).toInstant(TimeZone.UTC)

  private fun description(triangle: Triangle, match: Match) = buildString {
    appendLine("${match.id}: ${match.teamA.name} vs ${match.teamB.name}")
    appendLine()
    appendLine("Triangle ID: ${match.triangle}")
    appendLine("Host: ${triangle.host.name}")
    appendLine("Source: ${match.source}")
    val result = match.result
    if (result != null) {
      appendLine()
      appendLine("SETS (${result.teamA.sets}-${result.teamB.sets}):")
      for (set in 0..<result.sets.toInt()) {
        val setsA = result.teamA.scores?.getOrElse(set) { "--" }.toString().padStart(2)
        val setsB = result.teamB.scores?.getOrElse(set) { "--" }.toString().padStart(2)
        appendLine("  ${set + 1}) $setsA:$setsB")
      }
    }
    appendLine()
    appendLine("${PREFIX_EVENT_ID}${match.id}")
    appendLine("${PREFIX_LAST_UPDATED}${match.lastUpdated}")
    appendLine(config.descriptionByline)
  }

  private suspend fun location(match: Match): Location? {
    val resolved = locationService.resolveSpondLocation(match.venue.address)
    return if (resolved != null) {
      resolved
    } else {
      log.w("[${match.identity}] Unable to resolve location from address ${match.venue.address}.")
      null
    }
  }

  private fun matchInfo(
    match: Match,
    team: Team,
    homeMatch: Boolean,
    subGroup: SubGroup,
  ): MatchInfo {
    @Suppress("UseCheckOrError")
    val opponent =
      when {
        match.teamA == team -> match.teamB
        match.teamB == team -> match.teamA
        else ->
          throw IllegalStateException(
            "[${match.identity}] Neither teamA=${match.teamA} nor teamB=${match.teamB} match the team=$team."
          )
      }
    val base =
      MatchInfo(
        type = if (homeMatch) MatchType.Home else MatchType.Away,
        teamName = subGroup.name,
        teamColour = subGroup.color,
        opponentName = opponent.name,
        opponentColour = config.opponentColourHex,
      )
    val result = match.result
    return if (result == null) {
      base
    } else {
      val (teamResult, opponentResult) =
        if (match.teamA == team) result.teamA to result.teamB else result.teamB to result.teamA
      base.copy(
        scoresSet = true,
        scoresSetEver = true,
        scoresPublic = true,
        scoresFinal = true,
        teamScore = teamResult.sets,
        opponentScore = opponentResult.sets,
      )
    }
  }

  private companion object {
    const val PREFIX_EVENT_ID = "Event ID: "
    const val PREFIX_LAST_UPDATED = "Last updated: "
  }
}
