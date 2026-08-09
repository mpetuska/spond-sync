package dev.petuska.spond.sync.spond.data.event

import dev.petuska.spond.sync.spond.data.group.Group
import dev.petuska.spond.sync.spond.data.group.GroupId
import dev.petuska.spond.sync.spond.data.group.Member
import dev.petuska.spond.sync.spond.data.group.MemberId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Recipients(
  @SerialName("group") val group: Group,
  @SerialName("guardians") val guardians: List<JsonElement> = listOf(),
  @SerialName("groupMembers") val groupMembers: List<Member> = listOf(),
  @SerialName("profiles") val profiles: List<JsonElement> = listOf(),
) {
  @Serializable
  data class New(
    @SerialName("group") val group: NewRecipientsGroup,
    @SerialName("guardians") val guardians: List<JsonElement> = listOf(),
    @SerialName("groupMembers") val groupMembers: List<MemberId> = listOf(),
    @SerialName("profiles") val profiles: List<JsonElement> = listOf(),
  )

  @Serializable
  data class NewRecipientsGroup(
    @SerialName("id") val id: GroupId,
    @SerialName("subGroups") val subGroups: List<GroupId>,
  )
}
