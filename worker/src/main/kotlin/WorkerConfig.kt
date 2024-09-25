package worker

import kotlinx.serialization.Serializable


/**
 * @property source one of "volleyzone" or "sportpress" to determine which source should be used.
 * @property teams a mapping between source team names and spond subgroup name.
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
   * @property opponentColourHex a hexadecimal colour value to use for opponent teams.
   * @property invitationDayBeforeStart a number of days before the match start time to send an invitation at.
   * @property rsvpDeadlineBeforeStart a number of days before the match start time to set as RSVP deadline.
   * @property maxAccepted a number of players to allow accepting the invite.
   * @property descriptionByline an optional byline to insert at the bottom of description to identify managed events.
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
    val descriptionByline: String? = null,
  )

  @Serializable
  data class Sportpress(
    val club: String,
    val apiUrl: String,
  )

  /**
   * @property sources a map of named urls to VolleyZone leagues to scrape for events.
   * @property addresses a mapping between VolleyZone event venue address and full address.
   */
  @Serializable
  data class Volleyzone(
    val sources: Map<String, String>,
    val addresses: Map<String, String> = mapOf(),
  )
}
