package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 10: white cards. Tapped creatures, a planeswalker that looks at as many cards as your life total (CR 119), one or
 * two targets, damage assigned by toughness (CR 510.1c), creatures of an opponent becoming 1/1 Cowards for good (layers 4, 6, 7b), a card's
 * own exile cost from the graveyard (CR 113.6m), creatures that entered under your control this turn, a creature put onto the battlefield
 * attacking (CR 508.4), exile until this leaves (CR 610.3), cards returned at the next end step (CR 603.7), damage to you prevented until
 * your next turn (CR 615), a spell that exiles itself (CR 608.2n), removing any number of counters, and attacking alone (CR 506.5).
 */
class EclChunk10WhiteTest {
    private val tappedCreature = and(tapped, creature)

    @Test
    fun skilledTideshaper() = card(
        name = "Skilled Tideshaper",
        cost = "{2}{W}",
        type = "Creature — Merfolk Cleric",
        text = "Other tapped creatures you control have indestructible.",
        pt = "3/4",
    ) parsesTo abilities(
        static(has(Keyword.Indestructible), affects = Objects.Each(and(another, tapped, creature, you))),
    )

    // CR 606.3; X is your life total as the ability resolves (CR 107.3a)
    @Test
    fun varroFrontierEscort() = card(
        name = "Varro, Frontier Escort",
        cost = "{1}{W}{W}",
        type = "Legendary Planeswalker — Ajani",
        text = """
            [+1]: Create a 1/1 green and white Kithkin creature token.
            [−2]: Varro deals 4 damage to target tapped creature.
            [−8]: Look at the top X cards of your library, where X is your life total. You may put any number of nonland permanent cards with mana value 3 or less from among them onto the battlefield. Then shuffle.
        """,
        loyalty = "3",
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))), create(kithkin)),
        activated(cost(CostPart.Loyalty(n(-2))), damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", tappedCreature))),
        activated(cost(CostPart.Loyalty(n(-8))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.LifeOf(Players.You), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(nonland, permanentCard, manaValue(Comparison.AtMost, 3)), n(0), null, from = Objects.Bound("seen")),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            shuffle),
    )

    // CR 601.2c: one or two targets
    @Test
    fun pleaToSolvanne() = card(
        name = "Plea to Solvanne",
        cost = "{3}{W}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            One or two target creatures each get +2/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(pump(target("creature"), 2, 1), targets = listOf(slot("creature", creature, min = 1, max = 2))),
    )

    // CR 510.1c
    @Test
    fun rindOfTheOak() = card(
        name = "Rind of the Oak",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +0/+1.
            As long as equipped creature's toughness is greater than its power, it assigns combat damage equal to its toughness rather than its power.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(0, 1), affects = Objects.AttachedTo(Objects.Self)),
        static(Mechanic.AssignsDamageByToughness, affects = Objects.AttachedTo(Objects.Self), condition = arcanum.api.Condition.Compare(
            toughness(Objects.AttachedTo(Objects.Self)), Comparison.Greater, power(Objects.AttachedTo(Objects.Self)))),
        Keyword.Equip(manaCost("{1}")),
    )

    @Test
    fun hamletFair() = card(
        name = "Hamlet Fair",
        cost = "{2}{W}",
        type = "Kindred Enchantment — Kithkin",
        text = """
            When this enchantment enters, create two 1/1 green and white Kithkin creature tokens.
            {4}{W}: Create a 1/1 green and white Kithkin creature token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(kithkin, n(2))),
        activated(manaCost("{4}{W}"), create(kithkin)),
    )

    // layers 4, 6 and 7b, for good (CR 611.2c: the creatures are fixed as it resolves)
    @Test
    fun inquisitiveTitan() = card(
        name = "Inquisitive Titan",
        cost = "{5}{W}{W}",
        type = "Creature — Giant Warrior",
        text = "When this creature enters, each creature target opponent controls loses all abilities, becomes a Coward in addition to its other types, and has base power and toughness 1/1.",
        pt = "7/7",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.ApplyMechanics(listOf(Mechanic.LoseAbilities(), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Coward")))),
            Mechanic.SetPowerToughness(n(1), n(1))), arcanum.api.Affected.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))))),
            Duration.Indefinitely)), targets = listOf(targetOpponent)),
    )

    @Test
    fun valiantHenrider() = card(
        name = "Valiant Henrider",
        cost = "{3}{W}",
        type = "Creature — Kithkin Knight",
        text = "When this creature enters, creatures you control get +1/+0 until end of turn. Kithkin creatures you control also gain first strike until end of turn.",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf, pump(Objects.Each(creatureYouControl), 1, 0), gains(Objects.Each(and(sub("Kithkin"), creature, you)), Keyword.FirstStrike)),
    )

    // CR 113.6m: it works from the graveyard
    @Test
    fun gildfieldRover() = card(
        name = "Gildfield Rover",
        cost = "{W}",
        type = "Creature — Kithkin Scout",
        text = "{W}, Exile this card from your graveyard: Create a 1/1 green and white Kithkin creature token. Activate only as a sorcery.",
        pt = "1/2",
    ) parsesTo abilities(
        activated(cost(mana("{W}"), CostPart.Exile(arcanum.api.CostObjects.Self, from = Zone.Graveyard)), create(kithkin), rules = asSorcery),
    )

    @Test
    fun stayBack() = card(
        name = "Stay Back",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Stay Back deals 4 damage to target tapped creature.
            • Destroy target enchantment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", tappedCreature))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment)))), min = 1, max = 1, chooser = Players.You))),
    )

    // creatures that entered under your control this turn (CR 603.6a), counted as the static ability applies
    @Test
    fun kinweave() = card(
        name = "Kinweave",
        cost = "{3}{W}{W}",
        type = "Enchantment",
        text = """
            Creatures you control get +X/+X, where X is the number of creatures that entered the battlefield under your control this turn.
            At the beginning of combat on your turn, create a 1/1 green and white Kithkin creature token.
        """,
    ) parsesTo abilities(
        run {
            val x = Value.EventsThisTurn(EventPattern.ZoneChange(creatureYouControl, to = Zone.Battlefield), Measure.Objects)
            static(Mechanic.ModifyPowerToughness(x, x), affects = Objects.Each(creatureYouControl))
        },
        triggered(beginningOfCombatOnYourTurn, create(kithkin)),
    )

    // CR 508.4: put onto the battlefield attacking, never declared as an attacker
    @Test
    fun hearthscarLookout() = card(
        name = "Hearthscar Lookout",
        cost = "{1}{W}",
        type = "Creature — Kithkin Soldier",
        text = """
            First strike, lifelink
            Whenever this creature attacks, you may put a creature card with mana value X or less from your hand onto the battlefield tapped and attacking, where X is the number of attacking creatures you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Lifelink,
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, count(and(attacking, creature, you))), inZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), arcanum.api.Destination(Zone.Battlefield, tapped = true, attacking = arcanum.api.AttackTarget.Chosen)))),
    )

    // CR 610.3
    @Test
    fun thresholdGrasp() = card(
        name = "Threshold Grasp",
        cost = "{3}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile up to one target nonland permanent an opponent controls until this enchantment leaves the battlefield. You gain 2 life.",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("permanent"), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self)), gainLife(2),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents), min = 0))),
    )

    // CR 603.7: a delayed trigger; CR 615.1: a prevention effect for you; CR 608.2n: the spell exiles itself
    @Test
    fun daybreaksGlow() = card(
        name = "Daybreak's Glow",
        cost = "{3}{W}",
        type = "Sorcery",
        text = """
            Exile any number of target creatures. At the beginning of the next end step, return those cards to the battlefield tapped under their owners' control.
            Until your next turn, prevent all damage that would be dealt to you.
            Exile Daybreak's Glow.
        """,
    ) parsesTo abilities(
        spell(
            move(target("creatures"), Zone.Exile, postBind = "exiled"),
            doing(Effect.CreateDelayedTrigger(eachEndStep, Instructions(steps = listOf(
                move(Objects.Bound("exiled"), Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)))), null,
                Duration.UntilYourNextTurn)),
            move(Objects.Self, Zone.Exile),
            targets = listOf(slot("creatures", creature, min = 0, max = null))),
    )

    @Test
    fun guardingReply() = card(
        name = "Guarding Reply",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Destroy target attacking or blocking creature.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(arcanum.api.Status.Blocking)), creature)))),
    )

    // CR 702.79a; removing any number of counters: a number chosen as it resolves
    @Test
    fun caelinTheUndying() = card(
        name = "Caelin, the Undying",
        cost = "{1}{W}",
        type = "Legendary Creature — Elf Warrior",
        text = """
            Flash
            When Caelin enters, another target creature you control gains persist until end of turn. (When it dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
            {W}, {T}: Remove any number of counters from target creature you control. Activate only as a sorcery.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, gains(target("creature"), Keyword.Persist), targets = listOf(slot("creature", and(another, creature, you)))),
        activated(cost(mana("{W}"), tap),
            Step.Choose(Choice.Number(n(0), Value.Of(NumericProperty.Counters(null), target("creature"), arcanum.api.Combine.Single)), bind = "removed", chooser = Players.You),
            doing(Effect.RemoveCounters(target("creature"), null, Value.Bound("removed"))),
            targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    @Test
    fun streamwardensInstincts() = card(
        name = "Streamwarden's Instincts",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains first strike until end of turn. Untap it.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2, has(Keyword.FirstStrike)), untapIt(target("creature")), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun strandSkulker() = card(
        name = "Strand Skulker",
        cost = "{3}{W}",
        type = "Creature — Merfolk Scout",
        text = """
            Flying
            When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Surveil(Players.You, n(1)))),
    )

    @Test
    fun sunFleckedReveler() = card(
        name = "Sun-Flecked Reveler",
        cost = "{4}{W}{W}",
        type = "Creature — Treefolk Cleric",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Vigilance
        """,
        pt = "5/6",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Vigilance,
    )

    // CR 506.5: the only creature declared as an attacker
    @Test
    fun mindweaveEnchanter() = card(
        name = "Mindweave Enchanter",
        cost = "{3}{W}",
        type = "Creature — Kithkin Advisor",
        text = "Whenever a creature you control attacks alone, it gets +X/+X until end of turn, where X is the number of Kithkin you control.",
        pt = "0/5",
    ) parsesTo abilities(
        run {
            val x = count(and(sub("Kithkin"), you))
            triggered(attacksAlone(creatureYouControl), pumpBy(Objects.EventObject(arcanum.api.EventRole.Object), x, x))
        },
    )

    @Test
    fun bashfulBulwark() = card(
        name = "Bashful Bulwark",
        cost = "{1}{W}",
        type = "Creature — Kithkin Soldier",
        text = "{4}{W}: Creatures you control get +1/+1 until end of turn.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(manaCost("{4}{W}"), pump(Objects.Each(creatureYouControl), 1, 1)),
    )
}
