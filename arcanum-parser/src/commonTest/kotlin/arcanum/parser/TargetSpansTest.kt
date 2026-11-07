package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Instructions
import arcanum.parser.cube.card
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where each target slot's phrase sits in its ability's or mode's text ([arcanum.api.TargetSlot.span]), for hosts that
 * name the chosen targets in the text. Each test lists, per ability (and per mode, after it), its slots' phrases in
 * order; null for a slot without one. Every parsing test also checks all spans it parses (`checkSpans`).
 */
class TargetSpansTest {
    /** The phrase of each slot of [text]'s abilities: an ability's own slots, then each mode's, as "mode N: phrase". */
    private fun phrases(name: String, type: String, text: String, cost: String? = "{R}", pt: String? = null): List<String?> {
        val face = OracleParser.parse(card(name = name, cost = cost, type = type, text = text, pt = pt))
        fun of(text: String?, instructions: Instructions, prefix: String = ""): List<String?> =
            instructions.targets.map { slot -> slot.span?.let { span -> text?.let { prefix + span.of(it) } } } +
                instructions.modes?.options.orEmpty().flatMapIndexed { i, mode -> of(mode.text, mode.instructions, "mode ${i + 1}: ") }
        return face.abilities.flatMap { ability ->
            when (ability) {
                is Ability.Spell -> of(ability.text, ability.instructions)
                is Ability.Activated -> of(ability.text, ability.instructions)
                is Ability.Triggered -> of(ability.text, ability.instructions)
                else -> emptyList()
            }
        }
    }

    // CR 115.4: "any target"
    @Test fun aSingleTarget() = assertEquals(listOf("any target"),
        phrases("Storm Spark", "Instant", "Storm Spark deals 3 damage to any target."))

    @Test fun aTargetWithItsWholeNounPhrase() = assertEquals(listOf("target creature an opponent controls"),
        phrases("Ash Verdict", "Sorcery", "Destroy target creature an opponent controls."))

    @Test fun twoTargetsInTheOrderTheTextNamesThem() = assertEquals(listOf("Target creature you control", "target creature you don't control"),
        phrases("Ember Brawl", "Sorcery", "Target creature you control fights target creature you don't control.", cost = "{G}"))

    @Test fun upToNTargets() = assertEquals(listOf("up to two target creatures"),
        phrases("Frost Snare", "Instant", "Tap up to two target creatures.", cost = "{U}"))

    // CR 700.2: each mode's targets are its own; the spans count in the mode's text
    @Test fun eachModeHasItsOwnSpans() = assertEquals(listOf("mode 1: target creature", "mode 2: target artifact"),
        phrases("Ember Rasp", "Instant", """
            Choose one —
            • Ember Rasp deals 3 damage to target creature.
            • Destroy target artifact.
        """))

    @Test fun aTriggeredAbilitysTarget() = assertEquals(listOf("another target creature you control"),
        phrases("Ember Drill", "Creature — Human Soldier", "At the beginning of combat on your turn, put a +1/+1 counter on another target creature you control.",
            cost = "{1}{W}", pt = "2/2"))

    @Test fun anActivatedAbilitysTargetAfterItsCost() = assertEquals(listOf("target player"),
        phrases("Ember Glass", "Artifact", "{T}, Sacrifice this artifact: Look at the top card of target player's library. Draw a card at the beginning of the next turn's upkeep.",
            cost = "{1}"))

    // CR 601.2d: a divided amount's targets are one phrase
    @Test fun dividedDamage() = assertEquals(listOf("one or two targets"),
        phrases("Paired Spark", "Instant", "Paired Spark deals 2 damage divided as you choose among one or two targets."))

    @Test fun aListOfTypes() = assertEquals(listOf("target artifact, creature, or enchantment"),
        phrases("Ash Unmaking", "Instant", "Destroy target artifact, creature, or enchantment.", cost = "{1}{B}"))

    // Two slots read from "two target creatures": both slots share the phrase
    @Test fun slotsSharingOnePhrase() = assertEquals(listOf("two target creatures controlled by different players", "two target creatures controlled by different players"),
        phrases("Flee Apart", "Instant", "Choose two target creatures controlled by different players. Return those creatures to their owners' hands.", cost = "{1}{U}"))

    // CR 603.12: a reflexive trigger's targets are its own: the ability's own target is the one before it
    @Test fun aReflexiveTriggersTargetIsNotTheAbilitys() = assertEquals(listOf("target card from a graveyard"),
        phrases("Ember Kettle", "Artifact", "{T}: Exile target card from a graveyard. When a creature card is exiled this way, put a +1/+1 counter on target creature you control.",
            cost = "{1}"))

    // A keyword action that targets ("earthbend 2") has no phrase to place
    @Test fun aKeywordActionsTargetHasNoSpan() = assertEquals(listOf(null),
        phrases("Stone Heave", "Sorcery", "Earthbend 2.", cost = "{G}"))

    @Test fun targetWordsThatNameNoTarget() = assertEquals(emptyList(),
        TargetSpans.phrases("Whenever this creature becomes the target of a spell that targets only it, choose new targets for it."))
}
