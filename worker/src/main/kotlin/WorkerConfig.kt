package worker

import kotlinx.serialization.Serializable


/**
 * @property teams a mapping between source team names and spond subgroup name.
 */
@Serializable
data class WorkerConfig(
  val spond: Spond,
  val sportpress: Sportpress,
  val teams: Map<String, String>,
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
}
