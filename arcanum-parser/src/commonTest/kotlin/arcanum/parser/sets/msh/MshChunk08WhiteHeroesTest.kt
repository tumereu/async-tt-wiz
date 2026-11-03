package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 08: white Heroes. Hero anthems and counters; "if you attacked with a Hero this turn or a Hero entered
 * … this turn" (CR 603.4); a shield counter (CR 122.1c) giving you and other Heroes hexproof (CR 702.11c, d); +X/+X by its
 * toughness; casting spells as though they had flash while an opponent has cast one (CR 702.8a); counters put by you on Heroes
 * (one trigger for the batch); a card put into your graveyard from anywhere this turn; a player who investigates (CR 701.16a).
 */
class MshChunk08WhiteHeroesTest {
    @Test
    fun directorPhilCorbin() = card(
        name = "Handler Phil Corbin",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Spy Hero",
        text = """
            Vigilance
            {T}: Put a +1/+1 counter on each other Hero you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(tap), counters(Objects.Each(and(another, hero, you)))),
    )

    // CR 603.4: either thing this turn, checked as it triggers and as it resolves
    @Test
    fun guardiansAssemble() = card(
        name = "Guardians, Rally!",
        cost = "{4}{W}",
        type = "Enchantment",
        text = """
            Flash
            Heroes you control get +2/+2.
            At the beginning of each end step, if you attacked with a Hero this turn or a Hero entered the battlefield under your control this turn, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(pt(2, 2), affects = Objects.Each(and(hero, you))),
        triggered(stepBegins(arcanum.api.TurnStep.End, PlayerFilter.Any), draw(1), interveningIf = Condition.Or(listOf(
            Condition.HappenedThisTurn(EventPattern.Attack(and(hero, you)), atLeast = 1, measure = Measure.Events),
            Condition.HappenedThisTurn(EventPattern.ZoneChange(and(hero, you), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)))),
    )

    @Test
    fun boroughReinforcements() = card(
        name = "Borough Reinforcements",
        cost = "{4}{W}",
        type = "Sorcery",
        text = """
            Create two 3/2 white Hero creature tokens with vigilance.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Hero", listOf(Color.White), 3, 2, abilities = listOf(Keyword.Vigilance)), n(2))),
        Keyword.Typecycling(basicLand(), "Basic landcycling", manaCost("{2}")),
    )

    // CR 122.1c: a shield counter; CR 702.11c, d: a player and permanents with hexproof (two static abilities of one line)
    @Test
    fun commanderLibertySuperSoldier() = card(
        name = "Commander Liberty, Super-Soldier",
        cost = "{1}{W}{W}",
        type = "Legendary Creature — Human Soldier Hero",
        text = """
            First strike
            Commander Liberty enters with a shield counter on him. (If he would be dealt damage or destroyed, remove a shield counter from him instead.)
            As long as Commander Liberty has a shield counter on him, you and other Heroes you control have hexproof.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(CounterKind.Shield, n(1))))),
        playerStatic(Mechanic.CantBeTargeted(by = opponents), condition = shielded),
        static(has(Keyword.Hexproof), affects = Objects.Each(and(another, hero, you)), condition = shielded),
    )

    // +X/+X with X its toughness as the ability resolves (CR 608.2h)
    @Test
    fun commanderLibertyWingsOfFreedom() = card(
        name = "Commander Liberty, Wings of Valor",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Soldier Hero",
        text = """
            Flying, first strike, ward {1}
            Whenever Commander Liberty attacks, each other Hero you control gets +X/+X until end of turn, where X is Commander Liberty's toughness.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        Keyword.Ward(manaCost("{1}")),
        triggered(attacksSelf, untilEndOfTurn(Objects.Each(and(another, hero, you)), Mechanic.ModifyPowerToughness(toughness(Objects.Self), toughness(Objects.Self)))),
    )

    // CR 702.8a: casting as though it had flash; a flavor word (CR 207.2d)
    @Test
    fun starwardenSpaceBorn() = card(
        name = "Starwarden, Space-Born",
        cost = "{4}{W}",
        type = "Legendary Creature — Kree Soldier Hero",
        text = """
            Flying, vigilance
            Cosmic Awareness — As long as an opponent has cast a spell this turn, you may cast spells as though they had flash.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Static(listOf(Mechanic.CastAsThoughFlash(ObjectFilter.Any)), affects = arcanum.api.Affected.Players(Players.You),
            condition = Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events),
            abilityWord = "Cosmic Awareness"),
    )

    @Test
    fun heroInTheMaking() = card(
        name = "Hero in the Making",
        cost = "{2}{W}",
        type = "Creature — Human Hero",
        text = "When this creature enters, draw a card. If you control another Hero, you gain 2 life.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), Step.If(Condition.Exists(and(another, hero, you), atLeast = 1), then = listOf(gainLife(2)))),
    )

    // one trigger for a batch of counters put by you on other Heroes
    @Test
    fun invisibleMatriarch() = card(
        name = "Unseen Matriarch",
        cost = "{4}{W}",
        type = "Legendary Creature — Human Hero",
        text = """
            Lifelink
            Whenever you put one or more +1/+1 counters on one or more other Heroes you control, you may create a 0/4 colorless Wall creature token with defender.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(arcanum.api.Trigger.On(EventPattern.CountersPut(and(another, hero, you), p1p1, causedBy = byYou), oneOrMore = true),
            Step.May(listOf(create(creatureToken("Wall", emptyList(), 0, 4, abilities = listOf(Keyword.Defender)))), player = Players.You)),
    )

    // a card put into your graveyard this turn from any zone (the new object in the graveyard, CR 400.7)
    @Test
    fun nightMedicHealerOfHeroes() = card(
        name = "Night Medic, Healer of Heroes",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Doctor Hero",
        text = """
            Flash
            Lifelink
            When Night Medic enters, choose target permanent card in your graveyard that was put there from anywhere this turn. Return it to your hand.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Lifelink,
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", and(inYourGraveyard(permanentCard),
            ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Graveyard), role = EventRole.After))))),
    )

    @Test
    fun ayokaGuardCaptain() = card(
        name = "Ayoka, Royal Guard Captain",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Warrior Hero",
        text = """
            When Ayoka enters, create two 1/1 white Soldier creature tokens.
            Attacking creature tokens you control have first strike.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, create(soldier, n(2))),
        static(has(Keyword.FirstStrike), affects = Objects.Each(and(attacking, creature, token, you))),
    )

    // CR 701.16a: the target player investigates
    @Test
    fun pantherLeap() = card(
        name = "Panther Leap",
        cost = "{W}",
        type = "Instant",
        text = "Target player investigates. Target creature gets +1/+0 and gains flying until end of turn. Untap it. (To investigate, create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")",
    ) parsesTo abilities(
        spell(
            Step.ForEach(arcanum.api.Each.Players(Players.Target("player")), "player", listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Investigate(n(1))))), together = true),
            pump(target("creature"), 1, 0, has(Keyword.Flying)),
            untapIt(target("creature")),
            targets = listOf(targetPlayer, slot("creature", creature))),
    )

    @Test
    fun patriotShieldBearer() = card(
        name = "Young Patriot, Shield Bearer",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Hero",
        text = "{2}, {T}: Another target creature you control gets +2/+0 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), pump(target("creature"), 2, 0, has(Keyword.Hexproof)),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    private val shielded = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Shield, atLeast = 1))

}
