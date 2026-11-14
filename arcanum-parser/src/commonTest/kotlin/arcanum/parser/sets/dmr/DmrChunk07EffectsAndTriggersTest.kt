package arcanum.parser.sets.dmr

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 07: effects (combat damage prevented both ways, the next damage, piles, searches, extra turns, put back on the bottom) and
 * triggers (any player cycling, tapped for mana of the chosen color, state triggers, damage to a creature or opponent, end of combat). */
class DmrChunk07EffectsAndTriggersTest {
    private val inYourHand = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun bothWays(it: Objects): List<Mechanic> = listOf(
        Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(it)), combat = true)),
        Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Is(it), RecipientFilter(objects = ObjectFilter.Any, players = PlayerFilter.Any), combat = true)))
    private val aPlayerCycles: Trigger = on(EventPattern.Cycle(ObjectFilter.Any, PlayerFilter.Any))

    // CR 615.1, 510.2: untapped, and no combat damage to or by it this turn
    @Test
    fun mazeOfOssory() = card(
        name = "Maze of Ossory",
        cost = null,
        type = "Land",
        text = "{T}: Untap target attacking creature. Prevent all combat damage that would be dealt to and dealt by that creature this turn.",
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("creature")), doing(Effect.ApplyMechanics(bothWays(target("creature")), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 615.7 (a shield of 2 on that creature), 709 (split card)
    @Test
    fun wardOffSendAway() = cardFaces(
        Layout.Split,
        card(name = "Ward Off", cost = "{W}", type = "Instant", text = "Prevent the next 2 damage that would be dealt to target creature this turn.") to abilities(
            spell(doing(Effect.PreventDamage(Recipients(objects = target("creature")), n(2), from = ObjectFilter.Any, duration = Duration.EndOfTurn)),
                targets = listOf(slot("creature", creature))),
        ),
        card(name = "Send Away", cost = "{2}{U}", type = "Instant", text = "Return target permanent to its owner's hand.") to abilities(
            spell(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", permanentFilter))),
        ),
    )

    // CR 118.3 (the creatures tapped to pay), 205.3m (a creature type shared with each of them, not necessarily the same one)
    @Test
    fun riddledPortal() = card(
        name = "Riddled Portal",
        cost = "{5}",
        type = "Artifact",
        text = "Tap two untapped creatures you control: You may put a creature card from your hand that shares a creature type with each creature tapped this way onto the battlefield.",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, count = n(2)), bind = "tapped")),
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Shares(SharedQuality.CreatureType, Objects.Bound("tapped"), each = true)), min = n(0), max = n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 401.4: two cards from the hand on the bottom, in an order of their owner's choice
    @Test
    fun jaggedHeron() = card(
        name = "Jagged Heron",
        cost = "{2}{W}{U}",
        type = "Creature — Bird",
        pt = "2/2",
        text = """
            Flying
            When this creature enters, return a white or blue creature you control to its owner's hand.
            When this creature enters, draw two cards, then put two cards from your hand on the bottom of your library.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(or(ObjectFilter.OfColor(ColorRef.Literal(Color.White)), ObjectFilter.OfColor(ColorRef.Literal(Color.Blue))), creature, you), min = n(1), max = n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        triggered(entersSelf, draw(2),
            Step.Choose(Choice.Objects(inYourHand, n(2), n(2)), bind = "put", chooser = Players.You),
            move(Objects.Bound("put"), Zone.Library, position = -1)),
    )

    // CR 603.7 (a delayed trigger at end of combat, CR 511.2), 509.1 (blocks)
    @Test
    fun wallOfScrapIron() = card(
        name = "Wall of Scrap Iron",
        cost = "{2}",
        type = "Artifact Creature — Wall",
        pt = "0/7",
        text = """
            Defender (This creature can't attack.)
            When this creature blocks, return it to its owner's hand at end of combat. (Return it only if it's on the battlefield.)
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(on(EventPattern.Block(isSelf, ObjectFilter.Any)),
            Step.Bind(Objects.EventObject(EventRole.Object), "event object"),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.StepBegins(TurnStep.EndOfCombat, PlayerFilter.Any)),
                Instructions(steps = listOf(move(Objects.Bound("event object"), Zone.Hand)))))),
    )

    // CR 701.20a (the drawn card revealed), 701.9a (discarded unless it's a land card)
    @Test
    fun oasisDiviner() = card(
        name = "Oasis Diviner",
        cost = "{1}{G}",
        type = "Creature — Human Shaman",
        pt = "1/1",
        text = "{T}: Draw a card and reveal it. If it isn't a land card, discard it.",
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.Draw(Players.You, n(1), postBind = "drawn")), doing(Effect.Reveal(Objects.Bound("drawn"))),
            ifThen(Condition.ObjectMatches(Objects.Bound("drawn"), not(land)),
                doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Bound("drawn"))))),
    )

    // CR 118.12a (sacrificed unless the exile is paid), 607.2a (the exiled card linked to the leaves-the-battlefield ability), 610.3
    @Test
    fun wyrmtoothDrake() = card(
        name = "Wyrmtooth Drake",
        cost = "{2}{U}",
        type = "Creature — Nightmare Drake",
        pt = "3/4",
        text = """
            Flying
            When this creature enters, sacrifice it unless you exile a creature you control other than this creature.
            When this creature leaves the battlefield, return the exiled card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Pay(cost(CostPart.Exile(CostObjects.Choose(and(another, creature, you), count = n(1)), from = Zone.Battlefield, remember = "exiled")),
                payer = Players.You, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Sacrifice(Objects.Self)))),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)),
            move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 701.23d (simply three cards: found if there are that many), 701.24a (that player shuffles)
    @Test
    fun foolsBonnet() = card(
        name = "Fool's Bonnet",
        cost = "{4}",
        type = "Artifact",
        text = "{2}, {T}, Sacrifice this artifact: Search target player's library for three cards and exile them. Then that player shuffles.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, sacrificeSelf),
            doing(Effect.Search(Players.Target("player"), ObjectFilter.Any, n(3), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Exile),
            doing(Effect.Shuffle(Players.Target("player"))),
            targets = listOf(targetPlayer)),
    )

    // CR 701.24b: shuffle, then the found card on top
    @Test
    fun earthlyMentor() = card(
        name = "Earthly Mentor",
        cost = "{G}",
        type = "Instant",
        text = "Search your library for a creature card, reveal it, then shuffle and put the card on top.",
    ) parsesTo abilities(
        spell(doing(Effect.Search(Players.You, creature, n(1), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            doing(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0)),
    )

    // CR 701.20a (revealed), 700.3 (two piles, one of them may be empty): the chosen pile to hand, the other to the graveyard
    @Test
    fun truthOrRumor() = card(
        name = "Truth or Rumor",
        cost = "{3}{U}",
        type = "Instant",
        text = "Reveal the top five cards of your library. An opponent separates those cards into two piles. Put one pile into your hand and the other into your graveyard.",
    ) parsesTo abilities(
        spell(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.SeparatePiles(Objects.Bound("seen"), separator = Players.Bound("opponent"), chooser = Players.You,
                chosenBind = "chosen pile", otherBind = "other pile")),
            move(Objects.Bound("chosen pile"), Zone.Hand),
            move(Objects.Bound("other pile"), Zone.Graveyard)),
    )

    // CR 604.3 (a characteristic-defining ability), 119 (the life total); 603.6a, 400.7 (put into a graveyard from anywhere)
    @Test
    fun seraphIncarnate() = card(
        name = "Seraph Incarnate",
        cost = "{4}{W}{W}{W}",
        type = "Creature — Avatar",
        pt = "*/*",
        text = """
            Seraph Incarnate's power and toughness are each equal to your life total.
            When Seraph Incarnate is put into a graveyard from anywhere, shuffle it into its owner's library.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(Value.LifeOf(Players.You), Value.LifeOf(Players.You)), characteristicDefining = true),
        triggered(on(EventPattern.ZoneChange(isSelf, to = Zone.Graveyard)),
            move(Objects.EventObject(EventRole.After), Zone.Library),
            doing(Effect.Shuffle(Players.OwnerOf(Objects.EventObject(EventRole.After))))),
    )

    // CR 115.1 (a spell or a permanent), 105.3 (its new color), 709 (split card)
    @Test
    fun glamourSubstance() = cardFaces(
        Layout.Split,
        card(name = "Glamour", cost = "{U}", type = "Instant", text = "Target spell or permanent becomes the color of your choice until end of turn.") to abilities(
            spell(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
                untilEndOfTurn(target("permanent"), Mechanic.SetColors(listOf(ColorRef.Chosen("color")))),
                targets = listOf(slot("permanent", or(spellFilter, permanentFilter)))),
        ),
        card(name = "Substance", cost = "{2}{G}", type = "Instant", text = "Destroy target artifact.") to abilities(
            spell(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ),
    )

    // CR 500.7: two extra turns for the target player
    @Test
    fun hourWarp() = card(
        name = "Hour Warp",
        cost = "{8}{U}{U}",
        type = "Sorcery",
        text = "Target player takes two extra turns after this one.",
    ) parsesTo abilities(
        spell(Step.Repeat(n(2), listOf(doing(Effect.ExtraTurn(Players.Target("player"))))), targets = listOf(targetPlayer)),
    )

    // CR 701.8a, 701.19c: destroyed and not regenerated
    @Test
    fun heavensPurge() = card(
        name = "Heaven's Purge",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = "Destroy all creatures. They can't be regenerated.",
    ) parsesTo abilities(
        spell(doing(Effect.Destroy(Objects.Each(creature), noRegeneration = true))),
    )

    // CR 106.12a (tapped for mana of the chosen color), 605.1b (a triggered mana ability), 613.4c
    @Test
    fun bracerOfMight() = card(
        name = "Bracer of Might",
        cost = "{5}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a color.
            Creatures of the chosen color get +1/+1.
            Whenever a basic land is tapped for mana of the chosen color, its controller adds an additional one mana of that color.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", remember = true, chooser = Players.You)))))),
        static(pt(1, 1), affects = Objects.Each(and(creature, ObjectFilter.OfColor(ColorRef.Remembered("color"))))),
        triggered(on(EventPattern.Tap(basicLand(), forMana = true, producingColor = ColorRef.Remembered("color"))),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))),
                Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
    )

    // CR 122.1, 603.8 (a state trigger), 701.21a ("If you do": it was sacrificed), 111.1
    @Test
    fun blackAbyss() = card(
        name = "Black Abyss",
        cost = null,
        type = "Legendary Snow Land",
        text = """
            Black Abyss enters with ten ice counters on it.
            {3}: Remove an ice counter from Black Abyss.
            When Black Abyss has no ice counters on it, sacrifice it. If you do, create Vorn Hollow, a legendary 20/20 black Avatar creature token with flying and indestructible.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(ice, n(10))))),
        activated(cost(mana("{3}")), doing(Effect.RemoveCounters(Objects.Self, ice, n(1)))),
        arcanum.api.Ability.Triggered(Trigger.State(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(ice, atLeast = 1)))), Instructions(steps = listOf(
            doing(Effect.Sacrifice(Objects.Self, postBind = "sacrificed")),
            ifThen(Condition.NonEmpty("sacrificed"), create(creatureToken("Avatar", listOf(Color.Black), 20, 20,
                abilities = listOf(Keyword.Flying, Keyword.Indestructible), name = "Vorn Hollow").let { t ->
                (t as arcanum.api.TokenSpec.Defined).copy(face = t.face.copy(supertypes = listOf(Supertype.Legendary))) }))))),
    )

    private val ice = CounterKind.Named("ice")

    // CR 702.29a: any player's cycling
    @Test
    fun bracingGift() = card(
        name = "Bracing Gift",
        cost = "{1}{G}",
        type = "Enchantment",
        text = "Whenever a player cycles a card, you may put a +1/+1 counter on target creature.",
    ) parsesTo abilities(
        triggered(aPlayerCycles, Step.May(listOf(counters(target("creature"))), Players.You), targets = listOf(slot("creature", creature))),
    )

    // CR 702.29a, 118.12 (you may pay {1}; if you do)
    @Test
    fun thunderChasm() = card(
        name = "Thunder Chasm",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Whenever a player cycles a card, you may pay {1}. If you do, this enchantment deals 2 damage to any target.",
    ) parsesTo abilities(
        triggered(aPlayerCycles, Step.Pay(cost(mana("{1}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), damage(2, toAny)), targets = listOf(anyTarget)),
    )

    // CR 120.3, 603.2: all the damage it dealt, to a creature or an opponent
    @Test
    fun sinewRipper() = card(
        name = "Sinew Ripper",
        cost = "{1}{B}",
        type = "Creature — Phyrexian Horror",
        pt = "4/4",
        text = "Whenever this creature deals damage to a creature or opponent, this creature deals that much damage to you.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(isSelf, RecipientFilter(objects = creature, players = PlayerFilter.Opponent))),
            damage(Value.EventAmount, Recipients(players = Players.You))),
    )

    // CR 603.8 (a state trigger), 107.1a (half, rounded down), 120.3
    @Test
    fun tidebarrier() = card(
        name = "Tidebarrier",
        cost = "{3}{U}",
        type = "Creature — Wall",
        pt = "0/5",
        text = """
            Defender
            When this creature has flying, sacrifice it.
            When this creature leaves the battlefield, it deals damage to each nonblue creature without flying equal to half the number of Islands you control, rounded down.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        arcanum.api.Ability.Triggered(Trigger.State(Condition.ObjectMatches(Objects.Self, kw(Keyword.Flying.kind))),
            Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Self))))),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)),
            damage(Value.Half(count(and(sub("Island"), you)), roundUp = false),
                toObjects(Objects.Each(and(not(ObjectFilter.OfColor(ColorRef.Literal(Color.Blue))), creature, not(kw(Keyword.Flying.kind))))),
                source = Objects.Self)),
    )

    // CR 500.7 (an extra turn), 603.7 (a delayed trigger of that turn only), 104.3a
    @Test
    fun finalGambit() = card(
        name = "Final Gambit",
        cost = "{R}{R}",
        type = "Sorcery",
        text = "Take an extra turn after this one. At the beginning of that turn's end step, you lose the game.",
    ) parsesTo abilities(
        spell(doing(Effect.ExtraTurn(Players.You, mark = "that turn")),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(doing(Effect.LoseGame(Players.You)))), inTurn = "that turn"))),
    )
}
