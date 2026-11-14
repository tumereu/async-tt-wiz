package arcanum.parser.sets.woe

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** WOE chunk 01: Role tokens, part 1 (CR 111.10j–r, 303.7). */
class WoeChunk01RoleTokensTest {
    private val creatureSlot = slot("creature", creatureYouControl)

    // CR 702.8a (flash for an extra cost); CR 111.10j: a Cursed Role on each creature your opponents control
    @Test
    fun foolishFrolics() = card(
        name = "Foolish Frolics",
        cost = "{2}{U}{U}",
        type = "Sorcery",
        text = """
            You may cast this spell as though it had flash if you pay {2} more to cast it.
            For each creature your opponents control, create a Cursed Role token attached to that creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature is 1/1.)
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(mana("{2}")), optional = true, flash = true)),
        spell(Step.ForEach(arcanum.api.Each.Objects(Objects.Each(creatureOpponentControls)), "creature", listOf(createRole("Cursed", Objects.Bound("creature"))))),
    )

    // CR 111.10k: a Monster Role on each of the one or two targets
    @Test
    fun turnFeral() = card(
        name = "Turn Feral",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "One or two target creatures each gain haste until end of turn. For each of those creatures, create a Monster Role token attached to it. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample.)",
    ) parsesTo abilities(
        spell(gains(target("creatures"), Keyword.Haste),
            Step.ForEach(arcanum.api.Each.Objects(target("creatures")), "creature", listOf(createRole("Monster", Objects.Bound("creature")))),
            targets = listOf(slot("creatures", creature, max = 2))),
    )

    // CR 715 (Adventure); CR 111.10m: Royal Role
    @Test
    fun smittenSquire() = cardFaces(Layout.Adventure,
        card(name = "Smitten Squire", cost = "{3}{W}", type = "Creature — Human Knight", pt = "3/3") to none,
        card(name = "Wed the Wolf", cost = "{W}", type = "Sorcery — Adventure",
            text = "Create a Royal Role token attached to target creature you control. (Enchanted creature gets +1/+1 and has ward {1}.)",
        ) to abilities(spell(createRole("Royal", target("creature")), targets = listOf(creatureSlot))),
    )

    // CR 111.10m
    @Test
    fun enchantedSeamstress() = card(
        name = "Enchanted Seamstress",
        cost = "{4}{W}",
        type = "Creature — Faerie Advisor",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, create a Royal Role token attached to another target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has ward {1}.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, createRole("Royal", target("creature")), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 700.2 (modes); CR 111.10q: Wicked Role
    @Test
    fun winsomeRascal() = card(
        name = "Winsome Rascal",
        cost = "{1}{R}",
        type = "Creature — Human Rogue",
        pt = "1/1",
        text = """
            Haste
            When this creature enters, choose one —
            • Discard a card, then draw a card.
            • Create a Treasure token.
            • Create a Wicked Role token attached to target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        arcanum.api.Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(discard(1), draw(1)),
            mode(createTreasure()),
            mode(createRole("Wicked", target("creature")), targets = listOf(creatureSlot)),
        ))),
    )

    // CR 715; CR 111.10q
    @Test
    fun vainHexer() = cardFaces(Layout.Adventure,
        card(name = "Vain Hexer", cost = "{2}{B}", type = "Creature — Human Warlock", pt = "2/3",
            text = "Menace (This creature can't be blocked except by two or more creatures.)") to abilities(Keyword.Menace),
        card(name = "Cost of Vanity", cost = "{B}", type = "Sorcery — Adventure",
            text = "Create a Wicked Role token attached to target creature you control. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(createRole("Wicked", target("creature")), targets = listOf(creatureSlot))),
    )

    // CR 111.10j: "attached to it" is this creature (CR 603.6a: it entered)
    @Test
    fun hexedChamberlain() = card(
        name = "Hexed Chamberlain",
        cost = "{2}{W}",
        type = "Creature — Human Noble",
        pt = "3/3",
        text = """
            Lifelink
            When this creature enters, create a Cursed Role token attached to it. (Enchanted creature is 1/1.)
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, createRole("Cursed", Objects.EventObject(arcanum.api.EventRole.After))),
    )

    // CR 111.10r: Young Hero Role on up to one target (none: no token, CR 303.4i)
    @Test
    fun bargeAhead() = card(
        name = "Barge Ahead",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Barge Ahead deals 4 damage to target creature.
            Create a Young Hero Role token attached to up to one target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature has "Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.")
        """,
    ) parsesTo abilities(
        spell(damage(4, toObjects(target("creature"))), createRole("Young Hero", target("second creature")),
            targets = listOf(slot("creature", creature), slot("second creature", creatureYouControl, min = 0))),
    )

    // CR 111.10r
    @Test
    fun cinderholdOldTimer() = card(
        name = "Cinderhold Old-Timer",
        cost = "{R}",
        type = "Creature — Human Knight",
        pt = "2/1",
        text = "{1}, Sacrifice this creature: Create a Young Hero Role token attached to another target creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\")",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeSelf), createRole("Young Hero", target("creature")), targets = listOf(slot("creature", and(another, creature)))),
    )

    // CR 111.10q
    @Test
    fun witchsMurmur() = card(
        name = "Witch's Murmur",
        cost = "{3}{B}",
        type = "Sorcery",
        text = "Target opponent discards two cards. Create a Wicked Role token attached to up to one target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)",
    ) parsesTo abilities(
        spell(discard(2, Players.Target("opponent")), createRole("Wicked", target("creature")),
            targets = listOf(targetOpponent, slot("creature", creatureYouControl, min = 0))),
    )

    // CR 715; CR 111.10k
    @Test
    fun savageWerebadger() = cardFaces(Layout.Adventure,
        card(name = "Savage Werebadger", cost = "{3}{G}", type = "Creature — Elf Fox Warrior", pt = "4/3", text = "Trample") to abilities(Keyword.Trample),
        card(name = "Shift Swap", cost = "{1}{G}", type = "Instant — Adventure",
            text = "Create a Monster Role token attached to target creature you control. (Enchanted creature gets +1/+1 and has trample.)",
        ) to abilities(spell(createRole("Monster", target("creature")), targets = listOf(creatureSlot))),
    )

    // CR 714.2b (chapters); CR 111.10j; CR 603.7 (the delayed trigger of chapter III, for this turn)
    @Test
    fun tomlinsFirstBout() = card(
        name = "Tomlin's First Bout",
        cost = "{1}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create a Cursed Role token attached to up to one target creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature is 1/1.)
            II — Scry 2.
            III — When you next cast an instant or sorcery spell with mana value 3 or less this turn, copy that spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        chapter(1, createRole("Cursed", target("creature")), targets = listOf(slot("creature", creature, min = 0))),
        chapter(2, scry(2)),
        chapter(3, doing(Effect.CreateDelayedTrigger(youCast(and(or(instant, sorcery), spellFilter, manaValue(Comparison.AtMost, 3))),
            Instructions(steps = listOf(doing(Effect.CopySpell(Objects.EventObject(arcanum.api.EventRole.Object), Players.You, newTargets = true)))),
            duration = Duration.EndOfTurn))),
    )

    // CR 602.5d (sorcery timing); CR 111.10n: Sorcerer Role
    @Test
    fun walkingPodium() = card(
        name = "Walking Podium",
        cost = "{1}{U}",
        type = "Artifact Creature — Construct",
        pt = "0/4",
        text = "{1}, Sacrifice this creature: Draw a card. Create a Sorcerer Role token attached to up to one other target creature you control. Activate only as a sorcery. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has \"Whenever this creature attacks, scry 1.\")",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeSelf), draw(1), createRole("Sorcerer", target("creature")),
            targets = listOf(slot("creature", and(another, creature, you), min = 0)), rules = asSorcery),
    )

    // CR 111.10q; CR 603.4 (intervening if: a creature you control with an Aura attached, CR 303.4)
    @Test
    fun ratKingsBoon() = card(
        name = "Rat King's Boon",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a Wicked Role token attached to target creature you control. (Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)
            At the beginning of your draw step, if you control an enchanted creature, you lose 1 life and you draw an additional card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createRole("Wicked", target("creature")), targets = listOf(creatureSlot)),
        triggered(stepBegins(TurnStep.Draw, PlayerFilter.You), loseLife(1), draw(1),
            interveningIf = Condition.Exists(and(creature, you, ObjectFilter.Attached(attachments = sub("Aura"))), atLeast = 1)),
    )

    // CR 603.12: "you may pay {1}. When you do, …" (a reflexive trigger with its own target); CR 111.10r
    @Test
    fun jollyMinstrels() = card(
        name = "Jolly Minstrels",
        cost = "{2}{R}",
        type = "Creature — Human Bard",
        pt = "3/2",
        text = "When this creature enters, you may pay {1}. When you do, create a Young Hero Role token attached to target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\")",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(manaCost("{1}"), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), reflexive(createRole("Young Hero", target("creature")), targets = listOf(creatureSlot)))),
    )

    // CR 111.10k: "it" is the target
    @Test
    fun beastlyTemper() = card(
        name = "Beastly Temper",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +2/+0 until end of turn. Create a Monster Role token attached to it. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 0), createRole("Monster", target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 303.4: "enchanted permanent you control" is any permanent with an Aura attached, read as written (not as "enchanted creature")
    @Test
    fun lanternwardSentry() = card(
        name = "Lanternward Sentry",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/3",
        text = "Whenever this creature attacks, untap each enchanted permanent you control.",
    ) parsesTo abilities(
        triggered(attacksSelf, untapIt(Objects.Each(and(permanentFilter, you, ObjectFilter.Attached(attachments = sub("Aura")))))),
    )
}
