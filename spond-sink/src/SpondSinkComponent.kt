package spond.sink

import core.DataSink
import core.di.ClubScope
import core.model.TeamId
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.SpondCredentials
import spond.data.group.SubGroupName
import utils.Identifiable
import utils.Named
import utils.tokens.MemoryTokenHandler
import utils.tokens.TokenHandler

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
