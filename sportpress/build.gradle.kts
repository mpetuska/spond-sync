import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
  id("detekt")
  id("kjvm")
  id("koin-bom")
}

kotlin {
  explicitApi = ExplicitApiMode.Disabled
  sourceSets {
    main {
      dependencies {
        implementation(project(":utils"))
        implementation(libs.ktor.client.cio)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
        implementation(libs.kotlinx.datetime)
        implementation(libs.dagger)
        implementation(libs.kermit)
      }
    }
  }
}
