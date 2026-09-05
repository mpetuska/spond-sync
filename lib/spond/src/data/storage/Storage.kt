package dev.petuska.spond.sync.spond.data.storage

import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
data class PrepareUploadRequest(
  val type: String,
  val context: String,
  val usage: String,
  val fileName: String,
)

@Serializable
data class PrepareUploadResponse(
  val id: String,
  val uploadUrl: Url,
)

@Serializable data class FilesToken(val value: String)

@Serializable
data class UploadResponse(
  val id: String,
  val url: Url,
  val name: String,
  val mediaType: String,
  val width: Int? = null,
  val height: Int? = null,
  val length: Long? = null,
)
