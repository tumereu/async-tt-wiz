package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 06: Cases, "To solve" (CR 719.3a) and "Solved" (CR 702.169). */
class MkmChunk06SolvedTest {
    private fun solved(ability: Ability): Ability = when (ability) {
        is Ability.Activated -> ability.copy(label = AbilityLabel.Solved)
        is Ability.Triggered -> ability.copy(label = AbilityLabel.Solved)
        is Ability.Static -> ability.copy(label = AbilityLabel.Solved)
        else -> error("no label on $ability")
    }

    private val yourHand = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private fun yourLibraryTop(cards: ObjectFilter, how: PlayKind) = Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, how, topOnly = true,
        holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Each(and(cards, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))

    // CR 719.3a; CR 120.1, 609.7: "three or more sources" counts each source once, wherever it is now; CR 702.169d
    @Test
    fun caseOfTheSmolderingVeils() = card(
        name = "Case of the Smoldering Veils",
        cost = "{1}{R}{R}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, it deals 3 damage to target creature an opponent controls.
            To solve — Three or more sources you controlled dealt damage this turn. (If unsolved, solve at the beginning of your end step.)
            Solved — Sacrifice this Case: Exile the top three cards of your library. Choose one of them. You may play that card this turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", creatureOpponentControls))),
        toSolve(Condition.HappenedThisTurn(EventPattern.Damage(you, RecipientFilter()), atLeast = 3, measure = Measure.Sources)),
        solved(activated(
            cost(sacrificeSelf),
            move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("chosen")),
                Duration.EndOfTurn)),
        )),
    )

    // CR 719.3a: no cards in hand; CR 702.169c: the upkeep trigger only while solved
    @Test
    fun caseOfTheRacingHeartbeat() = card(
        name = "Case of the Racing Heartbeat",
        cost = "{2}{R}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, discard a card, then draw two cards.
            To solve — You have no cards in hand. (If unsolved, solve at the beginning of your end step.)
            Solved — At the beginning of your upkeep, discard your hand, then draw two cards.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, discard(1), draw(2)),
        toSolve(Condition.Not(Condition.Exists(yourHand, atLeast = 1))),
        solved(triggered(stepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You),
            doing(Effect.Discard(Players.You, n(1), filter = ObjectFilter.Any, objects = Objects.Each(yourHand))), draw(2))),
    )

    // CR 120.2: each creature you control is a source of 1 damage; CR 508.1: creatures that attacked (any player's); CR 613.4c
    @Test
    fun caseOfTheMidnightTram() = card(
        name = "Case of the Midnight Tram",
        cost = "{1}{W}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, choose target creature you don't control. Each creature you control deals 1 damage to that creature.
            To solve — Three or more creatures attacked this turn. (If unsolved, solve at the beginning of your end step.)
            Solved — Creatures you control get +1/+0.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.ForEach(Each.Objects(Objects.Each(creatureYouControl)), bind = "source", together = true, steps = listOf(
                damage(1, toObjects(target("creature")), source = Objects.Bound("source")))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
        toSolve(Condition.HappenedThisTurn(EventPattern.Attack(creature), atLeast = 3, measure = Measure.Objects)),
        solved(static(pt(1, 0), affects = Objects.Each(creatureYouControl))),
    )

    // CR 700.4 is not needed: "put into graveyards from anywhere" counts creature cards, not tokens; CR 613.1d, f: a 4/4 Gorgon creature while solved
    @Test
    fun caseOfTheBasiliskGlance() = card(
        name = "Case of the Basilisk Glance",
        cost = "{B}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, destroy up to one target creature that was dealt damage this turn.
            To solve — Three or more creature cards were put into graveyards from anywhere this turn. (If unsolved, solve at the beginning of your end step.)
            Solved — This Case is a 4/4 Gorgon creature with deathtouch and lifelink in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature",
            and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Object)), min = 0))),
        toSolve(Condition.HappenedThisTurn(EventPattern.ZoneChange(and(creature, cardFilter), to = Zone.Graveyard), atLeast = 3, measure = Measure.Objects)),
        solved(static(
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Gorgon")))),
            Mechanic.SetPowerToughness(n(4), n(4)),
            has(Keyword.Deathtouch, Keyword.Lifelink),
        )),
    )

    // CR 305.2: an additional land; CR 401.5, 601.3: lands, creature and enchantment spells from the top of the library while solved
    @Test
    fun caseOfTheSealedOrangery() = card(
        name = "Case of the Sealed Orangery",
        cost = "{3}{G}",
        type = "Enchantment — Case",
        text = """
            You may play an additional land on each of your turns.
            To solve — You control seven or more lands. (If unsolved, solve at the beginning of your end step.)
            Solved — You may look at the top card of your library any time, and you may play lands and cast creature and enchantment spells from the top of your library.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.ExtraLandPlays(1)),
        toSolve(youControl(land, atLeast = 7)),
        solved(Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))), affects = Affected.Players(Players.You))),
        solved(yourLibraryTop(land, PlayKind.Play)),
        solved(yourLibraryTop(or(creature, enchantment), PlayKind.Cast)),
    )

    // CR 702.168d: turning face up isn't entering; CR 614.1a: the Clue is added to the tokens while solved
    @Test
    fun caseOfThePurloinedLedger() = card(
        name = "Case of the Purloined Ledger",
        cost = "{1}{W}",
        type = "Enchantment — Case",
        text = """
            Whenever a Detective you control enters or is turned face up, put a +1/+1 counter on it.
            To solve — You control three or more Detectives. (If unsolved, solve at the beginning of your end step.)
            Solved — If one or more tokens would be created under your control, those tokens plus a Clue token are created instead. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(and(sub("Detective"), you), to = Zone.Battlefield),
            EventPattern.TurnedFaceUp(and(sub("Detective"), you)))), counters(Objects.EventObject(EventRole.After))),
        toSolve(youControl(sub("Detective"), atLeast = 3)),
        solved(static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.AlsoCreate(clue, n(1)))))),
    )

    // CR 601.2f: the reduction; CR 719.3a: instant and sorcery spells cast this turn
    @Test
    fun caseOfTheUpendedStudy() = card(
        name = "Case of the Upended Study",
        cost = "{2}{U}",
        type = "Enchantment — Case",
        text = """
            Instant and sorcery spells you cast cost {1} less to cast.
            To solve — You've cast four or more instant and sorcery spells this turn. (If unsolved, solve at the beginning of your end step.)
            Solved — Whenever you cast an instant or sorcery spell, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        toSolve(Condition.HappenedThisTurn(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You), atLeast = 4, measure = Measure.Events)),
        solved(triggered(youCast(or(instant, sorcery)), draw(1))),
    )

    // CR 105.2: five colors among permanents you control; CR 702.169c
    @Test
    fun caseOfTheBrokenAccord() = card(
        name = "Case of the Broken Accord",
        cost = "{2}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            To solve — There are five colors among permanents you control. (If unsolved, solve at the beginning of your end step.)
            Solved — At the beginning of combat on your turn, target creature you control gains flying, double strike, and vigilance until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        toSolve(Condition.Compare(Value.CountDistinct(Objects.Each(and(permanentFilter, you)), SharedQuality.Color), Comparison.AtLeast, n(5))),
        solved(triggered(beginningOfCombatOnYourTurn, gains(target("creature"), Keyword.Flying, Keyword.DoubleStrike, Keyword.Vigilance),
            targets = listOf(slot("creature", creatureYouControl)))),
    )

    // CR 701.60a: the token is suspected; CR 702.169d: activate only as a sorcery and only while solved
    @Test
    fun caseOfTheHiddenBones() = card(
        name = "Case of the Hidden Bones",
        cost = "{1}{B}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, create a 2/1 black Skeleton creature token and suspect it. (It has menace and can't block.)
            To solve — You control no suspected Skeletons. (If unsolved, solve at the beginning of your end step.)
            Solved — {1}{B}, Sacrifice this Case: Search your library for a card, put it into your hand, then shuffle. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Skeleton", listOf(Color.Black), 2, 1), bind = "token"), suspect(Objects.Bound("token"))),
        toSolve(Condition.Not(Condition.Exists(and(suspected, sub("Skeleton"), you), atLeast = 1))),
        solved(activated(cost(mana("{1}{B}"), sacrificeSelf), search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle, rules = asSorcery)),
    )

    // CR 601.2f is not involved: total power of creatures you control; CR 508.1m: "whenever you attack"
    @Test
    fun caseOfTheTroddenHedge() = card(
        name = "Case of the Trodden Hedge",
        cost = "{2}{G}",
        type = "Enchantment — Case",
        text = """
            When this Case enters, distribute two +1/+1 counters among one or two target creatures you control.
            To solve — Creatures you control have total power 8 or greater. (If unsolved, solve at the beginning of your end step.)
            Solved — Whenever you attack, put a +1/+1 counter on target attacking creature. It gains trample until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creatures"), Value.DividedShare),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creatureYouControl), min = n(1), max = n(2), divided = n(2)))),
        toSolve(Condition.Compare(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Total), Comparison.AtLeast, n(8))),
        solved(triggered(youAttack, counters(target("creature")), gains(target("creature"), Keyword.Trample),
            targets = listOf(slot("creature", and(attacking, creature))))),
    )

    // CR 119.10: life gained this turn; CR 611.2c: only the creature cards in the graveyard as it resolves gain the ability; CR 113.6f
    @Test
    fun caseOfTheUntouchedSupper() = card(
        name = "Case of the Untouched Supper",
        cost = "{W}",
        type = "Enchantment — Case",
        text = """
            Whenever a creature you control enters, you gain 1 life.
            To solve — You've gained 5 or more life this turn. (If unsolved, solve at the beginning of your end step.)
            Solved — Sacrifice this Case: Creature cards in your graveyard gain "You may cast this card from your graveyard" until end of turn.
        """,
    ) parsesTo abilities(
        triggered(enters(creatureYouControl), gainLife(1)),
        toSolve(Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 5, measure = Measure.Amount)),
        solved(activated(cost(sacrificeSelf), doing(Effect.ApplyMechanics(
            listOf(Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Self))))),
            Affected.Objects(Objects.Each(inYourGraveyard(creature))), Duration.EndOfTurn)))),
    )
}
