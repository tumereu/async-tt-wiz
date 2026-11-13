package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.*
import kotlin.test.Test

/**
 * Chunk 12: "enters with N oil counters" (CR 614.1c, 122.1), removing oil counters as a cost or a step (CR 118.3, 701.10), a creature
 * that makes its controller lose when it has no counters (CR 104.3e), the token-doubling replacement (CR 614.1, 111.1) and Sphere
 * lands that enter tapped (CR 614.1d).
 */
class OneChunk12ReplacementTest {
    private val oil = CounterKind.Named("oil")
    private val hasOil = ObjectFilter.HasCounter(oil, atLeast = 1)

    /** "This <permanent> enters with N oil counters on it." (CR 614.1c) */
    private fun entersWithOil(count: Int) = Ability.Static(listOf(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(oil, n(count))))), affects = Affected.Objects(Objects.Self))

    private fun removeOil(count: Int): CostPart = CostPart.RemoveCounters(oil, n(count), CostObjects.Self)

    private val entersTapped = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)))

    private fun tapForMana(symbol: String) = activated(cost(tap),
        doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse(symbol)!!)), Players.You)))

    // CR 603.10a: "its controller" is the dead creature's controller as it last existed on the battlefield. CR 614.1c, 104.3e: "Then if" is checked after the counter is removed (CR 608.2c); its controller loses when none is left
    @Test
    fun archdemonOfTheSump() = card(
        name = "Archdemon of the Sump",
        cost = "{2}{B}{B}",
        type = "Creature — Phyrexian Demon",
        pt = "6/6",
        text = """
            Flying
            This creature enters with four oil counters on it.
            At the beginning of your upkeep, remove an oil counter from this creature. Then if it has no oil counters on it, you lose the game.
            Whenever a creature an opponent controls dies, its controller loses 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersWithOil(4),
        triggered(stepBegins(TurnStep.Upkeep, arcanum.api.PlayerFilter.You),
            Step.Do(Effect.RemoveCounters(Objects.Self, oil, n(1))),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.Not(hasOil)), then = listOf(Step.Do(Effect.LoseGame(Players.You))))),
        triggered(on(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard)),
            loseLife(2, Players.ControllerOf(Objects.EventObject(arcanum.api.EventRole.Before)))),
    )

    // CR 614.1c, 603.4
    @Test
    fun vexaCreeper() = card(
        name = "Vexa's Creeper",
        cost = "{3}",
        type = "Artifact Creature — Phyrexian Insect",
        pt = "2/2",
        text = """
            This creature enters with three oil counters on it.
            At the beginning of combat on your turn, you may remove an oil counter from this creature. When you do, target creature you control gains your choice of flying, vigilance, deathtouch, or lifelink until end of turn.
        """,
    ) parsesTo abilities(
        entersWithOil(3),
        triggered(beginningOfCombatOnYourTurn,
            Step.Pay(cost(removeOil(1)), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(
                    Step.Choose(Choice.Option(listOf("flying", "vigilance", "deathtouch", "lifelink")), bind = "keyword", chooser = Players.You),
                    Step.If(Condition.Chose("keyword", "flying"), then = listOf(gains(target("creature"), Keyword.Flying)), otherwise = listOf(
                        Step.If(Condition.Chose("keyword", "vigilance"), then = listOf(gains(target("creature"), Keyword.Vigilance)), otherwise = listOf(
                            Step.If(Condition.Chose("keyword", "deathtouch"), then = listOf(gains(target("creature"), Keyword.Deathtouch)),
                                otherwise = listOf(gains(target("creature"), Keyword.Lifelink)))))))))))))),
    )

    // CR 614.1c, 118.3, 602.2: an oil counter removed as a cost, a card discarded as a cost
    @Test
    fun cogEtcher() = card(
        name = "Cog Etcher",
        cost = "{1}{R}",
        type = "Creature — Phyrexian Wizard",
        pt = "1/3",
        text = """
            This creature enters with two oil counters on it.
            {T}, Remove an oil counter from this creature, Discard a card: Draw a card.
        """,
    ) parsesTo abilities(
        entersWithOil(2),
        activated(cost(tap, removeOil(1), discardACard), draw(1)),
    )

    // CR 614.1c ("an" = one), 702.12
    @Test
    fun edgedEnvoy() = card(
        name = "Edged Envoy",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Soldier",
        pt = "3/1",
        text = """
            This creature enters with an oil counter on it.
            {1}, Remove an oil counter from this creature: This creature gains indestructible until end of turn.
        """,
    ) parsesTo abilities(
        entersWithOil(1),
        activated(cost(mana("{1}"), removeOil(1)), gains(Objects.Self, Keyword.Indestructible)),
    )

    @Test
    fun kilnTreader() = card(
        name = "Kiln Treader",
        cost = "{4}{R}",
        type = "Creature — Phyrexian Beast",
        pt = "4/5",
        text = """
            This creature enters with two oil counters on it.
            Remove an oil counter from this creature: Target creature you control gains haste until end of turn.
        """,
    ) parsesTo abilities(
        entersWithOil(2),
        activated(cost(removeOil(1)), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 613.4c: +1/-1 until end of turn
    @Test
    fun brinewingHawk() = card(
        name = "Brinewing Hawk",
        cost = "{2}{U}",
        type = "Creature — Phyrexian Bird",
        pt = "1/4",
        text = """
            Flying
            This creature enters with three oil counters on it.
            Remove an oil counter from this creature: This creature gets +1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersWithOil(3),
        activated(cost(removeOil(1)), pump(Objects.Self, 1, -1)),
    )

    // CR 614.1c ("This artifact"), 602.5d: sorcery timing
    @Test
    fun hatchingPouch() = card(
        name = "Hatching Pouch",
        cost = "{G}",
        type = "Artifact",
        text = """
            This artifact enters with three oil counters on it.
            {4}, {T}, Remove an oil counter from this artifact: Create a 3/3 colorless Phyrexian Golem artifact creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersWithOil(3),
        activated(cost(mana("{4}"), tap, removeOil(1)),
            create(creatureToken("Phyrexian Golem", emptyList(), 3, 3, types = listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature))),
            rules = asSorcery),
    )

    // CR 614.1c: the card's own name stands for the permanent; CR 118.3: removing several counters at once
    @Test
    fun zomrixMazeBreaker() = card(
        name = "Zomrix, Maze Breaker",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Phyrexian Beast",
        pt = "4/4",
        text = """
            Zomrix enters with five oil counters on it.
            {1}, Remove an oil counter from Zomrix: It gains vigilance and menace until end of turn.
            {2}, Remove two oil counters from Zomrix: It gets +2/+2 until end of turn.
            {3}, Remove three oil counters from Zomrix: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        entersWithOil(5),
        activated(cost(mana("{1}"), removeOil(1)), gains(Objects.Self, Keyword.Vigilance, Keyword.Menace)),
        activated(cost(mana("{2}"), removeOil(2)), pump(Objects.Self, 2, 2)),
        activated(cost(mana("{3}"), removeOil(3)), destroy(target("permanent")),
            targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    // CR 614.1, 111.1: replaces only tokens under its controller's control; CR 107.4f: {W/P} is paid with W or 2 life
    @Test
    fun dorkanThrongSovereign() = card(
        name = "Dorkan, Throng Sovereign",
        cost = "{2}{W}{W}",
        type = "Legendary Creature — Phyrexian Horror",
        pt = "4/4",
        text = """
            If one or more tokens would be created under your control, twice that many of those tokens are created instead.
            {1}{W/P}{W/P}, Sacrifice two other artifacts and/or creatures: Put an indestructible counter on Dorkan. ({W/P} can be paid with either {W} or 2 life.)
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(event = EventPattern.CreateToken(tokens = you), modify = listOf(Modification.MultiplyAmount(factor = 2)))),
        activated(cost(mana("{1}{W/P}{W/P}"), CostPart.Sacrifice(CostObjects.Choose(and(another, or(artifact, creature)), count = n(2)))),
            counters(Objects.Self, kind = CounterKind.Keyword(Keyword.Indestructible))),
    )

    // CR 614.1c, 106.1: the card's own name is not needed in a land's text; counters are put as it enters
    @Test
    fun huntingWarden() = card(
        name = "Hunting Warden",
        cost = "{1}{G}",
        type = "Creature — Phyrexian Elf Warrior",
        pt = "2/2",
        text = """
            This creature enters with two oil counters on it.
            {2}{G}, {T}, Remove an oil counter from this creature: Target creature gets +2/+2 until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersWithOil(2),
        activated(cost(mana("{2}{G}"), tap, removeOil(1)), pump(target("creature"), 2, 2),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    @Test
    fun lunarBinder() = card(
        name = "Lunar Binder",
        cost = "{3}{U}",
        type = "Artifact",
        text = """
            This artifact enters with four oil counters on it.
            {T}, Remove an oil counter from this artifact: Tap target artifact or creature.
        """,
    ) parsesTo abilities(
        entersWithOil(4),
        activated(cost(tap, removeOil(1)), tapIt(target("permanent")), targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 614.1c: a land with counters; CR 106.6/602.5d: sorcery timing for the counter ability
    @Test
    fun theGildedMask() = card(
        name = "The Gilded Mask",
        cost = null,
        type = "Land — Sphere",
        text = """
            This land enters with two oil counters on it.
            {T}: Add {C}.
            {T}, Remove an oil counter from this land: Put an oil counter on target artifact or creature you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersWithOil(2),
        tapForMana("{C}"),
        activated(cost(tap, removeOil(1)), counters(target("permanent"), kind = oil),
            targets = listOf(slot("permanent", and(or(artifact, creature), you))), rules = asSorcery),
    )

    // CR 614.1d: a land that enters tapped; CR 701.21a: sacrificing it as a cost
    private fun sphere(name: String, mana: String, symbol: String, cost: String) = card(
        name = name,
        cost = null,
        type = "Land — Sphere",
        text = """
            This land enters tapped.
            {T}: Add {$symbol}.
            $cost, {T}, Sacrifice this land: Draw a card.
        """,
    ) parsesTo abilities(
        entersTapped,
        tapForMana("{$symbol}"),
        activated(cost(mana(mana), tap, sacrificeSelf), draw(1)),
    )

    @Test
    fun theEmberWorks() = sphere("The Ember Works", "{1}{R}", "R", "{1}{R}")

    @Test
    fun theMurkWells() = sphere("The Murk Wells", "{1}{B}", "B", "{1}{B}")

    @Test
    fun thePaleVault() = sphere("The Pale Vault", "{1}{W}", "W", "{1}{W}")

    @Test
    fun theGroveHollow() = sphere("The Grove Hollow", "{1}{G}", "G", "{1}{G}")

    @Test
    fun theTideCistern() = sphere("The Tide Cistern", "{1}{U}", "U", "{1}{U}")
}
