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
internal class Sentences(
    private val self: Set<String>,
    private val isSpell: Boolean = false,
    /** chunks 21, 26: another ability of the card refers to cards "exiled with" it, so its exiling effects keep the exiled
     * cards as card memory (CR 607.2a). */
    private val linkedExile: Boolean = false,
) {
    /** The card's name or "this land" / "This land" (a sentence may start with it). */
    private fun isSelf(text: String) = text in self || text.replaceFirstChar { it.lowercase() } in self

    /** Lines that are more than one ability. */
    fun multi(line: String): List<Ability>? {
        // chunk 25: "Any player may cast creature spells with mana value 3 or less without paying their mana costs and as
        // though they had flash." A permission to cast from hand for free (each player for their own cards) and
        // player-wide flash (CR 601.2a, 118.9, 702.8a).
        MAY_CAST_WITHOUT_PAYING.matchEntire(line)?.let { m ->
            val anyPlayer = m.groupValues[1] == "Any player"
            val spells = Filters.noun(m.groupValues[2].replace(" spells", "")) ?: return null
            val owner = if (anyPlayer) PlayerFilter.Is(Players.Holder) else PlayerFilter.You
            val free = Ability.Static(
                listOf(Mechanic.MayPlay(Zone.Hand, withoutPaying = true, holder = if (anyPlayer) PlayerFilter.Any else PlayerFilter.You)),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(spells, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(owner)))))
            if (m.groupValues[3].isEmpty()) return listOf(free)
            val players = if (anyPlayer) Players.Each(PlayerFilter.Any) else Players.You
            return listOf(free, Ability.Static(listOf(Mechanic.CastAsThoughFlash(spells)), affects = arcanum.api.Affected.Players(players)))
        }
        return null
    }

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
        // "Choose one or more —" / "Choose one or both —": up to every mode, each at most once.
        val max = if (choose.groupValues[2].isNotEmpty()) options.size else n
        return arcanum.api.Modes(options, min = n, max = max, repeatable = choose.value.contains("same mode"))
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
        // chunk 13
        // "As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell
        // costs {2} less to cast for each card exiled this way." (CR 601.2f): the reduction counts the cost's cards.
        ADDITIONAL_EXILE_ANY_NUMBER_REDUCTION.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val per = Numbers.mana(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(
                Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(CostObjects.Choose(filter, count = null), from = Zone.Hand, postBind = "exiled")))),
                Mechanic.CostChange(ObjectFilter.Is(Objects.Self), change = Value.Times(Value.Count(Objects.Bound("exiled")), -per))))
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
        // chunk 26: 'Creature tokens you control have "[ability]"' (CR 113.10)
        HAVE_QUOTED.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val granted = Sentences(setOf("this creature", "this permanent")).line(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 26: "This token can't be blocked."
        CANT_BE_BLOCKED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantBeBlocked))
        }
        // chunk 25
        if (line == "Play with your hand revealed.")
            return Ability.Static(listOf(Mechanic.MayLookAt(Objects.Affected, reveal = true)), affects = arcanum.api.Affected.Objects(Objects.Each(yourHand)))
        // chunk 25: "If you would draw a card, [instead]." (CR 614.1a)
        IF_YOU_WOULD_DRAW.matchEntire(line)?.let { m ->
            val instead = instructions(m.groupValues[1].replaceFirstChar { it.uppercase() } + ". " + m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = instead)))
        }
        // chunk 25: "Creatures don't untap during their controllers' untap steps."
        DONT_UNTAP_PLURAL.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 25: "You may play an additional land on each of your turns." (CR 305.2)
        EXTRA_LAND_PLAYS.matchEntire(line)?.let { m ->
            val count = if (m.groupValues[1] == "any number of lands") null else Numbers.count(m.groupValues[1].substringBefore(' ')) ?: return null
            return Ability.Static(listOf(Mechanic.ExtraLandPlays(count)), affects = arcanum.api.Affected.Players(Players.You))
        }
        // chunk 25: "Enchanted permanent is a Vehicle artifact with crew 5 and it loses all other card types." (CR 205.1a)
        ENCHANTED_IS.matchEntire(line)?.let { m ->
            val add = mutableListOf<CardType>()
            val subtypes = mutableListOf<arcanum.api.SubtypeRef>()
            for (word in m.groupValues[1].split(' ')) {
                val type = Filters.CARD_TYPES[word]
                when {
                    type != null -> add += type
                    word in RulesWords.subtypes -> subtypes += arcanum.api.SubtypeRef.Literal(Subtype(word))
                    else -> return null
                }
            }
            val keywords = m.groupValues[2].takeIf { it.isNotEmpty() }?.split(" and ")?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
            val mechanics = listOf<Mechanic>(Mechanic.SetTypes(add = add, addSubtypes = subtypes, replaceTypes = m.groupValues[3].isNotEmpty())) +
                (if (keywords.isEmpty()) emptyList() else listOf(Mechanic.GainAbilities(keywords)))
            return Ability.Static(mechanics, affects = arcanum.api.Affected.Objects(Objects.AttachedTo()))
        }
        // chunk 20: "Equipped creature gets +1/+0 for each artifact you control." (CR 301.5)
        ATTACHED_GETS.matchEntire(line)?.let { m ->
            val per = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Value.Count(Filters.objects(it) ?: return null) }
            fun amount(n: Int): Value = when {
                per == null || n == 0 -> Value.Fixed(n)
                n == 1 -> per
                else -> Value.Times(per, n)
            }
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[1].toInt()), amount(m.groupValues[2].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.AttachedTo()))
        }
        // chunk 21: "You may play lands from your graveyard." (CR 305.1)
        MAY_PLAY_FROM_GRAVEYARD.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val how = if (m.groupValues[1] == "play") arcanum.api.PlayKind.Play else arcanum.api.PlayKind.Cast
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(filter, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 21: "You may spend mana as though it were mana of any color to activate abilities of creatures you control."
        SPEND_AS_ANY_COLOR_TO_ACTIVATE.matchEntire(line)?.let { m ->
            val sources = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = sources))),
                affects = arcanum.api.Affected.Players(Players.You))
        }
        // chunk 21: "[Creatures] have all activated abilities of all creature cards exiled with ~." (CR 607.2a)
        HAVE_ABILITIES_OF_EXILED.matchEntire(line)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val affected = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val cards = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilitiesOf(Objects.Where(Objects.Remembered(EXILED_WITH), cards))),
                affects = arcanum.api.Affected.Objects(Objects.Each(affected)))
        }
        // chunk 21: "This artifact enters with three wish counters on it." (CR 614.1c)
        ENTERS_WITH_COUNTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            // chunk 35: "enters with X +1/+1 counters": the X its spell was cast with (CR 107.3m)
            val n = if (m.groupValues[2] == "X") Value.CastX() else Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, n)))))
        }
        // chunk 35: "Anax's power is equal to your devotion to red." (a characteristic-defining ability, CR 604.3)
        POWER_IS_EQUAL_TO.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val value = selfValue(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.SetPowerToughness(value, null)), characteristicDefining = true)
        }
        // chunk 35: "This token can't block."
        CANT_BLOCK.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantBlock()))
        }
        // chunk 36: "This creature attacks each combat if able." (a requirement, CR 508.1d)
        ATTACKS_EACH_COMBAT.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.MustAttack()))
        }
        // chunk 36: "This creature can't be blocked except by creatures with flying or reach." (CR 509.1b)
        CANT_BE_BLOCKED_EXCEPT_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(Filters.noun(m.groupValues[2]) ?: return null)))
        }
        // chunk 36: "During your turn, [static ability]."
        DURING_YOUR_TURN_STATIC.matchEntire(line)?.let { m ->
            val inner = static(m.groupValues[1].replaceFirstChar { it.uppercase() }) as? Ability.Static ?: return null
            if (inner.condition != null) return null
            return inner.copy(condition = arcanum.api.Condition.YourTurn)
        }
        // chunk 36: "Nonland permanent cards in your graveyard have retrace."
        IN_YOUR_GRAVEYARD_HAVE.matchEntire(line)?.let { m ->
            val cards = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        return null
    }

    /** chunk 35: "your devotion to red" (CR 700.5). */
    private fun selfValue(text: String): Value? {
        DEVOTION_TO.matchEntire(text)?.let { m ->
            return Value.Devotion(m.groupValues[1].split(" and ").map { ((Filters.noun(it) as? ObjectFilter.OfColor)?.color as? ColorRef.Literal)?.color ?: return null })
        }
        return null
    }

    /** "[cost]: [effect]" (CR 602.1), with "This ability costs {1} less to activate for each …" as a last sentence. */
    private fun activated(line: String): Ability? {
        val colon = line.indexOf(": ").takeIf { it > 0 } ?: return null
        var cost = cost(line.substring(0, colon)) ?: return null
        var text = line.substring(colon + 2)
        // chunk 25: "the sacrificed land" reads the sacrificed object as it last existed (CR 608.2h)
        if (text.contains("the sacrificed ")) cost = Cost(cost.parts.map { if (it is CostPart.Sacrifice) it.copy(preBind = SACRIFICED) else it })
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
        // chunk 21
        ACTIVATE_ONLY_AS_SORCERY.find(text)?.let { m ->
            timing = arcanum.api.Timing.Sorcery
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_DURING_YOUR_TURN.find(text)?.let { m ->
            condition = arcanum.api.Condition.YourTurn
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
        // chunk 20: "the sacrificed creature's power" reads the sacrificed object as it last was (CR 608.2h)
        val named = if (!text.contains("the sacrificed ")) cost else cost.copy(parts = cost.parts.map {
            if (it is CostPart.Sacrifice && it.what is CostObjects.Choose) it.copy(preBind = SACRIFICED) else it
        })
        return Ability.Activated(named, instructions, arcanum.api.ActivationRules(timing = timing, costChange = costChange, condition = condition))
    }

    /** "Whenever [event], [if condition,] [effect]." (CR 603.1) */
    private fun triggered(line: String): Ability? {
        val m = WHENEVER.matchEntire(line) ?: return null
        val (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        var rest = m.groupValues[3]
        // chunk 21: "This ability triggers only once each turn."
        var perTurn: Int? = null
        TRIGGERS_ONLY_ONCE.find(rest)?.let { once ->
            perTurn = 1
            rest = rest.removeRange(once.range).trim()
        }
        val conditional = INTERVENING_IF.matchEntire(rest)
        val interveningIf = conditional?.let { Conditions.condition(it.groupValues[1], self) ?: return null }
        // chunk 25: "it"/"them" in the effect: the event's object, or the objects the condition is about;
        // chunk 20: "that creature" is the event's object
        val about = (interveningIf as? arcanum.api.Condition.ObjectMatches)
        val instructions = instructions(conditional?.groupValues?.get(2) ?: rest, event = trigger.events.first(),
            them = about?.objects?.takeIf { it != Objects.Self }, that = that(trigger)) ?: return null
        // chunk 25: "one or more [objects]" triggers once for the batch; "if this card is in your graveyard" states the
        // zone the ability works in (CR 113.6b)
        val oneOrMore = trigger.oneOrMore || m.groupValues[2].startsWith("one or more ")
        val zone = (about?.takeIf { it.objects == Objects.Self }?.filter as? ObjectFilter.InZone)?.zone
        return Ability.Triggered(trigger.copy(oneOrMore = oneOrMore), instructions, interveningIf = interveningIf,
            functionsIn = zone?.let { listOf(it) }, perTurn = perTurn, triggersOnlyIf = onlyIf)
    }

    /** "Whenever [event], choose one that hasn't been chosen this turn —" with its "• " modes (CR 700.2). */
    // chunk 21
    fun modalTrigger(header: String, modeLines: List<String>): Ability? {
        val m = WHENEVER.matchEntire(header) ?: return null
        val (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        val choose = CHOOSE_MODES.matchEntire(m.groupValues[3]) ?: return null
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it, that = that(trigger)) ?: return null) }
        if (options.isEmpty()) return null
        val unchosen = choose.groupValues[2].isNotEmpty()
        val thisTurn = choose.groupValues[3].isNotEmpty()
        return Ability.Triggered(trigger, Instructions(modes = arcanum.api.Modes(options, min = n, max = n,
            onlyUnchosen = unchosen && !thisTurn, onlyUnchosenThisTurn = thisTurn)), triggersOnlyIf = onlyIf)
    }

    /** A trigger's events. "One or more …" triggers once per batch; "during your turn" is checked as it would trigger. */
    // chunk 21
    private fun trigger(text: String): Pair<arcanum.api.Trigger.On, arcanum.api.Condition?>? {
        val during = DURING_YOUR_TURN.matchEntire(text)
        val events = during?.groupValues?.get(1) ?: text
        val onlyIf = during?.let { arcanum.api.Condition.YourTurn }
        ONE_OR_MORE_LEAVE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield), oneOrMore = true) to onlyIf
        }
        // chunk 36: "you discard one or more nonland cards": once for the batch
        YOU_DISCARD_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.Discard(filter, PlayerFilter.You), oneOrMore = true) to onlyIf
        }
        YOU_CREATE_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            // CR 111.2: the player who creates a token is its owner.
            return arcanum.api.Trigger.On(EventPattern.CreateToken(Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You))), oneOrMore = true) to onlyIf
        }
        return Events.events(events, self)?.let { arcanum.api.Trigger.On(it) to onlyIf }
    }

    /** "That creature", "that card", "that permanent" in a triggered ability: the object of its (first) event. */
    // chunk 20
    private fun that(trigger: arcanum.api.Trigger.On): Objects = when (val event = trigger.events.first()) {
        is EventPattern.ZoneChange -> Objects.EventObject(if (event.to == Zone.Battlefield) arcanum.api.EventRole.After else arcanum.api.EventRole.Before)
        is EventPattern.Discard -> Objects.EventObject(arcanum.api.EventRole.After)
        is EventPattern.Unattach -> Objects.EventObject(arcanum.api.EventRole.From)
        else -> Objects.EventObject()
    }

    /** Cost parts separated by ", " (CR 602.1a). */
    fun cost(text: String): Cost? {
        val parts = text.split(", ").map { costPart(it) ?: return null }
        if (parts.isEmpty()) return null
        return Cost(parts.flatten())
    }

    private fun costPart(text: String): List<CostPart>? {
        if (text == "{T}") return listOf(CostPart.TapSelf)
        // chunk 26: "[+1]", "[−2]", "[0]" (CR 606.4)
        LOYALTY.matchEntire(text)?.let { m ->
            val n = m.groupValues[2].toInt()
            return listOf(CostPart.Loyalty(Value.Fixed(if (m.groupValues[1].isEmpty() || m.groupValues[1] == "+") n else -n)))
        }
        ManaCost.parse(text)?.let { return listOf(CostPart.Mana(it)) }
        PAY_LIFE.matchEntire(text)?.let { return listOf(CostPart.PayLife(Value.Fixed(it.groupValues[1].toInt()))) }
        // chunk 36: "pay {1}" (in "you may pay {1} and discard a card")
        PAY_MANA.matchEntire(text)?.let { m -> ManaCost.parse(m.groupValues[1])?.let { return listOf(CostPart.Mana(it)) } }
        // chunk 14: "Flashback—{R}{R}, Discard X cards.": X in a cost of the card's own keyword is the X chosen as the
        // spell is cast (CR 107.3a, Value.CastX). Keyword costs are read with only "this card" as the self name; an
        // activated ability's X stays unread here.
        if (text == DISCARD_X_CARDS && self == setOf("this card"))
            return listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = Value.CastX())))
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
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            return listOf(CostPart.Sacrifice(if (isSelf(what)) CostObjects.Self else CostObjects.Choose(Filters.noun(what.removeArticle()) ?: return null)))
        }
        // chunk 21: "Remove two charge counters from this artifact"
        REMOVE_COUNTERS_FROM_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.RemoveCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))
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
    fun instructions(text: String, seedTargets: List<TargetSlot> = emptyList(), event: EventPattern? = null, them: Objects? = null,
                     that: Objects? = null): Instructions? {
        val context = Context()
        context.targets += seedTargets
        // chunk 25
        context.event = event
        context.it = event?.let { if (it is EventPattern.ZoneChange) Objects.EventObject(arcanum.api.EventRole.After) else Objects.EventObject() }
        context.them = them
        // chunk 20
        context.that = that
        val steps = mutableListOf<Step>()
        for (sentence in sentences(text)) {
            val modified = modify(sentence, steps, context)
            if (modified != null) { steps.clear(); steps += modified; continue }
            steps += effect(sentence, context) ?: return null
        }
        return Instructions(targets = context.targets, steps = steps, replacements = context.replacements)
    }

    /** Sentences that change the step before them ("If this spell was kicked, it deals 4 damage instead.", "It can't be
     * regenerated."). */
    private fun modify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        // chunk 26: "Untap those creatures." after an effect on them
        if (sentence == "Untap those creatures.") {
            val apply = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.ApplyMechanics) ?: return null
            val objects = (apply.affects as? arcanum.api.Affected.Objects)?.objects ?: return null
            return steps + Step.Do(Effect.Untap(objects))
        }
        // chunk 26: "They gain haste." / "Sacrifice them at the beginning of the next end step." after creating tokens
        THEY_GAIN.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val bound = bindTokens(steps) ?: return null
            return bound + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)),
                arcanum.api.Affected.Objects(Objects.Bound(TOKENS)), arcanum.api.Duration.Indefinitely))
        }
        if (sentence == "Sacrifice them at the beginning of the next end step.") {
            val bound = bindTokens(steps) ?: return null
            // CR 603.7: a delayed trigger, triggering once at the next end step
            return bound + Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound(TOKENS)))))))
        }
        // chunk 36: "You may put a land card from among them into your hand." after milling: zero or one of the milled cards
        MAY_PUT_FROM_AMONG_THEM_INTO_HAND.matchEntire(sentence)?.let { m ->
            val mill = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Mill)?.takeIf { it.postBind == null } ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return steps.dropLast(1) + Step.Do(mill.copy(postBind = "milled")) +
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound("milled")), bind = "chosen") +
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand)))
        }
        // chunk 36: "If it entered under your control, …" after a delayed trigger that returns a card: part of that trigger
        IF_IT_ENTERED_UNDER_YOUR_CONTROL.matchEntire(sentence)?.let { m ->
            val delayed = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateDelayedTrigger) ?: return null
            val returned = ((delayed.instructions.steps.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.postBind ?: return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            val iff = Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound(returned), ObjectFilter.ControlledBy(PlayerFilter.You)), then = then)
            return steps.dropLast(1) + Step.Do(delayed.copy(instructions = delayed.instructions.copy(steps = delayed.instructions.steps + iff)))
        }
        // chunk 25: "If …, …. Otherwise, …."
        OTHERWISE.matchEntire(sentence)?.let { m ->
            val iff = (steps.lastOrNull() as? Step.If)?.takeIf { it.otherwise.isEmpty() } ?: return null
            val otherwise = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return steps.dropLast(1) + iff.copy(otherwise = otherwise)
        }
        val last = steps.lastOrNull() as? Step.Do ?: return null
        val effect = last.effect
        // chunk 12
        // "If you search your library this way, shuffle.": only when an earlier search could search the library.
        if (sentence == "If you search your library this way, shuffle.") {
            val search = steps.mapNotNull { (it as? Step.Do)?.effect as? Effect.Search }.lastOrNull() ?: return null
            if (Zone.Library !in search.zones || search.zones.size < 2) return null
            return steps + Step.If(arcanum.api.Condition.Searched(search.bind ?: return null, Zone.Library),
                then = listOf(Step.Do(Effect.Shuffle(search.library))))
        }
        // "Create [a token] for each card type among cards discarded this way.": names the discarded cards.
        if (effect is Effect.Discard && effect.postBind == null) CREATE_FOR_EACH_CARD_TYPE_DISCARDED.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            if (count != 1) return null
            return steps.dropLast(1) + Step.Do(effect.copy(postBind = DISCARDED)) + Step.Do(Effect.CreateToken(token,
                count = Value.CountDistinct(Objects.Bound(DISCARDED), arcanum.api.SharedQuality.CardType)))
        }
        // "Draw a card for each card returned to your hand this way.": names the returned objects.
        if (effect is Effect.Move && effect.to.zone == Zone.Hand && effect.postBind == null) DRAW_FOR_EACH_RETURNED_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
            return steps.dropLast(1) + Step.Do(effect.copy(postBind = RETURNED)) +
                Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Where(Objects.Bound(RETURNED), filter))))
        }
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
        // chunk 14: "If this spell was cast from a graveyard, copy that spell twice instead." (CR 707.10)
        if (effect is Effect.CopySpell && sentence == "If this spell was cast from a graveyard, copy that spell twice instead.") {
            return steps.dropLast(1) + Step.If(arcanum.api.Condition.CastFrom(Zone.Graveyard),
                then = listOf(Step.Repeat(Value.Fixed(2), listOf(last))), otherwise = listOf(last))
        }
        if (effect is Effect.Destroy && sentence in setOf("It can't be regenerated.", "A creature destroyed this way can't be regenerated.")) {
            return steps.dropLast(1) + Step.Do(effect.copy(noRegeneration = true))
        }
        // chunk 20: "If the sacrificed creature's toughness was 4 or greater, create two Food tokens instead."
        IF_INSTEAD.matchEntire(sentence)?.let { m ->
            // chunk 35: "If the creature had power 4 or greater": the trigger event's object as it last existed (CR 603.10a)
            val had = THE_CREATURE_HAD.matchEntire(m.groupValues[1])?.let { h ->
                val n = Value.Fixed(h.groupValues[2].toInt())
                arcanum.api.Condition.ObjectMatches(context.that ?: return null,
                    if (h.groupValues[1] == "power") ObjectFilter.Power(arcanum.api.Comparison.AtLeast, n) else ObjectFilter.Toughness(arcanum.api.Comparison.AtLeast, n))
            }
            val condition = had ?: Conditions.condition(m.groupValues[1], self) ?: return null
            // chunk 35: "create two of those tokens instead": the same token, another number
            val more = OF_THOSE_TOKENS.matchEntire(m.groupValues[2])?.let { t ->
                val create = effect as? Effect.CreateToken ?: return null
                listOf(Step.Do(create.copy(count = Value.Fixed(Numbers.count(t.groupValues[1]) ?: return null))))
            }
            val then = more ?: effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return steps.dropLast(1) + Step.If(condition, then = then, otherwise = listOf(last))
        }
        return null
    }

    private class Context {
        var flip = false
        // chunk 25: the triggering event; what "it" and "them" refer to; the amount "that much" refers to
        var event: EventPattern? = null
        var it: Objects? = null
        var them: Objects? = null
        var thatMuch: Value? = null
        // chunk 20: the event's object in a trigger ("that creature")
        var that: Objects? = null
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

        // chunk 13
        /** The player whose hand was revealed ("that player"), the revealed hand ("from it"), the names of the cards
         * chosen ("that card", "the chosen cards") and of the chosen card name ("that name"). */
        var player: Players? = null
        var revealed: Objects? = null
        val chosen = mutableListOf<String>()
        var cardName: String? = null

        /** "target opponent" */
        fun targetOpponent(): Players {
            if (targets.none { it.name == "opponent" }) targets += TargetSlot("opponent", arcanum.api.TargetKind.Players(PlayerFilter.Opponent))
            return Players.Target("opponent")
        }

        // chunk 14
        /** A copy was made ("You may choose new targets for the copies"); who may pay ("If the player does"). */
        var copied = false
        var payer: Players? = null

        /** "That player": the controller of the last object target. */
        fun lastTargetController(): Players? = targets.lastOrNull()?.let {
            if (it.what is arcanum.api.TargetKind.Players) Players.Target(it.name) else Players.ControllerOf(Objects.Target(it.name))
        }

        // chunk 35
        /** The trigger is this object leaving the battlefield for a graveyard ("When this creature dies"). */
        fun selfDied(): Boolean = (event as? EventPattern.ZoneChange)?.let { it.to == Zone.Graveyard && it.from == Zone.Battlefield &&
            it.objects == ObjectFilter.Is(Objects.Self) } == true

        /** "another": not this object; once it has died, not the new card it became in the graveyard (CR 400.7, 603.6e). */
        fun another(): ObjectFilter = ObjectFilter.Not(ObjectFilter.Is(if (selfDied()) Objects.SelfAfter else Objects.Self))
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
        // "Draw two cards. Then discard a card …": a sentence that starts with "Then" follows the one before it.
        if (sentence.startsWith("Then ")) return effect(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() }, context)
        // chunk 12
        SEARCH_LIBRARY_AND_OR_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, zones = listOf(Zone.Library, Zone.Graveyard), bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))))
        }
        IF_X_AT_LEAST.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context)?.let { then ->
                return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())), then = then))
            }
        }
        // "Creatures you control get +X/+X and gain haste until end of turn." (locked in when it resolves, CR 611.2c)
        GROUP_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() })
            val keywords = m.groupValues[4].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) }
            if (filter != null && keywords?.contains(null) != true) {
                val mechanics = listOf<Mechanic>(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)) +
                    listOfNotNull(keywords?.let { Mechanic.GainAbilities(it.filterNotNull()) })
                return listOf(Step.Do(Effect.ApplyMechanics(mechanics, arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
            }
        }
        SHUFFLE_INTO_OWNERS_LIBRARY.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))))
        }
        DRAW_OR_DISCARD_X.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(if (m.groupValues[1] == "Draw") Effect.Draw(Players.You, Value.Announced()) else Effect.Discard(Players.You, Value.Announced())))
        }
        RETURN_ANY_NUMBER_OF_TARGETS_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val filter = Filters.noun(noun) ?: return null
            context.targets += TargetSlot(Filters.slotName(noun), TargetKind.Objects(filter), min = Value.Fixed(0), max = null)
            return listOf(Step.Do(Effect.Move(Objects.Target(Filters.slotName(noun)), Destination(Zone.Hand))))
        }
        // chunk 13
        // Cleave (CR 702.148a): the text in square brackets is there unless the cleave cost was paid.
        BRACKETED.matchEntire(sentence)?.let { m ->
            val slots = context.targets.size
            val full = effect(m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4], context) ?: return null
            val cleaved = effect(m.groupValues[1] + m.groupValues[4], context) ?: return null
            if (context.targets.size != slots) return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Paid(arcanum.api.Keyword.Cleave.Kind)), then = full, otherwise = cleaved))
        }
        CHOOSE_CARD_NAME.matchEntire(sentence)?.let { m ->
            val filter = m.groupValues[1].ifEmpty { null }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            context.cardName = "name"
            return listOf(Step.Choose(arcanum.api.Choice.CardName(filter), bind = "name"))
        }
        // "Target player reveals their hand[ and discards all cards with that name]."
        TARGET_REVEALS_HAND.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            val hand = Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player))))
            context.player = player
            context.revealed = Objects.Bound("revealed")
            val steps = mutableListOf<Step>(Step.Do(Effect.Reveal(hand, bind = "revealed")))
            if (m.groupValues[2].isNotEmpty()) {
                val name = context.cardName ?: return null
                steps += Step.Do(Effect.Discard(player, objects = Objects.Where(Objects.Bound("revealed"), ObjectFilter.ChosenName(name))))
            }
            return steps
        }
        // "You choose an artifact or creature card from it, then choose an artifact or creature card from their graveyard."
        YOU_CHOOSE_FROM_IT_AND_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val player = context.player ?: return null
            val from = context.revealed ?: return null
            val inHand = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            val inGraveyard = Filters.all(Filters.noun(m.groupValues[2].removeArticle()) ?: return null,
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))
            context.chosen += listOf("chosen", "chosenFromGraveyard")
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(inHand, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen"),
                Step.Choose(arcanum.api.Choice.Objects(inGraveyard, Value.Fixed(1), Value.Fixed(1)), bind = "chosenFromGraveyard"))
        }
        // "You choose a nonland card from it[ with mana value 3 or less]." (someone else chooses: Choose + Discard(objects))
        YOU_CHOOSE_FROM_IT.matchEntire(sentence)?.let { m ->
            val from = context.revealed ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle() + m.groupValues[2]) ?: return null
            if ("chosen" !in context.chosen) context.chosen += "chosen"
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen"))
        }
        if (sentence == "That player discards that card.") {
            val player = context.player ?: return null
            val card = context.chosen.lastOrNull() ?: return null
            return listOf(Step.Do(Effect.Discard(player, objects = Objects.Bound(card))))
        }
        if (sentence == "Exile the chosen cards.") {
            val cards = context.chosen.map { Objects.Bound(it) }.ifEmpty { return null }
            return listOf(Step.Do(Effect.Move(cards.singleOrNull() ?: Objects.Union(cards), Destination(Zone.Exile))))
        }
        TARGET_LOSES_YOU_GAIN.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            return listOf(
                Step.Do(Effect.LoseLife(player, Value.Fixed(m.groupValues[2].toInt()))),
                Step.Do(Effect.GainLife(Players.You, Value.Fixed(m.groupValues[3].toInt()))))
        }
        PUT_INTO_LIBRARY_FROM_TOP.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val position = FROM_TOP[m.groupValues[2]] ?: return null
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = position))))
        }
        ITS_CONTROLLER_GAINS_N.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.GainLife(Players.ControllerOf(target), Value.Fixed(m.groupValues[1].toInt()))))
        }
        // "Each opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and
        // planeswalkers they control." Each opponent chooses, then all sacrifice at once (CR 101.4, 608.2e).
        EACH_OPPONENT_SACRIFICES_GREATEST.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[1]) ?: return null
            val among = Filters.noun(m.groupValues[2].replace(" and ", " or ")) ?: return null
            val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))
            val greatest = Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Each(Filters.all(among, theirs)), arcanum.api.Combine.Greatest)
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(kind, theirs, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, greatest)),
                    Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))))
        }
        // CR 702.51c, 701.50
        if (sentence == "Each creature that convoked this spell connives.")
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Connive(Objects.PaidWith(arcanum.api.ManaPaymentWay.TapCreatures)))))
        DESTROY_UP_TO.matchEntire(sentence)?.let { m ->
            val max = if (m.groupValues[1] == "X") Value.Announced() else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val noun = m.groupValues[2].replace(" and/or ", " or ")
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = max)
            return listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        // 'Create twice X 1/1 black and green Pest creature tokens with "When this token dies, you gain 1 life."'
        CREATE_TOKENS_WITH_QUOTED.matchEntire(sentence)?.let { m ->
            val count = if (m.groupValues[1] == "twice X") Value.Times(Value.Announced(), 2) else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val token = Tokens.defined("a " + m.groupValues[2] + " token") ?: return null
            val granted = Sentences(setOf("this token", "this creature")).line(m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token.copy(face = token.face.copy(abilities = listOf(granted))), count = count)))
        }
        // chunk 14
        // "[This] deals X damage divided as you choose among any number of targets." (CR 601.2d, 115.4)
        DIVIDED_AMONG_ANY_NUMBER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            val slot = context.targets.removeAt(context.targets.lastIndex)
            context.targets += slot.copy(min = Value.Fixed(0), max = null, divided = Value.Announced())
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = slot.name), Value.DividedShare)))
        }
        // "Then that player or that permanent's controller may pay {R}{R}." after damage to any target
        THAT_PLAYER_OR_CONTROLLER_MAY_PAY.matchEntire(sentence)?.let { m ->
            val slot = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Either }?.name ?: return null
            val cost = ManaCost.parse(m.groupValues[1]) ?: return null
            val payer = Players.Each(PlayerFilter.Or(listOf(PlayerFilter.Is(Players.Target(slot)), PlayerFilter.Is(Players.ControllerOf(Objects.Target(slot))))))
            context.payer = payer
            return listOf(Step.Pay(Cost.mana(cost), payer = payer, bind = "paid"))
        }
        if (sentence == "If the player does, they may copy this spell and may choose a new target for that copy.") {
            val payer = context.payer ?: return null
            context.copied = true
            return listOf(Step.If(arcanum.api.Condition.Did("paid"), then = listOf(
                Step.May(player = payer, steps = listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = payer)))))))
        }
        // "Copy target instant or sorcery spell[ you control]." (CR 707.10)
        COPY_TARGET.matchEntire(sentence)?.let { m ->
            context.copied = true
            return listOf(Step.Do(Effect.CopySpell(context.target("spell", Filters.noun(m.groupValues[1]) ?: return null))))
        }
        // CR 707.10c: the copy's controller may choose new targets, which CopySpell allows by default.
        COPY_NEW_TARGETS.matchEntire(sentence)?.let { return if (context.copied) emptyList() else null }
        if (sentence == "You may copy this spell and may choose a new target for the copy.") {
            context.copied = true
            return listOf(Step.May(listOf(Step.Do(Effect.CopySpell(Objects.Self)))))
        }
        IF_CAST_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.CastFrom(Zone.Graveyard), then = then))
        }
        // "Until end of turn, whenever …" / "Whenever … this turn, …": a delayed trigger for the turn (CR 603.7b)
        (UNTIL_END_OF_TURN_WHENEVER.matchEntire(sentence) ?: WHENEVER_THIS_TURN.matchEntire(sentence))?.let { m ->
            val event = Events.event(m.groupValues[1], self) ?: return null
            val then = instructions(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(event), then,
                duration = arcanum.api.Duration.EndOfTurn, repeats = true)))
        }
        if (sentence == "That player copies it and may choose new targets for the copy.") {
            return listOf(Step.Do(Effect.CopySpell(Objects.EventObject(), controller = Players.EventPlayer)))
        }
        RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            // chunk 35: "another target"
            val another = if (m.groupValues[1].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(another + listOf(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))).toTypedArray())
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Battlefield, tapped = m.groupValues[3].isNotEmpty()))))
        }
        if (sentence == "Shuffle your library.") return listOf(Step.Do(Effect.Shuffle(Players.You)))
        if (sentence == "Until end of turn, you may play that card without paying its mana cost.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, withoutPaying = true)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        // "Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal
        // to its mana cost." (CR 702.34a)
        GAINS_FLASHBACK_OF_ITS_MANA_COST.matchEntire(sentence)?.let { m ->
            val cards = Filters.all(Filters.noun(m.groupValues[1].replace(" and ", " or ")) ?: return null,
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
                arcanum.api.Affected.Objects(Objects.Each(cards)), arcanum.api.Duration.EndOfTurn)))
        }
        // A permission, so it also covers cards put into the graveyard later this turn (CR 611.2c).
        if (sentence == "Until end of turn, you may play lands and cast spells from your graveyard.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, arcanum.api.PlayKind.Play)),
                arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "If a card would be put into your graveyard from anywhere this turn, exile that card instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.EndOfTurn)))
        }
        // "You may cast up to one target instant card and/or up to one target sorcery card from your graveyard each with
        // mana value X or less without paying their mana costs."
        CAST_UP_TO_ONE_EACH_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val most = if (m.groupValues[2] == "X") Value.Announced() else Value.Fixed(m.groupValues[2].toInt())
            val slots = m.groupValues[1].split(" and/or ").map { part ->
                val noun = UP_TO_ONE_TARGET_CARD.matchEntire(part)?.groupValues?.get(1) ?: return null
                val filter = Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, most))
                TargetSlot(noun, TargetKind.Objects(filter), min = Value.Fixed(0))
            }
            context.targets += slots
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(0), Value.Fixed(slots.size),
                    from = Objects.Union(slots.map { Objects.Target(it.name) })), bind = "toCast"),
                Step.Do(Effect.Cast(Objects.Bound("toCast"), withoutPaying = true, bind = "cast")))
        }
        if (sentence == "If a spell cast this way would be put into your graveyard, exile it instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.Indefinitely)))
        }
        IF_X_AT_LEAST_COPY_TWICE.matchEntire(sentence)?.let { m ->
            context.copied = true
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())),
                then = listOf(Step.Repeat(Value.Fixed(2), listOf(Step.Do(Effect.CopySpell(Objects.Bound("cast"))))))))
        }
        // chunk 15
        RETURN_SELF_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand))))
        }
        // "Return each creature card with mana value X or less from your graveyard to the battlefield."
        RETURN_EACH_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.Move(Objects.Each(filter), Destination(Zone.Battlefield), postBind = "returned")))
        }
        // "Exile those creatures at the beginning of your next upkeep." (CR 603.7: triggers once)
        if (sentence == "Exile those creatures at the beginning of your next upkeep.") {
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound("returned"), Destination(Zone.Exile))))))))
        }
        // "Choose up to two target permanent cards in your graveyard that were put there from the battlefield this turn."
        CHOOSE_UP_TO_TARGET_PUT_THERE_THIS_TURN.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You), PUT_THERE_FROM_BATTLEFIELD_THIS_TURN)
            context.targets += TargetSlot("cards", TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(n))
            return emptyList()
        }
        RETURN_THEM_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val slot = context.targets.lastOrNull()?.name ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Battlefield, tapped = m.groupValues[1].isNotEmpty()))))
        }
        // "[Each player returns|Return] to the battlefield all … cards in [their|your] graveyard that were put there from
        // the battlefield this turn." Each card returns under its owner's control.
        RETURN_ALL_PUT_THERE_THIS_TURN.matchEntire(sentence)?.let { m ->
            val each = m.groupValues[1] == "Each player returns"
            if (each != (m.groupValues[3] == "their")) return null
            val kind = Filters.noun(m.groupValues[2].replace(", and ", ", or ")) ?: return null
            val cards = Objects.Each(Filters.all(*listOfNotNull(kind, ObjectFilter.InZone(Zone.Graveyard),
                if (each) null else ObjectFilter.OwnedBy(PlayerFilter.You), PUT_THERE_FROM_BATTLEFIELD_THIS_TURN).toTypedArray()))
            return listOf(Step.Do(Effect.Move(cards, Destination(Zone.Battlefield, controller = if (each) Players.OwnerOf(cards) else null))))
        }
        // "Look at the top six cards of your library. Put up to two creature cards with mana value 3 or less from among
        // them onto the battlefield. Put the rest on the bottom of your library in any order."
        LOOK_PUT_UP_TO_ONTO_BATTLEFIELD_REST_BOTTOM.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            val most = Numbers.count(m.groupValues[2]) ?: return null
            val filter = Filters.noun(m.groupValues[3]) ?: return null
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen)), bind = "seen")),
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(most), from = Objects.Bound("seen")), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1))))
        }
        // In a mana trigger on tapping a permanent: its controller tapped it (CR 605.1b).
        THAT_PLAYER_ADDS_ADDITIONAL.matchEntire(sentence)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana)), players = Players.ControllerOf(Objects.EventObject()))))
        }
        if (sentence == "Take an extra turn after this one.") return listOf(Step.Do(Effect.ExtraTurn(Players.You)))
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
            val to = if (m.groupValues[3] == "into your hand") Destination(Zone.Hand) else Destination(Zone.Battlefield)
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
            // chunk 35: "another target"
            val another = if (m.groupValues[1].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(another + listOf(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))).toTypedArray())
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
        // chunk 21: "Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact."
        DESTROY_EACH.matchEntire(sentence)?.let { m -> counterNoun(m.groupValues[1])?.let { return listOf(Step.Do(Effect.Destroy(Objects.Each(it)))) } }
        // chunk 21: "Exile target … until this artifact leaves the battlefield." (CR 610.3)
        EXILE_TARGET_UNTIL_LEAVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Exile), untilLeaves = Objects.Self)))
        }
        // chunk 21: "Exile target card from a graveyard."
        EXILE_TARGET_FROM_A_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard)))
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled"))) + remembered(Objects.Bound("exiled"))
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
            if (m.groupValues[2].isEmpty()) return listOf(exile)
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
            // chunk 35: "you may pay {X}. If you do, return … target … card with mana value X …": X is the target's mana
            // value (CostPart.Mana.x); the target is chosen before X (CR 603.3d).
            if (cost.symbols.any { it is arcanum.api.ManaSymbol.Variable }) {
                val text = m.groupValues[2].takeIf { it.contains(WITH_MANA_VALUE_X) } ?: return null
                val then = effect(text.replace(WITH_MANA_VALUE_X, "").replaceFirstChar { it.uppercase() }, context) ?: return null
                val slot = context.targets.lastOrNull()?.name ?: return null
                val x = Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Target(slot), arcanum.api.Combine.Single)
                return listOf(Step.Pay(Cost(listOf(CostPart.Mana(cost, x = x))), bind = "paid"), Step.If(arcanum.api.Condition.Did("paid"), then = then))
            }
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
        // chunk 35: "Create a 1/1 … token for each +1/+1 counter on this creature." (read from the source, its last known
        // information once it has left, CR 113.7a, 608.2h)
        CREATE_FOR_EACH_COUNTER_ON_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            if (count != 1) return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single))))
        }
        // chunk 35: "Draw a card for each +1/+1 counter on it." in an ability of this object
        DRAW_FOR_EACH_COUNTER_ON.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) || it.groupValues[2] == "it" && context.selfDied() }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single))))
        }
        // chunk 35: "Create X tapped 1/1 green Squirrel creature tokens, where X is this creature's power."
        CREATE_X_WHERE_SELF_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val (_, token) = Tokens.spec("a " + m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single),
                tapped = m.groupValues[1].isNotEmpty())))
        }
        // chunk 35: "It deals damage equal to its power to any target." (the source's power, CR 113.7a)
        DEALS_DAMAGE_EQUAL_TO_ITS_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            val amount = Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single)
            val to = if (m.groupValues[2] == "any target") { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                else arcanum.api.Recipients(players = Filters.players(m.groupValues[2]) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(to, amount)))
        }
        // chunk 35: "Return to your hand target artifact card in your graveyard with lesser mana value." (lesser than the
        // object of the trigger event, as it last existed, CR 603.10a)
        RETURN_TO_HAND_WITH_LESSER_MANA_VALUE.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.ManaValue(arcanum.api.Comparison.Less, Value.Of(arcanum.api.NumericProperty.ManaValue, that, arcanum.api.Combine.Single)))
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Hand))))
        }
        // chunk 35: "Each player may search their library for up to two basic land cards, put them onto the battlefield, then
        // shuffle." Each player decides and searches in turn order, then the lands enter together (CR 101.4).
        EACH_PLAYER_MAY_SEARCH_UP_TO.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val player = Players.Bound("player")
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.May(player = player, steps = listOf(
                    Step.Do(Effect.Search(player, filter, max = Value.Fixed(n), bind = "found", searcher = player)),
                    Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                    Step.Do(Effect.Shuffle(player)))))))
        }
        CREATE_TOKEN.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count))))
        }
        THAT_PLAYER_MAY_SEARCH.matchEntire(sentence)?.let { m ->
            val player = context.lastTargetController() ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.May(player = player, steps = listOf(
                Step.Do(Effect.Search(player, filter, bind = "found", searcher = player)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty(), controller = player))),
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
        // chunk 26: "You may [cost or action]. If you do, [effect]." (CR 118.12)
        YOU_MAY_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val action = m.groupValues[1].replaceFirstChar { it.uppercase() }
            // chunk 36: "pay {1} and discard a card" is one cost of two parts; "exile one of them from your graveyard" in a
            // discard trigger exiles one of the discarded cards
            val them = EXILE_ONE_OF_THEM_FROM_GRAVEYARD.takeIf { action == it && context.event is EventPattern.Discard }?.let {
                context.it = Objects.Bound("exiled")
                listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.Is(Objects.EventObject(arcanum.api.EventRole.After))), from = Zone.Graveyard,
                    postBind = "exiled"))
            }
            val parts = them ?: costPart(action) ?: action.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) }
                .takeIf { it.size > 1 && it.all { p -> p != null } }?.flatMap { it!! }
            val pay = if (parts != null) Step.Pay(Cost(parts), bind = "paid")
                else Step.Pay(steps = effect("$action.", context) ?: return null, bind = "paid")
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(pay, Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        // chunk 26: "That creature's controller may draw a card." (the creature as it last existed, CR 603.10a)
        THAT_CREATURES_CONTROLLER_MAY_DRAW.matchEntire(sentence)?.let { m ->
            if (context.event !is EventPattern.ZoneChange) return null
            val player = Players.ControllerOf(Objects.EventObject(arcanum.api.EventRole.Before))
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return listOf(Step.May(listOf(Step.Do(Effect.Draw(player, Value.Fixed(n)))), player = player))
        }
        // chunk 26: "Exile the top card of your library. You may play that card for as long as it remains exiled."
        EXILE_TOP_CARD.matchEntire(sentence)?.let { m ->
            context.it = Objects.Bound("exiled")
            // chunk 36: "face down" (CR 406.3)
            val exile = Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1)), Destination(Zone.Exile, faceDown = m.groupValues[1].isNotEmpty()),
                postBind = "exiled"))
            return if (linkedExile) listOf(exile, Step.Do(Effect.Remember(EXILED_WITH, Objects.Bound("exiled")))) else listOf(exile)
        }
        if (sentence == "You may play that card for as long as it remains exiled.") {
            // CR 400.7: once it leaves exile it is a new object, so the permission has no other end
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 26: "Each opponent loses 1 life."
        PLAYERS_LOSE_LIFE.matchEntire(sentence)?.let { m ->
            Filters.players(m.groupValues[1])?.let { return listOf(Step.Do(Effect.LoseLife(it, Value.Fixed(m.groupValues[2].toInt())))) }
        }
        // chunk 26: "This enchantment deals that much damage to each opponent."
        DEALS_THAT_MUCH.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val players = Filters.players(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = players), context.thatMuch ?: return null)))
        }
        // chunk 26: "Put a loyalty counter on each red planeswalker you control."
        PUT_COUNTERS_ON_EACH.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(Objects.Each(Filters.noun(m.groupValues[3]) ?: return null), kind, Value.Fixed(n))))
        }
        // chunk 26: "Each creature gets -X/-X until end of turn.", "Creatures you control get +1/+1 until end of turn."
        EACH_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1].ifEmpty { m.groupValues[2].replaceFirstChar { it.lowercase() } }
            Filters.noun(noun)?.let { filter ->
                return listOf(Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[3]) ?: return null, ptChange(m.groupValues[4]) ?: return null)),
                    arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
            }
        }
        // chunk 26: "Creatures you control gain haste until end of turn."
        GAIN_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val subject = m.groupValues[1]
            val objects = if (isSelf(subject)) Objects.Self else Filters.noun(subject.replaceFirstChar { it.lowercase() })?.let { Objects.Each(it) }
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) }
            if (objects != null && keywords.all { it != null })
                return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords.filterNotNull())),
                    arcanum.api.Affected.Objects(objects), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 26: "You may cast target instant or sorcery card … from your graveyard." (cast during resolution, CR 608.2g)
        MAY_CAST_TARGET_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            // chunk 36: "with mana value less than or equal to this creature's power", "without paying its mana cost"
            val most = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { if (isSelf(it)) ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single)) else return null }
            val filter = Filters.all(*listOfNotNull(Filters.noun(m.groupValues[1]) ?: return null, most, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You)).toTypedArray())
            val card = context.target("card", filter)
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(card, withoutPaying = m.groupValues[4].isNotEmpty(), bind = "spell")))))
        }
        // chunk 26: "If that spell would be put into your graveyard, exile it instead." after casting it (CR 614.1a)
        if (sentence == "If that spell would be put into your graveyard, exile it instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, arcanum.api.Duration.Indefinitely)))
        }
        // chunk 26: "He phases out." (CR 702.26b)
        PHASES_OUT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] in setOf("He", "She") }?.let {
            return listOf(Step.Do(Effect.PhaseOut(Objects.Self)))
        }
        // chunk 26: 'You get an emblem with "[ability]".' (CR 114.2)
        EMBLEM.matchEntire(sentence)?.let { m ->
            val ability = Sentences(setOf("this emblem")).line(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateEmblem(listOf(ability))))
        }
        // chunk 26: "Target artifact you control becomes a copy of another target artifact or creature you control until end
        // of turn, except it's an artifact in addition to its other types." (CR 707.9b)
        BECOMES_COPY.matchEntire(sentence)?.let { m ->
            val first = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            val name = Filters.slotName(m.groupValues[2])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[2]) ?: return null),
                distinctFrom = listOf((first as Objects.Target).slot))
            val except = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { listOf(arcanum.api.CopyException.Types(add = listOf(Filters.CARD_TYPES[it] ?: return null))) }.orEmpty()
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Target(name), except)),
                arcanum.api.Affected.Objects(first), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 25
        UNTAP_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            return listOf(Step.Do(Effect.Untap(context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null))))
        }
        UNTAP_ALL.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Untap(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null))))
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
        PUT_IT_INTO.matchEntire(sentence)?.let { m ->
            val zone = if (m.groupValues[2] == "hand") Zone.Hand else Zone.Graveyard
            // chunk 36: "that card"
            if (m.groupValues[1] != "them") return listOf(Step.Do(Effect.Move(context.it ?: return null, Destination(zone))))
            context.thatMuch = Value.Count(Objects.Bound("moved"))
            return listOf(Step.Do(Effect.Move(context.them ?: return null, Destination(zone), postBind = "moved")))
        }
        ADD_AS_SACRIFICED_COULD.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.AsCouldProduce(ObjectFilter.Is(Objects.Bound(SACRIFICED)))))))
        }
        // chunk 20: "It deals 2 damage to any target." before any target is named: "it" is the source itself.
        if (sentence.startsWith("It deals ") && context.targets.isEmpty()) effect("This card" + sentence.removePrefix("It"), context)?.let { return it }
        // chunk 20: "Attach this Equipment to target creature you control." (CR 701.3)
        ATTACH_SELF_TO_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[2]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Attach(Objects.Self, arcanum.api.Recipients(objects = target))))
        }
        // chunk 20: "Attach it to that creature."
        if (sentence == "Attach it to that creature.") {
            val moved = context.it ?: return null
            val that = context.that ?: return null
            return listOf(Step.Do(Effect.Attach(moved, arcanum.api.Recipients(objects = that))))
        }
        // chunk 20: "Sacrifice that permanent."
        SACRIFICE_THAT.matchEntire(sentence)?.let { context.that?.let { that -> return listOf(Step.Do(Effect.Sacrifice(that))) } }
        // chunk 20: "Return this card from your graveyard to the battlefield"
        RETURN_SELF_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            context.it = Objects.Bound("returned")
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield), postBind = "returned")))
        }
        // chunk 20: "Target player mills cards equal to the sacrificed creature's power."
        TARGET_PLAYER_MILLS_SACRIFICED.matchEntire(sentence)?.let { m ->
            val property = if (m.groupValues[1] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.Toughness
            return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Of(property, Objects.Bound(SACRIFICED), arcanum.api.Combine.Single))))
        }
        // chunk 20: "Put up to one target card from your graveyard on the bottom of your library."
        PUT_UP_TO_TARGET_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[2]
            val filter = Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val slot = Filters.slotName(noun)
            context.targets += TargetSlot(slot, TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(n))
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Library, libraryPosition = -1))))
        }
        // chunk 20: "Look at the top card of target player's library."
        LOOK_TOP_OF_TARGET_LIBRARY.matchEntire(sentence)?.let { m ->
            val n = if (m.groupValues[2].isEmpty()) 1 else Numbers.count(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(context.targetPlayer(), Value.Fixed(n)))))
        }
        // chunk 20: "Look at a card at random in target player's hand."
        if (sentence == "Look at a card at random in target player's hand.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.LookAt(Objects.Random(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.Is(player))))))))
        }
        // chunk 20: "Draw a card at the beginning of the next turn's upkeep.": a delayed trigger (CR 603.7)
        AT_NEXT_UPKEEP.matchEntire(sentence)?.let { m ->
            val inner = Context()
            val steps = effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", inner)?.takeIf { inner.targets.isEmpty() } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep)),
                Instructions(steps = steps))))
        }
        // chunk 20: "Put this artifact on top of its owner's library."
        PUT_SELF_ON_TOP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Library, libraryPosition = 0))))
        }
        // chunk 21: "You may put a creature card with mana value … from your hand onto the battlefield." (zero or one)
        YOU_MAY_PUT_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = counterNoun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))))
        }
        // chunk 21: "Put a charge counter on this artifact." / "Put a +1/+1 counter on target creature you control."
        PUT_COUNTERS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val on = m.groupValues[3]
            val objects = when {
                isSelf(on) -> Objects.Self
                on.startsWith("target ") -> on.removePrefix("target ").let { context.target(Filters.slotName(it), Filters.noun(it) ?: return null) }
                // chunk 35: other wordings ("on Korvold and draw a card") are left to the rules below
                else -> null
            }
            if (objects != null) return listOf(Step.Do(Effect.PutCounters(objects, kind, Value.Fixed(n))))
        }
        // chunk 21: "When a creature card is exiled this way, [effect]": a reflexive trigger (CR 603.12)
        WHEN_EXILED_THIS_WAY.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val reflexive = instructions(m.groupValues[2]) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound("exiled"), filter),
                then = listOf(Step.Do(Effect.ReflexiveTrigger(reflexive)))))
        }
        // chunk 21: "Put a card exiled with this artifact into its owner's graveyard."
        PUT_EXILED_WITH_INTO_GRAVEYARD.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            context.it = Objects.Bound("moved")
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Remembered(EXILED_WITH)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Graveyard), postBind = "moved")))
        }
        // chunk 21: "If it's a land card, create a Treasure token."
        IF_ITS.matchEntire(sentence)?.let { m ->
            val moved = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(moved, filter), then = then))
        }
        // chunk 21: "Exile that card from your graveyard." (a discarded card; nothing happens if it has left)
        if (sentence == "Exile that card from your graveyard.") {
            val that = context.that ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Where(that, ObjectFilter.InZone(Zone.Graveyard)), Destination(Zone.Exile), postBind = "exiled"))) +
                remembered(Objects.Bound("exiled"))
        }
        // chunk 21: "Until end of turn, you may play that card."
        if (sentence == "Until end of turn, you may play that card.") {
            val exiled = context.it ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play)),
                arcanum.api.Affected.Objects(exiled), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 21: "That creature's controller creates a 1/1 colorless Myr artifact creature token."
        THAT_CONTROLLER_CREATES.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.ControllerOf(that))))
        }
        // chunk 21: "You gain 1 life for each spell you've cast this turn."
        GAIN_LIFE_FOR_EACH_SPELL_CAST.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val spells = Value.EventsThisTurn(EventPattern.Cast(by = PlayerFilter.You))
            val n = m.groupValues[2].toInt()
            return listOf(Step.Do(Effect.GainLife(players, if (n == 1) spells else Value.Times(spells, n))))
        }
        // chunk 21: "An opponent gains control of this artifact." (the opponent is chosen on resolution)
        OPPONENT_GAINS_CONTROL.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(
                Step.Choose(arcanum.api.Choice.Player(PlayerFilter.Opponent), bind = "opponent"),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("opponent"))),
                    arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 21: "You may [do something]." with nothing depending on it (CR 608.2d)
        YOU_MAY.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context)?.let { return listOf(Step.May(it)) }
        }
        // chunk 36
        if (sentence == "Discard your hand.") return listOf(Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand))))
        // "Put all cards exiled with this creature into their owners' hands." (CR 607.2a)
        PUT_ALL_EXILED_WITH_INTO_HANDS.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Remembered(EXILED_WITH), Destination(Zone.Hand))))
        }
        // "You may cast it this turn." (a permission for the exiled card, CR 601.2a)
        if (sentence == "You may cast it this turn." && context.it == Objects.Bound("exiled")) {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Cast)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        // "Sacrifice another permanent.": the controller chooses one they control (CR 701.21a)
        SACRIFICE_A.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[2]) ?: return null
            val another = if (m.groupValues[1] == "another") listOf(context.another()) else emptyList()
            val filter = Filters.all(*(another + listOf(kind, ObjectFilter.ControlledBy(PlayerFilter.You))).toTypedArray())
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "sacrificed"),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))
        }
        // "Exile up to one other target nonland permanent."
        EXILE_UP_TO_TARGET.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[3]
            val other = if (m.groupValues[2].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(other + listOf(Filters.noun(noun) ?: return null)).toTypedArray())
            val slot = Filters.slotName(noun)
            context.targets += TargetSlot(slot, TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(n))
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Exile), postBind = "exiled")))
        }
        // "At the beginning of the next end step, [effect].": a delayed trigger (CR 603.7), about the objects named now
        AT_BEGINNING_OF_NEXT_END_STEP.matchEntire(sentence)?.let { m ->
            val targets = context.targets.size
            val inner = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context)?.takeIf { context.targets.size == targets } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End)),
                Instructions(steps = inner))))
        }
        // "Return that card to the battlefield under its owner's control."
        RETURN_IT_UNDER_OWNERS_CONTROL.matchEntire(sentence)?.let {
            val card = context.it ?: return null
            return listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield, controller = Players.OwnerOf(card)), postBind = "returned")))
        }
        MILL.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // "Each opponent loses life equal to that card's mana value." (read as it last was, CR 608.2h)
        LOSE_LIFE_EQUAL_TO_THAT_CARDS_MANA_VALUE.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val card = context.it ?: return null
            return listOf(Step.Do(Effect.LoseLife(players, Value.Of(arcanum.api.NumericProperty.ManaValue, card, arcanum.api.Combine.Single))))
        }
        // "[Effect] unless [condition]." (not "unless [player] pays", which is Step.Unless)
        UNLESS_CONDITION.matchEntire(sentence)?.let { m ->
            Conditions.condition(m.groupValues[2], self)?.let { condition ->
                val then = effect(m.groupValues[1] + ".", context) ?: return null
                return listOf(Step.If(arcanum.api.Condition.Not(condition), then = then))
            }
        }
        // "[A], then [B].": tried last, after the wordings that contain ", then" themselves.
        THEN.matchEntire(sentence)?.let { m ->
            val first = effect(m.groupValues[1] + ".", context) ?: return null
            val second = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return first + second
        }
        // chunk 35: "[A] and [B].", two effects in one sentence ("each opponent loses 1 life and you gain 1 life"): tried
        // last, at each " and " from the left, only when both halves are whole effects.
        if (sentence.endsWith(".")) {
            var at = sentence.indexOf(" and ")
            while (at > 0) {
                val targets = context.targets.size
                val first = effect(sentence.substring(0, at) + ".", context)
                val second = first?.let { effect(sentence.substring(at + 5).replaceFirstChar { it.uppercase() }, context) }
                if (first != null && second != null) return first + second
                while (context.targets.size > targets) context.targets.removeAt(context.targets.lastIndex)
                at = sentence.indexOf(" and ", at + 5)
            }
        }
        return null
    }

    /** "nonland permanent with mana value equal to the number of charge counters on this artifact" (read from the source,
     * its last known information once it has left, CR 608.2h). */
    // chunk 21
    private fun counterNoun(phrase: String): ObjectFilter? {
        val m = WITH_MANA_VALUE_EQUAL_TO_COUNTERS.matchEntire(phrase)?.takeIf { isSelf(it.groupValues[3]) } ?: return null
        val kind = Counters.kind(m.groupValues[2]) ?: return null
        return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal,
            Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single)))
    }

    /** Keeps [objects] as cards "exiled with" this object when the card refers to them (CR 607.2a). */
    // chunk 21
    private fun remembered(objects: Objects): List<Step> =
        if (linkedExile) listOf(Step.Do(Effect.Remember(EXILED_WITH, objects))) else emptyList()

    /** "{C}", "{B}{B}", "{R} or {G}", "one mana of any color", "three mana of any one color" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        ANY_COMBINATION.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), combination = true) }
        ANY_COLOR.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null)) }
        ManaCost.parse(text)?.let { return ManaKind.Fixed(it) }
        val colors = text.split(Regex(""",? or |, """)).map { symbol -> ManaCost.parse(symbol)?.symbols?.singleOrNull()
            ?.let { (it as? arcanum.api.ManaSymbol.Colored)?.color } ?: return null }
        return ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })
    }

    /** chunk 26: "+1", "-X" in "gets +1/+1": X of the spell, or of the permanent's spell (CR 107.3a, 107.3m). */
    private fun ptChange(text: String): Value? {
        val x = if (isSpell) Value.Announced() else Value.CastX()
        return when (text) {
            "+X" -> x
            "-X" -> Value.Times(x, -1)
            else -> text.removePrefix("+").toIntOrNull()?.let { Value.Fixed(it) }
        }
    }

    /** chunk 26: the steps with the last created tokens named [TOKENS] ("they", "them"). */
    private fun bindTokens(steps: List<Step>): List<Step>? {
        val index = steps.indexOfLast { (it as? Step.Do)?.effect is Effect.CreateToken }.takeIf { it >= 0 } ?: return null
        val create = (steps[index] as Step.Do).effect as Effect.CreateToken
        if (create.bind != null && create.bind != TOKENS) return null
        return steps.toMutableList().also { it[index] = Step.Do(create.copy(bind = TOKENS)) }
    }

    /** Sentences in order; a "Spend this mana only …" sentence stays with the mana it restricts. */
    private fun sentences(text: String): List<String> {
        val raw = SENTENCE_END.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.endsWith(".")) it else "$it." }.map { it.replaceFirstChar { c -> c.uppercase() } }
        return raw.fold(mutableListOf()) { acc, sentence ->
            // chunk 14: "The flashback cost is equal to its mana cost." completes the sentence before it.
            if ((sentence.startsWith("Spend this mana only") || sentence.startsWith("If you do,") || sentence.startsWith("The flashback cost is") ||
                    (sentence.startsWith("Put ") && acc.lastOrNull()?.startsWith("Look at the top") == true)) && acc.isNotEmpty())
                acc[acc.size - 1] = acc.last() + " " + sentence
            else acc += sentence
            acc
        }
    }

    private companion object {
        // chunk 35: also after a quoted ability that ends a sentence ('… with "This token can't block." If …')
        val SENTENCE_END = Regex("""(?<=\.)\s+|(?<=\.")\s+""")
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
        val ITS_CONTROLLER_GAINS_LIFE_EQUAL_TO_POWER = Regex("""Its controller gains life equal to its power\.""")
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
        val RETURN_FROM_GRAVEYARD_TO_HAND = Regex("""Return (another )?target (.+) from your graveyard to your hand\.""")
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
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+?)(, reveal (?:it|that card))?, put (?:it|that card) (onto the battlefield|into your hand)(, discard a card at random)?, then shuffle\.""")
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
        // chunk 12
        const val DISCARDED = "discarded"
        const val RETURNED = "returned"
        val SEARCH_LIBRARY_AND_OR_GRAVEYARD = Regex("""Search your library and/or graveyard for (.+?) and put it onto the battlefield\.""")
        val IF_X_AT_LEAST = Regex("""If X is (\d+) or more, (.+)""")
        val GROUP_GETS_UNTIL_EOT = Regex("""(.+?) get ([+-](?:\d+|X))/([+-](?:\d+|X))(?: and gain (.+?))? until end of turn\.""")
        val SHUFFLE_INTO_OWNERS_LIBRARY = Regex("""Shuffle (.+) into its owner's library\.""")
        val DRAW_OR_DISCARD_X = Regex("""(Draw|Discard) X cards\.""")
        val RETURN_ANY_NUMBER_OF_TARGETS_TO_HAND = Regex("""Return any number of target (.+) to their owners' hands\.""")
        val CREATE_FOR_EACH_CARD_TYPE_DISCARDED = Regex("""Create (.+ token(?: with .+?)?) for each card type among cards discarded this way\.""")
        val DRAW_FOR_EACH_RETURNED_TO_HAND = Regex("""Draw a card for each (.+) returned to your hand this way\.""")
        // chunk 25
        val MAY_CAST_WITHOUT_PAYING = Regex("""(Any player|You) may cast (.+ spells.*?) without paying their mana costs( and as though they had flash)?\.""")
        val IF_YOU_WOULD_DRAW = Regex("""If you would draw a card, (.+?) instead\.(?: (.+))?""")
        val DONT_UNTAP_PLURAL = Regex("""(.+) don't untap during their controllers' untap steps\.""")
        val EXTRA_LAND_PLAYS = Regex("""You may play (an additional land|any number of lands|\w+ additional lands) on each of your turns\.""")
        val ENCHANTED_IS = Regex("""Enchanted \w+ is an? (.+?)(?: with (.+?))?( and it loses all other card types)?\.""")
        const val SACRIFICED = "sacrificed"
        val OTHERWISE = Regex("""Otherwise, (.+)""")
        val UNTAP_TARGET = Regex("""Untap target (.+)\.""")
        val UNTAP_ALL = Regex("""Untap all (.+)\.""")
        val IF_ITS_A = Regex("""If it's an? (.+?), (.+)""")
        val PUT_IT_INTO = Regex("""(?:Put|Return) (it|them|that card) (?:into|to) (?:your|its owner's|their owner's|their owners') (hand|graveyard)s?\.""")
        val ADD_AS_SACRIFICED_COULD = Regex("""Add one mana of any type the sacrificed \w+ could produce\.""")
        // chunk 26
        val HAVE_QUOTED = Regex("""(.+) have "(.+)"""")
        val CANT_BE_BLOCKED = Regex("""(.+) can't be blocked\.""")
        val LOYALTY = Regex("""\[([+−-]?)(\d+)\]""")
        const val TOKENS = "tokens"
        val THEY_GAIN = Regex("""They gain (.+)\.""")
        val YOU_MAY_IF_YOU_DO = Regex("""You may (.+?)\. If you do, (.+)""")
        val THAT_CREATURES_CONTROLLER_MAY_DRAW = Regex("""That creature's controller may draw (a|one|two) cards?\.""")
        val DEALS_THAT_MUCH = Regex("""(.+?) deals that much damage to (.+)\.""")
        val PUT_COUNTERS_ON_EACH = Regex("""Put (a|an|one|two|three) (\S+) counters? on each (.+)\.""")
        val EACH_GETS_UNTIL_EOT = Regex("""(?:Each (.+) gets|(.+) get) ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val GAIN_UNTIL_EOT = Regex("""(.+) gains? (.+) until end of turn\.""")
        val MAY_CAST_TARGET_FROM_GRAVEYARD = Regex("""You may cast target (.+?)( with mana value less than or equal to (.+)'s power)? from your graveyard( without paying its mana cost)?\.""")
        val PHASES_OUT = Regex("""(.+) phases out\.""")
        val PLAYERS_LOSE_LIFE = Regex("""(.+) loses? (\d+) life\.""")
        val EMBLEM = Regex("""You get an emblem with "(.+)"\.""")
        val BECOMES_COPY = Regex("""Target (.+) becomes a copy of another target (.+) until end of turn(?:, except it's an? (\w+) in addition to its other types)?\.""")
        // chunk 13
        val ADDITIONAL_EXILE_ANY_NUMBER_REDUCTION = Regex("""As an additional cost to cast this spell, you may exile any number of (.+) from your hand\. This spell costs (\{\d+\}) less to cast for each card exiled this way\.""")
        val BRACKETED = Regex("""(.*?)( ?)\[([^\]]+)\](.*)""")
        val CHOOSE_CARD_NAME = Regex("""Choose an? (?:(.+?) )?card name\.""")
        val TARGET_REVEALS_HAND = Regex("""Target (player|opponent) reveals their hand( and discards all cards with that name)?\.""")
        val YOU_CHOOSE_FROM_IT_AND_GRAVEYARD = Regex("""You choose (an? .+?) from it, then choose (an? .+?) from their graveyard\.""")
        val YOU_CHOOSE_FROM_IT = Regex("""You choose (an? .+?) from it( with .+)?\.""")
        val TARGET_LOSES_YOU_GAIN = Regex("""Target (player|opponent) loses (\d+) life and you gain (\d+) life\.""")
        val PUT_INTO_LIBRARY_FROM_TOP = Regex("""Put target (.+) into its owner's library (second|third|fourth) from the top\.""")
        val FROM_TOP = mapOf("second" to 1, "third" to 2, "fourth" to 3)
        val ITS_CONTROLLER_GAINS_N = Regex("""Its controller gains (\d+) life\.""")
        val EACH_OPPONENT_SACRIFICES_GREATEST = Regex("""Each opponent sacrifices an? (.+?) with the greatest mana value among (.+) they control\.""")
        val DESTROY_UP_TO = Regex("""Destroy up to (\w+) target (.+)\.""")
        val CREATE_TOKENS_WITH_QUOTED = Regex("""Create (twice X|a|an|one|two|three|four|five) (\d+/\d+ .+? creature) tokens? with "(.+)"\.""")
        // chunk 14
        val DIVIDED_AMONG_ANY_NUMBER = Regex("""(.+?) deals X damage divided as you choose among any number of targets\.""")
        val THAT_PLAYER_OR_CONTROLLER_MAY_PAY = Regex("""That player or that permanent's controller may pay (\{.+\})\.""")
        val COPY_TARGET = Regex("""Copy target (.+)\.""")
        val COPY_NEW_TARGETS = Regex("""You may choose new targets for the cop(?:y|ies)\.""")
        val IF_CAST_FROM_GRAVEYARD = Regex("""If this spell was cast from a graveyard, (.+)""")
        val UNTIL_END_OF_TURN_WHENEVER = Regex("""Until end of turn, whenever (.+?), (.+)""")
        val WHENEVER_THIS_TURN = Regex("""Whenever (.+?) this turn, (.+)""")
        val RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (another )?target (.+?) from your graveyard to the battlefield( tapped)?\.""")
        val GAINS_FLASHBACK_OF_ITS_MANA_COST = Regex("""Each (.+?) card in your graveyard gains flashback until end of turn\. The flashback cost is equal to its mana cost\.""")
        val CAST_UP_TO_ONE_EACH_FROM_GRAVEYARD = Regex("""You may cast (.+?) from your graveyard each with mana value (X|\d+) or less without paying their mana costs\.""")
        val UP_TO_ONE_TARGET_CARD = Regex("""up to one target (\w+) card""")
        val IF_X_AT_LEAST_COPY_TWICE = Regex("""If X is (\d+) or more, copy each of those spells twice\.""")
        val DISCARD_X_CARDS = "Discard X cards"
        // chunk 15
        val RETURN_SELF_FROM_GRAVEYARD_TO_HAND = Regex("""Return (.+) from your graveyard to your hand\.""")
        val RETURN_EACH_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return each (.+?) from your graveyard to the battlefield\.""")
        val CHOOSE_UP_TO_TARGET_PUT_THERE_THIS_TURN = Regex("""Choose up to (\w+) target (.+?) in your graveyard that were put there from the battlefield this turn\.""")
        val RETURN_THEM_TO_BATTLEFIELD = Regex("""Return them to the battlefield( tapped)?\.""")
        val RETURN_ALL_PUT_THERE_THIS_TURN = Regex("""(Each player returns|Return) to the battlefield all (.+?) in (their|your) graveyard that were put there from the battlefield this turn\.""")
        val PUT_THERE_FROM_BATTLEFIELD_THIS_TURN = ObjectFilter.ThisTurn(EventPattern.ZoneChange(from = Zone.Battlefield, to = Zone.Graveyard))
        val LOOK_PUT_UP_TO_ONTO_BATTLEFIELD_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put up to (\w+) (.+?) from among them onto the battlefield\. Put the rest on the bottom of your library in any order\.""")
        val THAT_PLAYER_ADDS_ADDITIONAL = Regex("""That player adds an additional (\{.\})\.""")
        // chunk 20
        val ATTACHED_GETS = Regex("""(?:Equipped|Enchanted) creature gets ([+-]\d+)/([+-]\d+)(?: for each (.+))?\.""")
        val ATTACH_SELF_TO_TARGET = Regex("""Attach (.+) to target (.+)\.""")
        val SACRIFICE_THAT = Regex("""Sacrifice that (\w+)\.""")
        val RETURN_SELF_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (.+) from your graveyard to the battlefield\.""")
        val TARGET_PLAYER_MILLS_SACRIFICED = Regex("""Target player mills cards equal to the sacrificed \w+'s (power|toughness)\.""")
        val PUT_UP_TO_TARGET_ON_BOTTOM = Regex("""Put up to (\w+) target (.+) from your graveyard on the bottom of your library\.""")
        val LOOK_TOP_OF_TARGET_LIBRARY = Regex("""Look at the top (card|(\w+) cards) of target player's library\.""")
        val AT_NEXT_UPKEEP = Regex("""(.+) at the beginning of the next turn's upkeep\.""")
        val PUT_SELF_ON_TOP = Regex("""Put (.+) on top of its owner's library\.""")
        val IF_INSTEAD = Regex("""If (.+?), (.+) instead\.""")
        // chunk 21
        val MAY_PLAY_FROM_GRAVEYARD = Regex("""You may (play|cast) (.+) from your graveyard\.""")
        val SPEND_AS_ANY_COLOR_TO_ACTIVATE = Regex("""You may spend mana as though it were mana of any color to activate abilities of (.+)\.""")
        val HAVE_ABILITIES_OF_EXILED = Regex("""(.+) have all activated abilities of all (.+) cards exiled with (.+)\.""")
        val ENTERS_WITH_COUNTERS = Regex("""(.+) enters with (\w+) (\S+) counters? on it\.""")
        val ACTIVATE_ONLY_AS_SORCERY = Regex("""Activate only as a sorcery\.""")
        val ACTIVATE_ONLY_DURING_YOUR_TURN = Regex("""Activate only during your turn\.""")
        val TRIGGERS_ONLY_ONCE = Regex("""This ability triggers only once each turn\.""")
        val CHOOSE_MODES = Regex("""choose (one|two)( that hasn't been chosen( this turn)?)? —""")
        val DURING_YOUR_TURN = Regex("""(.+) during your turn""")
        val ONE_OR_MORE_LEAVE = Regex("""one or more (.+) leave the battlefield""")
        val YOU_CREATE_ONE_OR_MORE = Regex("""you create one or more (.+) tokens""")
        val REMOVE_COUNTERS_FROM_SELF = Regex("""Remove (a|an|one|two|three) (\S+) counters? from (.+)""")
        val WITH_MANA_VALUE_EQUAL_TO_COUNTERS = Regex("""(.+) with mana value equal to the number of (\S+) counters on (.+)""")
        val EXILE_TARGET_UNTIL_LEAVES = Regex("""Exile target (.+) until (.+) leaves the battlefield\.""")
        val EXILE_TARGET_FROM_A_GRAVEYARD = Regex("""Exile target (.+) from a graveyard\.""")
        val YOU_MAY_PUT_FROM_HAND = Regex("""You may put an? (.+) from your hand onto the battlefield\.""")
        val PUT_COUNTERS = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+)\.""")
        val WHEN_EXILED_THIS_WAY = Regex("""When an? (.+) is exiled this way, (.+)""")
        val PUT_EXILED_WITH_INTO_GRAVEYARD = Regex("""Put an? (.+?) exiled with (.+) into its owner's graveyard\.""")
        val IF_ITS = Regex("""If it's an? (.+?), (.+)""")
        val THAT_CONTROLLER_CREATES = Regex("""That \w+'s controller creates (.+ tokens?)\.""")
        val GAIN_LIFE_FOR_EACH_SPELL_CAST = Regex("""(.+) gains? (\d+) life for each spell you've cast this turn\.""")
        val OPPONENT_GAINS_CONTROL = Regex("""An opponent gains control of (.+)\.""")
        val YOU_MAY = Regex("""You may (.+)\.""")
        // chunk 35
        val POWER_IS_EQUAL_TO = Regex("""(.+)'s power is equal to (.+)\.""")
        val DEVOTION_TO = Regex("""your devotion to (\w+(?: and \w+)?)""")
        val CANT_BLOCK = Regex("""(.+) can't block\.""")
        val CREATE_FOR_EACH_COUNTER_ON_SELF = Regex("""Create (an? .+ token(?: with .+?)?) for each (\S+) counter on (.+)\.""")
        val DRAW_FOR_EACH_COUNTER_ON = Regex("""Draw a card for each (\S+) counter on (.+)\.""")
        val CREATE_X_WHERE_SELF_POWER = Regex("""Create X (tapped )?(.+ tokens), where X is (.+)'s power\.""")
        val DEALS_DAMAGE_EQUAL_TO_ITS_POWER = Regex("""(.+?) deals damage equal to its power to (any target|each opponent)\.""")
        val RETURN_TO_HAND_WITH_LESSER_MANA_VALUE = Regex("""Return to your hand target (.+?) in your graveyard with lesser mana value\.""")
        val EACH_PLAYER_MAY_SEARCH_UP_TO = Regex("""Each player may search their library for up to (\w+) (.+?), put them onto the battlefield, then shuffle\.""")
        const val WITH_MANA_VALUE_X = " with mana value X"
        val THE_CREATURE_HAD = Regex("""the creature had (power|toughness) (\d+) or greater""")
        val OF_THOSE_TOKENS = Regex("""create (\w+) of those tokens""")
        // chunk 36
        val YOU_DISCARD_ONE_OR_MORE = Regex("""you discard one or more (.+)""")
        val ATTACKS_EACH_COMBAT = Regex("""(.+) attacks each combat if able\.""")
        val CANT_BE_BLOCKED_EXCEPT_BY = Regex("""(.+) can't be blocked except by (.+)\.""")
        val IN_YOUR_GRAVEYARD_HAVE = Regex("""(.+) in your graveyard have (.+)\.""")
        val DURING_YOUR_TURN_STATIC = Regex("""During your turn, (.+)""")
        val PAY_MANA = Regex("""Pay (\{.+\})""")
        val EXILE_TOP_CARD = Regex("""Exile the top card of your library( face down)?\.""")
        const val EXILE_ONE_OF_THEM_FROM_GRAVEYARD = "Exile one of them from your graveyard"
        val MAY_PUT_FROM_AMONG_THEM_INTO_HAND = Regex("""You may put an? (.+?) from among them into your hand\.""")
        val IF_IT_ENTERED_UNDER_YOUR_CONTROL = Regex("""If it entered under your control, (.+)""")
        val PUT_ALL_EXILED_WITH_INTO_HANDS = Regex("""Put all cards exiled with (.+) into their owners' hands\.""")
        val SACRIFICE_A = Regex("""Sacrifice (another|an?) (.+)\.""")
        val EXILE_UP_TO_TARGET = Regex("""Exile up to (\w+) (other )?target (.+)\.""")
        val AT_BEGINNING_OF_NEXT_END_STEP = Regex("""At the beginning of the next end step, (.+)""")
        val RETURN_IT_UNDER_OWNERS_CONTROL = Regex("""Return (?:that card|it) to the battlefield under its owner's control\.""")
        val MILL = Regex("""Mill (\w+) cards?\.""")
        val LOSE_LIFE_EQUAL_TO_THAT_CARDS_MANA_VALUE = Regex("""(.+) loses? life equal to that card's mana value\.""")
        val UNLESS_CONDITION = Regex("""(.+) unless (.+)\.""")
    }
}

internal fun String.removeArticle() = removePrefix("a ").removePrefix("an ")
