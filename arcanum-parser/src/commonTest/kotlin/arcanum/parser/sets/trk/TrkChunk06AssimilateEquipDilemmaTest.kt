package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 06: assimilate (CR 701.72b), Auras and Equipment, and "Whenever you face a dilemma" (CR 700.17: a player faces a dilemma as
 * they choose one or more modes for a spell or ability).
 */
class TrkChunk06AssimilateEquipDilemmaTest {
    private val exiledWith = Objects.Remembered("exiled")
    private val found = Objects.Bound("found")
    private val facedADilemmaThisTurn = Condition.HappenedThisTurn(EventPattern.FaceDilemma(PlayerFilter.You), atLeast = 1, measure = Measure.Events)

    // ---- Assimilate

    // CR 702.41a (affinity for a two-word quality), 701.72b (assimilate), 607.2a (cards exiled with it), 609.3 ("if you can't": no such card),
    // 722.2a-b (station symbol with a P/T box)
    @Test
    fun collectiveHexahedron() = card(
        name = "Collective Hexahedron",
        cost = "{5}{W}{B}",
        type = "Artifact — Spacecraft",
        pt = "6/6",
        text = """
            Affinity for artifact creatures
            Whenever this Spacecraft enters or attacks, assimilate a creature card exiled with it. If you can't, exile up to one target creature. (To assimilate a card, put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)
            Station
            12+ | Artifact creatures you control have flying.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(and(artifact, creature), "artifact creatures"),
        triggered(entersOrAttacksSelf,
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Where(exiledWith, creature)), bind = "chosen", chooser = Players.You),
            assimilate(Objects.Bound("chosen"), postBind = "returned"),
            ifThen(Condition.Not(Condition.NonEmpty("chosen")),
                move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled")))),
            targets = listOf(slot("creature", creature, min = 0))),
        Keyword.Station,
        station(12, static(has(Keyword.Flying), affects = Objects.Each(and(artifact, creature, you))), pt = "6/6"),
    )

    // CR 701.72b (assimilate a card in a graveyard), 702.12b (indestructible)
    @Test
    fun resistanceIsPointless() = card(
        name = "Resistance Is Pointless",
        cost = "{6}{B}{B}",
        type = "Enchantment",
        text = """
            Artifact creatures you control have indestructible.
            At the beginning of your end step, assimilate target creature card from a graveyard. (Put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Indestructible), affects = Objects.Each(and(artifact, creature, you))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), assimilate(target("card")),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard))))),
    )

    // ---- Auras

    // CR 702.196b ("can't be beamed up"), 502.3 (doesn't untap), 303.4 (enchanted permanent)
    @Test
    fun strandedAfar() = card(
        name = "Stranded Afar",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            When this Aura enters, you may pay {3}. If you do, tap enchanted permanent.
            Enchanted permanent doesn't untap during its controller's untap step and can't be beamed up.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        triggered(entersSelf, Step.Pay(manaCost("{3}"), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), tapIt(attached))),
        static(Mechanic.DoesntUntap, Mechanic.CantBeBeamedUp, affects = attached),
    )

    // CR 702.8a (flash), 303.4, 702.7 (first strike until end of turn)
    @Test
    fun commandersMentoring() = card(
        name = "Commander's Mentoring",
        cost = "{1}{R}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, enchanted creature gains first strike until end of turn.
            Enchanted creature gets +2/+0.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(creature),
        triggered(entersSelf, gains(attached, Keyword.FirstStrike)),
        static(pt(2, 0), affects = attached),
    )

    // CR 205.1a ("is a Salamander" sets its creature type: the others go), 509.1a (can't block), 603.4 (threshold's intervening if)
    @Test
    fun regressStrand() = card(
        name = "Regress Strand",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -4/-0, can't block, and is a Salamander. (It loses all other creature types.)
            Threshold — At the beginning of each upkeep, if there are seven or more cards in your graveyard, exile enchanted creature.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(-4, 0), Mechanic.CantBlock(attackers = ObjectFilter.Any),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Salamander"))), replaceSubtypes = true), affects = attached),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), move(attached, Zone.Exile),
            interveningIf = Condition.Exists(ObjectFilter.And(yourGraveyard), atLeast = 7), abilityWord = "Threshold"),
    )

    // ---- Equipment

    // CR 707.10f (a copy of a permanent spell becomes a token), 603.3c; CR 508.1 / 603.4-like "while" (judged as it attacks), 301.5 (a
    // Communicator attached to another creature)
    @Test
    fun hailingPin() = card(
        name = "Hailing Pin",
        cost = "{3}",
        type = "Artifact — Communicator Equipment",
        text = """
            When you cast this spell, copy it. (The copy becomes a token.)
            Equipped creature has "Whenever this creature attacks while you control another creature with a Communicator attached to it, this creature can't be blocked this turn."
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(youCastThis, doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = false))),
        static(has(triggered(attacksSelf, untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked),
            triggersOnlyIf = Condition.Exists(and(another, creature, ObjectFilter.Attached(attachments = sub("Communicator")), you), atLeast = 1))),
            affects = attached),
        equip("{2}"),
    )

    // CR 301.5, 702.6a (equip), 611.2c
    @Test
    fun crescentWarblade() = card(
        name = "Crescent Warblade",
        cost = "{2}{B}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, target creature an opponent controls gets -2/-2 until end of turn.
            Equipped creature gets +2/+0.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(target("creature"), -2, -2), targets = listOf(slot("creature", creatureOpponentControls))),
        static(pt(2, 0), affects = attached),
        equip("{2}"),
    )

    // CR 301.5, 508.1m (attack trigger of the equipped creature), 701.25a (surveil)
    @Test
    fun scanningWand() = card(
        name = "Scanning Wand",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1.
            Whenever equipped creature attacks, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = attached),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(attached))), surveil(1)),
        equip("{1}"),
    )

    // CR 122.1g (stun counter), 701.26a (tap), 301.5
    @Test
    fun handheldStunner() = card(
        name = "Handheld Stunner",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Equipped creature gets +3/+0.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun),
            targets = listOf(slot("creature", creatureOpponentControls))),
        static(pt(3, 0), affects = attached),
        equip("{3}"),
    )

    // ---- Face a dilemma

    // CR 700.17, 702.110a (menace), 119.3 (each opponent loses life)
    @Test
    fun orvanTeskPlainClothier() = card(
        name = "Orvan Tesk, Plain Clothier",
        cost = "{2}{B}",
        type = "Legendary Creature — Cardassian Rogue",
        pt = "3/3",
        text = """
            Menace
            Whenever you face a dilemma, each opponent loses 2 life and you gain 2 life. (You face a dilemma as you choose one or more modes for a spell or ability.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(youFaceADilemma, loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
    )

    // CR 700.17; CR 113.6m (a graveyard card's ability that returns it functions there)
    @Test
    fun drevenKaltMirrorRogue() = card(
        name = "Dreven Kalt, Mirror Rogue",
        cost = "{4}{B}",
        type = "Legendary Creature — Human Rogue Officer",
        pt = "5/4",
        text = """
            Menace
            Whenever you face a dilemma, you may pay {B}. If you do, return this card from your graveyard to your hand. (You face a dilemma as you choose one or more modes for a spell or ability.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(youFaceADilemma, Step.Pay(cost(mana("{B}")), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), move(Objects.Self, Zone.Hand)),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 701.26 (becomes tapped), 701.25a (surveil); "If her power is 4 or greater, instead draw a card": the surveil happens only when
    // the condition is false (CR 608.2c); CR 700.17, 122.1a
    @Test
    fun miraVolkFixer() = card(
        name = "Mira Volk, Fixer",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Artificer",
        pt = "1/2",
        text = """
            Whenever Mira Volk becomes tapped, surveil 1. If her power is 4 or greater, instead draw a card.
            Whenever you face a dilemma, put a +1/+1 counter on Mira Volk. (You face a dilemma as you choose one or more modes for a spell or ability.)
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))),
            ifThen(Condition.ObjectMatches(Objects.Self, power(Comparison.AtLeast, 4)), draw(1), otherwise = listOf(surveil(1)))),
        triggered(youFaceADilemma, counters(Objects.Self)),
    )

    // CR 700.17, 122.1a
    @Test
    fun etherealArbiter() = card(
        name = "Ethereal Arbiter",
        cost = "{2}{U}",
        type = "Creature — Melkotian Advisor",
        pt = "2/1",
        text = """
            Flying, vigilance
            Whenever you face a dilemma, put a +1/+1 counter on this creature. (You face a dilemma as you choose one or more modes for a spell or ability.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(youFaceADilemma, counters(Objects.Self)),
    )

    // CR 700.17
    @Test
    fun basaltCellAgent() = card(
        name = "Basalt Cell Agent",
        cost = "{3}{B}",
        type = "Creature — Cardassian Officer Spy",
        pt = "2/5",
        text = "Whenever you face a dilemma, each opponent loses 1 life and you gain 1 life. (You face a dilemma as you choose one or more modes for a spell or ability.)",
    ) parsesTo abilities(
        triggered(youFaceADilemma, loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 700.17
    @Test
    fun unitDroneAnalyst() = card(
        name = "Unit Drone Analyst",
        cost = "{2}{U}{B}",
        type = "Legendary Artifact Creature — Human Scientist",
        pt = "2/4",
        text = "Whenever you face a dilemma, draw a card. (You face a dilemma as you choose one or more modes for a spell or ability.)",
    ) parsesTo abilities(
        triggered(youFaceADilemma, draw(1)),
    )

    // CR 608.2d (the opponent chooses whether you draw), 115.1 (target opponent; "they" is that player), 701.25a; CR 700.17 (a may of
    // returning this enchantment: only from the battlefield)
    @Test
    fun describeTheVision() = card(
        name = "Describe the Vision",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, target opponent may have you draw a card. If they don't, they lose 4 life and you surveil 1.
            Whenever you face a dilemma, you may return this enchantment to its owner's hand. (You face a dilemma as you choose one or more modes for a spell or ability.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(steps = listOf(draw(1)), payer = Players.Target("opponent"), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), loseLife(4, Players.Target("opponent")), surveil(1)),
            targets = listOf(targetOpponent)),
        triggered(youFaceADilemma, Step.May(listOf(move(Objects.Self, Zone.Hand)), Players.You)),
    )

    // CR 700.17 ("faced a dilemma this turn"), 603.4 (intervening if), 701.20-like exile until a nonland card, 601.2a + 118.9 (cast
    // without paying its mana cost while the ability resolves, CR 608.2g); 702.21a (ward)
    @Test
    fun theArrayKeeper() = card(
        name = "The Array Keeper",
        cost = "{5}{U}",
        type = "Legendary Creature — Elemental",
        pt = "5/6",
        text = """
            Vigilance, ward {2}
            At the beginning of each end step, if you faced a dilemma this turn, target opponent exiles cards from the top of their library until they exile a nonland card. You may cast that card without paying its mana cost. (You face a dilemma as you choose one or more modes for a spell or ability.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Ward(manaCost("{2}")),
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any),
            doing(Effect.RevealUntil(nonland, Players.Target("opponent"), n(1), reveal = true, into = Zone.Exile, foundBind = "found")),
            Step.May(listOf(doing(Effect.Cast(found, PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You),
            targets = listOf(targetOpponent), interveningIf = facedADilemmaThisTurn),
    )
}
