pluginManagement {
  includeBuild("./build-conventions")
}

plugins {
  id("settings")
}

rootProject.name = "sportpress-to-spond"
include(
  ":spond",
  ":sportpress",
  ":cli",
  ":worker",
  ":utils",
)
