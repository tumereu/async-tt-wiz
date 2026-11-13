package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
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
 * MH3 chunk 12: searches and looks that put cards in several places (CR 701.23, 401.4), cards exiled face down and turned face up
 * (CR 406.3, 708.8), returns and reveals, and trigger conditions: two or more attackers, getting energy, cards exiled from your library or
 * graveyard, casting or copying (CR 707.10), attacking an opponent (CR 508.4).
 */
class Mh3Chunk12EffectsAndTriggersTest {
    private val found = Objects.Bound("found")
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val oil = CounterKind.Named("oil")

    // CR 701.23a: up to three found cards go to the graveyard
    @Test
    fun interredLiving() = card(
        name = "Interred Living",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Search your library for up to three creature cards, put them into your graveyard, then shuffle.",
    ) parsesTo abilities(
        spell(search(creature, max = 3), move(found, Zone.Graveyard), shuffle),
    )

    // CR 118.9 (the alternative cost), 701.23a: one found card onto the battlefield tapped, the other into your hand
    @Test
    fun blazeOfTillage() = card(
        name = "Blaze of Tillage",
        cost = "{1}{G}{G}",
        type = "Sorcery",
        text = """
            You may sacrifice a nontoken green creature rather than pay this spell's mana cost.
            Search your library for up to two basic land cards, reveal those cards, put one onto the battlefield tapped and the other into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = arcanum.api.Cost(listOf(sacrificeA(and(not(token), ObjectFilter.OfColor(Color.Green), creature)))))),
        spell(
            search(basicLand(), max = 2, reveal = true),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = found), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            move(Objects.Except(found, Objects.Bound("chosen")), Zone.Hand),
            shuffle,
        ),
    )

    // CR 700.5: devotion to black, counted as the trigger resolves
    @Test
    fun dourThrall() = card(
        name = "Dour Thrall",
        cost = "{3}{B}",
        type = "Creature — Zombie Warlock",
        pt = "3/2",
        text = """
            Menace
            When this creature enters, search your library for a card with mana value less than or equal to your devotion to black, reveal it, put it into your hand, then shuffle. You lose 3 life. (Each {B} in the mana costs of permanents you control counts toward your devotion to black.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf,
            search(and(cardFilter, ObjectFilter.ManaValue(Comparison.AtMost, Value.Devotion(listOf(Color.Black), Players.You))), reveal = true),
            move(found, Zone.Hand),
            shuffle,
            loseLife(3),
        ),
    )

    // CR 122.6: enters with two +1/+1 counters, tapped
    @Test
    fun rebuiltMutagrant() = card(
        name = "Rebuilt Mutagrant",
        cost = "{B}",
        type = "Artifact Creature — Zombie",
        pt = "1/1",
        text = "{3}{B}: Return this card from your graveyard to the battlefield tapped with two +1/+1 counters on it.",
    ) parsesTo abilities(
        activated(manaCost("{3}{B}"), Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = true,
            counters = listOf(arcanum.api.CountersToPut(p1p1, n(2))))))),
    )

    // CR 303.4: the Auras attached to it go with it; activate only during your turn (CR 602.5b)
    @Test
    fun spiritCoffer() = card(
        name = "Spirit Coffer",
        cost = "{2}{W}",
        type = "Artifact",
        text = "{T}: Return another target permanent you control and all Auras you control attached to it to their owner's hand. Activate only during your turn.",
    ) parsesTo abilities(
        activated(cost(tap),
            move(Objects.Union(listOf(target("permanent"), Objects.AttachmentsOf(target("permanent"), and(sub("Aura"), you)))), Zone.Hand),
            targets = listOf(slot("permanent", and(another, permanentFilter, you))),
            rules = anyTime.copy(condition = Condition.YourTurn)),
    )

    // CR 401.4: the land onto the battlefield, else a card into your hand; the rest on the bottom in a random order
    @Test
    fun planewellSprouting() = card(
        name = "Planewell Sprouting",
        cost = "{G}{U}",
        type = "Instant",
        text = "Look at the top four cards of your library. You may put a land card from among them onto the battlefield tapped. If you don't, put a card from among them into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            Step.If(Condition.Not(Condition.NonEmpty("chosen")), then = listOf(
                Step.Choose(Choice.Objects(cardFilter, n(1), n(1), from = Objects.Bound("looked")), bind = "kept", chooser = Players.You),
                move(Objects.Bound("kept"), Zone.Hand))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("kept")))),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 701.21a: sacrificed only with three or more counters; "If you do": it was sacrificed
    @Test
    fun almsTithe() = card(
        name = "Alms Tithe",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            Noncreature spells cost {1} more to cast.
            Whenever a player casts a noncreature spell, put a collection counter on this enchantment. Then if there are three or more collection counters on it, sacrifice it. If you do, draw a card, then you may search your library for a Plains card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(what = and(not(creature), spellFilter), change = arcanum.api.CostChange.More(arcanum.api.ManaCost.parse("{1}")!!, times = n(1)))),
        triggered(on(EventPattern.Cast(not(creature), PlayerFilter.Any)),
            counters(Objects.Self, 1, CounterKind.Named("collection")),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("collection"), atLeast = 3)), then = listOf(
                Step.Do(Effect.Sacrifice(Objects.Self, postBind = "sacrificed")),
                Step.If(Condition.NonEmpty("sacrificed"), then = listOf(
                    draw(1),
                    Step.May(listOf(search(sub("Plains")), move(found, Zone.Battlefield, tapped = true), shuffle), Players.You),
                )),
            )),
        ),
    )

    // CR 702.131c: with the city's blessing, a copy of each token that entered this turn (CR 707.2)
    @Test
    fun lynxVanity() = card(
        name = "Lynx Vanity",
        cost = "{W}",
        type = "Creature — Cat",
        pt = "1/1",
        text = """
            First strike, lifelink
            Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the game.)
            At the beginning of your end step, if you gained life this turn, create a 1/1 white Cat creature token. Then if you have the city's blessing, for each token you control that entered this turn, create a token that's a copy of it.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Lifelink,
        Keyword.Ascend,
        triggered(yourEndStep,
            create(creatureToken("Cat", listOf(Color.White), 1, 1)),
            Step.If(Condition.PlayerMatches(Players.You, PlayerFilter.Has(Designation.CitysBlessing)), then = listOf(
                Step.ForEach(arcanum.api.Each.Objects(Objects.Each(and(token, you, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))))),
                    "card", listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(Objects.Bound("card")), n(1), Players.You)))))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 1, measure = arcanum.api.Measure.Events)),
    )

    // CR 603.7c: the delayed trigger is about the cards still exiled (CR 400.7); one Spawn for each card put into the graveyard
    @Test
    fun peekBeyondReason() = card(
        name = "Peek Beyond Reason",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Exile the top three cards of your library. You may play those cards this turn. At the beginning of the next end step, if any of those cards remain exiled, put them into your graveyard, then create a 0/1 colorless Eldrazi Spawn creature token for each card put into your graveyard this way. Those tokens have \"Sacrifice this token: Add {C}.\"",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            delayed(on(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Step.If(Condition.Exists(and(ObjectFilter.Is(Objects.Bound("exiled")), inZone(Zone.Exile)), atLeast = 1), then = listOf(
                    move(Objects.Each(and(ObjectFilter.Is(Objects.Bound("exiled")), inZone(Zone.Exile))), Zone.Graveyard, postBind = "put"),
                    create(eldraziSpawn, count = Value.Count(Objects.Each(and(ObjectFilter.Is(Objects.Bound("put")), inZone(Zone.Graveyard))))),
                ))),
        ),
    )

    // CR 701.26a: tapped this way are those that were untapped; up to as many as the energy paid (CR 107.14) get a stun counter (CR 122.1d)
    @Test
    fun quellingBeam() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Quelling Beam",
            cost = "{3}{W/U}{W/U}",
            type = "Sorcery",
            text = "Tap all creatures target player controls. You may pay any amount of {E}. If you do, choose up to that many creatures tapped this way. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
        ) to abilities(
            spell(*(listOf(
                Step.Bind(Objects.Each(and(not(tapped), creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), "tapped"),
                tapIt(Objects.Bound("tapped"))) + payAnyAmountOfEnergy + listOf(
                Step.Choose(Choice.Objects(creature, n(0), energyPaid, from = Objects.Bound("tapped")), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen"), 1, CounterKind.Stun))).toTypedArray(),
                targets = listOf(targetPlayer)),
        ),
        card(
            name = "Ordered Square",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {W} or {U}.
            """,
        ) to abilities(entersTapped, addEither("W", "U")),
    )

    // CR 701.21a: the creatures sacrificed this way set the greatest mana value; both zones are searched (CR 701.23a)
    @Test
    fun theSwarmTideSwells() = card(
        name = "The Swarm Tide Swells",
        cost = "{2}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III — Create a 1/1 black and green Insect creature token.
            IV — Sacrifice any number of creatures. Search your library and/or graveyard for a creature card with mana value less than or equal to the number of creatures sacrificed this way and put it onto the battlefield. If you search your library this way, shuffle.
        """,
    ) parsesTo abilities(
        Ability.Triggered(Trigger.Chapter(listOf(1, 2, 3)), Instructions(steps = listOf(create(creatureToken("Insect", listOf(Color.Black, Color.Green), 1, 1))))),
        chapter(4,
            Step.Choose(Choice.Objects(creatureYouControl, n(0), null), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("chosen"), postBind = "sacrificed")),
            search(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Count(Objects.Bound("sacrificed")))), zones = listOf(Zone.Library, Zone.Graveyard)),
            move(found, Zone.Battlefield),
            shuffle,
        ),
    )

    // CR 406.3, 607.2a: the card exiled face down by chapter I is "the exiled card" of chapters II and III; turned face up (CR 708.8)
    @Test
    fun theForgingOfSeraphs() = card(
        name = "The Forging of Seraphs",
        cost = "{1}{B}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Search your library for a card, exile it face down, then shuffle.
            II — Turn the exiled card face up. If it's a creature card, you lose life equal to its mana value.
            III — You may put the exiled card onto the battlefield if it's a creature card. If you don't put it onto the battlefield, put it into its owner's hand.
        """,
    ) parsesTo abilities(
        chapter(1,
            search(cardFilter),
            Step.Do(Effect.Move(found, Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            shuffle),
        chapter(2,
            Step.Do(Effect.TurnFaceUp(Objects.Remembered("exiled"))),
            Step.If(Condition.ObjectMatches(Objects.Remembered("exiled"), creature), then = listOf(Step.Do(Effect.LoseLife(Players.You,
                Value.Of(NumericProperty.ManaValue, Objects.Remembered("exiled"), Combine.Total)))))),
        chapter(3,
            Step.If(Condition.ObjectMatches(Objects.Remembered("exiled"), creature), then = listOf(
                Step.May(listOf(Step.Do(Effect.Move(Objects.Remembered("exiled"), Destination(Zone.Battlefield), postBind = "put"))), Players.You))),
            Step.If(Condition.Not(Condition.NonEmpty("put")), then = listOf(move(Objects.Remembered("exiled"), Zone.Hand)))),
    )

    // CR 701.20a: one card, from the hand or the top of the library; a creature card revealed goes onto the battlefield
    @Test
    fun korvanElfWarden() = card(
        name = "Korvan, Elf Warden",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Elf Warrior",
        pt = "3/3",
        text = """
            You may look at the top card of your library any time.
            You may cast creature spells from the top of your library.
            {G}, {T}, Tap two untapped creatures you control: Reveal a card from your hand or the top card of your library. If you reveal a creature card this way, put it onto the battlefield. Activate only during your turn.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(creature, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        activated(cost(mana("{G}"), tap, arcanum.api.CostPart.TapObjects(arcanum.api.CostObjects.Choose(creature, n(2)))),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Union(listOf(
                Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))),
                Objects.LibraryCards(Players.You, n(1), fromTop = true)))), bind = "revealed", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("revealed"))),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), creature), then = listOf(move(Objects.Bound("revealed"), Zone.Battlefield))),
            rules = anyTime.copy(condition = Condition.YourTurn)),
    )

    // CR 120.3: X damage and X life, X counted once as the spell resolves (CR 107.3)
    @Test
    fun devouringBlight() = card(
        name = "Devouring Blight",
        cost = "{B}{B}",
        type = "Instant",
        text = "Devouring Blight deals X damage to target creature or planeswalker and you gain X life, where X is the number of Swamps you control.",
    ) parsesTo abilities(
        spell(
            damage(count(and(sub("Swamp"), you)), Recipients(objects = target("permanent"))),
            Step.Do(Effect.GainLife(Players.You, count(and(sub("Swamp"), you)))),
            targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 700.9: each modified creature you control deals damage equal to its power, the counter put first making the target modified
    @Test
    fun hallmarkSmash() = card(
        name = "Hallmark Smash",
        cost = "{2}{G}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature you control, then each modified creature you control deals damage equal to its power to target creature you don't control. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(
        spell(
            counters(target("creature")),
            Step.ForEach(arcanum.api.Each.Objects(Objects.Each(and(creature, you, ObjectFilter.Modified))), "source",
                listOf(damage(power(Objects.Bound("source")), Recipients(objects = target("second creature")), source = Objects.Bound("source"))), together = true),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    // CR 707.10: casting and copying are both events of the trigger; the second and third resolution this turn (CR 603.4 does not apply)
    @Test
    fun emberDancerVasha() = card(
        name = "Ember Dancer Vasha",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Elemental Shaman",
        pt = "4/4",
        text = """
            You don't lose unspent red mana as steps and phases end.
            Magecraft — Whenever you cast or copy an instant or sorcery spell, discard a card, then draw a card. If this is the second time this ability has resolved this turn, Ember Dancer Vasha deals 2 damage to each opponent and each creature they control. If it's the third time, add {R}{R}{R}{R}.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.KeepsUnspentMana(listOf(Color.Red))),
        triggered(Trigger.On(listOf(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You), EventPattern.Copy(and(or(instant, sorcery), spellFilter), PlayerFilter.You))),
            discard(1),
            draw(1),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2)), then = listOf(damage(2, Recipients(players = Players.Each(PlayerFilter.Opponent),
                objects = Objects.Each(and(creature, opponents)))))),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(3)), then = listOf(Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(
                arcanum.api.ManaCost.parse("{R}{R}{R}{R}")!!)), Players.You)))),
            abilityWord = "Magecraft"),
    )

    // CR 603.12: a reflexive trigger; a counter of any kind from a permanent or from an opponent (CR 122.1)
    @Test
    fun gravenSlith() = card(
        name = "Graven Slith",
        cost = "{1}{B}",
        type = "Artifact Creature — Phyrexian Slith",
        pt = "1/1",
        text = """
            Menace
            Whenever this creature deals combat damage to a player, put a +1/+1 counter on it. When you do, you may remove a counter from another target permanent or opponent.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer,
            counters(Objects.Self),
            reflexive(
                Step.May(listOf(Step.Do(Effect.RemoveCounters(target("target"), null, n(1), players = Players.Target("target")))), Players.You),
                targets = listOf(TargetSlot("target", TargetKind.Either(and(another, permanentFilter), PlayerFilter.Opponent), min = n(1), max = n(1))))),
    )

    // CR 603.1: two trigger conditions; the third card drawn each turn (CR 121.2); amass Zombies 3 (CR 701.47a)
    @Test
    fun witlessDraft() = card(
        name = "Witless Draft",
        cost = "{2}{B}",
        type = "Enchantment",
        text = "When this enchantment enters and whenever you draw your third card each turn, amass Zombies 3. (Put three +1/+1 counters on an Army you control. It's also a Zombie. If you don't control an Army, create a 0/0 black Zombie Army creature token first.)",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Action(arcanum.api.KeywordAction.Amass(Subtype("Zombie"), n(3))))),
        triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 3), Step.Do(Effect.Action(arcanum.api.KeywordAction.Amass(Subtype("Zombie"), n(3))))),
    )

    // CR 122.1: each time you get energy, that much damage
    @Test
    fun chargeUprising() = card(
        name = "Charge Uprising",
        cost = "{2}{R}{R}",
        type = "Enchantment",
        text = """
            Revolt — As long as a permanent left the battlefield under your control this turn, if a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals that much damage plus 2 instead.
            Whenever you get one or more {E}, this enchantment deals that much damage to any target.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Damage(you, arcanum.api.RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent),
            combat = false), modify = listOf(arcanum.api.Modification.AddAmount(n(2)))),
            condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentFilter, you), from = Zone.Battlefield), atLeast = 1,
                measure = arcanum.api.Measure.Events)).copy(abilityWord = "Revolt"),
        triggered(on(EventPattern.PlayerCountersPut(PlayerFilter.You, CounterKind.Energy)), damage(Value.EventAmount, toAny), targets = listOf(anyTarget)),
    )

    // CR 508.4: put onto the battlefield attacking the opponent Kessa attacks, not declared as an attacker
    @Test
    fun kessaOfTheBroadSky() = card(
        name = "Kessa of the Broad Sky",
        cost = "{1}{R}{W}{B}",
        type = "Legendary Creature — Human Cleric",
        pt = "2/2",
        text = """
            Flying
            Whenever Kessa of the Broad Sky attacks an opponent, you may put an Angel, Demon, or Dragon creature card from your hand onto the battlefield tapped and attacking that opponent.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self), target = arcanum.api.RecipientFilter(players = PlayerFilter.Opponent))),
            Step.Choose(Choice.Objects(and(or(sub("Angel"), sub("Demon"), sub("Dragon")), creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true,
                attacking = AttackTarget.These(Recipients(players = Players.EventPlayer)))))),
    )

    // CR 603.2c: one trigger for each batch of cards exiled from your library and/or graveyard
    @Test
    fun lyraTheReforgedEdge() = card(
        name = "Lyra, the Reforged Edge",
        cost = "{2}{R}",
        type = "Legendary Creature — Spirit Warrior",
        pt = "2/2",
        text = """
            Haste
            Whenever Lyra attacks, exile the top card of your library. You may play that card this turn.
            Whenever one or more cards are put into exile from your library and/or your graveyard, put a +1/+1 counter on Lyra.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf,
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
        triggered(Trigger.On(listOf(
            EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Library, to = Zone.Exile),
            EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard, to = Zone.Exile)), oneOrMore = true),
            counters(Objects.Self)),
    )

    // CR 508.1: two or more creatures declared as attackers together, by any player
    @Test
    fun silverPlinth() = card(
        name = "Silver Plinth",
        cost = "{1}{W}",
        type = "Artifact",
        text = """
            This artifact enters with two oil counters on it.
            Whenever two or more creatures attack, put an oil counter on this artifact.
            {2}, {T}, Remove two oil counters from this artifact: Exile another target nonland permanent. Its controller draws two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(arcanum.api.Modification.EntersWithCounters(oil, n(2))))),
        triggered(Trigger.On(listOf(EventPattern.Attack(creature)), oneOrMore = true, atLeast = 2), counters(Objects.Self, 1, oil)),
        activated(cost(mana("{2}"), tap, arcanum.api.CostPart.RemoveCounters(oil, n(2), arcanum.api.CostObjects.Self)),
            move(target("permanent"), Zone.Exile),
            Step.Do(Effect.Draw(Players.ControllerOf(target("permanent")), n(2))),
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter)))),
    )
}
