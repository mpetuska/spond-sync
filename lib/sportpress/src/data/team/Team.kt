package dev.petuska.spond.sync.sportpress.data.team

import dev.petuska.spond.sync.sportpress.data.RenderedSerializer
import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias TeamId = UInt

@Serializable
data class Team(
  val id: TeamId,
  @SerialName("title") @Serializable(RenderedSerializer::class) val name: String,
  val slug: String,
) : Identifiable {
  override val identity: String
    get() = "Team(id=$id, name=$name)"
}
