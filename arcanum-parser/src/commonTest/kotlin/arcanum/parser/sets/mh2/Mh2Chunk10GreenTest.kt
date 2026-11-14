package arcanum.parser.sets.mh2

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 10: green cards, second part: exalted with a three-colour mana ability, bushido (CR 702.45), Food tokens,
 * suspend on a mana creature, X mana of any one colour, evolve (CR 702.100), proliferate and a fight with an optional second target
 * (CR 701.14), returning itself on paying {1}, colour lords, rebound, a cost reduction for spells cast this turn with trample over
 * planeswalkers (CR 702.19c) and hexproof the turn it entered, eternalize, a choice of token, enrage, "choose two" with a loyalty
 * ability target (CR 606) and reinforce X (CR 702.77, 107.3a).
 */
class Mh2Chunk10GreenTest {
    @Test
    fun lowlyHierarch() = card(
        name = "Lowly Hierarch",
        cost = "{G}",
        type = "Creature — Goblin Shaman",
        text = """
            Exalted (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)
            {T}: Add {B}, {R}, or {G}.
        """,
        pt = "0/1",
    ) parsesTo abilities(Keyword.Exalted, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors = listOf(Color.Black, Color.Red, Color.Green).map { ColorRef.Literal(it) })), Players.You))))

    // CR 702.45a bushido
    @Test
    fun jadeRetaliator() = card(
        name = "Jade Retaliator",
        cost = "{1}{G}",
        type = "Creature — Frog Samurai",
        text = "Bushido 2 (Whenever this creature blocks or becomes blocked, it gets +2/+2 until end of turn.)",
        pt = "2/2",
    ) parsesTo abilities(Keyword.Bushido(2))

    @Test
    fun gemEyedViper() = card(
        name = "Gem-Eyed Viper",
        cost = "{2}{G}",
        type = "Creature — Snake",
        text = """
            Deathtouch
            When this creature dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/1",
    ) parsesTo abilities(Keyword.Deathtouch, triggered(diesSelf, createTreasure))

    @Test
    fun groveStrider() = card(
        name = "Grove Strider",
        cost = "{4}{G}{G}",
        type = "Creature — Treefolk",
        text = """
            When this creature enters, create two Food tokens. (They're artifacts with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Basic landcycling {1}{G} ({1}{G}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "6/4",
    ) parsesTo abilities(triggered(entersSelf, create(food, count = n(2))), basicLandcycling("{1}{G}"))

    @Test
    fun riftSeeder() = card(
        name = "Rift Seeder",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        text = """
            {T}: Add one mana of any color.
            Suspend 2—{G} (Rather than cast this card from your hand, you may pay {G} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost. It has haste.)
        """,
        pt = "1/3",
    ) parsesTo abilities(addAnyColor, Keyword.Suspend(2, manaCost("{G}")))

    // CR 106.1b: X mana of one colour, X counted as it resolves
    @Test
    fun sanctumSpinner() = card(
        name = "Sanctum Spinner",
        cost = "{1}{G}",
        type = "Enchantment Creature — Dryad",
        text = "{T}: Add X mana of any one color, where X is the number of enchantments you control.",
        pt = "0/2",
    ) parsesTo abilities(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(count(and(enchantment, you)))), Players.You))))

    // CR 702.100a evolve
    @Test
    fun scurryingOak() = card(
        name = "Scurrying Oak",
        cost = "{2}{G}",
        type = "Creature — Treefolk",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            Whenever one or more +1/+1 counters are put on this creature, you may create a 1/1 green Squirrel creature token.
        """,
        pt = "1/2",
    ) parsesTo abilities(Keyword.Evolve, triggered(countersPutOnSelf, Step.May(listOf(create(squirrel)), Players.You)))

    // CR 701.14a: a fight with up to one target; with no second target, no fight
    @Test
    fun scentOfDread() = card(
        name = "Scent of Dread",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
            Target creature you control fights up to one target creature you don't control.
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Action(KeywordAction.Proliferate)), doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0))),
    )

    @Test
    fun squirrelRefuge() = card(
        name = "Squirrel Refuge",
        cost = "{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 1/1 green Squirrel creature token.
            Whenever a nontoken creature you control dies, you may pay {1}. If you do, return this enchantment to its owner's hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(squirrel)),
        triggered(dies(and(nontoken, creature, you)), Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(move(Objects.Self, Zone.Hand)))),
    )

    @Test
    fun squirrelMonarch() = card(
        name = "Squirrel Monarch",
        cost = "{1}{G}",
        type = "Creature — Squirrel Noble",
        text = "Other Squirrels you control get +1/+1.",
        pt = "2/2",
    ) parsesTo abilities(static(pt(1, 1), affects = Objects.Each(and(another, sub("Squirrel"), you))))

    @Test
    fun sylvanHymn() = card(
        name = "Sylvan Hymn",
        cost = "{G}{G}",
        type = "Enchantment",
        text = """
            Green creatures you control get +1/+1.
            Whenever a green creature you control enters, scry 1.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(ObjectFilter.OfColor(Color.Green), creature, you))),
        triggered(enters(and(ObjectFilter.OfColor(Color.Green), creature, you)), scry(1)),
    )

    @Test
    fun groundmorph() = card(
        name = "Groundmorph",
        cost = "{3}{G}",
        type = "Sorcery",
        text = """
            Search your library for a basic land card, put it onto the battlefield, then shuffle.
            Rebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)
        """,
    ) parsesTo abilities(spell(search(basicLand), move(Objects.Bound("found"), Zone.Battlefield), shuffle), Keyword.Rebound)

    // CR 601.2f: {3} less for each other spell cast this turn; CR 702.19c trample over planeswalkers
    @Test
    fun thravosTempestsHowl() = card(
        name = "Thravos, Tempest's Howl",
        cost = "{10}{G}{G}",
        type = "Legendary Creature — Dinosaur",
        text = """
            This spell costs {3} less to cast for each other spell cast this turn.
            Trample, haste
            Trample over planeswalkers (This creature can deal excess combat damage to the controller of the planeswalker it's attacking.)
            Thravos has hexproof as long as it entered this turn.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{3}")!!,
            Value.EventsThisTurn(EventPattern.Cast(not(isSelf), PlayerFilter.Any), Measure.Events)))),
        Keyword.Trample,
        Keyword.Haste,
        Keyword.TrampleOverPlaneswalkers,
        static(has(Keyword.Hexproof), condition = Condition.ObjectMatches(Objects.Self,
            ObjectFilter.ThisTurn(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield)))),
    )

    // CR 702.129a eternalize
    @Test
    fun agelessWitness() = card(
        name = "Ageless Witness",
        cost = "{2}{G}{G}",
        type = "Creature — Human Shaman",
        text = """
            When this creature enters, return target card from your graveyard to your hand.
            Eternalize {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Human Shaman with no mana cost. Eternalize only as a sorcery.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(cardFilter)))),
        Keyword.Eternalize(manaCost("{5}{G}{G}")),
    )

    // CR 207.2c landfall; one token or the other, chosen as it resolves
    @Test
    fun untiringProvisioner() = card(
        name = "Untiring Provisioner",
        cost = "{2}{G}",
        type = "Creature — Elf Scout",
        text = "Landfall — Whenever a land you control enters, create a Food token or a Treasure token. (Food is an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\" Treasure is an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(landfall,
            Step.Choose(Choice.Option(listOf("Food", "Treasure")), bind = "token", chooser = Players.You),
            Step.If(Condition.Chose("token", "Food"), then = listOf(create(food)), otherwise = listOf(createTreasure)),
            abilityWord = "Landfall"),
    )

    // CR 207.2c enrage
    @Test
    fun urbanKnifetooth() = card(
        name = "Urban Knifetooth",
        cost = "{2}{G}{G}",
        type = "Creature — Dinosaur",
        text = """
            Vigilance
            Enrage — Whenever this creature is dealt damage, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = isSelf), per = DamageOccurrence.Recipient)), doing(Effect.Action(KeywordAction.Proliferate)), abilityWord = "Enrage"),
    )

    // CR 700.2: two different modes (each mode keeps its own "target player"); CR 606: a loyalty ability on the Stack
    @Test
    fun verdantDecree() = card(
        name = "Verdant Decree",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose two —
            • Target player creates two tapped 1/1 green Squirrel creature tokens.
            • Counter target loyalty ability of a planeswalker.
            • Exile target card from a graveyard.
            • Target player gains 3 life.
        """,
    ) parsesTo abilities(
        modal(
            mode(doing(Effect.CreateToken(squirrel, n(2), Players.Target("player"), tapped = true)), targets = listOf(targetPlayer)),
            mode(doing(Effect.Counter(target("ability"))), targets = listOf(slot("ability", ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Loyalty)))),
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
            mode(gainLife(3, Players.Target("player")), targets = listOf(targetPlayer)),
            min = 2, max = 2,
        ),
    )

    // CR 107.3m: X of the enters replacement is the X it was cast with; CR 702.77a reinforce X: the X paid for it
    @Test
    fun wrenhollowHydra() = card(
        name = "Wrenhollow Hydra",
        cost = "{X}{G}",
        type = "Creature — Hydra",
        text = """
            Reach
            This creature enters with X +1/+1 counters on it.
            Reinforce X—{X}{G}{G} ({X}{G}{G}, Discard this card: Put X +1/+1 counters on target creature.)
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Reach,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))),
        Keyword.ReinforceX(manaCost("{X}{G}{G}")),
    )
}
