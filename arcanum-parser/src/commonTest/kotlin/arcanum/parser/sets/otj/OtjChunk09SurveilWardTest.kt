package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 09: surveil (CR 701.25) and ward (CR 702.21) cards, with conditions on what was cast this turn, whose turn it is and what was
 * returned or exiled this way.
 */
class OtjChunk09SurveilWardTest {
    private fun surveil(n: Int) = Step.Do(Effect.Surveil(Players.You, n(n)))
    private val entersTapped = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self))
    private fun addColorless(mana: String) = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(mana)!!)), Players.You)))
    private val desert = sub("Desert")
    private fun castThisTurn(from: Zone?) = Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = from), atLeast = 1,
        measure = Measure.Events)
    private fun ward(mana: String) = Keyword.Ward(manaCost(mana))
    private val wardReminder = "(Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays"

    // CR 614.1c (enters tapped), 608.2h: the returned land as it last existed; "another" excludes this land returning itself
    @Test
    fun dustgateArch() = card(
        name = "Dustgate Arch",
        cost = null,
        type = "Land — Desert",
        text = """
            This land enters tapped.
            When this land enters, return a land you control to its owner's hand. If another Desert was returned this way, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {T}: Add {C}{C}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(land, you), n(1), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            ifThen(Condition.ObjectMatches(Objects.Bound("chosen"), and(another, desert)), surveil(1))),
        addColorless("{C}{C}"),
    )

    // CR 608.2h: the exiled creature's mana value as it last existed on the battlefield
    @Test
    fun cinderswallowRite() = card(
        name = "Cinderswallow Rite",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = "Exile target creature. If it had mana value 3 or less, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile),
            ifThen(Condition.ObjectMatches(target("creature"), manaValue(Comparison.AtMost, 3)), surveil(2)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 603.4 (intervening if, both parts), 611.2a (for good), 613.1d, 613.1f, 613.4b (type, ability, base P/T)
    @Test
    fun restlessHauntcall() = card(
        name = "Restless Hauntcall",
        cost = "{1}{U}",
        type = "Enchantment",
        text = """
            At the beginning of your end step, if you haven't cast a spell from your hand this turn and this enchantment isn't a creature, it becomes a 3/3 Spirit creature with flying in addition to its other types.
            {2}{U}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
                Mechanic.SetPowerToughness(n(3), n(3)),
                Mechanic.GainAbilities(listOf(Keyword.Flying)),
            ), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            interveningIf = Condition.And(listOf(Condition.Not(castThisTurn(Zone.Hand)), Condition.Not(Condition.ObjectMatches(Objects.Self, creature))))),
        activated(manaCost("{2}{U}"), surveil(1)),
    )

    // CR 700.13 (crime), 603.2h (once each turn), 702.21a (ward)
    @Test
    fun plunderingSphinx() = card(
        name = "Plundering Sphinx",
        cost = "{3}{U}{U}",
        type = "Creature — Sphinx Rogue",
        pt = "3/5",
        text = """
            Flying, vigilance, ward {2}
            Whenever you commit a crime, surveil 2. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        ward("{2}"),
        crime(surveil(2), perTurn = 1),
    )

    // CR 106.1b, 605.1a: "one mana of any color" is any of the five colors (a false alarm of the audit)
    @Test
    fun relaySpires() = card(
        name = "Relay Spires",
        cost = null,
        type = "Land — Desert",
        text = """
            When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, surveil(1)),
        addColorless("{C}"),
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = null)), Players.You))),
    )

    // CR 608.2c: whether you control a Desert, as it resolves
    @Test
    fun botchedCrossing() = card(
        name = "Botched Crossing",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target nonland permanent to its owner's hand. If you control a Desert, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Hand), ifThen(youControl(desert, 1), surveil(1)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    // CR 611.2c: the creatures that target opponent controls as it resolves
    @Test
    fun disarmTheSentries() = card(
        name = "Disarm the Sentries",
        cost = "{2}{B}",
        type = "Instant",
        text = "Creatures target opponent controls get -1/-1 until end of turn. Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        spell(pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))), -1, -1), surveil(2),
            targets = listOf(targetOpponent)),
    )

    // CR 702.21a (ward), 613.4c: X is its toughness as the ability resolves
    @Test
    fun platehideArmadillo() = card(
        name = "Platehide Armadillo",
        cost = "{W}",
        type = "Creature — Armadillo",
        pt = "0/4",
        text = """
            Ward {1} $wardReminder {1}.)
            {3}{W}: This creature gets +X/+0 until end of turn, where X is its toughness.
        """,
    ) parsesTo abilities(
        ward("{1}"),
        activated(manaCost("{3}{W}"), untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(toughness(Objects.Self), n(0)))),
    )

    // CR 702.21a (ward with a life payment), 613.1f, 613.4c, 700.13, 603.2h
    @Test
    fun vessaTheGravecaller() = card(
        name = "Vessa, the Gravecaller",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "4/4",
        text = """
            Ward—{2}, Pay 2 life.
            Skeletons and Zombies you control get +1/+1 and have menace.
            Whenever you commit a crime, create two tapped 2/2 blue and black Zombie Rogue creature tokens. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost(mana("{2}"), CostPart.PayLife(n(2)))),
        static(pt(1, 1), has(Keyword.Menace), affects = Objects.Each(and(or(sub("Skeleton"), sub("Zombie")), you))),
        crime(Step.Do(Effect.CreateToken(creatureToken("Zombie Rogue", listOf(Color.Blue, Color.Black), 2, 2), n(2), Players.You, tapped = true)), perTurn = 1),
    )

    // CR 702.21a, 611.2c (the creatures with power 4 or greater as it resolves gain the keywords)
    @Test
    fun saguaroDeadeye() = card(
        name = "Saguaro Deadeye",
        cost = "{2}{R}{G}",
        type = "Creature — Plant Mercenary",
        pt = "4/4",
        text = """
            Reach
            Ward {2} $wardReminder {2}.)
            At the beginning of combat on your turn, other creatures you control with power 4 or greater gain trample and haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        ward("{2}"),
        triggered(beginningOfCombatOnYourTurn,
            gains(Objects.Each(and(another, creature, you, power(Comparison.AtLeast, 4))), Keyword.Trample, Keyword.Haste)),
    )

    // CR 702.21a, 602.1 (activated from the hand: discarding this card is its cost), 701.23a
    @Test
    fun thornbrushArmadillo() = card(
        name = "Thornbrush Armadillo",
        cost = "{4}{G}{G}",
        type = "Creature — Armadillo",
        pt = "7/7",
        text = """
            Reach
            Ward {3} $wardReminder {3}.)
            {1}{G}, Discard this card: Search your library for a basic land card or a Desert card, reveal it, put it into your hand, then shuffle. You gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        ward("{3}"),
        activated(cost(mana("{1}{G}"), CostPart.Discard(CostObjects.Self)),
            search(or(basicLand(), desert), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, gainLife(3)),
    )

    // CR 702.11a (hexproof), 611.3a: a static ability whose condition is checked continuously
    @Test
    fun patientSphinx() = card(
        name = "Patient Sphinx",
        cost = "{2}{U}{U}",
        type = "Creature — Sphinx",
        pt = "5/3",
        text = """
            Flash
            Flying
            This creature has hexproof as long as you haven't cast a spell this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        static(has(Keyword.Hexproof), condition = Condition.Not(castThisTurn(null))),
    )

    // CR 603.12 (each flip's "when" is a reflexive trigger, its target chosen as it's put on the Stack), 705.2, 707.10c, 608.2h (that spell's
    // mana value as it last existed)
    @Test
    fun fusewickTheDemolisher() = card(
        name = "Fusewick, the Demolisher",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Goblin Pirate",
        pt = "3/3",
        text = """
            Menace
            Whenever you cast your second spell each turn, you may sacrifice an artifact. If you do, flip a coin. When you win the flip, copy that spell. You may choose new targets for the copy. When you lose the flip, Fusewick deals damage equal to that spell's mana value to any target.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            Step.Pay(cost(sacrificeA(artifact)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                doing(Effect.FlipCoin(Players.You, bind = "flip")),
                ifThen(Condition.WonFlip("flip"),
                    reflexive(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true))),
                    otherwise = listOf(reflexive(
                        damage(Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single), toAny),
                        targets = listOf(anyTarget)))))),
    )

    // CR 611.3a (only during your turn), 700.12 (outlaws), 700.13, 603.2h, 111.4 (a token with a quoted ability)
    @Test
    fun bladeToTheThroat() = card(
        name = "Blade to the Throat",
        cost = "{1}{B}{R}",
        type = "Enchantment",
        text = """
            During your turn, outlaws you control have first strike. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
            Whenever you commit a crime, create a 1/1 red Mercenary creature token with "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery." This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), affects = Objects.Each(and(outlaw, you)), condition = Condition.YourTurn),
        crime(create(creatureToken("Mercenary", listOf(Color.Red), 1, 1, abilities = listOf(activated(cost(tap),
            pump(target("creature"), 1, 0), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery)))), perTurn = 1),
    )

    // CR 611.3a (only during other players' turns), 601.2f (the cost reduction)
    @Test
    fun steamventDrake() = card(
        name = "Steamvent Drake",
        cost = "{2}{U}",
        type = "Creature — Drake",
        pt = "2/3",
        text = """
            Flying
            During turns other than yours, spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CostChange(and(spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1))), condition = Condition.Not(Condition.YourTurn)),
    )
}
