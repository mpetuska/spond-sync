package sportpress.data.event

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sportpress.data.RenderedSerializer
import sportpress.data.team.TeamId
import utils.Identifiable

typealias EventId = UInt

@Serializable
data class Event(
  val id: EventId,
  @SerialName("title")
  @Serializable(RenderedSerializer::class)
  val name: String,
  val teams: List<TeamId>,
  val day: String,
  val link: String,
  val date: LocalDateTime,
  @SerialName("date_gmt")
  val dateGmt: LocalDateTime,
  val modified: LocalDateTime,
  @SerialName("modified_gmt")
  val modifiedGmt: LocalDateTime,
) : Identifiable {
  override val identity: String
    get() = "Event(id=$id, name=$name)"
}
