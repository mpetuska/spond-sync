package spond.data.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

typealias EventId = String

@Serializable
data class Event(
  override val id: EventId,
  @SerialName("heading")
  val name: String,
) : Identifiable, WithId {
  override val identity: String
    get() = "Event(id=$id, name=$name)"
}
