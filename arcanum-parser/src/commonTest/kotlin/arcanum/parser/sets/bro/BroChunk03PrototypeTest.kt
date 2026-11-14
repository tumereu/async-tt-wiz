package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 03: prototype creatures (CR 702.160, 718). */
class BroChunk03PrototypeTest {
    private val reminder = "(You may cast this spell with different mana cost, color, and size. It keeps its abilities and types.)"

    // CR 702.160a, 603.4 ("if you cast it"), 707.12 (casting a copy of a card), 608.2h (its power as the ability resolves)
    @Test
    fun spellmirrorAutomaton() = card(
        name = "Spellmirror Automaton",
        cost = "{7}",
        type = "Artifact Creature — Wizard",
        text = """
            Prototype {1}{U}{U} — 2/1 $reminder
            When this creature enters, if you cast it, exile target instant or sorcery card with mana value less than or equal to this creature's power from your graveyard. Copy that card. You may cast the copy without paying its mana cost.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        prototype("{1}{U}{U}", 2, 1),
        triggered(entersSelf,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.CopyCard(Objects.Bound("exiled"), Players.You, bind = "copy")),
            Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You),
            targets = listOf(slot("card", and(or(instant, sorcery), ObjectFilter.ManaValue(Comparison.AtMost, power(Objects.Self)), *yourGraveyard.toTypedArray()))),
            interveningIf = ifYouCastIt),
    )

    // CR 702.160a, 702.20a (vigilance)
    @Test
    fun selfbuiltForeman() = card(
        name = "Selfbuilt Foreman",
        cost = "{5}",
        type = "Artifact Creature — Assembly-Worker",
        text = """
            Prototype {1}{W} — 2/2 $reminder
            Vigilance
            {1}, {T}: Put a +1/+1 counter on target Assembly-Worker you control.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        prototype("{1}{W}", 2, 2),
        Keyword.Vigilance,
        activated(cost(mana("{1}"), tap), counters(target("Assembly-Worker")),
            targets = listOf(slot("Assembly-Worker", and(sub("Assembly-Worker"), you)))),
    )

    // CR 702.160a, 702.10a (haste)
    @Test
    fun rushgearConstruct() = card(
        name = "Rushgear Construct",
        cost = "{7}",
        type = "Artifact Creature — Construct",
        text = """
            Prototype {2}{R} — 3/2 $reminder
            Haste
        """,
        pt = "6/4",
    ) parsesTo abilities(prototype("{2}{R}", 3, 2), Keyword.Haste)

    // CR 702.160a, 718.3b: its power is the prototype's when cast prototyped
    @Test
    fun mossrockGolem() = card(
        name = "Mossrock Golem",
        cost = "{7}",
        type = "Artifact Creature — Golem",
        text = """
            Prototype {3}{G} — 3/3 $reminder
            When this creature enters, you gain life equal to its power.
        """,
        pt = "6/5",
    ) parsesTo abilities(
        prototype("{3}{G}", 3, 3),
        triggered(entersSelf, doing(Effect.GainLife(Players.You, power(Objects.Self)))),
    )

    // CR 702.160a, 702.4b (double strike)
    @Test
    fun bladewhirlThresher() = card(
        name = "Bladewhirl Thresher",
        cost = "{7}",
        type = "Artifact Creature — Construct",
        text = """
            Prototype {2}{W} — 1/1 $reminder
            Double strike
            When this creature enters, draw a card.
        """,
        pt = "3/3",
    ) parsesTo abilities(prototype("{2}{W}", 1, 1), Keyword.DoubleStrike, triggered(entersSelf, draw(1)))

    // CR 702.160a, 106.1 (mana equal to its power)
    @Test
    fun grovefellerEngine() = card(
        name = "Grovefeller Engine",
        cost = "{6}",
        type = "Artifact Creature — Golem",
        text = """
            Prototype {2}{G} — 1/3 $reminder
            {T}: Add an amount of {G} equal to this creature's power.
        """,
        pt = "3/6",
    ) parsesTo abilities(
        prototype("{2}{G}", 1, 3),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!, times = power(Objects.Self))), Players.You))),
    )

    // CR 702.160a, 502.3 (doesn't untap)
    @Test
    fun trenchdropColossus() = card(
        name = "Trenchdrop Colossus",
        cost = "{9}",
        type = "Artifact Creature — Dreadnought",
        text = """
            Prototype {4}{U}{U} — 6/6 $reminder
            This creature doesn't untap during your untap step.
            {3}: Untap this creature.
        """,
        pt = "9/9",
    ) parsesTo abilities(
        prototype("{4}{U}{U}", 6, 6),
        static(Mechanic.DoesntUntap),
        activated(cost(mana("{3}")), untapIt(Objects.Self)),
    )

    // CR 702.160a, 702.9a (flying)
    @Test
    fun dunefireDrakeEngine() = card(
        name = "Dunefire Drake Engine",
        cost = "{8}",
        type = "Artifact Creature — Dragon",
        text = """
            Prototype {2}{R} — 1/3 $reminder
            Flying
            {2}: This creature gets +1/+0 until end of turn.
        """,
        pt = "5/5",
    ) parsesTo abilities(prototype("{2}{R}", 1, 3), Keyword.Flying, activated(cost(mana("{2}")), pump(Objects.Self, 1, 0)))

    // CR 702.160a, 702.2b (deathtouch)
    @Test
    fun spiketuskWarplow() = card(
        name = "Spiketusk Warplow",
        cost = "{6}",
        type = "Artifact Creature — Construct",
        text = """
            Prototype {1}{B} — 1/1 $reminder
            Deathtouch
        """,
        pt = "5/4",
    ) parsesTo abilities(prototype("{1}{B}", 1, 1), Keyword.Deathtouch)

    // CR 707.9b: exceptions are part of the copy's copiable values; its own power and toughness are the prototype's when cast prototyped
    // (CR 718.3b), fixed as it enters (CR 614.12)
    @Test
    fun bulkyShapecaster() = card(
        name = "Bulky Shapecaster",
        cost = "{9}",
        type = "Artifact Creature — Shapeshifter",
        text = """
            Prototype {2}{U}{U} — 3/3 $reminder
            You may have this creature enter as a copy of an artifact or creature you control, except it's an artifact creature in addition to its other types, and its power and toughness are equal to this creature's power and toughness.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        prototype("{2}{U}{U}", 3, 3),
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersAsCopy(
            of = and(or(artifact, creature), you),
            except = listOf(CopyException.Types(add = listOf(CardType.Artifact, CardType.Creature)), CopyException.OwnPowerToughness),
            optional = true,
        )))),
    )

    // CR 702.160a, 508.1 (attacking), 608.2h (its power as the ability resolves)
    @Test
    fun steelmawCrusher() = card(
        name = "Steelmaw Crusher",
        cost = "{7}",
        type = "Artifact Creature — Wurm",
        text = """
            Prototype {2}{G}{G} — 2/5 $reminder
            Whenever this creature attacks, target attacking creature gets +X/+0 until end of turn, where X is this creature's power.
        """,
        pt = "4/6",
    ) parsesTo abilities(
        prototype("{2}{G}{G}", 2, 5),
        triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(Objects.Self), n(0))),
            targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 702.21a: ward with a cost of life equal to this creature's power (as the ward ability resolves)
    @Test
    fun oilsickGorger() = card(
        name = "Oilsick Gorger",
        cost = "{7}",
        type = "Artifact Creature — Phyrexian Wurm",
        text = """
            Prototype {1}{B}{B} — 3/3 $reminder
            Menace, lifelink
            Ward—Pay life equal to this creature's power.
        """,
        pt = "7/5",
    ) parsesTo abilities(
        prototype("{1}{B}{B}", 3, 3),
        Keyword.Menace,
        Keyword.Lifelink,
        Keyword.Ward(cost(CostPart.PayLife(power(Objects.Self)))),
    )

    // CR 107.3c (X defined by the ability), 111.4 (token), 602.5d (sorcery speed)
    @Test
    fun tanglewireAmalgam() = card(
        name = "Tanglewire Amalgam",
        cost = "{5}",
        type = "Artifact Creature — Golem",
        text = """
            Prototype {1}{G} — 2/3 $reminder
            {3}{G}{G}, Sacrifice this creature: Create an X/X colorless Golem artifact creature token, where X is three times this creature's power. It gains haste until end of turn. Activate only as a sorcery.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        prototype("{1}{G}", 2, 3),
        activated(cost(mana("{3}{G}{G}"), sacrificeSelf),
            doing(Effect.CreateToken(TokenSpec.Defined(
                FaceDefinition("Golem Token", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
                    subtypes = listOf(Subtype("Golem")), power = PrintedNumber.X, toughness = PrintedNumber.X),
                power = Value.Times(power(Objects.Self), 3), toughness = Value.Times(power(Objects.Self), 3),
            ), n(1), Players.You, bind = "token")),
            gains(Objects.Bound("token"), Keyword.Haste),
            rules = asSorcery),
    )

    // CR 702.160a, 702.19b (trample), 702.17a (reach)
    @Test
    fun corrodedTitan() = card(
        name = "Corroded Titan",
        cost = "{10}",
        type = "Artifact Creature — Construct",
        text = """
            Prototype {3}{G}{G} — 3/5 $reminder
            Reach, trample
        """,
        pt = "10/10",
    ) parsesTo abilities(prototype("{3}{G}{G}", 3, 5), Keyword.Reach, Keyword.Trample)

    // CR 603.4 ("if you cast it"), 707.2, 718.3d (copies of a prototyped permanent are prototyped too)
    @Test
    fun scuttlelightBattalion() = card(
        name = "Scuttlelight Battalion",
        cost = "{9}",
        type = "Artifact Creature — Construct",
        text = """
            Prototype {3}{R}{R} — 2/2 $reminder
            Trample, haste
            When this creature enters, if you cast it, create two tokens that are copies of it.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        prototype("{3}{R}{R}", 2, 2),
        Keyword.Trample,
        Keyword.Haste,
        triggered(entersSelf, create(TokenSpec.CopyOf(Objects.EventObject(EventRole.After)), n(2)), interveningIf = ifYouCastIt),
    )

    // CR 701.22a (scry), 107.3c (X defined by the ability)
    @Test
    fun lookoutOrnithopter() = card(
        name = "Lookout Ornithopter",
        cost = "{8}",
        type = "Artifact Creature — Thopter",
        text = """
            Prototype {3}{U} — 2/3 $reminder
            Flying
            When this creature enters, scry X, where X is its power.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        prototype("{3}{U}", 2, 3),
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Scry(Players.You, power(Objects.Self)))),
    )

    // CR 702.160a, 700.2 (your choice of one keyword)
    @Test
    fun chromeSeraph() = card(
        name = "Chrome Seraph",
        cost = "{6}",
        type = "Artifact Creature — Angel",
        text = """
            Prototype {1}{W}{W} — 3/3 $reminder
            Flying
            At the beginning of combat on your turn, target creature you control gains your choice of flying, vigilance, or lifelink until end of turn.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        prototype("{1}{W}{W}", 3, 3),
        Keyword.Flying,
        triggered(beginningOfCombatOnYourTurn,
            Step.Choose(Choice.Option(listOf("flying", "vigilance", "lifelink")), bind = "keyword", chooser = Players.You),
            Step.If(Condition.Chose("keyword", "flying"), then = listOf(gains(target("creature"), Keyword.Flying)), otherwise = listOf(
                Step.If(Condition.Chose("keyword", "vigilance"), then = listOf(gains(target("creature"), Keyword.Vigilance)),
                    otherwise = listOf(gains(target("creature"), Keyword.Lifelink))))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.4 ("if you cast it"), 611.2a (for good), 613.1d, 613.4b (type and base power and toughness), 608.2h (its values fixed as the effect
    // begins), 205.1b ("It's still a land")
    @Test
    fun treecallAutomaton() = card(
        name = "Treecall Automaton",
        cost = "{10}",
        type = "Artifact Creature — Construct",
        text = """
            Prototype {2}{G}{G} — 3/3 $reminder
            When this creature enters, if you cast it, untap target land you control. It becomes a Treefolk creature with haste and base power and toughness equal to this creature's power and toughness. It's still a land.
        """,
        pt = "8/8",
    ) parsesTo abilities(
        prototype("{2}{G}{G}", 3, 3),
        triggered(entersSelf,
            untapIt(target("land")),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Treefolk")))),
                Mechanic.GainAbilities(listOf(Keyword.Haste)),
                Mechanic.SetPowerToughness(power(Objects.Self), toughness(Objects.Self)),
            ), arcanum.api.Affected.Objects(target("land")), Duration.Indefinitely)),
            targets = listOf(slot("land", and(land, you))),
            interveningIf = ifYouCastIt),
    )
}
