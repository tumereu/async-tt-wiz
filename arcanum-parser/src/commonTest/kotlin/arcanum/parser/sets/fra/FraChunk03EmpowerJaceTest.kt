package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 03: empower Jace (CR 701.71a: if you control no Jace planeswalker token, create a blue one with 0
 * loyalty and two loyalty abilities, then put N loyalty counters on a Jace token you control), as a spell's step, an enters
 * trigger, an attack trigger, an activated ability and a land's ability; with X counted or bound by an earlier step ("the
 * number of creatures exiled this way", "that excess damage", CR 120.4a). Also beholding a Jace as an additional cost or as
 * a land enters (CR 701.4a), loyalty abilities usable at instant speed, the next spell that can't be countered (CR 611.2f),
 * "whenever you put one or more loyalty counters on a planeswalker", planeswalkers that survive 0 loyalty (CR 704.5i), the
 * first noncreature spell each turn, and the top or bottom of a library chosen by a permanent's owner.
 */
class FraChunk03EmpowerJaceTest {
    // CR 701.71a
    @Test
    fun scholasticRise() = card(
        name = "Scholastic Rise",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Target creature gets +2/+2 and gains flying until end of turn.
            Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(spell(pump(target("creature"), 2, 2, Mechanic.GainAbilities(listOf(Keyword.Flying))), empower(2), targets = listOf(slot("creature", creature))))

    @Test
    fun quadHerald() = card(
        name = "Quad Herald",
        cost = "{1}{W}",
        type = "Creature — Human Advisor",
        pt = "3/1",
        text = "{1}, Exile this card from your graveyard: Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")",
    ) parsesTo abilities(activated(cost(mana("{1}"), exileSelfFromGraveyard), empower(2)))

    @Test
    fun hexwardRegiment() = card(
        name = "Hexward Regiment",
        cost = "{4}{W}{W}",
        type = "Sorcery",
        text = """
            Create three 2/2 colorless Wizard Soldier creature tokens named Cadet. Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
            spell(createCadets(3), empower(2)),
            Keyword.Typecycling(and(ObjectFilter.Super(Supertype.Basic), land), "Basic landcycling", cost(mana("{2}"))),
        )

    // CR 701.71a, 107.3c: X is counted as it resolves
    @Test
    fun retooledWarden() = card(
        name = "Retooled Warden",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = "Whenever this creature attacks, empower Jace X, where X is the number of creatures you control. (Put that many loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")",
    ) parsesTo abilities(triggered(attacksSelf, empower(Value.Count(Objects.Each(creatureYouControl)))))

    // CR 601.2f, 701.4a: beholding is one of the two additional costs
    @Test
    fun countershape() = card(
        name = "Countershape",
        cost = "{U}{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, behold a Jace or pay {1}. (To behold a Jace, choose a Jace you control or reveal a Jace card from your hand.)
            Counter target spell. Empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(
            static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(
                cost(CostPart.Action(KeywordAction.Behold(sub("Jace")), bind = "beheld")),
                cost(mana("{1}")),
            ))))),
            spell(Step.Do(Effect.Counter(target("spell"))), empower(1), targets = listOf(slot("spell", spellFilter))),
        )

    // CR 606.3: once each turn still
    @Test
    fun sculptorSStratagem() = card(
        name = "Sculptor's Stratagem",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Until end of turn, you may activate loyalty abilities of Jace planeswalkers you control on any player's turn any time you could cast an instant.
            Empower Jace 8. (Put eight loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.LoyaltyAtInstantSpeed), Affected.Objects(Objects.Each(and(sub("Jace"), planeswalker, you))), Duration.EndOfTurn)),
            empower(8),
        ))

    @Test
    fun thoughtseekerOrb() = card(
        name = "Thoughtseeker Orb",
        cost = "{2}{U}",
        type = "Creature — Homunculus",
        pt = "2/1",
        text = "When this creature enters, empower Jace 4. (Put four loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")",
    ) parsesTo abilities(triggered(entersSelf, empower(4)))

    // CR 401.4: the owner chooses top or bottom
    @Test
    fun contingencyPlanning() = card(
        name = "Contingency Planning",
        cost = "{3}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, the owner of up to one other target nonland permanent puts it on their choice of the top or bottom of their library.
            Whenever you cast your first noncreature spell each turn, empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(
            triggered(entersSelf,
                Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
                Step.If(Condition.Chose("position", "top"),
                    then = listOf(move(target("permanent"), Zone.Library, position = 0)),
                    otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
                targets = listOf(slot("permanent", and(anotherObject, nonland, permanentFilter), min = 0))),
            Ability.Triggered(Trigger.On(listOf(EventPattern.Cast(and(not(creature), spellFilter), PlayerFilter.You)), nthThisTurn = 1),
                Instructions(steps = listOf(empower(1)))),
        )

    @Test
    fun apprenticeSEpiphany() = card(
        name = "Apprentice's Epiphany",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Empower Jace 6. (Put six loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Draw a card.
        """,
    ) parsesTo abilities(spell(empower(6), draw(1)))

    // CR 611.2f: the next spell, used up as it is cast
    @Test
    fun scholarSStandIn() = card(
        name = "Scholar's Stand-In",
        cost = "{1}{U}",
        type = "Creature — Illusion",
        pt = "0/3",
        text = """
            Flash
            When this creature enters, empower Jace 3. (Put three loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            {U}, Sacrifice this creature: The next spell you cast this turn can't be countered.
        """,
    ) parsesTo abilities(
            Keyword.Flash,
            triggered(entersSelf, empower(3)),
            activated(cost(mana("{U}"), sacrificeSelf),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeCountered), Affected.NextSpells(controlledBy(PlayerFilter.You), 1), Duration.EndOfTurn))),
        )

    // CR 701.71a, 400.7: the creatures as they were exiled
    @Test
    fun rewriteTheCosmos() = card(
        name = "Rewrite the Cosmos",
        cost = "{4}{B}{B}",
        type = "Sorcery",
        text = "Exile all creatures. Empower Jace X, where X is the number of creatures exiled this way. (Put that many loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")",
    ) parsesTo abilities(spell(
            Step.Do(Effect.Move(Objects.Each(creature), Destination(Zone.Exile), postBind = "exiled")),
            empower(Value.Count(Objects.Bound("exiled"))),
        ))

    @Test
    fun amendOldSorrows() = card(
        name = "Amend Old Sorrows",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Return target creature or planeswalker card with mana value 6 or less from your graveyard to the battlefield.
            Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(spell(move(target("card"), Zone.Battlefield), empower(2),
            targets = listOf(slot("card", and(creatureOrPlaneswalker, manaValue(Comparison.AtMost, 6), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))))

    // CR 704.5i doesn't apply to them
    @Test
    fun shrineProwler() = card(
        name = "Shrine Prowler",
        cost = "{2}{B}",
        type = "Creature — Horror",
        pt = "3/2",
        text = """
            When this creature enters, empower Jace 1.
            Planeswalkers you control aren't put into their owners' graveyards for having 0 loyalty.
            Planeswalkers you control have "[+2]: This planeswalker deals 1 damage to each opponent and you gain 1 life."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(1)),
            static(Mechanic.NoGraveyardForZeroLoyalty, affects = Objects.Each(and(planeswalker, you))),
            static(Mechanic.GainAbilities(listOf(loyalty(2, damage(1, eachOpponent), gainLife(1)))), affects = Objects.Each(and(planeswalker, you))),
        )

    @Test
    fun resolveToDismay() = card(
        name = "Resolve to Dismay",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Target opponent reveals their hand. You choose a nonland permanent card from it. That player discards that card.
            Empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(spell(
            Step.Do(Effect.Reveal(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(and(nonland, or(artifact, ObjectFilter.Type(CardType.Battle), creature, ObjectFilter.Type(CardType.Enchantment), land, planeswalker)), n(1), n(1),
                from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            empower(1),
            targets = listOf(targetOpponent),
        ))

    @Test
    fun gorgonSLastClemency() = card(
        name = "Gorgon's Last Clemency",
        cost = "{B}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • You lose 2 life. Destroy target creature or planeswalker.
            • You lose 2 life. Empower Jace 6. (Put six loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("permanent", creatureOrPlaneswalker)), steps = listOf(loseLife(2), Step.Do(Effect.Destroy(target("permanent")))))),
            Mode(Instructions(steps = listOf(loseLife(2), empower(6)))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun admissionRefused() = card(
        name = "Admission Refused",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Admission Refused deals 3 damage to any target.
            Empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(spell(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(3), Objects.Self)), empower(1), targets = listOf(anyTarget)))

    // CR 120.4a: excess damage
    @Test
    fun brutalReverberation() = card(
        name = "Brutal Reverberation",
        cost = "{2}{R}{R}",
        type = "Instant",
        text = "Brutal Reverberation deals 6 damage to target creature or planeswalker. If excess damage was dealt to that permanent this way, empower Jace X, where X is that excess damage. (Put that many loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")",
    ) parsesTo abilities(spell(
            Step.Do(Effect.DealDamage(Recipients(objects = target("permanent")), n(6), Objects.Self, bind = "damage")),
            Step.If(Condition.Compare(Value.Excess("damage"), Comparison.Greater, n(0)), then = listOf(empower(Value.Excess("damage")))),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ))

    @Test
    fun twinmawAdder() = card(
        name = "Twinmaw Adder",
        cost = "{1}{G}",
        type = "Creature — Snake",
        pt = "1/1",
        text = """
            Deathtouch
            When this creature enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(Keyword.Deathtouch, triggered(entersSelf, empower(2)))

    // CR 606.4: a loyalty cost puts counters too
    @Test
    fun kindledTetherer() = card(
        name = "Kindled Tetherer",
        cost = "{2}{G}",
        type = "Creature — Elf Warrior",
        pt = "3/2",
        text = """
            Whenever you put one or more loyalty counters on a planeswalker, put a +1/+1 counter on this creature.
            {6}: Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(
            Ability.Triggered(Trigger.On(EventPattern.CountersPut(planeswalker, CounterKind.Loyalty, causedBy = controlledBy(PlayerFilter.You)), oneOrMore = true),
                Instructions(steps = listOf(counters(Objects.Self)))),
            activated(cost(mana("{6}")), empower(2)),
        )

    @Test
    fun heraldOfSwellingEchoes() = card(
        name = "Herald of Swelling Echoes",
        cost = "{G}{U}",
        type = "Creature — Avatar",
        pt = "2/3",
        text = """
            Landfall — Whenever a land you control enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[−10]: Put a +1/+1 counter on target creature for each land you control."
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield)), empower(2), abilityWord = "Landfall"),
            static(Mechanic.GainAbilities(listOf(loyalty(-10, Step.Do(Effect.PutCounters(target("creature"), p1p1, Value.Count(Objects.Each(and(land, you))))),
                targets = listOf(slot("creature", creature))))), affects = Objects.Each(and(planeswalker, you))),
        )

    @Test
    fun gorgonSDefiance() = card(
        name = "Gorgon's Defiance",
        cost = "{1}{G/U}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on up to one target creature. It gains vigilance until end of turn.
            Empower Jace 4. (Put four loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        """,
    ) parsesTo abilities(spell(counters(target("creature")), gains(target("creature"), Keyword.Vigilance), empower(4), targets = listOf(slot("creature", creature, min = 0))))

    @Test
    fun wardenOfTheSilentBell() = card(
        name = "Warden of the Silent Bell",
        cost = "{3}",
        type = "Artifact Creature — Chimera",
        pt = "3/2",
        text = "When this creature enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")",
    ) parsesTo abilities(triggered(entersSelf, empower(2)))

    // CR 614.12, 701.4a
    @Test
    fun scholarSRefuge() = card(
        name = "Scholar's Refuge",
        cost = null,
        type = "Land — Island",
        text = """
            ({T}: Add {U}.)
            As this land enters, you may behold a Jace. If you don't, this land enters tapped. (To behold a Jace, choose a Jace you control or reveal a Jace card from your hand.)
            {2}{U}, {T}: Empower Jace 2.
        """,
    ) parsesTo abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(
                    Step.Pay(cost(CostPart.Action(KeywordAction.Behold(sub("Jace")), bind = "beheld")), payer = Players.You, bind = "paid"),
                    Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped)))))))), affects = Affected.Objects(Objects.Self)),
            activated(cost(mana("{2}{U}"), tap), empower(2)),
        )

    @Test
    fun fatekeepCharm() = card(
        name = "Fatekeep Charm",
        cost = "{W}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Draw a card. Empower Jace 2.
            • Return target spell or creature to its owner's hand.
            • Creatures you control get +1/+2 until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(draw(1), empower(2)))),
            Mode(Instructions(targets = listOf(slot("permanent", or(spellFilter, creature))), steps = listOf(move(target("permanent"), Zone.Hand)))),
            Mode(Instructions(steps = listOf(pump(Objects.Each(creatureYouControl), 1, 2)))),
        ), min = 1, max = 1, chooser = Players.You))))
}
