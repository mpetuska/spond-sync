package spond.data.location

import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

typealias LocationId = String

@Serializable
data class Location(
  override val id: LocationId,
) : Identifiable, WithId {
  override val identity: String
    get() = "Location(id=$id)"
}
