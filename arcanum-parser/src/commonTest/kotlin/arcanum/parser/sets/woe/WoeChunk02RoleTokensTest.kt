package arcanum.parser.sets.woe

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** WOE chunk 02: Role tokens, part 2 (CR 111.10j–r, 303.7). */
class WoeChunk02RoleTokensTest {
    private val creatureSlot = slot("creature", creatureYouControl)
    private val anotherCreatureYouControl = slot("creature", and(another, creature, you))
    private val upToOneCreatureYouControl = slot("creature", creatureYouControl, min = 0)
    private val returned = Objects.EventObject(EventRole.After)

    // CR 113.1c, 603.6c (a granted dies trigger); CR 400.7, 111.10q: the Wicked Role goes on the creature as it returns, a new object
    @Test
    fun secondWindHex() = card(
        name = "Second Wind Hex",
        cost = "{B}",
        type = "Instant",
        text = "Until end of turn, target creature you control gains \"When this creature dies, return it to the battlefield tapped under its owner's control, then create a Wicked Role token attached to it.\" (Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), has(triggered(diesSelf,
                move(returned, Zone.Battlefield, tapped = true, controller = Players.OwnerOf(returned), postBind = "returned"),
                createRole("Wicked", Objects.Bound("returned"))))),
            targets = listOf(creatureSlot),
        ),
    )

    // CR 603.6c, 115.4 (up to one target); CR 111.10r
    @Test
    fun hearthwardParents() = card(
        name = "Hearthward Parents",
        cost = "{2}{W}",
        type = "Creature — Human Peasant",
        pt = "3/2",
        text = "When this creature dies, create a Young Hero Role token attached to up to one target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\")",
    ) parsesTo abilities(
        triggered(diesSelf, createRole("Young Hero", target("creature")), targets = listOf(upToOneCreatureYouControl)),
    )

    // CR 111.10m
    @Test
    fun rootlineArchivist() = card(
        name = "Rootline Archivist",
        cost = "{2}{G}",
        type = "Creature — Elf Advisor",
        pt = "2/3",
        text = "When this creature enters, create a Royal Role token attached to another target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has ward {1}.)",
    ) parsesTo abilities(
        triggered(entersSelf, createRole("Royal", target("creature")), targets = listOf(anotherCreatureYouControl)),
    )

    // CR 400.7, 111.10r: "it" is the returned creature
    @Test
    fun triumphalHomecoming() = card(
        name = "Triumphal Homecoming",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Return target creature card with mana value 3 or less from your graveyard to the battlefield. Create a Young Hero Role token attached to it. (Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\" If you put another Role on the creature later, put this one into the graveyard.)",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield, postBind = "returned"), createRole("Young Hero", Objects.Bound("returned")),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 702.11 (hexproof); CR 111.10m: "that creature" is the target
    @Test
    fun crowningFavor() = card(
        name = "Crowning Favor",
        cost = "{G}",
        type = "Instant",
        text = "Target creature you control gains hexproof until end of turn. Create a Royal Role token attached to that creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has ward {1}.)",
    ) parsesTo abilities(
        spell(gains(target("creature"), Keyword.Hexproof), createRole("Royal", target("creature")), targets = listOf(creatureSlot)),
    )

    // CR 701.8 (destroy); CR 111.10q
    @Test
    fun oathbreakersRuin() = card(
        name = "Oathbreaker's Ruin",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = "Destroy target creature or enchantment. Create a Wicked Role token attached to up to one target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), createRole("Wicked", target("creature")),
            targets = listOf(slot("permanent", or(creature, enchantment)), upToOneCreatureYouControl)),
    )

    // CR 702.20 (vigilance); CR 603.12: "you may pay {1}. When you do, …"; CR 111.10n
    @Test
    fun grimoirePeddler() = card(
        name = "Grimoire Peddler",
        cost = "{1}{W}",
        type = "Creature — Human Peasant",
        pt = "2/2",
        text = """
            Vigilance
            At the beginning of combat on your turn, you may pay {1}. When you do, create a Sorcerer Role token attached to target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has "Whenever this creature attacks, scry 1.")
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(beginningOfCombatOnYourTurn, Step.Pay(manaCost("{1}"), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), reflexive(createRole("Sorcerer", target("creature")), targets = listOf(creatureSlot)))),
    )

    // CR 111.10j
    @Test
    fun spitefulHedgewitch() = card(
        name = "Spiteful Hedgewitch",
        cost = "{B}",
        type = "Creature — Human Warlock",
        pt = "3/2",
        text = "When this creature enters, create a Cursed Role token attached to target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature is 1/1.)",
    ) parsesTo abilities(
        triggered(entersSelf, createRole("Cursed", target("creature")), targets = listOf(creatureSlot)),
    )

    // CR 601.2i (cast trigger); CR 111.10n
    @Test
    fun puddleConjurer() = card(
        name = "Puddle Conjurer",
        cost = "{3}{U}",
        type = "Creature — Elemental Wizard",
        pt = "2/4",
        text = "Whenever you cast an instant or sorcery spell, create a Sorcerer Role token attached to up to one other target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has \"Whenever this creature attacks, scry 1.\")",
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)), createRole("Sorcerer", target("creature")),
            targets = listOf(slot("creature", and(another, creature, you), min = 0))),
    )

    // CR 111.10k; CR 303.4 (an enchanted creature is one with an Aura attached), 613.4c
    @Test
    fun dameOrlithTheSteadfast() = card(
        name = "Dame Orlith, the Steadfast",
        cost = "{3}{G}{W}",
        type = "Legendary Creature — Human Knight",
        pt = "4/4",
        text = """
            When Dame Orlith enters, create a Monster Role token attached to another target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample.)
            Enchanted creatures you control get +1/+1.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createRole("Monster", target("creature")), targets = listOf(anotherCreatureYouControl)),
        static(pt(1, 1), affects = Objects.Each(and(ObjectFilter.Attached(attachments = sub("Aura")), creature, you))),
    )

    // CR 701.12 (control), 302.6, 702.10; CR 111.10q: the Role may go on any creature, the stolen one included
    @Test
    fun warpedAllegiance() = card(
        name = "Warped Allegiance",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.
            Create a Wicked Role token attached to up to one target creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            createRole("Wicked", target("second creature")),
            targets = listOf(slot("creature", creature), slot("second creature", creature, min = 0)),
        ),
    )

    // CR 111.1; CR 111.10q: a Wicked Role on each Rat Alice controls, the new one included
    @Test
    fun gutterwitchCrone() = card(
        name = "Gutterwitch Crone",
        cost = "{3}{B}{B}",
        type = "Creature — Human Warlock",
        pt = "3/4",
        text = "When this creature enters, create a 1/1 black Rat creature token with \"This creature can't block.\" Then for each Rat you control, create a Wicked Role token attached to that Rat. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)",
    ) parsesTo abilities(
        triggered(entersSelf,
            create(creatureToken("Rat", listOf(Color.Black), 1, 1, abilities = listOf(Ability.Static(
                listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))))),
            Step.ForEach(arcanum.api.Each.Objects(Objects.Each(and(sub("Rat"), you))), "creature",
                listOf(createRole("Wicked", Objects.Bound("creature"))))),
    )

    // CR 603.5, 118.12 ("you may pay … If you do"); CR 111.10n: "it" is this creature
    @Test
    fun humbleLorekeeper() = card(
        name = "Humble Lorekeeper",
        cost = "{1}{W}",
        type = "Creature — Human Peasant Wizard",
        pt = "2/2",
        text = "When this creature enters, you may pay {2}. If you do, create a Sorcerer Role token attached to it. (Enchanted creature gets +1/+1 and has \"Whenever this creature attacks, scry 1.\")",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(manaCost("{2}"), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), createRole("Sorcerer", Objects.EventObject(EventRole.After)))),
    )

    // CR 715 (Adventure); CR 701.26 (tap); CR 111.10j
    @Test
    fun brineglassAlchemist() = cardFaces(Layout.Adventure,
        card(name = "Brineglass Alchemist", cost = "{3}{U}", type = "Creature — Human Wizard", pt = "3/4") to none,
        card(name = "Toadish Hex", cost = "{1}{U}", type = "Sorcery — Adventure",
            text = "Tap target creature. Create a Cursed Role token attached to it. (Enchanted creature is 1/1.)",
        ) to abilities(spell(tapIt(target("creature")), createRole("Cursed", target("creature")), targets = listOf(slot("creature", creature)))),
    )

    // CR 118.12 ("you may discard … If you do"); CR 111.10q
    @Test
    fun hexersBrand() = card(
        name = "Hexer's Brand",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            You may discard a card. If you do, draw two cards.
            Create a Wicked Role token attached to up to one target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)
        """,
    ) parsesTo abilities(
        spell(Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(2))),
            createRole("Wicked", target("creature")),
            targets = listOf(upToOneCreatureYouControl)),
    )
}
