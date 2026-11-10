package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 15: artifacts, lands and the meld pair: a land made colourless by a counter for as long as it has it (CR 611.2b), extra
 * mana when a land is tapped for {C} (CR 106.12), a base power set by your life total, an activation limited by this turn's combat damage, a
 * free spell cast up to the damage dealt, cards shuffled back counted, attacking with first strike and a lure, a dying trigger doubled
 * (CR 603.2d), base power from Towns, three or more blockers, an optional attach that grants abilities, a hideaway played free if you have four
 * legends, a land untapped only on your first three turns, and the meld pair (CR 701.42, 712.4).
 */
class FinChunk15ArtifactsLandsAndMeldTest {
    // CR 611.2b, 205.1a (losing land types), 106.12
    @Test
    fun ultimusOriginOfTheVoid() = card(
        name = "Ultimus, Origin of the Void",
        cost = "{5}",
        type = "Legendary Creature — God",
        pt = "4/4",
        text = """
            Flying
            Whenever Ultimus attacks, put a blight counter on target land. For as long as that land has a blight counter on it, it loses all land types and abilities and has "{T}: Add {C}."
            Whenever you tap a land for {C}, add an additional {C}.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, counters(target("land"), 1, blight),
            doing(Effect.ApplyMechanics(listOf(Mechanic.LoseAllLandTypes, Mechanic.LoseAbilities(), has(addColorless)), Affected.Objects(target("land")),
                Duration.AsLongAs(Condition.ObjectMatches(target("land"), ObjectFilter.HasCounter(blight, atLeast = 1))))),
            targets = listOf(slot("land", land))),
        triggered(on(EventPattern.Tap(and(land, you), forMana = true, producing = ManaCost.parse("{C}"))),
            doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
    )

    // CR 613.4b
    @Test
    fun aegisAndBlade() = card(
        name = "Aegis and Blade",
        cost = "{6}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature has base power and toughness X/X, where X is your life total.
            Equip {5}
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(Value.LifeOf(Players.You), Value.LifeOf(Players.You)), affects = equipped),
        Keyword.Equip(manaCost("{5}")),
    )

    @Test
    fun goalBall() = card(
        name = "Goal Ball",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            GOOOOAAAALLL! — {T}, Sacrifice this artifact: Draw two cards. Activate only if an opponent was dealt combat damage by a legendary creature this turn.
        """,
    ) parsesTo abilities(
        addAnyColor,
        activated(cost(tap, sacrificeSelf), draw(2), rules = anyTime.copy(condition = Condition.HappenedThisTurn(EventPattern.Damage(and(legendary, creature),
            RecipientFilter(players = PlayerFilter.Opponent), combat = true), atLeast = 1, measure = Measure.Events))).copy(abilityWord = "GOOOOAAAALLL!"),
    )

    /** "you may cast a spell from your hand with mana value less than or equal to that damage without paying its mana cost" (CR 601.2a, 118.9) */
    @Test
    fun broadswordOfTheSoldier() = card(
        name = "Broadsword of the Soldier",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+2.
            Whenever equipped creature deals combat damage to a player, draw a card, then you may cast a spell from your hand with mana value less than or equal to that damage without paying its mana cost.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(3, 2), affects = equipped),
        triggered(on(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(players = PlayerFilter.Any), combat = true)), draw(1),
            Step.Choose(Choice.Objects(and(not(land), manaValue2(Value.EventAmount), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("chosen"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun cureAll() = card(
        name = "Cure-All",
        cost = "{1}",
        type = "Artifact",
        text = """
            This artifact enters tapped.
            {5}, {T}, Exile this artifact: Shuffle all nonland cards from your graveyard into your library. You gain life equal to the number of cards shuffled into your library this way.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(mana("{5}"), tap, CostPart.Exile(arcanum.api.CostObjects.Self, Zone.Battlefield)),
            move(Objects.Each(inYourGraveyard(nonland)), Zone.Library, postBind = "shuffled"), shuffle,
            doing(Effect.GainLife(Players.You, Value.Count(Objects.Bound("shuffled"))))),
    )

    // CR 509.1c (must be blocked), 603.2d
    @Test
    fun theTrueKatana() = card(
        name = "The True Katana",
        cost = "{3}",
        type = "Legendary Artifact — Equipment",
        text = """
            As long as equipped creature is attacking, it has first strike and must be blocked if able.
            Equipped creature has "If a creature dying causes a triggered ability of this creature or an emblem you own to trigger, that ability triggers an additional time."
            Equip {2}
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), Mechanic.MustBeBlocked, affects = equipped, condition = Condition.ObjectMatches(equipped, attacking)),
        static(has(static(Mechanic.TriggersAdditionalTime(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
            or(ObjectFilter.Is(Objects.Self), and(inZone(Zone.Command), ObjectFilter.OwnedBy(PlayerFilter.You)))))), affects = equipped),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun pupuSaucer() = card(
        name = "Moonhop Saucer",
        cost = "{2}",
        type = "Artifact Creature — Construct Alien",
        pt = "0/4",
        text = """
            Flying
            {T}: You may put a land card from your hand onto the battlefield.
            {3}: Until end of turn, this creature's base power becomes equal to the number of Towns you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(tap), Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen",
            chooser = Players.You), move(Objects.Bound("chosen"), Zone.Battlefield)),
        activated(manaCost("{3}"), untilEndOfTurn(Objects.Self, Mechanic.SetPowerToughness(count(and(town, you)), null))),
    )

    // CR 509.1b
    @Test
    fun relentlessHunterUnit() = card(
        name = "Relentless Hunter Unit",
        cost = "{6}",
        type = "Artifact Creature — Robot Spider",
        pt = "6/5",
        text = """
            This creature can't be blocked except by three or more creatures.
            {8}: Return this card from your graveyard to the battlefield tapped with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        static(Mechanic.MinimumBlockers(3)),
        activated(manaCost("{8}"), doing(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = true, counters = listOf(CountersToPut(finality, n(1))))))),
    )

    @Test
    fun clockworkScythe() = card(
        name = "Clockwork Scythe",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            A Test of Your Reflexes! — When this Equipment enters, you may attach it to target creature you control. If you do, that creature gains first strike until end of turn and must be blocked this turn if able.
            Equipped creature gets +2/+1.
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(steps = listOf(doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature"))))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), untilEndOfTurn(target("creature"), has(Keyword.FirstStrike), Mechanic.MustBeBlocked)),
            targets = listOf(slot("creature", creatureYouControl)), abilityWord = "A Test of Your Reflexes!"),
        static(pt(2, 1), affects = equipped),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 702.75a
    @Test
    fun corvansRetreat() = card(
        name = "Corvan's Retreat",
        cost = null,
        type = "Land — Town",
        text = """
            Hideaway 4 (When this land enters, look at the top four cards of your library, exile one face down, then put the rest on the bottom in a random order.)
            {T}: Add {C}.
            {2}, {T}: You may play the exiled card without paying its mana cost if you control four or more legendary creatures.
        """,
    ) parsesTo abilities(
        Keyword.Hideaway(4),
        addColorless,
        activated(cost(mana("{2}"), tap), ifThen(Condition.Exists(and(legendary, creature, you), atLeast = 4),
            Step.May(listOf(doing(Effect.Cast(Objects.LinkedTo(Keyword.Hideaway.Kind), PlayKind.Play, withoutPaying = true, caster = Players.You))), Players.You))),
    )

    @Test
    fun firstVillage() = card(
        name = "First Village",
        cost = null,
        type = "Land — Town",
        text = """
            This land enters tapped unless it's your first, second, or third turn of the game.
            {T}: Add {C}.
            {T}, Pay 1 life: Add one mana of any color.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.And(listOf(Condition.YourTurn, Condition.Compare(Value.TurnsTaken(Players.You), arcanum.api.Comparison.AtMost, n(3)))))),
        addColorless,
        activated(cost(tap, CostPart.PayLife(n(1))), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Choose(n(1))), Players.You))),
    )

    /** "if you both own and control Valla and a creature named Fenna, Fearless Pulsebearer, you may pay {3}{B}{G}. If you do, exile them, then
     * meld them into Ragnaroth, Divine Deliverance." (CR 701.42a, 712.4) */
    @Test
    fun vallaCheerfulPulsebearer() = cardFaces(Layout.Meld("Fenna, Fearless Pulsebearer"),
        card(
            name = "Valla, Cheerful Pulsebearer",
            cost = "{3}{G}",
            type = "Legendary Creature — Human Cleric",
            pt = "3/2",
            text = """
                When Valla enters, mill two cards, then return a permanent card from your graveyard to your hand.
                At the beginning of your first main phase, if you both own and control Valla and a creature named Fenna, Fearless Pulsebearer, you may pay {3}{B}{G}. If you do, exile them, then meld them into Ragnaroth, Divine Deliverance.
            """,
        ) to abilities(
            triggered(entersSelf, mill(2),
                Step.Choose(Choice.Objects(inYourGraveyard(permanentCard), n(1), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand)),
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
                Step.Pay(cost(mana("{3}{B}{G}")), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"),
                    Step.Choose(Choice.Objects(partner, n(1), n(1)), bind = "partner", chooser = Players.You),
                    move(Objects.Union(listOf(Objects.Self, Objects.Bound("partner"))), Zone.Exile, postBind = "exiled"),
                    doing(Effect.Action(KeywordAction.Meld(Objects.Bound("exiled"), "Ragnaroth, Divine Deliverance")))),
                // CR 603.4, 701.42a: "you both own and control" Valla too
                interveningIf = Condition.And(listOf(Condition.ObjectMatches(Objects.Self, and(you, ObjectFilter.OwnedBy(PlayerFilter.You))),
                    Condition.Exists(partner, atLeast = 1)))),
        ),
        card(
            name = "Ragnaroth, Divine Deliverance",
            cost = null,
            type = "Legendary Creature — Beast Avatar",
            colorIndicator = "BG",
            pt = "7/6",
            text = """
                Reach, vigilance, menace, trample, haste
                When Ragnaroth dies, destroy target permanent and return target nonlegendary permanent card from your graveyard to the battlefield.
            """,
        ) to ragnarothAbilities,
    )

    @Test
    fun fennaFearlessPulsebearer() = cardFaces(Layout.Meld("Valla, Cheerful Pulsebearer"),
        card(
            name = "Fenna, Fearless Pulsebearer",
            cost = "{2}{B}",
            type = "Legendary Creature — Human Warrior",
            pt = "2/3",
            text = """
                Whenever one or more cards leave your graveyard, you draw a card and you lose 1 life. This ability triggers only once each turn.
                (Melds with Valla, Cheerful Pulsebearer.)
            """,
        ) to abilities(
            Ability.Triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard)),
                oneOrMore = true), Instructions(steps = listOf(draw(1), loseLife(1))), perTurn = 1),
        ),
        card(
            name = "Ragnaroth, Divine Deliverance",
            cost = null,
            type = "Legendary Creature — Beast Avatar",
            colorIndicator = "BG",
            pt = "7/6",
            text = """
                Reach, vigilance, menace, trample, haste
                When Ragnaroth dies, destroy target permanent and return target nonlegendary permanent card from your graveyard to the battlefield.
            """,
        ) to ragnarothAbilities,
    )

    private val blight = arcanum.api.CounterKind.Named("blight")
    private val partner = and(creature, ObjectFilter.Named("Fenna, Fearless Pulsebearer"), you, ObjectFilter.OwnedBy(PlayerFilter.You))
    private val ragnarothAbilities = abilities(
        Keyword.Reach, Keyword.Vigilance, Keyword.Menace, Keyword.Trample, Keyword.Haste,
        triggered(diesSelf, destroy(target("permanent")), move(target("card"), Zone.Battlefield),
            targets = listOf(slot("permanent", permanentFilter), slot("card", inYourGraveyard(not(legendary), permanentCard)))),
    )
    private fun manaValue2(at: Value): ObjectFilter = ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, at)
}
