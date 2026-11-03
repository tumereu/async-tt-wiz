package arcanum.parser.sets.sos

import arcanum.api.Affected
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectCategory
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Choice
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 02: opus (an ability word): "Whenever you cast an instant or sorcery spell, [A]. If five or more mana was
 * spent to cast that spell, [B] instead / also [C]" — the mana spent on the spell that triggered it (CR 601.2h), as a conditional
 * instead within the ability (CR 608.2c); and other cards that read mana spent or {X}: "+X/+0 where X is the amount of mana spent", a
 * spell with {X} in its mana cost (CR 107.3), "look at the top X cards" for that spell's X, colourless mana added at your next main phase
 * (a delayed trigger, CR 603.7), damage equal to the mana spent on this spell, and 2ˣ cards.
 */
class SosChunk02OpusAndManaSpentTest {
    private val opus = "Opus"
    private fun opusTrigger(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) =
        triggered(youCastInstantOrSorcery, *steps, targets = targets, abilityWord = opus)
    private val instantOrSorceryOnly = and(spellFilter, instantOrSorcery)
    private val xSpell = ObjectFilter.XInManaCost

    // CR 122.1d: a stun counter; CR 608.2c: "instead" replaces the first bonus when the condition is true
    @Test
    fun tidebreakVirtuoso() = card(
        name = "Tidebreak Virtuoso",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. If five or more mana was spent to cast that spell, this creature gets +2/+2 until end of turn instead.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("creature")), stunCounters(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
        opusTrigger(ifThen(fiveOrMoreSpent, pump(Objects.Self, 2, 2), otherwise = listOf(pump(Objects.Self, 1, 1)))),
    )

    @Test
    fun showcaseTidewhisperer() = card(
        name = "Showcase Tidewhisperer",
        cost = "{U}",
        type = "Creature — Djinn Wizard",
        pt = "0/2",
        text = "Opus — Whenever you cast an instant or sorcery spell, target player mills three cards. If five or more mana was spent to cast that spell, that player mills ten cards instead.",
    ) parsesTo abilities(
        opusTrigger(
            ifThen(fiveOrMoreSpent, mill(10, Players.Target("player")), otherwise = listOf(mill(3, Players.Target("player")))),
            targets = listOf(targetPlayer),
        ),
    )

    @Test
    fun inspirationHunter() = card(
        name = "Inspiration Hunter",
        cost = "{1}{U}",
        type = "Creature — Elf Wizard",
        pt = "1/2",
        text = "Opus — Whenever you cast an instant or sorcery spell, draw a card. Then discard a card unless five or more mana was spent to cast that spell.",
    ) parsesTo abilities(opusTrigger(draw(1), ifThen(not(fiveOrMoreSpent), discard(1))))

    @Test
    fun flourishingFlamedancer() = card(
        name = "Flourishing Flamedancer",
        cost = "{1}{R}",
        type = "Creature — Human Sorcerer",
        pt = "2/2",
        text = "Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. If five or more mana was spent to cast that spell, this creature also gains double strike until end of turn.",
    ) parsesTo abilities(opusTrigger(pump(Objects.Self, 1, 1), ifThen(fiveOrMoreSpent, gains(Objects.Self, Keyword.DoubleStrike))))

    // CR 605.1b: a triggered ability that triggers on casting a spell isn't a mana ability, though it adds mana
    @Test
    fun emberHeartConductor() = card(
        name = "Ember-Heart Conductor",
        cost = "{1}{R}",
        type = "Creature — Goblin Bard",
        pt = "2/2",
        text = """
            Menace
            Opus — Whenever you cast an instant or sorcery spell, put a +1/+1 counter on this creature. If five or more mana was spent to cast that spell, add an amount of {R} equal to this creature's power.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        opusTrigger(counters(Objects.Self), ifThen(fiveOrMoreSpent,
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!, times = power(Objects.Self))), Players.You)))),
    )

    @Test
    fun grappleShowman() = card(
        name = "Grapple Showman",
        cost = "{3}{R}",
        type = "Creature — Orc Sorcerer",
        pt = "4/3",
        text = """
            Trample
            Opus — Whenever you cast an instant or sorcery spell, put a +1/+1 counter on this creature. If five or more mana was spent to cast that spell, put two +1/+1 counters on this creature instead.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        opusTrigger(ifThen(fiveOrMoreSpent, counters(Objects.Self, 2), otherwise = listOf(counters(Objects.Self)))),
    )

    @Test
    fun stormskinDrummer() = card(
        name = "Stormskin Drummer",
        cost = "{1}{R}",
        type = "Creature — Dwarf Bard",
        pt = "1/3",
        text = """
            Reach
            Opus — Whenever you cast an instant or sorcery spell, this creature deals 1 damage to each opponent. If five or more mana was spent to cast that spell, this creature deals 3 damage to each opponent instead.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        opusTrigger(ifThen(fiveOrMoreSpent, damage(3, eachOpponent), otherwise = listOf(damage(1, eachOpponent)))),
    )

    // CR 707.2: the token copies the creature's copiable values
    @Test
    fun prismgaleCharger() = card(
        name = "Prismgale Charger",
        cost = "{1}{U}{R}",
        type = "Creature — Elemental Horse",
        pt = "3/3",
        text = """
            Ward {1}, haste
            Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. If five or more mana was spent to cast that spell, create a token that's a copy of this creature.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{1}")),
        Keyword.Haste,
        opusTrigger(pump(Objects.Self, 1, 1), ifThen(fiveOrMoreSpent, create(TokenSpec.CopyOf(Objects.Self)))),
    )

    // CR 305.1: "play" includes playing a land
    @Test
    fun elementalPennantBearer() = card(
        name = "Elemental Pennant-Bearer",
        cost = "{1}{U}{R}",
        type = "Creature — Elemental Bird",
        pt = "1/4",
        text = """
            Flying, vigilance
            Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+0 until end of turn. If five or more mana was spent to cast that spell, exile the top card of your library. You may play that card until the end of your next turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        opusTrigger(pump(Objects.Self, 1, 0), ifThen(fiveOrMoreSpent,
            doing(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn)))),
    )

    @Test
    fun dazzlingCloudwhale() = card(
        name = "Dazzling Cloudwhale",
        cost = "{2}{U}{R}",
        type = "Creature — Elemental Whale",
        pt = "1/4",
        text = """
            Flying
            Opus — Whenever you cast an instant or sorcery spell, this creature gets +3/+0 until end of turn. If five or more mana was spent to cast that spell, put three +1/+1 counters on this creature instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        opusTrigger(ifThen(fiveOrMoreSpent, counters(Objects.Self, 3), otherwise = listOf(pump(Objects.Self, 3, 0)))),
    )

    // CR 601.2h: the total mana spent, of any kind
    @Test
    fun waywardSpellwurm() = card(
        name = "Wayward Spellwurm",
        cost = "{3}{G}",
        type = "Creature — Wurm",
        pt = "2/5",
        text = """
            Trample
            Whenever you cast an instant or sorcery spell, this creature gets +X/+0 until end of turn, where X is the amount of mana spent to cast that spell.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(youCastInstantOrSorcery, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.ManaSpent(of = thatSpell), n(0)))),
    )

    // CR 106.6: mana that may be spent only on some spells
    @Test
    fun streamChanneler() = card(
        name = "Stream-Channeler",
        cost = "{1}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "1/3",
        text = """
            {T}: Add {U}. Spend this mana only to cast an instant or sorcery spell.
            {1}, {T}: Add one mana of any color. Spend this mana only to cast an instant or sorcery spell.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!), restriction = instantOrSorceryOnly), Players.You))),
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1), restriction = instantOrSorceryOnly), Players.You))),
    )

    // CR 107.3: a spell with {X} in its mana cost; CR 509.1b: can't be blocked this turn
    @Test
    fun shapewarpingMage() = card(
        name = "Shapewarping Mage",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            When this creature enters, return up to one other target creature to its owner's hand.
            Whenever you cast a spell with {X} in its mana cost, this creature can't be blocked this turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(another, creature), min = 0))),
        triggered(youCast(xSpell), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)),
    )

    // CR 107.3a: X is the value chosen for that spell
    @Test
    fun surveyorsArthropod() = card(
        name = "Surveyor's Arthropod",
        cost = "{G}{U}",
        type = "Creature — Fractal Crab",
        pt = "1/4",
        text = "Whenever you cast a spell with {X} in its mana cost, look at the top X cards of your library. Put one of them into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(
            youCast(xSpell),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.CastX(of = thatSpell), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 603.7: a delayed trigger at your next main phase, precombat or postcombat (CR 505.1)
    @Test
    fun manaChisel() = card(
        name = "Mana Chisel",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = "Counter target spell. If you control a Wizard, add an amount of {C} equal to the amount of mana spent to cast that spell at the beginning of your next main phase.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Counter(target("spell"))),
            ifThen(Condition.Exists(and(sub("Wizard"), you), atLeast = 1), doing(Effect.CreateDelayedTrigger(
                Trigger.On(listOf(EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You), EventPattern.StepBegins(TurnStep.PostcombatMain, PlayerFilter.You))),
                Instructions(steps = listOf(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!,
                    times = Value.ManaSpent(of = target("spell")))), Players.You))))))),
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
        ),
    )

    // CR 601.2h: X and the coloured mana all count
    @Test
    fun searingRefrain() = card(
        name = "Searing Refrain",
        cost = "{X}{R}{W}",
        type = "Sorcery",
        text = """
            Searing Refrain deals damage to target creature equal to the amount of mana spent to cast this spell. Untap all creatures you control.
            Flashback {6}{R}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            damage(Value.ManaSpent(of = Objects.Self), toObjects(target("creature"))),
            untapIt(Objects.Each(creatureYouControl)),
            targets = listOf(slot("creature", creature)),
            announced = listOf("X"),
        ),
        Keyword.Flashback(manaCost("{6}{R}{W}")),
    )

    // 2⁰ = 1: X 0 draws one card
    @Test
    fun calculatedWonder() = card(
        name = "Calculated Wonder",
        cost = "{X}{X}{U}{U}",
        type = "Sorcery",
        text = "Target player draws 2ˣ cards. (2⁰ = 1, 2¹ = 2, 2² = 4, 2³ = 8, 2⁴ = 16, 2⁵ = 32, and so on.)",
    ) parsesTo abilities(
        spell(doing(Effect.Draw(Players.Target("player"), Value.Exponential(2, x))), targets = listOf(targetPlayer), announced = listOf("X")),
    )
}
