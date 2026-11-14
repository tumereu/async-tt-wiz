package arcanum.parser.sets.rvr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk09AdaptAfterlifeBattalionConvokeDredgeTest {
    private val battalion = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self), withOthers = 2))
    private val countersPutOnSelf = Trigger.On(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1), oneOrMore = true)
    private val self = ObjectFilter.Is(Objects.Self)

    // CR 701.46a (adapt), CR 601.2f via 602.2b (activated ability cost reduction)
    @Test
    fun pteramanderLookalike() = card(
        name = "Pterasalamander",
        cost = "{U}",
        type = "Creature — Salamander Drake",
        pt = "1/1",
        text = """
            Flying
            {7}{U}: Adapt 4. This ability costs {1} less to activate for each instant and sorcery card in your graveyard. (If this creature has no +1/+1 counters on it, put four +1/+1 counters on it.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{7}{U}")), doing(Effect.Action(KeywordAction.Adapt(n(4)))),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(or(instant, sorcery)))))),
    )

    // CR 701.46a, 121.6 (a counters-put trigger), 502.3 (doesn't untap)
    @Test
    fun sharktocrabLookalike() = card(
        name = "Sharkoctocrab",
        cost = "{2}{G}{U}",
        type = "Creature — Shark Octopus Crab",
        pt = "4/4",
        text = """
            {2}{G}{U}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)
            Whenever one or more +1/+1 counters are put on this creature, tap target creature an opponent controls. That creature doesn't untap during its controller's next untap step.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}{G}{U}")), doing(Effect.Action(KeywordAction.Adapt(n(1))))),
        triggered(countersPutOnSelf,
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(Objects.Target("creature")),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(Objects.Target("creature"))))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 702.135a
    @Test
    fun debtorsTransportLookalike() = card(
        name = "Creditors' Carrier",
        cost = "{5}{B}",
        type = "Creature — Thrull",
        pt = "5/3",
        text = "Afterlife 2 (When this creature dies, create two 1/1 white and black Spirit creature tokens with flying.)",
    ) parsesTo abilities(Keyword.Afterlife(2))

    @Test
    fun ministrantOfObligationLookalike() = card(
        name = "Minister of Duty",
        cost = "{2}{W}",
        type = "Creature — Human Cleric",
        pt = "2/1",
        text = "Afterlife 2 (When this creature dies, create two 1/1 white and black Spirit creature tokens with flying.)",
    ) parsesTo abilities(Keyword.Afterlife(2))

    @Test
    fun orzhovEnforcerLookalike() = card(
        name = "Orzhovan Bruiser",
        cost = "{1}{B}",
        type = "Creature — Human Rogue",
        pt = "1/2",
        text = """
            Deathtouch
            Afterlife 1 (When this creature dies, create a 1/1 white and black Spirit creature token with flying.)
        """,
    ) parsesTo abilities(Keyword.Deathtouch, Keyword.Afterlife(1))

    @Test
    fun syndicateMessengerLookalike() = card(
        name = "Guild Courier",
        cost = "{3}{W}",
        type = "Creature — Bird",
        pt = "2/3",
        text = """
            Flying
            Afterlife 1 (When this creature dies, create a 1/1 white and black Spirit creature token with flying.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Afterlife(1))

    // CR 702.101a (battalion is an ability word), CR 508.1a
    @Test
    fun makeshiftBattalionLookalike() = card(
        name = "Improvised Phalanx",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = "Battalion — Whenever this creature and at least two other creatures attack, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        Ability.Triggered(trigger = battalion, instructions = Instructions(steps = listOf(counters(Objects.Self))), abilityWord = "Battalion"),
    )

    // CR 702.51a, 107.3 (X in a search)
    @Test
    fun chordOfCallingLookalike() = card(
        name = "Chant of Summoning",
        cost = "{X}{G}{G}{G}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(
            search(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced("X")))),
            doing(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
            shuffle,
            announced = listOf("X"),
        ),
    )

    // CR 702.51a
    @Test
    fun conclaveEquenautLookalike() = card(
        name = "Conclave Skyrider",
        cost = "{4}{W}{W}",
        type = "Creature — Human Soldier",
        pt = "3/3",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Flying
        """,
    ) parsesTo abilities(Keyword.Convoke, Keyword.Flying)

    // CR 702.51a, 506.4 (attacking or blocking)
    @Test
    fun devouringLightLookalike() = card(
        name = "Consuming Radiance",
        cost = "{1}{W}{W}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Exile target attacking or blocking creature.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(move(target("creature"), Zone.Exile),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(arcanum.api.Status.Blocking)), creature)))),
    )

    // CR 702.51a
    @Test
    fun gatherCourageLookalike() = card(
        name = "Rally Resolve",
        cost = "{G}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Target creature gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(pump(target("creature"), 2, 2), targets = listOf(slot("creature", creature))),
    )

    // CR 702.51a
    @Test
    fun overwhelmLookalike() = card(
        name = "Swamp Them",
        cost = "{5}{G}{G}",
        type = "Sorcery",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Creatures you control get +3/+3 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(pump(Objects.Each(and(creature, you)), 3, 3)),
    )

    // CR 702.51a
    @Test
    fun siegeWurmLookalike() = card(
        name = "Besieger Wurm",
        cost = "{5}{G}{G}",
        type = "Creature — Wurm",
        pt = "5/5",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Trample
        """,
    ) parsesTo abilities(Keyword.Convoke, Keyword.Trample)

    // CR 702.51a, 700.2 (modes), 111.1
    @Test
    fun sproutingRenewalLookalike() = card(
        name = "Budding Revival",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Choose one —
            • Create a 2/2 green and white Elf Knight creature token with vigilance.
            • Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(create(creatureToken("Elf Knight", listOf(Color.Green, Color.White), 2, 2, listOf(Keyword.Vigilance)))))),
            Mode(Instructions(targets = listOf(slot("permanent", or(artifact, enchantment))), steps = listOf(destroy(target("permanent"))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 702.52a
    @Test
    fun darkblastLookalike() = card(
        name = "Gloomblast",
        cost = "{B}",
        type = "Instant",
        text = """
            Target creature gets -1/-1 until end of turn.
            Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -1, -1), targets = listOf(slot("creature", creature))),
        Keyword.Dredge(3),
    )

    // CR 614.1c (enters with counters), 122.1, 701.19a (regenerate), 702.52a
    @Test
    fun golgariGraveTrollLookalike() = card(
        name = "Golgarian Grave-Hulk",
        cost = "{4}{G}",
        type = "Creature — Troll Skeleton",
        pt = "0/0",
        text = """
            This creature enters with a +1/+1 counter on it for each creature card in your graveyard.
            {1}, Remove a +1/+1 counter from this creature: Regenerate this creature.
            Dredge 6 (If you would draw a card, you may mill six cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(self, to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(p1p1, count(inYourGraveyard(creature)))))),
        activated(cost(mana("{1}"), CostPart.RemoveCounters(p1p1, n(1), CostObjects.Self)),
            doing(Effect.Action(KeywordAction.Regenerate(Objects.Self)))),
        Keyword.Dredge(6),
    )

    // CR 700.4 (dies), 401.4, 702.52a
    @Test
    fun golgariThugLookalike() = card(
        name = "Golgarian Bruiser",
        cost = "{1}{B}",
        type = "Creature — Human Warrior",
        pt = "1/1",
        text = """
            When this creature dies, put target creature card from your graveyard on top of your library.
            Dredge 4 (If you would draw a card, you may mill four cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        triggered(diesSelf, move(target("card"), Zone.Library, position = 0), targets = listOf(slot("card", inYourGraveyard(creature)))),
        Keyword.Dredge(4),
    )

    // CR 702.52a
    @Test
    fun greaterMossdogLookalike() = card(
        name = "Lesser Mossdog Pack",
        cost = "{3}{G}",
        type = "Creature — Plant Dog",
        pt = "3/3",
        text = "Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)",
    ) parsesTo abilities(Keyword.Dredge(3))

    // CR 115.1 ("up to three target"), 702.52a
    @Test
    fun lifeFromTheLoamLookalike() = card(
        name = "Soil Rebirth",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Return up to three target land cards from your graveyard to your hand.
            Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(land), min = 0, max = 3))),
        Keyword.Dredge(3),
    )

    // CR 303.4 (Aura), 702.52a
    @Test
    fun moldervineCloakLookalike() = card(
        name = "Mossvine Mantle",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +3/+3.
            Dredge 2 (If you would draw a card, you may mill two cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(3, 3), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Dredge(2),
    )

    // CR 701.17a (sacrifice as a cost), 702.52a
    @Test
    fun shamblingShellLookalike() = card(
        name = "Crumbling Husk",
        cost = "{1}{B}{G}",
        type = "Creature — Plant Zombie",
        pt = "3/1",
        text = """
            Sacrifice this creature: Put a +1/+1 counter on target creature.
            Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        activated(cost(sacrificeSelf), counters(target("creature")), targets = listOf(slot("creature", creature))),
        Keyword.Dredge(3),
    )
}
