pluginManagement {
    repositories {
        google()
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

rootProject.name = "async-tt-wiz"
include(":core", ":protocol", ":client-storage", ":androidApp", ":server")
include(":scale-fixtures")
// Arcanum Automatum (docs/specs/arcanum-automatum.md): generic MTG rules libraries, not integrated into ATW yet.
include(":arcanum-api", ":arcanum-parser", ":arcanum-engine", ":arcanum-tools")
