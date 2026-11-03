package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 06: Lander tokens (CR 111.10u) made by spells, triggers and an opponent's choice, a token sacrificed on your next turn, excess damage, the controller of a destroyed permanent, and "each creature you control enters with an additional counter".
 */
class EoeChunk06LandersTest {

    @Test
    fun lasercutSurveyor() = card(
        name = "Lasercut Surveyor",
        cost = "{1}{B}",
        type = "Creature — Human Artificer",
        pt = "2/1",
        text = "When this creature dies, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        triggered(diesSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
    )

    @Test
    fun geneTailoredTomorrow() = card(
        name = "Gene-Tailored Tomorrow",
        cost = "{1}{G}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
            Each creature you control enters with an additional +1/+1 counter on it for each land that entered the battlefield under your control this turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = and(creature, you), to = Zone.Battlefield),
                modify = listOf(
                    Modification.EntersWithCounters(
                        kind = p1p1,
                        amount = Value.EventsThisTurn(
                            event = EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield),
                            measure = Measure.Objects,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun bioforgeMachinist() = card(
        name = "Bioforge Machinist",
        cost = "{G}{U}",
        type = "Creature — Insect Artificer",
        pt = "2/2",
        text = """
            When this creature enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
            {8}: Draw two cards and create a 2/2 colorless Robot artifact creature token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
        activated(
            cost(mana("{8}")),
            doing(Effect.Draw(players = Players.You, count = n(2))),
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun geneLabExpert() = card(
        name = "Gene-Lab Expert",
        cost = "{R}{G}",
        type = "Creature — Insect Scientist",
        pt = "1/3",
        text = """
            When this creature enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
            Whenever you sacrifice an artifact, this creature deals 2 damage to target opponent.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
        triggered(
            on(EventPattern.Sacrifice(objects = and(artifact, you))),
            doing(Effect.DealDamage(to = Recipients(players = Players.Target(slot = "opponent")), amount = n(2), source = Objects.Self)),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )

    @Test
    fun fearlessSalvagebot() = card(
        name = "Fearless Salvagebot",
        cost = "{3}",
        type = "Artifact Creature — Robot",
        pt = "3/1",
        text = "When this creature enters, exile each opponent's graveyard. Create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.Move(
                    objects = Objects.Each(filter = and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(players = PlayerFilter.Opponent))),
                    to = Destination(zone = Zone.Exile),
                ),
            ),
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun avertCatastrophe() = card(
        name = "Avert Catastrophe",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {2}. If they do, you create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        spell(
            Step.Pay(cost = cost(mana("{2}")), payer = Players.ControllerOf(objects = target("spell")), bind = "paid"),
            Step.If(
                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                then = listOf(doing(Effect.Counter(objects = target("spell")))),
            ),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
            ),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    @Test
    fun frontierCrawler() = card(
        name = "Frontier Crawler",
        cost = "{G}",
        type = "Artifact Creature — Robot Scout",
        pt = "2/2",
        text = """
            Reach
            When this creature dies, each player creates a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(diesSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.Each(filter = PlayerFilter.Any)))),
    )

    @Test
    fun escapePodLaunch() = card(
        name = "Escape Pod Launch",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target nonland permanent. Its controller creates a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(objects = target("permanent"))),
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.ControllerOf(objects = target("permanent")))),
            targets = listOf(slot("permanent", and(not(land), permanentFilter))),
        ),
    )

    @Test
    fun starlaneVoyager() = card(
        name = "Starlane Voyager",
        cost = "{2}{G}",
        type = "Creature — Human Scout",
        pt = "3/3",
        text = "When this creature enters, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
    )

    @Test
    fun icefieldWorldmaw() = card(
        name = "Icefield Worldmaw",
        cost = "{5}{G}{G}",
        type = "Creature — Leviathan",
        pt = "6/6",
        text = """
            Trample
            When this creature enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
            Landfall — Whenever a land you control enters, creatures you control get +1/+1 and gain vigilance and haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            untilEndOfTurn(Objects.Each(filter = and(creature, you)), pt(1, 1), has(Keyword.Vigilance, Keyword.Haste)),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun kavuGroundfinder() = card(
        name = "Kavu Groundfinder",
        cost = "{3}{R}",
        type = "Creature — Kavu Soldier",
        pt = "4/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, create a Lander token. At the beginning of the end step on your next turn, sacrifice that token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(
            entersSelf,
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You, bind = "token")),
            doing(
                Effect.CreateDelayedTrigger(
                    trigger = on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
                    instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("token"))))),
                    laterTurn = true,
                ),
            ),
        ),
    )

    @Test
    fun hardTouchdown() = card(
        name = "Hard Touchdown",
        cost = "{2}{R}",
        type = "Instant",
        text = "Create a Lander token. Then you may sacrifice an artifact. When you do, Hard Touchdown deals 2 damage to each creature. (A Lander token is an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        spell(
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You)),
            Step.Pay(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = artifact, count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(
                    doing(
                        Effect.ReflexiveTrigger(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.DealDamage(
                                            to = Recipients(objects = Objects.Each(filter = creature)),
                                            amount = n(2),
                                            source = Objects.Self,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun reentryDive() = card(
        name = "Reentry Dive",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Reentry Dive deals 6 damage to target creature. If excess damage was dealt this way, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(6), source = Objects.Self, bind = "damage")),
            Step.If(
                condition = Condition.Compare(left = Value.Excess(name = "damage"), comparison = Comparison.Greater, right = n(0)),
                then = listOf(doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun orlosWanderlust() = card(
        name = "Orlo's Wanderlust",
        cost = "{G}",
        type = "Sorcery",
        text = "You gain 2 life. Create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        spell(
            doing(Effect.GainLife(players = Players.You, amount = n(2))),
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun scavengeForForever() = card(
        name = "Scavenge for Forever",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Return target creature or Spacecraft card with mana value 5 or less from your graveyard to the battlefield. Then create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        """,
    ) parsesTo abilities(
        static(
            Mechanic.AdditionalCost(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = or(artifact, creature), count = n(1)))),
            ),
        ),
        spell(
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield))),
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You)),
            targets = listOf(
                slot(
                    "card",
                    and(
                        or(creature, sub("Spacecraft")),
                        ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(5)),
                        inZone(Zone.Graveyard),
                        ownedByYou,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun sowingArkCultivator() = card(
        name = "Sowing-Ark Cultivator",
        cost = "{3}{G}",
        type = "Creature — Insect Scientist",
        pt = "3/3",
        text = """
            Whenever this creature becomes tapped, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
            Landfall — Whenever a land you control enters, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(objects = isSelf)), doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun sowingArkCollision() = card(
        name = "Sowing-Ark Collision",
        cost = "{1}{G}",
        type = "Instant",
        text = "Destroy target artifact or enchantment. If its mana value was 2 or less, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(objects = target("permanent"))),
            Step.If(
                condition = Condition.ObjectMatches(
                    objects = target("permanent"),
                    filter = ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2)),
                ),
                then = listOf(doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You))),
            ),
            targets = listOf(slot("permanent", or(artifact, enchantment))),
        ),
    )

    @Test
    fun daystarFrontierist() = card(
        name = "Daystar Frontierist",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        pt = "2/3",
        text = """
            When this creature enters, if an opponent controls more lands than you, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
            Landfall — Whenever a land you control enters, this creature gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.CreateToken(token = lander, count = n(1), controller = Players.You)),
            interveningIf = Condition.PlayerExists(
                filter = PlayerFilter.And(
                    all = listOf(
                        PlayerFilter.Opponent,
                        PlayerFilter.Where(
                            condition = Condition.Compare(
                                left = Value.Count(
                                    objects = Objects.Each(
                                        filter = and(land, ObjectFilter.ControlledBy(players = PlayerFilter.Is(players = Players.Tested))),
                                    ),
                                ),
                                comparison = Comparison.Greater,
                                right = Value.Count(objects = Objects.Each(filter = and(land, you))),
                            ),
                        ),
                    ),
                ),
                atLeast = 1,
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            untilEndOfTurn(Objects.Self, pt(1, 0)),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun groundtreatyEnforcer() = card(
        name = "Groundtreaty Enforcer",
        cost = "{1}{R}",
        type = "Creature — Kavu Scout",
        pt = "2/1",
        text = "When this creature enters, target opponent may have you create two Lander tokens. If they don't, put two +1/+1 counters on this creature. (A Lander token is an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Pay(
                steps = listOf(doing(Effect.CreateToken(token = lander, count = n(2), controller = Players.You))),
                payer = Players.Target(slot = "opponent"),
                bind = "had",
            ),
            Step.If(
                condition = Condition.Not(condition = Condition.Did(name = "had")),
                then = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(2)))),
            ),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )
}
