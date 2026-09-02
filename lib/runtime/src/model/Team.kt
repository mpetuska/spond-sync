package dev.petuska.spond.sync.runtime.model

import dev.petuska.spond.sync.utils.Identifiable
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class TeamId(val value: String) {
  override fun toString(): String = value
}

data class Team(val id: TeamId, val name: String = id.value) : Identifiable {
  override val identity = "Team(id=$id, name=$name)"
}
