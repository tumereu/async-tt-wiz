package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Streets of New Capenna chunk 11: enters-tapped lands and creatures, shield counters on entering, an Angel counter replacement,
 * a chosen permanent whose activated abilities are shut off and copied. */
class SncChunk11ReplacementTest {
    private val entersTapped = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)))
    private val shieldOnEntering = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(CounterKind.Shield, n(1)))))
    private val hasShield = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Shield, atLeast = 1))

    private fun tapForEither(a: Char, b: Char) = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors = listOf(Color.of(a)!!, Color.of(b)!!).map { ColorRef.Literal(it) })), Players.You)))

    private fun triome(name: String, a: Char, b: Char) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {$a} or {$b}.
            {2}{$a}{$b}, {T}, Sacrifice this land: Draw a card.
        """,
    ) parsesTo abilities(
        entersTapped,
        tapForEither(a, b),
        activated(cost(mana("{2}{$a}{$b}"), tap, sacrificeSelf), draw(1)),
    )

    // CR 614.1c (enters tapped), 602.2 (sacrifice as a cost)
    @Test fun botanicalPlaza() = triome("Verdant Plaza", 'G', 'W')
    @Test fun racersRing() = triome("Rowdy Ring", 'R', 'G')
    @Test fun skybridgeTowers() = triome("Skyline Towers", 'W', 'U')
    @Test fun tramwayStation() = triome("Trolley Station", 'B', 'R')
    @Test fun waterfrontDistrict() = triome("Harborfront District", 'U', 'B')

    // CR 614.1c
    @Test
    fun crookedCustodian() = card(
        name = "Crooked Janitor",
        cost = "{1}{B}",
        type = "Creature — Ogre Rogue",
        pt = "3/2",
        text = "This creature enters tapped.",
    ) parsesTo abilities(entersTapped)

    // CR 122.1 (shield counter), 611.3a (a static effect that applies only during your turn)
    @Test
    fun dapperShieldmate() = card(
        name = "Dapper Shieldbearer",
        cost = "{3}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            During your turn, this creature gets +2/+0.
        """,
    ) parsesTo abilities(shieldOnEntering, static(Mechanic.ModifyPowerToughness(n(2), n(0)), condition = Condition.YourTurn))

    // CR 122.1, 702.4a
    @Test
    fun disciplinedDuelist() = card(
        name = "Drilled Fencer",
        cost = "{G}{W}{U}",
        type = "Creature — Human Citizen",
        pt = "2/1",
        text = """
            Double strike
            This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
        """,
    ) parsesTo abilities(Keyword.DoubleStrike, shieldOnEntering)

    // CR 614.1c, 614.12 (each other Angel enters with a counter per Angel you control already)
    @Test
    fun giadaFontOfHope() = card(
        name = "Gianna, Spring of Hope",
        cost = "{1}{W}",
        type = "Legendary Creature — Angel",
        pt = "2/2",
        text = """
            Flying, vigilance
            Each other Angel you control enters with an additional +1/+1 counter on it for each Angel you already control.
            {T}: Add {W}. Spend this mana only to cast an Angel spell.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(another, sub("Angel"), you), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, count(and(sub("Angel"), you)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{W}")!!),
            restriction = and(spellFilter, sub("Angel"))), Players.You))),
    )

    // CR 122.1, 702.19 (trample), 613.1f (conditional ability grant)
    @Test
    fun rhoxPummeler() = card(
        name = "Rhox Thumper",
        cost = "{5}{G}",
        type = "Creature — Rhino Soldier",
        pt = "6/3",
        text = """
            This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            This creature has trample as long as it has a shield counter on it.
        """,
    ) parsesTo abilities(shieldOnEntering, static(Mechanic.GainAbilities(listOf(Keyword.Trample)), condition = hasShield))

    // CR 122.1, 702.2 (flash is a keyword)
    @Test
    fun swoopingProtector() = card(
        name = "Swooping Guardian",
        cost = "{3}{W}",
        type = "Creature — Bird Citizen",
        pt = "2/1",
        text = """
            Flash
            Flying
            This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
        """,
    ) parsesTo abilities(Keyword.Flash, Keyword.Flying, shieldOnEntering)

    // CR 613.4b (base power and toughness), 508.1 (attack trigger)
    @Test
    fun voiceOfTheVermin() = card(
        name = "Voice of the Rats",
        cost = "{3}{G}",
        type = "Creature — Human Citizen",
        pt = "2/2",
        text = """
            This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            Whenever this creature attacks, target creature you control has base power and toughness 4/4 until end of turn.
        """,
    ) parsesTo abilities(
        shieldOnEntering,
        triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(4), n(4))),
            targets = listOf(slot("creature", creatureYouControl))),
    )
}
