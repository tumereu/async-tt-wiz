package arcanum.parser.sets.rvr

import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk04EffectsTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private fun stackAbility(kind: AbilityKind) = and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = kind))
    private val castSpell = Objects.EventObject(EventRole.Object)
    private val LAVINIA_TRIGGER = triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)), doing(Effect.Counter(castSpell)),
        interveningIf = Condition.Compare(Value.ManaSpent(of = castSpell), Comparison.Equal, n(0)))
    private val CHOOSE_TWO_COLORS = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Colors(2), bind = "color", chooser = Players.You, remember = true))))))
    private val TEFERI_MINUS_THREE = loyalty(-3, move(target("permanent"), Zone.Hand), draw(1),
        targets = listOf(slot("permanent", or(artifact, creature, enchantment), min = 0)))
    private val REPLICATE_FACE = spell(doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.You)),
        targets = listOf(slot("creature", creatureYouControl)))
    private val CINDERVINES_TRIGGER = triggered(on(EventPattern.Cast(noncreatureSpell, PlayerFilter.Opponent)),
        damage(1, Recipients(players = Players.EventPlayer)))
    private val PLAGUECRAFTER_LOOP = Players.Bound("player").let { player ->
        Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(player))), n(1), n(1)), bind = "sacrificed", chooser = player),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ifThen(Condition.Not(Condition.NonEmpty("sacrificed")), discard(1, player)),
        ))
    }

    /** Ten choices from the revealed cards, one for each colour pair (CR 105.1), each exactly those two colours (CR 105.2). */
    private fun nivMizzetSteps(): List<Step> {
        val colors = listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green)
        val revealed = Objects.Bound("revealed")
        val pairs = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 1 to 2, 1 to 3, 1 to 4, 2 to 3, 2 to 4, 3 to 4)
        val names = listOf("white", "blue", "black", "red", "green")
        val binds = pairs.map { (a, b) -> "chosen ${names[a]} ${names[b]}" }
        val chosen = Objects.Union(binds.map { Objects.Bound(it) })
        return listOf(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(10), fromTop = true), bind = "revealed"))) +
            pairs.zip(binds).map { (pair, bind) ->
                val exactly = and(*colors.indices.map { i -> if (i == pair.first || i == pair.second) ObjectFilter.OfColor(colors[i]) else not(ObjectFilter.OfColor(colors[i])) }.toTypedArray())
                Step.Choose(Choice.Objects(exactly, n(1), n(1), from = revealed), bind = bind, chooser = Players.You)
            } + listOf(
                move(chosen, Zone.Hand),
                doing(Effect.Move(Objects.Except(revealed, chosen), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            )
    }

    // ---- Can't attack, can't cast, cast timing

    // CR 508.1c: creatures can't attack you (other players and planeswalkers still can be)
    @Test
    fun searingWarden() = card(
        name = "Searing Warden",
        cost = "{6}{W}{W}{W}",
        type = "Creature — Archon",
        pt = "5/6",
        text = """
            Flying
            Creatures can't attack you.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantAttack(target = Recipients(players = Players.You)), affects = Objects.Each(creature)),
    )

    // CR 603.4: an intervening "if" of instants and sorceries you cast this turn; it works from the graveyard
    @Test
    fun embercallPhoenix() = card(
        name = "Embercall Phoenix",
        cost = "{3}{R}",
        type = "Creature — Phoenix",
        pt = "3/2",
        text = """
            Flying, haste
            At the beginning of combat on your turn, if you've cast three or more instant and sorcery spells this turn, return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(beginningOfCombatOnYourTurn, move(Objects.Self, Zone.Battlefield, postBind = "returned"),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You), atLeast = 3, measure = Measure.Events),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 101.2: each opponent can't cast them, counting their own lands; CR 603.4: counter it if no mana was spent
    @Test
    fun corvessaSenateDissenter() = card(
        name = "Corvessa, Senate Dissenter",
        cost = "{W}{U}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/2",
        text = """
            Each opponent can't cast noncreature spells with mana value greater than the number of lands that player controls.
            Whenever an opponent casts a spell, if no mana was spent to cast it, counter that spell.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.CantCast(and(noncreatureSpell, spellFilter,
            ObjectFilter.ManaValue(Comparison.Greater, count(and(land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Affected)))))
        )), players = Players.Each(PlayerFilter.Opponent)),
        LAVINIA_TRIGGER,
    )

    // CR 611.2f, 702.8a: the next sorcery spell you cast this turn may be cast any time you could cast an instant
    @Test
    fun hastenThought() = card(
        name = "Hasten Thought",
        cost = "{U}",
        type = "Instant",
        text = """
            The next sorcery spell you cast this turn can be cast as though it had flash. (It can be cast any time you could cast an instant.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.InstantSpeed), Affected.NextSpells(and(sorcery, spellFilter, you), 1), Duration.EndOfTurn)),
            draw(1),
        ),
    )

    // CR 105.2, 601.2f: {1} less for each chosen colour the spell is
    @Test
    fun sigilOfTheCompact() = card(
        name = "Sigil of the Compact",
        cost = "{5}",
        type = "Artifact",
        text = """
            As this artifact enters, choose two colors.
            Each spell you cast costs {1} less to cast for each of the chosen colors it is.
        """,
    ) parsesTo abilities(
        CHOOSE_TWO_COLORS,
        static(Mechanic.CostChange(and(spellFilter, you),
            CostChange.Less(ManaCost.parse("{1}")!!, Value.SharedColors(Objects.Tested, listOf(ColorRef.Remembered("color")))))),
    )

    // CR 307.5: opponents only with priority in their main phase on an empty Stack; CR 702.8a: until your next turn, your sorceries as though they
    // had flash
    @Test
    fun ombrelTideBender() = card(
        name = "Ombrel, Tide Bender",
        cost = "{1}{W}{U}",
        type = "Legendary Planeswalker — Teferi",
        loyalty = "4",
        text = """
            Each opponent can cast spells only any time they could cast a sorcery.
            [+1]: Until your next turn, you may cast sorcery spells as though they had flash.
            [−3]: Return up to one target artifact, creature, or enchantment to its owner's hand. Draw a card.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.CastOnlyAsSorcery(spellFilter), players = Players.Each(PlayerFilter.Opponent)),
        loyalty(1, doing(Effect.ApplyMechanics(listOf(Mechanic.CastAsThoughFlash(and(sorcery, spellFilter))), Affected.Players(Players.You), Duration.UntilYourNextTurn))),
        TEFERI_MINUS_THREE,
    )

    // ---- Choose, copy

    // CR 105.1: ten colour pairs, each chosen card exactly those two colours; CR 401.4: the rest on the bottom in a random order
    @Test
    fun prismaticWyrmlord() = card(
        name = "Prismatic Wyrmlord",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — Dragon Avatar",
        pt = "6/6",
        text = """
            Flying
            When Prismatic Wyrmlord enters, reveal the top ten cards of your library. For each color pair, choose a card that's exactly those colors from among them. Put the chosen cards into your hand and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, *nivMizzetSteps().toTypedArray()),
    )

    // CR 707.9b, 614.1c: the counters only as a copy, by what it enters as; it isn't legendary
    @Test
    fun mirroredGlint() = card(
        name = "Mirrored Glint",
        cost = "{3}{U}",
        type = "Creature — Illusion",
        pt = "0/0",
        text = "You may have this creature enter as a copy of a creature or planeswalker you control, except it enters with an additional +1/+1 counter on it if it's a creature, it enters with an additional loyalty counter on it if it's a planeswalker, and it isn't legendary.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersAsCopy(and(or(creature, planeswalker), you), listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary))), optional = true,
                withCounters = listOf(
                    Modification.EntersWithCounters(p1p1, n(1), ifEntersAs = creature),
                    Modification.EntersWithCounters(CounterKind.Loyalty, n(1), ifEntersAs = planeswalker),
                ))))),
    )

    // ---- Counter abilities

    // CR 701.6b: countering an activated ability; CR 113.1c: abilities on the Stack (never mana abilities, CR 605.3b)
    @Test
    fun senateArbitrar() = card(
        name = "Senate Arbitrar",
        cost = "{W/U}{W/U}",
        type = "Creature — Vedalken Wizard",
        pt = "2/2",
        text = """
            {2}{W}: Tap target creature.
            {2}{U}: Counter target activated ability. (Mana abilities can't be targeted.)
        """,
    ) parsesTo abilities(
        activated(manaCost("{2}{W}"), tapIt(target("creature")), targets = listOf(slot("creature", creature))),
        activated(manaCost("{2}{U}"), doing(Effect.Counter(target("ability"))), targets = listOf(slot("ability", stackAbility(AbilityKind.Activated)))),
    )

    // CR 701.6b: an activated or triggered ability; CR 707.10: the token copy
    @Test
    fun gainsayWhimReproduce() = cardFaces(
        Layout.Split,
        card(name = "Gainsay Whim", cost = "{G/U}{G/U}", type = "Instant", text = "Counter target activated or triggered ability. (Mana abilities can't be targeted.)") to abilities(
            spell(doing(Effect.Counter(target("ability"))),
                targets = listOf(slot("ability", or(stackAbility(AbilityKind.Activated), stackAbility(AbilityKind.Triggered)))))),
        card(name = "Reproduce", cost = "{1}{G}{U}", type = "Sorcery", text = "Create a token that's a copy of target creature you control.") to abilities(
            REPLICATE_FACE),
    )

    // ---- Move, attach, deal damage

    // CR 122.5: moving a counter; CR 701.3a: attaching the Aura to a permanent chosen on resolution, with the same controller as the one it's on
    @Test
    fun helixTender() = card(
        name = "Helix Tender",
        cost = "{G/U}{G/U}",
        type = "Creature — Elf Wizard",
        pt = "2/2",
        text = """
            ({G/U} can be paid with either {G} or {U}.)
            {1}{G}: Move a +1/+1 counter from target creature onto another target creature with the same controller.
            {1}{U}: Attach target Aura attached to a permanent to another permanent with the same controller.
        """,
    ) parsesTo abilities(
        activated(manaCost("{1}{G}"), Step.Do(Effect.MoveCounters(target("creature"), target("other creature"), p1p1, n(1))), targets = listOf(
            slot("creature", creature),
            TargetSlot("other creature", TargetKind.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(target("creature")))))),
                min = n(1), max = n(1), distinctFrom = listOf("creature")),
        )),
        activated(manaCost("{1}{U}"),
            Step.Choose(Choice.Objects(and(not(ObjectFilter.Is(Objects.AttachedTo(target("aura")))), permanentFilter,
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(Objects.AttachedTo(target("aura")))))), n(1), n(1)), bind = "permanent", chooser = Players.You),
            Step.Do(Effect.Attach(target("aura"), Recipients(objects = Objects.Bound("permanent")))),
            targets = listOf(slot("aura", and(sub("Aura"), ObjectFilter.Attached(to = Objects.Each(permanentFilter)))))),
    )

    // CR 115.3: "another target" is none of the first targets; each deals damage equal to its power
    @Test
    fun packbondStrike() = card(
        name = "Packbond Strike",
        cost = "{2}{G}",
        type = "Instant",
        text = "Up to two target creatures you control each deal damage equal to their power to another target creature.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(arcanum.api.Each.Objects(target("creatures")), bind = "dealer", steps = listOf(
                damage(power(Objects.Bound("dealer")), toObjects(target("creature")), source = Objects.Bound("dealer")))),
            targets = listOf(
                slot("creatures", creatureYouControl, min = 0, max = 2),
                TargetSlot("creature", TargetKind.Objects(creature), min = n(1), max = n(1), distinctFrom = listOf("creatures")),
            ),
        ),
    )

    // CR 608.2h: the destroyed permanent's controller as it last existed
    @Test
    fun ashthornCreepers() = card(
        name = "Ashthorn Creepers",
        cost = "{R}{G}",
        type = "Enchantment",
        text = """
            Whenever an opponent casts a noncreature spell, this enchantment deals 1 damage to that player.
            {1}, Sacrifice this enchantment: Destroy target artifact or enchantment. This enchantment deals 2 damage to that permanent's controller.
        """,
    ) parsesTo abilities(
        CINDERVINES_TRIGGER,
        activated(cost(mana("{1}"), sacrificeSelf),
            destroy(target("permanent")),
            damage(2, Recipients(players = Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    // CR 115.1: two targets, the second optional
    @Test
    fun cinderspokeReveler() = card(
        name = "Cinderspoke Reveler",
        cost = "{B}{B}{R}{R}",
        type = "Creature — Human Rogue",
        pt = "4/3",
        text = "When this creature enters, it deals 2 damage to target opponent and 2 damage to up to one target creature or planeswalker.",
    ) parsesTo abilities(
        triggered(entersSelf,
            damage(2, Recipients(players = Players.Target("opponent"))),
            damage(2, toObjects(target("permanent"))),
            targets = listOf(targetOpponent, slot("permanent", or(creature, planeswalker), min = 0))),
    )

    // ---- Discard

    // CR 118.12a: two cards unless that player discards a land card
    @Test
    fun obsessiveStudy() = card(
        name = "Obsessive Study",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Target player draws three cards. Then that player discards two cards unless they discard a land card.",
    ) parsesTo abilities(
        spell(
            draw(3, Players.Target("player")),
            Step.Pay(arcanum.api.Cost(listOf(CostPart.Discard(CostObjects.Choose(land, count = n(1))))), payer = Players.Target("player"), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), discard(2, Players.Target("player"))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 701.21a, 101.4: each player sacrifices; each who had nothing to sacrifice discards
    @Test
    fun blightmonger() = card(
        name = "Blightmonger",
        cost = "{2}{B}",
        type = "Creature — Human Shaman",
        pt = "3/2",
        text = "When this creature enters, each player sacrifices a creature or planeswalker of their choice. Each player who can't discards a card.",
    ) parsesTo abilities(
        triggered(entersSelf, PLAGUECRAFTER_LOOP),
    )
}
