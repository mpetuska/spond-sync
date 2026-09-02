package dev.petuska.spond.sync.runtime.model

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
  val teams: List<Team>,
  val matches: List<Match>,
) : Identifiable {
  override val identity = "Triangle(id=$id,venue=$venue,$start=$start)"

  init {
    require(teams.size == 3)
    require(matches.size == 3)
  }

  operator fun contains(team: Team) = contains(team.id)

  operator fun contains(team: TeamId) = teams.any { it.id == team }
}
