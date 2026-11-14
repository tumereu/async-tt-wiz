package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 01: devoid (CR 702.114) with "When you cast this spell" triggers (CR 603.1, 601.2i), colorless mana spent ({C}, CR 106.1b)
 * and colorless mana spent as though it were any colour (CR 609.4b).
 */
class Mh3Chunk01DevoidTest {
    /** "if {C} was spent to cast it" (colorless mana is a type, not a colour, CR 106.1b) */
    private val colorlessSpent = Condition.Compare(Value.ManaSpent(colorless = true, of = Objects.Self), Comparison.AtLeast, n(1))
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)

    // CR 609.4b: only colorless mana may be spent as though it were any colour; CR 400.7: the exiled card is a new object
    @Test
    fun hollowAppropriation() = card(
        name = "Hollow Appropriation",
        cost = "{2}{W}{B}",
        type = "Instant",
        text = """
            Devoid (This card has no color.)
            Exile target nonland permanent. You may cast that card for as long as it remains exiled, and you may spend colorless mana as though it were mana of any color to cast that spell.
        """,
    ) parsesTo abilities(
        devoid,
        spell(
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You, colorlessAsAnyColor = true)),
                Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    /** One of "your choice of" forms: the creature it enters as (CR 614.12, 614.1c; P/T layer 7b, abilities layer 6). */
    private fun becomes(option: String, power: Int, toughness: Int, keyword: Ability) =
        ifThen(Condition.Chose("form", option), Step.Modify(Modification.EntersAs(listOf(
            Mechanic.SetPowerToughness(n(power), n(toughness)), Mechanic.GainAbilities(listOf(keyword))))))

    // CR 614.12: chosen as it enters, so it is already that creature on the battlefield
    @Test
    fun shiftingVoidling() = card(
        name = "Shifting Voidling",
        cost = "{3}{U}",
        type = "Creature — Eldrazi Shapeshifter",
        pt = "*/*",
        text = """
            Devoid (This card has no color.)
            As this creature enters, it becomes your choice of a 3/3 creature with flying, a 2/5 creature with vigilance, or a 0/12 creature with defender.
        """,
    ) parsesTo abilities(
        devoid,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.AsEnters(listOf(
                Step.Choose(Choice.Option(listOf("a 3/3 creature with flying", "a 2/5 creature with vigilance", "a 0/12 creature with defender")),
                    bind = "form", chooser = Players.You),
                becomes("a 3/3 creature with flying", 3, 3, Keyword.Flying),
                becomes("a 2/5 creature with vigilance", 2, 5, Keyword.Vigilance),
                becomes("a 0/12 creature with defender", 0, 12, Keyword.Defender),
            ))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 603.4: the intervening "if" is checked as it triggers and as it resolves; CR 712.8a: the back face is a land
    @Test
    fun drownerOfTheDeep() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Abyssal Drowner",
            cost = "{5}{G/U}{G/U}",
            type = "Creature — Eldrazi",
            pt = "7/6",
            text = """
                Devoid (This card has no color.)
                When you cast this spell, if {C} was spent to cast it, create two 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
            """,
        ) to abilities(
            devoid,
            triggered(youCastThis, create(eldraziSpawn, n(2)), interveningIf = colorlessSpent),
        ),
        card(
            name = "Drowned Thicket",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {G} or {U}.
            """,
        ) to abilities(entersTapped, addEither("G", "U")),
    )

    // CR 603.1: "When you cast this spell and when this creature dies" is two trigger conditions with the same effect
    @Test
    fun spawnRecycler() = card(
        name = "Spawn Recycler",
        cost = "{2}{G}",
        type = "Creature — Eldrazi Drone",
        pt = "3/3",
        text = """
            Devoid (This card has no color.)
            When you cast this spell and when this creature dies, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCastThis, create(eldraziSpawn)),
        triggered(diesSelf, create(eldraziSpawn)),
    )

    // CR 118.12: "unless they pay": the target spell's controller may pay
    @Test
    fun hushingCoatl() = card(
        name = "Hushing Coatl",
        cost = "{2}{U}",
        type = "Creature — Eldrazi Snake",
        pt = "2/2",
        text = """
            Devoid (This card has no color.)
            Flash
            When you cast this spell, counter target spell an opponent controls unless they pay {1}.
            Flying
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Flash,
        triggered(youCastThis,
            Step.Pay(manaCost("{1}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", and(spellFilter, opponents)))),
        Keyword.Flying,
    )

    // CR 202.3: a spell's mana value; "4, 5, or 6" is any one of them
    @Test
    fun kozileksUnfolding() = card(
        name = "Kozilek's Unfolding",
        cost = "{2}{U}",
        type = "Enchantment",
        text = """
            Devoid (This card has no color.)
            Whenever you cast a creature spell with mana value 4, 5, or 6, create two 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
            Whenever you cast a creature spell with mana value 7 or greater, draw three cards.
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCast(and(creature, or(manaValue(Comparison.Equal, 4), manaValue(Comparison.Equal, 5), manaValue(Comparison.Equal, 6)))),
            create(eldraziSpawn, n(2))),
        triggered(youCast(and(creature, manaValue(Comparison.AtLeast, 7))), draw(3)),
    )

    // CR 400.7a: an ability the spell gains on the Stack stays with the permanent it becomes; "If you do": only when something was exiled
    @Test
    fun larcenousVoidling() = card(
        name = "Larcenous Voidling",
        cost = "{1}{C}{G}",
        type = "Creature — Eldrazi",
        pt = "3/4",
        text = """
            Devoid (This card has no color.)
            When you cast this spell, exile up to one target noncreature, nonland permanent an opponent controls with mana value 4 or less. If you do, Larcenous Voidling gains "When this creature leaves the battlefield, target opponent draws a card."
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCastThis,
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.NonEmpty("exiled"), doing(Effect.SpellGets(Objects.Self, listOf(Mechanic.GainAbilities(listOf(
                triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), draw(1, Players.Target("opponent")),
                    targets = listOf(targetOpponent)))))))),
            targets = listOf(slot("permanent", and(not(creature), nonland, permanentFilter, opponents, manaValue(Comparison.AtMost, 4)), min = 0))),
    )

    // CR 702.33d, 614.1c: kicked, it enters with the counters (CR 122.1b: a trample counter) and with haste
    @Test
    fun voidLeaper() = card(
        name = "Void Leaper",
        cost = "{1}{R}",
        type = "Creature — Eldrazi",
        pt = "3/1",
        text = """
            Devoid (This card has no color.)
            Kicker {2}{C} (You may pay an additional {2}{C} as you cast this spell.)
            If this creature was kicked, it enters with two +1/+1 counters and a trample counter on it and with haste.
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Kicker(listOf(manaCost("{2}{C}"))),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(p1p1, n(2)),
            Modification.EntersWithCounters(CounterKind.Keyword(Keyword.Trample), n(1)),
            Modification.EntersWithAbilities(listOf(Keyword.Haste))))),
            affects = Affected.Objects(Objects.Self), condition = kicked),
    )

    // CR 110.2: the opponent who puts the creature onto the battlefield controls it; CR 603.4 "if {C} wasn't spent"
    @Test
    fun wumpusOfTheVoid() = card(
        name = "Voidborn Wumpus",
        cost = "{3}{G}",
        type = "Creature — Eldrazi Beast",
        pt = "6/6",
        text = """
            Devoid (This card has no color.)
            When you cast this spell, if {C} wasn't spent to cast it, target opponent may put a creature card from their hand onto the battlefield.
            Trample
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCastThis,
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))),
                min = n(0), max = n(1)), bind = "chosen", chooser = Players.Target("opponent")),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.Target("opponent")))),
            targets = listOf(targetOpponent), interveningIf = Condition.Not(colorlessSpent)),
        Keyword.Trample,
    )

    // CR 700.2, 702.33d: kicked, both modes are chosen (not "you may")
    @Test
    fun abyssDefiler() = card(
        name = "Abyss Defiler",
        cost = "{3}{U}{U}",
        type = "Creature — Eldrazi",
        pt = "3/5",
        text = """
            Devoid (This card has no color.)
            Kicker {C} (You may pay an additional {C} as you cast this spell.)
            When you cast this spell, choose one. If it was kicked, choose both instead.
            • Return target creature to its owner's hand.
            • Target player draws two cards, then discards a card.
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Kicker(listOf(manaCost("{C}"))),
        Ability.Triggered(youCastThis, Instructions(modes = Modes(listOf(
            mode(move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))),
            mode(draw(2, Players.Target("player")), discard(1, Players.Target("player")), targets = listOf(targetPlayer)),
        ), min = 1, max = 1, maxIf = kicked, maxIfCount = 2, chooser = Players.You))),
    )

    // CR 611.2c: X is fixed as the ability resolves
    @Test
    fun eldraziLinesmasher() = card(
        name = "Eldrazi Linesmasher",
        cost = "{1}{C}{R}",
        type = "Creature — Eldrazi",
        pt = "3/3",
        text = """
            Devoid (This card has no color.)
            Trample
            At the beginning of combat on your turn, target creature you control gains haste and gets +X/+0 until end of turn, where X is the number of Eldrazi you control.
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Trample,
        triggered(beginningOfCombatOnYourTurn,
            untilEndOfTurn(target("creature"), Mechanic.GainAbilities(listOf(Keyword.Haste)), Mechanic.ModifyPowerToughness(count(and(sub("Eldrazi"), you)), n(0))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 121.2: the second card drawn this turn
    @Test
    fun emrakulsHerald() = card(
        name = "Emrakul's Herald",
        cost = "{1}{U}",
        type = "Creature — Eldrazi Faerie Rogue",
        pt = "2/1",
        text = """
            Devoid (This card has no color.)
            Flying
            Whenever you draw your second card each turn, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Flying,
        triggered(drawSecondCard, create(eldraziSpawn)),
    )

    // CR 614.1a: the "would die this turn" replacement lasts the turn
    @Test
    fun fangedInferno() = card(
        name = "Fanged Inferno",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Devoid (This card has no color.)
            Fanged Inferno deals 4 damage to target creature or planeswalker. If that creature or planeswalker would die this turn, exile it instead.
        """,
    ) parsesTo abilities(
        devoid,
        spell(
            damage(4, toObjects(target("permanent"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("permanent")), from = Zone.Battlefield,
                to = Zone.Graveyard), modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = Duration.EndOfTurn)),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )
}
