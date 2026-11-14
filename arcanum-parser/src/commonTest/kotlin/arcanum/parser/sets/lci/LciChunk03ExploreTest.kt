package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.KeywordActionKind
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 03: explore (CR 701.44), Map tokens (CR 111.10s) and the cards around them. */
class LciChunk03ExploreTest {
    private val exploreReminder = "(Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this " +
        "creature, then put the card back or put it into your graveyard.)"
    private val mapReminder = "(It's an artifact with \"{1}, {T}, Sacrifice this token: Target creature you control explores. Activate only as a sorcery.\")"
    private val map = TokenSpec.Predefined("Map")
    private fun createMap(count: Int = 1, controller: Players = Players.You) = Step.Do(Effect.CreateToken(map, n(count), controller))
    private fun explore(objects: Objects) = Step.Do(Effect.Action(KeywordAction.Explore(objects)))
    private fun exploresTwice(objects: Objects) = Step.Repeat(n(2), listOf(explore(objects)))
    private val creatureYouControlSlot = slot("creature", creatureYouControl)

    // CR 701.44a, 702.21a; CR 608.2h: its power as the ability resolves, after exploring
    @Test
    fun vesperineLifewarden() = card(
        name = "Vesperine Lifewarden",
        cost = "{W}{B}",
        type = "Legendary Creature — Vampire Scout",
        text = """
            Ward—Pay 3 life.
            Whenever you gain life, Vesperine Lifewarden explores. Then destroy all other creatures if its power is exactly 20. (To have this creature explore, reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Ward(cost(CostPart.PayLife(n(3)))),
        triggered(on(EventPattern.GainLife(PlayerFilter.You)),
            explore(Objects.Self),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.Power(Comparison.Equal, n(20))), destroy(Objects.Each(and(another, creature))))),
    )

    // CR 608.2h: whether it was tapped is its last known information
    @Test
    fun brinyMisstep() = card(
        name = "Briny Misstep",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target creature to its owner's hand. If it was tapped, create a Map token. $mapReminder",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(target("creature"), Destination(Zone.Hand), preBind = "returned")),
            ifThen(Condition.ObjectMatches(Objects.Bound("returned"), tapped), createMap()),
            targets = listOf(slot("creature", creature))),
    )

    // CR 111.10s
    @Test
    fun chartkeepersHelper() = card(
        name = "Chartkeeper's Helper",
        cost = "{3}",
        type = "Artifact Creature — Gnome",
        text = "When this creature enters, create a Map token. $mapReminder",
        pt = "2/1",
    ) parsesTo abilities(triggered(entersSelf, createMap()))

    // CR 701.44a; CR 707.2, 613.2a: a copy until end of turn, of another creature chosen as it resolves (not targeted)
    @Test
    fun fathomdeepMimic() = card(
        name = "Fathomdeep Mimic",
        cost = "{2}{G}{U}",
        type = "Creature — Merfolk Spirit",
        text = "At the beginning of combat on your turn, this creature explores. Then you may have it become a copy of another creature you control until end of turn. " +
            "(To have this creature explore, reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            explore(Objects.Self),
            Step.Choose(Choice.Objects(and(another, creature, you), n(0), n(1)), bind = "copied", chooser = Players.You),
            ifThen(Condition.NonEmpty("copied"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("copied"))), Affected.Objects(Objects.Self), Duration.EndOfTurn)))),
    )

    // CR 701.44a, b: two explores, one after the other
    @Test
    fun fossilRouse() = card(
        name = "Fossil Rouse",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to the battlefield. That creature explores, then it explores again. (Reveal the top card of your library. " +
            "Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard. Then repeat this process.)",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield, postBind = "returned"),
            exploresTwice(Objects.Bound("returned")),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 601.2b, f: an additional cost
    @Test
    fun zealousTithe() = card(
        name = "Zealous Tithe",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Draw two cards and create a Map token. $mapReminder
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature))))),
        spell(draw(2), createMap()),
    )

    // CR 111.2: the destroyed permanent's controller creates the tokens (as it last existed, CR 608.2h)
    @Test
    fun loseTheTrail() = card(
        name = "Lose the Trail",
        cost = "{1}{W}",
        type = "Instant",
        text = "Destroy target creature, enchantment, or planeswalker. Its controller creates two Map tokens. (They're artifacts with \"{1}, {T}, Sacrifice this token: " +
            "Target creature you control explores. Activate only as a sorcery.\")",
    ) parsesTo abilities(
        spell(
            destroy(target("permanent")),
            createMap(2, Players.ControllerOf(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, enchantment, ObjectFilter.Type(arcanum.api.CardType.Planeswalker))))),
    )

    // CR 107.3m: X in its enters trigger is the X paid for it
    @Test
    fun jadeglowDelver() = card(
        name = "Jadeglow Delver",
        cost = "{X}{G}",
        type = "Creature — Merfolk Scout",
        text = "When this creature enters, it explores X times. (To have it explore, reveal the top card of your library. Put that card into your hand if it's a land. " +
            "Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard.)",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Repeat(Value.CastX(Objects.Self), listOf(explore(Objects.Self)))),
    )

    // CR 701.44a: what it explored is the card revealed
    @Test
    fun nicoruTidePilot() = card(
        name = "Nicoru, Tide Pilot",
        cost = "{G}{U}",
        type = "Legendary Creature — Merfolk Scout",
        text = """
            Whenever a creature you control explores a land card, you may put a land card from your hand onto the battlefield tapped.
            Whenever a creature you control explores a nonland card, put a +1/+1 counter on Nicoru.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.KeywordActionDone(KeywordActionKind.Explore, objects = creatureYouControl, revealed = land)),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true)),
        triggered(on(EventPattern.KeywordActionDone(KeywordActionKind.Explore, objects = creatureYouControl, revealed = nonland)), counters(Objects.Self)),
    )

    // CR 700.2
    @Test
    fun pastTheBrink() = card(
        name = "Past the Brink",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Destroy target artifact or enchantment.
            • Target creature you control explores, then it explores again. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard. Then repeat this process.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            mode(exploresTwice(target("creature")), targets = listOf(creatureYouControlSlot))))),
    )

    // CR 111.10s; CR 603.2: either event
    @Test
    fun wardenOfTheSunkenPlaza() = card(
        name = "Warden of the Sunken Plaza",
        cost = "{2}{G}",
        type = "Creature — Merfolk Warrior Scout",
        text = """
            Vigilance
            Whenever this creature enters or attacks, create a Map token. $mapReminder
        """,
        pt = "3/4",
    ) parsesTo abilities(Keyword.Vigilance, triggered(entersOrAttacksSelf, createMap()))

    // CR 111.10s
    @Test
    fun lookoutSiren() = card(
        name = "Lookout Siren",
        cost = "{U}",
        type = "Creature — Siren Pirate",
        text = """
            Flying
            When this creature enters, create a Map token. $mapReminder
        """,
        pt = "1/1",
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, createMap()))

    // CR 614.1a: a replacement of each explore; CR 614.5: the explore it brings isn't replaced again by it
    @Test
    fun windingPaths() = cardFaces(Layout.Transform,
        card(
            name = "Winding Paths",
            cost = "{G}",
            type = "Enchantment",
            text = """
                If a creature you control would explore, instead you scry 1, then that creature explores.
                When this enchantment enters, target creature you control explores.
                When a land you control enters, if you control seven or more lands, transform this enchantment.
            """,
        ) to abilities(
            static(Mechanic.Replacement(EventPattern.Explore(creatureYouControl),
                instead = Instructions(steps = listOf(scry(1), explore(Objects.EventObject(EventRole.Object)))))),
            triggered(entersSelf, explore(target("creature")), targets = listOf(creatureYouControlSlot)),
            triggered(enters(and(land, you)), transformSelf, interveningIf = Condition.Exists(and(land, you), atLeast = 7)),
        ),
        card(
            name = "Sporeling Grotto",
            cost = null,
            type = "Land — Cave",
            text = """
                (Transforms from Winding Paths.)
                {T}: Add {G}.
                {3}{G}, {T}: Look at the top four cards of your library. You may reveal a creature card from among them and put that card into your hand. Put the rest on the bottom of your library in a random order.
            """,
        ) to abilities(
            addOne("{G}"),
            activated(cost(mana("{3}{G}"), tap),
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        ),
    )

    // CR 111.10s
    @Test
    fun skywaterLookout() = card(
        name = "Skywater Lookout",
        cost = "{2}{U}",
        type = "Creature — Merfolk Scout",
        text = """
            Flying
            When this creature enters, create a Map token. $mapReminder
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, createMap()))

    // CR 702.4b, 701.44a
    @Test
    fun dawnstrideOutrider() = card(
        name = "Dawnstride Outrider",
        cost = "{2}{W}",
        type = "Creature — Human Scout",
        text = """
            Double strike
            When this creature enters, it explores. $exploreReminder
        """,
        pt = "1/1",
    ) parsesTo abilities(Keyword.DoubleStrike, triggered(entersSelf, explore(Objects.Self)))

    // CR 701.44b: it triggers once the explore is done
    @Test
    fun grottoDelver() = card(
        name = "Grotto Delver",
        cost = "{2}{U}",
        type = "Creature — Merfolk Scout",
        text = "Whenever a creature you control explores, this creature gets +1/+0 until end of turn and can't be blocked this turn.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.KeywordActionDone(KeywordActionKind.Explore, objects = creatureYouControl)), pump(Objects.Self, 1, 0, Mechanic.CantBeBlocked)),
    )

    // CR 603.6c, 701.44a
    @Test
    fun shaftwingGuide() = card(
        name = "Shaftwing Guide",
        cost = "{W}",
        type = "Creature — Bird",
        text = """
            Flying, vigilance
            When this creature dies, target creature you control explores. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(diesSelf, explore(target("creature")), targets = listOf(creatureYouControlSlot)),
    )

    // CR 602.5d, 701.44a
    @Test
    fun sunseekerSprout() = card(
        name = "Sunseeker Sprout",
        cost = "{G}",
        type = "Creature — Merfolk Scout",
        text = "{2}{G}: This creature explores. Activate only as a sorcery. $exploreReminder",
        pt = "1/1",
    ) parsesTo abilities(activated(manaCost("{2}{G}"), explore(Objects.Self), rules = asSorcery))
}
