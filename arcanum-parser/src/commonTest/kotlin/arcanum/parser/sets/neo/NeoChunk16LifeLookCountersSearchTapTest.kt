package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 16: gain-life, look, modify-pt, phase-out, put-counters, reflexive-trigger, reveal, search, tap and untap wordings, and the additional-time
 * trigger: leave-the-battlefield and optional-return riders, look-at-the-top choices, power statics counting cards or modified creatures, a planeswalker
 * that phases out, counters on creatures or Vehicles, end-step "you may pay {1}. When you do" triggers, searches and "attacks alone" taps. */
class NeoChunk16LifeLookCountersSearchTapTest {
    private val modifiedYouControl = and(creature, you, ObjectFilter.Modified)
    private val shrinesYouControl = and(sub("Shrine"), you)
    private val creatureOrVehicle = or(creature, sub("Vehicle"))
    private val leavesSelf = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))
    private val samuraiOrWarriorAlone = on(EventPattern.Attack(and(or(sub("Samurai"), sub("Warrior")), you), alone = true))
    private fun bound(name: String) = Objects.Bound(name)
    private val payOne = Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid")
    private val toLibraryBottomRandom = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true)
    private fun lookChoose(count: Int, filter: ObjectFilter) = arrayOf(
        doing(Effect.LookAt(Objects.LibraryCards(of = Players.You, count = n(count), fromTop = true), players = Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(filter, min = n(0), max = n(1), from = bound("seen")), bind = "chosen", chooser = Players.You),
        doing(Effect.Reveal(bound("chosen"))),
        move(bound("chosen"), Zone.Hand),
        doing(Effect.Move(Objects.Except(bound("seen"), bound("chosen")), to = toLibraryBottomRandom)),
    )

    @Test
    fun circuitFixer() = card(
        name = "Circuit Fixer",
        cost = "{3}",
        type = "Artifact Creature — Insect",
        pt = "2/3",
        text = """
            When this creature enters, you gain 2 life.
            When this creature leaves the battlefield, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(2)),
        triggered(leavesSelf, draw(1)),
    )

    // CR 608.2c: the optional return is a cost-like step the "If you do" tests
    @Test
    fun geothermalSpirit() = card(
        name = "Geothermal Spirit",
        cost = "{3}{G}",
        type = "Creature — Spirit",
        pt = "4/3",
        text = "When this creature enters, you may return an enchantment you control to its owner's hand. If you do, you gain 3 life.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Pay(cost(CostPart.ReturnToHand(CostObjects.Choose(and(enchantment, you), count = n(1)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), gainLife(3)),
        ),
    )

    // CR 701.20 (look is private, reveal is public), 701.24 / 401.4 (the rest go to the bottom in a random order), 608.2c ("this way")
    @Test
    fun blossomLeaper() = card(
        name = "Blossom Leaper",
        cost = "{3}{G}{G}",
        type = "Creature — Spirit",
        pt = "4/4",
        text = """
            Reach
            When this creature enters, look at the top five cards of your library. You may reveal a creature or enchantment card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, you gain 4 life.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            entersSelf,
            *lookChoose(5, or(creature, enchantment)),
            ifThen(Condition.Not(Condition.NonEmpty("chosen")), gainLife(4)),
        ),
    )

    @Test
    fun communeWithKami() = card(
        name = "Commune with Kami",
        cost = "{G}",
        type = "Sorcery",
        text = "Look at the top four cards of your library. You may reveal an enchantment or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(*lookChoose(4, or(enchantment, land))),
    )

    // CR 613.4c (power-changing static from a count of cards in a graveyard)
    @Test
    fun runawayScrapBot() = card(
        name = "Runaway Scrap-Bot",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        pt = "0/4",
        text = """
            Trample
            This creature gets +1/+0 for each artifact and/or enchantment card in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.ModifyPowerToughness(count(and(or(artifact, enchantment), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), n(0))),
    )

    // CR 700.9 (modified), 613.4c
    @Test
    fun upriserVeteran() = card(
        name = "Upriser Veteran",
        cost = "{1}{R}",
        type = "Creature — Human Samurai",
        pt = "1/3",
        text = "This creature gets +2/+0 for each other modified creature you control. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(Value.Times(count(and(another, modifiedYouControl)), 2), n(0))),
    )

    // CR 707.10 (copy an ability), 113.3 (activated and triggered abilities from an enchantment source), 702.? none: mana abilities can't be targeted (reminder)
    @Test
    fun weaverOfPeace() = card(
        name = "Weaver of Peace",
        cost = "{1}{G}",
        type = "Enchantment Creature — Snake Druid",
        pt = "2/2",
        text = """
            Other enchantment creatures you control get +1/+1.
            {G}, {T}: Copy target activated or triggered ability you control from an enchantment source. You may choose new targets for the copy. (Mana abilities can't be targeted.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, enchantment, creature, you))),
        activated(
            cost(mana("{G}"), tap),
            doing(Effect.CopySpell(target("ability"), controller = Players.You, newTargets = true)),
            targets = listOf(slot("ability", and(
                or(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = enchantment), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered, source = enchantment)),
                you))),
        ),
    )

    // CR 702.26a (phasing), 603.4 (intervening "if": checked on trigger and on resolution), 306.5 (loyalty), 114.1 (emblem), 510.1
    @Test
    fun kaitoShizukiLookalike() = card(
        name = "Kenji Shizuma",
        cost = "{1}{U}{B}",
        type = "Legendary Planeswalker — Kenji",
        loyalty = "3",
        text = """
            At the beginning of your end step, if Kenji entered this turn, he phases out.
            [+1]: Draw a card. Then discard a card unless you attacked this turn.
            [−2]: Create a 1/1 blue Ninja creature token with "This token can't be blocked."
            [−7]: You get an emblem with "Whenever a creature you control deals combat damage to a player, search your library for a blue or black creature card, put it onto the battlefield, then shuffle."
        """,
    ) parsesTo abilities(
        triggered(
            yourEndStep,
            doing(Effect.PhaseOut(Objects.Self)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield, objects = ObjectFilter.Any))),
        ),
        loyalty(1, draw(1), ifThen(Condition.Not(Condition.HappenedThisTurn(EventPattern.Attack(you), atLeast = 1, measure = Measure.Events)), discard(1))),
        loyalty(-2, create(creatureToken("Ninja", listOf(Color.Blue), 1, 1, listOf(static(Mechanic.CantBeBlocked))))),
        loyalty(-7, Step.Do(Effect.CreateEmblem(
            listOf(triggered(
                on(EventPattern.Damage(and(creature, you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
                search(and(or(ObjectFilter.OfColor(Color.Blue), ObjectFilter.OfColor(Color.Black)), creature)),
                move(bound("found"), Zone.Battlefield),
                shuffle,
            )),
            Players.You,
        ))),
    )

    @Test
    fun bearerOfRemembrance() = card(
        name = "Bearer of Remembrance",
        cost = "{2}{G}",
        type = "Enchantment Creature — Human Monk",
        pt = "3/2",
        text = "{5}{G}: Put a +1/+1 counter on target enchantment creature. It gains trample until end of turn.",
    ) parsesTo abilities(
        activated(
            cost(mana("{5}{G}")),
            counters(target("creature")),
            gains(target("creature"), Keyword.Trample),
            targets = listOf(slot("creature", and(enchantment, creature))),
        ),
    )

    @Test
    fun generousGuest() = card(
        name = "Generous Guest",
        cost = "{G}",
        type = "Creature — Spirit",
        pt = "1/1",
        text = "Whenever you cast an enchantment spell, put a +1/+1 counter on target creature.",
    ) parsesTo abilities(
        triggered(youCast(enchantment), counters(target("creature")), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun lightTheRoad() = card(
        name = "Light the Road",
        cost = "{W}",
        type = "Instant",
        text = """
            Choose one —
            • Put a +1/+1 counter on target creature or Vehicle. Untap it.
            • Return target permanent you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
                mode(counters(target("permanent")), untapIt(target("permanent")), targets = listOf(slot("permanent", creatureOrVehicle))),
                mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(permanentFilter, you)))),
            ),
        )),
    )

    // CR 714.2 / 716.? is not involved: a lore counter is put by an ability (CR 714.3b), here "each Saga you control"; the modal trigger chooses "up to one"
    @Test
    fun satsukiTheLivingLoreLookalike() = card(
        name = "Satoko, the Living Lore",
        cost = "{G}{W}",
        type = "Legendary Creature — Human Druid",
        pt = "1/3",
        text = """
            {T}: Put a lore counter on each Saga you control. Activate only as a sorcery.
            When Satoko dies, choose up to one —
            • Return target Saga or enchantment creature you control to its owner's hand.
            • Return target Saga card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        activated(cost(tap), counters(Objects.Each(and(sub("Saga"), you)), 1, CounterKind.Lore), rules = asSorcery),
        Ability.Triggered(diesSelf, Instructions(modes = modes(
                mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(or(sub("Saga"), and(enchantment, creature)), you)))),
                mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(sub("Saga"))))),
                min = 0,
            ),
        )),
    )

    // CR 603.10a (a dies trigger looks back), one ability with two events
    @Test
    fun sevenTailTutor() = card(
        name = "Seven-Tail Tutor",
        cost = "{3}{W}",
        type = "Creature — Fox Samurai",
        pt = "2/3",
        text = "When this creature enters or dies, put a +1/+1 counter on target creature or Vehicle you control.",
    ) parsesTo abilities(
        triggered(
            Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            )),
            counters(target("permanent")),
            targets = listOf(slot("permanent", and(creatureOrVehicle, you))),
        ),
    )

    // CR 700.9, 608.2c ("Then": the counter first, so the target can become modified), 120.3 (damage from this creature)
    @Test
    fun thunderingBolt() = card(
        name = "Thundering Bolt",
        cost = "{2}{R}{R}",
        type = "Creature — Spirit",
        pt = "3/3",
        text = """
            Haste
            Whenever this creature attacks, put a +1/+1 counter on target creature you control. Then this creature deals X damage to each opponent, where X is the number of modified creatures you control other than this creature. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(
            attacksSelf,
            counters(target("creature")),
            damage(count(and(creature, you, another, ObjectFilter.Modified)), eachOpponent),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    // CR 601.2f / 602.2b (cost reductions apply to activated abilities too)
    @Test
    fun towashiGuideBox() = card(
        name = "Towashi Guide-Box",
        cost = "{4}",
        type = "Artifact Creature — Construct",
        pt = "2/1",
        text = """
            When this creature enters, put a +1/+1 counter on target creature you control.
            {4}, {T}: Draw a card. This ability costs {1} less to activate for each modified creature you control. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", and(creature, you)))),
        activated(
            cost(mana("{4}"), tap),
            draw(1),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(Objects.Each(modifiedYouControl)))),
        ),
    )

    // CR 603.12 (reflexive trigger: "When you do" has its own target, chosen when it goes on the stack), 700.2 is not involved
    @Test
    fun shintaiOfOldBattles() = card(
        name = "Shintai of Old Battles",
        cost = "{2}{R}",
        type = "Legendary Enchantment Creature — Shrine",
        pt = "2/2",
        text = """
            First strike
            At the beginning of your end step, you may pay {1}. When you do, Shintai of Old Battles deals X damage to target player or planeswalker, where X is the number of Shrines you control.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(
            yourEndStep,
            payOne,
            ifThen(Condition.Did("paid"), reflexive(
                damage(count(shrinesYouControl), Recipients(target = "player or planeswalker")),
                targets = listOf(TargetSlot("player or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Any),
                    min = Value.Fixed(1), max = Value.Fixed(1))),
            )),
        ),
    )

    @Test
    fun shintaiOfBoundlessGrowth() = card(
        name = "Shintai of Boundless Growth",
        cost = "{1}{G}",
        type = "Legendary Enchantment Creature — Shrine",
        pt = "1/1",
        text = """
            Trample
            At the beginning of your end step, you may pay {1}. When you do, put a +1/+1 counter on target Shrine for each Shrine you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            yourEndStep,
            payOne,
            ifThen(Condition.Did("paid"), reflexive(
                counters(target("Shrine"), count(shrinesYouControl)),
                targets = listOf(slot("Shrine", sub("Shrine"))),
            )),
        ),
    )

    // CR 701.20 (reveal), 608.2c ("If you do" = the choice was made; "If you don't" = it wasn't)
    @Test
    fun reckonerShakeup() = card(
        name = "Reckoner Shakeup",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You may choose a nonland card from it. If you do, that player discards that card. If you don't, put two +1/+1 counters on a creature or Vehicle you control.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Pay(
                steps = listOf(Step.Choose(Choice.Objects(nonland, n(1), n(1), from = bound("revealed")), bind = "chosen", chooser = Players.You)),
                payer = Players.You, bind = "paid",
            ),
            ifThen(
                Condition.Did("paid"),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = bound("chosen"))),
                otherwise = listOf(
                    Step.Choose(Choice.Objects(and(creatureOrVehicle, you), n(1), n(1)), bind = "chosen", chooser = Players.You),
                    counters(bound("chosen"), 2),
                ),
            ),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 701.23 (search), 701.20 (reveal), 307.1 (sorcery timing)
    @Test
    fun ecologistsGreenhouse() = card(
        name = "Ecologist's Greenhouse",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {2}, {T}, Sacrifice this artifact: Put a +1/+1 counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(basicLand(), reveal = true), move(bound("found"), Zone.Hand), shuffle), Players.You)),
        activated(cost(mana("{2}"), tap, sacrificeSelf), counters(target("creature")), targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 700.2 (modes), 107.3 (X is the count when the token is created: lands you control then, CR 608.2h)
    @Test
    fun kuraTheBoundlessSea() = card(
        name = "Kura, the Boundless Sea",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Dragon Spirit",
        pt = "4/4",
        text = """
            Flying, deathtouch
            When Kura dies, choose one —
            • Search your library for up to three land cards, reveal them, put them into your hand, then shuffle.
            • Create an X/X green Spirit creature token, where X is the number of lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        Ability.Triggered(diesSelf, Instructions(modes = modes(
                mode(search(land, max = 3, reveal = true), move(bound("found"), Zone.Hand), shuffle),
                mode(create(TokenSpec.Defined(
                    FaceDefinition("Spirit Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Spirit")),
                        power = PrintedNumber.X, toughness = PrintedNumber.X),
                    power = count(and(land, you)), toughness = count(and(land, you)),
                ))),
            ),
        )),
    )

    @Test
    fun shrineWarden() = card(
        name = "Shrine Warden",
        cost = "{5}",
        type = "Artifact Creature — Construct",
        pt = "3/2",
        text = "When this creature enters, you may search your library for an Aura or Shrine card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(or(sub("Aura"), sub("Shrine")), reveal = true), move(bound("found"), Zone.Hand), shuffle), Players.You)),
    )

    @Test
    fun imperialSubjugator() = card(
        name = "Imperial Subjugator",
        cost = "{2}{W}",
        type = "Creature — Human Samurai",
        pt = "3/2",
        text = "Whenever a Samurai or Warrior you control attacks alone, tap target creature you don't control.",
    ) parsesTo abilities(
        triggered(
            samuraiOrWarriorAlone,
            tapIt(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 603.2d (a permanent's trigger caused by an attacking creature triggers an additional time), 508.1m
    @Test
    fun ishinTwoHeavensAsOne() = card(
        name = "Isshin, Twin Heavens as One",
        cost = "{R}{W}{B}",
        type = "Legendary Creature — Human Samurai",
        pt = "3/4",
        text = "If a creature attacking causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.",
    ) parsesTo abilities(
        static(Mechanic.TriggersAdditionalTime(EventPattern.Attack(creature), and(permanentFilter, you))),
    )

    // CR 506.4 / 500.8 (additional combat phase), 608.2c; "attacks alone" (CR 506.5-ish: a lone attacker), the first combat phase has one beginning of combat
    @Test
    fun raiyuuStormsEye() = card(
        name = "Raiyuu, Storm's Eye",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Samurai",
        pt = "3/3",
        text = """
            First strike
            Whenever a Samurai or Warrior you control attacks alone, untap it. If it's the first combat phase of the turn, there is an additional combat phase after this phase.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(
            samuraiOrWarriorAlone,
            untapIt(Objects.EventObject(EventRole.Object)),
            ifThen(
                Condition.Compare(
                    Value.EventsThisTurn(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), Measure.Events),
                    Comparison.AtMost, n(1)),
                doing(Effect.ExtraCombat()),
            ),
        ),
    )
}
