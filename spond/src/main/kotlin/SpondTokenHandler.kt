package spond

import io.ktor.client.plugins.auth.providers.*

interface SpondTokenHandler {
  /**
   * Cached [BearerTokens] provider.
   */
  suspend fun onLoadTokens(): BearerTokens?

  /**
   * Callback invoked each time tokens are refreshed.
   */
  suspend fun onRefreshTokens(tokens: BearerTokens)
}
