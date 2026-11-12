@file:OptIn(ArcanumTestSetup::class)

package arcanum.tools

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.api.isManaAbility
import arcanum.engine.CastScreen
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.ArcanumTestSetup
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import arcanum.engine.state.start
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import java.io.File
import kotlin.system.exitProcess

/**
 * A local check of castability over every card of the supported sets (castability spec §6), beside [main] of SetSupport, from the same
 * pinned MTGJSON file (never committed):
 *
 * - each mana ability is put on a test board and must pay for a small spell through a payment by hand (CR 605.3a): `pays`, or `FAILS`
 *   with why.
 * - each cost reducer is tried on a test board and must lower a cost: `lowers`, or `look` when it didn't on that board (its condition
 *   may ask for a board this tool doesn't make): a list to read, not a failure.
 *
 * Writes everything to `build/arcanum/mana-check.tsv` and exits with 1 when a mana ability fails.
 * Args: <output file> [set codes, comma-separated; default every supported set].
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: ManaCheck <output file> [CODE,CODE,...]" }
    val supported = supportedSets()
    val codes = args.getOrNull(1)?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }.orEmpty().ifEmpty { supported.keys.toList() }
    val out = File(args[0]).apply { parentFile.mkdirs() }
    val sets = LinkedHashMap<String, List<SetCard>>()
    forEachPinnedSet(codes) { code, records -> sets[code] = setCards(records) }
    val seen = mutableSetOf<String>()
    val lines = mutableListOf("set\tcard\tkind\tresult\tability")
    var failed = 0
    for (code in codes) for (card in sets.getValue(code)) {
        if (!seen.add(card.name)) continue
        val definition = runCatching {
            val printed = card.faces.map(::oracleCard)
            OracleParser.parseCard(OracleParser.layout(card.layout, printed) ?: return@runCatching null, printed)
        }.getOrNull() ?: continue
        for (face in definition.faces) for (ability in face.abilities) {
            val text = (ability as? Ability.Activated)?.text ?: (ability as? Ability.Static)?.text ?: (ability as? Ability.Triggered)?.text ?: ability::class.simpleName
            if (ability is Ability.Activated && ability.isManaAbility()) {
                val result = runCatching { ManaBoards.manaAbilityPays(definition, ability) }.getOrElse { "FAILS: ${it::class.simpleName} ${it.message?.take(120)}" }
                if (result.startsWith("FAILS")) failed++
                lines += "$code\t${card.name}\tmana ability\t$result\t${text?.replace('\n', ' ')}"
            }
            if (ability is Ability.Triggered && ability.isManaAbility()) lines += "$code\t${card.name}\ttriggered mana\tlisted\t${text?.replace('\n', ' ')}"
            val reduces = (ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.CostChange>().any { it.change is CostChange.Less }
            if (reduces) {
                val result = runCatching { ManaBoards.reducerLowers(definition) }.getOrElse { "look: ${it::class.simpleName} ${it.message?.take(120)}" }
                lines += "$code\t${card.name}\tcost reducer\t$result\t${text?.replace('\n', ' ')}"
            }
        }
    }
    out.writeText(lines.joinToString("\n") + "\n")
    val rows = lines.drop(1).map { it.split('\t') }
    println("Mana abilities: ${rows.count { it[2] == "mana ability" }}, failing $failed; triggered mana ${rows.count { it[2] == "triggered mana" }}; " +
        "cost reducers ${rows.count { it[2] == "cost reducer" }}, lowered on a test board ${rows.count { it[2] == "cost reducer" && it[3] == "lowers" }}. Report: $out")
    rows.filter { it[3].startsWith("FAILS") }.forEach { println("  ${it[1]}: ${it[3]}   [${it[4]}]") }
    if (failed > 0) exitProcess(1)
}

/** Test boards made directly on a game state (the engine's test setup: this tool lives with the tests for that). */
private object ManaBoards {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private var next = 0

    private fun definition(name: String, type: String, cost: String?, text: String, pt: String? = null): CardDefinition =
        OracleParser.parseCard(Layout.Normal, listOf(OracleCard(name = name, typeLine = type, manaCost = cost, text = text,
            power = pt?.substringBefore('/'), toughness = pt?.substringAfter('/'))))

    private val anyLand = definition("Check Any Land", "Land", null, "{T}: Add one mana of any color.")
    private val helper = definition("Check Helper", "Artifact Creature — Elemental", "{1}", "", "1/1")
    private val landHelper = definition("Check Spare Land", "Land", null, "")
    private val filler = definition("Check Filler", "Sorcery", "{1}", "Draw a card.")
    private val creatureSpell = definition("Check Creature", "Creature — Elemental", "{4}", "", "1/1")
    private val instantSpell = definition("Check Instant", "Instant", "{4}", "Draw a card.")
    private val sorcerySpell = definition("Check Sorcery", "Sorcery", "{4}", "Draw a card.")
    private val artifactSpell = definition("Check Artifact", "Artifact", "{4}", "")
    private val enchantmentSpell = definition("Check Enchantment", "Enchantment", "{4}", "")

    private fun game(): GameState = GameState.start(listOf(alice, bob)).also { s ->
        s.setup.turn = s.turn.copy(turnStarted = mapOf(alice to 1, bob to 0))
        s.players.indices.forEach { i -> s.setup.players[i] = s.players[i].copy(library = emptyList()) }
    }

    private fun GameState.put(definition: CardDefinition, zone: Zone, counters: List<Pair<CounterKind, Int>> = emptyList()): ObjectId {
        val id = ObjectId.Card(CardId("alice.${++next}"), 0)
        val on = zone == Zone.Battlefield
        with(setup) {
            val t = ++timestampCounter
            objects[id] = GameObject(id = id, owner = alice, controller = if (on) alice else null, zone = zone, definition = definition,
                counters = counters.map { (kind, n) -> CounterEntry(kind, n, t) }, timestamp = t, controlSince = if (on) ControlSince(alice, -1) else null)
            if (on) battlefield.add(id) else {
                val p = players.indexOfFirst { it.id == alice }
                players[p] = players[p].copy(hand = players[p].hand + id)
            }
        }
        return id
    }

    /** "pays", "pays (Autotap doesn't find it)" or why not. */
    fun manaAbilityPays(card: CardDefinition, ability: Ability.Activated): String {
        val state = game()
        val counters = ability.cost.parts.filterIsInstance<CostPart.RemoveCounters>().map { (it.kind ?: CounterKind.PowerToughness(1, 1)) to 5 }
        val source = state.put(card, Zone.Battlefield, counters)
        repeat(3) { state.put(helper, Zone.Battlefield) }
        repeat(2) { state.put(landHelper, Zone.Battlefield) }
        val spell = state.put(filler, Zone.Hand)
        repeat(2) { state.put(filler, Zone.Hand) }
        repeat(6) { state.put(anyLand, Zone.Battlefield) }
        val cast = Engine.perform(state, Input.Cast(alice, spell))
        val running = state.deepCopy()
        cast.changes.forEach { running.apply(it) }
        val payment = cast.pending?.decision as? Decision.ManaPayment ?: return "FAILS: no payment asked of the test spell"
        val uses = payment.options.filterIsInstance<ManaUse.Ability>().filter { it.source == source }
        if (uses.isEmpty()) return "look: not offered on this board (its condition or a choice it asks)"
        val others = payment.options.filterIsInstance<ManaUse.Ability>().filter { it.source != source }
        for (color in arcanum.api.Color.all) {
            val lands = others.filter { it.color == color || it.color == null }.distinctBy { it.source }
            for (use in uses) for (helpers in 0..lands.size) {
                val by = listOf(use) + lands.take(helpers)
                val accepted = try { Engine.perform(running, Input.Answer(payment.id, Answer.ManaPayment(by))); true } catch (_: IllegalArgumentException) { false }
                if (accepted) return "pays"
            }
        }
        return "FAILS: no payment by hand with it is accepted"
    }

    /** "lowers" when some test board lowers a cost, else "look". */
    fun reducerLowers(card: CardDefinition): String {
        // A reducer on another permanent, then the card's own
        for (spell in listOf(creatureSpell, instantSpell, sorcerySpell, artifactSpell, enchantmentSpell)) {
            val state = game()
            state.put(card, Zone.Battlefield)
            repeat(10) { state.put(anyLand, Zone.Battlefield) }
            val id = state.put(spell, Zone.Hand)
            val outcome = runCatching { Engine.perform(state, Input.Cast(alice, id)) }.getOrNull() ?: continue
            val after = state.deepCopy()
            outcome.changes.forEach { after.apply(it) }
            val least = CastScreen.castCosts(after).least ?: continue
            if (arcanum.api.ManaCost.parse("{4}") != least) return "lowers"
        }
        val state = game()
        repeat(3) { state.put(helper, Zone.Battlefield) }
        repeat(12) { state.put(anyLand, Zone.Battlefield) }
        val id = state.put(card, Zone.Hand)
        val outcome = runCatching { Engine.perform(state, Input.Cast(alice, id)) }.getOrNull() ?: return "look: its cast isn't begun on this board"
        val after = state.deepCopy()
        outcome.changes.forEach { after.apply(it) }
        val least = CastScreen.castCosts(after).least
        val printed = card.faces.first().manaCost
        return if (least != null && least != printed) "lowers" else "look: not lowered on this board"
    }
}
