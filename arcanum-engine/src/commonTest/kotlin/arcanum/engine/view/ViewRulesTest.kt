package arcanum.engine.view

import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where abilities function (CR 113.6), rules applied that aren't characteristics (D24, CR 613.10, 613.11), summoning
 * sickness (CR 302.6) and unknown objects (D60). */
class ViewRulesTest {
    // CR 113.6: abilities of permanents function on the battlefield only; the anthem in the graveyard does nothing
    @Test
    fun staticAbilityInTheGraveyardDoesNothing() = scenario {
        alice.graveyard("Elvish Hierophant")
        alice.battlefield("Elvish Seer")
        expect {
            "Elvish Seer".power shouldBe 1
        }
    }

    // CR 604.1, 611.3a: "has haste as long as an opponent has 10 or less life" follows the condition
    @Test
    fun conditionalStaticFollowsItsCondition() {
        listOf(10 to true, 11 to false).forEach { (life, haste) ->
            scenario {
                alice.battlefield("Gorehaunt")
                bob.life = life
                expect {
                    if (haste) "Gorehaunt" hasKeyword Keyword.Haste else "Gorehaunt" lacksKeyword Keyword.Haste
                }
            }
        }
    }

    // CR 113.12, D24: "can't block" and "can't be blocked" are rules applied to the object, not abilities it has
    @Test
    fun nonLayerRulesApplyToTheirObject() = scenario {
        alice.battlefield("Shapeless Exile")
        expect {
            "Shapeless Exile" hasMechanic { it is Mechanic.CantBlock }
            "Shapeless Exile" hasMechanic { it == Mechanic.CantBeBlocked }
        }
    }

    // CR 702.2b, D24: a keyword's meaning comes from its expansion, deathtouch one to one
    @Test
    fun keywordOnlyMechanicsComeFromTheKeyword() = scenario {
        alice.battlefield("Plague Shamblers")
        expect {
            "Plague Shamblers" hasMechanic { it == Mechanic.Deathtouch }
        }
    }

    // CR 613.11: a replacement effect of a static ability is a rule of the game, with its source
    @Test
    fun replacementsAreGameRules() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal")
        expect {
            val rule = view.rules.single { it.mechanic is Mechanic.Replacement }
            rule.source shouldBe card("Nutcracker, Squirrel Marshal").id
            rule.controller shouldBe alice.id
        }
    }

    // CR 302.6: a creature that came under its controller's control this turn is summoning sick
    @Test
    fun summoningSickness() = scenario {
        turn(3, active = alice)
        alice.battlefield("Elvish Seer")
        alice.battlefield("Lineage Druid", sick = true)
        expect {
            "Elvish Seer".view.summoningSick shouldBe false
            "Lineage Druid".view.summoningSick shouldBe true
        }
    }

    // CR 302.6: a creature whose control changed is sick for its new controller
    @Test
    fun controlChangeMakesItSick() = scenario {
        turn(4, active = bob, step = TurnStep.PrecombatMain)
        alice.battlefield("Elvish Seer")
        effect(bob, Mechanic.ControlledBy(arcanum.api.Players.You), on = listOf("Elvish Seer"))
        expect {
            "Elvish Seer".view.summoningSick shouldBe true
        }
    }

    // D60: an unknown object has no characteristics in the view
    @Test
    fun unknownObjectsHaveNoCharacteristics() = scenario {
        bob.hiddenHand("Storm Spark")
        expect {
            "Storm Spark".view.characteristics shouldBe null
            "Storm Spark".zone shouldBe Zone.Hand
        }
    }

    // CR 613: the view is a pure function of the state (P3): it changes nothing and gives the same answer twice
    @Test
    fun viewIsPure() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Inspector's Firebird")
        attach("Inspector's Firebird", to = "Elvish Seer")
        val before = state.deepCopy()
        val first = Engine.view(state)
        assertTrue(before == state)
        first shouldBeSameAs Engine.view(state)
    }

    private infix fun View.shouldBeSameAs(other: View) = kotlin.test.assertEquals(other, this)
}
