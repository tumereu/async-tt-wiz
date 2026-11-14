package arcanum.parser.sets.bro

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 15: granted abilities, life gain and loss, looking at cards and power/toughness changes. */
class BroChunk15GainAbilitiesLifeLookModifyTest {
    private val drewTwo = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events)
    private val nonartifactOnly = not(and(spellFilter, not(artifact)))

    // CR 106.6, 613.1f: creatures gain a mana ability whose mana has a spending restriction
    @Test
    fun voltaicPorter() = card(
        name = "Voltaic Porter", cost = "{2}{G}{U}", type = "Creature — Human Artificer", pt = "3/4",
        text = """
            Creatures you control have "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell."
            Whenever you cast an artifact spell with mana value 6 or greater, draw a card.
        """,
    ) parsesTo abilities(
        static(has(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!), restriction = nonartifactOnly), Players.You)))),
            affects = Objects.Each(creatureYouControl)),
        triggered(youCast(and(artifact, manaValue(Comparison.AtLeast, 6))), draw(1)),
    )

    // CR 702.21a, 702.84a, 613.1f: a ward and unearth granted to permanents and to cards in the graveyard
    @Test
    fun maktarShaperOfWrecks() = card(
        name = "Maktar, Shaper of Wrecks", cost = "{3}{B}{R}", type = "Legendary Creature — Human Artificer", pt = "4/4",
        text = """
            Permanents you control have "Ward—Sacrifice a permanent."
            Each artifact card in your graveyard has unearth {1}{B}{R}. ({1}{B}{R}: Return the card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Ward(cost(sacrificeA(permanentFilter)))), affects = Objects.Each(and(permanentFilter, you))),
        static(has(Keyword.Unearth(manaCost("{1}{B}{R}"))), affects = Objects.Each(inYourGraveyard(artifact))),
    )

    // CR 611.3a, 700.1: deathtouch while a count of artifacts holds; the counter only the first time the ability resolves
    @Test
    fun gnawingCogling() = card(
        name = "Gnawing Cogling", cost = "{G}", type = "Creature — Wurm", pt = "1/1",
        text = """
            This creature has deathtouch as long as you control three or more artifacts.
            Whenever an artifact you control enters, you gain 1 life. If this is the first time this ability has resolved this turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Deathtouch), condition = Condition.Exists(and(artifact, you), atLeast = 3)),
        triggered(enters(and(artifact, you)), gainLife(1),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(1)), then = listOf(counters(Objects.Self)))),
    )

    // CR 614.1a, 700.1: two abilities granted at once by a condition about cards drawn
    @Test
    fun hauntedStalker() = card(
        name = "Haunted Stalker", cost = "{4}{B}", type = "Creature — Beast Horror", pt = "4/5",
        text = "As long as you've drawn two or more cards this turn, this creature has deathtouch and lifelink.",
    ) parsesTo abilities(static(has(Keyword.Deathtouch, Keyword.Lifelink), condition = drewTwo))

    // CR 615: prevention of combat damage dealt by creatures with power 3 or less
    @Test
    fun veilOfBattle() = card(
        name = "Veil of Battle", cost = "{2}{G}", type = "Instant",
        text = "You gain 1 life for each creature on the battlefield. Prevent all combat damage that would be dealt this turn by creatures with power 3 or less.",
    ) parsesTo abilities(spell(
        Step.Do(Effect.GainLife(Players.You, count(creature))),
        Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(and(creature, power(Comparison.AtMost, 3)), RecipientFilter(), combat = true))),
            null, Duration.EndOfTurn)),
    ))

    // CR 700.4: "dies" trigger; sacrifice cost with an exile of noncreature artifacts or enchantments
    @Test
    fun scrapMite() = card(
        name = "Scrap Mite", cost = "{1}", type = "Artifact Creature — Insect", pt = "1/1",
        text = """
            When this creature dies, you gain 2 life.
            {G}, Sacrifice this creature: Exile target noncreature artifact or noncreature enchantment.
        """,
    ) parsesTo abilities(
        triggered(diesSelf, gainLife(2)),
        activated(cost(mana("{G}"), sacrificeSelf), move(target("permanent"), Zone.Exile),
            targets = listOf(slot("permanent", or(and(not(creature), artifact), and(not(creature), enchantment))))),
    )

    // CR 701.9b, 614.1a
    @Test
    fun stubbornBrute() = card(
        name = "Stubborn Brute", cost = "{2}{G}{G}", type = "Creature — Beast", pt = "4/4",
        text = """
            When this creature enters, you gain 4 life.
            If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(4)),
        Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You, causedBy = ObjectFilter.ControlledBy(PlayerFilter.Opponent)),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Battlefield))))),
            affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
    )

    // CR 702.16a, 702.16b: protection from multicolored
    @Test
    fun prismWarden() = card(
        name = "Prism Warden", cost = "{1}", type = "Artifact Creature — Soldier", pt = "1/2",
        text = """
            Protection from multicolored (This creature can't be blocked, targeted, dealt damage, enchanted, or equipped by anything multicolored.)
            Whenever a nonbasic land an opponent controls enters, you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Protection(ObjectFilter.Multicolored),
        triggered(enters(and(not(ObjectFilter.Super(Supertype.Basic)), land, opponents)), gainLife(1)),
    )

    // CR 701.20a, 701.24: look at the top four, take one, rest on the bottom in a random order
    @Test
    fun draftOfficer() = card(
        name = "Draft Officer", cost = "{W}", type = "Creature — Human Soldier", pt = "2/1",
        text = "{3}{W}: Look at the top four cards of your library. You may reveal a creature card with mana value 3 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        activated(manaCost("{3}{W}"),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(creature, manaValue(Comparison.AtMost, 3)), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 701.20a: look at the top card, optionally take a land, otherwise optionally put it into the graveyard
    @Test
    fun delvingArtificer() = card(
        name = "Delving Artificer", cost = "{1}{G}", type = "Creature — Human Artificer Scout", pt = "1/2",
        text = "Whenever an artifact you control enters, look at the top card of your library. If it's a land card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.",
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "top")),
            Step.If(Condition.ObjectMatches(Objects.Bound("top"), land), then = listOf(
                Step.Pay(steps = listOf(doing(Effect.Reveal(Objects.Bound("top"))), doing(Effect.Move(Objects.Bound("top"), Destination(Zone.Hand)))),
                    payer = Players.You, bind = "kept"))),
            Step.If(Condition.Not(Condition.Did("kept")), then = listOf(
                Step.May(steps = listOf(doing(Effect.Move(Objects.Bound("top"), Destination(Zone.Graveyard)))), player = Players.You)))),
    )

    // CR 601.2f, 118.7: the cost reduction of an activated ability counts creature cards in the graveyard
    @Test
    fun drainWarden() = card(
        name = "Drain Warden", cost = "{2}{B}", type = "Creature — Human Soldier", pt = "1/4",
        text = "{5}, {T}: Each opponent loses 2 life. This ability costs {1} less to activate for each creature card in your graveyard.",
    ) parsesTo abilities(
        activated(cost(mana("{5}"), tap), loseLife(2, Players.Each(PlayerFilter.Opponent)))
            .copy(rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(creature))))),
    )

    // CR 603.2: the second card drawn each turn; "each opponent" loses life
    @Test
    fun twinPuppeteer() = card(
        name = "Twin Puppeteer", cost = "{3}{B}", type = "Creature — Phyrexian Warlock", pt = "4/3",
        text = """
            Whenever you draw your second card each turn, each opponent loses 2 life and you gain 2 life.
            When this creature dies, return another target creature card with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        triggered(drawSecondCard, loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
        triggered(diesSelf, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(not(ObjectFilter.Is(Objects.SelfAfter)), creature, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 701.17a, 611.3a: a bonus and a keyword while a sacrifice happened this turn
    @Test
    fun sparkRunner() = card(
        name = "Spark Runner", cost = "{R}", type = "Creature — Goblin", pt = "1/2",
        text = "This creature gets +2/+0 and has menace as long as you sacrificed a permanent this turn.",
    ) parsesTo abilities(
        static(pt(2, 0), has(Keyword.Menace),
            condition = Condition.HappenedThisTurn(EventPattern.Sacrifice(you), atLeast = 1, measure = Measure.Objects)),
    )

    // CR 611.3a, 613.4c: an anthem for creatures you control
    @Test
    fun rallyHall() = card(
        name = "Rally Hall", cost = "{1}{W}{W}", type = "Enchantment",
        text = """
            Creatures you control get +1/+1.
            {5}{W}: Exile target nonland permanent you don't control until this enchantment leaves the battlefield. Activate only as a sorcery and only once.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(creatureYouControl)),
        activated(manaCost("{5}{W}"), Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))), rules = asSorcery.copy(once = true)),
    )

    // CR 613.4c: a bonus equal to a count, only on your turn with four or more Mountains
    @Test
    fun dunefieldStomper() = card(
        name = "Dunefield Stomper", cost = "{1}{R}", type = "Creature — Minotaur Barbarian", pt = "0/4",
        text = "As long as it's your turn and you control four or more Mountains, this creature gets +X/+0, where X is the number of Mountains you control.",
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(count(and(sub("Mountain"), you)), n(0)),
            condition = Condition.And(listOf(Condition.YourTurn, Condition.Exists(and(sub("Mountain"), you), atLeast = 4)))),
    )

    // CR 613.4c: other Soldiers you control only
    @Test
    fun bannerCaptain() = card(
        name = "Banner Captain", cost = "{2}{W}{U}", type = "Creature — Human Soldier", pt = "3/4",
        text = "Other Soldiers you control get +1/+1.",
    ) parsesTo abilities(static(pt(1, 1), affects = Objects.Each(and(another, sub("Soldier"), you))))
}
