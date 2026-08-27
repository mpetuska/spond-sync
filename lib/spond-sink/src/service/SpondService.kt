package dev.petuska.spond.sync.spond.sink.service

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.TimeSource
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.group.Group
import dev.petuska.spond.sync.spond.data.group.Member
import dev.petuska.spond.sync.spond.data.group.ProfileId
import dev.petuska.spond.sync.spond.data.group.SubGroup
import dev.petuska.spond.sync.spond.sink.SpondSinkConfig
import dev.petuska.spond.sync.utils.Named
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.atomics.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach

@Inject
@SingleIn(ClubScope::class)
class SpondService(
  private val client: Spond,
  private val config: SpondSinkConfig,
  private val timeSource: TimeSource,
  private val eventBuilderService: EventBuilderService,
  private val teams: Map<TeamId, SpondSinkConfig.SubGroupConfig>,
  @Named("dry") private val dry: Boolean,
  logger: Logger,
) {
  private val log = logger.withTag("SpondService")

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
    val name = teams[team]?.name
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
    val config = teams[teamId]
    val owners =
      config?.hosts?.mapNotNull {
        val member = if ('@' in it) findMemberByEmail(it) else findMemberByName(it)
        member?.profile?.id ?: member?.id
      }
    log.v { "Found owners for $teamId: config=$config, owners=$owners" }
    return owners
  }
}
