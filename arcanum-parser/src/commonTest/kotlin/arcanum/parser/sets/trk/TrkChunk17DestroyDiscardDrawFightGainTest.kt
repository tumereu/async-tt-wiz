package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 17: destroy, discard, draw, fight and gained abilities (Borg artifact creatures, Spacecraft, mana creatures). */
class TrkChunk17DestroyDiscardDrawFightGainTest {
    private val isSelf: ObjectFilter = ObjectFilter.Is(Objects.Self)
    private val spacecraft = sub("Spacecraft")
    private val artifactCreature = and(artifact, creature)
    private val borgToken = creatureToken("Borg", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
    private val tapForTwoGreen = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{G}")!!)), Players.You)))

    // CR 700.2: modes of a spell
    @Test
    fun commandDecision() = card(
        name = "Order Of Command",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Destroy target creature with power 4 or greater.
            • Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, power(Comparison.AtLeast, 4))))),
            mode(draw(1)),
        ))),
    )

    // CR 701.8 (destroy), 603.4 (intervening "if you cast it"), 110.4
    @Test
    fun crystallineEntity() = card(
        name = "Crystal Colossus Of Glass",
        cost = "{8}",
        type = "Artifact Creature — Elemental",
        pt = "7/7",
        text = """
            Flying
            When this creature enters, if you cast it, destroy all nonartifact creatures.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, destroy(Objects.Each(and(not(artifact), creature))), interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    // CR 702.44 (sunburst), 701.8, 122.1: counts the charge counters on this artifact
    @Test
    fun engineeredExplosives() = card(
        name = "Tuned Detonation Rig",
        cost = "{X}",
        type = "Artifact",
        text = """
            Sunburst (This artifact enters with a charge counter on it for each color of mana spent to cast it.)
            {2}, Sacrifice this artifact: Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact.
        """,
    ) parsesTo abilities(
        Keyword.Sunburst,
        activated(cost(mana("{2}"), sacrificeSelf), destroy(Objects.Each(and(nonland, permanentFilter, ObjectFilter.ManaValue(Comparison.Equal,
            Value.Of(NumericProperty.Counters(charge), Objects.Self, Combine.Single)))))),
    )

    // CR 700.2, 701.8, 110.4a, 400.7 (return a card from the graveyard)
    @Test
    fun resistanceIsFutile() = card(
        name = "Resistance Is Vain",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Destroy target nonartifact creature.
            • Return target artifact creature card with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(not(artifact), creature)))),
            mode(move(target("card"), Zone.Battlefield),
                targets = listOf(slot("card", inYourGraveyard(artifact, creature, manaValue(Comparison.AtMost, 3))))),
        ))),
    )

    // CR 701.9 (discard), 603.2, 602.2 (discarding itself as a cost: it works from the hand)
    @Test
    fun armusSkinOfEvil() = card(
        name = "Armus Hide Of Ruin",
        cost = "{5}{B}{B}",
        type = "Legendary Creature — Ooze Horror",
        pt = "6/6",
        text = """
            Menace
            At the beginning of your end step, target opponent discards two cards.
            {2}{B}, Discard this card: Target creature gets -4/-4 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), discard(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
        activated(cost(mana("{2}{B}"), CostPart.Discard(CostObjects.Self)), pump(target("creature"), -4, -4),
            targets = listOf(slot("creature", creature))),
    )

    // CR 701.21 (sacrifice), 603.2: "another creature or artifact"
    @Test
    fun automatedWarfareSystem() = card(
        name = "Automated Siege Engine",
        cost = "{2}{B}",
        type = "Artifact Creature — Construct",
        pt = "2/2",
        text = """
            Flying
            Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, Step.Pay(cost(sacrificeA(and(another, or(creature, artifact)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1))),
    )

    // CR 701.26: tapping three untapped artifact creatures you control as a cost (control and untapped are implicit, CR 602.2)
    @Test
    fun collationTrimatrix() = card(
        name = "Collation Tri-Array",
        cost = "{B}",
        type = "Artifact Creature — Borg",
        pt = "1/1",
        text = "Tap three untapped artifact creatures you control: You draw a card and lose 1 life.",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(artifactCreature, count = n(3)))), draw(1), loseLife(1)),
    )

    // CR 510.2, 603.2: a creature you control with any counter
    @Test
    fun phloxUnorthodoxDoctor() = card(
        name = "Phlox Odd Healer",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Denobulan Doctor",
        pt = "3/4",
        text = """
            Trample
            Whenever a creature you control with a counter on it deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.Damage(and(creature, you, ObjectFilter.HasCounter(null, atLeast = 1)), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            draw(1)),
    )

    // CR 702.. n/a: an ability that exiles this card from the graveyard (CR 113.6m)
    @Test
    fun relentlessDrednok() = card(
        name = "Unyielding Drone Husk",
        cost = "{B}",
        type = "Artifact Creature — Robot",
        pt = "1/1",
        text = """
            Lifelink
            {2}{B}, Exile this card from your graveyard: You draw a card and lose 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(cost(mana("{2}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), draw(1), loseLife(1)),
    )

    // CR 701.26 (becomes tapped), 603.4 (intervening if), 400.7 (it entered under your control)
    @Test
    fun suluSpaceSwashbuckler() = card(
        name = "Sulu Skyward Duelist",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Pilot",
        pt = "2/2",
        text = """
            First strike
            Whenever Sulu Skyward Duelist becomes tapped, if an Equipment or Spacecraft entered the battlefield under your control this turn, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(on(EventPattern.Tap(objects = isSelf)), draw(1),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(or(equipment, spacecraft), you), to = Zone.Battlefield),
                atLeast = 1, measure = Measure.Events)),
    )

    // CR 700.2, 602.2 (sacrifice as a cost), 701.21, 119.3
    @Test
    fun teaEarlGreyHot() = card(
        name = "Tea Dark Roast Warm",
        cost = "{2}",
        type = "Artifact — Food",
        text = """
            When this artifact enters, draw a card.
            {2}, {T}, Sacrifice this artifact: Choose one —
            • This artifact deals 2 damage to target opponent.
            • You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        Ability.Activated(cost(mana("{2}"), tap, sacrificeSelf), Instructions(modes = modes(
            mode(damage(2, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
            mode(gainLife(3)),
        )), anyTime),
    )

    // CR 714.2 (chapters), 120.3, 121.1; the chapters share one ability
    @Test
    fun yearOfHell() = card(
        name = "Year Of Embers",
        cost = "{1}{U}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II, III — Each player draws a card. This Saga deals 2 damage to each player.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3),
            Step.Do(Effect.Draw(Players.Each(PlayerFilter.Any), n(1))),
            damage(2, Recipients(players = Players.Each(PlayerFilter.Any)))),
    )

    // CR 714.2, 701.14 (fight), 115.6 ("up to one"), 119.3, 400.7
    @Test
    fun amokTime() = card(
        name = "Blood Fever Hour",
        cost = "{2}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Target creature you control fights up to one target creature an opponent controls.
            II — You gain 3 life.
            III — Return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        chapter(1, Step.Do(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls, min = 0))),
        chapter(2, gainLife(3)),
        chapter(3, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 613.1f (granted abilities), 603.2, 120.3 (combat damage to a player), 118.12 (an optional payment)
    @Test
    fun antagonistIncorporator() = card(
        name = "Antagonist Merger",
        cost = "{1}{W}",
        type = "Artifact Creature — Borg",
        pt = "2/3",
        text = "Artifact creatures you control have \"Whenever this creature deals combat damage to a player, you may pay {1}. If you do, create a 1/1 colorless Borg artifact creature token.\"",
    ) parsesTo abilities(
        static(has(triggered(combatDamageToAPlayer, Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), create(borgToken)))), affects = Objects.Each(and(artifactCreature, you))),
    )

    // CR 613.1f, 702.20 (vigilance)
    @Test
    fun commandSubadjunct() = card(
        name = "Command Underling",
        cost = "{W}",
        type = "Artifact Creature — Borg",
        pt = "2/1",
        text = "Artifact creatures you control have vigilance.",
    ) parsesTo abilities(
        static(has(Keyword.Vigilance), affects = Objects.Each(and(artifactCreature, you))),
    )

    // CR 611.3 (static during your turn), 702.11 (hexproof), 701.26
    @Test
    fun hemmerFixingWhatIsBroken() = card(
        name = "Hemmer Mends The Hull",
        cost = "{W}{U}",
        type = "Legendary Creature — Aenar Artificer",
        pt = "2/3",
        text = """
            During your turn, Spacecraft you control have hexproof. (They can't be the targets of spells or abilities your opponents control.)
            Whenever Hemmer Mends The Hull becomes tapped, draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), affects = Objects.Each(and(spacecraft, you)), condition = Condition.YourTurn),
        triggered(on(EventPattern.Tap(objects = isSelf)), draw(1), discard(1)),
    )

    // CR 613.1f, 603.2, 118.3 (paying life)
    @Test
    fun hiveMindCoprocessor() = card(
        name = "Hive Mind Subprocessor",
        cost = "{4}{B}",
        type = "Artifact Creature — Borg",
        pt = "4/4",
        text = "Artifact creatures you control have \"Whenever this creature deals combat damage to a player, you may pay 1 life. If you do, draw a card.\"",
    ) parsesTo abilities(
        static(has(triggered(combatDamageToAPlayer, Step.Pay(cost(CostPart.PayLife(n(1))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)))), affects = Objects.Each(and(artifactCreature, you))),
    )

    // CR 613.1f, 702.4 (double strike)
    @Test
    fun primaryUnimatrixAdjunct() = card(
        name = "Primary Hexmatrix Adjunct",
        cost = "{3}{W}",
        type = "Artifact Creature — Borg",
        pt = "2/3",
        text = "Artifact creatures you control have double strike.",
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike), affects = Objects.Each(and(artifactCreature, you))),
    )

    // CR 613.1f (a granted mana ability, 605.1a), 111.4, 205.4 (legendary token), 702.12 (indestructible)
    @Test
    fun rokTahkBuddingScientist() = card(
        name = "Rok-Zhal Budding Tinkerer",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Brikar Scientist",
        pt = "5/5",
        text = """
            Creatures you control have "{T}: Add {G}{G}."
            When Rok-Zhal Budding Tinkerer enters, create Murf, a legendary 0/1 green Ooze Slug creature token with indestructible.
        """,
    ) parsesTo abilities(
        static(has(tapForTwoGreen), affects = Objects.Each(creatureYouControl)),
        triggered(entersSelf, create(TokenSpec.Defined(FaceDefinition("Murf", colorIndicator = listOf(Color.Green), supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Ooze"), Subtype("Slug")), power = PrintedNumber.Fixed(0),
            toughness = PrintedNumber.Fixed(1), abilities = listOf(Keyword.Indestructible))))),
    )

    // CR 611.3, 702.9 (flying), 701.26 (becomes tapped), 119.1
    @Test
    fun shuttleAce() = card(
        name = "Shuttle Wing Captain",
        cost = "{4}{W}",
        type = "Creature — Kelpien Pilot",
        pt = "4/4",
        text = """
            During your turn, this creature has flying.
            Whenever this creature becomes tapped, you gain 2 life.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.YourTurn),
        triggered(on(EventPattern.Tap(objects = isSelf)), gainLife(2)),
    )
}
