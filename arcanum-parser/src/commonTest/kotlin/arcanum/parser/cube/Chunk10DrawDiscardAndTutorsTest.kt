package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 10: card draw and tutors. Drawing (you, target player, each player), then discarding (at random, unless you
 * attacked) or losing life; wheels that replace every hand; additional and alternative costs that discard, sacrifice or
 * return permanents (CR 601.2f, 118.9); searching the library for a card and putting it into the hand, onto the
 * battlefield or on top (where the found card is left out of the shuffle, CR 701.24b).
 */
class Chunk10DrawDiscardAndTutorsTest {
    // ---- Shared shapes

    private val targetPlayer = TargetSlot(name = "player", what = TargetKind.Players(PlayerFilter.Any))
    private val eachPlayer = Players.Each(PlayerFilter.Any)
    private val anyCard = ObjectFilter.Category(ObjectCategory.Card)

    private fun draw(players: Players, n: Int) = Step.Do(Effect.Draw(players, count = Value.Fixed(n)))
    private fun discard(players: Players, n: Int, random: Boolean = false) =
        Step.Do(Effect.Discard(players, count = Value.Fixed(n), random = random))

    private fun spell(vararg steps: Step) = spell(steps.toList())
    private fun spell(steps: List<Step>) = Ability.Spell(Instructions(steps = steps))

    /** "As an additional cost to cast this spell, [part]." (CR 601.2f) */
    private fun additionalCost(part: CostPart) = Ability.Static(listOf(Mechanic.AdditionalCost(cost = Cost(listOf(part)))))

    /** "Search your library for [filter], put that card [to], then shuffle." */
    private fun tutor(filter: ObjectFilter, to: Destination) = listOf(
        Step.Do(Effect.Search(library = Players.You, filter = filter, bind = "found")),
        Step.Do(Effect.Move(Objects.Bound("found"), to = to)),
        Step.Do(Effect.Shuffle(Players.You)),
    )

    /** "Search your library for [filter], [reveal it, ]then shuffle and put that card on top." The found card stays out of
     * the shuffle (CR 701.24b). */
    private fun tutorToTop(filter: ObjectFilter, reveal: Boolean) = listOf(
        Step.Do(Effect.Search(library = Players.You, filter = filter, reveal = reveal, bind = "found")),
        Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
        Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Library, libraryPosition = 0))),
    )

    // ---- Drawing

    @Test
    fun forebearsInsight() = card(
        name = "Forebear's Insight",
        cost = "{U}",
        type = "Instant",
        text = "Target player draws three cards.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(draw(Players.Target("player"), 3)),
        )),
    )

    @Test
    fun midnightMurmur() = card(
        name = "Midnight Murmur",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "You draw two cards and lose 2 life.",
    ) parsesTo abilities(
        spell(
            draw(Players.You, 2),
            Step.Do(Effect.LoseLife(Players.You, amount = Value.Fixed(2))),
        ),
    )

    @Test
    fun hastyRummage() = card(
        name = "Hasty Rummage",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw two cards, then discard two cards. Untap up to three lands.",
    ) parsesTo abilities(
        spell(
            draw(Players.You, 2),
            discard(Players.You, 2),
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.Type(CardType.Land), min = Value.Fixed(0), max = Value.Fixed(3)),
                bind = "untapped",
            ),
            Step.Do(Effect.Untap(Objects.Bound("untapped"))),
        ),
    )

    /** "You attacked this turn": you declared an attacking creature this turn. */
    @Test
    fun plotTheVoyage() = card(
        name = "Plot the Voyage",
        cost = "{1}{U}",
        type = "Sorcery",
        text = "Draw two cards. Then discard a card unless you attacked this turn.",
    ) parsesTo abilities(
        spell(
            draw(Players.You, 2),
            Step.If(
                condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Attack(attacker = ObjectFilter.ControlledBy(PlayerFilter.You)))),
                then = listOf(discard(Players.You, 1)),
            ),
        ),
    )

    // ---- Each player: one event for all of them (CR 101.4)

    @Test
    fun searingQuestion() = card(
        name = "Searing Question",
        cost = "{R}",
        type = "Sorcery",
        text = "Each player draws three cards, then discards three cards at random.",
    ) parsesTo abilities(
        spell(
            draw(eachPlayer, 3),
            discard(eachPlayer, 3, random = true),
        ),
    )

    @Test
    fun spinningFate() = card(
        name = "Spinning Fate",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Each player discards their hand, then draws seven cards.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Discard(eachPlayer, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)))),
            draw(eachPlayer, 7),
        ),
    )

    @Test
    fun hourspinner() = card(
        name = "Hourspinner",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Each player shuffles their hand and graveyard into their library, then draws seven cards. (Then put Hourspinner into its owner's graveyard.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(
                Objects.Each(ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Graveyard)))),
                to = Destination(Zone.Library),
            )),
            Step.Do(Effect.Shuffle(eachPlayer)),
            draw(eachPlayer, 7),
        ),
    )

    // ---- Costs that discard, sacrifice or return (CR 601.2f, 118.9)

    @Test
    fun joyfulRelease() = card(
        name = "Joyful Release",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard two cards.
            Draw three cards.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.Discard(CostObjects.Choose(anyCard, count = Value.Fixed(2)))),
        spell(draw(Players.You, 3)),
    )

    @Test
    fun moltenEpiphany() = card(
        name = "Molten Epiphany",
        cost = "{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard a land card.
            Draw two cards.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.Discard(CostObjects.Choose(ObjectFilter.Type(CardType.Land)))),
        spell(draw(Players.You, 2)),
    )

    @Test
    fun insistOnTruth() = card(
        name = "Insist on Truth",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or discard a card.
            Draw two cards.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.OneOf(listOf(
            Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Artifact))))),
            Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard)))),
        ))),
        spell(draw(Players.You, 2)),
    )

    @Test
    fun tidalRush() = card(
        name = "Tidal Rush",
        cost = "{4}{U}",
        type = "Instant",
        text = """
            You may return two Islands you control to their owner's hand rather than pay this spell's mana cost.
            Draw two cards.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AlternativeCost(cost = Cost(listOf(
                CostPart.ReturnToHand(CostObjects.Choose(
                    filter = ObjectFilter.And(listOf(
                        ObjectFilter.Sub(Subtype("Island")),
                        ObjectFilter.ControlledBy(PlayerFilter.You),
                    )),
                    count = Value.Fixed(2),
                )),
            ))),
        )),
        spell(draw(Players.You, 2)),
    )

    // ---- Tutors

    @Test
    fun fiendishMentor() = card(
        name = "Fiendish Mentor",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Search your library for a card, put that card into your hand, then shuffle.",
    ) parsesTo abilities(
        spell(tutor(anyCard, to = Destination(Zone.Hand))),
    )

    @Test
    fun sinisterPurpose() = card(
        name = "Sinister Purpose",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Search your library for a card, put that card into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Creature)))),
        spell(tutor(anyCard, to = Destination(Zone.Hand))),
    )

    @Test
    fun recklessWager() = card(
        name = "Reckless Wager",
        cost = "{R}",
        type = "Sorcery",
        text = "Search your library for a card, put that card into your hand, discard a card at random, then shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Search(library = Players.You, filter = anyCard, bind = "found")),
            Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Hand))),
            discard(Players.You, 1, random = true),
            Step.Do(Effect.Shuffle(Players.You)),
        ),
    )

    @Test
    fun bloodsuckersLesson() = card(
        name = "Bloodsucker's Lesson",
        cost = "{B}",
        type = "Instant",
        text = "Search your library for a card, then shuffle and put that card on top. You lose 2 life.",
    ) parsesTo abilities(
        spell(
            tutorToTop(anyCard, reveal = false) +
                Step.Do(Effect.LoseLife(Players.You, amount = Value.Fixed(2))),
        ),
    )

    @Test
    fun illuminedMentor() = card(
        name = "Illumined Mentor",
        cost = "{W}",
        type = "Instant",
        text = "Search your library for an artifact or enchantment card, reveal it, then shuffle and put that card on top.",
    ) parsesTo abilities(
        spell(tutorToTop(
            ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment))),
            reveal = true,
        )),
    )

    @Test
    fun arcaneMentor() = card(
        name = "Arcane Mentor",
        cost = "{U}",
        type = "Instant",
        text = "Search your library for an instant or sorcery card, reveal it, then shuffle and put that card on top.",
    ) parsesTo abilities(
        spell(tutorToTop(
            ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
            reveal = true,
        )),
    )

    @Test
    fun fieldExchange() = card(
        name = "Field Exchange",
        cost = "{G}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a land.
            Search your library for a land card, put that card onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Land)))),
        spell(tutor(ObjectFilter.Type(CardType.Land), to = Destination(Zone.Battlefield))),
    )

    @Test
    fun tinkerersSwap() = card(
        name = "Tinkerer's Swap",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact.
            Search your library for an artifact card, put that card onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Artifact)))),
        spell(tutor(ObjectFilter.Type(CardType.Artifact), to = Destination(Zone.Battlefield))),
    )
}
