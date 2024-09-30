package utils.tokens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import javax.inject.Inject
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
class FileTokenHandler @Inject constructor(private val json: Json, key: String) : TokenHandler {
  private val file = File(
    System.getProperty("java.io.tmpdir") + "/" + "sportpress-to-spond-token".hashCode().toString(),
    key
  )

  override suspend fun onLoadTokens(): SerializableBearerTokens? {
    return if (file.exists()) {
      file.inputStream().use(json::decodeFromStream)
    } else {
      null
    }
  }

  override suspend fun onRefreshTokens(tokens: SerializableBearerTokens) {
    withContext(Dispatchers.IO) {
      file.parentFile.mkdirs()
      file.outputStream().use { stream ->
        json.encodeToStream(tokens, stream)
      }
    }
  }
}
