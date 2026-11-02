package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.another
import arcanum.parser.sets.rtr.Rtr.artifact
import arcanum.parser.sets.rtr.Rtr.combatDamageToAPlayer
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.counters
import arcanum.parser.sets.rtr.Rtr.create
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.creatureToken
import arcanum.parser.sets.rtr.Rtr.enchantment
import arcanum.parser.sets.rtr.Rtr.gainLife
import arcanum.parser.sets.rtr.Rtr.gains
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.populate
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import arcanum.parser.sets.rtr.Rtr.yourUpkeep
import kotlin.test.Test

/**
 * Return to Ravnica chunk 02: populate (CR 701.36a: a token copy of a creature token you control; none without one,
 * 701.36b) alone, after creating a token or another effect (", then populate"), in triggers and activated abilities; and
 * the Selesnya cards: two targets with the same wording (CR 115.3), a discard caused by an opponent's spell or ability, a
 * replacement for every graveyard, a characteristic-defining P/T, prevention for you only, a modal charm.
 */
class RtrChunk02PopulateAndSelesnyaTest {
    private val creaturesYouControl = Objects.Each(all(creature, you))
    private val centaur = creatureToken("Centaur", listOf(Color.Green), 3, 3)

    // ---- Populate (CR 701.36a)

    @Test
    fun wingsOverTheMarket() = card(
        name = "Wings Over the Market",
        cost = "{3}{W}",
        type = "Instant",
        text = "Create a 1/1 white Bird creature token with flying, then populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(create(creatureToken("Bird", listOf(Color.White), 1, 1, Keyword.Flying)), populate),
    )

    @Test
    fun deeprootBulwark() = card(
        name = "Deeproot Bulwark",
        cost = "{2}{W}",
        type = "Instant",
        text = "Populate. Creatures you control gain indestructible until end of turn. (To populate, create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(populate, gains(creaturesYouControl, Keyword.Indestructible)),
    )

    @Test
    fun matriarchsRuling() = card(
        name = "Matriarch's Ruling",
        cost = "{5}{W}",
        type = "Instant",
        text = "Exile target creature, then populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(Rtr.move(target("creature"), Zone.Exile), populate, targets = listOf(slot("creature", creature))),
    )

    /** Prevention of combat damage to you only (CR 615.1a), for the turn. */
    @Test
    fun grovekeepersShelter() = card(
        name = "Grovekeeper's Shelter",
        cost = "{1}{G}",
        type = "Instant",
        text = "Prevent all combat damage that would be dealt to you this turn. Populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You), combat = true))),
                null, Duration.EndOfTurn)),
            populate,
        ),
    )

    @Test
    fun hornsingersCall() = card(
        name = "Hornsinger's Call",
        cost = "{7}{G}",
        type = "Sorcery",
        text = "Create a 4/4 green Rhino creature token with trample, then populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(create(creatureToken("Rhino", listOf(Color.Green), 4, 4, Keyword.Trample)), populate),
    )

    @Test
    fun gallopingPact() = card(
        name = "Galloping Pact",
        cost = "{4}{G}{W}",
        type = "Sorcery",
        text = "Create a 3/3 green Centaur creature token, then populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(create(centaur), populate),
    )

    @Test
    fun swellingConclave() = card(
        name = "Swelling Conclave",
        cost = "{2}{G/W}{G/W}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        triggered(yourUpkeep, populate),
    )

    @Test
    fun splitrootBloom() = card(
        name = "Splitroot Bloom",
        cost = "{G/W}{G/W}",
        type = "Instant",
        text = "Destroy target artifact or enchantment, then populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("permanent")), populate, targets = listOf(slot("permanent", ObjectFilter.Or(listOf(artifact, enchantment))))),
    )

    /** "that creature's toughness": the creature that entered, as it is on the battlefield. */
    @Test
    fun ysoldeConclaveChorus() = card(
        name = "Ysolde, Conclave Chorus",
        cost = "{G}{G}{W}{W}",
        type = "Legendary Creature — Dryad",
        pt = "2/5",
        text = """
            Whenever another creature you control enters, you gain life equal to that creature's toughness.
            {1}{G}{W}, {T}: Populate. (Create a token that's a copy of a creature token you control.)
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(all(another, creature, you), to = Zone.Battlefield)),
            gainLife(Value.Of(NumericProperty.Toughness, Objects.EventObject(EventRole.After), Combine.Single))),
        activated(cost(manaPart("{1}{G}{W}"), CostPart.TapSelf), populate),
    )

    @Test
    fun treehallSpeaker() = card(
        name = "Treehall Speaker",
        cost = "{G}{W}",
        type = "Creature — Dryad Shaman",
        pt = "2/2",
        text = """
            {4}{G}{W}: Create a 3/3 green Centaur creature token.
            {2}{G}{W}: Populate. (Create a token that's a copy of a creature token you control.)
        """,
    ) parsesTo abilities(
        activated(cost(manaPart("{4}{G}{W}")), create(centaur)),
        activated(cost(manaPart("{2}{G}{W}")), populate),
    )

    /** A characteristic-defining ability (CR 604.3, layer 7a). */
    @Test
    fun wanderingSanctum() = card(
        name = "Wandering Sanctum",
        cost = "{1}{G}{W}",
        type = "Creature — Elemental",
        pt = "*/*",
        text = """
            Wandering Sanctum's power and toughness are each equal to the number of creatures you control.
            Whenever this creature deals combat damage to a player, populate. (Create a token that's a copy of a creature token you control.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.SetPowerToughness(Value.Count(creaturesYouControl), Value.Count(creaturesYouControl))),
            affects = Affected.Objects(Objects.Self), characteristicDefining = true),
        Rtr.triggered(combatDamageToAPlayer(), populate),
    )

    // ---- Selesnya

    /** CR 115.3: each "target" is a target of its own; the same creature may be chosen for both. */
    @Test
    fun sharedVow() = card(
        name = "Shared Vow",
        cost = "{1}{G}{W}",
        type = "Instant",
        text = """
            Put a +1/+1 counter on target creature.
            Put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        spell(counters(target("creature")), counters(target("second creature")),
            targets = listOf(slot("creature", creature), slot("second creature", creature))),
    )

    @Test
    fun kinsfolkGathering() = card(
        name = "Kinsfolk Gathering",
        cost = "{G}{W}",
        type = "Instant",
        text = "Target player gains 7 life.",
    ) parsesTo abilities(
        spell(gainLife(7, Players.Target("player")), targets = listOf(Rtr.targetPlayer)),
    )

    /** A replacement that works in the hand (CR 113.6): only for a discard an opponent's spell or ability causes, not a cost
     * or the cleanup step's discard. */
    @Test
    fun tuskguardBruiser() = card(
        name = "Tuskguard Bruiser",
        cost = "{1}{G}{W}",
        type = "Creature — Elephant Soldier",
        pt = "4/4",
        text = """
            This spell can't be countered.
            If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CantBeCountered),
        Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You, causedBy = ObjectFilter.ControlledBy(PlayerFilter.Opponent)),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Battlefield))))),
            affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
    )

    @Test
    fun conclaveSigil() = card(
        name = "Conclave Sigil",
        cost = "{G}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature gets +2/+2 and gains trample until end of turn.
            • Exile target creature with power 5 or greater.
            • Create a 2/2 white Knight creature token with vigilance.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(pump(target("creature"), 2, 2, Mechanic.GainAbilities(listOf(Keyword.Trample)))))),
            Mode(Instructions(targets = listOf(slot("creature", all(creature, ObjectFilter.Power(Comparison.AtLeast, n(5))))),
                steps = listOf(Rtr.move(target("creature"), Zone.Exile)))),
            Mode(Instructions(steps = listOf(create(creatureToken("Knight", listOf(Color.White), 2, 2, Keyword.Vigilance))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    /** Any graveyard, also cards an opponent owns (CR 614.1a); a reminder-only line gives no ability. */
    @Test
    fun groveMilitia() = card(
        name = "Grove Militia",
        cost = "{G/W}",
        type = "Creature — Dryad Soldier",
        pt = "2/1",
        text = """
            ({G/W} can be paid with either {G} or {W}.)
            If an instant or sorcery card would be put into a graveyard from anywhere, exile it instead.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Or(listOf(Rtr.instant, Rtr.sorcery)), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun communalGrace() = card(
        name = "Communal Grace",
        cost = "{3}{G}{G}{W}",
        type = "Enchantment",
        text = "Creatures you control get +3/+3.",
    ) parsesTo abilities(
        static(creaturesYouControl, Mechanic.ModifyPowerToughness(n(3), n(3))),
    )

    @Test
    fun flotillaWurm() = card(
        name = "Flotilla Wurm",
        cost = "{2}{G}{G}{W}{W}",
        type = "Creature — Wurm",
        pt = "5/5",
        text = """
            Trample
            When this creature enters, create a 5/5 green Wurm creature token with trample.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(thisEnters, create(creatureToken("Wurm", listOf(Color.Green), 5, 5, Keyword.Trample))),
    )

    @Test
    fun summonsOfTheGrove() = card(
        name = "Summons of the Grove",
        cost = "{G}{W}",
        type = "Sorcery",
        text = "Create a 3/3 green Centaur creature token.",
    ) parsesTo abilities(
        spell(create(centaur)),
    )

    @Test
    fun centaurMender() = card(
        name = "Centaur Mender",
        cost = "{1}{G}{W}",
        type = "Creature — Centaur Cleric",
        pt = "3/3",
        text = "When this creature enters, you gain 3 life.",
    ) parsesTo abilities(
        triggered(thisEnters, gainLife(3)),
    )
}
