package spond

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import spond.data.WithId
import spond.data.event.Event
import spond.data.event.EventId
import spond.data.event.MatchScore
import spond.data.event.NewEvent
import spond.data.group.Group
import spond.data.group.GroupId
import spond.data.location.AutocompleteLocation
import spond.data.location.Location
import spond.data.location.LocationId
import utils.http.paginate
import utils.tokens.TokenHandler
import javax.inject.Inject

@Suppress("TooManyFunctions")
class Spond @Inject constructor(
  credentials: SpondCredentials,
  tokenHandler: TokenHandler,
  baseClient: HttpClient,
  private val json: Json,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("Spond")
  private val client = buildHttpClient(baseClient, credentials, json, log, tokenHandler)

  /**
   * Get details of all group memberships and all members of those groups.
   */
  fun listGroups(): Flow<Group> = paginate(UInt.MAX_VALUE) {
    url("groups")
  }

  /**
   * Get existing group.
   *
   * @param id the [Group.id] of the group to fetch.
   */
  suspend fun getGroup(id: GroupId): Group = client.get("group/$id").body()

  /**
   * Delete a group.
   *
   * @param id the [Group.id] of the group to be deleted
   */
  suspend fun deleteGroup(id: GroupId) {
    client.delete("group/$id")
  }

//  /**
//   * Create a new group.
//   *
//   * @param groupId the id of parent group for this subgroup.
//   * @param newSubGroup the details of the subgroup to be created.
//   */
//  suspend fun createSubGroup(groupId: GroupId, newSubGroup: NewSubGroup): SubGroup =
//    client.post("group/$groupId/subgroups") {
//      setBody(newSubGroup)
//    }.body()

  /**
   * Get events.
   * Subject to authenticated user's access.
   *
   * @param groupId only include events in this group.
   * @param subGroupId only include events in this subgroup.
   * @param includeScheduled include scheduled events.
   * @param includeHidden include hidden/cancelled events.
   * @param includeRepeating include repeating events.
   * @param includeComments return event comments.
   * @param addProfileInfo return full profileInfos.
   * @param minStart only include events which start after or at this datetime.
   * @param maxStart only include events which start before or at this datetime.
   * @param minEnd only include events which end after or at this datetime.
   * @param maxEnd only include events which end before or at this datetime.
   * @param descending should the returned events be sorted from latest to earliest
   * @param limit maximum number of events to return
   */
  @Suppress("LongParameterList")
  fun listEvents(
    groupId: GroupId? = null,
    subGroupId: GroupId? = null,
    includeScheduled: Boolean = false,
    includeHidden: Boolean = false,
    includeRepeating: Boolean = false,
    includeComments: Boolean = false,
    addProfileInfo: Boolean = false,
    minStart: Instant? = null,
    maxStart: Instant? = null,
    minEnd: Instant? = null,
    maxEnd: Instant? = null,
    descending: Boolean = false,
    limit: UInt = 20u,
  ): Flow<Event> = paginate(limit) {
    url("sponds")
    parameter("groupId", groupId)
    parameter("subGroupId", subGroupId)
    parameter("scheduled", includeScheduled)
    parameter("includeHidden", includeHidden)
    parameter("includeComments", includeComments)
    parameter("excludeRepeating", !includeRepeating)
    parameter("addProfileInfo", addProfileInfo)
    parameter("minStartTimestamp", minStart)
    parameter("maxStartTimestamp", maxStart)
    parameter("minEndTimestamp", minEnd)
    parameter("maxEndTimestamp", maxEnd)
    parameter("order", if (descending) "desc" else "asc")
    parameter("max", limit)
  }

  /**
   * Create a new event.
   *
   * @param newEvent the details of the event to be created.
   */
  suspend fun createEvent(newEvent: NewEvent): Event = client.post("sponds") {
    setBody(newEvent)
  }.body()

  /**
   * Get existing event.
   *
   * @param id the [Event.id] of the event to fetch.
   * @param includeComments should comment data be included in the response
   * @param addProfileInfo should profile info data be included in the response
   */
  suspend fun getEvent(id: EventId, includeComments: Boolean = false, addProfileInfo: Boolean = false): Event =
    client.get("sponds/$id") {
      parameter("includeComments", includeComments)
      parameter("addProfileInfo", addProfileInfo)
    }.body()

  /**
   * Update the existing event
   *
   * @param updatedEvent new event state
   * @param clearResponses require recipients to answer again
   * @param sendUpdate notify recipients about the changes
   */
  suspend fun updateEvent(
    updatedEvent: Event,
    clearResponses: Boolean = false,
    sendUpdate: Boolean = true,
  ): Event = client.post("sponds/${updatedEvent.id}") {
    header("X-Spond-ClearResponses", clearResponses)
    header("X-Spond-SendUpdate", sendUpdate)
    setBody(updatedEvent)
  }.body()

  /**
   * Cancel existing event.
   *
   * @param id the [Event.id] of the event to cancel.
   * @param quiet should the attendees be notified.
   * @param reason cancellation reason to show the attendees.
   */
  suspend fun cancelEvent(id: EventId, quiet: Boolean = false, reason: String? = null) {
    client.delete("sponds/$id") {
      parameter("quiet", quiet)
      reason?.let { parameter("reason", it) }
    }
  }

  /**
   * Update a given match score.
   * @param id the [Event.id] of the event to update the score at.
   * @param score the updates score data.
   */
  suspend fun updateMatchScore(id: EventId, score: MatchScore): Event = client.post("sponds/$id/matchUpdate") {
    setBody(score)
  }.body()

  /**
   * Autocompletes location candidates from a given [search] term.
   *
   * @param search term to lookup.
   */
  fun autocompleteLocation(search: String): Flow<AutocompleteLocation> = paginate(UInt.MAX_VALUE) {
    url("locations/autocomplete")
    parameter("keyword", search)
    parameter("sessionToken", null)
  }

  /**
   * Get location.
   * @param id the id of the location to fetch.
   */
  suspend fun getLocation(id: LocationId): Location = client.get {
    url("location/$id")
  }.body()

  private inline fun <reified T : WithId> paginate(
    limit: UInt,
    crossinline builder: HttpRequestBuilder.() -> Unit,
  ): Flow<T> = client.paginate(
    json = json,
    setPage = { lastValue, _ ->
      parameter("prevId", lastValue?.id)
    },
    isLastPage = { _, _, pageValueCount ->
      // TODO this should be `pageValueCount == 0u` but for some reason spond is not paginating via `prevId`
      pageValueCount < limit
    },
    builder = builder,
  )
}
