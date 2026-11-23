package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 03: Treasure tokens (CR 111.10a) made, counted and sacrificed. */
class SncChunk03TreasureTest {
    private val treasures = sub("Treasure")
    private val notYours: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))
    private val citizen = creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)
    private val yourTreasures = and(treasures, you)

    // CR 120.3 (damage to you from this creature), 111.10a; one amount: two for each Treasure counted
    @Test
    fun blackMarketTycoon() = card(
        name = "Back Alley Hoarder",
        cost = "{R}{G}",
        type = "Creature — Cat Rogue",
        pt = "2/2",
        text = """
            At the beginning of your upkeep, this creature deals 2 damage to you for each Treasure you control.
            {T}: Create a Treasure token.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            damage(Value.Times(count(yourTreasures), 2), Recipients(players = Players.You))),
        activated(cost(tap), createTreasure()),
    )

    // CR 603.12 (the reflexive trigger), 608.2h (the sacrificed creature's power as it last existed), 111.10a
    @Test
    fun ziatoraLike() = card(
        name = "Brazen Cinder Wyrm",
        cost = "{3}{B}{R}{G}",
        type = "Legendary Creature — Demon Dragon",
        pt = "6/6",
        text = """
            Flying
            At the beginning of your end step, you may sacrifice another creature. When you do, this creature deals damage equal to that creature's power to any target and you create three Treasure tokens.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Pay(cost(sacrificeA(and(another, creature), preBind = "sacrificed")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(anyTarget),
                steps = listOf(damage(power(Objects.Bound("sacrificed")), toAny), createTreasure(n(3))))))))),
    )

    // CR 118.8 (the discard is an additional cost), 111.10a
    @Test
    fun bigScore() = card(
        name = "Heist Windfall",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards and create two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2), createTreasure(n(2))),
    )

    // CR 113.3d (lands have the ability), 111.10a
    @Test
    fun bootleggersStash() = card(
        name = "Smugglers' Cellar Stock",
        cost = "{5}{G}",
        type = "Artifact",
        text = "Lands you control have \"{T}: Create a Treasure token.\"",
    ) parsesTo abilities(
        static(has(activated(cost(tap), createTreasure())), affects = Objects.Each(and(land, you))),
    )

    // CR 111.2 (the token's controller is the permanent's controller), 608.2h (as it last existed)
    @Test
    fun buyYourSilence() = card(
        name = "Hush Money Offer",
        cost = "{4}{W}",
        type = "Sorcery",
        text = """Exile target nonland permanent. Its controller creates a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Exile),
            Step.Do(Effect.CreateToken(treasure, n(1), Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 700.2 (modal), 111.10a
    @Test
    fun exhibitionMagician() = card(
        name = "Sideshow Conjurer",
        cost = "{2}{R}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            When this creature enters, choose one —
            • Create a 1/1 green and white Citizen creature token.
            • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(create(citizen)),
            mode(createTreasure()),
        ))),
    )

    // CR 111.10a, 602.1 (an activated ability with {T})
    @Test
    fun glittermonger() = card(
        name = "Gleam Peddler",
        cost = "{3}{G}",
        type = "Creature — Elf Rogue",
        pt = "1/4",
        text = """{T}: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
    ) parsesTo abilities(activated(cost(tap), createTreasure()))

    // CR 113.6m (works from the graveyard when exiling itself from there is the cost), 111.10a
    @Test
    fun haloScarab() = card(
        name = "Gilded Dung Beetle",
        cost = "{2}",
        type = "Artifact Creature — Insect",
        pt = "2/1",
        text = """{2}, Exile this card from your graveyard: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), createTreasure()),
    )

    // CR 111.10a; the token is created tapped (CR 111.5)
    @Test
    fun ognis() = card(
        name = "Ashmane Firebrand",
        cost = "{1}{B/R}{R}{R/G}",
        type = "Legendary Creature — Lizard Warrior",
        pt = "3/3",
        text = """
            Haste
            Whenever a creature you control with haste attacks, create a tapped Treasure token.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(on(EventPattern.Attack(and(creature, you, ObjectFilter.HasKeyword(Keyword.Haste)))), createTreasure(tapped = true)),
    )

    // CR 701.14a (fight), 111.10a
    @Test
    fun prizefight() = card(
        name = "Backroom Scuffle",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
            Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Fight(target("creature"), target("second creature"))),
            createTreasure(),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, notYours))),
        ),
    )

    // CR 603.2c (one trigger for all the creatures), 510.2, 111.10a; CR 701.21a (sacrificing a Treasure is a cost), 406.1, 608.2
    @Test
    fun professionalFaceBreaker() = card(
        name = "Knuckle Fixer",
        cost = "{2}{R}",
        type = "Creature — Human Warrior",
        pt = "2/3",
        text = """
            Menace
            Whenever one or more creatures you control deal combat damage to a player, create a Treasure token.
            Sacrifice a Treasure: Exile the top card of your library. You may play that card this turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)),
                oneOrMore = true,
                batchPerPlayer = true,
            ),
            instructions = Instructions(steps = listOf(createTreasure())),
        ),
        activated(cost(sacrificeA(treasures)),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
    )

    // CR 111.10a, 701.21a (sacrificing a Treasure as the cost)
    @Test
    fun stimulusPackage() = card(
        name = "Windfall Fund",
        cost = "{2}{R}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
            Sacrifice a Treasure: Create a 1/1 green and white Citizen creature token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createTreasure(n(2))),
        activated(cost(sacrificeA(treasures)), create(citizen)),
    )
}
