package sportpress

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import sportpress.data.event.Event
import sportpress.data.event.EventId
import sportpress.data.league.LeagueId
import sportpress.data.season.Season
import sportpress.data.season.SeasonId
import sportpress.data.team.Team
import utils.http.paginate
import utils.http.parameters

@Inject
@SingleIn(AppScope::class)
class Sportpress(
  credentials: SportpressCredentials,
  baseClient: HttpClient,
  private val json: Json,
) {
  private val client = buildHttpClient(baseClient, credentials, json)

  /**
   * List seasons.
   *
   * @param limit maximum number of items to be returned in result set.
   */
  fun listSeasons(limit: UInt = 10u): Flow<Season> = paginate {
    url("seasons")
    parameter("per_page", limit)
  }

  /**
   * List teams.
   *
   * @param before limit response to posts published before a given ISO8601 compliant date.
   * @param after limit response to posts published after a given ISO8601 compliant date.
   * @param seasons limit result set to items with specific terms assigned in the seasons taxonomy.
   * @param limit maximum number of items to be returned in result set.
   */
  fun listTeams(
    before: Instant? = null,
    after: Instant? = null,
    seasons: Iterable<SeasonId>? = null,
    limit: UInt = 10u,
  ): Flow<Team> = paginate {
    url("teams")
    parameter("per_page", limit)
    parameter("before", before)
    parameter("after", after)
    parameters("seasons", seasons)
  }

  /**
   * List events.
   *
   * @param before limit response to posts published before a given ISO8601 compliant date.
   * @param after limit response to posts published after a given ISO8601 compliant date.
   * @param include limit result set to specific IDs.
   * @param exclude ensure result set excludes specific IDs.
   * @param seasons limit result set to items with specific terms assigned in the seasons taxonomy.
   * @param leagues limit result set to item…n the leagues taxonomy.
   * @param limit maximum number of items to be returned in result set.
   */
  @Suppress("LongParameterList")
  fun listEvents(
    before: Instant? = null,
    after: Instant? = null,
    include: Iterable<EventId>? = null,
    exclude: Iterable<EventId>? = null,
    seasons: Iterable<SeasonId>? = null,
    leagues: Iterable<LeagueId>? = null,
    limit: UInt = 10u,
  ): Flow<Event> = paginate {
    url("events")
    parameter("per_page", limit)
    parameter("before", before)
    parameter("after", after)
    parameters("include", include)
    parameters("exclude", exclude)
    parameters("seasons", seasons)
    parameters("leagues", leagues)
  }

  //  /**
  //   * List calendars.
  //   * @param before limit response to posts published before a given ISO8601 compliant date.
  //   * @param after limit response to posts published after a given ISO8601 compliant date.
  //   * @param seasons limit result set to items with specific terms assigned in the seasons
  // taxonomy.
  //   * @param limit maximum number of items to be returned in result set.
  //   */
  //  fun listCalendars(
  //    before: Instant? = null,
  //    after: Instant? = null,
  //    seasons: Iterable<SeasonId>? = null,
  //    limit: UInt = 10u,
  //  ): Flow<Calendar> = client.paginate(json) {
  //    url("calendars")
  //    parameter("per_page", limit)
  //    parameter("before", before)
  //    parameter("after", after)
  //    parameter("seasons", seasons)
  //  }
  //
  //  /**
  //   * List leagues.
  //   * @param include limit result set to specific IDs.
  //   * @param exclude ensure result set excludes specific IDs.
  //   * @param limit maximum number of items to be returned in result set.
  //   */
  //  fun listLeagues(
  //    include: Iterable<LeagueId>? = null,
  //    exclude: Iterable<LeagueId>? = null,
  //    limit: UInt = 10u,
  //  ): Flow<League>
  //
  //  /**
  //   * Lists venues.
  //   * @param include limit result set to specific IDs.
  //   * @param exclude ensure result set excludes specific IDs.
  //   * @param hideEmpty whether to hide terms not assigned to any posts.
  //   * @param limit maximum number of items to be returned in result set.
  //   */
  //  fun listVenues(
  //    include: Iterable<VenueId>? = null,
  //    exclude: Iterable<VenueId>? = null,
  //    hideEmpty: Boolean? = null,
  //    limit: UInt = 100u,
  //  ): Flow<Venue>

  private inline fun <reified T> paginate(
    crossinline builder: HttpRequestBuilder.() -> Unit
  ): Flow<T> =
    client.paginate(
      json = json,
      setPage = { _, page -> parameter("page", page) },
      isLastPage = { page, response, _ ->
        page >= (response.headers["x-wp-totalpages"]?.toUInt() ?: 0u)
      },
      builder = builder,
    )
}
