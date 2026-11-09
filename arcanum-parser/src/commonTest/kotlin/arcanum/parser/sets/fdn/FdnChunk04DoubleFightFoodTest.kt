package arcanum.parser.sets.fdn

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Doubled
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN affinity, doubling, fight and Food cards (chunk 04). The invented names carry the oracle text of the real cards. */
class FdnChunk04DoubleFightFoodTest {
    private val gate = sub("Gate")
    private val notYours = ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))
    private fun double(objects: Objects, kind: CounterKind?) = Step.Do(Effect.Action(KeywordAction.Double(Doubled.Counters(objects, kind))))
    private val damageDoubled = listOf(Modification.MultiplyAmount(2))
    private val raid = Condition.HappenedThisTurn(EventPattern.Attack(you), atLeast = 1, measure = Measure.Events)

    // CR 702.41a, 509.1b, 113.6m: the last ability works while this card is in the graveyard
    @Test
    fun gateholdColossus() = card(
        name = "Gatehold Colossus",
        cost = "{8}",
        type = "Artifact Creature — Construct",
        pt = "8/8",
        text = """
            Affinity for Gates (This spell costs {1} less to cast for each Gate you control.)
            This creature can't be blocked by creatures with power 2 or less.
            Whenever a Gate you control enters, you may put this card from your graveyard on top of your library.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(gate, "Gates"),
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
        triggered(enters(and(gate, you)),
            Step.May(listOf(move(Objects.Self, Zone.Library, position = 0)), player = Players.You),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 702.41a
    @Test
    fun purringRally() = card(
        name = "Purring Rally",
        cost = "{3}{W}{W}",
        type = "Instant",
        text = """
            Affinity for Cats (This spell costs {1} less to cast for each Cat you control.)
            Creatures you control get +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Cat"), "Cats"),
        spell(pump(Objects.Each(creatureYouControl), 2, 2)),
    )

    // CR 601.2d, 701.10e: the counters are divided as the spell is cast; the doubling comes after they are put
    @Test
    fun graftbloomUpgrade() = card(
        name = "Graftbloom Upgrade",
        cost = "{4}{G}{G}",
        type = "Sorcery",
        text = "Distribute three +1/+1 counters among one, two, or three target creatures, then double the number of +1/+1 counters on each of those creatures.",
    ) parsesTo abilities(
        spell(
            doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
            double(target("creatures"), p1p1),
            targets = listOf(slot("creatures", creature, min = 1, max = 3).copy(divided = n(3))),
        ),
    )

    // CR 701.10b: +X/+0, X its power as the spell resolves
    @Test
    fun swellOfStrength() = card(
        name = "Swell of Strength",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Double target creature's power until end of turn.
            Flashback {4}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), n(0))),
            targets = listOf(slot("creature", creature)),
        ),
        Keyword.Flashback(manaCost("{4}{R}{R}")),
    )

    // CR 701.10e, 614.1c: "this creature" is this permanent, not the land that entered
    @Test
    fun sproutingHydra() = card(
        name = "Sprouting Hydra",
        cost = "{2}{G}",
        type = "Creature — Elemental Hydra",
        pt = "0/0",
        text = """
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
            This creature enters with a +1/+1 counter on it.
            Landfall — Whenever a land you control enters, double the number of +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
        triggered(landfall, double(Objects.Self, p1p1), abilityWord = "Landfall"),
    )

    // CR 614.1a, 120.3: only damage to an opponent or an opponent's permanent is doubled
    @Test
    fun forkflameTyrant() = card(
        name = "Forkflame Tyrant",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        pt = "3/5",
        text = """
            Flying
            If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals double that damage instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(
            EventPattern.Damage(you, RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent)),
            modify = damageDoubled,
        )),
    )

    // CR 601.2c, 701.10e: "up to two", each kind of counter
    @Test
    fun odessaCounterSculptor() = card(
        name = "Odessa, Counter Sculptor",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/4",
        text = """
            At the beginning of combat on your turn, put a +1/+1 counter on each of up to two target creatures you control.
            {G}{U}, {T}: Double the number of each kind of counter on up to two target creatures and/or artifacts you control.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(target("creatures")),
            targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
        activated(cost(mana("{G}{U}"), tap), double(target("artifacts"), null),
            targets = listOf(slot("artifacts", and(or(creature, artifact), you), min = 0, max = 2))),
    )

    // CR 614.1a: all damage a creature you control deals
    @Test
    fun savageExcess() = card(
        name = "Savage Excess",
        cost = "{2}{R}{R}{R}",
        type = "Enchantment",
        text = "If a creature you control would deal damage to a permanent or player, it deals double that damage instead.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(
            EventPattern.Damage(creatureYouControl, RecipientFilter(objects = permanentFilter, players = PlayerFilter.Any)),
            modify = damageDoubled,
        )),
    )

    // CR 701.14a, 603.5: "it" is the creature whose entering triggered this
    @Test
    fun quarrelsomeIndrik() = card(
        name = "Quarrelsome Indrik",
        cost = "{5}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = "When this creature enters, you may have it fight target creature you don't control. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(doing(Effect.Fight(Objects.EventObject(EventRole.After), target("creature")))), player = Players.You),
            targets = listOf(slot("creature", and(creature, notYours)))),
    )

    // CR 700.2, 701.14a
    @Test
    fun thornwhack() = card(
        name = "Thornwhack",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            • Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(
        arcanum.api.Ability.Spell(Instructions(modes = modes(
            mode(
                search(basicLand(), reveal = true),
                move(Objects.Bound("found"), Zone.Hand),
                shuffle,
            ),
            mode(
                doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, notYours))),
            ),
        ))),
    )

    // CR 107.3, 701.14a, 608.2b: "up to one" may be no fight; the pump still happens
    @Test
    fun overgrownMight() = card(
        name = "Overgrown Might",
        cost = "{X}{G}",
        type = "Sorcery",
        text = "Target creature you control gets +X/+X until end of turn. Then it fights up to one target creature you don't control. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Announced("X"), Value.Announced("X"))),
            doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, notYours), min = 0, max = 1)),
            announced = listOf("X"),
        ),
    )

    // CR 603.2, 111.10b: the first life gain of a turn that is yours
    @Test
    fun tabbyKeeper() = card(
        name = "Tabby Keeper",
        cost = "{2}{W}",
        type = "Creature — Human Citizen",
        pt = "3/2",
        text = """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you gain life for the first time during each of your turns, create a 1/1 white Cat creature token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(food)),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.GainLife(PlayerFilter.You)), nthThisTurn = 1),
            create(creatureToken("Cat", listOf(arcanum.api.Color.White), 1, 1)),
            triggersOnlyIf = Condition.YourTurn),
    )

    // CR 111.10b
    @Test
    fun bakedComeuppance() = card(
        name = "Baked Comeuppance",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = """Destroy target creature. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(destroy(target("creature")), create(food), targets = listOf(slot("creature", creature))),
    )

    // CR 510.2, 111.10b
    @Test
    fun hungryTrufflehog() = card(
        name = "Hungry Trufflehog",
        cost = "{2}{G}",
        type = "Creature — Boar",
        pt = "4/2",
        text = """
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
            Whenever this creature deals combat damage to a player, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(combatDamageToAPlayer, create(food)),
    )

    // CR 603.4 (Raid is an intervening "if"), 608.2h
    @Test
    fun moonlitMorsel() = card(
        name = "Moonlit Morsel",
        cost = "{2}{B}",
        type = "Enchantment",
        text = """
            Raid — At the beginning of your end step, if you attacked this turn, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {2}{B}, Sacrifice this enchantment: Target opponent loses X life, where X is the amount of life you gained this turn.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), create(food), interveningIf = raid, abilityWord = "Raid"),
        activated(cost(mana("{2}{B}"), sacrificeSelf),
            doing(Effect.LoseLife(Players.Target("opponent"), Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount))),
            targets = listOf(targetOpponent)),
    )
}
