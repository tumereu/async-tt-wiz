package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 15: green cards. A Food or a Treasure by choice; "Choose up to two" modes that are only targets, all
 * returned by the step after them (CR 700.2); counters you put plus one of each kind (CR 614.1a); X mana of one colour by its power;
 * returned to the battlefield, then losing all abilities and gaining haste (layer 6); "up to that many target creatures" by the life
 * gained; an intervening if comparing the entering creature (CR 603.4); a named legendary token with landfall (CR 111.4); a counter
 * removed as a mandatory cost on resolution (CR 118.12); lands played from the graveyard (CR 305.1); one more counter of each kind on a
 * permanent or player; a spell costing less with creature cards in your graveyard and twice its power as damage.
 */
class MshChunk15GreenTest {
    @Test
    fun tinySoldiersArmy() = card(
        name = "Tiny Soldiers' Army",
        cost = "{2}{G}",
        type = "Creature — Insect",
        text = "When this creature enters, create a Food token or a Treasure token. (A Food token is an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\" A Treasure token is an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Choose(Choice.Option(listOf("Food", "Treasure")), bind = "token", chooser = Players.You),
            Step.If(Condition.Chose("token", "Food"), then = listOf(create(food)), otherwise = listOf(createTreasure()))),
    )

    // CR 700.2: each mode is a target; the step after the modes returns every chosen target
    @Test
    fun callTheCleanupCrew() = card(
        name = "Call the Cleanup Crew",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Choose up to two. Return those cards from your graveyard to your hand.
            • Target artifact card.
            • Target creature card.
            • Target enchantment card.
            • Target land card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            modes = Modes(listOf(artifact, creature, enchantment, land).map { Mode(Instructions(targets = listOf(slot("card", inYourGraveyard(it))))) },
                min = 0, max = 2, chooser = Players.You),
            steps = listOf(move(Objects.Union(List(4) { target("card") }), Zone.Hand)))),
    )

    // CR 614.1a: plus one of each kind; X mana of one colour (X its power)
    @Test
    fun doctorStallionGammaTherapist() = card(
        name = "Doctor Stallion, Gamma Therapist",
        cost = "{4}{G}",
        type = "Legendary Creature — Gamma Doctor Hero",
        text = """
            If you would put one or more counters on a permanent you control, put that many plus one of each of those kinds of counters on that permanent instead.
            {T}: Add X mana of any one color, where X is Doctor Stallion's power.
        """,
        pt = "3/6",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CountersPut(and(permanentFilter, you), causedBy = byYou), modify = listOf(Modification.AddAmount(n(1))))),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(power(Objects.Self))), Players.You))),
    )

    @Test
    fun jungleCommandoApe() = card(
        name = "Jungle Commando Ape",
        cost = "{1}{G}",
        type = "Creature — Ape Soldier Hero",
        text = """
            Reach
            Sacrifice this creature: Destroy target noncreature artifact or noncreature enchantment. Activate only as a sorcery.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(sacrificeSelf), destroy(target("permanent")),
            targets = listOf(slot("permanent", or(and(not(creature), artifact), and(not(creature), enchantment)))), rules = asSorcery),
    )

    // the returned card is a new object (CR 400.7); layer 6: abilities lost, then haste gained
    @Test
    fun lynxclawUndyingVigilante() = card(
        name = "Lynxclaw, Undying Vigilante",
        cost = "{G}{G}",
        type = "Legendary Creature — Human Hero",
        text = """
            Haste
            When Lynxclaw dies, return her to the battlefield under her owner's control with a +1/+1 counter on her. She loses all abilities and gains haste.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(diesSelf,
            Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(EventRole.After)),
                counters = listOf(arcanum.api.CountersToPut(p1p1, n(1)))), postBind = "returned")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.LoseAbilities(), has(Keyword.Haste)), arcanum.api.Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely))),
    )

    // the number of targets is the life gained
    @Test
    fun championsBanquet() = card(
        name = "Champions' Banquet",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you gain life, choose up to that many target creatures you control. Put a +1/+1 counter on each of them.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(food)),
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(target("creature")),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(creatureYouControl), min = n(0), max = Value.EventAmount))),
    )

    // CR 603.4: the entering creature compared with this one
    @Test
    fun shapelingBurgeoningBruiser() = card(
        name = "Shapeling, Burgeoning Bruiser",
        cost = "{2}{G}",
        type = "Legendary Creature — Kree Skrull Hero",
        text = """
            Vigilance
            Whenever another creature you control enters, if it has greater power or toughness than Shapeling, put a +1/+1 counter on Shapeling.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(enters(and(another, creatureYouControl)), counters(Objects.Self), interveningIf = Condition.Or(listOf(
            Condition.Compare(power(Objects.EventObject(EventRole.After)), arcanum.api.Comparison.Greater, power(Objects.Self)),
            Condition.Compare(toughness(Objects.EventObject(EventRole.After)), arcanum.api.Comparison.Greater, toughness(Objects.Self))))),
    )

    // CR 111.4: a named legendary token with landfall (CR 207.2c)
    @Test
    fun kazanOfTheWildLand() = card(
        name = "Kazan of the Wild Land",
        cost = "{4}{G}",
        type = "Legendary Creature — Human Barbarian Hero",
        text = """
            You may look at the top card of your library any time.
            You may play lands from the top of your library.
            When Kazan enters, create Sabre, a legendary 2/2 green Cat creature token with "Landfall — Whenever a land you control enters, put a +1/+1 counter on Sabre."
        """,
        pt = "3/2",
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You), affects = Objects.Each(and(land, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        triggered(entersSelf, create(TokenSpec.Defined(FaceDefinition("Sabre", colorIndicator = listOf(Color.Green), supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Cat")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2),
            abilities = listOf(triggered(landfall, counters(Objects.Self), abilityWord = "Landfall")))))),
    )

    // CR 122.6: each counter you put on another creature (once each turn)
    @Test
    fun knightOfMountVargor() = card(
        name = "Knight of Mount Vargor",
        cost = "{1}{G}",
        type = "Creature — Cat Knight Villain",
        text = """
            Trample
            Whenever you put a +1/+1 counter on another creature, put a +1/+1 counter on this creature. This ability triggers only once each turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.CountersPut(and(another, creature), p1p1, causedBy = byYou)), counters(Objects.Self), perTurn = 1),
    )

    // CR 118.12: "Remove a counter … If you do": a mandatory cost on resolution
    @Test
    fun misterGrendelMonsterWithin() = card(
        name = "Mister Grendel, Monster Within",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Villain",
        text = """
            At the beginning of your upkeep, choose one —
            • Put a +1/+1 counter on Mister Grendel.
            • Remove a counter from a creature you control. If you do, draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Triggered(stepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You), Instructions(modes = modes(
            mode(counters(Objects.Self)),
            mode(Step.Pay(Cost(listOf(CostPart.RemoveCounters(null, n(1), CostObjects.Choose(creatureYouControl, count = n(1))))), payer = Players.You,
                mandatory = true, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1))))))),
    )

    // CR 305.1: lands played from the graveyard; a named token with a quoted trigger
    @Test
    fun burrowLordUnderlingMaster() = card(
        name = "Burrow Lord, Underling Master",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Villain",
        text = """
            You may play lands from your graveyard.
            Landfall — Whenever a land you control enters, create a 1/1 green Minion creature token named Underling with "Whenever this token attacks, you may mill a card."
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You), affects = Objects.Each(and(land, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        triggered(landfall, create(TokenSpec.Defined(FaceDefinition("Underling", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Minion")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1),
            abilities = listOf(triggered(attacksSelf, Step.May(listOf(mill(1)), player = Players.You))))), bind = "created"),
            abilityWord = "Landfall"),
    )

    // one more counter of each kind (as proliferate does for one permanent or player, CR 701.34a)
    @Test
    fun influentialBroker() = card(
        name = "Influential Broker",
        cost = "{2}{G}",
        type = "Creature — Human Villain",
        text = "{T}: For each kind of counter on target permanent or player, give that permanent or player another counter of that kind. Activate only as a sorcery.",
        pt = "3/3",
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.OneMoreCounterOfEachKind(Recipients(target = "target"))),
            targets = listOf(TargetSlot("target", TargetKind.Either(permanentFilter, PlayerFilter.Any), min = n(1), max = n(1))), rules = asSorcery),
    )

    @Test
    fun pummelingPunch() = card(
        name = "Pummeling Punch",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if there are two or more creature cards in your graveyard.
            Target creature you control deals damage equal to twice its power to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
            affects = arcanum.api.Affected.Objects(Objects.Self), condition = Condition.Exists(inYourGraveyard(creature), atLeast = 2)),
        spell(damage(Value.Times(power(target("creature")), 2), toObjects(target("creature an opponent controls")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls))),
    )
}
