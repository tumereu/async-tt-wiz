package arcanum.parser.sets.mom

import arcanum.api.*
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/** MOM chunk 14: transforming double-faced cards and Sieges, part 3 (CR 701.27, 712, 310). */
class MomChunk14TransformTest {
    private fun siege(name: String, cost: String, text: String, defense: String) =
        OracleCard(name, cost, "Battle — Siege", siegeReminder + "\n" + text.trimIndent(), defense = defense)

    private val zombie = creatureToken("Zombie", listOf(Color.Black), 2, 2)
    private fun discardThen(vararg then: Step) = arrayOf(
        Step.Pay(cost = cost(discardACard), bind = "paid", payer = Players.You),
        Step.If(condition = Condition.Did("paid"), then = then.toList()),
    )
    private fun searchToHand(filter: ObjectFilter) = arrayOf(search(filter, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle)
    private fun hydraToken(keyword: Keyword) = creatureToken("Phyrexian Hydra", listOf(Color.Green, Color.White), 3, 3, abilities = listOf(keyword))
    private val anyPlayerLands = count(and(land, you))
    private fun transformsInto(name: String): Trigger = on(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named(name))))
    private val anySpell: Trigger = youCast(ObjectFilter.Any)

    // CR 701.9, 701.23; CR 608.2c: "Then you may discard" follows the Treasure; "If you do" refers to that discard
    @Test
    fun invasionOfQuelmar() = cardFaces(Layout.Transform,
        siege("Invasion of Quelmar", "{R}{G}", "When this Siege enters, create a Treasure token. Then you may discard a card. If you do, draw a card.", "5") to abilities(
            triggered(entersSelf, createTreasure(), *discardThen(draw(1))),
        ),
        card(
            name = "Hornrunner Brute",
            cost = null,
            type = "Creature — Rhino",
            text = """
                Trample
                When this creature enters, you may discard a card. If you do, search your library for a land or battle card, reveal it, put it into your hand, then shuffle.
            """,
            pt = "3/4",
            colorIndicator = "RG",
        ) to abilities(
            Keyword.Trample,
            triggered(entersSelf, *discardThen(*searchToHand(or(land, battle)))),
        ),
    )

    // CR 701.34 (Siege), 115.1: the same shapes as Invasion of the Moors
    @Test
    fun invasionOfSevrath() = cardFaces(Layout.Transform,
        siege("Invasion of Sevrath", "{2}{B}{B}", """
            Flash
            When this Siege enters, target creature an opponent controls gets -13/-13 until end of turn.
        """, "5") to abilities(
            Keyword.Flash,
            triggered(entersSelf, untilEndOfTurn(target("creature"), pt(-13, -13)), targets = listOf(slot("creature", and(creature, opponents)))),
        ),
        card(
            name = "Tide of the Departed",
            cost = null,
            type = "Enchantment",
            text = """
                When this enchantment enters, create two 2/2 black Zombie creature tokens.
                {2}{B}: Exile target card from a graveyard. If it was a creature card, create a 2/2 black Zombie creature token.
            """,
            colorIndicator = "B",
        ) to abilities(
            triggered(entersSelf, create(zombie, count = n(2))),
            activated(manaCost("{2}{B}"), Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
                ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), creature), create(zombie)),
                targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        ),
    )

    // CR 701.20 (look), 701.20a, 400.7: a permanent card is a card that could be on the battlefield
    @Test
    fun invasionOfPalmora() = cardFaces(Layout.Transform,
        siege("Invasion of Palmora", "{1}{G}", "When this Siege enters, look at the top five cards of your library. You may reveal a permanent card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.", "4") to abilities(
            triggered(entersSelf,
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        ),
        card(
            name = "Frenzied Lizardon",
            cost = null,
            type = "Creature — Dinosaur",
            text = """
                Trample
                Whenever you cast a spell, this creature gains indestructible until end of turn.
            """,
            pt = "4/3",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Trample,
            triggered(anySpell, gains(Objects.Self, Keyword.Indestructible)),
        ),
    )

    // CR 120.3 (damage), 702.21 (ward): "each creature and each planeswalker" includes this Siege's controller's permanents
    @Test
    fun invasionOfKolwen() = cardFaces(Layout.Transform,
        siege("Invasion of Kolwen", "{2}{R}{R}", "When this Siege enters, it deals 3 damage to each creature and each planeswalker.", "4") to abilities(
            triggered(entersSelf, damage(3, toObjects(Objects.Each(or(creature, ObjectFilter.Type(CardType.Planeswalker)))))),
        ),
        card(
            name = "Cinder Elemental Lord",
            cost = null,
            type = "Creature — Elemental",
            text = """
                Ward—Pay 2 life.
                Whenever you cast a spell, this creature deals 2 damage to each opponent.
            """,
            pt = "4/4",
            colorIndicator = "R",
        ) to abilities(
            Keyword.Ward(cost(CostPart.PayLife(n(2)))),
            triggered(anySpell, damage(2, eachOpponent)),
        ),
    )

    // CR 208.3 and 604.3: a characteristic-defining ability sets power and toughness; X is the lands counted on resolution
    @Test
    fun invasionOfGlenmoor() = cardFaces(Layout.Transform,
        siege("Invasion of Glenmoor", "{4}{B}{G}", "When this Siege enters, destroy target non-Elf creature an opponent controls with power X or less, where X is the number of lands you control.", "5") to abilities(
            triggered(entersSelf, destroy(target("creature")),
                targets = listOf(slot("creature", and(not(sub("Elf")), creature, opponents, ObjectFilter.Power(Comparison.AtMost, anyPlayerLands))))),
        ),
        card(
            name = "Thornhide Warden",
            cost = null,
            type = "Creature — Elf Warrior",
            text = "Thornhide Warden's power and toughness are each equal to the number of lands you control.",
            pt = "*/*",
            colorIndicator = "BG",
        ) to abilities(
            static(Mechanic.SetPowerToughness(power = anyPlayerLands, toughness = anyPlayerLands), characteristicDefining = true),
        ),
    )

    @Test
    fun invasionOfBrasmere() = cardFaces(Layout.Transform,
        siege("Invasion of Brasmere", "{1}{R}", "When this Siege enters, you may discard a card. If you do, draw two cards.", "4") to abilities(
            triggered(entersSelf, *discardThen(draw(2))),
        ),
        card(
            name = "Cindertail Spellshaper",
            cost = null,
            type = "Creature — Goblin Spellshaper",
            text = "{2}{R}, {T}, Discard a card: Create two 1/1 blue and red Elemental creature tokens. Creatures you control get +1/+0 and gain haste until end of turn.",
            pt = "3/3",
            colorIndicator = "R",
        ) to abilities(
            activated(cost(mana("{2}{R}"), tap, discardACard),
                create(creatureToken("Elemental", listOf(Color.Blue, Color.Red), 1, 1), count = n(2)),
                pump(Objects.Each(creatureYouControl), 1, 0, has(Keyword.Haste))),
        ),
    )

    @Test
    fun invasionOfMoag() = cardFaces(Layout.Transform,
        siege("Invasion of Moag", "{2}{G}{W}", "When this Siege enters, put a +1/+1 counter on each creature you control.", "5") to abilities(
            triggered(entersSelf, counters(Objects.Each(creatureYouControl))),
        ),
        card(
            name = "Bloomwielder Dryads",
            cost = null,
            type = "Creature — Dryad",
            text = """
                Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
                At the beginning of your end step, put a +1/+1 counter on target creature you control.
            """,
            pt = "3/3",
            colorIndicator = "GW",
        ) to abilities(
            Keyword.Ward(manaCost("{2}")),
            triggered(yourEndStep, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
        ),
    )

    // CR 702.51 (convoke): granted to spells; CR 701.26 (untap): "up to four" targets
    @Test
    fun invasionOfTidewick() = cardFaces(Layout.Transform,
        siege("Invasion of Tidewick", "{2}{U}", "When this Siege enters, create two 1/1 blue Kraken creature tokens with trample.", "4") to abilities(
            triggered(entersSelf, create(creatureToken("Kraken", listOf(Color.Blue), 1, 1, abilities = listOf(Keyword.Trample)), count = n(2))),
        ),
        card(
            name = "Caeran, Sea Warden",
            cost = null,
            type = "Legendary Creature — Serpent",
            text = """
                Noncreature spells you cast have convoke. (Your creatures can help cast those spells. Each creature you tap while casting a noncreature spell pays for {1} or one mana of that creature's color.)
                At the beginning of your end step, untap up to four target creatures.
            """,
            pt = "3/3",
            colorIndicator = "U",
        ) to abilities(
            static(has(Keyword.Convoke), affects = Objects.Each(and(not(creature), spellFilter, you))),
            triggered(yourEndStep, untapIt(target("creature")), targets = listOf(slot("creature", creature, min = 0, max = 4))),
        ),
    )

    @Test
    fun invasionOfHalvor() = cardFaces(Layout.Transform,
        siege("Invasion of Halvor", "{3}{G}{G}", "When this Siege enters, return up to three target permanent cards from your graveyard to your hand.", "4") to abilities(
            triggered(entersSelf, move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(permanentCard), min = 0, max = 3))),
        ),
        card(
            name = "Wild Surge",
            cost = null,
            type = "Enchantment",
            text = "At the beginning of your upkeep, you may put a permanent card from your hand onto the battlefield.",
            colorIndicator = "G",
        ) to abilities(
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
                Step.Choose(Choice.Objects(and(permanentCard, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                    bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield)),
        ),
    )

    // CR 702.15 (lifelink), 702.12 (indestructible), 613.1f: granted only as long as three other enchantments are controlled
    @Test
    fun invasionOfThessar() = cardFaces(Layout.Transform,
        siege("Invasion of Thessar", "{2}{W}", "When this Siege enters, search your library for an Aura, God, or Demigod card, reveal it, put it into your hand, then shuffle.", "4") to abilities(
            triggered(entersSelf, *searchToHand(or(sub("Aura"), sub("God"), sub("Demigod")))),
        ),
        card(
            name = "Aphra, Ever-Sheltered",
            cost = null,
            type = "Legendary Enchantment Creature — God",
            text = """
                Aphra has lifelink and indestructible as long as you control at least three other enchantments.
                Whenever another enchantment you control enters, draw a card.
            """,
            pt = "4/4",
            colorIndicator = "W",
        ) to abilities(
            static(has(Keyword.Lifelink, Keyword.Indestructible), condition = Condition.Exists(and(another, enchantment, you), atLeast = 3)),
            triggered(enters(and(another, enchantment, you)), draw(1)),
        ),
    )

    @Test
    fun invasionOfVolkav() = cardFaces(Layout.Transform,
        siege("Invasion of Volkav", "{3}{W}{B}", "When this Siege enters, return target nonbattle permanent card from your graveyard to the battlefield.", "5") to abilities(
            triggered(entersSelf, move(target("card"), Zone.Battlefield),
                targets = listOf(slot("card", and(not(battle), permanentCard, inYourGraveyard())))),
        ),
        card(
            name = "The Shattered Dawn",
            cost = null,
            type = "Enchantment",
            text = """
                Creature tokens you control get +1/+0 and have lifelink.
                At the beginning of your end step, create a 1/1 white and black Spirit creature token with flying.
            """,
            colorIndicator = "WB",
        ) to abilities(
            static(pt(1, 0), has(Keyword.Lifelink), affects = Objects.Each(and(creature, token, you))),
            triggered(yourEndStep, create(creatureToken("Spirit", listOf(Color.White, Color.Black), 1, 1, abilities = listOf(Keyword.Flying)))),
        ),
    )

    // CR 707.10 (copying a spell), 115.7 (new targets): "Copy target spell you control" with new targets
    @Test
    fun invasionOfWrenn() = cardFaces(Layout.Transform,
        siege("Invasion of Wrenn", "{3}{U}", "When this Siege enters, draw three cards, then discard a card.", "4") to abilities(
            triggered(entersSelf, draw(3), discard(1)),
        ),
        card(
            name = "Overcharged Mage-Ring",
            cost = null,
            type = "Artifact",
            text = "{1}, {T}, Sacrifice this artifact: Copy target spell you control. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)",
            colorIndicator = "U",
        ) to abilities(
            activated(cost(mana("{1}"), tap, CostPart.Sacrifice(CostObjects.Self)),
                Step.Do(Effect.CopySpell(target("spell"), controller = Players.You, newTargets = true)),
                targets = listOf(slot("spell", and(spellFilter, you)))),
        ),
    )

    @Test
    fun invasionOfZorex() = cardFaces(Layout.Transform,
        siege("Invasion of Zorex", "{2}{W}{U}", "When this Siege enters, return up to one target creature to its owner's hand.", "4") to abilities(
            triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature, min = 0))),
        ),
        card(
            name = "Apex Paladin",
            cost = null,
            type = "Creature — Angel Knight",
            text = """
                Flying
                Apex Paladin's power and toughness are each equal to the number of creatures you control.
            """,
            pt = "*/*",
            colorIndicator = "WU",
        ) to abilities(
            Keyword.Flying,
            static(Mechanic.SetPowerToughness(power = count(creatureYouControl), toughness = count(creatureYouControl)), characteristicDefining = true),
        ),
    )

    // CR 701.27a, 702.15 (lifelink), 702.17 (reach): a {W/P} cost part is paid with {W} or 2 life (CR 107.4f)
    @Test
    fun gorgonaxReborn() = cardFaces(Layout.Transform,
        card(
            name = "Gorgonax Reborn",
            cost = "{G}{G}{G}",
            type = "Legendary Creature — Hydra",
            text = """
                Reach
                {6}{W/P}: Transform Gorgonax Reborn. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)
            """,
            pt = "4/5",
        ) to abilities(Keyword.Reach, transformAsSorcery("{6}{W/P}")),
        card(
            name = "Gorgonax, Engine of Doom",
            cost = null,
            type = "Legendary Creature — Phyrexian Hydra",
            text = """
                Reach, lifelink
                Whenever Gorgonax or another nontoken Hydra you control dies, create a 3/3 green and white Phyrexian Hydra creature token with reach and a 3/3 green and white Phyrexian Hydra creature token with lifelink.
            """,
            pt = "6/6",
            colorIndicator = "GW",
        ) to abilities(
            Keyword.Reach,
            Keyword.Lifelink,
            triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, not(token), sub("Hydra"), you))),
                create(hydraToken(Keyword.Reach)), create(hydraToken(Keyword.Lifelink))),
        ),
    )

    // CR 701.21 (sacrifice), 101.4: each opponent chooses their own sacrifice
    @Test
    fun sulfuricPrankster() = cardFaces(Layout.Transform,
        card(
            name = "Sulfuric Prankster",
            cost = "{1}{R}",
            type = "Creature — Devil",
            text = "{3}{B/P}: Transform this creature. Activate only as a sorcery. ({B/P} can be paid with either {B} or 2 life.)",
            pt = "2/1",
        ) to abilities(transformAsSorcery("{3}{B/P}")),
        card(
            name = "Gleaming Gorehound",
            cost = null,
            type = "Creature — Phyrexian Devil",
            text = "When this creature dies, each opponent sacrifices an artifact or creature of their choice.",
            pt = "3/2",
            colorIndicator = "RB",
        ) to abilities(
            triggered(diesSelf, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(or(artifact, creature), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
        ),
    )

    @Test
    fun seraphOfOldTarkir() = cardFaces(Layout.Transform,
        card(
            name = "Seraph of Old Tarkir",
            cost = "{2}{W}",
            type = "Creature — Angel Soldier",
            text = """
                Flying
                {4}{B/P}: Transform this creature. Activate only as a sorcery. ({B/P} can be paid with either {B} or 2 life.)
            """,
            pt = "2/2",
        ) to abilities(Keyword.Flying, transformAsSorcery("{4}{B/P}")),
        card(
            name = "Seraph of New Tarkir",
            cost = null,
            type = "Creature — Phyrexian Angel",
            text = """
                Flying
                Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, this creature gets +2/+1 until end of turn.
            """,
            pt = "3/3",
            colorIndicator = "WB",
        ) to abilities(
            Keyword.Flying,
            triggered(attacksSelf,
                Step.Pay(cost = cost(sacrificeA(and(another, or(creature, artifact)))), bind = "paid", payer = Players.You),
                ifThen(Condition.Did("paid"), pump(Objects.Self, 2, 1))),
        ),
    )

    // CR 701.27: "transforms into" triggers on the face-down-to-up change; CR 700.4 is not involved
    @Test
    fun cliffholdAerialist() = cardFaces(Layout.Transform,
        card(
            name = "Cliffhold Aerialist",
            cost = "{1}{U}",
            type = "Creature — Merfolk Scout",
            text = """
                Flying
                {4}{G/P}: Transform this creature. Activate only as a sorcery. ({G/P} can be paid with either {G} or 2 life.)
            """,
            pt = "2/1",
        ) to abilities(Keyword.Flying, transformAsSorcery("{4}{G/P}")),
        card(
            name = "Cliffhold Invader",
            cost = null,
            type = "Creature — Phyrexian Merfolk Scout",
            text = """
                Flying
                When this creature transforms into Cliffhold Invader, look at the top card of your library. If it's a land card, you may put it onto the battlefield. If you don't put the card onto the battlefield, put it into your hand.
            """,
            pt = "2/4",
            colorIndicator = "UG",
        ) to abilities(
            Keyword.Flying,
            triggered(transformsInto("Cliffhold Invader"),
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "seen")),
                ifThen(Condition.ObjectMatches(Objects.Bound("seen"), land),
                    Step.Pay(steps = listOf(move(Objects.Bound("seen"), Zone.Battlefield)), payer = Players.You, bind = "put")),
                ifThen(Condition.Not(Condition.Did("put")), move(Objects.Bound("seen"), Zone.Hand))),
        ),
    )

    @Test
    fun dunesShaper() = cardFaces(Layout.Transform,
        card(
            name = "Dunes Shaper",
            cost = "{W}",
            type = "Creature — Dog Warrior",
            text = "{4}{G/P}: Transform this creature. Activate only as a sorcery. ({G/P} can be paid with either {G} or 2 life.)",
            pt = "1/2",
        ) to abilities(transformAsSorcery("{4}{G/P}")),
        card(
            name = "Burnished Duneclaw",
            cost = null,
            type = "Creature — Phyrexian Dog Warrior",
            text = "Trample",
            pt = "4/3",
            colorIndicator = "WG",
        ) to abilities(Keyword.Trample),
    )
}
