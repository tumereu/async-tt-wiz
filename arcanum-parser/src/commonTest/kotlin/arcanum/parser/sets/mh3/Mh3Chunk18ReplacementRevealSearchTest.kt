package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 18: counter and graveyard replacements (CR 614), enters-tapped conditions, looking at and revealing cards
 * (CR 701.20), searching (CR 701.23), setting base power and toughness (CR 613.4b), skipping a step (CR 614.10) and
 * additional triggers (CR 603.2d).
 */
class Mh3Chunk18ReplacementRevealSearchTest {
    private val entersWithThreeCounters: Ability = Ability.Static(listOf(Mechanic.Replacement(
        event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(p1p1, n(3))),
    )), affects = Affected.Objects(Objects.Self))

    /** "This land enters tapped unless you control a <basic land type>." (CR 614.1c) */
    private fun entersTappedUnlessYouControl(type: String): Ability = Ability.Static(
        (entersTapped as Ability.Static).mechanics, affects = Affected.Objects(Objects.Self),
        condition = Condition.Not(Condition.Exists(and(sub(type), you), atLeast = 1)))

    /** CR 700.7: four or more card types among cards in your graveyard. */
    private val deliriumCondition = Condition.Compare(
        Value.CountDistinct(Objects.Each(inYourGraveyard()), SharedQuality.CardType), Comparison.AtLeast, n(4))

    // CR 614.1a, 122.6: twice that many counters
    @Test
    fun growingEvolution() = card(
        name = "Growing Evolution",
        cost = "{2}{G}",
        type = "Enchantment",
        text = "If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on that creature instead.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CountersPut(and(creature, you), p1p1), modify = listOf(Modification.MultiplyAmount(2)))),
    )

    // CR 614.1c: "enters with" counters
    @Test
    fun loyalHound() = card(
        name = "Loyal Hound",
        cost = "{G}{W}",
        type = "Creature — Dog",
        pt = "0/0",
        text = """
            Vigilance
            This creature enters with three +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        entersWithThreeCounters,
    )

    // CR 614.1c, 205.3i (Plains), historic (CR 700.6), 401.4 (random order)
    @Test
    fun hallowedHenge() = card(
        name = "Hallowed Henge",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Plains.
            {T}: Add {W}.
            {2}{W}{W}, {T}: Look at the top five cards of your library. You may reveal a historic card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. (Artifacts, legendaries, and Sagas are historic.)
        """,
    ) parsesTo abilities(
        entersTappedUnlessYouControl("Plains"),
        addOne("{W}"),
        activated(cost(mana("{2}{W}{W}"), tap),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(artifact, legendary, sub("Saga")), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 614.1c, 205.3i (Forest), 700.7 (delirium: ability word, a restriction on activation), 707.2 (copy), 611.2
    @Test
    fun mutableThicket() = card(
        name = "Mutable Thicket",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Forest.
            {T}: Add {G}.
            Delirium — {2}{G}{G}: This land becomes a copy of target permanent card in your graveyard until end of turn. Activate only if there are four or more card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        entersTappedUnlessYouControl("Forest"),
        addOne("{G}"),
        activated(cost(mana("{2}{G}{G}")),
            untilEndOfTurn(Objects.Self, Mechanic.CopyOf(target("card"))),
            targets = listOf(slot("card", and(permanentCard, inYourGraveyard()))),
            rules = anyTime.copy(condition = deliriumCondition)).copy(abilityWord = "Delirium"),
    )

    // CR 614.1c
    @Test
    fun humblePowerstone() = card(
        name = "Humble Powerstone",
        cost = "{3}",
        type = "Artifact",
        text = """
            This artifact enters tapped.
            {T}: Add {C}{C}.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You))),
    )

    // CR 701.20 (reveal), 111 (token), 401.4
    @Test
    fun sproutingRumble() = card(
        name = "Sprouting Rumble",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Reveal the top four cards of your library. You may put a permanent card from among them into your hand. Put the rest into your graveyard. Create a 0/1 colorless Eldrazi Spawn creature token with \"Sacrifice this token: Add {C}.\"",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(4), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Graveyard),
            create(eldraziSpawn),
        ),
    )

    // CR 701.23, 712 (modal double-faced card)
    @Test
    fun drownedTeachings() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Drowned Teachings",
            cost = "{3}{U/B}",
            type = "Instant",
            text = "Search your library for an instant card or a card with flash, reveal it, put it into your hand, then shuffle.",
        ) to abilities(
            spell(
                search(or(instant, and(cardFilter, kw(Keyword.Flash.kind))), reveal = true),
                move(Objects.Bound("found"), Zone.Hand),
                shuffle,
            ),
        ),
        card(
            name = "Sunken Archive",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {U} or {B}.
            """,
        ) to abilities(
            entersTapped,
            addEither("U", "B"),
        ),
    )

    // CR 613.4b, 613.1d: base P/T in layer 7b, the added subtype in layer 4; "other" excludes this creature
    @Test
    fun kingOfLittleBears() = card(
        name = "King of Little Bears",
        cost = "{G}{W}",
        type = "Legendary Creature — Bear",
        pt = "2/2",
        text = "Other creatures have base power and toughness 2/2 and are Bears in addition to their other types.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(n(2), n(2)), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bear")))),
            affects = Objects.Each(and(another, creature))),
    )

    // CR 614.10 (skip a step), 402.2 (maximum hand size), 614.1 (exile instead)
    @Test
    fun necrodominanceLookalike() = card(
        name = "Grim Dominion",
        cost = "{B}{B}{B}",
        type = "Legendary Enchantment",
        text = """
            Skip your draw step.
            At the beginning of your end step, you may pay any amount of life. If you do, draw that many cards.
            Your maximum hand size is five.
            If a card or token would be put into your graveyard from anywhere, exile it instead.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.SkipStep(TurnStep.Draw)),
        triggered(yourEndStep,
            Step.Pay(cost = null, steps = listOf(
                Step.Choose(Choice.Number(n(0), Value.LifeOf(Players.You)), bind = "life", chooser = Players.You),
                Step.Pay(cost(CostPart.PayLife(Value.Bound("life"))), payer = Players.You, mandatory = true, bind = "paid"),
            ), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Bound("life")))))),
        playerStatic(Mechanic.MaximumHandSize(n(5))),
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(or(cardFilter, token), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
    )

    // CR 603.2d: an additional trigger; 707.10 (copy a spell)
    @Test
    fun echoesOfEmptiness() = card(
        name = "Echoes of Emptiness",
        cost = "{3}{C}{C}{C}",
        type = "Kindred Enchantment — Eldrazi",
        text = """
            If a triggered ability of a colorless spell you control or another colorless permanent you control triggers, that ability triggers an additional time.
            Whenever you cast a colorless spell, copy it. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
        """,
    ) parsesTo abilities(
        static(Mechanic.TriggersAdditionalTime(event = null,
            sources = and(ObjectFilter.Colorless, or(and(spellFilter, you), and(another, ObjectFilter.Colorless, permanentFilter)), you))),
        triggered(youCast(and(ObjectFilter.Colorless, spellFilter)),
            Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true))),
    )
}

