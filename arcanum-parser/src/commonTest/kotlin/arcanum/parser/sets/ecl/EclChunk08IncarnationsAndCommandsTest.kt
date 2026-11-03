package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 08: the Incarnations, with evoke (CR 702.74) and two enters triggers each checking which coloured mana was spent
 * to cast it (an intervening "if", CR 603.4, 601.2h: hybrid symbols paid with either colour), and the Commands: choose two of four modes
 * (CR 700.2), each with its own targets ("target player" in several modes), a copy token of a creature of the Command's type (CR 707.2),
 * creatures a target player controls, one or two target cards, and the cards of a type milled this way.
 */
class EclChunk08IncarnationsAndCommandsTest {
    private fun spent(color: Color): Condition = Condition.Compare(Value.ManaSpent(color = color, of = Objects.Self), Comparison.AtLeast, n(2))
    private fun chooseTwo(vararg options: Mode): Ability = Ability.Spell(Instructions(modes = Modes(options.toList(), min = 2, max = 2, chooser = Players.You)))
    private val targetPlayerCreatures = Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))))
    private fun copyOf(noun: String) = mode(create(TokenSpec.CopyOf(target(noun))), targets = listOf(slot(noun, and(sub(noun), you))))

    @Test
    fun cinnasDecree() = card(
        name = "Cinna's Decree",
        cost = "{3}{U}{R}",
        type = "Kindred Instant — Elemental",
        text = """
            Choose two —
            • Create a token that's a copy of target Elemental you control.
            • Target player draws two cards.
            • Cinna's Decree deals 2 damage to each creature target player controls.
            • Target player creates two Treasure tokens.
        """,
    ) parsesTo abilities(
        chooseTwo(
            copyOf("Elemental"),
            mode(draw(2, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(damage(2, toObjects(targetPlayerCreatures)), targets = listOf(targetPlayer)),
            mode(doing(Effect.CreateToken(TokenSpec.Predefined("Treasure"), n(2), Players.Target("player"))), targets = listOf(targetPlayer))),
    )

    @Test
    fun maewensDecree() = card(
        name = "Maewen's Decree",
        cost = "{1}{G}{W}",
        type = "Kindred Sorcery — Kithkin",
        text = """
            Choose two —
            • Create a token that's a copy of target Kithkin you control.
            • Target player creates a 1/1 green and white Kithkin creature token.
            • Target creature you control gets +3/+3 until end of turn.
            • Target creature you control fights target creature an opponent controls.
        """,
    ) parsesTo abilities(
        chooseTwo(
            copyOf("Kithkin"),
            mode(doing(Effect.CreateToken(kithkin, n(1), Players.Target("player"))), targets = listOf(targetPlayer)),
            mode(pump(target("creature"), 3, 3), targets = listOf(slot("creature", creatureYouControl))),
            // CR 701.14a
            mode(doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls)))),
    )

    // CR 702.74a: evoked, it is sacrificed as it enters; its triggers check the mana spent
    @Test
    fun unburdening() = card(
        name = "Unburdening",
        cost = "{4}{R/W}{R/W}",
        type = "Creature — Elemental Incarnation",
        text = """
            When this creature enters, if {W}{W} was spent to cast it, create two 1/1 green and white Kithkin creature tokens.
            When this creature enters, if {R}{R} was spent to cast it, creatures you control get +1/+1 and gain haste until end of turn.
            Evoke {R/W}{R/W} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf, create(kithkin, n(2)), interveningIf = spent(Color.White)),
        triggered(entersSelf, pump(Objects.Each(creatureYouControl), 1, 1, has(Keyword.Haste)), interveningIf = spent(Color.Red)),
        Keyword.Evoke(manaCost("{R/W}{R/W}")),
    )

    @Test
    fun chicanery() = card(
        name = "Chicanery",
        cost = "{4}{U/B}{U/B}",
        type = "Creature — Elemental Incarnation",
        text = """
            When this creature enters, if {U}{U} was spent to cast it, return up to one other target nonland permanent to its owner's hand.
            When this creature enters, if {B}{B} was spent to cast it, target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
            Evoke {U/B}{U/B}
        """,
        pt = "5/5",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, nonland, permanentFilter), min = 0)),
            interveningIf = spent(Color.Blue)),
        triggered(entersSelf,
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetOpponent), interveningIf = spent(Color.Black)),
        Keyword.Evoke(manaCost("{U/B}{U/B}")),
    )

    @Test
    fun hollowness() = card(
        name = "Hollowness",
        cost = "{4}{W/B}{W/B}",
        type = "Creature — Elemental Incarnation",
        text = """
            When this creature enters, if {W}{W} was spent to cast it, return target creature card with mana value 3 or less from your graveyard to the battlefield.
            When this creature enters, if {B}{B} was spent to cast it, put three -1/-1 counters on up to one target creature.
            Evoke {W/B}{W/B}
        """,
        pt = "3/5",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)))),
            interveningIf = spent(Color.White)),
        triggered(entersSelf, counters(target("creature"), 3, m1m1), targets = listOf(slot("creature", creature, min = 0)), interveningIf = spent(Color.Black)),
        Keyword.Evoke(manaCost("{W/B}{W/B}")),
    )

    @Test
    fun nettlesDecree() = card(
        name = "Nettle's Decree",
        cost = "{3}{B}{R}",
        type = "Kindred Sorcery — Goblin",
        text = """
            Choose two —
            • Create a token that's a copy of target Goblin you control.
            • Creatures target player controls get +1/+1 and gain haste until end of turn.
            • Destroy target artifact or creature.
            • Target player mills five cards, then puts each Goblin card milled this way into their hand.
        """,
    ) parsesTo abilities(
        chooseTwo(
            copyOf("Goblin"),
            mode(pump(targetPlayerCreatures, 1, 1, has(Keyword.Haste)), targets = listOf(targetPlayer)),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, creature)))),
            mode(mill(5, Players.Target("player"), postBind = "milled"), move(Objects.Where(Objects.Bound("milled"), sub("Goblin")), Zone.Hand),
                targets = listOf(targetPlayer))),
    )

    @Test
    fun tellissDecree() = card(
        name = "Tellis's Decree",
        cost = "{1}{W}{U}",
        type = "Kindred Sorcery — Merfolk",
        text = """
            Choose two —
            • Create a token that's a copy of target Merfolk you control.
            • Creatures target player controls gain lifelink until end of turn.
            • Target player draws a card.
            • Tap target creature. Put a stun counter on it.
        """,
    ) parsesTo abilities(
        chooseTwo(
            copyOf("Merfolk"),
            mode(gains(targetPlayerCreatures, Keyword.Lifelink), targets = listOf(targetPlayer)),
            mode(draw(1, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(*tapAndStun(target("creature")).toTypedArray(), targets = listOf(slot("creature", creature)))),
    )

    @Test
    fun orwensDecree() = card(
        name = "Orwen's Decree",
        cost = "{4}{B}{G}",
        type = "Kindred Sorcery — Elf",
        text = """
            Choose two —
            • Create a token that's a copy of target Elf you control.
            • Return one or two target permanent cards from your graveyard to your hand.
            • Destroy target creature or enchantment.
            • Creatures target player controls get +3/+3 until end of turn. Untap them.
        """,
    ) parsesTo abilities(
        chooseTwo(
            copyOf("Elf"),
            mode(move(target("permanent cards"), Zone.Hand), targets = listOf(slot("permanent cards", inYourGraveyard(permanentCard), min = 1, max = 2))),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, enchantment)))),
            mode(pump(targetPlayerCreatures, 3, 3), untapIt(targetPlayerCreatures), targets = listOf(targetPlayer))),
    )

    @Test
    fun vividness() = card(
        name = "Vividness",
        cost = "{3}{R/G}{R/G}",
        type = "Creature — Elemental Incarnation",
        text = """
            When this creature enters, if {R}{R} was spent to cast it, this creature deals 3 damage to any target.
            When this creature enters, if {G}{G} was spent to cast it, search your library for a land card, reveal it, put it into your hand, then shuffle. You gain 2 life.
            Evoke {R/G}{R/G}
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersSelf, damage(3, toAny), targets = listOf(anyTarget), interveningIf = spent(Color.Red)),
        triggered(entersSelf, search(land, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, gainLife(2), interveningIf = spent(Color.Green)),
        Keyword.Evoke(manaCost("{R/G}{R/G}")),
    )

    @Test
    fun yearning() = card(
        name = "Yearning",
        cost = "{3}{G/U}{G/U}",
        type = "Creature — Elemental Incarnation",
        text = """
            When this creature enters, if {G}{G} was spent to cast it, exile target artifact or enchantment an opponent controls.
            When this creature enters, if {U}{U} was spent to cast it, draw two cards, then discard a card.
            Evoke {G/U}{G/U} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(or(artifact, enchantment), opponents))),
            interveningIf = spent(Color.Green)),
        triggered(entersSelf, draw(2), discard(1), interveningIf = spent(Color.Blue)),
        Keyword.Evoke(manaCost("{G/U}{G/U}")),
    )
}
