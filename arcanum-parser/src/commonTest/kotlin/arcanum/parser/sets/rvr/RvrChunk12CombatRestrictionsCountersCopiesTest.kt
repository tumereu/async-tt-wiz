package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica chunk 12: attack costs, blocking restrictions, uncounterable spells, flash permissions, cast restrictions, cost
 * changes, counterspells and copying spells. */
class RvrChunk12CombatRestrictionsCountersCopiesTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val instantOrSorcery = or(instant, sorcery)
    private val targetSpell = Objects.Target("spell")
    private fun flying() = Keyword.Flying

    // CR 508.1h (a cost to attack, X counted on payment), 508.1d
    @Test
    fun sphereOfSafetyLookalike() = card(
        name = "Sphere of Quiet", cost = "{4}{W}", type = "Enchantment",
        text = "Creatures can't attack you or planeswalkers you control unless their controller pays {X} for each of those creatures, where X is the number of enchantments you control.",
    ) parsesTo abilities(
        static(Mechanic.AttackCost(
            Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Count(Objects.Each(and(enchantment, you)))))),
            target = Recipients(objects = Objects.Each(and(planeswalker, you)), players = Players.You)),
            affects = Objects.Each(creature)),
    )

    // CR 702.11b (hexproof), 509.1b (blocked only by creatures with flying)
    @Test
    fun silhanaLedgewalkerLookalike() = card(
        name = "Silhana Ledgeskulker", cost = "{1}{G}", type = "Creature — Elf Rogue", pt = "1/1",
        text = """
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            This creature can't be blocked except by creatures with flying.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        static(Mechanic.CanBeBlockedOnlyBy(and(creature, ObjectFilter.HasKeyword(flying())))),
    )

    // CR 508.1b (can only attack alone: not with other creatures)
    @Test
    fun masterOfCrueltiesLookalike() = card(
        name = "Master of Cruelty", cost = "{3}{B}{R}", type = "Creature — Demon", pt = "1/4",
        text = """
            First strike, deathtouch
            This creature can only attack alone.
            Whenever this creature attacks a player and isn't blocked, that player's life total becomes 1. This creature assigns no combat damage this combat.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike, Keyword.Deathtouch,
        static(Mechanic.CanOnlyAttackAlone),
        triggered(on(EventPattern.AttacksUnblocked(ObjectFilter.Is(Objects.Self), target = RecipientFilter(players = PlayerFilter.Any))),
            doing(Effect.SetLife(Players.EventPlayer, n(1))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.AssignsNoCombatDamage), Affected.Objects(Objects.Self), Duration.EndOfCombat))),
    )

    // CR 509.1b (can't be blocked)
    @Test
    fun keymasterRogueLookalike() = card(
        name = "Keymaster Scoundrel", cost = "{3}{U}", type = "Creature — Human Rogue", pt = "3/2",
        text = """
            This creature can't be blocked.
            When this creature enters, return a creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked),
        triggered(entersSelf,
            Step.Choose(choice = Choice.Objects(and(creature, you), min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    // CR 702.41a (affinity for Gates), 509.1b (can't be blocked by power 2 or less), 603.6c
    @Test
    fun gateColossusLookalike() = card(
        name = "Gate Behemoth", cost = "{8}", type = "Artifact Creature — Construct", pt = "8/8",
        text = """
            Affinity for Gates (This spell costs {1} less to cast for each Gate you control.)
            This creature can't be blocked by creatures with power 2 or less.
            Whenever a Gate you control enters, you may put this card from your graveyard on top of your library.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Gate"), "Gates"),
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
        triggered(enters(and(sub("Gate"), you)),
            Step.May(listOf(move(Objects.Self, Zone.Library, position = 0)), player = Players.You),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 101.2 (can't beats can), 701.6a; the spell itself can't be countered (CR 101.3)
    @Test
    fun nivMizzetParunLookalike() = card(
        name = "Niv-Mizzet, Prime", cost = "{U}{U}{U}{R}{R}{R}", type = "Legendary Creature — Dragon Wizard", pt = "5/5",
        text = """
            This spell can't be countered.
            Flying
            Whenever you draw a card, Niv-Mizzet deals 1 damage to any target.
            Whenever a player casts an instant or sorcery spell, you draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Flying,
        triggered(on(EventPattern.Draw(PlayerFilter.You)), damage(1, toAny), targets = listOf(anyTarget)),
        triggered(on(EventPattern.Cast(instantOrSorcery, PlayerFilter.Any)), draw(1)),
    )

    // CR 101.3 (spells you control can't be countered), 702.136a (riot)
    @Test
    fun rhythmOfTheWildLookalike() = card(
        name = "Rhythm of the Plains", cost = "{1}{R}{G}", type = "Enchantment",
        text = """
            Creature spells you control can't be countered.
            Nontoken creatures you control have riot. (They enter with your choice of a +1/+1 counter or haste.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered, affects = Objects.Each(and(creature, spellFilter, you))),
        static(has(Keyword.Riot), affects = Objects.Each(and(not(token), creature, you))),
    )

    @Test
    fun supremeVerdictLookalike() = card(
        name = "Supreme Judgment", cost = "{1}{W}{W}{U}", type = "Sorcery",
        text = """
            This spell can't be countered.
            Destroy all creatures.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        spell(destroy(Objects.Each(creature))),
    )

    // CR 702.8a-style permission: cast green creature spells as though they had flash
    @Test
    fun yevaNaturesHeraldLookalike() = card(
        name = "Yeva, Nature's Warden", cost = "{2}{G}{G}", type = "Legendary Creature — Elf Shaman", pt = "4/4",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            You may cast green creature spells as though they had flash.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(listOf(Mechanic.CastAsThoughFlash(and(ObjectFilter.OfColor(Color.Green), creature, spellFilter))), affects = Affected.Players(Players.You)),
    )

    // CR 601.3 (a restriction on casting), 119.3, 601.2f (a cost reduced by life lost)
    @Test
    fun rakdosLordOfRiotsLookalike() = card(
        name = "Rakdos, Lord of Revels", cost = "{B}{B}{R}{R}", type = "Legendary Creature — Demon", pt = "6/6",
        text = """
            You can't cast Rakdos unless an opponent lost life this turn.
            Flying, trample
            Creature spells you cast cost {1} less to cast for each 1 life your opponents have lost this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CastRestriction(condition = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events))),
        Keyword.Flying,
        Keyword.Trample,
        static(Mechanic.CostChange(and(creature, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, Value.EventsThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), Measure.Amount)))),
    )

    // CR 709.3 (each half of a split card on its own), 707.10c (new targets for the copy), 107.3a
    @Test
    fun expansionExplosionLookalike() = cardFaces(
        layout = Layout.Split,
        card(
            name = "Expansion", cost = "{U/R}{U/R}", type = "Instant",
            text = "Copy target instant or sorcery spell with mana value 4 or less. You may choose new targets for the copy.",
        ) to abilities(
            spell(doing(Effect.CopySpell(targetSpell, controller = Players.You, newTargets = true)),
                targets = listOf(slot("spell", and(instantOrSorcery, spellFilter, manaValue(Comparison.AtMost, 4))))),
        ),
        card(
            name = "Explosion", cost = "{X}{U}{U}{R}{R}", type = "Instant",
            text = "Explosion deals X damage to any target. Target player draws X cards.",
        ) to abilities(
            spell(damage(Value.Announced("X"), toAny), Step.Do(Effect.Draw(Players.Target("player"), Value.Announced("X"))),
                targets = listOf(anyTarget, targetPlayer), announced = listOf("X")),
        ),
    )

    // CR 707.10 (copying a spell: instant spell with mana value 2 or less), 702.. hybrid mana in the cost
    @Test
    fun izzetGuildmageLookalike() = card(
        name = "Izzet Guildmender", cost = "{U/R}{U/R}", type = "Creature — Human Wizard", pt = "2/2",
        text = """
            ({U/R} can be paid with either {U} or {R}.)
            {2}{U}: Copy target instant spell you control with mana value 2 or less. You may choose new targets for the copy.
            {2}{R}: Copy target sorcery spell you control with mana value 2 or less. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}{U}")), doing(Effect.CopySpell(targetSpell, controller = Players.You, newTargets = true)),
            targets = listOf(slot("spell", and(instant, spellFilter, you, manaValue(Comparison.AtMost, 2))))),
        activated(cost(mana("{2}{R}")), doing(Effect.CopySpell(targetSpell, controller = Players.You, newTargets = true)),
            targets = listOf(slot("spell", and(sorcery, spellFilter, you, manaValue(Comparison.AtMost, 2))))),
    )

    // CR 601.2f (cost reduction for a spell class)
    @Test
    fun goblinElectromancerLookalike() = card(
        name = "Goblin Electrocutor", cost = "{U}{R}", type = "Creature — Goblin Wizard", pt = "2/2",
        text = "Instant and sorcery spells you cast cost {1} less to cast.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(instantOrSorcery, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 601.2f (a cost increase only for spells that target it)
    @Test
    fun sphinxOfNewPrahvLookalike() = card(
        name = "Sphinx of Old Prahv", cost = "{W}{W}{U}{U}", type = "Creature — Sphinx", pt = "4/3",
        text = """
            Flying, vigilance
            Spells your opponents cast that target this creature cost {2} more to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Vigilance,
        static(Mechanic.CostChange(and(opponents, ObjectFilter.Targets(objects = Objects.Self)), CostChange.More(ManaCost.parse("{2}")!!, n(1)))),
    )

    // CR 701.6a (counter), 603.1
    @Test
    fun frilledMysticLookalike() = card(
        name = "Frilled Seer", cost = "{G}{G}{U}{U}", type = "Creature — Elf Lizard Wizard", pt = "3/2",
        text = """
            Flash
            When this creature enters, you may counter target spell.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.May(listOf(Step.Do(Effect.Counter(targetSpell))), player = Players.You),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 700.2 (modes), 118.12 (counter unless its controller pays)
    @Test
    fun izzetCharmLookalike() = card(
        name = "Izzet Talisman", cost = "{U}{R}", type = "Instant",
        text = """
            Choose one —
            • Counter target noncreature spell unless its controller pays {2}.
            • Izzet Talisman deals 2 damage to target creature.
            • Draw two cards, then discard two cards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                mode(
                    Step.Pay(cost = manaCost("{2}"), payer = Players.ControllerOf(targetSpell), bind = "paid"),
                    Step.If(Condition.Not(Condition.Did("paid")), listOf(Step.Do(Effect.Counter(targetSpell)))),
                    targets = listOf(slot("spell", and(noncreatureSpell, spellFilter)))),
                mode(damage(2, Recipients(objects = Objects.Target("creature"))), targets = listOf(slot("creature", creature))),
                mode(draw(2), discard(2)),
            ),
            min = 1, max = 1, repeatable = false, chooser = Players.You,
        ))),
    )

    // CR 701.6a, 614.1 (put into its owner's hand instead of the graveyard)
    @Test
    fun remandLookalike() = card(
        name = "Remind", cost = "{1}{U}", type = "Instant",
        text = """
            Counter target spell. If that spell is countered this way, put it into its owner's hand instead of into that player's graveyard.
            Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("spell", spellFilter)),
            steps = listOf(Step.Do(Effect.Counter(targetSpell)), draw(1)),
            replacements = listOf(Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(targetSpell), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Hand))))),
        )),
    )

    // CR 701.6a, 112.3 (an ability on the stack can be countered)
    @Test
    fun voidslimeLookalike() = card(
        name = "Voidgoo", cost = "{G}{U}{U}", type = "Instant",
        text = "Counter target spell, activated ability, or triggered ability. (Mana abilities can't be targeted.)",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(targetSpell)), targets = listOf(slot("spell", or(
            spellFilter,
            and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
            and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered)))))),
    )
}
