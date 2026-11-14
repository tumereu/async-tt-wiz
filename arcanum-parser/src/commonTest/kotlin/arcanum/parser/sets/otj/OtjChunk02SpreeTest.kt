package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 02: spree (CR 702.172): "+ [cost] — [effect]" modes, one or more chosen, each mode's cost an additional cost of the spell
 * (CR 601.2f), and the wordings of the set's spree cards.
 */
class OtjChunk02SpreeTest {
    private val creatureSlot = listOf(slot("creature", creature))
    private val yourCreature = and(creature, you)
    private fun graveyardCard(vararg filters: ObjectFilter) = inYourGraveyard(*filters)
    private val combatPhase = Condition.And(listOf(Condition.YourTurn, Condition.Or(listOf(TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers,
        TurnStep.DeclareBlockers, TurnStep.CombatDamage, TurnStep.EndOfCombat).map { Condition.StepIs(it) })))
    private fun exchange(noun: String, filter: ObjectFilter) = spreeMode(
        if (noun == "creature") "{2}" else "{1}",
        doing(Effect.ExchangeControl(target(noun), target("other $noun"))),
        targets = listOf(slot(noun, filter), slot("other $noun", filter).copy(distinctFrom = listOf(noun))),
    )

    // CR 700.12: outlaws are Assassins, Mercenaries, Pirates, Rogues and Warlocks; CR 702.172a
    @Test
    fun crossfireReckoning() = card(
        name = "Crossfire Reckoning",
        cost = "{R}{R}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Crossfire Reckoning deals 2 damage to each outlaw creature. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
            + {1} — Crossfire Reckoning deals 2 damage to each non-outlaw creature.
        """,
    ) parsesTo spree(
        spreeMode("{1}", damage(2, toObjects(Objects.Each(and(outlaw, creature))))),
        spreeMode("{1}", damage(2, toObjects(Objects.Each(and(not(outlaw), creature))))),
    )

    // CR 613.1f: all creatures lose all abilities; CR 702.12b: indestructible; the modes resolve in printed order (CR 700.2)
    @Test
    fun highNoonStandoff() = card(
        name = "High Noon Standoff",
        cost = "{W}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — All creatures lose all abilities until end of turn.
            + {1} — Choose a creature you control. It gains indestructible until end of turn.
            + {3}{W}{W} — Destroy all creatures.
        """,
    ) parsesTo spree(
        spreeMode("{1}", untilEndOfTurn(Objects.Each(creature), Mechanic.LoseAbilities())),
        spreeMode("{1}",
            Step.Choose(Choice.Objects(yourCreature, n(1), n(1)), bind = "chosen", chooser = Players.You),
            gains(Objects.Bound("chosen"), Keyword.Indestructible)),
        spreeMode("{3}{W}{W}", destroy(Objects.Each(creature))),
    )

    // CR 603.7: a delayed trigger returns the exiled card, a new object (CR 400.7); CR 608.2c: "if no other creature has greater power" is
    // checked as it resolves
    @Test
    fun vanishingVeilTrick() = card(
        name = "Vanishing Veil Trick",
        cost = "{W}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Exile target nontoken creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
            + {2} — Destroy target creature if no other creature has greater power.
        """,
    ) parsesTo spree(
        spreeMode("{1}",
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
            targets = listOf(slot("creature", and(not(token), creature)))),
        spreeMode("{2}",
            ifThen(Condition.Not(Condition.Exists(and(not(ObjectFilter.Is(target("creature"))), creature,
                ObjectFilter.Power(Comparison.Greater, power(target("creature")))), atLeast = 1)), destroy(target("creature"))),
            targets = creatureSlot),
    )

    // CR 506.1: "your combat phase"; CR 500.8: an additional combat phase; CR 603.7b, c: a delayed trigger for every time this turn, about the
    // chosen opponent
    @Test
    fun ironHorseRobbery() = card(
        name = "Iron Horse Robbery",
        cost = "{R}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {2}{R} — Untap all creatures you control. If it's your combat phase, there is an additional combat phase after this phase.
            + {2} — Creatures you control get +1/+0 and gain first strike until end of turn.
            + {R} — Choose target opponent. Whenever a creature you control deals combat damage to that player this turn, create a tapped Treasure token.
        """,
    ) parsesTo spree(
        spreeMode("{2}{R}", untapIt(Objects.Each(yourCreature)), ifThen(combatPhase, doing(Effect.ExtraCombat()))),
        spreeMode("{2}", pump(Objects.Each(yourCreature), 1, 0, has(Keyword.FirstStrike))),
        spreeMode("{R}",
            Step.Do(Effect.CreateDelayedTrigger(
                on(EventPattern.Damage(yourCreature, RecipientFilter(players = PlayerFilter.Is(Players.Target("opponent"))), combat = true)),
                Instructions(steps = listOf(createTreasure(tapped = true))), duration = Duration.EndOfTurn, repeats = true)),
            targets = listOf(targetOpponent)),
    )

    // CR 701.23a: search for any card; the creature cards are chosen as it resolves, not targeted, their total mana value at most 4
    @Test
    fun mournfulJig() = card(
        name = "Mournful Jig",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Search your library for a card, put it into your graveyard, then shuffle.
            + {2} — Return up to two creature cards with total mana value 4 or less from your graveyard to the battlefield.
        """,
    ) parsesTo spree(
        spreeMode("{1}", search(cardFilter), move(Objects.Bound("found"), Zone.Graveyard), shuffle),
        spreeMode("{2}",
            Step.Choose(Choice.Objects(graveyardCard(creature), n(0), n(2), totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(4))),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 303.4f, 301.5c: the creature it is attached to is chosen as it resolves; without one an Equipment enters unattached and an Aura stays
    @Test
    fun finalScoreCaper() = card(
        name = "Final Score Caper",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Spree (Choose one or more additional costs.)
            + {2} — Return target creature card from your graveyard to the battlefield.
            + {1} — Return target Mount or Vehicle card from your graveyard to the battlefield.
            + {1} — Return target Aura or Equipment card from your graveyard to the battlefield attached to a creature you control.
        """,
    ) parsesTo spree(
        spreeMode("{2}", move(target("card"), Zone.Battlefield), targets = listOf(slot("card", graveyardCard(creature)))),
        spreeMode("{1}", move(target("card"), Zone.Battlefield), targets = listOf(slot("card", graveyardCard(or(sub("Mount"), sub("Vehicle")))))),
        spreeMode("{1}",
            Step.Choose(Choice.Objects(yourCreature, n(1), n(1)), bind = "host", chooser = Players.You),
            ifThen(Condition.NonEmpty("host"),
                Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, attachedTo = Recipients(objects = Objects.Bound("host"))))),
                otherwise = listOf(move(Objects.Where(target("card"), not(sub("Aura"))), Zone.Battlefield))),
            targets = listOf(slot("card", graveyardCard(or(sub("Aura"), sub("Equipment")))))),
    )

    // CR 107.1a: half, rounded up; CR 701.21a: the opponent chooses what to sacrifice
    @Test
    fun waveOfTerrorTactics() {
        val opponent = Players.Target("opponent")
        val theirCreatures = and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(opponent)))
        val half = Value.Half(count(theirCreatures), roundUp = true)
        card(
            name = "Wave of Terror Tactics",
            cost = "{1}{B}{B}",
            type = "Sorcery",
            text = """
                Spree (Choose one or more additional costs.)
                + {1} — Target opponent sacrifices half the creatures they control of their choice, rounded up.
                + {2} — Target opponent discards half the cards in their hand, rounded up.
                + {2} — Target opponent loses half their life, rounded up.
            """,
        ) parsesTo spree(
            spreeMode("{1}",
                Step.Choose(Choice.Objects(theirCreatures, half, half), bind = "sacrificed", chooser = opponent),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                targets = listOf(targetOpponent)),
            spreeMode("{2}", doing(Effect.Discard(opponent, Value.Half(Value.HandSize(opponent), roundUp = true), ObjectFilter.Any)), targets = listOf(targetOpponent)),
            spreeMode("{2}", doing(Effect.LoseLife(opponent, Value.Half(Value.LifeOf(opponent), roundUp = true))), targets = listOf(targetOpponent)),
        )
    }

    // CR 701.26b: untap; CR 702.4b: double strike
    @Test
    fun cattleThiefStampede() = card(
        name = "Cattle Thief Stampede",
        cost = "{W}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Untap all creatures target player controls.
            + {1} — Target creature gains double strike until end of turn.
        """,
    ) parsesTo spree(
        spreeMode("{1}", untapIt(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))))), targets = listOf(targetPlayer)),
        spreeMode("{1}", gains(target("creature"), Keyword.DoubleStrike), targets = creatureSlot),
    )

    // CR 701.12a: exchange control of two target permanents (two different ones, CR 115.3)
    @Test
    fun swappingSwindle() = card(
        name = "Swapping Swindle",
        cost = "{U}{U}",
        type = "Sorcery",
        text = """
            Spree (Choose one or more additional costs.)
            + {2} — Exchange control of two target creatures.
            + {1} — Exchange control of two target artifacts.
            + {1} — Exchange control of two target enchantments.
        """,
    ) parsesTo spree(exchange("creature", creature), exchange("artifact", artifact), exchange("enchantment", enchantment))

    // CR 111.10: a token with an activated ability; CR 602.5d: "Activate only as a sorcery"
    @Test
    fun regrettableMishap() = card(
        name = "Regrettable Mishap",
        cost = "{B}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {2}{B} — Destroy target creature.
            + {1} — Create a 1/1 red Mercenary creature token with "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery."
        """,
    ) parsesTo spree(
        spreeMode("{2}{B}", destroy(target("creature")), targets = creatureSlot),
        spreeMode("{1}", create(creatureToken("Mercenary", listOf(Color.Red), 1, 1, abilities = listOf(
            activated(cost(tap), pump(target("creature"), 1, 0), targets = listOf(slot("creature", yourCreature)), rules = asSorcery))))),
    )

    // CR 701.23a; CR 111.4: X is counted as the token is created
    @Test
    fun tumbleweedWaltz() {
        val lands = count(and(land, you))
        card(
            name = "Tumbleweed Waltz",
            cost = "{1}{G}",
            type = "Sorcery",
            text = """
                Spree (Choose one or more additional costs.)
                + {1} — Search your library for a basic land card or a Desert card, put it onto the battlefield, then shuffle.
                + {3} — Create an X/X green Elemental creature token, where X is the number of lands you control.
            """,
        ) parsesTo spree(
            spreeMode("{1}", search(or(basicLand(), sub("Desert"))),
                move(Objects.Bound("found"), Zone.Battlefield), shuffle),
            spreeMode("{3}", doing(Effect.CreateToken(
                TokenSpec.Defined(FaceDefinition("Elemental Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Elemental")), power = PrintedNumber.X, toughness = PrintedNumber.X), power = lands, toughness = lands),
                n(1), Players.You))),
        )
    }

    // CR 120.3: damage to a creature; CR 701.8a: destroy
    @Test
    fun boilerBlastWreck() = card(
        name = "Boiler Blast Wreck",
        cost = "{R}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {2} — Boiler Blast Wreck deals 4 damage to target creature.
            + {2} — Destroy target artifact.
        """,
    ) parsesTo spree(
        spreeMode("{2}", damage(4, toObjects(target("creature"))), targets = creatureSlot),
        spreeMode("{2}", destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
    )

    // CR 701.23a, 701.24a: the found card goes on top after the shuffle
    @Test
    fun bottomlessGreed() = card(
        name = "Bottomless Greed",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Spree (Choose one or more additional costs.)
            + {2} — Search your library for a card, then shuffle and put that card on top.
            + {B}{B} — Target player draws three cards and loses 3 life.
        """,
    ) parsesTo spree(
        spreeMode("{2}", search(cardFilter), doing(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0)),
        spreeMode("{B}{B}", draw(3, Players.Target("player")), loseLife(3, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 122.1: a +1/+1 counter; CR 509.1b: can't be blocked; CR 401.4: the owner chooses top or bottom
    @Test
    fun cellblockGambit() = card(
        name = "Cellblock Gambit",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Spree (Choose one or more additional costs.)
            + {3} — Put a +1/+1 counter on target creature. It can't be blocked this turn.
            + {2} — Target artifact or creature's owner puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo spree(
        spreeMode("{3}", counters(target("creature")), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = creatureSlot),
        spreeMode("{2}",
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            ifThen(Condition.Chose("position", "top"), move(target("permanent"), Zone.Library, position = 0),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 205.1b, 105.3: the new colour and creature type replace the old; CR 613.4b: base power and toughness
    @Test
    fun shapeshiftBurst() = card(
        name = "Shapeshift Burst",
        cost = "{U}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Until end of turn, target creature becomes a white Rabbit with base power and toughness 0/1.
            + {3} — Target player draws two cards.
        """,
    ) parsesTo spree(
        spreeMode("{1}", untilEndOfTurn(target("creature"), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Rabbit"))), replaceSubtypes = true),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.White))),
            Mechanic.SetPowerToughness(n(0), n(1))), targets = creatureSlot),
        spreeMode("{3}", draw(2, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 111.1: a token; CR 701.6a, 118.12: counter unless its controller pays
    @Test
    fun spectralMeddling() = card(
        name = "Spectral Meddling",
        cost = "{U}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {3} — Create a 2/2 white Spirit creature token with flying.
            + {1} — Counter target spell unless its controller pays {2}.
        """,
    ) parsesTo spree(
        spreeMode("{3}", create(creatureToken("Spirit", listOf(Color.White), 2, 2, abilities = listOf(Keyword.Flying)))),
        spreeMode("{1}", Step.Pay(cost = manaCost("{2}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 701.8a; CR 122.1
    @Test
    fun confiscationSweep() = card(
        name = "Confiscation Sweep",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Destroy target artifact.
            + {1} — Destroy target enchantment.
            + {1} — Put a +1/+1 counter on each creature target player controls.
        """,
    ) parsesTo spree(
        spreeMode("{1}", destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        spreeMode("{1}", destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
        spreeMode("{1}", counters(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))))), targets = listOf(targetPlayer)),
    )

    // CR 707.10: copy a spell or ability (an instant spell or a sorcery spell or an ability on the stack); CR 115.7: change the target
    @Test
    fun repayTheKindness() = card(
        name = "Repay the Kindness",
        cost = "{R}{R}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1} — Copy target instant spell, sorcery spell, activated ability, or triggered ability. You may choose new targets for the copy.
            + {1} — Change the target of target spell or ability with a single target.
        """,
    ) parsesTo spree(
        spreeMode("{1}", doing(Effect.CopySpell(target("spell"), controller = Players.You, newTargets = true)),
            targets = listOf(slot("spell", or(and(instant, spellFilter), and(sorcery, spellFilter),
                and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
                and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered)))))),
        spreeMode("{1}", doing(Effect.ChangeTargets(target("spell"), chooser = Players.You, optional = false)),
            targets = listOf(slot("spell", and(or(spellFilter, ObjectFilter.Category(ObjectCategory.StackAbility)), ObjectFilter.SingleTarget())))),
    )

    // CR 701.6a; CR 707.2: a token copy; CR 701.9a
    @Test
    fun fourMovesEarly() = card(
        name = "Four Moves Early",
        cost = "{U}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1}{U} — Counter target spell.
            + {3} — Create a token that's a copy of target artifact or creature you control.
            + {2} — Draw two cards, then discard a card.
        """,
    ) parsesTo spree(
        spreeMode("{1}{U}", doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
        spreeMode("{3}", doing(Effect.CreateToken(TokenSpec.CopyOf(target("permanent")), count = n(1), controller = Players.You)),
            targets = listOf(slot("permanent", and(or(artifact, creature), you)))),
        spreeMode("{2}", draw(2), discard(1)),
    )

    // CR 702.19a: trample; CR 613.1f: a granted triggered ability until end of turn
    @Test
    fun wreckTheSaloon() = card(
        name = "Wreck the Saloon",
        cost = "{G}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {2} — Put two +1/+1 counters on target creature.
            + {1} — Target creature gains trample until end of turn.
            + {1} — Until end of turn, target creature gains "Whenever this creature deals combat damage to a player, draw two cards."
        """,
    ) parsesTo spree(
        spreeMode("{2}", counters(target("creature"), 2), targets = creatureSlot),
        spreeMode("{1}", gains(target("creature"), Keyword.Trample), targets = creatureSlot),
        spreeMode("{1}", gains(target("creature"), triggered(combatDamageToAPlayer, draw(2))), targets = creatureSlot),
    )
}
