package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PlayerFilter
import arcanum.api.Recipients
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN Equipment (chunk 03). The invented names carry the oracle text of the real cards. */
class FdnChunk03EquipmentTest {
    private val equipped = Objects.AttachedTo(Objects.Self)
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))
    private fun attachToTarget() = Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature"))))

    // CR 207.2c, 603.2, 301.5: the bonus goes to whatever the Equipment is attached to as the ability resolves
    @Test
    fun trailwornSatchel() = card(
        name = "Trailworn Satchel",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Landfall — Whenever a land you control enters, equipped creature gets +2/+2 until end of turn.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(landfall, pump(equipped, 2, 2), abilityWord = "Landfall"),
        equip("{1}"),
    )

    // CR 113.10, 602.2b: the creature has the ability, its {T} taps the creature, the named object is the Equipment.
    // CR 701.26, 603.2: untapping is an event; CR 118.12: "If you do" only if a counter came off
    @Test
    fun anglersReel() = card(
        name = "Angler's Reel",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has "{1}, {T}, Tap Angler's Reel: Put a bait counter on Angler's Reel."
            Whenever equipped creature becomes untapped, remove a bait counter from this Equipment. If you do, create a 1/1 blue Fish creature token.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(
            has(
                activated(
                    cost(mana("{1}"), tap, CostPart.TapObjects(CostObjects.These(Objects.Granter))),
                    counters(Objects.Granter, 1, CounterKind.Named("bait")),
                    rules = anyTime,
                ),
            ),
            affects = equipped,
        ),
        triggered(
            on(EventPattern.Untap(ObjectFilter.Is(equipped))),
            Step.Pay(Cost(listOf(CostPart.RemoveCounters(CounterKind.Named("bait"), n(1), CostObjects.Self))), payer = Players.You, mandatory = true, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(create(creatureToken("Fish", listOf(Color.Blue), 1, 1)))),
        ),
        equip("{2}"),
    )

    // CR 702.2a, 702.15a, 301.5
    @Test
    fun cockatriceChoker() = card(
        name = "Cockatrice Choker",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has deathtouch and lifelink. (Any amount of damage it deals to a creature is enough to destroy it. Damage dealt by this creature also causes you to gain that much life.)
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Deathtouch, Keyword.Lifelink), affects = equipped),
        equip("{2}"),
    )

    // CR 702.8a, 603.3d, 701.3a, 702.11a, 702.12a: the until-end-of-turn gains stay on that creature, the static bonus follows the Equipment
    @Test
    fun starlitCuirass() = card(
        name = "Starlit Cuirass",
        cost = "{2}{W}",
        type = "Artifact — Equipment",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            When this Equipment enters, attach it to target creature you control. That creature gains hexproof and indestructible until end of turn.
            Equipped creature gets +2/+0 and has flying.
            Equip {3}{W} ({3}{W}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(
            entersSelf,
            attachToTarget(),
            gains(target("creature"), Keyword.Hexproof, Keyword.Indestructible),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
        static(pt(2, 0), has(Keyword.Flying), affects = equipped),
        equip("{3}{W}"),
    )

    // CR 702.4a, 301.5
    @Test
    fun screamblade() = card(
        name = "Screamblade",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has double strike. (It deals both first-strike and regular combat damage.)
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike), affects = equipped),
        equip("{2}"),
    )

    // CR 301.5, 510.3a: the controller of the Equipment gets the Treasure; 111.10a
    @Test
    fun oreseamMattock() = card(
        name = "Oreseam Mattock",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1.
            Whenever equipped creature deals combat damage to a player, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = equipped),
        triggered(
            on(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            createTreasure(),
        ),
        equip("{1}"),
    )

    // CR 103.6a: works in the opening hand; 702.4a, 702.19a, 301.5
    @Test
    fun groundwellHatchet() = card(
        name = "Groundwell Hatchet",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            If this card is in your opening hand, you may begin the game with it on the battlefield.
            Equipped creature gets +1/+1 and has double strike and trample.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayBeginOnBattlefield), affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
        static(pt(1, 1), has(Keyword.DoubleStrike, Keyword.Trample), affects = equipped),
        equip("{3}"),
    )

    // CR 603.3d, 701.3a: the target is any Pirate you control, the ability says nothing about equip's own restriction
    @Test
    fun buccaneersHanger() = card(
        name = "Buccaneer's Hanger",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target Pirate you control.
            Equipped creature gets +2/+1.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("Pirate")))),
            targets = listOf(slot("Pirate", and(sub("Pirate"), you))),
        ),
        static(pt(2, 1), affects = equipped),
        equip("{2}"),
    )

    // CR 611.3a: a static ability with a condition; "your" is the Equipment's controller, 702.7a, 301.5
    @Test
    fun swiftdrawnWakizashi() = card(
        name = "Swiftdrawn Wakizashi",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            During your turn, equipped creature gets +2/+0 and has first strike. (It deals combat damage before creatures without first strike.)
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(2, 0), has(Keyword.FirstStrike), affects = equipped, condition = Condition.YourTurn),
        equip("{2}"),
    )

    // CR 702.11a, 702.10a, 301.5
    @Test
    fun fleetstepSandals() = card(
        name = "Fleetstep Sandals",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has hexproof and haste. (It can't be the target of spells or abilities your opponents control. It can attack and {T} no matter when it came under your control.)
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Hexproof, Keyword.Haste), affects = equipped),
        equip("{1}"),
    )
}
