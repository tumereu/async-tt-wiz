package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 14: multicoloured cards: an extra combat after a Vehicle it crewed attacks (CR 500.8), cards looked at by the number of
 * attackers, lore counters removed for +1/+1 counters, artifact-count thresholds, a land put from the top six, a Mutant made and drawn for,
 * damage to a player and their permanents doubled until your next turn (CR 614.1a), casting artifacts from the graveyard for life, one target
 * per opponent (CR 115.1), a random card copied and the process repeated, lands entering untapped, an all-colours token (CR 105.2), keywords
 * during your turn, gaining control of a permanent from you.
 */
class FinChunk14MulticoloredTest {
    // CR 702.122, 500.8
    @Test
    fun baltherAndFrin() = card(
        name = "Balther and Frin",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Human Rabbit",
        pt = "4/3",
        text = """
            Reach
            Vehicles you control get +1/+1 and have reach and vigilance.
            Whenever a Vehicle crewed by Balther and Frin this turn attacks, if it's the first combat phase of the turn, you may pay {1}{R}{G}. If you do, after this phase, there is an additional combat phase.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        static(pt(1, 1), has(Keyword.Reach, Keyword.Vigilance), affects = Objects.Each(and(sub("Vehicle"), you))),
        triggered(on(EventPattern.Attack(and(sub("Vehicle"), ObjectFilter.CrewedBy(Objects.Self)))),
            Step.Pay(cost(mana("{1}{R}{G}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.ExtraCombat())),
            interveningIf = firstCombat),
    )

    @Test
    fun chocoboSeeker() = card(
        name = "Striderbird, Seeker of Havens",
        cost = "{1}{G}{W}{U}",
        type = "Legendary Creature — Bird",
        pt = "3/5",
        text = """
            Whenever one or more Birds you control attack, look at that many cards from the top of your library. You may put one of them into your hand. Then put any number of land cards from among them onto the battlefield tapped and the rest into your graveyard.
            Landfall — Whenever a land you control enters, Striderbird gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(and(sub("Bird"), you)), oneOrMore = true),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Choose(Choice.Objects(land, n(0), null, from = Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen"))), bind = "lands", chooser = Players.You),
            move(Objects.Bound("lands"), Zone.Battlefield, tapped = true),
            move(Objects.Except(Objects.Bound("seen"), Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("lands")))), Zone.Graveyard)),
        triggered(landfall, pump(Objects.Self, 1, 0), abilityWord = "Landfall"),
    )

    // CR 714.3b (removing lore counters)
    @Test
    fun garnaPrincessOfTheMist() = card(
        name = "Garna, Princess of the Mist",
        cost = "{G}{W}",
        type = "Legendary Creature — Human Noble Cleric",
        pt = "2/2",
        text = """
            Lifelink
            Whenever Garna attacks, you may remove a lore counter from each of any number of Sagas you control. Put a +1/+1 counter on Garna for each lore counter removed this way.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(saga, you), n(0), null), bind = "sagas", chooser = Players.You),
            doing(Effect.RemoveCounters(Objects.Bound("sagas"), lore, n(1), bind = "removed")),
            counters(Objects.Self, Value.Bound("removed"))),
    )

    @Test
    fun golbarCrystalHoarder() = card(
        name = "Golbar, Crystal Hoarder",
        cost = "{U}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/4",
        text = """
            Whenever an artifact you control enters, surveil 1.
            At the beginning of your end step, if you control four or more artifacts, return target creature card from your graveyard to your hand. Then if you control eight or more artifacts, each opponent loses life equal to that card's power.
        """,
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), surveil(1)),
        triggered(yourEndStep, move(target("card"), Zone.Hand),
            ifThen(Condition.Exists(and(artifact, you), atLeast = 8), loseLife(0, Players.Each(PlayerFilter.Opponent)).let {
                doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), power(target("card"))))
            }),
            targets = listOf(slot("card", inYourGraveyard(creature))), interveningIf = Condition.Exists(and(artifact, you), atLeast = 4)),
    )

    @Test
    fun ignatiusChef() = card(
        name = "Ignatius, Camp Chef",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/2",
        text = """
            When Ignatius enters, look at the top six cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest on the bottom of your library in a random order.
            I've Come Up with a New Recipe! — {1}{G}{U}, {T}: Exile target card from a graveyard. If a creature card was exiled this way, create a Food token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        activated(cost(mana("{1}{G}{U}"), tap), move(target("card"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), creature), createFood), targets = listOf(slot("card", inZone(Zone.Graveyard))))
            .copy(abilityWord = "I've Come Up with a New Recipe!"),
    )

    @Test
    fun jenaraAncientCalamity() = card(
        name = "Jenara, Ancient Calamity",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Alien",
        pt = "1/5",
        text = """
            At the beginning of combat on your turn, put a number of +1/+1 counters equal to Jenara's power on up to one other target creature. That creature becomes a Mutant in addition to its other types.
            Whenever a Mutant you control dies during your turn, you draw cards equal to its power.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(target("creature"), power(Objects.Self)),
            doing(Effect.ApplyMechanics(listOf(alsoA("Mutant")), Affected.Objects(target("creature")), Duration.Indefinitely)),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
        Ability.Triggered(on(EventPattern.ZoneChange(and(sub("Mutant"), you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Instructions(steps = listOf(doing(Effect.Draw(Players.You, power(leftOne))))), triggersOnlyIf = Condition.YourTurn),
    )

    /** "until your next turn, if a source would deal damage to that player or a permanent that player controls, it deals double that damage instead" */
    // CR 614.1a
    @Test
    fun lightningLegionOfOne() = card(
        name = "Lightfall, Legion of One",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/2",
        text = """
            First strike, trample, lifelink
            Stagger — Whenever Lightfall deals combat damage to a player, until your next turn, if a source would deal damage to that player or a permanent that player controls, it deals double that damage instead.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike, Keyword.Trample, Keyword.Lifelink,
        triggered(combatDamageToAPlayer, doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any,
            RecipientFilter(objects = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer)), players = PlayerFilter.Is(Players.EventPlayer))),
            modify = listOf(Modification.MultiplyAmount(2)))), null, Duration.UntilYourNextTurn)), abilityWord = "Stagger"),
    )

    // CR 601.2b, 601.2f, 122.1h
    @Test
    fun noctiaPrinceOfLight() = card(
        name = "Noctia, Prince of Light",
        cost = "{1}{W}{U}{B}",
        type = "Legendary Creature — Human Noble",
        pt = "4/3",
        text = """
            Lifelink
            You may cast artifact spells from your graveyard by paying 3 life in addition to paying their other costs. If you cast a spell this way, that artifact enters with a finality counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You, additionalCost = cost(CostPart.PayLife(n(3))),
            spellGets = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(finality, n(1)))))),
            affects = Objects.Each(inYourGraveyard(artifact))),
    )

    /** "for each opponent, tap up to one target nonland permanent that opponent controls" (one slot per opponent, CR 115.1) */
    @Test
    fun omicronHeartlessEvolution() = card(
        name = "Omicron, Heartless Evolution",
        cost = "{5}{G}{U}",
        type = "Legendary Artifact Creature — Robot",
        pt = "8/8",
        text = "Wave Cannon — When Omicron enters, for each opponent, tap up to one target nonland permanent that opponent controls. Put X stun counters on each of those permanents and you gain X life, where X is the number of nonbasic lands you control. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("permanent")), counters(target("permanent"), nonbasic, stun), doing(Effect.GainLife(Players.You, nonbasic)),
            targets = listOf(slot("permanent", and(nonlandPermanent, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer))), min = 0)
                .copy(perPlayer = PlayerFilter.Opponent)), abilityWord = "Wave Cannon"),
    )

    /** "exile a permanent card from your graveyard at random, then create a tapped token that's a copy of that card. If the exiled card is a land
     * card, repeat this process." (CR 608.2c) */
    @Test
    fun synScourgeOfTheSea() = card(
        name = "Synn, Scourge of the Sea",
        cost = "{4}{B}{G}{U}",
        type = "Legendary Creature — Leviathan Avatar",
        pt = "7/7",
        text = """
            Flying
            Whenever Synn enters or attacks, exile a permanent card from your graveyard at random, then create a tapped token that's a copy of that card. If the exiled card is a land card, repeat this process.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersOrAttacksSelf, Step.RepeatWhile(listOf(
            move(Objects.Random(Objects.Each(inYourGraveyard(permanentCard)), n(1)), Zone.Exile, postBind = "exiled"),
            doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("exiled")), n(1), Players.You, tapped = true)),
        ), Condition.ObjectMatches(Objects.Bound("exiled"), land))),
    )

    // CR 105.2c (a token that's all colors)
    @Test
    fun theWanderingBard() = card(
        name = "The Wandering Bard",
        cost = "{G}{U}",
        type = "Legendary Creature — Human Bard",
        pt = "1/3",
        text = """
            Lands you control enter untapped.
            The Minstrel's Ballad — At the beginning of combat on your turn, if you control five or more Towns, create a 2/2 Elemental creature token that's all colors.
            {3}{W}{U}{B}{R}{G}: Other creatures you control get +X/+X until end of turn, where X is the number of Towns you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield), modify = listOf(Modification.EntersUntapped))),
        triggered(beginningOfCombatOnYourTurn, create(creatureToken("Elemental", listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green), 2, 2)),
            interveningIf = Condition.Exists(and(town, you), atLeast = 5), abilityWord = "The Minstrel's Ballad"),
        activated(manaCost("{3}{W}{U}{B}{R}{G}"), pump(Objects.Each(and(another, creature, you)), 0, 0).let {
            untilEndOfTurn(Objects.Each(and(another, creature, you)), Mechanic.ModifyPowerToughness(count(and(town, you)), count(and(town, you))))
        }),
    )

    @Test
    fun yunelleHopeOfTheIsles() = card(
        name = "Yunelle, Hope of the Isles",
        cost = "{3}{G}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "3/5",
        text = """
            During your turn, Yunelle and enchantment creatures you control have trample, lifelink, and ward {2}.
            At the beginning of your end step, return up to one target enchantment card from your graveyard to the battlefield with a finality counter on it. (If a permanent with a finality counter on it would be put into a graveyard from the battlefield, exile it instead.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Trample, Keyword.Lifelink, Keyword.Ward(manaCost("{2}"))), affects = Objects.Union(listOf(Objects.Self,
            Objects.Each(and(enchantment, creature, you)))), condition = Condition.YourTurn),
        triggered(yourEndStep, doing(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(finality, n(1)))))),
            targets = listOf(slot("card", inYourGraveyard(enchantment), min = 0))),
    )

    // CR 613.1b
    @Test
    fun zidrenTantalusThief() = card(
        name = "Zidren, Troupe Thief",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — Human Mutant Scout",
        pt = "3/3",
        text = """
            When Zidren enters, gain control of target creature an opponent controls until end of turn. Untap it. It gains lifelink and haste until end of turn.
            Whenever an opponent gains control of a permanent from you, you create a Treasure token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            untapIt(target("creature")), gains(target("creature"), Keyword.Lifelink, Keyword.Haste),
            targets = listOf(slot("creature", creatureOpponentControls))),
        triggered(on(EventPattern.ControlChange(permanentFilter, from = PlayerFilter.You, to = PlayerFilter.Opponent)), createTreasure),
    )

    private val firstCombat = Condition.Compare(Value.EventsThisTurn(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), Measure.Events),
        Comparison.AtMost, n(1))
    private val nonbasic = count(and(not(basic), land, you))
}
