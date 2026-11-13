package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Recipients
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MH3 chunk 09: manifest (CR 701.40, from a hand too), mill (CR 701.17), replicate (CR 702.56) with an energy cost, scry (CR 701.22),
 * and creatures that exile themselves and return transformed (CR 701.27) into planeswalkers.
 */
class Mh3Chunk09ManifestMillReplicateTransformTest {
    private val colorless = ObjectFilter.Colorless
    private val instantOrSorcery = or(instant, sorcery)
    private val stackAbility = ObjectFilter.Category(ObjectCategory.StackAbility)
    private val exileAndReturnTransformed = listOf(
        move(Objects.Self, Zone.Exile, postBind = "exiled"),
        Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))),
    )

    // CR 701.40a, 101.4: each target player chooses two cards in their hand; they are manifested together, each under its owner's control.
    // CR 603.6 (a cast trigger), 613.4c (+3/+2 for other colorless creatures)
    @Test
    fun vorzhakTheShatteredTruth() = card(
        name = "Vorzhak, the Shattered Truth",
        cost = "{9}",
        type = "Legendary Creature — Eldrazi",
        pt = "9/9",
        text = """
            When you cast this spell, up to two target players each manifest two cards from their hands. For each card manifested this way, you draw a card. (To manifest a card, put it onto the battlefield face down as a 2/2 creature. Turn it face up any time for its mana cost if it's a creature card.)
            Other colorless creatures you control get +3/+2.
        """,
    ) parsesTo abilities(
        triggered(youCastThis,
            Step.ForEach(Each.Players(Players.Target("players")), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))), n(2), n(2)),
                    bind = "chosen", chooser = Players.Bound("player")),
                Step.Do(Effect.Action(KeywordAction.Manifest(n(2), cards = Objects.Bound("chosen")), bind = "manifested")))),
            Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Bound("manifested")))),
            targets = listOf(TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(0), max = n(2))),
            functionsIn = null),
        static(Mechanic.ModifyPowerToughness(n(3), n(2)), affects = Objects.Each(and(another, colorless, creature, you))),
    )

    // CR 700.9 (modified), 701.40a (manifest the top card of your library)
    @Test
    fun wardenOfTheUnremembered() = card(
        name = "Warden of the Unremembered",
        cost = "{3}{W}",
        type = "Creature — Elephant Warrior",
        pt = "4/4",
        text = """
            Vigilance
            Whenever a modified creature you control dies, manifest the top card of your library. (Equipment, Auras you control, and counters are modifications. To manifest a card, put it onto the battlefield face down as a 2/2 creature. Turn it face up any time for its mana cost if it's a creature card.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(dies(and(creature, you, ObjectFilter.Modified)), Step.Do(Effect.Action(KeywordAction.Manifest(n(1))))),
    )

    // CR 603.2, 404.2: a creature card entering from its owner's (your) graveyard, or cast from your graveyard; CR 118.12 "you may pay … If you do";
    // CR 701.27a; CR 701.17a: "if a black card was milled this way" is any one of the milled cards; CR 707.9b: the copies are 1/1 black and green
    // Insects
    @Test
    fun grindleRavenousGrub() = cardFaces(Layout.Transform,
        card(
            name = "Grindle, Ravenous Grub",
            cost = "{G}",
            type = "Legendary Creature — Insect",
            pt = "1/2",
            text = """
                Deathtouch
                Whenever Grindle or another creature you control enters, if it entered from your graveyard or you cast it from your graveyard, you may pay {G}. If you do, exile Grindle, then return it to the battlefield transformed under its owner's control.
            """,
        ) to abilities(
            Keyword.Deathtouch,
            Ability.Triggered(Trigger.On(listOf(
                EventPattern.ZoneChange(and(creature, you, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard, to = Zone.Battlefield),
                EventPattern.ZoneChange(and(creature, you, ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.WasCast(from = Zone.Graveyard)), to = Zone.Battlefield))),
                Instructions(steps = listOf(Step.Pay(manaCost("{G}"), payer = Players.You, bind = "paid"),
                    ifThen(Condition.Did("paid"), *exileAndReturnTransformed.toTypedArray())))),
        ),
        card(
            name = "Grindle, the Plague Host",
            cost = null,
            type = "Legendary Planeswalker — Grist",
            colorIndicator = "BG",
            loyalty = "3",
            text = """
                [+1]: Create a 1/1 black and green Insect creature token, then mill two cards. Put a deathtouch counter on the token if a black card was milled this way.
                [−2]: Destroy target artifact or enchantment.
                [−6]: For each creature card in your graveyard, create a token that's a copy of it, except it's a 1/1 black and green Insect.
            """,
        ) to abilities(
            loyalty(1,
                create(creatureToken("Insect", listOf(Color.Black, Color.Green), 1, 1), bind = "token"),
                mill(2, postBind = "milled"),
                ifThen(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("milled"), and(ObjectFilter.OfColor(Color.Black), inZone(Zone.Graveyard)))),
                    Comparison.AtLeast, n(1)), counters(Objects.Bound("token"), 1, CounterKind.Keyword(Keyword.Deathtouch)))),
            loyalty(-2, destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            loyalty(-6, Step.ForEach(Each.Objects(Objects.Each(inYourGraveyard(creature))), "card", listOf(
                create(TokenSpec.CopyOf(Objects.Bound("card"), except = listOf(
                    CopyException.PowerToughness(1, 1),
                    CopyException.Colors(listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Green))),
                    CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Insect"))), replaceSubtypes = true))))))),
        ),
    )

    // CR 701.17a, 118.12: the cost is paid on resolution; one of the milled cards still in the graveyard goes to your hand
    @Test
    fun ripplesOfTheGrave() = card(
        name = "Ripples of the Grave",
        cost = "{1}{B}",
        type = "Enchantment",
        text = "At the beginning of your first main phase, mill three cards. Then you may pay {1} and 3 life. If you do, put a card from among those cards into your hand.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
            mill(3, postBind = "milled"),
            Step.Pay(cost(mana("{1}"), CostPart.PayLife(n(3))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Where(Objects.Bound("milled"), inZone(Zone.Graveyard))), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand))),
    )

    // CR 702.81a: retrace for nonland permanent cards in your graveyard during your turn; CR 701.17a
    @Test
    fun tallbarkElder() = card(
        name = "Tallbark Elder",
        cost = "{2}{G}",
        type = "Legendary Creature — Treefolk",
        pt = "2/4",
        text = """
            Reach
            Whenever Tallbark Elder attacks, mill three cards. You may put a land card from among them into your hand.
            During your turn, nonland permanent cards in your graveyard have retrace. (You may cast permanent cards from your graveyard by discarding a land card in addition to paying their other costs.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(attacksSelf,
            mill(3, postBind = "milled"),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        static(has(Keyword.Retrace), affects = Objects.Each(and(nonland, permanentCard, *yourGraveyard.toTypedArray())), condition = Condition.YourTurn),
    )

    // CR 702.108a prowess; CR 122.1 energy; CR 603.12 "When you do" (a reflexive trigger), 608.2h (its power as it last existed)
    @Test
    fun cyclopsVoltcaller() = card(
        name = "Cyclops Voltcaller",
        cost = "{1}{U}{R}",
        type = "Creature — Cyclops Wizard",
        pt = "2/2",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, you get {E}{E}{E} (three energy counters).
            When this creature dies, you may pay {E}{E}{E}. When you do, this creature deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        entersGetEnergy(3),
        triggered(diesSelf, *mayPayEnergyWhenYouDo(3, Instructions(targets = listOf(anyTarget), steps = listOf(damage(power(Objects.Self), toAny)))).toTypedArray()),
    )

    // CR 712 (modal double-faced), 702.108a; CR 614.12 (as this land enters, pay 3 life or it enters tapped)
    @Test
    fun summitMonk() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Summit Monk",
            cost = "{3}{R}{R}",
            type = "Creature — Djinn Monk",
            pt = "2/2",
            text = """
                Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
                When this creature enters, return target instant or sorcery card from your graveyard to your hand.
            """,
        ) to abilities(
            Keyword.Prowess,
            triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(instantOrSorcery)))),
        ),
        card(
            name = "Hermit Crag",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {R}.
            """,
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
                Step.Pay(Cost(listOf(CostPart.PayLife(n(3)))), payer = Players.You, bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")), Step.Modify(Modification.EntersTapped))))))), affects = Affected.Objects(Objects.Self)),
            addOne("{R}"),
        ),
    )

    // CR 702.56a replicate; CR 701.6a: a triggered ability or a colorless spell
    @Test
    fun commitToOblivion() = card(
        name = "Commit to Oblivion",
        cost = "{U}",
        type = "Instant",
        text = """
            Replicate {1} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)
            Counter target triggered ability or colorless spell.
        """,
    ) parsesTo abilities(
        Keyword.Replicate(manaCost("{1}")),
        spell(Step.Do(Effect.Counter(target("spell"))), targets = listOf(slot("spell", or(
            and(stackAbility, ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Triggered)), and(colorless, spellFilter))))),
    )

    // CR 702.56a: a replicate cost that isn't mana (three energy counters each time, CR 107.14)
    @Test
    fun recurringBolt() = card(
        name = "Recurring Bolt",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Replicate—Pay {E}{E}{E}. (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)
            Recurring Bolt deals 3 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Keyword.Replicate(cost(payEnergy(3))),
        spell(damage(3, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 700.2 (choose two), 107.3a (X announced as it is cast), 701.22a scry, 111.1 tokens for the target player
    @Test
    fun vorzhaksDecree() = card(
        name = "Vorzhak's Decree",
        cost = "{X}{C}{C}",
        type = "Kindred Instant — Eldrazi",
        text = """
            Choose two —
            • Target player creates X 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
            • Target player scries X, then draws a card.
            • Exile target creature with mana value X or less.
            • Exile up to X target cards from graveyards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(announced = listOf("X"), modes = modes(
            mode(Step.Do(Effect.CreateToken(eldraziSpawn, Value.Announced("X"), Players.Target("player"))), targets = listOf(targetPlayer)),
            mode(Step.Do(Effect.Scry(Players.Target("player"), Value.Announced("X"))), draw(1, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced("X")))))),
            mode(move(target("cards"), Zone.Exile), targets = listOf(TargetSlot("cards", TargetKind.Objects(and(cardFilter, inZone(Zone.Graveyard))), min = n(0), max = Value.Announced("X")))),
            min = 2, max = 2))),
    )

    // CR 701.22a: scry after the draw
    @Test
    fun serumSeer() = card(
        name = "Serum Seer",
        cost = "{2}{U}",
        type = "Creature — Vedalken Wizard",
        pt = "2/2",
        text = "When this creature enters, draw a card, then scry 2.",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), scry(2)),
    )

    // CR 122.1b: a flying counter gives flying; CR 701.22a
    @Test
    fun takeWingNow() = card(
        name = "Take Wing Now",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. Put a flying counter on it. Scry 1.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2), counters(target("creature"), 1, CounterKind.Keyword(Keyword.Flying)), scry(1), targets = listOf(slot("creature", creature))),
    )

    // CR 109.4: "a blue permanent other than this creature" in "if you control …" is one you control (the condition of Rhal's [−2] written
    // with "this creature")
    @Test
    fun mistcallerAdept() = card(
        name = "Mistcaller Adept",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/2",
        text = "When this creature enters, draw a card if you control a blue permanent other than this creature.",
    ) parsesTo abilities(
        triggered(entersSelf, ifThen(Condition.Exists(and(ObjectFilter.OfColor(Color.Blue), permanentFilter, another, you), 1), draw(1))),
    )

    // CR 603.12, 603.4: a reflexive trigger whose "if" is checked as it triggers and resolves; CR 701.21a: each opponent keeps one of each type
    // named and sacrifices the rest together
    private val redOtherThanSelf = Condition.Exists(and(ObjectFilter.OfColor(Color.Red), permanentFilter, another, you), 1)
    private val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))

    @Test
    fun avaniPridemaneExile() = cardFaces(Layout.Transform,
        card(
            name = "Avani, Pridemane Exile",
            cost = "{1}{W}",
            type = "Legendary Creature — Cat Warrior",
            pt = "1/2",
            text = """
                When Avani enters, create a 2/1 white Cat Warrior creature token.
                Whenever one or more other Cats you control die, you may exile Avani, then return him to the battlefield transformed under his owner's control.
            """,
        ) to abilities(
            triggered(entersSelf, create(creatureToken("Cat Warrior", listOf(Color.White), 2, 1))),
            Ability.Triggered(Trigger.On(EventPattern.ZoneChange(and(another, sub("Cat"), you), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
                Instructions(steps = listOf(Step.May(exileAndReturnTransformed, Players.You)))),
        ),
        card(
            name = "Avani, Pridemane Avenger",
            cost = null,
            type = "Legendary Planeswalker — Ajani",
            colorIndicator = "RW",
            loyalty = "3",
            text = """
                [+2]: Put a +1/+1 counter on each Cat you control.
                [0]: Create a 2/1 white Cat Warrior creature token. When you do, if you control a red permanent other than Avani, he deals damage equal to the number of creatures you control to any target.
                [−4]: Each opponent chooses an artifact, a creature, an enchantment, and a planeswalker from among the nonland permanents they control, then sacrifices the rest.
            """,
        ) to abilities(
            loyalty(2, counters(Objects.Each(and(sub("Cat"), you)), 1)),
            loyalty(0, create(creatureToken("Cat Warrior", listOf(Color.White), 2, 1)),
                ifThen(redOtherThanSelf, Step.Do(Effect.ReflexiveTrigger(Instructions(targets = listOf(anyTarget), steps = listOf(
                    ifThen(redOtherThanSelf, damage(count(creatureYouControl), toAny)))))))),
            loyalty(-4, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "player", together = true,
                steps = listOf(artifact, creature, enchantment, ObjectFilter.Type(CardType.Planeswalker)).zip(listOf("artifact", "creature", "enchantment", "planeswalker")).map { (type, word) ->
                    Step.Choose(Choice.Objects(and(type, nonland, permanentFilter, theirs), n(1), n(1)), bind = "kept $word", chooser = Players.Bound("player"))
                } + Step.Do(Effect.Sacrifice(Objects.Except(Objects.Each(and(nonland, permanentFilter, theirs)), Objects.Union(listOf("artifact", "creature", "enchantment", "planeswalker")
                    .map { Objects.Bound("kept $it") })))))),
        ),
    )

    // CR 306.5b, 614.1c: it enters with its printed loyalty plus one for each instant and sorcery spell cast this turn; CR 611.2a (until your next
    // turn), 601.2f; CR 608.2g, 118.9 (cast from among the exiled cards without paying)
    @Test
    fun rhalStormAcolyte() = cardFaces(Layout.Transform,
        card(
            name = "Rhal, Storm Acolyte",
            cost = "{1}{R}",
            type = "Legendary Creature — Human Wizard",
            pt = "1/3",
            text = """
                Instant and sorcery spells you cast cost {1} less to cast.
                Whenever you cast an instant or sorcery spell during your turn, flip a coin. If you lose the flip, Rhal deals 1 damage to you. If you win the flip, you may exile Rhal. If you do, return him to the battlefield transformed under his owner's control.
            """,
        ) to abilities(
            static(Mechanic.CostChange(and(instantOrSorcery, spellFilter, you), arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1)))),
            triggered(youCast(instantOrSorcery),
                Step.Do(Effect.FlipCoin(Players.You, bind = "flip")),
                ifThen(Condition.Not(Condition.WonFlip("flip")), damage(1, Recipients(players = Players.You))),
                ifThen(Condition.WonFlip("flip"),
                    Step.Pay(cost(CostPart.Exile(arcanum.api.CostObjects.Self, from = Zone.Battlefield, postBind = "exiled")), payer = Players.You, bind = "paid"),
                    ifThen(Condition.Did("paid"), exileAndReturnTransformed[1])),
                triggersOnlyIf = Condition.YourTurn),
        ),
        card(
            name = "Rhal, Leyline Savant",
            cost = null,
            type = "Legendary Planeswalker — Ral",
            colorIndicator = "UR",
            loyalty = "2",
            text = """
                Rhal enters with an additional loyalty counter on him for each instant and sorcery spell you've cast this turn.
                [+1]: Until your next turn, instant and sorcery spells you cast cost {1} less to cast.
                [−2]: Rhal deals 2 damage divided as you choose among one or two targets. Draw a card if you control a blue permanent other than Rhal.
                [−8]: Exile the top eight cards of your library. You may cast instant and sorcery spells from among them this turn without paying their mana costs.
            """,
        ) to abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(
                CounterKind.Loyalty, Value.EventsThisTurn(EventPattern.Cast(instantOrSorcery, PlayerFilter.You), Measure.Events))))),
            loyalty(1, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CostChange(and(instantOrSorcery, spellFilter, you), arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1)))),
                null, Duration.UntilYourNextTurn))),
            loyalty(-2,
                damage(Value.DividedShare, toAny),
                ifThen(Condition.Exists(and(ObjectFilter.OfColor(Color.Blue), permanentFilter, another, you), 1), draw(1)),
                targets = listOf(anyTarget.copy(max = n(2), divided = n(2)))),
            loyalty(-8,
                move(Objects.LibraryCards(Players.You, n(8), fromTop = true), Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Cast, resultingSpell = instantOrSorcery, withoutPaying = true,
                    holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
        ),
    )

    // CR 603.4 (intervening if on each postcombat main phase, CR 505.1a), 701.27a; CR 702.101a extort; CR 120.3 (damage equal to life gained this
    // turn); CR 205.1b (becomes a Vampire in addition); CR 122.1b (a lifelink counter if you control another white permanent)
    @Test
    fun sorvinOfHouseMarkas() = cardFaces(Layout.Transform,
        card(
            name = "Sorvin of House Markas",
            cost = "{1}{B}",
            type = "Legendary Creature — Human Noble",
            pt = "1/4",
            text = """
                Lifelink
                Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
                At the beginning of each of your postcombat main phases, if you gained 3 or more life this turn, exile Sorvin, then return him to the battlefield transformed under his owner's control.
            """,
        ) to abilities(
            Keyword.Lifelink,
            Keyword.Extort,
            triggered(stepBegins(TurnStep.PostcombatMain, PlayerFilter.You), *exileAndReturnTransformed.toTypedArray(),
                interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), 3, Measure.Amount)),
        ),
        card(
            name = "Sorvin, Ravenous Fledgling",
            cost = null,
            type = "Legendary Planeswalker — Sorin",
            colorIndicator = "WB",
            loyalty = "3",
            text = """
                Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
                [+2]: Create a Food token.
                [−1]: Sorvin deals damage equal to the amount of life you gained this turn to any target.
                [−6]: Gain control of target creature. It becomes a Vampire in addition to its other types. Put a lifelink counter on it if you control a white permanent other than that creature or Sorvin.
            """,
        ) to abilities(
            Keyword.Extort,
            loyalty(2, create(food)),
            loyalty(-1, damage(Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount), toAny), targets = listOf(anyTarget)),
            loyalty(-6,
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.Indefinitely)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire"))))), Affected.Objects(target("creature")), Duration.Indefinitely)),
                ifThen(Condition.Exists(and(not(ObjectFilter.Is(target("creature"))), another, ObjectFilter.OfColor(Color.White), permanentFilter, you), 1),
                    counters(target("creature"), 1, CounterKind.Keyword(Keyword.Lifelink))),
                targets = listOf(slot("creature", creature))),
        ),
    )
}
