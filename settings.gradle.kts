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

rootProject.name = "IGL-Detection"

// Plattformunabhängige Erkennungslogik (reines Kotlin/JVM). Als eigener Build eingebunden,
// damit sie auch ohne Android SDK gebaut und getestet werden kann:
//   ./gradlew -p core test
includeBuild("core")

include(":app")
