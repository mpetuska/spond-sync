package spond

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.tokens.SerializableBearerTokens
import utils.tokens.TokenHandler
import kotlin.time.Instant

internal fun buildHttpClient(
  baseClient: HttpClient,
  credentials: SpondCredentials,
  json: Json,
  log: Logger,
  tokenHandler: TokenHandler,
) =
  baseClient.config {
    val url = credentials.apiUrl.removeSuffix("/")
    expectSuccess = true
    install(DefaultRequest) {
      contentType(ContentType.Application.Json)
      url("$url/")
    }
    install(ContentNegotiation) { json(json) }
    install(Auth) {
      bearer {
        sendWithoutRequest { request ->
          log.w { "sendWithoutRequest(${request.url} in $url)" }
          request.url.host in url
        }

        loadTokens {
          log.d("Loading tokens")
          tokenHandler
            .onLoadTokens()
            ?.let { BearerTokens(accessToken = it.accessToken, refreshToken = it.refreshToken) }
            .also {
              if (it == null) {
                log.i("Failed to load tokens")
              } else {
                log.i("Successfully loaded tokens")
              }
            } ?: BearerTokens("REFRESH", "REFRESH")
        }
        refreshTokens {
          log.d("Refreshing tokens")
          val resp =
            client
              .preparePost("$url/auth2/login") {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to credentials.username, "password" to credentials.password))
                markAsRefreshTokenRequest()
              }
              .execute()
          val tokens: SpondTokens = resp.body()
          log.i("Refreshed tokens: $resp")
          BearerTokens(
              accessToken = tokens.accessToken.token,
              refreshToken = tokens.refreshToken.token,
            )
            .also {
              log.i("Storing refreshed tokens")
              tokenHandler.onRefreshTokens(
                SerializableBearerTokens(
                  accessToken = it.accessToken,
                  refreshToken = it.refreshToken,
                )
              )
            }
        }
      }
    }
  }

@Serializable
private data class SpondTokens(
  val accessToken: SpondToken,
  val refreshToken: SpondToken,
  val passwordToken: SpondToken,
)

@Serializable private data class SpondToken(val token: String, val expiration: Instant)
