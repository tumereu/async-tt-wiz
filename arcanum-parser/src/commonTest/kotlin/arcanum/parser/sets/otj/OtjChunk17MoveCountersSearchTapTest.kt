package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 17: move, put-counters, reflexive-trigger, search and tap cards.
 */
class OtjChunk17MoveCountersSearchTapTest {
    private val desert = sub("Desert")
    private val mount = sub("Mount")

    // CR 701.21a (sacrifice as a cost: only your own permanents), 602.5d (sorcery timing), 602.5b (once each turn)
    @Test
    fun akurTheUnyielding() = card(
        name = "Akur the Unyielding", cost = "{B}{B}{R}{R}", type = "Legendary Creature — Scorpion Dragon Rogue", pt = "5/5",
        text = """
            Flying, trample
            Sacrifice three other creatures: You may put a creature card from your hand onto the battlefield. Activate only as a sorcery and only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(3)))),
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            rules = asSorcery.copy(perTurn = 1)),
    )

    // CR 603.4 (intervening if: cast it), 110.5b (tapped), 701.26a (becomes tapped), 400.7
    @Test
    fun annieFlare() = card(
        name = "Annie Flare, the Veteran", cost = "{3}{R}{G}{W}", type = "Legendary Creature — Human Rogue", pt = "4/5",
        text = """
            Flash
            When Annie Flare enters, if you cast it, return target permanent card with mana value 3 or less from your graveyard to the battlefield tapped.
            Whenever Annie Flare becomes tapped, exile the top two cards of your library. You may play those cards this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, move(target("card"), Zone.Battlefield, tapped = true),
            targets = listOf(slot("card", inYourGraveyard(permanentCard, manaValue(Comparison.AtMost, 3)))),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
        triggered(on(EventPattern.Tap(objects = ObjectFilter.Is(Objects.Self))),
            move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You))),
    )

    // CR 115.1 (up to one target each), 400.3
    @Test
    fun barrensRevival() = card(
        name = "Barrens Revival", cost = "{3}{B}{G}", type = "Sorcery",
        text = "Return up to one target creature card from your graveyard to the battlefield. Return up to one target permanent card from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), move(target("cards"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(creature), min = 0), slot("cards", inYourGraveyard(permanentCard), min = 0))),
    )

    // CR 601.2f (cost reduction)
    @Test
    fun honestRutherford() = card(
        name = "Honest Rutherford", cost = "{1}{B}{G}", type = "Legendary Creature — Human Warlock", pt = "3/2",
        text = """
            When Honest Rutherford enters, return target creature card from your graveyard to your hand.
            Creature spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        static(Mechanic.CostChange(and(creature, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 400.3 (to its owner's hand), 115.1 (up to one target)
    @Test
    fun peerlessRopewright() = card(
        name = "Peerless Ropewright", cost = "{4}{U}", type = "Creature — Human Rogue", pt = "4/4",
        text = "When this creature enters, return up to one target tapped creature to its owner's hand.",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(tapped, creature), min = 0))),
    )

    // CR 122.1 (counters), 701.26 n/a: "entered this turn" is a this-turn history filter
    @Test
    fun raucousPerformer() = card(
        name = "Raucous Performer", cost = "{1}{G}", type = "Creature — Plant Bard", pt = "2/2",
        text = "{1}, {T}: Put a +1/+1 counter on each creature you control that entered this turn.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), counters(Objects.Each(and(creature, you, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))))))
    )

    // CR 603.2 (second spell each turn), 509.1b (can't be blocked) applies to this creature
    @Test
    fun rascalDazzler() = card(
        name = "Rascal Dazzler", cost = "{1}{U}", type = "Creature — Human Wizard", pt = "1/2",
        text = "Whenever you cast your second spell each turn, put a +1/+1 counter on this creature. It can't be blocked this turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            counters(Objects.Self), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)),
    )

    // CR 603.10a, 608.2h: after "put a +1/+1 counter on <this>", "that creature" is still the creature that died, as it last existed
    @Test
    fun gravemournBishop() = card(
        name = "Gravemourn Bishop", cost = "{1}{W}{B}", type = "Legendary Creature — Human Cleric", pt = "2/2",
        text = "Whenever another nontoken creature you control dies, put a +1/+1 counter on Gravemourn Bishop. If that creature was a Cleric, you may draw a card. If you do, you lose 1 life.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, ObjectFilter.Not(token), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            counters(Objects.Self),
            Step.If(Condition.ObjectMatches(Objects.EventObject(EventRole.Before), sub("Cleric")), then = listOf(
                Step.Pay(steps = listOf(Step.Do(Effect.Draw(Players.You, n(1)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.LoseLife(Players.You, n(1)))))))),
    )

    // CR 603.2c (one or more), 700.4 (dies)
    @Test
    fun vengefulTownsman() = card(
        name = "Vengeful Townsman", cost = "{2}{W}", type = "Creature — Human Citizen", pt = "3/3",
        text = "Whenever one or more other creatures you control die, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)), oneOrMore = true),
            counters(Objects.Self)),
    )

    // CR 122.1b (keyword counters), 508.1 combat damage trigger
    @Test
    fun vraskaJoinsForces() = card(
        name = "Vraska Joins Forces", cost = "{B}{G}", type = "Legendary Enchantment",
        text = """
            When Vraska Joins Forces enters, put a deathtouch counter on each creature you control.
            Whenever a legendary creature you control deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(Objects.Each(creatureYouControl), kind = CounterKind.Keyword(Keyword.Deathtouch.kind))),
        triggered(on(EventPattern.Damage(and(legendary, creature, you), RecipientFilter(players = PlayerFilter.Any), combat = true)), draw(1)),
    )

    // CR 603.12 (reflexive trigger), 701.21a
    @Test
    fun ruthlessLawgiver() = card(
        name = "Ruthless Lawgiver", cost = "{1}{W}{B}", type = "Creature — Vampire Assassin", pt = "3/2",
        text = "When this creature enters, you may sacrifice another creature. When you do, destroy target nonland permanent.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter))))))),
    )

    // CR 701.23a, 305.8 (basic land or Desert, "and/or")
    @Test
    fun chartTheFrontier() = card(
        name = "Chart the Frontier", cost = "{3}{G}", type = "Sorcery",
        text = "Search your library for up to two basic land cards and/or Desert cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(search(or(basicLand(), desert), max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 701.23a, 701.20a (reveal), 613.4c ("for each Desert you control")
    @Test
    fun outcasterGreenhorn() = card(
        name = "Outcaster Greenhorn", cost = "{2}{G}", type = "Creature — Human Mercenary", pt = "1/2",
        text = """
            When this creature enters, search your library for a basic land card or a Desert card, reveal it, put it into your hand, then shuffle.
            This creature gets +1/+1 for each Desert you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(or(basicLand(), desert), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        static(Mechanic.ModifyPowerToughness(count(and(desert, you)), count(and(desert, you)))),
    )

    // CR 701.26a (tap), 702.9a
    @Test
    fun harrierStrike() = card(
        name = "Harrier Strike", cost = "{U}", type = "Creature — Bird", pt = "1/1",
        text = """
            Flying
            When this creature enters, tap target permanent.
            {2}{U}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, tapIt(target("permanent")), targets = listOf(slot("permanent", permanentFilter))),
        activated(manaCost("{2}{U}"), draw(1), discard(1)),
    )

    // CR 701.26a, 205.3 (non-Mount creature)
    @Test
    fun sterlingKeyholder() = card(
        name = "Sterling Keyholder", cost = "{1}{W}", type = "Creature — Human Mercenary", pt = "2/2",
        text = "{2}, {T}: Tap target non-Mount creature.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", and(not(mount), creature)))),
    )
}
