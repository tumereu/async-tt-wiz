package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 8: conditions ("you control an artifact and an enchantment", "this creature is modified", "entered this turn", "If it's a … instead"),
 * cost changes (two conditional reductions, per target, per creature tapped, the next spell) and repeated or chosen-from-zones processes. */
class NeoChunk08ConditionsAndCostsTest {
    private val samuraiToken = creatureToken("Samurai", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance))
    private val modified: ObjectFilter = ObjectFilter.Modified
    private val spellsBy = { who: PlayerFilter -> and(spellFilter, ObjectFilter.ControlledBy(who)) }
    private val enteredThisTurn = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)))

    // CR 608.2c (the condition checked as the spell resolves), 702.15a
    @Test
    fun whenWeWereYoung() = card(
        name = "When We Were Bold",
        cost = "{3}{W}",
        type = "Instant",
        text = "Up to two target creatures each get +2/+2 until end of turn. If you control an artifact and an enchantment, those creatures also gain lifelink until end of turn.",
    ) parsesTo abilities(
        spell(
            pump(target("creatures"), 2, 2),
            ifThen(controlArtifactAndEnchantment, gains(target("creatures"), Keyword.Lifelink)),
            targets = listOf(slot("creatures", creature, min = 0, max = 2)),
        ),
    )

    // CR 601.2f (each reduction applies while its condition holds)
    @Test
    fun assassinsInk() = card(
        name = "Shadowblade Ink",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control an artifact and {1} less to cast if you control an enchantment.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(artifact, you), atLeast = 1)),
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(enchantment, you), atLeast = 1)),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 608.2h (the target's types as the spell resolves); "instead": the first sentence happens only when the condition is false
    @Test
    fun regentsAuthority() = card(
        name = "Regent's Mandate",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. If it's an enchantment creature or legendary creature, instead put a +1/+1 counter on it and it gets +1/+1 until end of turn.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.ObjectMatches(target("creature"), or(and(enchantment, creature), and(legendary, creature))),
                counters(target("creature")), pump(target("creature"), 1, 1),
                otherwise = listOf(pump(target("creature"), 2, 2))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 205.1b (a creature type replaces the others), 613.1d (layer 4), 613.4b (base power and toughness, layer 7b), 611.3a, 509.1b
    @Test
    fun futuristOperative() = card(
        name = "Neon Operative",
        cost = "{3}{U}",
        type = "Creature — Human Ninja",
        pt = "3/4",
        text = """
            As long as this creature is tapped, it's a Human Citizen with base power and toughness 1/1 and can't be blocked.
            {2}{U}: Untap this creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Human")), SubtypeRef.Literal(Subtype("Citizen"))), replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)), Mechanic.CantBeBlocked,
            condition = Condition.ObjectMatches(Objects.Self, tapped)),
        activated(manaCost("{2}{U}"), untapIt(Objects.Self)),
    )

    // CR 606.3 (loyalty abilities: as a sorcery, once each turn; this lets them be activated at instant speed while it entered this turn),
    // 702.8a, 122.1, 111.1, 701.37a
    @Test
    fun theWanderingEmperor() = card(
        name = "The Roaming Empress",
        cost = "{2}{W}{W}",
        type = "Legendary Planeswalker",
        loyalty = "3",
        text = """
            Flash
            As long as The Roaming Empress entered this turn, you may activate her loyalty abilities any time you could cast an instant.
            [+1]: Put a +1/+1 counter on up to one target creature. It gains first strike until end of turn.
            [−1]: Create a 2/2 white Samurai creature token with vigilance.
            [−2]: Exile target tapped creature. You gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.LoyaltyAtInstantSpeed, condition = enteredThisTurn),
        loyalty(1, counters(target("creature")), gains(target("creature"), Keyword.FirstStrike), targets = listOf(slot("creature", creature, min = 0))),
        loyalty(-1, create(samuraiToken)),
        loyalty(-2, move(target("creature"), Zone.Exile), gainLife(2), targets = listOf(slot("creature", and(tapped, creature)))),
    )

    // CR 701.21a (each sacrifice of the player's choice, in turn), 609.3 ("can't": none of that type to sacrifice), 115.1
    @Test
    fun invokeDespair() {
        val opponent = Players.Target("opponent")
        fun process(type: ObjectFilter, bind: String) = arrayOf(
            Step.Choose(Choice.Objects(and(type, ObjectFilter.ControlledBy(PlayerFilter.Is(opponent))), n(1), n(1)), bind = bind, chooser = opponent),
            doing(Effect.Sacrifice(Objects.Bound(bind))),
            ifThen(Condition.Not(Condition.NonEmpty(bind)), loseLife(2, opponent), draw(1)),
        )
        card(
            name = "Summon Dread",
            cost = "{1}{B}{B}{B}{B}",
            type = "Sorcery",
            text = "Target opponent sacrifices a creature of their choice. If they can't, they lose 2 life and you draw a card. Then repeat this process for an enchantment and a planeswalker.",
        ) parsesTo abilities(
            spell(
                *process(creature, "sacrificed"),
                *process(enchantment, "sacrificed 2"),
                *process(ObjectFilter.Type(CardType.Planeswalker), "sacrificed 3"),
                targets = listOf(targetOpponent),
            ),
        )
    }

    // CR 700.9 (modified), 611.3a, 605.1a
    @Test
    fun orochiMergeKeeper() = card(
        name = "Orochi Graft-Tender",
        cost = "{1}{G}",
        type = "Creature — Snake Druid",
        pt = "1/1",
        text = """
            {T}: Add {G}.
            As long as this creature is modified, it has "{T}: Add {G}{G}." (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        addMana("{G}"),
        static(has(addMana("{G}{G}")), condition = Condition.ObjectMatches(Objects.Self, modified)),
    )

    // CR 602.5b (an activation restriction), 700.9, 506.4
    @Test
    fun goroGoroDiscipleOfRyusei() = card(
        name = "Rasa-Rasa, Pupil of Ryusei",
        cost = "{1}{R}",
        type = "Legendary Creature — Goblin Samurai",
        pt = "2/2",
        text = """
            {R}: Creatures you control gain haste until end of turn.
            {3}{R}{R}: Create a 5/5 red Dragon Spirit creature token with flying. Activate only if you control an attacking modified creature. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        activated(manaCost("{R}"), untilEndOfTurn(Objects.Each(creatureYouControl), Mechanic.GainAbilities(listOf(Keyword.Haste)))),
        activated(manaCost("{3}{R}{R}"), create(creatureToken("Dragon Spirit", listOf(Color.Red), 5, 5, listOf(Keyword.Flying))),
            rules = anyTime.copy(condition = Condition.Exists(and(attacking, modified, creature, you), atLeast = 1))),
    )

    // CR 603.4 (intervening if), 205.2b (an artifact enchantment is both)
    @Test
    fun kamiOfTerribleSecrets() = card(
        name = "Kami of Hidden Truths",
        cost = "{3}{B}",
        type = "Creature — Spirit",
        pt = "3/4",
        text = "When this creature enters, if you control an artifact and an enchantment, you draw a card and you gain 1 life.",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), gainLife(1), interveningIf = controlArtifactAndEnchantment),
    )

    // CR 603.4, 508.1m
    @Test
    fun naomiPillarOfOrder() = card(
        name = "Hanae, Pillar of Order",
        cost = "{3}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "4/4",
        text = "Whenever Hanae enters or attacks, if you control an artifact and an enchantment, create a 2/2 white Samurai creature token with vigilance.",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, create(samuraiToken), interveningIf = controlArtifactAndEnchantment),
    )

    // CR 601.2b (an additional cost of any number of creatures), 601.2f (the reduction counts them), 118.3 (untapped creatures you control)
    @Test
    fun explosiveSingularity() = card(
        name = "Volatile Singularity",
        cost = "{8}{R}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may tap any number of untapped creatures you control. This spell costs {1} less to cast for each creature tapped this way.
            Volatile Singularity deals 10 damage to any target.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.TapObjects(CostObjects.Choose(creature, count = null), bind = "tapped"))),
            Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(Objects.Bound("tapped"))))),
        spell(damage(10, toAny), targets = listOf(anyTarget)),
    )

    // CR 601.2c (targets come first), 601.2f (each target counted, CR 115.1)
    @Test
    fun hinataDawnCrowned() = card(
        name = "Akari, Dusk-Crowned",
        cost = "{1}{U}{R}{W}",
        type = "Legendary Creature — Kirin Spirit",
        pt = "4/4",
        text = """
            Flying, trample
            Spells you cast cost {1} less to cast for each target.
            Spells your opponents cast cost {1} more to cast for each target.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        static(Mechanic.CostChange(spellsBy(PlayerFilter.You), CostChange.Less(ManaCost.parse("{1}")!!, Value.TargetsOf(Objects.Tested)))),
        static(Mechanic.CostChange(spellsBy(PlayerFilter.Opponent), CostChange.More(ManaCost.parse("{1}")!!, Value.TargetsOf(Objects.Tested)))),
    )

    // CR 608.2g (cast while it resolves), 601.2 (the order chosen), 614.1a (exile instead), 118.9
    @Test
    fun invokeCalamity() = card(
        name = "Summon Catastrophe",
        cost = "{1}{R}{R}{R}{R}",
        type = "Instant",
        text = "You may cast up to two instant and/or sorcery spells with total mana value 6 or less from your graveyard and/or hand without paying their mana costs. If those spells would be put into your graveyard, exile them instead. Exile Summon Catastrophe.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(or(instant, sorcery), or(inZone(Zone.Graveyard), inZone(Zone.Hand)), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(2),
                totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(6))), bind = "toCast", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, bind = "cast", caster = Players.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely)),
            move(Objects.Self, Zone.Exile),
        ),
    )

    // CR 603.4 ("if you cast it": the Aura that entered), 201.2 (different names), 303.4f (put onto the battlefield attached: not cast, no target),
    // 701.23a
    @Test
    fun lightPawsEmperorsVoice() = card(
        name = "Bright-Paws, Empress's Voice",
        cost = "{1}{W}",
        type = "Legendary Creature — Fox Advisor",
        pt = "2/2",
        text = "Whenever an Aura you control enters, if you cast it, you may search your library for an Aura card with mana value less than or equal to that Aura and with a different name than each Aura you control, put that card onto the battlefield attached to Bright-Paws, then shuffle.",
    ) parsesTo abilities(
        triggered(enters(and(sub("Aura"), you)),
            Step.May(listOf(
                search(and(sub("Aura"), not(ObjectFilter.SameNameAs(Objects.Each(and(sub("Aura"), you)))),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.After), Combine.Single)))),
                doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, attachedTo = Recipients(objects = Objects.Self)))),
                shuffle,
            ), player = Players.You),
            interveningIf = Condition.CastFrom(null, Objects.EventObject(EventRole.After))),
    )

    // CR 506.5 (attacks alone), 611.2f (the next spell, as it is cast), 601.2f
    @Test
    fun peerlessSamurai() = card(
        name = "Matchless Samurai",
        cost = "{2}{R}",
        type = "Creature — Human Samurai",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever a Samurai or Warrior you control attacks alone, the next spell you cast this turn costs {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(on(EventPattern.Attack(and(or(sub("Samurai"), sub("Warrior")), you), alone = true)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CostChange(spellsBy(PlayerFilter.You), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
                Affected.NextSpells(spellsBy(PlayerFilter.You), 1), Duration.EndOfTurn))),
    )
}
