package arcanum.parser.sets.mh3

import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/*
 * MH3 chunk 03: cycling (CR 702.29). "When you cast or cycle this card" is one ability with two events: casting it (it triggers from the
 * stack, CR 603.6) and cycling it (from wherever the card ends up, CR 702.29c); "When you cycle this card, you may pay {X}" chooses X as the
 * cost is paid on resolution (CR 107.3f, 118.12); typecycling (CR 702.29e); the Landscape lands.
 */
class Mh3Chunk03CyclingTest {
    private val youCastOrCycleThis: Trigger = Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Is(Objects.Self), PlayerFilter.You),
        EventPattern.Cycle(ObjectFilter.Is(Objects.Self), PlayerFilter.You)))
    private val youCycleThis: Trigger = on(EventPattern.Cycle(ObjectFilter.Is(Objects.Self), PlayerFilter.You))
    private val addColorless: Step = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))

    // CR 603.6 (cast trigger from the stack), 702.29c (cycle trigger): one ability, two events (CR 603.2)
    @Test
    fun tidewreckLurker() = card(
        name = "Tidewreck Lurker",
        cost = "{7}",
        type = "Creature — Eldrazi Trilobite",
        pt = "7/7",
        text = """
            Vigilance
            When you cast or cycle Tidewreck Lurker, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
            Cycling {2}{U} ({2}{U}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(youCastOrCycleThis, create(eldraziSpawn)),
        Keyword.Cycling(manaCost("{2}{U}")),
    )

    // CR 603.6, 702.29c
    @Test
    fun gnarledTusker() = card(
        name = "Gnarled Tusker",
        cost = "{7}",
        type = "Creature — Eldrazi Boar Beast",
        pt = "6/8",
        text = """
            Reach
            When you cast or cycle Gnarled Tusker, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
            Cycling {2}{G} ({2}{G}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(youCastOrCycleThis, create(eldraziSpawn)),
        Keyword.Cycling(manaCost("{2}{G}")),
    )

    // CR 122.1 (energy), 107.14 (paying it), 118.12 ("you may pay … If you do"); the amount is the target card's mana value (CR 202.3)
    @Test
    fun sparkedRevival() = card(
        name = "Sparked Revival",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Choose up to one target artifact or creature card in your graveyard. You get {E}{E} (two energy counters). Then you may pay an amount of {E} equal to that card's mana value. If you do, return it from your graveyard to the battlefield.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(
            getEnergy(2),
            Step.Pay(cost(CostPart.PayEnergy(Value.Of(NumericProperty.ManaValue, target("card"), Combine.Single))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), move(target("card"), Zone.Battlefield)),
            targets = listOf(slot("card", inYourGraveyard(or(artifact, creature)), min = 0)),
        ),
        Keyword.Cycling(manaCost("{2}")),
    )

    // CR 115.1 (up to two targets), 702.29e (Plainscycling)
    @Test
    fun angelOfTheRubble() = card(
        name = "Angel of the Rubble",
        cost = "{5}{W}{W}",
        type = "Artifact Creature — Angel",
        pt = "5/7",
        text = """
            Flying
            When this creature enters, exile up to two target artifacts and/or enchantments.
            Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(move(target("enchantments"), Zone.Exile, postBind = "exiled"),
            targets = listOf(slot("enchantments", and(or(artifact, enchantment)), min = 0, max = 2))),
        Keyword.Typecycling(sub("Plains"), "Plainscycling", manaCost("{2}")),
    )

    // CR 107.3a (X announced for the spell); CR 107.3f, 118.12: in the cycle trigger nothing defines X (not the cycling cost, which has none): it is
    // chosen as {X} is paid on resolution (Audit: x was the cycling's X, a misread)
    @Test
    fun edictOfRighteousness() = card(
        name = "Edict of Righteousness",
        cost = "{X}{X}{2}{W}{W}",
        type = "Sorcery",
        text = """
            Create X 4/4 white Angel creature tokens with flying.
            Cycling {2}{W} ({2}{W}, Discard this card: Draw a card.)
            When you cycle this card, you may pay {X}. If you do, create X 1/1 white Soldier creature tokens.
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Angel", listOf(arcanum.api.Color.White), 4, 4, abilities = listOf(Keyword.Flying)), count = Value.Announced("X")), announced = listOf("X")),
        Keyword.Cycling(manaCost("{2}{W}")),
        triggered(youCycleThis,
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), create(soldier, count = Value.Announced("X")))),
    )

    // ---- The Landscapes: CR 106.1 ({C}), 701.23 (search for one of three basic land types, CR 205.3i), 702.29a (cycling)

    private fun landscape(name: String, types: List<String>, cycling: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}, Sacrifice this land: Search your library for a basic ${types[0]}, ${types[1]}, or ${types[2]} card, put it onto the battlefield tapped, then shuffle.
            Cycling $cycling ($cycling, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), addColorless),
        activated(cost(tap, sacrificeSelf),
            search(and(ObjectFilter.Super(Supertype.Basic), or(*types.map { sub(it) }.toTypedArray()))),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle),
        Keyword.Cycling(manaCost(cycling)),
    )

    @Test
    fun plentifulVista() = landscape("Plentiful Vista", listOf("Forest", "Island", "Mountain"), "{G}{U}{R}")

    @Test
    fun taintedVista() = landscape("Tainted Vista", listOf("Plains", "Island", "Swamp"), "{W}{U}{B}")

    @Test
    fun beguilingVista() = landscape("Beguiling Vista", listOf("Plains", "Swamp", "Forest"), "{W}{B}{G}")

    @Test
    fun ominousVista() = landscape("Ominous Vista", listOf("Swamp", "Forest", "Island"), "{B}{G}{U}")

    @Test
    fun hazardousVista() = landscape("Hazardous Vista", listOf("Island", "Mountain", "Plains"), "{U}{R}{W}")

    @Test
    fun simmeringVista() = landscape("Simmering Vista", listOf("Island", "Swamp", "Mountain"), "{U}{B}{R}")

    @Test
    fun brokenVista() = landscape("Broken Vista", listOf("Mountain", "Plains", "Swamp"), "{R}{W}{B}")

    @Test
    fun shelteredVista() = landscape("Sheltered Vista", listOf("Mountain", "Forest", "Plains"), "{R}{G}{W}")

    @Test
    fun placidVista() = landscape("Placid Vista", listOf("Forest", "Plains", "Island"), "{G}{W}{U}")

    @Test
    fun gnarledVista() = landscape("Gnarled Vista", listOf("Swamp", "Mountain", "Forest"), "{B}{R}{G}")
}
