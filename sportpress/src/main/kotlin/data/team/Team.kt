package sportpress.data.team

import kotlinx.serialization.Serializable
import utils.Identifiable

typealias TeamId = UInt

@Serializable
data class Team(
  val id: TeamId,
  val slug: String,
) : Identifiable {
  override val identity: String
    get() = "Team(id=$id)"
}
