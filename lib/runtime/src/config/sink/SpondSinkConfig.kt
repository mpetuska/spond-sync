package dev.petuska.spond.sync.runtime.config.sink

import dev.petuska.spond.sync.runtime.model.SourceId
import dev.petuska.spond.sync.runtime.model.TeamId
import dev.petuska.spond.sync.spond.SpondCredentials
import dev.petuska.spond.sync.spond.data.group.SubGroupName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @property group spond group name
 * @property subGroups a mapping of Spond SubGroups.
 * @property syncResults whether to also update match results for managed events.
 * @property forceUpdate whether to update the events that have not changed.
 * @property api credentials for Spond API.
 */
@Serializable
data class SpondSinkConfig(
  val group: String,
  val api: SpondCredentials,
  val subGroups: Map<SubGroupName, SubGroupConfig>,
  val syncResults: Boolean = true,
  val forceUpdate: Boolean = false,
) {
  /**
   * @property team a team id this subgroup represents on a source system.
   * @property sources a list of source ids to filter source events from.
   * @property hosts a list of emails (matching their spond account emails) of the subgroup hosts to
   *   be added as hosts to created events. If not specified, a service account will be used.
   * @property events events config for this subGroup.
   */
  @Serializable
  data class SubGroupConfig(
    val team: TeamId,
    val sources: List<SourceId>,
    val hosts: List<String>? = null,
    val events: Events = Events(),
  )

  /**
   * @property mode a mode of the managed events.
   * @property opponentColourHex a hexadecimal colour value to use for opponent teams.
   * @property invitationDaysBeforeStart a number of days before the match start time to send an
   *   invitation at.
   * @property rsvpDeadlineBeforeStart a number of days before the match start time to set as RSVP
   *   deadline.
   * @property maxAccepted a number of players to allow accepting the invite.
   * @property descriptionByline an optional byline to insert at the bottom of description to
   *   identify managed events.
   */
  @Serializable
  data class Events(
    val mode: Mode = Mode.Matches,
    val opponentColourHex: String = "#FFFFFF",
    val invitationDaysBeforeStart: UInt = 6u,
    val rsvpDeadlineBeforeStart: UInt = 2u,
    val maxAccepted: UInt = 10u,
    val descriptionByline: String = "Managed event.",
  ) {
    @Serializable
    enum class Mode {
      /**
       * Each triangle gets 2 separate match type events created for each game. All triangle data is
       * stored in both events, and match results get uploaded to matching events.
       */
      @SerialName("matches") Matches,
      /**
       * Each triangle gets a single regular type event created. All triangle data is stored in that
       * event. Match results are not getting uploaded.
       */
      @SerialName("triangles") Triangles,
    }
  }
}
