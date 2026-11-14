package arcanum.parser.sets.bro

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Combine
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 10: "until end of turn" effects: pumps, granted keywords and type changes of single cards. */
class BroChunk10ApplyTest {
    private val soldier = sub("Soldier")

    @Test
    fun cloudSergeant() = card(
        name = "Cloud Sergeant", cost = "{1}{U}", type = "Creature — Human Soldier", pt = "2/1",
        text = "{3}: Target Soldier gains flying until end of turn.",
    ) parsesTo abilities(
        activated(manaCost("{3}"), gains(target("Soldier"), Keyword.Flying), targets = listOf(slot("Soldier", soldier))),
    )

    @Test
    fun brassWhisperer() = card(
        name = "Brass Whisperer", cost = "{G}", type = "Creature — Human Druid", pt = "1/1",
        text = "{2}{G}: Until end of turn, target noncreature artifact you control becomes a 4/4 artifact creature.",
    ) parsesTo abilities(
        activated(manaCost("{2}{G}"),
            untilEndOfTurn(target("artifact"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
                Mechanic.SetPowerToughness(n(4), n(4))),
            targets = listOf(slot("artifact", and(not(creature), artifact, you)))),
    )

    @Test
    fun dunesChainwalker() = card(
        name = "Dunes Chainwalker", cost = "{3}{R}", type = "Creature — Human Soldier", pt = "2/4",
        text = "{2}: This creature gains double strike until end of turn.",
    ) parsesTo abilities(
        activated(manaCost("{2}"), gains(Objects.Self, Keyword.DoubleStrike)),
    )

    // CR 603.2: "this creature or another creature you control" is one trigger over either entering
    @Test
    fun dunesPikeguard() = card(
        name = "Dunes Pikeguard", cost = "{2}{R}{W}", type = "Creature — Human Soldier", pt = "2/3",
        text = """
            First strike
            Whenever this creature or another creature you control enters, target creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(enters(or(isSelf, and(another, creature, you))), pump(target("creature"), 2, 0), targets = listOf(slot("creature", creature))),
    )

    // CR 107.3a: X is the number of creature cards in the graveyard when the ability resolves
    @Test
    fun rootbarkMourner() = card(
        name = "Rootbark Mourner", cost = "{4}{G}{G}", type = "Creature — Treefolk Druid", pt = "5/5",
        text = """
            Trample
            When this creature enters, target creature gets +X/+X until end of turn, where X is the number of creature cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf,
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature)))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun halvarSteadfastGuard() = card(
        name = "Halvar, Steadfast Guard", cost = "{R}{G}", type = "Legendary Creature — Human Soldier", pt = "3/3",
        text = "Sacrifice Halvar: Legendary creatures you control get +1/+0 and gain indestructible until end of turn.",
    ) parsesTo abilities(
        activated(cost(sacrificeSelf),
            untilEndOfTurn(Objects.Each(and(legendary, creature, you)), pt(1, 0), has(Keyword.Indestructible))),
    )

    // CR 508.1, 603.2c: "attack with five or more Soldiers" counts the creatures declared as attackers together
    @Test
    fun hollinCloudPilot() = card(
        name = "Hollin, Cloud Pilot", cost = "{W}{U}", type = "Legendary Creature — Human Soldier", pt = "3/2",
        text = """
            Flying
            Whenever you attack with five or more Soldiers, creatures you control get +1/+1 and gain flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(listOf(EventPattern.Attack(and(soldier, you))), oneOrMore = true, atLeast = 5), untilEndOfTurn(Objects.Each(creatureYouControl), pt(1, 1), has(Keyword.Flying))),
    )

    // CR 608.2c: the sacrifice is optional ("you may"); the keyword follows only if it was paid
    @Test
    fun pitTumbler() = card(
        name = "Pit Tumbler", cost = "{2}{B}", type = "Creature — Human Soldier", pt = "3/2",
        text = "Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, this creature gains flying until end of turn.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), or(creature, artifact)), count = n(1)))),
                payer = Players.You, bind = "paid"),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(gains(Objects.Self, Keyword.Flying)))),
    )

    @Test
    fun hourOfResolve() = card(
        name = "Hour of Resolve", cost = "{2}{B}", type = "Instant",
        text = """
            Target creature gets +2/+1 and gains lifelink until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 1, has(Keyword.Lifelink)), draw(1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun pellorIronbull() = card(
        name = "Pellor Ironbull", cost = "{2}{R}", type = "Creature — Minotaur", pt = "2/3",
        text = "{1}, Sacrifice an artifact: This creature gets +1/+1 until end of turn and deals 1 damage to each opponent.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(artifact)), pump(Objects.Self, 1, 1), damage(1, eachOpponent)),
    )

    @Test
    fun fencewallWatcher() = card(
        name = "Fencewall Watcher", cost = "{2}{G}", type = "Creature — Human Soldier", pt = "3/3",
        text = "Whenever an artifact you control enters, this creature gets +1/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), pump(Objects.Self, 1, 0)),
    )

    @Test
    fun shieldlineVeteran() = card(
        name = "Shieldline Veteran", cost = "{1}{W}", type = "Creature — Human Soldier", pt = "2/2",
        text = """
            Vigilance
            Whenever an artifact you control enters, this creature gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(enters(and(artifact, you)), pump(Objects.Self, 1, 0)),
    )

    @Test
    fun parcelRelease() = card(
        name = "Parcel Release", cost = "{3}", type = "Artifact",
        text = """
            Flash
            When this artifact enters, target creature you control gets +2/+2 until end of turn.
            {4}, {T}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(target("creature"), 2, 2), targets = listOf(slot("creature", and(creature, you)))),
        activated(cost(mana("{4}"), tap, sacrificeSelf), draw(1)),
    )

    // CR 702.16j: protection from everything, for the player, until the start of their next turn (CR 514.2)
    @Test
    fun theFrozenCasket() = card(
        name = "The Frozen Casket", cost = "{3}", type = "Legendary Artifact",
        text = "{2}, {T}, Exile The Frozen Casket: You gain protection from everything until your next turn.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Protection(ObjectFilter.Any))), Affected.Players(Players.You), Duration.UntilYourNextTurn))),
    )

    @Test
    fun wingworkPlanner() = card(
        name = "Wingwork Planner", cost = "{3}{W}", type = "Creature — Human Artificer", pt = "2/3",
        text = "Whenever an artifact you control enters, target creature gains flying until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun spinningBlow() = card(
        name = "Spinning Blow", cost = "{1}{R}", type = "Instant",
        text = "Target creature gets +2/+0 and gains first strike and trample until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 0, has(Keyword.FirstStrike, Keyword.Trample)), targets = listOf(slot("creature", creature))),
    )
}
