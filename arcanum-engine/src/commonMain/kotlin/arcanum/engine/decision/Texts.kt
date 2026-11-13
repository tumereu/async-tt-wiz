package arcanum.engine.decision

import arcanum.api.Ability
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Step
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Characteristics

/**
 * Text for people in decisions (API review 3): what an ability says as printed (the parser keeps each ability's lines,
 * [Ability.Triggered.text] and the like, and each mode's line), the sentence of it a decision is about, and plain words
 * when there is no printed text (hand-made definitions). Never the engine's data as Kotlin text.
 */
internal object Texts {
    /** The text players read for [ability]: its printed text, a keyword's name ("Flying"), unparsed text as printed. */
    fun of(ability: Ability?): String? = when (ability) {
        is Ability.Spell -> ability.text
        is Ability.Activated -> ability.text
        is Ability.Triggered -> ability.text
        is Ability.Static -> ability.text
        is Ability.SpecialAction -> ability.text
        is Ability.Unparsed -> ability.text
        is Keyword -> ability.text
        else -> null
    }

    /** The text of [ability] with [origin] on an object with [characteristics]: its own, else the printed ability at
     * [origin], else the keyword whose expansion it is ("Prowess" for prowess's trigger, D23). */
    fun of(ability: Ability?, origin: AbilityOrigin?, characteristics: Characteristics?): String? {
        of(ability)?.let { return it }
        val abilities = characteristics?.abilities.orEmpty()
        fun at(o: AbilityOrigin?): String? = o?.let { wanted -> of(abilities.firstOrNull { it.origin == wanted }?.ability) }
        return at(origin) ?: (origin as? AbilityOrigin.Expansion)?.let { at(it.keyword) }
    }

    /** The sentences of [text], one line or sentence each, a mode's bullet left out. */
    private fun sentences(text: String): List<String> =
        text.lines().flatMap { line -> line.removePrefix("• ").split(Regex("""(?<=[.!?])\s+(?=[A-Z])""")) }
            .map { it.trim() }.filter { it.isNotEmpty() }

    private val MAY = Regex("""\bmay\b""", RegexOption.IGNORE_CASE)

    /**
     * The prompt of the "you may" [step] of [ability] (CR 608.2d): its sentence of the ability's text when the text has
     * one "may" sentence for each optional step, else the whole text; plain words for what it does when there is no text.
     */
    /** Whether [step] is one of [ability]'s own optional steps, not one a keyword action expands into (discover's cast, CR 701.57a). */
    fun printedMay(ability: Ability?, step: Step): Boolean {
        val optional = instructionsOf(ability)?.let { optionalSteps(it) }.orEmpty()
        return optional.any { it === step } || step in optional
    }

    fun may(ability: Ability?, step: Step): String {
        val text = of(ability) ?: return "You may: ${describe(maySteps(step))}"
        val mays = sentences(text).filter { MAY.containsMatchIn(it) }
        val optional = instructionsOf(ability)?.let { optionalSteps(it) }.orEmpty()
        val index = optional.indexOfFirst { it === step }.takeIf { it >= 0 } ?: optional.indexOf(step)
        if (mays.size == optional.size && index >= 0) return mays[index]
        return mays.singleOrNull() ?: text
    }

    private val PAY = Regex("""\bpay""", RegexOption.IGNORE_CASE)

    /** A sentence telling what follows a choice to pay ("If you do, …", "When you do, …", "If they don't, …"). */
    private val FOLLOW = Regex("""^(If|When)\s+(you|they|that player)\s+(do|does|don't|doesn't|did|didn't)\b""")

    /** A triggered ability's trigger condition before its first comma ("When this creature enters, "). */
    private val CONDITION = Regex("""^(When|Whenever|At)\b[^,]*,\s+""")

    /**
     * The prompt of a cost paid on resolution (CR 118.12): the sentence of [ability]'s text that says "pay", else its
     * "may" sentence ("you may forage"), a triggered ability's trigger condition left out, followed by the sentences
     * that say what paying does ("If you do, put two +1/+1 counters on it."): "You may forage. If you do, put two +1/+1
     * counters on it." [otherwise] when the text has neither. Every sentence after the first "If you do" up to the next
     * choice belongs to what paying does ("If you do, create a 1/1 red Goblin creature token. It gains haste until end of turn.").
     */
    fun pay(ability: Ability?, otherwise: String): String {
        val all = sentences(of(ability) ?: return otherwise)
        val at = all.indexOfFirst { PAY.containsMatchIn(it) }.takeIf { it >= 0 }
            ?: all.indexOfFirst { MAY.containsMatchIn(it) }.takeIf { it >= 0 } ?: return otherwise
        val first = all[at].let { if (ability is Ability.Triggered) it.replace(CONDITION, "").replaceFirstChar(Char::uppercaseChar) else it }
        val rest = all.drop(at + 1)
        val follows = if (rest.firstOrNull()?.let { FOLLOW.containsMatchIn(it) } == true)
            listOf(rest.first()) + rest.drop(1).takeWhile { FOLLOW.containsMatchIn(it) || !(PAY.containsMatchIn(it) || MAY.containsMatchIn(it)) }
        else emptyList()
        return (listOf(first) + follows).joinToString(" ")
    }

    /** The sentence of [ability]'s text that says [verb] ("search", "choose"), else [otherwise]. */
    fun sentence(ability: Ability?, verb: String, otherwise: String): String {
        val text = of(ability) ?: return otherwise
        val word = Regex("""\b$verb""", RegexOption.IGNORE_CASE)
        return sentences(text).firstOrNull { word.containsMatchIn(it) } ?: otherwise
    }

    private fun maySteps(step: Step): List<Step> = when (step) {
        is Step.May -> step.steps
        is Step.Pay -> step.steps
        else -> listOf(step)
    }

    private fun instructionsOf(ability: Ability?): Instructions? = when (ability) {
        is Ability.Spell -> ability.instructions
        is Ability.Activated -> ability.instructions
        is Ability.Triggered -> ability.instructions
        is Ability.SpecialAction -> ability.instructions
        else -> null
    }

    /** The optional steps ("you may", a cost paid by choice) of [instructions], in printed order: modes first. */
    private fun optionalSteps(instructions: Instructions): List<Step> {
        val all = instructions.modes?.options.orEmpty().flatMap { it.instructions.steps } + instructions.steps
        val found = mutableListOf<Step>()
        fun walk(s: Step) {
            when (s) {
                is Step.May -> { found += s; s.steps.forEach(::walk) }
                is Step.Pay -> { if (!s.mandatory && s.cost == null) found += s; s.steps.forEach(::walk) }
                is Step.Simultaneously -> s.steps.forEach(::walk)
                is Step.If -> { s.then.forEach(::walk); s.otherwise.forEach(::walk) }
                is Step.ForEach -> s.steps.forEach(::walk)
                is Step.Repeat -> s.steps.forEach(::walk)
                is Step.RepeatWhile -> s.steps.forEach(::walk)
                else -> {}
            }
        }
        all.forEach(::walk)
        return found
    }

    /** Plain words for what [steps] do, by kind ("copy spell; draw"), for definitions without printed text. */
    fun describe(steps: List<Step>): String = steps.joinToString("; ") { s ->
        when (s) {
            is Step.Do -> words(effectName(s.effect))
            is Step.Manual -> s.text
            else -> words(className(s) ?: "step")
        }
    }.ifEmpty { "do it" }

    private fun effectName(effect: Effect): String = className(effect) ?: "effect"

    /**
     * [value]'s class name without its outer classes ("Draw" for `Effect.Draw`), for texts only: a shrunk Android build
     * may name a nested class "Effect$Draw" where the JVM says "Draw". Never for ids, keys or answers: those write their
     * names out ([costName], [arcanum.engine.decision.Decision.kind]).
     */
    fun className(value: Any): String? = value::class.simpleName?.substringAfterLast('$')

    /**
     * A cost part's kind in words ("TapObjects"), written out because cost descriptions are choices a player answers
     * ([arcanum.engine.decision.Decision.ChooseOption]) and a device and a server must offer the same ones.
     */
    fun costName(part: CostPart): String = when (part) {
        is CostPart.Mana -> "Mana"
        CostPart.ItsManaCost -> "ItsManaCost"
        is CostPart.ItsManaCostLess -> "ItsManaCostLess"
        is CostPart.ManaCostOf -> "ManaCostOf"
        CostPart.TapSelf -> "TapSelf"
        CostPart.UntapSelf -> "UntapSelf"
        is CostPart.TapObjects -> "TapObjects"
        is CostPart.Sacrifice -> "Sacrifice"
        is CostPart.Discard -> "Discard"
        is CostPart.Exile -> "Exile"
        is CostPart.OneOf -> "OneOf"
        is CostPart.Action -> "Action"
        is CostPart.ReturnToHand -> "ReturnToHand"
        is CostPart.Reveal -> "Reveal"
        is CostPart.PayLife -> "PayLife"
        is CostPart.PayEnergy -> "PayEnergy"
        is CostPart.RemoveCounters -> "RemoveCounters"
        is CostPart.PutCounters -> "PutCounters"
        is CostPart.Loyalty -> "Loyalty"
        is CostPart.Mill -> "Mill"
        is CostPart.ChoosePlayer -> "ChoosePlayer"
        is CostPart.Choose -> "Choose"
        is CostPart.GetCounters -> "GetCounters"
        is CostPart.Waterbend -> "Waterbend"
        is CostPart.Manual -> "Manual"
        is CostPart.RevealChoice -> "RevealChoice"
    }

    /** "CopySpell" → "copy spell". */
    fun words(name: String): String = name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
}
