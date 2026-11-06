package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 multicoloured cards, second part (chunk 12): a planeswalker that is a creature outside the battlefield with "repeat this
 * process" (CR 608.2c), amass (CR 701.47), revealing an opponent's top cards, a graveyard upkeep trigger, one spell each turn, an upkeep
 * payment or sacrifice, a delirium mode choice (CR 700.2), echo with a discard (CR 702.30), aftermath (CR 702.127), a power-for-toughness
 * pump, a characteristic-defining domain (CR 604.3, 305.6), an attack trigger with modes, investigating twice with evoke, and choosing a
 * number of coins to flip (CR 705) with free spells.
 */
class Mh2Chunk12MulticoloredTest {
    // CR 113.6b: the ability works only outside the battlefield; CR 608.2c "repeat this process"
    @Test
    fun grastTheRavenousTide() = card(
        name = "Grast, the Ravenous Tide",
        cost = "{1}{B}{G}",
        type = "Legendary Planeswalker — Grist",
        text = """
            As long as Grast isn't on the battlefield, it's a 1/1 Insect creature in addition to its other types.
            [+1]: Create a 1/1 black and green Insect creature token, then mill a card. If an Insect card was milled this way, put a loyalty counter on Grast and repeat this process.
            [−2]: You may sacrifice a creature. When you do, destroy target creature or planeswalker.
            [−5]: Each opponent loses life equal to the number of creature cards in your graveyard.
        """,
        loyalty = "3",
    ) parsesTo abilities(
        static(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Insect")))),
            Mechanic.SetPowerToughness(n(1), n(1)), condition = Condition.Not(Condition.ObjectMatches(Objects.Self, inZone(Zone.Battlefield))))
            .copy(functionsIn = listOf(Zone.Library, Zone.Hand, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)),
        activated(cost(CostPart.Loyalty(n(1))), Step.RepeatWhile(listOf(
            create(creatureToken("Insect", listOf(Color.Black, Color.Green), 1, 1)),
            mill(1, postBind = "milled"),
            Step.If(Condition.ObjectMatches(Objects.Bound("milled"), sub("Insect")), then = listOf(counters(Objects.Self, 1, CounterKind.Loyalty))),
        ), Condition.ObjectMatches(Objects.Bound("milled"), sub("Insect")))),
        activated(cost(CostPart.Loyalty(n(-2))),
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, planeswalker))))))),
        activated(cost(CostPart.Loyalty(n(-5))), doing(Effect.LoseLife(eachOpponentPlayers, count(inYourGraveyard(creature))))),
    )

    // CR 701.47a amass
    @Test
    fun lazotepVizier() = card(
        name = "Lazotep Vizier",
        cost = "{U}{B}",
        type = "Creature — Zombie Wizard",
        text = "Whenever you discard a card, you may pay {1}. If you do, amass Zombies 2. (Put two +1/+1 counters on an Army you control. It's also a Zombie. If you don't control an Army, create a 0/0 black Zombie Army creature token first.)",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)), Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.Action(KeywordAction.Amass(Subtype("Zombie"), n(2))))))),
    )

    // CR 107.3a: X is the number of Clues sacrificed
    @Test
    fun lonasCryptobotanist() = card(
        name = "Lonas, Cryptobotanist",
        cost = "{G}{U}",
        type = "Legendary Creature — Snake Elf Scout",
        text = """
            Whenever another nontoken creature you control enters, investigate.
            {T}, Sacrifice X Clues: Target opponent reveals the top X cards of their library. You may put a nonland permanent card with mana value X or less from among them onto the battlefield under your control. That player puts the rest on the bottom of their library in a random order.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        triggered(enters(and(another, nontoken, creature, you)), investigate),
        Ability.Activated(cost(tap, CostPart.Sacrifice(CostObjects.Choose(sub("Clue"), count = x))), Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                doing(Effect.Reveal(Objects.LibraryCards(Players.Target("opponent"), x, fromTop = true), bind = "revealed")),
                Step.Choose(Choice.Objects(and(nonland, permanentCard, ObjectFilter.ManaValue(Comparison.AtMost, x)), n(0), n(1), from = Objects.Bound("revealed")),
                    bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield, controller = Players.You),
                doing(Effect.Move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            ),
            announced = listOf("X"),
        ), anyTime),
    )

    // CR 113.6b: the upkeep trigger works in the graveyard
    @Test
    fun lordOfDemise() = card(
        name = "Lord of Demise",
        cost = "{1}{U}{B}",
        type = "Creature — Zombie Wizard",
        text = """
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            At the beginning of your upkeep, if this card is in your graveyard, you may pay 1 life. If you do, return it to your hand.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(entersSelf, surveil(2)),
        triggered(yourUpkeep, Step.Pay(Cost(listOf(CostPart.PayLife(n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(move(Objects.Self, Zone.Hand))),
            interveningIf = Condition.ObjectMatches(Objects.Self, inZone(Zone.Graveyard))).copy(functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 101.2: a "can't" with a condition on the spells already cast
    @Test
    fun temperance() = card(
        name = "Temperance",
        cost = "{1}{W}{U}",
        type = "Enchantment",
        text = """
            You can't cast more than one spell each turn.
            Whenever you cast a spell, draw a card.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.CantCast(ObjectFilter.Any), condition = Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
        triggered(youCast(ObjectFilter.Any), draw(1)),
    )

    // CR 118.12a: "unless you pay"
    @Test
    fun pirsuTheUnstable() = card(
        name = "Pirsu, the Unstable",
        cost = "{2}{R}{R}{W}{W}{B}{B}",
        type = "Legendary Creature — Elder Dragon",
        text = """
            Flying, lifelink
            At the beginning of your upkeep, sacrifice Pirsu unless you pay {R}{W}{B}.
            When Pirsu dies, it deals 7 damage to each nonlegendary creature.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(yourUpkeep, Step.Pay(manaCost("{R}{W}{B}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Sacrifice(Objects.Self))))),
        triggered(diesSelf, damage(7, toObjects(Objects.Each(and(not(ObjectFilter.Super(arcanum.api.Supertype.Legendary)), creature))))),
    )

    @Test
    fun priestessOfFellRites() = card(
        name = "Priestess of Fell Rites",
        cost = "{W}{B}",
        type = "Creature — Human Warlock",
        text = """
            {T}, Pay 3 life, Sacrifice this creature: Return target creature card from your graveyard to the battlefield. Activate only as a sorcery.
            Unearth {3}{W}{B} ({3}{W}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.PayLife(n(3)), sacrificeSelf), move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature))), rules = asSorcery),
        Keyword.Unearth(manaCost("{3}{W}{B}")),
    )

    // CR 700.2: one mode, or both with delirium
    @Test
    fun prescientTitan() = card(
        name = "Prescient Titan",
        cost = "{4}{U}{R}",
        type = "Creature — Giant Wizard",
        text = """
            Delirium — When this creature enters, choose one. If there are four or more card types among cards in your graveyard, choose both instead.
            • This creature deals 4 damage to any target.
            • Look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in a random order.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            mode(damage(4, toAny), targets = listOf(anyTarget)),
            mode(
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                move(Objects.Bound("kept"), Zone.Hand),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            ),
        ), min = 1, max = 1, maxIf = delirium, maxIfCount = 2, chooser = Players.You)), abilityWord = "Delirium"),
    )

    // CR 702.30a: echo with a cost that isn't mana
    @Test
    fun rakdosShowstopper() = card(
        name = "Rakdos Showstopper",
        cost = "{B}{R}",
        type = "Creature — Devil",
        text = """
            Haste
            Echo—Discard a card. (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Haste, Keyword.Echo(Cost(listOf(discardACard))))

    @Test
    fun voraciousSquirrel() = card(
        name = "Voracious Squirrel",
        cost = "{B/G}",
        type = "Creature — Squirrel",
        text = """
            Whenever you sacrifice an artifact or creature, put a +1/+1 counter on this creature.
            {1}{B}{G}, Sacrifice an artifact or creature: You gain 1 life and draw a card.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(on(EventPattern.Sacrifice(and(or(artifact, creature), you))), counters(Objects.Self)),
        activated(cost(mana("{1}{B}{G}"), sacrificeA(or(artifact, creature))), gainLife(1), draw(1)),
    )

    // CR 702.127a: aftermath, the second half only from the graveyard
    @Test
    fun wayWreck() = cardFaces(
        Layout.Split,
        card(name = "Way", cost = "{2}{G}", type = "Instant",
            text = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.") to abilities(spell(*rampTapped.toTypedArray())),
        card(name = "Wreck", cost = "{1}{R}{R}", type = "Sorcery", text = """
            Aftermath (Cast this spell only from your graveyard. Then exile it.)
            Wreck deals damage to target creature equal to the number of lands you control.
        """) to abilities(
            Keyword.Aftermath,
            spell(damage(count(and(land, you)), toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
        ),
    )

    @Test
    fun stormGodsSeer() = card(
        name = "Storm God's Seer",
        cost = "{1}{U}{R}",
        type = "Enchantment Creature — Human Shaman",
        text = """
            {1}: This creature gets +1/-1 until end of turn.
            When this creature dies, it deals 3 damage to any target.
        """,
        pt = "1/3",
    ) parsesTo abilities(activated(cost(mana("{1}")), pump(Objects.Self, 1, -1)), triggered(diesSelf, damage(3, toAny), targets = listOf(anyTarget)))

    @Test
    fun sythriaHarvestsHand() = card(
        name = "Sythria, Harvest's Hand",
        cost = "{G}{W}",
        type = "Legendary Enchantment Creature — Nymph",
        text = "Whenever you cast an enchantment spell, you gain 1 life and draw a card.",
        pt = "1/2",
    ) parsesTo abilities(triggered(youCast(enchantment), gainLife(1), draw(1)))

    @Test
    fun mortalAgony() = card(
        name = "Mortal Agony",
        cost = "{2}{B}{R}",
        type = "Sorcery",
        text = """
            Destroy target creature.
            Madness {B}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(spell(destroy(target("creature")), targets = listOf(slot("creature", creature))), Keyword.Madness(manaCost("{B}{R}")))

    // CR 604.3: characteristic-defining; CR 305.6 the basic land types
    @Test
    fun domainKavu() = card(
        name = "Domain Kavu",
        cost = "{R}{G}",
        type = "Creature — Kavu",
        text = """
            Domain — Domain Kavu's power and toughness are each equal to the number of basic land types among lands you control.
            Whenever this creature attacks, choose one —
            • Discard a card. If you do, draw a card.
            • Exile up to one target card from a graveyard.
        """,
        pt = "*/*",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.BasicLandType),
            Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.BasicLandType)), characteristicDefining = true).copy(abilityWord = "Domain"),
        modalTriggered(attacksSelf,
            mode(Step.Pay(Cost(listOf(discardACard)), payer = Players.You, mandatory = true, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
        ),
    )

    @Test
    fun sievesifter() = card(
        name = "Sievesifter",
        cost = "{3}{G}{U}",
        type = "Creature — Elemental",
        text = """
            Flying
            When this creature enters, investigate twice. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Evoke {G}{U} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Action(KeywordAction.Investigate(n(2))))),
        Keyword.Evoke(manaCost("{G}{U}")),
    )

    // CR 705: the chosen number of coins flipped at once; the flips won and lost
    @Test
    fun yuzraFortunesBlaze() = card(
        name = "Yuzra, Fortune's Blaze",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Efreet",
        text = """
            Flying
            Whenever Yuzra attacks, choose a number between 1 and 5. Flip that many coins. For each flip you win, draw a card. For each flip you lose, Yuzra deals 2 damage to you. If you won five flips this way, you may cast spells from your hand this turn without paying their mana costs.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf,
            Step.Choose(Choice.Number(n(1), n(5)), bind = "number", chooser = Players.You),
            doing(Effect.FlipCoin(Players.You, bind = "flips", count = Value.Bound("number"))),
            doing(Effect.Draw(Players.You, Value.FlipsWon("flips"))),
            doing(Effect.DealDamage(Recipients(players = Players.You), Value.Times(Value.FlipsLost("flips"), 2), Objects.Self)),
            Step.If(Condition.Compare(Value.FlipsWon("flips"), Comparison.AtLeast, n(5)), then = listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Hand, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))), Duration.EndOfTurn)))),
        ),
    )
}
