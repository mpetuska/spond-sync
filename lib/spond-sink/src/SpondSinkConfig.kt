package dev.petuska.spond.sync.spond.sink

import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.spond.SpondCredentials
import dev.petuska.spond.sync.spond.data.group.SubGroupName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
  @SerialName("subGroups") private val _subGroups: Map<SubGroupName, SubGroupConfig>,
  val syncResults: Boolean = true,
  val forceUpdate: Boolean = false,
  val events: Events = Events(),
) {
  @Transient
  val subGroups: Map<SubGroupName, SubGroupConfig> = _subGroups.mapValues { (k, v) ->
    v.copy(name = v.name.ifBlank { k })
  }
  @Transient val teams: Map<TeamId, SubGroupConfig> = subGroups.values.associateBy { it.team }

  /**
   * @property name a name of the subgroup on spond. This is injected as a map key if left
   *   unspecified or blank.
   * @property team a team id this subgroup represents on a source system.
   * @property hosts a list of emails (matching their spond account emails) of the subgroup hosts to
   *   be added as hosts to created events. If not specified, a service account will be used.
   */
  @Serializable
  data class SubGroupConfig(
    val name: SubGroupName = "",
    val team: TeamId,
    val hosts: List<String>? = null,
  )

  /**
   * @property mode a mode of the managed events.
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
    val mode: Mode = Mode.Triangles,
    val opponentColourHex: String = "#FFFFFF",
    val invitationDayBeforeStart: UInt = 6u,
    val rsvpDeadlineBeforeStart: UInt = 2u,
    val maxAccepted: UInt = 10u,
    val descriptionByline: String = "Managed event.",
  ) {
    @Serializable
    enum class Mode {
      /**
       * Each triangle gets a single regular type event created. All triangle data is stored in that
       * event. Match results are not getting uploaded.
       */
      @SerialName("triangles") Triangles,
      /**
       * Each triangle gets 2 separate match type events created for each game. All triangle data is
       * stored in both events, and match results get uploaded to matching events.
       */
      @SerialName("matches") Matches,
      /**
       * Each triangle gets a single regular type event created for invites and common triangle
       * data. Additionally, two separate events get created for each match with no invitations
       * (read-only) to store match-specific details. Results get uploaded to such read-only
       * matches.
       */
      @SerialName("both") Both,
    }
  }
}
