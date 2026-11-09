package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 12 of FDN: "until end of turn" pumps and keyword grants. Invented names, the real oracle text. */
class FdnChunk12ApplyTest {
    private val yourCreatures = Objects.Each(and(creature, you))

    // CR 611.2a (effect lasts until end of turn), 613.4c, 702.12a (indestructible)
    @Test
    fun steadfastResolve() = card(
        name = "Steadfast Resolve",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2, has(Keyword.Indestructible)), targets = listOf(slot("creature", creature))),
    )

    // CR 602.1 (activated ability), 702.10a (haste)
    @Test
    fun forgeCavalry() = card(
        name = "Forgeward Rider",
        cost = "{1}{R}",
        type = "Creature — Dwarf Berserker",
        pt = "2/2",
        text = "{T}: Target creature gains haste until end of turn. (It can attack and {T} this turn.)",
    ) parsesTo abilities(
        activated(cost(tap), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", creature))),
    )

    // CR 603.2, 702.9a (flying), 702.19a (trample), 601.2 (cast)
    @Test
    fun skyBattlemage() = card(
        name = "Stormcrest Battlemage",
        cost = "{U}{R}",
        type = "Legendary Creature — Bird Wizard",
        pt = "1/3",
        text = """
            Flying
            Whenever you cast an instant or sorcery spell, creatures you control get +1/+0 and gain trample until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(or(instant, sorcery)), untilEndOfTurn(yourCreatures, pt(1, 0), has(Keyword.Trample))),
    )

    // CR 506.2 (attack), 603.2, 702.110a (menace)
    @Test
    fun warchantBerserker() = card(
        name = "Warchant Raider",
        cost = "{3}{R}",
        type = "Creature — Human Berserker",
        pt = "3/4",
        text = "Whenever you attack, target creature you control gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        triggered(youAttack, pump(target("creature"), 1, 0, has(Keyword.Menace)), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 702.8a (flash), 603.6a (enters), 613.4c (negative modifier)
    @Test
    fun puddleBefuddler() = card(
        name = "Puddle Dazzler",
        cost = "{1}{U}",
        type = "Creature — Frog Wizard",
        pt = "2/1",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            When this creature enters, target creature an opponent controls gets -1/-0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(target("creature"), -1, 0), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 508.1m, 603.4 (a condition checked as it attacks), 702.110a
    @Test
    fun boldGoblin() = card(
        name = "Brazen Goblin",
        cost = "{1}{R}",
        type = "Creature — Goblin",
        pt = "2/2",
        text = "Whenever this creature attacks while you control a creature with power 4 or greater, this creature gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(Objects.Self, 1, 0, has(Keyword.Menace)),
            triggersOnlyIf = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1)),
    )

    // CR 603.2, 601.2 (cast), 613.4c
    @Test
    fun sparkCyclops() = card(
        name = "Sparking Cyclops",
        cost = "{2}{R}",
        type = "Creature — Cyclops Wizard",
        pt = "0/4",
        text = "Whenever you cast a noncreature spell, this creature gets +3/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), pump(Objects.Self, 3, 0)),
    )

    // CR 702.19a, 611.2c (affects only creatures you control as it resolves), 121.1
    @Test
    fun chargeThrough() = card(
        name = "Charge Ahead",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Creatures you control gain trample until end of turn. (Each of those creatures can deal excess combat damage to the player or planeswalker it's attacking.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(gains(yourCreatures, Keyword.Trample), draw(1)),
    )

    // CR 508.1m, 603.2, 611.2c, 613.4c
    @Test
    fun fearlessVeteran() = card(
        name = "Steadfast Veteran",
        cost = "{1}{W}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = "Whenever this creature attacks, creatures you control get +1/+1 until end of turn.",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(yourCreatures, 1, 1)),
    )

    // CR 613.4c, 121.1
    @Test
    fun briefDistraction() = card(
        name = "Momentary Distraction",
        cost = "{U}",
        type = "Instant",
        text = """
            Target creature gets -1/-0 until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -1, 0), draw(1), targets = listOf(slot("creature", creature))),
    )

    // CR 117.12 ("you may pay"), 608.2c ("if you do"), 509.1b (can't block)
    @Test
    fun rabidGoblin() = card(
        name = "Frenetic Goblin",
        cost = "{R}",
        type = "Creature — Goblin Berserker",
        pt = "1/1",
        text = "Whenever this creature attacks, you may pay {R}. If you do, target creature can't block this turn.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost = cost(mana("{R}")), payer = arcanum.api.Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 702.12 (gate is a land subtype CR 205.3i), 509.1b, 603.2
    @Test
    fun portalSneak() = card(
        name = "Doorway Sneak",
        cost = "{2}{U}",
        type = "Creature — Vedalken Rogue",
        pt = "1/3",
        text = """
            Whenever a Gate you control enters, this creature can't be blocked this turn.
            Whenever this creature deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        triggered(enters(and(sub("Gate"), you)), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)),
        triggered(combatDamageToAPlayer, draw(1)),
    )

    // CR 602.1, 509.1b, 700.2 ("another target creature with power 2 or less")
    @Test
    fun backalleySmuggler() = card(
        name = "Alleyway Smuggler",
        cost = "{2}{R}",
        type = "Creature — Goblin Rogue",
        pt = "2/2",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            {T}: Another target creature with power 2 or less can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
            targets = listOf(slot("creature", and(another, creature, power(Comparison.AtMost, 2))))),
    )

    // CR 700.2 (modes), 111.1 (tokens), 613.4c
    @Test
    fun hordeSurprise() = card(
        name = "Horde Gambit",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures you control get +2/+0 until end of turn.
            • Create two 1/1 red Goblin creature tokens.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(untilEndOfTurn(yourCreatures, pt(2, 0))),
            mode(create(creatureToken("Goblin", listOf(Color.Red), 1, 1), n(2))),
        ))),
    )

    // CR 107.3 (X defined by the ability), 508.1k (attacking creatures), 613.4c
    @Test
    fun goldenCommander() = card(
        name = "Gilded Commander",
        cost = "{2}{W}{W}",
        type = "Legendary Creature — Cat Warrior",
        pt = "4/4",
        text = """
            First strike (This creature deals combat damage before creatures without first strike.)
            {3}{W}{W}: Attacking creatures you control get +X/+X until end of turn, where X is the number of attacking creatures.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        activated(manaCost("{3}{W}{W}"),
            untilEndOfTurn(Objects.Each(and(attacking, creature, you)),
                Mechanic.ModifyPowerToughness(count(and(attacking, creature)), count(and(attacking, creature))))),
    )

    // CR 613.4c, 702.7a (first strike)
    @Test
    fun emberFury() = card(
        name = "Smoldering Fury",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +1/+0 and gains first strike until end of turn. (It deals combat damage before creatures without first strike.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 0, has(Keyword.FirstStrike)), targets = listOf(slot("creature", creature))),
    )

    // CR 603.4 (intervening if), 506.1 (beginning of combat), 119.1, 613.4c
    @Test
    fun lionguardVanguard() = card(
        name = "Pridekeep Vanguard",
        cost = "{W}",
        type = "Creature — Cat Soldier",
        pt = "1/1",
        text = "At the beginning of combat on your turn, if you control three or more creatures, this creature gets +1/+1 until end of turn and you gain 1 life.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(Objects.Self, 1, 1), gainLife(1),
            interveningIf = Condition.Exists(and(creature, you), 3)),
    )
}
