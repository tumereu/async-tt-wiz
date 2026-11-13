package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 03: collect evidence (CR 701.59), as an optional additional cost linked to "if evidence was collected"
 * (CR 701.59c, 607.2), as a cost of activated abilities, as an effect and as an alternative cost. */
class MkmChunk03CollectEvidenceTest {
    private val notYours = ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))

    // CR 701.59c: one search or the other; CR 701.23a
    @Test
    fun siftTheSpores() = card(
        name = "Sift the Spores",
        cost = "{G}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may collect evidence 8. (Exile cards with total mana value 8 or greater from your graveyard.)
            Search your library for a basic land card. If evidence was collected, instead search your library for a creature or land card. Reveal that card, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        mayCollectEvidenceAsAdditionalCost(8),
        spell(
            ifThen(evidenceCollected,
                search(or(creature, land), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle,
                otherwise = listOf(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle)),
        ),
    )

    // CR 701.59c; CR 613.4b: base power and toughness 1/1 instead of 4/3
    @Test
    fun underTheVeil() = card(
        name = "Under the Veil",
        cost = "{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may collect evidence 6. (Exile cards with total mana value 6 or greater from your graveyard.)
            Until end of turn, target artifact or creature becomes an artifact creature with base power and toughness 4/3. If evidence was collected, it has base power and toughness 1/1 until end of turn instead.
        """,
    ) parsesTo abilities(
        mayCollectEvidenceAsAdditionalCost(6),
        spell(
            ifThen(evidenceCollected,
                untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(1), n(1))),
                otherwise = listOf(untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
                    Mechanic.SetPowerToughness(n(4), n(3))))),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
    )

    // CR 601.2f: the reduction counts once the optional additional cost is chosen; CR 701.59c
    @Test
    fun clampOnGuilt() = card(
        name = "Clamp on Guilt",
        cost = "{3}{G}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may collect evidence 6. This spell costs {2} less to cast if evidence was collected. (To collect evidence 6, exile cards with total mana value 6 or greater from your graveyard.)
            Target creature you control gets +2/+0 until end of turn. It deals damage equal to its power to target creature you don't control.
        """,
    ) parsesTo abilities(
        mayCollectEvidenceAsAdditionalCost(6),
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
            affects = Affected.Objects(Objects.Self), condition = evidenceCollected),
        spell(
            pump(target("creature"), 2, 0),
            damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature you don't control", and(creature, notYours))),
        ),
    )

    // CR 118.9: an alternative cost for the spells you cast; CR 701.59a
    @Test
    fun plotUntangler() = card(
        name = "Plot Untangler",
        cost = "{5}{U}{U}",
        type = "Creature — Sphinx Detective",
        pt = "6/6",
        text = """
            Flying
            You may collect evidence 10 rather than pay the mana cost for spells you cast. (To collect evidence 10, exile cards with total mana value 10 or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost(listOf(collectEvidence(10))))), affects = Affected.Objects(Objects.Each(ObjectFilter.WasCast()))),
    )

    // CR 701.59c: an enters trigger of the permanent refers to how it was cast; CR 122.1d: stun counter
    @Test
    fun patrolPixie() = card(
        name = "Patrol Pixie",
        cost = "{2}{U}",
        type = "Creature — Faerie Detective",
        pt = "2/2",
        text = """
            As an additional cost to cast this spell, you may collect evidence 6. (Exile cards with total mana value 6 or greater from your graveyard.)
            Flying
            When this creature enters, tap target creature. If evidence was collected, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        mayCollectEvidenceAsAdditionalCost(6),
        Keyword.Flying,
        triggered(entersSelf, tapIt(target("creature")), ifThen(evidenceCollected, counters(target("creature"), 1, CounterKind.Stun)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 701.59c; CR 701.23a: the owner's graveyard, hand and library searched for cards with the exiled card's name; the card from an
    // opponent's graveyard is chosen as it resolves (any opponent's, with several)
    @Test
    fun grimHushUp() {
        val owner = Players.OwnerOf(Objects.Bound("exiled"))
        val zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library)
        card(
            name = "Grim Hush-Up",
            cost = "{3}{B}{B}",
            type = "Sorcery",
            text = """
                As an additional cost to cast this spell, you may collect evidence 6.
                Destroy all creatures. If evidence was collected, exile a card from an opponent's graveyard. Then search its owner's graveyard, hand, and library for any number of cards with that name and exile them. That player shuffles, then draws a card for each card exiled from their hand this way.
            """,
        ) parsesTo abilities(
            mayCollectEvidenceAsAdditionalCost(6),
            spell(
                destroy(Objects.Each(creature)),
                ifThen(evidenceCollected,
                    Step.Choose(Choice.Objects(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)), n(1), n(1)), bind = "chosen", chooser = Players.You),
                    move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
                    doing(Effect.Search(owner, ObjectFilter.SameNameAs(Objects.Bound("exiled")),
                        max = Value.Count(Objects.Each(and(ObjectFilter.OwnedBy(PlayerFilter.Is(owner)), or(*zones.map { inZone(it) }.toTypedArray())))),
                        zones = zones, bind = "found", searcher = Players.You)),
                    Step.Bind(Objects.Where(Objects.Bound("found"), inZone(Zone.Hand)), "fromHand"),
                    move(Objects.Bound("found"), Zone.Exile),
                    doing(Effect.Shuffle(owner)),
                    doing(Effect.Draw(owner, Value.Count(Objects.Bound("fromHand"))))),
            ),
        )
    }

    // CR 701.59a: collecting evidence as an effect; CR 701.59b; "whenever you collect evidence" (CR 603.2)
    @Test
    fun proofInspector() = card(
        name = "Proof Inspector",
        cost = "{G}{U}",
        type = "Creature — Merfolk Detective",
        pt = "2/2",
        text = """
            At the beginning of combat on your turn, you may collect evidence 4. (Exile cards with total mana value 4 or greater from your graveyard.)
            Whenever you collect evidence, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, Step.May(listOf(collectEvidenceStep(4)), Players.You)),
        triggered(youCollectEvidence, investigate()),
    )

    // CR 701.59c; CR 701.21a: each opponent sacrifices one with the greatest power among their creatures (CR 101.4: chosen in turn order)
    @Test
    fun wringAStatement() {
        fun opponentsSacrifice(kind: ObjectFilter): Step = Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent",
            steps = listOf(
                Step.Choose(Choice.Objects(kind, n(1), n(1)), bind = "sacrificed", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)
        val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))
        val greatest = Value.Of(NumericProperty.Power, Objects.Each(and(creature, theirs)), arcanum.api.Combine.Greatest)
        card(
            name = "Wring a Statement",
            cost = "{1}{B}",
            type = "Sorcery",
            text = """
                As an additional cost to cast this spell, you may collect evidence 6. (Exile cards with total mana value 6 or greater from your graveyard.)
                Each opponent sacrifices a creature of their choice. If evidence was collected, instead each opponent sacrifices a creature with the greatest power among creatures they control.
            """,
        ) parsesTo abilities(
            mayCollectEvidenceAsAdditionalCost(6),
            spell(
                ifThen(evidenceCollected,
                    opponentsSacrifice(and(creature, theirs, ObjectFilter.Power(arcanum.api.Comparison.Equal, greatest))),
                    otherwise = listOf(opponentsSacrifice(and(creature, theirs)))),
            ),
        )
    }

    // CR 502.3: the active player may keep it tapped; CR 611.2b: "for as long as this creature remains tapped"; CR 205.1b: still a land
    @Test
    fun thicketMurmurer() = card(
        name = "Thicket Murmurer",
        cost = "{G}",
        type = "Creature — Elf Druid Detective",
        pt = "0/3",
        text = """
            You may choose not to untap this creature during your untap step.
            {3}{G}, {T}, Collect evidence 4: Target land you control becomes a 5/5 green Plant Boar creature with haste for as long as this creature remains tapped. It's still a land. Activate only as a sorcery. (To collect evidence 4, exile cards with total mana value 4 or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        static(Mechanic.MayChooseNotToUntap),
        activated(cost(mana("{3}{G}"), tap, collectEvidence(4)),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype("Plant")), arcanum.api.SubtypeRef.Literal(Subtype("Boar"))), replaceSubtypes = true),
                Mechanic.SetColors(listOf(arcanum.api.ColorRef.Literal(Color.Green))),
                Mechanic.SetPowerToughness(n(5), n(5)),
                Mechanic.GainAbilities(listOf(Keyword.Haste))),
                Affected.Objects(target("land")), Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, tapped)))),
            targets = listOf(slot("land", and(land, you))), rules = asSorcery),
    )

    // CR 607.2a: "cards exiled with it" are those its collect evidence cost exiled; CR 608.2g: cast as the trigger resolves; CR 614.1a
    @Test
    fun sparkcoilStrider() = card(
        name = "Sparkcoil Strider",
        cost = "{1}{U}{R}",
        type = "Artifact — Vehicle",
        pt = "4/4",
        text = """
            Collect evidence 6: This Vehicle becomes an artifact creature until end of turn.
            Whenever this Vehicle attacks, you may cast an instant or sorcery spell from among cards exiled with it. If that spell would be put into a graveyard, put it on the bottom of its owner's library instead.
            Crew 2
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Action(KeywordAction.CollectEvidence(n(6)), remember = "exiled")),
            untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)))),
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(or(instant, sorcery), inZone(Zone.Exile), ObjectFilter.Is(Objects.Remembered("exiled"))), n(0), n(1)),
                bind = "toCast", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, bind = "cast", caster = Players.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Library, libraryPosition = -1))))), null, Duration.Indefinitely))),
        Keyword.Crew(2),
    )

    // CR 118.12: one optional cost of two parts; CR 400.7: "this card" is the card exiled by that cost
    @Test
    fun lanternwingPhoenix() = card(
        name = "Lanternwing Phoenix",
        cost = "{1}{R}{R}",
        type = "Creature — Phoenix",
        pt = "3/3",
        text = """
            Flying
            When this creature dies, you may exile it and collect evidence 4. If you do, return this card to the battlefield tapped. (To collect evidence 4, exile cards with total mana value 4 or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelf,
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.After)), from = Zone.Graveyard, postBind = "exiled"), collectEvidence(4))),
                bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), move(Objects.Bound("exiled"), Zone.Battlefield, tapped = true))),
    )

    // CR 701.20: look, two to the hand, the rest to the graveyard; CR 118.12: "unless they discard a card or sacrifice a creature"
    @Test
    fun truthseerSphere() = card(
        name = "Truthseer Sphere",
        cost = "{4}{B}",
        type = "Artifact",
        text = """
            When this artifact enters, look at the top four cards of your library. Put two of them into your hand and the rest into your graveyard. You lose 2 life.
            {2}, {T}, Collect evidence 3: Each opponent loses 3 life unless they discard a card or sacrifice a creature. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("looked")), bind = "picked", chooser = Players.You),
            move(Objects.Bound("picked"), Zone.Hand),
            move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), Zone.Graveyard),
            loseLife(2)),
        activated(cost(mana("{2}"), tap, collectEvidence(3)),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", steps = listOf(
                Step.Pay(Cost(listOf(CostPart.OneOf(listOf(Cost(listOf(discardACard)), Cost(listOf(sacrificeA(creature))))))), payer = Players.Bound("opponent"), bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")), loseLife(3, Players.Bound("opponent")))), together = true)),
    )

    // CR 701.59a, b: collecting evidence as an effect; CR 603.2
    @Test
    fun watchpostOverseer() = card(
        name = "Watchpost Overseer",
        cost = "{3}{U}",
        type = "Creature — Vedalken Detective",
        pt = "3/3",
        text = """
            When this creature enters, you may collect evidence 4. (Exile cards with total mana value 4 or greater from your graveyard.)
            Whenever you collect evidence, create a 1/1 colorless Thopter artifact creature token with flying.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(collectEvidenceStep(4)), Players.You)),
        triggered(youCollectEvidence, create(thopter)),
    )

    // CR 611.2a: for good; CR 205.1a: "becomes a Human Detective" sets its creature types, "becomes a legendary creature" its card type;
    // CR 612.8: the name (layer 3)
    @Test
    fun ninthWardChampion() = card(
        name = "Ninth Ward Champion",
        cost = "{1}{W}",
        type = "Creature — Human",
        pt = "2/3",
        text = """
            {1}{W}, Collect evidence 2: This creature becomes a Human Detective with base power and toughness 4/4 and gains vigilance.
            {2}{W}, Collect evidence 4: If this creature is a Detective, it becomes a legendary creature named Oreva, the Steadfast, it has base power and toughness 5/5, and it gains "Other creatures you control have indestructible."
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{W}"), collectEvidence(2)),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype("Human")), arcanum.api.SubtypeRef.Literal(Subtype("Detective"))), replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(4), n(4)),
                Mechanic.GainAbilities(listOf(Keyword.Vigilance))), Affected.Objects(Objects.Self), Duration.Indefinitely))),
        activated(cost(mana("{2}{W}"), collectEvidence(4)),
            ifThen(Condition.ObjectMatches(Objects.Self, sub("Detective")),
                doing(Effect.ApplyMechanics(listOf(
                    Mechanic.SetTypes(add = listOf(CardType.Creature), addSupertypes = listOf(Supertype.Legendary), replaceTypes = true),
                    Mechanic.SetName("Oreva, the Steadfast"),
                    Mechanic.SetPowerToughness(n(5), n(5)),
                    Mechanic.GainAbilities(listOf(static(has(Keyword.Indestructible), affects = Objects.Each(and(another, creature, you)))))),
                    Affected.Objects(Objects.Self), Duration.Indefinitely)))),
    )

    // CR 603.4: "if evidence was collected" is an intervening "if" (CR 701.59c)
    @Test
    fun grovekeepInspector() = card(
        name = "Grovekeep Inspector",
        cost = "{1}{G}",
        type = "Creature — Elf Detective",
        pt = "1/3",
        text = """
            As an additional cost to cast this spell, you may collect evidence 6. (Exile cards with total mana value 6 or greater from your graveyard.)
            Reach
            When this creature enters, if evidence was collected, put a +1/+1 counter on target creature and you gain 2 life.
        """,
    ) parsesTo abilities(
        mayCollectEvidenceAsAdditionalCost(6),
        Keyword.Reach,
        triggered(entersSelf, counters(target("creature")), gainLife(2), targets = listOf(slot("creature", creature)), interveningIf = evidenceCollected),
    )

    // CR 702.21a: ward with a cost that isn't mana
    @Test
    fun hatchetbackBrute() = card(
        name = "Hatchetback Brute",
        cost = "{2}{G}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            Deathtouch, haste
            Ward—Collect evidence 4. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player exiles cards with total mana value 4 or greater from their graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Haste,
        Keyword.Ward(Cost(listOf(collectEvidence(4)))),
    )

    // CR 605.1a: a mana ability with a cost that isn't only mana; CR 602.5b: activate only with five or more counters
    @Test
    fun lockboxCipher() = card(
        name = "Lockbox Cipher",
        cost = "{2}",
        type = "Artifact",
        text = """
            {T}, Collect evidence 3: Add one mana of any color. Put an unlock counter on this artifact. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
            Sacrifice this artifact: Surveil 3, then draw three cards. Activate only if this artifact has five or more unlock counters on it.
        """,
    ) parsesTo abilities(
        activated(cost(tap, collectEvidence(3)),
            doing(Effect.AddMana(arcanum.api.ManaProduction(anyOneColor(1)), Players.You)),
            counters(Objects.Self, 1, CounterKind.Named("unlock"))),
        activated(cost(sacrificeSelf), doing(Effect.Surveil(players = Players.You, count = n(3))), draw(3),
            rules = anyTime.copy(condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("unlock"), 5)))),
    )

    // CR 701.26b: untap another target permanent; CR 701.26a
    @Test
    fun clueglassAnalyst() = card(
        name = "Clueglass Analyst",
        cost = "{2}{U}",
        type = "Creature — Merfolk Detective",
        pt = "1/3",
        text = """
            {T}: Untap another target permanent you control.
            {T}, Collect evidence 3: Tap target creature you don't control. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("permanent")), targets = listOf(slot("permanent", and(another, permanentFilter, you)))),
        activated(cost(tap, collectEvidence(3)), tapIt(target("creature")), targets = listOf(slot("creature", and(creature, notYours)))),
    )

    // CR 118.12: "you may collect evidence 4. If you do"; CR 603.2: enters or attacks
    @Test
    fun vessaHeartOfTheSnare() = card(
        name = "Vessa, Heart of the Snare",
        cost = "{4}{B}{G}",
        type = "Legendary Creature — Elf Detective",
        pt = "5/4",
        text = """
            Menace
            Whenever Vessa enters or attacks, you may collect evidence 4. If you do, create two 2/1 black and green Spider creature tokens with reach and menace.
            Sacrifice four tokens: Surveil 2, then draw two cards. You gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersOrAttacksSelf, Step.Pay(Cost(listOf(collectEvidence(4))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), create(creatureToken("Spider", listOf(Color.Black, Color.Green), 2, 1, abilities = listOf(Keyword.Reach, Keyword.Menace)), n(2)))),
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(token, count = n(4)))), doing(Effect.Surveil(players = Players.You, count = n(2))), draw(2), gainLife(2)),
    )

    // CR 603.12: "When you do" is a reflexive trigger
    @Test
    fun specimenGatherer() = card(
        name = "Specimen Gatherer",
        cost = "{2}{G}",
        type = "Creature — Troll Detective",
        pt = "2/3",
        text = """
            Whenever this creature attacks, you may collect evidence 3. When you do, put a +1/+1 counter on target creature you control. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Pay(Cost(listOf(collectEvidence(3))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))))),
    )
}
