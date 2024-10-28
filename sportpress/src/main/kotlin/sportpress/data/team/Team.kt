package sportpress.data.team

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sportpress.data.RenderedSerializer
import utils.Identifiable

typealias TeamId = UInt

@Serializable
data class Team(
  val id: TeamId,
  @SerialName("title")
  @Serializable(RenderedSerializer::class)
  val name: String,
  val slug: String,
) : Identifiable {
  override val identity: String
    get() = "Team(id=$id, name=$name)"
}
