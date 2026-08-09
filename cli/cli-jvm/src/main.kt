import com.github.ajalt.clikt.command.main
import dev.petuska.spond.sync.cli.CliCommand
import kotlinx.coroutines.runBlocking

fun main(vararg args: String): Unit = runBlocking { CliCommand().main(args) }
