package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 01: white cards, first part. Cost reductions by a target's status (CR 601.2f), Food tokens
 * (CR 111.10b) counted and made by the batch, a creature assigning combat damage by its toughness (CR 510.1c), a spell cast
 * with no mana spent (CR 601.2h), counters by the number of creatures as it enters (CR 614.1c), a reflexive trigger after
 * making a Food (CR 603.12), equip limited to a subtype (CR 702.6a), plainscycling (CR 702.29e), a choice of keywords, a
 * Spirit that can't attack or block losing its other creature types (CR 205.1b), equip at instant speed and for {0} once a
 * turn (CR 602.5d, 118.9), a creature that becomes another with new base power and toughness (CR 613.4b), flash for
 * legendary and artifact spells, triggers by a legendary or artifact entering or leaving happening twice (CR 603.2d) and
 * attacks by two or more creatures (CR 508.1).
 */
class LtrChunk01WhiteTest {
    // CR 601.2f: the reduction looks at the spell's chosen target
    @Test
    fun banishFromHalmere() = card(
        name = "Banish from Halmere",
        cost = "{4}{W}",
        type = "Sorcery",
        text = """
            This spell costs {2} less to cast if it targets a tapped creature.
            Exile target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), tapped)),
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", creature))),
    )

    // CR 608.2c: "for each creature you control" counts after the destruction
    @Test
    fun theSkirmishAtReedbrook() = card(
        name = "The Skirmish at Reedbrook",
        cost = "{1}{W}{W}",
        type = "Sorcery",
        text = """Destroy all creatures with power 3 or greater. Then create a Food token for each creature you control. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(destroy(Objects.Each(and(creature, power(Comparison.AtLeast, 3)))), createFood(count(creatureYouControl))),
    )

    // CR 510.1c: assigning combat damage by toughness
    @Test
    fun tamThePackhorse() = card(
        name = "Tam the Packhorse",
        cost = "{3}{W}",
        type = "Legendary Creature — Horse",
        text = """
            When Tam the Packhorse enters, create two Food tokens. (They're artifacts with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Sacrifice a Food: Until end of turn, target creature you control assigns combat damage equal to its toughness rather than its power.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        triggered(entersSelf, createFood(n(2))),
        activated(cost(sacrificeA(sub("Food"))), untilEndOfTurn(target("creature"), Mechanic.AssignsDamageByToughness),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 601.2h, 603.4: no mana spent to cast that spell
    @Test
    fun hadrionWardenOfTheGate() = card(
        name = "Hadrion, Warden of the Gate",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Soldier",
        text = """
            Vigilance
            Whenever an opponent casts a spell, if no mana was spent to cast it, counter that spell.
            Sacrifice Hadrion: Creatures you control gain indestructible until end of turn. The Ring tempts you.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)), doing(Effect.Counter(Objects.EventObject(EventRole.Object))),
            interveningIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.Object)), Comparison.Equal, n(0))),
        activated(cost(sacrificeSelf), gains(Objects.Each(creatureYouControl), Keyword.Indestructible), ringTempts),
    )

    // CR 614.1c: counters as it enters; CR 603.12: "If you do" after removing a counter
    @Test
    fun morningOfNewHope() = card(
        name = "Morning of New Hope",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            This enchantment enters with a hope counter on it for each creature you control.
            At the beginning of your end step, remove a hope counter from this enchantment. If you do, draw a card. Then if this enchantment has no hope counters on it, sacrifice it and you gain 4 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(hope, count(creatureYouControl))))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Pay(arcanum.api.Cost(listOf(CostPart.RemoveCounters(hope, n(1), arcanum.api.CostObjects.Self))), payer = Players.You, mandatory = true, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1))),
            Step.If(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(hope, atLeast = 1))),
                then = listOf(doing(Effect.Sacrifice(Objects.Self)), gainLife(4)))),
    )

    // CR 702.6a: an equip ability that targets only a Human
    @Test
    fun waywardRangersBlade() = card(
        name = "Wayward Ranger's Blade",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+1.
            Equip Human {1}
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(2, 1), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{1}"), onto = sub("Human")),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 702.29e: plainscycling
    @Test
    fun eaglesOfTheFarPeaks() = card(
        name = "Eagles of the Far Peaks",
        cost = "{5}{W}",
        type = "Creature — Bird Soldier",
        text = """
            Flying
            When this creature enters, creatures you control get +1/+0 and gain first strike until end of turn.
            Plainscycling {1} ({1}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, untilEndOfTurn(Objects.Each(creatureYouControl), pt(1, 0), has(Keyword.FirstStrike))),
        Keyword.Typecycling(sub("Plains"), "Plainscycling", manaCost("{1}")),
    )

    // CR 603.12: "When you do" after creating the Food: a reflexive trigger, its target chosen then
    @Test
    fun westmeadowFarmer() = card(
        name = "Westmeadow Farmer",
        cost = "{2}{W}",
        type = "Creature — Halfling Peasant",
        text = """When this creature enters, create a Food token. When you do, target creature you control gets +1/+1 until end of turn for each Food you control. (A Food token is an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, createFood(), doing(Effect.ReflexiveTrigger(Instructions(
            targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(foods, foods))))))),
    )

    // CR 120.3: the damaged creature is the event's object
    @Test
    fun northMarkCavalier() = card(
        name = "North-Mark Cavalier",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        text = """
            Vigilance
            Whenever this creature deals damage to a Goblin or Orc, destroy that creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(objects = goblinOrOrc))),
            destroy(Objects.EventObject(EventRole.Object))),
    )

    // CR 702.6a, 601.2f: equip abilities cost less
    @Test
    fun aldrethLadyOfThePlains() = card(
        name = "Aldreth, Lady of the Plains",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Noble",
        text = """
            At the beginning of combat on your turn, target creature gains your choice of first strike or vigilance until end of turn. If that creature is equipped, it gains first strike and vigilance until end of turn instead.
            Equip abilities you activate cost {1} less to activate.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            Step.If(Condition.ObjectMatches(target("creature"), ObjectFilter.Attached(attachments = equipment)),
                then = listOf(gains(target("creature"), Keyword.FirstStrike, Keyword.Vigilance)),
                otherwise = listOf(
                    Step.Choose(Choice.Option(listOf("first strike", "vigilance")), bind = "keyword", chooser = Players.You),
                    Step.If(Condition.Chose("keyword", "first strike"), then = listOf(gains(target("creature"), Keyword.FirstStrike)),
                        otherwise = listOf(gains(target("creature"), Keyword.Vigilance))))),
            targets = listOf(slot("creature", creature))),
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind), you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 701.20 (not a legendary creature: one card from hand to the bottom)
    @Test
    fun errandRiderOfTheWhiteCity() = card(
        name = "Errand-Rider of the White City",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        text = "When this creature enters, draw a card. Then if you don't control a legendary creature, put a card from your hand on the bottom of your library.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1),
            Step.If(Condition.Not(Condition.Exists(and(legendary, creature, you), atLeast = 1)), then = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    bind = "put", chooser = Players.You),
                move(Objects.Bound("put"), Zone.Library, position = -1)))),
    )

    @Test
    fun escapeFromTheTower() = card(
        name = "Escape from the Tower",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +1/+3 and gains flying until end of turn. Untap it.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(1, 3), has(Keyword.Flying)), untapIt(target("creature")),
            targets = listOf(slot("creature", creature))),
    )

    // CR 602.2b, 601.2f: the activation's cost reduction
    @Test
    fun esquireOfTheSteward() = card(
        name = "Esquire of the Steward",
        cost = "{W}",
        type = "Creature — Human Soldier",
        text = "{4}{W}, {T}: Creatures you control get +1/+1 until end of turn. This ability costs {2} less to activate if you control a legendary creature.",
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(mana("{4}{W}"), tap), pump(Objects.Each(creatureYouControl), 1, 1),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, costChange = CostChange.Less(ManaCost.parse("{2}")!!,
                Value.Min(listOf(count(and(legendary, creature, you)), n(1)))))),
    )

    // CR 701.54d, 603.4
    @Test
    fun haldorFieldCaptain() = card(
        name = "Haldor, Field Captain",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Soldier",
        text = """
            At the beginning of your end step, if a creature died under your control this turn, draw a card.
            Whenever the Ring tempts you, if you chose a creature other than Haldor as your Ring-bearer, create a 1/1 white Human Soldier creature token.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), draw(1), interveningIf = creatureDiedUnderYourControl),
        triggered(ringTemptsYou, create(humanSoldier), interveningIf = choseAnotherBearer),
    )

    // CR 702.21a: ward {1} granted
    @Test
    fun blossomingOfTheSilverTree() = card(
        name = "Blossoming of the Silver Tree",
        cost = "{W}{W}",
        type = "Legendary Enchantment",
        text = """
            Legendary creatures you control get +2/+1 and have ward {1}.
            Nonlegendary creatures you control get +1/+1.
        """,
    ) parsesTo abilities(
        static(pt(2, 1), has(Keyword.Ward(manaCost("{1}"))), affects = Objects.Each(and(legendary, creature, you))),
        static(pt(1, 1), affects = Objects.Each(and(not(legendary), creature, you))),
    )

    // CR 205.1b: becoming a Spirit replaces its other creature types
    @Test
    fun mistOnTheBarrowHills() = card(
        name = "Mist on the Barrow Hills",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature is a Spirit and can't attack or block. (It loses all other creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(arcanum.api.Subtype("Spirit"))), replaceSubtypes = true),
            Mechanic.CantAttack(), Mechanic.CantBlock(attackers = ObjectFilter.Any), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 702.6a, 118.9: equip at instant speed during your turn; the first equip each of your turns for {0}
    @Test
    fun smithAnew() = card(
        name = "Smith Anew",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, return target Equipment card from your graveyard to the battlefield.
            During your turn, you may activate equip abilities any time you could cast an instant.
            You may pay {0} rather than pay the equip cost of the first equip ability you activate during each of your turns.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(equipment)))),
        playerStatic(Mechanic.ActivateAtInstantSpeed(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind)), condition = Condition.YourTurn),
        playerStatic(Mechanic.AbilityAlternativeCost(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind), manaCost("{0}"), firstEachTurn = true),
            condition = Condition.YourTurn),
    )

    // CR 613.4b: new base power and toughness; CR 613.1d: new subtypes; CR 701.54a in the granted ability
    @Test
    fun pellShadowsBane() = card(
        name = "Pell, Shadow's Bane",
        cost = "{W}",
        type = "Legendary Creature — Halfling Citizen",
        text = """
            {W/B}{W/B}: If Pell is a Citizen, it becomes a Halfling Scout with base power and toughness 2/3 and lifelink.
            {B}{B}{B}: If Pell is a Scout, it becomes a Halfling Rogue with "Whenever this creature deals combat damage to a player, that player loses the game if the Ring has tempted you four or more times this game. Otherwise, the Ring tempts you."
        """,
        pt = "1/2",
    ) parsesTo abilities(
        activated(cost(mana("{W/B}{W/B}")),
            Step.If(Condition.ObjectMatches(Objects.Self, sub("Citizen")), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(addSubtypes = listOf("Halfling", "Scout").map { SubtypeRef.Literal(arcanum.api.Subtype(it)) }, replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(2), n(3)),
                has(Keyword.Lifelink)), arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely))))),
        activated(cost(mana("{B}{B}{B}")),
            Step.If(Condition.ObjectMatches(Objects.Self, sub("Scout")), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(addSubtypes = listOf("Halfling", "Rogue").map { SubtypeRef.Literal(arcanum.api.Subtype(it)) }, replaceSubtypes = true),
                has(triggered(combatDamageToAPlayer,
                    Step.If(Condition.Compare(Value.RingTemptations(Players.You), Comparison.AtLeast, n(4)),
                        then = listOf(doing(Effect.LoseGame(Players.EventPlayer))),
                        otherwise = listOf(ringTempts))))), arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely))))),
    )

    // CR 603.2d: an entering or leaving legendary permanent or artifact makes triggers happen twice
    @Test
    fun mithrandelTheRadiant() = card(
        name = "Mithrandel the Radiant",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Flash
            You may cast legendary spells and artifact spells as though they had flash.
            If a legendary permanent or an artifact entering or leaving the battlefield causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flash,
        playerStatic(Mechanic.CastAsThoughFlash(or(and(legendary, spellFilter), and(artifact, spellFilter)))),
        static(
            Mechanic.TriggersAdditionalTime(EventPattern.ZoneChange(legendaryPermanentOrArtifact, to = Zone.Battlefield), and(permanentFilter, you)),
            Mechanic.TriggersAdditionalTime(EventPattern.ZoneChange(legendaryPermanentOrArtifact, from = Zone.Battlefield), and(permanentFilter, you))),
    )

    @Test
    fun halflingsPrick() = card(
        name = "Halfling's Prick",
        cost = "{1}{W}",
        type = "Instant",
        text = "Halfling's Prick deals X damage to target creature, where X is the number of creatures you control plus the number of Foods you control.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(count(creatureYouControl), foods)), toObjects(target("creature"))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 508.1: two or more creatures attacking the same player
    @Test
    fun windrovarHorizonWatcher() = card(
        name = "Windrovar, Horizon Watcher",
        cost = "{4}{W}",
        type = "Legendary Creature — Bird Noble",
        text = """
            Flying
            Whenever two or more creatures you control attack a player, target attacking creature without flying gains flying until end of turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(twoOrMoreAttackAPlayer, gains(target("creature"), Keyword.Flying),
            targets = listOf(slot("creature", and(attacking, creature, not(kw(Keyword.Flying)))))),
    )

    private val hope = CounterKind.Named("hope")
    private val foods = count(and(sub("Food"), you))
    private val legendaryPermanentOrArtifact = or(and(legendary, permanentFilter), artifact)
}
