package dev.petuska.spond.sync.spond.data.group

import dev.petuska.spond.sync.spond.data.WithId
import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.Serializable

typealias SubGroupId = String

typealias SubGroupName = String

@Serializable
data class SubGroup(override val id: SubGroupId, val name: SubGroupName, val color: String) :
  Identifiable, WithId {
  override val identity: String
    get() = "SubGroup(id=$id, name=$name)"
}
