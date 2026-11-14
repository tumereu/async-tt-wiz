package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 08: moving cards between zones (returns from the graveyard and to hand, linked exiles, exile-and-return, vanishing, split card). */
class DmrChunk08MoveTest {
    private val graveyardMove = { filter: ObjectFilter -> listOf(slot("card", inYourGraveyard(filter))) }

    // CR 603.1, 603.5: "you may" return the enchantment card from your graveyard
    @Test
    fun auramancer() = card(
        name = "Wispweaver", cost = "{2}{W}", type = "Creature — Human Wizard", pt = "2/2",
        text = "When this creature enters, you may return target enchantment card from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(move(target("card"), Zone.Hand)), Players.You), targets = graveyardMove(enchantment)),
    )

    // CR 118.12a: "unless you discard a creature card"; CR 603.10a, 700.4: dies, exile it, return a creature card
    @Test
    fun bodySnatcher() = card(
        name = "Husk Reaver", cost = "{2}{B}{B}", type = "Creature — Phyrexian Minion", pt = "2/2",
        text = """
            When this creature enters, exile it unless you discard a creature card.
            When this creature dies, exile it and return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(creature, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled")))),
        triggered(diesSelf, move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled"), move(target("card"), Zone.Battlefield), targets = graveyardMove(creature)),
    )

    // CR 702.63a: vanishing N; CR 603.6c: one trigger for entering and leaving; "another" excludes the card itself (CR 608.2b)
    @Test
    fun deadwoodTreefolk() = card(
        name = "Thornbark Elder", cost = "{5}{G}", type = "Creature — Treefolk", pt = "3/6",
        text = """
            Vanishing 3 (This creature enters with three time counters on it. At the beginning of your upkeep, remove a time counter from it. When the last is removed, sacrifice it.)
            When this creature enters or leaves the battlefield, return another target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Vanishing(3),
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), EventPattern.ZoneChange(isSelf, from = Zone.Battlefield))),
            move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(another, creature)))),
    )

    // CR 400.3: each other creature you control returns to its owner's hand
    @Test
    fun denizenOfTheDeep() = card(
        name = "Tide Colossus", cost = "{6}{U}{U}", type = "Creature — Serpent", pt = "11/11",
        text = "When this creature enters, return each other creature you control to its owner's hand.",
    ) parsesTo abilities(
        triggered(entersSelf, move(Objects.Each(and(another, creature, you)), Zone.Hand)),
    )

    // CR 610.3, 603.6c: the exiled card returns when this leaves (two linked abilities)
    @Test
    fun facelessButcher() = card(
        name = "Gloom Butcher", cost = "{2}{B}{B}", type = "Creature — Nightmare Horror", pt = "2/3",
        text = """
            When this creature enters, exile another target creature.
            When this creature leaves the battlefield, return the exiled card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("creature", and(another, creature)))),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)),
            move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 614.1a, 113.6b: from anywhere; CR 701.24: shuffled into its owner's library
    @Test
    fun legacyWeapon() = card(
        name = "Prismatic Relic", cost = "{7}", type = "Legendary Artifact",
        text = """
            {W}{U}{B}{R}{G}: Exile target permanent.
            If Prismatic Relic would be put into a graveyard from anywhere, reveal Prismatic Relic and shuffle it into its owner's library instead.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{W}{U}{B}{R}{G}")), move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", permanentFilter))),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Graveyard),
            instead = Instructions(steps = listOf(doing(Effect.Reveal(Objects.Self)), move(Objects.Self, Zone.Library),
                doing(Effect.Shuffle(Players.OwnerOf(Objects.Self))))))), affects = Affected.Objects(Objects.Self),
            functionsIn = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Exile, Zone.Stack, Zone.Command)),
    )

    // CR 602.2, 701.21a: sacrifice as a cost, then exile the attacking creature
    @Test
    fun lieutenantKirtar() = card(
        name = "Captain Veyra", cost = "{1}{W}{W}", type = "Legendary Creature — Bird Soldier", pt = "2/2",
        text = """
            Flying
            {1}{W}, Sacrifice Captain Veyra: Exile target attacking creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{1}{W}"), sacrificeSelf), move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 709.3: each half is cast on its own; CR 509.1b: creatures can't block this turn
    @Test
    fun orderChaos() = cardFaces(
        Layout.Split,
        card(name = "Edict", cost = "{3}{W}", type = "Instant", text = "Exile target attacking creature.") to abilities(
            spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(attacking, creature)))),
        ),
        card(name = "Mayhem", cost = "{2}{R}", type = "Instant", text = "Creatures can't block this turn.") to abilities(
            spell(untilEndOfTurn(Objects.Each(creature), Mechanic.CantBlock(ObjectFilter.Any))),
        ),
    )

    // CR 702.62: not an optional cast; the pay is optional ("you may pay"), CR 603.4: only works from the graveyard
    @Test
    fun pyreZombie() = card(
        name = "Cinder Revenant", cost = "{1}{B}{R}", type = "Creature — Zombie", pt = "2/1",
        text = """
            At the beginning of your upkeep, if this card is in your graveyard, you may pay {1}{B}{B}. If you do, return it to your hand.
            {1}{R}{R}, Sacrifice this creature: It deals 2 damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
            instructions = Instructions(steps = listOf(
                Step.Pay(cost = manaCost("{1}{B}{B}"), mandatory = false, bind = "paid", payer = Players.You),
                Step.If(Condition.Did("paid"), then = listOf(move(Objects.Self, Zone.Hand))),
            )),
        ),
        activated(cost(mana("{1}{R}{R}"), sacrificeSelf), damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 400.3: the player is the card's owner (the permanent's owner), who discards
    @Test
    fun recoil() = card(
        name = "Tidal Backlash", cost = "{1}{U}{B}", type = "Instant",
        text = "Return target permanent to its owner's hand. Then that player discards a card.",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Hand),
            Step.Do(Effect.Discard(Players.OwnerOf(Objects.Target("permanent")), n(1), filter = ObjectFilter.Any)),
            targets = listOf(slot("permanent", permanentFilter))),
    )

    @Test
    fun urborgUprising() = card(
        name = "Gravebound Rally", cost = "{4}{B}", type = "Sorcery",
        text = """
            Return up to two target creature cards from your graveyard to your hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), draw(1), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))),
    )

    // CR 610.3: the exiled permanents return when this leaves, each under its owner's control
    @Test
    fun worldgorgerDragon() = card(
        name = "Ember Wyrm", cost = "{3}{R}{R}{R}", type = "Creature — Nightmare Dragon", pt = "7/7",
        text = """
            Flying, trample
            When this creature enters, exile all other permanents you control.
            When this creature leaves the battlefield, return the exiled cards to the battlefield under their owners' control.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Trample,
        triggered(entersSelf, move(Objects.Each(and(another, permanentFilter, you)), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled")))),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)),
            move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )
}
