// Init script for parser-explain.sh: adds :arcanum-tools:explainClasspath, which builds arcanum-tools' runtime classpath
// and writes it to arcanum-tools/build/explain-classpath.txt, and the java of the project's toolchain (the one Gradle runs
// the tools with) to arcanum-tools/build/explain-java.txt, so ParserExplain can then run with plain java.
allprojects {
    if (path == ":arcanum-tools") {
        afterEvaluate {
            tasks.register("explainClasspath") {
                val cp = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java).getByName("main").runtimeClasspath
                val toolchain = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java).toolchain
                val launcher = project.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java).launcherFor(toolchain)
                dependsOn(cp)
                val out = project.layout.buildDirectory.file("explain-classpath.txt")
                val java = project.layout.buildDirectory.file("explain-java.txt")
                outputs.upToDateWhen { false }
                doLast {
                    out.get().asFile.writeText(cp.asPath)
                    java.get().asFile.writeText(launcher.get().executablePath.asFile.path)
                }
            }
        }
    }
}
