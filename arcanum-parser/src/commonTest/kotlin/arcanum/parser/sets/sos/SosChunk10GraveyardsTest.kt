package arcanum.parser.sets.sos

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 10: graveyards — cards leaving your graveyard (one trigger for cards leaving together, looking back in
 * time, CR 603.10a) and "if a card left your graveyard this turn", exiling cards from graveyards, abilities that work from the graveyard
 * (CR 113.6m), a blink until the next end step (CR 603.7), "cards were put into exile this turn", an alternative additional cost
 * ("exile two cards or pay {1}{W}"), a mana ability with a reflexive trigger (CR 605.1a, 603.12) and a card milled to be played this
 * turn.
 */
class SosChunk10GraveyardsTest {
    private val cardLeftYourGraveyard = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
        from = Zone.Graveyard), atLeast = 1, measure = Measure.Events)
    private val exileUpToOneFromAGraveyard = triggered(beginningOfCombatOnYourTurn, move(target("card"), Zone.Exile, postBind = "exiled"),
        targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0)))

    @Test
    fun owlfolkChronicler() = card(
        name = "Owlfolk Chronicler",
        cost = "{2}{W}",
        type = "Creature — Bird Cleric",
        pt = "2/3",
        text = """
            Flying
            When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            Whenever one or more cards leave your graveyard, this creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Surveil(Players.You, n(1)))),
        triggered(leaveYourGraveyard, pump(Objects.Self, 1, 1)),
    )

    // CR 603.4: an intervening if over the whole turn
    @Test
    fun foundationalInquiry() = card(
        name = "Foundational Inquiry",
        cost = "{4}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, return target nonland permanent card with mana value 3 or less from your graveyard to the battlefield.
            At the beginning of your end step, if a card left your graveyard this turn, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(nonland, permanentCard, manaValue(arcanum.api.Comparison.AtMost, 3))))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), draw(1), interveningIf = cardLeftYourGraveyard),
    )

    @Test
    fun barracksDigger() = card(
        name = "Barracks Digger",
        cost = "{3}{R}",
        type = "Creature — Orc Sorcerer",
        pt = "3/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever one or more cards leave your graveyard, create a 2/2 red and white Spirit creature token.
        """,
    ) parsesTo abilities(
        Keyword.Menace, triggered(leaveYourGraveyard, create(spirit)),
    )

    // CR 508.3d: "whenever you attack" triggers once per declaration
    @Test
    fun breathingChronicle() = card(
        name = "Breathing Chronicle",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 2/2 red and white Spirit creature token.
            Whenever you attack, if a card left your graveyard this turn, target attacking creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(spirit)),
        triggered(youAttack, pump(target("creature"), 2, 0), targets = listOf(slot("creature", and(attacking, creature))), interveningIf = cardLeftYourGraveyard),
    )

    @Test
    fun vesselOfCraving() = card(
        name = "Vessel of Craving",
        cost = "{2}{R}{W}",
        type = "Artifact",
        text = """
            Whenever one or more cards leave your graveyard, this artifact deals 1 damage to each opponent and you gain 1 life.
            {T}: Mill a card. You may play that card this turn.
        """,
    ) parsesTo abilities(
        triggered(leaveYourGraveyard, damage(1, eachOpponent), gainLife(1)),
        activated(cost(tap), mill(1, postBind = "milled"), doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
            Affected.Objects(Objects.Bound("milled")), Duration.EndOfTurn))),
    )

    @Test
    fun weatheredAcademic() = card(
        name = "Weathered Academic",
        cost = "{R}{W}",
        type = "Creature — Bird Cleric",
        pt = "2/1",
        text = """
            Flying, haste
            Discard a card: This creature gains lifelink until end of turn.
            Whenever one or more cards leave your graveyard, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        activated(cost(discardACard), gains(Objects.Self, Keyword.Lifelink)),
        triggered(leaveYourGraveyard, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun spiritPennantBearer() = card(
        name = "Spirit Pennant-Bearer",
        cost = "{R}{W}",
        type = "Creature — Spirit Ox",
        pt = "2/2",
        text = "Whenever one or more cards leave your graveyard, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(leaveYourGraveyard, counters(Objects.Self)),
    )

    // CR 601.2f: the reduction applies while casting; CR 614.1a: a replacement for the rest of the turn
    @Test
    fun witherInTheSwelter() = card(
        name = "Wither in the Swelter",
        cost = "{2}{R}{W}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if one or more cards left your graveyard this turn.
            Wither in the Swelter deals 5 damage to target creature. If that creature would die this turn, exile it instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))).copy(
            affects = Affected.Objects(Objects.Self), condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
                from = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)),
        spell(
            damage(5, toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun jumpyRelicSloth() = card(
        name = "Jumpy Relic Sloth",
        cost = "{2}{R}{W}",
        type = "Creature — Sloth Beast",
        pt = "4/4",
        text = """
            Trample, lifelink
            At the beginning of combat on your turn, exile up to one target card from a graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Trample, Keyword.Lifelink, exileUpToOneFromAGraveyard,
    )

    @Test
    fun risingDustcaller() = card(
        name = "Rising Dustcaller",
        cost = "{4}{W}",
        type = "Creature — Orc Cleric",
        pt = "3/4",
        text = """
            Flying
            When this creature enters, put a +1/+1 counter on another target creature you control.
            At the beginning of combat on your turn, exile up to one target card from a graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", and(another, creature, you)))),
        exileUpToOneFromAGraveyard,
    )

    // CR 603.7: the card returns at the beginning of the next end step; CR 400.7: a new object
    @Test
    fun brannocDebateSteward() = card(
        name = "Brannoc, Debate Steward",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "1/1",
        text = """
            When Brannoc enters, exile up to one other target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
            At the beginning of your end step, if one or more cards were put into exile this turn, put a +1/+1 counter on Brannoc.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))), postBind = "returned")))))),
            targets = listOf(slot("creature", and(another, creature, you), min = 0))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), counters(Objects.Self), interveningIf = Condition.HappenedThisTurn(
            EventPattern.ZoneChange(ObjectFilter.Category(ObjectCategory.Card), to = Zone.Exile), atLeast = 1, measure = Measure.Events)),
    )

    // CR 113.6m: it works in the graveyard, its cost exiling it from there
    @Test
    fun statuaryGuide() = card(
        name = "Statuary Guide",
        cost = "{1}{W}",
        type = "Creature — Spirit Chimera",
        pt = "3/1",
        text = "{W}, Exile this card from your graveyard: You gain 2 life. Surveil 1. Activate only as a sorcery. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        activated(cost(mana("{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), gainLife(2), doing(Effect.Surveil(Players.You, n(1))), rules = asSorcery),
    )

    @Test
    fun conjuredDromedary() = card(
        name = "Conjured Dromedary",
        cost = "{3}{W}",
        type = "Creature — Spirit Camel",
        pt = "4/3",
        text = """
            Vigilance
            {1}{W}: Return this card from your graveyard to your hand. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(manaCost("{1}{W}"), move(Objects.Self, Zone.Hand), rules = asSorcery, functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun perpetualPupil() = card(
        name = "Perpetual Pupil",
        cost = "{3}{B}",
        type = "Creature — Zombie Warlock",
        pt = "4/2",
        text = "{1}{B}, Exile this card from your graveyard: Create two 1/1 white and black Inkling creature tokens with flying.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), create(inkling, n(2))),
    )

    @Test
    fun postMortemLecturer() = card(
        name = "Post-Mortem Lecturer",
        cost = "{1}{B}",
        type = "Creature — Zombie Warlock",
        pt = "2/2",
        text = """
            This creature can't block.
            Whenever this creature attacks, each opponent loses 1 life and you gain 1 life.
            {1}{B}, Exile an instant or sorcery card from your graveyard: Return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)),
        triggered(attacksSelf, loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
        activated(cost(mana("{1}{B}"), CostPart.Exile(CostObjects.Choose(instantOrSorcery, n(1)), from = Zone.Graveyard)), move(Objects.Self, Zone.Battlefield, postBind = "returned")),
    )

    // CR 601.2a: permission to cast it from exile until the end of your next turn
    @Test
    fun seasonedScrollwright() = card(
        name = "Seasoned Scrollwright",
        cost = "{R}{R/W}{W}",
        type = "Creature — Dwarf Cleric",
        pt = "3/2",
        text = """
            First strike
            When this creature enters, exile target noncreature, nonland card from your graveyard. Until the end of your next turn, you may cast that card.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(entersSelf,
            doing(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn)),
            targets = listOf(slot("card", inYourGraveyard(not(creature), nonland)))),
    )

    @Test
    fun ferventLorechanter() = card(
        name = "Fervent Lorechanter",
        cost = "{5}{R}",
        type = "Creature — Giant Sorcerer",
        pt = "4/4",
        text = "When this creature enters, return target instant or sorcery card from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(instantOrSorcery)))),
    )

    @Test
    fun dragFromTheCrypt() = card(
        name = "Drag from the Crypt",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Return up to two target creature cards from your graveyard to your hand. You gain 2 life.",
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), gainLife(2), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))),
    )

    // CR 601.2f: one of two additional costs
    @Test
    fun glidingStoneflier() = card(
        name = "Gliding Stoneflier",
        cost = "{2}{W}",
        type = "Creature — Elephant Cleric",
        pt = "4/3",
        text = """
            As an additional cost to cast this spell, exile two cards from your graveyard or pay {1}{W}.
            Flying, vigilance
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(
            cost(CostPart.Exile(CostObjects.Choose(cardFilter, n(2)), from = Zone.Graveyard)),
            manaCost("{1}{W}")))))),
        Keyword.Flying,
        Keyword.Vigilance,
    )

    // CR 605.1a: a mana ability; CR 603.12: its reflexive trigger uses the Stack
    @Test
    fun debrisAgitator() = card(
        name = "Debris Agitator",
        cost = "{2}{R}",
        type = "Creature — Dwarf Sorcerer",
        pt = "1/4",
        text = """
            When this creature enters, you may discard a card. If you do, draw a card.
            {T}, Exile a card from your graveyard: Add {R}. When you do, this creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(1))),
        activated(cost(tap, CostPart.Exile(CostObjects.Choose(cardFilter, n(1)), from = Zone.Graveyard)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You)),
            reflexive(damage(1, eachOpponent))),
    )

    // CR 118.12: exiling a card on resolution; the damage goes to the creature's controller as it last existed
    @Test
    fun fieryQuarrel() = card(
        name = "Fiery Quarrel",
        cost = "{4}{R}",
        type = "Instant",
        text = "Fiery Quarrel deals 6 damage to target creature. You may exile a card from your graveyard. If you do, Fiery Quarrel also deals 2 damage to that creature's controller.",
    ) parsesTo abilities(
        spell(
            damage(6, toObjects(target("creature"))),
            Step.Pay(cost(CostPart.Exile(CostObjects.Choose(cardFilter, n(1)), from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), damage(2, Recipients(players = Players.ControllerOf(target("creature"))))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 106.6: mana spent only on instant and sorcery spells
    @Test
    fun slabOfFinding() = card(
        name = "Slab of Finding",
        cost = "{2}{R}",
        type = "Artifact",
        text = """
            When this artifact enters, mill a card. You may play that card this turn. (To mill a card, put the top card of your library into your graveyard.)
            {T}: Add {R}.
            {T}: Add {R}{R}. Spend this mana only to cast instant and sorcery spells.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(1, postBind = "milled"), doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
            Affected.Objects(Objects.Bound("milled")), Duration.EndOfTurn))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!), restriction = and(spellFilter, instantOrSorcery)), Players.You))),
    )
}
