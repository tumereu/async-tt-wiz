package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 15: Auras with "Enchant", scry, add-mana, additional costs, costs to attack and block, attacking despite defender, and
 * cost changes.
 */
class OtjChunk15EnchantScryCostsTest {
    private val equipped: Objects = Objects.AttachedTo(Objects.Self)
    private val creatures = Objects.Each(creature)
    private val isSelf = ObjectFilter.Is(Objects.Self)
    private val diesLike = on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, to = Zone.Graveyard))
    private fun whenThisEnters(vararg steps: Step) = triggered(entersSelf, *steps)

    // CR 303.4, 702.5a: Enchant creature; CR 700.4: "put into a graveyard from the battlefield" (the Aura itself)
    @Test
    fun demonicRuckus() = card(
        name = "Furious Haunting", cost = "{1}{R}", type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has menace and trample.
            When this Aura is put into a graveyard from the battlefield, draw a card.
            Plot {R} (You may pay {R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(1, 1), has(Keyword.Menace, Keyword.Trample), affects = equipped),
        triggered(diesLike, draw(1)),
        plot("{R}"),
    )

    @Test
    fun reachForTheSky() = card(
        name = "Stretch for Clouds", cost = "{3}{G}", type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets +3/+2 and has reach.
            When this Aura is put into a graveyard from the battlefield, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(3, 2), has(Keyword.Reach), affects = equipped),
        triggered(diesLike, draw(1)),
    )

    // CR 701.26b (tapping), 613.1f (loses all abilities), 502.3 (doesn't untap)
    @Test
    fun stopCold() = card(
        name = "Frozen Standstill", cost = "{3}{U}", type = "Enchantment — Aura",
        text = """
            Flash
            Enchant artifact or creature
            When this Aura enters, tap enchanted permanent.
            Enchanted permanent loses all abilities and doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(or(artifact, creature))),
        triggered(entersSelf, tapIt(equipped)),
        static(Mechanic.LoseAbilities(), Mechanic.DoesntUntap, affects = equipped),
    )

    // CR 701.22a: scry
    @Test
    fun holyCow() = card(
        name = "Blessed Heifer", cost = "{2}{W}", type = "Creature — Ox Angel", pt = "2/2",
        text = """
            Flash
            Flying
            When this creature enters, you gain 2 life and scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
    ) parsesTo abilities(Keyword.Flash, Keyword.Flying, whenThisEnters(gainLife(2), scry(1)))

    // CR 700.1: "second spell each turn" counts the spells you cast this turn
    @Test
    fun ironFistPulverizer() = card(
        name = "Cinder-Fist Crusher", cost = "{4}{R}", type = "Creature — Giant Warrior", pt = "4/5",
        text = """
            Reach
            Whenever you cast your second spell each turn, this creature deals 2 damage to target opponent. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            damage(2, Recipients(players = Players.Target("opponent"))), scry(1), targets = listOf(targetOpponent)),
    )

    // CR 106.1a, 106.6: two mana of one chosen color, spendable only on Mount or Vehicle spells
    @Test
    fun intrepidStablemaster() = card(
        name = "Bold Wrangler", cost = "{1}{G}", type = "Creature — Human Scout", pt = "2/2",
        text = """
            Reach
            {T}: Add {G}.
            {T}: Add two mana of any one color. Spend this mana only to cast Mount or Vehicle spells.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), players = Players.You))),
        activated(cost(tap), Step.Do(Effect.AddMana(
            ManaProduction(ManaKind.Choose(n(2)), restriction = and(spellFilter, or(sub("Mount"), sub("Vehicle")))), players = Players.You))),
    )

    // CR 700.1: "first spell each turn"; mana added by a triggered ability (CR 605.1b: not a mana ability)
    @Test
    fun rodeoPyromancers() = card(
        name = "Saddle-Fire Hirelings", cost = "{3}{R}", type = "Creature — Human Mercenary", pt = "3/4",
        text = "Whenever you cast your first spell each turn, add {R}{R}.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 1),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!)), players = Players.You))),
    )

    // CR 509.1a: "can't block this turn"; CR 602.5d: sorcery-speed activation
    @Test
    fun sandstormVerge() = card(
        name = "Dune-Scoured Verge", cost = null, type = "Land — Desert",
        text = """
            {T}: Add {C}.
            {3}, {T}: Target creature can't block this turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), players = Players.You))),
        activated(cost(mana("{3}"), tap), untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 601.2b, 601.2h: the sacrificed creature is chosen as the spell is cast
    @Test
    fun corruptedConviction() = card(
        name = "Grim Bargain", cost = "{B}", type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Draw two cards.
        """,
    ) parsesTo abilities(static(Mechanic.AdditionalCost(cost = Cost(listOf(sacrificeA(creature))))), spell(draw(2)))

    // CR 601.3b: flash with an extra {2}; CR 610.3: exile until it leaves
    @Test
    fun mysticalTether() = card(
        name = "Spectral Shackle", cost = "{2}{W}", type = "Enchantment",
        text = """
            You may cast this spell as though it had flash if you pay {2} more to cast it.
            When this enchantment enters, exile target artifact or creature an opponent controls until this enchantment leaves the battlefield.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(mana("{2}")), optional = true, flash = true)),
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
    )

    // CR 700.2: modal triggered ability; CR 603.2
    @Test
    fun ferocification() = card(
        name = "Warband Frenzy", cost = "{2}{R}", type = "Enchantment",
        text = """
            At the beginning of combat on your turn, choose one —
            • Target creature you control gets +2/+0 until end of turn.
            • Target creature you control gains menace and haste until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(beginningOfCombatOnYourTurn, Instructions(modes = modes(
            mode(pump(target("creature"), 2, 0), targets = listOf(slot("creature", and(creature, you)))),
            mode(gains(target("creature"), Keyword.Menace, Keyword.Haste), targets = listOf(slot("creature", and(creature, you)))),
        ))),
    )

    // CR 508.1: "defending player" is the player the attacking creature attacks
    @Test
    fun springSplasher() = card(
        name = "Puddle Hopper", cost = "{1}{U}", type = "Creature — Frog Beast", pt = "2/1",
        text = "Whenever this creature attacks, target creature defending player controls gets -3/-0 until end of turn.",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(target("creature"), -3, 0),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))),
    )

    // CR 508.1h, 509.1d: costs to attack a player or planeswalker and to block, each for every creature
    @Test
    fun archangelOfTithes() = card(
        name = "Seraph of Tolls", cost = "{1}{W}{W}{W}", type = "Creature — Angel", pt = "3/5",
        text = """
            Flying
            As long as this creature is untapped, creatures can't attack you or planeswalkers you control unless their controller pays {1} for each of those creatures.
            As long as this creature is attacking, creatures can't block unless their controller pays {1} for each of those creatures.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.AttackCost(manaCost("{1}"), Recipients(players = Players.You,
            objects = Objects.Each(and(ObjectFilter.Type(CardType.Planeswalker), you))))), Affected.Objects(creatures),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped)))),
        Ability.Static(listOf(Mechanic.BlockCost(manaCost("{1}"))), Affected.Objects(creatures),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Attacking))),
    )

    // CR 702.3b-style permission: "can attack as though it didn't have defender" while a condition holds
    @Test
    fun bristlepackSentry() = card(
        name = "Thicket Warden", cost = "{1}{G}", type = "Creature — Plant Wolf", pt = "3/3",
        text = """
            Defender
            As long as you control a creature with power 4 or greater, this creature can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(Mechanic.CanAttackDespiteDefender, condition = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), atLeast = 1)),
    )

    // CR 601.2f: a cost reduction that applies only when its condition holds
    @Test
    fun cactarantula() = card(
        name = "Dune Weaver", cost = "{4}{G}{G}", type = "Creature — Plant Spider", pt = "6/5",
        text = """
            This spell costs {1} less to cast if you control a Desert.
            Reach
            Whenever this creature becomes the target of a spell or ability an opponent controls, you may draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{1}")!!, n(1))), condition = Condition.Exists(and(sub("Desert"), you), atLeast = 1)),
        Keyword.Reach,
        triggered(on(EventPattern.BecomesTarget(isSelf, opponents)), Step.May(listOf(draw(1)), Players.You)),
    )

    // CR 601.2f: the reduction applies when any chosen target is a permanent you control
    @Test
    fun thisTownAintBigEnough() = card(
        name = "Not Enough Room", cost = "{4}{U}", type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a permanent you control.
            Return up to two target nonland permanents to their owners' hands.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.Compare(Value.Count(Objects.Where(target("permanents"), and(ObjectFilter.Category(ObjectCategory.Permanent), you))),
                Comparison.AtLeast, n(1))),
        spell(move(target("permanents"), Zone.Hand), targets = listOf(slot("permanents", and(nonland, ObjectFilter.Category(ObjectCategory.Permanent)), min = 0, max = 2))),
    )
}
