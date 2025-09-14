package sportpress

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun buildHttpClient(
  baseClient: HttpClient,
  credentials: SportpressCredentials,
  json: Json,
): HttpClient =
  baseClient.config {
    val url = credentials.apiUrl.removeSuffix("/")
    expectSuccess = true
    install(DefaultRequest) {
      contentType(ContentType.Application.Json)
      url("$url/")
    }
    install(ContentNegotiation) { json(json) }
  }
