package arcanum.parser.sets.dgm

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Doubled
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.anyTime
import arcanum.parser.sets.gtc.costOf
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureToken
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.diesSelf
import arcanum.parser.sets.gtc.entersSelf
import arcanum.parser.sets.gtc.manaPart
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.p1p1
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.sorcerySpeed
import arcanum.parser.sets.gtc.steps
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.untilEndOfTurn
import arcanum.parser.sets.gtc.yourUpkeep
import kotlin.test.Test

/**
 * Dragon's Maze chunk 07: multicoloured cards, Rot Farm Corpse to Zhur-Taa Shaman. An ability that works only from the
 * graveyard (CR 113.6m) with a mill cost, a punishing trigger for noncreature spells, X counters as it enters (CR
 * 107.3m), a fight with a randomly chosen target, a granted dies trigger with its own target, a revealed hand and a
 * chosen card exiled, each player discarding their hand, protection from creatures, three different tokens, an Aura,
 * an enters pump for the entering object, a trigger only during your turn (Voice: two triggered abilities), doubling
 * counters (CR 701.10e), "+X/-X" from the hand size, landwalk, and triggered mana abilities (CR 605.1b).
 */
class DgmChunk07MulticoloredCTest {
    private val creaturesYouControl = Value.Count(Objects.Each(creatureYouControl))
    private val elemental = TokenSpec.Defined(FaceDefinition("Elemental Token", colorIndicator = listOf(Color.Green, Color.White), types = listOf(CardType.Creature),
        subtypes = listOf(Subtype("Elemental")), power = PrintedNumber.Star(), toughness = PrintedNumber.Star(), abilities = listOf(
            Ability.Static(listOf(Mechanic.SetPowerToughness(creaturesYouControl, creaturesYouControl)), affects = Affected.Objects(Objects.Self), characteristicDefining = true),
        )))

    // CR 113.6m: "from your graveyard" — it works there
    @Test
    fun rotFarmCorpse() = card(
        name = "Rot Farm Corpse",
        cost = "{2}{B}{G}",
        type = "Creature — Plant Skeleton",
        pt = "4/1",
        text = """
            This creature can't block.
            {2}{B}{G}, Mill four cards: Return this card from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)),
        Ability.Activated(costOf(manaPart("{2}{B}{G}"), arcanum.api.CostPart.Mill(n(4))),
            steps(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield), postBind = "returned"))), rules = sorcerySpeed),
    )

    // The casting player is dealt the damage
    @Test
    fun ruricThalTheUnyielding() = card(
        name = "Ruric Thal, the Unyielding",
        cost = "{4}{R}{G}",
        type = "Legendary Creature — Ogre Warrior",
        pt = "6/6",
        text = """
            Reach, vigilance
            Ruric Thal attacks each combat if able.
            Whenever a player casts a noncreature spell, Ruric Thal deals 6 damage to that player.
        """,
    ) parsesTo abilities(
        Keyword.Reach, Keyword.Vigilance,
        Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), by = PlayerFilter.Any)),
            steps(Step.Do(Effect.DealDamage(Recipients(players = Players.EventPlayer), n(6), source = Objects.Self)))),
    )

    // CR 107.3m: X is the value announced when it was cast; hybrid {R/G}
    @Test
    fun savagebredHydra() = card(
        name = "Savagebred Hydra",
        cost = "{X}{R}{G}",
        type = "Creature — Hydra",
        pt = "0/0",
        text = """
            Double strike
            This creature enters with X +1/+1 counters on it.
            {1}{R/G}: Put a +1/+1 counter on this creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))), affects = Affected.Objects(Objects.Self)),
        Ability.Activated(costOf(manaPart("{1}{R/G}")), steps(Step.Do(Effect.PutCounters(Objects.Self, p1p1, n(1)))), rules = sorcerySpeed),
    )

    // The target is chosen at random by the host; CR 701.14a: fight
    @Test
    fun scabClanBrute() = card(
        name = "Scab-Clan Brute",
        cost = "{4}{R}{G}",
        type = "Creature — Giant Warrior",
        pt = "4/5",
        text = "When this creature enters, it fights target creature an opponent controls chosen at random.",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(
            targets = listOf(slot("creature", creatureOpponentControls).copy(atRandom = true)),
            steps = listOf(Step.Do(Effect.Fight(Objects.Self, Objects.Target("creature")))),
        )),
    )

    // CR 113.1c: the quoted ability, with its own target chosen when it triggers
    @Test
    fun showstealer() = card(
        name = "Showstealer",
        cost = "{1}{B}{R}",
        type = "Instant",
        text = "Until end of turn, creatures you control gain \"When this creature dies, it deals 2 damage to target creature an opponent controls.\"",
    ) parsesTo abilities(
        Ability.Spell(steps(untilEndOfTurn(Objects.Each(creatureYouControl), Mechanic.GainAbilities(listOf(
            Ability.Triggered(diesSelf, Instructions(targets = listOf(slot("creature", creatureOpponentControls)),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), n(2), source = Objects.Self))))),
        ))))),
    )

    // The revealed hand; an instant or sorcery card is chosen and exiled
    @Test
    fun sinGatherer() = card(
        name = "Sin Gatherer",
        cost = "{1}{W}{B}",
        type = "Creature — Human Cleric",
        pt = "2/1",
        text = "When this creature enters, target opponent reveals their hand. You choose an instant or sorcery card from it and exile that card.",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                Step.Do(Effect.Reveal(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))))), bind = "revealed")),
                Step.Choose(Choice.Objects(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))), n(1), n(1), from = Objects.Bound("revealed")),
                    bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
            ),
        )),
    )

    // CR 101.4: all at once
    @Test
    fun sireOfMadness() = card(
        name = "Sire of Madness",
        cost = "{4}{B}{R}",
        type = "Creature — Demon",
        pt = "6/4",
        text = "At the beginning of each end step, each player discards their hand.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), steps(
            Step.Do(Effect.Discard(Players.Each(PlayerFilter.Any), n(1), filter = ObjectFilter.Any, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)))),
        )),
    )

    // Not targeted: chosen as it resolves
    @Test
    fun speciesDevourer() = card(
        name = "Species Devourer",
        cost = "{3}{G}{U}",
        type = "Creature — Frog Beast",
        pt = "6/6",
        text = "At the beginning of your upkeep, return a creature you control to its owner's hand.",
    ) parsesTo abilities(
        Ability.Triggered(yourUpkeep, steps(
            Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
        )),
    )

    // CR 702.16: protection from creatures; "that creature" is the damage's source
    @Test
    fun teyraEnvoyOfGhosts() = card(
        name = "Teyra, Envoy of Ghosts",
        cost = "{5}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "4/4",
        text = """
            Vigilance, protection from creatures
            Whenever a creature deals combat damage to you, destroy that creature. Create a 1/1 white and black Spirit creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Protection(creature),
        Ability.Triggered(Trigger.On(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.You), combat = true)), steps(
            Step.Do(Effect.Destroy(Objects.EventObject(EventRole.Source))),
            Step.Do(Effect.CreateToken(creatureToken("Spirit", listOf(Color.White, Color.Black), 1, 1, listOf(Keyword.Flying)), n(1), Players.You)),
        )),
    )

    // CR 702.101: extort
    @Test
    fun titheSipper() = card(
        name = "Tithe Sipper",
        cost = "{W}{B}",
        type = "Creature — Vampire",
        pt = "2/1",
        text = """
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        """,
    ) parsesTo abilities(Keyword.Lifelink, Keyword.Extort)

    @Test
    fun trostanisCaller() = card(
        name = "Trostani's Caller",
        cost = "{5}{G}{W}",
        type = "Creature — Elf Shaman",
        pt = "1/1",
        text = "When this creature enters, create a 2/2 white Knight creature token with vigilance, a 3/3 green Centaur creature token, and a 4/4 green Rhino creature token with trample.",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, steps(
            Step.Do(Effect.CreateToken(creatureToken("Knight", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance)), n(1), Players.You)),
            Step.Do(Effect.CreateToken(creatureToken("Centaur", listOf(Color.Green), 3, 3), n(1), Players.You)),
            Step.Do(Effect.CreateToken(creatureToken("Rhino", listOf(Color.Green), 4, 4, listOf(Keyword.Trample)), n(1), Players.You)),
        )),
    )

    @Test
    fun unflinchingValor() = card(
        name = "Unflinching Valor",
        cost = "{1}{G}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has trample and lifelink. (Damage dealt by the creature also causes its controller to gain that much life.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(2), n(2)), Mechanic.GainAbilities(listOf(Keyword.Trample, Keyword.Lifelink))),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
    )

    // "it" is the creature that entered
    @Test
    fun viashinoFirstblood() = card(
        name = "Viashino Firstblood",
        cost = "{1}{R}{W}",
        type = "Creature — Lizard Soldier",
        pt = "2/2",
        text = """
            Haste
            When this creature enters, it gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Haste, Ability.Triggered(entersSelf, Instructions(steps = listOf(pump(Objects.EventObject(EventRole.After), 2, 2)))))

    // Two triggered abilities in one sentence; the first triggers only during your turn (CR 603.2); CR 604.3: the token's
    // characteristic-defining ability
    @Test
    fun voiceOfReawakening() = card(
        name = "Voice of Reawakening",
        cost = "{G}{W}",
        type = "Creature — Elemental",
        pt = "2/2",
        text = "Whenever an opponent casts a spell during your turn and when this creature dies, create a green and white Elemental creature token with \"This token's power and toughness are each equal to the number of creatures you control.\"",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.Opponent)), steps(Step.Do(Effect.CreateToken(elemental, n(1), Players.You))),
            triggersOnlyIf = Condition.YourTurn),
        Ability.Triggered(diesSelf, steps(Step.Do(Effect.CreateToken(elemental, n(1), Players.You)))),
    )

    // CR 701.10e: double each kind of counter
    @Test
    fun vorelOfTheHullClan() = card(
        name = "Vorel of the Hull Clan",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Human Merfolk",
        pt = "1/4",
        text = "{G}{U}, {T}: Double the number of each kind of counter on target artifact, creature, or land.",
    ) parsesTo abilities(
        Ability.Activated(costOf(manaPart("{G}{U}"), arcanum.api.CostPart.TapSelf), Instructions(
            targets = listOf(slot("permanent", ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature, ObjectFilter.Type(CardType.Land))))),
            steps = listOf(Step.Do(Effect.Action(KeywordAction.Double(Doubled.Counters(Objects.Target("permanent"), null))))),
        ), rules = anyTime),
    )

    @Test
    fun warleadersSpiral() = card(
        name = "Warleader's Spiral",
        cost = "{2}{R}{W}",
        type = "Instant",
        text = "Warleader's Spiral deals 4 damage to any target and you gain 4 life.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(anyTarget), steps = listOf(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), n(4), source = Objects.Self)),
            Step.Do(Effect.GainLife(Players.You, n(4))),
        ))),
    )

    // CR 608.2h: X counted as it resolves
    @Test
    fun warpedForm() {
        val hand = Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        card(
            name = "Warped Form",
            cost = "{U}{B}",
            type = "Instant",
            text = "Target creature gets +X/-X until end of turn, where X is the number of cards in your hand.",
        ) parsesTo abilities(
            Ability.Spell(Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(untilEndOfTurn(Objects.Target("creature"), Mechanic.ModifyPowerToughness(hand, Value.Times(hand, -1)))))),
        )
    }

    // CR 605.1b: a triggered mana ability; it adds one mana of a type the land produced (CR 106.7)
    @Test
    fun zhurTaaElder() = card(
        name = "Zhur-Taa Elder",
        cost = "{3}{R}{G}",
        type = "Creature — Beast",
        pt = "7/5",
        text = "Whenever a player taps a land for mana, that player adds one mana of any type that land produced.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Tap(arcanum.parser.sets.gtc.land, forMana = true)), steps(
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.SameAsProduced(n(1))), Players.ControllerOf(Objects.EventObject(EventRole.Object)))),
        )),
    )

    // CR 605.1b: it triggers from a mana ability, but isn't a mana ability itself (it deals damage): it uses the Stack
    @Test
    fun zhurTaaShaman() = card(
        name = "Zhur-Taa Shaman",
        cost = "{R}{G}",
        type = "Creature — Human Druid",
        pt = "1/1",
        text = """
            {T}: Add {G}.
            Whenever you tap this creature for mana, it deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        Ability.Activated(costOf(arcanum.api.CostPart.TapSelf), steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))), rules = anyTime),
        Ability.Triggered(Trigger.On(EventPattern.Tap(ObjectFilter.Is(Objects.Self), forMana = true)),
            steps(Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), n(1), source = Objects.Self)))),
    )
}
