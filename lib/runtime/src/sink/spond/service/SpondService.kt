package dev.petuska.spond.sync.runtime.sink.spond.service

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.runtime.config.Config
import dev.petuska.spond.sync.runtime.model.TeamId
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.group.Group
import dev.petuska.spond.sync.spond.data.group.Member
import dev.petuska.spond.sync.spond.data.group.ProfileId
import dev.petuska.spond.sync.spond.data.group.SubGroup
import dev.petuska.spond.sync.spond.data.location.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.atomics.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach

@Inject
@SingleIn(AppScope::class)
class SpondService(
  private val client: Spond,
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

  suspend fun getSubGroup(team: TeamId): SubGroup {
    val name = config.subGroups.entries.find { (_, it) -> it.team == team }?.key
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

  suspend fun findOwners(teamId: TeamId): List<ProfileId>? {
    val config = config.subGroups.values.find { it.team == teamId }
    val owners =
      config?.hosts?.mapNotNull {
        val member = if ('@' in it) findMemberByEmail(it) else findMemberByName(it)
        member?.profile?.id ?: member?.id
      }
    log.v { "Found owners for $teamId: config=$config, owners=$owners" }
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
}
