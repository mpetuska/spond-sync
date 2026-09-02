package dev.petuska.spond.sync.runtime.source.volleyzone

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import dev.petuska.spond.sync.runtime.model.Match
import dev.petuska.spond.sync.runtime.model.SourceId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.collections.*

@Inject
@SingleIn(AppScope::class)
class VolleyZoneClient(private val httpClient: HttpClient) {
  private val log = Logger.withTag("VolleyZoneClient")
  private val cache = ConcurrentMap<Url, Document>()

  private suspend fun getDocument(url: Url): Document {
    val cached = cache[url]
    if (cached != null) return cached
    val document = httpClient.get(url).bodyAsText().let(Ksoup::parse)
    cache[url] = document
    return document
  }

  suspend fun fetchFixtures(
    sourceId: SourceId,
    url: Url,
    parser: (row: Element) -> Match?,
  ): List<Match> {
    val document = getDocument(url)
    val fixturesTable = document.getElementById("fixtures_league")
    if (fixturesTable == null) {
      log.e("[$sourceId] Unable to find fixtures.")
      return emptyList()
    }
    log.v("[$sourceId] Parsing fixtures.")
    return fixturesTable.getElementsByClass("table-body").mapNotNull(parser).onEach {
      log.d("[$sourceId] Parsed fixture $it")
    }
  }

  suspend fun fetchResults(
    sourceId: SourceId,
    url: Url,
    parser: (row: Element, scores: Element) -> Match?,
  ): List<Match> {
    val document = getDocument(url)
    val resultsTable = document.getElementById("results_league")
    if (resultsTable == null) {
      log.e("[$sourceId] Unable to find results.")
      return emptyList()
    }
    log.v("[$sourceId] Parsing results.")
    return resultsTable
      .getElementsByTag("ul")
      .filter { !it.hasClass("table-header") }
      .windowed(size = 2, step = 2)
      .mapNotNull { (row, scores) -> parser(row, scores) }
      .onEach {
        log.d("[$sourceId] Parsed result $it")
      }
  }
}
