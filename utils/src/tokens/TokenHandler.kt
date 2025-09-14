package utils.tokens

interface TokenHandler {
  /** Cached [SerializableBearerTokens] provider. */
  suspend fun onLoadTokens(): SerializableBearerTokens?

  /** Callback invoked each time tokens are refreshed. */
  suspend fun onRefreshTokens(tokens: SerializableBearerTokens)
}
