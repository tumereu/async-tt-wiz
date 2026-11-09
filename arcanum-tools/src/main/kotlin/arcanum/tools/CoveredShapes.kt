package arcanum.tools

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes `covered-shapes.tsv`: the ability shapes the tests cover. Its Gradle task first runs the parser's and the engine's
 * tests with their recorder on; those leave one `coverage<TAB>ability JSON` line per ability. Args: the two files.
 */
fun main(args: Array<String>) {
    val shapes = coveredShapesFrom(args.map(::File).flatMap { file ->
        check(file.exists()) { "$file is missing: run this through :arcanum-tools:coveredShapes" }
        file.readLines().map { it.substringBefore('\t') to Json.parseToJsonElement(it.substringAfter('\t')) }
    })
    val out = coveredShapesFile()
    out.parentFile.mkdirs()
    out.writeText(shapes.toTsv())
    val all = shapes.byId.values
    println("${all.size} shapes: parser ${all.count { it.parser > 0 }}, engine covered ${all.count { it.engineCovered }} " +
        "(exercised ${all.count { it.exercised > 0 }}, only present ${all.count { it.exercised == 0 && it.present > 0 }}), " +
        "both ${all.count { it.parser > 0 && it.engineCovered }}: $out")
}

internal fun coveredShapesFile() = File(ParserToolPaths.setPlans, "covered-shapes.tsv")

/** Keywords whose whole meaning is a fixed rule of the engine, independent of the rest of the card: being in play is their coverage. */
internal val SIMPLE_KEYWORDS = setOf("flying", "first-strike", "double-strike", "deathtouch", "defender", "flash", "haste", "hexproof",
    "indestructible", "lifelink", "menace", "reach", "trample", "vigilance", "shroud", "fear", "intimidate", "skulk", "shadow", "horsemanship")

/** How the tests cover one shape: how many different abilities of that shape each kind of test met. */
internal class ShapeCoverage(val kind: String, val shape: String, val parser: Int, val exercised: Int, val present: Int) {
    /**
     * Whether a scenario really played it. A spell or ability counts once one resolved, a modal one never (which modes were played
     * isn't kept). A static ability's work can't be seen from a scenario, so being in play doesn't count: only [SIMPLE_KEYWORDS] do.
     */
    val engineCovered: Boolean get() = when {
        modal -> false
        kind in SIMPLE_KEYWORDS -> exercised > 0 || present > 0
        else -> exercised > 0
    }
    private val modal: Boolean get() = "\"modes\":" in shape
}

/** The shapes the tests cover, by [shapeId]. */
internal class CoveredShapes(val byId: Map<String, ShapeCoverage>) {
    fun parser(id: String): Boolean = (byId[id]?.parser ?: 0) > 0
    fun engine(id: String): Boolean = byId[id]?.engineCovered == true

    fun toTsv(): String = "#shape\tparser\texercised\tpresent\tengine\tkind\tcanonical form\n" +
        byId.entries.sortedBy { it.key }.joinToString("") { (id, c) ->
            "$id\t${c.parser}\t${c.exercised}\t${c.present}\t${if (c.engineCovered) "yes" else "no"}\t${c.kind}\t${c.shape}\n"
        }

    companion object {
        fun read(file: File): CoveredShapes {
            check(file.exists()) { "$file is missing: run ./scripts/gradle.sh :arcanum-tools:coveredShapes first" }
            return CoveredShapes(file.readLines().filter { !it.startsWith("#") && it.isNotBlank() }.associate { line ->
                val c = line.split('\t', limit = 7)
                c[0] to ShapeCoverage(c[5], c[6], c[1].toInt(), c[2].toInt(), c[3].toInt())
            })
        }
    }
}

/** Counts recorded abilities (`parser`, `exercised` or `present`, and the ability's JSON) by shape. */
internal fun coveredShapesFrom(records: List<Pair<String, JsonElement>>): CoveredShapes {
    class Counts(val kind: String, val shape: String, var parser: Int = 0, var exercised: Int = 0, var present: Int = 0)
    val counts = HashMap<String, Counts>()
    for ((coverage, ability) in records) {
        if (abilityKind(ability) == "unparsed") continue
        val shape = abilityShape(ability)
        val c = counts.getOrPut(shapeId(shape)) { Counts(abilityKind(ability), shape) }
        when (coverage) {
            "parser" -> c.parser++
            "exercised" -> c.exercised++
            "present" -> c.present++
            else -> error("Unknown coverage: $coverage")
        }
    }
    return CoveredShapes(counts.mapValues { (_, c) -> ShapeCoverage(c.kind, c.shape, c.parser, c.exercised, c.present) })
}

/** What one object of the model says, for the tools that read it: `{"type":"x"}` or null. */
internal fun JsonElement.typeOrNull(): String? = ((this as? JsonObject)?.get("type") as? JsonPrimitive)?.content
