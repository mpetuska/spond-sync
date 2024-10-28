package sportpress

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

internal fun buildHttpClient(
  baseClient: HttpClient,
  credentials: SportpressCredentials,
  json: Json,
): HttpClient = baseClient.config {
  val url = credentials.apiUrl.removeSuffix("/")
  expectSuccess = true
  install(DefaultRequest) {
    contentType(ContentType.Application.Json)
    url("$url/")
  }
  install(ContentNegotiation) {
    json(json)
  }
}
