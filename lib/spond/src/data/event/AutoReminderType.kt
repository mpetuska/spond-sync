package spond.data.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AutoReminderType {
  @SerialName("DISABLED") Disabled,
  @SerialName("REMIND_48H_BEFORE") Before48Hours,
  @SerialName("REMIND_48H_AFTER") After48Hours,
}
