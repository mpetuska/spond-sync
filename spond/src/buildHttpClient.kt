package spond

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.tokens.SerializableBearerTokens
import utils.tokens.TokenHandler

internal fun buildHttpClient(
  baseClient: HttpClient,
  credentials: SpondCredentials,
  json: Json,
  log: Logger,
  tokenHandler: TokenHandler,
) = baseClient.config {
  val url = credentials.apiUrl.removeSuffix("/")
  expectSuccess = true
  install(DefaultRequest) {
    contentType(ContentType.Application.Json)
    url("$url/")
  }
  install(ContentNegotiation) {
    json(json)
  }
  install(Auth) {
    bearer {
      loadTokens {
        log.d("Loading tokens")
        tokenHandler.onLoadTokens()
          ?.let {
            BearerTokens(
              accessToken = it.accessToken,
              refreshToken = it.refreshToken
            )
          }
          .also {
            if (it == null) {
              log.i("Failed to load tokens")
            } else {
              log.i("Successfully loaded tokens")
            }
          }
      }
      refreshTokens {
        log.d("Refreshing tokens")
        val resp = client.preparePost("$url/login") {
          expectSuccess = true
          contentType(ContentType.Application.Json)
          setBody(
            mapOf(
              "email" to credentials.username,
              "password" to credentials.password,
            )
          )
        }.execute()
        val tokens: SpondTokens = resp.body()
        log.i("Refreshed tokens: $resp")
        BearerTokens(
          accessToken = tokens.accessToken,
          refreshToken = tokens.refreshToken
        ).also {
          log.i("Storing refreshed tokens")
          tokenHandler.onRefreshTokens(
            SerializableBearerTokens(
              accessToken = it.accessToken,
              refreshToken = it.refreshToken
            )
          )
        }
      }
    }
  }
}

@Serializable
private data class SpondTokens(
  @SerialName("loginToken")
  val accessToken: String,
  @SerialName("passwordToken")
  val refreshToken: String,
)
