package spond.data.event

import kotlinx.serialization.Serializable

@Serializable
data class MatchScore(
  val teamScore: UInt?,
  val opponentScore: UInt?,
  val scoresPublic: Boolean?,
  val scoresFinal: Boolean?,
)
