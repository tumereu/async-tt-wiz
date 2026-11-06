package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 15: the timeshifted reprints, first part: protection from artifacts, echo (CR 702.30), sacrificing itself as a
 * cost, an upkeep "unless you discard" with skipping your draw step (CR 614.10), shroud for you and damage to you prevented, a creature
 * attacking you or your planeswalker (CR 506.2), returning two lands, all permanents to their owners' hands, an ability of a card in the
 * graveyard (CR 113.6b), each player's upkeep sacrifice, each player choosing a creature type (CR 101.4), a mana ability with a discard,
 * winning the game with luck counters (CR 104.2b), X twice in a cost (CR 107.3) and a free alternative cost (CR 118.9).
 */
class Mh2Chunk15TimeshiftedTest {
    @Test
    fun seraphicCurator() = card(
        name = "Seraphic Curator",
        cost = "{1}{W}",
        type = "Creature — Angel Spirit",
        text = "Flying, protection from artifacts",
        pt = "1/1",
    ) parsesTo abilities(Keyword.Flying, Keyword.Protection(artifact))

    // CR 702.30a echo
    @Test
    fun karmicShepherd() = card(
        name = "Karmic Shepherd",
        cost = "{3}{W}{W}",
        type = "Creature — Angel Spirit",
        text = """
            Flying, protection from black
            Echo {3}{W}{W} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
            When this creature enters, return target creature card from your graveyard to the battlefield.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Protection(ObjectFilter.OfColor(Color.Black)),
        Keyword.Echo(manaCost("{3}{W}{W}")),
        triggered(entersSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun sigilOfCleansing() = card(
        name = "Sigil of Cleansing",
        cost = "{1}{W}",
        type = "Enchantment",
        text = "Sacrifice this enchantment: Destroy target artifact or enchantment.",
    ) parsesTo abilities(activated(cost(sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))))

    // CR 118.12a: "unless you discard a card"; CR 614.10: skipping a step; CR 702.18: shroud for a player; CR 615.1 prevention
    @Test
    fun lonelyConfinement() = card(
        name = "Lonely Confinement",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, sacrifice this enchantment unless you discard a card.
            Skip your draw step.
            You have shroud. (You can't be the target of spells or abilities.)
            Prevent all damage that would be dealt to you.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, Step.Pay(Cost(listOf(discardACard)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Sacrifice(Objects.Self))))),
        playerStatic(Mechanic.SkipStep(TurnStep.Draw)),
        playerStatic(Mechanic.CantBeTargeted(by = ObjectFilter.Any)),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)))),
    )

    // CR 506.2: attacking you or a planeswalker you control makes you its defending player
    @Test
    fun spiritSnare() = card(
        name = "Spirit Snare",
        cost = "{W}",
        type = "Enchantment",
        text = "{W}, Sacrifice this enchantment: Exile target creature that's attacking you or a planeswalker you control.",
    ) parsesTo abilities(
        activated(cost(mana("{W}"), sacrificeSelf), move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(creature, attacking,
            ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You)))))),
    )

    @Test
    fun spellRefusal() = card(
        name = "Spell Refusal",
        cost = "{U}{U}",
        type = "Instant",
        text = "Counter target spell.",
    ) parsesTo abilities(spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))))

    @Test
    fun brineDrake() = card(
        name = "Brine Drake",
        cost = "{2}{U}",
        type = "Creature — Drake",
        text = """
            Flying
            When this creature enters, return two target lands you control to their owner's hand.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("lands"), Zone.Hand), targets = listOf(slot("lands", and(land, you), min = 2, max = 2))),
    )

    @Test
    fun sigilOfRemoval() = card(
        name = "Sigil of Removal",
        cost = "{U}",
        type = "Enchantment",
        text = "Sacrifice this enchantment: Return target creature to its owner's hand.",
    ) parsesTo abilities(activated(cost(sacrificeSelf), move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))))

    @Test
    fun upturning() = card(
        name = "Upturning",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = "Return all permanents to their owners' hands.",
    ) parsesTo abilities(spell(move(Objects.Each(permanentFilter), Zone.Hand)))

    // CR 113.6b: the ability works only in the graveyard
    @Test
    fun amazement() = card(
        name = "Amazement",
        cost = "{3}{U}",
        type = "Creature — Incarnation",
        text = """
            Flying
            As long as this card is in your graveyard and you control an Island, creatures you control have flying.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.Flying), affects = Objects.Each(creatureYouControl), condition = Condition.And(listOf(
            Condition.ObjectMatches(Objects.Self, inZone(Zone.Graveyard)), Condition.Exists(and(sub("Island"), you), atLeast = 1))))
            .copy(functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun boneShearer() = card(
        name = "Bone Shearer",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Minion",
        text = """
            Flying
            Echo {2}{B} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
            When this creature enters, destroy target nonartifact, nonblack creature.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Echo(manaCost("{2}{B}")),
        triggered(entersSelf, destroy(target("permanent")), targets = listOf(slot("permanent", and(not(artifact), not(ObjectFilter.OfColor(Color.Black)), creature)))),
    )

    // CR 101.4: the player whose upkeep it is chooses and sacrifices
    @Test
    fun braidaCabalServant() = card(
        name = "Braida, Cabal Servant",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Human Minion",
        text = "At the beginning of each player's upkeep, that player sacrifices an artifact, creature, or land of their choice.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any),
            Step.Choose(Choice.Objects(and(or(artifact, creature, land), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Active))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Active),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))),
    )

    @Test
    fun covetousness() = card(
        name = "Covetousness",
        cost = "{3}{B}",
        type = "Enchantment",
        text = "{B}, Pay 2 life: Draw a card.",
    ) parsesTo abilities(activated(cost(mana("{B}"), CostPart.PayLife(n(2))), draw(1)))

    // CR 101.4: in turn order each player chooses, then each returns their own cards of any type chosen
    @Test
    fun patriarchsSummons() = card(
        name = "Patriarch's Summons",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = "Each player chooses a creature type. Each player returns all creature cards of a type chosen this way from their graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.Bound("player"))), together = true),
            move(Objects.Each(and(creature, ObjectFilter.Sub(SubtypeRef.Chosen("type")), inZone(Zone.Graveyard))), Zone.Battlefield,
                controller = Players.OwnerOf(Objects.Each(and(creature, ObjectFilter.Sub(SubtypeRef.Chosen("type")), inZone(Zone.Graveyard))))),
        ),
    )

    @Test
    fun skirgeCompanion() = card(
        name = "Skirge Companion",
        cost = "{4}{B}",
        type = "Creature — Phyrexian Imp",
        text = """
            Flying
            Discard a card: Add {B}.
        """,
        pt = "3/2",
    ) parsesTo abilities(Keyword.Flying, activated(cost(discardACard), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!)), Players.You))))

    // CR 705.2 winning a flip; CR 104.2b: an effect that says you win
    @Test
    fun luckyEncounter() = card(
        name = "Lucky Encounter",
        cost = "{2}{R}{R}",
        type = "Enchantment",
        text = """
            Whenever you win a coin flip, put a luck counter on this enchantment.
            At the beginning of your upkeep, if this enchantment has ten or more luck counters on it, you win the game.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.FlipCoin(PlayerFilter.You, won = true)), counters(Objects.Self, 1, CounterKind.Named("luck"))),
        triggered(yourUpkeep, doing(Effect.WinGame(Players.You)), interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("luck"), atLeast = 10))),
    )

    @Test
    fun flameFissure() = card(
        name = "Flame Fissure",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Flame Fissure deals 4 damage to each player.",
    ) parsesTo abilities(spell(damage(4, Recipients(players = Players.Each(PlayerFilter.Any)))))

    @Test
    fun goblinShellfire() = card(
        name = "Goblin Shellfire",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Sacrifice a creature: This enchantment deals 1 damage to any target.",
    ) parsesTo abilities(activated(cost(sacrificeA(creature)), damage(1, Recipients(target = "any")), targets = listOf(anyTarget)))

    // CR 107.3: both X are the same announced number
    @Test
    fun gorillaMystic() = card(
        name = "Gorilla Mystic",
        cost = "{R}",
        type = "Creature — Ape Shaman",
        text = "{X}{X}{1}: Destroy target noncreature artifact with mana value X.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{X}{X}{1}")), Instructions(
            targets = listOf(slot("artifact", and(noncreature, artifact, ObjectFilter.ManaValue(Comparison.Equal, x)))),
            steps = listOf(destroy(target("artifact"))),
            announced = listOf("X"),
        ), anyTime),
    )

    @Test
    fun imperialEnlister() = card(
        name = "Imperial Enlister",
        cost = "{2}{R}",
        type = "Creature — Human Advisor",
        text = "When this creature enters, search your library for a creature card with power 2 or less, reveal it, put it into your hand, then shuffle.",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, search(and(creature, power(Comparison.AtMost, 2)), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 118.9: cast without paying its mana cost, only while the condition holds
    @Test
    fun moggScavenging() = card(
        name = "Mogg Scavenging",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            If an opponent controls an Island and you control a Mountain, you may cast this spell without paying its mana cost.
            Destroy target artifact.
        """,
    ) parsesTo abilities(
        static(Mechanic.MayPlay(Zone.Hand, arcanum.api.PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You), condition = Condition.And(listOf(
            Condition.Exists(and(sub("Island"), opponents), atLeast = 1), Condition.Exists(and(sub("Mountain"), you), atLeast = 1)))),
        spell(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
    )
}
