package worker.data

import kotlinx.datetime.Instant
import utils.Identifiable

data class SourceEvent(
  val provider: String,
  val name: String,
  val description: String,
  val start: Instant,
  val end: Instant,
  val host: String,
  val teamA: String,
  val teamB: String,
  val homeMatch: Boolean,
  val address: String,
): Identifiable {
  override val identity: String = "SourceEvent(provider=$provider, name=$name)"
}
