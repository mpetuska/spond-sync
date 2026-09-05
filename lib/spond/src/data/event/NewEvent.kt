package dev.petuska.spond.sync.spond.data.event

import dev.petuska.spond.sync.spond.data.group.ProfileId
import dev.petuska.spond.sync.spond.data.location.Location
import dev.petuska.spond.sync.utils.Identifiable
import io.ktor.http.Url
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewEvent(
  @SerialName("heading") val name: String,
  val location: Location?,
  val recipients: Recipients.New,
  @SerialName("startTimestamp") @Serializable val start: Instant,
  @SerialName("endTimestamp") @Serializable val end: Instant,
  val matchInfo: MatchInfo? = null,
  @Serializable val inviteTime: Instant? = null,
  @Serializable val rsvpDate: Instant? = null,
  val owners: List<Owner>? = null,
  val maxAccepted: UInt? = null,
  val description: String? = null,
  val picture: Url? = null,
  val commentsDisabled: Boolean = false,
  val participantsHidden: Boolean = false,
  val type: String = "EVENT",
  val spondType: String = "event",
  val visibility: String = "ALL",
  val autoReminderType: AutoReminderType = AutoReminderType.Disabled,
) : Identifiable {
  override val identity: String
    get() = "NewEvent(name=$name)"

  @Serializable data class Owner(val id: ProfileId)
}
