package dev.petuska.spond.sync.spond.data.group

import dev.petuska.spond.sync.spond.data.WithId
import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.Serializable

typealias GroupId = String

@Serializable
data class Group(
  override val id: GroupId,
  val name: String,
  val subGroups: List<SubGroup> = listOf(),
  val members: List<Member> = listOf(),
) : Identifiable, WithId {
  override val identity: String
    get() = "Group(id=$id, name=$name)"
}
