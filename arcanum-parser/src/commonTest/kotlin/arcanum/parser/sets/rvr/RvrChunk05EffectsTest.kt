package arcanum.parser.sets.rvr

import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk05EffectsTest {
    private val nextEndStep = stepBegins(TurnStep.End, PlayerFilter.Any)

    // ---- Life

    // CR 701.10d: doubling a life total gains (or loses) life to reach twice it; CR 107.1a: half, rounded up
    @Test
    fun restoralReckoningDue() = cardFaces(
        Layout.Split,
        card(name = "Restoral", cost = "{W/B}{W/B}", type = "Sorcery",
            text = "Return target creature card with mana value 3 or less from your graveyard to the battlefield.") to abilities(
            spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))),
        ),
        card(name = "Reckoning Due", cost = "{4}{W}{B}", type = "Sorcery", text = "Double your life total. Target opponent loses half their life, rounded up.") to abilities(
            spell(
                doing(Effect.SetLife(Players.You, Value.Times(Value.LifeOf(Players.You), 2))),
                doing(Effect.LoseLife(Players.Target("opponent"), Value.Half(Value.LifeOf(Players.Target("opponent")), roundUp = true))),
                targets = listOf(targetOpponent),
            ),
        ),
    )

    // CR 608.2h: "its toughness" of the creature put into the library, as it last existed
    @Test
    fun verdictOfExile() = card(
        name = "Verdict of Exile",
        cost = "{W}",
        type = "Instant",
        text = "Put target attacking creature on the bottom of its owner's library. Its controller gains life equal to its toughness.",
    ) parsesTo abilities(
        spell(
            move(target("creature"), Zone.Library, position = -1),
            doing(Effect.GainLife(Players.ControllerOf(target("creature")), toughness(target("creature")))),
            targets = listOf(slot("creature", and(attacking, creature))),
        ),
    )

    // CR 118.3, 119.4: paying life as an optional cost of the effect; CR 701.21a: each other player chooses their own creature
    @Test
    fun velkaQueenOfTheRot() = card(
        name = "Velka, Queen of the Rot",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Elf Shaman",
        pt = "2/2",
        text = """
            Whenever you sacrifice a black creature, you may pay 2 life. If you do, each other player sacrifices a creature of their choice.
            Whenever you sacrifice a green creature, you may gain 2 life.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Sacrifice(and(ObjectFilter.OfColor(Color.Black), creature, you))),
            Step.Pay(Cost(listOf(CostPart.PayLife(n(2)))), payer = Players.You, bind = "paid"),
            ifThen(arcanum.api.Condition.Did("paid"), Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Not(PlayerFilter.You))), bind = "player", together = true, steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "sacrificed",
                    chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            )))),
        triggered(on(EventPattern.Sacrifice(and(ObjectFilter.OfColor(Color.Green), creature, you))),
            Step.May(listOf(gainLife(2)), player = Players.You)),
    )

    // ---- Graveyards and libraries

    // CR 603.10, 113.6k: a library-to-graveyard trigger of the card itself works from the graveyard it was put into
    @Test
    fun dreamdriftAmoeba() = card(
        name = "Dreamdrift Amoeba",
        cost = "{1}{U}",
        type = "Creature — Illusion",
        pt = "1/1",
        text = """
            Flying
            When this card is put into your graveyard from your library, you may put it onto the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Library, to = Zone.Graveyard)),
            Step.May(listOf(move(Objects.EventObject(EventRole.After), Zone.Battlefield)), player = Players.You)),
    )

    // CR 603.10: checked on the card in the graveyard; "you may exile it. If you do": exiling it from there is paid or not (CR 608.2c)
    @Test
    fun seepingFrost() = card(
        name = "Seeping Frost",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Seeping Frost deals 3 damage to each opponent and you gain 3 life.
            When Seeping Frost is put into your graveyard from your library, you may exile it. If you do, Seeping Frost deals 3 damage to each opponent and you gain 3 life.
        """,
    ) parsesTo abilities(
        spell(damage(3, eachOpponent), gainLife(3)),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Library, to = Zone.Graveyard)),
            Step.Pay(Cost(listOf(CostPart.Exile(arcanum.api.CostObjects.These(Objects.EventObject(EventRole.After)), from = Zone.Graveyard))), payer = Players.You, bind = "paid"),
            ifThen(arcanum.api.Condition.Did("paid"), damage(3, eachOpponent), gainLife(3))),
    )

    // CR 115.1: the cards target in the target player's graveyard; CR 701.24a shuffle
    @Test
    fun siltStirrerShaman() = card(
        name = "Silt-Stirrer Shaman",
        cost = "{2}{G}",
        type = "Creature — Centaur Shaman",
        pt = "3/2",
        text = "When this creature enters, target player shuffles any number of target cards from their graveyard into their library.",
    ) parsesTo abilities(
        triggered(entersSelf,
            move(target("cards"), Zone.Library),
            doing(Effect.Shuffle(Players.Target("player"))),
            targets = listOf(targetPlayer, TargetSlot("cards", TargetKind.Objects(and(ObjectFilter.Category(ObjectCategory.Card), inZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), min = n(0), max = null))),
    )

    // CR 604.3: a characteristic-defining power; CR 400.1, 108.3: cards you own in exile and your graveyard
    @Test
    fun sparkwingDrake() = card(
        name = "Sparkwing Drake",
        cost = "{U}{U}{R}{R}",
        type = "Creature — Drake",
        pt = "*/4",
        text = """
            Flying
            Sparkwing Drake's power is equal to the total number of instant and sorcery cards you own in exile and in your graveyard.
            When this creature enters, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(count(and(or(instant, sorcery), or(inZone(Zone.Exile), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You))), null),
            characteristicDefining = true),
        triggered(entersSelf, draw(1)),
    )

    // ---- Next end step

    // CR 603.7, 400.7: the exiled cards return as new objects at the next end step
    @Test
    fun spiritpath() = card(
        name = "Spiritpath",
        cost = "{2}{W}",
        type = "Instant",
        text = "Exile each creature you control. Return those cards to the battlefield under their owner's control at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            move(Objects.Each(creatureYouControl), Zone.Exile, postBind = "exiled"),
            delayed(nextEndStep, doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
        ),
    )

    // CR 603.7c: the delayed trigger names the token just created
    @Test
    fun cacklingCultmage() = card(
        name = "Cackling Cultmage",
        cost = "{B/R}{B/R}",
        type = "Creature — Zombie Shaman",
        pt = "2/2",
        text = """
            ({B/R} can be paid with either {B} or {R}.)
            {3}{B}, Discard a card: Target creature gets -2/-2 until end of turn.
            {3}{R}: Create a 2/1 red Goblin creature token with haste. Exile it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{3}{B}"), discardACard), pump(target("creature"), -2, -2), targets = listOf(slot("creature", creature))),
        activated(manaCost("{3}{R}"),
            create(creatureToken("Goblin", listOf(Color.Red), 2, 1, listOf(Keyword.Haste)), bind = "tokens"),
            delayed(nextEndStep, move(Objects.Bound("tokens"), Zone.Exile))),
    )

    // CR 603.7: "this turn" makes a delayed trigger that triggers each time a creature dies; CR 603.7d: its source is this creature
    @Test
    fun carnageMaiden() = card(
        name = "Carnage Maiden",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Human Assassin",
        pt = "4/4",
        text = """
            Menace
            When Carnage Maiden enters, each other creature gets -1/-1 until end of turn. Whenever a creature dies this turn, each creature other than Carnage Maiden gets -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf,
            pump(Objects.Each(and(another, creature)), -1, -1),
            doing(Effect.CreateDelayedTrigger(dies(creature), Instructions(steps = listOf(pump(Objects.Each(and(another, creature)), -1, -1))),
                duration = Duration.EndOfTurn, repeats = true))),
    )

    // ---- Names, types and abilities

    private val entered = Objects.EventObject(EventRole.After)

    // CR 603.4: an intervening "if" about the creature that entered; "another" is other than it (CR 201.2: same name)
    @Test
    fun stewardInitiative() = card(
        name = "Steward Initiative",
        cost = "{3}{G}",
        type = "Enchantment",
        text = "Whenever a nontoken creature you control enters, if it doesn't have the same name as another creature you control or a creature card in your graveyard, draw a card.",
    ) parsesTo abilities(
        triggered(enters(and(not(token), creature, you)), draw(1),
            interveningIf = arcanum.api.Condition.ObjectMatches(entered, not(ObjectFilter.SameNameAs(Objects.Each(or(
                and(not(ObjectFilter.Is(entered)), creature, you),
                inYourGraveyard(creature),
            )))))),
    )

    // CR 110.4a: a permanent type shared with the permanent that entered; "another": not that permanent
    @Test
    fun mistglassTrinket() = card(
        name = "Mistglass Trinket",
        cost = "{3}",
        type = "Artifact",
        text = "Whenever a nonartifact permanent you control enters, you may return another permanent you control that shares a permanent type with it to its owner's hand.",
    ) parsesTo abilities(
        triggered(enters(and(not(artifact), permanentFilter, you)),
            Step.Choose(arcanum.api.Choice.Objects(and(not(ObjectFilter.Is(entered)), permanentFilter, you, ObjectFilter.Shares(arcanum.api.SharedQuality.PermanentType, entered)),
                n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    // CR 107.5: an activated ability with the tap symbol in its cost (mana abilities too, CR 605.1a)
    @Test
    fun tinkerersWhetstone() = card(
        name = "Tinkerer's Whetstone",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Untap target creature that has an activated ability with {T} in its cost.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), untapIt(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.HasTapAbility)))),
    )

    // CR 613.1f, 606.2: it has the loyalty abilities of the other planeswalkers; CR 101.4: each opponent chooses their own card;
    // CR 104.3e: an effect says a player loses the game
    @Test
    fun vorthraxDragonTyrant() = card(
        name = "Vorthrax, Dragon-Tyrant",
        cost = "{U}{B}{B}{B}{R}",
        type = "Legendary Planeswalker — Bolas",
        loyalty = "4",
        text = """
            Vorthrax has all loyalty abilities of all other planeswalkers on the battlefield.
            [+1]: You draw a card. Each opponent exiles a card from their hand or a permanent they control.
            [−3]: Destroy target creature or planeswalker.
            [−8]: Each opponent who doesn't control a legendary creature or planeswalker loses the game.
        """,
    ) parsesTo abilities(
        static(Mechanic.GainAbilitiesOf(Objects.Each(and(another, planeswalker)), onlyLoyalty = true)),
        loyalty(1, draw(1), Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
            Step.Choose(arcanum.api.Choice.Objects(or(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent")))),
                and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent"))))), n(1), n(1)), bind = "chosen", chooser = Players.Bound("opponent")),
            move(Objects.Bound("chosen"), Zone.Exile),
        ))),
        loyalty(-3, destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, planeswalker)))),
        loyalty(-8, Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
            ifThen(arcanum.api.Condition.Not(arcanum.api.Condition.Exists(and(or(and(legendary, creature), planeswalker),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), atLeast = 1)),
                doing(Effect.LoseGame(Players.Bound("opponent")))),
        ))),
    )
    private val planeswalker = ObjectFilter.Type(arcanum.api.CardType.Planeswalker)

    // CR 115.1, 109.5: lands of any player are protected from the targets of this ability's controller's opponents; CR 305.1, 101.2
    @Test
    fun varnekEsteemedBarrister() = card(
        name = "Varnek, Esteemed Barrister",
        cost = "{W}{W}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/3",
        text = """
            Flying
            Lands on the battlefield and land cards in graveyards can't be the targets of spells or abilities your opponents control.
            Your opponents can't play land cards from graveyards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantBeTargeted(by = ObjectFilter.ControlledBy(PlayerFilter.Opponent)),
            affects = Objects.Each(or(and(land, inZone(Zone.Battlefield)), and(land, inZone(Zone.Graveyard))))),
        playerStatic(Mechanic.CantPlayLands(and(land, inZone(Zone.Graveyard))), players = Players.Each(PlayerFilter.Opponent)),
    )

    // ---- Hands, exile and libraries

    // CR 607.2a: the cards exiled with it; CR 406.3: face down
    @Test
    fun corkedSanctum() = card(
        name = "Corked Sanctum",
        cost = "{4}",
        type = "Artifact",
        text = """
            At the beginning of each opponent's upkeep, exile all cards from your hand face down.
            At the beginning of your upkeep, return all cards you own exiled with this artifact to your hand, then draw a card.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Opponent),
            doing(Effect.Move(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            doing(Effect.Remember("exiled", Objects.Bound("exiled")))),
        triggered(yourUpkeep(), move(Objects.Where(Objects.Remembered("exiled"), ObjectFilter.OwnedBy(PlayerFilter.You)), Zone.Hand), draw(1)),
    )

    // CR 510.1, 601.2, 608.2g: cast from the damaged player's hand while the ability resolves; a land isn't cast (CR 305.9)
    @Test
    fun thoughtsapHorror() = card(
        name = "Thoughtsap Horror",
        cost = "{5}{U}{B}{B}",
        type = "Creature — Horror",
        pt = "6/6",
        text = """
            Trample
            Whenever this creature deals combat damage to a player, you may look at that player's hand. If you do, you may cast a spell from among those cards without paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(combatDamageToAPlayer,
            Step.May(listOf(
                doing(Effect.LookAt(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer)))), Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(and(not(land), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer))), n(0), n(1)),
                    bind = "chosen", chooser = Players.You),
                doing(Effect.Cast(Objects.Bound("chosen"), arcanum.api.PlayKind.Cast, withoutPaying = true, caster = Players.You)),
            ), player = Players.You)),
    )

    // CR 508.4: put onto the battlefield attacking; CR 400.7, 603.7: the permanent returns at the next end step; CR 401.1 third from the top
    @Test
    fun grummokTheRuinBoar() = card(
        name = "Grummok, the Ruin-Boar",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Boar God",
        pt = "6/6",
        text = """
            Trample
            Whenever Grummok attacks, you may put a creature card from your hand onto the battlefield tapped and attacking. Return that creature to your hand at the beginning of the next end step.
            When Grummok dies or is put into exile from the battlefield, you may put it into its owner's library third from the top.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(attacksSelf,
            Step.Choose(arcanum.api.Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true, attacking = arcanum.api.AttackTarget.Chosen), postBind = "put")),
            delayed(nextEndStep, move(Objects.Bound("put"), Zone.Hand))),
        triggered(arcanum.api.Trigger.On(listOf(
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Exile))),
            Step.May(listOf(move(entered, Zone.Library, position = 2)), player = Players.You)),
    )

    // CR 701.23a: a search for any number of cards with a total mana value limit (CR 202.3)
    @Test
    fun shiftingBehemoth() = card(
        name = "Shifting Behemoth",
        cost = "{5}{G}{G}",
        type = "Creature — Beast",
        pt = "6/6",
        text = "When this creature dies, search your library for any number of creature cards with total mana value 6 or less, put them onto the battlefield, then shuffle.",
    ) parsesTo abilities(
        triggered(diesSelf,
            doing(Effect.Search(Players.You, creature, max = count(and(creature, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))), zones = listOf(Zone.Library),
                bind = "found", searcher = Players.You, totalAtMost = arcanum.api.Choice.TotalLimit(arcanum.api.NumericProperty.ManaValue, n(6)))),
            move(Objects.Bound("found"), Zone.Battlefield),
            shuffle),
    )
}
