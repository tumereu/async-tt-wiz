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
        // Kotlin/Wasm toolchain downloads (Node.js runs the wasm tests; Binaryen optimises wasm builds). With
        // FAIL_ON_PROJECT_REPOS the Kotlin plugin can't add these itself (build.gradle.kts turns its own off).
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
// The shared Compose UI (Compose Multiplatform: Android and the web client; docs/specs/web-client.md §1).
include(":app-ui")
// The web client's page and its served build (docs/specs/web-client.md §1 item 1, §2.1).
include(":webApp")
// Arcanum Automatum (docs/specs/arcanum-automatum.md): generic MTG rules libraries, not integrated into ATW yet.
include(":arcanum-api", ":arcanum-parser", ":arcanum-engine", ":arcanum-tools")
// ATW's mapping between its shared game state and the Arcanum engine (docs/specs/arcanum-atw-integration.md, I0).
include(":automatum")
include(":catalog")
// End-to-end tests (e2e/): the real server and PostgreSQL with two app instances; run with :e2e:test.
include(":e2e")
