package arcanum.parser.sets.tla

import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 09: Lesson instants and sorceries of red and green: damage counting Lessons with an exile-instead
 * replacement (CR 614.1a), a kicked damage increase (CR 702.33d), a pump for one player's creatures, a land search by the destroyed
 * permanent's controller, an "A or B" additional cost (CR 601.2b), changing a target (CR 115.7), casting the first nonland card exiled if its
 * mana value is low enough, sacrificing as an effect, an opponent choosing among searched cards, and looking at X cards.
 */
class TlaChunk09LessonsRedGreenTest {
    // CR 614.1a: an "exile instead" replacement for the rest of the turn
    @Test
    fun detonationTechnique() = card(
        name = "Detonation Technique",
        cost = "{1}{R}",
        type = "Instant — Lesson",
        text = "Detonation Technique deals damage equal to 2 plus the number of Lesson cards in your graveyard to target creature. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(n(2), lessonsInGraveyard())), toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(arcanum.api.Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 702.33d
    @Test
    fun flamecallingLesson() = card(
        name = "Flamecalling Lesson",
        cost = "{R}",
        type = "Instant — Lesson",
        text = """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Flamecalling Lesson deals 2 damage to target creature. If this spell was kicked, it deals 5 damage to that creature instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}"))),
        spell(Step.If(kicked, then = listOf(damage(5, toObjects(target("creature")))), otherwise = listOf(damage(2, toObjects(target("creature"))))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun howToSparkAnUprising() = card(
        name = "How to Spark an Uprising",
        cost = "{2}{R}",
        type = "Instant — Lesson",
        text = """
            Target creature gains menace until end of turn. (It can't be blocked except by two or more creatures.)
            Creatures target player controls get +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        spell(gains(target("creature"), Keyword.Menace),
            pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), 2, 0),
            targets = listOf(slot("creature", creature), targetPlayer)),
    )

    @Test
    fun theOldGeneralsDemonstration() = card(
        name = "The Old General's Demonstration",
        cost = "{1}{R}",
        type = "Sorcery — Lesson",
        text = """
            Choose one —
            • The Old General's Demonstration deals 1 damage to each creature your opponents control.
            • The Old General's Demonstration deals 4 damage to target creature.
        """,
    ) parsesTo abilities(
        modal(
            mode(damage(1, toObjects(Objects.Each(creatureOpponentControls)))),
            mode(damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 701.23: the destroyed permanent's controller may search (its controller as it last existed, CR 608.2h)
    @Test
    fun costOfLiberty() = card(
        name = "Cost of Liberty",
        cost = "{1}{R}",
        type = "Sorcery — Lesson",
        text = """
            Destroy target artifact or land an opponent controls. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(destroy(target("permanent")),
            Step.May(listOf(
                doing(Effect.Search(Players.ControllerOf(target("permanent")), basicLand, max = n(1), zones = listOf(Zone.Library), bind = "found",
                    searcher = Players.ControllerOf(target("permanent")))),
                doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true, controller = Players.ControllerOf(target("permanent"))))),
                doing(Effect.Shuffle(Players.ControllerOf(target("permanent"))))), player = Players.ControllerOf(target("permanent"))),
            draw(1),
            targets = listOf(slot("permanent", and(or(artifact, land), opponents)))),
    )

    // CR 601.2b ("pay 5 life or pay {2}"), 115.7
    @Test
    fun rerouteThunderbolt() = card(
        name = "Reroute Thunderbolt",
        cost = "{R}",
        type = "Instant — Lesson",
        text = """
            As an additional cost to cast this spell, pay 5 life or pay {2}.
            Change the target of target spell or ability with a single target.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(cost(CostPart.PayLife(n(5))), manaCost("{2}")))))),
        spell(doing(Effect.ChangeTargets(target("spell"), Players.You, optional = false)),
            targets = listOf(slot("spell", and(or(spellFilter, ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility)), ObjectFilter.SingleTarget())))),
    )

    // CR 608.2g: cast while resolving only if its mana value is low enough, else to hand
    @Test
    fun equinoxRevelations() = card(
        name = "Equinox Revelations",
        cost = "{2}{R}",
        type = "Instant — Lesson",
        text = """
            Exile cards from the top of your library until you exile a nonland card. You may cast that card without paying its mana cost if the spell's mana value is less than the number of Mountains you control. If you don't cast that card this way, put it into your hand.
            Flashback {6}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.RevealUntil(nonland, Players.You, n(1), reveal = true, into = Zone.Exile, foundBind = "found")),
            Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("found"), PlayKind.Cast, withoutPaying = true, caster = Players.You,
                resultingSpell = ObjectFilter.ManaValue(Comparison.Less, count(and(sub("Mountain"), you)))))), payer = Players.You, bind = "cast"),
            Step.If(Condition.Not(Condition.Did("cast")), then = listOf(move(Objects.Bound("found"), Zone.Hand)))),
        Keyword.Flashback(manaCost("{6}{R}")),
    )

    // CR 701.21a: sacrificing as an effect
    @Test
    fun roundOfRenewal() = card(
        name = "Round of Renewal",
        cost = "{2}{G}",
        type = "Instant — Lesson",
        text = "Sacrifice a land. Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(land, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            search(basicLand, max = n(2)),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle),
    )

    // CR 701.23 (different names), an opponent choosing among the found cards
    @Test
    fun primalTeachings() = card(
        name = "Primal Teachings",
        cost = "{4}{G}",
        type = "Instant — Lesson",
        text = "Search your library for up to four land cards with different names and reveal them. An opponent chooses two of those cards. Put the chosen cards into your graveyard and the rest onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Search(Players.You, land, max = n(4), reveal = true, zones = listOf(Zone.Library), differentNames = true, bind = "found", searcher = Players.You)),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("found")), bind = "chosen", chooser = Players.Each(PlayerFilter.Opponent)),
            move(Objects.Bound("chosen"), Zone.Graveyard),
            move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Zone.Battlefield, tapped = true),
            shuffle),
    )

    @Test
    fun dawnOfIronshaping() = card(
        name = "Dawn of Ironshaping",
        cost = "{1}{G}",
        type = "Instant — Lesson",
        text = """
            Choose one —
            • Destroy target artifact or enchantment.
            • Put a +1/+1 counter on target creature you control. It gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            mode(counters(target("creature")), gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creatureYouControl))),
        ),
    )

    // CR 107.3c: X counted as it resolves
    @Test
    fun tremorSight() = card(
        name = "Tremor Sight",
        cost = "{G}",
        type = "Sorcery — Lesson",
        text = "Look at the top X cards of your library, where X is the number of lands you control. You may reveal a creature or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, count(and(land, you)), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(creature, land), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun commonRoots() = card(
        name = "Common Roots",
        cost = "{1}{G}",
        type = "Sorcery — Lesson",
        text = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(*rampTapped.toTypedArray()),
    )

    @Test
    fun genuineLineage() = card(
        name = "Genuine Lineage",
        cost = "{1}{G}",
        type = "Sorcery — Lesson",
        text = """
            Return up to one target permanent card from your graveyard to your hand.
            Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), createClue, targets = listOf(slot("cards", inYourGraveyard(or(artifact, ObjectFilter.Type(arcanum.api.CardType.Battle),
            creature, enchantment, land, ObjectFilter.Type(arcanum.api.CardType.Planeswalker))), min = 0))),
    )

    // CR 118.12
    @Test
    fun releaseAttachments() = card(
        name = "Release Attachments",
        cost = "{1}{U/R}",
        type = "Instant — Lesson",
        text = "You may discard a card. If you do, draw two cards.",
    ) parsesTo abilities(
        spell(Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(2)))),
    )
}
