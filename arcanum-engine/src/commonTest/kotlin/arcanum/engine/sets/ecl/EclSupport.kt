package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.state.TargetRef

/** Plain supporting cards and moves shared by the Lorwyn Eclipsed scenarios (invented, D15). */
internal object Ecl {
    val m1m1 = CounterKind.PowerToughness(-1, -1)
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val redBear = Cards.invent("Ember Bear", "{1}{R}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Hill Giant Kin", "{3}{R}", "Creature — Giant", "", "3/3")
    val elf = Cards.invent("Grove Elf", "{G}", "Creature — Elf", "", "1/1")
    val elfMatron = Cards.invent("Grove Matron", "{1}{G}", "Creature — Elf", "", "3/3")
    val goblin = Cards.invent("Bog Goblin", "{B}", "Creature — Goblin", "", "1/1")
    val kithkin = Cards.invent("Hamlet Smith", "{W}", "Creature — Kithkin", "", "1/1")
    val merfolk = Cards.invent("Shoal Wader", "{U}", "Creature — Merfolk", "", "1/1")
    val faerie = Cards.invent("Bramble Sprite", "{U}", "Creature — Faerie", "Flying", "1/1")
    val elemental = Cards.invent("Cinder Wisp", "{1}{R}", "Creature — Elemental", "", "2/2")
    val bigElemental = Cards.invent("Ashen Colossus", "{4}{R}", "Creature — Elemental", "", "5/5")
    val jolt = Cards.invent("Spark Jolt", "{R}", "Instant", "Spark Jolt deals 2 damage to any target.")
    val bolt = Cards.invent("Searing Bolt", "{R}", "Instant", "Searing Bolt deals 3 damage to any target.")
    val unmake = Cards.invent("Unmake Ward", "{W}{W}", "Instant", "Exile target creature.")
    val growth = Cards.invent("Sudden Vigor", "{G}", "Instant", "Target creature gets +3/+3 until end of turn.")
    val ponder = Cards.invent("Quiet Study", "{U}", "Sorcery", "Draw a card.")
    val bigSpell = Cards.invent("Tide of Ages", "{3}{U}", "Sorcery", "Draw two cards.")
    val totem = Cards.invent("Hamlet Totem", "{2}", "Artifact", "")
    val charm = Cards.invent("Hearth Ward Charm", "{1}{W}", "Enchantment", "")
}

/** Passes priority until the Stack is empty or a decision is open; triggers that trigger together are put on the Stack in
 * the order offered (CR 603.3b). */
internal fun Scenario.resolveAll() {
    var guard = 60
    while (guard-- > 0) {
        (pending as? Decision.OrderTriggers)?.let { d -> answer(Answer.Order(d.triggers.map { it.index })) }
        if (state.stack.isEmpty() || pending != null) break
        allPass()
    }
}

/** Answers an open yes/no question (a "you may", or "choose new targets for the copy?"). */
internal fun Scenario.say(yes: Boolean) {
    if (pending is Decision.May) answer(Answer.YesNo(yes))
}

/** The top of the Stack as a target (an ability there has no card name). */
internal fun Scenario.topOfStack(): TargetRef = TargetRef.Object(state.stack.last())

/** Pays an open mana payment with the engine's suggestion. */
internal fun Scenario.paySuggested() {
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
}
