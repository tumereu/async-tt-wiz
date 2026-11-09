package arcanum.engine.scenario

import arcanum.api.Ability
import java.io.File
import kotlinx.serialization.json.Json

private val output = System.getProperty("arcanum.coveredAbilities")?.let(::File)
private val seen = HashSet<String>()

private val recording: (Coverage, Ability) -> Unit = { coverage, ability ->
    val line = "${coverage.code}\t${Json.encodeToString(Ability.serializer(), ability)}\n"
    synchronized(seen) { if (seen.add(line)) output!!.appendText(line) }
}

/** Only the task `recordCoveredAbilities` sets the property; each different ability is one line of JSON per kind of coverage. */
internal actual fun coveredAbilitySink(): ((Coverage, Ability) -> Unit)? = if (output == null) null else recording
