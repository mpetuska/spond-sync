package spond.data.group

import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

@Serializable
data class SubGroup(
  override val id: GroupId,
  val name: String,
  val color: String,
) : Identifiable, WithId {
  override val identity: String
    get() = "SubGroup(id=$id, name=$name)"
}
