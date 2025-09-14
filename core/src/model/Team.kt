package core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable
import utils.Identifiable

@JvmInline
@Serializable
value class TeamId(val value: String) {
  override fun toString(): String = value
}

data class Team(val id: TeamId, val name: String) : Identifiable {
  override val identity = "Team(id=$id, name=$name)"
}
