package config

import cli.config.ClubApp
import core.di.ClubScope
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.sink.SpondSinkConfig

@SingleIn(ClubScope::class)
@ContributesSubcomponent(ClubScope::class)
interface ClubComponent {
  val app: ClubApp

  @ContributesSubcomponent.Factory(AppScope::class)
  interface Factory {
    fun createClubComponent(config: SpondSinkConfig): ClubComponent

    fun club(config: SpondSinkConfig): ClubApp = createClubComponent(config).app
  }
}
