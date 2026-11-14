package arcanum.parser.sets.mkm

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 09: collect evidence X (CR 701.59a), "counter up to one target spell unless …" (CR 118.12a), damage instead
 * (CR 614.1a), colour pairs (CR 105.2), exiling and playing cards (CR 406.3), copying cards and permanents (CR 707), all colours and every basic
 * land type (CR 105.2c, 305.7), casting from the top of the library once each turn and "can't be turned face up" (CR 708.8). */
class MkmChunk09EffectsTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val x = Value.Bound("X")
    private val announcedX = Value.Announced("X")
    private val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))

    // CR 701.59a: X chosen as evidence is collected; CR 603.12: "When you do" is a reflexive trigger
    @Test
    fun cinderjudgeWyrm() = card(
        name = "Cinderjudge Wyrm",
        cost = "{4}{R}{R}",
        type = "Creature — Dragon",
        pt = "6/6",
        text = """
            Flying, trample
            Whenever this creature deals combat damage to a player, you may collect evidence X. When you do, this creature deals X damage to each creature and planeswalker that player controls. (To collect evidence X, exile cards with total mana value X or greater from your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        triggered(combatDamageToAPlayer,
            Step.Choose(Choice.Number(n(0), Value.Of(NumericProperty.ManaValue, Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                Combine.Total)), bind = "X", chooser = Players.You),
            Step.Pay(Cost(listOf(CostPart.Action(KeywordAction.CollectEvidence(x)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(damage(x, toObjects(Objects.Each(and(or(creature, planeswalker), theirs))))))),
    )

    // CR 115.1, 601.2c: "up to one target spell"; CR 118.12a: generic mana equal to a number
    @Test
    fun grotesqueGraft() = card(
        name = "Grotesque Graft",
        cost = "{X}{G}{U}",
        type = "Instant",
        text = "Put X +1/+1 counters on target creature you control. Then counter up to one target spell unless its controller pays mana equal to the greatest power among creatures you control.",
    ) parsesTo abilities(
        spell(
            counters(target("creature"), announcedX),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
                payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
            targets = listOf(slot("creature", creatureYouControl), slot("spell", spellFilter, min = 0)),
            announced = listOf("X"),
        ),
    )

    // CR 614.1a: "deals 5 damage … instead"; CR 121.2: each card drawn is one draw
    @Test
    fun staticSurge() = card(
        name = "Static Surge",
        cost = "{1}{R}",
        type = "Instant",
        text = "Static Surge deals 3 damage to target creature. If you've drawn two or more cards this turn, Static Surge deals 5 damage to that creature instead.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events),
                damage(5, toObjects(target("creature"))), otherwise = listOf(damage(3, toObjects(target("creature"))))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 105.2: colour pairs of permanents that are exactly two colours; CR 702.11d: hexproof from
    @Test
    fun zarvexConcordOfPairs() = card(
        name = "Zarvex, Concord of Pairs",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — Dragon Avatar",
        pt = "6/6",
        text = """
            Flying, hexproof from multicolored
            Whenever Zarvex deals combat damage to a player, it deals X damage to any target, target player draws X cards, and you gain X life, where X is the number of different color pairs among permanents you control that are exactly two colors.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.HexproofFrom(ObjectFilter.Multicolored),
        Value.CountDistinct(Objects.Each(and(permanentFilter, you)), SharedQuality.ColorPair).let { pairs ->
            triggered(combatDamageToAPlayer,
                damage(pairs, toAny),
                doing(Effect.Draw(Players.Target("player"), pairs)),
                doing(Effect.GainLife(Players.You, pairs)),
                targets = listOf(anyTarget, targetPlayer))
        },
    )

    // CR 608.2c: X counted after the destruction; CR 611.2a: haste for good; CR 603.7: the delayed return
    @Test
    fun quakehammerTantrum() = card(
        name = "Quakehammer Tantrum",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = "Destroy all artifacts you don't control, then exile the top X cards of your library, where X is the number of artifacts that were put into graveyards from the battlefield this turn. You may put a creature card exiled this way onto the battlefield. It gains haste. Return it to your hand at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            destroy(Objects.Each(and(artifact, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
            doing(Effect.Move(Objects.LibraryCards(Players.You, Value.EventsThisTurn(EventPattern.ZoneChange(artifact, from = Zone.Battlefield, to = Zone.Graveyard),
                Measure.Objects), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("put")), Duration.Indefinitely)),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Bound("put"), Zone.Hand)),
        ),
    )

    // CR 120.3, 406.3: as many cards as damage dealt; "their next turn" of that creature's controller (CR 611.2c)
    @Test
    fun hastenedBequest() = card(
        name = "Hastened Bequest",
        cost = "{R}{R}",
        type = "Enchantment",
        text = "Whenever a creature is dealt damage, its controller may exile that many cards from the top of their library. They may play those cards until the end of their next turn.",
    ) parsesTo Players.ControllerOf(Objects.Bound("creature")).let { controller ->
        abilities(
            triggered(on(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(objects = creature), per = arcanum.api.DamageOccurrence.Recipient)),
                Step.Bind(Objects.EventObject(EventRole.Object), "creature"),
                Step.May(listOf(
                    doing(Effect.Move(Objects.LibraryCards(controller, Value.EventAmount, fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                    doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.Is(controller))),
                        Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfNextTurnOf(controller)))), controller)),
        )
    }

    // CR 406.3: face-down exiled cards you may look at; CR 609.4b: mana as though it were mana of any type
    @Test
    fun brazenPilfering() = card(
        name = "Brazen Pilfering",
        cost = "{X}{B}{B}",
        type = "Instant",
        text = "Target opponent exiles the top X cards of their library face down. You may look at and play those cards for as long as they remain exiled. If you cast a spell this way, you may spend mana as though it were mana of any type to cast it.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(Objects.LibraryCards(Players.Target("opponent"), announcedX, fromTop = true), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound("exiled"))), Affected.Players(Players.You), Duration.Indefinitely)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, manaAsAnyColor = true)),
                Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)),
            targets = listOf(targetOpponent),
            announced = listOf("X"),
        ),
    )

    // CR 707.12: a copy of a card cast without paying its mana cost; "put there from anywhere this turn" (any graveyard)
    @Test
    fun restageTheDeed() = card(
        name = "Restage the Deed",
        cost = "{1}{U}{U}{U}",
        type = "Instant",
        text = "Exile target nonland card in a graveyard that was put there from anywhere this turn. Copy it. You may cast the copy without paying its mana cost.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CopyCard(Objects.Bound("exiled"), owner = Players.You, bind = "copy")),
            Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You),
            targets = listOf(slot("card", and(nonland, inZone(Zone.Graveyard),
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Graveyard), role = EventRole.After)))),
        ),
    )

    // CR 400.7: the returned permanent is a new object; CR 205.1b: a subtype in addition to its other types; CR 122.1b: a flying counter
    @Test
    fun summonTheSpectralWitness() = card(
        name = "Summon the Spectral Witness",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Return target creature card with mana value 3 or less from your graveyard to the battlefield. Put a flying counter on it. It's a Spirit in addition to its other types.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield, postBind = "returned"),
            counters(Objects.Bound("returned"), kind = arcanum.api.CounterKind.Keyword(Keyword.Flying)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit"))))), Affected.Objects(Objects.Bound("returned")),
                Duration.Indefinitely)),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(arcanum.api.Comparison.AtMost, 3)))),
        ),
    )

    // CR 613.1f: loses defender and gains vigilance (layer 6); CR 603.2: the second card drawn each turn
    @Test
    fun wearyExaminer() = card(
        name = "Weary Examiner",
        cost = "{1}{U}",
        type = "Creature — Human Detective",
        pt = "3/2",
        text = """
            Defender
            Whenever you draw your second card each turn, this creature loses defender and gains vigilance until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(drawSecondCard, untilEndOfTurn(Objects.Self, Mechanic.LoseAbilities(Keyword.Defender.kind), Mechanic.GainAbilities(listOf(Keyword.Vigilance)))),
    )

    // CR 604.3: a characteristic-defining ability; CR 601.2f: the first instant or sorcery spell each turn costs less
    @Test
    fun torvinRemadeScholar() = card(
        name = "Torvin, Remade Scholar",
        cost = "{3}{U}{R}",
        type = "Legendary Creature — Weird Detective",
        pt = "*/*",
        text = """
            Torvin's power and toughness are each equal to twice the number of instant and sorcery cards in your graveyard.
            The first instant or sorcery spell you cast each turn costs {3} less to cast.
        """,
    ) parsesTo abilities(
        Value.Times(count(inYourGraveyard(or(instant, sorcery))), 2).let { pt ->
            static(Mechanic.SetPowerToughness(pt, pt), characteristicDefining = true)
        },
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(or(instant, sorcery), PlayerFilter.You), atLeast = 1, measure = Measure.Events))),
    )

    // CR 103.6a: begins the game on the battlefield; CR 105.2c: all colours (layer 5); CR 305.7: every basic land type in addition (layer 4)
    @Test
    fun leylineOfTheConcord() = card(
        name = "Leyline of the Concord",
        cost = "{G/W}{G/U}{B/G}{R/G}",
        type = "Enchantment",
        text = """
            If this card is in your opening hand, you may begin the game with it on the battlefield.
            Each nonland permanent you control is all colors.
            Lands you control are every basic land type in addition to their other types.
        """,
    ) parsesTo abilities(
        arcanum.api.Ability.Static(listOf(Mechanic.MayBeginOnBattlefield), affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
        static(Mechanic.SetColors(Color.all.map { ColorRef.Literal(it) }), affects = Objects.Each(and(nonland, permanentFilter, you))),
        static(Mechanic.SetTypes(addSubtypes = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map { SubtypeRef.Literal(Subtype(it)) }),
            affects = Objects.Each(and(land, you))),
    )

    // CR 401.5: looking at the top card any time; CR 601.3: casting from the top of the library once each turn, any turn
    @Test
    fun gatherTheTroupe() = card(
        name = "Gather the Troupe",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            You may look at the top card of your library any time.
            Once each turn, you may cast a creature spell with power 2 or less from the top of your library.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, perTurn = 1, holder = PlayerFilter.You),
            affects = Objects.Each(and(creature, power(arcanum.api.Comparison.AtMost, 2), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 608.2e: you choose for each player, then the lands are destroyed at once; CR 608.2h: "its controller" as the land last existed
    @Test
    fun grumbitsLandgrinder() = card(
        name = "Grumbit's Landgrinder",
        cost = "{2}{R}{R}",
        type = "Artifact Creature — Insect Thopter",
        pt = "4/4",
        text = """
            Flying, trample
            When this creature enters, for each player, destroy up to one nonbasic land that player controls. For each land destroyed this way, its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo Players.ControllerOf(Objects.Bound("land")).let { controller ->
        abilities(
            Keyword.Flying,
            Keyword.Trample,
            triggered(entersSelf,
                Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                    Step.Choose(Choice.Objects(and(not(ObjectFilter.Super(Supertype.Basic)), land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))),
                        n(0), n(1)), bind = "chosen", chooser = Players.You),
                    doing(Effect.Destroy(Objects.Bound("chosen"), preBind = "destroyed")))),
                Step.ForEach(Each.Objects(Objects.Bound("destroyed")), bind = "land", steps = listOf(Step.May(listOf(
                    doing(Effect.Search(controller, basicLand(), max = n(1), reveal = false, zones = listOf(Zone.Library), bind = "found", searcher = controller)),
                    move(Objects.Bound("found"), Zone.Battlefield, tapped = true, controller = controller),
                    doing(Effect.Shuffle(controller))), controller)))),
        )
    }

    // CR 601.2c: exactly X targets; CR 707.2: X token copies of each
    @Test
    fun mirrorfoldMultitude() = card(
        name = "Mirrorfold Multitude",
        cost = "{X}{X}{X}{G}{U}",
        type = "Sorcery",
        text = "For each of X target permanents, create X tokens that are copies of that permanent.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Objects(target("permanents")), bind = "permanent", steps = listOf(
                doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("permanent")), announcedX, Players.You)))),
            targets = listOf(TargetSlot("permanents", TargetKind.Objects(permanentFilter), min = announcedX, max = announcedX)),
            announced = listOf("X"),
        ),
    )

    // CR 708.8, 702.168d: permanents that can't be turned face up during your turn; CR 508.1: attacking with three or more creatures
    @Test
    fun manorHoundSentry() = card(
        name = "Manor Hound Sentry",
        cost = "{3}{W}",
        type = "Creature — Dog",
        pt = "3/2",
        text = """
            Vigilance
            Permanents your opponents control can't be turned face up during your turn.
            Whenever you attack with three or more creatures, creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.CantBeTurnedFaceUp, affects = Objects.Each(and(permanentFilter, opponents)), condition = Condition.YourTurn),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.Attack(creatureYouControl)), oneOrMore = true, atLeast = 3), pump(Objects.Each(creatureYouControl), 1, 1)),
    )
}
