package arcanum.parser.sets.dft

import arcanum.api.AbilityKind
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 09: blue cards and the exhaust legends: counters unless paid (CR 118.12a), an Aura removing abilities (CR 613.1f),
 * mana for artifact spells and abilities (CR 106.6), a search by the sacrificed artifact's mana value (CR 608.2h), cost reductions by target,
 * looking at cards, mana by power, permanents put from the hand, base P/T set by a power fixed as it resolves (CR 611.2c), revealing until two
 * lands, ward that costs life, and exile X cards as a cost (CR 107.3a).
 */
class DftChunk09BlueAndExhaustLegendsTest {
    @Test
    fun bounceAway() = card(
        name = "Bounce Away",
        cost = "{U}",
        type = "Instant",
        text = "Return target creature or Vehicle to its owner's hand.",
    ) parsesTo abilities(spell(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", creatureOrVehicle))))

    @Test
    fun caeloraReefTyrant() = card(
        name = "Caelora, Reef Tyrant",
        cost = "{1}{U}",
        type = "Legendary Creature — Octopus",
        pt = "0/8",
    ) parsesTo abilities()

    // CR 118.12a: countered unless its controller pays
    @Test
    fun distractionUnit() = card(
        name = "Distraction Unit",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot",
        text = """
            Flying
            {U}, Sacrifice this creature: Counter target instant or sorcery spell unless its controller pays {3}.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{U}"), sacrificeSelf), *unlessPays("{3}"), targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter)))),
    )

    // CR 613.1f: the Aura removes all abilities in layer 6
    @Test
    fun drownTheEngine() = card(
        name = "Drown the Engine",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature or Vehicle
            When this Aura enters, tap enchanted permanent.
            Enchanted permanent loses all abilities and doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchantCreatureOrVehicle,
        triggered(entersSelf, tapIt(enchanted)),
        static(Mechanic.LoseAbilities(), Mechanic.DoesntUntap, affects = enchanted),
    )

    @Test
    fun gearhunterSerpent() = card(
        name = "Gearhunter Serpent",
        cost = "{5}{U}{U}",
        type = "Creature — Serpent",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            {5}{U}: This creature can't be blocked this turn.
        """,
        pt = "5/6",
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), activated(cost(mana("{5}{U}")), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)))

    // CR 106.6: the mana only for artifact spells and activated abilities
    @Test
    fun pathlightOptimizer() = card(
        name = "Pathlight Optimizer",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot",
        text = "{T}: Add {U}. Spend this mana only to cast an artifact spell or activate an ability.",
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!),
            restriction = or(and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = AbilityKind.Activated))), Players.You))),
    )

    @Test
    fun recallGuardian() = card(
        name = "Recall Guardian",
        cost = "{4}{U}",
        type = "Artifact Creature — Robot Artificer",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
        """,
        pt = "3/4",
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), Keyword.Flying)

    @Test
    fun nimbleThoptersmith() = card(
        name = "Nimble Thoptersmith",
        cost = "{3}{U}",
        type = "Creature — Vedalken Artificer",
        text = "When this creature enters, create a 1/1 colorless Thopter artifact creature token with flying.",
        pt = "3/2",
    ) parsesTo abilities(triggered(entersSelf, create(thopter)))

    // CR 608.2h: the sacrificed artifact's mana value as it last existed
    @Test
    fun recyclingBay() = card(
        name = "Recycling Bay",
        cost = "{2}{U}",
        type = "Artifact",
        text = "{2}, {T}, Sacrifice another artifact: Search your library for an artifact card with mana value equal to 1 plus the sacrificed artifact's mana value, put that card onto the battlefield, then shuffle. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, CostPart.Sacrifice(CostObjects.Choose(and(another, artifact), count = n(1)), preBind = "sacrificed")),
            search(and(artifact, ObjectFilter.ManaValue(Comparison.Equal, Value.Sum(listOf(n(1),
                Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), arcanum.api.Combine.Single)))))),
            move(Objects.Bound("found"), Zone.Battlefield), shuffle, rules = asSorcery),
    )

    @Test
    fun waysideBlowout() = card(
        name = "Wayside Blowout",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            This spell costs {2} less to cast if it targets a permanent with mana value 1.
            Return target creature or Vehicle an opponent controls to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.ObjectMatches(target("permanent"), and(permanentFilter, manaValue(Comparison.Equal, 1)))),
        spell(move(target("permanent"), Zone.Hand), draw(1), targets = listOf(slot("permanent", and(creatureOrVehicle, opponents)))),
    )

    @Test
    fun spectralObstruction() = card(
        name = "Spectral Obstruction",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target artifact or creature spell unless its controller pays {4}.",
    ) parsesTo abilities(spell(*unlessPays("{4}"), targets = listOf(slot("spell", and(or(artifact, creature), spellFilter)))))

    @Test
    fun spellPuncture() = card(
        name = "Spell Puncture",
        cost = "{U}",
        type = "Instant",
        text = "Counter target noncreature spell unless its controller pays {2}.",
    ) parsesTo abilities(spell(*unlessPays("{2}"), targets = listOf(slot("spell", and(noncreature, spellFilter)))))

    @Test
    fun stockPile() = card(
        name = "Stock Pile",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Look at the top five cards of your library. Put two of them into your hand and the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Library, position = -1),
        ),
    )

    @Test
    fun transitAdept() = card(
        name = "Transit Adept",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        text = "When this creature enters, you may search your library for an artifact card with mana value 4 or 5, reveal it, put it into your hand, then shuffle.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(and(artifact, or(manaValue(Comparison.Equal, 4), manaValue(Comparison.Equal, 5))), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You)),
    )

    @Test
    fun unrelentingPlan() = card(
        name = "Unrelenting Plan",
        cost = "{2}{U}",
        type = "Enchantment",
        text = "At the beginning of your end step, untap all nonland permanents you control.",
    ) parsesTo abilities(triggered(yourEndStep, untapIt(Objects.Each(and(nonland, permanentFilter, you)))))

    @Test
    fun luteTheWayfinder() = card(
        name = "Lute, the Wayfinder",
        cost = "{2}{G}{U}{R}",
        type = "Legendary Creature — Beast Noble",
        text = """
            Double strike, vigilance, haste
            Exhaust — {G}, {T}: Add three mana of any one color. (Activate each exhaust ability only once.)
            Exhaust — {U}, {T}: Draw three cards.
            Exhaust — {R}, {T}: Lute deals 3 damage to any target.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Keyword.Vigilance,
        Keyword.Haste,
        exhaust(cost(mana("{G}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(3))), Players.You))),
        exhaust(cost(mana("{U}"), tap), draw(3)),
        exhaust(cost(mana("{R}"), tap), damage(3, toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun redstreakRocketeerBoss() = card(
        name = "Redstreak, Rocketeer Boss",
        cost = "{R}{G}",
        type = "Legendary Creature — Goblin Pilot",
        text = """
            Vigilance
            {T}: Add X mana of any one color, where X is Redstreak's power. Spend this mana only to activate abilities.
            Exhaust — {10}{R}{G}: Put any number of permanent cards from your hand onto the battlefield. (Activate each exhaust ability only once.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(powerOf(Objects.Self)), restriction = ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
            Players.You))),
        exhaust(cost(mana("{10}{R}{G}")),
            Step.Choose(Choice.Objects(and(permanentCard, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = null), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 611.2c: the base power and toughness set to its power as the ability resolves
    @Test
    fun sitaraVarmaMaskedRacer() = card(
        name = "Sitara Varma, Masked Racer",
        cost = "{G}{U}",
        type = "Legendary Creature — Human Rogue",
        text = "Exhaust — {X}{G}{G}{U}: Put X +1/+1 counters on Sitara Varma. Then you may have the base power and toughness of each other creature you control become equal to Sitara Varma's power until end of turn. (Activate each exhaust ability only once.)",
        pt = "2/3",
    ) parsesTo abilities(
        exhaust(cost(mana("{X}{G}{G}{U}")), counters(Objects.Self, x),
            Step.May(listOf(untilEndOfTurn(Objects.Each(and(another, creature, you)), Mechanic.SetPowerToughness(powerOf(Objects.Self), powerOf(Objects.Self)))),
                player = Players.You)).let { it.copy(instructions = it.instructions.copy(announced = listOf("X"))) },
    )

    // CR 701.20: revealed until two lands are found
    @Test
    fun skyserpentSearcher() = card(
        name = "Skyserpent Searcher",
        cost = "{G}{U}",
        type = "Creature — Snake",
        text = """
            Flying, deathtouch
            Exhaust — {4}: Reveal cards from the top of your library until you reveal two land cards. Put those land cards onto the battlefield tapped and the rest on the bottom of your library in a random order. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        exhaust(cost(mana("{4}")),
            doing(Effect.RevealUntil(land, Players.You, n(2), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            counters(Objects.Self)),
    )

    // CR 702.21a: ward that costs life, also given to artifacts; CR 107.3a: X is the number of cards exiled as the cost
    @Test
    fun wintraCursedRider() = card(
        name = "Wintra, Cursed Rider",
        cost = "{U}{B}",
        type = "Legendary Creature — Human Warlock",
        text = """
            Ward—Pay 2 life.
            Artifacts you control have "Ward—Pay 2 life."
            Exhaust — {2}{U}{B}, {T}, Exile X artifact cards from your graveyard: Each other nonartifact creature gets -X/-X until end of turn. (Activate each exhaust ability only once.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Ward(cost(CostPart.PayLife(n(2)))),
        static(has(Keyword.Ward(cost(CostPart.PayLife(n(2))))), affects = Objects.Each(and(artifact, you))),
        exhaust(cost(mana("{2}{U}{B}"), tap, CostPart.Exile(CostObjects.Choose(artifact, count = x), from = Zone.Graveyard)),
            untilEndOfTurn(Objects.Each(and(another, not(artifact), creature)), Mechanic.ModifyPowerToughness(Value.Times(x, -1), Value.Times(x, -1))))
            .let { it.copy(instructions = it.instructions.copy(announced = listOf("X"))) },
    )

    /** "Counter target spell unless its controller pays [cost]" (CR 118.12a). */
    private fun unlessPays(cost: String): Array<Step> = arrayOf(
        Step.Pay(Cost.mana(ManaCost.parse(cost)!!), payer = Players.ControllerOf(target("spell")), bind = "paid"),
        Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
    )
}
