package dev.petuska.spond.sync.utils.tokens

import kotlinx.serialization.Serializable

@Serializable
data class SerializableBearerTokens(val accessToken: String, val refreshToken: String?)
