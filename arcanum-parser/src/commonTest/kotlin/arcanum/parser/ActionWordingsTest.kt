package arcanum.parser

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.EventPattern
import arcanum.api.KeywordActionKind
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals

/** The wordings of keyword actions and marks as trigger events, read by one general rule on invented cards. */
class ActionWordingsTest {
    private val creatureYouControl = Filters.noun("creature you control")!!
    private val creature = ObjectFilter.Type(CardType.Creature)

    private fun event(text: String): EventPattern? = Events.event(text, setOf("this creature"))

    private fun did(kind: KeywordActionKind, players: PlayerFilter? = PlayerFilter.You, objects: ObjectFilter? = null) =
        EventPattern.KeywordActionDone(kind, players, objects)

    // CR 701.16a, 701.22d, 701.25d, 701.34a, 701.57b, 701.59a, 701.61a, 702.174c
    @Test
    fun whatAPlayerDoes() {
        assertEquals(did(KeywordActionKind.Proliferate), event("you proliferate"))
        assertEquals(did(KeywordActionKind.Investigate), event("you investigate"))
        assertEquals(did(KeywordActionKind.Discover), event("you discover"))
        assertEquals(did(KeywordActionKind.CollectEvidence), event("you collect evidence"))
        assertEquals(did(KeywordActionKind.Forage), event("you forage"))
        assertEquals(did(KeywordActionKind.Scry), event("you scry"))
        assertEquals(did(KeywordActionKind.Surveil), event("you surveil"))
        assertEquals(did(KeywordActionKind.GiveGift), event("you give a gift"))
    }

    // The same verbs in their other form, whoever does them
    @Test
    fun whatAnotherPlayerDoes() {
        assertEquals(did(KeywordActionKind.Scry, PlayerFilter.Opponent), event("an opponent scries"))
        assertEquals(did(KeywordActionKind.Proliferate, PlayerFilter.Any), event("a player proliferates"))
        assertEquals(did(KeywordActionKind.CollectEvidence, PlayerFilter.Opponent), event("an opponent collects evidence"))
    }

    // CR 701.43a, 702.143c
    @Test
    fun whatAPlayerDoesToAnObject() {
        assertEquals(did(KeywordActionKind.Exert, objects = creature), event("you exert a creature"))
        assertEquals(did(KeywordActionKind.Foretell, objects = ObjectFilter.Any), event("you foretell a card"))
    }

    // CR 701.44b, 701.50f
    @Test
    fun whatAPermanentDoes() {
        assertEquals(did(KeywordActionKind.Explore, null, creatureYouControl), event("a creature you control explores"))
        assertEquals(did(KeywordActionKind.Explore, null, ObjectFilter.Is(Objects.Self)), event("this creature explores"))
        assertEquals(did(KeywordActionKind.Connive, null, creatureYouControl), event("a creature you control connives"))
    }

    // CR 701.37b, 702.112b, 701.60a, 722.3a, 702.171b
    @Test
    fun gainingAMark() {
        assertEquals(EventPattern.GotMark(ObjectMark.Monstrous, creatureYouControl), event("a creature you control becomes monstrous"))
        assertEquals(EventPattern.GotMark(ObjectMark.Renowned, creatureYouControl), event("a creature you control becomes renowned"))
        assertEquals(EventPattern.GotMark(ObjectMark.Suspected, creatureYouControl), event("a creature you control becomes suspected"))
        assertEquals(EventPattern.GotMark(ObjectMark.Prepared, creature), event("a creature becomes prepared"))
        assertEquals(EventPattern.GotMark(ObjectMark.Saddled, ObjectFilter.Is(Objects.Self)), event("this creature becomes saddled"))
    }

    // A player doesn't explore and a permanent doesn't proliferate: those aren't read, rather than read as something else
    @Test
    fun whatTheRulesDontSayIsntRead() {
        assertEquals(null, event("you explore"))
        assertEquals(null, event("you connive"))
        assertEquals(null, event("a creature you control proliferates"))
        assertEquals(null, event("a creature you control becomes tapped by magic"))
    }

    // CR 603.1: the whole line reads as a triggered ability, "it" being the creature that explored
    @Test
    fun aWholeLine() {
        val abilities = OracleParser.parse(OracleCard("Trail Herald", "{1}{G}", "Enchantment",
            "Whenever a creature you control explores, you gain 1 life.\nWhenever a creature you control becomes monstrous, draw a card.")).abilities
        assertEquals(listOf<Trigger>(Trigger.On(did(KeywordActionKind.Explore, null, creatureYouControl)),
            Trigger.On(EventPattern.GotMark(ObjectMark.Monstrous, creatureYouControl))), abilities.map { (it as Ability.Triggered).trigger })
    }
}
