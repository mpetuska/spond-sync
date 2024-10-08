package spond

/**
 * @property username typically the email used to login.
 * @property password user's password.
 * @property apiUrl spond API url, default will be used if left null.
 */
data class SpondCredentials(
  val username: String,
  val password: String,
  val apiUrl: String = "https://api.spond.com/core/v1",
)
