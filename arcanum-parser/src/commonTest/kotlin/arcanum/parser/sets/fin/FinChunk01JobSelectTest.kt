package arcanum.parser.sets.fin

import arcanum.api.AttackTarget
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.EventPattern
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Mechanic
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 01: job select Equipment (CR 702.182a: a Hero token made and equipped as it enters) that make the equipped creature
 * one more creature type (CR 205.1b, layer 4) and grant keywords or quoted abilities (layer 6), equip abilities after a flavor word
 * (CR 207.2d), an equip cost of life usable once each turn, and a granted trigger that is two trigger conditions.
 */
class FinChunk01JobSelectTest {
    private fun equipmentCard(name: String, cost: String, text: String, type: String = "Artifact — Equipment") =
        card(name = name, cost = cost, type = type, text = text)

    // CR 702.182a, 205.1b, 613.1f
    @Test
    fun wyrmridersLance() = equipmentCard(
        name = "Wyrmrider's Lance",
        cost = "{1}{W}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +1/+0 and is a Knight in addition to its other types.
            During your turn, equipped creature has flying.
            Gae Bolg — Equip {4}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 0), alsoA("Knight"), affects = equipped),
        static(has(Keyword.Flying), affects = equipped, condition = Condition.YourTurn),
        Keyword.Equip(manaCost("{4}")),
    )

    @Test
    fun tinkerersArsenal() = equipmentCard(
        name = "Tinkerer's Arsenal",
        cost = "{4}{W}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +2/+2 for each artifact you control and is an Artificer in addition to its other types.
            Machina — Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(Mechanic.ModifyPowerToughness(Value.Times(count(and(artifact, you)), 2), Value.Times(count(and(artifact, you)), 2)), alsoA("Artificer"),
            affects = equipped),
        Keyword.Equip(manaCost("{4}")),
    )

    // CR 702.21a (ward granted, layer 6)
    @Test
    fun oathkeepersPlate() = equipmentCard(
        name = "Oathkeeper's Plate",
        cost = "{2}{W}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +2/+1, has ward {1}, and is a Knight in addition to its other types.
            Lightbringer and Hero's Shield — Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(2, 1), has(Keyword.Ward(manaCost("{1}"))), alsoA("Knight"), affects = equipped),
        Keyword.Equip(manaCost("{4}")),
    )

    @Test
    fun mendersCrook() = equipmentCard(
        name = "Mender's Crook",
        cost = "{1}{W}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +1/+1, has "Whenever this creature attacks, you gain 1 life," and is a Cleric in addition to its other types.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 1), has(triggered(attacksSelf, gainLife(1))), alsoA("Cleric"), affects = equipped),
        Keyword.Equip(manaCost("{3}")),
    )

    /** "Whenever you cast a noncreature spell and whenever you draw your third card each turn": two trigger conditions of one granted
     * ability, written as two abilities with the same effect. */
    @Test
    fun stargazersOrrery() = equipmentCard(
        name = "Stargazer's Orrery",
        cost = "{1}{U}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature is a Wizard in addition to its other types and has "Whenever you cast a noncreature spell and whenever you draw your third card each turn, put a +1/+1 counter on this creature."
            Diana — Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(alsoA("Wizard"), has(
            triggered(youCast(noncreature), counters(Objects.Self)),
            triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 3), counters(Objects.Self)),
        ), affects = equipped),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun mystikPetalblades() = equipmentCard(
        name = "Mystik Petalblades",
        cost = "{1}{U}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +1/+0, has "Whenever this creature attacks, untap target attacking creature," and is a Cleric in addition to its other types.
            Hagneia — Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 0), has(triggered(attacksSelf, untapIt(target("creature")), targets = listOf(slot("creature", and(attacking, creature))))),
            alsoA("Cleric"), affects = equipped),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun pickpocketsDirk() = equipmentCard(
        name = "Pickpocket's Dirk",
        cost = "{2}{U}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +1/+1, has "Whenever this creature deals combat damage to a player, draw a card," and is a Rogue in addition to its other types.
            Equip {4}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 1), has(triggered(combatDamageToAPlayer, draw(1))), alsoA("Rogue"), affects = equipped),
        Keyword.Equip(manaCost("{4}")),
    )

    @Test
    fun shadowcastersWand() = equipmentCard(
        name = "Shadowcaster's Wand",
        cost = "{1}{B}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +1/+0, has "Whenever you cast a noncreature spell, this creature deals 1 damage to each opponent," and is a Wizard in addition to its other types.
            Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 0), has(triggered(youCast(noncreature), damage(1, eachOpponent))), alsoA("Wizard"), affects = equipped),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 702.6a (an equip cost that isn't mana), 602.5b (once each turn)
    @Test
    fun abyssKnightsClaymore() = equipmentCard(
        name = "Abyss Knight's Claymore",
        cost = "{2}{B}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +3/+0 and is a Knight in addition to its other types.
            Chaosbringer — Equip—Pay 3 life. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(3, 0), alsoA("Knight"), affects = equipped),
        Keyword.Equip(cost(CostPart.PayLife(n(3))), perTurn = 1),
    )

    /** "That player loses life equal to the discarded card's mana value": the player dealt damage, the card discarded this way. */
    @Test
    fun shinobiTwinEdges() = equipmentCard(
        name = "Shinobi Twin Edges",
        cost = "{2}{B}",
        text = """
            Job select
            Equipped creature gets +1/+1, is a Ninja in addition to its other types, and has "Whenever this creature deals combat damage to a player, draw a card, then discard a card. That player loses life equal to the discarded card's mana value."
            Mutsunokami — Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 1), alsoA("Ninja"), has(triggered(combatDamageToAPlayer,
            draw(1),
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, postBind = "discarded")),
            doing(Effect.LoseLife(Players.EventPlayer, Value.Of(NumericProperty.ManaValue, Objects.Bound("discarded"), Combine.Single))),
        )), affects = equipped),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun crimsonDuelistsFoil() = equipmentCard(
        name = "Crimson Duelist's Foil",
        cost = "{1}{R}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature has "Whenever you cast a noncreature spell, this creature gets +2/+0 until end of turn" and is a Wizard in addition to its other types.
            Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(has(triggered(youCast(noncreature), pump(Objects.Self, 2, 0))), alsoA("Wizard"), affects = equipped),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun roninsNodachi() = equipmentCard(
        name = "Ronin's Nodachi",
        cost = "{2}{R}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +2/+2, has trample and haste, and is a Samurai in addition to its other types.
            Murasame — Equip {5}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(2, 2), has(Keyword.Trample, Keyword.Haste), alsoA("Samurai"), affects = equipped),
        Keyword.Equip(manaCost("{5}")),
    )

    @Test
    fun berserkersGreataxe() = equipmentCard(
        name = "Berserker's Greataxe",
        cost = "{3}{R}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +3/+2 and is a Warrior in addition to its other types.
            Equip {5} ({5}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(3, 2), alsoA("Warrior"), affects = equipped),
        Keyword.Equip(manaCost("{5}")),
    )

    @Test
    fun troubadoursLongbow() = equipmentCard(
        name = "Troubadour's Longbow",
        cost = "{2}{G}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +2/+2, has reach, and is a Bard in addition to its other types.
            Perseus's Bow — Equip {6} ({6}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(2, 2), has(Keyword.Reach), alsoA("Bard"), affects = equipped),
        Keyword.Equip(manaCost("{6}")),
    )

    /** "If that card is an enchantment card, it enters tapped and attacking" (CR 508.4: put onto the battlefield attacking, not declared). */
    @Test
    fun conjurersTome() = equipmentCard(
        name = "Conjurer's Tome",
        cost = "{3}{G}",
        type = "Artifact — Book Equipment",
        text = """
            Job select
            Equipped creature is a Shaman in addition to its other types and has "Whenever this creature attacks, you may put a creature card from your hand onto the battlefield. If that card is an enchantment card, it enters tapped and attacking."
            Abraxas — Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(alsoA("Shaman"), has(triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            ifThen(Condition.ObjectMatches(Objects.Bound("chosen"), enchantment),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true, attacking = AttackTarget.Chosen))),
                otherwise = listOf(move(Objects.Bound("chosen"), Zone.Battlefield))),
        )), affects = equipped),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun pugilistsCesti() = equipmentCard(
        name = "Pugilist's Cesti",
        cost = "{2}",
        text = """
            Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
            Equipped creature gets +1/+0 and is a Monk in addition to its other types.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.JobSelect,
        static(pt(1, 0), alsoA("Monk"), affects = equipped),
        Keyword.Equip(manaCost("{2}")),
    )
}
