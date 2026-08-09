package dev.petuska.spond.sync.spond.sink

import dev.petuska.spond.sync.core.DataSink
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.core.model.TeamId
import dev.petuska.spond.sync.spond.SpondCredentials
import dev.petuska.spond.sync.spond.data.group.SubGroupName
import dev.petuska.spond.sync.utils.Identifiable
import dev.petuska.spond.sync.utils.Named
import dev.petuska.spond.sync.utils.tokens.MemoryTokenHandler
import dev.petuska.spond.sync.utils.tokens.TokenHandler
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json

@SingleIn(ClubScope::class)
@ContributesTo(ClubScope::class)
interface SpondSinkComponent {
  @Provides @SingleIn(ClubScope::class) fun sink(impl: SpondSink): DataSink<Identifiable> = impl

  @Provides
  @SingleIn(ClubScope::class)
  fun spondCredentials(config: SpondSinkConfig): SpondCredentials = config.api

  @Provides
  @SingleIn(ClubScope::class)
  fun eventConfig(config: SpondSinkConfig): SpondSinkConfig.Events = config.events

  @Provides
  @SingleIn(ClubScope::class)
  fun teamsToSubGroups(teamsToSubGroups: Map<SubGroupName, TeamId>): Map<TeamId, SubGroupName> =
    teamsToSubGroups.entries.associate { (k, v) -> v to k }

  @Provides
  @SingleIn(ClubScope::class)
  fun subGroupsToTeams(config: SpondSinkConfig): Map<SubGroupName, TeamId> = config.subGroups

  @Provides
  @SingleIn(ClubScope::class)
  fun teams(config: SpondSinkConfig): Set<TeamId> = config.subGroups.values.toSet()

  @Provides
  @SingleIn(ClubScope::class)
  @Named("spond")
  fun tokenHandler(json: Json, config: SpondSinkConfig): TokenHandler {
    //    return FileTokenHandler(json, config.api.toString())
    return MemoryTokenHandler
  }
}
