package arcanum.parser.sets.one

import arcanum.api.*
import arcanum.parser.cube.*
import kotlin.test.Test

/**
 * Chunk 15: damage by count and by oil counters (CR 120.3, 122.1), modes with "can't block" Mites (CR 700.2, 702.164), Skullbomb
 * sacrifice artifacts (CR 602.5d), cost reductions on an activated ability (CR 601.2f), looting and look-at-top-cards, life gain
 * behind a "Then if", and a once-each-turn trigger for several dying creatures (CR 603.2c, 603.4).
 */
class OneChunk15DamageDrawLifeLookTest {
    private val oil = CounterKind.Named("oil")
    private val toxic = Keyword.Toxic(1)
    private val cantBlock = Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
    private val mite = creatureToken("Phyrexian Mite", emptyList(), 1, 1, abilities = listOf(toxic, cantBlock),
        types = listOf(CardType.Artifact, CardType.Creature))
    private val hasOil = ObjectFilter.HasCounter(oil, atLeast = 1)
    private val creatureOrPlaneswalkerSlot = { name: String -> slot(name, or(creature, planeswalker)) }
    private val drawSkull = activated(cost(mana("{1}"), sacrificeSelf), draw(1))

    // CR 700.2: one of two modes; CR 120.3: damage equal to the number of creatures; CR 111.1, 702.164a: Mite tokens
    @Test
    fun chargeOfTheMites() = card(
        name = "Charge of the Mice",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Charge of the Mice deals damage equal to the number of creatures you control to target creature or planeswalker.
            • Create two 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and "This token can't block." (Players dealt combat damage by them also get a poison counter.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(count(creatureYouControl), toObjects(target("permanent"))), targets = listOf(creatureOrPlaneswalkerSlot("permanent"))),
            mode(create(mite, n(2), bind = "token")),
        ))),
    )

    // CR 120.3, 509.1a: damage to each creature of the opponents, who then can't block this turn (CR 611.2a)
    @Test
    fun hazardousBlast() = card(
        name = "Hazardous Burst",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Hazardous Burst deals 1 damage to each creature your opponents control. Creatures your opponents control can't block this turn.",
    ) parsesTo abilities(
        spell(damage(1, toObjects(Objects.Each(creatureOpponentControls))),
            untilEndOfTurn(Objects.Each(creatureOpponentControls), Mechanic.CantBlock(attackers = ObjectFilter.Any))),
    )

    // CR 700.2d: "one or both"; CR 701.8: destroy
    @Test
    fun moltenRebuke() = card(
        name = "Molten Reproof",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Molten Reproof deals 5 damage to target creature or planeswalker.
            • Destroy target Equipment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(5, toObjects(target("permanent"))), targets = listOf(creatureOrPlaneswalkerSlot("permanent"))),
            // both modes may be chosen, so the second's target has a name of its own (CR 601.2c)
            mode(destroy(target("Equipment")), targets = listOf(slot("Equipment", equipment))),
            max = 2,
        ))),
    )

    // CR 122.1, 120.3: X is the number of permanents you control with oil counters
    @Test
    fun urabraskAnointer() = card(
        name = "Urabrask's Anointress",
        cost = "{3}{R}",
        type = "Artifact Creature — Phyrexian Wizard",
        pt = "4/2",
        text = "When this creature enters, it deals X damage to any target, where X is the number of permanents you control with oil counters on them.",
    ) parsesTo abilities(
        triggered(entersSelf, damage(count(and(permanentFilter, you, hasOil)), toAny), targets = listOf(anyTarget)),
    )

    // CR 602.5d, 602.1: Skullbomb cycle; the second ability is sorcery speed (CR 602.5d) and draws after the pump
    @Test
    fun basilicaSkullbomb() = card(
        name = "Basilica Skullburst",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, Sacrifice this artifact: Draw a card.
            {2}{W}, Sacrifice this artifact: Target creature you control gets +2/+2 and gains flying until end of turn. Draw a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        drawSkull,
        activated(cost(mana("{2}{W}"), sacrificeSelf), untilEndOfTurn(target("creature"), pt(2, 2), has(Keyword.Flying)), draw(1),
            targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    @Test
    fun drossSkullbomb() = card(
        name = "Dross Skullburst",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, Sacrifice this artifact: Draw a card.
            {2}{B}, Sacrifice this artifact: Return target creature card from your graveyard to your hand. Draw a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        drawSkull,
        activated(cost(mana("{2}{B}"), sacrificeSelf), move(target("card"), Zone.Hand), draw(1),
            targets = listOf(slot("card", inYourGraveyard(creature))), rules = asSorcery),
    )

    // CR 122.1: oil counters put on an artifact or creature
    @Test
    fun furnaceSkullbomb() = card(
        name = "Furnace Skullburst",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, Sacrifice this artifact: Draw a card.
            {1}{R}, Sacrifice this artifact: Put two oil counters on target artifact or creature you control. Draw a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        drawSkull,
        activated(cost(mana("{1}{R}"), sacrificeSelf), counters(target("permanent"), 2, oil), draw(1),
            targets = listOf(slot("permanent", and(or(artifact, creature), you))), rules = asSorcery),
    )

    // CR 700.2: three modes; CR 122.8: remove up to three counters
    @Test
    fun glissaSunslayer() = card(
        name = "Glissa Sunbringer",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Phyrexian Zombie Elf",
        pt = "3/3",
        text = """
            First strike, deathtouch
            Whenever Glissa Sunbringer deals combat damage to a player, choose one —
            • You draw a card and lose 1 life.
            • Destroy target enchantment.
            • Remove up to three counters from target permanent.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Deathtouch,
        Ability.Triggered(combatDamageToAPlayer, Instructions(modes = modes(
            mode(draw(1), loseLife(1)),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(Step.Choose(Choice.Number(n(0), n(3)), bind = "removed", chooser = Players.You),
                Step.Do(Effect.RemoveCounters(target("permanent"), null, Value.Bound("removed"))),
                targets = listOf(slot("permanent", permanentFilter))),
        ))),
    )

    @Test
    fun mazeSkullbomb() = card(
        name = "Maze Skullburst",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, Sacrifice this artifact: Draw a card.
            {2}{G}, Sacrifice this artifact: Target creature you control gets +3/+3 and gains trample until end of turn. Draw a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        drawSkull,
        activated(cost(mana("{2}{G}"), sacrificeSelf), untilEndOfTurn(target("creature"), pt(3, 3), has(Keyword.Trample)), draw(1),
            targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    @Test
    fun surgicalSkullbomb() = card(
        name = "Surgical Skullburst",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, Sacrifice this artifact: Draw a card.
            {2}{U}, Sacrifice this artifact: Return target creature to its owner's hand. Draw a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        drawSkull,
        activated(cost(mana("{2}{U}"), sacrificeSelf), move(target("creature"), Zone.Hand), draw(1),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 601.2f: the activated ability costs {1} less for each other artifact
    @Test
    fun tamiyosLogbook() = card(
        name = "Tamiyo's Ledger",
        cost = "{2}{U}",
        type = "Artifact — Book",
        text = "{5}{U}, {T}: Draw a card. This ability costs {1} less to activate for each other artifact you control.",
    ) parsesTo abilities(
        activated(cost(mana("{5}{U}"), tap), draw(1),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(and(another, artifact, you))))),
    )

    // CR 603.2: one trigger for this creature or another artifact; CR 121.1, 701.9
    @Test
    fun transplantTheorist() = card(
        name = "Transplant Theoretician",
        cost = "{3}{U}",
        type = "Artifact Creature — Phyrexian Artificer",
        pt = "2/4",
        text = """
            Whenever this creature or another artifact you control enters, you may draw a card. If you do, discard a card.
            {2}: Put target card from your graveyard on the bottom of your library.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, artifact, you)), to = Zone.Battlefield)),
            Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), listOf(discard(1)))),
        activated(cost(mana("{2}")), move(target("card"), Zone.Library, position = -1),
            targets = listOf(slot("card", inYourGraveyard(cardFilter)))),
    )

    // CR 608.2c: "Then if" is checked after the life is gained; CR 122.1: oil counter
    @Test
    fun oilGorgerTroll() = card(
        name = "Oil-Glutton Troll",
        cost = "{3}{G}{G}",
        type = "Creature — Phyrexian Troll Warrior",
        pt = "3/4",
        text = "When this creature enters, you gain 3 life. Then if you control a permanent with an oil counter on it, draw a card.",
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(3),
            Step.If(Condition.Exists(and(permanentFilter, hasOil, you), atLeast = 1), listOf(draw(1)))),
    )

    // CR 701.20 (look at), 400.7: the rest go to the graveyard
    @Test
    fun testamentBearer() = card(
        name = "Testimony Bearer",
        cost = "{3}{B}",
        type = "Creature — Phyrexian Warrior",
        pt = "4/1",
        text = "When this creature dies, look at the top three cards of your library. Put one of them into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        triggered(diesSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "picked", chooser = Players.You),
            move(Objects.Bound("picked"), Zone.Hand), move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), Zone.Graveyard)),
    )

    // CR 603.2c: one trigger for one or more creatures; CR 603.4: once each turn; CR 119.3, 120.3
    @Test
    fun vraanExecutionerThane() = card(
        name = "Vraan, Executioner Than",
        cost = "{1}{B}",
        type = "Legendary Creature — Phyrexian Vampire",
        pt = "2/2",
        text = "Whenever one or more other creatures you control die, each opponent loses 2 life and you gain 2 life. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2), perTurn = 1),
    )
}
