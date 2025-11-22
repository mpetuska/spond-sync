package utils.tokens

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object MemoryTokenHandler : TokenHandler {
  private val token = AtomicReference<SerializableBearerTokens?>(null)

  override suspend fun onLoadTokens(): SerializableBearerTokens? {
    return token.load()
  }

  override suspend fun onRefreshTokens(tokens: SerializableBearerTokens) {
    token.store(tokens)
  }
}
