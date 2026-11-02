package arcanum.parser.sets.dgm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.anyTime
import arcanum.parser.sets.gtc.attacksSelf
import arcanum.parser.sets.gtc.combatDamageToPlayer
import arcanum.parser.sets.gtc.costOf
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.manaPart
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.p1p1
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.steps
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.untilEndOfTurn
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Dragon's Maze chunk 06: multicoloured cards, Jelenn Sentinel to Restore the Calm. Other attacking creatures, a cost
 * removing a counter from any creature you control, an Aura locking a creature and returning itself, exiling your
 * creatures and returning them at the next combat (CR 603.7), attacking alone (CR 508.1c) and an unblocked attack that
 * sets a life total (CR 119.5), playing from the top of the library revealed (CR 401.5) and copying spells cast from
 * there, a draw replacement except the first draw of the draw step (CR 614.11), a delayed mana trigger, entering as a
 * copy with an added ability (CR 707.9b), "can't be regenerated" (CR 701.19c), a planeswalker with two different targets
 * and coin flips for extra turns (CR 705, 500.7), searching hidden zones for same-named cards, and creatures that dealt
 * damage this turn.
 */
class DgmChunk06MulticoloredBTest {
    private val instantOrSorcery = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
    private val spellSlot = slot("spell", ObjectFilter.Category(ObjectCategory.Spell))
    private val permanent = ObjectFilter.Category(ObjectCategory.Permanent)

    @Test
    fun jelennSentinel() = card(
        name = "Jelenn Sentinel",
        cost = "{3}{W}{U}",
        type = "Creature — Sphinx",
        pt = "1/5",
        text = """
            Flying, vigilance
            Whenever this creature attacks, other attacking creatures get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Vigilance,
        Ability.Triggered(attacksSelf, Instructions(steps = listOf(pump(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
            ObjectFilter.HasStatus(Status.Attacking), creature))), 1, 1)))),
    )

    // CR 118.3: the counter is removed from a creature you control as the cost
    @Test
    fun korozdaBasilisk() = card(
        name = "Korozda Basilisk",
        cost = "{3}{B}{G}",
        type = "Creature — Gorgon",
        pt = "2/5",
        text = """
            Deathtouch
            {2}, Remove a +1/+1 counter from a creature you control: Target creature gets -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Activated(costOf(manaPart("{2}"), CostPart.RemoveCounters(p1p1, n(1), CostObjects.Choose(creatureYouControl, count = n(1)))),
            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(Objects.Target("creature"), -1, -1))), rules = anyTime),
    )

    // CR 602.5: its activated abilities, mana abilities too
    @Test
    fun krasisIncubator() = card(
        name = "Krasis Incubator",
        cost = "{2}{G}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block, and its activated abilities can't be activated.
            {1}{G}{U}, Return this Aura to its owner's hand: Put two +1/+1 counters on enchanted creature.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true)),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Ability.Activated(costOf(manaPart("{1}{G}{U}"), CostPart.ReturnToHand(CostObjects.Self)),
            steps(Step.Do(Effect.PutCounters(Objects.AttachedTo(Objects.Self), p1p1, n(2)))), rules = anyTime),
    )

    // CR 603.7: the delayed trigger returns the exiled cards, new objects that gain haste (CR 400.7)
    @Test
    fun legionsResolve() {
        val red = ObjectFilter.OfColor(arcanum.api.Color.Red)
        val white = ObjectFilter.OfColor(arcanum.api.Color.White)
        card(
            name = "Legion's Resolve",
            cost = "{R}{W}",
            type = "Enchantment",
            text = """
                Red creatures you control get +1/+0.
                White creatures you control get +0/+1.
                {R}{W}, Exile this enchantment: Exile all creatures you control. At the beginning of the next combat, return those cards to the battlefield under their owner's control and those creatures gain haste until end of turn.
            """,
        ) parsesTo abilities(
            Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(0))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(red, creature, you))))),
            Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(0), n(1))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(white, creature, you))))),
            Ability.Activated(costOf(manaPart("{R}{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)), steps(
                Step.Do(Effect.Move(Objects.Each(creatureYouControl), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any)), steps(
                    Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))), postBind = "returned")),
                    untilEndOfTurn(Objects.Bound("returned"), Mechanic.GainAbilities(listOf(Keyword.Haste))),
                ))),
            ), rules = anyTime),
        )
    }

    // CR 508.1c: can only attack alone; CR 509.1h, 119.5: the attacked player's life total becomes 1; CR 510.1
    @Test
    fun mistressOfCruelties() = card(
        name = "Mistress of Cruelties",
        cost = "{3}{B}{R}",
        type = "Creature — Demon",
        pt = "1/4",
        text = """
            First strike, deathtouch
            This creature can only attack alone.
            Whenever this creature attacks a player and isn't blocked, that player's life total becomes 1. This creature assigns no combat damage this combat.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike, Keyword.Deathtouch,
        Ability.Static(listOf(Mechanic.CanOnlyAttackAlone), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(Trigger.On(EventPattern.AttacksUnblocked(ObjectFilter.Is(Objects.Self), target = RecipientFilter(players = PlayerFilter.Any))), steps(
            Step.Do(Effect.SetLife(Players.EventPlayer, n(1))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.AssignsNoCombatDamage), Affected.Objects(Objects.Self), Duration.EndOfCombat)),
        )),
    )

    @Test
    fun maulOfTheObzedat() = card(
        name = "Maul of the Obzedat",
        cost = "{3}{W}{B}",
        type = "Creature — Thrull",
        pt = "3/3",
        text = "Sacrifice a creature: Creatures you control get +1/+1 until end of turn.",
    ) parsesTo abilities(
        Ability.Activated(costOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1)))), Instructions(steps = listOf(pump(Objects.Each(creatureYouControl), 1, 1))), rules = anyTime),
    )

    // CR 401.5: the top card revealed; CR 601.3: cast from the library top; CR 707.10: the copy, new targets allowed
    @Test
    fun melokIzzetProdigy() = card(
        name = "Melok, Izzet Prodigy",
        cost = "{4}{U}{R}",
        type = "Legendary Creature — Weird Wizard",
        pt = "2/4",
        text = """
            Play with the top card of your library revealed.
            You may cast instant and sorcery spells from the top of your library.
            Whenever you cast an instant or sorcery spell from your library, copy it. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), reveal = true)), affects = Affected.Players(Players.You)),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(instantOrSorcery, ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
        Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.And(listOf(instantOrSorcery, ObjectFilter.Category(ObjectCategory.Spell))), by = PlayerFilter.You, from = Zone.Library)),
            steps(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))),
    )

    // CR 701.20: revealed until four lands; all those cards go to the graveyard
    @Test
    fun mirkoVaskMindSiphon() = card(
        name = "Mirko Vask, Mind Siphon",
        cost = "{3}{U}{B}",
        type = "Legendary Creature — Vampire",
        pt = "2/4",
        text = """
            Flying
            Whenever Mirko Vask deals combat damage to a player, that player reveals cards from the top of their library until they reveal four land cards, then puts those cards into their graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(combatDamageToPlayer, steps(
            Step.Do(Effect.RevealUntil(land, Players.EventPlayer, n(4), reveal = true, foundBind = "found", restBind = "rest")),
            Step.Do(Effect.Move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Destination(Zone.Graveyard))),
        )),
    )

    // CR 608.2h: the returned card's power as it is in the hand
    @Test
    fun morgueBlast() = card(
        name = "Morgue Blast",
        cost = "{4}{B}{R}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to your hand. Morgue Blast deals damage to any target equal to the power of the card returned this way.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), anyTarget),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))),
                Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Of(NumericProperty.Power, Objects.Target("card"), Combine.Single), source = Objects.Self)),
            ),
        )),
    )

    // CR 702.3b: can attack as though it didn't have defender
    @Test
    fun nivixBrute() = card(
        name = "Nivix Brute",
        cost = "{1}{U}{R}",
        type = "Creature — Cyclops",
        pt = "1/4",
        text = """
            Defender
            Whenever you cast an instant or sorcery spell, this creature gets +3/+0 until end of turn and can attack this turn as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Ability.Triggered(Trigger.On(EventPattern.Cast(instantOrSorcery, by = PlayerFilter.You)),
            steps(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(n(3), n(0)), Mechanic.CanAttackDespiteDefender))),
    )

    // CR 614.11, 504.1: every draw but the draw step's first is replaced
    @Test
    fun notionPilferer() = card(
        name = "Notion Pilferer",
        cost = "{2}{U}{B}",
        type = "Creature — Human Rogue",
        pt = "3/1",
        text = """
            Flash
            If an opponent would draw a card except the first one they draw in each of their draw steps, instead that player skips that draw and you draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.Opponent, exceptFirstIn = TurnStep.Draw),
            instead = steps(Step.Do(Effect.Draw(Players.You, n(1)))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 110.4a: a permanent card
    @Test
    fun obzedatsGift() = card(
        name = "Obzedat's Gift",
        cost = "{3}{W}{B}",
        type = "Sorcery",
        text = "Return target permanent card from your graveyard to the battlefield.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("card", ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) }),
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield)))),
        )),
    )

    @Test
    fun pilferedSchemes() = card(
        name = "Pilfered Schemes",
        cost = "{1}{U}{B}",
        type = "Sorcery",
        text = "Target player mills two cards. Draw two cards.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(targetPlayer), steps = listOf(
            Step.Do(Effect.Mill(Players.Target("player"), n(2))), Step.Do(Effect.Draw(Players.You, n(2))),
        ))),
    )

    // CR 603.7: a delayed trigger at your next precombat main phase; CR 608.2h: the countered spell's mana value
    @Test
    fun plasmSeizure() = card(
        name = "Plasm Seizure",
        cost = "{G}{G}{U}{U}",
        type = "Instant",
        text = "Counter target spell. At the beginning of your next first main phase, add X mana in any combination of colors, where X is that spell's mana value.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(spellSlot), steps = listOf(
            Step.Do(Effect.Counter(Objects.Target("spell"))),
            Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You)), steps(
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Of(NumericProperty.ManaValue, Objects.Target("spell"), Combine.Single), combination = true)), Players.You)),
            ))),
        ))),
    )

    // CR 707.9b: the added ability is part of the copy, so its token copies have it too; CR 603.4
    @Test
    fun progenitorMime() = card(
        name = "Progenitor Mime",
        cost = "{4}{G}{U}",
        type = "Creature — Shapeshifter",
        pt = "0/0",
        text = "You may have this creature enter as a copy of any creature on the battlefield, except it has \"At the beginning of your upkeep, if this creature isn't a token, create a token that's a copy of this creature.\"",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersAsCopy(creature, except = listOf(CopyException.AddAbilities(listOf(
                Ability.Triggered(arcanum.parser.sets.gtc.yourUpkeep, steps(Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You))),
                    interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.Category(ObjectCategory.Token)))),
            ))), optional = true),
        ))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 701.19c: it can't be regenerated
    @Test
    fun putridRot() = card(
        name = "Putrid Rot",
        cost = "{1}{B}{G}",
        type = "Instant",
        text = "Destroy target artifact or creature. It can't be regenerated.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature)))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent"), noRegeneration = true))),
        )),
    )

    // CR 606: loyalty abilities; CR 115.3: "another target permanent"; CR 705: coins, heads win (CR 500.7: extra turns)
    @Test
    fun ralZarov() = card(
        name = "Ral Zarov",
        cost = "{2}{U}{R}",
        type = "Legendary Planeswalker — Ral",
        loyalty = "4",
        text = """
            [+1]: Tap target permanent, then untap another target permanent.
            [−2]: Ral Zarov deals 3 damage to any target.
            [−7]: Flip five coins. Take an extra turn after this one for each coin that comes up heads.
        """,
    ) parsesTo abilities(
        Ability.Activated(costOf(CostPart.Loyalty(n(1))), Instructions(
            targets = listOf(slot("permanent", permanent), TargetSlot("second permanent", TargetKind.Objects(permanent), min = n(1), max = n(1), distinctFrom = listOf("permanent"))),
            steps = listOf(Step.Do(Effect.Tap(Objects.Target("permanent"))), Step.Do(Effect.Untap(Objects.Target("second permanent")))),
        ), rules = anyTime),
        Ability.Activated(costOf(CostPart.Loyalty(n(-2))), Instructions(targets = listOf(anyTarget),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(3), source = Objects.Self)))), rules = anyTime),
        Ability.Activated(costOf(CostPart.Loyalty(n(-7))), steps(Step.Repeat(n(5), listOf(
            Step.Do(Effect.FlipCoin(Players.You, bind = "flip")),
            Step.If(Condition.WonFlip("flip"), then = listOf(Step.Do(Effect.ExtraTurn(Players.You)))),
        ))), rules = anyTime),
    )

    // CR 701.20, 701.23: the exiled cards' names are searched for in three zones, then the library is shuffled
    @Test
    fun reapWisdom() {
        val opponent = Players.Target("opponent")
        val owned = ObjectFilter.OwnedBy(PlayerFilter.Is(opponent))
        card(
            name = "Reap Wisdom",
            cost = "{X}{2}{U}{B}",
            type = "Sorcery",
            text = "Target opponent reveals their hand. You choose up to X nonland cards from it and exile them. For each card exiled this way, search that player's graveyard, hand, and library for any number of cards with the same name as that card and exile them. Then that player shuffles.",
        ) parsesTo abilities(
            Ability.Spell(Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(
                    Step.Do(Effect.Reveal(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), owned))), bind = "revealed")),
                    Step.Choose(Choice.Objects(ObjectFilter.Not(land), n(0), Value.Announced("X"), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Search(opponent, ObjectFilter.SameNameAs(Objects.Bound("exiled")),
                        max = Value.Count(Objects.Each(ObjectFilter.And(listOf(owned, ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.InZone(Zone.Hand),
                            ObjectFilter.InZone(Zone.Library))))))),
                        zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library), bind = "found", searcher = Players.You)),
                    Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Exile))),
                    Step.Do(Effect.Shuffle(opponent)),
                ),
                announced = listOf("X"),
            )),
        )
    }

    // CR 101.2: its controller can't cast spells this turn
    @Test
    fun renderMute() = card(
        name = "Render Mute",
        cost = "{W}{U}{U}",
        type = "Instant",
        text = "Counter target spell. Its controller can't cast spells this turn.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(spellSlot), steps = listOf(
            Step.Do(Effect.Counter(Objects.Target("spell"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(ObjectFilter.Category(ObjectCategory.Spell))),
                Affected.Players(Players.ControllerOf(Objects.Target("spell"))), Duration.EndOfTurn)),
        ))),
    )

    // Each creature that was the source of damage this turn
    @Test
    fun restoreTheCalm() = card(
        name = "Restore the Calm",
        cost = "{1}{W}{U}",
        type = "Instant",
        text = "Return each creature that dealt damage this turn to its owner's hand.",
    ) parsesTo abilities(
        Ability.Spell(steps(Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(creature,
            ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Source)))), Destination(Zone.Hand))))),
    )
}
