package dev.petuska.spond.sync.runtime.source.volleyzone

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.nodes.Element
import dev.petuska.spond.sync.runtime.model.Match
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant

@Inject
@SingleIn(AppScope::class)
class VolleyZoneParser(@Named("addresses") private val addresses: Map<String, String>) {
  private val log = Logger.withTag("VolleyZoneParser")

  private fun Element.attrOrNull(name: String): String? =
    attr(name).trim().takeIf(String::isNotBlank)

  private fun String.sanitiseTeamName(): String = replace("?", "'").replace("’", "'")

  fun homeTeam(row: Element): String? = row.attrOrNull("data-hometeam")?.sanitiseTeamName()

  fun awayTeam(row: Element): String? = row.attrOrNull("data-awayteam")?.sanitiseTeamName()

  fun date(row: Element): String? = row.attrOrNull("data-date")

  fun time(row: Element): String? = row.attrOrNull("data-time")

  fun venue(row: Element): String? = row.attrOrNull("data-venue")

  fun venueExtra(row: Element, venue: String?): String? {
    if (venue == null) return null
    return row
      .getElementsByTag("li")
      .getOrNull(5)
      ?.getElementsByClass("data")
      ?.firstOrNull()
      ?.text()
      ?.takeIf { it.startsWith(venue, ignoreCase = true) }
  }

  fun comment(row: Element): String? = row.attrOrNull("data-comment")

  fun homeSets(row: Element): UInt? = row.attrOrNull("data-homescore")?.toUInt()

  fun awaySets(row: Element): UInt? = row.attrOrNull("data-awayscore")?.toUInt()

  fun setScores(scores: Element): List<List<UInt>> =
    scores.getElementsByTag("li").mapNotNull { li ->
      li
        .getElementsByTag("span")
        .mapNotNull { it.text().trim().toUIntOrNull() }
        .takeIf { it.size >= 2 }
    }

  fun parseTime(date: String, time: LocalTime): Instant {
    val dateLocal =
      LocalDate.parse(
        input = date,
        format =
          LocalDate.Format {
            day()
            char(' ')
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            year()
          },
      )
    val timezone =
      if (
        dateLocal >= LocalDate(dateLocal.year, Month.MARCH, 25) &&
          dateLocal < LocalDate(dateLocal.year, Month.OCTOBER, 25)
      ) {
        BST
      } else {
        GMT
      }
    return dateLocal.atTime(time).toInstant(timezone)
  }

  /** Resolve match venue address into more detailed form. */
  fun resolveAddress(match: Match): Match {
    log.v("[${match.identity}] Resolving address.")
    val mapped =
      addresses.entries
        .firstOrNull { (k, _) ->
          match.venue.address.startsWith(k, ignoreCase = true) ||
            match.venue.alternativeAddress?.startsWith(k, ignoreCase = true) == true
        }
        ?.value
    return if (mapped != null) {
      log.d("[${ match.identity}] Resolved ${match.venue} to mapped $mapped address.")
      match.copy(venue = match.venue.copy(address = mapped))
    } else {
      val fallbackVenue =
        match.venue.alternativeAddress ?: "${ match.venue}, England, United Kingdom"
      log.d(
        "[${match.identity}] Unable to map address ${ match.venue}. Falling back to source venue $fallbackVenue"
      )
      match.copy(
        venue = match.venue.copy(address = fallbackVenue, alternativeAddress = match.venue.address)
      )
    }
  }

  companion object {
    val GMT = TimeZone.of("GMT")
    val BST = TimeZone.of("GMT+1")
  }
}
