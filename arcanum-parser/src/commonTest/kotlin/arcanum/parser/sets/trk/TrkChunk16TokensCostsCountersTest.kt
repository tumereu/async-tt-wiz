package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 16: attacking despite defender, can't block, cost changes, counters, tokens, damage with replacements. */
class TrkChunk16TokensCostsCountersTest {
    private val borgArtifact = creatureToken("Borg", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
    private val officer = creatureToken("Officer", listOf(Color.Red), 1, 1)
    private val noncreature = not(creature)
    private val cantBlock = Mechanic.CantBlock(attackers = ObjectFilter.Any)

    private fun ownerChoosesTopOrBottom(target: Objects) = listOf(
        Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target)),
        Step.If(Condition.Chose("position", "top"), then = listOf(move(target, Zone.Library, position = 0)),
            otherwise = listOf(move(target, Zone.Library, position = -1))))

    private fun costLess(amount: String, times: Value, condition: Condition? = null) =
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse(amount)!!, times)), condition = condition)

    // CR 702.3 (defender), 508.1 (the attack restriction lifts while the condition holds)
    @Test
    fun starfleetSpannerJockey() = card(
        name = "Fleet Spanner Rigger",
        cost = "{1}{U}",
        type = "Creature — Human Artificer",
        pt = "3/3",
        text = """
            Defender
            As long as you've cast a noncreature spell this turn, this creature can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(Mechanic.CanAttackDespiteDefender,
            condition = Condition.HappenedThisTurn(EventPattern.Cast(noncreature, PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
    )

    // CR 509.1b: a creature that can't block
    @Test
    fun storiedVeteran() = card(
        name = "Veteran Of Old Tales",
        cost = "{B}",
        type = "Creature — Klingon Advisor",
        pt = "2/1",
        text = """
            This creature can't block.
            You may cast this card from your graveyard. If you do, it enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        static(cantBlock),
        static(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You)),
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(kind = CounterKind.Finality, amount = n(1)))),
            condition = Condition.CastFrom(zone = Zone.Graveyard, of = Objects.Self)),
    )

    // CR 509.1b, 603.2: an upkeep trigger copying itself
    @Test
    fun troublingTribbles() = card(
        name = "Multiplying Fuzzballs",
        cost = "{2}{W}",
        type = "Creature — Tribble",
        pt = "0/1",
        text = """
            This creature can't block.
            At the beginning of your upkeep, create a token that's a copy of this creature.
        """,
    ) parsesTo abilities(
        static(cantBlock),
        triggered(yourUpkeep, Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You))),
    )

    // CR 601.2f (a cost reduction conditional on the target), 608.2d (the owner chooses)
    @Test
    fun guidanceFailure() = card(
        name = "Navigation Mishap",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets an attacking creature.
            Target creature or Spacecraft's owner puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo abilities(
        costLess("{1}", n(1), Condition.ObjectMatches(target("permanent"), and(attacking, creature))),
        spell(*ownerChoosesTopOrBottom(target("permanent")).toTypedArray(), targets = listOf(slot("permanent", or(creature, sub("Spacecraft"))))),
    )

    // CR 601.2f: one {1} less for each matching card
    @Test
    fun ravenousCosmozoan() = card(
        name = "Gorging Starfish",
        cost = "{5}{R}",
        type = "Creature — Fish",
        pt = "3/3",
        text = """
            This spell costs {1} less to cast for each noncreature, nonland card in your graveyard.
            Flying, haste
        """,
    ) parsesTo abilities(
        costLess("{1}", count(inYourGraveyard(noncreature, nonland))),
        Keyword.Flying,
        Keyword.Haste,
    )

    // CR 601.2f, 506.2 (attacking you), 611.2c
    @Test
    fun redAlert() = card(
        name = "Battle Stations Klaxon",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if a creature is attacking you.
            Creatures you control get +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        costLess("{2}", n(1), Condition.Exists(and(creature, attacking,
            ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You))), atLeast = 1)),
        spell(pump(Objects.Each(creatureYouControl), 2, 0)),
    )

    private val abilitySlot = slot("ability", or(
        and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
        and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered))))

    // CR 700.2 (modes), 701.6 (counter), 122.1d (stun counter)
    @Test
    fun subcommanderTPol() = card(
        name = "Subcommander Vel'Sar",
        cost = "{2}{U}",
        type = "Legendary Creature — Vulcan Scientist",
        pt = "3/1",
        text = """
            Flash
            When Subcommander Vel'Sar enters, choose one —
            • Counter target activated or triggered ability.
            • Tap up to one target artifact or creature. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(doing(Effect.Counter(target("ability"))), targets = listOf(abilitySlot)),
            mode(tapIt(target("permanent")), counters(target("permanent"), kind = CounterKind.Stun),
                targets = listOf(slot("permanent", or(artifact, creature), min = 0))),
        ))),
    )

    // CR 700.2d (choose two), 701.6, 118.12 (unless pays), 702.2 (can't be blocked)
    @Test
    fun wesleyCrusher() = card(
        name = "Wendell Cadet Prodigy",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Human Officer",
        pt = "2/2",
        text = """
            Flash
            When Wendell Cadet Prodigy enters, choose two —
            • Counter target noncreature spell unless its controller pays {2}.
            • Return target creature with mana value 2 or less to its owner's hand.
            • Target creature can't be blocked this turn.
            • Target player draws two cards, then discards two cards.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(
                Step.Pay(cost = manaCost("{2}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
                targets = listOf(slot("spell", and(noncreature, spellFilter))),
            ),
            mode(move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(creature, manaValue(Comparison.AtMost, 2))))),
            mode(untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
            mode(draw(2, Players.Target("player")), discard(2, Players.Target("player")), targets = listOf(targetPlayer)),
            min = 2, max = 2,
        ))),
    )

    // CR 111.1 (creating a token), 205.3 (token types)
    @Test
    fun assemblySpecialist() = card(
        name = "Assembly Line Tinkerer",
        cost = "{1}{W}",
        type = "Artifact Creature — Borg",
        pt = "1/1",
        text = "When this creature enters, create a 1/1 colorless Borg artifact creature token.",
    ) parsesTo abilities(triggered(entersSelf, create(borgArtifact)))

    // CR 111.1, 702.10 (haste on the token)
    @Test
    fun chaDichInvestigator() = card(
        name = "Ch'Vok Inquisitor",
        cost = "{4}{B}",
        type = "Creature — Klingon Warrior",
        pt = "4/4",
        text = "When this creature enters, create a 2/1 red Klingon Warrior creature token with haste.",
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Klingon Warrior", listOf(Color.Red), 2, 1, abilities = listOf(Keyword.Haste)))),
    )

    // CR 603.2, 115.1 (becoming the target), 110.5 (tapped token)
    @Test
    fun macoStrikeTeam() = card(
        name = "Strike Team Ranger",
        cost = "{G}",
        type = "Creature — Human Soldier",
        pt = "2/1",
        text = "Whenever a creature you control becomes the target of a spell or ability an opponent controls, create a tapped 1/1 red Officer creature token.",
    ) parsesTo abilities(
        triggered(on(EventPattern.BecomesTarget(and(creature, you), by = opponents)),
            Step.Do(Effect.CreateToken(officer, n(1), Players.You, tapped = true))),
    )

    // CR 602.5d (sorcery timing), 111.1
    @Test
    fun photosyntheticPhysician() = card(
        name = "Greenhouse Surgeon",
        cost = "{4}{G}",
        type = "Creature — Plant Doctor",
        pt = "5/5",
        text = """
            Reach
            {G}, Exile this card from your graveyard: Create a 1/1 red Officer creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(mana("{G}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), create(officer), rules = asSorcery),
    )

    // CR 700.4 (dies), 111.1
    @Test
    fun tenaciousDrone() = card(
        name = "Persistent Assimilant",
        cost = "{1}{B}",
        type = "Artifact Creature — Borg",
        pt = "2/1",
        text = "When this creature dies, create a 1/1 colorless Borg artifact creature token.",
    ) parsesTo abilities(triggered(diesSelf, create(borgArtifact)))

    // CR 120.3 (damage), 614.1a (a replacement lasting until end of turn)
    @Test
    fun khanSpell() = card(
        name = "Khaaaan Roar",
        cost = "{X}{R}",
        type = "Instant",
        text = "Khaaaan Roar deals twice X damage to target creature. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(
            damage(Value.Times(Value.Announced("X"), 2), toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")),
                from = Zone.Battlefield, to = Zone.Graveyard), modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
                null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)), announced = listOf("X"),
        ),
    )

    // CR 608.2c (the instead version), 700.4 (died this turn)
    @Test
    fun planetaryBombardment() = card(
        name = "Orbital Barrage",
        cost = "{R}",
        type = "Sorcery",
        text = "Orbital Barrage deals 2 damage to target creature. Orbital Barrage deals 4 damage to that creature instead if a creature died this turn.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.HappenedThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events),
                damage(4, toObjects(target("creature"))), otherwise = listOf(damage(2, toObjects(target("creature"))))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 602.2 (activated abilities), 302.6 (tapping), 603.2: both abilities share the creature's tapped state
    @Test
    fun talShiarAnalyst() = card(
        name = "Obsidian Order Analyst",
        cost = "{2}{R}",
        type = "Creature — Romulan Scientist",
        pt = "3/2",
        text = """
            {T}: This creature deals 1 damage to each opponent.
            Whenever you cast a noncreature spell, untap this creature.
        """,
    ) parsesTo abilities(
        activated(cost(tap), damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent)))),
        triggered(youCast(noncreature), untapIt(Objects.Self)),
    )
}
