package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
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
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MH3 chunk 08: Equipment (living weapon, equip paid with energy), escape, exert, hexproof from abilities, investigate, kicker (two kicker
 * costs, "instead choose"), landfall.
 */
class Mh3Chunk08EquipEscapeKickerLandfallTest {
    private val artifactsYouControl = count(and(artifact, you))

    // CR 702.92a: living weapon; CR 613.4c: +X/+1 where X is counted as it applies
    @Test
    fun cranialRam() = card(
        name = "Cranebone Butter",
        cost = "{B}{R}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +X/+1, where X is the number of artifacts you control.
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        static(Mechanic.ModifyPowerToughness(artifactsYouControl, n(1)), affects = equipped),
        equip("{2}"),
    )

    // CR 702.6a: an equip cost paid with energy (CR 107.14)
    @Test
    fun inventorsAxe() = card(
        name = "Tinkerer's Hatchet",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, you get {E}{E} (two energy counters).
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +2/+0.
            Equip—Pay {E}{E}.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        entersGetEnergy(2),
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
        static(pt(2, 0), affects = equipped),
        Keyword.Equip(cost(payEnergy(2))),
    )

    @Test
    fun colossalDreadmask() = card(
        name = "Towering Dreadhelm",
        cost = "{4}{G}{G}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +6/+6 and has trample.
            Equip {3}{G}{G}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        static(pt(6, 6), has(Keyword.Trample), affects = equipped),
        equip("{3}{G}{G}"),
    )

    // CR 508.1m: "whenever equipped creature attacks"
    @Test
    fun drossclaw() = card(
        name = "Slagtalon",
        cost = "{1}{B}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +1/+1.
            Whenever equipped creature attacks, each opponent loses 1 life.
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        static(pt(1, 1), affects = equipped),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))), loseLife(1, Players.Each(PlayerFilter.Opponent))),
        equip("{2}"),
    )

    @Test
    fun mandibularKite() = card(
        name = "Jawbone Glider",
        cost = "{W}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +1/+1 and has flying.
            Equip {3}{W}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        static(pt(1, 1), has(Keyword.Flying), affects = equipped),
        equip("{3}{W}"),
    )

    // CR 702.138c: "unless it escaped"; CR 702.138a: the escape cost exiles five other cards from your graveyard
    @Test
    fun phlage() = card(
        name = "Pyrrhax, Titan of Ember's Wrath",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Elder Giant",
        pt = "6/6",
        text = """
            When Pyrrhax enters, sacrifice it unless it escaped.
            Whenever Pyrrhax enters or attacks, it deals 3 damage to any target and you gain 3 life.
            Escape—{R}{R}{W}{W}, Exile five other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, ifThen(Condition.Not(Condition.Paid(Keyword.Escape.Kind, times = 1, of = Objects.Self)),
            Step.Do(Effect.Sacrifice(Objects.Self)))),
        triggered(entersOrAttacksSelf, damage(3, toAny), gainLife(3), targets = listOf(anyTarget)),
        Keyword.Escape(Cost(listOf(mana("{R}{R}{W}{W}"), CostPart.Exile(CostObjects.Choose(and(another, cardFilter), count = n(5)), from = Zone.Graveyard)))),
    )

    // CR 701.43a: exert as a cost; CR 106.6a: the spell any of that mana is spent on gains haste until end of turn
    @Test
    fun arenaOfGlory() = card(
        name = "Coliseum of Valor",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Mountain.
            {T}: Add {R}.
            {R}, {T}, Exert this land: Add {R}{R}. If any of that mana is spent on a creature spell, it gains haste until end of turn. (An exerted permanent won't untap during your next untap step.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(arcanum.api.Modification.EntersTapped))), affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.Exists(and(sub("Mountain"), you), atLeast = 1))),
        addOne("{R}"),
        activated(cost(mana("{R}"), tap, CostPart.Action(arcanum.api.KeywordAction.Exert(Objects.Self))), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!),
            spellGets = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), spellGetsIf = and(creature, spellFilter), spellGetsUntil = Duration.EndOfTurn), Players.You))),
    )

    // CR 702.11d, 702.11f: hexproof from activated and triggered abilities; CR 701.12b: "if you do" is that the exchange happened
    @Test
    fun volatileStormdrake() = card(
        name = "Fickle Squalldrake",
        cost = "{1}{U}",
        type = "Creature — Drake",
        pt = "3/2",
        text = """
            Flying, hexproof from activated and triggered abilities
            When this creature enters, exchange control of this creature and target creature an opponent controls. If you do, you get {E}{E}{E}{E}, then sacrifice that creature unless you pay an amount of {E} equal to its mana value.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.HexproofFrom(or(
            and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated)),
            and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Triggered)))),
        triggered(entersSelf,
            Step.Do(Effect.ExchangeControl(Objects.Self, target("creature"))),
            ifThen(Condition.ObjectMatches(target("creature"), you),
                getEnergy(4),
                Step.Pay(cost(CostPart.PayEnergy(Value.Of(NumericProperty.ManaValue, target("creature"), Combine.Single))), payer = Players.You, bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")), Step.Do(Effect.Sacrifice(target("creature"))))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 714.2b: chapter abilities; CR 701.16a: investigate twice for each discarded card
    @Test
    fun tamiyoMeetsTheStoryCircle() = card(
        name = "Moonfolk Hears the Tale",
        cost = "{1}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Until your next turn, whenever a creature attacks you or a planeswalker you control, it gets -2/-0 until end of turn.
            II — Discard any number of cards, then investigate twice for each card discarded this way.
            III — Shuffle up to three target cards from your graveyard into your library.
        """,
    ) parsesTo abilities(
        chapter(1, Step.Do(Effect.CreateDelayedTrigger(on(EventPattern.Attack(creature, target = RecipientFilter(objects = and(ObjectFilter.Type(CardType.Planeswalker), you),
            players = PlayerFilter.You))), Instructions(steps = listOf(pump(Objects.EventObject(EventRole.Object), -2, 0))), duration = Duration.UntilYourNextTurn, repeats = true))),
        chapter(2,
            Step.Choose(arcanum.api.Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))), n(0), null), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
            investigate(Value.Times(Value.Count(Objects.Bound("discarded")), 2))),
        chapter(3, Step.Do(Effect.Move(target("cards"), Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.You)),
            targets = listOf(slot("cards", inYourGraveyard(cardFilter), min = 0, max = 3))),
    )

    // CR 712: transforming; CR 606.4: loyalty abilities; "if it's a green card" is the returned card (CR 400.7); CR 107.1a: rounded up
    @Test
    fun tamiyoInquisitiveStudent() = cardFaces(
        Layout.Transform,
        card(
            name = "Kiyome, Curious Pupil",
            cost = "{U}",
            type = "Legendary Creature — Moonfolk Wizard",
            pt = "0/3",
            text = """
                Flying
                Whenever Kiyome attacks, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
                When you draw your third card in a turn, exile Kiyome, then return her to the battlefield transformed under her owner's control.
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(attacksSelf, investigate()),
            triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 3),
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true)))),
        ),
        card(
            name = "Kiyome, Learned Sage",
            cost = null,
            type = "Legendary Planeswalker — Tamiyo",
            loyalty = "2",
            text = """
                [+2]: Until your next turn, whenever a creature attacks you or a planeswalker you control, it gets -1/-0 until end of turn.
                [−3]: Return target instant or sorcery card from your graveyard to your hand. If it's a green card, add one mana of any color.
                [−7]: Draw cards equal to half the number of cards in your library, rounded up. You get an emblem with "You have no maximum hand size."
            """,
        ) to abilities(
            loyalty(2, Step.Do(Effect.CreateDelayedTrigger(on(EventPattern.Attack(creature, target = RecipientFilter(objects = and(ObjectFilter.Type(CardType.Planeswalker), you),
                players = PlayerFilter.You))), Instructions(steps = listOf(pump(Objects.EventObject(EventRole.Object), -1, 0))), duration = Duration.UntilYourNextTurn, repeats = true))),
            loyalty(-3, move(target("card"), Zone.Hand),
                ifThen(Condition.ObjectMatches(target("card"), ObjectFilter.OfColor(Color.Green)),
                    Step.Do(Effect.AddMana(ManaProduction(arcanum.parser.cube.anyOneColor(1)), Players.You))),
                targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
            loyalty(-7, Step.Do(Effect.Draw(Players.You, Value.Half(Value.Count(Objects.Each(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))), roundUp = true))),
                Step.Do(Effect.CreateEmblem(listOf(playerStatic(Mechanic.NoMaximumHandSize)), Players.You))),
        ),
    )

    // CR 702.33d: only the version that happens chooses its target (so the total of the pair is the chosen one's); CR 608.2h: the exiled
    // creature's mana value and controller as it last existed
    @Test
    fun expelTheUnworthy() = card(
        name = "Banish the Undeserving",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
            Choose target creature with mana value 3 or less. If this spell was kicked, instead choose target creature. Exile the chosen creature, then its controller gains life equal to its mana value.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}{W}"))),
        spell(move(chosen, Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.GainLife(Players.ControllerOf(chosen), Value.Of(NumericProperty.ManaValue, chosen, Combine.Total))),
            targets = listOf(
                slot("creature", and(creature, manaValue(Comparison.AtMost, 3))).copy(onlyIf = Condition.Not(kicked)),
                slot("kicked creature", creature).copy(onlyIf = kicked))),
    )
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private val chosen = Objects.Union(listOf(target("creature"), target("kicked creature")))

    // CR 101.2: "can't" beats "can"; CR 702.33d: the kicked part
    @Test
    fun orimsChant() = card(
        name = "Velma's Hush",
        cost = "{W}",
        type = "Instant",
        text = """
            Kicker {W} (You may pay an additional {W} as you cast this spell.)
            Target player can't cast spells this turn. If this spell was kicked, creatures can't attack this turn.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{W}"))),
        spell(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(ObjectFilter.Any)), Affected.Players(Players.Target("player")), Duration.EndOfTurn)),
            ifThen(kicked, untilEndOfTurn(Objects.Each(creature), Mechanic.CantAttack())),
            targets = listOf(targetPlayer)),
    )

    // CR 702.33b: two kicker costs; CR 702.33f: "kicked with its {G} kicker" is the first of them; CR 603.4: checked as it triggers and resolves
    @Test
    fun wastescapeBattlemage() = card(
        name = "Blightfield Warmage",
        cost = "{1}{C}",
        type = "Creature — Eldrazi Wizard",
        pt = "2/2",
        text = """
            Kicker {G} and/or {1}{U}
            When you cast this spell, if it was kicked with its {G} kicker, exile target artifact or enchantment an opponent controls.
            When you cast this spell, if it was kicked with its {1}{U} kicker, return target creature an opponent controls to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{G}"), manaCost("{1}{U}"))),
        triggered(youCastThis, move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(or(artifact, enchantment), opponents))),
            interveningIf = Condition.Paid(Keyword.Kicker.Kind, times = 1, costIndex = 0, of = Objects.Self), functionsIn = null),
        triggered(youCastThis, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creatureOpponentControls)),
            interveningIf = Condition.Paid(Keyword.Kicker.Kind, times = 1, costIndex = 1, of = Objects.Self), functionsIn = null),
    )

    // CR 207.2c: landfall; CR 602.5d: sorcery timing; CR 601.2f: {1} less for each quest counter
    @Test
    fun questForTheNecropolis() = card(
        name = "Quest for the Bone Halls",
        cost = "{B}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, put a quest counter on this enchantment.
            {5}{B}, Sacrifice this enchantment: Put target creature card from a graveyard onto the battlefield under your control. This ability costs {1} less to activate for each quest counter on this enchantment. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(landfall, counters(Objects.Self, 1, CounterKind.Named("quest")), abilityWord = "Landfall"),
        activated(cost(mana("{5}{B}"), sacrificeSelf),
            Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, controller = Players.You))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard)))),
            rules = asSorcery.copy(costChange = arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!,
                Value.Of(NumericProperty.Counters(CounterKind.Named("quest")), Objects.Self, Combine.Single)))),
    )

    // CR 107.14: "Pay six {E}"
    @Test
    fun roilCartographer() = card(
        name = "Churn Mapmaker",
        cost = "{1}{U}",
        type = "Creature — Merfolk Rogue",
        pt = "1/3",
        text = """
            Landfall — Whenever a land you control enters, you get {E} (an energy counter).
            {T}, Pay six {E}: Draw three cards.
        """,
    ) parsesTo abilities(
        triggered(landfall, getEnergy(1), abilityWord = "Landfall"),
        activated(cost(tap, payEnergy(6)), draw(3)),
    )

    // CR 702.91a: battle cry, gained until end of turn
    @Test
    fun recklessPyrosurfer() = card(
        name = "Heedless Flamerider",
        cost = "{1}{R}",
        type = "Creature — Human Scout",
        pt = "2/2",
        text = """
            Haste
            Landfall — Whenever a land you control enters, this creature gains battle cry until end of turn. (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn. Each instance of battle cry triggers separately.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(landfall, gains(Objects.Self, Keyword.BattleCry), abilityWord = "Landfall"),
    )

    // CR 614.1a: "instead"; CR 702.52a: dredge granted to land cards in your graveyard
    @Test
    fun theNecrobloom() = card(
        name = "The Gravebloom",
        cost = "{1}{W}{B}{G}",
        type = "Legendary Creature — Plant",
        pt = "2/7",
        text = """
            Landfall — Whenever a land you control enters, create a 0/1 green Plant creature token. If you control seven or more lands with different names, create a 2/2 black Zombie creature token instead.
            Land cards in your graveyard have dredge 2. (You may return a land card from your graveyard to your hand and mill two cards instead of drawing a card.)
        """,
    ) parsesTo abilities(
        triggered(landfall, ifThen(sevenDifferentLands, create(creatureToken("Zombie", listOf(Color.Black), 2, 2)),
            otherwise = listOf(create(creatureToken("Plant", listOf(Color.Green), 0, 1)))), abilityWord = "Landfall"),
        static(has(Keyword.Dredge(2)), affects = Objects.Each(inYourGraveyard(land))),
    )
    private val sevenDifferentLands = Condition.Compare(Value.CountDistinct(Objects.Each(and(land, you)), arcanum.api.SharedQuality.Name), Comparison.AtLeast, n(7))
}
