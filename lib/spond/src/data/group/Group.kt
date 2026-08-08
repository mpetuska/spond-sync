package spond.data.group

import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

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
