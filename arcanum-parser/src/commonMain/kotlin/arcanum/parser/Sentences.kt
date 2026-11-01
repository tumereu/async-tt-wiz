package arcanum.parser

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone

/**
 * Rules-text lines that aren't keyword lines: activated abilities ("[cost]: [effect]") and the static wordings learned
 * so far. Each wording is one rule; a line no rule reads stays [Ability.Unparsed]. [self] holds the names the card
 * calls itself ("this land", its own name).
 */
internal class Sentences(private val self: Set<String>, private val isSpell: Boolean = false) {
    /** The card's name or "this land" / "This land" (a sentence may start with it). */
    private fun isSelf(text: String) = text in self || text.replaceFirstChar { it.lowercase() } in self

    /** chunk 33: what "the exiled card" means on this card (linked abilities, CR 607.2a); null when nothing links. */
    var exiledCard: Objects? = null

    /** chunk 31: what "it" means in the triggered ability being read, and the zone it is in ("you may exile it" in a
     * dies trigger: the new card in the graveyard, CR 400.7). */
    private var triggerIt: Pair<Objects, Zone?>? = null

    fun line(line: String): Ability? {
        // "Channel — [ability]": an ability word, no rules meaning (CR 207.2c). Keywords with a dash ("Ward—{2}") have
        // no spaces around it.
        ABILITY_WORD.matchEntire(line)?.let { m ->
            val word = m.groupValues[1]
            // Keywords that label an ability (CR 702.142, 702.169, 702.177, 702.178, 702.193): they add rules.
            LABELS[word.lowercase()]?.let { label ->
                return when (val ability = line(m.groupValues[2])) {
                    is Ability.Activated -> ability.copy(label = label)
                    is Ability.Triggered -> ability.copy(label = label)
                    is Ability.Static -> ability.copy(label = label)
                    else -> null
                }
            }
            if (word.lowercase() !in RulesWords.abilityWords) return null
            return when (val ability = line(m.groupValues[2])) {
                is Ability.Activated -> ability.copy(abilityWord = word)
                is Ability.Triggered -> ability.copy(abilityWord = word)
                is Ability.Static -> ability.copy(abilityWord = word)
                is Ability.Spell -> ability.copy(abilityWord = word)
                else -> null
            }
        }
        return static(line) ?: triggered(line) ?: activated(line) ?: spell(line)
    }

    /**
     * "Threshold — Add {B}{B}{B}{B}{B} instead if there are seven or more cards in your graveyard.": the spell's last
     * step happens only if the condition is false; the new one if it is true (a conditional "instead", written as If).
     */
    fun insteadOf(line: String, spell: Ability.Spell): Ability.Spell? {
        val m = INSTEAD_IF.matchEntire(line) ?: return null
        if (m.groupValues[1].isNotEmpty() && m.groupValues[1].lowercase() !in RulesWords.abilityWords) return null
        val condition = Conditions.condition(m.groupValues[3], self) ?: return null
        val steps = spell.instructions.steps
        if (steps.isEmpty()) return null
        val last = steps.last()
        // "[This] deals 4 damage instead": the same damage to the same recipients, another amount.
        val damage = SELF_DEALS_N.matchEntire(m.groupValues[2])?.takeIf { isSelf(it.groupValues[1]) }?.let { d ->
            val previous = ((last as? Step.Do)?.effect as? Effect.DealDamage) ?: return null
            listOf(Step.Do(previous.copy(amount = Value.Fixed(d.groupValues[2].toInt()))))
        }
        val replacement = damage ?: instructions(m.groupValues[2] + ".", spell.instructions.targets)?.takeIf { it.targets == spell.instructions.targets }?.steps
            ?: return null
        return spell.copy(
            abilityWord = m.groupValues[1].ifEmpty { null },
            instructions = spell.instructions.copy(steps = steps.dropLast(1) + Step.If(condition, then = replacement, otherwise = listOf(last))),
        )
    }

    /** "Choose one —" with its modes; "Choose three. You may choose the same mode more than once." (CR 700.2) */
    fun modes(choose: MatchResult, modeLines: List<String>): arcanum.api.Modes? {
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it) ?: return null) }
        if (options.isEmpty()) return null
        return arcanum.api.Modes(options, min = n, max = n, repeatable = choose.value.contains("same mode"))
    }

    /** The instructions of an instant or sorcery (CR 113.3a). */
    private fun spell(line: String): Ability? = if (isSpell) instructions(line)?.let { Ability.Spell(it) } else null

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (CR 614.12, 118.12a) */
    private fun static(line: String): Ability? {
        // "[If …, ]you may [cost] rather than pay this spell's mana cost." (CR 118.9)
        ALTERNATIVE_COST.matchEntire(line)?.let { m ->
            val parts = m.groupValues[2].split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten()
            val condition = when (val c = m.groupValues[1]) {
                "" -> null
                "it's not your turn" -> arcanum.api.Condition.Not(arcanum.api.Condition.YourTurn)
                else -> Conditions.condition(c, self) ?: return null
            }
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost(parts))), condition = condition)
        }
        if (line == "This spell can't be countered.") return Ability.Static(listOf(Mechanic.CantBeCountered))
        // "If X is 5 or more, this spell can't be countered and the damage can't be prevented." (CR 107.3m, 615.12)
        X_AT_LEAST_UNCOUNTERABLE.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeCountered, Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self)))),
                condition = arcanum.api.Condition.Compare(Value.CastX(), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())))
        }
        // "As an additional cost to cast this spell, [cost][ or cost]." (CR 601.2f)
        ADDITIONAL_COST.matchEntire(line)?.let { m ->
            val options = m.groupValues[1].split(" or ").map { option ->
                Cost(option.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten())
            }
            val cost = options.singleOrNull() ?: Cost(listOf(CostPart.OneOf(options)))
            return Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost)))
        }
        OTHERS_GET.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))))
        }
        DOESNT_UNTAP.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.DoesntUntap))
        }
        // 'Equipped creature has "[ability]"' (CR 301.5)
        EQUIPPED_HAS_QUOTED.matchEntire(line)?.let { m ->
            val granted = Sentences(setOf("this creature"), false).line(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.AttachedTo()))
        }
        // "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the
        // battlefield. If you don't, put it into its owner's graveyard." (CR 614.1c)
        WOULD_ENTER_DISCARD_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                instead = Instructions(steps = listOf(
                    Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(filter)))), bind = "discarded"),
                    Step.If(arcanum.api.Condition.Did("discarded"),
                        then = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield)))),
                        otherwise = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Graveyard))))))))))
        }
        ENTERS_TAPPED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))))
        }
        AS_ENTERS_PAY_LIFE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Unless(Players.You, Cost(listOf(CostPart.PayLife(Value.Fixed(m.groupValues[2].toInt())))),
                    listOf(Step.Modify(Modification.EntersTapped)))))))))
        }
        // chunk 30: "This creature has deathtouch as long as you control at least three other snow permanents."
        HAS_KEYWORDS_AS_LONG_AS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null)),
                condition = Conditions.condition(m.groupValues[3], self) ?: return null)
        }
        // chunk 30: "This creature escapes with a +1/+1 counter on it." means "If this permanent escaped, it enters with
        // [those counters]" (CR 702.138c).
        ESCAPES_WITH.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))),
                condition = arcanum.api.Condition.Paid(arcanum.api.Keyword.Escape.Kind))
        }
        // chunk 33: "X can't be 0."
        X_CANT_BE.matchEntire(line)?.let { m -> return Ability.Static(listOf(Mechanic.XAtLeast(m.groupValues[1].toInt() + 1))) }
        // chunk 32: "Golems you control have first strike."
        HAVE_KEYWORDS.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null)),
                affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 32: "As long as you've discarded a card this turn, you may pay {B/R} to cast this spell." (CR 118.9)
        MAY_PAY_TO_CAST.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse(m.groupValues[2]) ?: return null))),
                condition = Conditions.condition(m.groupValues[1], self) ?: return null)
        }
        return null
    }

    /** chunk 30: "first strike", "trample and haste": keywords named in a sentence. */
    private fun keywords(text: String): List<Ability>? =
        text.split(Regex(""",? and |, """)).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }

    /** "[cost]: [effect]" (CR 602.1), with "This ability costs {1} less to activate for each …" as a last sentence. */
    private fun activated(line: String): Ability? {
        val colon = line.indexOf(": ").takeIf { it > 0 } ?: return null
        val cost = cost(line.substring(0, colon)) ?: return null
        var text = line.substring(colon + 2)
        var costChange: Value? = null
        var condition: arcanum.api.Condition? = null
        var timing: arcanum.api.Timing = arcanum.api.Timing.Any
        ACTIVATE_ONLY_AS_INSTANT.find(text)?.let { m ->
            timing = arcanum.api.Timing.Instant
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_IF.find(text)?.let { m ->
            condition = Conditions.condition(m.groupValues[1]) ?: return null
            text = text.removeRange(m.range).trim()
        }
        COSTS_LESS_FOR_EACH.find(text)?.let { m ->
            val per = Numbers.mana(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            costChange = Value.Times(Value.Count(count), -per)
            text = text.removeRange(m.range).trim()
        }
        val instructions = instructions(text)?.let { i ->
            if (cost.parts.any { (it as? CostPart.Mana)?.cost?.symbols?.any { s -> s is arcanum.api.ManaSymbol.Variable } == true })
                i.copy(announced = listOf("X")) else i
        } ?: return null
        return Ability.Activated(cost, instructions, arcanum.api.ActivationRules(timing = timing, costChange = costChange, condition = condition))
    }

    /** "Whenever [event], [if condition,] [effect]." (CR 603.1) */
    private fun triggered(line: String): Ability? {
        val m = WHENEVER.matchEntire(line) ?: return null
        // chunk 31: "When this creature enters or dies", "Whenever one or more artifacts you control enter"
        val (events, oneOrMore) = Events.events(m.groupValues[2], self) ?: return null
        var rest = m.groupValues[3]
        // chunk 32: "This ability triggers only once each turn."
        var perTurn: Int? = null
        TRIGGERS_ONLY_ONCE_EACH_TURN.find(rest)?.let { perTurn = 1; rest = rest.removeRange(it.range).trim() }
        val conditional = INTERVENING_IF.matchEntire(rest)
        val interveningIf = conditional?.let { Conditions.condition(it.groupValues[1], self) ?: return null }
        triggerIt = itOf(events)
        var instructions = instructions(conditional?.groupValues?.get(2) ?: rest)
        triggerIt = null
        // chunk 33: X in this permanent's enters trigger is the X chosen for the spell it was (CR 107.3m)
        if (events == listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)) && !rest.contains("where X is"))
            instructions = instructions?.withX(Value.CastX())
        return Ability.Triggered(arcanum.api.Trigger.On(events, oneOrMore = oneOrMore), instructions ?: return null,
            interveningIf = interveningIf, perTurn = perTurn)
    }

    /** chunk 31: "it" in a trigger on this object moving: the permanent that entered, or the new card in a graveyard
     * (CR 400.7). */
    private fun itOf(events: List<EventPattern>): Pair<Objects, Zone?>? {
        val move = events.singleOrNull() as? EventPattern.ZoneChange ?: return null
        if (move.objects != ObjectFilter.Is(Objects.Self)) return null
        return when (move.to) {
            Zone.Battlefield -> Objects.Self to Zone.Battlefield
            Zone.Graveyard -> Objects.EventObject(arcanum.api.EventRole.After) to Zone.Graveyard
            else -> null
        }
    }

    /** Cost parts separated by ", " (CR 602.1a). */
    fun cost(text: String): Cost? {
        val parts = text.split(", ").map { costPart(it) ?: return null }
        if (parts.isEmpty()) return null
        return Cost(parts.flatten())
    }

    private fun costPart(text: String): List<CostPart>? {
        if (text == "{T}") return listOf(CostPart.TapSelf)
        ManaCost.parse(text)?.let { return listOf(CostPart.Mana(it)) }
        PAY_LIFE.matchEntire(text)?.let { return listOf(CostPart.PayLife(Value.Fixed(it.groupValues[1].toInt()))) }
        DISCARD_COST.matchEntire(text)?.let { m ->
            if (isSelf(m.groupValues[1])) return listOf(CostPart.Discard(CostObjects.Self))
            if (m.groupValues[1] == "your hand") return listOf(CostPart.Discard(CostObjects.These(Objects.Each(yourHand))))
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.Discard(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        // "Tap two untapped Elves you control": tapping as a cost can only tap untapped permanents you control
        // (CR 602.2b, 701.26a), so the canonical form names only the kind (as crew does).
        TAP_UNTAPPED.matchEntire(text)?.let { m ->
            val count = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2].removeSuffix(" you control")) ?: return null
            return listOf(CostPart.TapObjects(CostObjects.Choose(filter, count = Value.Fixed(count))))
        }
        PUT_COUNTER_ON_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.PutCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))
        }
        RETURN_TO_HAND_COST.matchEntire(text)?.let { m ->
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.ReturnToHand(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        EXILE_FROM_HAND_COST.matchEntire(text)?.let { m ->
            return listOf(CostPart.Exile(CostObjects.Choose(Filters.noun(m.groupValues[1]) ?: return null), from = Zone.Hand))
        }
        // chunk 30: "Exile eight other cards from your graveyard", "Exile a creature card from your graveyard"
        EXILE_FROM_GRAVEYARD_COST.matchEntire(text)?.let { m ->
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            val filter = Filters.noun(noun.removePrefix("other ")) ?: return null
            val chosen = if (noun.startsWith("other ")) Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter) else filter
            return listOf(CostPart.Exile(CostObjects.Choose(chosen, count = Value.Fixed(count)), from = Zone.Graveyard))
        }
        // chunk 31: "exile it" in a trigger on this object moving
        if (text == "Exile it") triggerIt?.let { (it, zone) -> return listOf(CostPart.Exile(CostObjects.These(it), from = zone ?: return null)) }
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            if (isSelf(what)) return listOf(CostPart.Sacrifice(CostObjects.Self))
            // chunk 32: "Sacrifice two Foods"
            val (count, noun) = counted(what) ?: return null
            return listOf(CostPart.Sacrifice(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        return null
    }

    /** "a card" → 1 and "card"; "two cards" → 2 and "cards". */
    private fun counted(phrase: String): Pair<Int, String>? {
        val first = phrase.substringBefore(' ')
        val count = if (first == "a" || first == "an") 1 else Numbers.count(first) ?: return 1 to phrase
        return count to phrase.substringAfter(' ')
    }

    /** The effect sentences of a spell or ability, in order. */
    fun instructions(text: String, seedTargets: List<TargetSlot> = emptyList()): Instructions? {
        // chunk 30: "…, where X is [a number]" defines X for the whole ability (CR 107.3c)
        val whereX = WHERE_X.find(text)
        val x = whereX?.let { Values.value(it.groupValues[1], exiledCard) ?: return null }
        val context = Context()
        context.targets += seedTargets
        context.it = triggerIt?.first ?: Objects.Self
        val steps = mutableListOf<Step>()
        for (sentence in sentences(if (whereX == null) text else text.removeRange(whereX.range))) {
            val modified = modify(sentence, steps, context)
            if (modified != null) { steps.clear(); steps += modified; continue }
            steps += effect(sentence, context) ?: return null
        }
        val result = Instructions(targets = context.targets, steps = steps, replacements = context.replacements)
        return if (x == null) result else result.withX(x)
    }

    /** Sentences that change the step before them ("If this spell was kicked, it deals 4 damage instead.", "It can't be
     * regenerated."). */
    private fun modify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        // chunk 31: "If it's a land card, put it onto the battlefield. Otherwise, put that card into your hand."
        OTHERWISE.matchEntire(sentence)?.let { m ->
            val last = steps.lastOrNull() as? Step.If ?: return null
            if (last.otherwise.isNotEmpty()) return null
            val otherwise = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return steps.dropLast(1) + last.copy(otherwise = otherwise)
        }
        // chunk 33: "If this creature was kicked, copy that spell twice instead." after a delayed copy trigger
        COPY_TWICE_IF_KICKED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            val delayed = (steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateDelayedTrigger ?: return null
            val twice = delayed.copy(instructions = delayed.instructions.copy(steps = listOf(Step.Repeat(Value.Fixed(2), delayed.instructions.steps))))
            return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind),
                then = listOf(Step.Do(twice)), otherwise = listOf(Step.Do(delayed)))
        }
        // chunk 33: "You may choose new targets for the copies." (CR 707.10c)
        if (sentence == "You may choose new targets for the copies." && steps.isNotEmpty())
            return steps.map { withNewTargets(it) ?: return null }
        val previous = (steps.lastOrNull() as? Step.Do)?.effect
        // chunk 32: "Copy target instant or sorcery spell. You may choose new targets for the copy." (CR 707.10c)
        if (previous is Effect.CopySpell && sentence == "You may choose new targets for the copy.")
            return steps.dropLast(1) + Step.Do(previous.copy(newTargets = true))
        // chunk 32: "The token enters tapped and attacking." (CR 508.4: its controller chooses what it attacks)
        if (previous is Effect.CreateToken && sentence == "The token enters tapped and attacking.")
            return steps.dropLast(1) + Step.Do(previous.copy(tapped = true, attacking = arcanum.api.AttackTarget.Chosen))
        // chunk 30: "Each opponent discards a card. For each opponent who can't, you draw a card." Each opponent's
        // discard is a mandatory payment, so "can't" is known per opponent (CR 118.12).
        if (previous is Effect.Discard && previous.players == Players.Each(PlayerFilter.Opponent) && previous.objects == null &&
            sentence == "For each opponent who can't, you draw a card.") {
            val discard = CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = previous.count))
            return steps.dropLast(1) + Step.ForEach(arcanum.api.Each.Players(previous.players), bind = "opponent", together = true, steps = listOf(
                Step.Pay(Cost(listOf(discard)), payer = Players.Bound("opponent"), mandatory = true, bind = "discarded"),
                Step.If(arcanum.api.Condition.CouldNot("discarded"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))))))
        }
        val last = steps.lastOrNull() as? Step.Do ?: return null
        val effect = last.effect
        if (effect is Effect.DealDamage) {
            KICKED_DEALS_INSTEAD.matchEntire(sentence)?.let { m ->
                return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind),
                    then = listOf(Step.Do(effect.copy(amount = Value.Fixed(m.groupValues[1].toInt())))), otherwise = listOf(last))
            }
            if (sentence == "A creature dealt damage this way can't be regenerated this turn.") {
                val damaged = effect.to.target?.let { Objects.Target(it) } ?: effect.to.objects ?: return null
                return steps + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeRegenerated),
                    arcanum.api.Affected.Objects(Objects.Where(damaged, ObjectFilter.Type(arcanum.api.CardType.Creature))), arcanum.api.Duration.EndOfTurn))
            }
        }
        if (effect is Effect.Destroy && sentence in setOf("It can't be regenerated.", "A creature destroyed this way can't be regenerated.")) {
            return steps.dropLast(1) + Step.Do(effect.copy(noRegeneration = true))
        }
        return null
    }

    private class Context {
        var flip = false
        val replacements = mutableListOf<Mechanic.Replacement>()
        val targets = mutableListOf<TargetSlot>()
        /** "any target" (CR 115.4): creature, planeswalker or battle, or player. */
        fun anyTarget() {
            targets += TargetSlot("any", arcanum.api.TargetKind.Either(ObjectFilter.Or(listOf(
                ObjectFilter.Type(arcanum.api.CardType.Creature), ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                ObjectFilter.Type(arcanum.api.CardType.Battle))), PlayerFilter.Any))
        }

        /** "target player" */
        fun targetPlayer(): Players {
            if (targets.none { it.name == "player" }) targets += TargetSlot("player", arcanum.api.TargetKind.Players(PlayerFilter.Any))
            return Players.Target("player")
        }

        fun target(noun: String, filter: ObjectFilter): Objects {
            targets += TargetSlot(noun, TargetKind.Objects(filter))
            return Objects.Target(noun)
        }

        // chunk 31: what "it" / "that card" refers to; the zone it is in; the cards looked at or revealed and the one
        // chosen from among them.
        var it: Objects? = null
        var itZone: Zone? = null
        var seen: String? = null
        var chosen: String? = null
        /** chunk 33: the player whose hand "it" is ("look at target opponent's hand. You may exile a card from it"). */
        var lastPlayer: Players? = null

        /** chunk 33: "target opponent" */
        fun targetOpponent(): Players {
            if (targets.none { it.name == "opponent" }) targets += TargetSlot("opponent", arcanum.api.TargetKind.Players(PlayerFilter.Opponent))
            return Players.Target("opponent").also { lastPlayer = it }
        }

        /** "That player": the controller of the last object target. */
        fun lastTargetController(): Players? = targets.lastOrNull()?.let {
            if (it.what is arcanum.api.TargetKind.Players) Players.Target(it.name) else Players.ControllerOf(Objects.Target(it.name))
        }
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
        // "Draw two cards. Then discard a card …": a sentence that starts with "Then" follows the one before it.
        if (sentence.startsWith("Then ")) return effect(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() }, context)
        enters(sentence, context)?.let { return it }
        PUT_FROM_HAND_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n, n), bind = "put"),
                Step.Do(Effect.Move(Objects.Bound("put"), Destination(Zone.Library, libraryPosition = 0))))
        }
        PLAYERS_DRAW.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(players, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))
            if (m.groupValues[3].isNotEmpty())
                steps += Step.Do(Effect.Discard(players, Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null), random = m.groupValues[4].isNotEmpty()))
            if (m.groupValues[5].isNotEmpty()) steps += Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[5].toInt())))
            return steps
        }
        EACH_PLAYER_DISCARDS_HAND_DRAWS.matchEntire(sentence)?.let { m ->
            val each = Players.Each(PlayerFilter.Any)
            return listOf(
                Step.Do(Effect.Discard(each, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)))),
                Step.Do(Effect.Draw(each, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD_UNLESS_ATTACKED.matchEntire(sentence)?.let { m ->
            val attacked = arcanum.api.Condition.HappenedThisTurn(arcanum.api.EventPattern.Attack(attacker = ObjectFilter.ControlledBy(PlayerFilter.You)))
            return listOf(Step.If(arcanum.api.Condition.Not(attacked),
                then = listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))))
        }
        SCRY_OR_SURVEIL.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            return listOf(Step.Do(if (m.groupValues[1] == "Scry") Effect.Scry(Players.You, n) else Effect.Surveil(Players.You, n)))
        }
        SEARCH_TO_TOP.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card").ifEmpty { "card" }) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found")),
                Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Library, libraryPosition = 0))))
        }
        SEARCH_PUT_SHUFFLE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card")) ?: return null
            // chunk 32: "reveal it, ", "into your graveyard"
            val to = when (m.groupValues[3]) {
                "into your hand" -> Destination(Zone.Hand)
                "into your graveyard" -> Destination(Zone.Graveyard)
                else -> Destination(Zone.Battlefield)
            }
            val steps = mutableListOf<Step>(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), to)))
            if (m.groupValues[4].isNotEmpty()) steps += Step.Do(Effect.Discard(Players.You, random = true))
            steps += Step.Do(Effect.Shuffle(Players.You))
            return steps
        }
        if (sentence == "Look at target player's hand.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.LookAt(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))))))
        }
        TARGET_PLAYER_MILLS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        LOOK_PUT_BACK.matchEntire(sentence)?.let { m ->
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)), bind = "seen")),
                Step.Do(Effect.Reorder(Objects.Bound("seen"))))
        }
        if (sentence == "You may shuffle.") return listOf(Step.May(listOf(Step.Do(Effect.Shuffle(Players.You)))))
        if (sentence == "Investigate.") return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Investigate())))
        TARGET_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        RETURN_UP_TO_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            context.targets += TargetSlot("cards", TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
            return listOf(Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Hand))))
        }
        YOU_LOSE_LIFE.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.LoseLife(Players.You, Value.Fixed(m.groupValues[1].toInt())))) }
        SELF_DEALS_DAMAGE_TO_EACH.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val amount = Value.Fixed(m.groupValues[2].toInt())
            val to = Filters.players(m.groupValues[3])?.let { arcanum.api.Recipients(players = it) }
                ?: arcanum.api.Recipients(objects = Objects.Each(Filters.noun(m.groupValues[3].removePrefix("each ")) ?: return null))
            return listOf(Step.Do(Effect.DealDamage(to, amount)))
        }
        EXILE_THEN_RETURN.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(
                Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))
        }
        TARGET_PLAYER_DRAWS.matchEntire(sentence)?.let { m ->
            val player = context.targetPlayer()
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(player, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
            if (m.groupValues[2].isNotEmpty()) steps += Step.Do(Effect.Discard(player, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)))
            return steps
        }
        TARGET_PLAYER_DISCARDS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        TARGET_PLAYER_CREATES.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = context.targetPlayer())))
        }
        RETURN_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Hand))))
        }
        EACH_PLAYER_SHUFFLES_HAND_AND_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val each = Players.Each(PlayerFilter.Any)
            return listOf(
                Step.Do(Effect.Move(Objects.Each(ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Graveyard)))),
                    Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(each)),
                Step.Do(Effect.Draw(each, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        EXILE_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile))))
        }
        LOOK_PUT_N_INTO_HAND_REST_BOTTOM.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            val kept = Numbers.count(m.groupValues[2]) ?: return null
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen)), bind = "seen")),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(kept), Value.Fixed(kept), from = Objects.Bound("seen")), bind = "kept"),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1))))
        }
        LOOK_HAND_BOTTOM_EXILE.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            fun one(from: Objects, bind: String) = Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = from), bind = bind)
            val rest = Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand"))
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen)), bind = "seen")),
                one(Objects.Bound("seen"), "toHand"),
                Step.Do(Effect.Move(Objects.Bound("toHand"), Destination(Zone.Hand))),
                one(rest, "toBottom"),
                Step.Do(Effect.Move(Objects.Bound("toBottom"), Destination(Zone.Library, libraryPosition = -1))),
                Step.Do(Effect.Move(Objects.Except(rest, Objects.Bound("toBottom")), Destination(Zone.Exile), postBind = "exiled")))
        }
        if (sentence == "You may play the exiled card this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        SELF_DEALS_DAMAGE_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val amount = if (m.groupValues[2] == "X") Value.Announced() else Value.Fixed(m.groupValues[2].toInt())
            val to = when (val what = m.groupValues[3]) {
                "any target" -> { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                else -> {
                    val noun = what.removePrefix("target ").takeIf { what.startsWith("target ") } ?: return null
                    arcanum.api.Recipients(objects = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null))
                }
            }
            return listOf(Step.Do(Effect.DealDamage(to, amount)))
        }
        DESTROY_TARGET_IF.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val target = if (m.groupValues[1] == "target") context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
                else context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target,
                ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, Value.Fixed(m.groupValues[3].toInt()))),
                then = listOf(Step.Do(Effect.Destroy(target)))))
        }
        DESTROY_EACH.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Destroy(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null))))
        }
        EXILE_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            val exile = Step.Do(Effect.Move(target, Destination(Zone.Exile)))
            if (m.groupValues[2].isEmpty()) return this.exile(target) // chunk 33: linked to "the exiled card"
            // Converge: "if its mana value is less than or equal to the number of colors of mana spent to cast this spell"
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.ManaSpent(distinctColors = true))), then = listOf(exile)))
        }
        RETURN_TARGET_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            return listOf(Step.Do(Effect.Move(context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null), Destination(Zone.Hand))))
        }
        ITS_CONTROLLER_GAINS_LIFE_EQUAL_TO_POWER.matchEntire(sentence)?.let {
            val target = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.GainLife(Players.ControllerOf(target),
                Value.Of(arcanum.api.NumericProperty.Power, target, arcanum.api.Combine.Single))))
        }
        (UNTAP_UP_TO.matchEntire(sentence) ?: YOU_UNTAP_UP_TO.matchEntire(sentence))?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(n)), bind = "untapped"),
                Step.Do(Effect.Untap(Objects.Bound("untapped"))))
        }
        COUNTER_TARGET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val spell = context.target(Filters.slotName(m.groupValues[1]), filter)
            val counter = Step.Do(Effect.Counter(spell))
            if (m.groupValues[2].isEmpty()) return listOf(counter)
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return listOf(Step.Unless(Players.ControllerOf(spell), Cost.mana(cost), listOf(counter)))
        }
        COUNTERED_THIS_WAY.matchEntire(sentence)?.let { m ->
            val spell = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            val to = when (m.groupValues[1]) {
                "exile it" -> Destination(Zone.Exile)
                "put it on top of its owner's library" -> Destination(Zone.Library, libraryPosition = 0)
                "put it into its owner's hand" -> Destination(Zone.Hand)
                else -> return null
            }
            // CR 614.15: a replacement of this spell's own effect, applied while it resolves
            context.replacements += Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(spell), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(to)))
            return emptyList()
        }
        NEXT_MAIN_PHASE_ADD_MANA_VALUE.matchEntire(sentence)?.let {
            val spell = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(listOf(EventPattern.StepBegins(arcanum.api.TurnStep.PrecombatMain, PlayerFilter.You),
                    EventPattern.StepBegins(arcanum.api.TurnStep.PostcombatMain, PlayerFilter.You))),
                Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!,
                    times = Value.Of(arcanum.api.NumericProperty.ManaValue, spell, arcanum.api.Combine.Single))))))))))
        }
        ADD_X_ANY_COMBINATION.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Announced(), combination = true)))))
        }
        ADD_ANY_OF_REMEMBERED_COLORS.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1),
                colors = listOf(ColorRef.Of(Objects.Remembered(IMPRINTED))))))))
        }
        FLIP_A_COIN.matchEntire(sentence)?.let {
            context.flip = true
            return listOf(Step.Do(Effect.FlipCoin(bind = "flip")))
        }
        IF_YOU_LOSE_THE_FLIP.matchEntire(sentence)?.let { m ->
            if (!context.flip) return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.WonFlip("flip")), then = then))
        }
        SELF_DEALS_DAMAGE_TO_YOU.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.You), Value.Fixed(m.groupValues[2].toInt()))))
        }
        YOU_MAY_PAY_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.Pay(Cost.mana(cost), bind = "paid"), Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        UNTAP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Untap(Objects.Self)))
        }
        YOU_MAY_EXILE_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember(IMPRINTED, Objects.Bound("exiled"))))
        }
        ADD_MANA_RESTRICTED.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = Filters.all(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), spells)))))
        }
        ADD_MANA_FOR_EACH.matchEntire(sentence)?.let { m ->
            val symbols = ManaCost.parse(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(symbols, times = Value.Count(count))))))
        }
        ADD_MANA.matchEntire(sentence)?.let { m -> mana(m.groupValues[1])?.let { return listOf(Step.Do(Effect.AddMana(ManaProduction(it)))) } }
        DRAW.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        CREATE_TOKEN.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count))))
        }
        THAT_PLAYER_MAY_SEARCH.matchEntire(sentence)?.let { m ->
            val player = context.lastTargetController() ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.May(player = player, steps = listOf(
                Step.Do(Effect.Search(player, filter, bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty()))),
                Step.Do(Effect.Shuffle(player)))))
        }
        PLAYERS_GAIN_LIFE.matchEntire(sentence)?.let { m ->
            Filters.players(m.groupValues[1])?.let { return listOf(Step.Do(Effect.GainLife(it, Value.Fixed(m.groupValues[2].toInt())))) }
        }
        DESTROY_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val filter = Filters.noun(noun) ?: return null
            return listOf(Step.Do(Effect.Destroy(context.target(Filters.slotName(noun), filter))))
        }
        // chunk 30: "You may discard a card. If you do, draw a card." (CR 118.12: an action that is a cost)
        YOU_MAY_COST_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            costPart(m.groupValues[1].replaceFirstChar { it.uppercase() })?.let { parts ->
                val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
                return listOf(Step.Pay(Cost(parts), bind = "paid"), Step.If(arcanum.api.Condition.Did("paid"), then = then))
            }
        }
        // chunk 31: "You may exile it. When you do, [reflexive trigger]." (CR 603.12)
        YOU_MAY_COST_WHEN_YOU_DO.matchEntire(sentence)?.let { m ->
            costPart(m.groupValues[1].replaceFirstChar { it.uppercase() })?.let { parts ->
                val reflexive = instructions(m.groupValues[2].replaceFirstChar { it.uppercase() }) ?: return null
                return listOf(Step.Pay(Cost(parts), bind = "paid"),
                    Step.If(arcanum.api.Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(reflexive)))))
            }
        }
        // chunk 31: "You may [effect]." with nothing depending on it (CR 608.2d)
        YOU_MAY.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context)?.let { return listOf(Step.May(it)) }
        }
        // "[A], then [B].": tried last, after the wordings that contain ", then" themselves.
        THEN.matchEntire(sentence)?.let { m ->
            val first = effect(m.groupValues[1] + ".", context) ?: return null
            val second = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return first + second
        }
        return null
    }

    /** chunks 30–33: effect wordings of creatures' enters (and leaves) abilities; null when none reads [sentence]. */
    private fun enters(sentence: String, context: Context): List<Step>? {
        // chunk 30
        MILL.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))) }
        if (sentence == "Draw X cards.") return listOf(Step.Do(Effect.Draw(Players.You, Value.Announced())))
        PLAYERS_LOSE_LIFE.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[2].toInt()))))
            if (m.groupValues[3].isNotEmpty()) steps += Step.Do(Effect.GainLife(Players.You, Value.Fixed(m.groupValues[3].toInt())))
            return steps
        }
        PLAYERS_DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Filters.players(m.groupValues[1]) ?: return null, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))
        }
        if (sentence == "Discard your hand.") return listOf(Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand))))
        PUT_COUNTERS_ON.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(Objects.Self, kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }

        // chunk 31
        RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val to = Destination(Zone.Battlefield, tapped = m.groupValues[3].isNotEmpty())
            val what = m.groupValues[2]
            if (m.groupValues[1].isEmpty()) return if (isSelf(what)) listOf(Step.Do(Effect.Move(Objects.Self, to))) else null
            val filter = Filters.all(Filters.noun(what) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val objects = if (m.groupValues[1] == "target ") context.target(Filters.slotName(what), filter) else Objects.Each(filter)
            return listOf(Step.Do(Effect.Move(objects, to)))
        }
        if (sentence == "Return it to your hand.") return listOf(Step.Do(Effect.Move(context.it ?: return null, Destination(Zone.Hand))))
        MAY_PUT_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, yourHand)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty()))))
        }
        if (sentence == "Reveal the top card of your library.") {
            context.it = Objects.Bound("revealed")
            return listOf(Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(1)), bind = "revealed")))
        }
        IF_ITS_A.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, filter), then = then))
        }
        PUT_IT.matchEntire(sentence)?.let { m ->
            val to = if (m.groupValues[1] == "into your hand") Destination(Zone.Hand) else Destination(Zone.Battlefield)
            return listOf(Step.Do(Effect.Move(context.it ?: return null, to)))
        }
        LOOK_PUT_UP_TO_ONE_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = if (m.groupValues[1] == "X") Value.Announced() else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n), bind = "seen")),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound("seen")), bind = "kept"),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Library, libraryPosition = 0))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        LOOK_OR_REVEAL_TOP.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            context.seen = "seen"
            val cards = Objects.LibraryCards(Players.You, n)
            return listOf(Step.Do(if (m.groupValues[1] == "Look at") Effect.LookAt(cards, bind = "seen") else Effect.Reveal(cards, bind = "seen")))
        }
        MAY_FROM_AMONG_INTO_HAND.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            context.chosen = "chosen"
            return listOfNotNull(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound(seen)), bind = "chosen"),
                if (m.groupValues[1] == "reveal") Step.Do(Effect.Reveal(Objects.Bound("chosen"))) else null,
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        PUT_THE_REST.matchEntire(sentence)?.let { m ->
            val rest = Objects.Except(Objects.Bound(context.seen ?: return null), Objects.Bound(context.chosen ?: return null))
            val to = if (m.groupValues[1] == "into your graveyard") Destination(Zone.Graveyard)
                else Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
            return listOf(Step.Do(Effect.Move(rest, to)))
        }
        WIN_IF_X_AT_LEAST.matchEntire(sentence)?.let { m ->
            val other = Values.value(m.groupValues[1], exiledCard) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(), arcanum.api.Comparison.AtLeast, other),
                then = listOf(Step.Do(Effect.WinGame(Players.You)))))
        }
        // "The flashback cost is equal to its mana cost" (CR 702.34a: cast from the graveyard for that cost)
        GAINS_FLASHBACK.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        CHOOSE_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            context.it = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            context.itZone = if (noun.endsWith(" in your graveyard")) Zone.Graveyard else null
            return emptyList()
        }
        if (sentence == "You may cast that card this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(context.itZone ?: return null)),
                arcanum.api.Affected.Objects(context.it ?: return null), arcanum.api.Duration.EndOfTurn)))
        }
        GRAVEYARD_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            context.targets += TargetSlot("player", TargetKind.Players(PlayerFilter.Any),
                min = Value.Fixed(if (m.groupValues[1] == "Target") 1 else 0))
            val graveyard = Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))
            return listOf(Step.Do(Effect.Move(Objects.Each(graveyard), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }

        // chunk 32
        RETURN_YOURS_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        TARGET_DEALS_DAMAGE_TO_ITSELF.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = target), Value.Fixed(m.groupValues[2].toInt()), source = target)))
        }
        UNTAP_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val chosen = if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)
            return listOf(Step.Do(Effect.Untap(context.target(Filters.slotName(noun), chosen))))
        }
        GAIN_AND_GET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(keywords), Mechanic.ModifyPowerToughness(ptChange(m.groupValues[3]) ?: return null, ptChange(m.groupValues[4]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
        }
        COPY_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val spell = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.CopySpell(spell, newTargets = false)))
        }
        CREATE_COPY_OF_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val chosen = if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(context.target(Filters.slotName(noun), chosen)))))
        }

        // chunk 33
        EACH_SACRIFICES.matchEntire(sentence)?.let { m ->
            val player = m.groupValues[1]
            val who = if (player == "player") PlayerFilter.Any else PlayerFilter.Opponent
            val filter = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound(player))))
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(who)), bind = player, together = true, steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "sacrificed", chooser = Players.Bound(player)),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))))
        }
        // "gain control of": a control-changing effect with no end (CR 613.1b)
        GAIN_CONTROL_OF_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), arcanum.api.Affected.Objects(target),
                arcanum.api.Duration.Indefinitely)))
        }
        IF_THAT_IS_ATTACH_IT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val slot = context.targets.lastOrNull()?.takeIf { it.name == m.groupValues[1] } ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val target = Objects.Target(slot.name)
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target, filter),
                then = listOf(Step.Do(Effect.Attach(target, arcanum.api.Recipients(objects = Objects.Self))))))
        }
        // "copy the next … spell you cast this turn when you cast it": a delayed trigger that fires once (CR 603.7)
        COPY_NEXT_SPELL.matchEntire(sentence)?.let { m ->
            val spell = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.Cast(spell, by = PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(), newTargets = false)))),
                duration = arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "Look at target opponent's hand.") {
            return listOf(Step.Do(Effect.LookAt(Objects.Each(handOf(context.targetOpponent())))))
        }
        TARGET_REVEALS_HAND_YOU_CHOOSE.matchEntire(sentence)?.let { m ->
            val opponent = context.targetOpponent()
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, handOf(opponent))
            context.it = Objects.Bound("chosen")
            return listOf(
                Step.Do(Effect.Reveal(Objects.Each(handOf(opponent)))),
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen"))
        }
        if (sentence == "Exile that card.") return exile(context.it ?: return null)
        MAY_EXILE_FROM_IT_UNTIL_LEAVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, handOf(context.lastPlayer ?: return null))
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), untilLeaves = Objects.Self)))
        }
        EXILED_TO_HAND.matchEntire(sentence)?.let { return listOf(Step.Do(Effect.Move(exiledCard ?: return null, Destination(Zone.Hand)))) }
        EXILED_OWNER_CREATES.matchEntire(sentence)?.let { m ->
            val exiled = exiledCard ?: return null
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.OwnerOf(exiled))))
        }
        if (sentence == "The exiled card's owner may cast that card without paying its mana cost.") {
            val exiled = exiledCard ?: return null
            val owner = Players.OwnerOf(exiled)
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(exiled, withoutPaying = true, caster = owner))), player = owner))
        }
        UP_TO_ONE_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun) ?: return null
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(if (m.groupValues[2].isEmpty()) filter
                else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)), min = Value.Fixed(0))
            return if (m.groupValues[1] == "Exile") exile(Objects.Target(name)) else listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        DAMAGE_EQUAL_TO_POWER.matchEntire(sentence)?.takeIf { it.groupValues[1] == "It" || isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = "any"),
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single))))
        }
        OPPONENTS_CANT_CAST.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(Filters.noun(m.groupValues[1]) ?: return null)),
                arcanum.api.Affected.Players(Players.Each(PlayerFilter.Opponent)), arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "That creature's owner can't cast spells with the same name as that creature until your next turn.") {
            val creature = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.CantCast(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), ObjectFilter.SameNameAs(creature)))),
                arcanum.api.Affected.Players(Players.OwnerOf(creature)), arcanum.api.Duration.UntilYourNextTurn)))
        }
        YOU_AND_TARGET_OPPONENT_DRAW.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val players = Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(context.targetOpponent()))))
            return listOf(Step.Do(Effect.Draw(players, n)))
        }
        return null
    }

    /** chunk 33: [step] with its spell copies (in delayed triggers, ifs and repeats too) allowed new targets. */
    private fun withNewTargets(step: Step): Step? = when (step) {
        is Step.If -> step.copy(then = step.then.map { withNewTargets(it) ?: return null }, otherwise = step.otherwise.map { withNewTargets(it) ?: return null })
        is Step.Repeat -> step.copy(steps = step.steps.map { withNewTargets(it) ?: return null })
        is Step.Do -> when (val e = step.effect) {
            is Effect.CopySpell -> Step.Do(e.copy(newTargets = true))
            is Effect.CreateDelayedTrigger -> Step.Do(e.copy(instructions = e.instructions.copy(steps = e.instructions.steps.map { withNewTargets(it) ?: return null })))
            else -> null
        }
        else -> null
    }

    /** chunk 33: the cards in [player]'s hand. */
    private fun handOf(player: Players) = Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))

    /** chunk 33: exile [objects]; when a later ability refers to "the exiled card", keep them as card memory (CR 607.2a). */
    private fun exile(objects: Objects): List<Step> =
        if (exiledCard == Objects.Remembered(EXILED_KEY)) listOf(
            Step.Do(Effect.Move(objects, Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember(EXILED_KEY, Objects.Bound("exiled"))))
        else listOf(Step.Do(Effect.Move(objects, Destination(Zone.Exile))))

    /** chunk 32: "+3", "-1", "+X" in "+X/+X". */
    private fun ptChange(text: String): Value? {
        val sign = if (text.startsWith("-")) -1 else 1
        val number = text.drop(1)
        if (number == "X") return if (sign > 0) Value.Announced() else Value.Times(Value.Announced(), -1)
        return Value.Fixed(sign * (number.toIntOrNull() ?: return null))
    }

    /** "{C}", "{B}{B}", "{R} or {G}", "one mana of any color", "three mana of any one color" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        ANY_COMBINATION.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), combination = true) }
        ANY_COLOR.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null)) }
        ManaCost.parse(text)?.let { return ManaKind.Fixed(it) }
        val colors = text.split(Regex(""",? or |, """)).map { symbol -> ManaCost.parse(symbol)?.symbols?.singleOrNull()
            ?.let { (it as? arcanum.api.ManaSymbol.Colored)?.color } ?: return null }
        return ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })
    }

    /** Sentences in order; a "Spend this mana only …" sentence stays with the mana it restricts. */
    private fun sentences(text: String): List<String> {
        val raw = SENTENCE_END.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.endsWith(".")) it else "$it." }.map { it.replaceFirstChar { c -> c.uppercase() } }
        return raw.fold(mutableListOf()) { acc, sentence ->
            if ((sentence.startsWith("Spend this mana only") || sentence.startsWith("If you do,") ||
                    sentence.startsWith("When you do,") || sentence.startsWith("The flashback cost is") || // chunk 31
                    (sentence.startsWith("Put ") && acc.lastOrNull()?.startsWith("Look at the top") == true)) && acc.isNotEmpty())
                acc[acc.size - 1] = acc.last() + " " + sentence
            else acc += sentence
            acc
        }
    }

    private companion object {
        val SENTENCE_END = Regex("""(?<=\.)\s+""")
        val ABILITY_WORD = Regex("""([A-Z][A-Za-z' ]+) — (.+)""")
        const val IMPRINTED = "imprinted"
        val LABELS = mapOf("boast" to arcanum.api.AbilityLabel.Boast, "solved" to arcanum.api.AbilityLabel.Solved,
            "exhaust" to arcanum.api.AbilityLabel.Exhaust, "max speed" to arcanum.api.AbilityLabel.MaxSpeed,
            "power-up" to arcanum.api.AbilityLabel.PowerUp)
        val ALTERNATIVE_COST = Regex("""(?:If (.+?), )?[Yy]ou may (.+) rather than pay this spell's mana cost\.""")
        val X_AT_LEAST_UNCOUNTERABLE = Regex("""If X is (\d+) or more, this spell can't be countered and the damage can't be prevented\.""")
        val ADDITIONAL_COST = Regex("""As an additional cost to cast this spell, (.+)\.""")
        val SELF_DEALS_N = Regex("""(.+) deals (\d+) damage""")
        val SELF_DEALS_DAMAGE_TO = Regex("""(.+?) deals (\d+|X) damage to (any target|target .+)\.""")
        val KICKED_DEALS_INSTEAD = Regex("""If this spell was kicked, it deals (\d+) damage instead\.""")
        val DESTROY_TARGET_IF = Regex("""Destroy (target|that) (.+?) if it has mana value (\d+) or less\.""")
        val DESTROY_EACH = Regex("""Destroy each (.+)\.""")
        val EXILE_TARGET = Regex("""Exile target (.+?)( if its mana value is less than or equal to the number of colors of mana spent to cast this spell)?\.""")
        val RETURN_TARGET_TO_HAND = Regex("""Return target (.+) to its owner's hand\.""")
        val ITS_CONTROLLER_GAINS_LIFE_EQUAL_TO_POWER = Regex("""(?:Its|That creature's) controller gains life equal to its power\.""")
        val UNTAP_UP_TO = Regex("""Untap up to (\w+) (.+)\.""")
        val RETURN_TO_HAND_COST = Regex("""Return (.+) to (?:its|their) owners?'s? hands?""")
        val EXILE_FROM_HAND_COST = Regex("""Exile an? (.+) from your hand""")
        val COUNTER_TARGET = Regex("""Counter target (.+?)(?: unless its controller pays (\{.+\}))?\.""")
        val COUNTERED_THIS_WAY = Regex("""If that spell is countered this way, (exile it|put it on top of its owner's library|put it into its owner's hand) instead of .+\.""")
        val NEXT_MAIN_PHASE_ADD_MANA_VALUE = Regex("""At the beginning of your next main phase, add an amount of \{C\} equal to that spell's mana value\.""")
        val yourHand = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
        val INSTEAD_IF = Regex("""(?:([A-Z][A-Za-z ]+) — )?(.+) instead if (.+)\.""")
        val DOESNT_UNTAP = Regex("""(.+) doesn't untap during your untap step\.""")
        val EQUIPPED_HAS_QUOTED = Regex("""Equipped creature has "(.+)"""")
        val WOULD_ENTER_DISCARD_INSTEAD = Regex("""If (.+) would enter, you may discard an? (.+) instead\. If you do, put .+ onto the battlefield\. If you don't, put it into its owner's graveyard\.""")
        val ACTIVATE_ONLY_AS_INSTANT = Regex("""Activate only as an instant\.""")
        val ADD_X_ANY_COMBINATION = Regex("""Add X mana in any combination of colors\.""")
        val ADD_ANY_OF_REMEMBERED_COLORS = Regex("""Add one mana of any of the exiled card's colors\.""")
        val FLIP_A_COIN = Regex("""Flip a coin\.""")
        val IF_YOU_LOSE_THE_FLIP = Regex("""If you lose the flip, (.+)""")
        val SELF_DEALS_DAMAGE_TO_YOU = Regex("""(.+) deals (\d+) damage to you\.""")
        val YOU_MAY_PAY_IF_YOU_DO = Regex("""You may pay (\{.+?\})\. If you do, (.+)""")
        val UNTAP = Regex("""Untap (.+)\.""")
        val YOU_MAY_EXILE_FROM_HAND = Regex("""You may exile an? (.+) from your hand\.""")
        val ACTIVATE_ONLY_IF = Regex("""Activate only if (.+?)\.""")
        val TAP_UNTAPPED = Regex("""Tap (an|two|three|four|five) untapped (.+)""")
        val PUT_COUNTER_ON_SELF = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+)""")
        val OTHERS_GET = Regex("""Other (.+) get ([+-]\d+)/([+-]\d+)\.""")
        val ANY_COLOR = Regex("""(one) mana of any color|(\w+) mana of any one color""")
        val ENTERS_TAPPED = Regex("""(.+) enters tapped\.""")
        val WHENEVER = Regex("""(Whenever|When|At) (.+?), (.+)""")
        val INTERVENING_IF = Regex("""if (.+?), (.+)""")
        val COSTS_LESS_FOR_EACH = Regex("""This ability costs (\{\d+\}) less to activate for each (.+?)\.""")
        val DISCARD_COST = Regex("""Discard (.+)""")
        val THEN = Regex("""(.+?), then (.+)""")
        val ADD_MANA_RESTRICTED = Regex("""Add (.+)\. Spend this mana only to cast (.+) spells\.""")
        val ADD_MANA_FOR_EACH = Regex("""Add (\{.\}) for each (.+)\.""")
        val DRAW = Regex("""Draw (a|one|two|three|four|five|six|seven) cards?\.""")
        val DISCARD = Regex("""[Dd]iscard (a|one|two|three|four|five|six|seven) cards?\.""")
        val CREATE_TOKEN = Regex("""[Cc]reate (.+ tokens?(?: with .+)?)\.""")
        val SELF_DEALS_DAMAGE_TO_EACH = Regex("""(.+?) deals (\d+) damage to (each .+)\.""")
        val EXILE_THEN_RETURN = Regex("""Exile target (.+), then return it to the battlefield under its owner's control\.""")
        val TARGET_PLAYER_DRAWS = Regex("""Target player draws (\w+) cards?(?:, then discards (\w+) cards?)?\.""")
        val TARGET_PLAYER_DISCARDS = Regex("""Target player discards (\w+) cards?\.""")
        val TARGET_PLAYER_CREATES = Regex("""Target player creates (.+ tokens?)\.""")
        val RETURN_FROM_GRAVEYARD_TO_HAND = Regex("""Return target (.+) from your graveyard to your hand\.""")
        val EACH_PLAYER_SHUFFLES_HAND_AND_GRAVEYARD = Regex("""Each player shuffles their hand and graveyard into their library, then draws (\w+) cards\.""")
        val EXILE_SELF = Regex("""Exile (.+)\.""")
        val LOOK_PUT_N_INTO_HAND_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put (\w+) of them into your hand and the rest on the bottom of your library in any order\.""")
        val LOOK_HAND_BOTTOM_EXILE = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your hand, put one of them on the bottom of your library, and exile one of them\.""")
        val YOU_UNTAP_UP_TO = Regex("""You untap up to (\w+) (.+)\.""")
        val THAT_PLAYER_MAY_SEARCH = Regex("""(?:That player|Its controller) may search their library for (.+), put (?:it|that card) onto the battlefield( tapped)?, then shuffle\.""")
        val AS_ENTERS_PAY_LIFE = Regex("""As (.+) enters, you may pay (\d+) life\. If you don't, it enters tapped\.""")
        val PAY_LIFE = Regex("""Pay (\d+) life""")
        val SACRIFICE = Regex("""Sacrifice (.+)""")
        val ADD_MANA = Regex("""Add (.+)\.""")
        val PLAYERS_GAIN_LIFE = Regex("""(.+) gains? (\d+) life\.""")
        val DESTROY_TARGET = Regex("""Destroy target (.+)\.""")
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+?), (reveal it, )?put (?:it|that card) (onto the battlefield|into your hand|into your graveyard)(, discard a card at random)?, then shuffle\.""")
        val SEARCH_TO_TOP = Regex("""Search your library for (.+?), (reveal it, )?then shuffle and put that card on top\.""")
        val PUT_FROM_HAND_ON_TOP = Regex("""Put (\w+) cards? from your hand on top of your library(?: in any order)?\.""")
        val PLAYERS_DRAW = Regex("""(Each player|You) draws? (\w+) cards?(?:, then discards? (\w+) cards?( at random)?)?(?: and loses? (\d+) life)?\.""")
        val EACH_PLAYER_DISCARDS_HAND_DRAWS = Regex("""Each player discards their hand, then draws (\w+) cards\.""")
        val DISCARD_UNLESS_ATTACKED = Regex("""Discard (a|one|two) cards? unless you attacked this turn\.""")
        val SCRY_OR_SURVEIL = Regex("""(Scry|Surveil) (\d+)\.""")
        val TARGET_PLAYER_MILLS = Regex("""Target player mills (\w+) cards?\.""")
        val LOOK_PUT_BACK = Regex("""Look at the top (\w+) cards of your library, then put them back in any order\.""")
        val TARGET_GETS_UNTIL_EOT = Regex("""Target (.+) gets ([+-]\d+)/([+-]\d+) until end of turn\.""")
        val RETURN_UP_TO_FROM_GRAVEYARD_TO_HAND = Regex("""Return up to (\w+) target (.+) from your graveyard to your hand\.""")
        val YOU_LOSE_LIFE = Regex("""You lose (\d+) life\.""")
        val ANY_COMBINATION = Regex("""(\w+) mana in any combination of colors""")
        // chunk 30
        val WHERE_X = Regex(""", where X is ([^.]+)""")
        val HAS_KEYWORDS_AS_LONG_AS = Regex("""(.+) has ([a-z ,]+) as long as (.+)\.""")
        val ESCAPES_WITH = Regex("""(.+) escapes with (a|an|one|two|three) (\S+) counters? on it\.""")
        val EXILE_FROM_GRAVEYARD_COST = Regex("""Exile (.+) from your graveyard""")
        val YOU_MAY_COST_IF_YOU_DO = Regex("""You may (.+?)\. If you do, (.+)""")
        val MILL = Regex("""Mill (\w+) cards?\.""")
        val PLAYERS_LOSE_LIFE = Regex("""(Each opponent|Each player) loses (\d+) life(?: and you gain (\d+) life)?\.""")
        val PLAYERS_DISCARD = Regex("""(Each opponent|Each player) discards (\w+) cards?\.""")
        val PUT_COUNTERS_ON = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+)\.""")
        // chunk 31
        val YOU_MAY_COST_WHEN_YOU_DO = Regex("""You may (.+?)\. When you do, (.+)""")
        val YOU_MAY = Regex("""You may (.+)\.""")
        val OTHERWISE = Regex("""Otherwise, (.+)""")
        val IF_ITS_A = Regex("""If it's an? (.+?), (.+)""")
        val PUT_IT = Regex("""Put (?:it|that card) (onto the battlefield|into your hand)\.""")
        val LOOK_OR_REVEAL_TOP = Regex("""(Look at|Reveal) the top (\w+) cards of your library\.""")
        val MAY_FROM_AMONG_INTO_HAND = Regex("""You may (reveal|put) an? (.+?) from among them (?:and put it )?into your hand\.""")
        val PUT_THE_REST = Regex("""Put the rest (on the bottom of your library in a random order|into your graveyard)\.""")
        val LOOK_PUT_UP_TO_ONE_ON_TOP = Regex("""Look at the top (\w+) cards of your library\. Put up to one of them on top of your library and the rest on the bottom of your library in a random order\.""")
        val WIN_IF_X_AT_LEAST = Regex("""If X is greater than or equal to (.+), you win the game\.""")
        val RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (target |all )?(.+) from your graveyard to the battlefield( tapped)?\.""")
        val GAINS_FLASHBACK = Regex("""Target (.+) gains flashback until end of turn\. The flashback cost is equal to its mana cost\.""")
        val CHOOSE_TARGET = Regex("""Choose target (.+)\.""")
        val MAY_PUT_FROM_HAND = Regex("""You may put an? (.+) from your hand onto the battlefield( tapped)?\.""")
        val GRAVEYARD_ON_BOTTOM = Regex("""(Up to one target|Target) player puts all the cards from their graveyard on the bottom of their library in a random order\.""")
        // chunk 32
        val HAVE_KEYWORDS = Regex("""(.+) have ([a-z ,]+)\.""")
        val MAY_PAY_TO_CAST = Regex("""As long as (.+), you may pay (\{.+\}) to cast this spell\.""")
        val TRIGGERS_ONLY_ONCE_EACH_TURN = Regex("""\s*This ability triggers only once each turn\.""")
        val RETURN_YOURS_TO_HAND = Regex("""Return an? (.+ you control) to its owner's hand\.""")
        val TARGET_DEALS_DAMAGE_TO_ITSELF = Regex("""Target (.+) deals (\d+) damage to itself\.""")
        val UNTAP_TARGET = Regex("""Untap (another )?target (.+)\.""")
        val GAIN_AND_GET = Regex("""(.+?) gains? (.+?) and gets? ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val COPY_TARGET = Regex("""Copy target (.+)\.""")
        val CREATE_COPY_OF_TARGET = Regex("""Create a token that's a copy of (another )?target (.+)\.""")
        // chunk 33
        val EACH_SACRIFICES = Regex("""Each (player|opponent) sacrifices an? (.+?)(?: of their choice)?\.""")
        val TARGET_REVEALS_HAND_YOU_CHOOSE = Regex("""Target opponent reveals their hand and you choose an? (.+) from it\.""")
        val MAY_EXILE_FROM_IT_UNTIL_LEAVES = Regex("""You may exile an? (.+) from it until (.+) leaves the battlefield\.""")
        val EXILED_TO_HAND = Regex("""(?:Return|Put) the exiled card (?:to|into) its owner's hand\.""")
        val EXILED_OWNER_CREATES = Regex("""The exiled card's owner creates (.+ tokens?)\.""")
        val UP_TO_ONE_TARGET = Regex("""(Exile|Destroy) up to one (other )?target (.+)\.""")
        val DAMAGE_EQUAL_TO_POWER = Regex("""(.+) deals damage equal to its power to any target\.""")
        val OPPONENTS_CANT_CAST = Regex("""Your opponents can't cast (.+) this turn\.""")
        val YOU_AND_TARGET_OPPONENT_DRAW = Regex("""You and target opponent each draw (\w+) cards?\.""")
        val X_CANT_BE = Regex("""X can't be (\d+)\.""")
        val GAIN_CONTROL_OF_TARGET = Regex("""Gain control of target (.+)\.""")
        val IF_THAT_IS_ATTACH_IT = Regex("""If that (\w+) is an? (.+), attach it to (.+)\.""")
        val COPY_NEXT_SPELL = Regex("""Copy the next (.+) you cast this turn when you cast it\.""")
        val COPY_TWICE_IF_KICKED = Regex("""If (.+) was kicked, copy that spell twice instead\.""")
    }
}

/** chunk 30: the key of the card memory that keeps the cards an ability exiled, for "the exiled card" (CR 607.2a). */
internal const val EXILED_KEY = "exiled"

/** chunk 30: [x] in place of every announced X: a number the text defines (CR 107.3c). Walks the JSON form, as
 * DefinitionChecks does, to reach every nested value. */
private fun Instructions.withX(x: Value): Instructions {
    val json = kotlinx.serialization.json.Json
    val announced = json.encodeToJsonElement(Value.serializer(), Value.Announced())
    val replacement = json.encodeToJsonElement(Value.serializer(), x)
    fun replace(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when {
        e == announced -> replacement
        e is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.mapValues { replace(it.value) })
        e is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { replace(it) })
        else -> e
    }
    return json.decodeFromJsonElement(Instructions.serializer(), replace(json.encodeToJsonElement(Instructions.serializer(), this)))
}

internal fun String.removeArticle() = removePrefix("a ").removePrefix("an ")
