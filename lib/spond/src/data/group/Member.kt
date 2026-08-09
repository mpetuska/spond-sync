package dev.petuska.spond.sync.spond.data.group

import dev.petuska.spond.sync.spond.data.WithId
import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.Serializable

typealias MemberId = String

@Serializable
data class Member(override val id: MemberId, val subGroups: List<SubGroupId>) :
  Identifiable, WithId {
  override val identity: String
    get() = "Member(id=$id)"
}
