package arcanum.parser

import arcanum.api.Ability
import java.io.File
import kotlin.test.Test

// SCRATCH, never committed: parses every card of a set file; writes unparsed lines and every definition.
class ScratchSetParseTest {
    private val dir = "/tmp/claude-1000/-home-tuomas-projects-async-tt-wiz/c365b669-9d68-4394-8fbc-e55ae10d7b5d/scratchpad/rtrw"

    @Test
    fun report() {
        val lines = File("$dir/rtr.txt").readLines()
        val only = File("$dir/only.txt").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val out = StringBuilder(); val defs = StringBuilder()
        var i = 0; var total = 0; var bad = 0
        while (i < lines.size) {
            val head = lines[i].split('\t'); i++
            if (head[0] != "#CARD") continue
            val name = head[1]
            val faces = mutableListOf<OracleCard>()
            while (i < lines.size && lines[i].startsWith("#FACE")) {
                val f = lines[i].split('\t'); i++
                val text = StringBuilder()
                while (lines[i] != "#ENDFACE") { if (text.isNotEmpty()) text.append('\n'); text.append(lines[i]); i++ }
                i++
                fun g(k: Int) = f.getOrNull(k)?.ifEmpty { null }
                faces += OracleCard(g(1) ?: name, g(2), g(3)!!, text.toString(), g(4), g(5), g(6), colorIndicator = g(7))
            }
            if (only.isNotEmpty() && name !in only) continue
            total++
            val face = try { OracleParser.parse(faces[0]) } catch (e: Throwable) { out.append("=== $name EXCEPTION $e\n"); bad++; continue }
            val unparsed = face.abilities.filterIsInstance<Ability.Unparsed>()
            if (unparsed.isNotEmpty()) { bad++; out.append("=== $name\n"); unparsed.forEach { out.append("  ! ").append(it.text.replace("\n", "\n    ")).append('\n') } }
            defs.append("=== $name\n    ").append(faces[0].text.replace("\n", "\n    ")).append('\n')
            face.abilities.forEach { defs.append("  ").append(it.toString().replace("arcanum.api.", "")).append('\n') }
        }
        out.insert(0, "$bad of $total cards have unparsed text\n")
        File("$dir/rtr-parse-report.txt").writeText(out.toString())
        File("$dir/rtr-defs.txt").writeText(defs.toString())
    }
}
