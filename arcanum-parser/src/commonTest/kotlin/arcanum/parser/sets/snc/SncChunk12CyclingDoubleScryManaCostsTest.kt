package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Streets of New Capenna chunk 12: the cycling triomes, doubling power, scry, mana abilities, "can't be countered" and the
 * "costs less if it targets" spells. */
class SncChunk12CyclingDoubleScryManaCostsTest {
    private val entersTapped = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)))
    private val creatureOrPlaneswalker = ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker)))

    private fun tapForThree(a: Char, b: Char, c: Char) = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors = listOf(a, b, c).map { ColorRef.Literal(Color.of(it)!!) })), Players.You)))

    private fun cyclingTriome(name: String, types: String, a: Char, b: Char, c: Char) = card(
        name = name,
        cost = null,
        type = "Land — $types",
        text = """
            ({T}: Add {$a}, {$b}, or {$c}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    // CR 614.1c (enters tapped), 702.29a (cycling), 305.6 (basic land types give the mana abilities)
    @Test fun jetmirsGarden() = cyclingTriome("Jetsam Garden", "Mountain Forest Plains", 'R', 'G', 'W')
    @Test fun raffinesTower() = cyclingTriome("Rafter Tower", "Plains Island Swamp", 'W', 'U', 'B')
    @Test fun sparasHeadquarters() = cyclingTriome("Sparrow Headquarters", "Forest Plains Island", 'G', 'W', 'U')
    @Test fun xandersLounge() = cyclingTriome("Xylem Lounge", "Island Swamp Mountain", 'U', 'B', 'R')
    @Test fun ziatorasProvingGround() = cyclingTriome("Zither Proving Ground", "Swamp Mountain Forest", 'B', 'R', 'G')

    // CR 701.10a (double a power), 508.3d (whenever you attack)
    @Test
    fun mrOrfeoTheBoulder() = card(
        name = "Mr. Orwick, the Pebble",
        cost = "{1}{B}{R}{G}",
        type = "Legendary Creature — Rhino Warrior",
        pt = "2/4",
        text = "Whenever you attack, double target creature's power until end of turn.",
    ) parsesTo abilities(
        triggered(youAttack,
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), n(0))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 122.1c (shield counter), 701.22a (scry)
    @Test
    fun boonOfSafety() = card(
        name = "Boon of Cover",
        cost = "{W}",
        type = "Instant",
        text = """
            Put a shield counter on target creature. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            Scry 1.
        """,
    ) parsesTo abilities(
        spell(counters(target("creature"), 1, CounterKind.Shield), scry(1), targets = listOf(slot("creature", creature))),
    )

    // CR 702.7a (first strike), 701.22a
    @Test
    fun daringEscape() = card(
        name = "Daring Retreat",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +1/+0 and gains first strike until end of turn. Scry 1.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(1, 0), has(Keyword.FirstStrike)), scry(1), targets = listOf(slot("creature", creature))),
    )

    // CR 701.22a, 121.1
    @Test
    fun demonsDue() = card(
        name = "Demon's Debt",
        cost = "{3}{B}",
        type = "Instant",
        text = "Scry 2, then draw two cards. You lose 2 life.",
    ) parsesTo abilities(
        spell(scry(2), draw(2), loseLife(2)),
    )

    // CR 603.3, 120.3 (damage to each opponent), 701.22a, 112.3b/602 (exile from hand), 611.2a, 400.7
    @Test
    fun glamorousOutlaw() = card(
        name = "Glamorous Bandit",
        cost = "{3}{U}{B}{R}",
        type = "Creature — Vampire Rogue",
        pt = "4/5",
        text = """
            When this creature enters, it deals 2 damage to each opponent and you scry 2.
            {2}, Exile this card from your hand: Target land gains "{T}: Add {U}, {B}, or {R}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(2, eachOpponent), scry(2)),
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Hand, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(tapForThree('U', 'B', 'R')))),
                Affected.Objects(target("land")), Duration.UntilCast(Objects.Bound("exiled")))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely)),
            targets = listOf(slot("land", land))),
    )

    // CR 106.6 (spending restriction), 603.10a (dies), 701.10
    @Test
    fun cormelaGlamourThief() = card(
        name = "Corvela, Glimmer Thief",
        cost = "{1}{U}{B}{R}",
        type = "Legendary Creature — Vampire Rogue",
        pt = "2/4",
        text = """
            Haste
            {1}, {T}: Add {U}{B}{R}. Spend this mana only to cast instant and/or sorcery spells.
            When Corvela dies, return up to one target instant or sorcery card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}{B}{R}")!!),
            restriction = and(spellFilter, or(instant, sorcery))), Players.You))),
        triggered(diesSelf, move(target("cards"), Zone.Hand),
            targets = listOf(slot("cards", inYourGraveyard(or(instant, sorcery)), min = 0))),
    )

    // CR 122.1, 602.2, 106.1b
    @Test
    fun glitteringStockpile() = card(
        name = "Glittering Hoard",
        cost = "{2}{R}",
        type = "Artifact — Treasure",
        text = """
            {T}: Add {R}. Put a stash counter on this artifact.
            {T}, Sacrifice this artifact: Add X mana of any one color, where X is the number of stash counters on this artifact.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You)),
            counters(Objects.Self, 1, CounterKind.Named("stash"))),
        activated(cost(tap, sacrificeSelf), Step.Do(Effect.AddMana(
            ManaProduction(ManaKind.Choose(amount = Value.Of(NumericProperty.Counters(CounterKind.Named("stash")), Objects.Self, Combine.Single))),
            Players.You))),
    )

    // CR 101.5 (can't be countered)
    @Test
    fun voidRend() = card(
        name = "Void Rift",
        cost = "{W}{U}{B}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Destroy target nonland permanent.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    private fun lessIfTargets(n: Int, slotName: String, filter: ObjectFilter) =
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{$n}")!!, n(1))),
            condition = Condition.ObjectMatches(target(slotName), filter))

    private fun colored(color: Color) = ObjectFilter.OfColor(color)

    // CR 601.2f, 700.8 (the greatest power), 614.1a/614.6 (exile instead of dying)
    @Test
    fun bouncersBeatdown() = card(
        name = "Bouncer's Battering",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets a black permanent.
            Bouncer's Battering deals X damage to target creature or planeswalker, where X is the greatest power among creatures you control. If that creature or planeswalker would die this turn, exile it instead.
        """,
    ) parsesTo abilities(
        lessIfTargets(2, "permanent", colored(Color.Black)),
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("permanent")),
                Value.Of(NumericProperty.Power, Objects.Each(and(creature, you)), Combine.Greatest), source = Objects.Self)),
            exileIfItWouldDie("permanent"),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ),
    )

    private fun exileIfItWouldDie(slot: String) = Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Target(slot)), from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        affects = null, duration = Duration.EndOfTurn))

    // CR 601.2f
    @Test
    fun knockoutBlow() = card(
        name = "Knockdown Blow",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets a red creature.
            Knockdown Blow deals 4 damage to target attacking or blocking creature and you gain 2 life.
        """,
    ) parsesTo abilities(
        lessIfTargets(2, "creature", colored(Color.Red)),
        spell(damage(4, toObjects(target("creature"))), gainLife(2),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature)))),
    )

    // CR 601.2f
    @Test
    fun outOfTheWay() = card(
        name = "Out of the Alley",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets a green permanent.
            Return target nonland permanent an opponent controls to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        lessIfTargets(2, "permanent", colored(Color.Green)),
        spell(move(target("permanent"), Zone.Hand), draw(1),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
    )

    // CR 601.2f, 101.5, 107.3
    @Test
    fun torchBreath() = card(
        name = "Torch Gust",
        cost = "{X}{R}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets a blue permanent.
            This spell can't be countered.
            Torch Gust deals X damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        lessIfTargets(2, "permanent", colored(Color.Blue)),
        static(Mechanic.CantBeCountered),
        spell(damage(Value.Announced("X"), toObjects(target("permanent"))), targets = listOf(slot("permanent", creatureOrPlaneswalker)), announced = listOf("X")),
    )

    // CR 601.2f, 613.4c
    @Test
    fun whack() = card(
        name = "Whap",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            This spell costs {3} less to cast if it targets a white creature.
            Target creature gets -4/-4 until end of turn.
        """,
    ) parsesTo abilities(
        lessIfTargets(3, "creature", colored(Color.White)),
        spell(pump(target("creature"), -4, -4), targets = listOf(slot("creature", creature))),
    )
}
