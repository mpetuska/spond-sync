package dev.petuska.spond.sync.spond.data.event

import dev.petuska.spond.sync.spond.data.WithId
import dev.petuska.spond.sync.spond.data.group.MemberId
import dev.petuska.spond.sync.spond.data.location.Location
import dev.petuska.spond.sync.utils.Identifiable
import dev.petuska.spond.sync.utils.serialization.PreservingJsonSerializer
import kotlin.time.Instant
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

typealias EventId = String

@Serializable(Event.Serializer::class)
@KeepGeneratedSerializer
data class Event(
  override val id: EventId,
  @SerialName("heading") val name: String,
  @SerialName("startTimestamp") @Serializable val start: Instant,
  @SerialName("endTimestamp") @Serializable val end: Instant,
  val description: String? = null,
  val matchInfo: MatchInfo? = null,
  val location: Location? = null,
  @Serializable val inviteTime: Instant? = null,
  @Serializable val rsvpDate: Instant? = null,
  val maxAccepted: UInt? = null,
  val owners: List<Owner>? = null,
  val type: String? = null,
  @SerialName(PreservingJsonSerializer.KEY) val json: JsonObject,
) : Identifiable, WithId {
  override val identity: String
    get() = "Event(id=$id, start=$start, name=$name)"

  @Serializable(Owner.Serializer::class)
  @KeepGeneratedSerializer
  data class Owner(
    val id: MemberId,
    val response: String?,
    @SerialName(PreservingJsonSerializer.KEY) val json: JsonObject = JsonObject(emptyMap()),
  ) {
    internal object Serializer : PreservingJsonSerializer<Owner>(generatedSerializer())
  }

  internal object Serializer : PreservingJsonSerializer<Event>(generatedSerializer())
}
