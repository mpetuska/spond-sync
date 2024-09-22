package worker.data

import kotlinx.datetime.Instant
import utils.Identifiable

data class SourceEvent(
  val provider: String,
  val id: String,
  val name: String,
  val description: String,
  val start: Instant,
  val end: Instant,
  val host: String,
  val teamA: String,
  val teamAId: UInt,
  val teamB: String,
  val teamBId: UInt,
  val homeMatch: Boolean,
  val address: String,
  val result: Result?,
) : Identifiable {
  override val identity: String = "SourceEvent(provider=$provider, name=$name)"

  data class Result(
    val winnerId: UInt,
    val loserId: UInt,
    val sets: UInt,
    val winnerSets: UInt,
    val loserSets: UInt,
    val winnerScores: List<UInt>?,
    val loserScores: List<UInt>?,
  )
}
