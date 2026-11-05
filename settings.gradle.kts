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
// Arcanum Automatum (docs/specs/arcanum-automatum.md): generic MTG rules libraries, not integrated into ATW yet.
include(":arcanum-api", ":arcanum-parser", ":arcanum-engine", ":arcanum-tools")
// ATW's mapping between its shared game state and the Arcanum engine (docs/specs/arcanum-atw-integration.md, I0).
include(":automatum")
// End-to-end tests (e2e/): the real server and PostgreSQL with two app instances; run with :e2e:test.
include(":e2e")
