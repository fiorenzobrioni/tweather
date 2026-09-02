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

rootProject.name = "chiaro"

// :core:sync (the shared WorkManager job and the alarm schedulers) is deliberately
// absent until Fase 6 — see PLANNING.md, "What Fase 0 does not seed, and why".
include(":app")
include(":core:domain")
include(":core:data")
