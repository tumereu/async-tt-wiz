package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CopyException
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.EventPattern
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 05: changelings (CR 702.73: every creature type, in every zone) and Shapeshifter tokens with changeling,
 * cards from graveyards exiled, exiling and returning a creature (CR 400.7), entering as a copy with changeling added (CR 707.9b),
 * losing all creature types (layer 4), a Mutavault land token described by its reminder text, an artifact that becomes a creature
 * (CR 205.1b) and an Equipment whose creature is all creature types.
 */
class EclChunk05ChangelingsTest {
    @Test
    fun changelingPathseeker() = card(
        name = "Changeling Pathseeker",
        cost = "{3}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            When this creature enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        triggered(entersSelf, Step.May(listOf(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You)),
    )

    @Test
    fun gableLurker() = card(
        name = "Gable Lurker",
        cost = "{5}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Flying
            When this creature enters, exile up to two target cards from graveyards. You gain 3 life.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Flying,
        triggered(entersSelf, move(target("cards"), Zone.Exile), gainLife(3),
            targets = listOf(slot("cards", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 2))),
    )

    // CR 702.73a: a Kindred instant with changeling is every creature type
    @Test
    fun cradleTrade() = card(
        name = "Cradle Trade",
        cost = "{2}{W}",
        type = "Kindred Instant — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Exile target creature. Its controller creates a 1/1 colorless Shapeshifter creature token with changeling.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        spell(move(target("creature"), Zone.Exile),
            Step.Do(Effect.CreateToken(shapeshifter, n(1), Players.ControllerOf(target("creature")))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun coveyPretender() = card(
        name = "Covey Pretender",
        cost = "{2}{W}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Flash
            Flying
            When this creature enters, return up to one other target creature you control to its owner's hand.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(another, creature, you), min = 0))),
    )

    // CR 400.7: the card returns as a new object
    @Test
    fun embodiment() = card(
        name = "Embodiment",
        cost = "{1}{W}",
        type = "Instant",
        text = "Exile target creature you control, then return that card to the battlefield under its owner's control. Create a 1/1 colorless Shapeshifter creature token with changeling. (It's every creature type.)",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
            create(shapeshifter),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun tombshifter() = card(
        name = "Tombshifter",
        cost = "{3}{B}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            When this creature enters, you may return target creature card from your graveyard to your hand.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        triggered(entersSelf, Step.May(listOf(move(target("card"), Zone.Hand)), player = Players.You), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun searingChangeling() = card(
        name = "Searing Changeling",
        cost = "{2}{R}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            When this creature dies, exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        triggered(diesSelf, move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
    )

    @Test
    fun gnashingChangeling() = card(
        name = "Gnashing Changeling",
        cost = "{2}{G}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            When this creature enters, destroy up to one target artifact or enchantment.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        triggered(entersSelf, destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment), min = 0))),
    )

    // CR 111.4: a token named Mutavault, as its reminder text describes it; CR 205.1b: still a land as it becomes a creature
    @Test
    fun shiftingWanderer() = card(
        name = "Shifting Wanderer",
        cost = "{2}{G}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            When this creature enters, create a tapped Mutavault token. (It's a land with "{T}: Add {C}" and "{1}: This token becomes a 2/2 creature with all creature types until end of turn. It's still a land.")
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Changeling,
        triggered(entersSelf, doing(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Mutavault", colorIndicator = emptyList(), types = listOf(CardType.Land),
            abilities = listOf(
                (activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))) as Ability.Activated)
                    .copy(text = "{T}: Add {C}."),
                (activated(manaCost("{1}"), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.AllCreatureTypes,
                    Mechanic.SetPowerToughness(n(2), n(2)))) as Ability.Activated)
                    .copy(text = "{1}: This token becomes a 2/2 creature with all creature types until end of turn. It's still a land.")))),
            n(1), Players.You, tapped = true))),
    )

    @Test
    fun carapaceClutchling() = card(
        name = "Carapace Clutchling",
        cost = "{3}{G/U}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Reach
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Reach,
    )

    @Test
    fun scrappyBarbling() = card(
        name = "Scrappy Barbling",
        cost = "{1}{R/W}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            During your turn, this creature has first strike.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Changeling,
        static(has(Keyword.FirstStrike), condition = arcanum.api.Condition.YourTurn),
    )

    @Test
    fun lankyTramper() = card(
        name = "Lanky Tramper",
        cost = "{2}{R/G}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Trample
        """,
        pt = "4/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Trample,
    )

    @Test
    fun impishCreepling() = card(
        name = "Impish Creepling",
        cost = "{1}{U/B}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Flash
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Flash,
    )

    @Test
    fun haughtyGorgeling() = card(
        name = "Haughty Gorgeling",
        cost = "{2}{W/B}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Lifelink
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Lifelink,
    )

    // CR 205.1b: it becomes an artifact creature, its own types kept
    @Test
    fun mossbrookHeart() = card(
        name = "Mossbrook Heart",
        cost = "{3}",
        type = "Kindred Artifact — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            {T}: Add one mana of any color.
            {4}: This artifact becomes a 4/4 artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(manaCost("{4}"), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
            Mechanic.SetPowerToughness(n(4), n(4)))),
    )

    @Test
    fun dripstoneDirk() = card(
        name = "Dripstone Dirk",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a 1/1 colorless Shapeshifter creature token with changeling. (It's every creature type.)
            Equipped creature gets +1/+1 and is all creature types.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(shapeshifter)),
        static(pt(1, 1), Mechanic.AllCreatureTypes, affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 707.9b, 614.1c: it may enter as a copy with changeling added
    @Test
    fun everyChangeling() = card(
        name = "Every-Changeling",
        cost = "{3}{U}{U}",
        type = "Creature — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            You may have this creature enter as a copy of any creature on the battlefield, except it has changeling.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Convoke,
        static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.EntersAsCopy(creature, listOf(CopyException.AddAbilities(listOf(Keyword.Changeling))),
            optional = true)))),
    )

    // CR 613.1d: no creature types until end of turn
    @Test
    fun facelessReversal() = card(
        name = "Faceless Reversal",
        cost = "{1}{B}",
        type = "Kindred Instant — Shapeshifter",
        text = """
            Changeling (This card is every creature type.)
            Target creature gets +3/-3 and loses all creature types until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        spell(pump(target("creature"), 3, -3, Mechanic.LoseAllCreatureTypes), targets = listOf(slot("creature", creature))),
    )

    private val entersEvent = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
}
