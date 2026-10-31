import com.github.ajalt.clikt.command.main
import dev.petuska.spond.sync.cli.CliCommand
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** Executes CLI with source offset set to last october and sink offset set to next october. */
suspend fun main(vararg args: String) {
  val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
  val lastOctoberFirst = lastOctoberFirst(today)
  val nextOctoberFirst = nextOctoberFirst(today)
  val sourceOffest = today.daysUntil(lastOctoberFirst)
  val sinkOffest = today.daysUntil(nextOctoberFirst)
  val args =
    buildList {
//      add("--source-offset=$sourceOffest")
//      add("--sink-offset=$sinkOffest")
      add("configs/BVA_2026-2027.config.json5")
      add("cli/cli-test/test.creds.json5")
      add("cli/cli-test/test.config.json5")
      add("-li")
//      add("--clean")
//      add("--nosync")
      addAll(args.asList())
    }
  println(args.joinToString(" "))
  CliCommand().main(args)
}

private fun lastOctoberFirst(today: LocalDate): LocalDate {
  val lastOctoberYear =
    if (today.month >= Month.OCTOBER) {
      today.year
    } else {
      today.year - 1
    }

  return LocalDate(year = lastOctoberYear, month = Month.OCTOBER, day = 1)
}

private fun nextOctoberFirst(today: LocalDate): LocalDate {
  val nextOctoberYear =
    if (today.month <= Month.OCTOBER) {
      today.year
    } else {
      today.year + 1
    }

  return LocalDate(year = nextOctoberYear, month = Month.OCTOBER, day = 1)
}
