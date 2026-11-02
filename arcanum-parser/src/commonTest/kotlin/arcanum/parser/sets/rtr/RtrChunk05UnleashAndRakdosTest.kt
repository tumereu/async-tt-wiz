package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Choice
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.artifact
import arcanum.parser.sets.rtr.Rtr.combatDamageToAPlayer
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.damage
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonland
import arcanum.parser.sets.rtr.Rtr.p1p1
import arcanum.parser.sets.rtr.Rtr.planeswalker
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.regenerate
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import kotlin.test.Test

/**
 * Return to Ravnica chunk 05: unleash (CR 702.98a: it may enter with a +1/+1 counter, and can't block while it has one)
 * alone and with other keywords and abilities; and the Rakdos cards: life lost by opponents this turn (a cast restriction,
 * a cost reduction per life, a target filter, counters as it enters), "target opponent or planeswalker" and its player or
 * controller, discards at random, a named card searched for in three zones (CR 701.23), half a player's life rounded up,
 * each creature dealing damage to its own controller.
 */
class RtrChunk05UnleashAndRakdosTest {
    private val life = Value.EventsThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), Measure.Amount)

    // ---- Unleash (CR 702.98a)

    @Test
    fun carouseGhoul() = card(
        name = "Carouse Ghoul",
        cost = "{2}{B}",
        type = "Creature — Zombie",
        pt = "3/3",
        text = "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)",
    ) parsesTo abilities(
        Keyword.Unleash,
    )

    @Test
    fun boneJuggler() = card(
        name = "Bone Juggler",
        cost = "{1}{B}",
        type = "Creature — Skeleton Warrior",
        pt = "1/1",
        text = """
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
            {1}{B}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        Keyword.Unleash,
        activated(cost(manaPart("{1}{B}")), regenerate(Objects.Self)),
    )

    @Test
    fun revelCutthroat() = card(
        name = "Revel Cutthroat",
        cost = "{1}{B}",
        type = "Creature — Human Assassin",
        pt = "1/1",
        text = """
            Deathtouch
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Unleash,
    )

    @Test
    fun brawlhouseGiant() = card(
        name = "Brawlhouse Giant",
        cost = "{2}{R}{R}",
        type = "Creature — Giant",
        pt = "2/2",
        text = """
            Trample
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Unleash,
    )

    /** A keyword as long as it has a +1/+1 counter (layer 6), the counter an unleashed creature may enter with. */
    @Test
    fun mayhemImps() = card(
        name = "Mayhem Imps",
        cost = "{4}{R}{R}",
        type = "Creature — Imp",
        pt = "6/5",
        text = """
            Flying
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
            This creature has trample as long as it has a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Unleash,
        static(Objects.Self, Mechanic.GainAbilities(listOf(Keyword.Trample)), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 1))),
    )

    @Test
    fun chainhookBrawler() = card(
        name = "Chainhook Brawler",
        cost = "{1}{R}",
        type = "Creature — Human Warrior",
        pt = "2/1",
        text = "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)",
    ) parsesTo abilities(
        Keyword.Unleash,
    )

    @Test
    fun spatterBruiser() = card(
        name = "Spatter Bruiser",
        cost = "{2}{R}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = """
            First strike
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Unleash,
    )

    @Test
    fun fairgroundNightmare() = card(
        name = "Fairground Nightmare",
        cost = "{4}{B}{R}",
        type = "Creature — Nightmare Horse",
        pt = "4/4",
        text = """
            First strike, haste
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Haste,
        Keyword.Unleash,
    )

    /** "It deals damage equal to its power": the sacrificed creature as it last existed (CR 608.2h), counters included. */
    @Test
    fun pitfightFlailer() = card(
        name = "Pitfight Flailer",
        cost = "{1}{B}{R}",
        type = "Creature — Ogre Warrior",
        pt = "2/2",
        text = """
            Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
            {2}{B}{R}, Sacrifice this creature: It deals damage equal to its power to target player or planeswalker.
        """,
    ) parsesTo abilities(
        Keyword.Unleash,
        activated(cost(manaPart("{2}{B}{R}"), CostPart.Sacrifice(CostObjects.Self)),
            damage(Recipients(target = "player or planeswalker"), Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)),
            targets = listOf(TargetSlot("player or planeswalker", TargetKind.Either(planeswalker, PlayerFilter.Any), min = n(1), max = n(1)))),
    )

    @Test
    fun gigglingFiendling() = card(
        name = "Giggling Fiendling",
        cost = "{B/R}",
        type = "Creature — Devil",
        pt = "2/2",
        text = "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)",
    ) parsesTo abilities(
        Keyword.Unleash,
    )

    @Test
    fun spawnOfThePit() = card(
        name = "Spawn of the Pit",
        cost = "{3}{B}{R}",
        type = "Creature — Horror",
        pt = "5/5",
        text = "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)",
    ) parsesTo abilities(
        Keyword.Unleash,
    )

    // ---- Rakdos

    /** CR 601.3: casting is allowed only while the condition holds; a cost reduction counting life lost this turn (CR 601.2f). */
    @Test
    fun zarkhulLordOfRevels() = card(
        name = "Zarkhul, Lord of Revels",
        cost = "{B}{B}{R}{R}",
        type = "Legendary Creature — Demon",
        pt = "6/6",
        text = """
            You can't cast Zarkhul unless an opponent lost life this turn.
            Flying, trample
            Creature spells you cast cost {1} less to cast for each 1 life your opponents have lost this turn.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CastRestriction(condition = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events))),
        Keyword.Flying,
        Keyword.Trample,
        static(Objects.Self, Mechanic.CostChange(all(creature, Rtr.spell, you), CostChange.Less(Rtr.mana("{1}"), life))),
    )

    /** One target, a player or a planeswalker (CR 115.4): the player discards, or the planeswalker's controller does. */
    @Test
    fun zarkhulsReturn() = card(
        name = "Zarkhul's Return",
        cost = "{X}{B}{R}",
        type = "Sorcery",
        text = "Zarkhul's Return deals X damage to target opponent or planeswalker. That player or that planeswalker's controller discards X cards.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("opponent or planeswalker", TargetKind.Either(planeswalker, PlayerFilter.Opponent), min = n(1), max = n(1))),
            steps = listOf(
                damage(Recipients(target = "opponent or planeswalker"), Value.Announced("X")),
                Step.Do(Effect.Discard(Players.Target("opponent or planeswalker"), Value.Announced("X"), ObjectFilter.Any)),
                Step.Do(Effect.Discard(Players.ControllerOf(target("opponent or planeswalker")), Value.Announced("X"), ObjectFilter.Any)),
            ),
            announced = listOf("X"),
        )),
    )

    /** A target player who lost life this turn (a player filter on this turn's events). */
    @Test
    fun pitRingmaster() = card(
        name = "Pit Ringmaster",
        cost = "{B}{R}",
        type = "Creature — Human Shaman",
        pt = "2/2",
        text = """
            {B}{R}: Target blocking creature gets -1/-1 until end of turn.
            {B}{R}: Target player who lost life this turn loses 1 life.
        """,
    ) parsesTo abilities(
        activated(cost(manaPart("{B}{R}")), pump(target("creature"), -1, -1),
            targets = listOf(slot("creature", all(ObjectFilter.HasStatus(Status.Blocking), creature)))),
        activated(cost(manaPart("{B}{R}")), Rtr.loseLife(n(1), Players.Target("player")),
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any))), min = n(1), max = n(1)))),
    )

    /** "Those players": the opponents dealt damage; each discards two cards at random (CR 701.9b). */
    @Test
    fun cranialRupture() = card(
        name = "Cranial Rupture",
        cost = "{3}{B}{R}",
        type = "Sorcery",
        text = "Cranial Rupture deals 2 damage to each opponent. Those players each discard two cards at random.",
    ) parsesTo abilities(
        spell(
            damage(Recipients(players = Players.Each(PlayerFilter.Opponent)), 2),
            Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), n(2), ObjectFilter.Any, random = true)),
        ),
    )

    /** "Any number of cards with that name" in the graveyard, hand and library (CR 701.23a); their owner shuffles. */
    @Test
    fun butcherySpectacle() = card(
        name = "Butchery Spectacle",
        cost = "{2}{B}{R}",
        type = "Sorcery",
        text = """
            This spell can't be countered.
            Choose a nonland card name. Search target opponent's graveyard, hand, and library for any number of cards with that name and exile them. Then that player shuffles.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CantBeCountered),
        spell(
            Step.Choose(Choice.CardName(nonland), bind = "name", chooser = Players.You),
            Step.Do(Effect.Search(Players.Target("opponent"), ObjectFilter.ChosenName("name"),
                max = Value.Count(Objects.Each(all(ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))),
                    ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Library)))))),
                zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library), bind = "found", searcher = Players.You)),
            Rtr.move(Objects.Bound("found"), Zone.Exile),
            Step.Do(Effect.Shuffle(Players.Target("opponent"))),
            targets = listOf(Rtr.targetOpponent),
        ),
    )

    /** "That player loses half their life, rounded up" (CR 107.1a): the upkeep's active player. */
    @Test
    fun mayhemJubilee() = card(
        name = "Mayhem Jubilee",
        cost = "{4}{B}{R}",
        type = "Enchantment",
        text = """
            Players can't gain life.
            At the beginning of each player's upkeep, that player loses half their life, rounded up.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantGainLife), affects = Affected.Players(Players.Each(PlayerFilter.Any))),
        triggered(Rtr.step(TurnStep.Upkeep, PlayerFilter.Any),
            Rtr.loseLife(Value.Half(Value.LifeOf(Players.EventPlayer), roundUp = true), Players.EventPlayer)),
    )

    /** Each creature is the source of its own damage, all dealt at once (CR 120.3). */
    @Test
    fun carnivalSigil() = card(
        name = "Carnival Sigil",
        cost = "{B}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Exile target player's graveyard.
            • Destroy target artifact.
            • Each creature deals 1 damage to its controller.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(Rtr.targetPlayer), steps = listOf(Rtr.move(Objects.Each(all(ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile)))),
            Mode(Instructions(targets = listOf(slot("artifact", artifact)), steps = listOf(Rtr.destroy(target("artifact"))))),
            Mode(Instructions(steps = listOf(Step.ForEach(Each.Objects(Objects.Each(creature)), bind = "source", together = true, steps = listOf(
                damage(Recipients(players = Players.ControllerOf(Objects.Bound("source"))), n(1), Objects.Bound("source"))))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun revelRingleader() = card(
        name = "Revel Ringleader",
        cost = "{4}{B}{R}",
        type = "Creature — Skeleton Warrior",
        pt = "3/1",
        text = """
            First strike
            Whenever this creature deals combat damage to a player, that player discards a card at random.
            {B}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(combatDamageToAPlayer(), Step.Do(Effect.Discard(Players.EventPlayer, n(1), ObjectFilter.Any, random = true))),
        activated(cost(manaPart("{B}")), regenerate(Objects.Self)),
    )

    /** "Enters with X counters, where X is …": counted as it enters (CR 614.1c). */
    @Test
    fun cryptspawnTerror() = card(
        name = "Cryptspawn Terror",
        cost = "{1}{B/R}{B/R}",
        type = "Creature — Horror",
        pt = "0/0",
        text = """
            Trample
            This creature enters with X +1/+1 counters on it, where X is the total life lost by your opponents this turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, life)))),
    )

    @Test
    fun drillfrenzy() = card(
        name = "Drillfrenzy",
        cost = "{1}{B}{R}",
        type = "Instant",
        text = "Target creature gets +4/-4 until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 4, -4), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun doomdrill() = card(
        name = "Doomdrill",
        cost = "{B}{R}",
        type = "Sorcery",
        text = "Destroy target creature or planeswalker.",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("permanent")), targets = listOf(slot("permanent", ObjectFilter.Or(listOf(creature, planeswalker))))),
    )
}
