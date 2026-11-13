package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 09: check lands ("enters tapped unless you control a X or a Y"), enters-with-counters replacements, enters tapped,
 * "doesn't untap", a draw-from-empty-library win, and a token-doubling replacement.
 */
class IsdChunk09ReplacementTest {
    private fun checkLand(name: String, a: String, b: String, manaA: String, manaB: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control ${article(a)} $a or ${article(b)} $b.
            {T}: Add {$manaA} or {$manaB}.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.Exists(and(or(sub(a), sub(b)), you), atLeast = 1)),
        ),
        addEither(manaA, manaB),
    )

    private fun article(noun: String) = if (noun[0] in "AEIOU") "an" else "a"

    // CR 614.1c, 614.12: enters tapped unless a condition holds as it enters
    @Test fun cliffheadRetreat() = checkLand("Cliffhead Retreat", "Mountain", "Plains", "R", "W")
    @Test fun hinterfieldHarbor() = checkLand("Hinterfield Harbor", "Forest", "Island", "G", "U")
    @Test fun isolatedShrine() = checkLand("Isolated Shrine", "Plains", "Swamp", "W", "B")
    @Test fun sulphurFalls() = checkLand("Sulphur Falls", "Island", "Mountain", "U", "R")
    @Test fun woodlandCrypt() = checkLand("Woodland Crypt", "Swamp", "Forest", "B", "G")

    // CR 614.1c, 113.6: works while the card is in its owner's graveyard; each Human enters with a counter
    @Test
    fun dearlyDeparted() = card(
        name = "Dearly Deceased",
        cost = "{4}{W}{W}",
        type = "Creature — Spirit",
        pt = "5/5",
        text = """
            Flying
            As long as this creature is in your graveyard, each Human creature you control enters with an additional +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(and(sub("Human"), creature, you), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    // CR 614.1c: enters tapped
    @Test
    fun diregrafGhoulLike() = card(
        name = "Diregrave Ghoul",
        cost = "{B}",
        type = "Creature — Zombie",
        pt = "2/2",
        text = "This creature enters tapped.",
    ) parsesTo abilities(entersTapped)

    // CR 614.1c, 502.3 (doesn't untap), 602.1 (sacrifice as a cost)
    @Test
    fun grimgrinCorpseBorn() = card(
        name = "Grimgrave, Corpse-Risen",
        cost = "{3}{U}{B}",
        type = "Legendary Creature — Zombie Warrior",
        pt = "5/5",
        text = """
            Grimgrave enters tapped and doesn't untap during your untap step.
            Sacrifice another creature: Untap Grimgrave and put a +1/+1 counter on it.
            Whenever Grimgrave attacks, destroy target creature defending player controls, then put a +1/+1 counter on Grimgrave.
        """,
    ) parsesTo abilities(
        entersTapped,
        static(Mechanic.DoesntUntap),
        activated(cost(sacrificeA(and(another, creature))), untapIt(Objects.Self), counters(Objects.Self)),
        triggered(attacksSelf, destroy(target("creature")), counters(Objects.Self),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))),
    )

    // CR 614.1a, 121.4 (drawing from an empty library), 104.2b (win the game)
    @Test
    fun laboratoryManiacLike() = card(
        name = "Archive Maniac",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = "If you would draw a card while your library has no cards in it, you win the game instead.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = Instructions(steps = listOf(doing(Effect.WinGame(Players.You))))),
            condition = Condition.Not(Condition.Exists(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))),
    )

    // CR 614.1c (enters with counters), 107.3 (X), 602.1
    @Test
    fun mikaeusTheLunarchLike() = card(
        name = "Mikael, the Moonward",
        cost = "{X}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "0/0",
        text = """
            Mikael enters with X +1/+1 counters on it.
            {T}: Put a +1/+1 counter on Mikael.
            {T}, Remove a +1/+1 counter from Mikael: Put a +1/+1 counter on each other creature you control.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(of = Objects.Self))))), affects = Affected.Objects(Objects.Self)),
        activated(cost(tap), counters(Objects.Self)),
        activated(cost(tap, CostPart.RemoveCounters(p1p1, n(1), CostObjects.Self)), counters(Objects.Each(and(another, creature, you)))),
    )

    // CR 614.1a, 111.1: token creation replaced
    @Test
    fun parallelLivesLike() = card(
        name = "Doubled Lives",
        cost = "{3}{G}",
        type = "Enchantment",
        text = "If an effect would create one or more tokens under your control, it creates twice that many of those tokens instead.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.MultiplyAmount(2)))),
            affects = Affected.Objects(Objects.Self)),
    )
}
