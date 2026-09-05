package dev.petuska.spond.sync.runtime.sink.spond.service

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.runtime.config.Config
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.group.Group
import dev.petuska.spond.sync.spond.data.group.Member
import dev.petuska.spond.sync.spond.data.group.ProfileId
import dev.petuska.spond.sync.spond.data.group.SubGroup
import dev.petuska.spond.sync.spond.data.group.SubGroupName
import dev.petuska.spond.sync.spond.data.location.Location
import dev.petuska.spond.sync.spond.data.storage.PrepareUploadRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.concurrent.atomics.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Inject
@SingleIn(AppScope::class)
class SpondService(
  private val client: Spond,
  private val baseClient: HttpClient,
  config: Config,
) {
  private val log = Logger.withTag("SpondService")
  private val config = config.spond

  private val groupFetching = AtomicBoolean(false)
  private var group: Deferred<Group>? = null

  suspend fun getGroup(): Group {
    if (groupFetching.compareAndSet(expectedValue = false, newValue = true) && group == null) {
      val result = CompletableDeferred<Group>()
      this.group = result
      val group =
        client
          .listGroups()
          .onEach { log.v("Found group ${it.identity}") }
          .firstOrNull { it.name == config.group }
      checkNotNull(group) { "Unable to find Spond group \"${config.group}\"" }
      result.complete(group)
      return group
    } else {
      return checkNotNull(group).await()
    }
  }

  suspend fun getSubGroup(name: SubGroupName): SubGroup {
    return getGroup().subGroups.single { it.name == name }
  }

  suspend fun findMemberByName(name: String): Member? {
    return getGroup().members.singleOrNull {
      name.contains(it.firstName.trim(), ignoreCase = true) &&
        name.contains(it.lastName.trim(), ignoreCase = true)
    }
  }

  suspend fun findMemberByEmail(email: String): Member? {
    return getGroup().members.singleOrNull {
      email.equals(it.profile?.email?.trim(), ignoreCase = true) ||
        email.equals(it.email?.trim(), ignoreCase = true)
    }
  }

  suspend fun findOwners(subGroup: SubGroupName): List<ProfileId>? {
    val config = config.subGroups[subGroup]
    val owners =
      config?.hosts?.mapNotNull {
        val member = if ('@' in it) findMemberByEmail(it) else findMemberByName(it)
        member?.profile?.id ?: member?.id
      }
    log.v { "Found owners for $subGroup: config=$config, owners=$owners" }
    return owners
  }

  suspend fun resolveSpondLocation(address: String): Location? {
    val autocomplete = client.autocompleteLocation(address).firstOrNull()
    if (autocomplete != null) {
      log.d("[$address] Successfully autocompleted location to ${autocomplete.identity}.")
    } else {
      log.w("[$address] Could not autocomplete location.")
      return null
    }
    val location = client.getLocation(autocomplete.id)
    log.d(
      "[$address] Resolved autocomplete location ${autocomplete.identity} to location ${location.identity}."
    )
    return location
  }

  private val uploadedImages = mutableMapOf<Url, Url>()
  private val uploadMutex = Mutex()

  suspend fun ensureImageUploaded(externalUrl: Url, currentSpondUrl: Url?): Url {
    return uploadMutex.withLock {
      uploadedImages.getOrPut(externalUrl) {
        log.d("[$externalUrl] Downloading external image.")
        val response = baseClient.get(externalUrl)
        val content = response.bodyAsBytes()

        if (currentSpondUrl != null && isFileAlreadyUploaded(content, currentSpondUrl)) {
          log.d("[$externalUrl] Image already on Spond at $currentSpondUrl (hash match).")
          return@getOrPut currentSpondUrl
        }

        log.d("[$externalUrl] Image not in cache or hash mismatch, uploading to Spond.")
        val contentType = response.contentType() ?: ContentType.Image.Any
        val fileName = externalUrl.segments.lastOrNull() ?: "image.png"

        val group = getGroup()
        val filesToken = client.getFilesToken(group.id)

        val prepareResponse =
          client.prepareUpload(
            filesToken = filesToken,
            request =
              PrepareUploadRequest(
                type = "IMAGE",
                context = "GROUP",
                usage = "EVENT_IMAGE",
                fileName = fileName,
              ),
          )

        val uploadResponse =
          client.uploadFile(
            filesToken = filesToken,
            id = prepareResponse.id,
            fileName = fileName,
            contentType = contentType,
            content = content,
          )
        log.i("[$externalUrl] Successfully uploaded image to Spond: ${uploadResponse.url}")
        uploadResponse.url
      }
    }
  }

  private suspend fun isFileAlreadyUploaded(new: ByteArray, currentSpondUrl: Url): Boolean {
    return try {
      val response = baseClient.head(currentSpondUrl)
      val contentLength = response.contentLength()
      return contentLength != null && contentLength == new.size.toLong()
    } catch (e: ResponseException) {
      log.w(e) { "[$currentSpondUrl] Failed to check remote image headers, uploading." }
      false // Network failure or missing resource -> default to uploading
    }
  }
}
