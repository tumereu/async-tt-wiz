package arcanum.parser.sets.sos

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 11: casting and copying — a spell that can't be copied (CR 707.10), copying a creature spell (the copy
 * becomes a token, CR 707.10f, 111.13) with what it gains, copies paid for by sacrificing or tapping, a spell copying itself as it is
 * cast, spells you don't own (CR 108.3), casting from an opponent's graveyard with mana of any type, a free spell once each turn, keywords
 * given to cards in hand and to spells as they are cast (miracle, storm, cascade, casualty, affinity; CR 601.2a), restricted mana,
 * activation conditions, a cost reduced by counters (CR 601.2f), Grandeur, a self-returning sorcery and a cost reduced by total toughness.
 */
class SosChunk11CastingAndCopyingTest {
    private val yourInstantAndSorcerySpells = and(instantOrSorcery, spellFilter, you)
    private val page = arcanum.api.CounterKind.Named("page")
    private val copyInstantOrSorcery = Mode(Instructions(targets = listOf(slot("spell", and(instantOrSorcery, spellFilter, you))),
        steps = listOf(doing(Effect.CopySpell(target("spell"), Players.You, newTargets = true)))))
    private val sacrificeAtEndStep = triggered(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Self)))
    private val copyCreature = Mode(Instructions(targets = listOf(slot("spell", and(spellFilter, creature, you))), steps = listOf(
        doing(Effect.CopySpell(target("spell"), Players.You, newTargets = false, bind = "copy")),
        doing(Effect.SpellGets(Objects.Bound("copy"), listOf(Mechanic.GainAbilities(listOf(Keyword.Haste, sacrificeAtEndStep))))))))

    // CR 707.10: an effect that would copy it does nothing; CR 700.2: one or both modes
    @Test
    fun stagedEmbers() = card(
        name = "Staged Embers",
        cost = "{R}{R}",
        type = "Instant",
        text = """
            This spell can't be copied.
            Choose one or both —
            • Copy target instant or sorcery spell you control. You may choose new targets for the copy.
            • Copy target creature spell you control. The copy gains haste and "At the beginning of the end step, sacrifice this token."
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCopied),
        Ability.Spell(Instructions(modes = Modes(listOf(copyInstantOrSorcery, copyCreature), min = 1, max = 2, chooser = Players.You))),
    )

    // CR 118.12: sacrificing as the trigger resolves, a cost; CR 707.10c: new targets for the copy
    @Test
    fun teskaReaderOfWreckage() = card(
        name = "Teska, Reader of Wreckage",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Artificer",
        pt = "4/4",
        text = """
            Ward—Pay 3 life. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays 3 life.)
            Whenever you cast an instant or sorcery spell, you may sacrifice an artifact. If you do, copy that spell and you may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost(CostPart.PayLife(n(3)))),
        triggered(youCastInstantOrSorcery,
            Step.Pay(cost(sacrificeA(artifact)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)))),
    )

    @Test
    fun marisolSpireDuelCaptain() = card(
        name = "Marisol, Spire Duel Captain",
        cost = "{R}{W}",
        type = "Legendary Creature — Djinn Sorcerer",
        pt = "2/2",
        text = "Whenever you cast an instant or sorcery spell, you may tap three untapped creatures you control. If you do, copy that spell. You may choose new targets for the copy.",
    ) parsesTo abilities(
        triggered(youCastInstantOrSorcery,
            Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(creature, n(3)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)))),
    )

    // CR 603.2: "while you control a creature" is checked only as it triggers; CR 701.21a: each player chooses; CR 707.10: the copy keeps
    // the spell's choices (new targets only when the text says so, CR 707.10c)
    @Test
    fun coldShoulder() = card(
        name = "Cold Shoulder",
        cost = "{1}{W}{B}",
        type = "Sorcery",
        text = """
            When you cast this spell while you control a creature, you may copy this spell.
            Each player sacrifices a creature of their choice. Each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Is(Objects.Self)), Step.May(listOf(doing(Effect.CopySpell(Objects.Self, Players.You, newTargets = false))), Players.You),
            triggersOnlyIf = Condition.Exists(creatureYouControl, atLeast = 1), functionsIn = listOf(Zone.Stack)),
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "sacrificed",
                    chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true),
            loseLife(1, Players.Each(PlayerFilter.Opponent)),
            gainLife(1),
        ),
    )

    // CR 108.3: a spell you don't own (a copy is owned by the player who created it, CR 707.10)
    @Test
    fun odalysAgoraPeacemaker() = card(
        name = "Odalys, Agora Peacemaker",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/3",
        text = """
            Whenever you cast a spell you don't own, put a +1/+1 counter on each creature you control.
            {2}, Sacrifice another creature: Exile target instant or sorcery card from an opponent's graveyard. You may cast it this turn, and mana of any type can be spent to cast that spell. If that spell would be put into a graveyard, exile it instead. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youCast(not(ObjectFilter.OwnedBy(PlayerFilter.You))), counters(Objects.Each(creatureYouControl))),
        activated(cost(mana("{2}"), sacrificeA(and(another, creature))),
            doing(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, manaAsAnyColor = true, holder = PlayerFilter.You,
                then = Destination(Zone.Exile), thenInsteadOf = Zone.Graveyard)), Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            targets = listOf(slot("card", and(instantOrSorcery, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)))),
            rules = asSorcery),
    )

    // CR 601.2a, 118.9: a permission to cast without paying, once each of your turns
    @Test
    fun corwenAndTheSqualls() = card(
        name = "Corwen and the Squalls",
        cost = "{5}{U}{R}",
        type = "Legendary Creature — Human Bard Sorcerer",
        pt = "5/7",
        text = "Once during each of your turns, you may cast an instant or sorcery spell from your hand without paying its mana cost.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Hand, PlayKind.Cast, withoutPaying = true, perTurn = 1, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(and(instantOrSorcery, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))), condition = Condition.YourTurn),
    )

    // CR 702.94a: miracle works from the hand
    @Test
    fun embervaultTheChronicler() = card(
        name = "Embervault, the Chronicler",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — Elder Dragon",
        pt = "5/5",
        text = """
            Flying, haste
            Each instant and sorcery card in your hand has miracle {2}. (You may cast a card for its miracle cost when you draw it if it's the first card you drew this turn.)
            At the beginning of each opponent's upkeep, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        static(has(Keyword.Miracle(manaCost("{2}"))), affects = Objects.Each(and(instantOrSorcery, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Opponent), Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(1))),
    )

    // CR 702.40a: storm triggers as the spell is cast, so the spell has it from the moment it is cast
    @Test
    fun hueflareTheMuse() = card(
        name = "Hueflare, the Muse",
        cost = "{5}{U}{R}",
        type = "Legendary Creature — Elder Dragon",
        pt = "7/7",
        text = """
            Flying
            Ward—Pay 5 life.
            Instant and sorcery spells you cast have storm. (Whenever you cast an instant or sorcery spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(cost(CostPart.PayLife(n(5)))),
        static(has(Keyword.Storm), affects = Objects.Each(yourInstantAndSorcerySpells)),
    )

    // CR 702.85a: cascade triggers as the spell is cast
    @Test
    fun tesseractTheTheorem() = card(
        name = "Tesseract, the Theorem",
        cost = "{4}{G}{U}",
        type = "Legendary Creature — Elder Dragon",
        pt = "6/6",
        text = """
            Flying, trample
            Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)
            Instant and sorcery spells you cast from your hand have cascade.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        Keyword.Cascade,
        static(has(Keyword.Cascade), affects = Objects.Each(and(yourInstantAndSorcerySpells, ObjectFilter.WasCast(from = Zone.Hand)))),
    )

    // CR 702.153a: casualty is an optional additional cost of the spell
    @Test
    fun inkwingThePolemicist() = card(
        name = "Inkwing, the Polemicist",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Elder Dragon",
        pt = "4/4",
        text = """
            Flying, vigilance
            Each instant and sorcery spell you cast has casualty 1. (As you cast that spell, you may sacrifice a creature with power 1 or greater. When you do, copy the spell and you may choose new targets for the copy.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(has(Keyword.Casualty(1)), affects = Objects.Each(yourInstantAndSorcerySpells)),
    )

    // CR 702.41a: affinity reduces the cost as it is determined (CR 601.2f)
    @Test
    fun rotbloomTheEquilibrist() = card(
        name = "Rotbloom, the Equilibrist",
        cost = "{6}{B}{G}",
        type = "Legendary Creature — Elder Dragon",
        pt = "5/5",
        text = """
            Affinity for creatures (This spell costs {1} less to cast for each creature you control.)
            Flying, deathtouch
            Instant and sorcery spells you cast have affinity for creatures.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(creature, "creatures"),
        Keyword.Flying,
        Keyword.Deathtouch,
        static(has(Keyword.Affinity(creature, "creatures")), affects = Objects.Each(yourInstantAndSorcerySpells)),
    )

    // CR 106.6: mana only for instant and sorcery spells
    @Test
    fun abstractedColormage() = card(
        name = "Abstracted Colormage",
        cost = "{U}{U/R}{R}",
        type = "Creature — Djinn Sorcerer",
        pt = "2/2",
        text = "At the beginning of your first main phase, add {U}{R}. Spend this mana only to cast instant and sorcery spells.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}{R}")!!),
            restriction = and(spellFilter, instantOrSorcery)), Players.You))),
    )

    // CR 613.1f: the lands gain the mana ability
    @Test
    fun echoingCittern() = card(
        name = "Echoing Cittern",
        cost = "{2}{U}{R}",
        type = "Artifact",
        text = """
            Lands you control have "{T}: Add two mana of any one color. Spend this mana only to cast instant and sorcery spells."
            {T}: Draw a card. Activate only if you have seven or more cards in your hand.
        """,
    ) parsesTo abilities(
        static(has(activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(2), restriction = and(spellFilter, instantOrSorcery)), Players.You)))),
            affects = Objects.Each(and(land, you))),
        activated(cost(tap), draw(1), rules = anyTime.copy(condition = Condition.Compare(Value.HandSize(Players.You), Comparison.AtLeast, n(7)))),
    )

    @Test
    fun brewersHoard() = card(
        name = "Brewer's Hoard",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            {T}: You gain 2 life. Activate only if you've cast an instant or sorcery spell this turn.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
        activated(cost(tap), gainLife(2), rules = anyTime.copy(condition = Condition.HappenedThisTurn(EventPattern.Cast(instantOrSorcery, PlayerFilter.You), atLeast = 1,
            measure = Measure.Events))),
    )

    // CR 601.2f, 602.2b: {1} less for each page counter
    @Test
    fun journalOfReveries() = card(
        name = "Journal of Reveries",
        cost = "{2}",
        type = "Artifact — Book",
        text = """
            Whenever you cast an instant or sorcery spell, put a page counter on this artifact.
            {5}, {T}: Draw a card. This ability costs {1} less to activate for each page counter on this artifact.
        """,
    ) parsesTo abilities(
        triggered(youCastInstantOrSorcery, counters(Objects.Self, 1, page)),
        activated(cost(mana("{5}"), tap), draw(1), rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Of(NumericProperty.Counters(page), Objects.Self, Combine.Single)))),
    )

    // CR 207.2c: Grandeur is an ability word; the discarded card has the same name (CR 201.2)
    @Test
    fun folioStraySheet() = card(
        name = "Folio, Stray Sheet",
        cost = "{2}",
        type = "Legendary Artifact Creature — Construct",
        pt = "0/2",
        text = """
            {T}: Add {C}.
            Grandeur — Discard another card named Folio, Stray Sheet: Reveal cards from the top of your library until you reveal an instant or sorcery card. Put that card into your hand and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        Ability.Activated(cost(CostPart.Discard(CostObjects.Choose(and(ObjectFilter.Named("Folio, Stray Sheet"), another), n(1)))), Instructions(steps = listOf(
            doing(Effect.RevealUntil(instantOrSorcery, Players.You, count = n(1), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Hand),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))), anyTime, abilityWord = "Grandeur"),
    )

    // CR 113.6b: the trigger works in the graveyard
    @Test
    fun wardensAssurance() = card(
        name = "Warden's Assurance",
        cost = "{W}{B}",
        type = "Sorcery",
        text = """
            Target creature gets +1/+1 until end of turn. Draw a card.
            Whenever one or more creatures you control deal combat damage to a player, you may pay {W/B}. If you do, return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1), draw(1), targets = listOf(slot("creature", creature))),
        triggered(Trigger.On(listOf(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)), oneOrMore = true, batchPerPlayer = true),
            Step.Pay(manaCost("{W/B}"), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), move(Objects.Self, Zone.Hand)), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 611.2c: each card's owner may play it until the end of that player's next turn
    @Test
    fun stayTheHostility() = card(
        name = "Stay the Hostility",
        cost = "{1}{R}{W}",
        type = "Instant",
        text = "Exile target nonland permanent and the top card of your library. For each of those cards, its owner may play it until the end of their next turn.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(Objects.Union(listOf(target("permanent"), Objects.LibraryCards(Players.You, n(1), fromTop = true))), Destination(Zone.Exile), postBind = "exiled")),
            Step.ForEach(Each.Objects(Objects.Bound("exiled")), "card", listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play,
                holder = PlayerFilter.Is(Players.OwnerOf(Objects.Bound("card"))))), Affected.Objects(Objects.Bound("card")),
                Duration.UntilEndOfNextTurnOf(Players.OwnerOf(Objects.Bound("card"))))))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 601.2f: the reduction applies while casting if the creatures' total toughness is 10 or greater
    @Test
    fun merrinSurfChoreographer() = card(
        name = "Merrin, Surf Choreographer",
        cost = "{4}{U}",
        type = "Legendary Creature — Merfolk Bard",
        pt = "2/2",
        text = """
            This spell costs {3} less to cast if creatures you control have total toughness 10 or greater.
            When Merrin enters, draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.Compare(Value.Of(NumericProperty.Toughness, Objects.Each(creatureYouControl), Combine.Total), Comparison.AtLeast, n(10))),
        triggered(entersSelf, draw(2)),
    )
}
