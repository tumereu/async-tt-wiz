package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 13: counterspells, damage (to each non-Vampire, excess damage, toughness as damage), destroy modes and
 * discard-and-draw effects, several of them with Blood tokens (CR 111.10g).
 */
class VowChunk13CounterDamageDestroyDrawTest {
    private val vampire = sub("Vampire")
    private val human = sub("Human")
    private val zombie = sub("Zombie")
    private val creatureOrPlaneswalker = or(creature, ObjectFilter.Type(CardType.Planeswalker))

    // CR 701.6a (counter a spell), 111.10g
    @Test
    fun syphonEssence() = card(
        name = "Drain Echo",
        cost = "{2}{U}",
        type = "Instant",
        text = """Counter target creature or planeswalker spell. Create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Counter(target("spell"))),
            create(blood),
            targets = listOf(slot("spell", and(creatureOrPlaneswalker, ObjectFilter.Category(ObjectCategory.Spell)))),
        ),
    )

    // CR 700.2 (modes), 120.3 (damage), 701.8a (destroy)
    @Test
    fun abrade() = card(
        name = "Scour Strike",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Scour Strike deals 3 damage to target creature.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ))),
    )

    // CR 120.4a (excess damage is the damage beyond lethal), 111.10g
    @Test
    fun lacerateFlesh() = card(
        name = "Gash Wound",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """Gash Wound deals 4 damage to target creature. Create a number of Blood tokens equal to the amount of excess damage dealt to that creature this way. (They're artifacts with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toObjects(target("creature")), n(4), Objects.Self, bind = "damage")),
            create(blood, Value.Excess("damage")),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 120.3, 205.3m (Wolf and Werewolf subtypes), 602.2
    @Test
    fun runeboundWolf() = card(
        name = "Pack Howler",
        cost = "{1}{R}",
        type = "Creature — Wolf",
        pt = "2/2",
        text = "{3}{R}, {T}: This creature deals damage equal to the number of Wolves and Werewolves you control to target opponent.",
    ) parsesTo abilities(
        activated(cost(mana("{3}{R}"), tap),
            damage(count(and(or(sub("Wolf"), sub("Werewolf")), you)), Recipients(players = Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    // CR 120.3 (damage to each creature that is not a Vampire), 111.10g
    @Test
    fun vampiresVengeance() = card(
        name = "Night Reprisal",
        cost = "{2}{R}",
        type = "Instant",
        text = """Night Reprisal deals 2 damage to each non-Vampire creature. Create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(damage(2, toObjects(Objects.Each(and(not(vampire), creature)))), create(blood)),
    )

    // CR 510.1a and 510.1c (combat damage assignment by toughness)
    @Test
    fun ancientLumberknot() = card(
        name = "Elder Rootknot",
        cost = "{2}{B}{G}",
        type = "Creature — Treefolk",
        pt = "1/4",
        text = "Each creature you control with toughness greater than its power assigns combat damage equal to its toughness rather than its power.",
    ) parsesTo abilities(
        static(Mechanic.AssignsDamageByToughness, affects = Objects.Each(and(creature, you,
            ObjectFilter.Toughness(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Tested, Combine.Single))))),
    )

    // CR 700.2, 701.8a, 702.9a
    @Test
    fun crushingCanopy() = card(
        name = "Verdant Crush",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with flying.
            • Destroy target enchantment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, kw(Keyword.Flying.kind))))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
        ))),
    )

    // CR 701.8a, 111.10g
    @Test
    fun grislyRitual() = card(
        name = "Grim Rite",
        cost = "{5}{B}",
        type = "Sorcery",
        text = """Destroy target creature or planeswalker. Create two Blood tokens. (They're artifacts with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), create(blood, n(2)), targets = listOf(slot("permanent", creatureOrPlaneswalker))),
    )

    // CR 701.8a, 119.1
    @Test
    fun sanctify() = card(
        name = "Hallow Purge",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Destroy target artifact or enchantment. You gain 3 life.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), gainLife(3), targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    // CR 700.2 ("choose up to one": zero modes allowed), 701.14a (fight)
    @Test
    fun sawbladeSlinger() = card(
        name = "Thicket Sawyer",
        cost = "{3}{G}",
        type = "Creature — Human Archer",
        pt = "4/3",
        text = """
            When this creature enters, choose up to one —
            • Destroy target artifact an opponent controls.
            • This creature fights target Zombie an opponent controls.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", and(artifact, opponents)))),
            mode(Step.Do(Effect.Fight(Objects.Self, target("Zombie"))), targets = listOf(slot("Zombie", and(zombie, opponents)))),
        ), min = 0, max = 1, chooser = Players.You))),
    )

    // CR 120.3 (a source dealing damage), 603.2, 701.8a
    @Test
    fun vampireSlayer() = card(
        name = "Slayer of Fangs",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = "Whenever this creature deals damage to a Vampire, destroy that creature.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(vampire))),
            doing(Effect.Destroy(Objects.EventObject(EventRole.Object))),
        ),
    )

    // CR 701.9a (discard), 121.1 (draw), 614.? none: counts cards discarded this turn, including those just discarded
    @Test
    fun changeOfFortune() = card(
        name = "Fate Reversal",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Discard your hand, then draw a card for each card you've discarded this turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            Step.Do(Effect.Draw(Players.You, Value.EventsThisTurn(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), Measure.Objects))),
        ),
    )

    // CR 603.2 (a Human you control dies), 700.? counters on it as it last existed (CR 603.10a)
    @Test
    fun laidToRest() = card(
        name = "Funeral Garden",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            Whenever a Human you control dies, draw a card.
            Whenever a creature you control with a +1/+1 counter on it dies, you gain 2 life.
        """,
    ) parsesTo abilities(
        triggered(dies(and(human, you)), draw(1)),
        triggered(dies(and(creature, you, ObjectFilter.HasCounter(p1p1, 1))), gainLife(2)),
    )

    // CR 603.4 (intervening if: checked on trigger and on resolution), 117.12 (pay as it resolves), 119.9
    @Test
    fun markovPurifier() = card(
        name = "Mardov Purifier",
        cost = "{1}{W}{B}",
        type = "Creature — Vampire Cleric",
        pt = "2/3",
        text = """
            Lifelink
            At the beginning of your end step, if you gained life this turn, you may pay {2}. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(yourEndStep,
            Step.Pay(manaCost("{2}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1))),
            interveningIf = gainedLifeThisTurn),
    )

    // CR 603.4 (intervening if on enter)
    @Test
    fun resistanceSquad() = card(
        name = "Rally Squad",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = "When this creature enters, if you control another Human, draw a card.",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), interveningIf = Condition.Exists(and(another, human, you), 1)),
    )

    // CR 602.2 (cost: tap, discard a creature card), 701.9a
    @Test
    fun selhoffEntomber() = card(
        name = "Cemetery Entomber",
        cost = "{1}{U}",
        type = "Creature — Zombie",
        pt = "1/3",
        text = "{T}, Discard a creature card: Draw a card.",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.Discard(CostObjects.Choose(creature, count = n(1)))), draw(1)),
    )

    // CR 117.12 (optional exile as a cost of an "if you do"), 404.1 (graveyard)
    @Test
    fun skywarpSkaab() = card(
        name = "Skybound Skaab",
        cost = "{3}{U}{U}",
        type = "Creature — Zombie Drake",
        pt = "2/5",
        text = """
            Flying
            When this creature enters, you may exile two creature cards from your graveyard. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Choose(creature, count = n(2)), from = Zone.Graveyard))),
                payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    // CR 603.2 (becomes the target), 115.1
    @Test
    fun stormchaserDrake() = card(
        name = "Gale Drake",
        cost = "{1}{U}",
        type = "Creature — Drake",
        pt = "2/1",
        text = """
            Flying
            Whenever this creature becomes the target of a spell you control, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.BecomesTarget(objects = ObjectFilter.Is(Objects.Self), by = and(spellFilter, you))), draw(1)),
    )

    // CR 701.9a, 117.12 ("unless you discard": a choice to discard a basic land card instead of two)
    @Test
    fun thirstForDiscovery() = card(
        name = "Quest for Lore",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw three cards. Then discard two cards unless you discard a basic land card.",
    ) parsesTo abilities(
        spell(
            draw(3),
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(basicLand(), count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(discard(2))),
        ),
    )

    // CR 603.1 (enters), 702.? can't be blocked (CR 509.1b), 702.15a (lifelink), 611.2a
    @Test
    fun weddingInvitation() = card(
        name = "Wedding Token",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, draw a card.
            {T}, Sacrifice this artifact: Target creature can't be blocked this turn. If it's a Vampire, it also gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        activated(cost(tap, sacrificeSelf),
            untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
            ifThen(Condition.ObjectMatches(target("creature"), vampire), gains(target("creature"), Keyword.Lifelink)),
            targets = listOf(slot("creature", creature))),
    )
}
