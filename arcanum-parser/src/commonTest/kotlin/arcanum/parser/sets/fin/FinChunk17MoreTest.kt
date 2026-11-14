package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 17: multicoloured legends, artifacts and Towns with shapes tested elsewhere in parts: named legendary tokens
 * (CR 111.4), an extra combat after the first (CR 500.8), a Saga returned from the graveyard by its mana value, counters by life gained,
 * playing exiled cards for as long as you control a creature (CR 611.2b), equip cost reductions for a target (CR 601.2f), the Town lands
 * and their Adventures (CR 715). The other Towns that enter tapped and tap for two colours, and the basic lands, repeat these templates.
 */
class FinChunk17MoreTest {
    @Test
    fun gerrotKingOfDwarves() = card(
        name = "Gerrot, King of the Dwarves",
        cost = "{R}{W}",
        type = "Legendary Creature — Dwarf Noble",
        pt = "1/1",
        text = """
            Double strike
            Whenever Gerrot or another Dwarf you control enters and whenever an Equipment you control enters, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, sub("Dwarf"), you)), to = Zone.Battlefield),
            EventPattern.ZoneChange(and(equipment, you), to = Zone.Battlefield))),
            Step.Pay(cost(CostPart.Discard(arcanum.api.CostObjects.Choose(cardFilter, n(1)))), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(1))),
    )

    @Test
    fun judgeMagistrate() = card(
        name = "Judge Magistrate Gavran",
        cost = "{W}{B}",
        type = "Legendary Creature — Human Advisor Knight",
        pt = "2/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever another creature or artifact you control dies, put a +1/+1 counter on Judge Magistrate Gavran.
        """,
    ) parsesTo abilities(Keyword.Menace, triggered(dies(and(another, or(creature, artifact), you)), counters(Objects.Self)))

    @Test
    fun lockeTreasureHunter() = card(
        name = "Lorne Coleman",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Human Rogue",
        pt = "2/3",
        text = """
            Deathtouch, lifelink
            Whenever Lorne Coleman deals combat damage to a player, draw a card, then discard a card.
        """,
    ) parsesTo abilities(Keyword.Deathtouch, Keyword.Lifelink, triggered(combatDamageToAPlayer, draw(1), discard(1)))

    // CR 111.4 (a named token)
    @Test
    fun rinaHeartsong() = card(
        name = "Rina Heartsong",
        cost = "{3}{G}{W}",
        type = "Legendary Creature — Human Rebel Warlock",
        pt = "4/4",
        text = """
            When Rina Heartsong enters, create Biscuit, a legendary 1/1 green and white Dog creature token.
            Angelo Cannon — Whenever Rina Heartsong attacks, another target creature you control gets +1/+1 until end of turn for each creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Dog", listOf(Color.Green, Color.White), 1, 1, name = "Biscuit").let {
            it.copy(face = it.face.copy(supertypes = listOf(Supertype.Legendary)))
        })),
        triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(count(creatureYouControl), count(creatureYouControl))),
            targets = listOf(slot("creature", and(another, creature, you))), abilityWord = "Angelo Cannon"),
    )

    @Test
    fun rexShinral() = card(
        name = "Rex Shinral",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Noble",
        pt = "2/4",
        text = "Whenever Rex Shinral attacks, if you don't control a creature named Nightshade, create Nightshade, a legendary 2/2 white and black Dog creature token.",
    ) parsesTo abilities(
        triggered(attacksSelf, create(creatureToken("Dog", listOf(Color.White, Color.Black), 2, 2, name = "Nightshade").let {
            it.copy(face = it.face.copy(supertypes = listOf(Supertype.Legendary)))
        }), interveningIf = Condition.Not(Condition.Exists(and(creature, ObjectFilter.Named("Nightshade"), you), atLeast = 1))),
    )

    // CR 107.3 (X in the cost and the target), 122.1h
    @Test
    fun rhyaSummonerOfMist() = card(
        name = "Rhya, Summoner of Mist",
        cost = "{R}{G}",
        type = "Legendary Creature — Human Shaman",
        pt = "1/2",
        text = """
            Landfall — Whenever a land you control enters, you may discard a card. If you do, draw a card.
            Summon — {X}, {T}: Return target Saga card with mana value X from your graveyard to the battlefield with a finality counter on it. It gains haste until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(landfall, Step.Pay(cost(CostPart.Discard(arcanum.api.CostObjects.Choose(cardFilter, n(1)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)), abilityWord = "Landfall"),
        activated(cost(mana("{X}"), tap),
            // woe sweep: "It" is the permanent the card became (CR 400.7)
            doing(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(finality, n(1)))), postBind = "moved-card")),
            gains(Objects.Bound("moved-card"), Keyword.Haste),
            targets = listOf(slot("card", inYourGraveyard(saga, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, x)))), rules = asSorcery)
            .let { it.copy(abilityWord = "Summon", instructions = it.instructions.copy(announced = listOf("X"))) },
    )

    // CR 506.5
    @Test
    fun squalSeedMercenary() = card(
        name = "Squal, Seed Mercenary",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Human Knight Mercenary",
        pt = "3/4",
        text = """
            Rough Divide — Whenever a creature you control attacks alone, it gains double strike until end of turn.
            Whenever Squal deals combat damage to a player, return target permanent card with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl, alone = true)), gains(thatSpell, Keyword.DoubleStrike), abilityWord = "Rough Divide"),
        triggered(combatDamageToAPlayer, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(permanentCard, manaValue(arcanum.api.Comparison.AtMost, 3))))),
    )

    @Test
    fun excaliburTheSecond() = card(
        name = "Brightblade II",
        cost = "{1}",
        type = "Legendary Artifact — Equipment",
        text = """
            Whenever you gain life, put a charge counter on Brightblade II.
            Equipped creature gets +1/+1 for each charge counter on Brightblade II.
            Equip {3}
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self, 1, charge)),
        static(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Counters(charge), Objects.Self, Combine.Single),
            Value.Of(NumericProperty.Counters(charge), Objects.Self, Combine.Single)), affects = equipped),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 500.8
    @Test
    fun genjiGauntlet() = card(
        name = "Gauntlet of Genshi",
        cost = "{5}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has double strike.
            Whenever equipped creature attacks, if it's the first combat phase of the turn, untap it. After this phase, there is an additional combat phase.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike), affects = equipped),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))), untapIt(thatSpell), doing(Effect.ExtraCombat()),
            interveningIf = Condition.Compare(Value.EventsThisTurn(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), Measure.Events),
                arcanum.api.Comparison.AtMost, n(1))),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun cupNoodles() = card(
        name = "Cup Noodles",
        cost = "{2}",
        type = "Artifact — Food",
        text = """
            Flash
            When this artifact enters, draw a card.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(Keyword.Flash, triggered(entersSelf, draw(1)), activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)))

    @Test
    fun brokenHeart() = card(
        name = "Lionmane Pendant",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, it deals 2 damage to any target.
            Equipped creature gets +2/+1.
            Equip {2}
        """,
    ) parsesTo abilities(triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget)), static(pt(2, 1), affects = equipped), Keyword.Equip(manaCost("{2}")))

    @Test
    fun lunarPandora() = card(
        name = "Lunar Pandora",
        cost = "{1}",
        type = "Legendary Artifact",
        text = """
            {2}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {6}, {T}, Sacrifice Lunar Pandora: Destroy target nonland permanent.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), surveil(1)),
        activated(cost(mana("{6}"), tap, sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", and(not(land), permanentFilter)))),
    )

    @Test
    fun miracleUrn() = card(
        name = "Miracle Urn",
        cost = "{3}",
        type = "Artifact Creature — Goblin Construct",
        pt = "1/4",
        text = """
            When this creature dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            {2}, {T}: Exile target card from a graveyard.
        """,
    ) parsesTo abilities(
        triggered(diesSelf, createTreasure),
        activated(cost(mana("{2}"), tap), move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 701.20b (reveal until)
    @Test
    fun theRoyalCar() = card(
        name = "The Royal Roadster",
        cost = "{4}",
        type = "Legendary Artifact — Vehicle",
        pt = "4/4",
        text = """
            Haste
            Whenever The Royal Roadster attacks, reveal cards from the top of your library until you reveal a land card. Put that card onto the battlefield tapped and the rest on the bottom of your library in a random order.
            Crew 1
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf, doing(Effect.RevealUntil(land, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        Keyword.Crew(1),
    )

    @Test
    fun ringOfTheKings() = card(
        name = "Ring of the Kings",
        cost = "{4}",
        type = "Legendary Artifact",
        text = """
            {T}: Add {C}{C}.
            {2}, {T}, Pay 1 life: Tap target nonland permanent.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You))),
        activated(cost(mana("{2}"), tap, CostPart.PayLife(n(1))), tapIt(target("permanent")), targets = listOf(slot("permanent", and(not(land), permanentFilter)))),
    )

    @Test
    fun worldAtlas() = card(
        name = "World Atlas",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, {T}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {3}, {T}, Sacrifice this artifact: Search your library for a land card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeSelf), search(basicLand, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(cost(mana("{3}"), tap, sacrificeSelf), search(land, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 611.2b ("for as long as you control Lightning")
    @Test
    fun boltSecuritySergeant() = card(
        name = "Bolt, Security Sergeant",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever Bolt deals combat damage to a player, exile the top card of your library. You may play that card for as long as you control Bolt.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer, move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, ObjectFilter.ControlledBy(PlayerFilter.You)))))),
    )

    // CR 601.2f (an equip cost reduced for one target)
    @Test
    fun claudPlanetsChampion() = card(
        name = "Claud, Planet's Champion",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — Human Soldier Mercenary",
        pt = "4/4",
        text = """
            During your turn, as long as Claud is equipped, it has double strike and indestructible. (This creature deals both first-strike and regular combat damage. Damage and effects that say "destroy" don't destroy this creature.)
            Equip abilities you activate that target Claud cost {2} less to activate.
        """,
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike, Keyword.Indestructible), condition = Condition.And(listOf(Condition.YourTurn,
            Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(to = null, attachments = equipment))))),
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind), you, ObjectFilter.Targets(objects = Objects.Self)),
            arcanum.api.CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
    )

    @Test
    fun severinPlanetsHeir() = card(
        name = "Severin, Planet's Heir",
        cost = "{4}{U}{B}",
        type = "Legendary Creature — Human Avatar Soldier",
        pt = "4/4",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            When Severin enters, creatures your opponents control get -2/-2 until end of turn.
            Whenever a creature an opponent controls dies, put a +1/+1 counter on Severin.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, pump(Objects.Each(creatureOpponentControls), -2, -2)),
        triggered(dies(creatureOpponentControls), counters(Objects.Self)),
    )

    @Test
    fun ultimateWeapon() = card(
        name = "Ultimate Armament",
        cost = "{7}",
        type = "Legendary Artifact — Equipment",
        text = """
            Whenever equipped creature attacks, destroy target creature an opponent controls.
            Equipped creature gets +7/+7.
            Equip {7}
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))), destroy(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
        static(pt(7, 7), affects = equipped),
        Keyword.Equip(manaCost("{7}")),
    )

    @Test
    fun villainsDemise() = card(
        name = "Villain's Demise",
        cost = "{3}{B}",
        type = "Instant",
        text = "Destroy target creature. You gain 2 life.",
    ) parsesTo abilities(spell(destroy(target("creature")), gainLife(2), targets = listOf(slot("creature", creature))))

    @Test
    fun travelersRest() = card(
        name = "Traveler's Rest",
        cost = null,
        type = "Land — Town",
        text = """
            When this land enters, you gain 2 life.
            {T}: Add {C}.
        """,
    ) parsesTo abilities(triggered(entersSelf, gainLife(2)), addColorless)

    @Test
    fun harborTown() = card(
        name = "Harbor Town",
        cost = null,
        type = "Land — Town",
        text = """
            This land enters tapped.
            {T}: Add {U} or {R}.
        """,
    ) parsesTo abilities(entersTapped, addEither("U", "R"))

    @Test
    fun capitalCitadel() = card(
        name = "Capital Citadel",
        cost = null,
        type = "Land — Town",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        Keyword.Cycling(manaCost("{2}")),
    )

    // CR 614.12 (a colour chosen as it enters)
    @Test
    fun crossingVillage() = card(
        name = "Crossing Village",
        cost = null,
        type = "Land — Town",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        entersTapped,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(arcanum.api.ColorRef.Remembered("color")))), Players.You))),
    )

    // CR 603.12
    @Test
    fun edenSanctumSeat() = card(
        name = "Eden, Sanctum Seat",
        cost = null,
        type = "Land — Town",
        text = """
            {T}: Add {C}.
            {5}, {T}: Mill two cards. Then you may sacrifice this land. When you do, return another target permanent card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{5}"), tap), mill(2), Step.Pay(cost(sacrificeSelf), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(another, permanentCard)))))),
    )

    // CR 705
    @Test
    fun theGoldenArcade() = card(
        name = "The Golden Arcade",
        cost = null,
        type = "Land — Town",
        text = """
            {T}: Add {C}.
            {2}, {T}: Flip a coin. If you win the flip, create a Treasure token.
            {3}, {T}, Sacrifice two artifacts: Draw a card.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{2}"), tap), doing(Effect.FlipCoin(Players.You, bind = "flip")), ifThen(Condition.WonFlip("flip"), createTreasure)),
        activated(cost(mana("{3}"), tap, CostPart.Sacrifice(arcanum.api.CostObjects.Choose(artifact, n(2)))), draw(1)),
    )

    // CR 715 (an Adventure on a land, played later from exile)
    @Test
    fun holySeeTown() = cardFaces(Layout.Adventure,
        card(name = "Holy See of Ishvar", cost = null, type = "Land — Town", text = """
            This land enters tapped.
            {T}: Add {W}.
        """) to abilities(entersTapped, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{W}")!!)), Players.You)))),
        card(name = "Hope and Sorrow", cost = "{3}{W}{W}", type = "Sorcery — Adventure",
            text = "Return up to two target artifact and/or enchantment cards from your graveyard to your hand. (Then exile this card. You may play the land later from exile.)",
        ) to abilities(spell(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(or(artifact, enchantment)), min = 0, max = 2)))),
    )

    @Test
    fun aristocratsCapital() = cardFaces(Layout.Adventure,
        card(name = "Aristocrats' Capital", cost = null, type = "Land — Town", text = """
            This land enters tapped.
            {T}: Add {U}.
        """) to abilities(entersTapped, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)), Players.You)))),
        card(name = "Grand Overture", cost = "{4}{U}{U}", type = "Sorcery — Adventure",
            text = "Target opponent mills half their library, rounded down. (Then exile this card. You may play the land later from exile.)",
        ) to abilities(spell(doing(Effect.Mill(Players.Target("opponent"), Value.Half(Value.Count(Objects.Each(and(inZone(Zone.Library),
            ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))))), roundUp = false))), targets = listOf(targetOpponent))),
    )

    @Test
    fun forgeRegency() = cardFaces(Layout.Adventure,
        card(name = "Forge Regency", cost = null, type = "Land — Town", text = """
            This land enters tapped.
            {T}: Add {R}.
        """) to abilities(entersTapped, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You)))),
        card(name = "Siege of Mages", cost = "{2}{R}", type = "Instant — Adventure",
            text = "Create a 0/1 black Wizard creature token with \"Whenever you cast a noncreature spell, this token deals 1 damage to each opponent.\"",
        ) to abilities(spell(create(wizardToken))),
    )

    @Test
    fun cityOfMakoLights() = cardFaces(Layout.Adventure,
        card(name = "City of Glowing Mako", cost = null, type = "Land — Town", text = """
            This land enters tapped.
            {T}: Add {B}.
        """) to abilities(entersTapped, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!)), Players.You)))),
        card(name = "Reactor Heist", cost = "{2}{B}", type = "Sorcery — Adventure",
            text = "You may sacrifice an artifact or creature. If you do, draw two cards. (Then exile this card. You may play the land later from exile.)",
        ) to abilities(spell(Step.Pay(cost(sacrificeA(or(artifact, creature))), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(2)))),
    )

    @Test
    fun ancientMetropolis() = cardFaces(Layout.Adventure,
        card(name = "Ancient Metropolis of Zan", cost = null, type = "Land — Town", text = """
            This land enters tapped.
            {T}: Add {G}.
        """) to abilities(entersTapped, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You)))),
        card(name = "Enduring Faith", cost = "{4}{G}{G}", type = "Sorcery — Adventure",
            text = "Create a 1/1 colorless Hero creature token. Put a +1/+1 counter on it for each land you control. (Then exile this card. You may play the land later from exile.)",
        ) to abilities(spell(create(heroToken, bind = "tokens"), counters(Objects.Bound("tokens"), count(and(land, you))))),
    )

    private val charge = arcanum.api.CounterKind.Named("charge")
}
