package spond.data.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import spond.data.group.GroupId
import spond.data.group.Group
import spond.data.group.Member
import spond.data.group.MemberId

@Serializable
data class Recipients(
  @SerialName("group")
  val group: Group,
  @SerialName("guardians")
  val guardians: List<JsonElement> = listOf(),
  @SerialName("groupMembers")
  val groupMembers: List<Member> = listOf(),
  @SerialName("profiles")
  val profiles: List<JsonElement> = listOf()
) {
  @Serializable
  data class New(
    @SerialName("group")
    val group: NewRecipientsGroup,
    @SerialName("guardians")
    val guardians: List<JsonElement> = listOf(),
    @SerialName("groupMembers")
    val groupMembers: List<MemberId> = listOf(),
    @SerialName("profiles")
    val profiles: List<JsonElement> = listOf()
  )

  @Serializable
  data class NewRecipientsGroup(
    @SerialName("id")
    val id: GroupId,
    @SerialName("subGroups")
    val subGroups: List<GroupId>,
  )
}
