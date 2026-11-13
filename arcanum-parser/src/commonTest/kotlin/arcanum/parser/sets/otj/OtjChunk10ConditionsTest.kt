package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 10: conditions on "it" (its type, whether it's tapped), "as long as" a crime was committed, controlling an outlaw, what was
 * returned this way, "two or fewer other lands", and "Until end of turn, …" in front of a whole effect.
 */
class OtjChunk10ConditionsTest {
    private val crimeThisTurn = Condition.HappenedThisTurn(EventPattern.CommitCrime(PlayerFilter.You), atLeast = 1, measure = Measure.Events)
    private fun addEither(a: String, b: String): Ability = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors = listOf(Color.of(a[0])!!, Color.of(b[0])!!).map { ColorRef.Literal(it) })), Players.You)))

    // CR 614.1c: checked as it enters; "two or fewer other lands" = not three or more, so it enters tapped unless that holds
    private val entersTappedUnlessTwoOrFewer = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)), condition = Condition.Not(Condition.Not(Condition.Exists(and(another, land, you), atLeast = 3))))

    private fun fastLand(name: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or fewer other lands.
            {T}: Add {$a} or {$b}.
        """,
    ) parsesTo abilities(
        entersTappedUnlessTwoOrFewer,
        addEither(a, b),
    )

    @Test
    fun mireThicket() = fastLand("Mire Thicket", "B", "G")

    @Test
    fun fernGrotto() = fastLand("Fern Grotto", "G", "U")

    @Test
    fun shadedCloister() = fastLand("Shaded Cloister", "W", "B")

    @Test
    fun emberOverlook() = fastLand("Ember Overlook", "R", "W")

    @Test
    fun sluiceBluff() = fastLand("Sluice Bluff", "U", "R")

    // CR 401.4: a card not put into the hand may go to the bottom of the library; CR 106.6: mana spendable only on Mount spells
    @Test
    fun pastoralCorral() = card(
        name = "Pastoral Corral",
        cost = null,
        type = "Land — Desert",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Mount spell.
            {3}, {T}: Look at the top card of your library. If it's a Mount card, you may reveal it and put it into your hand. If you don't put it into your hand, you may put it on the bottom of your library.
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, sub("Mount"))), Players.You))),
        activated(cost(mana("{3}"), tap),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "top")),
            Step.If(Condition.ObjectMatches(Objects.Bound("top"), sub("Mount")), then = listOf(
                Step.Pay(steps = listOf(Step.Do(Effect.Reveal(Objects.Bound("top"))), Step.Do(Effect.Move(Objects.Bound("top"), Destination(Zone.Hand)))),
                    payer = Players.You, bind = "kept"))),
            Step.If(Condition.Not(Condition.Did("kept")), then = listOf(
                Step.May(listOf(Step.Do(Effect.Move(Objects.Bound("top"), Destination(Zone.Library, libraryPosition = -1)))), Players.You)))),
    )

    // CR 122.1d: a stun counter if the target is tapped, else it's tapped
    @Test
    fun manacleHurler() = card(
        name = "Manacle Hurler",
        cost = "{2}{U}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = "Whenever you cast your second spell each turn, choose target creature an opponent controls. If it's tapped, put a stun counter on it. Otherwise, tap it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            Step.If(Condition.ObjectMatches(target("creature"), tapped), then = listOf(counters(target("creature"), kind = CounterKind.Stun)),
                otherwise = listOf(tapIt(target("creature")))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 700.13, 611.3a: double strike while you've committed a crime this turn
    @Test
    fun portsideEnforcer() = card(
        name = "Portside Enforcer",
        cost = "{1}{W}",
        type = "Creature — Human Mercenary",
        pt = "2/2",
        text = "This creature has double strike as long as you've committed a crime this turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)",
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike), condition = crimeThisTurn),
    )

    @Test
    fun quickdrawSafecracker() = card(
        name = "Quickdraw Safecracker",
        cost = "{2}{U}",
        type = "Creature — Human Rogue",
        pt = "1/4",
        text = """
            Vigilance
            This creature gets +2/+0 as long as you've committed a crime this turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(pt(2, 0), condition = crimeThisTurn),
    )

    // CR 702.8a, 601.3d: flash works from the hand while the condition holds
    @Test
    fun borrowedSteedHeist() = card(
        name = "Borrowed Steed Heist",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Borrowed Steed Heist has flash as long as you've committed a crime this turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flash), condition = crimeThisTurn),
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 700.12: outlaws; CR 400.7: the exiled card is a new object the permission names
    @Test
    fun banditsRage() = card(
        name = "Bandits' Rage",
        cost = "{2}{R}",
        type = "Instant",
        text = "Creatures you control get +2/+0 until end of turn. If you control an outlaw, exile the top card of your library. Until the end of your next turn, you may play that card. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)",
    ) parsesTo abilities(
        spell(
            pump(Objects.Each(creatureYouControl), 2, 0),
            Step.If(youControl(outlaw, 1), then = listOf(
                move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
        ),
    )

    // CR 608.2c: the counter only if a permanent was returned
    @Test
    fun tendingSprite() = card(
        name = "Tending Sprite",
        cost = "{W}",
        type = "Creature — Faerie Rogue",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, return up to one target non-Faerie, nonland permanent you control to its owner's hand. If a permanent was returned this way, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("permanent"), Zone.Hand, postBind = "returned"),
            Step.If(Condition.NonEmpty("returned"), then = listOf(counters(Objects.Self))),
            targets = listOf(slot("permanent", and(not(sub("Faerie")), nonland, permanentFilter, you), min = 0))),
    )

    // CR 707.2, 115.3: a copy of up to one other target creature until end of turn (layer 1, CR 613.1a)
    @Test
    fun passingMirage() = card(
        name = "Passing Mirage",
        cost = "{1}{U}",
        type = "Instant",
        text = "Target creature you control gains hexproof until end of turn. Untap that creature. Until end of turn, it becomes a copy of up to one other target creature.",
    ) parsesTo abilities(
        spell(
            gains(target("creature"), Keyword.Hexproof),
            untapIt(target("creature")),
            untilEndOfTurn(target("creature"), Mechanic.CopyOf(target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl),
                TargetSlot("second creature", TargetKind.Objects(creature), min = n(0), max = n(1), distinctFrom = listOf("creature"))),
        ),
    )

    // CR 611.2c: the creatures you control as it resolves; CR 509.1b: the granted blocking restriction
    @Test
    fun thunderingOnrush() = card(
        name = "Thundering Onrush",
        cost = "{3}{G}{G}",
        type = "Sorcery",
        text = "Until end of turn, each creature you control gets +2/+2 and gains trample and \"This creature can't be blocked by more than one creature.\"",
    ) parsesTo abilities(
        spell(pump(Objects.Each(creatureYouControl), 2, 2, has(Keyword.Trample, static(Mechanic.MaximumBlockers(1))))),
    )

    @Test
    fun backAlleyScheme() = card(
        name = "Back-Alley Scheme",
        cost = "{B}",
        type = "Instant",
        text = "Until end of turn, target creature you control gets +1/+1 and target creature an opponent controls gets -1/-1.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 1, 1),
            pump(target("second creature"), -1, -1),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls)),
        ),
    )
}
