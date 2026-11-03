package arcanum.parser.sets.sos

import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Total
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 03: paradigm (CR 702.192: the Lessons that, after their first resolution, may be cast again each first main
 * phase), spells that exile themselves as they resolve (CR 608.2n), flashback (CR 702.34) and a spell giving it, "if this spell was cast
 * from anywhere other than your hand", an additional cost of X life (X announced as the spell is cast, CR 601.2b), and "choose up to four;
 * you may choose the same mode more than once" (CR 700.2d).
 */
class SosChunk03ParadigmAndFlashbackTest {
    private val nonlandPermanentCardInYourGraveyard = inYourGraveyard(nonland, permanentCard)

    // CR 702.192a: paradigm's two spell abilities come from the keyword
    @Test
    fun renewalColloquium() = card(
        name = "Renewal Colloquium",
        cost = "{5}{W}{W}",
        type = "Sorcery — Lesson",
        text = """
            Return target nonland permanent card from your graveyard to the battlefield.
            Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", nonlandPermanentCardInYourGraveyard))),
        Keyword.Paradigm,
    )

    // CR 707.2: the token copies the creature; it is created by the target player
    @Test
    fun mirrorcastColloquy() = card(
        name = "Mirrorcast Colloquy",
        cost = "{4}{U}{U}",
        type = "Sorcery — Lesson",
        text = """
            Target player creates a token that's a copy of target creature you control.
            Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.Target("player"))),
            targets = listOf(targetPlayer, slot("creature", creatureYouControl)),
        ),
        Keyword.Paradigm,
    )

    @Test
    fun etiquetteThesis() = card(
        name = "Etiquette Thesis",
        cost = "{3}{B}{B}",
        type = "Sorcery — Lesson",
        text = """
            Target player draws two cards and loses 2 life.
            Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
        """,
    ) parsesTo abilities(
        spell(draw(2, Players.Target("player")), loseLife(2, Players.Target("player")), targets = listOf(targetPlayer)),
        Keyword.Paradigm,
    )

    // CR 608.2g: spells cast while it resolves; lands among the exiled cards can't be cast
    @Test
    fun extemporalFinale() = card(
        name = "Extemporal Finale",
        cost = "{5}{R}{R}",
        type = "Sorcery — Lesson",
        text = """
            Exile cards from the top of your library until you exile cards with total mana value 4 or greater. You may cast any number of spells from among them without paying their mana costs.
            Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.RevealUntil(ObjectFilter.Any, Players.You, count = n(0), reveal = true, into = Zone.Exile, foundBind = "exiled",
                totalAtLeast = Total(NumericProperty.ManaValue, n(4)))),
            Step.ForEach(Each.Objects(Objects.Bound("exiled")), "card", listOf(Step.May(listOf(
                doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You))),
        ),
        Keyword.Paradigm,
    )

    @Test
    fun sproutingWorkshop() = card(
        name = "Sprouting Workshop",
        cost = "{3}{G}{G}",
        type = "Sorcery — Lesson",
        text = """
            Put two +1/+1 counters on each creature you control.
            Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
        """,
    ) parsesTo abilities(spell(counters(Objects.Each(creatureYouControl), 2)), Keyword.Paradigm)

    // CR 601.2c: up to X targets, X announced first; CR 608.2n: a spell exiled as it resolves isn't put into the graveyard
    @Test
    fun branchingFormula() = card(
        name = "Branching Formula",
        cost = "{X}{X}{U}",
        type = "Instant",
        text = """
            Return up to X target instant and/or sorcery cards from your graveyard to your hand.
            Exile Branching Formula.
        """,
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Hand),
            move(Objects.Self, Zone.Exile),
            targets = listOf(TargetSlot("card", TargetKind.Objects(inYourGraveyard(instantOrSorcery)), min = n(0), max = x)),
            announced = listOf("X"),
        ),
    )

    // CR 402.2: no maximum hand size, for the rest of the game
    @Test
    fun loreOfTheEons() = card(
        name = "Lore of the Eons",
        cost = "{4}{U}{U}{U}",
        type = "Sorcery",
        text = """
            Return all instant and sorcery cards from your graveyard to your hand. You have no maximum hand size for the rest of the game.
            Exile Lore of the Eons.
        """,
    ) parsesTo abilities(
        spell(
            move(Objects.Each(inYourGraveyard(instantOrSorcery)), Zone.Hand),
            doing(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You), Duration.Indefinitely)),
            move(Objects.Self, Zone.Exile),
        ),
    )

    // CR 702.34a: flashback with a cost equal to its mana cost
    @Test
    fun secondRecital() = card(
        name = "Second Recital",
        cost = "{R}",
        type = "Instant",
        text = "Target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)",
    ) parsesTo abilities(
        spell(
            gains(target("card"), Keyword.Flashback(cost(CostPart.ItsManaCost))),
            targets = listOf(slot("card", inYourGraveyard(instantOrSorcery))),
        ),
    )

    // CR 601.2a: where the spell was cast from; flashback casts it from the graveyard
    @Test
    fun relicsRunAmok() = card(
        name = "Relics Run Amok",
        cost = "{1}{W}{W}",
        type = "Sorcery",
        text = """
            Create two 2/2 red and white Spirit creature tokens. Then if this spell was cast from anywhere other than your hand, put a +1/+1 counter on each Spirit you control.
            Flashback {4}{W}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            create(spirit, n(2)),
            ifThen(not(Condition.CastFrom(Zone.Hand, Objects.Self)), counters(Objects.Each(and(sub("Spirit"), you)))),
        ),
        Keyword.Flashback(manaCost("{4}{W}{W}")),
    )

    // CR 400.7: the card returned is a new object; CR 122.6: it enters with the counter
    @Test
    fun wistfulReverie() = card(
        name = "Wistful Reverie",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Exile target creature you control, then return that card to the battlefield under its owner's control with a +1/+1 counter on it.
            Flashback {2}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")),
                counters = listOf(CountersToPut(p1p1, n(1)))))),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
        Keyword.Flashback(manaCost("{2}{W}")),
    )

    @Test
    fun excavationLedger() = card(
        name = "Excavation Ledger",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on target creature you control. It gains vigilance until end of turn.
            Flashback {W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(counters(target("creature")), gains(target("creature"), Keyword.Vigilance), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Flashback(manaCost("{W}")),
    )

    // CR 702.34a: a flashback cost that isn't mana
    @Test
    fun sharedAssignment() = card(
        name = "Shared Assignment",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Create a 2/2 red and white Spirit creature token.
            Flashback—Tap three untapped creatures you control. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(spirit)),
        Keyword.Flashback(cost(CostPart.TapObjects(CostObjects.Choose(creature, n(3))))),
    )

    // CR 608.2d: the choice is made as it resolves
    @Test
    fun rehearsedAssault() = card(
        name = "Rehearsed Assault",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on each creature target player controls. Target creature gains your choice of double strike or lifelink until end of turn.
            Flashback {1}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            counters(Objects.Each(and(creature, ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Is(Players.Target("player")))))),
            Step.Choose(arcanum.api.Choice.Option(listOf("double strike", "lifelink")), bind = "keyword", chooser = Players.You),
            ifThen(Condition.Chose("keyword", "double strike"), gains(target("creature"), Keyword.DoubleStrike),
                otherwise = listOf(gains(target("creature"), Keyword.Lifelink))),
            targets = listOf(targetPlayer, slot("creature", creature)),
        ),
        Keyword.Flashback(manaCost("{1}{W}")),
    )

    @Test
    fun sparringStratagem() = card(
        name = "Sparring Stratagem",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Sparring Stratagem deals 1 damage to target creature. It can't block this turn.
            Flashback {1}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            damage(1, toObjects(target("creature"))),
            untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creature)),
        ),
        Keyword.Flashback(manaCost("{1}{R}")),
    )

    @Test
    fun codexBurst() = card(
        name = "Codex Burst",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Codex Burst deals 2 damage to any target.
            Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(damage(2, toAny), targets = listOf(anyTarget)), Keyword.Flashback(manaCost("{4}{R}")))

    // CR 118.12: "you may discard a card. If you do" is a cost paid on resolution
    @Test
    fun chaseBygoneDays() = card(
        name = "Chase Bygone Days",
        cost = "{R}{W}",
        type = "Sorcery",
        text = """
            You gain 2 life. You may discard a card. If you do, draw two cards.
            Flashback {2}{R}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(gainLife(2), Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(2))),
        Keyword.Flashback(manaCost("{2}{R}{W}")),
    )

    // CR 601.2b, 107.3: X is announced for the additional cost; each card has exactly that mana value
    @Test
    fun mendWhatShattered() = card(
        name = "Mend What Shattered",
        cost = "{2}{W}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, pay X life.
            Return each artifact and creature card with mana value X from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.PayLife(x)))),
        spell(
            move(Objects.Each(inYourGraveyard(or(artifact, creature), ObjectFilter.ManaValue(Comparison.Equal, x))), Zone.Battlefield),
            announced = listOf("X"),
        ),
    )

    @Test
    fun cutthroatContest() = card(
        name = "Cutthroat Contest",
        cost = "{2}{B}{G}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, pay X life.
            Destroy all artifacts and creatures with mana value X or less.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.PayLife(x)))),
        spell(destroy(Objects.Each(and(or(artifact, creature), ObjectFilter.ManaValue(Comparison.AtMost, x)))), announced = listOf("X")),
    )

    // CR 700.2d: a mode chosen several times happens that many times in sequence, each with its own target
    @Test
    fun hourOfJudgmentRendered() = card(
        name = "Hour of Judgment Rendered",
        cost = "{3}{W}{W}{B}{B}",
        type = "Sorcery",
        text = """
            Choose up to four. You may choose the same mode more than once.
            • Destroy target nonland permanent.
            • Return target nonland permanent card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        arcanum.api.Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("permanent", and(nonland, permanentFilter))), steps = listOf(destroy(target("permanent"))))),
            Mode(Instructions(targets = listOf(slot("card", nonlandPermanentCardInYourGraveyard)), steps = listOf(move(target("card"), Zone.Battlefield)))),
        ), min = 0, max = 4, repeatable = true, chooser = Players.You))),
    )
}
