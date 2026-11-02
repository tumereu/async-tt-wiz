package arcanum.parser.sets.dgm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
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
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTime
import arcanum.parser.sets.gtc.attacksSelf
import arcanum.parser.sets.gtc.costOf
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureToken
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.entersSelf
import arcanum.parser.sets.gtc.gains
import arcanum.parser.sets.gtc.manaPart
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.p1p1
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.steps
import kotlin.test.Test

/**
 * Dragon's Maze chunk 02: the guilds' mechanics again. Battalion (CR 207.2c, 508.1a), extort granted to other creatures
 * (CR 702.101b: each instance triggers), bloodrush, a trigger in the graveyard (CR 113.6b) paying to return the card,
 * evolve and "this creature evolves" (CR 702.100b), unleash (CR 702.98), scavenge and scavenge given to graveyard cards
 * for their mana cost (CR 702.97), detain (CR 701.35) and populate (CR 701.36).
 */
class DgmChunk02GuildMechanicsTest {
    private fun battalion(vararg steps: Step) = Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self), withOthers = 2)),
        Instructions(steps = steps.toList()), abilityWord = "Battalion")
    private val attackingCreature = slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature)))
    private fun bloodrush(cost: String, p: Int, t: Int) = Ability.Activated(costOf(manaPart(cost), CostPart.Discard(CostObjects.Self)),
        Instructions(targets = listOf(attackingCreature), steps = listOf(pump(Objects.Target("creature"), p, t))), rules = anyTime, abilityWord = "Bloodrush")
    private val withCounter = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, arcanum.parser.sets.gtc.you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))

    @Test
    fun legionMastiff() = card(
        name = "Legion Mastiff",
        cost = "{1}{W}",
        type = "Creature — Dog",
        pt = "2/2",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gains lifelink until end of turn. (Damage dealt by a creature with lifelink also causes its controller to gain that much life.)",
    ) parsesTo abilities(battalion(gains(Objects.Self, Keyword.Lifelink)))

    @Test
    fun tavicEdgeOfTheLegion() = card(
        name = "Tavic, Edge of the Legion",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/2",
        text = """
            Indestructible
            Battalion — Whenever Tavic and at least two other creatures attack, Tavic gets +5/+5 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Indestructible, battalion(pump(Objects.Self, 5, 5)))

    // CR 702.101b: every instance triggers on its own
    @Test
    fun pontiffOfTithes() = card(
        name = "Pontiff of Tithes",
        cost = "{4}{B}{B}",
        type = "Creature — Zombie Cleric",
        pt = "2/7",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Other creatures you control have extort. (If a creature has multiple instances of extort, each triggers separately.)
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Extort))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, arcanum.parser.sets.gtc.you))))),
    )

    // CR 113.6b: the second ability works only while this card is in your graveyard; one trigger per player dealt damage
    @Test
    fun pyrewildFirecaller() = card(
        name = "Pyrewild Firecaller",
        cost = "{2}{R}",
        type = "Creature — Goblin Shaman",
        pt = "3/1",
        text = """
            Bloodrush — {1}{R}, Discard this card: Target attacking creature gets +3/+1 until end of turn.
            Whenever one or more creatures you control deal combat damage to a player, if this card is in your graveyard, you may pay {3}. If you do, return this card to your hand.
        """,
    ) parsesTo abilities(
        bloodrush("{1}{R}", 3, 1),
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)), oneOrMore = true, batchPerPlayer = true),
            steps(
                Step.Pay(arcanum.parser.sets.gtc.mana("{3}"), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand))))),
            ),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    @Test
    fun rubblebeltMauler() = card(
        name = "Rubblebelt Mauler",
        cost = "{3}{R}",
        type = "Creature — Cat",
        pt = "3/3",
        text = "Bloodrush — {R}, Discard this card: Target attacking creature gets +3/+3 until end of turn.",
    ) parsesTo abilities(bloodrush("{R}", 3, 3))

    @Test
    fun batteringShark() = card(
        name = "Battering Shark",
        cost = "{2}{G}",
        type = "Creature — Shark Beast",
        pt = "2/1",
        text = """
            Trample
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Trample, Keyword.Evolve)

    // CR 702.100b: it evolves when its evolve ability puts +1/+1 counters on it
    @Test
    fun rogueKrasis() = card(
        name = "Rogue Krasis",
        cost = "{1}{G}{G}",
        type = "Creature — Beast Mutant",
        pt = "3/2",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            Whenever this creature evolves, put a +1/+1 counter on each other creature you control with a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        Ability.Triggered(
            Trigger.On(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1, causedBy = ObjectFilter.AbilityOf(keyword = Keyword.Evolve))),
            steps(Step.Do(Effect.PutCounters(Objects.Each(withCounter), p1p1, n(1)))),
        ),
    )

    @Test
    fun rakdosWyrm() = card(
        name = "Rakdos Wyrm",
        cost = "{2}{B}",
        type = "Creature — Drake",
        pt = "1/2",
        text = """
            Flying
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Unleash)

    @Test
    fun ezavaBloodWitchOfRakdos() = card(
        name = "Ezava, Blood Witch of Rakdos",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Human Cleric",
        pt = "3/3",
        text = """
            First strike, haste
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
            Each other creature you control with a +1/+1 counter on it has haste.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike, Keyword.Haste, Keyword.Unleash,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), affects = Affected.Objects(Objects.Each(withCounter))),
    )

    @Test
    fun thrashingVinedog() = card(
        name = "Thrashing Vinedog",
        cost = "{3}{G}",
        type = "Creature — Plant Dog",
        pt = "3/3",
        text = """
            Reach (This creature can block creatures with flying.)
            Scavenge {4}{G}{G} ({4}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(Keyword.Reach, Keyword.Scavenge(arcanum.parser.sets.gtc.mana("{4}{G}{G}")))

    // CR 702.97a: each creature card there has scavenge for its own mana cost; regeneration (CR 701.19a)
    @Test
    fun varrokTheScarMarked() = card(
        name = "Varrok, the Scar-Marked",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Troll Warrior",
        pt = "2/2",
        text = """
            Each creature card in your graveyard has scavenge. The scavenge cost is equal to its mana cost. (Exile a creature card from your graveyard and pay its mana cost: Put a number of +1/+1 counters equal to that card's power on target creature. Scavenge only as a sorcery.)
            Sacrifice another creature: Regenerate Varrok.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Scavenge(Cost(listOf(CostPart.ItsManaCost)))))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
        Ability.Activated(
            costOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)), count = n(1)))),
            steps(Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Self)))),
            rules = anyTime,
        ),
    )

    // CR 701.35: detain
    @Test
    fun lyevEdict() = card(
        name = "Lyev Edict",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Detain up to two target creatures your opponents control. (Until your next turn, those creatures can't attack or block and their activated abilities can't be activated.)",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creatures", creatureOpponentControls, min = 0, max = 2)),
            steps = listOf(Step.Do(Effect.Action(KeywordAction.Detain(Objects.Target("creatures"))))),
        )),
    )

    // CR 701.35: every such permanent, not targeted
    @Test
    fun lavinnaOfTheTenth() = card(
        name = "Lavinna of the Tenth",
        cost = "{3}{W}{U}",
        type = "Legendary Creature — Human Soldier",
        pt = "4/4",
        text = """
            Protection from red
            When Lavinna enters, detain each nonland permanent your opponents control with mana value 4 or less. (Until your next turn, those permanents can't attack or block and their activated abilities can't be activated.)
        """,
    ) parsesTo abilities(
        Keyword.Protection(ObjectFilter.OfColor(Color.Red)),
        Ability.Triggered(entersSelf, steps(Step.Do(Effect.Action(KeywordAction.Detain(Objects.Each(ObjectFilter.And(listOf(
            ObjectFilter.Not(arcanum.parser.sets.gtc.land), ObjectFilter.Category(ObjectCategory.Permanent), arcanum.parser.sets.gtc.opponents,
            ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, n(4)))))))))),
    )

    // CR 603.4: only if it was cast from your hand; CR 701.36: populate after the Bird exists
    @Test
    fun heirOfVituGhazi() = card(
        name = "Heir of Vitu-Ghazi",
        cost = "{3}{W}{W}",
        type = "Creature — Elemental",
        pt = "4/4",
        text = "When this creature enters, if you cast it from your hand, create a 1/1 white Bird creature token with flying, then populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, steps(
            Step.Do(Effect.CreateToken(creatureToken("Bird", listOf(Color.White), 1, 1, listOf(Keyword.Flying)), n(1), Players.You)),
            Step.Do(Effect.Action(KeywordAction.Populate)),
        ), interveningIf = Condition.CastFrom(Zone.Hand, Objects.Self)),
    )

    @Test
    fun wakenTheReflections() = card(
        name = "Waken the Reflections",
        cost = "{W}",
        type = "Sorcery",
        text = "Populate. (Create a token that's a copy of a creature token you control.)",
    ) parsesTo abilities(Ability.Spell(steps(Step.Do(Effect.Action(KeywordAction.Populate)))))

    // CR 603.3d: the target is chosen as it triggers; paying is "you may … If you do" (CR 118.12)
    @Test
    fun haazdaSnarePatrol() = card(
        name = "Haazda Snare Patrol",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "1/4",
        text = "Whenever this creature attacks, you may pay {W}. If you do, tap target creature an opponent controls.",
    ) parsesTo abilities(
        Ability.Triggered(attacksSelf, Instructions(
            targets = listOf(slot("creature", creatureOpponentControls)),
            steps = listOf(
                Step.Pay(arcanum.parser.sets.gtc.mana("{W}"), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Tap(Objects.Target("creature"))))),
            ),
        )),
    )
}
