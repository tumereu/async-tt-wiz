package arcanum.parser.sets.dmr

import arcanum.api.DamageOccurrence
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria Remastered chunk 02: Auras (enchant, CR 702.5, 303.4): static bonuses and restrictions on the enchanted object, abilities
 * granted to it, triggers on what it does, and the Mirage-era "as though it had flash" sacrifice at the next cleanup step.
 */
class DmrChunk02EnchantTest {
    // CR 702.8a, 613.4c, 502.3: two effects of one static ability on the enchanted creature
    @Test
    fun ironweightCuffs() = card(
        name = "Ironweight Cuffs",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Enchant creature
            Enchanted creature gets +3/+3 and doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(creature),
        static(pt(3, 3), Mechanic.DoesntUntap, affects = enchanted),
    )

    // CR 702.8a, 307.1, 514.3a, 400.7d: cast any time a sorcery couldn't have been, the permanent it became is sacrificed at the next
    // cleanup step by its controller then
    @Test
    fun sparkstepInstinct() = card(
        name = "Sparkstep Instinct",
        cost = "{1}{R}",
        type = "Enchantment — Aura",
        text = """
            You may cast this spell as though it had flash. If you cast it any time a sorcery couldn't have been cast, the controller of the permanent it becomes sacrifices it at the beginning of the next cleanup step.
            Enchant creature
            Enchanted creature gets +1/+0 and has first strike.
        """,
    ) parsesTo abilities(
        static(Mechanic.InstantSpeed),
        triggered(stepBegins(TurnStep.Cleanup, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Self)),
            interveningIf = Condition.CastOutsideSorceryTiming(Objects.Self)).copy(once = true),
        enchant(creature),
        static(pt(1, 0), has(Keyword.FirstStrike), affects = enchanted),
    )

    // CR 509.1c, 207.2c: threshold; every creature able to block the enchanted creature does so
    @Test
    fun huntersBeckoning() = card(
        name = "Hunter's Beckoning",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2.
            Threshold — As long as there are seven or more cards in your graveyard, all creatures able to block enchanted creature do so.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 2), affects = enchanted),
        static(Mechanic.MustBlock(ObjectFilter.Is(enchanted)), affects = Objects.Each(creature), condition = threshold).copy(abilityWord = "Threshold"),
    )

    // CR 120.1, 603.2: any damage it deals, combat or not; "that much" is the damage dealt; CR 603.2c: once for all it deals at once
    @Test
    fun kindredLifebond() = card(
        name = "Kindred Lifebond",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature (Target a creature as you cast this. This card enters attached to that creature.)
            Whenever enchanted creature deals damage, you gain that much life.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(on(EventPattern.Damage(ObjectFilter.Is(enchanted), RecipientFilter(), per = DamageOccurrence.Source)), doing(Effect.GainLife(Players.You, Value.EventAmount))),
    )

    // CR 602.1: an Aura's activated ability returns the creature it enchants
    @Test
    fun dawnBrace() = card(
        name = "Dawn Brace",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+3.
            {W}: Return enchanted creature to its owner's hand.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(1, 3), affects = enchanted),
        activated(manaCost("{W}"), move(enchanted, Zone.Hand)),
    )

    // CR 605.1b, 106.12a: a triggered mana ability; the tapped land's controller adds the mana
    @Test
    fun rampantTendril() = card(
        name = "Rampant Tendril",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Whenever enchanted land is tapped for mana, its controller adds an additional {G}.
        """,
    ) parsesTo abilities(
        enchant(land),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(enchanted), forMana = true)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
    )

    // CR 303.4, 613.1b: a control-changing static ability (layer 2)
    @Test
    fun usurpedAllegiance() = card(
        name = "Usurped Allegiance",
        cost = "{4}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant permanent
            You control enchanted permanent.
        """,
    ) parsesTo abilities(
        enchant(permanentFilter),
        static(Mechanic.ControlledBy(Players.You), affects = enchanted),
    )

    // CR 700.4, 603.10a: a leaves-the-battlefield trigger of the Aura on the creature it enchanted dying
    @Test
    fun rocEscort() = card(
        name = "Roc Escort",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has flying.
            When enchanted creature dies, create a 2/2 white Griffin creature token with flying.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 2), has(Keyword.Flying), affects = enchanted),
        triggered(dies(ObjectFilter.Is(enchanted)), create(creatureToken("Griffin", listOf(Color.White), 2, 2, abilities = listOf(Keyword.Flying)))),
    )

    // CR 113.10, 613.1f: an activated ability granted to the enchanted creature; "this creature" is that creature
    @Test
    fun runicTutorship() = card(
        name = "Runic Tutorship",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has "{T}: This creature deals 1 damage to any target."
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(has(activated(cost(tap), damage(1, toAny), targets = listOf(anyTarget))), affects = enchanted),
    )

    // CR 508.1c, 509.1a
    @Test
    fun quelledSpirit() = card(
        name = "Quelled Spirit",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(attackers = ObjectFilter.Any), affects = enchanted),
    )

    // CR 113.10, 115.1: a granted ability targeting a player or planeswalker, and its controller draws
    @Test
    fun mercuryShiv() = card(
        name = "Mercury Shiv",
        cost = "{1}{U}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has "{T}: This creature deals 1 damage to target player or planeswalker. You draw a card."
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(has(activated(cost(tap), damage(1, Recipients(target = "player or planeswalker")), draw(1),
            targets = listOf(TargetSlot("player or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Any),
                min = n(1), max = n(1))))), affects = enchanted),
    )

    // CR 113.10, 111.1: a land granted a token-making tap ability
    @Test
    fun squirrelHollow() = card(
        name = "Squirrel Hollow",
        cost = "{1}{G}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "{T}: Create a 1/1 green Squirrel creature token."
        """,
    ) parsesTo abilities(
        enchant(land),
        static(has(activated(cost(tap), create(creatureToken("Squirrel", listOf(Color.Green), 1, 1)))), affects = enchanted),
    )

    // CR 613.4c, 704.5f: a toughness loss that can kill
    @Test
    fun warpedInfusion() = card(
        name = "Warped Infusion",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +3/-1.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(3, -1), affects = enchanted),
    )

    // CR 603.6c, 400.7e: the Aura's own leaves-the-battlefield trigger finds it in the graveyard
    @Test
    fun unquenchedFury() = card(
        name = "Unquenched Fury",
        cost = "{2}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and can't block.
            When this Aura is put into a graveyard from the battlefield, return it to its owner's hand.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 2), Mechanic.CantBlock(attackers = ObjectFilter.Any), affects = enchanted),
        triggered(diesSelf, move(Objects.EventObject(EventRole.After), Zone.Hand)),
    )
}
