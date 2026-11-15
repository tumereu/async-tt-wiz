package arcanum.parser.sets.trk

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 01: surveil, the lands that trigger when they enter untapped, modal enters and attack triggers. */
class TrkChunk01SurveilTest {
    // CR 305.7, 614.1c: the land enters tapped unless two or more other lands are already there
    private val entersTappedUnlessTwoOtherLands = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(arcanum.api.Modification.EntersTapped)), condition = Condition.Not(Condition.Exists(and(another, land, you), atLeast = 2)))

    /** "When this land enters untapped, choose one — …": the land as it entered (CR 603.6a, 603.10). */
    private fun whenEntersUntapped(vararg modes: arcanum.api.Mode) =
        Ability.Triggered(on(EventPattern.ZoneChange(and(ObjectFilter.Is(Objects.Self), not(tapped)), to = Zone.Battlefield)),
            Instructions(modes = modes(*modes)))

    private fun untappedLand(name: String, mode: String, color: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            When this land enters untapped, choose one —
            • $mode
            • Surveil 1.
            {T}: Add {$color}.
        """,
    )

    private val targetCreature = listOf(slot("creature", creature))

    // CR 603.6a, 603.10, 700.2: a modal trigger of entering untapped
    @Test
    fun sentinelWaystation() = untappedLand("Sentinel Waystation", "You gain 2 life.", "W") parsesTo abilities(
        entersTappedUnlessTwoOtherLands,
        whenEntersUntapped(mode(gainLife(2)), mode(surveil(1))),
        tapFor("W"),
    )

    // CR 603.6a, 702.9a
    @Test
    fun skyreachObservatory() = untappedLand("Skyreach Observatory", "Target creature gains flying until end of turn.", "U") parsesTo abilities(
        entersTappedUnlessTwoOtherLands,
        whenEntersUntapped(mode(gains(target("creature"), Keyword.Flying), targets = targetCreature), mode(surveil(1))),
        tapFor("U"),
    )

    // CR 603.6a, 702.20a
    @Test
    fun verdantNexusGrove() = untappedLand("Verdant Nexus Grove", "Target creature gets +1/+1 and gains vigilance until end of turn.", "G") parsesTo abilities(
        entersTappedUnlessTwoOtherLands,
        whenEntersUntapped(mode(pump(target("creature"), 1, 1, has(Keyword.Vigilance)), targets = targetCreature), mode(surveil(1))),
        tapFor("G"),
    )

    // CR 603.6a, 120.3
    @Test
    fun smokyTavernDen() = untappedLand("Smoky Tavern Den", "This land deals 1 damage to each opponent.", "R") parsesTo abilities(
        entersTappedUnlessTwoOtherLands,
        whenEntersUntapped(mode(damage(1, eachOpponent)), mode(surveil(1))),
        tapFor("R"),
    )

    // CR 603.6a, 401.4
    @Test
    fun hiveSpireCitadel() = untappedLand("Hive Spire Citadel", "Put target creature card from your graveyard on top of your library.", "B") parsesTo abilities(
        entersTappedUnlessTwoOtherLands,
        whenEntersUntapped(mode(move(target("card"), Zone.Library, position = 0), targets = listOf(slot("card", inYourGraveyard(creature)))),
            mode(surveil(1))),
        tapFor("B"),
    )

    // CR 205.1a: the new creature types replace the old ones; CR 613.4b: new base power and toughness; CR 611.2a: for good;
    // CR 608.2b: the condition is checked as the ability resolves
    @Test
    fun brinEagerTrainee() = card(
        name = "Brin, Eager Trainee",
        cost = "{B}{G}",
        type = "Legendary Creature — Ferengi Rogue",
        pt = "2/2",
        text = """
            Menace
            {1}: If Brin is a Rogue, he becomes a Ferengi Scout with base power and toughness 3/4 and gains "Whenever Brin becomes tapped, surveil 2."
            {3}: If Brin is a Scout, he becomes a Ferengi Officer and gains "You may play lands from your graveyard" and "You may play an additional land on each of your turns."
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(mana("{1}")), ifThen(Condition.ObjectMatches(Objects.Self, sub("Rogue")), doing(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Ferengi")), SubtypeRef.Literal(Subtype("Scout"))), replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(3), n(4)),
            Mechanic.GainAbilities(listOf(triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), surveil(2)))),
        ), Affected.Objects(Objects.Self), Duration.Indefinitely)))),
        activated(cost(mana("{3}")), ifThen(Condition.ObjectMatches(Objects.Self, sub("Scout")), doing(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Ferengi")), SubtypeRef.Literal(Subtype("Officer"))), replaceSubtypes = true),
            Mechanic.GainAbilities(listOf(
                // CR 305.2: playing a land from the graveyard still uses a land play; CR 305.2a: one more land play each turn
                static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You), affects = Objects.Each(inYourGraveyard(land))),
                playerStatic(Mechanic.ExtraLandPlays(1)),
            )),
        ), Affected.Objects(Objects.Self), Duration.Indefinitely)))),
    )

    // CR 118.12: a cost of two parts paid as the ability resolves; CR 603.12: the reflexive trigger only if it was paid
    @Test
    fun miraQuellKeenExplorer() = card(
        name = "Mira Quell, Keen Explorer",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Scientist Artificer",
        pt = "2/2",
        text = """
            When Mira Quell enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            At the beginning of combat on your turn, you may pay {1} and exile a card from your graveyard. When you do, put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, surveil(2)),
        triggered(beginningOfCombatOnYourTurn,
            Step.Pay(cost(mana("{1}"), CostPart.Exile(CostObjects.Choose(cardFilter, count = n(1)), from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(counters(target("creature")), targets = targetCreature))),
    )

    // CR 207.2d: the mode names are flavor words; CR 603.7a: the delayed return; CR 400.7: "that card" is the exiled card
    @Test
    fun trialOfAllotment() = card(
        name = "Trial of Allotment",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Power to Engines — Surveil 2, then draw a card.
            • Power to Life Support — Exile target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(surveil(2), draw(1)),
            mode(
                move(target("creature"), Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                    move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
                targets = listOf(slot("creature", creatureYouControl)),
            ),
        ))),
    )

    // CR 701.25a; CR 401.4: the bottom of the library
    @Test
    fun augmentedAnalyst() = card(
        name = "Augmented Analyst",
        cost = "{2}",
        type = "Artifact Creature — Human Scientist",
        pt = "2/1",
        text = """
            When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {1}, {T}: Put target card from your graveyard on the bottom of your library.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, surveil(1)),
        activated(cost(mana("{1}"), tap), move(target("card"), Zone.Library, position = -1), targets = listOf(slot("card", inYourGraveyard(cardFilter)))),
    )

    // CR 701.26b: untap; CR 115.3: "another" target isn't this creature
    @Test
    fun pellOstrowHopefulEnsign() = card(
        name = "Pell Ostrow, Hopeful Ensign",
        cost = "{1}{G/U}{G/U}",
        type = "Legendary Creature — Orion Scientist",
        pt = "2/4",
        text = """
            {T}: Untap another target permanent.
            {3}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("permanent")), targets = listOf(slot("permanent", and(another, permanentFilter)))),
        activated(cost(mana("{3}")), surveil(1)),
    )

    // CR 700.2, 603.3c: the mode is chosen as the trigger is put on the Stack
    @Test
    fun vanguardStrikeColumn() = card(
        name = "Vanguard Strike Column",
        cost = "{3}{G}",
        type = "Creature — Jem'Hadar Warrior",
        pt = "4/4",
        text = """
            Vigilance
            When this creature enters, choose one —
            • Exile target card from a graveyard.
            • Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
            mode(surveil(1)),
        ))),
    )

    // CR 700.2
    @Test
    fun contingencyLightformCrew() = card(
        name = "Contingency Lightform Crew",
        cost = "{3}{U}",
        type = "Artifact Creature — Illusion Officer",
        pt = "3/4",
        text = """
            When this creature enters, choose one —
            • Return target Spacecraft card from your graveyard to your hand.
            • Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(sub("Spacecraft"))))),
            mode(surveil(2)),
        ))),
    )

    // CR 700.2
    @Test
    fun scoutingProbeUnit() = card(
        name = "Scouting Probe Unit",
        cost = "{U}",
        type = "Artifact Creature — Construct",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, choose one —
            • Target creature gets +1/+1 until end of turn.
            • Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(pump(target("creature"), 1, 1), targets = targetCreature),
            mode(surveil(1)),
        ))),
    )

    // CR 701.8a, 701.25a
    @Test
    fun flatlinedVerdict() = card(
        name = "Flatlined Verdict",
        cost = "{3}{B}",
        type = "Instant",
        text = "Destroy target creature. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(destroy(target("creature")), surveil(1), targets = targetCreature),
    )

    // CR 603.7a, 111.1: a token sacrificed at the next end step; CR 701.21a
    @Test
    fun kessaFierceSeer() = card(
        name = "Kessa, Fierce Seer",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Klingon Warrior",
        pt = "1/4",
        text = """
            At the beginning of combat on your turn, create a 2/1 red Klingon Warrior creature token with haste. Sacrifice it at the beginning of the next end step.
            {2}, Sacrifice another creature or artifact: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            create(creatureToken("Klingon Warrior", listOf(Color.Red), 2, 1, listOf(Keyword.Haste)), bind = "token"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("token"), by = Players.You))))))),
        activated(cost(mana("{2}"), sacrificeA(and(another, or(creature, artifact)))), surveil(1)),
    )

    // CR 700.2, 509.1b: can't be blocked this turn
    @Test
    fun ondoSyntheticInstigator() = card(
        name = "Ondo, Synthetic Instigator",
        cost = "{1}{U/B}{U/B}",
        type = "Legendary Artifact Creature — Android Rogue",
        pt = "2/3",
        text = """
            When Ondo enters, choose one —
            • Target creature gets +1/+0 until end of turn and can't be blocked this turn.
            • Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(pump(target("creature"), 1, 0, Mechanic.CantBeBlocked), targets = targetCreature),
            mode(surveil(2)),
        ))),
    )

    // CR 700.2, 702.2b
    @Test
    fun veiledBureauAgent() = card(
        name = "Veiled Bureau Agent",
        cost = "{1}{B}",
        type = "Creature — Romulan Officer Spy",
        pt = "2/2",
        text = """
            Whenever this creature attacks, choose one —
            • Target creature you control gains deathtouch until end of turn.
            • Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(attacksSelf, Instructions(modes = modes(
            mode(gains(target("creature"), Keyword.Deathtouch), targets = listOf(slot("creature", creatureYouControl))),
            mode(surveil(1)),
        ))),
    )

    // CR 603.2h: once each turn; CR 603.10a: leaving a graveyard looks back in time
    @Test
    fun tallaRovingArchaeologist() = card(
        name = "Talla, Roving Archaeologist",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Human Rogue Scientist",
        pt = "2/4",
        text = """
            At the beginning of your upkeep, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            Whenever a card leaves your graveyard, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, surveil(1)),
        triggered(on(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard)), draw(1), perTurn = 1),
    )
}
