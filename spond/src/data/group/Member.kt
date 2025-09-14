package spond.data.group

import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

typealias MemberId = String

@Serializable
data class Member(override val id: MemberId, val subGroups: List<SubGroupId>) :
  Identifiable, WithId {
  override val identity: String
    get() = "Member(id=$id)"
}
