package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordActionKind
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 01: proliferate (CR 701.34a) after a condition on an earlier target as it last existed (CR 608.2h), a number
 * of times, in modes and as a cost's reward; a replacement of proliferating (CR 614.1a); planeswalkers granted two loyalty abilities (CR 606);
 * one more counter of a chosen kind (CR 122.1); a reanimation from any graveyard (CR 110.2a).
 */
class OneChunk01ProliferateTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val power = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)

    // CR 608.2h: the destroyed permanent's mana value as it last existed on the battlefield
    @Test
    fun devouringBower() = card(
        name = "Devouring Bower", cost = "{2}{G}", type = "Sorcery",
        text = "Destroy target artifact, enchantment, or creature with flying. If that permanent's mana value was 3 or less, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            destroy(target("permanent")),
            Step.If(Condition.ObjectMatches(target("permanent"), manaValue(Comparison.AtMost, 3)), then = listOf(proliferate)),
            targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying.kind))))),
        ),
    )

    // CR 701.34a: proliferate as many times as fewer lands were put onto the battlefield (the lands actually put there, CR 400.7)
    @Test
    fun widenTheOrb() = card(
        name = "Widen the Orb", cost = "{3}{G}", type = "Sorcery",
        text = "Look at the top six cards of your library. Put up to two land cards from among them onto the battlefield tapped and the rest on the bottom of your library in a random order. If you put fewer than two lands onto the battlefield this way, proliferate a number of times equal to the difference. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), bind = "seen", players = Players.You)),
            Step.Choose(Choice.Objects(land, n(0), n(2), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true), postBind = "put")),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.Repeat(Value.Difference(n(2), Value.Count(Objects.Bound("put"))), listOf(proliferate)),
        ),
    )

    // CR 701.34a, 118.1: "you may pay {3}. If you do, proliferate twice." and a trigger on proliferating
    @Test
    fun vexilSeekerOfOrbs() = card(
        name = "Vexil, Seeker of Orbs", cost = "{2}{G}{U}", type = "Legendary Creature — Phyrexian Elf Warrior", pt = "3/3",
        text = """
            When Vexil enters, you may pay {3}. If you do, proliferate twice.
            Whenever you proliferate, draw a card.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Pay(cost = manaCost("{3}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Repeat(n(2), listOf(proliferate)))),
        ),
        triggered(on(EventPattern.KeywordActionDone(KeywordActionKind.Proliferate, PlayerFilter.You)), draw(1)),
    )

    // CR 606.3, 613.1f: planeswalkers you control gain two loyalty abilities; CR 122.1: one more counter of the kind chosen
    @Test
    fun oilmoonBracer() = card(
        name = "Oilmoon Bracer", cost = "{2}{U}", type = "Artifact",
        text = """
            Planeswalkers you control have "[0]: Proliferate" and "[−12]: Take an extra turn after this one."
            Whenever you cast a noncreature spell, choose a counter on target permanent. Put an additional counter of that kind on that permanent.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.GainAbilities(listOf(
                loyalty(0, proliferate),
                loyalty(-12, doing(Effect.ExtraTurn(Players.You))),
            )),
            affects = Objects.Each(and(planeswalker, you)),
        ),
        triggered(
            youCast(noncreatureSpell),
            doing(Effect.OneMoreCounterOfChosenKind(Recipients(objects = target("permanent")))),
            targets = listOf(slot("permanent", permanentFilter)),
        ),
    )

    // CR 608.2h: the countered spell's mana value as it last existed on the Stack
    @Test
    fun refuseTheFlawed() = card(
        name = "Refuse the Flawed", cost = "{1}{U}{U}", type = "Instant",
        text = "Counter target spell. If that spell's mana value was 3 or less, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Counter(target("spell"))),
            Step.If(Condition.ObjectMatches(target("spell"), manaValue(Comparison.AtMost, 3)), then = listOf(proliferate)),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    // CR 608.2h: "had mana value": the returned permanent as it last existed on the battlefield
    @Test
    fun ichorNoose() = card(
        name = "Ichor Noose", cost = "{1}{U}", type = "Instant",
        text = "Return target nonland permanent to its owner's hand. If that permanent had mana value 3 or less, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Hand),
            Step.If(Condition.ObjectMatches(target("permanent"), manaValue(Comparison.AtMost, 3)), then = listOf(proliferate)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 614.1a, 614.5: proliferating twice instead (two of these: four times); CR 118.3: counters removed from among other permanents
    @Test
    fun quorvathQuerySovereign() = card(
        name = "Quorvath, Query Sovereign", cost = "{2}{U}{U}", type = "Legendary Creature — Phyrexian Horror", pt = "3/5",
        text = """
            Flying
            If you would proliferate, proliferate twice instead.
            {1}{U/P}{U/P}, Remove three counters from among other artifacts, creatures, and planeswalkers you control: Put an indestructible counter on Quorvath. ({U/P} can be paid with either {U} or 2 life.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.KeywordActionDone(KeywordActionKind.Proliferate, PlayerFilter.You), modify = listOf(Modification.MultiplyAmount(2)))),
        activated(
            cost(mana("{1}{U/P}{U/P}"), CostPart.RemoveCounters(null, n(3),
                CostObjects.Choose(and(another, or(artifact, creature, planeswalker), you), count = null), distributed = true)),
            counters(Objects.Self, 1, CounterKind.Keyword(Keyword.Indestructible.kind)),
        ),
    )

    // CR 110.2a: a creature card from any graveyard, under your control
    @Test
    fun tankRebirth() = card(
        name = "Tank Rebirth", cost = "{4}{B}", type = "Sorcery",
        text = "Put target creature card from a graveyard onto the battlefield under your control. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield, controller = Players.You),
            proliferate,
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard)))),
        ),
    )

    // CR 700.2: an enters trigger with a pump mode and a proliferate mode
    @Test
    fun pliantSporecaller() = card(
        name = "Pliant Sporecaller", cost = "{2}{G}", type = "Creature — Phyrexian Druid", pt = "2/2",
        text = """
            Vigilance
            When this creature enters, choose one —
            • Target creature gets +2/+2 and gains vigilance until end of turn.
            • Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(pump(target("creature"), 2, 2, has(Keyword.Vigilance)), targets = listOf(slot("creature", creature))),
            mode(proliferate),
        ))),
    )

    // CR 702.164a: toxic; a dies trigger that proliferates
    @Test
    fun plaguegutVermin() = card(
        name = "Plaguegut Vermin", cost = "{1}{B}", type = "Creature — Phyrexian Rat", pt = "2/2",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature dies, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        triggered(diesSelf, proliferate),
    )

    // CR 702.19, 702.164a: trample and toxic; combat damage to a player proliferates
    @Test
    fun swollenBefouler() = card(
        name = "Swollen Befouler", cost = "{2}{G}", type = "Creature — Phyrexian Beast", pt = "4/4",
        text = """
            Trample
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            Whenever this creature deals combat damage to a player, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Toxic(1),
        triggered(combatDamageToAPlayer, proliferate),
    )

    // CR 701.21a: "you may sacrifice it. If you do, proliferate."; CR 608.2h: the dies trigger uses its power as it last existed
    @Test
    fun clamorImp() = card(
        name = "Clamor Imp", cost = "{R}", type = "Creature — Phyrexian Goblin Warrior", pt = "1/1",
        text = """
            Whenever this creature deals combat damage to a player, you may sacrifice it. If you do, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
            When this creature dies, it deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        triggered(
            combatDamageToAPlayer,
            Step.Pay(steps = listOf(doing(Effect.Sacrifice(Objects.Self))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(proliferate)),
        ),
        triggered(diesSelf, damage(power, toAny), targets = listOf(anyTarget)),
    )

    // CR 700.2: a sacrifice ability with three modes
    @Test
    fun rotblossomSprout() = card(
        name = "Rotblossom Sprout", cost = "{1}{G}", type = "Creature — Phyrexian Fungus", pt = "3/2",
        text = """
            {1}, Sacrifice this creature: Choose one —
            • Destroy target artifact.
            • Destroy target enchantment.
            • Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Ability.Activated(
            Cost(listOf(mana("{1}"), sacrificeSelf)),
            Instructions(modes = modes(
                mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
                mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
                mode(proliferate),
            )),
            anyTime,
        ),
    )

    // CR 701.20a: reveal a land from the top four; proliferate if none was put into your hand
    @Test
    fun infectiousTusker() = card(
        name = "Infectious Tusker", cost = "{2}{G}", type = "Creature — Phyrexian Boar Beast", pt = "3/3",
        text = "When this creature enters, look at the top four cards of your library. You may reveal a land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.If(Condition.Not(Condition.NonEmpty("chosen")), then = listOf(proliferate)),
        ),
    )

    // CR 701.34a: a sacrifice ability that proliferates
    @Test
    fun bronzeStiltwalker() = card(
        name = "Bronze Stiltwalker", cost = "{1}{G}", type = "Creature — Phyrexian Spider", pt = "1/3",
        text = """
            Reach
            {1}{G}, Sacrifice this creature: Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(mana("{1}{G}"), sacrificeSelf), proliferate),
    )

    // CR 613.4c: -4/-4 until end of turn, then proliferate
    @Test
    fun sinkInOil() = card(
        name = "Sink in Oil", cost = "{1}{B}", type = "Sorcery",
        text = "Target creature gets -4/-4 until end of turn. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), -4, -4), proliferate, targets = listOf(slot("creature", creature))),
    )

}
