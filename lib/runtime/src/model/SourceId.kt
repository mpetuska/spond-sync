package dev.petuska.spond.sync.runtime.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SourceId(val id: String) {
  override fun toString(): String = id
}
