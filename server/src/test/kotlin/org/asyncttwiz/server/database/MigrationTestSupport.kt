package org.asyncttwiz.server.database

/** Read the same classpath migration registry as the runner, excluding unsupported applied rows. */
object MigrationTestSupport {
    fun supportedVersions(settings: DatabaseSettings): List<String> =
        FoundationMigrations.flyway(settings).info().all()
            .filter { it.state.isResolved }
            .map { it.version.toString() }

    // A further version component is always newer, even if the registry uses dotted versions.
    fun futureVersion(settings: DatabaseSettings): String = supportedVersions(settings).last() + ".1"
}
