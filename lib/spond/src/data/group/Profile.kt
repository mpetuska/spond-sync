package dev.petuska.spond.sync.spond.data.group

import kotlinx.serialization.Serializable

typealias ProfileId = String

@Serializable
data class Profile(
  val id: ProfileId,
  val contactMethod: String,
  val unableToReach: Boolean,
  val firstName: String? = null,
  val lastName: String? = null,
  val email: String? = null,
)
