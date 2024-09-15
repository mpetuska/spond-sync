import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
  id("detekt")
  id("kjvm")
  application
  id("ksp")
}

dependencies {
  ksp(libs.dagger.compiler)
}

application {
  mainClass = "cli.MainKt"
}

kotlin {
  explicitApi = ExplicitApiMode.Disabled
  sourceSets {
    main {
      dependencies {
        implementation(project(":worker"))
        implementation(libs.ktor.client.cio)
        implementation(libs.ktor.client.auth)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
        implementation(libs.kermit)
        implementation(libs.dagger)
        runtimeOnly(libs.logback)
      }
    }
  }
}
