package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 12: restrictions and costs: ward, scry, can't block / can't be blocked / can't attack alone, can't be countered,
 * cost changes, mana with a spending restriction, and the cards around them.
 */
class VowChunk12RestrictionsTest {
    // CR 702.21a (ward), 614.1c (enters with counters), 122.1, 111.1 (nontoken), 608.2h (counted as it enters)
    @Test
    fun hamlet() = card(
        name = "Thornhedge Vanguard", cost = "{2}{G}", type = "Creature — Human Warrior", pt = "1/1",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            This creature enters with two +1/+1 counters on it for each other nontoken Human you control.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1,
                Value.Times(count(and(another, not(token), sub("Human"), you)), 2))))),
    )

    // CR 702.21a, 509.1b (can't be blocked), 602.5b (activate only if), 400.3 (owner of the exiled cards)
    @Test
    fun dread() = card(
        name = "Gloomtide Monstrosity", cost = "{4}{U}{U}", type = "Creature — Crab Horror", pt = "5/5",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            {3}{U}{U}: This creature can't be blocked this turn. Activate only if you own a card in exile.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        activated(manaCost("{3}{U}{U}"), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked),
            rules = anyTime.copy(condition = Condition.Exists(and(cardFilter, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))),
    )

    // CR 506.4 (attacking or blocking), 120.3, 701.22a (scry)
    @Test
    fun piercing() = card(
        name = "Needle of Dawn", cost = "{W}", type = "Instant",
        text = "Needle of Dawn deals 2 damage to target attacking or blocking creature. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(damage(2, toObjects(target("creature"))), scry(1),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature)))),
    )

    // CR 106.1b (any color), 602.2 (a cost of {T}), 701.13 (exile a card from a graveyard)
    @Test
    fun heirloom() = card(
        name = "Venerated Keepsake", cost = "{3}", type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            {2}, {T}: Exile target card from a graveyard.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
        activated(cost(mana("{2}"), tap), move(target("card"), Zone.Exile, postBind = "exiled"),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 106.6 (spend only on), 118.2 (life as a cost), 602.2b + 601.2f (cost reduction of an ability), 111.10g
    @Test
    fun voldaren() = card(
        name = "Crimson Manor", cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast a Vampire spell.
            {5}, {T}: Create a Blood token. This ability costs {1} less to activate for each Vampire you control. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap, CostPart.PayLife(n(1))),
            doing(Effect.AddMana(ManaProduction(anyOneColor(1), restriction = and(spellFilter, sub("Vampire"))), Players.You))),
        activated(cost(mana("{5}"), tap), create(blood),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(and(sub("Vampire"), you))))),
    )

    // CR 101.2 (can't be countered), 702.36 (flash), 700.2 (modal trigger, up to one), 115.1
    @Test
    fun hull() = card(
        name = "Keelsplitter Horror", cost = "{5}{U}{U}", type = "Creature — Kraken Horror", pt = "7/8",
        text = """
            Flash
            This spell can't be countered.
            Whenever you cast a spell, choose up to one —
            • Return target spell you don't control to its owner's hand.
            • Return target nonland permanent to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.CantBeCountered),
        Ability.Triggered(youCast(ObjectFilter.Any), Instructions(modes = modes(
                mode(move(target("spell"), Zone.Hand), targets = listOf(slot("spell", and(spellFilter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
                mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
                min = 0, max = 1))),
    )

    // CR 509.1b (can't block), 603.2c (one or more), 603.4 + 603.2 (once each turn), 701.21 (sacrifice)
    @Test
    fun hypno() = card(
        name = "Gore Mesmerist", cost = "{2}{R}", type = "Creature — Vampire", pt = "3/3",
        text = """
            This creature can't block.
            Whenever you sacrifice one or more Blood tokens, target creature can't block this turn. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(Trigger.On(EventPattern.Sacrifice(and(sub("Blood"), token, you)), oneOrMore = true),
            untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creature)), perTurn = 1),
    )

    // CR 601.2f (cost reduction if a condition holds), 502.3 (doesn't untap during the next untap step), 701.26a (tap)
    @Test
    fun chill() = card(
        name = "Frost of the Barrow", cost = "{2}{U}", type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a Zombie.
            Tap target creature. It doesn't untap during its controller's next untap step.
            Draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(sub("Zombie"), you), atLeast = 1)),
        spell(tapIt(target("creature")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(target("creature")),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(target("creature"))))),
            draw(1), targets = listOf(slot("creature", creature))),
    )

    // CR 509.1b (can't block), 510.2, 111.10g, 602.2 (sacrifice two as a cost), 400.7 (returned from the graveyard)
    @Test
    fun forebear() = card(
        name = "Ravenmoor Forebear", cost = "{2}{B}", type = "Creature — Vampire", pt = "3/1",
        text = """
            Flying
            This creature can't block.
            Whenever this creature deals combat damage to a player, create a Blood token.
            {B}, Sacrifice two Blood tokens: Return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(combatDamageToAPlayer, create(blood)),
        activated(cost(mana("{B}"), CostPart.Sacrifice(CostObjects.Choose(and(sub("Blood"), token), count = n(2)))),
            move(Objects.Self, Zone.Battlefield, postBind = "returned")),
    )

    // CR 506.2 (attack alone: "can't attack alone" needs another attacker), 508.1c, 603.2 (attack trigger), 701.26b (untap)
    @Test
    fun militiaRallier() = card(
        name = "Muster Rallier", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "3/3",
        text = """
            This creature can't attack alone.
            Whenever this creature attacks, untap target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantAttackAlone),
        triggered(attacksSelf, untapIt(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 601.2f (a static cost increase for spells of every player), 702.7b (first strike)
    @Test
    fun thalia() = card(
        name = "Thessa, Warden of the Pass", cost = "{1}{W}", type = "Legendary Creature — Human Soldier", pt = "2/1",
        text = """
            First strike
            Noncreature spells cost {1} more to cast.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        static(Mechanic.CostChange(what = and(not(creature), spellFilter), change = CostChange.More(ManaCost.parse("{1}")!!, times = n(1)))),
    )
}
