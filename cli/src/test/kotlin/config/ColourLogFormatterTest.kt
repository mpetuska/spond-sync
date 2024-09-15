package cli.config

import co.touchlab.kermit.*
import org.junit.jupiter.api.Test

class ColourLogFormatterTest {
  private val target = ColourLogFormatter()
  private val tag = Tag("Tag")
  private val message = Message("Message")

  @Test
  fun test() {
    for (severity in Severity.entries) {
      val prefix = "$severity:"
      val formatted = target.formatMessage(severity, tag, message)
      println("${prefix.padEnd(8)} |$formatted|")
    }
  }
}
