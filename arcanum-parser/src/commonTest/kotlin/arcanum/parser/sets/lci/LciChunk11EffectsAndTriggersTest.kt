package arcanum.parser.sets.lci

import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.KeywordActionKind
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 11: effects and triggers the earlier chunks didn't read (each-opponent punishers, discover again, base power, Caves, descend
 * counts, reflexive damage, reveals, the player with the most life). */
class LciChunk11EffectsAndTriggersTest {
    private val descendedReminder = "(You descended if a permanent card was put into your graveyard from anywhere.)"
    private val dinosaur = sub("Dinosaur")
    private val bottomRandom = Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
    private fun elemental(vararg names: String) = Mechanic.SetTypes(add = listOf(CardType.Creature),
        addSubtypes = names.map { SubtypeRef.Literal(Subtype(it)) }, replaceSubtypes = true)

    // CR 700.11, 603.4: checked as the step begins and as it resolves; CR 101.4: each opponent chooses in turn order, then all at once;
    // CR 120.3: the damage to each opponent who did neither
    @Test
    fun smolderingTonguewitch() = card(
        name = "Smoldering Tonguewitch",
        cost = "{B}{R}",
        type = "Legendary Creature — Goblin Warlock",
        pt = "2/2",
        text = """
            Deathtouch
            At the beginning of your end step, if you descended this turn, each opponent may discard a card or sacrifice a permanent of their choice. Smoldering Tonguewitch deals 3 damage to each opponent who didn't. $descendedReminder
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(yourEndStep, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
            Step.Pay(Cost(listOf(CostPart.OneOf(listOf(Cost(listOf(discardACard)), Cost(listOf(sacrificeA(permanentFilter))))))),
                payer = Players.Bound("opponent"), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), damage(3, Recipients(players = Players.Bound("opponent")))))),
            interveningIf = descendedThisTurn),
    )

    // CR 701.57a: the same N as the discover that triggered it; CR 603.2h: once each turn
    @Test
    fun keeperOfTheSecondDawn() = card(
        name = "Keeper of the Second Dawn",
        cost = "{3}{R}",
        type = "Creature — Human Artificer",
        pt = "3/3",
        text = "Whenever you discover, discover again for the same value. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.KeywordActionDone(KeywordActionKind.Discover, PlayerFilter.You)), discover(Value.EventAmount), perTurn = 1),
    )

    // CR 508.1m: an attack trigger; CR 115.1: a card in an opponent's graveyard
    @Test
    fun gravecryptWanderglyph() = card(
        name = "Gravecrypt Wanderglyph",
        cost = "{4}",
        type = "Artifact Creature — Golem",
        pt = "3/4",
        text = "Whenever this creature attacks, exile target card from an opponent's graveyard.",
    ) parsesTo abilities(
        triggered(attacksSelf, move(target("card"), Zone.Exile),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent))))),
    )

    // CR 613.4b: base power only (layer 7b), the entering creature's power as it resolves (CR 608.2h)
    @Test
    fun hotheadedHatchling() = card(
        name = "Hotheaded Hatchling",
        cost = "{1}{R}",
        type = "Creature — Dinosaur",
        pt = "3/2",
        text = """
            Trample
            Whenever another Dinosaur you control enters, you may have this creature's base power become equal to that creature's power until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(enters(and(another, dinosaur, you)), Step.May(listOf(Step.Do(Effect.ApplyMechanics(listOf(
            Mechanic.SetPowerToughness(power(Objects.EventObject(EventRole.After)), null)), Affected.Objects(Objects.Self), Duration.EndOfTurn))),
            player = Players.You)),
    )

    // CR 205.1b: still a land and a Cave; CR 602.5b: the activation restriction
    @Test
    fun hollowGullet() = card(
        name = "Hollow Gullet",
        cost = null,
        type = "Land — Cave",
        text = """
            {T}: Add {C}.
            {2}: This land becomes a 3/3 Elemental creature until end of turn. It's still a Cave land. Activate only if the number of other Caves you control plus the number of Cave cards in your graveyard is three or greater.
        """,
    ) parsesTo abilities(
        addOne("{C}"),
        activated(manaCost("{2}"), untilEndOfTurn(Objects.Self, elemental("Elemental"), Mechanic.SetPowerToughness(n(3), n(3))),
            rules = ActivationRules(Timing.Any, PlayerFilter.You, condition = Condition.Compare(Value.Sum(listOf(
                count(and(another, sub("Cave"), you)), count(inYourGraveyard(sub("Cave"))))), Comparison.AtLeast, n(3)))),
    )

    // CR 205.1b: still a land; CR 509.1c: must be blocked this turn
    @Test
    fun rousedTorpor() = card(
        name = "Roused Torpor",
        cost = "{1}{G}",
        type = "Instant",
        text = "Until end of turn, target land you control becomes a 4/4 Dinosaur creature with reach and haste. It's still a land. It must be blocked this turn if able.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("land"), elemental("Dinosaur"), Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.Reach, Keyword.Haste)),
            untilEndOfTurn(target("land"), Mechanic.MustBeBlocked),
            targets = listOf(slot("land", and(land, you)))),
    )

    // CR 604.3: a characteristic-defining ability (Fungi, Saprolings or both); CR 700.11: each descend counts
    @Test
    fun theSporemonarch() = card(
        name = "The Sporemonarch",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Elder Fungus",
        pt = "*/*",
        text = """
            Trample
            The Sporemonarch's power and toughness are each equal to the number of creatures you control that are Fungi and/or Saprolings.
            At the beginning of your end step, create X 1/1 black Fungus creature tokens with "This token can't block," where X is the number of times you descended this turn. (You descend each time a permanent card is put into your graveyard from anywhere.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        count(and(creature, you, or(sub("Fungus"), sub("Saproling")))).let { static(Mechanic.SetPowerToughness(it, it), characteristicDefining = true) },
        triggered(yourEndStep, create(creatureToken("Fungus", listOf(Color.Black), 1, 1, listOf(static(Mechanic.CantBlock(ObjectFilter.Any)))),
            Value.EventsThisTurn(descendEvent, Measure.Objects))),
    )

    // CR 603.4: "if you control one or more Eggs" checked as combat begins and as it resolves; CR 701.21a
    @Test
    fun nestingRexsire() = card(
        name = "Nesting Rexsire",
        cost = "{3}{R}{G}",
        type = "Creature — Dinosaur",
        pt = "5/3",
        text = """
            Other Dinosaurs you control have haste.
            When this creature enters, create two 0/1 green Dinosaur Egg creature tokens.
            At the beginning of combat on your turn, if you control one or more Eggs, sacrifice an Egg, then create a 3/3 green Dinosaur creature token.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(and(another, dinosaur, you))),
        triggered(entersSelf, create(creatureToken("Dinosaur Egg", listOf(Color.Green), 0, 1), n(2))),
        triggered(beginningOfCombatOnYourTurn,
            Step.Choose(arcanum.api.Choice.Objects(and(sub("Egg"), you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            create(creatureToken("Dinosaur", listOf(Color.Green), 3, 3)),
            interveningIf = Condition.Exists(and(sub("Egg"), you), 1)),
    )

    // CR 603.12: a reflexive trigger once paid; CR 115.3: "another target" differs from the first; CR 120.3: the Dinosaur deals it
    @Test
    fun quetzalcrestFirstbornOfIxa() = card(
        name = "Quetzalcrest, Firstborn of Ixa",
        cost = "{R}{G}",
        type = "Legendary Creature — Dinosaur",
        pt = "2/3",
        text = """
            Haste
            When Quetzalcrest enters, you may pay {2}. When you do, target Dinosaur you control deals damage equal to its power to another target creature.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, Step.Pay(manaCost("{2}"), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"),
            reflexive(damage(power(target("Dinosaur")), toObjects(target("creature")), source = target("Dinosaur")),
                targets = listOf(slot("Dinosaur", and(dinosaur, you)), slot("creature", creature).copy(distinctFrom = listOf("Dinosaur")))))),
    )

    // CR 107.3c: X defined by the text; CR 401.4: the rest on the bottom in a random order
    @Test
    fun coilingGulfsaur() = card(
        name = "Coiling Gulfsaur",
        cost = "{5}{U}",
        type = "Creature — Dinosaur",
        pt = "4/4",
        text = "When this creature enters, look at the top X cards of your library, where X is the number of Caves you control plus the number of Cave cards in your graveyard. Put two of those cards into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Sum(listOf(count(and(sub("Cave"), you)), count(inYourGraveyard(sub("Cave"))))),
                fromTop = true), Players.You, bind = "looked")),
            Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomRandom))),
    )

    // CR 122.6: enters with X +1/+1 counters; CR 611.2e: a 1/1 Spirit creature with flying as it enters, for good (layers 4, 6, 7b)
    @Test
    fun grandmothersRousing() = card(
        name = "Grandmother's Rousing",
        cost = "{X}{3}{W}",
        type = "Sorcery",
        text = "Return target artifact or non-Aura enchantment card from your graveyard to the battlefield with X additional +1/+1 counters on it. It's a 1/1 Spirit creature with flying in addition to its other types.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(p1p1, Value.Announced("X"))), entersAs = listOf(
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
            Mechanic.SetPowerToughness(n(1), n(1)), has(Keyword.Flying))))),
            targets = listOf(slot("card", inYourGraveyard(or(artifact, and(not(sub("Aura")), enchantment))))), announced = listOf("X")),
    )

    // CR 115.1: up to one target; CR 122.6: with a finality counter; its mana value: the returned permanent's (CR 400.7)
    @Test
    fun tidewatchPaladin() = card(
        name = "Tidewatch Paladin",
        cost = "{3}{B}{B}",
        type = "Creature — Vampire Knight",
        pt = "5/4",
        text = "Whenever this creature enters or attacks, return up to one target Vampire card from your graveyard to the battlefield with a finality counter on it. You lose life equal to its mana value. (If a creature with a finality counter on it would die, exile it instead.)",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf,
            Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, n(1)))), postBind = "returned")),
            Step.Do(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, Objects.Bound("returned"), Combine.Single))),
            targets = listOf(slot("card", inYourGraveyard(sub("Vampire")), min = 0))),
    )

    // CR 701.20a: an optional reveal; either it or another Dinosaur gains the life (checked as it resolves)
    @Test
    fun platedKinfriend() = card(
        name = "Plated Kinfriend",
        cost = "{2}{G}",
        type = "Creature — Dinosaur",
        pt = "3/3",
        text = "When this creature enters, you may reveal a Dinosaur card from your hand. If you do or if you control another Dinosaur, you gain 3 life.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(Cost(listOf(CostPart.Reveal(CostObjects.Choose(dinosaur, n(1))))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Or(listOf(Condition.Did("paid"), Condition.Exists(and(another, dinosaur, you), 1))), gainLife(3))),
    )

    // CR 510.2: "that many" is the damage dealt; CR 701.20a: revealed; put onto the battlefield together, the rest on the bottom (CR 401.4)
    @Test
    fun ixtlanDawnsAvatar() = card(
        name = "Ixtlan, Dawn's Avatar",
        cost = "{5}{R}{G}{W}",
        type = "Legendary Creature — Dinosaur Avatar",
        pt = "7/6",
        text = """
            Vigilance, trample, haste
            Whenever Ixtlan deals combat damage to a player, reveal that many cards from the top of your library. Put any number of Dinosaur creature cards from among them onto the battlefield and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance, Keyword.Trample, Keyword.Haste,
        triggered(combatDamageToAPlayer,
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), bind = "revealed")),
            Step.Choose(arcanum.api.Choice.Objects(and(dinosaur, creature), n(0), null, from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("chosen")), bottomRandom))),
    )

    // CR 702.105a (as dethrone): the player attacked has the most life or is tied; CR 603.4: "while" checked as attackers are declared
    @Test
    fun heraldOfTheRuin() = card(
        name = "Herald of the Ruin",
        cost = "{2}{B}",
        type = "Creature — Vampire Cleric",
        pt = "2/4",
        text = """
            Deathtouch
            Whenever this creature attacks the player with the most life or tied for most life, create a 1/1 white Vampire creature token with lifelink.
            Whenever this creature attacks while you have the most life or are tied for most life, you draw a card and you lose 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self), target = RecipientFilter(players = mostLife))),
            create(creatureToken("Vampire", listOf(Color.White), 1, 1, listOf(Keyword.Lifelink)))),
        triggered(attacksSelf, draw(1), loseLife(1), triggersOnlyIf = Condition.PlayerMatches(Players.You, mostLife)),
    )

    // CR 208.4b: power greater than its own base power; CR 603.2c: once for the batch of combat damage
    @Test
    fun malametSwiftblade() = card(
        name = "Malamet Swiftblade",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Cat Warrior",
        pt = "3/3",
        text = """
            Your opponents can't cast spells during your turn.
            Whenever one or more creatures you control each with power greater than its base power deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.CantCast(ObjectFilter.Any), players = Players.Each(PlayerFilter.Opponent), condition = Condition.YourTurn),
        triggered(Trigger.On(listOf(EventPattern.Damage(and(creature, you, ObjectFilter.Power(Comparison.Greater,
            Value.Of(NumericProperty.BasePower, Objects.Tested, Combine.Single))), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            oneOrMore = true, batchPerPlayer = true), draw(1)),
    )

    // CR 700.4: dies; CR 702.167a, 602.2b: exiled from the battlefield as a craft material while you activate the ability
    @Test
    fun bazaarGnome() = card(
        name = "Bazaar Gnome",
        cost = "{W}",
        type = "Artifact Creature — Gnome",
        pt = "0/3",
        text = """
            When this creature dies, you gain 1 life and draw a card.
            When this creature is exiled from the battlefield while you're activating a craft ability, you gain 1 life and draw a card.
        """,
    ) parsesTo abilities(
        triggered(diesSelf, gainLife(1), draw(1)),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Exile, forCost = Keyword.Craft.Kind)),
            gainLife(1), draw(1)),
    )
}
