pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MChat"

include(":app")
include(":core-common")
include(":core-data")
include(":core-llm")
include(":core-prompt")
include(":core-attachment")
include(":core-theme")
include(":feature-chat")
include(":feature-history")
include(":feature-settings")
include(":feature-title")
