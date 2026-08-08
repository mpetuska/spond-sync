import cli.CliCommand
import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.runBlocking

fun main(vararg args: String): Unit = runBlocking { CliCommand().main(args) }
