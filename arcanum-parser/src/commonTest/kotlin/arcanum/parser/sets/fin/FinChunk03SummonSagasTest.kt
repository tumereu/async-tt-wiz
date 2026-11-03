package arcanum.parser.sets.fin

import arcanum.api.Affected
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 03: Saga creatures (CR 714: chapter abilities on a creature, sacrificed after the last chapter, CR 714.4), their
 * chapter text after flavor words (CR 207.2d); "when you next cast" delayed triggers (CR 603.7), a delayed trigger that lasts until end of
 * turn, a permission that works only during turns a lore counter was put on the Saga, an ability gained for good.
 */
class FinChunk03SummonSagasTest {
    private fun saga(name: String, cost: String, type: String, pt: String, text: String) = card(name = name, cost = cost, type = type, pt = pt, text = text)
    private val otherCreaturesYouControl = Objects.Each(and(another, creature, you))

    // CR 714.2c, 714.4
    @Test
    fun conjureSkyfireWyrm() = saga(
        name = "Conjure: Skyfire Wyrm",
        cost = "{9}",
        type = "Enchantment Creature — Saga Dragon",
        pt = "9/9",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II — Destroy up to one target nonland permanent.
            III — Draw two cards.
            IV — Mega Flare — This creature deals damage equal to the total mana value of other permanents you control to each opponent.
            Flying
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2), destroy(target("permanent")), targets = listOf(slot("permanent", nonlandPermanent, min = 0))),
        chapter(3, draw(2)),
        chapter(4, damage(Value.Of(NumericProperty.ManaValue, Objects.Each(and(another, permanentFilter, you)), Combine.Total), eachOpponent)),
        Keyword.Flying,
    )

    @Test
    fun conjureChocoboStampede() = saga(
        name = "Conjure: Featherfoot Stampede",
        cost = "{2}{W}",
        type = "Enchantment Creature — Saga Bird Moogle",
        pt = "3/3",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III, IV — Stampede! — Other creatures you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3, 4), pump(otherCreaturesYouControl, 1, 0)),
    )

    @Test
    fun conjureRoundtableKnights() = saga(
        name = "Conjure: Roundtable Paladins",
        cost = "{6}{W}{W}",
        type = "Enchantment Creature — Saga Knight",
        pt = "3/3",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after V.)
            I, II, III, IV — Create three 2/2 white Knight creature tokens.
            V — Ultimate End — Other creatures you control get +2/+2 until end of turn. Put an indestructible counter on each of them.
            Indestructible
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3, 4), create(knightToken(), n(3))),
        chapter(5, pump(otherCreaturesYouControl, 2, 2), counters(otherCreaturesYouControl, 1, indestructibleCounter)),
        Keyword.Indestructible,
    )

    @Test
    fun conjureGaleHarpy() = saga(
        name = "Conjure: Gale Harpy",
        cost = "{3}{W}",
        type = "Enchantment Creature — Saga Harpy",
        pt = "3/3",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Aerial Blast — This creature deals 4 damage to target tapped creature an opponent controls.
            II, III — Slipstream — Another target creature you control gets +1/+0 and gains flying until end of turn.
            Flying
        """,
    ) parsesTo abilities(
        chapter(1, damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", and(tapped, creature, opponents)))),
        chapters(listOf(2, 3), pump(target("creature"), 1, 0, has(Keyword.Flying)), targets = listOf(slot("creature", and(another, creature, you)))),
        Keyword.Flying,
    )

    /** "Until end of turn, whenever …": a delayed triggered ability that triggers every time until the turn ends (CR 603.7c). */
    @Test
    fun conjureDeepSerpent() = saga(
        name = "Conjure: Abyssal Wyrm",
        cost = "{4}{U}{U}",
        type = "Enchantment Creature — Saga Leviathan",
        pt = "6/6",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Return each creature that isn't a Kraken, Leviathan, Merfolk, Octopus, or Serpent to its owner's hand.
            II, III — Until end of turn, whenever a Kraken, Leviathan, Merfolk, Octopus, or Serpent attacks, draw a card.
            Ward {2}
        """,
    ) parsesTo abilities(
        chapter(1, move(Objects.Each(and(creature, not(sea))), Zone.Hand)),
        chapters(listOf(2, 3), doing(Effect.CreateDelayedTrigger(on(EventPattern.Attack(sea)), Instructions(steps = listOf(draw(1))), Duration.EndOfTurn,
            repeats = true))),
        Keyword.Ward(manaCost("{2}")),
    )

    @Test
    fun conjureRimeMaiden() = saga(
        name = "Conjure: Rime Maiden",
        cost = "{3}{U}{U}",
        type = "Enchantment Creature — Saga Elemental",
        pt = "4/5",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Heavenly Strike — Tap target creature an opponent controls. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            III — Diamond Dust — Draw a card for each tapped creature your opponents control.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2), tapIt(target("creature")), counters(target("creature"), 1, stun), targets = listOf(slot("creature", creatureOpponentControls))),
        chapter(3, doing(Effect.Draw(Players.You, count(and(tapped, creature, opponents))))),
    )

    // CR 701.21a: each opponent chooses the creature they sacrifice
    @Test
    fun conjureSorrowShade() = saga(
        name = "Conjure: Sorrow Shade",
        cost = "{4}{B}{B}",
        type = "Enchantment Creature — Saga Horror",
        pt = "4/4",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III — Pain — You draw a card and you lose 1 life.
            IV — Oblivion — Each opponent sacrifices a creature of their choice and loses 3 life.
            Menace
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3), draw(1), loseLife(1)),
        chapter(4, opponentsSacrifice(creature), loseLife(3, Players.Each(PlayerFilter.Opponent))),
        Keyword.Menace,
    )

    /** "This creature gains [ability]" with no duration: for as long as the Saga is on the battlefield (CR 611.2a). */
    @Test
    fun conjureSpectralRider() = saga(
        name = "Conjure: Spectral Rider",
        cost = "{4}{B}{B}",
        type = "Enchantment Creature — Saga Knight",
        pt = "5/3",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Gungnir — Destroy target creature an opponent controls.
            II — Zantetsuken — This creature gains "Whenever this creature deals combat damage to a player, that player loses the game."
            III — Hall of Sorrow — Draw two cards. Each player loses 2 life.
        """,
    ) parsesTo abilities(
        chapter(1, destroy(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
        chapter(2, doing(Effect.ApplyMechanics(listOf(has(triggered(combatDamageToAPlayer, doing(Effect.LoseGame(Players.EventPlayer))))),
            Affected.Objects(Objects.Self), Duration.Indefinitely))),
        chapter(3, draw(2), loseLife(2, Players.Each(PlayerFilter.Any))),
    )

    /** "During any turn you put a lore counter on this Saga, you may play that card." */
    @Test
    fun conjureValkyrieOfChains() = saga(
        name = "Conjure: Chainbound Valkyrie",
        cost = "{1}{R}",
        type = "Enchantment Creature — Saga Knight",
        pt = "2/1",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Chain — Exile the top card of your library. During any turn you put a lore counter on this Saga, you may play that card.
            II, III — Gestalt Mode — When you next cast a creature spell this turn, it gains haste until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You,
                condition = Condition.HappenedThisTurn(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), lore), atLeast = 1, measure = Measure.Events))),
                Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely))),
        chapters(listOf(2, 3), doing(Effect.CreateDelayedTrigger(youCast(and(creature, spellFilter)), Instructions(steps = listOf(
            doing(Effect.SpellGets(thatSpell, listOf(has(Keyword.Haste)), Duration.EndOfTurn)))), Duration.EndOfTurn))),
    )

    @Test
    fun conjureStormcaller() = saga(
        name = "Conjure: Stormcaller Sage",
        cost = "{2}{R}{R}",
        type = "Enchantment Creature — Saga Wizard",
        pt = "3/3",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Judgment Bolt — This creature deals damage equal to the number of noncreature, nonland cards in your graveyard to target creature an opponent controls.
            II, III — Wizards you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, damage(count(inYourGraveyard(noncreature, nonland)), toObjects(target("creature"))), targets = listOf(slot("creature", creatureOpponentControls))),
        chapters(listOf(2, 3), pump(Objects.Each(and(sub("Wizard"), you)), 1, 0)),
    )

    /** "When you next cast an instant or sorcery spell this turn, copy it" (CR 603.7c, 707.10). */
    @Test
    fun conjureThreeHeadedHound() = saga(
        name = "Conjure: Threefold Hound",
        cost = "{2}{R}{R}",
        type = "Enchantment Creature — Saga Dog",
        pt = "3/3",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            II — Double — When you next cast an instant or sorcery spell this turn, copy it. You may choose new targets for the copy.
            III — Triple — When you next cast an instant or sorcery spell this turn, copy it twice. You may choose new targets for the copies.
        """,
    ) parsesTo abilities(
        chapter(1, surveil(1)),
        chapter(2, nextInstantOrSorcery(doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)))),
        chapter(3, nextInstantOrSorcery(Step.Repeat(n(2), listOf(doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)))))),
    )

    @Test
    fun conjureBlazeImp() = saga(
        name = "Conjure: Cinder Imp",
        cost = "{2}{R}",
        type = "Enchantment Creature — Saga Demon",
        pt = "3/2",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II — You may discard a card. If you do, draw a card.
            III, IV — Add {R}.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2), Step.Pay(cost(arcanum.api.CostPart.Discard(arcanum.api.CostObjects.Choose(cardFilter, n(1)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1))),
        chapters(listOf(3, 4), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{R}")!!)), Players.You))),
    )

    @Test
    fun conjurePlumpChocobo() = saga(
        name = "Conjure: Rotund Strider",
        cost = "{4}{G}",
        type = "Enchantment Creature — Saga Bird",
        pt = "4/4",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Wark — Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
            II, III, IV — Kerplunk — Creatures you control gain trample until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, create(birdToken)),
        chapters(listOf(2, 3, 4), gains(Objects.Each(creatureYouControl), Keyword.Trample)),
    )

    /** "that creature enters with an additional +1/+1 counter on it": the spell gets a replacement of how it enters (CR 614.1c). */
    @Test
    fun conjureMoonWolf() = saga(
        name = "Conjure: Moonhowl Wolf",
        cost = "{2}{G}",
        type = "Enchantment Creature — Saga Wolf",
        pt = "3/2",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Crescent Fang — Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            II — Heavenward Howl — When you next cast a creature spell this turn, that creature enters with an additional +1/+1 counter on it.
            III — Ecliptic Growl — Draw a card if you control the creature with the greatest power or tied for the greatest power.
        """,
    ) parsesTo abilities(
        chapter(1, *rampTapped.toTypedArray()),
        chapter(2, doing(Effect.CreateDelayedTrigger(youCast(and(creature, spellFilter)), Instructions(steps = listOf(
            doing(Effect.SpellGets(thatSpell, listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))))))), Duration.EndOfTurn))),
        chapter(3, ifThen(Condition.Compare(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest), Comparison.AtLeast,
            Value.Of(NumericProperty.Power, Objects.Each(creature), Combine.Greatest)), draw(1))),
    )

    @Test
    fun conjureEarthGiant() = saga(
        name = "Conjure: Bedrock Giant",
        cost = "{3}{G}{G}",
        type = "Enchantment Creature — Saga Giant",
        pt = "7/7",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Mill five cards.
            II — Return all land cards from your graveyard to the battlefield tapped.
            III — Until end of turn, another target creature you control gains trample and gets +X/+X, where X is the number of lands you control.
            Reach, trample
        """,
    ) parsesTo abilities(
        chapter(1, mill(5)),
        chapter(2, move(Objects.Each(inYourGraveyard(land)), Zone.Battlefield, tapped = true)),
        chapter(3, untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(count(and(land, you)), count(and(land, you))), has(Keyword.Trample)),
            targets = listOf(slot("creature", and(another, creature, you)))),
        Keyword.Reach,
        Keyword.Trample,
    )

    private val sea = or(sub("Kraken"), sub("Leviathan"), sub("Merfolk"), sub("Octopus"), sub("Serpent"))

    private fun nextInstantOrSorcery(vararg steps: Step) = doing(Effect.CreateDelayedTrigger(youCast(and(or(instant, sorcery), spellFilter)),
        Instructions(steps = steps.toList()), Duration.EndOfTurn))

    /** "Each opponent sacrifices a [kind] of their choice" (CR 701.21a). */
    private fun opponentsSacrifice(kind: arcanum.api.ObjectFilter): Step = Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent",
        steps = listOf(
            Step.Choose(arcanum.api.Choice.Objects(and(kind, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Bound("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ), together = true)
}
