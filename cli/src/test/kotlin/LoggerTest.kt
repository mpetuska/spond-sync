package cli

import cli.config.ColourLogFormatter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import org.junit.jupiter.api.Test

class LoggerTest {
  private val target = Logger(
    config = loggerConfigInit(
      platformLogWriter(ColourLogFormatter()),
      minSeverity = Severity.Verbose,
    ),
    tag = "Test"
  )

  @Test
  fun test() {
    for (severity in Severity.entries) {
      target.log(severity, target.tag, null, "Logging at $severity")
    }
  }
}
