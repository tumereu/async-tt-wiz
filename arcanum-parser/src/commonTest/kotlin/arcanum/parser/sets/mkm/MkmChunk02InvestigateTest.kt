package arcanum.parser.sets.mkm

import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.abilities
import kotlin.test.Test

/** Murders at Karlov Manor chunk 02: investigate (CR 701.16a), part 1, and the Case's "To solve" (CR 719.3a). */
class MkmChunk02InvestigateTest {
    // CR 719.3a: "To solve" is an end-step trigger; CR 702.169d: a solved activated ability; CR 613.1d, f: it becomes a 0/0 creature
    // with flying for good
    @Test
    fun caseOfThePlunderedPerch() = card(
        name = "Case of the Plundered Perch",
        cost = "{U}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            To solve — You control three or more artifacts. (If unsolved, solve at the beginning of your end step.)
            Solved — {2}{U}, Sacrifice this Case: Put four +1/+1 counters on target noncreature artifact. It becomes a 0/0 Bird creature with flying in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, investigate()),
        toSolve(youControl(artifact, atLeast = 3)),
        activated(
            cost(mana("{2}{U}"), sacrificeSelf),
            counters(target("artifact"), 4),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bird")))),
                Mechanic.SetPowerToughness(n(0), n(0)),
                has(Keyword.Flying),
            ), Affected.Objects(target("artifact")), Duration.Indefinitely)),
            targets = listOf(slot("artifact", and(not(creature), artifact))),
        ).copy(label = AbilityLabel.Solved),
    )

    // CR 700.4: a creature died this turn; the three effects all depend on it
    @Test
    fun dredgeTheMillrace() = card(
        name = "Dredge the Millrace",
        cost = "{U}{B}",
        type = "Instant",
        text = """
            Create a 2/2 white and blue Detective creature token. If a creature died this turn, you gain 2 life, surveil 2, then investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            create(creatureToken("Detective", listOf(Color.White, Color.Blue), 2, 2)),
            ifThen(creatureDiedThisTurn, gainLife(2), doing(Effect.Surveil(Players.You, n(2))), investigate()),
        ),
    )

    // CR 701.60a: a creature stops being suspected when an effect says so; CR 611.2c: the creatures it affects are fixed as it resolves
    @Test
    fun ruleOutTheAbsurd() = card(
        name = "Rule Out the Absurd",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Investigate. Creatures your opponents control get -2/-0 until end of turn. If any of them are suspected, they're no longer suspected. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            investigate(),
            pump(Objects.Each(creatureOpponentControls), -2, 0),
            doing(Effect.Mark(Objects.Each(creatureOpponentControls), ObjectMark.Suspected, remove = true)),
        ),
    )

    // CR 709.3: a split card's halves; CR 608.2g, 601.2: cast while it resolves, at most one card from each opponent's graveyard; CR 614.1a
    @Test
    fun driftwoodWrackage() = cardFaces(
        Layout.Split,
        card(
            name = "Driftwood",
            cost = "{1}{G/U}",
            type = "Instant",
            text = """Mill three cards. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
        ) to abilities(
            spell(mill(3), investigate()),
        ),
        card(
            name = "Wrackage",
            cost = "{4}{U/B}{U/B}",
            type = "Sorcery",
            text = "Each opponent mills three cards, then you may cast a spell from each opponent's graveyard without paying its mana cost. If a spell cast this way would be put into a graveyard, exile it instead.",
        ) to abilities(
            spell(
                mill(3, players = Players.Each(PlayerFilter.Opponent)),
                Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                    Step.Choose(Choice.Objects(and(not(land), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent")))),
                        n(0), n(1)), bind = "toCast", chooser = Players.You),
                    doing(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, bind = "cast", caster = Players.You)),
                ), together = true),
                doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                    modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = Duration.Indefinitely)),
            ),
        ),
    )

    // CR 602.2b, 601.2f: activated abilities cost less; CR 118.7: not below one mana, every mana symbol counted
    @Test
    fun evidentiaryTinkerer() = card(
        name = "Evidentiary Tinkerer",
        cost = "{2}{U}",
        type = "Creature — Vedalken Artificer Detective",
        pt = "2/3",
        text = """
            Whenever you cast an artifact spell, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Activated abilities of artifacts you control cost {1} less to activate. This effect can't reduce the mana in that cost to less than one mana.
        """,
    ) parsesTo abilities(
        triggered(youCast(artifact), investigate()),
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated, source = and(artifact, you)),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)), minimum = 1)),
    )

    // CR 608.2h: "if you controlled it" is the creature as it last existed on the battlefield
    @Test
    fun brashInquirers() = card(
        name = "Brash Inquirers",
        cost = "{5}{U}",
        type = "Creature — Vedalken Detective",
        pt = "4/4",
        text = """
            When this creature enters, return up to one other target creature to its owner's hand. If you controlled it, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            move(target("creature"), Zone.Hand),
            ifThen(Condition.ObjectMatches(target("creature"), you), investigate()),
            targets = listOf(slot("creature", and(another, creature), min = 0)),
        ),
    )

    // CR 120.4, 603.2c: damage dealt to it at once is one event; 3 or more in all
    @Test
    fun unluckyOnlooker() = card(
        name = "Unlucky Onlooker",
        cost = "{1}{R}",
        type = "Creature — Goblin Citizen",
        pt = "2/1",
        text = """
            Whenever this creature is dealt 3 or more damage, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))), oneOrMore = true),
            investigate(), triggersOnlyIf = Condition.Compare(Value.EventAmount, Comparison.AtLeast, n(3))),
    )

    // CR 715.2: an Adventurer card; CR 608.2h: "if you controlled that permanent" as it last existed; CR 305.2: an additional land
    @Test
    fun tamsinCuriousProdigy() = cardFaces(
        Layout.Adventure,
        card(
            name = "Tamsin, Curious Prodigy",
            cost = "{2}{G}{U}",
            type = "Legendary Creature — Human Faerie Detective",
            pt = "3/4",
            text = """
                Flying, vigilance
                Whenever Tamsin attacks, destroy up to one target artifact. If you controlled that permanent, draw a card.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Vigilance,
            triggered(attacksSelf,
                destroy(target("artifact")),
                ifThen(Condition.ObjectMatches(target("artifact"), you), draw(1)),
                targets = listOf(slot("artifact", artifact, min = 0)),
            ),
        ),
        card(
            name = "Shadow the Lead",
            cost = "{G}{U}",
            type = "Sorcery — Adventure",
            text = "Investigate. You may play an additional land this turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(investigate(), doing(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn))),
        ),
    )

    // CR 607.2a: "exiled with it" are the cards its first ability exiled; CR 707.2: it copies the card's copiable values until end of turn
    @Test
    fun vessivWearerOfMasks() = card(
        name = "Vessiv, Wearer of Masks",
        cost = "{U}{B}",
        type = "Legendary Creature — Shapeshifter Detective",
        pt = "2/3",
        text = """
            Whenever Vessiv attacks, exile target card from a graveyard, then investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you sacrifice a Clue, you may have Vessiv become a copy of a creature card exiled with it until end of turn.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            investigate(),
            targets = listOf(slot("card", and(cardFilter, ObjectFilter.InZone(Zone.Graveyard)))),
        ),
        triggered(youSacrificeAClue,
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.InZone(Zone.Exile), ObjectFilter.Is(Objects.Remembered("exiled"))), n(0), n(1)),
                bind = "copied", chooser = Players.You),
            ifThen(Condition.NonEmpty("copied"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("copied"))), Affected.Objects(Objects.Self), Duration.EndOfTurn))),
        ),
    )

    // CR 101.4: the players who control the most creatures (all of them in a tie) investigate at once; then the creatures are destroyed
    @Test
    fun silenceTheWitnesses() = card(
        name = "Silence the Witnesses",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = """
            Each player who controls the most creatures investigates. Then destroy all creatures. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(mostCreatures)), "player", listOf(investigate()), together = true),
            destroy(Objects.Each(creature)),
        ),
    )

    private val testedCreatures = Value.Count(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested)))))
    private val mostCreatures = PlayerFilter.Where(Condition.Compare(testedCreatures, Comparison.AtLeast,
        Value.GreatestFor(Players.Each(PlayerFilter.Any), testedCreatures)))

    // CR 601.2f: the cost counts the targets chosen (CR 601.2c); CR 115.1: any number of target players
    @Test
    fun zealousQuestioning() = card(
        name = "Zealous Questioning",
        cost = "{W}{U}",
        type = "Instant",
        text = """
            This spell costs {W}{U} more to cast for each target beyond the first.
            Choose any number of target players. Investigate X times, where X is the total number of creatures those players control.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.More(ManaCost.parse("{W}{U}")!!,
            Value.Max(listOf(n(0), Value.Difference(Value.CountPlayers(Players.Target("players")), n(1))))))),
        spell(
            investigate(Value.Count(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("players"))))))),
            targets = listOf(TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(0), max = null)),
        ),
    )

    // CR 122.1d: a stun counter; the creatures are tapped and each gets one
    @Test
    fun knockedSenseless() = card(
        name = "Knocked Senseless",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell can't be countered. (This includes by the ward ability.)
            Tap up to two target creatures and put a stun counter on each of them. Investigate. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        spell(
            tapIt(target("creature")),
            counters(target("creature"), 1, CounterKind.Stun),
            investigate(),
            targets = listOf(slot("creature", creature, min = 0, max = 2)),
        ),
    )

    // CR 122.1f: poison counters; CR 104.3d: ten or more and the player loses
    @Test
    fun coerciveInquisitors() = card(
        name = "Coercive Inquisitors",
        cost = "{4}{B}{B}",
        type = "Creature — Gorgon Detective",
        pt = "5/6",
        text = """
            When this creature enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you sacrifice a Clue, target opponent gets two poison counters. (A player with ten or more poison counters loses the game.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, investigate()),
        triggered(youSacrificeAClue, doing(Effect.GivePlayerCounters(Players.Target("opponent"), CounterKind.Poison, n(2))), targets = listOf(targetOpponent)),
    )

    // CR 603.4: an intervening "if" about the creature that entered, checked as it triggers and as it resolves
    @Test
    fun keenEyedRecruit() = card(
        name = "Keen-Eyed Recruit",
        cost = "{1}{G}",
        type = "Creature — Human Detective",
        pt = "2/2",
        text = """
            Vigilance
            Whenever a creature you control enters, if its power is greater than this creature's power or its toughness is greater than this creature's toughness, put a +1/+1 counter on this creature and investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(enters(creatureYouControl), counters(Objects.Self), investigate(),
            interveningIf = Condition.Or(listOf(
                Condition.Compare(power(Objects.EventObject(EventRole.After)), Comparison.Greater, power(Objects.Self)),
                Condition.Compare(toughness(Objects.EventObject(EventRole.After)), Comparison.Greater, toughness(Objects.Self)),
            ))),
    )

    // CR 119.3: an opponent who lost life this turn; CR 603.4: "only once each turn"
    @Test
    fun ordessaGildedMagnate() = card(
        name = "Ordessa, Gilded Magnate",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/3",
        text = """
            Deathtouch
            At the beginning of your end step, investigate for each opponent who lost life this turn.
            Whenever a Clue you control is put into a graveyard from the battlefield, create a 1/1 white and black Spirit creature token with flying. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(yourEndStep, investigate(Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
            PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any)))))))),
        triggered(dies(and(sub("Clue"), you)), create(creatureToken("Spirit", listOf(Color.White, Color.Black), 1, 1, abilities = listOf(Keyword.Flying))),
            perTurn = 1),
    )

    // CR 107.3a: X announced as it's cast; CR 120.4a: excess damage is damage over lethal
    @Test
    fun scorchTheInformant() = card(
        name = "Scorch the Informant",
        cost = "{X}{R}",
        type = "Sorcery",
        text = """
            Scorch the Informant deals twice X damage to target creature. If excess damage was dealt to that creature this way, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toObjects(target("creature")), Value.Times(Value.Announced(name = "X"), 2), Objects.Self, bind = "damage")),
            ifThen(Condition.Compare(Value.Excess("damage"), Comparison.Greater, n(0)), investigate()),
            targets = listOf(slot("creature", creature)),
            announced = listOf("X"),
        ),
    )

    // CR 402.1: each opponent with more cards in hand than you is counted as the ability resolves
    @Test
    fun precinctGumshoe() = card(
        name = "Precinct Gumshoe",
        cost = "{2}{W}",
        type = "Creature — Angel Detective",
        pt = "2/4",
        text = """
            Flying, vigilance
            At the beginning of your upkeep, investigate once for each opponent who has more cards in hand than you. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), investigate(Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
            PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.Greater, Value.HandSize(Players.You))))))))),
    )
}
