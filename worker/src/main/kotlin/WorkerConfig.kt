package worker

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * @property spond spond sink config
 * @property teams a mapping between source team names and spond subgroup name.
 * @property source one of "volleyzone" or "sportpress" to determine which source should be used.
 * @property sportpress sportpress source config
 * @property volleyzone volleyzone source config
 * @property yearOffset how many years source data should be offset when fetching events
 * @property debug turns on extra logging with sensitive information
 */
@Serializable
data class WorkerConfig(
  val spond: Spond,
  val teams: Map<String, String>,
  val source: String,
  val sportpress: Sportpress? = null,
  val volleyzone: Volleyzone? = null,
  val yearOffset: Int = 0,
  val debug: Boolean = false,
) {
  /**
   * An inverse of [teams] which maps Spond subGroup to source team name.
   */
  @Transient
  val subGroups = teams.map { (k, v) -> v to k }.toMap()

  /**
   * @property group spond group name
   * @property username spond username
   * @property password spond password
   * @property apiUrl spond api url
   * @property opponentColourHex a hexadecimal colour value to use for opponent teams.
   * @property invitationDayBeforeStart a number of days before the match start time to send an invitation at.
   * @property rsvpDeadlineBeforeStart a number of days before the match start time to set as RSVP deadline.
   * @property maxAccepted a number of players to allow accepting the invite.
   * @property descriptionByline an optional byline to insert at the bottom of description to identify managed events.
   * @property syncResults whether to also update match results for managed events.
   */
  @Serializable
  data class Spond(
    val group: String,
    val username: String,
    val password: String,
    val apiUrl: String = "https://api.spond.com/core/v1",
    val opponentColourHex: String = "#FFFFFF",
    val invitationDayBeforeStart: UInt = 7u,
    val rsvpDeadlineBeforeStart: UInt = 2u,
    val maxAccepted: UInt = 10u,
    val descriptionByline: String = "Managed event.",
    val syncResults: Boolean = false,
  )

  @Serializable
  data class Sportpress(
    val club: String,
    val apiUrl: String,
  )

  /**
   * @property leagues a map of named urls to VolleyZone leagues to scrape for events.
   * @property addresses a mapping between VolleyZone event venue address and full address.
   * @property attemptToFixBrokenTriangles try to fix broken triangles
   *   (where more than 3 events refer to the same triangle ID), by building triangles via `date + time + venue` keys.
   */
  @Serializable
  data class Volleyzone(
    val leagues: Map<String, String>,
    val addresses: Map<String, String> = mapOf(),
    val attemptToFixBrokenTriangles: Boolean = false,
  )
}
