package dev.petuska.spond.sync.spond.data.group

import dev.petuska.spond.sync.spond.data.WithId
import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.Serializable

typealias MemberId = String

@Serializable
data class Member(
  override val id: MemberId,
  val clubMembershipId: String,
  val subGroups: List<SubGroupId>,
  val firstName: String,
  val lastName: String,
  val profile: Profile? = null,
  val email: String? = null,
) : Identifiable, WithId {
  override val identity: String
    get() = "Member(id=$id)"
}
