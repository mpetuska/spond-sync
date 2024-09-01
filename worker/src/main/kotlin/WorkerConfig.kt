package worker

import kotlinx.serialization.Serializable
import sportpress.Sportpress
import javax.inject.Inject
import javax.inject.Named

@Serializable
data class WorkerConfig @Inject constructor(
  @Named("team")
  val team: String,
  val spond: Spond,
  val sportpress: WorkerConfig.Sportpress,
) {
  @Serializable
  data class Spond(
    val username: String,
    val password: String,
    val apiUrl: String = "https://api.spond.com/core/v1"
  )

  @Serializable
  data class Sportpress(
    val apiUrl: String,
  )
}
