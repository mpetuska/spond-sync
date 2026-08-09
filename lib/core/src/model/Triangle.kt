package dev.petuska.spond.sync.core.model

import dev.petuska.spond.sync.utils.Identifiable
import kotlin.jvm.JvmInline

@JvmInline
value class TriangleId(val value: String) {
  override fun toString(): String = value
}

data class Triangle(
  val id: TriangleId,
  val venue: Venue,
  val start: Time,
  val end: Time,
  val host: Team,
  val teams: Triple<Team, Team, Team>,
  val matches: Triple<Match, Match, Match>,
) : Identifiable {
  override val identity = "Triangle(id=$id,venue=$venue,$start=$start)"
}
