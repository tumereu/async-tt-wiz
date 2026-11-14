package arcanum.parser.sets.bro

import arcanum.api.*
import arcanum.parser.OracleParser
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BRO sweep: wordings beside the ones the set's parser fixes changed: copies keeping targets, "that player" after a chosen target opponent,
 * a qualifier on the last of two alternatives. */
class BroSweepTest {
    private fun theirs(player: Players) = ObjectFilter.ControlledBy(PlayerFilter.Is(player))

    /** Every "may choose new targets" switch of the copies in [text]'s model. */
    private fun newTargets(name: String, type: String, text: String): List<Boolean> {
        val face = OracleParser.parse(card(name = name, cost = "{2}{U}", type = type, text = text))
        assertTrue(face.abilities.none { it is Ability.Unparsed }, "$name: $text")
        return Regex("""newTargets=(true|false)""").findAll(face.abilities.toString()).map { it.groupValues[1].toBoolean() }.toList()
    }

    // CR 707.10: a copy of an ability keeps its targets; CR 707.10c: new ones only when the effect says so (it doesn't here)
    @Test
    fun copyTargetAbilityKeepsItsTargets() =
        assertEquals(listOf(false), newTargets("Echo of Machinations", "Instant", "Copy target activated or triggered ability you control."))

    // CR 707.10: "copy that spell" after a payment in a cast trigger keeps the spell's targets (no "You may choose new targets", CR 707.10c)
    @Test
    fun copyThatSpellAfterAPaymentKeepsItsTargets() = assertEquals(listOf(false), newTargets("Twinflare Adept", "Creature — Human Wizard",
        "Whenever you cast an instant or sorcery spell, you may pay {2}. If you do, copy that spell."))

    // CR 115.1, 601.2c: "that player" is the target opponent chosen first; the creatures are theirs (up to two, CR 115.1b)
    @Test
    fun upToTwoTargetCreaturesThatPlayerControls() = card(
        name = "Pillager of Kroog", cost = "{1}{U}", type = "Sorcery",
        text = "Choose target opponent. Tap up to two target creatures that player controls.",
    ) parsesTo abilities(spell(
        tapIt(target("creatures")),
        targets = listOf(targetOpponent, slot("creatures", and(creature, theirs(Players.Target("opponent"))), min = 0, max = 2)),
    ))

    // CR 115.1, 603.2: in a trigger about damage to a player, "that player" after "choose target opponent" is the opponent chosen, not the
    // player dealt damage (three or more players: they can differ)
    @Test
    fun thatPlayerAfterChosenTargetOpponentInADamageTrigger() {
        val face = OracleParser.parse(card(name = "Kilnwise Seeker", cost = "{2}{R}", type = "Creature — Human Artificer", pt = "2/2",
            text = "Whenever a creature you control deals combat damage to a player, choose target opponent. Destroy target artifact that player controls."))
        val trigger = face.abilities.single() as Ability.Triggered
        assertEquals(listOf(targetOpponent, slot("artifact", and(artifact, theirs(Players.Target("opponent"))))), trigger.instructions.targets.map { it.copy(span = null) })
    }

    // CR 701.23a: the qualifier "with flying" belongs to the last alternative only: an artifact card, or a creature card with flying
    @Test
    fun aQualifierOnTheLastOfTwoAlternatives() = card(
        name = "Bladewing Vanguard", cost = "{2}{W}", type = "Sorcery",
        text = "Search your library for an artifact card or a creature card with flying, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(spell(
        search(or(artifact, and(creature, kw(Keyword.Flying))), reveal = true),
        move(Objects.Bound("found"), Zone.Hand),
        shuffle,
    ))
}
