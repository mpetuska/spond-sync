package dev.petuska.spond.sync.plugins.ktfmt

import com.facebook.ktfmt.format.Formatter
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.TaskAction

@TaskAction
fun format(@Input sources: ModuleSources) {
  val options = Formatter.GOOGLE_FORMAT

  sources.sourceDirectories.forEach { dir ->
    if (Files.exists(dir)) {
      dir.walk().forEach { file ->
        if (file.isRegularFile() && (file.extension == "kt" || file.extension == "kts")) {
          val originalText = file.readText()
          try {
            val formattedText = Formatter.format(options, originalText)
            if (originalText != formattedText) {
              file.writeText(formattedText)
              println("Formatted: $file")
            }
          } catch (e: Exception) {
            System.err.println("Failed to format $file: ${e.message}")
          }
        }
      }
    }
  }
}
