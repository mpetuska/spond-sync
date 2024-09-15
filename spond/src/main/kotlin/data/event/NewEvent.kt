package spond.data.event

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import spond.data.location.Location
import utils.Identifiable
import kotlin.time.Duration.Companion.hours

@Serializable
data class NewEvent(
  @SerialName("heading")
  val name: String,
  val matchInfo: MatchInfo,
  val location: Location,
  val recipients: Recipients.New,
  @SerialName("startTimestamp")
  val start: Instant,
  @SerialName("endTimestamp")
  val end: Instant,
  val inviteTime: Instant? = null,
  val rsvpDate: Instant? = null,
  val maxAccepted: UInt? = null,
  val description: String? = null,
  val commentsDisabled: Boolean = false,
  val participantsHidden: Boolean = false,
  val type: String = "EVENT",
  val spondType: String = "event",
  val visibility: String = "ALL",
  val autoReminderType: AutoReminderType = AutoReminderType.Disabled,
) : Identifiable {
  override val identity: String
    get() = "NewEvent(name=$name)"
}
