package arcanum.parser

import java.io.File
import kotlin.test.Test

// SCRATCH, never committed: probes phrases (E event, N noun, C condition, V value, L line, I instructions).
class ScratchProbeTest {
    private val dir = "/tmp/claude-1000/-home-tuomas-projects-async-tt-wiz/c365b669-9d68-4394-8fbc-e55ae10d7b5d/scratchpad/rtrw"

    @Test
    fun probe() {
        val f = File("$dir/probe.txt").takeIf { it.exists() } ?: return
        val self = setOf("this creature", "this enchantment", "this artifact", "this land", "this permanent", "this Aura", "Foo")
        val out = StringBuilder()
        f.readLines().filter { it.length > 2 && it.contains("|") }.forEach { l ->
            val (k, t) = l.split('|', limit = 2)
            val r: Any? = try {
                when (k) {
                    "E" -> Events.events(t, self)
                    "N" -> Filters.noun(t)
                    "C" -> Conditions.condition(t, self)
                    "V" -> Values.value(t)
                    "L" -> Sentences(self).line(t)
                    "S" -> Sentences(self, isSpell = true).line(t)
                    "I" -> Sentences(self).instructions(t)
                    else -> "?"
                }
            } catch (e: Throwable) { "EXC $e" }
            out.append("$l\n    => ").append(r.toString().replace("arcanum.api.", "")).append("\n")
        }
        File("$dir/probe-out.txt").writeText(out.toString())
    }
}
