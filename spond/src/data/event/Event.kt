package spond.data.event

import kotlinx.serialization.Contextual
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import spond.data.WithId
import spond.data.location.Location
import utils.Identifiable
import utils.serialization.InstantSerializer
import utils.serialization.PreservingJsonSerializer

typealias EventId = String

@OptIn(ExperimentalSerializationApi::class)
@Serializable(Event.Serializer::class)
@KeepGeneratedSerializer
data class Event(
  override val id: EventId,
  @SerialName("heading")
  val name: String,
  @SerialName("startTimestamp")
  @Serializable(InstantSerializer::class)
  val start: Instant,
  @SerialName("endTimestamp")
  @Serializable(InstantSerializer::class)
  val end: Instant,
  val description: String? = null,
  val matchInfo: MatchInfo? = null,
  val location: Location? = null,
  @Serializable(InstantSerializer::class)
  val inviteTime: Instant? = null,
  @Serializable(InstantSerializer::class)
  val rsvpDate: Instant? = null,
  val maxAccepted: UInt? = null,
  @SerialName("#json")
  val json: JsonObject,
) : Identifiable, WithId {
  override val identity: String
    get() = "Event(id=$id, start=$start, name=$name)"

  internal object Serializer : PreservingJsonSerializer<Event>(generatedSerializer())
}
