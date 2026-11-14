package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 08: conditions, cost changes, chosen numbers and other one-off wordings. */
class WoeChunk08ConditionsAndCostsTest {
    private val adventure = ObjectFilter.Sub(SubtypeRef.Literal(Subtype("Adventure")))
    private val chosenNumber = Value.Remembered("number")
    private fun lessBy(times: Value) = CostChange.Less(ManaCost.parse("{1}")!!, times)

    // CR 608.2h: the destroyed permanent as it last existed (its controller, whether it was a token)
    @Test
    fun breakTheSpell() = card(
        name = "Shatter the Hex",
        cost = "{W}",
        type = "Instant",
        text = "Destroy target enchantment. If a permanent you controlled or a token was destroyed this way, draw a card.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(target("enchantment"), preBind = "destroyed")),
            ifThen(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("destroyed"), or(and(permanentFilter, you), token))), Comparison.AtLeast, n(1)),
                draw(1)),
            targets = listOf(slot("enchantment", enchantment)),
        ),
    )

    // CR 603.2d (a permanent entering, not only "the battlefield" in the wording); CR 707.10 (copy of an ability); CR 715.3
    @Test
    fun virtueOfKnowledge() = cardFaces(Layout.Adventure,
        card(name = "Virtue of Insight", cost = "{4}{U}", type = "Enchantment",
            text = "If a permanent entering causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.",
        ) to abilities(
            static(Mechanic.TriggersAdditionalTime(event = EventPattern.ZoneChange(objects = permanentFilter, to = Zone.Battlefield), sources = and(permanentFilter, you))),
        ),
        card(name = "Mirrorpool Visions", cost = "{1}{U}", type = "Instant — Adventure",
            text = "Copy target activated or triggered ability you control. You may choose new targets for the copy.",
        ) to abilities(
            spell(Step.Do(Effect.CopySpell(target("ability"), Players.You, newTargets = true)),
                targets = listOf(slot("ability", and(or(ObjectFilter.AbilityOf(kind = AbilityKind.Activated), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered)), you)))),
        ),
    )

    // CR 508.1k (attacking creatures); "If a Rat is attacking": any attacking Rat, checked as the trigger resolves (CR 608.2c)
    @Test
    fun chargingHooligan() = card(
        name = "Rushing Ruffian",
        cost = "{3}{R}",
        type = "Creature — Human Peasant",
        pt = "3/3",
        text = "Whenever this creature attacks, it gets +1/+0 until end of turn for each attacking creature. If a Rat is attacking, this creature " +
            "gains trample until end of turn.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(and(attacking, creature)), n(0))),
            ifThen(Condition.Exists(and(attacking, sub("Rat")), atLeast = 1), gains(Objects.Self, Keyword.Trample)),
        ),
    )

    // CR 106.12b: a replacement of the mana a basic land produces when tapped for mana; CR 715.3
    @Test
    fun virtueOfStrength() = cardFaces(Layout.Adventure,
        card(name = "Virtue of Might", cost = "{5}{G}{G}", type = "Enchantment",
            text = "If you tap a basic land for mana, it produces three times as much of that mana instead.",
        ) to abilities(
            static(Mechanic.Replacement(EventPattern.Tap(basicLand(), forMana = true, by = PlayerFilter.You), modify = listOf(Modification.MultiplyAmount(3)))),
        ),
        card(name = "Hedgerow Growth", cost = "{G}", type = "Sorcery — Adventure",
            text = "Return target creature or land card from your graveyard to your hand. (Then exile this card. You may cast the enchantment later from exile.)",
        ) to abilities(
            spell(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(creature, land))))),
        ),
    )

    // CR 613.4b (layer 7b: base power and toughness), CR 613.1f (layer 6: abilities); CR 702.11
    @Test
    fun waterWings() = card(
        name = "Puddle Wings",
        cost = "{1}{U}",
        type = "Instant",
        text = "Until end of turn, target creature you control has base power and toughness 4/4 and gains flying and hexproof. (It can't be the target " +
            "of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.Flying, Keyword.Hexproof)),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 113.10 (a granted ability); "this artifact" in it is the artifact that has it; "named Pantry Brawl" is a name, not this enchantment
    @Test
    fun foodFight() = card(
        name = "Pantry Brawl",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Artifacts you control have \"{2}, Sacrifice this artifact: It deals damage to any target equal to 1 plus the number of permanents " +
            "named Pantry Brawl you control.\"",
    ) parsesTo abilities(
        static(has(activated(cost(mana("{2}"), sacrificeSelf),
            damage(Value.Sum(listOf(n(1), count(and(permanentFilter, ObjectFilter.Named("Pantry Brawl"), you)))), toAny),
            targets = listOf(anyTarget))), affects = Objects.Each(and(artifact, you))),
    )

    // CR 601.2f (an activated ability's cost reduction during your turn); CR 608.2h
    @Test
    fun hyldasCrownOfWinter() = card(
        name = "Frostqueen's Diadem",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            {1}, {T}: Tap target creature. This ability costs {1} less to activate during your turn.
            {3}, Sacrifice Frostqueen's Diadem: Draw a card for each tapped creature your opponents control.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature)),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, Value.If(Condition.YourTurn, n(1))))),
        activated(cost(mana("{3}"), sacrificeSelf), Step.Do(Effect.Draw(Players.You, count(and(tapped, creature, opponents))))),
    )

    // CR 602.2b, 601.2f: activated abilities of its creatures cost less, but not below one mana (the card's own floor)
    @Test
    fun agathaOfTheVileCauldron() = card(
        name = "Morwen of the Bitter Pot",
        cost = "{R}{G}",
        type = "Legendary Creature — Human Warlock",
        pt = "1/1",
        text = """
            Activated abilities of creatures you control cost {X} less to activate, where X is Morwen's power. This effect can't reduce the mana in that cost to less than one mana.
            {4}{R}{G}: Other creatures you control get +1/+1 and gain trample and haste until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = creatureYouControl), lessBy(power(Objects.Self)), minimum = 1)),
        activated(manaCost("{4}{R}{G}"), untilEndOfTurn(Objects.Each(and(another, creature, you)), pt(1, 1), has(Keyword.Trample, Keyword.Haste))),
    )

    // CR 601.2f; CR 715.2a (a card that has an Adventure); CR 715.3
    @Test
    fun hearthElemental() = cardFaces(Layout.Adventure,
        card(name = "Hearthfire Spirit", cost = "{5}{R}", type = "Creature — Elemental", pt = "4/5",
            text = "This spell costs {X} less to cast, where X is the number of cards in your graveyard that are instant cards, sorcery cards, and/or have an Adventure.",
        ) to abilities(
            Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self),
                lessBy(count(and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You), or(instant, sorcery, ObjectFilter.HasAdventure)))))),
                affects = Affected.Objects(Objects.Self)),
        ),
        card(name = "Kindle Wits", cost = "{1}{R}", type = "Sorcery — Adventure",
            text = "Discard your hand, then draw two cards. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))), draw(2)),
        ),
    )

    // CR 715.3 (cast as an Adventure: only that half), CR 400.7 (the card in the graveyard); CR 611.2a
    @Test
    fun mosswoodDreadknight() = cardFaces(Layout.Adventure,
        card(name = "Mossgrove Darkknight", cost = "{1}{G}", type = "Creature — Human Knight", pt = "3/2",
            text = """
                Trample
                When this creature dies, you may cast it from your graveyard as an Adventure until the end of your next turn.
            """,
        ) to abilities(
            Keyword.Trample,
            triggered(diesSelf, doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, resultingSpell = adventure,
                holder = PlayerFilter.You)), Affected.Objects(Objects.EventObject(EventRole.After)), Duration.UntilEndOfYourNextTurn))),
        ),
        card(name = "Grim Murmurs", cost = "{1}{B}", type = "Sorcery — Adventure",
            text = "You draw a card and you lose 1 life. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(draw(1), loseLife(1))),
    )

    // CR 105.2 (black and/or red: either colour); CR 608.2h (X found as the ability resolves); CR 611.2c
    @Test
    fun rowanScionOfWar() = card(
        name = "Rhiannon, Scion of Ash",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Human Wizard",
        pt = "4/2",
        text = """
            Menace
            {T}: Spells you cast this turn that are black and/or red cost {X} less to cast, where X is the amount of life you lost this turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(tap), doing(Effect.ApplyMechanics(listOf(Mechanic.CostChange(and(spellFilter, you, or(black, red)),
            lessBy(Value.EventsThisTurn(EventPattern.LoseLife(PlayerFilter.You), Measure.Amount)))), null, Duration.EndOfTurn)), rules = asSorcery),
    )

    // CR 601.2f; CR 608.2h (the permanent and its controller as it last existed); CR 701.23 (search); CR 121.2
    @Test
    fun theEnd() = card(
        name = "Final Curtain",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if your life total is 5 or less.
            Exile target creature or planeswalker. Search its controller's graveyard, hand, and library for any number of cards with the same name as that permanent and exile them. That player shuffles, then draws a card for each card exiled from their hand this way.
        """,
    ) parsesTo run {
        val exiled = Objects.Bound("exiled permanent")
        val player = Players.ControllerOf(exiled)
        val zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library)
        abilities(
            Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
                affects = Affected.Objects(Objects.Self), condition = Condition.Compare(Value.LifeOf(Players.You), Comparison.AtMost, n(5))),
            spell(
                doing(Effect.Move(target("permanent"), Destination(Zone.Exile), preBind = "exiled permanent")),
                doing(Effect.Search(player, ObjectFilter.SameNameAs(exiled), max = count(and(ObjectFilter.OwnedBy(PlayerFilter.Is(player)),
                    ObjectFilter.Or(zones.map { ObjectFilter.InZone(it) }))), zones = zones, bind = "found", searcher = Players.You)),
                Step.Bind(Objects.Where(Objects.Bound("found"), inZone(Zone.Hand)), "from hand"),
                move(Objects.Bound("found"), Zone.Exile),
                doing(Effect.Shuffle(player)),
                doing(Effect.Draw(player, Value.Count(Objects.Bound("from hand")))),
                targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
            ),
        )
    }

    // CR 106.6 (spending restrictions); CR 107.3 ({X} in a mana cost)
    @Test
    fun troyanGutsyExplorer() = card(
        name = "Tobin, Bold Wanderer",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Vedalken Scout",
        pt = "1/3",
        text = """
            {T}: Add {G}{U}. Spend this mana only to cast spells with mana value 5 or greater or spells with {X} in their mana costs.
            {U}, {T}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{U}")!!),
            restriction = or(and(spellFilter, manaValue(Comparison.AtLeast, 5)), and(spellFilter, ObjectFilter.XInManaCost))), Players.You))),
        activated(cost(mana("{U}"), tap), draw(1), discard(1)),
    )

    // CR 105.2; CR 608.2h; the life gained this turn (CR 119.9)
    @Test
    fun willScionOfPeace() = card(
        name = "Wendel, Scion of Calm",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/4",
        text = """
            Vigilance
            {T}: Spells you cast this turn that are white and/or blue cost {X} less to cast, where X is the amount of life you gained this turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(tap), doing(Effect.ApplyMechanics(listOf(Mechanic.CostChange(and(spellFilter, you, or(white, blue)),
            lessBy(Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount)))), null, Duration.EndOfTurn)), rules = asSorcery),
    )

    // CR 701.9b (a discard of a card another player chooses); a hidden zone's card is chosen, not targeted (CR 115.1)
    @Test
    fun egoDrain() = card(
        name = "Mind Siphoning",
        cost = "{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You choose a nonland card from it. That player discards that card. If you don't control a Faerie, " +
            "exile a card from your hand.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            ifThen(Condition.Not(Condition.Exists(and(sub("Faerie"), you), atLeast = 1)),
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))), n(1), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Exile)),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 608.2d (the number chosen as it resolves); CR 701.8 (destroy)
    @Test
    fun expelTheInterlopers() = card(
        name = "Banish the Trespassers",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Choose a number between 0 and 10. Destroy all creatures with power greater than or equal to the chosen number.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Number(n(0), n(10)), bind = "number", chooser = Players.You),
            destroy(Objects.Each(and(creature, ObjectFilter.Power(Comparison.AtLeast, Value.Bound("number"))))),
        ),
    )

    // CR 700.2d (choose one or more: each mode at most once); CR 715.2a; CR 108.3 (owner of a card in exile); CR 406
    @Test
    fun sentinelOfLostLore() = card(
        name = "Warden of Lost Tales",
        cost = "{1}{G}{G}",
        type = "Creature — Elf Knight",
        pt = "3/4",
        text = """
            When this creature enters, choose one or more —
            • Return target card you own in exile that has an Adventure to your hand.
            • Put target card you don't own in exile that has an Adventure on the bottom of its owner's library.
            • Exile target player's graveyard.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("card", and(cardFilter, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.HasAdventure))),
                steps = listOf(move(target("card"), Zone.Hand)))),
            Mode(Instructions(targets = listOf(slot("card", and(cardFilter, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.Not(PlayerFilter.You)),
                ObjectFilter.HasAdventure))), steps = listOf(move(target("card"), Zone.Library, position = -1)))),
            Mode(Instructions(targets = listOf(targetPlayer), steps = listOf(move(Objects.Each(and(inZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile)))),
        ), min = 1, max = 3, chooser = Players.You))),
    )

    // CR 614.12, 607.2d (the number chosen as it enters); CR 202.3, 208.1 (a spell's mana value, power, toughness)
    @Test
    fun talionTheKindlyLord() = card(
        name = "Talwyn, the Gentle Lord",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Faerie Noble",
        pt = "3/4",
        text = """
            Flying
            As Talwyn enters, choose a number between 1 and 10.
            Whenever an opponent casts a spell with mana value, power, or toughness equal to the chosen number, that player loses 2 life and you draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Number(n(1), n(10)), bind = "number", chooser = Players.You, remember = true)))))),
        triggered(on(EventPattern.Cast(and(spellFilter, or(ObjectFilter.ManaValue(Comparison.Equal, chosenNumber), ObjectFilter.Power(Comparison.Equal, chosenNumber),
            ObjectFilter.Toughness(Comparison.Equal, chosenNumber))), PlayerFilter.Opponent)),
            loseLife(2, Players.EventPlayer), draw(1)),
    )

    // CR 700.2 (a mode that hasn't been chosen for this object); CR 602.1
    @Test
    fun threeBowlsOfPorridge() = card(
        name = "Three Cups of Broth",
        cost = "{2}",
        type = "Artifact — Food",
        text = """
            {2}, {T}: Choose one that hasn't been chosen —
            • This artifact deals 2 damage to target creature.
            • Tap target creature.
            • Sacrifice this artifact. You gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{2}"), tap), Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(damage(2, toObjects(target("creature")))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(tapIt(target("creature"))))),
            Mode(Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Self)), gainLife(3)))),
        ), min = 1, max = 1, onlyUnchosen = true, chooser = Players.You)), anyTime),
    )

    private val white = ObjectFilter.OfColor(ColorRef.Literal(Color.White))
    private val blue = ObjectFilter.OfColor(ColorRef.Literal(Color.Blue))
    private val black = ObjectFilter.OfColor(ColorRef.Literal(Color.Black))
    private val red = ObjectFilter.OfColor(ColorRef.Literal(Color.Red))
}
