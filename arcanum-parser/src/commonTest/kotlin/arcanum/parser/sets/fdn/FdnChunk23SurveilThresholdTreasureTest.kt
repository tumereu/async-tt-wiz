package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** The FDN cards with surveil, threshold, Treasure and mana abilities (chunk 23). The invented names carry the oracle text of the real cards. */
class FdnChunk23SurveilThresholdTreasureTest {
    private fun surveil(count: Int): Step = Step.Do(Effect.Surveil(Players.You, n(count)))
    private fun threshold(vararg mechanics: Mechanic) =
        Ability.Static(mechanics.toList(), Affected.Objects(Objects.Self), condition = sevenInGraveyard, abilityWord = "Threshold")
    private val sevenInGraveyard = Condition.Exists(inYourGraveyard(), atLeast = 7)
    private val anyColor = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))
    private val addColorless = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))

    // CR 701.25a, 603.6a, 509.1b: surveil; CR 702.xx threshold is an ability word (CR 207.2c), the static is conditional
    @Test
    fun cephalidSeer() = card(
        name = "Cephalid Seer",
        cost = "{2}{U}",
        type = "Creature — Octopus Wizard",
        pt = "2/2",
        text = """
            When this creature enters, surveil 3. (Look at the top three cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            Threshold — This creature can't be blocked as long as there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        whenThisEnters(surveil(3)),
        threshold(Mechanic.CantBeBlocked),
    )

    // CR 701.25a, 602.1: a tap cost
    @Test
    fun runeBoundWall() = card(
        name = "Rune-Bound Wall",
        cost = "{2}{U}",
        type = "Artifact Creature — Wall",
        pt = "0/6",
        text = """
            Defender
            {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), surveil(1)),
    )

    // CR 400.3: the owner chooses; CR 701.25a
    @Test
    fun unmappedVoyage() = card(
        name = "Unmapped Voyage",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            Target creature's owner puts it on their choice of the top or bottom of their library.
            Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("creature"))),
            Step.If(Condition.Chose("position", "top"), then = listOf(move(target("creature"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
            surveil(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 603.2: the first time each turn is the first event of the turn
    @Test
    fun vanguardSeer() = card(
        name = "Vanguard Seer",
        cost = "{3}{W}",
        type = "Creature — Angel Warrior",
        pt = "3/3",
        text = """
            Flying
            Whenever you gain life for the first time each turn, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.GainLife(PlayerFilter.You)), nthThisTurn = 1), surveil(1)),
    )

    // CR 207.2c, 603.4: an intervening if, checked on attacking and on resolution
    @Test
    fun cryptEater() = card(
        name = "Crypt Eater",
        cost = "{3}{B}",
        type = "Creature — Zombie",
        pt = "3/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Threshold — Whenever this creature attacks, if there are seven or more cards in your graveyard, this creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, pump(Objects.Self, 2, 0), interveningIf = sevenInGraveyard, abilityWord = "Threshold"),
    )

    // CR 205.1: the two keywords; CR 207.2c, 604.1: both modifications follow one condition
    @Test
    fun dreadwingForager() = card(
        name = "Dreadwing Forager",
        cost = "{1}{U}{B}",
        type = "Creature — Nightmare Bird",
        pt = "2/2",
        text = """
            Flying
            Whenever this creature enters or attacks, draw a card, then discard a card.
            Threshold — This creature gets +1/+1 and has deathtouch as long as there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersOrAttacksSelf, draw(1), discard(1)),
        threshold(pt(1, 1), has(Keyword.Deathtouch)),
    )

    // CR 111.4: a named legendary token; CR 603.5: "you may"
    @Test
    fun cioraTheRisingTide() = card(
        name = "Ciora, the Rising Tide",
        cost = "{2}{U}",
        type = "Legendary Creature — Merfolk Noble",
        pt = "3/2",
        text = """
            When Ciora enters, draw two cards, then discard two cards.
            Threshold — Whenever Ciora attacks, if there are seven or more cards in your graveyard, you may create Scion of the Deep, a legendary 8/8 blue Octopus creature token.
        """,
    ) parsesTo abilities(
        whenThisEnters(draw(2), discard(2)),
        triggered(attacksSelf, Step.May(listOf(create(scion)), player = Players.You), interveningIf = sevenInGraveyard, abilityWord = "Threshold"),
    )

    private val scion = (creatureToken("Octopus", listOf(Color.Blue), 8, 8, name = "Scion of the Deep") as TokenSpec.Defined)
        .let { TokenSpec.Defined(it.face.copy(supertypes = listOf(Supertype.Legendary))) }

    // CR 111.10b: Treasure; CR 701.6a: counter; the spell's controller is "its controller"
    @Test
    fun anOfferYouMustRefuse() = card(
        name = "An Offer You Must Refuse",
        cost = "{U}",
        type = "Instant",
        text = """
            Counter target noncreature spell. Its controller creates two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Counter(target("spell"))),
            Step.Do(Effect.CreateToken(treasure, n(2), Players.ControllerOf(target("spell")))),
            targets = listOf(slot("spell", and(noncreatureSpell, spellFilter))),
        ),
    )

    // CR 111.10b, 107.3: a count of lands
    @Test
    fun brassesBounty() = card(
        name = "Brasses' Bounty",
        cost = "{6}{R}",
        type = "Sorcery",
        text = """
            For each land you control, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(createTreasure(count(and(land, you)))),
    )

    // CR 111.10b, 613.4c: a lord that leaves out itself
    @Test
    fun corsairLieutenant() = card(
        name = "Corsair Lieutenant",
        cost = "{2}{U}",
        type = "Creature — Human Pirate",
        pt = "2/2",
        text = """
            When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Other Pirates you control get +1/+1.
        """,
    ) parsesTo abilities(
        whenThisEnters(createTreasure()),
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Pirate"), you))),
    )

    // CR 113.1c, 603.7: the granted ability is a quoted dies trigger; CR 111.10b
    @Test
    fun fakeYourOwnBirth() = card(
        name = "Fake Your Own Birth",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Until end of turn, target creature gets +2/+0 and gains "When this creature dies, return it to the battlefield tapped under its owner's control and you create a Treasure token." (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(2, 0), has(
                triggered(diesSelf, move(Objects.EventObject(arcanum.api.EventRole.After), Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.EventObject(arcanum.api.EventRole.After))), createTreasure()))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 701.12 (control), 302.6, 702.10: untap and haste; CR 111.10b
    @Test
    fun involuntaryServitude() = card(
        name = "Involuntary Servitude",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            createTreasure(),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 111.10b: two tokens
    @Test
    fun rapaciousWyrm() = card(
        name = "Rapacious Wyrm",
        cost = "{4}{R}",
        type = "Creature — Dragon",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, create two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(createTreasure(n(2))),
    )

    // CR 118.8: the additional cost is a discard; CR 111.10b
    @Test
    fun seizeTheLoot() = card(
        name = "Seize the Loot",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2), createTreasure()),
    )

    // CR 602.5b: an activation restriction on the count of lands
    @Test
    fun crypticGrotto() = card(
        name = "Cryptic Grotto",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}, Sacrifice this land: Draw a card. Activate only if you control five or more lands.
        """,
    ) parsesTo abilities(
        addColorless,
        Ability.Activated(cost(mana("{1}"), tap, sacrificeSelf), Instructions(steps = listOf(draw(1))),
            anyTime.copy(condition = Condition.Exists(and(land, you), atLeast = 5))),
    )

    // CR 702.122a: crew; CR 106.1b: any color
    @Test
    fun cultivatorsWagon() = card(
        name = "Cultivator's Wagon",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            {T}: Add one mana of any color.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), anyColor),
        Keyword.Crew(3),
    )

    // CR 106.1a: two colourless mana; CR 602.2: a sacrifice cost
    @Test
    fun hedronLibrary() = card(
        name = "Hedron Library",
        cost = "{4}",
        type = "Artifact",
        text = """
            {T}: Add {C}{C}.
            {2}, {T}, Sacrifice this artifact: Draw two cards.
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You))),
        activated(cost(mana("{2}"), tap, sacrificeSelf), draw(2)),
    )

    // CR 506.4, 509.1b: unblockable this turn; the mana ability and the activated ability are separate
    @Test
    fun rogueTrail() = card(
        name = "Rogue's Trail",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}, {T}: Target creature can't be blocked this turn.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{4}"), tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
    )

    // CR 106.4a, 106.4c: mana added on an attack trigger, kept until end of turn
    @Test
    fun savageVentwing() = card(
        name = "Savage Ventwing",
        cost = "{4}{R}{G}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying
            Whenever this creature attacks, add {R}{R}{R}{G}{G}{G}. Until end of turn, you don't lose this mana as steps and phases end.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}{R}{G}{G}{G}")!!), keptUntil = Duration.EndOfTurn), Players.You))),
    )
}
