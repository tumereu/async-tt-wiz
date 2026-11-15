package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 18: life gain, looking at cards, modifying and setting P/T, moving, reflexive triggers, sacrifice, search, tap and untap. */
class TrkChunk18GainLifeLookModifyMoveSearchTapTest {
    private val officer = sub("Officer")
    private val artifactCreature = and(artifact, creature)
    private val officerToken = creatureToken("Officer", listOf(Color.Red), 1, 1)
    private val nonlandPermanent = and(not(land), permanentFilter)
    private val seen = Objects.Bound("seen")
    private val chosen = Objects.Bound("chosen")
    private val restOfSeen = Objects.Except(seen, chosen)
    private val bottom = Destination(Zone.Library, libraryPosition = -1)
    private val bottomRandom = Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
    private val untilNextEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any))
    private fun top(n: Int) = Objects.LibraryCards(Players.You, Value.Fixed(n), fromTop = true)
    private fun lookAndTake(count: Int, filter: ObjectFilter, rest: Destination) = arrayOf(
        Step.Do(Effect.LookAt(top(count), players = Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(filter, min = n(0), max = n(1), from = seen), bind = "chosen", chooser = Players.You),
        Step.Do(Effect.Reveal(chosen)),
        Step.Do(Effect.Move(chosen, Destination(Zone.Hand))),
        Step.Do(Effect.Move(restOfSeen, rest)),
    )
    private fun exileTop(count: Int, duration: Duration) = arrayOf(
        Step.Do(Effect.Move(top(count), Destination(Zone.Exile), postBind = "exiled")),
        Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
            Affected.Objects(Objects.Bound("exiled")), duration)),
    )

    // CR 603.2, 119.1 (life gained), 608.2h (the count is fixed on resolution): X is one plus the life gained this turn
    @Test
    fun medicalBayOfRecovery() = card(
        name = "Infirmary Of Growth",
        cost = "{5}",
        type = "Artifact",
        text = """
            Whenever a creature you control enters, you gain 1 life.
            At the beginning of combat on your turn, put X +1/+1 counters on target creature you control, where X is one plus the amount of life you gained this turn.
        """,
    ) parsesTo abilities(
        triggered(enters(creatureYouControl), gainLife(1)),
        triggered(beginningOfCombatOnYourTurn,
            counters(target("creature"), Value.Sum(listOf(n(1), Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount)))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 701.20 (look), 603.2 (cast trigger)
    @Test
    fun rutherfordUpgradedEnsign() = card(
        name = "Rafferty, Upgraded Cadet",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/2",
        text = """
            When Rafferty enters, look at the top six cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Whenever you cast a Spacecraft spell, put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, *lookAndTake(6, artifact, bottomRandom)),
        triggered(youCast(sub("Spacecraft")), counters(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 701.20, 701.20b: a card is looked at, revealed only when put into the hand
    @Test
    fun spockAlwaysYourFriend() = card(
        name = "Sprock, Ever Your Ally",
        cost = "{U}{R}",
        type = "Legendary Creature — Vulcan Human Scientist",
        pt = "2/2",
        text = "When Sprock enters, look at the top four cards of your library. You may reveal a noncreature, nonland card from among them and put it into your hand. Put the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        triggered(entersSelf, *lookAndTake(4, and(not(creature), not(land)), bottom)),
    )

    // CR 700.2 (modes on a triggered ability), 603.2 (enters or attacks), 611.2b (until your next turn)
    @Test
    fun vgerTheIntruder() = card(
        name = "W'Ger, the Interloper",
        cost = "{6}{U}",
        type = "Legendary Artifact Creature — Construct",
        pt = "5/7",
        text = """
            Flying
            Whenever W'Ger enters or attacks, choose one —
            • Look at target opponent's hand, then draw a card.
            • Creatures your opponents control get -1/-0 until your next turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(entersOrAttacksSelf, Instructions(modes = modes(
            mode(
                Step.Do(Effect.LookAt(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))),
                    players = Players.You)),
                draw(1),
                targets = listOf(targetOpponent),
            ),
            mode(Step.Do(Effect.ApplyMechanics(listOf(pt(-1, 0)), Affected.Objects(Objects.Each(creatureOpponentControls)), Duration.UntilYourNextTurn))),
        ))),
    )

    // CR 701.20, 406.3 (exiling from a hand), 400.7 (once it leaves exile it is a new object), 601.2f (costs {2} more), 611.2b
    @Test
    fun zeroIncorporealSensate() = card(
        name = "Nero, Incorporeal Sensor",
        cost = "{1}{W/B}{W/B}",
        type = "Legendary Artifact Creature — Medusan Scientist",
        pt = "2/2",
        text = """
            Lifelink
            When Nero enters, look at target opponent's hand. You may exile a nonland card from it. For as long as that card remains exiled, its owner may play it. A spell cast this way costs {2} more to cast.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))),
                players = Players.You)),
            Step.Choose(Choice.Objects(and(not(land), ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))),
                min = n(0), max = n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(chosen, Destination(Zone.Exile), postBind = "exiled")),
            Step.ForEach(Each.Objects(Objects.Bound("exiled")), bind = "card", steps = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.Is(Players.OwnerOf(Objects.Bound("card"))),
                    costChange = CostChange.More(ManaCost.parse("{2}")!!, n(1)))),
                    Affected.Objects(Objects.Bound("card")), Duration.Indefinitely)))),
            targets = listOf(targetOpponent)),
    )

    // CR 613.4c (+1/+0 per count, counted continuously), 111.1 (tokens), 603.2
    @Test
    fun academyInstructor() = card(
        name = "Academy Tutor",
        cost = "{2}{R}",
        type = "Creature — Bolian Officer",
        pt = "0/3",
        text = """
            This creature gets +1/+0 for each Officer token you control.
            Whenever you cast a noncreature spell, create a 1/1 red Officer creature token.
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(count(and(officer, token, you)), n(0))),
        triggered(youCast(noncreatureSpell), create(officerToken)),
    )

    // CR 613.4c: a plural anthem over artifact creatures, itself included
    @Test
    fun assaultDrone() = card(
        name = "Assault Automaton",
        cost = "{3}{B}",
        type = "Artifact Creature — Borg",
        pt = "2/4",
        text = "Artifact creatures you control get +1/+0.",
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(and(artifactCreature, you))),
    )

    // CR 613.4c, 602.2 (discarding itself as a cost), 702.1 (works from the hand)
    @Test
    fun locutusTheOneWhoSpeaks() = card(
        name = "Lucutus, the One Who Spoke",
        cost = "{W}{B}",
        type = "Legendary Artifact Creature — Borg",
        pt = "1/1",
        text = """
            Artifact creatures you control get +1/+1.
            {W}{B}, Discard this card: It deals 2 damage to target creature. You gain 2 life.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(artifactCreature, you))),
        activated(cost(mana("{W}{B}"), CostPart.Discard(CostObjects.Self)),
            damage(2, toObjects(target("creature"))), gainLife(2), targets = listOf(slot("creature", creature))),
    )

    // CR 701.11 (exile), 610.3 / 603.7 (delayed return at the next end step), 400.7
    @Test
    fun ghostOfIllyria() = card(
        name = "Ghoul of Illyria",
        cost = "{3}{W}",
        type = "Creature — Elemental Spirit",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, exile up to one other target nonland permanent you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CreateDelayedTrigger(untilNextEndStep, Instructions(steps = listOf(
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                    postBind = "returned")))))),
            targets = listOf(slot("permanent", and(another, nonlandPermanent, you), min = 0))),
    )

    // CR 603.2, 603.2c/603.2g (leaves the graveyard), 603.4, 400.7 (once each turn)
    @Test
    fun hologramStalker() = card(
        name = "Hologram Prowler",
        cost = "{4}{G}{G}",
        type = "Creature — Hirogen Warrior",
        pt = "2/2",
        text = """
            When this creature enters, return target permanent card from your graveyard to your hand.
            Whenever a card leaves your graveyard, create a 3/3 colorless Illusion artifact creature token. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(or(artifact, ObjectFilter.Type(CardType.Battle), creature, enchantment, land,
                ObjectFilter.Type(CardType.Planeswalker)))))),
        triggered(on(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard)),
            create(creatureToken("Illusion", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature))), perTurn = 1),
    )

    // CR 508.3d (attack trigger), 603.4 (total power 8 or greater), 406.3, 611.2a (this turn), 701.8
    @Test
    fun kirkCommandingOfficer() = card(
        name = "Kirkland, Commanding Officer",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Officer",
        pt = "3/3",
        text = """
            Whenever you attack while creatures you control have total power 8 or greater, exile the top card of your library. You may play that card this turn.
            {2}, Sacrifice Kirkland: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        triggered(youAttack, *exileTop(1, Duration.EndOfTurn),
            triggersOnlyIf = Condition.Compare(Value.Of(NumericProperty.Power, Objects.Each(and(creature, you)), Combine.Total), Comparison.AtLeast, n(8))),
        activated(cost(mana("{2}"), sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    // CR 406.3, 611.2b (until the end of your next turn), 305.1 (playing the cards)
    @Test
    fun recklessImpulse() = card(
        name = "Rash Impulse",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Exile the top two cards of your library. Until the end of your next turn, you may play those cards.",
    ) parsesTo abilities(
        spell(*exileTop(2, Duration.UntilEndOfYourNextTurn)),
    )

    // CR 701.8 (exile), 603.10 (leave the graveyard), 111.1 (tapped token)
    @Test
    fun selaTalShiarParadox() = card(
        name = "Sera, Tal Shiar Paradox",
        cost = "{3}{B}",
        type = "Legendary Creature — Romulan Human Rogue",
        pt = "3/3",
        text = """
            Whenever Sera attacks, exile up to one target card from a graveyard.
            Whenever one or more cards leave your graveyard, create a tapped 2/2 black Romulan creature token.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, move(target("card"), Zone.Exile, postBind = "exiled"),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
        triggered(Trigger.On(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            Step.Do(Effect.CreateToken(creatureToken("Romulan", listOf(Color.Black), 2, 2), n(1), Players.You, tapped = true))),
    )

    // CR 406.3, 611.2b, 702.1 (an ability from the graveyard), 707.2 (token copy), 603.7 (delayed sacrifice), 602.5d (sorcery timing)
    @Test
    fun tashaYarOutOfTime() = card(
        name = "Tasha Yarrow, Out of Time",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Officer",
        pt = "2/1",
        text = """
            Haste
            When Tasha Yarrow dies, exile the top card of your library. Until the end of your next turn, you may play that card.
            {2}{R}, Exile this card from your graveyard: Create a token that's a copy of this card. Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(diesSelf, *exileTop(1, Duration.UntilEndOfYourNextTurn)),
        activated(cost(mana("{2}{R}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You, bind = "tokens")),
            Step.Do(Effect.CreateDelayedTrigger(untilNextEndStep, Instructions(steps = listOf(
                Step.Do(Effect.Sacrifice(Objects.Bound("tokens"), by = Players.You)))))),
            rules = asSorcery),
    )

    // CR 603.12 (reflexive trigger), 701.21 (sacrifice), 120.3
    @Test
    fun daharMaster() = card(
        name = "Dahar Apprentice",
        cost = "{3}{R}",
        type = "Creature — Klingon Warrior",
        pt = "4/3",
        text = "When this creature enters, you may sacrifice another nonland permanent. When you do, this creature deals 2 damage to any target.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(and(another, nonlandPermanent))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(damage(2, toAny), targets = listOf(anyTarget)))),
    )

    // CR 603.4 (intervening if), 701.21 (sacrifice is chosen on resolution)
    @Test
    fun targWrangler() = card(
        name = "Targ Handler",
        cost = "{2}{R}",
        type = "Creature — Klingon Warrior",
        pt = "5/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            At the beginning of your end step, if this creature attacked this turn, sacrifice a nonland permanent.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Choose(Choice.Objects(and(nonlandPermanent, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Is(Objects.Self))))),
    )

    // CR 701.23 (search), 701.21 (sacrifice any number), 602.5d (sorcery timing), 205.3i (Planet)
    @Test
    fun genesisDevice() = card(
        name = "Genesis Engine",
        cost = "{2}{G}",
        type = "Legendary Artifact",
        text = """
            When Genesis Engine enters, search your library for a basic land card or Planet card, put it onto the battlefield tapped, then shuffle.
            {3}{G}{G}, {T}, Sacrifice Genesis Engine: Sacrifice any number of lands you control, then search your library for up to that many land cards, put them onto the battlefield tapped, then shuffle. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(or(basicLand(), sub("Planet"))), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        activated(cost(mana("{3}{G}{G}"), tap, sacrificeSelf),
            Step.Choose(Choice.Objects(and(land, you), n(0), null), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("chosen"), postBind = "sacrificed")),
            Step.Do(Effect.Search(Players.You, land, max = Value.Count(Objects.Bound("sacrificed")), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle,
            rules = asSorcery),
    )

    // CR 714.2 (chapters), 305.6 (basic land type Mountain), 603.7 (a delayed trigger lasting this turn), 614.1 (counter and haste on the new creature)
    @Test
    fun theNakedTime() = card(
        name = "The Naked Hour",
        cost = "{1}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Search your library for a basic Mountain card, put it onto the battlefield tapped, then shuffle.
            II — Whenever a creature you control enters this turn, put a +1/+1 counter on it and it gains haste until end of turn.
            III — Sacrifice a land.
        """,
    ) parsesTo abilities(
        chapter(1, search(and(ObjectFilter.Super(Supertype.Basic), sub("Mountain"))), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        chapter(2, Step.Do(Effect.CreateDelayedTrigger(enters(creatureYouControl), Instructions(steps = listOf(
            counters(Objects.EventObject(EventRole.After)),
            gains(Objects.EventObject(EventRole.After), Keyword.Haste),
        )), duration = Duration.EndOfTurn, repeats = true))),
        chapter(3, Step.Choose(Choice.Objects(and(land, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))),
    )

    // CR 701.23, 701.20b (reveal), 122.1 (charge counters), 602.5d
    @Test
    fun warpCore() = card(
        name = "Warp Coil",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {1}, {T}: Put two charge counters on target Spacecraft. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            player = Players.You)),
        activated(cost(mana("{1}"), tap), counters(target("Spacecraft"), 2, charge), targets = listOf(slot("Spacecraft", sub("Spacecraft"))), rules = asSorcery),
    )

    // CR 604.3, 604.3a (characteristic-defining: power equals a count, toughness printed)
    @Test
    fun collectiveDrone() = card(
        name = "Collective Automaton",
        cost = "{1}{B}",
        type = "Artifact Creature — Borg",
        pt = "*/2",
        text = "Collective Automaton's power is equal to the number of artifact creatures you control.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(artifactCreature, you)), null), characteristicDefining = true),
    )

    // CR 701.26b (tap), 603.2
    @Test
    fun vexingDrone() = card(
        name = "Vexing Automaton",
        cost = "{1}{W}",
        type = "Artifact Creature — Borg",
        pt = "2/2",
        text = "When this creature enters, tap target creature an opponent controls.",
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 701.26b (untap), 602.5d (sorcery timing), 602.5b (once each turn)
    @Test
    fun dot7RepairSquad() = card(
        name = "DOT-9 Repair Squad",
        cost = "{3}{W}",
        type = "Artifact Creature — Robot",
        pt = "3/3",
        text = """
            Flying
            {1}{W}: Untap target creature. Activate only as a sorcery and only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{1}{W}")), untapIt(target("creature")), targets = listOf(slot("creature", creature)),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You, perTurn = 1)),
    )

    // CR 700.2 (modes), 701.26b (untap), 111.1 (token), 121.1 (draw)
    @Test
    fun makeItSo() = card(
        name = "Make It Thus",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Choose one —
            • Untap target permanent you control. Target player draws two cards.
            • Create a 1/1 red Officer creature token and draw two cards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(untapIt(target("permanent")), draw(2, Players.Target("player")),
                targets = listOf(slot("permanent", and(permanentFilter, you)), targetPlayer)),
            mode(create(officerToken), draw(2)),
        ))),
    )
}
