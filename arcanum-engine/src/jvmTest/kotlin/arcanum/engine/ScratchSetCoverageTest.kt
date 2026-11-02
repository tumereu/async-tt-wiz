package arcanum.engine

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.engine.view.KeywordMeanings
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

// SCRATCH, never committed: parses every card of a set file and reports unparsed and by-hand parts.
class ScratchSetCoverageTest {
    @Test
    fun report() {
        val input = "/tmp/claude-1000/-home-tuomas-projects-async-tt-wiz/c365b669-9d68-4394-8fbc-e55ae10d7b5d/scratchpad/rtrw/rtr.txt"
        val outFile = File("/tmp/claude-1000/-home-tuomas-projects-async-tt-wiz/c365b669-9d68-4394-8fbc-e55ae10d7b5d/scratchpad/rtrw/rtr-report.txt")
        val lines = File(input).readLines()
        val out = StringBuilder()
        var i = 0
        var total = 0; var bad = 0
        val json = Json { encodeDefaults = false }
        while (i < lines.size) {
            val head = lines[i].split('\t'); i++
            if (head[0] != "#CARD") continue
            val name = head[1]; val layout = head[2]
            val faces = mutableListOf<OracleCard>()
            while (i < lines.size && lines[i].startsWith("#FACE")) {
                val f = lines[i].split('\t'); i++
                val text = StringBuilder()
                while (lines[i] != "#ENDFACE") { if (text.isNotEmpty()) text.append('\n'); text.append(lines[i]); i++ }
                i++
                fun g(k: Int) = f.getOrNull(k)?.ifEmpty { null }
                faces += OracleCard(g(1) ?: name, g(2), g(3)!!, text.toString(), g(4), g(5), g(6), colorIndicator = g(7))
            }
            total++
            val problems = mutableListOf<String>()
            val def = try {
                if (faces.size == 1) CardDefinition(Layout.Normal, listOf(OracleParser.parse(faces[0])))
                else OracleParser.parseCard(when (layout) { "split" -> Layout.Split; "adventure" -> Layout.Adventure; "transform" -> Layout.Transform; else -> Layout.ModalDoubleFaced }, faces)
            } catch (e: Throwable) { problems += "EXCEPTION $e"; null }
            def?.faces?.forEach { face ->
                face.abilities.forEach { a ->
                    if (a is Ability.Unparsed) problems += "UNPARSED: ${a.text}"
                    if (a is Keyword && KeywordMeanings.isManual(a)) problems += "MANUAL KEYWORD: ${a.text}"
                }
                val js = try { json.encodeToString(CardDefinition.serializer(), def) } catch (e: Throwable) { "" }
                Regex("\"type\":\"manual\",\"text\":\"([^\"]*)\"").findAll(js).forEach { problems += "MANUAL STEP: ${it.groupValues[1]}" }
                listOf("support", "venture", "learn", "manifest", "cloak", "time-travel", "ring-tempts", "bolster", "endure").forEach { a ->
                    if (js.contains("\"type\":\"$a\"")) problems += "ACTION BY HAND?: $a"
                }
            }
            arcanum.api.DefinitionChecks.problems(def ?: CardDefinition(Layout.Normal, emptyList())).forEach { problems += "CHECK: $it" }
            if (problems.isNotEmpty()) {
                bad++
                out.append("=== $name\n")
                faces.forEach { out.append("    ").append(it.text.replace("\n", "\n    ")).append('\n') }
                problems.forEach { out.append("  ! ").append(it).append('\n') }
            }
        }
        out.insert(0, "$bad of $total cards have problems\n")
        outFile.writeText(out.toString())
    }
}
