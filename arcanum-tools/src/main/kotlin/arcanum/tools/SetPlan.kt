package arcanum.tools

import arcanum.api.Ability
import arcanum.api.KeywordKind
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Plans the work on one set so a fresh worker per chunk can start from a small digest: every card's verdict, chunks of the cards
 * that need work, the difficult ones for the owner and the audit packets. Writes outside the repository
 * (`set-plans/CODE/`); notes and audit results already there are kept. Args: the set's code.
 */
fun main(args: Array<String>) {
    val code = args.firstOrNull()?.uppercase() ?: error("Usage: setPlan CODE")
    val setCards = setCards(SetCache(ParserToolPaths.setCache.apply { mkdirs() }).file(code))
    val covered = CoveredShapes.read(coveredShapesFile())
    val index = RuleIndex.at(ParserToolPaths.parserSources)
    val cards = setCards.map { card ->
        val keywords = card.faces.flatMap { face -> face.getAsJsonArray("keywords")?.map { it.asString }.orEmpty() }.distinct()
        planCard(card.name, card.layout, card.faces.map(::oracleCard), keywords, covered)
    }
    val plan = SetPlanOutput(code, cards, index, covered.byId.values.joinToString("\n") { it.shape }.lowercase())
    val dir = File(ParserToolPaths.setPlans, code)
    plan.writeTo(dir)
    println("$code, parser version ${OracleParser.VERSION}: ${cards.size} cards, cold read ${plan.coldRead()}")
    println(Verdicts.all.joinToString(", ") { v -> "${cards.count { it.verdict.verdict == v }} ${v.code}" } +
        "; ${plan.chunks.size} chunks (${plan.chunks.count { it.mechanical }} mechanical); ${plan.difficult.size} difficult candidates; digest ${plan.digest().size} lines")
    println("Plan in $dir")
}

private object Verdicts {
    val all: List<Verdict> = listOf(Verdict.Unread, Verdict.Test, Verdict.Repeat)
}

/** One card with what the plan says about it. */
internal class PlannedCard(val name: String, val layout: String, val faces: List<OracleCard>, val keywords: List<String>, val verdict: CardVerdict, val explanation: Explanation?) {
    val needsWork: Boolean get() = verdict.verdict != Verdict.Repeat
    val unread: Boolean get() = verdict.verdict == Verdict.Unread

    /** Why a card is unread, for the plan: its first unread clause, or that its layout or printed values couldn't be read. */
    val reason: String get() = when {
        !unread -> verdict.reason
        else -> explanation?.firstUnread?.let { "first unread ${it.kind.label}: ${it.text.replace("\n", " / ")}" } ?: "layout $layout or its printed values can't be read"
    }

    val firstEffect: String by lazy {
        val abilities = parsedCard(layout, faces)?.faces?.flatMap { it.abilities }.orEmpty().filter { it !is arcanum.api.Keyword && it !is Ability.Unparsed }
        abilities.firstNotNullOfOrNull { firstDoing(abilityJson(it)) } ?: abilities.firstOrNull()?.let { abilityKind(abilityJson(it)) } ?: "keywords"
    }
}

/** The first effect or continuous rule in an ability's model. */
private fun firstDoing(e: JsonElement): String? = when (e) {
    is JsonObject -> (e["effect"] as? JsonObject)?.typeOrNull() ?: (e["mechanics"] as? JsonArray)?.firstOrNull()?.typeOrNull()
        ?: e.entries.filter { it.key != "trigger" && it.key != "cost" && it.key != "targets" }.firstNotNullOfOrNull { firstDoing(it.value) }
    is JsonArray -> e.firstNotNullOfOrNull(::firstDoing)
    else -> null
}

internal fun planCard(name: String, layout: String, faces: List<OracleCard>, keywords: List<String>, covered: CoveredShapes): PlannedCard {
    val verdict = judge(layout, faces, covered)
    val explanation = if (verdict.verdict != Verdict.Unread) null else {
        // A multi-faced card: the lines of each face, its own type line carrying them.
        val lines = faces.flatMap { face -> explain(face.text, face).lines }
        Explanation(lines)
    }
    return PlannedCard(name, layout, faces, keywords, verdict, explanation)
}

/** A group of cards one worker does together, sharing a first unread clause, a verb or a mechanic. */
internal class Chunk(val number: Int, val theme: String, val cards: List<PlannedCard>) {
    val file: String get() = "%02d.md".format(number)

    /** Every card in it only needs tests, no rule. */
    val mechanical: Boolean get() = cards.none { it.unread }
}

private const val MOST_IN_A_CHUNK = 25
private const val FEWEST_IN_A_CHUNK = 10

/** What groups a card with others: a mechanic of the set, else what its first unread clause does, else what it does. */
internal fun groupKey(card: PlannedCard, mechanics: Set<String>): String {
    card.keywords.firstOrNull { it in mechanics }?.let { return "mechanic $it" }
    if (!card.unread) return "test: ${card.firstEffect}"
    val first = card.explanation?.firstUnread ?: return "unread: layout"
    val words = wordsOf(first.text).toList()
    val word = words.firstOrNull { it in COMMON_VERBS } ?: words.firstOrNull { it !in NOT_A_THEME } ?: "other"
    return "unread: ${first.kind.label.substringBefore(',')} $word"
}

private val COMMON_VERBS = setOf("add", "attach", "cast", "counter", "create", "deal", "destroy", "discard", "draw", "exile", "gain", "get", "look", "lose", "mill", "pay", "put",
    "remove", "return", "reveal", "sacrifice", "scry", "search", "shuffle", "surveil", "tap", "transform", "untap", "copy", "choose", "convert", "incubate")
private val NOT_A_THEME = setOf("target", "you", "may", "each", "your", "when", "whenever", "if", "creature", "card", "then", "another", "all", "other", "player", "opponent", "control")

/** Cards that need work, as chunks: unread ones first, each key alone when it is big enough, the small keys put together in key order. */
internal fun chunksOf(cards: List<PlannedCard>, mechanics: Set<String>): List<Chunk> {
    val groups = cards.filter { it.needsWork }.groupBy { groupKey(it, mechanics) }
    fun ordered(list: List<PlannedCard>) = list.sortedWith(compareBy({ !it.unread }, { it.name }))
    val whole = ArrayList<Pair<String, List<PlannedCard>>>()
    val small = ArrayList<Pair<String, List<PlannedCard>>>()
    for ((key, group) in groups.entries.sortedWith(compareBy({ it.value.none(PlannedCard::unread) }, { -it.value.size }, { it.key }))) {
        when {
            group.size < FEWEST_IN_A_CHUNK -> small += key to group
            group.size <= MOST_IN_A_CHUNK -> whole += key to ordered(group)
            else -> {
                val pieces = (group.size + MOST_IN_A_CHUNK - 1) / MOST_IN_A_CHUNK
                val size = (group.size + pieces - 1) / pieces
                ordered(group).chunked(size).forEachIndexed { i, piece -> whole += "$key, part ${i + 1}" to piece }
            }
        }
    }
    // The small keys: those with unread cards together, those with only tests together, about 15 to a chunk.
    val bins = ArrayList<Pair<String, List<PlannedCard>>>()
    for (needsRule in listOf(true, false)) {
        var keys = ArrayList<String>()
        var bin = ArrayList<PlannedCard>()
        fun close() {
            if (bin.isEmpty()) return
            val last = bins.lastOrNull()
            if (bin.size < FEWEST_IN_A_CHUNK && last != null && last.second.any(PlannedCard::unread) == needsRule && last.second.size + bin.size <= MOST_IN_A_CHUNK) {
                bins[bins.size - 1] = "${last.first}; ${keys.joinToString("; ")}" to (last.second + bin)
            } else {
                bins += keys.joinToString("; ") to bin
            }
            keys = ArrayList()
            bin = ArrayList()
        }
        for ((key, group) in small.filter { (_, g) -> g.any(PlannedCard::unread) == needsRule }.sortedBy { it.first }) {
            if (bin.size + group.size > MOST_IN_A_CHUNK) close()
            keys += key
            bin += ordered(group)
            if (bin.size >= 15) close()
        }
        close()
    }
    val all = whole.filter { it.second.any(PlannedCard::unread) } + bins.filter { it.second.any(PlannedCard::unread) } +
        whole.filter { it.second.none(PlannedCard::unread) } + bins.filter { it.second.none(PlannedCard::unread) }
    return all.mapIndexed { i, (theme, group) -> Chunk(i + 1, theme, group) }
}

/** The mechanics of a set: what MTGJSON lists on its cards that is no simple keyword, by how many cards name it. */
internal fun setMechanics(cards: List<PlannedCard>): Map<String, Int> =
    cards.flatMap { it.keywords }.groupingBy { it }.eachCount().filterKeys { it.lowercase().replace(' ', '-') !in SIMPLE_KEYWORDS }

/** Whether the API seems to have [word], which MTGJSON lists for a card: a keyword by name, or a word some tested model uses (`shapes`, lower case). */
internal fun knownMechanic(word: String, shapes: String): Boolean = KeywordKind.all.any { it.text.equals(word, ignoreCase = true) } ||
    word.endsWith("cycling") || "\"${word.lowercase().replace(' ', '-')}\"" in shapes

/** One card for the owner, and why. */
internal class DifficultCard(val name: String, val why: String)

/**
 * Candidates: several unread clauses, or a clause no rule comes near. A clause that only names a mechanic in [lacking] isn't listed
 * card by card: the mechanic is the owner's one question. Over-inclusive on purpose.
 */
internal fun difficultCards(cards: List<PlannedCard>, index: RuleIndex, lacking: Set<String>): List<DifficultCard> = cards.filter { it.unread }.mapNotNull { card ->
    val why = ArrayList<String>()
    val unread = card.explanation?.unread.orEmpty()
    if (unread.size >= SEVERAL_CLAUSES) why += "${unread.size} clauses unread"
    if (unread.isEmpty()) why += "its layout ${card.layout} or printed values can't be read"
    val newWords = lacking.map { it.lowercase().trimEnd('e') } // "incubat": incubate, Incubator
    unread.firstOrNull { c -> newWords.none { it in c.text.lowercase() } && (index.nearest(c.text, 1).firstOrNull()?.score ?: 0.0) < NEAR_ENOUGH }
        ?.let { why += "no rule comes near \"${it.text.replace("\n", " / ")}\"" }
    if (why.isEmpty()) null else DifficultCard(card.name, why.joinToString("; "))
}

private const val SEVERAL_CLAUSES = 3

/** All that a plan writes. [shapes]: every tested model, lower case, for what the API seems to know. */
internal class SetPlanOutput(val code: String, val cards: List<PlannedCard>, val index: RuleIndex, shapes: String) {
    private val mechanics = setMechanics(cards)
    /** What the cards name that the API seems not to have, with how many cards name it. */
    val newMechanics: Map<String, Int> = mechanics.filterKeys { !knownMechanic(it, shapes) }
    val chunks: List<Chunk> = chunksOf(cards, mechanics.filterValues { it >= 2 }.keys)
    val difficult: List<DifficultCard> = difficultCards(cards, index, newMechanics.keys)

    fun coldRead(): String = "%.1f%% (%d of %d)".format(java.util.Locale.ROOT, 100.0 * cards.count { !it.unread } / maxOf(1, cards.size), cards.count { !it.unread }, cards.size)

    fun planTsv(): String = "#card\tverdict\treason\tshapes\n" + cards.joinToString("") { c ->
        "${c.name}\t${c.verdict.verdict.code}\t${c.reason.replace('\t', ' ').replace('\n', ' ')}\t${c.verdict.shapes.joinToString(",")}\n"
    }

    fun chunkFile(chunk: Chunk): String = buildString {
        append("# Chunk ${chunk.number}: ${chunk.theme}\n")
        append("${chunk.cards.size} cards, ${chunk.cards.count { it.unread }} unread${if (chunk.mechanical) "; mechanical: only tests, no rule" else ""}\n")
        for (card in chunk.cards) {
            append("\n## ${card.name}\n")
            append(printedCard(card))
            append("\nVerdict: ${card.verdict.verdict.code}: ${card.reason}\n")
            card.explanation?.let { explanation ->
                append("Explain:\n")
                renderExplanation(explanation, index, width = 120, near = 5).forEach { append("  ").append(it).append('\n') }
            }
        }
    }

    fun digest(): List<String> = buildList {
        val unknown = newMechanics.entries.sortedWith(compareBy({ -it.value }, { it.key }))
        val readBy = { word: String -> cards.count { word in it.keywords && it.unread } }
        add("# $code set plan: digest")
        add("")
        add("Parser ${OracleParser.VERSION}. ${cards.size} cards, cold read ${coldRead()}: ${cards.count { it.unread }} UNREAD, ${cards.count { it.verdict.verdict == Verdict.Test }} TEST, ${cards.count { it.verdict.verdict == Verdict.Repeat }} REPEAT.")
        add("Layouts: " + cards.groupingBy { it.layout }.eachCount().entries.sortedBy { -it.value }.joinToString { "${it.key} ${it.value}" })
        add("")
        add("## New mechanics and keywords (the API seems not to have them: card count, unread)")
        unknown.take(MECHANICS_SHOWN).forEach { (word, n) -> add("- $word: $n, ${readBy(word)} unread") }
        if (unknown.isEmpty()) add("- none")
        if (unknown.size > MECHANICS_SHOWN) add("- and ${unknown.size - MECHANICS_SHOWN} more")
        add("")
        add("## Chunks")
        chunks.take(CHUNKS_SHOWN).forEach { c -> add("- ${c.file}: ${c.cards.size} cards, ${c.cards.count { it.unread }} unread${if (c.mechanical) ", mechanical" else ""}: ${c.theme.take(90)}") }
        if (chunks.size > CHUNKS_SHOWN) add("- and ${chunks.size - CHUNKS_SHOWN} more in chunks/")
        add("")
        add("Difficult candidates for the owner: ${difficult.size}, in difficult.md.")
        addAll(digestFacts(code))
    }

    fun difficultFile(): String = "# $code: difficult candidates for the owner\n\nHeuristic and over-inclusive.\n\n" +
        "## Concepts the API seems to lack\n" +
        newMechanics.entries.sortedBy { -it.value }.joinToString("") { (word, n) -> "- $word: named by $n cards, ${cards.count { word in it.keywords && it.unread }} unread\n" } +
        "\n## Cards\n" + difficult.joinToString("") { "- ${it.name}: ${it.why}\n" }

    /** The audit, only for the cards that read. */
    fun audit(): AuditPlan = auditPlan(cards.filter { !it.unread }.mapNotNull { card ->
        parsedCard(card.layout, card.faces)?.let { AuditCard(card.name, printedCard(card), it) }
    })

    fun writeTo(dir: File) {
        val chunkDir = File(dir, "chunks")
        chunkDir.deleteRecursively()
        chunkDir.mkdirs()
        File(dir, "plan.tsv").writeText(planTsv())
        chunks.forEach { File(chunkDir, it.file).writeText(chunkFile(it)) }
        File(dir, "digest.md").writeText(digest().joinToString("\n", postfix = "\n"))
        File(dir, "difficult.md").writeText(difficultFile())
        File(dir, "notes.md").let { if (!it.exists()) it.writeText("") }
        val auditDir = File(dir, "audit").apply { mkdirs() }
        // Results refer to the entries as they were: a later plan keeps that audit.
        if (auditDir.listFiles().orEmpty().any { it.name.startsWith("results-") }) {
            println("Kept the audit in $auditDir: it has results")
            return
        }
        auditDir.listFiles().orEmpty().forEach { it.delete() }
        val audit = audit()
        audit.batches.forEachIndexed { i, batch ->
            File(auditDir, "batch-%02d.md".format(i + 1)).writeText(batch.joinToString("\n") { e -> "## ${e.id} ${e.card}\nOracle:\n${e.oracle}\nModel:\n${e.model}\n" })
        }
        File(auditDir, "seeded.tsv").writeText("#entry\tcard\tmutation\n" + audit.seeded.joinToString("") { "${it.entry}\t${it.card}\t${it.mutation.code}\n" })
    }
}

private const val MECHANICS_SHOWN = 25
private const val CHUNKS_SHOWN = 45

/** A card's faces as printed: cost, type, P/T and text, one face after the other. */
internal fun printedCard(card: PlannedCard): String = card.faces.joinToString("\n//\n") { face ->
    val stats = listOfNotNull(
        face.power?.let { "$it/${face.toughness}" },
        face.loyalty?.let { "loyalty $it" },
        face.defense?.let { "defense $it" },
    ).joinToString(" ")
    val heading = listOfNotNull(face.manaCost, face.typeLine, stats.ifEmpty { null }).joinToString(" | ")
    if (face.text.isEmpty()) heading else "$heading\n${face.text}"
}

/** The short facts a chunk worker needs instead of the READMEs. */
private fun digestFacts(code: String): List<String> {
    val lower = code.lowercase()
    return """

## Facts
- Parser sources: `arcanum-parser/src/commonMain/kotlin/arcanum/parser/`, wordings of past sets in `<Set>Wordings.kt`, the sentence rules in `Sentences.kt`, the
  phrases in `Phrases.kt`, keywords in `KeywordSyntax.kt` and `OracleParser.keyword`. Model: `arcanum-api/src/commonMain/kotlin/arcanum/api/` (`Definitions.kt`,
  `Effects.kt`, `Mechanics.kt`, `Selectors.kt`, `Values.kt`, `Keywords.kt`).
- `./scripts/gradle.sh :arcanum-tools:parserExplain --args='"<text>"'` names the first unread clause and the nearest rules.
- Bump `OracleParser.VERSION` when output changes.

## Parser tests
`arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/$lower/<Code>ChunkNN<Theme>Test.kt`, one test a card; imports `arcanum.api.*` and from `arcanum.parser.cube`:
`card(name, cost, type, text, pt, loyalty, colorIndicator)`, `parsesTo`, `abilities(...)`, `none`, `cardFaces(layout, card(...) to abilities(...), ...)`, `manaCost("{1}")`,
`thisEnters`, `whenThisEnters(steps...)`, `drawCards(n)`, `objectSlot(name, filter)`, `allOf(...)`. Each set keeps its own helpers beside its tests in `<Code>Cards.kt`; start from the filters at the top
of `sets/tdm/TdmCards.kt` (`creature`, `land`, `artifact`, `you`, `opponents`, `isSelf`).
Multi-line text is one rules line per source line, named arguments always, the expected value from the oracle text and the CR.
```kotlin
class ${code.lowercase().replaceFirstChar { it.uppercase() }}Chunk01ExampleTest {
    // CR 603.6a
    @Test
    fun gildedStag() = card(
        name = "Gilded Stag",
        cost = "{1}{W}",
        type = "Creature — Elk",
        pt = "2/2",
        text = "When this creature enters, draw a card.",
    ) parsesTo abilities(
        whenThisEnters(drawCards(1)),
    )
}
```

## Engine tests
`arcanum-engine/src/commonTest/kotlin/arcanum/engine/sets/$lower/`. Invent the card once per class; the text must parse fully:
`Cards.invent(name, cost, type, text, pt)` returns the name; `Cards.inventFaces(layout, face(name, cost, type, text, pt, loyalty, colorIndicator), ...)` for several faces.
`scenario { ... }`: setup, then moves, then expectations. It also checks every change reverts. Setup: `alice.battlefield(...)`, `alice.hand(...)`, `.library(...)`, `.graveyard(...)`, `turn(number, active, step)`.
Moves: `alice.casts(name, target = ...)`, `.activates(...)`, `.plays(...)`, `.attacks(...)`, `.blocks(a to b)`, `.chooses(...)`, `allPass()`, `resolve()`, `advanceTo(step)`. Answers: `alice.answers(...)`.
```kotlin
class ${code.lowercase().replaceFirstChar { it.uppercase() }}ExampleScenariosTest {
    private val gildedStag = Cards.invent("Gilded Stag", "{1}{W}", "Creature — Elk", "When this creature enters, draw a card.", "2/2")

    // CR 603.3
    @Test
    fun drawsWhenItEnters() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(gildedStag)
        alice.library("Island")
        alice.casts(gildedStag)
        allPass()
        allPass()
        expect { alice.hand has "Island" }
    }
}
```
Run only yours: `./scripts/gradle.sh :arcanum-parser:jvmTest --tests 'arcanum.parser.sets.$lower.*'` and `:arcanum-engine:jvmTest --tests 'arcanum.engine.sets.$lower.*'`.""".split('\n')
}
