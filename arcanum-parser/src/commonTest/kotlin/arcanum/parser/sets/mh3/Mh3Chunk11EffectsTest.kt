package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CopyException
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
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
 * MH3 chunk 11: changing a spell's target (CR 115.7), modes that haven't been chosen this turn (CR 700.2), tokens defined by reminder
 * text (CR 111.4), moving a counter (CR 122.1), land types set (CR 305.7), discover X (CR 701.57), and protection from spells and from
 * permanents cast this turn (CR 702.16).
 */
class Mh3Chunk11EffectsTest {
    private val entersEvent = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val creatureOrPlaneswalker = or(creature, planeswalker)

    // CR 115.7a: the new target is this creature, if it's legal; "you may" lets the controller leave the target as it is
    @Test
    fun spillwayOddity() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Spillway Oddity",
            cost = "{2}{U}",
            type = "Creature — Weird",
            pt = "1/4",
            text = """
                Flash
                When this creature enters, you may change the target of target instant or sorcery spell with a single target to this creature.
            """,
        ) to abilities(
            Keyword.Flash,
            triggered(entersSelf,
                Step.May(listOf(Step.Do(Effect.ChangeTargets(target("spell"), chooser = Players.You, optional = false, to = Objects.Self))), Players.You),
                targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter, ObjectFilter.SingleTarget())))),
        ),
        card(
            name = "Spillway Works",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {U}.
            """,
        ) to abilities(
            static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.AsEnters(listOf(
                Step.Pay(Cost(listOf(CostPart.PayLife(n(3)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped)))))))),
            addOne("{U}"),
        ),
    )

    // CR 700.2: a mode chosen earlier this turn can't be chosen again; each mode creates a token with its characteristics (CR 111.1)
    @Test
    fun mirelleShapeWeaver() = card(
        name = "Mirelle, Shape Weaver",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Moonfolk Wizard",
        pt = "2/5",
        text = """
            Whenever another nontoken permanent you control leaves the battlefield, choose one that hasn't been chosen this turn. Create a creature token with those characteristics.
            • 2/2 white Fox with vigilance.
            • 1/2 blue Moonfolk with flying.
            • 1/1 black Rat with lifelink.
            {3}{W}{U}: Put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(on(EventPattern.ZoneChange(and(another, not(token), permanentFilter, you), from = Zone.Battlefield)), Instructions(modes = Modes(listOf(
            mode(create(creatureToken("Fox", listOf(Color.White), 2, 2, abilities = listOf(Keyword.Vigilance)))),
            mode(create(creatureToken("Moonfolk", listOf(Color.Blue), 1, 2, abilities = listOf(Keyword.Flying)))),
            mode(create(creatureToken("Rat", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Lifelink)))),
        ), min = 1, max = 1, onlyUnchosenThisTurn = true, chooser = Players.You))),
        activated(manaCost("{3}{W}{U}"), counters(Objects.Each(creatureYouControl))),
    )

    // CR 700.2, 122.1 (energy counters)
    @Test
    fun hopefulTinkerer() = card(
        name = "Hopeful Tinkerer",
        cost = "{2}{W}",
        type = "Creature — Human Artificer",
        pt = "2/2",
        text = """
            When this creature enters, choose one —
            • You get {E}{E}{E} (three energy counters).
            • Put a +1/+1 counter on target creature.
            • Create a 1/1 colorless Servo artifact creature token.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(getEnergy(3)),
            mode(counters(target("creature")), targets = listOf(slot("creature", creature))),
            mode(create(creatureToken("Servo", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature)))),
        ))),
    )

    // CR 701.21a: "If you do" holds only when a creature was sacrificed; CR 608.2b: a target that's no longer legal stays where it is
    @Test
    fun grimExchange() = card(
        name = "Grim Exchange",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Choose two target creature cards in your graveyard. Sacrifice a creature. If you do, return the chosen cards to the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"), postBind = "sacrificed")),
            ifThen(Condition.NonEmpty("sacrificed"), move(target("cards"), Zone.Battlefield, tapped = true)),
            targets = listOf(slot("cards", inYourGraveyard(creature), min = 2, max = 2)),
        ),
    )

    // CR 707.9b: the copy has the quoted ability too; CR 614.12: it's chosen as it would enter
    @Test
    fun echoingRitual() = card(
        name = "Echoing Ritual",
        cost = "{2}{U}",
        type = "Enchantment",
        text = "You may have this enchantment enter as a copy of an enchantment you control, except it has \"At the beginning of your upkeep, you may exile this enchantment. If you do, return it to the battlefield under its owner's control.\"",
    ) parsesTo abilities(
        static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.EntersAsCopy(and(enchantment, you), listOf(CopyException.AddAbilities(listOf(
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
                Step.Pay(Cost(listOf(CostPart.Exile(arcanum.api.CostObjects.Self, from = Zone.Battlefield, postBind = "exiled"))), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")),
                    postBind = "returned"))),
        ))), optional = true)))),
    )

    // CR 122.1: one counter of a kind the controller chooses moves; the second target is another permanent
    @Test
    fun broodingHollow() = card(
        name = "Brooding Hollow",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Move a counter from target permanent you control onto a second target permanent. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        addOne("{C}"),
        activated(cost(mana("{1}"), tap),
            doing(Effect.MoveCounters(target("permanent"), target("second permanent"), kind = null, amount = n(1), inAll = true)),
            targets = listOf(slot("permanent", and(permanentFilter, you)),
                TargetSlot("second permanent", TargetKind.Objects(permanentFilter), min = n(1), max = n(1), distinctFrom = listOf("permanent"))),
            rules = asSorcery),
    )

    // CR 714.2b (chapter abilities); CR 111.4, 202.2: the token named in chapter III has the mana cost its reminder gives, so it's red
    @Test
    fun vexaAndTheSpiralVault() = card(
        name = "Vexa and the Spiral Vault",
        cost = "{3}{R}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — This Saga deals 2 damage to each creature and planeswalker your opponents control.
            II — You may discard a card. If you do, exile the top two cards of your library. You may play them until the end of your next turn.
            III — Create a Spellsipper Weird token. (It's a {2}{R} 2/2 Weird creature with "Whenever you cast a noncreature spell, put a +1/+1 counter on Spellsipper Weird.")
        """,
    ) parsesTo abilities(
        chapter(1, damage(2, toObjects(Objects.Each(and(creatureOrPlaneswalker, opponents))))),
        chapter(2,
            Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"),
            // the permission is about the cards exiled this way: none when no card was discarded (CR 608.2c)
            ifThen(Condition.Did("paid"), move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
        chapter(3, Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Spellsipper Weird", manaCost = ManaCost.parse("{2}{R}"),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Weird")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2),
            abilities = listOf(triggered(youCast(noncreatureSpell), counters(Objects.Self))))), n(1), Players.You, bind = "created"))),
    )

    // CR 608.2h: the damage is the returned card's mana value; "up to one" target may be none (CR 115.1)
    @Test
    fun cinderReturn() = card(
        name = "Cinder Return",
        cost = "{2}{B}{R}",
        type = "Instant",
        text = "Return target artifact or creature card from your graveyard to your hand. Cinder Return deals damage equal to that card's mana value to up to one target creature or planeswalker.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Hand, postBind = "returned"),
            damage(Value.Of(NumericProperty.ManaValue, Objects.Bound("returned"), Combine.Single), toObjects(target("permanent"))),
            targets = listOf(slot("card", inYourGraveyard(or(artifact, creature))), slot("permanent", creatureOrPlaneswalker, min = 0)),
        ),
    )

    // CR 701.9b (a random discard), 118.12 ("unless you pay": paying is optional), 107.14 (energy)
    @Test
    fun surgeOfInsight() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Surge of Insight",
            cost = "{1}{U/R}{U/R}",
            type = "Instant",
            text = "Draw two cards. Then discard a card at random unless you pay {E}{E} (two energy counters).",
        ) to abilities(
            spell(draw(2), *unlessYouPayEnergy(2, Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, random = true))).toTypedArray()),
        ),
        card(
            name = "Sparking Rapids",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {U} or {R}.
            """,
        ) to abilities(entersTapped, addEither("U", "R")),
    )

    // CR 701.57a: discover X, X the mana value of the spell that was cast (CR 202.3)
    @Test
    fun titanicMaelstrom() = card(
        name = "Titanic Maelstrom",
        cost = "{3}{G}",
        type = "Enchantment",
        text = "Whenever you cast a creature spell with power 5 or greater, discover X, where X is that spell's mana value. (Exile cards from the top of your library until you exile a nonland card with that mana value or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)",
    ) parsesTo abilities(
        triggered(youCast(and(creature, power(arcanum.api.Comparison.AtLeast, 5))),
            Step.Do(Effect.Action(KeywordAction.Discover(Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single))))),
    )

    // CR 105.2: each of its colours counts once; CR 608.2h: the destroyed permanent's colours as it last existed
    @Test
    fun lastExhale() = card(
        name = "Last Exhale",
        cost = "{1}{B}{B}",
        type = "Instant",
        text = "Destroy target creature or planeswalker. You gain 1 life for each of its colors.",
    ) parsesTo abilities(
        spell(
            destroy(target("permanent")),
            Step.Do(Effect.GainLife(Players.You, Value.SharedColors(target("permanent"), Color.all.map { ColorRef.Literal(it) }))),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ),
    )

    // CR 603.6 (a cast trigger), 702.16a (protection from either quality; a permanent cast this turn, CR 400.7d), 702.35a (madness with
    // a cost of six colorless mana, CR 107.4c), 701.21a (sacrifice all)
    @Test
    fun vorzhalTheUnbound() = card(
        name = "Vorzhal, the Unbound",
        cost = "{12}",
        type = "Legendary Creature — Eldrazi",
        pt = "12/12",
        text = """
            When you cast this spell, gain control of all creatures target player controls.
            Flying, protection from spells and from permanents that were cast this turn
            When Vorzhal leaves the battlefield, sacrifice all creatures you control.
            Madness—Pay six {C}.
        """,
    ) parsesTo abilities(
        Ability.Triggered(youCastThis, Instructions(targets = listOf(targetPlayer), steps = listOf(Step.Do(Effect.ApplyMechanics(
            listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))))),
            Duration.Indefinitely)))), functionsIn = null),
        Keyword.Flying,
        Keyword.Protection(or(spellFilter, and(permanentFilter, ObjectFilter.WasCast(), ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))))),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), doing(Effect.Sacrifice(Objects.Each(creatureYouControl)))),
        Keyword.Madness(manaCost("{C}{C}{C}{C}{C}{C}")),
    )

    // CR 603.2 (a trigger on becoming a target), 613.1f (layer 6: each creature gets the ability, counted twice a turn for each), 701.20a
    @Test
    fun pellisFeatheredMind() = card(
        name = "Pellis, Feathered Mind",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Bird Wizard",
        pt = "3/4",
        text = """
            Flying
            Creatures you control have "Whenever this creature becomes the target of a spell or ability, reveal the top card of your library. If it's a land card, put it onto the battlefield. Otherwise, put it into your hand. This ability triggers only twice each turn."
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), by = ObjectFilter.Any)),
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
            ifThen(Condition.ObjectMatches(Objects.Bound("revealed"), land), move(Objects.Bound("revealed"), Zone.Battlefield),
                otherwise = listOf(move(Objects.Bound("revealed"), Zone.Hand))),
            perTurn = 2)), affects = Objects.Each(creatureYouControl)),
    )

    // CR 305.7: nonbasic lands become Islands, losing their other land types and the abilities of their rules text (layer 4, 6)
    @Test
    fun heraldOfTheTides() = card(
        name = "Herald of the Tides",
        cost = "{1}{U}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "2/2",
        text = "Nonbasic lands are Islands.",
    ) parsesTo abilities(
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Island"))), replaceSubtypes = true),
            affects = Objects.Each(and(not(ObjectFilter.Super(arcanum.api.Supertype.Basic)), land))),
    )
}
