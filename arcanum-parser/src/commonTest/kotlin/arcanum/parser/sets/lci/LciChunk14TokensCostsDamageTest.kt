package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Recipients
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 14: additional costs, defender override, cost changes, token creation and damage to each opponent. */
class LciChunk14TokensCostsDamageTest {
    private val gnome = creatureToken("Gnome", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
    private val counterSpell = Instructions(
        targets = listOf(TargetSlot("spell", TargetKind.Objects(spellFilter), Value.Fixed(1), Value.Fixed(1))),
        steps = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))),
    )

    // CR 118.8 (additional costs), 601.2b: a choice of two costs
    @Test
    fun grimRecompense() = card(
        name = "Grim Recompense",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card or pay 3 life.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AdditionalCost(cost = Cost(listOf(
                CostPart.OneOf(listOf(
                    Cost(listOf(discardACard)),
                    Cost(listOf(CostPart.PayLife(Value.Fixed(3)))),
                )),
            ))),
        ), affects = Affected.Objects(Objects.Self)),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 118.8, 701.26a: the additional cost taps four untapped permanents you control (the cost's choice only reaches the caster's own)
    @Test
    fun sentinelOfTheGrandGate() = card(
        name = "Sentinel of the Grand Gate",
        cost = "{W}{W}",
        type = "Creature — Angel",
        pt = "4/4",
        text = """
            As an additional cost to cast this spell, tap four untapped artifacts, creatures, and/or lands you control.
            Flying
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = Cost(listOf(
            CostPart.TapObjects(CostObjects.Choose(or(artifact, creature, land), n(4))))))), affects = Affected.Objects(Objects.Self)),
        Keyword.Flying,
    )

    // CR 702.3b: a creature with defender can attack if an effect says so; 603.4-style turn history condition
    @Test
    fun wreckLookout() = card(
        name = "Wreck Lookout",
        cost = "{1}{U}",
        type = "Creature — Human Pirate",
        pt = "3/3",
        text = """
            Defender
            As long as an artifact entered the battlefield under your control this turn, this creature can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(
            Mechanic.CanAttackDespiteDefender,
            condition = Condition.HappenedThisTurn(
                event = EventPattern.ZoneChange(objects = and(artifact, you), to = Zone.Battlefield),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
    )

    // CR 601.2f: the cost reduction applies when the spell targets a creature spell
    @Test
    fun smotheringGasp() = card(
        name = "Smothering Gasp",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets a creature spell.
            Counter target spell.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.ObjectMatches(target("spell"), creature)),
        Ability.Spell(counterSpell),
    )

    // CR 601.2f
    @Test
    fun sinkingEddy() = card(
        name = "Sinking Eddy",
        cost = "{5}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped creature.
            Exile target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), tapped)),
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", creature))),
    )

    // CR 603.2: one or more creatures becoming tapped trigger once; 111.2
    @Test
    fun rootboundProcession() = card(
        name = "Rootbound Procession",
        cost = "{1}{U}",
        type = "Enchantment",
        text = "Whenever one or more nontoken Merfolk you control become tapped, create a 1/1 blue Merfolk creature token with hexproof.",
    ) parsesTo abilities(
        triggered(
            Trigger.On(EventPattern.Tap(and(not(token), sub("Merfolk"), you)), oneOrMore = true),
            create(creatureToken("Merfolk", listOf(Color.Blue), 1, 1, abilities = listOf(Keyword.Hexproof))),
        ),
    )

    // CR 602.1, 111.2
    @Test
    fun emissaryOfTezcal() = card(
        name = "Emissary of Tezcal",
        cost = "{2}{W}",
        type = "Creature — Cat Advisor",
        pt = "3/3",
        text = "{4}{W}: Create a 1/1 colorless Gnome artifact creature token.",
    ) parsesTo abilities(activated(cost(mana("{4}{W}")), create(gnome)))

    // CR 603.1, 111.2
    @Test
    fun oltecLampGuard() = card(
        name = "Oltec Lamp Guard",
        cost = "{3}{W}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = """
            Flying
            When this creature enters, create a 1/1 colorless Gnome artifact creature token.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, create(gnome)))

    // CR 603.4 (intervening if), 603.2 (each end step), 119.1 (life gained this turn)
    @Test
    fun radiantSeraphOfRuin() = card(
        name = "Radiant Seraph of Ruin",
        cost = "{1}{W}{W}",
        type = "Creature — Angel",
        pt = "3/3",
        text = """
            Flying
            At the beginning of each end step, if you gained 5 or more life this turn, create a 4/4 white Angel creature token with flying and vigilance.
            {3}{W}{W}{W}: Until end of turn, this creature gets +2/+2 and gains lifelink.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any),
            create(creatureToken("Angel", listOf(Color.White), 4, 4, abilities = listOf(Keyword.Flying, Keyword.Vigilance))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 5, measure = Measure.Amount)),
        activated(cost(mana("{3}{W}{W}{W}")), pump(Objects.Self, 2, 2, has(Keyword.Lifelink))),
    )

    // CR 702.91a (battle cry), 603.1 (enters or dies in one ability)
    @Test
    fun duskStandardBearer() = card(
        name = "Dusk Standard-Bearer",
        cost = "{2}{W}",
        type = "Creature — Vampire Cleric",
        pt = "2/1",
        text = """
            Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)
            When this creature enters or dies, create a 1/1 black Bat creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.BattleCry,
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))),
            create(creatureToken("Bat", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Flying)))),
    )

    // CR 702.34a (flashback), 707.2 (token copy)
    @Test
    fun echoRite() = card(
        name = "Echo Rite",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Create a token that's a copy of target creature you control.
            Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(TokenSpec.CopyOf(target("creature"), emptyList())), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Flashback(manaCost("{3}{U}")),
    )

    // CR 111.2, 508.1 (can't block)
    @Test
    fun fungalReaper() = card(
        name = "Fungal Reaper",
        cost = "{2}{B}",
        type = "Creature — Fungus Wizard",
        pt = "3/1",
        text = "When this creature dies, create two 1/1 black Fungus creature tokens with \"This token can't block.\"",
    ) parsesTo abilities(
        triggered(diesSelf, create(creatureToken("Fungus", listOf(Color.Black), 1, 1, abilities = listOf(Ability.Static(
            listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)))), n(2))),
    )

    // CR 603.1, 111.2, 602.1
    @Test
    fun gearmakersSatchel() = card(
        name = "Gearmaker's Satchel",
        cost = "{2}{W}",
        type = "Artifact",
        text = """
            When this artifact enters, create two 1/1 colorless Gnome artifact creature tokens.
            {W}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(gnome, n(2))),
        activated(cost(mana("{W}"), sacrificeSelf), gainLife(3)),
    )

    // CR 700.2: each mode has its own target
    @Test
    fun scorchSweep() = card(
        name = "Scorch Sweep",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Scorch Sweep deals 3 damage to target creature.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(target("creature")), source = Objects.Self), targets = listOf(slot("creature", creature))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ))),
    )

    // CR 120.3 (damage), 602.1
    @Test
    fun cindercrestSaurian() = card(
        name = "Cindercrest Saurian",
        cost = "{4}{R}",
        type = "Creature — Dinosaur",
        pt = "4/5",
        text = """
            Reach
            {T}: This creature deals 2 damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(tap), damage(2, Recipients(players = Players.Each(PlayerFilter.Opponent)))),
    )

    // CR 117.1b (sorcery timing), 701.26a (tap cost), 120.3
    @Test
    fun gantryRaider() = card(
        name = "Gantry Raider",
        cost = "{1}{R}",
        type = "Creature — Human Soldier",
        pt = "1/3",
        text = "Tap two untapped artifacts and/or creatures you control: This creature deals 1 damage to each opponent. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), n(2)))),
            damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent))), rules = asSorcery),
    )
}
