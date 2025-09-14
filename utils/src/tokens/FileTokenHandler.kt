package utils.tokens

import io.ktor.utils.io.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

class FileTokenHandler(
  private val json: Json,
  key: String,
  private val fileSystem: FileSystem = SystemFileSystem,
) : TokenHandler {
  private val file = Path(SystemTemporaryDirectory, "${key.hashCode()}.tmp")

  override suspend fun onLoadTokens(): SerializableBearerTokens? {
    return if (fileSystem.exists(file)) {
      fileSystem.source(file).buffered().use { it.readText() }.let(json::decodeFromString)
    } else {
      null
    }
  }

  override suspend fun onRefreshTokens(tokens: SerializableBearerTokens) {
    withContext(Dispatchers.IO) {
      file.parent?.let(fileSystem::createDirectories)
      fileSystem.sink(file).buffered().use { json.encodeToString(tokens).let(it::writeString) }
    }
  }
}
