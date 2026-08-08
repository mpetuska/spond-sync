package core.util

import co.touchlab.kermit.Message
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import de.infix.testBalloon.framework.core.testSuite

val ColourLogFormatterTest by testSuite {
  val target = ColourLogFormatter()
  val tag = Tag("Tag")
  val message = Message("Message")

  for (severity in Severity.entries) {
    test("Logs at $severity severity") {
      val prefix = "$severity:"
      val formatted = target.formatMessage(severity, tag, message)
      println("${prefix.padEnd(8)} |$formatted|")
    }
  }
}
