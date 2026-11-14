package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 15: look at the top cards, lose life, +N/+N for a class, and moving cards between zones. */
class WoeChunk15LookLoseLifeModifyPtMoveTest {
    private val bottomAnyOrder = Destination(Zone.Library, libraryPosition = -1)
    private val cardInAGraveyard = and(cardFilter, inZone(Zone.Graveyard))
    private val leavesSelf = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))
    private fun look(n: Int) = doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(n), fromTop = true), Players.You, bind = "seen"))

    // CR 701.20 (look), 701.16 (reveal), 401.4 (bottom of library in any order)
    @Test
    fun communeWithNature() = card(
        name = "Thistle Whisperer",
        cost = "{G}",
        type = "Sorcery",
        text = "Look at the top five cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        spell(
            look(5),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Library, position = -1),
        ),
    )

    // CR 701.20 (look), 401.4
    @Test
    fun sleightOfHand() = card(
        name = "Mossbrook Cartographer",
        cost = "{U}",
        type = "Sorcery",
        text = "Look at the top two cards of your library. Put one of them into your hand and the other on the bottom of your library.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomAnyOrder)),
        ),
    )

    // CR 603.2 (enters trigger), 119.3 (lose life), 102.2 (each opponent)
    @Test
    fun obyraDreamingDuelist() = card(
        name = "Sorrel Duelist",
        cost = "{U}{B}",
        type = "Legendary Creature — Faerie Warrior",
        text = """
            Flash
            Flying
            Whenever another Faerie you control enters, each opponent loses 1 life.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(enters(and(another, sub("Faerie"), you)), loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    // CR 700.4 (put into a graveyard from the battlefield), 119.3
    @Test
    fun wickedVisitor() = card(
        name = "Gravewick Visitor",
        cost = "{1}{B}",
        type = "Creature — Nightmare",
        text = "Whenever an enchantment you control is put into a graveyard from the battlefield, each opponent loses 1 life.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(enchantment, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    // CR 613.4c (power/toughness modifying effects); an enchanted creature is one with an Aura attached (CR 303.4)
    @Test
    fun aTaleForTheAges() = card(
        name = "Tale of Ages",
        cost = "{1}{W}",
        type = "Enchantment",
        text = "Enchanted creatures you control get +2/+2.",
    ) parsesTo abilities(
        static(pt(2, 2), affects = Objects.Each(and(ObjectFilter.Attached(attachments = sub("Aura")), creature, you))),
    )

    // CR 715.2 (Adventure faces), 613.4c, 111.1 (token), 608.2h (X fixed on resolution)
    @Test
    fun pollenShieldHare() = cardFaces(Layout.Adventure,
        card(
            name = "Cobble-Shield Hare",
            cost = "{1}{W}",
            type = "Creature — Rabbit",
            text = "Creature tokens you control get +1/+1.",
            pt = "2/2",
        ) to abilities(static(pt(1, 1), affects = Objects.Each(and(creature, token, you)))),
        card(
            name = "Raising the Hedge",
            cost = "{G}",
            type = "Sorcery — Adventure",
            text = "Target creature you control gains vigilance and gets +X/+X until end of turn, where X is the number of creatures you control. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            run {
                val x = count(creatureYouControl)
                spell(untilEndOfTurn(target("creature"), Mechanic.GainAbilities(listOf(Keyword.Vigilance)), Mechanic.ModifyPowerToughness(x, x)),
                    targets = listOf(slot("creature", creatureYouControl)))
            },
        ),
    )

    // CR 715.2, 400.7 (a spell returned to hand), 111.1
    @Test
    fun belunasGatekeeper() = cardFaces(Layout.Adventure,
        card(
            name = "Barrowgate Gatekeeper",
            cost = "{5}{U}",
            type = "Creature — Giant Soldier",
            pt = "6/5",
        ) to emptyList<Ability>(),
        card(
            name = "Entry Barred",
            cost = "{1}{U}",
            type = "Sorcery — Adventure",
            text = "Return target creature you don't control with mana value 3 or less to its owner's hand. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(move(target("creature"), Zone.Hand),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)), manaValue(Comparison.AtMost, 3)))))),
    )

    // CR 113.6m (works from the graveyard), 118.8 (sacrifice as a cost)
    @Test
    fun dutifulGriffin() = card(
        name = "Brightfeather Griffin",
        cost = "{3}{W}{W}",
        type = "Creature — Griffin",
        text = """
            Flying
            {2}{W}, Sacrifice two enchantments: Return this card from your graveyard to your hand.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{2}{W}"), CostPart.Sacrifice(CostObjects.Choose(enchantment, count = n(2)))), move(Objects.Self, Zone.Hand))
            .copy(functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 715.2, 700.4, 400.7 (it is a new object in the library)
    @Test
    fun fellHorseman() = cardFaces(Layout.Adventure,
        card(
            name = "Marrowmoor Horseman",
            cost = "{3}{B}",
            type = "Creature — Zombie Knight",
            text = "When this creature dies, put it on the bottom of its owner's library.",
            pt = "3/3",
        ) to abilities(triggered(diesSelf, Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Library, libraryPosition = -1))))),
        card(
            name = "Grim Procession",
            cost = "{1}{B}",
            type = "Sorcery — Adventure",
            text = "Return target creature card from your graveyard to your hand. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature))))),
    )

    // CR 610.3 (exile until it leaves), 603.6a
    @Test
    fun glassCasket() = card(
        name = "Stoneglass Casket",
        cost = "{1}{W}",
        type = "Artifact",
        text = "When this artifact enters, exile target creature an opponent controls with mana value 3 or less until this artifact leaves the battlefield.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent), manaValue(Comparison.AtMost, 3))))),
    )

    // CR 603.2, 406 (exile), 111.1 (token that can't block, CR 509.1b)
    @Test
    fun lordSkitter() = card(
        name = "Skitterpath King",
        cost = "{2}{B}",
        type = "Legendary Creature — Rat Noble",
        text = """
            Whenever another Rat you control enters, exile up to one target card from an opponent's graveyard.
            At the beginning of combat on your turn, create a 1/1 black Rat creature token with "This token can't block."
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(enters(and(another, sub("Rat"), you)), move(target("card"), Zone.Exile),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)), min = 0))),
        triggered(beginningOfCombatOnYourTurn, create(creatureToken("Rat", listOf(Color.Black), 1, 1, listOf(static(Mechanic.CantBlock(ObjectFilter.Any)))))),
    )

    // CR 113.6m, 118.12 (you may pay; if you do)
    @Test
    fun redtoothVanguard() = card(
        name = "Briarvault Vanguard",
        cost = "{1}{G}",
        type = "Creature — Elf Warrior",
        text = """
            Trample
            Whenever an enchantment you control enters, you may pay {2}. If you do, return this card from your graveyard to your hand.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(enters(and(enchantment, you)), Step.Pay(cost(mana("{2}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(move(Objects.Self, Zone.Hand))), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 701.7 (exile), 602.5 (sacrifice as a cost), 404 (graveyard)
    @Test
    fun soulGuideLantern() = card(
        name = "Wanderlight Lantern",
        cost = "{1}",
        type = "Artifact",
        text = """
            When this artifact enters, exile target card from a graveyard.
            {T}, Sacrifice this artifact: Exile each opponent's graveyard.
            {1}, {T}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", cardInAGraveyard))),
        activated(cost(tap, sacrificeSelf), move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent))), Zone.Exile)),
        activated(cost(mana("{1}"), tap, sacrificeSelf), draw(1)),
    )

    // CR 714.2b (chapters), 610.3 (exile), 400.7 (return as a new object), 613.4c
    @Test
    fun thePrincessTakesFlight() = card(
        name = "Crowned Vigil",
        cost = "{2}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Exile up to one target creature.
            II — Target creature you control gets +2/+2 and gains flying until end of turn.
            III — Return the exiled card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        chapter(1, move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))), targets = listOf(slot("creature", creature, min = 0))),
        chapter(2, pump(target("creature"), 2, 2, has(Keyword.Flying)), targets = listOf(slot("creature", creatureYouControl))),
        chapter(3, move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 610.3, 603.6a, 701.21 (sacrifice), 119.1 (gain life)
    @Test
    fun werefoxBodyguard() = card(
        name = "Hedgehound Bodyguard",
        cost = "{1}{W}{W}",
        type = "Creature — Elf Fox Knight",
        text = """
            Flash
            When this creature enters, exile up to one other target non-Fox creature until this creature leaves the battlefield.
            {1}{W}, Sacrifice this creature: You gain 2 life.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", and(not(ObjectFilter.Is(Objects.Self)), not(sub("Fox")), creature), min = 0))),
        activated(cost(mana("{1}{W}"), sacrificeSelf), gainLife(2)),
    )
}
