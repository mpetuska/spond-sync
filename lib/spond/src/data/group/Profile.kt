package dev.petuska.spond.sync.spond.data.group

import kotlinx.serialization.Serializable

typealias ProfileId = String

@Serializable
data class Profile(
  val id: ProfileId,
  val firstName: String,
  val lastName: String,
  val email: String,
  val contactMethod:String,
  val unableToReach: Boolean,
)
