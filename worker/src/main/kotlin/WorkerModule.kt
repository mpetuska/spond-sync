package worker

import co.touchlab.kermit.Logger
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.ktor.client.*
import kotlinx.serialization.json.Json
import spond.SpondCredentials
import sportpress.SportpressCredentials
import utils.tokens.FileTokenHandler
import utils.tokens.TokenHandler
import javax.inject.Named

@Module
interface WorkerModule {
  companion object {
    @Provides
    fun spondCredentials(config: WorkerConfig.Spond): SpondCredentials = SpondCredentials(
      username = config.username,
      password = config.password,
      apiUrl = config.apiUrl,
    )

    @Provides
    fun sportpressCredentials(config: WorkerConfig.Sportpress): SportpressCredentials = SportpressCredentials(
      apiUrl = config.apiUrl,
    )

    @Provides
    fun httpClient(): HttpClient = HttpClient()

    @Provides
    fun logger(): Logger = Logger.withTag("Global")

    @Provides
    fun json(): Json = Json {
      ignoreUnknownKeys = true
    }

    @Provides
//    @Named("spond")
    fun spondTokenHandler(json: Json, credentials: WorkerConfig.Spond): TokenHandler = FileTokenHandler(
      json = json,
      key = "${credentials.hashCode()}",
    )
  }
}


