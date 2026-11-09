package arcanum.parser.cube

import arcanum.api.Ability
import java.io.File
import kotlinx.serialization.json.Json

private val output = System.getProperty("arcanum.coveredAbilities")?.let(::File)
private val seen = HashSet<String>()

/** Only the task `recordCoveredAbilities` sets the property; each different ability is one line of JSON. */
internal actual fun recordCoveredAbilities(abilities: List<Ability>) {
    val file = output ?: return
    synchronized(seen) {
        for (ability in abilities) {
            val json = Json.encodeToString(Ability.serializer(), ability)
            if (seen.add(json)) file.appendText("parser\t$json\n")
        }
    }
}
