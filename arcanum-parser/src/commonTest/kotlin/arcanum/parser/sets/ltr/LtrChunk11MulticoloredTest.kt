package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 11: multicoloured cards, first part. A keyword counter of your choice (CR 122.1b) and the same kinds put
 * on another creature, spells of each colour, an indestructible counter removed as a cost, a cost reduction by permanents sacrificed this
 * turn (CR 601.2f), blocking only by legendary creatures (CR 509.1b), a revealed creature put onto the battlefield attacking with keywords
 * by conditions, X targets from the cards scried, protection from each of a creature's colours (CR 702.16), a delayed trigger at the
 * chosen opponent's next end step (CR 603.7), two modes with a Wizard (CR 700.2), two creatures each dealing damage, must be blocked while
 * your Ring-bearer, and a revealed land put onto the battlefield.
 */
class LtrChunk11MulticoloredTest {
    // CR 122.1b: a keyword counter of your choice; the kinds just put, put again on another creature
    @Test
    fun arathornCompanyLeader() = card(
        name = "Arathorn, Company Leader",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Human Ranger",
        text = """
            Whenever the Ring tempts you, if you chose a creature other than Arathorn as your Ring-bearer, put your choice of a counter from among first strike, vigilance, deathtouch, and lifelink on Arathorn.
            Whenever you put one or more counters on Arathorn, put one of each of those kinds of counters on up to one other target creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(ringTemptsYou,
            Step.Choose(Choice.Option(listOf("first strike", "vigilance", "deathtouch", "lifelink")), bind = "counter", chooser = Players.You),
            Step.If(Condition.Chose("counter", "first strike"), then = listOf(counters(Objects.Self, kind = CounterKind.Keyword(Keyword.FirstStrike))), otherwise = listOf(
                Step.If(Condition.Chose("counter", "vigilance"), then = listOf(counters(Objects.Self, kind = CounterKind.Keyword(Keyword.Vigilance))), otherwise = listOf(
                    Step.If(Condition.Chose("counter", "deathtouch"), then = listOf(counters(Objects.Self, kind = CounterKind.Keyword(Keyword.Deathtouch))),
                        otherwise = listOf(counters(Objects.Self, kind = CounterKind.Keyword(Keyword.Lifelink)))))))),
            interveningIf = choseAnotherBearer),
        triggered(Trigger.On(listOf(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), causedBy = you)), oneOrMore = true),
            doing(Effect.PutEventCounterKinds(target("creature"))), targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    // CR 105.2: spells of each colour
    @Test
    fun elessarTheUniter() = card(
        name = "Elessar, the Uniter",
        cost = "{R}{G}{W}{U}",
        type = "Legendary Creature — Human Noble",
        text = """
            Whenever you cast a white spell, create a 1/1 white Human Soldier creature token.
            Whenever you cast a blue spell, scry 2.
            Whenever you cast a red spell, Elessar deals 3 damage to target opponent.
            Whenever you cast a green spell, target creature gets +4/+4 until end of turn.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.OfColor(Color.White)), create(humanSoldier)),
        triggered(youCast(ObjectFilter.OfColor(Color.Blue)), scry(2)),
        triggered(youCast(ObjectFilter.OfColor(Color.Red)), damage(3, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
        triggered(youCast(ObjectFilter.OfColor(Color.Green)), pump(target("creature"), 4, 4), targets = listOf(slot("creature", creature))),
    )

    // CR 122.1b: an indestructible counter as it enters, removed as a cost
    @Test
    fun luthienMortalQueen() = card(
        name = "Lúthien, Mortal Queen",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Lúthien enters with an indestructible counter on her.
            {1}, Remove an indestructible counter from Lúthien: Another target creature gains indestructible until end of turn. Put a +1/+1 counter and a lifelink counter on that creature and a +1/+1 counter and a lifelink counter on Lúthien.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(indestructible, n(1))))),
        activated(cost(mana("{1}"), CostPart.RemoveCounters(indestructible, n(1), CostObjects.Self)),
            gains(target("creature"), Keyword.Indestructible),
            counters(target("creature")), counters(target("creature"), kind = lifelink),
            counters(Objects.Self), counters(Objects.Self, kind = lifelink),
            targets = listOf(slot("creature", and(another, creature)))),
    )

    @Test
    fun idrilCelebrindal() = card(
        name = "Idril Celebrindal",
        cost = "{G}{U}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever you scry, put a +1/+1 counter on target creature.
            {4}{G}{U}: Scry 2.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(youScry, counters(target("creature")), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{4}{G}{U}")), scry(2)),
    )

    // CR 601.2f: {1} less for each permanent sacrificed this turn; CR 509.1b: blocked only by legendary creatures
    @Test
    fun theFireDemonDurinsBane() = card(
        name = "The Fire Demon, Durin's Bane",
        cost = "{5}{B}{R}",
        type = "Legendary Creature — Avatar Demon",
        text = """
            This spell costs {1} less to cast for each permanent sacrificed this turn.
            Haste
            The Fire Demon can't be blocked except by legendary creatures.
            When The Fire Demon dies, destroy target artifact or creature an opponent controls.
        """,
        pt = "7/5",
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!,
            Value.EventsThisTurn(EventPattern.Sacrifice(permanentFilter), arcanum.api.Measure.Objects)))),
        Keyword.Haste,
        static(Mechanic.CanBeBlockedOnlyBy(and(legendary, creature))),
        triggered(diesSelf, destroy(target("permanent")), targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
    )

    @Test
    fun bungoRetiredBurglar() = card(
        name = "Bungo, Retired Burglar",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Halfling Rogue",
        text = """
            When Bungo enters or leaves the battlefield, the Ring tempts you.
            Whenever Bungo deals combat damage to a player, create a Treasure token.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))), ringTempts),
        triggered(combatDamageToAPlayer, createTreasure()),
    )

    @Test
    fun nobBreeInnkeeper() = card(
        name = "Nob, Bree Innkeeper",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Human Peasant",
        text = """At the beginning of your end step, if you don't control a Food, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), createFood(), interveningIf = Condition.Not(Condition.Exists(and(sub("Food"), you), atLeast = 1))),
    )

    @Test
    fun ecthelionRulingSteward() = card(
        name = "Ecthelion, Ruling Steward",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Noble",
        text = """
            At the beginning of your end step, if a creature died under your control this turn, create a 1/1 white Human Soldier creature token.
            {2}, Sacrifice another creature: Each opponent loses 1 life and you gain 1 life.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), create(humanSoldier), interveningIf = creatureDiedUnderYourControl),
        activated(cost(mana("{2}"), sacrificeA(and(another, creature))), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 508.4: put onto the battlefield tapped and attacking; keywords until your next turn by conditions
    @Test
    fun gatesOfTheDwarfLord() = card(
        name = "Gates of the Dwarf Lord",
        cost = "{3}{R}{G}",
        type = "Legendary Artifact",
        text = "Whenever you attack, scry 2, then you may reveal the top card of your library. If a creature card is revealed this way, put it onto the battlefield tapped and attacking. Until your next turn, it gains trample if you control a Dwarf and hexproof if you control an Elf.",
    ) parsesTo abilities(
        triggered(youAttack, scry(2), Step.May(listOf(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed"))), Players.You),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), creature), then = listOf(
                doing(Effect.Move(Objects.Bound("revealed"), arcanum.api.Destination(Zone.Battlefield, tapped = true, attacking = arcanum.api.AttackTarget.Chosen), postBind = "put")),
                Step.If(Condition.Exists(and(sub("Dwarf"), you), atLeast = 1), then = listOf(doing(Effect.ApplyMechanics(listOf(has(Keyword.Trample)),
                    Affected.Objects(Objects.Bound("put")), Duration.UntilYourNextTurn)))),
                Step.If(Condition.Exists(and(sub("Elf"), you), atLeast = 1), then = listOf(doing(Effect.ApplyMechanics(listOf(has(Keyword.Hexproof)),
                    Affected.Objects(Objects.Bound("put")), Duration.UntilYourNextTurn))))))),
    )

    // CR 701.22: X targets from the cards looked at; becoming the target of an opponent's spell or ability (CR 115.1)
    @Test
    fun elrosMasterOfHealing() = card(
        name = "Elros, Master of Healing",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever you scry, put a +1/+1 counter on each of up to X target creatures, where X is the number of cards looked at while scrying this way.
            Whenever a creature you control with a +1/+1 counter on it becomes the target of a spell or ability an opponent controls, you may draw a card.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(youScry, counters(target("creatures")), targets = listOf(arcanum.api.TargetSlot("creatures", arcanum.api.TargetKind.Objects(creature),
            min = n(0), max = Value.EventAmount))),
        triggered(on(EventPattern.BecomesTarget(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)), opponents)),
            Step.May(listOf(draw(1)), Players.You)),
    )

    // CR 702.16: protection from each of that creature's colours
    @Test
    fun theodwynFearlessKnight() = card(
        name = "Théodwyn, Fearless Knight",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Knight",
        text = """
            Haste
            When Théodwyn enters, exile target creature an opponent controls with greater power. Legendary creatures you control gain protection from each of that creature's colors until end of turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, move(target("creature"), Zone.Exile, postBind = "exiled"),
            untilEndOfTurn(Objects.Each(and(legendary, creature, you)), has(Keyword.Protection(ObjectFilter.Shares(arcanum.api.SharedQuality.Color, Objects.Bound("exiled"))))),
            targets = listOf(slot("creature", and(creature, opponents, ObjectFilter.Power(Comparison.Greater, power(Objects.Self)))))),
    )

    // CR 603.7: a delayed trigger at the chosen opponent's next end step
    @Test
    fun damrodPrinceOfIthilien() = card(
        name = "Damrod, Prince of Ithilien",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Human Noble",
        text = "At the beginning of your end step, choose an opponent. At the beginning of that player's next end step, you draw a card if they didn't attack you that turn. Otherwise, create three 1/1 white Human Soldier creature tokens.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Is(Players.Bound("opponent")))), Instructions(steps = listOf(
                Step.If(Condition.Not(Condition.HappenedThisTurn(EventPattern.Attack(ObjectFilter.Any, target = RecipientFilter(players = PlayerFilter.You)), atLeast = 1,
                    measure = arcanum.api.Measure.Events)), then = listOf(draw(1)), otherwise = listOf(create(humanSoldier, n(3)))))), laterTurn = true))),
    )

    // CR 700.2: two modes with a Wizard as it is cast
    @Test
    fun flameOfTheSecretFire() = card(
        name = "Flame of the Secret Fire",
        cost = "{1}{U}{R}",
        type = "Instant",
        text = """
            Choose one. If you control a Wizard as you cast this spell, you may choose two instead.
            • Target player draws two cards.
            • Destroy target artifact.
            • Flame of the Secret Fire deals 5 damage to target creature.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(draw(2, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            mode(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature)))),
            min = 1, max = 1, chooser = Players.You, maxIf = Condition.Exists(and(sub("Wizard"), you), atLeast = 1), maxIfCount = 2, upToMaxIf = true))),
    )

    // CR 120.3: each of the two deals damage equal to its power; CR 115.3: "other target" is a different creature from the first target
    @Test
    fun friendlyContest() = card(
        name = "Friendly Contest",
        cost = "{R}{G}",
        type = "Instant",
        text = "Target creature you control and up to one other target legendary creature you control each deal damage equal to their power to target creature you don't control.",
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(target("third creature")), source = target("creature")),
            damage(power(target("second creature")), toObjects(target("third creature")), source = target("second creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(legendary, creature, you), min = 0).copy(distinctFrom = listOf("creature")),
                slot("third creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    // CR 509.1c: must be blocked while it is your Ring-bearer
    @Test
    fun drogoBaggins() = card(
        name = "Drogo Baggins",
        cost = "{G}{W}",
        type = "Legendary Creature — Halfling Scout",
        text = """
            Whenever Drogo Baggins or another legendary creature you control enters, the Ring tempts you.
            As long as Drogo Baggins is your Ring-bearer, it must be blocked if able.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, legendary, creature, you))), ringTempts),
        static(Mechanic.MustBeBlocked, condition = Condition.ObjectMatches(Objects.Self, yourRingBearer)),
    )

    // CR 701.20: a revealed land put onto the battlefield tapped
    @Test
    fun celebrianOfLothlorien() = card(
        name = "Celebrían of Lothlórien",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever the Ring tempts you, if you chose a creature other than Celebrían as your Ring-bearer, scry 3.
            Whenever you scry, you may reveal the top card of your library. If a land card is revealed this way, put it onto the battlefield tapped.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(ringTemptsYou, scry(3), interveningIf = choseAnotherBearer),
        triggered(youScry, Step.May(listOf(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed"))), Players.You),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), land), then = listOf(move(Objects.Bound("revealed"), Zone.Battlefield, tapped = true)))),
    )

    private val indestructible = CounterKind.Keyword(Keyword.Indestructible)
    private val lifelink = CounterKind.Keyword(Keyword.Lifelink)
    @Suppress("unused") private val unused = listOf(EventRole.Object)
}
