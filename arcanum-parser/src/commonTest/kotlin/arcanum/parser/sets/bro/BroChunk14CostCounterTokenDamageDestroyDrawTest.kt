package arcanum.parser.sets.bro

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 14: cost changes, counterspells, Soldier tokens, damage, destruction and drawing. */
class BroChunk14CostCounterTokenDamageDestroyDrawTest {
    private val colorlessSoldier = creatureToken("Soldier", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
    private val opponentCreature = and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))

    // CR 601.2f
    @Test
    fun machineOverMatter() = card(
        name = "Gear Overmatch", cost = "{1}{U}", type = "Instant",
        text = """
            This spell costs {1} less to cast if you control an artifact creature.
            Return target nonland permanent to its owner's hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(artifact, creature, you), 1)),
        spell(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    // CR 601.2f
    @Test
    fun overwhelmingRemorse() = card(
        name = "Crushing Contrition", cost = "{4}{B}", type = "Instant",
        text = """
            This spell costs {1} less to cast for each creature card in your graveyard.
            Exile target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(creature))))),
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 118.12a: the controller of the countered spell pays or it is countered
    @Test
    fun scatterRay() = card(
        name = "Scatter Beam", cost = "{1}{U}", type = "Instant",
        text = "Counter target artifact or creature spell unless its controller pays {4}.",
    ) parsesTo abilities(spell(
        Step.Pay(Cost.mana(ManaCost.parse("{4}")!!), payer = Players.ControllerOf(target("spell")), bind = "paid"),
        Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
        targets = listOf(slot("spell", and(or(artifact, creature), spellFilter))),
    ))

    // CR 700.2
    @Test
    fun urzasRebuff() = card(
        name = "Tinker's Rebuff", cost = "{1}{U}{U}", type = "Instant",
        text = """
            Choose one —
            • Counter target spell.
            • Tap up to two target creatures.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = modes(
        mode(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
        mode(tapIt(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    ))))

    // CR 107.3, 111.3: X tokens
    @Test
    fun awakenTheWoods() = card(
        name = "Wake the Grove", cost = "{X}{G}{G}", type = "Sorcery",
        text = "Create X 1/1 green Forest Dryad land creature tokens. (They're affected by summoning sickness.)",
    ) parsesTo abilities(spell(create(
        creatureToken("Forest Dryad", listOf(Color.Green), 1, 1, types = listOf(CardType.Land, CardType.Creature)), count = Value.Announced(name = "X")), announced = listOf("X")))

    @Test
    fun conscriptedInfantry() = card(
        name = "Drafted Infantry", cost = "{2}{R}", type = "Creature — Human Soldier", pt = "3/1",
        text = "When this creature dies, create a 1/1 colorless Soldier artifact creature token.",
    ) parsesTo abilities(triggered(diesSelf, create(colorlessSoldier)))

    @Test
    fun massProduction() = card(
        name = "Assembly Line Surge", cost = "{5}{W}", type = "Sorcery",
        text = "Create four 1/1 colorless Soldier artifact creature tokens.",
    ) parsesTo abilities(spell(create(colorlessSoldier, count = n(4))))

    // CR 700.2
    @Test
    fun mishrasOnslaught() = card(
        name = "Artificer's Onslaught", cost = "{3}{R}", type = "Instant",
        text = """
            Choose one —
            • Create two 1/1 colorless Soldier artifact creature tokens.
            • Creatures you control get +2/+0 until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = modes(
        mode(create(colorlessSoldier, count = n(2))),
        mode(pump(Objects.Each(creatureYouControl), 2, 0)),
    ))))

    @Test
    fun skystrikeOfficer() = card(
        name = "Skyward Lieutenant", cost = "{2}{U}", type = "Creature — Human Soldier", pt = "2/3",
        text = """
            Flying
            Whenever this creature attacks, create a 1/1 colorless Soldier artifact creature token.
            Tap three untapped Soldiers you control: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, create(colorlessSoldier)),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(sub("Soldier"), count = n(3)))), draw(1)),
    )

    // CR 603.2
    @Test
    fun thirdPathIconoclast() = card(
        name = "Fourth Path Tinkerer", cost = "{U}{R}", type = "Creature — Human Monk", pt = "2/1",
        text = "Whenever you cast a noncreature spell, create a 1/1 colorless Soldier artifact creature token.",
    ) parsesTo abilities(triggered(youCast(noncreatureSpell), create(colorlessSoldier)))

    // CR 700.2, 120.3
    @Test
    fun brotherhoodsEnd() = card(
        name = "Guild's Reckoning", cost = "{1}{R}{R}", type = "Sorcery",
        text = """
            Choose one —
            • Guild's Reckoning deals 3 damage to each creature and each planeswalker.
            • Destroy all artifacts with mana value 3 or less.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = modes(
        mode(damage(3, toObjects(Objects.Each(or(creature, ObjectFilter.Type(CardType.Planeswalker)))))),
        mode(destroy(Objects.Each(and(artifact, manaValue(Comparison.AtMost, 3))))),
    ))))

    // CR 120.3: the damage to the permanent's controller
    @Test
    fun unleashShell() = card(
        name = "Release Casing", cost = "{3}{R}{R}", type = "Instant",
        text = "Release Casing deals 5 damage to target creature or planeswalker and 2 damage to that permanent's controller.",
    ) parsesTo abilities(spell(
        damage(5, Recipients(objects = target("permanent"))),
        damage(2, Recipients(players = Players.ControllerOf(target("permanent")))),
        targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
    ))

    @Test
    fun goForTheThroat() = card(
        name = "Strike the Windpipe", cost = "{1}{B}", type = "Instant",
        text = "Destroy target nonartifact creature.",
    ) parsesTo abilities(spell(destroy(target("creature")), targets = listOf(slot("creature", and(not(artifact), creature)))))

    // CR 702.20a (vigilance)
    @Test
    fun loranOfTheThirdPath() = card(
        name = "Loran of the Fourth Path", cost = "{2}{W}", type = "Legendary Creature — Human Artificer", pt = "2/1",
        text = """
            Vigilance
            When Loran of the Fourth Path enters, destroy up to one target artifact or enchantment.
            {T}: You and target opponent each draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment), min = 0))),
        activated(cost(tap), doing(Effect.Draw(Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(Players.Target("opponent"))))), n(1))),
            targets = listOf(targetOpponent)),
    )

    // CR 106.1b
    @Test
    fun energyRefractor() = card(
        name = "Power Diffuser", cost = "{2}", type = "Artifact",
        text = """
            When this artifact enters, draw a card.
            {2}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        Ability.Activated(cost(mana("{2}")), Instructions(steps = listOf(doing(Effect.AddMana(ManaProduction(ManaKind.Choose(amount = n(1))), Players.You)))), anyTime),
    )

    // CR 614.1, 702.110a (menace)
    @Test
    fun evangelOfSynthesis() = card(
        name = "Herald of Fusion", cost = "{U}{B}", type = "Creature — Phyrexian Human Cleric", pt = "2/3",
        text = """
            When this creature enters, draw a card, then discard a card.
            As long as you've drawn two or more cards this turn, this creature gets +1/+0 and has menace.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), discard(1)),
        static(pt(1, 0), has(Keyword.Menace), condition = drewTwo),
    )

    @Test
    fun flowOfKnowledge() = card(
        name = "Tide of Insight", cost = "{4}{U}", type = "Instant",
        text = "Draw a card for each Island you control, then discard two cards.",
    ) parsesTo abilities(spell(
        Step.Do(Effect.Draw(Players.You, count(and(sub("Island"), you)))),
        discard(2),
    ))

    @Test
    fun gaeasCourser() = card(
        name = "Grove Pacer", cost = "{4}{G}", type = "Creature — Centaur Soldier", pt = "4/5",
        text = "Whenever this creature attacks, if there are three or more creature cards in your graveyard, draw a card.",
    ) parsesTo abilities(triggered(attacksSelf, draw(1),
        interveningIf = Condition.Exists(inYourGraveyard(creature), 3)))

    @Test
    fun gruesomeRealization() = card(
        name = "Dreadful Epiphany", cost = "{1}{B}{B}", type = "Sorcery",
        text = """
            Choose one —
            • You draw two cards and you lose 2 life.
            • Creatures your opponents control get -1/-1 until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = modes(
        mode(draw(2), loseLife(2)),
        mode(pump(Objects.Each(creatureOpponentControls), -1, -1)),
    ))))

    @Test
    fun mishraExcavationProdigy() = card(
        name = "Quarry Tinkerer", cost = "{2}{R}", type = "Legendary Creature — Human Artificer", pt = "2/1",
        text = """
            Haste
            {1}, {T}, Discard a card: Draw a card.
            Whenever you discard one or more artifact cards, add {R}{R}. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(mana("{1}"), tap, discardACard), draw(1)),
        triggered(Trigger.On(EventPattern.Discard(artifact, players = PlayerFilter.You), oneOrMore = true),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!)), Players.You)), perTurn = 1),
    )

    @Test
    fun thraxodemon() = card(
        name = "Gloom Imp", cost = "{1}{B}", type = "Creature — Demon", pt = "2/2",
        text = "{3}, {T}, Sacrifice another creature or artifact: Draw a card.",
    ) parsesTo abilities(activated(cost(mana("{3}"), tap, sacrificeA(and(another, or(creature, artifact)))), draw(1)))

    @Test
    fun tocasiasWelcome() = card(
        name = "Welcome of the Dig", cost = "{2}{W}", type = "Enchantment",
        text = "Whenever one or more creatures you control with mana value 3 or less enter, draw a card. This ability triggers only once each turn.",
    ) parsesTo abilities(Ability.Triggered(
        trigger = Trigger.On(EventPattern.ZoneChange(and(creature, you, manaValue(Comparison.AtMost, 3)), to = Zone.Battlefield), oneOrMore = true),
        instructions = Instructions(steps = listOf(draw(1))),
        perTurn = 1,
    ))

    @Test
    fun unionOfTheThirdPath() = card(
        name = "Union of Paths", cost = "{2}{W}", type = "Instant",
        text = "Draw a card, then you gain life equal to the number of cards in your hand.",
    ) parsesTo abilities(spell(
        draw(1),
        Step.Do(Effect.GainLife(Players.You, count(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    ))

    private val drewTwo = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events)
}
