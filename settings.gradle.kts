pluginManagement {
  includeBuild("./build-conventions")
}

plugins {
  id("settings")
}

rootProject.name = "spond-sync"
include(
  ":spond",
  ":sportpress",
  ":cli",
  ":worker",
  ":utils",
)
