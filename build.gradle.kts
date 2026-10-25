import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

buildscript {
    configurations.getByName("classpath").resolutionStrategy.activateDependencyLocking()
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    group = "org.asyncttwiz"
    version = "0.1.0"
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

// Resolve every configuration, including tooling/lint and both Android variants,
// when intentionally refreshing the checked-in locks. Ordinary builds never write locks.
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Explicit dependency maintenance across projects")
    doLast {
        require(gradle.startParameter.isWriteDependencyLocks) { "Use --write-locks" }
        allprojects.forEach { project ->
            project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
                logger.lifecycle("Locking ${project.path}:${configuration.name}")
                // AGP resolves artifacts through typed views. Resolving raw files
                // here is ambiguous for Android project variants; locks need the
                // complete dependency graph, not an untyped artifact selection.
                configuration.incoming.resolutionResult.allDependencies.forEach { dependency ->
                    if (dependency is UnresolvedDependencyResult) throw dependency.failure
                }
            }
        }
    }
}

tasks.register("verifyModuleBoundaries") {
    notCompatibleWithConfigurationCache("Examines the configured dependency graph")
    doLast {
        val expected = mapOf(
            ":core" to emptySet(),
            ":protocol" to setOf(":core", ":scale-fixtures"),
            ":client-storage" to setOf(":core"),
            ":androidApp" to setOf(":core", ":protocol", ":client-storage", ":scale-fixtures"),
            ":server" to setOf(":core", ":protocol", ":scale-fixtures"),
            ":scale-fixtures" to setOf(":protocol"),
        )
        subprojects.forEach { project ->
            val actual = project.configurations.flatMap { configuration ->
                configuration.dependencies.withType<ProjectDependency>().mapNotNull { dependency ->
                    if (dependency.path == project.path) {
                        // AGP adds the app-under-test to instrumentation's graph.
                        check(configuration.name.contains("test", ignoreCase = true))
                        null
                    } else dependency.path
                }
            }.toSet()
            check(actual == expected.getValue(project.path)) { "${project.path}: unexpected dependencies $actual" }
            logger.lifecycle("PASS ${project.path} -> ${actual.sorted()}")
        }
        listOf("jvmRuntimeClasspath", "androidRuntimeClasspath").forEach { name ->
            val components = project(":core").configurations.getByName(name)
                .incoming.resolutionResult.allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
            check(components.all {
                it.group == "org.jetbrains.kotlin" || (it.group == "org.jetbrains" && it.module == "annotations")
            }) { "Platform/framework dependency leaked into :core $name: $components" }
            logger.lifecycle("PASS :core $name contains only Kotlin/annotations: $components")
        }
        fileTree("core/src").matching { include("**/*.kt") }.forEach { source ->
            val imports = source.readLines().filter { it.startsWith("import ") }
            check(imports.all { it.startsWith("import kotlin.") }) { "Non-Kotlin import in shared semantics: $source" }
        }
        logger.lifecycle("PASS semantic source imports contain no generated database/UI/server types")
        val androidRuntime = project(":androidApp").configurations.getByName("debugRuntimeClasspath")
            .incoming.resolutionResult.allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
        check(androidRuntime.none { it.group.startsWith("com.google.android.gms") || it.group.startsWith("com.google.firebase") })
        logger.lifecycle("PASS normal Android runtime has no mandatory GMS/Firebase dependency")
    }
}
