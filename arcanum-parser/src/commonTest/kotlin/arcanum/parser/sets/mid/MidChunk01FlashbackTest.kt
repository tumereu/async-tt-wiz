package arcanum.parser.sets.mid

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
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
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MID chunk 01: Flashback, part 1 (CR 702.34a: cast from the graveyard for the flashback cost and any additional costs, then exiled).
 * Test cards are invented: new names, the real cards' exact oracle text with the new name.
 */
class MidChunk01FlashbackTest {
    private val flashbackLine = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private fun flashback(cost: String) = Keyword.Flashback(manaCost(cost))
    private val sacrificed = Objects.Bound("sacrificed")

    // CR 601.2b (any number of creatures sacrificed as an additional cost, zero included), 608.2h (their total power as they last existed),
    // 111.4 (an X/X token), 702.34a (flashback with the additional cost)
    @Test
    fun cadaverPatchwork() = card(
        name = "Cadaver Patchwork",
        cost = "{U}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice any number of creatures.
            Create an X/X blue and black Zombie creature token with menace, where X is the total power of the sacrificed creatures.
            Flashback {3}{U}{B} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.Sacrifice(CostObjects.Choose(creature, count = null), preBind = "sacrificed")))),
        spell(doing(Effect.CreateToken(
            TokenSpec.Defined(FaceDefinition("Zombie Token", colorIndicator = listOf(Color.Blue, Color.Black), types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Zombie")), power = PrintedNumber.X, toughness = PrintedNumber.X, abilities = listOf(Keyword.Menace)),
                power = Value.Of(NumericProperty.Power, sacrificed, Combine.Total), toughness = Value.Of(NumericProperty.Power, sacrificed, Combine.Total)),
            n(1), Players.You))),
        flashback("{3}{U}{B}"),
    )

    // CR 701.8a (destroy), 608.2h (whether a land was destroyed, and its controller, as it last existed), 608.2c ("Otherwise"),
    // 701.23a (search), 701.24a (shuffle)
    @Test
    fun feralGroveUpheaval() {
        val controller = Players.ControllerOf(target("permanent"))
        fun searchFor(max: Int) = Step.May(listOf(
            doing(Effect.Search(controller, basicLand(), max = n(max), zones = listOf(Zone.Library), bind = "found", searcher = controller)),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true, controller = controller))),
            doing(Effect.Shuffle(controller))), player = controller)
        card(
            name = "Feral-Grove Upheaval",
            cost = "{1}{R}{G}",
            type = "Sorcery",
            text = """
                Destroy target artifact, enchantment, or land. If a land was destroyed this way, its controller may search their library for up to two basic land cards, put them onto the battlefield tapped, then shuffle. Otherwise, its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
                Flashback {3}{R}{G}
            """,
        ) parsesTo abilities(
            spell(
                doing(Effect.Destroy(target("permanent"), preBind = "destroyed")),
                ifThen(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("destroyed"), land)), Comparison.AtLeast, n(1)), searchFor(2),
                    otherwise = listOf(searchFor(1))),
                targets = listOf(slot("permanent", or(artifact, enchantment, land))),
            ),
            flashback("{3}{R}{G}"),
        )
    }

    // CR 400.7 (it returns as a new object), 110.2 (under its owner's control), 608.2c ("If it entered under your control … Otherwise")
    @Test
    fun sanctifiedInterlude() {
        val it = Objects.Bound("exiled")
        card(
            name = "Sanctified Interlude",
            cost = "{W}{U}",
            type = "Sorcery",
            text = """
                Exile target nonlegendary creature, then return it to the battlefield under its owner's control. If it entered under your control, put a +1/+1 counter on it. Otherwise, tap it.
                Flashback {1}{W}{U} $flashbackLine
            """,
        ) parsesTo abilities(
            spell(
                move(target("creature"), Zone.Exile, postBind = "exiled"),
                doing(Effect.Move(it, Destination(Zone.Battlefield, controller = Players.OwnerOf(it)))),
                ifThen(Condition.ObjectMatches(it, you), counters(it), otherwise = listOf(tapIt(it))),
                targets = listOf(slot("creature", and(not(legendary), creature))),
            ),
            flashback("{1}{W}{U}"),
        )
    }

    // CR 107.3a (X), 608.2b (the target's kind checked as it resolves; a player is neither), 702.34a (a flashback cost with a non-mana part:
    // X loyalty counters removed from among planeswalkers, CR 118.3), 107.3 ("X can't be 0" only when cast this way)
    @Test
    fun kindleTheDusk() {
        val x = Value.Announced("X")
        card(
            name = "Kindle the Dusk",
            cost = "{X}{R}",
            type = "Sorcery",
            text = """
                Kindle the Dusk deals X damage to any target. It deals X plus 1 damage instead if that target is a creature or planeswalker.
                Flashback—{3}{R}, Remove X loyalty counters from among planeswalkers you control. If you cast this spell this way, X can't be 0. $flashbackLine
            """,
        ) parsesTo abilities(
            spell(
                ifThen(Condition.ObjectMatches(Objects.Target("any"), or(creature, planeswalker)), damage(Value.Sum(listOf(x, n(1))), toAny),
                    otherwise = listOf(damage(x, toAny))),
                targets = listOf(anyTarget),
                announced = listOf("X"),
            ),
            Keyword.Flashback(cost(mana("{3}{R}"), CostPart.RemoveCounters(CounterKind.Loyalty, Value.CastX(of = Objects.Self),
                CostObjects.Choose(and(planeswalker, you), count = null), distributed = true))),
            static(Mechanic.XAtLeast(1, castWith = Keyword.Flashback.Kind)),
        )
    }

    // CR 406.3 (exiled face down; its exiler may look at it), 601.2a / 609.4b (may play it, mana spent as though of any color, not colorless),
    // 400.7 (the permission ends when it leaves exile)
    @Test
    fun pilferedForesight() {
        val exiled = Objects.Bound("exiled")
        card(
            name = "Pilfered Foresight",
            cost = "{U}{B}",
            type = "Instant",
            text = """
                Look at the top two cards of target opponent's library. Exile one of them face down and put the other on the bottom of that library. You may play the exiled card for as long as it remains exiled, and you may spend mana as though it were mana of any color to cast that spell.
                Flashback {1}{U}{B}
            """,
        ) parsesTo abilities(
            spell(
                doing(Effect.LookAt(Objects.LibraryCards(Players.Target("opponent"), n(2), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(exiled)), Affected.Players(Players.You), Duration.Indefinitely)),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, manaAsAnyColorOnly = true)),
                    Affected.Objects(exiled), Duration.Indefinitely)),
                targets = listOf(targetOpponent),
            ),
            flashback("{1}{U}{B}"),
        )
    }

    // CR 110.4b (permanent cards), 202.3 (mana value), 608.2d ("you may put up to two": zero to two), 401.4 (the rest on the bottom, random order)
    @Test
    fun rushTheHarvestFair() = card(
        name = "Rush the Harvest Fair",
        cost = "{3}{G}{G}{G}",
        type = "Sorcery",
        text = """
            Look at the top five cards of your library. You may put up to two permanent cards with mana value 5 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
            Flashback {7}{G}{G}{G} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(and(permanentCard, manaValue(Comparison.AtMost, 5)), n(0), n(2), from = Objects.Bound("looked")), bind = "picked",
                chooser = Players.You),
            move(Objects.Bound("picked"), Zone.Battlefield),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
        flashback("{7}{G}{G}{G}"),
    )

    // CR 115.1 (up to three targets in any graveyards), 400.3 (each into its owner's library), 701.24a (each owner shuffles), 119.3 (gain life)
    @Test
    fun churnTheLoam() = card(
        name = "Churn the Loam",
        cost = "{G}",
        type = "Instant",
        text = """
            Choose up to three target cards in graveyards. The owners of those cards shuffle them into their libraries. You gain 2 life.
            Flashback {1}{G} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(
            move(target("cards"), Zone.Library),
            doing(Effect.Shuffle(Players.OwnerOf(target("cards")))),
            gainLife(2),
            targets = listOf(slot("cards", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 3)),
        ),
        flashback("{1}{G}"),
    )

    // CR 608.2d (an opponent chooses; with one target chosen, that one), 400.7 (the returned card is a new object: "it"), 702.10 (haste),
    // 603.7 (a delayed trigger at the next end step)
    @Test
    fun riseForTheButcher() {
        val returned = Objects.Bound("returned")
        card(
            name = "Rise for the Butcher",
            cost = "{3}{B}{R}",
            type = "Sorcery",
            text = """
                Choose up to two target creature cards in your graveyard. An opponent chooses one of them. Return that card to your hand. Return the other to the battlefield under your control. It gains haste. Exile it at the beginning of the next end step.
                Flashback {4}{B}{R} $flashbackLine
            """,
        ) parsesTo abilities(
            spell(
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = target("cards")), bind = "chosen", chooser = Players.Each(PlayerFilter.Opponent)),
                move(Objects.Bound("chosen"), Zone.Hand),
                doing(Effect.Move(Objects.Except(target("cards"), Objects.Bound("chosen")), Destination(Zone.Battlefield, controller = Players.You), postBind = "returned")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(returned), Duration.Indefinitely)),
                doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                    arcanum.api.Instructions(steps = listOf(move(returned, Zone.Exile, postBind = "exiled"))))),
                targets = listOf(slot("cards", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0, max = 2)),
            ),
            flashback("{4}{B}{R}"),
        )
    }

    // CR 122.1 (+1/+1 counter), 115.1 ("up to one": zero allowed), 701.26a (tap), 502.3 (doesn't untap during its controller's next untap step)
    @Test
    fun frostbriarBenediction() {
        val other = target("second creature")
        card(
            name = "Frostbriar Benediction",
            cost = "{G}{U}",
            type = "Sorcery",
            text = """
                Put a +1/+1 counter on up to one target creature you control. Tap up to one target creature you don't control, and that creature doesn't untap during its controller's next untap step.
                Flashback {1}{G}{U} $flashbackLine
            """,
        ) parsesTo abilities(
            spell(
                counters(target("creature")),
                tapIt(other),
                doing(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(other),
                    Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(other)))),
                targets = listOf(slot("creature", creatureYouControl, min = 0),
                    slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0)),
            ),
            flashback("{1}{G}{U}"),
        )
    }

    // CR 509.1a (can't block), 611.2a (this turn), 115.1 (up to two)
    @Test
    fun desertTheWatch() = card(
        name = "Desert the Watch",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Up to two target creatures can't block this turn.
            Flashback {3}{R} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creatures"), Mechanic.CantBlock(ObjectFilter.Any)), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        flashback("{3}{R}"),
    )

    // CR 122.1, 613.1f (abilities until end of turn), 702.34a
    @Test
    fun seraphblazeKindling() = card(
        name = "Seraphblaze Kindling",
        cost = "{1}{R}{W}",
        type = "Sorcery",
        text = """
            Put two +1/+1 counters on target creature. It gains vigilance, trample, lifelink, indestructible, and haste until end of turn.
            Flashback {2}{R}{W} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(counters(target("creature"), 2),
            gains(target("creature"), Keyword.Vigilance, Keyword.Trample, Keyword.Lifelink, Keyword.Indestructible, Keyword.Haste),
            targets = listOf(slot("creature", creature))),
        flashback("{2}{R}{W}"),
    )

    // CR 701.20a (reveal), 608.2d (may: zero or one), 401.4 (random order on the bottom)
    @Test
    fun arcaneDistillation() = card(
        name = "Arcane Distillation",
        cost = "{U}{R}",
        type = "Instant",
        text = """
            Look at the top four cards of your library. You may reveal an instant or sorcery card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Flashback {3}{U}{R} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(instant, sorcery), min = n(0), max = n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
        flashback("{3}{U}{R}"),
    )

    // CR 400.7 (the returned permanent), 614.1a / 614.6 (it would die: exiled instead), 611.2a (gains it indefinitely)
    @Test
    fun refuseToRest() = card(
        name = "Refuse to Rest",
        cost = "{W}{B}",
        type = "Sorcery",
        text = """
            Return target creature card with mana value 3 or less from your graveyard to the battlefield. It gains "If this creature would die, exile it instead."
            Flashback {3}{W}{B} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield, postBind = "returned"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))))),
                Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)))),
        ),
        flashback("{3}{W}{B}"),
    )

    // CR 115.1 ("up to one target Zombie"), 122.1, 702.34a
    @Test
    fun clawFromTheCrypt() = card(
        name = "Claw from the Crypt",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Return target creature card from your graveyard to your hand. Put a +1/+1 counter on up to one target Zombie you control.
            Flashback {3}{B} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand), counters(target("Zombie")),
            targets = listOf(slot("card", inYourGraveyard(creature)), slot("Zombie", and(sub("Zombie"), you), min = 0))),
        flashback("{3}{B}"),
    )

    // CR 707.9b (copy exceptions: base 1/1, green, a Frog replacing its creature types, CR 205.1b), 707.2 (the copiable values), 111.1
    @Test
    fun ribbitingDouble() = card(
        name = "Ribbiting Double",
        cost = "{1}{G}{U}",
        type = "Sorcery",
        text = """
            Create a token that's a copy of target non-Frog creature, except it's a 1/1 green Frog.
            Flashback {3}{G}{U} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(
            create(TokenSpec.CopyOf(target("creature"), listOf(CopyException.PowerToughness(1, 1), CopyException.Colors(listOf(arcanum.api.ColorRef.Literal(Color.Green))),
                CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Frog"))), replaceSubtypes = true)))),
            targets = listOf(slot("creature", and(not(sub("Frog")), creature))),
        ),
        flashback("{3}{G}{U}"),
    )

    // CR 601.2f (cost reduction counted as the cost is determined), 700.4 (died: put into a graveyard from the battlefield; each creature counts
    // once), 702.34a
    @Test
    fun gravemoorRenewal() = card(
        name = "Gravemoor Renewal",
        cost = "{3}{B}{G}",
        type = "Sorcery",
        text = """
            This spell costs {1} less to cast for each creature that died this turn.
            Return target creature card from your graveyard to the battlefield.
            Flashback {5}{B}{G} $flashbackLine
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.EventsThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), Measure.Objects)))),
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
        flashback("{5}{B}{G}"),
    )

    // CR 400.7 / 702.34a (a card in the graveyard; this card itself can't be its own target as it is cast from there, CR 601.2c)
    @Test
    fun sylvanRecall() = card(
        name = "Sylvan Recall",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Return target card from your graveyard to your hand.
            Flashback {4}{G} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(cardFilter)))),
        flashback("{4}{G}"),
    )

    // CR 601.2b / 601.2f (an additional discard cost, paid with flashback too, CR 702.34a), 121.1
    @Test
    fun voltaicEpiphany() = card(
        name = "Voltaic Epiphany",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards.
            Flashback {3}{R} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2)),
        flashback("{3}{R}"),
    )

    // CR 119.3, 121.1, 701.9a (gain, draw, then discard, in order, CR 608.2c)
    @Test
    fun devoutRestoration() = card(
        name = "Devout Restoration",
        cost = "{W}{U}",
        type = "Instant",
        text = """
            You gain 2 life, draw two cards, then discard two cards.
            Flashback {1}{W}{U} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(gainLife(2), draw(2), discard(2)),
        flashback("{1}{W}{U}"),
    )
}
