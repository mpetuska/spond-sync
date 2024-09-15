import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
  id("detekt")
  id("kjvm")
}

kotlin {
  explicitApi = ExplicitApiMode.Disabled
  sourceSets {
    main {
      dependencies {
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.dagger)
        implementation(libs.ktor.client.core)
        implementation(libs.kermit)
      }
    }
  }
}
