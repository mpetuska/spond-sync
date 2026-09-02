package dev.petuska.spond.sync.runtime.model

import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.datetime.LocalTime

typealias MatchId = String

data class Match(
  val id: MatchId,
  val title: String,
  val start: Time,
  val startTime: LocalTime,
  val end: Time,
  val venue: Venue,
  val teamA: Team,
  val teamB: Team,
  val lastUpdated: Time,
  val source: String,
  val order: UInt,
  val result: Result?,
) : Identifiable {
  override val identity = "Match(id=$id, start=$start, order=$order, title=$title)"

  data class Result(val sets: UInt, val teamA: TeamResult, val teamB: TeamResult)

  data class TeamResult(val sets: UInt, val scores: List<UInt>?)

  operator fun contains(team: TeamId) = teamA.id == team || teamB.id == team

  operator fun contains(team: Team) = contains(team.id)
}

operator fun OpenEndRange<Time>.contains(match: Match): Boolean =
  match.start >= start && match.end < endExclusive

operator fun ClosedRange<Time>.contains(match: Match): Boolean =
  match.start >= start && match.end <= endInclusive
