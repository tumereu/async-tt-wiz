package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 12: counterspells, token creation, damage and destroy spells and abilities.
 */
class IsdChunk12CounterTokensDamageDestroyTest {
    private val targetSpell = Objects.Target("spell")
    private val spirit = creatureToken("Spirit", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying))
    private fun playerOrPlaneswalker(name: String) = TargetSlot(name,
        TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Any), min = n(1), max = n(1))

    // CR 701.6a, 614.6: the countered spell goes to exile instead of the graveyard
    @Test
    fun spectralDissolve() = card(
        name = "Spectral Dissolve", cost = "{1}{U}{U}", type = "Instant",
        text = "Counter target spell. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("spell", spellFilter)),
            steps = listOf(doing(Effect.Counter(targetSpell))),
            replacements = listOf(Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(targetSpell), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        )),
    )

    // CR 701.6a, 115.1: two targets, a spell and a permanent
    @Test
    fun dismissingFog() = card(
        name = "Dismissing Fog", cost = "{3}{U}{U}", type = "Instant",
        text = "Counter target spell. Return target permanent to its owner's hand.",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(targetSpell)), move(target("permanent"), Zone.Hand),
            targets = listOf(slot("spell", spellFilter), slot("permanent", permanentFilter))),
    )

    // CR 700.4: dies = put into a graveyard from the battlefield
    @Test
    fun veiledTraveler() = card(
        name = "Veiled Traveler", cost = "{W}", type = "Creature — Human Soldier", pt = "1/1",
        text = "When this creature dies, create a 1/1 white Spirit creature token with flying.",
    ) parsesTo abilities(triggered(diesSelf, create(spirit)))

    // CR 111.2, 701.21a: the token exists before the sacrifice, so it may be the one sacrificed
    @Test
    fun patchworkPupil() = card(
        name = "Patchwork Pupil", cost = "{1}{U}", type = "Creature — Homunculus", pt = "1/2",
        text = "{1}{U}, {T}: Create a 2/2 blue Homunculus creature token, then sacrifice a creature.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{U}"), tap),
            create(creatureToken("Homunculus", listOf(Color.Blue), 2, 2)),
            Step.Choose(Choice.Objects(and(creature, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))),
    )

    // CR 120.3, 700.4: another creature dying, whoever controls it
    @Test
    fun furyHurler() = card(
        name = "Fury Hurler", cost = "{5}{R}", type = "Creature — Human Shaman", pt = "4/2",
        text = "Whenever another creature dies, this creature deals 2 damage to target player or planeswalker.",
    ) parsesTo abilities(
        triggered(dies(and(another, creature)), damage(2, Recipients(target = "player or planeswalker")),
            targets = listOf(playerOrPlaneswalker("player or planeswalker"))),
    )

    // CR 118.12, 601.2h: the sacrifice is a cost; the damage's source is the creature
    @Test
    fun pyreCultist() = card(
        name = "Pyre Cultist", cost = "{2}{R}{R}", type = "Creature — Human Shaman", pt = "2/2",
        text = "{R}, {T}, Sacrifice a creature: This creature deals 2 damage to any target.",
    ) parsesTo abilities(
        activated(cost(mana("{R}"), tap, sacrificeA(creature)), damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 701.8a: "noncreature" excludes creature permanents, lands included
    @Test
    fun thornbreaker() = card(
        name = "Thornbreaker", cost = "{2}{G}{G}", type = "Sorcery",
        text = "Destroy target noncreature permanent.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", and(not(creature), permanentFilter)))),
    )

    // CR 701.8a, 120.3: the spell is the source of the damage
    @Test
    fun mawOfFire() = card(
        name = "Maw of Fire", cost = "{4}{R}{R}", type = "Sorcery",
        text = "Destroy target land. Maw of Fire deals 13 damage to target creature.",
    ) parsesTo abilities(
        spell(destroy(target("land")), damage(13, toObjects(target("creature"))),
            targets = listOf(slot("land", land), slot("creature", creature))),
    )

    // CR 701.8a, 119.1
    @Test
    fun mawOfTheMarsh() = card(
        name = "Maw of the Marsh", cost = "{4}{B}", type = "Sorcery",
        text = "Destroy target land. You gain 4 life.",
    ) parsesTo abilities(spell(destroy(target("land")), gainLife(4), targets = listOf(slot("land", land))))

    // CR 701.8a: only what was actually destroyed counts (a regenerated or indestructible one doesn't)
    @Test
    fun purifyingDawn() = card(
        name = "Purifying Dawn", cost = "{2}{W}", type = "Sorcery",
        text = "Destroy all enchantments. You gain 1 life for each enchantment destroyed this way.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Destroy(Objects.Each(enchantment), postBind = "destroyed")),
            Step.Do(Effect.GainLife(Players.You, Value.Times(Value.Count(Objects.Bound("destroyed")), 1)))),
    )

    // CR 506.4: an attacking creature
    @Test
    fun reprimand() = card(
        name = "Reprimand", cost = "{2}{W}", type = "Instant",
        text = "Destroy target attacking creature.",
    ) parsesTo abilities(spell(destroy(target("creature")), targets = listOf(slot("creature", and(attacking, creature)))))

    // CR 701.8a: either of two kinds of permanent
    @Test
    fun spectralCleansing() = card(
        name = "Spectral Cleansing", cost = "{1}{W}", type = "Instant",
        text = "Destroy target Spirit or enchantment.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(sub("Spirit"), enchantment)))),
    )

    // CR 205.3m, 608.2b: all three subtypes are excluded
    @Test
    fun nightsVictim() = card(
        name = "Night's Victim", cost = "{B}{B}", type = "Instant",
        text = "Destroy target non-Vampire, non-Werewolf, non-Zombie creature.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")),
            targets = listOf(slot("permanent", and(not(sub("Vampire")), not(sub("Werewolf")), not(sub("Zombie")), creature)))),
    )

    // CR 613.1b (control for as long as), 205.3: "in addition to its other types" adds a subtype (audit: false alarm)
    @Test
    fun crimsonLord() = card(
        name = "Crimson Lord", cost = "{2}{B}{R}", type = "Legendary Creature — Vampire", pt = "3/3",
        text = """
            Flying
            {1}{R}: Crimson Lord deals 1 damage to another target creature. That creature becomes a Vampire in addition to its other types. Put a +1/+1 counter on Crimson Lord.
            {3}{B}{B}: Gain control of target Vampire for as long as you control Crimson Lord.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{1}{R}"),
            damage(1, toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire"))))),
                Affected.Objects(target("creature")), Duration.Indefinitely)),
            counters(Objects.Self),
            targets = listOf(slot("creature", and(another, creature)))),
        activated(manaCost("{3}{B}{B}"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("Vampire")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(slot("Vampire", sub("Vampire")))),
    )
}
