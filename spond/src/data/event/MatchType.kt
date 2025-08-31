package spond.data.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchType {
  @SerialName("HOME")
  Home,

  @SerialName("AWAY")
  Away
}
