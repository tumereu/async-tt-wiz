package arcanum.parser.sets.tla

import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 03: more earthbend (CR 701.66a): in modes, in a delayed trigger with its own target, as a Saga chapter
 * that also makes the land an Island, twice in one ability (two targets, CR 115.3), with X read from a creature that died (CR 603.10a), and
 * with an additional counter for a Lesson; the cards around it: an extra combat phase only land creatures attack in (CR 500.8), artifacts
 * that are lands (CR 305), a delayed transform, attack triggers by power.
 */
class TlaChunk03EarthbendMoreTest {
    @Test
    fun dunecallersStorm() = card(
        name = "Dunecallers' Storm",
        cost = "{3}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with power 4 or greater.
            • Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, power(Comparison.AtLeast, 4))))),
            mode(earthbend(3), targets = listOf(landSlot())),
        ),
    )

    // CR 701.20a (reveal), 701.66a
    @Test
    fun stoneGuardConditioning() = card(
        name = "Stone Guard Conditioning",
        cost = "{1}{B}",
        type = "Sorcery — Lesson",
        text = """
            Choose one —
            • Target opponent reveals their hand. You choose a nonland permanent card from it. That player discards that card.
            • Earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
    ) parsesTo abilities(
        modal(
            mode(
                doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
                Step.Choose(Choice.Objects(and(nonland, ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
                    CardType.Planeswalker).map { ObjectFilter.Type(it) })), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetOpponent)),
            mode(earthbend(2), targets = listOf(landSlot())),
        ),
    )

    // CR 603.7: a delayed trigger with its own target (chosen as it is put on the Stack), 701.66a
    @Test
    fun deadlyCrevasse() = card(
        name = "Deadly Crevasse",
        cost = "{1}{B}",
        type = "Instant",
        text = "Choose target creature. When that creature dies this turn, you earthbend 4. (Target land you control becomes a 0/0 creature with haste that's still a land. Put four +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)",
    ) parsesTo abilities(
        spell(doing(Effect.CreateDelayedTrigger(dies(ObjectFilter.Is(target("creature"))), Instructions(targets = listOf(landSlot()), steps = listOf(earthbend(4))),
            duration = Duration.EndOfTurn)), targets = listOf(slot("creature", creature))),
    )

    // CR 714.2b, 701.66a
    @Test
    fun theGrottoOfTwoSweethearts() = card(
        name = "The Grotto of Two Sweethearts",
        cost = "{3}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create two 1/1 white Ally creature tokens.
            II — Search your library for a Mountain or Cave card, reveal it, put it into your hand, then shuffle.
            III — Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
    ) parsesTo abilities(
        chapter(1, create(allyToken, n(2))),
        chapter(2, search(or(sub("Mountain"), sub("Cave")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        chapter(3, earthbend(3), targets = listOf(landSlot())),
    )

    // CR 701.66a: the earthbent land also becomes an Island (layer 4, for good); X counted as the chapter ability resolves
    @Test
    fun theTaleOfMaelis() = cardFaces(Layout.Transform,
        card(
            name = "The Tale of Maelis",
            cost = "{4}{G}{G}",
            type = "Enchantment — Saga",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I — Draw cards equal to the greatest power among creatures you control.
                II — Earthbend X, where X is the number of cards in your hand. That land becomes an Island in addition to its other types.
                III — Exile this Saga, then return it to the battlefield transformed under your control.
            """,
        ) to abilities(
            chapter(1, doing(Effect.Draw(Players.You, Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
            chapter(2, earthbend(count(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Island"))))), Affected.Objects(target("land")),
                    Duration.Indefinitely)),
                targets = listOf(landSlot())),
            exileAndReturnTransformed,
        ),
        card(
            name = "Herald Maelis",
            cost = null,
            type = "Legendary Creature — Avatar",
            text = """
                Lands you control have trample and hexproof.
                {T}: Add X mana of any one color, where X is the greatest power among creatures you control.
            """,
            pt = "5/4",
        ) to abilities(
            static(has(Keyword.Trample, Keyword.Hexproof), affects = Objects.Each(and(land, you))),
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest))),
                Players.You))),
        ),
    )

    // CR 603.7 (a delayed transform, 701.27), 701.66a
    @Test
    fun kiroAtTheFork() = cardFaces(Layout.Transform,
        card(
            name = "Kiro, at the Fork",
            cost = "{2}{G}{W}{U}",
            type = "Legendary Creature — Human Avatar Ally",
            text = """
                Flying
                When Kiro enters, look at the top five cards of your library. You may put a creature card with mana value 4 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
                When another creature you control leaves the battlefield, transform Kiro at the beginning of the next upkeep.
            """,
            pt = "3/3",
        ) to abilities(
            Keyword.Flying,
            triggered(entersSelf,
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
                Step.Choose(Choice.Objects(and(creature, manaValue(Comparison.AtMost, 4)), n(0), n(1), from = Objects.Bound("looked")), bind = "chosen",
                    chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield),
                doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")),
                    arcanum.api.Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
            triggered(on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield)),
                delayed(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), doing(Effect.Transform(Objects.Self)))),
        ),
        card(
            name = "Kiro, Fated Savior",
            cost = null,
            type = "Legendary Creature — Avatar Ally",
            text = """
                Flying
                Land creatures you control have vigilance.
                At the beginning of combat on your turn, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.Flying,
            static(has(Keyword.Vigilance), affects = Objects.Each(and(land, creature, you))),
            triggered(beginningOfCombatOnYourTurn, earthbend(2), targets = listOf(landSlot())),
        ),
    )

    // CR 701.66a: X is the power of the creature that died, as it last existed (CR 603.10a)
    @Test
    fun ironclanBountyHunters() = card(
        name = "Ironclan Bounty Hunters",
        cost = "{2}{B}{G}",
        type = "Creature — Human Mercenary",
        text = "Whenever a nonland creature you control dies, earthbend X, where X is that creature's power. (Target land you control becomes a 0/0 creature with haste that's still a land. Put X +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(dies(and(nonland, creature, you)), earthbend(powerOf(leftOne)), targets = listOf(landSlot())),
    )

    // CR 508.3d (one trigger for the declaration), 702.177a, 701.66a
    @Test
    fun gruelingToil() = card(
        name = "Grueling Toil",
        cost = "{1}{R}{G}",
        type = "Enchantment",
        text = """
            Whenever you attack a player with one or more creatures with power 4 or greater, draw a card.
            Exhaust — {4}: Earthbend 4. Activate only during your turn. (Target land you control becomes a 0/0 creature with haste that's still a land. Put four +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped. Activate each exhaust ability only once.)
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(and(creature, power(Comparison.AtLeast, 4), you), target = RecipientFilter(players = PlayerFilter.Any)), oneOrMore = true),
            draw(1)),
        activated(cost(mana("{4}")), earthbend(4), targets = listOf(landSlot()), rules = duringYourTurn, label = AbilityLabel.Exhaust),
    )

    // CR 500.8: an additional combat phase that only land creatures may attack in
    @Test
    fun odricUnleashed() = card(
        name = "Odric, Unleashed",
        cost = "{3}{R}{G}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            Trample
            When Odric enters, earthbend 4.
            Whenever Odric deals combat damage to a player, untap all lands you control. After this phase, there is an additional combat phase. Only land creatures can attack during that combat phase.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, earthbend(4), targets = listOf(landSlot())),
        triggered(combatDamageToAPlayer, untapIt(Objects.Each(and(land, you))), doing(Effect.ExtraCombat(onlyAttackers = and(land, creature)))),
    )

    // CR 701.66a: one target land for each earthbend (CR 115.3)
    @Test
    fun stoneGuardAgents() = card(
        name = "Stone Guard Agents",
        cost = "{3}{B}{G}",
        type = "Creature — Human Soldier",
        text = """
            When this creature enters, earthbend 1, then earthbend 1. (To earthbend 1, target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)
            Whenever this creature attacks, each opponent loses X life and you gain X life, where X is the number of creatures you control with +1/+1 counters on them.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf, earthbend(1), earthbend(1, slot = "second land"), targets = listOf(landSlot(), landSlot("second land"))),
        triggered(attacksSelf,
            doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), count(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))))),
            doing(Effect.GainLife(Players.You, count(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))))),
    )

    @Test
    fun stoneVillageRuffians() = card(
        name = "Stone Village Ruffians",
        cost = "{2}{B/G}",
        type = "Creature — Human Soldier Rogue",
        text = "When this creature dies, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)",
        pt = "3/1",
    ) parsesTo abilities(
        triggered(diesSelf, earthbend(2), targets = listOf(landSlot())),
    )

    // CR 118.12; CR 701.66a and an extra counter when the spell cast is a Lesson
    @Test
    fun miraHardheadedTeacher() = card(
        name = "Mira, Hardheaded Teacher",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            When Mira enters, you may discard a card. If you do, return target instant or sorcery card from your graveyard to your hand.
            Whenever you cast a spell, earthbend 1. If that spell is a Lesson, put an additional +1/+1 counter on that land. (Target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(move(target("card"), Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
        triggered(youCast(ObjectFilter.Any), earthbend(1),
            Step.If(Condition.ObjectMatches(Objects.EventObject(arcanum.api.EventRole.Object), lesson), then = listOf(counters(target("land")))),
            targets = listOf(landSlot())),
    )

    // CR 205.1b, 305: artifacts that are also lands (layer 4), 701.66a
    @Test
    fun miraTheFirstIronshaper() = card(
        name = "Mira, the First Ironshaper",
        cost = "{1}{R}{G}{W}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Nontoken artifacts you control are lands in addition to their other types. (They don't gain the ability to {T} for mana.)
            At the beginning of your end step, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        static(Mechanic.SetTypes(add = listOf(CardType.Land)), affects = Objects.Each(and(not(token), artifact, you))),
        triggered(yourEndStep, earthbend(2), targets = listOf(landSlot())),
    )
}
