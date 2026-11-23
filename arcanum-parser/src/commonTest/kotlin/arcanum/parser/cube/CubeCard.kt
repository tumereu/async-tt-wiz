package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.DefinitionChecks
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import arcanum.parser.PrintedNumbers
import arcanum.parser.TargetSpans
import arcanum.parser.TypeLine
import kotlin.test.assertEquals

/**
 * Helpers for the cube parsing tests (D43, D46): every card of the first target cube, one test each. A test card is
 * invented: a new name in the spirit of the original and, where it doesn't change how the card plays, a different
 * mana cost or subtypes. Its rules text is the original's exact oracle text (with the new name). Each test states what
 * the text must parse to; the helper also checks that the printed characteristics read back unchanged, that nothing is
 * left unparsed and that the definition passes [DefinitionChecks].
 */
fun card(
    name: String, cost: String?, type: String, text: String = "", pt: String? = null, loyalty: String? = null,
    colorIndicator: String? = null,
): OracleCard {
    val (power, toughness) = pt?.split('/')?.let { it[0] to it[1] } ?: (null to null)
    // Multi-line text is written one rules line per source line, indented: strip the indentation.
    val oracle = if (text.startsWith("\n")) text.trimIndent() else text
    return OracleCard(name, cost, type, oracle, power, toughness, loyalty, colorIndicator = colorIndicator)
}

infix fun OracleCard.parsesTo(expected: List<Ability>) {
    val face = OracleParser.parse(this)
    checkFace(this, face, expected)
    assertEquals(emptyList(), DefinitionChecks.problems(CardDefinition(Layout.Normal, listOf(face))), name)
    recordCoveredAbilities(face.abilities)
}

/** A card with two faces or halves (CR 709 split, 712 double-faced, 716 adventurer): each face, written as
 * `card(…) to abilities(…)`, is checked like [parsesTo], and the whole card's definition passes [DefinitionChecks]. */
fun cardFaces(layout: Layout, vararg faces: Pair<OracleCard, List<Ability>>) {
    val definition = OracleParser.parseCard(layout, faces.map { it.first })
    assertEquals(layout, definition.layout)
    faces.zip(definition.faces).forEach { (printed, face) -> checkFace(printed.first, face, printed.second) }
    assertEquals(emptyList(), DefinitionChecks.problems(definition), faces.first().first.name)
    definition.faces.forEach { recordCoveredAbilities(it.abilities) }
}

/** The printed characteristics read back unchanged and the abilities are [expected]; their texts are checked apart
 * ([checkTexts]), so expectations state what the text means. */
private fun checkFace(printed: OracleCard, face: FaceDefinition, expected: List<Ability>) {
    assertEquals(printed.typeLine, TypeLine.render(face), "type line")
    assertEquals(printed.manaCost, face.manaCost?.toString(), "mana cost")
    assertEquals(printed.colorIndicator, face.colorIndicator?.joinToString("") { it.symbol.toString() }, "colour indicator")
    // A station card's P/T box is part of its striation's ability (CR 722.2b, checked with the abilities), not the face's (CR 208.3)
    val pt = if (Keyword.Station in face.abilities) listOf(null, null) else listOf(printed.power, printed.toughness)
    assertEquals(pt + printed.loyalty, listOf(face.power, face.toughness, face.loyalty).map(PrintedNumbers::render), "P/T, loyalty")
    assertEquals(expected, face.abilities.mapIndexed { i, ability -> withoutTextUnstated(ability, expected.getOrNull(i)) }, printed.name)
    checkTexts(printed, face.abilities)
    checkManaWords(printed, face.abilities)
    checkSpans(printed.name, face.abilities)
    checkCostSpans(printed.name, face.abilities)
}

/**
 * The mana a parsed ability adds says its riders in the card's words ([arcanum.api.ManaProduction.words], CR 106.4, 106.6): each
 * production with a rider has words, each a sentence the card prints. Expectations state them only where a test is about them.
 */
fun checkManaWords(printed: OracleCard, abilities: List<Ability>) {
    for (production in abilities.flatMap(::manaWords)) {
        val riders = listOf("restriction", "onlyCostsWithX", "spellGets", "keptUntil", "whenSpent").filter { it in production }
        val words = (production["words"] as? kotlinx.serialization.json.JsonArray)?.map { (it as kotlinx.serialization.json.JsonPrimitive).content }.orEmpty()
        if (riders.isEmpty()) {
            assertEquals(emptyList(), words, "${printed.name}: mana without riders has words")
            continue
        }
        assertEquals(true, words.isNotEmpty(), "${printed.name}: mana with $riders has no words")
        for (w in words) assertEquals(true, w in printed.text, "${printed.name}: mana words \"$w\" aren't printed on the card")
    }
}

/** Whether [e] is a mana production ([arcanum.api.ManaProduction]): its kind one of the mana kinds. */
private fun manaProduction(e: kotlinx.serialization.json.JsonObject) =
    ((e["kind"] as? kotlinx.serialization.json.JsonObject)?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content in
        setOf("same-as-produced", "fixed", "choose", "as-could", "each-color-among")

/** The mana productions inside [ability] with riders or words, through its JSON form. */
private fun manaWords(ability: Ability): List<kotlinx.serialization.json.JsonObject> {
    val found = mutableListOf<kotlinx.serialization.json.JsonObject>()
    fun walk(e: kotlinx.serialization.json.JsonElement) {
        when (e) {
            is kotlinx.serialization.json.JsonObject -> {
                if (manaProduction(e) && listOf("words", "restriction", "onlyCostsWithX", "spellGets", "keptUntil", "whenSpent").any { it in e }) found += e
                e.values.forEach(::walk)
            }
            is kotlinx.serialization.json.JsonArray -> e.forEach(::walk)
            else -> {}
        }
    }
    walk(definitionJson.encodeToJsonElement(Ability.serializer(), ability))
    return found
}

/**
 * Each cost part of a parsed ability knows where its phrase sits in its printed text ([Cost.spans]): every activated
 * ability's cost written before a colon has a span for each part, in order without overlapping; a static ability's
 * additional or alternative cost has them when it has any, each one a phrase of its text.
 */
fun checkCostSpans(name: String, abilities: List<Ability>) {
    fun check(text: String?, cost: Cost, required: Boolean, what: String) {
        if (text == null) return
        if (cost.spans.isEmpty()) {
            assertEquals(false, required, "$name: $what cost ${cost.parts} has no spans in \"$text\"")
            return
        }
        assertEquals(cost.parts.size, cost.spans.size, "$name: $what cost ${cost.parts} has ${cost.spans.size} spans in \"$text\"")
        var last: arcanum.api.TextSpan? = null
        for ((part, span) in cost.parts.zip(cost.spans)) {
            val phrase = span.of(text)
            assertEquals(true, !phrase.isNullOrBlank() && phrase.trim() == phrase,
                "$name: $what cost part $part's span $span (\"$phrase\") isn't a phrase of \"$text\"")
            assertEquals(true, last == null || span.start >= last.end,
                "$name: $what cost part $part's span $span overlaps or precedes the one before it in \"$text\"")
            last = span
        }
    }
    abilities.forEach { ability ->
        when (ability) {
            is Ability.Activated -> check(ability.text, ability.cost, required = ability.text?.contains(':') == true, what = "activated")
            is Ability.Static -> ability.mechanics.forEach { m ->
                when (m) {
                    is arcanum.api.Mechanic.AdditionalCost -> check(ability.text, m.cost, required = false, what = "additional")
                    is arcanum.api.Mechanic.AlternativeCost -> check(ability.text, m.cost, required = false, what = "alternative")
                    else -> {}
                }
            }
            is arcanum.api.Keyword.ClassLevel -> checkCostSpans(name, ability.abilities)
            else -> {}
        }
    }
}

/**
 * Each target slot of a parsed ability knows where its phrase sits in the ability's text, or a mode's slot in the mode's
 * text ([TargetSlot.span]): every slot has one, unless its text names no target of its own (a keyword action that
 * targets, "earthbend 2", "support 2"; targets only in a reflexive trigger's sentence); it names a target ("any
 * target", "up to two target creatures"), and a text's spans come in order without overlapping (slots sharing one phrase,
 * "two target creatures controlled by different players", share its span).
 */
fun checkSpans(name: String, abilities: List<Ability>) {
    fun check(text: String?, instructions: Instructions) {
        if (text == null) return
        var last: arcanum.api.TextSpan? = null
        for (slot in instructions.targets) {
            val span = slot.span
            if (span == null) {
                assertEquals(emptyList(), TargetSpans.phrases(TargetSpans.ownText(text, instructions)),
                    "$name: slot ${slot.name} has no span in \"$text\"")
                continue
            }
            val phrase = span.of(text)
            assertEquals(true, phrase != null && Regex("\\btargets?\\b", RegexOption.IGNORE_CASE).containsMatchIn(phrase),
                "$name: slot ${slot.name}'s span $span (\"$phrase\") isn't a target phrase of \"$text\"")
            assertEquals(true, last == null || span == last || span.start >= last.end,
                "$name: slot ${slot.name}'s span $span overlaps or precedes the one before it in \"$text\"")
            last = span
        }
        instructions.modes?.options?.forEach { check(it.text, it.instructions) }
    }
    abilities.forEach { ability ->
        when (ability) {
            is Ability.Spell -> check(ability.text, ability.instructions)
            is Ability.Activated -> check(ability.text, ability.instructions)
            is Ability.Triggered -> check(ability.text, ability.instructions)
            is arcanum.api.Keyword.ClassLevel -> checkSpans(name, ability.abilities)
            else -> {}
        }
    }
}

/**
 * [ability] without its own printed text and its modes' texts, and without what [expected] doesn't state of the abilities
 * inside it (emblems', quoted ones: their texts and the places of their phrases), which [checkTexts] checks apart; what
 * [expected] states of them is compared.
 */
private fun withoutTextUnstated(ability: Ability, expected: Ability?): Ability {
    val own = withoutOwnText(ability)
    if (expected == null || (quoted(own).isEmpty() && manaWords(own).isEmpty())) return own
    fun strip(actual: kotlinx.serialization.json.JsonElement, stated: kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonElement =
        when (actual) {
            is kotlinx.serialization.json.JsonObject -> {
                val e = stated as? kotlinx.serialization.json.JsonObject
                kotlinx.serialization.json.JsonObject(actual.filterKeys { k ->
                    val droppable = k == "span" || (k == "text" && texted(actual)) || (k == "spans" && "parts" in actual) ||
                        (k == "words" && manaProduction(actual))
                    !(droppable && e != null && k !in e)
                }.mapValues { (k, v) -> strip(v, e?.get(k)) })
            }
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(actual.mapIndexed { i, v ->
                strip(v, (stated as? kotlinx.serialization.json.JsonArray)?.getOrNull(i)) })
            else -> actual
        }
    return definitionJson.decodeFromJsonElement(Ability.serializer(), strip(definitionJson.encodeToJsonElement(Ability.serializer(), own),
        definitionJson.encodeToJsonElement(Ability.serializer(), expected)))
}

/** [ability] without its printed text, its modes' texts and the texts of the abilities inside it, emblems' and quoted ones ([withoutQuotedTexts]). */
fun withoutText(ability: Ability): Ability = withoutOwnText(withoutQuotedTexts(ability))

private fun withoutOwnText(ability: Ability): Ability = when (ability) {
    is Ability.Spell -> ability.copy(text = null, instructions = withoutText(ability.instructions))
    is Ability.Activated -> ability.copy(text = null, cost = ability.cost.copy(spans = emptyList()), instructions = withoutText(ability.instructions))
    is Ability.Triggered -> ability.copy(text = null, instructions = withoutText(ability.instructions))
    is Ability.Static -> ability.copy(text = null, mechanics = ability.mechanics.map { m ->
        when (m) {
            is arcanum.api.Mechanic.AdditionalCost -> m.copy(cost = m.cost.copy(spans = emptyList()))
            is arcanum.api.Mechanic.AlternativeCost -> m.copy(cost = m.cost.copy(spans = emptyList()))
            else -> m
        }
    })
    is Ability.SpecialAction -> ability.copy(text = null, cost = ability.cost.copy(spans = emptyList()), instructions = withoutText(ability.instructions))
    // blb: a class level bar's abilities keep their lines too
    is arcanum.api.Keyword.ClassLevel -> ability.copy(abilities = ability.abilities.map(::withoutText))
    // A keyword's cost keeps where its parts sit in the keyword's line (checked by [checkCostSpans])
    is arcanum.api.Keyword -> withoutCostSpans(ability)
    else -> ability
}

/** [keyword] without its cost's spans ([Cost.spans]), through its JSON form as the parser adds them. */
internal fun withoutCostSpans(keyword: arcanum.api.Keyword): Ability {
    val cost = (keyword as? arcanum.api.HasCost)?.cost ?: (keyword as? arcanum.api.Keyword.Mayhem)?.cost
    if (cost == null || cost.spans.isEmpty()) return keyword
    val encoded = definitionJson.encodeToJsonElement(Ability.serializer(), keyword) as kotlinx.serialization.json.JsonObject
    val plain = definitionJson.encodeToJsonElement(Cost.serializer(), cost.copy(spans = emptyList()))
    return definitionJson.decodeFromJsonElement(Ability.serializer(), kotlinx.serialization.json.JsonObject(encoded + ("cost" to plain)))
}

/** [instructions] without its modes' texts and its slots' places in the text ([TargetSlot.span], checked by
 * [checkSpans]). */
private fun withoutText(instructions: Instructions): Instructions = instructions.copy(
    targets = instructions.targets.map { it.copy(span = null) },
    modes = instructions.modes?.let { m -> m.copy(options = m.options.map { it.copy(text = null, instructions = withoutText(it.instructions)) }) })

private val definitionJson = kotlinx.serialization.json.Json

/** Each emblem's abilities (CR 114.2) wherever [ability] creates one, through its JSON form (emblems sit at any depth of
 * the instructions). */
private fun emblems(ability: Ability): List<kotlinx.serialization.json.JsonObject> {
    if ("CreateEmblem(" !in ability.toString()) return emptyList()
    val found = mutableListOf<kotlinx.serialization.json.JsonObject>()
    fun walk(e: kotlinx.serialization.json.JsonElement) {
        when (e) {
            is kotlinx.serialization.json.JsonObject -> {
                if ((e["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "emblem")
                    (e["abilities"] as kotlinx.serialization.json.JsonArray).forEach { found += it as kotlinx.serialization.json.JsonObject }
                e.values.forEach(::walk)
            }
            is kotlinx.serialization.json.JsonArray -> e.forEach(::walk)
            else -> {}
        }
    }
    walk(definitionJson.encodeToJsonElement(Ability.serializer(), ability))
    return found
}

/** The serial names of the abilities that carry a printed text of their own. */
private val TEXTED = setOf("spell", "activated", "triggered", "static", "special-action")

/** Whether [e] is an ability that carries a printed text of its own ([TEXTED]). */
private fun texted(e: kotlinx.serialization.json.JsonObject) =
    (e["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content in TEXTED && ("instructions" in e || "mechanics" in e)

/** The abilities inside [ability] (an emblem's, CR 114.2; a token's or a grant's quoted one, CR 113.1a, 111.4), through its
 * JSON form (they sit at any depth of it). */
private fun quoted(ability: Ability): List<kotlinx.serialization.json.JsonObject> {
    val found = mutableListOf<kotlinx.serialization.json.JsonObject>()
    fun walk(e: kotlinx.serialization.json.JsonElement, top: Boolean) {
        when (e) {
            is kotlinx.serialization.json.JsonObject -> {
                if (!top && texted(e)) found += e
                e.values.forEach { walk(it, false) }
            }
            is kotlinx.serialization.json.JsonArray -> e.forEach { walk(it, false) }
            else -> {}
        }
    }
    walk(definitionJson.encodeToJsonElement(Ability.serializer(), ability), true)
    return found
}

/** [ability] with the abilities inside it (emblems', quoted ones: [quoted]) without their printed texts and the places of
 * their phrases in them (target slots' and cost parts' spans), which [checkTexts] checks apart. */
private fun withoutQuotedTexts(ability: Ability): Ability {
    if (quoted(ability).isEmpty()) return ability
    fun strip(e: kotlinx.serialization.json.JsonElement, top: Boolean): kotlinx.serialization.json.JsonElement = when (e) {
        is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(
            (if (!top && texted(e)) e - "text" else e).let { o -> if ("parts" in o) o - "spans" else o }
                .minus("span").mapValues { strip(it.value, false) })
        is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { strip(it, false) })
        else -> e
    }
    return definitionJson.decodeFromJsonElement(Ability.serializer(),
        strip(definitionJson.encodeToJsonElement(Ability.serializer(), ability), true))
}

/**
 * Each parsed ability keeps its printed text: one or more whole lines of the card, in order, without reminder text (a
 * spell's lines, a modal spell's "Choose one —" with its "• " lines, a leveler band); each mode its "• " line without
 * the bullet. Keywords have their own text; unparsed text stays as printed.
 */
private fun checkTexts(printed: OracleCard, abilities: List<Ability>) {
    val lines = printed.text.lines().map { Regex("""\s*\([^)]*\)""").replace(it, "").trim() }.filter { it.isNotEmpty() }
    var from = 0
    // CR 114.2: an emblem's ability keeps its quoted text as printed
    for (emblem in abilities.flatMap(::emblems)) {
        val text = (emblem["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: kotlin.test.fail("${printed.name}: an emblem's ability has no text: $emblem")
        // CR 114.3: an emblem of several abilities, 'with "[first]" and "[second]"'
        assertEquals(true, "emblem with \"$text\"" in printed.text || "\" and \"$text\"" in printed.text,
            "${printed.name}: emblem text \"$text\" isn't quoted on the card")
    }
    // CR 113.1a, 111.4 (owner fault 2PTQG5): an ability quoted inside another (a token's, a grant's) keeps words the card prints
    // (an ability quoted inside a quoted one is in single quotes on the card, CR 113.1a)
    val plain = printed.text.replace('\n', ' ').replace('\'', '"')
    for (inner in abilities.flatMap(::quoted)) {
        val text = (inner["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
        assertEquals(true, text.replace('\'', '"').removeSuffix(".").lines().all { it.trim().removeSuffix(".") in plain },
            "${printed.name}: quoted ability text \"$text\" isn't words of the card")
    }
    abilities.forEach { ability ->
        val (text, instructions) = when (ability) {
            is Ability.Spell -> ability.text to ability.instructions
            is Ability.Activated -> ability.text to ability.instructions
            is Ability.Triggered -> ability.text to ability.instructions
            is Ability.Static -> ability.text to null
            is Ability.SpecialAction -> ability.text to ability.instructions
            else -> return@forEach
        }
        val own = text?.lines() ?: kotlin.test.fail("${printed.name}: no text for $ability")
        val at = (from until lines.size).firstOrNull { lines.drop(it).take(own.size) == own }
            ?: (0 until lines.size).firstOrNull { lines.drop(it).take(own.size) == own }
            ?: kotlin.test.fail("${printed.name}: \"$text\" isn't the card's lines in order: $lines")
        from = at
        instructions?.modes?.options?.forEach { mode ->
            // blb chunk 06: a pawprint mode keeps its whole line ("{P} — Draw a card.", CR 700.2i)
            assertEquals(true, "• ${mode.text}" in own || mode.text in own, "${printed.name}: mode text \"${mode.text}\" isn't one of the ability's lines")
        }
    }
}

/** A card with no rules text. */
val none: List<Ability> = emptyList()

fun abilities(vararg abilities: Ability): List<Ability> = abilities.toList()

/** A cost of only mana, as written ("{2}{U}"). */
fun manaCost(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)

/** "[amount] mana of any one color": the player chooses one colour for all of it (CR 106.1b). */
fun anyOneColor(amount: Int): ManaKind = ManaKind.Choose(amount = Value.Fixed(amount))

// ---- Enters-the-battlefield creatures (chunks 30–33)

/** "When this creature enters" (CR 603.6a). */
val thisEnters: Trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))

/** "When this creature enters, [steps]" with its targets and intervening "if" (CR 603.4). */
fun whenThisEnters(vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null) = Ability.Triggered(
    trigger = thisEnters,
    instructions = Instructions(targets = targets, steps = steps.toList()),
    interveningIf = interveningIf,
)

/** "[You] draw [n] cards." */
fun drawCards(n: Int, players: Players = Players.You): Step = Step.Do(Effect.Draw(players, count = Value.Fixed(n)))

/** A target slot of objects. */
fun objectSlot(name: String, filter: ObjectFilter, min: Int = 1) =
    TargetSlot(name = name, what = TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(1))

/** Each of [filters], as one "and". */
fun allOf(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())

/** Your graveyard, as a zone filter. */
val inYourGraveyard: List<ObjectFilter> = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
