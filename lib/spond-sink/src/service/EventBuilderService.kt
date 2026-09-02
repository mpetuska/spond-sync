package dev.petuska.spond.sync.spond.sink.service

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.TimeSource
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.Team
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.Triangle
import dev.petuska.spond.sync.core.model.Venue
import dev.petuska.spond.sync.spond.data.event.Event
import dev.petuska.spond.sync.spond.data.event.MatchInfo
import dev.petuska.spond.sync.spond.data.event.MatchType
import dev.petuska.spond.sync.spond.data.event.NewEvent
import dev.petuska.spond.sync.spond.data.event.Recipients
import dev.petuska.spond.sync.spond.data.group.Group
import dev.petuska.spond.sync.spond.data.group.MemberId
import dev.petuska.spond.sync.spond.data.group.ProfileId
import dev.petuska.spond.sync.spond.data.group.SubGroup
import dev.petuska.spond.sync.spond.data.location.Location
import dev.petuska.spond.sync.spond.sink.SpondSinkConfig.Events
import dev.petuska.spond.sync.utils.Identifiable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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

@Inject
@SingleIn(ClubScope::class)
class EventBuilderService(
  private val locationService: LocationService,
  private val timeSource: TimeSource,
  private val config: Events,
  logger: Logger,
) {
  private val log = logger.withTag("EventBuilderService")

  suspend fun createTriangle(
    triangle: Triangle,
    group: Group,
    subGroup: SubGroup,
    subGroupMembers: List<MemberId>,
    owners: List<ProfileId>?,
  ): NewEvent {
    return NewEvent(
      name = triangle.id.value,
      description = triangleDescription(triangle),
      location = location(triangle, triangle.venue),
      recipients =
        Recipients.New(
          group = Recipients.NewRecipientsGroup(id = group.id, subGroups = listOf(subGroup.id)),
          groupMembers = subGroupMembers,
        ),
      start = triangle.start.atSink,
      end = triangle.end.atSink,
      inviteTime = inviteTime(triangle.start.atSink),
      rsvpDate = rsvpDate(triangle.start.atSink),
      maxAccepted = config.maxAccepted,
      owners = owners?.map(NewEvent::Owner),
    )
  }

  suspend fun updateTriangle(
    triangle: Triangle,
    base: Event,
    owners: List<ProfileId>?,
  ): Event {
    val updatedOwners = owners?.map { newId ->
      base.owners?.find { it.id == newId } ?: Event.Owner(id = newId, response = null)
    }
    return base.copy(
      name = triangle.id.value,
      description = triangleDescription(triangle),
      location = location(triangle, triangle.venue),
      start = triangle.start.atSink,
      end = triangle.end.atSink,
      inviteTime = inviteTime(triangle.start.atSink) ?: base.inviteTime,
      rsvpDate = rsvpDate(triangle.start.atSink) ?: base.rsvpDate,
      maxAccepted = maxOf(config.maxAccepted, base.acceptedCount),
      owners = updatedOwners,
      json = base.json.toMutableMap().apply { remove("responses") }.let(::JsonObject),
    )
  }

  suspend fun updateMatch(
    triangle: Triangle,
    match: Match,
    team: Team,
    base: Event,
    subGroup: SubGroup,
    owners: List<ProfileId>?,
  ): Event {
    val homeMatch = triangle.host == team
    val updatedOwners = owners?.map { newId ->
      base.owners?.find { it.id == newId } ?: Event.Owner(id = newId, response = null)
    }
    val start = start(triangle, match, team)
    return base.copy(
      name = match.title,
      description = matchDescription(triangle, match),
      matchInfo = matchInfo(match, team, homeMatch, subGroup),
      location = location(match, match.venue),
      start = start,
      end = match.end.atSink,
      inviteTime = inviteTime(start) ?: base.inviteTime,
      rsvpDate = rsvpDate(start) ?: base.rsvpDate,
      maxAccepted = maxOf(config.maxAccepted, base.acceptedCount),
      owners = updatedOwners,
      json = base.json.toMutableMap().apply { remove("responses") }.let(::JsonObject),
    )
  }

  suspend fun createMatch(
    triangle: Triangle,
    match: Match,
    team: Team,
    group: Group,
    subGroup: SubGroup,
    subGroupMembers: List<MemberId>,
    owners: List<ProfileId>?,
  ): NewEvent {
    val homeMatch = triangle.host == team
    val start = start(triangle, match, team)
    return NewEvent(
      name = match.title,
      description = matchDescription(triangle, match),
      matchInfo = matchInfo(match, team, homeMatch, subGroup),
      location = location(match, match.venue),
      recipients =
        Recipients.New(
          group = Recipients.NewRecipientsGroup(id = group.id, subGroups = listOf(subGroup.id)),
          groupMembers = subGroupMembers,
        ),
      start = start,
      end = match.end.atSink,
      inviteTime = inviteTime(start),
      rsvpDate = rsvpDate(start),
      maxAccepted = config.maxAccepted,
      owners = owners?.map(NewEvent::Owner),
    )
  }

  /** Returns `true` if [prop] is different between [old] and [new]. */
  private fun diff(path: String, old: Event, new: Event, prop: Event.() -> Any?): Boolean {
    val oldValue = old.prop()
    val newValue = new.prop()
    val same = oldValue == newValue
    if (same) {
      log.v { "[${old.identity}] Event property at $path matches: old=$oldValue, new=$newValue." }
    } else {
      log.d { "[${old.identity}] Event property at $path differs: old=$oldValue, new=$newValue." }
    }
    return !same
  }

  /** Compares the two events and returns two if [new] has been updated. */
  fun isModified(old: Event, new: Event): Boolean {
    val diffLocation =
      diff("location.address", old, new) { location?.address } ||
        diff("location.feature", old, new) { location?.feature }
    val diffInviteTime = old.inviteTime != null && diff("inviteTime", old, new) { inviteTime }
    return areResultsModified(old, new) ||
      diffLocation ||
      diffInviteTime ||
      diff("start", old, new) { start } ||
      diff("end", old, new) { end } ||
      diff("maxAccepted", old, new) { maxAccepted } ||
      diff("rsvpDate", old, new) { rsvpDate } ||
      diff("lastUpdated", old, new) {
        description?.lines()?.filter { !it.startsWith(PREFIX_LAST_UPDATED) }
      } ||
      diff("owners", old, new) { owners?.map { it.id }?.sorted() }
  }

  fun areResultsModified(old: Event, new: Triangle): Boolean {
    return new.matches.toList().any { match ->
      match.result != null &&
        old.description?.contains(
          buildString {
            appendLine("${match.id}: ${match.teamA.name} vs ${match.teamB.name}")
            appendLine()
            appendResult(match)
          }
        ) != true
    }
  }

  fun areResultsModified(old: Event, new: Event): Boolean {
    if (new.matchInfo?.teamScore == null) {
      log.v { "[${old.identity}] New event has no matchInfo. Assuming no result diff..." }
      return false
    }
    return diff("matchInfo.type", old, new) { matchInfo?.type } ||
      diff("matchInfo.scoresFinal", old, new) { matchInfo?.scoresFinal } ||
      diff("matchInfo.opponentScore", old, new) { matchInfo?.opponentScore } ||
      diff("matchInfo.teamScore", old, new) { matchInfo?.teamScore } ||
      diff("matchInfo.teamColour", old, new) { matchInfo?.teamColour }
  }

  fun extractEventId(event: Event): String? {
    return event
      .metadata()
      ?.first { it.startsWith(PREFIX_EVENT_ID) }
      ?.removePrefix(PREFIX_EVENT_ID)
      ?.trim()
  }

  /** Extracts the metadata lines from the event description. */
  private fun Event.metadata(): List<String>? =
    description?.split("\n")?.dropWhile { it != SEPARATOR_LINE }?.drop(1)

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

  private fun inviteTime(start: Instant): Instant? {
    return (start - config.invitationDayBeforeStart.toInt().days).atNoon().takeIf {
      it > timeSource.now().atSink
    }
  }

  private fun rsvpDate(start: Instant): Instant? {
    return (start - config.rsvpDeadlineBeforeStart.toInt().days).atNoon().takeIf {
      it > timeSource.now().atSink
    }
  }

  private fun Instant.atNoon(): Instant =
    toLocalDateTime(TimeZone.UTC).date.atTime(12, 0).toInstant(TimeZone.UTC)

  private fun triangleDescription(triangle: Triangle) = buildString {
    appendLine(SEPARATOR_LINE)
    appendLine("Triangle ID: ${triangle.id}")
    appendLine("Host: ${triangle.host.name}")

    val matches = triangle.matches.toList().sortedBy { it.order }
    for (match in matches) {
      appendLine()
      appendLine("${match.id}: ${match.teamA.name} vs ${match.teamB.name}")
      appendResult(match)
    }

    val lastUpdated = matches.maxOf { it.lastUpdated }
    val source = matches.maxOf { it.source }
    appendMetadata(
      id = triangle.id.value,
      lastUpdated = lastUpdated,
      source = source,
    )
  }

  private fun matchDescription(triangle: Triangle, match: Match) = buildString {
    appendLine(SEPARATOR_LINE)
    appendLine("${match.id}: ${match.teamA.name} vs ${match.teamB.name}")
    appendLine()
    appendLine("Triangle ID: ${match.triangle}")
    appendLine("Host: ${triangle.host.name}")
    appendResult(match)

    appendMetadata(id = match.id, lastUpdated = match.lastUpdated, source = match.source)
  }

  private fun StringBuilder.appendResult(match: Match) {
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
  }

  private fun StringBuilder.appendMetadata(id: String, lastUpdated: Time, source: String) {
    appendLine()
    appendLine("${PREFIX_EVENT_ID}${id}")
    appendLine("${PREFIX_LAST_UPDATED}${lastUpdated.atSink}")
    appendLine("$PREFIX_SOURCE${source}")
    appendLine(config.descriptionByline)
  }

  private suspend fun location(id: Identifiable, venue: Venue): Location? {
    val resolved = locationService.resolveSpondLocation(venue.address)
    return if (resolved != null) {
      resolved
    } else {
      log.w("[${id.identity}] Unable to resolve location from address ${venue.address}.")
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
    const val SEPARATOR_LINE = "--- DO NOT EDIT BELOW THIS LINE ---"
    const val PREFIX_EVENT_ID = "ID: "
    const val PREFIX_LAST_UPDATED = "Last updated: "
    const val PREFIX_SOURCE = "Source: "
  }
}
