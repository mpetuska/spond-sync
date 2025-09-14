package core.util

import co.touchlab.kermit.Message
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import kotlin.test.Test

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
