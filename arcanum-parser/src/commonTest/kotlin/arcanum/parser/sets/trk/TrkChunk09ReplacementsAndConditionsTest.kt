package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 09: replacement effects of entering, counters and tokens (CR 614.1a, 614.1c), a trigger doubling of life gain (CR 603.2d),
 * conditions of a cost's sacrifice (CR 608.2h) and of differently named lands (CR 201.2), a title in a card name ("Dr. Crusher"),
 * a cost putting this card from the graveyard on the bottom of the library (CR 113.6m), phasing (CR 702.26) and casting from exile.
 */
class TrkChunk09ReplacementsAndConditionsTest {
    private val isSelf = ObjectFilter.Is(Objects.Self)
    private val permanentYouControl = and(permanentFilter, you)
    private val differentlyNamedLands = Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.Name)

    // CR 603.7 (a delayed trigger made by a triggered ability), 614.1a; "If you do": a card was put onto the battlefield
    @Test
    fun ericaOrtegasPluckyPilot() = card(
        name = "Ellen Ortiz, Plucky Pilot",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Pilot",
        pt = "3/3",
        text = """
            Haste
            Whenever Ellen Ortiz becomes tapped, you may put a land card from your hand onto the battlefield. If you do, return a land you control to its owner's hand at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(
            on(EventPattern.Tap(objects = isSelf)),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield), postBind = "put")),
            ifThen(
                Condition.NonEmpty("put"),
                // the outer event's object, named for the delayed trigger (CR 603.7c)
                Step.Bind(Objects.EventObject(EventRole.Object), "event object"),
                Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Instructions(steps = listOf(
                    Step.Choose(Choice.Objects(and(land, you), min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
                    move(Objects.Bound("chosen"), Zone.Hand),
                )))),
            ),
        ),
    )

    // CR 603.2d (an ability triggering an additional time), 201.5c ("Dr. Crane": the first and last words of the name); the title's
    // period doesn't end the sentence
    @Test
    fun drBeverlyCrusher() = card(
        name = "Dr. Bernice Crane",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Doctor",
        pt = "2/2",
        text = """
            If you gaining life causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
            Whenever you gain life, put a +1/+1 counter on Dr. Crane.
        """,
    ) parsesTo abilities(
        static(Mechanic.TriggersAdditionalTime(EventPattern.GainLife(PlayerFilter.You), permanentYouControl)),
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self)),
    )

    // CR 614.1a (tokens created instead, as many), 111.4
    @Test
    fun holoEngineer() {
        val illusion = creatureToken("Illusion", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature))
        card(
            name = "Holo-Fabricator",
            cost = "{2}{G}{G}",
            type = "Creature — Human Artificer",
            pt = "3/3",
            text = """
                When this creature enters, create a 3/3 colorless Illusion artifact creature token.
                If one or more tokens would be created under your control, that many 3/3 colorless Illusion artifact creature tokens are created instead.
            """,
        ) parsesTo abilities(
            triggered(entersSelf, create(illusion)),
            static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.CreateInstead(illusion)))),
        )
    }

    // CR 614.1a (twice that many of each kind of counter you'd put), 702.11 (hexproof)
    @Test
    fun drJosephMBenga() = card(
        name = "Dr. Josephine Mbeki",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Human Doctor",
        pt = "3/3",
        text = """
            Other creatures you control with counters on them have hexproof.
            If you would put one or more counters on a creature you control, put twice that many of each of those kinds of counters on that creature instead.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), affects = Objects.Each(and(another, creature, you, ObjectFilter.HasCounter(null, 1)))),
        static(Mechanic.Replacement(EventPattern.CountersPut(and(creature, you), null, causedBy = you), modify = listOf(Modification.MultiplyAmount(2)))),
    )

    // CR 614.1a (twice that many charge counters you'd put)
    @Test
    fun ltCmdrGeordiLaForge() = card(
        name = "Lt. Cmdr. Orla Vance",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Artificer",
        pt = "3/2",
        text = "If you would put one or more charge counters on an artifact you control, put twice that many charge counters on that artifact instead.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CountersPut(and(artifact, you), charge, causedBy = you), modify = listOf(Modification.MultiplyAmount(2)))),
    )

    // CR 608.2h (the creature sacrificed to pay the cost, as it last existed), 700.4
    @Test
    fun gowronEagerForBattle() = card(
        name = "Gorvak, Eager for Battle",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Klingon Warrior",
        pt = "3/3",
        text = """
            Whenever another creature dies, put a +1/+1 counter on Gorvak.
            Sacrifice another creature: Gorvak gains trample until end of turn. If you sacrificed a Klingon this way, you draw a card and lose 1 life.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(another, creature), from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self)),
        activated(
            cost(sacrificeA(and(another, creature), preBind = "sacrificed")),
            gains(Objects.Self, Keyword.Trample),
            ifThen(Condition.ObjectMatches(Objects.Bound("sacrificed"), sub("Klingon")), draw(1), loseLife(1)),
        ),
    )

    // CR 201.2 (differently named), 613.4b (base power and toughness, layer 7b), 611.2a (no duration: for good), 602.5b
    @Test
    fun sevenOfNineUnassimilated() = card(
        name = "Nine of Twelve, Unassimilated",
        cost = "{1}{U}",
        type = "Legendary Artifact Creature — Human Officer",
        pt = "0/4",
        text = """
            At the beginning of your first main phase, draw a card. Then discard a card unless you control five or more differently named lands.
            {6}{U}: Nine of Twelve's base power and toughness become 7/9. Activate only if you control seven or more differently named lands.
        """,
    ) parsesTo abilities(
        triggered(
            stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
            draw(1),
            ifThen(Condition.Not(Condition.Compare(differentlyNamedLands, Comparison.AtLeast, n(5))), discard(1)),
        ),
        activated(
            cost(mana("{6}{U}")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetPowerToughness(n(7), n(9))), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            rules = anyTime.copy(condition = Condition.Compare(differentlyNamedLands, Comparison.AtLeast, n(7))),
        ),
    )

    // CR 205.1b (a creature type without "in addition to its other types" replaces its creature types), 613.4c
    @Test
    fun imADoctorNotA() = card(
        name = "I'm a Medic, Not a . . .",
        cost = "{G}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature you control. Untap it. Until end of turn, it gets +1/+1 and becomes a Doctor. (It loses all other creature types.)",
    ) parsesTo abilities(
        spell(
            counters(target("creature")),
            untapIt(target("creature")),
            untilEndOfTurn(target("creature"), pt(1, 1), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Doctor"))), replaceSubtypes = true)),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    // CR 614.1c (a replacement of how a creature enters), 508.4 (entering attacking: its controller chooses what it attacks), 506.3
    @Test
    fun kiraNerysFirebrand() = card(
        name = "Kiri Narro, Firebrand",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Bajoran Soldier",
        pt = "4/3",
        text = """
            Trample
            If a creature you control would enter while you control an attacking creature, instead it enters tapped and attacking.
            Whenever another creature you control enters, it gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(
            listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(creature, you), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped, Modification.EntersAttacking))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Exists(and(attacking, creature, you), atLeast = 1),
        ),
        triggered(enters(and(another, creature, you)), untilEndOfTurn(Objects.EventObject(EventRole.After), pt(1, 1))),
    )

    // CR 113.6m (a cost putting this card from the graveyard on the bottom of the library works in the graveyard), 401.4
    @Test
    fun danielsTemporalAgent() = card(
        name = "Danvers, Temporal Agent",
        cost = "{2}{B/G}{B/G}",
        type = "Legendary Creature — Human Advisor",
        pt = "3/1",
        text = """
            When Danvers enters, return target creature card from your graveyard to your hand.
            {2}, Put this card from your graveyard on the bottom of your library: You gain 2 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        activated(cost(mana("{2}"), CostPart.PutOnBottomOfLibrary(CostObjects.Self, from = Zone.Graveyard)), gainLife(2)),
    )

    // CR 106.1 (an amount of mana; "her" is this creature), 122.1 (counters of every kind), 207.2c (a flavor word), 113.6j (discard
    // this card: works in the hand)
    @Test
    fun jadziaDaxLivingLegacy() = card(
        name = "Jorna Vex, Living Legacy",
        cost = "{1}{G}",
        type = "Legendary Creature — Trill Scientist",
        pt = "0/1",
        text = """
            At the beginning of your first main phase, put a +1/+1 counter on Jorna Vex, then add an amount of {G} equal to the number of counters on her.
            Transfer Symbiont — {2}{G}, Discard this card: Put two +1/+1 counters on target creature.
        """,
    ) parsesTo abilities(
        triggered(
            stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
            counters(Objects.Self),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!,
                times = Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single))), Players.You)),
        ),
        activated(cost(mana("{2}{G}"), CostPart.Discard(CostObjects.Self)), counters(target("creature"), 2),
            targets = listOf(slot("creature", creature))).copy(abilityWord = "Transfer Symbiont"),
    )

    // CR 702.26 (phasing), 714 (Sagas), 101.2 ("can't phase in"), 701.21a
    @Test
    fun theCityOnTheEdgeOfForever() = card(
        name = "The Town at the Rim of Always",
        cost = "{3}{W}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — All artifacts and creatures phase out. Create a 0/1 white Human creature token with "Permanents can't phase in." (Treat phased-out permanents and anything attached to them as though they don't exist.)
            II — You draw two cards and each player loses 2 life.
            III — Sacrifice a creature token. If you do, you gain 5 life.
        """,
    ) parsesTo abilities(
        chapter(1,
            Step.Do(Effect.PhaseOut(Objects.Each(or(artifact, creature)))),
            create(creatureToken("Human", listOf(Color.White), 0, 1, abilities = listOf(static(Mechanic.PermanentsCantPhaseIn)))),
        ),
        chapter(2, draw(2), loseLife(2, Players.Each(PlayerFilter.Any))),
        chapter(3,
            Step.Choose(Choice.Objects(and(creature, token, you), min = n(1), max = n(1)), bind = "sacrificed", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"), postBind = "sacrificed")),
            ifThen(Condition.Compare(Value.Count(Objects.Bound("sacrificed")), Comparison.AtLeast, n(1)), gainLife(5)),
        ),
    )

    // CR 506.5 (can't attack alone)
    @Test
    fun petSehlat() = card(
        name = "Pet Sandcat",
        cost = "{2}{W}",
        type = "Creature — Bear",
        pt = "4/3",
        text = "This creature can't attack alone.",
    ) parsesTo abilities(
        static(Mechanic.CantAttackAlone),
    )

    // CR 603.4 (an intervening "if"), 707.9b (a copy except it isn't legendary), 603.7 (sacrificed at the next end step)
    @Test
    fun grayAndAdiraForeverJoined() = card(
        name = "Grael and Adin, Forever Joined",
        cost = "{2}{U/R}{U/R}",
        type = "Legendary Creature — Trill Human Officer",
        pt = "2/3",
        text = "At the beginning of combat on your turn, if you've cast a noncreature spell this turn, create a token that's a copy of target creature you control, except it isn't legendary. It gains haste until end of turn. Sacrifice it at the beginning of the next end step.",
    ) parsesTo abilities(
        triggered(
            beginningOfCombatOnYourTurn,
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(target("creature"), listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)))),
                n(1), Players.You, bind = "token")),
            gains(Objects.Bound("token"), Keyword.Haste),
            Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("token"), by = Players.You)))))),
            targets = listOf(slot("creature", and(creature, you))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Cast(noncreatureSpell, PlayerFilter.You), atLeast = 1, measure = Measure.Events),
        ),
    )

    // CR 113.6f (casting this card from exile: the permission works there), 608.2h
    @Test
    fun khanVengefulHeart() = card(
        name = "Kharn, Vengeful Heart",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Human Warrior",
        pt = "5/3",
        text = """
            When Kharn enters, you may sacrifice another creature or artifact. If you do, draw two cards.
            You may cast this card from exile.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(and(another, or(creature, artifact)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(2)),
        ),
        static(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)),
    )
}
