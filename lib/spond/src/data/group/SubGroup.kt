package spond.data.group

import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

typealias SubGroupId = String

typealias SubGroupName = String

@Serializable
data class SubGroup(override val id: SubGroupId, val name: SubGroupName, val color: String) :
  Identifiable, WithId {
  override val identity: String
    get() = "SubGroup(id=$id, name=$name)"
}
