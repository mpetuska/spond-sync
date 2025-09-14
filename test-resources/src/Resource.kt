package testing

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

object Resource {
  fun read(path: Path, module: String? = null): Source? {
    var basePath =
      if (module != null) {
        Path("../$module/resources/$path")
      } else {
        Path("resources/$path")
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
