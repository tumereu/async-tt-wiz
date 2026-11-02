package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.another
import arcanum.parser.sets.rtr.Rtr.combatDamageToAPlayer
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.enchant
import arcanum.parser.sets.rtr.Rtr.enchanted
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonland
import arcanum.parser.sets.rtr.Rtr.opponents
import arcanum.parser.sets.rtr.Rtr.permanent
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisDies
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import arcanum.parser.sets.rtr.Rtr.yourUpkeep
import kotlin.test.Test

/**
 * Return to Ravnica chunk 07: the blue cards. A planeswalker with a repeating delayed trigger until your next turn (CR
 * 603.7b), two piles separated by an opponent (CR 701.20), a search of every player's library and free casting (CR 608.2g);
 * cards linked to their exiling ability (CR 607.2a) and "playing" a card with the same name (casting or playing a land);
 * exchanging control (CR 701.12); milling by the damage dealt or by a count; "unless you return another creature"; a cost
 * of two cards with the same name; a land's controller milling when it becomes tapped.
 */
class RtrChunk07BlueTest {
    @Test
    fun canalSteed() = card(
        name = "Canal Steed",
        cost = "{3}{U}",
        type = "Creature — Beast",
        pt = "3/1",
        text = "{2}{U}, {T}: Target creature gets -2/-0 until end of turn.",
    ) parsesTo abilities(
        activated(cost(manaPart("{2}{U}"), CostPart.TapSelf), pump(target("creature"), -2, 0), targets = listOf(slot("creature", creature))),
    )

    /** "Its controller": the land's controller as the ability resolves. */
    @Test
    fun seepingDeluge() = card(
        name = "Seeping Deluge",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Whenever enchanted land becomes tapped, its controller mills three cards.
        """,
    ) parsesTo abilities(
        enchant(land),
        triggered(Trigger.On(EventPattern.Tap(ObjectFilter.Is(enchanted))),
            Step.Do(Effect.Mill(Players.ControllerOf(Objects.EventObject(arcanum.api.EventRole.Object)), n(3)))),
    )

    /** CR 701.12: both or nothing; "you neither own nor control". */
    @Test
    fun phantomCoinage() = card(
        name = "Phantom Coinage",
        cost = "{5}{U}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, you may exchange control of this enchantment and target permanent you neither own nor control.",
    ) parsesTo abilities(
        triggered(yourUpkeep, Step.May(listOf(Step.Do(Effect.ExchangeControl(Objects.Self, target("permanent")))), player = Players.You),
            targets = listOf(slot("permanent", all(permanent, ObjectFilter.Not(ObjectFilter.OwnedBy(PlayerFilter.You)), ObjectFilter.Not(you))))),
    )

    /** "That many": the combat damage dealt to that player. */
    @Test
    fun alleywayMessenger() = card(
        name = "Alleyway Messenger",
        cost = "{1}{U}",
        type = "Creature — Vedalken",
        pt = "2/1",
        text = "Whenever this creature deals combat damage to a player, that player mills that many cards.",
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer(), Step.Do(Effect.Mill(Players.EventPlayer, Value.EventAmount))),
    )

    /** "X cards, where X is …" (CR 107.3c), counted as it resolves. */
    @Test
    fun thresholdWarden() = card(
        name = "Threshold Warden",
        cost = "{1}{U}",
        type = "Creature — Homunculus",
        pt = "0/4",
        text = """
            Defender
            {2}{U}, {T}: Target player mills X cards, where X is the number of creatures you control with defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(manaPart("{2}{U}"), CostPart.TapSelf),
            Step.Do(Effect.Mill(Players.Target("player"), Value.Count(Objects.Each(all(creature, you, ObjectFilter.HasKeyword(Keyword.Defender)))))),
            targets = listOf(Rtr.targetPlayer)),
    )

    /** "Sacrifice it unless you return another creature" (CR 118.12a): returning is paid as it resolves. */
    @Test
    fun faePretender() = card(
        name = "Fae Pretender",
        cost = "{U}",
        type = "Creature — Faerie Rogue",
        pt = "2/1",
        text = """
            Flying
            When this creature enters, sacrifice it unless you return another creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisEnters,
            Step.Pay(Cost(listOf(CostPart.ReturnToHand(CostObjects.Choose(all(another, creature, you), count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Sacrifice(Objects.Self))))),
    )

    @Test
    fun flashOfInsightUnbound() = card(
        name = "Flash of Insight Unbound",
        cost = "{3}{U}",
        type = "Instant",
        text = "Target player draws two cards.",
    ) parsesTo abilities(
        spell(draw(2, Players.Target("player")), targets = listOf(Rtr.targetPlayer)),
    )

    /**
     * +1: a delayed trigger that triggers each time until your next turn (CR 603.7b); −2: an opponent of your choice splits the
     * revealed cards (CR 701.20), you choose a pile; −8: each library searched in turn, the cards cast for free afterwards
     * (CR 608.2g), each one you choose to.
     */
    @Test
    fun tamsinWeaverOfThought() = card(
        name = "Tamsin, Weaver of Thought",
        cost = "{2}{U}{U}",
        type = "Legendary Planeswalker — Jace",
        loyalty = "4",
        text = """
            [+1]: Until your next turn, whenever a creature an opponent controls attacks, it gets -1/-0 until end of turn.
            [−2]: Reveal the top three cards of your library. An opponent separates those cards into two piles. Put one pile into your hand and the other on the bottom of your library in any order.
            [−8]: For each player, search that player's library for a nonland card and exile it, then that player shuffles. You may cast those cards without paying their mana costs.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))), Step.Do(Effect.CreateDelayedTrigger(
            Trigger.On(EventPattern.Attack(all(creature, opponents))),
            Instructions(steps = listOf(pump(Objects.EventObject(arcanum.api.EventRole.Object), -1, 0))),
            duration = Duration.UntilYourNextTurn, repeats = true))),
        activated(cost(CostPart.Loyalty(n(-2))),
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(3), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            Step.Do(Effect.SeparatePiles(Objects.Bound("seen"), separator = Players.Bound("opponent"), chooser = Players.You,
                chosenBind = "chosen pile", otherBind = "other pile")),
            Rtr.move(Objects.Bound("chosen pile"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Bound("other pile"), Destination(Zone.Library, libraryPosition = -1)))),
        activated(cost(CostPart.Loyalty(n(-8))),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Do(Effect.Search(Players.Bound("player"), nonland, max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Shuffle(Players.Bound("player"))))),
            Step.ForEach(Each.Objects(Objects.Bound("exiled")), bind = "card", steps = listOf(
                Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You)))),
    )

    @Test
    fun numbingClutch() = card(
        name = "Numbing Clutch",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.DoesntUntap),
    )

    /** "That many": the cards shuffled in, counted as they were (CR 608.2h). */
    @Test
    fun mindwhirl() = card(
        name = "Mindwhirl",
        cost = "{4}{U}",
        type = "Instant",
        text = "Shuffle all cards from your graveyard into your library. Target player mills that many cards.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Each(all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), Destination(Zone.Library),
                preBind = "shuffled")),
            Step.Do(Effect.Shuffle(Players.You)),
            Step.Do(Effect.Mill(Players.Target("player"), Value.Count(Objects.Bound("shuffled")))),
            targets = listOf(Rtr.targetPlayer),
        ),
    )

    @Test
    fun glyphwing() = card(
        name = "Glyphwing",
        cost = "{3}{U}",
        type = "Creature — Bird",
        pt = "2/2",
        text = """
            Flying
            When this creature dies, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisDies, draw(1)),
    )

    /**
     * The exiled cards are linked to it (CR 607.2a). "Play a card": cast it or play it as a land (CR 601, 305.1). One exiled
     * card with that name goes to its owner's hand; once none is left, it is sacrificed and, if it was ("if you do", CR
     * 118.12), an extra turn follows.
     */
    @Test
    fun combTheDistricts() = card(
        name = "Comb the Districts",
        cost = "{4}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile the top five cards of your library.
            Whenever you play a card with the same name as one of the exiled cards, you may put one of those cards with that name into its owner's hand. Then if there are no cards exiled with this enchantment, sacrifice it. If you do, take an extra turn after this one.
        """,
    ) parsesTo abilities(
        triggered(thisEnters,
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(5), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled")))),
        triggered(Trigger.On(listOf(
            EventPattern.Cast(ObjectFilter.SameNameAs(Objects.Remembered("exiled")), PlayerFilter.You),
            EventPattern.PlayLand(ObjectFilter.SameNameAs(Objects.Remembered("exiled")), PlayerFilter.You))),
            Step.Choose(Choice.Objects(ObjectFilter.SameNameAs(Objects.EventObject(arcanum.api.EventRole.Object)), n(0), n(1), from = Objects.Remembered("exiled")),
                bind = "chosen", chooser = Players.You),
            Rtr.move(Objects.Bound("chosen"), Zone.Hand),
            Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Remembered("exiled"), ObjectFilter.InZone(Zone.Exile))), Comparison.Equal, n(0)), then = listOf(
                Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Self))), payer = Players.You, mandatory = true, bind = "sacrificed"),
                Step.If(Condition.Did("sacrificed"), then = listOf(Step.Do(Effect.ExtraTurn(Players.You))))))),
    )

    /** Two cards that share a name (CR 201.2) as the cost. */
    @Test
    fun sphinxOfTheBells() = card(
        name = "Sphinx of the Bells",
        cost = "{4}{U}{U}",
        type = "Creature — Sphinx",
        pt = "5/6",
        text = """
            Flying
            Discard two nonland cards with the same name: Draw four cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(CostPart.Discard(CostObjects.Choose(nonland, count = n(2), shared = SharedQuality.Name))), draw(4)),
    )

    @Test
    fun pilfererOfWhispers() = card(
        name = "Pilferer of Whispers",
        cost = "{2}{U}",
        type = "Creature — Human Rogue",
        pt = "2/2",
        text = "Whenever this creature deals combat damage to a player, draw a card.",
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer(), draw(1)),
    )

    @Test
    fun spireDrake() = card(
        name = "Spire Drake",
        cost = "{2}{U}",
        type = "Creature — Drake",
        pt = "1/1",
        text = """
            Flying
            {W}: This creature gets +0/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(manaPart("{W}")), pump(Objects.Self, 0, 1)),
    )

    @Test
    fun nullcaster() = card(
        name = "Nullcaster",
        cost = "{4}{U}",
        type = "Creature — Human Wizard",
        pt = "1/4",
        text = "When this creature enters, you may return target creature to its owner's hand.",
    ) parsesTo abilities(
        triggered(thisEnters, Step.May(listOf(Rtr.move(target("creature"), Zone.Hand)), player = Players.You), targets = listOf(slot("creature", creature))),
    )
}
