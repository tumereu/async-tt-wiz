package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.token
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 09: red cards. Damage from an enters trigger with X as it was cast (CR 107.3m); exiling the top card
 * to play it until the end of your next turn; a Cadet then a counter on each other Wizard token (not the one just created);
 * returning from the graveyard after an opponent was dealt noncombat damage last turn; a token replacement ("that many
 * Dragon tokens are created instead", CR 614.1a); "if that permanent would die this turn, exile it instead"; token copies with
 * haste and a sacrifice trigger (CR 707.9b); revealing until a creature or planeswalker card; flashback given to a card in
 * the graveyard (CR 702.34); a reflexive trigger after a discard (CR 603.12); a cost reduction with a condition.
 */
class FraChunk09RedTest {
    // CR 107.3m
    @Test
    fun lionSTorment() = card(
        name = "Lion's Torment",
        cost = "{X}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, it deals X damage to any target.
            Creatures you control have trample.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.CastX(Objects.Self), Objects.Self)), targets = listOf(anyTarget)),
            static(Mechanic.GainAbilities(listOf(Keyword.Trample)), affects = Objects.Each(creatureYouControl)),
        )

    @Test
    fun artificerSInsight() = card(
        name = "Artificer's Insight",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Creatures you control gain first strike until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(spell(gains(Objects.Each(creatureYouControl), Keyword.FirstStrike), draw(1)))

    @Test
    fun rouseTheBlaze() = card(
        name = "Rouse the Blaze",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Rouse the Blaze deals 6 damage to target creature or planeswalker an opponent controls. Put a +1/+1 counter on up to one target creature you control.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
            spell(damage(6, Recipients(objects = target("permanent"))), counters(target("creature")),
                targets = listOf(slot("permanent", and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Opponent))), slot("creature", creatureYouControl, min = 0))),
            Keyword.Typecycling(and(ObjectFilter.Super(Supertype.Basic), land), "Basic landcycling", cost(mana("{2}"))),
        )

    // CR 611.2a
    @Test
    fun fieryClimax() = card(
        name = "Fiery Climax",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature gets +3/+1 until end of turn.
            Exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(spell(
            pump(target("creature"), 3, 1),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn)),
            targets = listOf(slot("creature", creature)),
        ))

    @Test
    fun pyromancerSSparkling() = card(
        name = "Pyromancer's Sparkling",
        cost = "{2}{R}",
        type = "Creature — Gremlin Elemental",
        pt = "2/2",
        text = """
            Haste
            Whenever you cast a noncreature spell, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(Keyword.Haste, triggered(youCast(not(creature)), counters(Objects.Self)))

    // CR 113.6b
    @Test
    fun claimTheLimelight() = card(
        name = "Claim the Limelight",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Create a 2/2 colorless Wizard Soldier creature token named Cadet, then put a +1/+1 counter on each other Wizard token you control.
            At the beginning of each upkeep, if an opponent was dealt noncombat damage last turn, return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
            spell(createCadets(1), counters(Objects.Each(and(not(ObjectFilter.Is(Objects.Bound("created"))), sub("Wizard"), token, you)))),
            Ability.Triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), Instructions(steps = listOf(move(Objects.Self, Zone.Hand))),
                interveningIf = Condition.HappenedLastTurn(opponentDealtNoncombatDamage, 1, Measure.Events), functionsIn = listOf(Zone.Graveyard)),
        )

    @Test
    fun cragmawBehemoth() = card(
        name = "Cragmaw Behemoth",
        cost = "{4}{R}{R}{R}",
        type = "Artifact Creature — Beast Construct",
        pt = "5/5",
        text = """
            Haste
            When this creature enters, creatures you control gain trample and get +X/+0 until end of turn, where X is the number of artifacts you control.
        """,
    ) parsesTo abilities(Keyword.Haste, triggered(entersSelf, untilEndOfTurn(Objects.Each(creatureYouControl),
            Mechanic.GainAbilities(listOf(Keyword.Trample)), Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(and(artifact, you))), n(0)))))

    // CR 701.20c
    @Test
    fun hexscarredFiend() = card(
        name = "Hexscarred Fiend",
        cost = "{2}{R}{R}",
        type = "Creature — Demon",
        pt = "4/4",
        text = """
            Flying, trample
            When this creature enters, search your library for a card, put it into your hand, shuffle, then discard a card at random.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Trample, triggered(entersSelf,
            Step.Do(Effect.Search(Players.You, ObjectFilter.Any, n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            Step.Do(Effect.Shuffle(Players.You)),
            Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, random = true))))

    // CR 614.1a, 111.1
    @Test
    fun wyrmishWanderer() = card(
        name = "Wyrmish Wanderer",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        pt = "5/5",
        text = """
            Flying
            If one or more artifact tokens would be created under your control, that many 5/5 red Dragon creature tokens with flying are created instead.
        """,
    ) parsesTo abilities(Keyword.Flying, static(Mechanic.Replacement(EventPattern.CreateToken(and(artifact, controlledBy(PlayerFilter.You))),
            instead = Instructions(steps = listOf(Step.Do(Effect.CreateToken(creatureToken("Dragon", listOf(Color.Red), 5, 5, abilities = listOf(Keyword.Flying)),
                Value.EventAmount, Players.You)))))))

    @Test
    fun eardrumSplitter() = card(
        name = "Eardrum Splitter",
        cost = "{1}{R}",
        type = "Creature — Human Bard",
        pt = "2/2",
        text = "{1}, {T}: Another target creature you control with power 2 or less can't be blocked this turn.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
            targets = listOf(slot("creature", and(anotherObject, creature, you, ObjectFilter.Power(Comparison.AtMost, n(2)))))))

    // CR 614.1a: a replacement effect created by the spell
    @Test
    fun quintessenceScorch() = card(
        name = "Quintessence Scorch",
        cost = "{1}{R}",
        type = "Instant",
        text = "Quintessence Scorch deals 5 damage to target black or green creature or planeswalker. If that permanent would die this turn, exile it instead.",
    ) parsesTo abilities(spell(
            damage(5, Recipients(objects = target("permanent"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("permanent")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("permanent", and(or(ObjectFilter.OfColor(Color.Black), ObjectFilter.OfColor(Color.Green)), creatureOrPlaneswalker))),
        ))

    // CR 707.9b
    @Test
    fun confrontThyself() = card(
        name = "Confront Thyself",
        cost = "{5}{R}{R}",
        type = "Sorcery",
        text = "For each creature target player controls, create a token that's a copy of that creature, except it has haste and \"At the beginning of the end step, if you don't control a planeswalker, sacrifice this creature.\"",
    ) parsesTo abilities(spell(Step.ForEach(Each.Objects(Objects.Each(and(creature, controlledBy(PlayerFilter.Is(Players.Target("player")))))), "creature", listOf(
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("creature"), except = listOf(CopyException.AddAbilities(listOf(
                Keyword.Haste,
                triggered(stepBegins(TurnStep.End, PlayerFilter.Any), Step.Do(Effect.Sacrifice(Objects.Self)),
                    interveningIf = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            )))), n(1), Players.You)))), targets = listOf(targetPlayer)))

    @Test
    fun thunderingFortissimo() = card(
        name = "Thundering Fortissimo",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Thundering Fortissimo deals 1 damage to each creature and planeswalker your opponents control.
            • Thundering Fortissimo deals 5 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Each(and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Opponent)))),
                n(1), Objects.Self))))),
            Mode(Instructions(targets = listOf(slot("permanent", creatureOrPlaneswalker)), steps = listOf(damage(5, Recipients(objects = target("permanent")))))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun heartcordTugger() = card(
        name = "Heartcord Tugger",
        cost = "{3}{R}",
        type = "Creature — Elf Sorcerer",
        pt = "3/1",
        text = """
            Trample
            When this creature enters, create a 2/2 colorless Wizard Soldier creature token named Cadet.
        """,
    ) parsesTo abilities(Keyword.Trample, triggered(entersSelf, createCadets(1)))

    // CR 701.20
    @Test
    fun personaReverberation() = card(
        name = "Persona Reverberation",
        cost = "{2}{R}",
        type = "Enchantment",
        text = "{3}{R}: Exile target creature or planeswalker you control. Reveal cards from the top of your library until you reveal a creature or planeswalker card. Put that card onto the battlefield and the rest on the bottom of your library in a random order. Activate only as a sorcery.",
    ) parsesTo abilities(activated(cost(mana("{3}{R}")),
            move(target("permanent"), Zone.Exile),
            Step.Do(Effect.RevealUntil(creatureOrPlaneswalker, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Battlefield),
            Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            targets = listOf(slot("permanent", and(creatureOrPlaneswalker, you))), rules = asSorcery))

    // CR 603.2c: once for the batch
    @Test
    fun sovereignOfSpines() = card(
        name = "Sovereign of Spines",
        cost = "{1}{R}",
        type = "Creature — Lizard Bard",
        pt = "2/1",
        text = """
            Menace
            Whenever one or more opponents are dealt noncombat damage, creatures you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Menace, Ability.Triggered(Trigger.On(opponentDealtNoncombatDamage, oneOrMore = true), Instructions(steps = listOf(pump(Objects.Each(creatureYouControl), 1, 0)))))

    @Test
    fun deftBladeshaper() = card(
        name = "Deft Bladeshaper",
        cost = "{1}{R}",
        type = "Creature — Human Warrior",
        pt = "2/1",
        text = """
            During your turn, this creature has first strike.
            {1}{R}: This creature gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(static(Mechanic.GainAbilities(listOf(Keyword.FirstStrike)), condition = Condition.YourTurn), activated(cost(mana("{1}{R}")), pump(Objects.Self, 1, 0)))

    // CR 702.34a
    @Test
    fun barbcasterAdept() = card(
        name = "Barbcaster Adept",
        cost = "{1}{R}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Haste
            When this creature enters, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(Keyword.Haste, triggered(entersSelf, untilEndOfTurn(target("card"), Mechanic.GainAbilities(listOf(Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
            targets = listOf(slot("card", and(or(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))))

    // CR 603.12
    @Test
    fun tetherlineEngineer() = card(
        name = "Tetherline Engineer",
        cost = "{4}{R}",
        type = "Creature — Minotaur Artificer",
        pt = "4/5",
        text = """
            Reach
            When this creature enters, you may discard a card. When you do, this creature deals 2 damage to any target.
        """,
    ) parsesTo abilities(Keyword.Reach, triggered(entersSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(
            Step.Do(Effect.ReflexiveTrigger(Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(2), Objects.Self))))))))))

    // CR 601.2f
    @Test
    fun furyOfTheCrimsonmane() = card(
        name = "Fury of the Crimsonmane",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a legendary creature.
            Fury of the Crimsonmane deals 4 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
            static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
                condition = Condition.Exists(and(ObjectFilter.Super(Supertype.Legendary), creature, you), 1)),
            spell(damage(4, Recipients(objects = target("permanent"))), targets = listOf(slot("permanent", creatureOrPlaneswalker))),
        )
}
