package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Designation
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.RulesGlossary
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.query.Evaluator
import arcanum.engine.scenario.scenario
import arcanum.engine.state.StatusFlag
import arcanum.engine.turn.Speed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [Engine.restrictions], [Engine.speed], [Engine.ringTemptations], [Engine.dayOrNight], [Engine.classLevel] and the
 * glossary of states: what the rules keep beyond characteristics, for a host's Inspect and chips. */
class RestrictionsTest {
    private val cub = Cards.invent("Hollow Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val mule = Cards.invent("Hollow Mule", "{1}{G}", "Creature — Ox", "This creature can't block.", "2/2")
    private val snare = Cards.define("Hollow Snare", arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition("Hollow Snare",
        types = listOf(CardType.Enchantment), abilities = listOf(Ability.Static(listOf(Mechanic.CantAttack()),
            Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Opponent)))))))))))
    private val wings = Cards.invent("Hollow Wings", "{1}{U}", "Enchantment", "", null)
    private val tale = Cards.invent("Hollow Tale", "{1}{W}", "Enchantment — Class", "", null)
    private val beckon = Cards.invent("Hollow Beckoning", "{B}", "Sorcery", "The Ring tempts you.")

    private fun arcanum.engine.scenario.Scenario.texts(name: String): List<String> =
        Engine.restrictions(state, view(), card(name)).map { it.text }

    // CR 509.1b: "can't block" from an effect is listed, with what made it and how long it lasts
    @Test
    fun anEffectsCantBlockNamesItsSourceAndDuration() = scenario {
        alice.battlefield(cub, wings)
        val made = effect(alice, Mechanic.CantBlock(ObjectFilter.Any), on = listOf(cub), duration = Duration.EndOfTurn)
        val restriction = Engine.restrictions(state, view(), card(cub)).single()
        assertEquals("Can't block", restriction.text)
        assertEquals(made, restriction.effect)
        assertEquals(Duration.EndOfTurn, restriction.duration)
    }

    // CR 508.1c, 611.3a: a static ability on another permanent restricts while it functions: no duration, that permanent as its maker
    @Test
    fun aStaticAbilityOfAnotherPermanentIsItsMaker() = scenario {
        alice.battlefield(snare)
        bob.battlefield(cub)
        val restriction = Engine.restrictions(state, view(), card(cub)).single()
        assertEquals("Can't attack", restriction.text)
        assertEquals(card(snare), restriction.madeBy)
        assertNull(restriction.duration)
        assertEquals(emptyList(), texts(snare), "its own creature-less text restricts nothing on it")
    }

    // CR 113.12: what its own printed text says is on the card: not an effect on it
    @Test
    fun itsOwnPrintedTextIsNotListed() = scenario {
        alice.battlefield(mule)
        assertTrue(view()[card(mule)]!!.has { it is Mechanic.CantBlock }, "the rule is in force")
        assertEquals(emptyList(), texts(mule))
    }

    // CR 702.9b: flying means "can be blocked only by …": the keyword is what a host lists, not its meaning
    @Test
    fun aKeywordsMeaningIsNotListed() = scenario {
        alice.battlefield(cub)
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Flying)), on = listOf(cub))
        assertTrue(view()[card(cub)]!!.has { it is Mechanic.CanBeBlockedOnlyBy }, "flying's meaning is in force")
        assertEquals(emptyList(), texts(cub))
    }

    // CR 113.3d, 613.1f: an ability an effect grants is an effect on the creature: its maker and duration are the effect's
    @Test
    fun aGrantedAbilityCountsAsTheEffect() = scenario {
        alice.battlefield(cub, wings)
        val made = effect(alice, Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.DoesntUntap), Affected.Objects(Objects.Self)))), on = listOf(cub), duration = Duration.UntilYourNextTurn)
        val restriction = Engine.restrictions(state, view(), card(cub)).single()
        assertEquals("Doesn't untap", restriction.text)
        assertEquals(made, restriction.effect)
        assertEquals(Duration.UntilYourNextTurn, restriction.duration)
    }

    // CR 701.60c: the rules themselves stop a suspected creature blocking: no card made it
    @Test
    fun aSuspectedCreaturesCantBlockIsTheRules() = scenario {
        alice.battlefield(cub)
        setup()
        change(Change.Status(card(cub), StatusFlag.Marked(ObjectMark.Suspected), false, true))
        val restriction = Engine.restrictions(state, view(), card(cub)).single()
        assertEquals("Can't block", restriction.text)
        assertNull(restriction.madeBy)
    }

    // CR 508.1d, 509.1c, 502.3, 101.2: the words of what an effect can say, a filter in plain words
    @Test
    fun theWordsOfRestrictionsAndRequirements() {
        val flying = ObjectFilter.HasKeyword(Keyword.Flying)
        val greater = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
            ObjectFilter.Power(Comparison.Greater, Value.Of(arcanum.api.NumericProperty.Power, Objects.Affected, arcanum.api.Combine.Single))))
        assertEquals("Can't be blocked", Restrictions.text(Mechanic.CantBeBlocked))
        assertEquals("Can't be blocked by creatures with flying", Restrictions.text(Mechanic.CantBeBlockedBy(flying)))
        assertEquals("Can't be blocked by creatures with greater power", Restrictions.text(Mechanic.CantBeBlockedBy(greater)))
        assertEquals("Can block only creatures with flying", Restrictions.text(Mechanic.CanBlockOnly(flying)))
        assertEquals("Can't block creatures with power 2 or less",
            Restrictions.text(Mechanic.CantBlock(ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)))))
        assertEquals("Can be blocked only by Walls", Restrictions.text(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.Sub(arcanum.api.Subtype("Wall")))))
        assertEquals("Can't be blocked by some creatures", Restrictions.text(Mechanic.CantBeBlockedBy(ObjectFilter.Paired)))
        assertEquals("Attacks each combat if able", Restrictions.text(Mechanic.MustAttack()))
        assertEquals("Doesn't untap", Restrictions.text(Mechanic.DoesntUntap))
        assertEquals("Can't be countered", Restrictions.text(Mechanic.CantBeCountered))
        assertNull(Restrictions.text(Mechanic.CantAttack(fromDefender = true)), "defender's own restriction is the keyword's")
        assertNull(Restrictions.text(Mechanic.CantGainLife), "not about an object")
    }

    // CR 702.179, 701.54c, 731: what the rules keep for a player and the game
    @Test
    fun aPlayersAndTheGamesStates() = scenario {
        alice.battlefield("Swamp", cub)
        alice.hand(beckon)
        assertEquals(0, Engine.ringTemptations(state, alice.id))
        assertEquals(0, Engine.speed(state, alice.id))
        assertNull(Engine.dayOrNight(state))
        change(Change.Ledger(alice.id, Speed.KEY, 0, 2))
        assertEquals(2, Engine.speed(state, alice.id))
        alice.casts(beckon)
        allPass()
        assertEquals(1, Engine.ringTemptations(state, alice.id))
        change(Change.Ledger(null, Evaluator.DAY_NIGHT, 0, Evaluator.NIGHT))
        assertEquals(DayOrNight.Night, Engine.dayOrNight(state))
    }

    // CR 716.2: a Class is level 1 until it levels up
    @Test
    fun aClasssLevel() = scenario {
        alice.battlefield(tale, cub)
        assertEquals(1, Engine.classLevel(state, card(tale)))
        assertNull(Engine.classLevel(state, card(cub)))
        change(Change.Memory(card(tale), Evaluator.CLASS_LEVEL, null, arcanum.engine.state.NamedValue.Number(2)))
        assertEquals(2, Engine.classLevel(state, card(tale)))
    }

    // The glossary explains every mark and designation a host may show, in its own words
    @Test
    fun everyStateHasAnExplanation() {
        val marks = listOf(ObjectMark.Monstrous, ObjectMark.Renowned, ObjectMark.Suspected, ObjectMark.Saddled, ObjectMark.Solved, ObjectMark.Prepared,
            ObjectMark.Harnessed, ObjectMark.Warped, ObjectMark.Foretold, ObjectMark.Plotted, ObjectMark.RingBearer, ObjectMark.Unlocked(arcanum.api.Door.Left))
        for (mark in marks) {
            val id = when (mark) {
                ObjectMark.RingBearer -> "ring-bearer"
                is ObjectMark.Unlocked -> "unlocked"
                else -> mark::class.simpleName!!.lowercase()
            }
            assertNotNull(RulesGlossary.state(id), id)
        }
        assertNotNull(RulesGlossary.state("goaded"))
        assertNotNull(RulesGlossary.state("phased-out"))
        listOf(Designation.Monarch to "monarch", Designation.Initiative to "initiative", Designation.CitysBlessing to "citys-blessing",
            Designation.EnduringStory to "enduring-story").forEach { (_, id) -> assertNotNull(RulesGlossary.state(id), id) }
        listOf("speed", "the-ring", "day", "night", "poison", "energy").forEach { assertNotNull(RulesGlossary.state(it), it) }
        assertEquals(4, RulesGlossary.ringLevels.size, "the Ring has four levels (CR 701.54c)")
    }

    // A line naming a state explains it, a player's words stay plain text
    @Test
    fun aStateNamedInALineIsExplained() {
        assertEquals(listOf("Goaded"), RulesGlossary.named("Goaded by Bob").map { it.name })
        assertEquals("Ring-bearer", RulesGlossary.first("Ring-bearer: legendary")?.name)
        assertEquals(emptyList(), RulesGlossary.named("Until your next day of speed"))
        assertEquals(listOf("Goad"), RulesGlossary.named("Goad target creature").map { it.name }, "the keyword action keeps its own")
    }
}
