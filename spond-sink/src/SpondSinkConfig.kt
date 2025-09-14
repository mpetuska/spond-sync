package spond.sink

import core.model.TeamId
import kotlinx.serialization.Serializable
import spond.SpondCredentials
import spond.data.group.SubGroupName

/**
 * @property group spond group name
 * @property subGroups a mapping of [spond.data.group.SubGroupName] to [core.model.TeamId] on
 *   source.
 * @property syncResults whether to also update match results for managed events.
 * @property forceUpdate whether to update the events that have not changed.
 * @property api credentials for Spond API.
 */
@Serializable
data class SpondSinkConfig(
  val group: String,
  val api: SpondCredentials,
  val subGroups: Map<SubGroupName, TeamId>,
  val syncResults: Boolean = true,
  val forceUpdate: Boolean = false,
  val events: Events = Events(),
) {

  /**
   * @property opponentColourHex a hexadecimal colour value to use for opponent teams.
   * @property invitationDayBeforeStart a number of days before the match start time to send an
   *   invitation at.
   * @property rsvpDeadlineBeforeStart a number of days before the match start time to set as RSVP
   *   deadline.
   * @property maxAccepted a number of players to allow accepting the invite.
   * @property descriptionByline an optional byline to insert at the bottom of description to
   *   identify managed events.
   */
  @Serializable
  data class Events(
    val opponentColourHex: String = "#FFFFFF",
    val invitationDayBeforeStart: UInt = 6u,
    val rsvpDeadlineBeforeStart: UInt = 2u,
    val maxAccepted: UInt = 10u,
    val descriptionByline: String = "Managed event.",
  )
}
