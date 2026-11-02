package arcanum.engine.cards

import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser

/** One invented cube card as printed: its layout's name and its faces ([CubeCardTexts], generated). */
internal class CubeCardText(val layout: String, val faces: List<OracleCard>) {
    constructor(layout: String, vararg faces: OracleCard) : this(layout, faces.toList())

    val name: String get() = faces.joinToString(" // ") { it.name }
}

/** One printed face, as the parser tests' `card(…)` writes it. */
internal fun face(
    name: String, cost: String?, type: String, text: String, pt: String?, loyalty: String?, colorIndicator: String?,
): OracleCard {
    val (power, toughness) = pt?.split('/')?.let { it[0] to it[1] } ?: (null to null)
    return OracleCard(name, cost, type, text, power, toughness, loyalty, colorIndicator = colorIndicator)
}

/**
 * Card definitions for engine tests, by name (D61): the invented cube cards of the parser tests (498+, D46), the basic
 * lands, cards a test invents itself ([invent]), and hand-written definitions for structures the cube lacks
 * ([InventedCards]). Each is parsed from its oracle text once, on first use, and shared by all tests.
 */
object Cards {
    private val texts: Map<String, CubeCardText> by lazy { CubeCardTexts.all.associateBy { it.name } }
    private val invented = mutableMapOf<String, CubeCardText>()
    private val parsed = mutableMapOf<String, CardDefinition>()
    private val written = mutableMapOf<String, CardDefinition>()

    /**
     * Adds a card written by hand in the model (spec §4.4), for an effect no cube card has and the parser can't read
     * yet; the test names the oracle text it stands for. Returns [name].
     */
    fun define(name: String, definition: CardDefinition): String {
        val existing = written[name]
        require(existing == null || existing == definition) { "Card \"$name\" defined twice differently" }
        require(name !in texts && name !in InventedCards.all) { "\"$name\" is a card already" }
        written[name] = definition
        return name
    }

    /** Every cube card name. */
    val cubeNames: List<String> get() = CubeCardTexts.all.map { it.name }

    /** The definition of the card named [name] (a multi-faced card by either its full "A // B" name or its first
     * face's name). */
    fun definition(name: String): CardDefinition = InventedCards.all[name] ?: written[name] ?: parsed.getOrPut(name) {
        val text = invented[name] ?: texts[name] ?: BASICS[name]
            ?: CubeCardTexts.all.firstOrNull { it.faces.first().name == name }
            ?: invented.values.firstOrNull { it.faces.size > 1 && it.faces.first().name == name }
            ?: error("No test card named \"$name\"")
        parse(text)
    }

    /**
     * Adds an invented card for tests that need a wording the cube lacks ("Nonbasic lands are Mountains."): same fields
     * as the parser tests' `card(…)`. Its text must parse fully.
     */
    fun invent(
        name: String, cost: String?, type: String, text: String = "", pt: String? = null, loyalty: String? = null,
    ): String {
        val card = CubeCardText("Normal", face(name, cost, type, if (text.startsWith("\n")) text.trimIndent() else text, pt, loyalty, null))
        val existing = invented[name]
        require(existing == null || existing.faces == card.faces) { "Invented card \"$name\" defined twice differently" }
        require(name !in texts) { "\"$name\" is a cube card already" }
        invented[name] = card
        return name
    }

    /**
     * Adds an invented card of several faces, each as [face] writes it ([layout] as in [CubeCardTexts]: "Split" for a
     * Room); found by its full "A // B" name or its first face's name. Its text must parse fully. Returns the full name.
     */
    fun inventFaces(layout: String, vararg faces: OracleCard): String {
        val card = CubeCardText(layout, faces.map { if (it.text.startsWith("\n")) it.copy(text = it.text.trimIndent()) else it })
        val existing = invented[card.name]
        require(existing == null || existing.faces == card.faces) { "Invented card \"${card.name}\" defined twice differently" }
        require(card.name !in texts) { "\"${card.name}\" is a cube card already" }
        invented[card.name] = card
        return card.name
    }

    private fun parse(text: CubeCardText): CardDefinition {
        val layout = LAYOUTS[text.layout] ?: error("Unknown layout ${text.layout}")
        val definition = if (layout == Layout.Normal) CardDefinition(Layout.Normal, listOf(OracleParser.parse(text.faces.single())))
            else OracleParser.parseCard(layout, text.faces)
        val unparsed = definition.faces.flatMap { it.abilities }.filterIsInstance<arcanum.api.Ability.Unparsed>()
        require(unparsed.isEmpty()) { "${text.name} doesn't parse fully: $unparsed" }
        return definition
    }

    private val LAYOUTS = mapOf(
        "Normal" to Layout.Normal, "Split" to Layout.Split, "Flip" to Layout.Flip, "Transform" to Layout.Transform,
        "ModalDoubleFaced" to Layout.ModalDoubleFaced, "Adventure" to Layout.Adventure, "Omen" to Layout.Omen,
    )

    /** The basic lands (CR 305.6): no rules text; their mana ability comes from their land type. */
    private val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").associateWith { land ->
        CubeCardText("Normal", face(land, null, "Basic Land — $land", "", null, null, null))
    }
}
