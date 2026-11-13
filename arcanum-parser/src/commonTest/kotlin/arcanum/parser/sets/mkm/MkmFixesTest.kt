package arcanum.parser.sets.mkm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor fix chunk: a secret choice of creature type and its reveal as a cost. */
class MkmFixesTest {
    // CR 607.2d: the type chosen by the enters trigger is the source's card memory, read by its other ability (as it last existed after
    // the sacrifice, CR 608.2h); the choice is secret until the cost reveals it (CR 118.3: no choice kept, the cost can't be paid);
    // CR 506.4: an attacking creature token
    @Test
    fun lineupOfSuspects() = card(
        name = "Lineup of Suspects",
        cost = "{4}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 1/1 white Human creature token, a 1/1 blue Merfolk creature token, and a 1/1 red Goblin creature token. Then secretly choose Human, Merfolk, or Goblin.
            Sacrifice this enchantment, Reveal the creature type you chose: If target attacking creature token is the chosen type, put three +1/+1 counters on it and it gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            create(creatureToken("Human", listOf(Color.White), 1, 1)),
            create(creatureToken("Merfolk", listOf(Color.Blue), 1, 1)),
            create(creatureToken("Goblin", listOf(Color.Red), 1, 1)),
            Step.Choose(Choice.CreatureTypeAmong(listOf(Subtype("Human"), Subtype("Merfolk"), Subtype("Goblin"))), bind = "type", chooser = Players.You,
                remember = true, secret = true)),
        activated(cost(sacrificeSelf, CostPart.RevealChoice("type")),
            Step.If(Condition.ObjectMatches(target("token"), ObjectFilter.Sub(SubtypeRef.Remembered("type"))), then = listOf(
                counters(target("token"), 3),
                gains(target("token"), Keyword.Deathtouch))),
            targets = listOf(slot("token", and(attacking, creature, token)))),
    )
}
