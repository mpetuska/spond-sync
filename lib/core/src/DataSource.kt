package core

import core.model.Match
import core.model.Time
import kotlinx.coroutines.flow.Flow

fun interface DataSource {
  fun listMatches(from: Time, until: Time): Flow<Match>
}
