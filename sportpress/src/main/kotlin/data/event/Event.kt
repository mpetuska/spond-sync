package sportpress.data.event

import kotlinx.serialization.Serializable
import sportpress.data.team.TeamId
import utils.Identifiable

typealias EventId = UInt

@Serializable
data class Event(
  val id: EventId,
  val teams: List<TeamId>,
) : Identifiable {
  override val identity: String
    get() = "Event(id=$id)"
}
