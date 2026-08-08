package core.model

typealias MatchId = String

data class Match(
  val source: String,
  val triangle: TriangleId,
  val id: MatchId,
  val order: UInt,
  val title: String,
  val venue: Venue,
  val start: Time,
  val end: Time,
  val teamA: Team,
  val teamB: Team,
  val result: Result?,
  val lastUpdated: Time,
) {
  val identity = "Match(id=$id, start=$start, order=$order, title=$title)"

  data class Result(val sets: UInt, val teamA: TeamResult, val teamB: TeamResult)

  data class TeamResult(val sets: UInt, val scores: List<UInt>?)

  operator fun contains(team: TeamId) = teamA.id == team || teamB.id == team

  operator fun contains(team: Team) = contains(team.id)
}

operator fun OpenEndRange<Time>.contains(match: Match): Boolean =
  match.start >= start && match.end < endExclusive

operator fun ClosedRange<Time>.contains(match: Match): Boolean =
  match.start >= start && match.end <= endInclusive
