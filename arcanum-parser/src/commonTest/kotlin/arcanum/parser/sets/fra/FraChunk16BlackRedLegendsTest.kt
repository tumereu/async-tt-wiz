package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 16: black and red legendary creatures. A counter when you cast a spell targeting an opponent or their
 * creature (CR 115.1); returning from the graveyard tapped with a counter by exiling another card; an exhaust ability (CR
 * 702.177); negative power assigning damage as though positive; "remove up to three counters" (CR 122.8); a casting
 * restriction under threshold (CR 601.3); a trigger once for each player's discards; a reflexive trigger after a sacrifice
 * (CR 603.12); a creature's power counting creature and planeswalker cards in your graveyard; a pump by the power of the
 * creature that entered; "that land is a Mountain"; a noncombat damage boost (CR 614.1a); convoke.
 */
class FraChunk16BlackRedLegendsTest {
    // CR 115.1
    @Test
    fun lieslLanceOfTorment() = card(
        name = "Liesl, Lance of Torment",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Knight",
        pt = "2/2",
        text = """
            First strike
            Whenever you cast a spell that targets an opponent or a creature an opponent controls, put a +1/+1 counter on Liesl.
        """,
    ) parsesTo abilities(Keyword.FirstStrike, triggered(youCast(ObjectFilter.Targets(objects = Objects.Each(creatureOpponentControls),
            players = Players.Each(PlayerFilter.Opponent))), counters(Objects.Self)))

    // CR 113.6b, 122.6
    @Test
    fun mirethMournfulRevenant() = card(
        name = "Mireth, Mournful Revenant",
        cost = "{1}{B}",
        type = "Legendary Creature — Zombie Satyr",
        pt = "2/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            {4}{B}, Exile another creature card from your graveyard: Return this card from your graveyard to the battlefield tapped with a +1/+1 counter on her.
        """,
    ) parsesTo abilities(Keyword.Menace, activated(cost(mana("{4}{B}"), CostPart.Exile(CostObjects.Choose(and(anotherObject, creature), n(1)), from = Zone.Graveyard)),
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = true, counters = listOf(CountersToPut(p1p1, n(1))))))))

    // CR 702.177a
    @Test
    fun selineThePenitent() = card(
        name = "Seline the Penitent",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "2/2",
        text = """
            Whenever another creature or planeswalker you control enters, mill two cards.
            Exhaust — {5}{B}: Return target creature or planeswalker card from your graveyard to the battlefield. Put a +1/+1 counter on Seline. Activate only as a sorcery. (Activate each exhaust ability only once.)
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(anotherObject, creatureOrPlaneswalker, you), to = Zone.Battlefield)), mill(2)),
            Ability.Activated(cost(mana("{5}{B}")), Instructions(targets = listOf(slot("card", and(creatureOrPlaneswalker, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                steps = listOf(move(target("card"), Zone.Battlefield), counters(Objects.Self))), asSorcery, label = AbilityLabel.Exhaust),
        )

    // CR 510.1a
    @Test
    fun snibbetTheOddity() = card(
        name = "Snibbet, the Oddity",
        cost = "{2}{B}",
        type = "Legendary Creature — Beast Horror",
        pt = "-2/4",
        text = """
            If Snibbet's power is negative, he assigns combat damage as though his power were positive.
            Threshold — Sacrifice another creature or planeswalker: Snibbet gets -2/-0 until end of turn. Activate only if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
            static(Mechanic.AssignsDamageAsThoughPowerPositive),
            Ability.Activated(cost(CostPart.Sacrifice(CostObjects.Choose(and(anotherObject, creatureOrPlaneswalker), n(1)))), Instructions(steps = listOf(pump(Objects.Self, -2, 0))),
                ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = threshold), abilityWord = "Threshold"),
        )

    // CR 122.8
    @Test
    fun pipwickSourHermit() = card(
        name = "Pipwick, Sour Hermit",
        cost = "{B}",
        type = "Legendary Creature — Mouse Warlock",
        pt = "1/1",
        text = """
            Deathtouch
            When Pipwick enters, remove up to three counters from another target creature or planeswalker.
        """,
    ) parsesTo abilities(Keyword.Deathtouch, triggered(entersSelf,
            Step.Choose(Choice.Number(n(0), n(3)), bind = "removed", chooser = Players.You),
            Step.Do(Effect.RemoveCounters(target("permanent"), null, Value.Bound("removed"))),
            targets = listOf(slot("permanent", and(anotherObject, creatureOrPlaneswalker)))))

    @Test
    fun carnageMaidenMostFeared() = card(
        name = "Carnage Maiden, Most Feared",
        cost = "{4}{B}",
        type = "Legendary Creature — Human Assassin",
        pt = "4/4",
        text = """
            Whenever another creature or planeswalker you control dies, Carnage Maiden deals 1 damage to target opponent and you gain 1 life.
            Whenever an opponent is dealt noncombat damage, put a +1/+1 counter on Carnage Maiden.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(anotherObject, creatureOrPlaneswalker, you), from = Zone.Battlefield, to = Zone.Graveyard)),
                damage(1, Recipients(players = Players.Target("opponent"))), gainLife(1), targets = listOf(targetOpponent)),
            triggered(on(opponentDealtNoncombatDamage), counters(Objects.Self)),
        )

    // CR 601.3
    @Test
    fun quillSinisterSchemer() = card(
        name = "Quill, Sinister Schemer",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Rogue",
        pt = "5/5",
        text = """
            Threshold — You can't cast this spell unless there are seven or more cards in your graveyard.
            Menace
            {B}, Discard this card: Target creature gets -3/-1 until end of turn.
        """,
    ) parsesTo abilities(
            static(Mechanic.CastRestriction(condition = threshold), abilityWord = "Threshold"),
            Keyword.Menace,
            activated(cost(mana("{B}"), CostPart.Discard(CostObjects.Self)), pump(target("creature"), -3, -1), targets = listOf(slot("creature", creature))),
        )

    // CR 608.2c
    @Test
    fun ilsaDiamondedgeMage() = card(
        name = "Ilsa, Diamondedge Mage",
        cost = "{3}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "3/1",
        text = """
            Flash
            When Ilsa enters, target permanent you control gains deathtouch until end of turn. Put a +1/+1 counter on it if it's a creature. Put a loyalty counter on it if it's a planeswalker.
        """,
    ) parsesTo abilities(
            Keyword.Flash,
            triggered(entersSelf,
                gains(target("permanent"), Keyword.Deathtouch),
                Step.If(Condition.ObjectMatches(target("permanent"), creature), then = listOf(counters(target("permanent")))),
                Step.If(Condition.ObjectMatches(target("permanent"), planeswalker), then = listOf(counters(target("permanent"), kind = CounterKind.Loyalty))),
                targets = listOf(slot("permanent", and(permanentFilter, you)))),
        )

    // CR 603.2c: once for each player's batch
    @Test
    fun smallbonesPocketPest() = card(
        name = "Smallbones, Pocket Pest",
        cost = "{2}{B}",
        type = "Legendary Creature — Skeleton Rogue",
        pt = "2/1",
        text = """
            When Smallbones enters, each opponent discards a card.
            Whenever a player discards one or more cards, Smallbones deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), n(1), ObjectFilter.Any))),
            Ability.Triggered(Trigger.On(listOf(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.Any)), oneOrMore = true, batchPerPlayer = true),
                Instructions(steps = listOf(damage(1, eachOpponent)))),
        )

    // CR 603.12, 101.4
    @Test
    fun hollisHauntedLoner() = card(
        name = "Hollis, Haunted Loner",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "0/3",
        text = """
            When Hollis enters, you may sacrifice a creature or planeswalker. When you do, each opponent sacrifices a creature of their choice.
            Hollis gets +1/+0 for each creature and planeswalker card in your graveyard.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(creatureOrPlaneswalker, n(1)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(steps = listOf(
                    Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                        Step.Choose(Choice.Objects(and(creature, controlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)), bind = "sacrificed",
                            chooser = Players.Bound("opponent")),
                        Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)))))))),
            static(Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(and(creatureOrPlaneswalker, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), n(0))),
        )

    // CR 603.6a
    @Test
    fun orrinFamedChampion() = card(
        name = "Orrin, Famed Champion",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Berserker",
        pt = "1/5",
        text = """
            Trample
            Whenever another creature you control enters, Orrin gets +X/+0 until end of turn, where X is that creature's power.
        """,
    ) parsesTo abilities(Keyword.Trample, triggered(on(EventPattern.ZoneChange(and(anotherObject, creature, you), to = Zone.Battlefield)),
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.After), Combine.Single), n(0)))))

    @Test
    fun mirethTheReveler() = card(
        name = "Mireth, the Reveler",
        cost = "{1}{R}",
        type = "Legendary Creature — Satyr",
        pt = "2/1",
        text = """
            Haste
            Each other creature you control with a +1/+1 counter on it has haste.
            {1}{R}, {T}: Put a +1/+1 counter on target creature that entered this turn.
        """,
    ) parsesTo abilities(
            Keyword.Haste,
            static(Mechanic.GainAbilities(listOf(Keyword.Haste)), affects = Objects.Each(and(anotherObject, creature, you, ObjectFilter.HasCounter(p1p1, 1)))),
            activated(cost(mana("{1}{R}"), tap), counters(target("creature")),
                targets = listOf(slot("creature", and(creature, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield)))))),
        )

    // CR 506.5
    @Test
    fun weiLanfengSolitary() = card(
        name = "Wei Lanfeng, Solitary",
        cost = "{4}{R}",
        type = "Legendary Creature — Human Berserker",
        pt = "4/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever a creature you control attacks a player alone, discard a card, then draw a card. Then put a +1/+1 counter on that creature for each card you've discarded this turn.
        """,
    ) parsesTo abilities(Keyword.Menace, triggered(on(EventPattern.Attack(creatureYouControl, alone = true, target = RecipientFilter(players = PlayerFilter.Any))),
            Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any)), draw(1),
            Step.Do(Effect.PutCounters(Objects.EventObject(EventRole.Object), p1p1, Value.EventsThisTurn(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), Measure.Objects)))))

    @Test
    fun seraphineOfEmberAndSoot() = card(
        name = "Seraphine of Ember and Soot",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Merfolk Noble",
        pt = "2/2",
        text = """
            When Seraphine enters, create a 5/5 red Dragon creature token with flying.
            {8}: Create a 5/5 red Dragon creature token with flying.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.CreateToken(creatureToken("Dragon", listOf(Color.Red), 5, 5, abilities = listOf(Keyword.Flying)), n(1), Players.You))),
            activated(cost(mana("{8}")), Step.Do(Effect.CreateToken(creatureToken("Dragon", listOf(Color.Red), 5, 5, abilities = listOf(Keyword.Flying)), n(1), Players.You))),
        )

    // CR 605.1a: a triggered ability that adds mana, but not a mana ability
    @Test
    fun brannocTheEarthshaper() = card(
        name = "Brannoc, the Earthshaper",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Warrior",
        pt = "3/2",
        text = """
            Reach
            Landfall — Whenever a land you control enters, Brannoc deals 1 damage to each opponent. If that land is a Mountain, add {R}.
        """,
    ) parsesTo abilities(Keyword.Reach, triggered(on(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield)), damage(1, eachOpponent),
            Step.If(Condition.ObjectMatches(Objects.EventObject(EventRole.After), sub("Mountain")),
                then = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You)))), abilityWord = "Landfall"))

    @Test
    fun tindraTheWoodfeller() = card(
        name = "Tindra, the Woodfeller",
        cost = "{R}",
        type = "Legendary Creature — Elf Warrior",
        pt = "2/1",
        text = "{2}, {T}, Sacrifice an artifact or land: Draw a card.",
    ) parsesTo abilities(activated(cost(mana("{2}"), tap, CostPart.Sacrifice(CostObjects.Choose(or(artifact, land), n(1)))), draw(1)))

    @Test
    fun ashaSteadfastRebuilder() = card(
        name = "Asha, Steadfast Rebuilder",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/2",
        text = """
            When Asha enters, create a 1/1 colorless Thopter artifact creature token with flying.
            {5}{R}: Target creature gets +X/+0 until end of turn, where X is the number of artifacts you control.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.CreateToken(creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying),
                types = listOf(CardType.Artifact, CardType.Creature)), n(1), Players.You))),
            activated(cost(mana("{5}{R}")), untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(and(artifact, you))), n(0))),
                targets = listOf(slot("creature", creature))),
        )

    @Test
    fun hekatTheStormGodSChosen() = card(
        name = "Hekat, the Storm God's Chosen",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Warrior Cleric",
        pt = "2/2",
        text = "Creatures you control have haste.",
    ) parsesTo abilities(static(Mechanic.GainAbilities(listOf(Keyword.Haste)), affects = Objects.Each(creatureYouControl)))

    @Test
    fun rinkoTakaharaHunter() = card(
        name = "Rinko Takahara, Hunter",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Mercenary",
        pt = "2/4",
        text = """
            Double strike
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever a creature an opponent controls with power or toughness 1 or less blocks, Rinko Takahara deals 1 damage to that creature's controller.
        """,
    ) parsesTo abilities(Keyword.DoubleStrike, Keyword.Prowess, triggered(on(EventPattern.Block(and(creature, controlledBy(PlayerFilter.Opponent),
            or(ObjectFilter.Power(Comparison.AtMost, n(1)), ObjectFilter.Toughness(Comparison.AtMost, n(1)))), ObjectFilter.Any)),
            damage(1, Recipients(players = Players.ControllerOf(Objects.EventObject(EventRole.Object))))))

    // CR 614.1a
    @Test
    fun dovanGuildSparkwright() = card(
        name = "Dovan, Guild Sparkwright",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/2",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals that much damage plus 1 instead.
        """,
    ) parsesTo abilities(Keyword.Prowess, static(Mechanic.Replacement(EventPattern.Damage(controlledBy(PlayerFilter.You),
            RecipientFilter(objects = and(permanentFilter, controlledBy(PlayerFilter.Opponent)), players = PlayerFilter.Opponent), combat = false), modify = listOf(Modification.AddAmount(n(1))))))

    @Test
    fun hollisTeamChampion() = card(
        name = "Hollis, Team Champion",
        cost = "{4}{R}",
        type = "Legendary Creature — Human Warrior",
        pt = "3/3",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Whenever you cast a noncreature spell, creatures you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Convoke, triggered(youCast(not(creature)), pump(Objects.Each(creatureYouControl), 1, 0)))
}
