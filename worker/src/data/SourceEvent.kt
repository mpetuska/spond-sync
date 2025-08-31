package worker.data

import kotlin.time.Instant
import utils.Identifiable

data class SourceEvent(
  val provider: String,
  val source: String,
  val triangleId: String,
  val id: String,
  val name: String,
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
  val lastUpdated: Instant,
) : Identifiable {
  override val identity: String = "SourceEvent(provider=$provider, id=$id, name=$name)"

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
