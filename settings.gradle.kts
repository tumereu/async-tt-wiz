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
        // Web spike: the Kotlin/Wasm toolchain's downloads (the plugin may not add project repositories here).
        ivy("https://nodejs.org/dist") {
            name = "Node.js"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen"
            patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
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
// Web spike (throwaway, w/web-spike): the engine benchmark on the JVM and in Kotlin/Wasm.
include(":webspike-bench")
project(":webspike-bench").projectDir = file("webspike/bench")
include(":webspike-table")
// Web spike (part D): the first piece of the Android UI as a Compose Multiplatform module (Android + Kotlin/Wasm).
include(":shared-ui")
project(":webspike-table").projectDir = file("webspike/table")
