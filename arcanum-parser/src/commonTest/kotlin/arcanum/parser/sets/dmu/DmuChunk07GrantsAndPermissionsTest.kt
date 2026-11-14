package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMU chunk 07: linked choices, granted abilities, permissions and the costs of paying. */
class DmuChunk07GrantsAndPermissionsTest {
    private val asEntersChooseAnother = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Objects(and(another, creature, you), n(1), n(1)), bind = "chosen",
            chooser = Players.You, remember = true)))))), affects = Affected.Objects(Objects.Self))

    // CR 607.2a: the creature chosen as it entered is the one pumped and, when this leaves, sacrificed
    @Test
    fun despoticPitfiend() = card(
        name = "Despotic Pitfiend",
        cost = "{4}{B}{B}",
        type = "Creature — Demon",
        pt = "6/6",
        text = """
            Flying, trample
            As this creature enters, choose another creature you control.
            The chosen creature gets +3/+3 and has flying.
            When this creature leaves the battlefield, sacrifice the chosen creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Trample,
        asEntersChooseAnother,
        static(pt(3, 3), has(Keyword.Flying), affects = Objects.Remembered("chosen")),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), doing(Effect.Sacrifice(Objects.Remembered("chosen")))),
    )

    // CR 602.2b, 601.2f: its mana abilities, the chosen basic land type's included (CR 305.6), cost 1 life more
    @Test
    fun thranGatewayRuin() = card(
        name = "Thran Gateway Ruin",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped unless you control two or fewer other lands.
            As this land enters, choose a basic land type.
            This land is the chosen type in addition to its other types.
            Mana abilities of this land cost an additional 1 life to activate.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Compare(count(and(another, land, you)), Comparison.AtMost, n(2)))),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.BasicLandType, bind = "type", chooser = Players.You, remember = true)))))),
            affects = Affected.Objects(Objects.Self)),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))),
        static(Mechanic.AdditionalActivationCost(Cost(listOf(CostPart.PayLife(n(1)))), manaAbilitiesOnly = true)),
    )

    // CR 611.2c, 613.1d, 613.4b: a creature for good, its base power and toughness its mana value as the ability resolved
    @Test
    fun vorrEternalPlotter() = card(
        name = "Vorr, Eternal Plotter",
        cost = "{W}{U}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/4",
        text = """
            Flying
            Enchantment creatures you control have deathtouch, lifelink, and hexproof.
            {1}{W}: Target non-Aura enchantment you control becomes a creature in addition to its other types and has base power and base toughness each equal to its mana value.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.Deathtouch, Keyword.Lifelink, Keyword.Hexproof), affects = Objects.Each(and(enchantment, creature, you))),
        activated(manaCost("{1}{W}"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Creature)),
                Mechanic.SetPowerToughness(Value.Of(NumericProperty.ManaValue, target("enchantment"), Combine.Single), Value.Of(NumericProperty.ManaValue, target("enchantment"), Combine.Single))),
                Affected.Objects(target("enchantment")), Duration.Indefinitely)),
            targets = listOf(slot("enchantment", and(not(sub("Aura")), enchantment, you)))),
    )

    // CR 305.1, 601.2, 400.7d: one land or permanent spell a turn from the graveyard, and what it becomes gains the ability
    @Test
    fun serraExemplar() = card(
        name = "Serra Exemplar",
        cost = "{2}{W}{W}",
        type = "Creature — Angel",
        pt = "3/4",
        text = """
            Flying
            Once during each of your turns, you may play a land from your graveyard or cast a permanent spell with mana value 3 or less from your graveyard. If you do, it gains "When this permanent is put into a graveyard from the battlefield, exile it and you gain 2 life."
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how = PlayKind.Play, perTurn = 1, holder = PlayerFilter.You, spellGets = listOf(has(
            triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
                move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled"), gainLife(2)))))),
            affects = Affected.Objects(Objects.Each(and(or(land, and(permanentCard, manaValue(Comparison.AtMost, 3))), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            condition = Condition.YourTurn),
    )

    // CR 603.7a: paid, a delayed trigger copies the next instant or sorcery spell this turn (CR 707.10c)
    @Test
    fun quazirTheGaleRunner() = card(
        name = "Quazir, the Gale Runner",
        cost = "{2}{U}{U}{R}",
        type = "Legendary Creature — Efreet Wizard",
        pt = "5/4",
        text = """
            You may cast sorcery spells as though they had flash.
            Whenever Quazir attacks, you may pay {2}. If you do, when you next cast an instant or sorcery spell this turn, copy it. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.CastAsThoughFlash(and(sorcery, spellFilter))),
        triggered(attacksSelf,
            Step.Pay(manaCost("{2}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.CreateDelayedTrigger(youCast(and(or(instant, sorcery), spellFilter)),
                Instructions(steps = listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))),
                duration = Duration.EndOfTurn))))),
    )

    // CR 508.4, 122.6: returned from the graveyard attacking (not declared), with the counter as it enters
    @Test
    fun phoenixFledgling() = card(
        name = "Phoenix Fledgling",
        cost = "{R}",
        type = "Creature — Phoenix",
        pt = "1/1",
        text = """
            Flying, haste
            This creature can't block.
            Whenever you attack with three or more creatures, you may pay {R}{R}. If you do, return this card from your graveyard to the battlefield tapped and attacking with a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Haste,
        static(Mechanic.CantBlock(attackers = ObjectFilter.Any)),
        triggered(Trigger.On(listOf(EventPattern.Attack(creatureYouControl)), oneOrMore = true, atLeast = 3),
            Step.Pay(manaCost("{R}{R}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = true,
                attacking = arcanum.api.AttackTarget.Chosen, counters = listOf(CountersToPut(p1p1, n(1)))))))),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 107.3a, 107.1c: X chosen as it is paid, at most the life gained this turn
    @Test
    fun shallaCleansingEdge() = card(
        name = "Shalla, Cleansing Edge",
        cost = "{G}{W}{U}",
        type = "Legendary Creature — Human Warrior",
        pt = "3/3",
        text = """
            Lifelink
            At the beginning of your end step, you may pay {X}. If you do, draw X cards. X can't be greater than the amount of life you gained this turn.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Choose(Choice.Number(n(0), max = Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), arcanum.api.Measure.Amount)), bind = "X", chooser = Players.You),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X")))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.Draw(Players.You, Value.Bound("X")))))),
    )

    // CR 208.4b, 603.4: a creature whose power is above its base power (counters and pumps raise it), checked again on resolution
    @Test
    fun bardoArgivianDrillmaster() = card(
        name = "Bardo, Argivian Drillmaster",
        cost = "{R}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/2",
        text = "At the beginning of your end step, if you control a creature with power greater than its base power, create a 1/1 white Soldier creature token.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), create(soldier),
            interveningIf = Condition.Exists(and(creature, you, ObjectFilter.Where(Condition.Compare(Value.Of(NumericProperty.Power, Objects.Tested, Combine.Single),
                Comparison.Greater, Value.Of(NumericProperty.BasePower, Objects.Tested, Combine.Single)))), atLeast = 1)),
    )

    // CR 303.4f, 301.5c: put onto the battlefield attached to it, not cast, so it targets nothing
    @Test
    fun danikaBenalianHope() = card(
        name = "Danika, Benalian Hope",
        cost = "{4}{W}",
        type = "Legendary Creature — Human Knight",
        pt = "4/4",
        text = """
            First strike, vigilance, lifelink
            When Danika enters, you may put an Aura or Equipment card from your hand or graveyard onto the battlefield attached to Danika.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike, Keyword.Vigilance, Keyword.Lifelink,
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(or(sub("Aura"), equipment), or(inZone(Zone.Hand), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, attachedTo = Recipients(objects = Objects.Self))))),
    )

    // CR 701.20a, 609.3: one land card if there is one; without one, a card is drawn
    @Test
    fun etchedSlate() = card(
        name = "Etched Slate",
        cost = "{1}",
        type = "Artifact",
        text = "{1}, {T}, Sacrifice this artifact: Reveal the top five cards of your library. Put a land card from among them into your hand and the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, draw a card.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeSelf),
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(land, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.If(Condition.Not(Condition.NonEmpty("kept")), then = listOf(draw(1)))),
    )

    // CR 201.5c: a legendary name without its regnal number is its short name
    @Test
    fun kingDorianXlvii() = card(
        name = "King Dorian XLVII",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/3",
        text = """
            Other creatures you control get +1/+1.
            {3}{G}{W}: Put a +1/+1 counter on King Dorian and create a 1/1 white Soldier creature token.
            Sacrifice King Dorian: Creature tokens you control gain hexproof and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, creature, you))),
        activated(manaCost("{3}{G}{W}"), counters(Objects.Self), create(soldier)),
        activated(cost(sacrificeSelf), gains(Objects.Each(and(creature, token, you)), Keyword.Hexproof, Keyword.Indestructible)),
    )

    // CR 110.2a: from any graveyard, under your control
    @Test
    fun spiritOfWindmere() = card(
        name = "Spirit of Windmere",
        cost = "{1}{B}{R}{G}",
        type = "Legendary Creature — Cat Avatar",
        pt = "5/4",
        text = """
            Whenever Spirit of Windmere enters or attacks, you may put a land card from a graveyard onto the battlefield tapped under your control.
            {G}, Discard a land card: You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf,
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Graveyard)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true, controller = Players.You)))),
        activated(cost(mana("{G}"), CostPart.Discard(arcanum.api.CostObjects.Choose(land, count = n(1)))), gainLife(3)),
    )

    // CR 701.23a: different powers; an opponent of your choice chooses the two shuffled back
    @Test
    fun dangersUnseen() = card(
        name = "Dangers Unseen",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Search your library for up to four creature cards with different powers and reveal them. An opponent chooses two of those cards. Shuffle the chosen cards into your library and put the rest into your hand.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Search(Players.You, creature, max = n(4), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You,
                differentIn = NumericProperty.Power)),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("found")), bind = "chosen", chooser = Players.Each(PlayerFilter.Opponent)),
            move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Zone.Hand),
            move(Objects.Bound("chosen"), Zone.Library),
            shuffle),
    )

    // CR 115.7, 707.10e: any player's single-target spell; the copy's every target is this creature, or no copy
    @Test
    fun ivetMerrySpellthief() = card(
        name = "Ivet, Merry Spellthief",
        cost = "{G}{U}",
        type = "Legendary Creature — Faerie Rogue",
        pt = "2/1",
        text = """
            Flying
            Whenever a player casts a spell that targets only a single creature other than Ivet, you may copy that spell. The copy targets Ivet. (A copy of an Aura spell becomes a token.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Cast(and(spellFilter, ObjectFilter.SingleTarget(and(creature, another))), PlayerFilter.Any)),
            Step.May(listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = false, forEachCouldTarget = Objects.Self))),
                player = Players.You)),
    )

    // CR 601.2a, 400.7d: cast from the graveyard; the spell and the creature it becomes gain the ability
    @Test
    fun rizavOfTheTalon() = card(
        name = "Rizav of the Talon",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Lizard Warlock",
        pt = "3/3",
        text = """
            Menace
            Whenever you cast a Dragon creature spell from your graveyard, it gains "When this creature dies, exile it."
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(on(EventPattern.Cast(and(sub("Dragon"), creature, spellFilter), PlayerFilter.You, from = Zone.Graveyard)),
            doing(Effect.SpellGets(Objects.EventObject(EventRole.Object), listOf(has(triggered(diesSelf, move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled"))))))),
    )

    // CR 115.7, 707.9b: the spell's one target is copied, except the token isn't legendary
    @Test
    fun vesuvanMimicry() = card(
        name = "Vesuvan Mimicry",
        cost = "{3}{U}",
        type = "Enchantment",
        text = "Whenever you cast a spell that targets only a single artifact or creature you control, create a token that's a copy of that artifact or creature, except it's not legendary.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(and(spellFilter, ObjectFilter.SingleTarget(and(or(artifact, creature), you))), PlayerFilter.You)),
            doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Each(and(or(artifact, creature), ObjectFilter.TargetedBy(Objects.EventObject(EventRole.Object)))),
                listOf(arcanum.api.CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)))), n(1), Players.You))),
    )
}
