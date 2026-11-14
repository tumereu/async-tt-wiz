package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 12: token creation (Rats that can't block, Birds, Beasts, Mice) and the cards around it. */
class WoeChunk12CreateTokenTest {
    private val rat = creatureToken("Rat", listOf(Color.Black), 1, 1, abilities = listOf(Ability.Static(
        listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))))
    private val enchantmentLeavesToGraveyard = on(EventPattern.ZoneChange(and(enchantment, you), from = Zone.Battlefield, to = Zone.Graveyard))

    private fun dies(filter: ObjectFilter) = on(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))
    private val exileTop: Step =
        Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled"))
    private val mayPlayExiled: Step = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))

    // CR 111.4 (token with a quoted ability), 509.1b (can't block), 702.111 (menace)
    @Test
    fun hedgewallPack() = card(
        name = "Hedgewall Pack",
        cost = "{3}{R}",
        type = "Creature — Dog",
        pt = "3/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, create a 1/1 black Rat creature token with "This token can't block."
        """,
    ) parsesTo abilities(Keyword.Menace, triggered(entersSelf, create(rat)))

    // CR 111.4, 509.1b, 700.4 (dies)
    @Test
    fun harriedSpearman() = card(
        name = "Harried Spearman",
        cost = "{R}",
        type = "Creature — Human Soldier",
        pt = "1/1",
        text = """
            Haste
            When this creature dies, create a 1/1 black Rat creature token with "This token can't block."
        """,
    ) parsesTo abilities(Keyword.Haste, triggered(diesSelf, create(rat)))

    // CR 700.4 ("put into a graveyard from the battlefield" is not only dying creatures)
    @Test
    fun knightOfLarks() = card(
        name = "Knight of Larks",
        cost = "{2}{W}",
        type = "Creature — Human Knight",
        pt = "1/3",
        text = "Whenever an enchantment you control is put into a graveyard from the battlefield, create a 1/1 white Bird creature token with flying.",
    ) parsesTo abilities(triggered(enchantmentLeavesToGraveyard,
        create(creatureToken("Bird", listOf(Color.White), 1, 1, listOf(Keyword.Flying)))))

    // CR 603.4 (intervening "if" is not used: "Then if" is checked on resolution, 608.2c), 509.1b, 702.111
    @Test
    fun ogreChatterlord() = card(
        name = "Ogre Chatterlord",
        cost = "{4}{R}{R}",
        type = "Creature — Ogre Warrior",
        pt = "6/5",
        text = """
            Menace
            Whenever this creature enters or attacks, create two 1/1 black Rat creature tokens with "This token can't block." Then if you control five or more Rats, each Rat you control gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Menace, triggered(entersOrAttacksSelf,
        create(rat, n(2)),
        ifThen(Condition.Exists(and(sub("Rat"), you), atLeast = 5), pump(Objects.Each(and(sub("Rat"), you)), 2, 0))))

    // CR 111.4, 509.1b, 701.21 (sacrifice), 603.12 ("If you do"), 614 (the upkeep trigger shares the zone with the enters trigger)
    @Test
    fun redcapGutterDigger() = card(
        name = "Redcap Gutter-Digger",
        cost = "{2}{R}{R}",
        type = "Creature — Goblin Warrior",
        pt = "3/3",
        text = """
            Menace
            When this creature enters, create two 1/1 black Rat creature tokens with "This token can't block."
            At the beginning of your upkeep, you may sacrifice another creature. If you do, put a +1/+1 counter on this creature and exile the top card of your library. You may play that card this turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, create(rat, n(2))),
        triggered(yourUpkeep,
            Step.Pay(cost = cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self), exileTop)),
            mayPlayExiled),
    )

    // CR 107.3 (X), 111.4, 509.1b, 702.10 (haste)
    @Test
    fun songOfTotenwalz() = card(
        name = "Song of Totenwalz",
        cost = "{X}{R}",
        type = "Sorcery",
        text = "Create X 1/1 black Rat creature tokens with \"This token can't block.\" Creatures you control gain haste until end of turn.",
    ) parsesTo abilities(spell(
        create(rat, Value.Announced("X")),
        gains(Objects.Each(creatureYouControl), Keyword.Haste),
        announced = listOf("X")))

    // CR 714.2b (chapters), 111.2, 608.2c
    @Test
    fun theHuntsmansReward() = card(
        name = "The Huntsman's Reward",
        cost = "{2}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create a 3/3 green Beast creature token.
            II — You may sacrifice a creature. If you do, search your library for a creature or basic land card, reveal it, put it into your hand, then shuffle.
            III — Up to two target creatures each get +2/+2 and gain trample until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, create(creatureToken("Beast", listOf(Color.Green), 3, 3))),
        chapter(2,
            Step.Pay(cost = cost(sacrificeA(creature)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                search(or(creature, basicLand()), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle))),
        chapter(3, pump(target("creatures"), 2, 2, has(Keyword.Trample)), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 714.2b, 111.2, 707.2 (token copy), 611.2a
    @Test
    fun threeBlindMoles() = card(
        name = "Three Blind Moles",
        cost = "{2}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Create a 1/1 white Mole creature token.
            II, III — Create a token that's a copy of target token you control.
            IV — Creatures you control get +1/+1 and gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, create(creatureToken("Mole", listOf(Color.White), 1, 1))),
        chapters(listOf(2, 3), create(TokenSpec.CopyOf(target("token"))), targets = listOf(slot("token", and(token, you)))),
        chapter(4, pump(Objects.Each(creatureYouControl), 1, 1, has(Keyword.Vigilance))),
    )

    // CR 700.4, 111.4, 509.1b, 702.2 (deathtouch)
    @Test
    fun totenwalzSwarmPiper() = card(
        name = "Totenwalz, Swarm Piper",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Human Warlock Bard",
        pt = "2/3",
        text = """
            Whenever Totenwalz or another nontoken creature you control dies, create a 1/1 black Rat creature token with "This token can't block."
            {1}{B}: Target attacking Rat you control gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, not(token), creature, you)),
            from = Zone.Battlefield, to = Zone.Graveyard)), create(rat)),
        activated(cost(mana("{1}{B}")), gains(target("Rat"), Keyword.Deathtouch),
            targets = listOf(slot("Rat", and(attacking, sub("Rat"), you)))),
    )

    // CR 111.4, 509.1b, 700.4 (dies), 122.1 (+1/+1 counter)
    @Test
    fun voraciousVole() = card(
        name = "Voracious Vole",
        cost = "{2}{B}",
        type = "Creature — Rat",
        pt = "2/1",
        text = """
            When this creature enters, create a 1/1 black Rat creature token with "This token can't block."
            Whenever another creature you control dies, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(rat)),
        triggered(dies(and(another, creature, you)), counters(Objects.Self)),
    )

    // CR 700.4, 111.4, 509.1b, 702.2
    @Test
    fun warehouseTabbit() = card(
        name = "Warehouse Tabbit",
        cost = "{B}",
        type = "Creature — Cat",
        pt = "1/1",
        text = """
            Whenever an enchantment you control is put into a graveyard from the battlefield, create a 1/1 black Rat creature token with "This token can't block."
            {1}{B}: This creature gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        triggered(enchantmentLeavesToGraveyard, create(rat)),
        activated(cost(mana("{1}{B}")), gains(Objects.Self, Keyword.Deathtouch)),
    )
}
