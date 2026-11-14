package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 08: kicker, part 2. "If this spell was kicked" steps and target slots only a kicked cast needs
 * (CR 702.33d, 601.2c), "when this enters, if it was kicked" (CR 603.4) and "if this creature was kicked, it enters with ..."
 * (CR 614.1c).
 */
class DmuChunk08KickerPartTwoTest {
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private fun kicker(cost: String) = Keyword.Kicker(listOf(manaCost(cost)))
    private val inYourHand = listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val creatureOrPlaneswalker = or(creature, planeswalker)
    private val landWithBasicType = and(land, or(*listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map { sub(it) }.toTypedArray()))
    private val tokenSoldiers = create(soldier, n(2))
    private val entersWithTwoCounters = Ability.Static(
        listOf(Mechanic.Replacement(
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(2))),
        )),
        affects = Affected.Objects(Objects.Self),
        condition = kicked,
    )

    // CR 702.33d, 601.2c: the draw is only done when kicked; the damage counts the hand after it
    @Test
    fun flamesOfTriumph() = card(
        name = "Flames of Triumph",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Kicker {2}{U} (You may pay an additional {2}{U} as you cast this spell.)
            If this spell was kicked, draw a card. Flames of Triumph deals damage to target creature or planeswalker equal to the number of cards in your hand.
        """,
    ) parsesTo abilities(
        kicker("{2}{U}"),
        spell(
            Step.If(kicked, then = listOf(draw(1))),
            damage(count(ObjectFilter.And(inYourHand)), toObjects(target("permanent"))),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ),
    )

    // CR 702.33d, 603.4
    @Test
    fun emberiteAmplifier() = card(
        name = "Emberite Amplifier",
        cost = "{1}{R}",
        type = "Creature — Human Wizard",
        pt = "1/2",
        text = """
            Kicker {2}{U} (You may pay an additional {2}{U} as you cast this spell.)
            When this creature enters, if it was kicked, return target creature an opponent controls to its owner's hand.
            Whenever you cast an instant or sorcery spell, this creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{2}{U}"),
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creatureOpponentControls)), interveningIf = kicked),
        triggered(youCast(or(instant, sorcery)), pump(Objects.Self, 2, 0)),
    )

    // CR 702.33d
    @Test
    fun minotaurWarChant() = card(
        name = "Minotaur War Chant",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Kicker {W} (You may pay an additional {W} as you cast this spell.)
            Minotaur War Chant deals 4 damage to target creature or planeswalker. If this spell was kicked, you gain 4 life.
        """,
    ) parsesTo abilities(
        kicker("{W}"),
        spell(
            damage(4, toObjects(target("permanent"))),
            Step.If(kicked, then = listOf(gainLife(4))),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ),
    )

    // CR 702.33d, 701.22a
    @Test
    fun sharedForay() = card(
        name = "Shared Foray",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Kicker {G} (You may pay an additional {G} as you cast this spell.)
            Scry 2, then draw a card. If this spell was kicked, you may put a land card from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        kicker("{G}"),
        spell(
            scry(2),
            draw(1),
            Step.If(kicked, then = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(land) + inYourHand), n(0), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield),
            )),
        ),
    )

    // CR 702.33d, 603.4
    @Test
    fun cedarGuildWeaver() = card(
        name = "Cedar Guild Weaver",
        cost = "{1}{W}",
        type = "Creature — Human Druid",
        pt = "2/2",
        text = """
            Kicker {G} (You may pay an additional {G} as you cast this spell.)
            When this creature enters, if it was kicked, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        kicker("{G}"),
        triggered(entersSelf, counters(target("creature"), 1), targets = listOf(slot("creature", creatureYouControl)), interveningIf = kicked),
    )

    // CR 702.33d, 603.4, 611.3a: the haste lasts only while it entered this turn
    @Test
    fun cinderRaidSquad() = card(
        name = "Cinder Raid Squad",
        cost = "{2}{R}",
        type = "Creature — Human Warrior",
        pt = "3/1",
        text = """
            Kicker {1}{W} (You may pay an additional {1}{W} as you cast this spell.)
            When this creature enters, if it was kicked, create two 1/1 white Soldier creature tokens.
            As long as this creature entered this turn, creatures you control have haste.
        """,
    ) parsesTo abilities(
        kicker("{1}{W}"),
        triggered(entersSelf, tokenSoldiers, interveningIf = kicked),
        static(has(Keyword.Haste), affects = Objects.Each(creatureYouControl),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield)))),
    )

    // CR 702.33d
    @Test
    fun praetorSpying() = card(
        name = "Praetor Spying",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)
            Draw two cards. If this spell was kicked, each opponent discards a card.
        """,
    ) parsesTo abilities(
        kicker("{1}{B}"),
        spell(draw(2), Step.If(kicked, then = listOf(discard(1, Players.Each(PlayerFilter.Opponent))))),
    )

    // CR 702.33d, 603.4
    @Test
    fun praetorEnvoy() = card(
        name = "Praetor Envoy",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Human Cleric",
        pt = "2/3",
        text = """
            Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)
            Lifelink
            When this creature enters, if it was kicked, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        kicker("{1}{B}"),
        Keyword.Lifelink,
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature))), interveningIf = kicked),
    )

    // CR 702.33d, 603.4, 118.1
    @Test
    fun praetorCharger() = card(
        name = "Praetor Charger",
        cost = "{3}{B}",
        type = "Creature — Phyrexian Horse",
        pt = "3/3",
        text = """
            Kicker {W} (You may pay an additional {W} as you cast this spell.)
            When this creature enters, if it was kicked, create a 1/1 white Soldier creature token.
            {1}, Sacrifice another creature: This creature gets +2/+1 until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{W}"),
        triggered(entersSelf, create(soldier), interveningIf = kicked),
        activated(cost(mana("{1}"), sacrificeA(and(another, creature))), pump(Objects.Self, 2, 1)),
    )

    // CR 702.33d, 601.2c
    @Test
    fun glyphBolt() = card(
        name = "Glyph Bolt",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Kicker {U} (You may pay an additional {U} as you cast this spell.)
            Destroy target tapped creature. If this spell was kicked, scry 2.
        """,
    ) parsesTo abilities(
        kicker("{U}"),
        spell(destroy(target("creature")), Step.If(kicked, then = listOf(scry(2))), targets = listOf(slot("creature", and(tapped, creature)))),
    )

    // CR 702.33d, 701.23, 701.21
    @Test
    fun surveyTheWilds() = card(
        name = "Survey the Wilds",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Kicker {1}{W} (You may pay an additional {1}{W} as you cast this spell.)
            Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. If this spell was kicked, create two 1/1 white Soldier creature tokens.
        """,
    ) parsesTo abilities(
        kicker("{1}{W}"),
        spell(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle, Step.If(kicked, then = listOf(tokenSoldiers))),
    )

    // CR 702.33d, 614.1c
    @Test
    fun halosNovice() = card(
        name = "Halo's Novice",
        cost = "{4}{W}",
        type = "Creature — Angel",
        pt = "3/4",
        text = """
            Kicker {1}{G} (You may pay an additional {1}{G} as you cast this spell.)
            Flying
            If this creature was kicked, it enters with two +1/+1 counters on it.
        """,
    ) parsesTo abilities(kicker("{1}{G}"), Keyword.Flying, entersWithTwoCounters)

    // CR 702.33d, 603.4, 701.23 (a land with a basic land type is not necessarily a basic land)
    @Test
    fun buddingImp() = card(
        name = "Budding Imp",
        cost = "{1}{R}",
        type = "Creature — Goblin Druid",
        pt = "2/2",
        text = """
            Kicker {G} (You may pay an additional {G} as you cast this spell.)
            When this creature enters, if it was kicked, search your library for a land card with a basic land type, reveal it, put it into your hand, then shuffle.
            {R}, {T}, Sacrifice a land: Draw a card.
        """,
    ) parsesTo abilities(
        kicker("{G}"),
        triggered(entersSelf, search(landWithBasicType, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, interveningIf = kicked),
        activated(cost(mana("{R}"), tap, sacrificeA(land)), draw(1)),
    )

    // CR 702.33d, 601.2c, 702.171
    @Test
    fun delayTheHour() = card(
        name = "Delay the Hour",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            Tap up to two target creatures. If this spell was kicked, put a stun counter on each of those creatures. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Draw a card.
        """,
    ) parsesTo abilities(
        kicker("{1}{U}"),
        spell(
            tapIt(target("creatures")),
            Step.If(kicked, then = listOf(counters(target("creatures"), 1, CounterKind.Stun))),
            draw(1),
            targets = listOf(slot("creatures", creature, min = 0, max = 2)),
        ),
    )

    // CR 702.33d, 122.1
    @Test
    fun mightOfTheAlliance() = card(
        name = "Might of the Alliance",
        cost = "{G}",
        type = "Instant",
        text = """
            Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
            Target creature you control gets +2/+2 until end of turn. If this spell was kicked, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        kicker("{2}{W}"),
        spell(
            pump(target("creature"), 2, 2),
            Step.If(kicked, then = listOf(counters(Objects.Each(creatureYouControl), 1))),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    // CR 702.33d, 601.2c: audit: one target, an artifact or enchantment, or a nonland permanent if kicked
    @Test
    fun rendApart() = card(
        name = "Rend Apart",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)
            Exile target artifact or enchantment. If this spell was kicked, exile target nonland permanent instead.
        """,
    ) parsesTo abilities(
        kicker("{1}{B}"),
        spell(
            Step.If(kicked, then = listOf(move(target("second permanent"), Zone.Exile)), otherwise = listOf(move(target("permanent"), Zone.Exile))),
            targets = listOf(
                slot("permanent", or(artifact, enchantment)).copy(onlyIf = Condition.Not(kicked)),
                slot("second permanent", and(nonland, permanentFilter)).copy(onlyIf = kicked),
            ),
        ),
    )

    // CR 702.33d
    @Test
    fun arcaneFountain() = card(
        name = "Arcane Fountain",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Kicker {W} (You may pay an additional {W} as you cast this spell.)
            Return target creature to its owner's hand. Draw a card. If this spell was kicked, you gain 3 life.
        """,
    ) parsesTo abilities(
        kicker("{W}"),
        spell(move(target("creature"), Zone.Hand), draw(1), Step.If(kicked, then = listOf(gainLife(3))), targets = listOf(slot("creature", creature))),
    )

    // CR 702.33d, 601.2c: the second target is another permanent card, chosen only when kicked
    @Test
    fun swampReclamation() = card(
        name = "Swamp Reclamation",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Kicker {1}{G} (You may pay an additional {1}{G} as you cast this spell.)
            Return target creature card from your graveyard to your hand. You gain 2 life. If this spell was kicked, return another target permanent card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        kicker("{1}{G}"),
        spell(
            move(target("card"), Zone.Hand),
            gainLife(2),
            Step.If(kicked, then = listOf(move(target("second card"), Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(creature)), slot("second card", inYourGraveyard(permanentCard)).copy(onlyIf = kicked, distinctFrom = listOf("card"))),
        ),
    )

    // CR 702.33d, 614.1c
    @Test
    fun skinkHillrider() = card(
        name = "Skink Hillrider",
        cost = "{R}",
        type = "Creature — Lizard Warrior",
        pt = "1/1",
        text = """
            Kicker {2}{G} (You may pay an additional {2}{G} as you cast this spell.)
            Haste
            If this creature was kicked, it enters with two +1/+1 counters on it.
            {2}{R}: This creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{2}{G}"),
        Keyword.Haste,
        entersWithTwoCounters,
        activated(cost(mana("{2}{R}")), pump(Objects.Self, 2, 0)),
    )

    // CR 702.33d, 603.4, 701.20 (look at the top three, one to hand, the rest on the bottom in any order)
    @Test
    fun grovecraftProdigy() = card(
        name = "Grovecraft Prodigy",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        pt = "2/2",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            When this creature enters, if it was kicked, look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.
        """,
    ) parsesTo abilities(
        kicker("{1}{U}"),
        triggered(
            entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Library, position = -1),
            interveningIf = kicked,
        ),
    )

    // CR 702.33d, 603.7: a delayed trigger made only when kicked, for the rest of the turn
    @Test
    fun hordesRage() = card(
        name = "Horde's Rage",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Kicker {B} (You may pay an additional {B} as you cast this spell.)
            Creatures you control get +2/+0 until end of turn. If this spell was kicked, whenever a creature you control dies this turn, draw a card.
        """,
    ) parsesTo abilities(
        kicker("{B}"),
        spell(
            pump(Objects.Each(creatureYouControl), 2, 0),
            Step.If(kicked, then = listOf(doing(Effect.CreateDelayedTrigger(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(draw(1))), duration = arcanum.api.Duration.EndOfTurn, repeats = true)))),
        ),
    )

    // CR 702.33d, 603.4, 611.2a
    @Test
    fun jungleHeretic() = card(
        name = "Jungle Heretic",
        cost = "{1}{G}",
        type = "Creature — Elf",
        pt = "3/2",
        text = """
            Kicker {R} (You may pay an additional {R} as you cast this spell.)
            Trample
            When this creature enters, if it was kicked, it gets +1/+1 and gains haste until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{R}"),
        Keyword.Trample,
        triggered(entersSelf, pump(Objects.EventObject(arcanum.api.EventRole.After), 1, 1, has(Keyword.Haste)), interveningIf = kicked),
    )
}
