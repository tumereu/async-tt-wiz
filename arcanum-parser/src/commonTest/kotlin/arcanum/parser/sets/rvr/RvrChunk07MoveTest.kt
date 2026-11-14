package arcanum.parser.sets.rvr

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.Measure
import arcanum.api.PlayerFilter
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk07MoveTest {
    private fun exileFromAGraveyard(cost: Cost, kind: ObjectFilter, vararg then: Step) = activated(
        cost,
        move(target("card"), Zone.Exile, postBind = "exiled"),
        *then,
        targets = listOf(slot("card", and(kind, inZone(Zone.Graveyard)))),
    )

    private val ownerShuffles = arrayOf(move(Objects.Self, Zone.Library), Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))))

    // CR 701.24a: "its owner shuffles it into their library" is a move, then the owner's shuffle
    @Test
    fun ceruleanSphinxLookalike() = card(
        name = "Brinegate Sphinx",
        cost = "{4}{U}{U}",
        type = "Creature — Sphinx",
        pt = "5/5",
        text = """
            Flying
            {U}: This creature's owner shuffles it into their library.
        """,
    ) parsesTo abilities(Keyword.Flying, activated(cost(mana("{U}")), *ownerShuffles))

    // Audit (unsure) on the first ability: "(Activate only as an instant.)" is Timing.Any, i.e. the default; the mana is one of any colour (CR 106.1)
    @Test
    fun deathriteShamanLookalike() = card(
        name = "Gravebloom Warden",
        cost = "{B/G}",
        type = "Creature — Elf Shaman",
        pt = "1/2",
        text = """
            {T}: Exile target land card from a graveyard. Add one mana of any color. (Activate only as an instant.)
            {B}, {T}: Exile target instant or sorcery card from a graveyard. Each opponent loses 2 life.
            {G}, {T}: Exile target creature card from a graveyard. You gain 2 life.
        """,
    ) parsesTo abilities(
        exileFromAGraveyard(cost(tap), land, doing(Effect.AddMana(arcanum.api.ManaProduction(anyOneColor(1)), Players.You))),
        exileFromAGraveyard(cost(mana("{B}"), tap), or(instant, sorcery), loseLife(2, Players.Each(arcanum.api.PlayerFilter.Opponent))),
        exileFromAGraveyard(cost(mana("{G}"), tap), creature, gainLife(2)),
    )

    // CR 709.3: each half of a split card is read on its own
    @Test
    fun findFinalityLookalike() = cardFaces(
        Layout.Split,
        card(
            name = "Regrave",
            cost = "{B/G}{B/G}",
            type = "Sorcery",
            text = "Return up to two target creature cards from your graveyard to your hand.",
        ) to abilities(spell(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2)))),
        card(
            name = "Rotwave",
            cost = "{4}{B}{G}",
            type = "Sorcery",
            text = "You may put two +1/+1 counters on a creature you control. Then all creatures get -4/-4 until end of turn.",
        ) to abilities(spell(
            Step.Choose(Choice.Objects(creatureYouControl, n(0), n(1)), bind = "chosen", chooser = Players.You),
            counters(Objects.Bound("chosen"), 2),
            untilEndOfTurn(Objects.Each(creature), pt(-4, -4)),
        )),
    )

    @Test
    fun forcedLandingLookalike() = card(
        name = "Skyhook Snare",
        cost = "{1}{G}",
        type = "Instant",
        text = "Put target creature with flying on the bottom of its owner's library.",
    ) parsesTo abilities(spell(move(target("creature"), Zone.Library, position = -1),
        targets = listOf(slot("creature", and(creature, kw(Keyword.Flying))))))

    // CR 700.4: "died" is battlefield to graveyard; the condition checks this turn's events
    @Test
    fun fungalRebirthLookalike() = card(
        name = "Mossbed Rebirth",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Return target permanent card from your graveyard to your hand. If a creature died this turn, create two 1/1 green Saproling creature tokens.
        """,
    ) parsesTo abilities(spell(
        move(target("card"), Zone.Hand),
        ifThen(Condition.HappenedThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events),
            create(creatureToken("Saproling", listOf(Color.Green), 1, 1), n(2))),
        targets = listOf(slot("card", inYourGraveyard(permanentCard))),
    ))

    // CR 602.1: sacrifice is a cost, the creature is chosen on activation
    @Test
    fun golgariGuildmageLookalike() = card(
        name = "Verdant Guildmage",
        cost = "{B/G}{B/G}",
        type = "Creature — Elf Shaman",
        pt = "2/2",
        text = """
            {4}{B}, Sacrifice a creature: Return target creature card from your graveyard to your hand.
            {4}{G}: Put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{4}{B}"), sacrificeA(creature)), move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        activated(cost(mana("{4}{G}")), counters(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 401.4: owner's library, bottom
    @Test
    fun junktrollerLookalike() = card(
        name = "Scrapyard Warden",
        cost = "{4}",
        type = "Artifact Creature — Golem",
        pt = "0/6",
        text = """
            Defender
            {T}: Put target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(Keyword.Defender, activated(cost(tap), move(target("card"), Zone.Library, position = -1),
        targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))))

    // CR 603.2: triggers when it becomes the target of any spell or ability
    @Test
    fun petrahydroxLookalike() = card(
        name = "Mirrorhide Weird",
        cost = "{3}{U/R}",
        type = "Creature — Weird",
        pt = "3/3",
        text = """
            ({U/R} can be paid with either {U} or {R}.)
            When this creature becomes the target of a spell or ability, return this creature to its owner's hand.
        """,
    ) parsesTo abilities(triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), by = ObjectFilter.Any)), move(Objects.Self, Zone.Hand)))

    // CR 107.3: X is announced, the target's mana value equals it; the two lines are one spell
    @Test
    fun repealLookalike() = card(
        name = "Recall Surge",
        cost = "{X}{U}",
        type = "Instant",
        text = """
            Return target nonland permanent with mana value X to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(spell(
        move(target("permanent"), Zone.Hand),
        draw(1),
        targets = listOf(slot("permanent", and(nonland, permanentFilter, ObjectFilter.ManaValue(Comparison.Equal, Value.Announced("X"))))),
        announced = listOf("X"),
    ))

    // CR 700.4: dies; CR 118.8 sacrifice cost of three white creatures
    @Test
    fun teysaLookalike() = card(
        name = "Bonetide Council",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/3",
        text = """
            Sacrifice three white creatures: Exile target creature.
            Whenever another black creature you control dies, create a 1/1 white Spirit creature token with flying.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(and(ObjectFilter.OfColor(ColorRef.Literal(Color.White)), creature), count = n(3)))),
            move(target("creature"), Zone.Exile), targets = listOf(slot("creature", creature))),
        triggered(dies(and(another, ObjectFilter.OfColor(ColorRef.Literal(Color.Black)), creature, you)),
            create(creatureToken("Spirit", listOf(Color.White), 1, 1, listOf(Keyword.Flying)))),
    )

    // CR 601.2i: cast trigger; the target is any permanent
    @Test
    fun tidespoutTyrantLookalike() = card(
        name = "Stormcrown Djinn",
        cost = "{5}{U}{U}{U}",
        type = "Creature — Djinn",
        pt = "5/5",
        text = """
            Flying
            Whenever you cast a spell, return target permanent to its owner's hand.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(youCast(ObjectFilter.Any), move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", permanentFilter))))

    // CR 603.7c, 400.7: the Staff is sacrificed as a cost, so the card is a bound object (not memory of the Staff); the delayed trigger returns the exiled card (a new object) under its owner's control at the next end step
    @Test
    fun voyagerStaffLookalike() = card(
        name = "Gleamcoin Staff",
        cost = "{1}",
        type = "Artifact",
        text = """
            {2}, Sacrifice this artifact: Exile target creature. Return the exiled card to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(activated(cost(mana("{2}"), sacrificeSelf),
        move(target("creature"), Zone.Exile, postBind = "exiled"),
        doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
        delayed(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))),
        targets = listOf(slot("creature", creature))))

    // CR 709.3: each half is read on its own; CR 506.4: attacking or blocking creature; position 0 is the top of the library
    @Test
    fun warrantWardenLookalike() = cardFaces(
        Layout.Split,
        card(
            name = "Shelve",
            cost = "{W/U}{W/U}",
            type = "Instant",
            text = "Put target attacking or blocking creature on top of its owner's library.",
        ) to abilities(spell(move(target("creature"), Zone.Library, position = 0),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature))))),
        card(
            name = "Sentinel Born",
            cost = "{3}{W}{U}",
            type = "Sorcery",
            text = "Create a 4/4 white and blue Sphinx creature token with flying and vigilance.",
        ) to abilities(spell(create(creatureToken("Sphinx", listOf(Color.White, Color.Blue), 4, 4, listOf(Keyword.Flying, Keyword.Vigilance))))),
    )
}
