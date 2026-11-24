package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 10: effects that apply until end of turn: gains and pumps for the turn (trample, first strike, indestructible, lifelink), control
 * for a turn, "until end of turn" replacement (exile instead of dying), becoming a creature or another kind of creature, defender lifted for a turn.
 */
class VowChunk10ApplyTest {
    private val human = sub("Human")
    private val namedInGraveyard = Value.Sum(listOf(n(1), Value.Count(Objects.Each(inYourGraveyard(ObjectFilter.Named("Ancestral Malice"))))))

    // CR 107.3c: X counted on resolution (CR 201.2: the same name)
    @Test
    fun ancestralMalice() = card(
        name = "Ancestral Malice",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Target creature gains trample and gets +X/+0 until end of turn, where X is 1 plus the number of cards named Ancestral Malice in your graveyard.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), has(Keyword.Trample), Mechanic.ModifyPowerToughness(namedInGraveyard, n(0))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 613.4c (-13/-13), 614.1a / 614.6 (dies this turn: exile instead), 700.4
    @Test
    fun witheringDusk() = card(
        name = "Withering Dusk",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = "Target creature gets -13/-13 until end of turn. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), -13, -13),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 701.12 (control), 302.6, 702.10: untap and haste; CR 111.10g: a Blood token
    @Test
    fun crimsonTreachery() = card(
        name = "Crimson Treachery",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. Create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            create(blood),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 603.2: "another Human you control" excludes this creature
    @Test
    fun dawnmoorDisciple() = card(
        name = "Dawnmoor Disciple",
        cost = "{1}{G}",
        type = "Creature — Human Warlock",
        pt = "2/2",
        text = "Whenever another Human you control enters, this creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(triggered(enters(and(another, human, you)), pump(Objects.Self, 1, 1)))

    // CR 702.10 (haste), 603.2
    @Test
    fun dawnbreakFighters() = card(
        name = "Dawnbreak Fighters",
        cost = "{2}{R}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            When this creature enters, target creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, pump(target("creature"), 2, 0), targets = listOf(slot("creature", creature))),
    )

    // CR 701.12, 302.6, 702.10; CR 202.3 (mana value compared with the number of Vampires you control, counted as the effect is applied)
    @Test
    fun overbearingVampire() = card(
        name = "Overbearing Vampire",
        cost = "{1}{R}{R}",
        type = "Creature — Vampire",
        pt = "3/3",
        text = "When this creature enters, gain control of target creature with mana value less than or equal to the number of Vampires you control until end of turn. Untap that creature. It gains haste until end of turn.",
    ) parsesTo abilities(
        triggered(entersSelf,
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ManaValue(Comparison.AtMost, count(and(sub("Vampire"), you))))))),
    )

    // CR 603.2, 118.12 (an optional payment), 702.12a (indestructible)
    @Test
    fun estmoorShieldbreaker() = card(
        name = "Estmoor Shieldbreaker",
        cost = "{3}{W}",
        type = "Creature — Human Soldier",
        pt = "4/2",
        text = "Whenever this creature attacks, you may pay {1}. If you do, it gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(mana("{1}")), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), gains(Objects.Self, Keyword.Indestructible))),
    )

    // CR 602.1, 702.7 (first strike); CR 118.3 (exiling three cards as a cost); CR 603.7 (a delayed trigger at the next end step), 400.7
    @Test
    fun waveringSpirit() = card(
        name = "Wavering Spirit",
        cost = "{1}{W}",
        type = "Creature — Spirit",
        pt = "3/1",
        text = """
            {W}, Exile three cards from your graveyard: This creature gains first strike until end of turn.
            Discard a card: Exile this creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{W}"), CostPart.Exile(CostObjects.Choose(cardFilter, count = n(3)), from = Zone.Graveyard)),
            gains(Objects.Self, Keyword.FirstStrike)),
        activated(cost(discardACard),
            doing(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))))))),
    )

    // CR 603.4, 117.1 ("whenever you cast"), 613.4c (+2/+2)
    @Test
    fun madDevils() = card(
        name = "Mad Devils",
        cost = "{4}{R}",
        type = "Creature — Devil",
        pt = "3/3",
        text = """
            Haste
            Whenever you cast a noncreature spell, this creature gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(youCast(noncreatureSpell), pump(Objects.Self, 2, 2)),
    )

    // CR 301.7 (Vehicles), 702.122 / 205.1 (becomes an artifact creature); the cost taps two untapped creatures its controller controls (CR 118.3)
    @Test
    fun bridalHearse() = card(
        name = "Bridal Hearse",
        cost = "{2}{R}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            Trample
            Tap two untapped creatures you control: This Vehicle becomes an artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, n(2)))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
                Affected.Objects(Objects.Self), Duration.EndOfTurn))),
    )

    // CR 507.1 (beginning of combat), 603.2; CR 700.9 and 205.3 (Wolf or Werewolf); 603.4 (an opponent's second spell each turn); CR 111.10 (a token)
    @Test
    fun bayingMoon() = card(
        name = "Baying Moon",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            At the beginning of combat on your turn, target Wolf or Werewolf you control gets +2/+2 until end of turn.
            Whenever an opponent casts their second spell each turn, create a 2/2 green Wolf creature token.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(target("permanent"), 2, 2),
            targets = listOf(slot("permanent", and(or(sub("Wolf"), sub("Werewolf")), you)))),
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)), nthThisTurn = 2),
            create(creatureToken("Wolf", listOf(Color.Green), 2, 2))),
    )

    // CR 602.5d (activate only as a sorcery), 702.7
    @Test
    fun lightningHound() = card(
        name = "Lightning Hound",
        cost = "{3}{R}",
        type = "Creature — Wolf",
        pt = "4/3",
        text = "{1}{R}: This creature gains first strike until end of turn. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(manaCost("{1}{R}"), gains(Objects.Self, Keyword.FirstStrike), rules = asSorcery),
    )

    // CR 700.2 (one or both: one or two modes), 120.3 (damage equal to its power), 115.3 (another target)
    @Test
    fun markovReprisal() = card(
        name = "Markov Reprisal",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Creatures you control get +1/+0 until end of turn.
            • Target Vampire you control deals damage equal to its power to another target creature.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(pump(Objects.Each(creatureYouControl), 1, 0)),
            mode(damage(power(target("Vampire")), toObjects(target("creature")), target("Vampire")),
                targets = listOf(slot("Vampire", and(sub("Vampire"), you)),
                    TargetSlot("creature", TargetKind.Objects(creature), min = n(1), max = n(1), distinctFrom = listOf("Vampire")))),
            min = 1, max = 2))),
    )

    // CR 115.1d: "up to two" targets, each gets the bonus
    @Test
    fun markovDancer() = card(
        name = "Markov Dancer",
        cost = "{2}{R}{W}",
        type = "Creature — Vampire",
        pt = "1/3",
        text = """
            Flying, haste
            At the beginning of combat on your turn, up to two target creatures you control each get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Haste,
        triggered(beginningOfCombatOnYourTurn, pump(target("creatures"), 1, 0), targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
    )

    // CR 602.1 (sacrifice as a cost), 702.15 (lifelink), 702.12 (indestructible), 122.1 (a +1/+1 counter)
    @Test
    fun ollenmoorEscort() = card(
        name = "Ollenmoor Escort",
        cost = "{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = """
            Vigilance
            Sacrifice this creature: Target creature you control with a +1/+1 counter on it gains lifelink and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(sacrificeSelf), gains(target("creature"), Keyword.Lifelink, Keyword.Indestructible),
            targets = listOf(slot("creature", and(creatureYouControl, ObjectFilter.HasCounter(p1p1, atLeast = 1))))),
    )

    // CR 613.1d, 613.1e, 613.4b: becomes blue (colour), Serpent (type), base 5/5
    @Test
    fun serpentsAmbush() = card(
        name = "Serpent's Ambush",
        cost = "{1}{U}",
        type = "Instant",
        text = "Until end of turn, target creature becomes a blue Serpent with base power and toughness 5/5.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Serpent"))), replaceSubtypes = true),
                Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue))), Mechanic.SetPowerToughness(n(5), n(5))), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    /** "Can attack this turn as though it didn't have defender" lifts defender's restriction (CR 702.3b). */
    @Test
    fun ironboundSpirit() = card(
        name = "Ironbound Spirit",
        cost = "{1}{U}",
        type = "Creature — Spirit",
        pt = "3/3",
        text = """
            Defender
            Whenever an enchantment you control enters, this creature can attack this turn as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(enters(and(enchantment, you)), untilEndOfTurn(Objects.Self, Mechanic.CanAttackDespiteDefender)),
    )

    // CR 602.5d, 613.4c, 119.1 (gain life); the pump and the life gain are one ability
    @Test
    fun wanderingMinister() = card(
        name = "Wandering Minister",
        cost = "{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = "{T}: Target creature gets +1/+0 until end of turn. You gain 1 life. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(tap), pump(target("creature"), 1, 0), gainLife(1), targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 700.2, 702.12a, 701.8 (destroy), 208.1
    @Test
    fun valiantStance() = card(
        name = "Valiant Stance",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
            • Destroy target creature with toughness 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creature))),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtLeast, n(4)))))),
        ))),
    )
}
