package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** The FDN counterspells, destroy effects and discard effects (chunk 25). The invented names carry the oracle text of the real cards. */
class FdnChunk25CounterDestroyDiscardTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val counterTarget = Step.Do(Effect.Counter(target("spell")))
    private fun destroyTarget(name: String) = destroy(target(name))

    // CR 701.6a, 114.1: a creature spell is the only legal target
    @Test
    fun essenceDissolve() = card(
        name = "Essence Dissolve",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target creature spell.",
    ) parsesTo abilities(
        spell(counterTarget, targets = listOf(slot("spell", and(creature, spellFilter)))),
    )

    // CR 105.1, 202.2: a spell is red or green by its colors
    @Test
    fun flashchill() = card(
        name = "Flashchill",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target red or green spell.",
    ) parsesTo abilities(
        spell(counterTarget, targets = listOf(slot("spell", and(or(ObjectFilter.OfColor(Color.Red), ObjectFilter.OfColor(Color.Green)), spellFilter)))),
    )

    // CR 701.6a: any spell that is not a creature spell (lands are never spells)
    @Test
    fun nullifySpell() = card(
        name = "Nullify Spell",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target noncreature spell.",
    ) parsesTo abilities(
        spell(counterTarget, targets = listOf(slot("spell", and(noncreatureSpell, spellFilter)))),
    )

    // CR 701.6a, 608.2c: counter, then draw and discard in order
    @Test
    fun rebuff() = card(
        name = "Rebuff",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = "Counter target spell. Draw a card, then discard a card.",
    ) parsesTo abilities(
        spell(counterTarget, draw(1), discard(1), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 603.1, 701.6a: an artifact spell or a creature spell
    @Test
    fun hungryGreatshark() = card(
        name = "Hungry Greatshark",
        cost = "{3}{U}{U}",
        type = "Creature — Shark",
        pt = "5/4",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            When this creature enters, counter target artifact or creature spell.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        whenThisEnters(counterTarget, targets = listOf(slot("spell", and(or(artifact, creature), spellFilter)))),
    )

    // CR 700.2: a mode is chosen as the spell is cast; the second mode targets a card in a graveyard
    @Test
    fun deadlyScheme() = card(
        name = "Deadly Scheme",
        cost = "{3}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature or planeswalker.
            • Return target Zombie creature card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroyTarget("permanent"), targets = listOf(slot("permanent", or(creature, planeswalker)))),
            mode(move(target("card"), Zone.Battlefield, tapped = true),
                targets = listOf(slot("card", inYourGraveyard(sub("Zombie"), creature)))),
        ))),
    )

    // CR 701.8a, 105.1: green or white
    @Test
    fun deathbrand() = card(
        name = "Deathbrand",
        cost = "{B}",
        type = "Sorcery",
        text = "Destroy target green or white creature.",
    ) parsesTo abilities(
        spell(destroyTarget("permanent"), targets = listOf(slot("permanent", and(or(ObjectFilter.OfColor(Color.Green), ObjectFilter.OfColor(Color.White)), creature)))),
    )

    // CR 701.8a: "with power 4 or greater" belongs to the creature option only
    @Test
    fun makeYourStand() = card(
        name = "Make Your Stand",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target artifact, enchantment, or creature with power 4 or greater.",
    ) parsesTo abilities(
        spell(destroyTarget("permanent"), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, power(Comparison.AtLeast, 4)))))),
    )

    // CR 701.8a, 603.1: only a nonland permanent an opponent controls
    @Test
    fun meteorGiant() = card(
        name = "Meteor Giant",
        cost = "{7}",
        type = "Artifact Creature — Golem",
        pt = "3/3",
        text = "When this creature enters, destroy target nonland permanent an opponent controls.",
    ) parsesTo abilities(
        whenThisEnters(destroyTarget("permanent"), targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
    )

    // CR 701.8a
    @Test
    fun mortifyPair() = card(
        name = "Mortify Pair",
        cost = "{1}{W}{B}",
        type = "Instant",
        text = "Destroy target creature or enchantment.",
    ) parsesTo abilities(
        spell(destroyTarget("permanent"), targets = listOf(slot("permanent", or(creature, enchantment)))),
    )

    // CR 701.8a, 111.2: the token's creator is the destroyed permanent's controller, as it last existed (608.2h)
    @Test
    fun strokeOfDusk() = card(
        name = "Stroke of Dusk",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target nonland permanent. Its controller creates a 1/1 white Human creature token.",
    ) parsesTo abilities(
        spell(
            destroyTarget("permanent"),
            Step.Do(Effect.CreateToken(creatureToken("Human", listOf(Color.White), 1, 1), n(1),
                Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 510.2, 603.1: the "you may" is made on resolution, the player is the damaged one
    @Test
    fun trygonHunter() = card(
        name = "Trygon Hunter",
        cost = "{1}{G}{U}",
        type = "Creature — Beast",
        pt = "2/3",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, you may destroy target artifact or enchantment that player controls.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, Step.May(listOf(destroyTarget("permanent")), Players.You),
            targets = listOf(slot("permanent", and(or(artifact, enchantment), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer)))))),
    )

    // CR 603.4, 701.9a: an intervening "if"; each opponent discards
    @Test
    fun bloodtitheGatherer() = card(
        name = "Bloodtithe Gatherer",
        cost = "{4}{B}",
        type = "Creature — Vampire Noble",
        pt = "3/4",
        text = """
            Flying
            When this creature enters, if an opponent lost life this turn, each opponent discards a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(discard(1, Players.Each(PlayerFilter.Opponent)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), 1, Measure.Events)),
    )

    // CR 701.9a, 121.1: each player discards their whole hand, then draws seven
    @Test
    fun dragonWizard() = card(
        name = "Dragon Wizard",
        cost = "{5}{R}{R}",
        type = "Creature — Dragon Wizard",
        pt = "5/5",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, each player discards their hand, then draws seven cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer,
            Step.Do(Effect.Discard(Players.Each(PlayerFilter.Any), n(1), ObjectFilter.Any,
                objects = Objects.Each(inZone(Zone.Hand)))),
            draw(7, Players.Each(PlayerFilter.Any))),
    )

    // CR 700.2, 701.9a: the opponent chooses what to discard; the pump applies to the opponents' creatures only
    @Test
    fun seekersError() = card(
        name = "Seeker's Error",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target opponent discards two cards.
            • Creatures your opponents control get -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(discard(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
            mode(pump(Objects.Each(and(creature, opponents)), -1, -1)),
        ))),
    )
}
