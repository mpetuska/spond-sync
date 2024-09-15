package spond.data.event

import kotlinx.serialization.Serializable

@Serializable
data class MatchInfo(
  val teamName: String,
  val opponentName: String,
  val type: MatchType,
  val teamColour: String? = null,
  val opponentColour: String?,
  val scoresFinal: Boolean? = null,
  val scoresPublic: Boolean? = null,
  val scoresSet: Boolean? = null,
  val scoresSetEver: Boolean? = null,
)
