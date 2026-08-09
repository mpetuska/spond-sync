package dev.petuska.spond.sync.testing

import io.ktor.utils.io.readText
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

object Resource {
  fun readText(path: Path, module: String? = null): String =
    read(path, module).use { it.readText() }

  fun read(path: Path, module: String? = null): Source =
    checkNotNull(readOrNull(path, module)) { "Resource $path not found!" }

  fun readOrNull(path: Path, module: String? = null): Source? {
    var basePath =
      if (module != null) {
        Path("../$module/resources/$path")
      } else {
        Path("resources/$path")
      }
    if (!SystemFileSystem.exists(basePath)) {
      basePath =
        if (module != null) {
          Path("../$module/testResources/$path")
        } else {
          Path("testResources/$path")
        }
    }
    if (!SystemFileSystem.exists(basePath)) {
      basePath = Path("../test-resources/resources/$path")
    }
    return if (SystemFileSystem.exists(basePath)) {
      SystemFileSystem.source(basePath).buffered()
    } else {
      println("Resource $basePath not found!")
      null
    }
  }
}
