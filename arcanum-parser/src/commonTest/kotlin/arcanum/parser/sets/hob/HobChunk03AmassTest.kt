package arcanum.parser.sets.hob

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 03: amass Goblins (CR 701.47a): the keyword action of the player who amasses (an opponent's when "its
 * controller amasses", done in a loop over that player because the action takes no players), with X read as the creature last
 * existed (CR 608.2h), "the amassed Army" (701.47c), counters put by your spells and abilities as a batch (CR 603.2c), a
 * reflexive trigger after an optional sacrifice with excess damage (CR 603.12, 120.4a) and a spell cast from a graveyard.
 */
class HobChunk03AmassTest {
    // CR 603.10a: leaving the graveyard looks back in time
    @Test
    fun downTheTwistedPath() = card(
        name = "Down the Twisted Path",
        cost = "{2}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, return target creature card from your graveyard to your hand.
            Whenever a creature card leaves your graveyard, amass Goblins 1.
            {1}{B}: Goblins and Orcs you control gain menace until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        triggered(on(EventPattern.ZoneChange(and(creature, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard)), amassGoblins(1)),
        activated(cost(mana("{1}{B}")), gains(Objects.Each(and(or(sub("Goblin"), sub("Orc")), you)), Keyword.Menace)),
    )

    /** The destroyed creature's controller amasses, X its power as it last existed (CR 608.2h, 701.47a). */
    // CR 701.47a
    @Test
    fun grukkDeepdelvesRuin() = card(
        name = "Grukk, Deepdelve's Ruin",
        cost = "{2}{B}",
        type = "Legendary Creature — Goblin Soldier",
        text = "When Grukk enters, destroy up to one other target creature. Its controller amasses Goblins X, where X is that creature's power. If you controlled that creature, draw a card. (To amass Goblins X, that player puts X +1/+1 counters on an Army they control. It's also a Goblin. If they don't control an Army, they create a 0/0 black Goblin Army creature token first.)",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(entersSelf,
            destroy(target("creature")),
            Step.ForEach(Each.Players(Players.ControllerOf(target("creature"))), "player", listOf(amassGoblins(power(target("creature")))), together = true),
            Step.If(Condition.ObjectMatches(target("creature"), you), then = listOf(draw(1))),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    // CR 701.47a
    @Test
    fun musteringOfShadows() = card(
        name = "Mustering of Shadows",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Return up to one target creature card from your graveyard to your hand.
            Amass Goblins 3. (Put three +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), amassGoblins(3), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0))),
    )

    @Test
    fun stormIntoTheDale() = card(
        name = "Storm into the Dale",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            You draw a card and lose 1 life.
            Amass Goblins 2. (Put two +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(draw(1), loseLife(1), amassGoblins(2)),
    )

    // CR 118.12, 608.2h: the sacrificed creature's power as it last existed; the dying creature's power likewise (CR 603.10a)
    @Test
    fun wilderfellRampager() = card(
        name = "Wilderfell Rampager",
        cost = "{2}{B}",
        type = "Creature — Wolf",
        text = """
            Whenever this creature attacks, you may sacrifice another creature. If you do, put a number of +1/+1 counters on this creature equal to the sacrificed creature's power.
            When this creature dies, amass Goblins X, where X is this creature's power. (Put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(sacrificeA(and(another, creature), preBind = "sacrificed")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self, power(Objects.Bound("sacrificed")))))),
        triggered(diesSelf, amassGoblins(power(Objects.EventObject(EventRole.Before)))),
    )

    @Test
    fun peskyDrumbanger() = card(
        name = "Pesky Drumbanger",
        cost = "{1}{R}",
        type = "Creature — Goblin Bard",
        text = "Whenever you cast a noncreature spell, amass Goblins 1. (Put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), amassGoblins(1)),
    )

    @Test
    fun caveTownLackeys() = card(
        name = "Cave-town Lackeys",
        cost = "{1}{R}",
        type = "Creature — Goblin Soldier",
        text = """
            Haste
            When this creature enters, amass Goblins 1. (Put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, amassGoblins(1)),
    )

    // CR 508.3d
    @Test
    fun foggyPeaksRaider() = card(
        name = "Foggy Peaks Raider",
        cost = "{4}{R}",
        type = "Creature — Goblin Soldier",
        text = "Whenever you attack, amass Goblins 2. (Put two +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(youAttack, amassGoblins(2)),
    )

    // CR 702.34a: cast from a graveyard with flashback
    @Test
    fun rumorsOfBattle() = card(
        name = "Rumors of Battle",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Amass Goblins 1. If this spell was cast from a graveyard, amass Goblins 3 instead. (To amass Goblins X, put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
            Flashback {3}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(Step.If(Condition.CastFrom(Zone.Graveyard, Objects.Self), then = listOf(amassGoblins(3)), otherwise = listOf(amassGoblins(1)))),
        Keyword.Flashback(manaCost("{3}{R}")),
    )

    /** "When you do" after the optional sacrifice; the excess damage is what's over lethal (CR 603.12, 120.4a). */
    // CR 603.12, 120.4a
    @Test
    fun skarnOfTheNorth() = card(
        name = "Skarn of the North",
        cost = "{3}{B}{R}",
        type = "Legendary Creature — Goblin Soldier",
        text = "When Skarn enters, you may sacrifice another creature. When you do, Skarn deals damage equal to that creature's power to another target creature. If excess damage was dealt this way, amass Goblins X, where X is that excess damage. (Put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)",
        pt = "5/5",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(and(another, creature), preBind = "sacrificed")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("creature", and(another, creature))),
                steps = listOf(
                    Step.Do(Effect.DealDamage(toObjects(target("creature")), power(Objects.Bound("sacrificed")), Objects.Self, bind = "damage")),
                    Step.If(Condition.Compare(Value.Excess("damage"), Comparison.Greater, n(0)), then = listOf(amassGoblins(Value.Excess("damage"))))))))))),
    )

    @Test
    fun dreadfulGoblinDuo() = card(
        name = "Dreadful Goblin Duo",
        cost = "{2}{B/R}",
        type = "Creature — Goblin Soldier",
        text = "When this creature dies, amass Goblins 4. (Put four +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(diesSelf, amassGoblins(4)),
    )

    // CR 701.47c: the amassed Army
    @Test
    fun goblinScaleHauberk() = card(
        name = "Goblin Scale Hauberk",
        cost = "{1}{B/R}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, amass Goblins 1, then attach this Equipment to the amassed Army. (To amass Goblins 1, put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
            Equipped creature gets +1/+0 and has menace.
            Equip {4}
        """,
    ) parsesTo abilities(
        triggered(entersSelf, amassGoblins(n(1), bind = "amassed"), Step.Do(Effect.Attach(Objects.Self, toObjects(Objects.Bound("amassed"))))),
        static(pt(1, 0), has(Keyword.Menace), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{4}")),
    )

    // CR 603.2c: once for the batch of counters put by your spells and abilities
    @Test
    fun theVastGoblin() = card(
        name = "The Vast Goblin",
        cost = "{1}{B/R}{B/R}",
        type = "Legendary Creature — Goblin Noble",
        text = """
            Whenever you put one or more counters on a Goblin, Orc, or Army you control, The Vast Goblin deals 2 damage to target opponent.
            Whenever another Goblin, Orc, or Army you control dies, exile the top card of your library. You may play it until the end of your next turn.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.CountersPut(and(or(sub("Goblin"), sub("Orc"), sub("Army")), you), causedBy = you), oneOrMore = true),
            damage(2, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
        triggered(on(EventPattern.ZoneChange(and(another, or(sub("Goblin"), sub("Orc"), sub("Army")), you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), arcanum.api.Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), arcanum.api.Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn))),
    )

    @Test
    fun skarnsWarband() = card(
        name = "Skarn's Warband",
        cost = "{B}{R}",
        type = "Creature — Goblin Soldier",
        text = """
            This creature has haste as long as you control another Goblin.
            {T}, Sacrifice another Goblin: Add {B}{R}.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(has(Keyword.Haste), condition = Condition.Exists(and(another, sub("Goblin"), you), atLeast = 1)),
        activated(cost(tap, sacrificeA(and(another, sub("Goblin")))), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}{R}")!!)), Players.You))),
    )
}
