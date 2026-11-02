package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
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
    /** chunk 40: parsing an activated ability with X in its cost: X is the announced value (CR 107.3a). */
    private var announcedX = false

    /** The card's name or "this land" / "This land" (a sentence may start with it). */
    private fun isSelf(text: String) = text in self || text.replaceFirstChar { it.lowercase() } in self

    /** Lines that are more than one ability. */
    fun multi(line: String): List<Ability>? {
        // dsk chunk 14: "This land enters tapped. As it enters, choose a color." (two replacement effects, CR 614.1c, 614.12)
        ENTERS_TAPPED_AS_IT_ENTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val tapped = static("${m.groupValues[1]} enters tapped.") ?: return null
            val choose = static("As ${m.groupValues[1].replaceFirstChar { it.lowercase() }} enters, ${m.groupValues[2]}") ?: return null
            return listOf(tapped, choose)
        }
        // dsk chunk 13: a conditional permission to cast it from the graveyard and a counter when it was (CR 614.1c)
        MAY_CAST_SELF_FROM_GRAVEYARD_IF.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) && isSelf(it.groupValues[3]) }?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self) ?: return null
            val kind = Counters.kind(m.groupValues[4]) ?: return null
            return listOf(
                Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self), condition = condition),
                Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(kind, Value.Fixed(1))))), affects = Affected.Objects(Objects.Self),
                    condition = arcanum.api.Condition.CastFrom(Zone.Graveyard, Objects.Self)),
            )
        }
        // chunk 25: "Any player may cast creature spells with mana value 3 or less without paying their mana costs and as
        // though they had flash." A permission to cast from hand for free (each player for their own cards) and
        // player-wide flash (CR 601.2a, 118.9, 702.8a).
        MAY_CAST_WITHOUT_PAYING.matchEntire(line)?.let { m ->
            val anyPlayer = m.groupValues[1] == "Any player"
            val spells = Filters.noun(m.groupValues[2].replace(" spells", "")) ?: return null
            val owner = if (anyPlayer) PlayerFilter.Is(Players.Holder) else PlayerFilter.You
            val free = Ability.Static(
                listOf(Mechanic.MayPlay(Zone.Hand, withoutPaying = true, holder = if (anyPlayer) PlayerFilter.Any else PlayerFilter.You, how = PlayKind.Cast)),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(spells, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(owner)))))
            if (m.groupValues[3].isEmpty()) return listOf(free)
            val players = if (anyPlayer) Players.Each(PlayerFilter.Any) else Players.You
            return listOf(free, Ability.Static(listOf(Mechanic.CastAsThoughFlash(spells)), affects = arcanum.api.Affected.Players(players)))
        }
        // chunk 52: "This creature has all activated abilities of the chosen permanent except for loyalty abilities. You may
        // spend mana as though it were mana of any color to activate those abilities." (layer 6, CR 613.1f)
        HAS_ACTIVATED_ABILITIES_OF.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            // dsk chunk 14: "creatures you control that don't have the same name as this creature"
            val of = chosenObjects(m.groupValues[2]) ?: WITHOUT_SAME_NAME.matchEntire(m.groupValues[2])?.takeIf { isSelf(it.groupValues[2]) }?.let {
                Objects.Each(Filters.all(Filters.noun(it.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.SameNameAs(Objects.Self))))
            } ?: return null
            val gains = Ability.Static(listOf(Mechanic.GainAbilitiesOf(of, exceptLoyalty = m.groupValues[3].isNotEmpty())), affects = Affected.Objects(Objects.Self))
            if (m.groupValues[4].isEmpty()) return listOf(gains)
            return listOf(gains, Ability.Static(listOf(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = ObjectFilter.Is(Objects.Self)))),
                affects = arcanum.api.Affected.Players(Players.You)))
        }
        // blb chunk 12: "This land enters tapped. As it enters, choose a color." (two abilities on one line)
        Blb.ENTERS_TAPPED_AS_IT_ENTERS.matchEntire(line)?.let { m ->
            val tapped = line(m.groupValues[1] + " enters tapped.") ?: return null
            val choose = line("As " + m.groupValues[1].replaceFirstChar { it.lowercase() } + " enters, " + m.groupValues[2]) ?: return null
            return listOf(tapped, choose)
        }
        // blb chunk 12: "You may play lands and cast spells with mana value 4 or greater from the top of your library." (two
        // permissions, CR 601.2a, 305.1)
        Blb.MAY_PLAY_LANDS_AND_CAST_FROM_TOP.matchEntire(line)?.let { m ->
            fun top(cards: ObjectFilter, how: PlayKind) = Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, how, topOnly = true, holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Each(Filters.all(cards, ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))
            val comparison = if (m.groupValues[2] == "greater") arcanum.api.Comparison.AtLeast else arcanum.api.Comparison.AtMost
            return listOf(top(ObjectFilter.Type(arcanum.api.CardType.Land), PlayKind.Play),
                top(Filters.all(ObjectFilter.Not(ObjectFilter.Type(arcanum.api.CardType.Land)), ObjectFilter.ManaValue(comparison, Value.Fixed(m.groupValues[1].toInt()))), PlayKind.Cast))
        }
        return null
    }

    /** dsk chunk 13: what "that much"/"that many" means after an intervening if that counts something. */
    private var conditionAmount: Value? = null

    /** dsk chunk 13: the amount [condition] counts ("if you sacrificed one or more permanents this turn": how many). */
    private fun amountOf(condition: arcanum.api.Condition): Value? = when (condition) {
        is arcanum.api.Condition.HappenedThisTurn -> Value.EventsThisTurn(condition.event, condition.measure)
        is arcanum.api.Condition.Prime -> condition.value
        is arcanum.api.Condition.And -> condition.all.mapNotNull(::amountOf).lastOrNull()
        else -> null
    }

    /** dsk chunk 11: the card refers to "the discarded card": a discard of its additional cost names it. */
    var discardedCard = false

    /** chunk 33: what "the exiled card" means on this card (linked abilities, CR 607.2a); null when nothing links. */
    var exiledCard: Objects? = null

    /** chunk 31: what "it" means in the triggered ability being read, and the zone it is in ("you may exile it" in a
     * dies trigger: the new card in the graveyard, CR 400.7). */
    private var triggerIt: Pair<Objects, Zone?>? = null

    fun line(line: String): Ability? {
        // chunk 56: a Saga's "I, II — [effect]" is a chapter ability for each listed chapter (CR 714.2b, c)
        CHAPTERS.matchEntire(line)?.let { m ->
            val chapters = m.groupValues[1].split(", ").mapNotNull { ROMAN[it] }
            if (chapters.size == m.groupValues[1].split(", ").size)
                return Ability.Triggered(arcanum.api.Trigger.Chapter(chapters), instructions(m.groupValues[2]) ?: return null)
        }
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
            // chunk 50: flavor words too (CR 207.2d)
            if (!RulesWords.isAbilityOrFlavorWord(word)) return null
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
        if (m.groupValues[1].isNotEmpty() && !RulesWords.isAbilityOrFlavorWord(m.groupValues[1])) return null
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

    /**
     * dsk chunk 07: a later paragraph of an instant or sorcery that refers to the earlier paragraphs' targets ("that
     * creature", "the creature you control"), read with them: one spell ability (CR 113.3a). Its ability word, if any, is
     * dropped with the paragraph joined. Null when it can't be read that way.
     */
    fun continuation(line: String, previous: Instructions): Instructions? {
        val text = ABILITY_WORD.matchEntire(line)?.takeIf { RulesWords.isAbilityOrFlavorWord(it.groupValues[1]) }?.groupValues?.get(2) ?: line
        val read = instructions(text, seedTargets = previous.targets) ?: return null
        return previous.copy(targets = read.targets, steps = previous.steps + read.steps, replacements = previous.replacements + read.replacements,
            announced = (previous.announced + read.announced).distinct())
    }

    /** dsk chunk 07: "Delirium — If [condition], instead [instructions].": the whole spell is replaced when it holds. */
    fun insteadAll(line: String, spell: Ability.Spell): Ability.Spell? {
        val m = IF_INSTEAD_ALL.matchEntire(line) ?: return null
        if (m.groupValues[1].isNotEmpty() && !RulesWords.isAbilityOrFlavorWord(m.groupValues[1])) return null
        val condition = Conditions.condition(m.groupValues[2], self) ?: return null
        val replacement = instructions(m.groupValues[3].replaceFirstChar { it.uppercase() })?.takeIf { it.targets.isEmpty() } ?: return null
        return spell.copy(abilityWord = m.groupValues[1].ifEmpty { null }, instructions = spell.instructions.copy(
            steps = listOf(Step.If(condition, then = replacement.steps, otherwise = spell.instructions.steps))))
    }

    /** dsk chunk 07: "Delirium — Choose one. If [condition], choose one or more instead." with its modes (CR 700.2). */
    fun modesWithMaxIf(header: String, modeLines: List<String>): Ability.Spell? {
        val m = CHOOSE_MAX_IF.matchEntire(header) ?: return null
        if (m.groupValues[1].isNotEmpty() && !RulesWords.isAbilityOrFlavorWord(m.groupValues[1])) return null
        val n = Numbers.count(m.groupValues[2]) ?: return null
        val condition = Conditions.condition(m.groupValues[3], self) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it) ?: return null, text = it) }
        if (options.isEmpty()) return null
        val maxIf = when (m.groupValues[4]) { "one or more" -> options.size; "both" -> 2; else -> Numbers.count(m.groupValues[4]) ?: return null }
        return Ability.Spell(Instructions(modes = arcanum.api.Modes(options, min = n, max = n, maxIf = condition, maxIfCount = maxIf, chooser = Players.You)),
            abilityWord = m.groupValues[1].ifEmpty { null })
    }

    /** dsk chunk 12: "[cost]: Choose one —" with its modes: a modal activated ability (CR 700.2). */
    fun modalActivated(costText: String, choose: MatchResult, modeLines: List<String>): Ability.Activated? {
        val cost = cost(costText) ?: return null
        val modes = modes(choose, modeLines) ?: return null
        return Ability.Activated(cost, Instructions(modes = modes),
            arcanum.api.ActivationRules(timing = arcanum.api.Timing.Any, activatedBy = PlayerFilter.You))
    }

    /** "Choose one —" with its modes; "Choose three. You may choose the same mode more than once." (CR 700.2) */
    fun modes(choose: MatchResult, modeLines: List<String>): arcanum.api.Modes? {
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it) ?: return null, text = it) }
        if (options.isEmpty()) return null
        // "Choose one or more —" / "Choose one or both —": up to every mode, each at most once.
        val max = if (choose.groupValues[2].isNotEmpty()) options.size else n
        return arcanum.api.Modes(options, min = n, max = max, repeatable = choose.value.contains("same mode"), chooser = Players.You)
    }

    /** The instructions of an instant or sorcery (CR 113.3a). */
    private fun spell(line: String): Ability? = if (isSpell) instructions(line)?.let { Ability.Spell(it) } else null

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (CR 614.12, 118.12a) */
    private fun static(line: String): Ability? {
        blbStatic(line)?.let { return it }
        blbGapsStatic(line)?.let { return it } // blb gaps
        blbGaps2Static(line)?.let { return it } // blb gaps 2
        rtrStatic(line)?.let { return it } // rtr
        // dsk chunk 02: "You have no maximum hand size[ and don't lose the game for having 0 or less life]." (CR 402.2, 704.5a)
        NO_MAXIMUM_HAND_SIZE.matchEntire(line)?.let { m ->
            val mechanics = listOf(Mechanic.NoMaximumHandSize) + listOfNotNull(Mechanic.NoLossForZeroLife.takeIf { m.groupValues[1].isNotEmpty() })
            return Ability.Static(mechanics, affects = Affected.Players(Players.You))
        }
        // dsk chunk 02: "[Once each turn, ]you may pay {0} rather than pay the mana cost for a spell you cast from exile." An
        // alternative cost (CR 118.9) for the spells its controller casts (from exile, or from anywhere; dsk gaps: the spell
        // cast from a zone, CR 601.2a, not the card in that zone)
        ALTERNATIVE_COST_FOR_SPELLS.matchEntire(line)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            val cards = if (m.groupValues[3].isEmpty()) ObjectFilter.WasCast() else ObjectFilter.WasCast(Zone.Exile)
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost.mana(cost), perTurn = 1.takeIf { m.groupValues[1].isNotEmpty() })),
                affects = Affected.Objects(Objects.Each(cards)))
        }
        // dsk chunk 13: "This permanent is a 3/4 Ninja creature and has hexproof." (layers 4, 7b and 6)
        SELF_IS_PT_CREATURE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val subtypes = m.groupValues[4].split(' ').filter { it.isNotEmpty() }.map { arcanum.api.SubtypeRef.Literal(Subtype(it.takeIf { s -> s in RulesWords.subtypes } ?: return null)) }
            val keywords = m.groupValues[5].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }
            return Ability.Static(listOfNotNull(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = subtypes),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt())),
                keywords?.let { Mechanic.GainAbilities(it) },
            ), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 11: "This creature must be blocked if able." (CR 509.1c)
        MUST_BE_BLOCKED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { return Ability.Static(listOf(Mechanic.MustBeBlocked), affects = Affected.Objects(Objects.Self)) }
        // dsk chunk 11: "This creature has first strike during your turn."
        HAS_KEYWORDS_DURING_YOUR_TURN.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self),
                condition = arcanum.api.Condition.YourTurn)
        }
        // dsk chunk 11: "Equipped creature gets +X/+0, where X is the number of rev counters on this Equipment."
        ATTACHED_GETS_X_COUNTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[4]) }?.let { m ->
            val x = Value.Of(arcanum.api.NumericProperty.Counters(Counters.kind(m.groupValues[3]) ?: return null), Objects.Self, arcanum.api.Combine.Single)
            fun part(text: String) = if (text == "+X") x else Value.Fixed(text.toInt())
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(part(m.groupValues[1]), part(m.groupValues[2]))),
                affects = Affected.Objects(Objects.AttachedTo(Objects.Self)))
        }
        // dsk chunk 10: "Enchanted player can't gain life." (CR 303.4m)
        if (line == "Enchanted player can't gain life.") return Ability.Static(listOf(Mechanic.CantGainLife), affects = Affected.Players(Players.AttachedTo(Objects.Self)))
        // dsk chunk 10: "Creatures your opponents control can be the targets of spells and abilities as though they didn't have
        // hexproof. Ward abilities of those creatures don't trigger." (CR 702.11b, 702.21a)
        TARGETABLE_DESPITE_HEXPROOF.matchEntire(line)?.let { m ->
            val affected = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val mechanics = listOf<Mechanic>(Mechanic.TargetableDespiteHexproof) + listOfNotNull(Mechanic.WardDoesntTrigger.takeIf { m.groupValues[2].isNotEmpty() })
            return Ability.Static(mechanics, affects = Affected.Objects(Objects.Each(affected)))
        }
        // dsk chunk 10: "If a card you didn't control would be put into an opponent's graveyard from anywhere, exile it instead."
        // The exiled cards stay linked to this object when another ability refers to them (CR 607.2a)
        CARD_YOU_DIDNT_CONTROL_EXILE_INSTEAD.matchEntire(line)?.let {
            val card = Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.Not(ObjectFilter.ControlledBy(PlayerFilter.You)),
                ObjectFilter.OwnedBy(PlayerFilter.Opponent))
            val exile = Step.Do(Effect.Move(Objects.EventObject(EventRole.Before), Destination(Zone.Exile), postBind = "exiled"))
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(card, to = Zone.Graveyard),
                instead = Instructions(steps = listOf(exile) + remembered(Objects.Bound("exiled"))))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 10: "During your turn, you may play cards exiled with Valgavoth. If you cast a spell this way, pay life equal to
        // its mana value rather than pay its mana cost." (CR 118.9)
        PLAY_EXILED_WITH_FOR_LIFE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You,
                cost = Cost(listOf(CostPart.PayLife(Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Affected, arcanum.api.Combine.Single)))))),
                affects = Affected.Objects(Objects.Remembered(EXILED_WITH)), condition = arcanum.api.Condition.YourTurn)
        }
        // dsk chunk 09: "You control enchanted creature." (layer 2, CR 613.1b)
        if (line == "You control enchanted creature.") return Ability.Static(listOf(Mechanic.ControlledBy(Players.You)), affects = Affected.Objects(Objects.AttachedTo(Objects.Self)))
        // dsk chunk 09: "This spell costs {3} less to cast if it targets a tapped creature." (CR 601.2f: the spell's target slot,
        // named as the spell names it)
        SELF_COSTS_LESS_IF_IT_TARGETS.matchEntire(line)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            val noun = m.groupValues[2]
            val words = noun.split(' ')
            val kind = Filters.noun(words.last()) ?: return null
            val quality = Filters.noun(words.dropLast(1).joinToString(" ").ifEmpty { return null }) ?: return null
            if (kind == quality) return null
            return Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(mana, Value.Fixed(1)))),
                affects = Affected.Objects(Objects.Self), condition = arcanum.api.Condition.ObjectMatches(Objects.Target(Filters.slotName(words.last())), quality))
        }
        // dsk chunk 09: "This token can't attack or block alone." (CR 506.5)
        CANT_ATTACK_OR_BLOCK_ALONE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantAttackAlone, Mechanic.CantBlockAlone), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 09: "If a source you control would deal damage to an opponent, prevent that damage and each opponent mills that
        // many cards.": a prevention effect with an additional effect (CR 615.5; dsk gaps: not a replacement)
        if (line == "If a source you control would deal damage to an opponent, prevent that damage and each opponent mills that many cards.") {
            return Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You), RecipientFilter(players = PlayerFilter.Opponent)),
                additional = Instructions(steps = listOf(Step.Do(Effect.Mill(Players.Each(PlayerFilter.Opponent), Value.EventAmount)))))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 08: "If this card is in your opening hand, you may begin the game with it on the battlefield." (CR 103.6a)
        if (line == "If this card is in your opening hand, you may begin the game with it on the battlefield.")
            return Ability.Static(listOf(Mechanic.MayBeginOnBattlefield), affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand))
        // dsk chunk 08: "If you would gain life, you gain that much life plus 1 instead." (CR 614.1a)
        GAIN_THAT_MUCH_PLUS.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You),
                modify = listOf(Modification.AddAmount(Value.Fixed(m.groupValues[1].toInt()))))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 08: "If a card would be put into an opponent's graveyard from anywhere, exile it instead." (CR 614.1a)
        WOULD_BE_PUT_INTO_GRAVEYARD_EXILE_INSTEAD.matchEntire(line)?.let { m ->
            val owner = Filters.noun(m.groupValues[1]) ?: return null
            val whose = if (m.groupValues[2] == "an opponent's") PlayerFilter.Opponent else PlayerFilter.You
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(Filters.all(owner, ObjectFilter.OwnedBy(whose)), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 08: "Creatures you control are the chosen type in addition to their other types. The same is true for
        // creature spells you control and creature cards you own that aren't on the battlefield." (CR 607.2d)
        if (line == "Creatures you control are the chosen type in addition to their other types. The same is true for creature spells you control and creature cards you own that aren't on the battlefield.") {
            val creature = ObjectFilter.Type(CardType.Creature)
            val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
            return Ability.Static(listOf(Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Remembered(CHOSEN_TYPE)))), affects = Affected.Objects(Objects.Union(listOf(
                Objects.Each(Filters.all(creature, yours)),
                Objects.Each(Filters.all(creature, ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), yours)),
                Objects.Each(Filters.all(creature, ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.Or(EVERY_ZONE.filter { it != Zone.Battlefield && it != Zone.Stack }.map { ObjectFilter.InZone(it) })))))))
        }
        // dsk chunk 07: "This spell costs {2} less to cast as long as …" (CR 601.2f)
        SELF_COSTS_LESS_AS_LONG_AS.matchEntire(line)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(mana, Value.Fixed(1)))),
                affects = Affected.Objects(Objects.Self), condition = Conditions.condition(m.groupValues[2], self) ?: return null)
        }
        // dsk chunk 07: "This creature can't attack or block unless …"
        CANT_ATTACK_OR_BLOCK_UNLESS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self),
                condition = arcanum.api.Condition.Not(Conditions.condition(m.groupValues[2], self) ?: return null))
        }
        // dsk chunk 07: "Each opponent's maximum hand size is equal to seven minus the number of those card types." (after a
        // delirium condition: the card types among cards in your graveyard)
        MAXIMUM_HAND_SIZE_MINUS_CARD_TYPES.matchEntire(line)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.MaximumHandSize(Value.Difference(Value.Fixed(n),
                Values.value("the number of card types among cards in your graveyard")!!))), affects = Affected.Players(Players.Each(PlayerFilter.Opponent)))
        }
        // dsk chunk 07: "If a source you control would deal noncombat damage to a permanent or player while …, it deals double
        // that damage instead." (CR 614.1a)
        WOULD_DEAL_DOUBLE.matchEntire(line)?.let { m ->
            val combat = when (m.groupValues[1]) { "noncombat " -> false; "combat " -> true; else -> null }
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You),
                RecipientFilter(objects = ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent), players = PlayerFilter.Any), combat = combat),
                modify = listOf(Modification.MultiplyAmount(2)))), affects = Affected.Objects(Objects.Self),
                condition = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { Conditions.condition(it, self) ?: return null })
        }
        // dsk chunk 06: "You may look at face-down creatures your opponents control any time."
        MAY_LOOK_AT_ANY_TIME.matchEntire(line)?.let { m ->
            Filters.noun(m.groupValues[1])?.let { return Ability.Static(listOf(Mechanic.MayLookAt(Objects.Each(it))), affects = Affected.Players(Players.You)) }
        }
        // dsk chunk 05: "Enchanted creature loses all abilities and is a Toy artifact creature with base power and toughness 0/2 in
        // addition to its other types." (layers 4, 6, 7b, CR 613.1d, f, 613.4b)
        LOSES_ALL_AND_IS.matchEntire(line)?.let { m ->
            val affected = attachedSubject(m.groupValues[1]) ?: return null
            val add = mutableListOf<CardType>()
            val subtypes = mutableListOf<arcanum.api.SubtypeRef>()
            for (word in m.groupValues[2].split(' ')) {
                val type = Filters.CARD_TYPES[word]
                when {
                    type != null -> add += type
                    word in RulesWords.subtypes -> subtypes += arcanum.api.SubtypeRef.Literal(Subtype(word))
                    else -> return null
                }
            }
            return Ability.Static(listOf(Mechanic.LoseAbilities(), Mechanic.SetTypes(add = add, addSubtypes = subtypes),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[3].toInt()), Value.Fixed(m.groupValues[4].toInt()))), affects = Affected.Objects(affected))
        }
        // dsk chunk 05: "As long as enchanted creature is face down, it can't be turned face up."
        FACE_DOWN_CANT_BE_TURNED_FACE_UP.matchEntire(line)?.let { m ->
            val affected = attachedSubject(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CantBeTurnedFaceUp), affects = Affected.Objects(affected),
                condition = arcanum.api.Condition.ObjectMatches(affected, ObjectFilter.HasStatus(arcanum.api.Status.FaceDown)))
        }
        // dsk chunk 05: "Enchanted creature doesn't untap during its controller's untap step."
        DOESNT_UNTAP_DURING_CONTROLLERS.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(attachedSubject(m.groupValues[1]) ?: return null))
        }
        // dsk chunk 04: "This creature can't be blocked by Glimmers." (CR 509.1b)
        CANT_BE_BLOCKED_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeBlockedBy(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 03: "Unlock costs you pay cost {1} less." (the unlock special action, CR 709.5e)
        UNLOCK_COSTS_LESS.matchEntire(line)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CostChange(Filters.all(ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Unlock), ObjectFilter.ControlledBy(PlayerFilter.You)),
                change = arcanum.api.CostChange.Less(mana, Value.Fixed(1)))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 01: "Untap each creature you control during each other player's untap step." (CR 502.3)
        UNTAP_DURING_OTHER_UNTAP_STEPS.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.UntapsDuringOtherUntapSteps), affects = Affected.Objects(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null)))
        }
        // dsk chunk 01: "Creatures you control have base power and toughness each equal to the number of creatures you control."
        // (layer 7b, CR 613.4b)
        HAVE_BASE_PT_EQUAL_TO_NUMBER.matchEntire(line)?.let { m ->
            val affected = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val count = Value.Count(Filters.objects(m.groupValues[2]) ?: return null)
            return Ability.Static(listOf(Mechanic.SetPowerToughness(count, count)), affects = Affected.Objects(Objects.Each(affected)))
        }
        // dsk chunk 01: "If a triggered ability of a permanent you control triggers, that ability triggers an additional time."
        // (CR 603.2d)
        TRIGGERS_ADDITIONAL_TIME.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.TriggersAdditionalTime(null, Filters.noun(m.groupValues[1]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // "[If …, ]you may [cost] rather than pay this spell's mana cost." (CR 118.9)
        ALTERNATIVE_COST.matchEntire(line)?.let { m ->
            val parts = m.groupValues[2].split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten()
            val condition = when (val c = m.groupValues[1]) {
                "" -> null
                "it's not your turn" -> arcanum.api.Condition.Not(arcanum.api.Condition.YourTurn)
                else -> Conditions.condition(c, self) ?: return null
            }
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost(parts))), condition = condition, affects = Affected.Objects(Objects.Self))
        }
        if (line == "This spell can't be countered.") return Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self))
        // "If X is 5 or more, this spell can't be countered and the damage can't be prevented." (CR 107.3m, 615.12)
        X_AT_LEAST_UNCOUNTERABLE.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeCountered, Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter()))),
                condition = arcanum.api.Condition.Compare(Value.CastX(of = Objects.Self), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())), affects = Affected.Objects(Objects.Self))
        }
        // chunk 13
        // "As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell
        // costs {2} less to cast for each card exiled this way." (CR 601.2f): the reduction counts the cost's cards.
        ADDITIONAL_EXILE_ANY_NUMBER_REDUCTION.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val per = ManaCost.parse(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(
                Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(CostObjects.Choose(filter, count = null), from = Zone.Hand, postBind = "exiled")))),
                Mechanic.CostChange(ObjectFilter.Is(Objects.Self), change = arcanum.api.CostChange.Less(per, Value.Count(Objects.Bound("exiled"))))), affects = Affected.Objects(Objects.Self))
        }
        // "As an additional cost to cast this spell, [cost][ or cost]." (CR 601.2f)
        ADDITIONAL_COST.matchEntire(line)?.let { m ->
            // dsk chunk 10: an "or" between nouns stays in its option ("sacrifice a creature or enchantment or pay {2}")
            val options = (if (BEHOLD_COST.matches(m.groupValues[1].replaceFirstChar { it.uppercase() })) listOf(m.groupValues[1])
                else m.groupValues[1].split(Regex(""" or (?=(?:sacrifice|pay|discard|exile|return|tap|reveal|choose) )"""))).map { option ->
                Cost(option.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten())
            }
            val cost = options.singleOrNull() ?: Cost(listOf(CostPart.OneOf(options)))
            return Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost)), affects = Affected.Objects(Objects.Self))
        }
        OTHERS_GET.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))))
        }
        DOESNT_UNTAP.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.Self))
        }
        // 'Equipped creature has "[ability]"' (CR 301.5)
        EQUIPPED_HAS_QUOTED.matchEntire(line)?.let { m ->
            val granted = Sentences(setOf("this creature"), false).line(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the
        // battlefield. If you don't, put it into its owner's graveyard." (CR 614.1c)
        WOULD_ENTER_DISCARD_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                instead = Instructions(steps = listOf(
                    Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(filter, count = Value.Fixed(1))))), bind = "discarded", payer = Players.You),
                    Step.If(arcanum.api.Condition.Did("discarded"),
                        then = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield)))),
                        otherwise = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Graveyard))))))))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 14: "This land enters tapped unless a player has 13 or less life." (checked as it enters, CR 614.1c)
        ENTERS_TAPPED_UNLESS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self), condition = arcanum.api.Condition.Not(condition))
        }
        ENTERS_TAPPED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self))
        }
        AS_ENTERS_PAY_LIFE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(unlessPays(Players.You, Cost(listOf(CostPart.PayLife(Value.Fixed(m.groupValues[2].toInt())))),
                    listOf(Step.Modify(Modification.EntersTapped))))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 26: 'Creature tokens you control have "[ability]"' (CR 113.10)
        HAVE_QUOTED.matchEntire(line)?.let { m ->
            // chunk 55: a subtype keeps its capital ("Elves you control")
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val granted = Sentences(setOf("this creature", "this permanent")).line(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 26: "This token can't be blocked."
        CANT_BE_BLOCKED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self))
        }
        // chunk 25
        if (line == "Play with your hand revealed.")
            return Ability.Static(listOf(Mechanic.MayLookAt(Objects.Affected, reveal = true)), affects = arcanum.api.Affected.Objects(Objects.Each(yourHand)))
        // chunk 25: "If you would draw a card, [instead]." (CR 614.1a)
        IF_YOU_WOULD_DRAW.matchEntire(line)?.let { m ->
            val instead = instructions(m.groupValues[1].replaceFirstChar { it.uppercase() } + ". " + m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = instead)), affects = Affected.Objects(Objects.Self))
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
            return Ability.Static(mechanics, affects = arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // chunk 20: "Equipped creature gets +1/+0 for each artifact you control." (CR 301.5)
        // dsk chunk 09: falls through when "for each …" isn't all ("… and has first strike")
        ATTACHED_GETS.matchEntire(line)?.takeIf { it.groupValues[3].isEmpty() || perEach(it.groupValues[3]) != null }?.let { m ->
            val per = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { perEach(it) ?: return null }
            fun amount(n: Int): Value = when {
                per == null || n == 0 -> Value.Fixed(n)
                n == 1 -> per
                else -> Value.Times(per, n)
            }
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[1].toInt()), amount(m.groupValues[2].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // chunk 21: "You may play lands from your graveyard." (CR 305.1)
        MAY_PLAY_FROM_GRAVEYARD.matchEntire(line)?.let { m ->
            val how = if (m.groupValues[1] == "play") arcanum.api.PlayKind.Play else arcanum.api.PlayKind.Cast
            // chunk 45: "You may cast this card from your graveyard." (on the card itself, CR 113.6f)
            if (isSelf(m.groupValues[2])) return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self))
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how, holder = PlayerFilter.You)), affects = arcanum.api.Affected.Objects(Objects.Each(
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
        // chunk 41: "enters with X +1/+1 counters": the X of the spell it was (CR 107.3m); chunk 42: "enters tapped with"
        ENTERS_WITH_COUNTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val n = if (m.groupValues[3] == "X") Value.CastX(of = Objects.Self) else Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null)
            val kind = Counters.kind(m.groupValues[4]) ?: return null
            val modify = listOfNotNull(Modification.EntersTapped.takeIf { m.groupValues[2].isNotEmpty() }, Modification.EntersWithCounters(kind, n))
            // dsk chunk 09: "… if you cast it from your hand", "… if a creature died this turn"
            val condition = m.groupValues[5].takeIf { it.isNotEmpty() }?.let { Conditions.condition(it, self) ?: return null }
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = modify)), affects = Affected.Objects(Objects.Self), condition = condition)
        }
        // chunk 40: "Green spells you control can't be countered."
        GROUP_CANT_BE_COUNTERED.matchEntire(line)?.let { m ->
            val spells = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return Ability.Static(listOf(Mechanic.CantBeCountered), affects = arcanum.api.Affected.Objects(Objects.Each(spells)))
        }
        // chunk 40: "If one or more tokens would be created under your control, those tokens plus that many 1/1 green Squirrel
        // creature tokens are created instead." (CR 614.1a)
        TOKENS_PLUS_THAT_MANY.matchEntire(line)?.let { m ->
            val token = Tokens.defined("a " + m.groupValues[1] + " token") ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.CreateToken(ObjectFilter.ControlledBy(PlayerFilter.You)),
                modify = listOf(Modification.AlsoCreate(token, Value.EventAmount)))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 40: "If a modular triggered ability would put one or more +1/+1 counters on a creature you control, that many
        // plus one +1/+1 counters are put on it instead." (CR 614.1a)
        KEYWORD_COUNTERS_PLUS.matchEntire(line)?.let { m ->
            val keyword = arcanum.api.KeywordKind.byId(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2])?.takeIf { it == Counters.kind(m.groupValues[5]) } ?: return null
            val on = Filters.noun(m.groupValues[3].removeArticle()) ?: return null
            val plus = Numbers.count(m.groupValues[4]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.CountersPut(on, kind, causedBy = ObjectFilter.AbilityOf(arcanum.api.AbilityKind.Triggered, keyword = keyword)),
                modify = listOf(Modification.AddAmount(Value.Fixed(plus))))), affects = Affected.Objects(Objects.Self))
        }
        // chunks 35, 41: "This creature can't block.", "This token can't block."
        CANT_BLOCK.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 42: "This creature gets +2/+2 as long as there are three or more land cards in your graveyard."
        SELF_GETS_AS_LONG_AS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val condition = Conditions.condition(m.groupValues[4], self) ?: return null
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                condition = condition, affects = Affected.Objects(Objects.Self))
        }
        // chunk 42: "This creature gets +1/+1 for each creature card in your graveyard."
        SELF_GETS_FOR_EACH.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val per = perEach(m.groupValues[4]) ?: return null
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(times(per, m.groupValues[2].toInt()), times(per, m.groupValues[3].toInt()))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 30: "This creature has deathtouch as long as you control at least three other snow permanents."
        HAS_KEYWORDS_AS_LONG_AS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null)),
                condition = Conditions.condition(m.groupValues[3], self) ?: return null, affects = Affected.Objects(Objects.Self))
        }
        // chunk 30: "This creature escapes with a +1/+1 counter on it." means "If this permanent escaped, it enters with
        // [those counters]" (CR 702.138c).
        ESCAPES_WITH.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))),
                condition = arcanum.api.Condition.Paid(arcanum.api.Keyword.Escape.Kind, times = 1, of = Objects.Self), affects = Affected.Objects(Objects.Self))
        }
        // chunk 33: "X can't be 0."
        X_CANT_BE.matchEntire(line)?.let { m -> return Ability.Static(listOf(Mechanic.XAtLeast(m.groupValues[1].toInt() + 1)), affects = Affected.Objects(Objects.Self)) }
        // chunk 32: "Golems you control have first strike."
        HAVE_KEYWORDS.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() })
            val keywords = keywords(m.groupValues[2])
            if (filter != null && keywords != null)
                return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 32: "As long as you've discarded a card this turn, you may pay {B/R} to cast this spell." (CR 118.9)
        MAY_PAY_TO_CAST.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse(m.groupValues[2]) ?: return null))),
                condition = Conditions.condition(m.groupValues[1], self) ?: return null, affects = Affected.Objects(Objects.Self))
        }
        // chunk 35: "Anax's power is equal to your devotion to red." (a characteristic-defining ability, CR 604.3)
        POWER_IS_EQUAL_TO.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val value = selfValue(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.SetPowerToughness(value, null)), characteristicDefining = true, affects = Affected.Objects(Objects.Self))
        }
        // chunk 36: "This creature attacks each combat if able." (a requirement, CR 508.1d)
        ATTACKS_EACH_COMBAT.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self))
        }
        // chunk 36: "This creature can't be blocked except by creatures with flying or reach." (CR 509.1b)
        CANT_BE_BLOCKED_EXCEPT_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 01: "This creature can't be blocked by creatures with power 2 or less." (CR 509.1b)
        Blb.CANT_BE_BLOCKED_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeBlockedBy(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 36: "During your turn, [static ability]."
        DURING_YOUR_TURN_STATIC.matchEntire(line)?.let { m ->
            val inner = static(m.groupValues[1].replaceFirstChar { it.uppercase() }) as? Ability.Static ?: return null
            // dsk chunk 13: "During your turn, as long as …, …": both
            val condition = inner.condition?.let { arcanum.api.Condition.And(listOf(arcanum.api.Condition.YourTurn) +
                ((it as? arcanum.api.Condition.And)?.all ?: listOf(it))) } ?: arcanum.api.Condition.YourTurn
            return inner.copy(condition = condition)
        }
        // chunk 36: "Nonland permanent cards in your graveyard have retrace."
        IN_YOUR_GRAVEYARD_HAVE.matchEntire(line)?.let { m ->
            val cards = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 45: "As long as [condition], [static ability]." / "[static ability] as long as [condition]."; "as long as this
        // card is in your graveyard" states the zone it works in (CR 113.6b)
        (AS_LONG_AS_FIRST.matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2].replaceFirstChar { c -> c.uppercase() } }
            ?: AS_LONG_AS_LAST.matchEntire(line)?.let { it.groupValues[2] to it.groupValues[1] + "." })?.let { (text, rest) ->
            val condition = conditionWithAnd(text)
            // dsk chunk 04: "As long as …, it gets +3/+3 and has hexproof.": "it" is this object
            // dsk chunk 13: "he's a 3/4 Ninja creature": this object
            val pronoun = Regex("""^(?:It|He|She)(?:'s| is) """).find(rest)
            val inner = condition?.let { static(when {
                pronoun != null -> "This permanent is " + rest.substring(pronoun.range.last + 1)
                rest.startsWith("It ") -> "This permanent " + rest.removePrefix("It ")
                else -> rest
            }) as? Ability.Static }
            if (inner != null && inner.condition == null) return inner.copy(condition = condition, functionsIn = zoneOf(condition))
        }
        // chunk 46: "Each player who has cast a nonartifact spell this turn can't cast additional nonartifact spells."
        CANT_CAST_ADDITIONAL.matchEntire(line)?.takeIf { it.groupValues[1] == it.groupValues[2] }?.let { m ->
            Filters.noun(m.groupValues[1])?.let { kind ->
                return Ability.Static(listOf(Mechanic.CantCast(kind)), affects = arcanum.api.Affected.Players(Players.Each(PlayerFilter.Any)),
                    condition = arcanum.api.Condition.HappenedThisTurn(EventPattern.Cast(kind, by = PlayerFilter.Is(Players.Affected)), atLeast = 1, measure = Measure.Events))
            }
        }
        // chunks 45-47: "This creature can't block and can't be blocked.", "Creatures you control have haste.", "Enchanted
        // creature gets +2/+2 and has flying and haste."
        SUBJECT_PREDICATES.matchEntire(line)?.let { m ->
            val subject = m.groupValues[1]
            val affects = when {
                isSelf(subject) -> arcanum.api.Affected.Objects(Objects.Self)
                subject == "Enchanted creature" -> arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self))
                // dsk chunk 05: "Equipped creature has menace."
                subject == "Equipped creature" -> arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self))
                // dsk chunk 07: subtypes keep their capitals ("Insects and Spiders you control")
                else -> (Filters.noun(subject.replaceFirstChar { it.lowercase() }) ?: Filters.noun(subject))?.let { arcanum.api.Affected.Objects(Objects.Each(it)) }
            }
            val mechanics = predicates(m.groupValues[2])
            if (affects != null && mechanics != null) return Ability.Static(mechanics, affects = affects)
        }
        // chunk 46: "Each other creature you control enters with a number of additional +1/+1 counters on it equal to
        // Arwen's toughness." (CR 614.1c)
        EACH_OTHER_ENTERS_WITH.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val chosen = if (m.groupValues[2].isNotEmpty()) listOf(ObjectFilter.Sub(arcanum.api.SubtypeRef.Remembered(CHOSEN_TYPE))) else emptyList()
            val kind = Counters.kind(m.groupValues[3].ifEmpty { m.groupValues[4] }) ?: return null
            val amount = if (m.groupValues[5].isEmpty()) Value.Fixed(1) else selfNumber(m.groupValues[5]) ?: return null
            val entering = Filters.all(*(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter) + chosen).toTypedArray())
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(entering, to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, amount)))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "As this creature enters, choose a creature type." (CR 614.12, 607.2d)
        AS_ENTERS_CHOOSE_TYPE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(arcanum.api.Choice.CreatureType, bind = CHOSEN_TYPE, remember = true, chooser = Players.You)))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "This creature is the chosen type in addition to its other types."
        IS_THE_CHOSEN_TYPE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Remembered(CHOSEN_TYPE)))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "Master of Etherium's power and toughness are each equal to the number of artifacts you control." (CR 604.3)
        PT_EACH_EQUAL_TO_NUMBER.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val count = Value.Count(Filters.objects(m.groupValues[2]) ?: return null)
            return Ability.Static(listOf(Mechanic.SetPowerToughness(count, count)), characteristicDefining = true, affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "Nonartifact spells cost {1} more to cast."
        SPELLS_COST.matchEntire(line)?.let { m ->
            val kind = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() } + " spell") ?: return null
            val spells = if (m.groupValues[2].isEmpty()) kind else Filters.all(kind, ObjectFilter.ControlledBy(PlayerFilter.You))
            val mana = ManaCost.parse(m.groupValues[3]) ?: return null
            val change = if (m.groupValues[4] == "more") arcanum.api.CostChange.More(mana, Value.Fixed(1)) else arcanum.api.CostChange.Less(mana, Value.Fixed(1))
            return Ability.Static(listOf(Mechanic.CostChange(spells, change = change)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "Prevent all combat damage that would be dealt to attacking artifact creatures you control." (CR 615)
        PREVENT_ALL_COMBAT_DAMAGE_TO.matchEntire(line)?.let { m ->
            val to = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(to = arcanum.api.RecipientFilter(objects = to), combat = true, source = ObjectFilter.Any))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 45: "You may look at the top card of your library any time."
        if (line == "You may look at the top card of your library any time.")
            return Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true))), affects = arcanum.api.Affected.Players(Players.You))
        // chunk 45: "You may play lands from the top of your library."
        MAY_PLAY_FROM_TOP.matchEntire(line)?.let { m ->
            val how = if (m.groupValues[1] == "play") arcanum.api.PlayKind.Play else arcanum.api.PlayKind.Cast
            val cards = Filters.noun(m.groupValues[2].removeSuffix(" spells")) ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, how, topOnly = true, holder = PlayerFilter.You)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(cards, ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 45: "Once during each of your turns, you may cast a permanent spell with mana value 2 or less from your
        // graveyard." A permanent spell is cast from a permanent card (CR 110.4a).
        ONCE_EACH_TURN_CAST_FROM_GRAVEYARD.matchEntire(line)?.let { m ->
            val cards = Filters.noun(m.groupValues[1].replace(" spell", " card")) ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, perTurn = 1, how = PlayKind.Cast, holder = PlayerFilter.You)), condition = arcanum.api.Condition.YourTurn,
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 45: "You may cast this card from your graveyard using its bestow ability."
        CAST_FROM_GRAVEYARD_USING.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val keyword = arcanum.api.KeywordKind.all.firstOrNull { it.text.lowercase() == m.groupValues[2] } ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, viaAlternative = keyword, how = PlayKind.Cast, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 45
        if (line == "You can't spend mana to cast this spell.") return Ability.Static(listOf(Mechanic.CantSpendMana), affects = Affected.Objects(Objects.Self))
        // chunk 52: "If one or more -1/-1 counters would be put on a creature you control, that many -1/-1 counters minus one
        // are put on it instead." (CR 614.1a)
        COUNTERS_MINUS_ONE.matchEntire(line)?.takeIf { it.groupValues[1] == it.groupValues[3] }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            val on = Filters.noun(m.groupValues[2].removeArticle()) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.CountersPut(on, kind), modify = listOf(Modification.AddAmount(Value.Fixed(-1))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 52: "As this creature enters, choose a nonland card name." / "…, you may choose a nonland permanent." A choice
        // made as it enters, kept on it (CR 614.12, 607.2d)
        AS_ENTERS_CHOOSE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val name = CARD_NAME.matchEntire(m.groupValues[3])
            val choose = when {
                // dsk chunk 14: "choose a color" (kept on the permanent, CR 607.2d)
                m.groupValues[3] == "a color" && m.groupValues[2].isEmpty() ->
                    Step.Choose(arcanum.api.Choice.Color(), bind = CHOSEN_COLOR, remember = true, chooser = Players.You)
                name != null && m.groupValues[2].isEmpty() -> Filters.noun(name.groupValues[1])?.let {
                    Step.Choose(arcanum.api.Choice.CardName(it), bind = CHOSEN_NAME, remember = true, chooser = Players.You) }
                name == null -> Filters.noun(m.groupValues[3].removeArticle())?.let {
                    Step.Choose(arcanum.api.Choice.Objects(it, Value.Fixed(if (m.groupValues[2].isEmpty()) 1 else 0), Value.Fixed(1)), bind = CHOSEN, remember = true, chooser = Players.You) }
                else -> null
            }
            if (choose != null) return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(choose))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 52: "Activated abilities of sources with the chosen name can't be activated."
        CANT_BE_ACTIVATED.matchEntire(line)?.let { m ->
            chosenObjects(m.groupValues[1])?.let { return Ability.Static(listOf(Mechanic.CantBeActivated(manaAbilities = true)), affects = arcanum.api.Affected.Objects(it)) }
        }
        // chunk 50: "You may cast this creature from your graveyard by paying {B}{B} and sacrificing two creatures rather than
        // paying its mana cost." An alternative cost (CR 118.9) with a permission to cast from the graveyard.
        MAY_CAST_SELF_FROM_GRAVEYARD_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val parts = m.groupValues[2].split(" and ").map { gerund ->
                val verb = GERUNDS.keys.firstOrNull { gerund.startsWith("$it ") } ?: return null
                costPart((GERUNDS.getValue(verb) + " " + gerund.removePrefix("$verb ")).trim()) ?: return null
            }.flatten()
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, cost = Cost(parts), how = PlayKind.Cast, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 56: "This creature can block only creatures with flying." (CR 509.1b)
        CAN_BLOCK_ONLY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CanBlockOnly(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 56: "Creatures you control can boast twice during each of your turns rather than once." (CR 702.142a)
        LABEL_TWICE_DURING_YOUR_TURNS.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val label = LABELS[m.groupValues[2]] ?: return null
            return Ability.Static(listOf(Mechanic.LabelUsesPerTurn(label, 2)), affects = arcanum.api.Affected.Objects(Objects.Each(filter)),
                condition = arcanum.api.Condition.YourTurn)
        }
        return null
    }

    /** chunk 52: what a choice made as this object entered refers to: "the chosen permanent" (the object kept), "sources
     * with the chosen name" (objects with the kept name, in every zone: cards in hand and graveyard have activated
     * abilities too). */
    private fun chosenObjects(phrase: String): Objects? = when (phrase) {
        "the chosen permanent" -> Objects.Remembered(CHOSEN)
        "sources with the chosen name" -> Objects.Each(Filters.all(ObjectFilter.RememberedName(CHOSEN_NAME),
            ObjectFilter.Or(EVERY_ZONE.map { ObjectFilter.InZone(it) })))
        else -> null
    }

    /** chunk 35: "your devotion to red" (CR 700.5). */
    private fun selfValue(text: String): Value? {
        DEVOTION_TO.matchEntire(text)?.let { m ->
            return Value.Devotion(m.groupValues[1].split(" and ").map { ((Filters.noun(it) as? ObjectFilter.OfColor)?.color as? ColorRef.Literal)?.color ?: return null }, players = Players.You)
        }
        return null
    }

    /** chunk 30: "first strike", "trample and haste": keywords named in a sentence. */
    private fun keywords(text: String): List<Ability>? =
        text.split(Regex(""",? and |, """)).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }

    /** chunk 41: what "for each [phrase]" counts: "+1/+1 counter on this Equipment" (its counters), or matching objects. */
    private fun perEach(phrase: String): Value? {
        COUNTER_ON.matchEntire(phrase)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            return Value.Of(arcanum.api.NumericProperty.Counters(Counters.kind(m.groupValues[1]) ?: return null), Objects.Self, arcanum.api.Combine.Single)
        }
        // chunk 52: "instant and sorcery card in your graveyard": each card that is either
        return (Filters.objects(phrase, self) ?: Filters.objects(phrase.replace(" and ", " or "), self))?.let { Value.Count(it) }
    }

    /** chunk 41: [n] times [per] (+1 per object is the count itself). */
    private fun times(per: Value, n: Int): Value = when (n) {
        0 -> Value.Fixed(0)
        1 -> per
        else -> Value.Times(per, n)
    }

    /** chunk 45: a condition, or several joined by "and" ("this card is in your graveyard and you control a Mountain"). */
    private fun conditionWithAnd(text: String): arcanum.api.Condition? {
        Conditions.condition(text, self)?.let { return it }
        rtrCondition(text)?.let { return it } // rtr
        val parts = text.split(" and ").takeIf { it.size > 1 } ?: return null
        return arcanum.api.Condition.And(parts.map { Conditions.condition(it, self) ?: return null })
    }

    /** chunk 45: the zone a condition on the object itself names ("this card is in your graveyard", "this card is
     * suspended"): an ability that states its zone works there (CR 113.6b). */
    private fun zoneOf(condition: arcanum.api.Condition?): List<Zone>? {
        val parts = (condition as? arcanum.api.Condition.And)?.all ?: listOfNotNull(condition)
        val filter = parts.firstNotNullOfOrNull { (it as? arcanum.api.Condition.ObjectMatches)?.takeIf { c -> c.objects == Objects.Self }?.filter } ?: return null
        val zone = ((filter as? ObjectFilter.And)?.all ?: listOf(filter)).firstNotNullOfOrNull { (it as? ObjectFilter.InZone)?.zone } ?: return null
        return listOf(zone)
    }

    /** chunks 45-47: "gets +2/+2", "has flying and haste", "attacks each combat if able", "can't block", "can't be
     * blocked", joined by commas and "and". */
    private fun predicates(text: String): List<Mechanic>? = text.split(PREDICATE_SPLIT).map { predicate ->
        GETS_PT.matchEntire(predicate)?.let { m ->
            val per = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Value.Count(Filters.objects(it) ?: return null) }
            fun amount(n: Int): Value = when {
                per == null || n == 0 -> Value.Fixed(n)
                n == 1 -> per
                else -> Value.Times(per, n)
            }
            Mechanic.ModifyPowerToughness(amount(m.groupValues[1].toInt()), amount(m.groupValues[2].toInt()))
        } ?: HAS_KEYWORDS.matchEntire(predicate)?.let { m ->
            Mechanic.GainAbilities(m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null })
        } ?: when (predicate) {
            "attacks each combat if able" -> Mechanic.MustAttack()
            "can't block" -> Mechanic.CantBlock(attackers = ObjectFilter.Any)
            "can't be blocked" -> Mechanic.CantBeBlocked
            "loses all abilities" -> Mechanic.LoseAbilities() // dsk chunk 09
            else -> return null
        }
    }

    /** chunk 46: "Arwen's toughness": a number of the object itself. */
    private fun selfNumber(text: String): Value? {
        val m = SELF_PROPERTY.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) } ?: return null
        val property = if (m.groupValues[2] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.Toughness
        return Value.Of(property, Objects.Self, arcanum.api.Combine.Single)
    }

    /** "[cost]: [effect]" (CR 602.1), with "This ability costs {1} less to activate for each …" as a last sentence. */
    private fun activated(line: String): Ability? {
        val colon = line.indexOf(": ").takeIf { it > 0 } ?: return null
        var cost = cost(line.substring(0, colon)) ?: return null
        var text = line.substring(colon + 2)
        // chunk 25: "the sacrificed land" reads the sacrificed object as it last existed (CR 608.2h)
        if (text.contains("the sacrificed ")) cost = Cost(cost.parts.map { if (it is CostPart.Sacrifice) it.copy(preBind = SACRIFICED) else it })
        var costChange: arcanum.api.CostChange? = null
        var condition: arcanum.api.Condition? = null
        var timing: arcanum.api.Timing = arcanum.api.Timing.Any
        // chunk 40: "Activate only once each turn."
        var perTurn: Int? = null
        // blb chunk 07: "Activate only if … and only once each turn.", "… and only once." (CR 602.5b)
        var once = false
        if (text.contains(" and only once each turn.")) { perTurn = 1; text = text.replace(" and only once each turn.", ".") }
        if (text.contains(" and only once.")) { once = true; text = text.replace(" and only once.", ".") }
        ACTIVATE_ONLY_ONCE_EACH_TURN.find(text)?.let { m ->
            perTurn = 1
            text = text.removeRange(m.range).trim()
        }
        // dsk chunk 09: "Activate only once." (for as long as its object exists)
        ACTIVATE_ONLY_ONCE_EVER.find(text)?.let { m ->
            once = true
            text = text.removeRange(m.range).trim()
        }
        // chunk 51: "Any player may activate this ability." (CR 602.2)
        var activatedBy: PlayerFilter = PlayerFilter.You
        ANY_PLAYER_MAY_ACTIVATE.find(text)?.let { m ->
            activatedBy = PlayerFilter.Any
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_AS_INSTANT.find(text)?.let { m ->
            timing = arcanum.api.Timing.Instant
            text = text.removeRange(m.range).trim()
        }
        // dsk chunk 07: "Activate only if … and only as a sorcery."
        ACTIVATE_ONLY_IF_AND_AS_SORCERY.find(text)?.let { m ->
            condition = Conditions.condition(m.groupValues[1], self) ?: return null
            timing = arcanum.api.Timing.Sorcery
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_IF.find(text)?.let { m ->
            condition = Conditions.condition(m.groupValues[1], self) ?: return null
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
        // blb gaps 2: "For each mana in this ability's activation cost, you may tap an untapped creature you control rather than
        // pay that mana." (another way to pay its mana)
        var payManaBy = emptyList<arcanum.api.ManaPaymentWay>()
        BlbGaps2.TAP_CREATURE_FOR_EACH_MANA.find(text)?.let { m ->
            payManaBy = listOf(arcanum.api.ManaPaymentWay.TapCreaturesForAnyMana)
            text = text.removeRange(m.range).replace("  ", " ").trim()
        }
        COSTS_LESS_FOR_EACH.find(text)?.let { m ->
            val per = ManaCost.parse(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            costChange = arcanum.api.CostChange.Less(per, Value.Count(count))
            text = text.removeRange(m.range).trim()
        }
        // chunk 40: an X in the activation cost ("{X}", "Sacrifice X Squirrels") is announced as it is activated (CR 107.3a, k)
        val xInCost = X_IN_COST.containsMatchIn(line.substring(0, colon))
        announcedX = xInCost
        val parsed = try { instructions(text) } finally { announcedX = false }
        val instructions = parsed?.let { i ->
            if (xInCost || cost.parts.any { (it as? CostPart.Mana)?.cost?.symbols?.any { s -> s is arcanum.api.ManaSymbol.Variable } == true })
                i.copy(announced = listOf("X")) else i
        } ?: return null
        // chunk 20: "the sacrificed creature's power" reads the sacrificed object as it last was (CR 608.2h)
        val named = if (!text.contains("the sacrificed ")) cost else cost.copy(parts = cost.parts.map {
            if (it is CostPart.Sacrifice && it.what is CostObjects.Choose) it.copy(preBind = SACRIFICED) else it
        })
        // chunk 45: "Activate only if this card is suspended" states the zone it works in (CR 113.6b)
        return Ability.Activated(named, instructions, arcanum.api.ActivationRules(timing = timing, perTurn = perTurn, costChange = costChange, condition = condition,
            activatedBy = activatedBy, once = once, payManaBy = payManaBy),
            functionsIn = zoneOf(condition))
    }

    /** "Whenever [event], [if condition,] [effect]." (CR 603.1) */
    private fun triggered(line: String): Ability? {
        val m = WHENEVER.matchEntire(line) ?: return null
        // blb chunk 05: a trigger whose own words hold a comma ("one or more noncreature, nonland permanents you control
        // enter"): split at the first comma after which the trigger reads
        var split = m.groupValues[2] to m.groupValues[3]
        var read = trigger(split.first)
        while (read == null) {
            val next = split.second.indexOf(", ").takeIf { it >= 0 } ?: return null
            split = (split.first + ", " + split.second.substring(0, next)) to split.second.substring(next + 2)
            read = trigger(split.first)
        }
        var (trigger, onlyIf) = read
        var rest = split.second
        // chunk 50: "Whenever you cast a spell, if it's the second creature spell you cast this turn, …": which creature spell
        // of the turn it is can't change after it is cast, so the condition (CR 603.4) is the trigger event itself.
        // blb gaps 2: "… if it's the first instant spell, the first sorcery spell, or the first Otter spell other than Alania
        // you've cast this turn, …": a spell that is any of them, each counted on its own (the ObjectFilter.NthSpellThisTurn
        // of each kind), part of the trigger event for the same reason
        BlbGaps2.NTH_SPELLS_IF.matchEntire(rest)?.let { n ->
            val cast = trigger.events.singleOrNull() as? EventPattern.Cast
            if (cast != null && cast.spell == ObjectFilter.Any && cast.by == PlayerFilter.You && cast.from == null) {
                val kinds = n.groupValues[1].split(Regex(""",? or |, """)).map { item ->
                    val k = BlbGaps2.NTH_SPELL_ITEM.matchEntire(item) ?: return null
                    val kind = Filters.noun(k.groupValues[2]) ?: return null
                    val other = k.groupValues[3].takeIf { it.isNotEmpty() }?.let { if (isSelf(it)) ObjectFilter.Not(ObjectFilter.Is(Objects.Self)) else return null }
                    ObjectFilter.NthSpellThisTurn(if (other == null) kind else Filters.all(kind, other), Numbers.ordinal(k.groupValues[1]) ?: return null)
                }
                trigger = trigger.copy(events = listOf(cast.copy(spell = kinds.singleOrNull() ?: ObjectFilter.Or(kinds))))
                rest = n.groupValues[2]
            }
        }
        NTH_SPELL_IF.matchEntire(rest)?.let { n ->
            val cast = trigger.events.singleOrNull() as? EventPattern.Cast
            if (cast != null && cast.spell == ObjectFilter.Any && cast.by == PlayerFilter.You && cast.from == null) {
                trigger = trigger.copy(events = listOf(cast.copy(spell = Filters.noun(n.groupValues[2]) ?: return null)),
                    nthThisTurn = Numbers.ordinal(n.groupValues[1]) ?: return null)
                rest = n.groupValues[3]
            }
        }
        // chunk 21: "This ability triggers only once each turn."
        var perTurn: Int? = null
        TRIGGERS_ONLY_ONCE.find(rest)?.let { once ->
            perTurn = 1
            rest = rest.removeRange(once.range).trim()
        }
        // dsk chunk 11: "Do this only once each turn." (CR 603.2h)
        var actionOnce = false
        DO_THIS_ONLY_ONCE_EACH_TURN.find(rest)?.let { m ->
            actionOnce = true
            rest = rest.removeRange(m.range).trim()
        }
        // dsk chunk 04: "This ability triggers only once." (for as long as its object exists)
        var once = false
        TRIGGERS_ONLY_ONCE_EVER.find(rest)?.let { m ->
            once = true
            rest = rest.removeRange(m.range).trim()
        }
        val conditional = INTERVENING_IF.matchEntire(rest)
        // dsk chunk 04: "if it had counters on it": the object of the event as it last existed (CR 603.10a)
        val interveningIf = conditional?.let {
            if (it.groupValues[1] == "it had counters on it") arcanum.api.Condition.ObjectMatches(that(trigger), ObjectFilter.HasCounter(null, atLeast = 1))
            else if (it.groupValues[1] == "it had no counters on it")
                arcanum.api.Condition.Not(arcanum.api.Condition.ObjectMatches(that(trigger), ObjectFilter.HasCounter(null, atLeast = 1)))
            // dsk chunk 08: "if it was a creature": the object of the event as it last existed (CR 603.10a)
            else if (IT_WAS_A.matches(it.groupValues[1])) arcanum.api.Condition.ObjectMatches(that(trigger),
                Filters.noun(IT_WAS_A.matchEntire(it.groupValues[1])!!.groupValues[1]) ?: return null)
            // dsk chunk 13: "if a land entered … this turn and you control a prime number of lands"
            else conditionWithAnd(it.groupValues[1]) ?: return null
        }
        // chunk 25: "it"/"them" in the effect: the event's object, or the objects the condition is about;
        // chunk 20: "that creature" is the event's object
        val about = (interveningIf as? arcanum.api.Condition.ObjectMatches)
        triggerIt = itOf(trigger.events)
        // blb chunk 04: with an event that has no object ("whenever you expend 4"), "it" is this permanent
        val event = trigger.events.firstOrNull { it is EventPattern.Expend }
            // blb gaps: "whenever this creature enters or attacks, put a counter on it": "it" is this creature, as when it attacks
            ?: trigger.events.takeIf { events -> events.size > 1 && events.all { selfEntersOrAttacks(it) } }?.firstOrNull { it is EventPattern.Attack }
            ?: trigger.events.first()
        // dsk chunk 13: "that much"/"that many" after an intervening if: the amount the condition counts
        conditionAmount = interveningIf?.let(::amountOf)
        var instructions = try {
            instructions(conditional?.groupValues?.get(2) ?: rest, event = event,
                them = about?.objects?.takeIf { it != Objects.Self }, that = that(trigger))
        } finally { conditionAmount = null }
        triggerIt = null
        // chunk 33: X in this permanent's enters trigger is the X chosen for the spell it was (CR 107.3m)
        if (trigger.events == listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)) && !rest.contains("where X is"))
            instructions = instructions?.withX(Value.CastX(of = Objects.Self))
        if (instructions == null) return null
        // chunk 25: "one or more [objects]" triggers once for the batch; "if this card is in your graveyard" states the
        // zone the ability works in (CR 113.6b)
        val oneOrMore = trigger.oneOrMore || m.groupValues[2].startsWith("one or more ")
        // chunk 15: "return this card from your graveyard" works only there (CR 113.6m)
        val zone = (about?.takeIf { it.objects == Objects.Self }?.filter as? ObjectFilter.InZone)?.zone
            ?: Zone.Graveyard.takeIf { rest.contains("this card from your graveyard") }
        return Ability.Triggered(trigger.copy(oneOrMore = oneOrMore), instructions, interveningIf = interveningIf,
            functionsIn = zone?.let { listOf(it) }, perTurn = perTurn, triggersOnlyIf = onlyIf, once = once, actionOncePerTurn = actionOnce)
    }

    /** "Whenever [event], choose one that hasn't been chosen this turn —" with its "• " modes (CR 700.2). */
    // chunk 21
    fun modalTrigger(header: String, modeLines: List<String>): Ability? {
        val m = WHENEVER.matchEntire(header) ?: return null
        val (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        val choose = CHOOSE_MODES.matchEntire(m.groupValues[3]) ?: return null
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it, that = that(trigger)) ?: return null, text = it) }
        if (options.isEmpty()) return null
        val unchosen = choose.groupValues[2].isNotEmpty()
        val thisTurn = choose.groupValues[3].isNotEmpty()
        return Ability.Triggered(trigger, Instructions(modes = arcanum.api.Modes(options, min = n, max = n,
            onlyUnchosen = unchosen && !thisTurn, onlyUnchosenThisTurn = thisTurn, chooser = Players.You)), triggersOnlyIf = onlyIf)
    }

    /** A trigger's events. "One or more …" triggers once per batch; "during your turn" is checked as it would trigger. */
    // chunk 21
    private fun trigger(text: String): Pair<arcanum.api.Trigger.On, arcanum.api.Condition?>? {
        val during = DURING_YOUR_TURN.matchEntire(text)
        val events = during?.groupValues?.get(1) ?: text
        val onlyIf = during?.let { arcanum.api.Condition.YourTurn }
        rtrEvents(events)?.let { return it to onlyIf } // rtr
        // dsk chunk 07: "this creature attacks while there are four or more …": checked only as it would trigger
        WHILE.matchEntire(events)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self)
            val (inner, innerIf) = condition?.let { trigger(m.groupValues[1]) } ?: (null to null)
            if (condition != null && inner != null && innerIf == null && onlyIf == null) return inner to condition
        }
        // dsk chunk 04: "the beginning of your second main phase": the first postcombat main phase of the turn (CR 505.1a, b)
        if (events == "the beginning of your second main phase")
            return arcanum.api.Trigger.On(listOf(EventPattern.StepBegins(arcanum.api.TurnStep.PostcombatMain, PlayerFilter.You)), nthThisTurn = 1) to onlyIf
        // dsk chunk 01: "one or more non-Toy creatures you control attack a player", "one or more creatures you control deal
        // combat damage to a player": one trigger per player (CR 603.2c by wording)
        ONE_OR_MORE_ATTACK_A_PLAYER.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(listOf(EventPattern.Attack(filter, target = RecipientFilter(players = PlayerFilter.Any))),
                oneOrMore = true, batchPerPlayer = true) to onlyIf
        }
        ONE_OR_MORE_DEAL_COMBAT_DAMAGE_TO_A_PLAYER.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(listOf(EventPattern.Damage(filter, RecipientFilter(players = PlayerFilter.Any), combat = true)),
                oneOrMore = true, batchPerPlayer = true) to onlyIf
        }
        ONE_OR_MORE_LEAVE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield), oneOrMore = true) to onlyIf
        }
        // chunk 40: "one or more other Elves you control enter"
        ONE_OR_MORE_ENTER.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(filter, to = Zone.Battlefield), oneOrMore = true) to onlyIf
        }
        // chunk 52: "you discard one or more cards for the first time each turn": only the first such batch
        FOR_THE_FIRST_TIME_EACH_TURN.matchEntire(events)?.let { m ->
            val (first, _) = trigger(m.groupValues[1]) ?: return null
            return first.copy(nthThisTurn = 1) to onlyIf
        }
        // chunk 50: "you draw your third card in a turn"
        NTH_CARD_DRAWN.matchEntire(events)?.let { m ->
            return arcanum.api.Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = Numbers.ordinal(m.groupValues[1]) ?: return null) to onlyIf
        }
        YOU_CREATE_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            // CR 111.2: the player who creates a token is its owner.
            return arcanum.api.Trigger.On(EventPattern.CreateToken(Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You))), oneOrMore = true) to onlyIf
        }
        // chunk 56: "you create a Blood token": once per token (CR 111.2: its creator owns it)
        YOU_CREATE_A.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.CreateToken(Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You)))) to onlyIf
        }
        // chunk 47: "Whenever you attack" triggers once when one or more creatures you control are declared as attackers
        // (CR 508.3d)
        if (events == "you attack")
            return arcanum.api.Trigger.On(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You)), oneOrMore = true) to onlyIf
        // chunks 36, 47: "you discard one or more cards", "… nonland cards": once for the batch
        // blb chunk 08: "this creature attacks while you control a token": checked only as it would trigger (CR 603.2)
        Blb.WHILE.matchEntire(events)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self) ?: return@let
            val (inner, _) = trigger(m.groupValues[1]) ?: return@let
            return inner to (if (onlyIf == null) condition else arcanum.api.Condition.And(listOf(onlyIf, condition)))
        }
        // blb chunk 11: "you put one or more +1/+1 counters on a creature you control": once for the batch, put by your spells
        // and abilities
        Blb.YOU_PUT_COUNTERS_ON.matchEntire(events)?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2].removeArticle()) ?: return null
            return arcanum.api.Trigger.On(EventPattern.CountersPut(filter, kind, causedBy = ObjectFilter.ControlledBy(PlayerFilter.You)), oneOrMore = true) to onlyIf
        }
        // blb chunk 07: "you attack with one or more Lizards": once per declaration of attackers with such creatures (CR 508.3d)
        Blb.YOU_ATTACK_WITH_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.Attack(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You))), oneOrMore = true) to onlyIf
        }
        // blb chunk 03: "you sacrifice one or more Foods": once for the batch (only a permanent's controller sacrifices it)
        Blb.YOU_SACRIFICE_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.Sacrifice(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You))), oneOrMore = true) to onlyIf
        }
        YOU_DISCARD_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val cards = if (m.groupValues[1] == "cards") ObjectFilter.Any else Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.Discard(cards, PlayerFilter.You), oneOrMore = true) to onlyIf
        }
        // chunk 47: "an opponent casts their first noncreature spell each turn"
        NTH_SPELL_EACH_TURN.matchEntire(events)?.let { m ->
            val by = if (m.groupValues[1] == "an opponent") PlayerFilter.Opponent else PlayerFilter.Any
            val n = Numbers.ordinal(m.groupValues[2]) ?: return null
            val kind = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            return arcanum.api.Trigger.On(listOf(EventPattern.Cast(kind, by)), nthThisTurn = n) to onlyIf
        }
        return Events.events(events, self)?.let { arcanum.api.Trigger.On(it) to onlyIf }
    }

    /** "That creature", "that card", "that permanent" in a triggered ability: the object of its (first) event. */
    // chunk 20
    private fun that(trigger: arcanum.api.Trigger.On): Objects = when (val event = trigger.events.first()) {
        is EventPattern.ZoneChange -> Objects.EventObject(if (event.to == Zone.Battlefield) arcanum.api.EventRole.After else arcanum.api.EventRole.Before)
        is EventPattern.Discard -> Objects.EventObject(arcanum.api.EventRole.After)
        is EventPattern.Unattach -> Objects.EventObject(arcanum.api.EventRole.From)
        else -> Objects.EventObject(role = EventRole.Object)
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
        // chunk 45: "Sacrifice an artifact, creature, or land": a piece starting in lower case continues the list before it
        val pieces = text.split(", ").fold(mutableListOf<String>()) { acc, piece ->
            if (acc.isNotEmpty() && piece.firstOrNull()?.isLowerCase() == true) acc[acc.size - 1] = acc.last() + ", " + piece else acc += piece
            acc
        }
        val parts = pieces.map { costPart(it) ?: return null }
        if (parts.isEmpty()) return null
        return Cost(parts.flatten())
    }

    private fun costPart(text: String): List<CostPart>? {
        rtrCostPart(text)?.let { return it } // rtr
        if (text == "{T}") return listOf(CostPart.TapSelf)
        // chunk 26: "[+1]", "[−2]", "[0]" (CR 606.4)
        LOYALTY.matchEntire(text)?.let { m ->
            val n = m.groupValues[2].toInt()
            return listOf(CostPart.Loyalty(Value.Fixed(if (m.groupValues[1].isEmpty() || m.groupValues[1] == "+") n else -n)))
        }
        ManaCost.parse(text)?.let { return listOf(CostPart.Mana(it)) }
        PAY_LIFE.matchEntire(text)?.let { return listOf(CostPart.PayLife(Value.Fixed(it.groupValues[1].toInt()))) }
        // dsk chunk 14: "sacrifice a permanent other than that creature or this Equipment" (that creature: the event's)
        SACRIFICE_OTHER_THAN_THAT_OR_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.Is(Objects.EventObject(EventRole.Object))),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))
            return listOf(CostPart.Sacrifice(CostObjects.Choose(filter, count = Value.Fixed(1))))
        }
        // dsk chunk 12: "choose a creature you control or reveal a creature card from your hand" is beholding (CR 701.4a)
        BEHOLD_COST.matchEntire(text)?.takeIf { it.groupValues[1] == it.groupValues[2] }?.let { m ->
            return listOf(CostPart.Action(arcanum.api.KeywordAction.Behold(Filters.noun(m.groupValues[1]) ?: return null), bind = "beheld"))
        }
        // dsk chunk 12: "Exile this card and two other cards named Utter Its Name from your graveyard"
        EXILE_SELF_AND_OTHERS_NAMED.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val others = Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Named(m.groupValues[3]))
            return listOf(CostPart.Exile(CostObjects.Self, from = Zone.Graveyard),
                CostPart.Exile(CostObjects.Choose(others, count = Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)), from = Zone.Graveyard))
        }
        // chunk 36: "pay {1}" (in "you may pay {1} and discard a card")
        PAY_MANA.matchEntire(text)?.let { m -> ManaCost.parse(m.groupValues[1])?.let { return listOf(CostPart.Mana(it)) } }
        // chunk 14: "Flashback—{R}{R}, Discard X cards.": X in a cost of the card's own keyword is the X chosen as the
        // spell is cast (CR 107.3a, Value.CastX). Keyword costs are read with only "this card" as the self name; an
        // activated ability's X stays unread here.
        if (text == DISCARD_X_CARDS && self == setOf("this card"))
            return listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = Value.CastX(of = Objects.Self))))
        // dsk chunk 10: "Discard any number of cards" ("that many" afterwards: the discarded cards)
        if (text == "Discard any number of cards")
            return listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = null), preBind = "discarded"))
        DISCARD_COST.matchEntire(text)?.let { m ->
            if (isSelf(m.groupValues[1])) return listOf(CostPart.Discard(CostObjects.Self))
            // dsk chunk 11: "the discarded card" afterwards
            if (discardedCard && m.groupValues[1] == "a card")
                return listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = Value.Fixed(1)), preBind = "discarded"))
            if (m.groupValues[1] == "your hand") return listOf(CostPart.Discard(CostObjects.These(Objects.Each(yourHand))))
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.Discard(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        // "Tap two untapped Elves you control": tapping as a cost can only tap untapped permanents you control
        // (CR 602.2b, 701.26a), so the canonical form names only the kind (as crew does).
        TAP_UNTAPPED.matchEntire(text)?.let { m ->
            // dsk chunk 12: "Tap another untapped creature you control"
            val another = m.groupValues[1] == "another"
            val count = if (another) 1 else Numbers.count(m.groupValues[1]) ?: return null
            val noun = Filters.noun(m.groupValues[2].removeSuffix(" you control")) ?: return null
            val filter = if (another) Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), noun) else noun
            return listOf(CostPart.TapObjects(CostObjects.Choose(filter, count = Value.Fixed(count))))
        }
        PUT_COUNTER_ON_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.PutCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), on = CostObjects.Self))
        }
        RETURN_TO_HAND_COST.matchEntire(text)?.let { m ->
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.ReturnToHand(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        EXILE_FROM_HAND_COST.matchEntire(text)?.let { m ->
            return listOf(CostPart.Exile(CostObjects.Choose(Filters.noun(m.groupValues[1]) ?: return null, count = Value.Fixed(1)), from = Zone.Hand))
        }
        // chunks 30, 41: "Exile eight other cards from your graveyard", "Exile two cards from your graveyard",
        // "Exile another creature card from your graveyard"
        EXILE_FROM_GRAVEYARD_COST.matchEntire(text)?.let { m ->
            val phrase = m.groupValues[1]
            val what = when {
                isSelf(phrase) -> CostObjects.Self
                phrase.contains(" other ") -> {
                    val (count, noun) = counted(phrase) ?: return null
                    val filter = Filters.noun(noun.removePrefix("other ")) ?: return null
                    CostObjects.Choose(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter), count = Value.Fixed(count))
                }
                else -> costChoice(phrase) ?: return null
            }
            return listOf(CostPart.Exile(what, from = Zone.Graveyard))
        }
        // chunk 31: "exile it" in a trigger on this object moving
        if (text == "Exile it") triggerIt?.let { (it, zone) -> return listOf(CostPart.Exile(CostObjects.These(it), from = zone ?: return null)) }
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            // chunk 40: counted ("two artifacts", "X Squirrels", "another creature")
            return listOf(CostPart.Sacrifice(if (isSelf(what)) CostObjects.Self else costChoice(what) ?: return null))
        }
        // chunk 21: "Remove two charge counters from this artifact"
        REMOVE_COUNTERS_FROM_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.RemoveCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), from = CostObjects.Self))
        }
        // dsk chunk 09: "Exile a creature you control" (from the battlefield; kept with this card when an ability refers to the cards
        // exiled with it, CR 607.2q)
        EXILE_YOU_CONTROL_COST.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1] + " you control") ?: return null
            return listOf(CostPart.Exile(CostObjects.Choose(filter, count = Value.Fixed(1)), from = Zone.Battlefield, remember = EXILED_WITH.takeIf { linkedExile }))
        }
        // chunk 45: "Collect evidence 6" (CR 701.59a)
        COLLECT_EVIDENCE.matchEntire(text)?.let { m ->
            return listOf(CostPart.Action(arcanum.api.KeywordAction.CollectEvidence(Value.Fixed(m.groupValues[1].toInt()))))
        }
        // blb chunk 07: "pay {W}{B} and 2 life"
        Blb.PAY_MANA_AND_LIFE.matchEntire(text)?.let { m ->
            return listOf(CostPart.Mana(ManaCost.parse(m.groupValues[1]) ?: return null), CostPart.PayLife(Value.Fixed(m.groupValues[2].toInt())))
        }
        // blb chunk 03: "Forage" (CR 701.61a)
        if (text == "Forage") return listOf(CostPart.Action(arcanum.api.KeywordAction.Forage))
        return null
    }

    /** chunk 40: the objects a cost chooses: "a Forest or Plains", "two artifacts", "X Squirrels" (X announced, CR 107.3a),
     * "another creature". */
    private fun costChoice(phrase: String): CostObjects.Choose? {
        if (phrase.startsWith("X ")) return CostObjects.Choose(Filters.noun(phrase.removePrefix("X ")) ?: return null, count = Value.Announced(name = "X"))
        val (count, noun) = counted(phrase) ?: return null
        return CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))
    }

    /** "a card" → 1 and "card"; "two cards" → 2 and "cards". */
    private fun counted(phrase: String): Pair<Int, String>? {
        val first = phrase.substringBefore(' ')
        val count = if (first == "a" || first == "an") 1 else Numbers.count(first) ?: return 1 to phrase
        return count to phrase.substringAfter(' ')
    }

    /** The effect sentences of a spell or ability, in order. */
    fun instructions(printed: String, seedTargets: List<TargetSlot> = emptyList(), event: EventPattern? = null, them: Objects? = null,
                     that: Objects? = null, itZone: Zone? = null): Instructions? {
        // chunk 30: "…, where X is [a number]" defines X for the whole ability (CR 107.3c)
        // An X this reader can't define stays in the text for the sentence rules that read it themselves.
        // blb gaps 2: a comma kept inside a quoted ability before "where X is" ('… you control," where X is …')
        val text = printed.replace(",\" where X is ", ".\", where X is ")
        val defined = WHERE_X.find(text)?.let { Values.value(it.groupValues[1], exiledCard) }
        // blb gaps 2: "where X is Helga's power" (this object's), when no sentence rule reads that X itself
        if (defined == null) WHERE_X.find(text)?.let { selfNumber(it.groupValues[1]) }?.let { own ->
            return read(text, null, seedTargets, event, them, that, itZone) ?: read(text, own, seedTargets, event, them, that, itZone)
        }
        return read(text, defined, seedTargets, event, them, that, itZone)
    }

    /** [instructions] with [x] defined by the text's "where X is" (null: not defined here). */
    private fun read(text: String, x: Value?, seedTargets: List<TargetSlot>, event: EventPattern?, them: Objects?, that: Objects?, itZone: Zone?): Instructions? {
        val whereX = WHERE_X.find(text)?.takeIf { x != null }
        val context = Context()
        context.targets += seedTargets
        // chunk 25
        context.event = event
        context.it = event?.let { if (it is EventPattern.ZoneChange) Objects.EventObject(arcanum.api.EventRole.After) else Objects.EventObject(role = EventRole.Object) }
        // chunk 30: a step has no object, so "it" is this object ("return it to your hand")
        if (event is EventPattern.StepBegins || event is EventPattern.Expend) context.it = Objects.Self
        // rtr chunk 03: at the beginning of a step of an Aura's enchanted permanent's controller, "it" is that permanent
        if (event is EventPattern.StepBegins && event.whose == PlayerFilter.Is(Players.ControllerOf(Objects.AttachedTo(Objects.Self)))) context.it = Objects.AttachedTo(Objects.Self)
        // blb chunk 05: "Whenever a creature you control deals combat damage to a player, … exile it": the creature dealing it
        if (event is EventPattern.Damage && event.source != ObjectFilter.Is(Objects.Self) && event.to.objects == null) context.it = Objects.EventObject(EventRole.Source)
        // chunk 36: "Whenever this creature attacks, put a +1/+1 counter on it": it is this creature
        if (event is EventPattern.Attack && event.attacker == ObjectFilter.Is(Objects.Self)) context.it = Objects.Self
        context.them = them
        // chunk 20
        context.that = that
        // blb gaps: the zone "it" is in, when the caller knows it ("you may exile it" about a permanent)
        context.itZone = itZone
        val steps = mutableListOf<Step>()
        // dsk chunk 12: an X that "where X is" defines is read as that value, not as the X its permanent was cast with
        val outerX = announcedX
        if (whereX != null) announcedX = true
        try {
            for (sentence in sentences(if (whereX == null) text else text.removeRange(whereX.range))) {
                // blb chunk 02: where the sentence's targets and steps begin ("If the gift was promised, instead …" replaces them)
                val targetsFrom = context.targets.size
                val stepsFrom = steps.size
                val modified = modify(sentence, steps, context)
                if (modified != null) { steps.clear(); steps += modified; continue }
                val parsed = effect(sentence, context)
                if (parsed == null) {
                    // blb chunk 02: "that card", "this way" after a move or destruction: name what it moved (CR 400.7)
                    val rebound = afterMove(sentence, steps, context) ?: return null
                    steps.clear(); steps += rebound
                } else steps += renameRepeatedTargets(parsed, context, targetsFrom) // rtr chunk 08
                context.previous = targetsFrom to stepsFrom
            }
        } finally { announcedX = outerX }
        val result = Instructions(targets = context.targets, steps = steps, replacements = context.replacements)
        return if (x == null) result else result.withX(x)
    }

    /** Sentences that change the step before them ("If this spell was kicked, it deals 4 damage instead.", "It can't be
     * regenerated."). */
    private fun modify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        // dsk gaps: "It's an enchantment." after returning it to the battlefield: it isn't a creature as it enters (layer 4,
        // CR 611.2e), so its enters triggers and state-based actions see an enchantment
        if (sentence == "It's an enchantment.") {
            val move = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.takeIf { it.to.zone == Zone.Battlefield } ?: return null
            return steps.dropLast(1) + Step.Do(move.copy(to = move.to.copy(entersAs = move.to.entersAs + Mechanic.SetTypes(remove = listOf(CardType.Creature)))))
        }
        // dsk gaps: "If two cards that share a card type were milled this way, draw a card and repeat this process." after
        // milling: the milling and the draw again while the condition holds (CR 608.2c)
        REPEAT_IF_MILLED_SHARE.matchEntire(sentence)?.let { m ->
            val mill = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Mill)?.takeIf { it.postBind == null } ?: return null
            val quality = when (m.groupValues[1]) {
                "card type" -> arcanum.api.SharedQuality.CardType
                "color" -> arcanum.api.SharedQuality.Color
                else -> return null
            }
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            val milled = Objects.Bound("milled")
            val shared = arcanum.api.Condition.ObjectMatches(Objects.Where(milled, ObjectFilter.Shares(quality, milled)), ObjectFilter.Any)
            return steps.dropLast(1) + Step.RepeatWhile(listOf(Step.Do(mill.copy(postBind = "milled")), Step.If(shared, then = then)), shared)
        }
        // dsk gaps: "Copy them." after exiling cards: copies of those cards (CR 707.12), named for the casting that follows
        if (sentence == "Copy them." || sentence == "Copy it.") {
            val move = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.takeIf { it.to.zone == Zone.Exile } ?: return null
            val bind = move.postBind ?: "exiled"
            return steps.dropLast(1) + Step.Do(move.copy(postBind = bind)) + Step.Do(Effect.CopyCard(Objects.Bound(bind), owner = Players.You, bind = COPIES))
        }
        // dsk gaps: "You may cast any number of the copies." / "You may cast the copy." after copying cards: each copy may be
        // cast (CR 707.12a), paying its costs
        CAST_THE_COPIES.matchEntire(sentence)?.let {
            val copy = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.CopyCard) ?: return null
            val cast = { objects: Objects -> Step.May(listOf(Step.Do(Effect.Cast(objects, PlayKind.Cast, caster = Players.You))), player = Players.You) }
            return steps + Step.ForEach(arcanum.api.Each.Objects(Objects.Bound(copy.bind)), bind = "copy", steps = listOf(cast(Objects.Bound("copy"))))
        }
        blbModify(sentence, steps, context)?.let { return it }
        rtrModify(sentence, steps, context)?.let { return it } // rtr
        // blb chunk 02: "If the gift was promised, instead [effect]." replaces the sentence before; each version's targets
        // are chosen only when it is the one that happens (CR 702.174m)
        Blb.GIFT_INSTEAD.matchEntire(sentence)?.let { m ->
            val (targetsFrom, stepsFrom) = context.previous ?: return null
            val promised = Blb.promised
            val before = context.targets.size
            context.promisedNames = true
            val then = try { effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context) } finally { context.promisedNames = false } ?: return null
            for (i in targetsFrom until before) context.targets[i] = context.targets[i].copy(onlyIf = arcanum.api.Condition.Not(promised))
            for (i in before until context.targets.size) context.targets[i] = context.targets[i].copy(onlyIf = promised)
            return steps.take(stepsFrom) + Step.If(promised, then = then, otherwise = steps.drop(stepsFrom))
        }
        // dsk chunk 13: "Shards you control become copies of it until the next end step." after exiling a target (layer 1; the
        // exiled card's copiable values, CR 707.2)
        BECOME_COPIES_OF_IT_UNTIL_NEXT_END_STEP.matchEntire(sentence)?.let { m ->
            val move = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.takeIf { it.to.zone == Zone.Exile && it.postBind == null } ?: return null
            val affected = Filters.noun(m.groupValues[1]) ?: return null
            context.it = Objects.Bound("exiled")
            return steps.dropLast(1) + Step.Do(move.copy(postBind = "exiled")) + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("exiled"))),
                arcanum.api.Affected.Objects(Objects.Each(affected)), arcanum.api.Duration.UntilStep(arcanum.api.TurnStep.End, PlayerFilter.Any)))
        }
        // dsk chunk 11: a sentence after "Until end of turn, whenever …, exile the top card of your library." continues that
        // delayed trigger ("You may play it until the end of your next turn.")
        if (sentence.startsWith("You may play it ")) {
            val delayed = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateDelayedTrigger)?.takeIf { it.repeats } ?: return null
            val inner = Context().also { it.it = Objects.Bound("exiled") }
            val more = effect(sentence, inner) ?: return null
            return steps.dropLast(1) + Step.Do(delayed.copy(instructions = delayed.instructions.copy(steps = delayed.instructions.steps + more)))
        }
        // dsk chunk 10: "If a creature card is put into a graveyard this way, return it to the battlefield under your control." after
        // destroying (the destroyed card in a graveyard, CR 400.7)
        IF_PUT_INTO_GRAVEYARD_THIS_WAY_RETURN.matchEntire(sentence)?.let { m ->
            val destroy = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Destroy)?.takeIf { it.postBind == null } ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard))
            return steps.dropLast(1) + Step.Do(destroy.copy(postBind = "destroyed")) + Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound("destroyed"), filter),
                then = listOf(Step.Do(Effect.Move(Objects.Bound("destroyed"), Destination(Zone.Battlefield, controller = Players.You), postBind = "returned"))))
        }
        // dsk chunk 10: "Sacrifice it at the beginning of your next end step." (a delayed trigger, CR 603.7) inside that "if"
        if (sentence == "Sacrifice it at the beginning of your next end step.") {
            val iff = (steps.lastOrNull() as? Step.If)?.takeIf { s -> ((s.then.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.postBind == "returned" } ?: return null
            val delayed = Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("returned")))))))
            return steps.dropLast(1) + iff.copy(then = iff.then + delayed)
        }
        // dsk chunk 10: "If you discarded a card this way, draw a card." after each player discards
        if (sentence == "If you discarded a card this way, draw a card.") {
            val discard = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Discard)?.takeIf { it.postBind == null } ?: return null
            return steps.dropLast(1) + Step.Do(discard.copy(postBind = "discarded")) + Step.If(
                arcanum.api.Condition.ObjectMatches(Objects.Where(Objects.Bound("discarded"), ObjectFilter.OwnedBy(PlayerFilter.You)), ObjectFilter.Any),
                then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))))
        }
        // dsk chunk 07: "Then this creature deals damage equal to its power to each opponent who didn't sacrifice a permanent or discard
        // a card this way." inside the "each opponent may" loop, after every opponent has decided
        DAMAGE_TO_EACH_WHO_DIDNT.matchEntire(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() })?.takeIf { isSelf(it.groupValues[1]) }?.let {
            val loop = steps.lastOrNull() as? Step.ForEach ?: return null
            if (loop.bind != "opponent" || (loop.steps.lastOrNull() as? Step.Pay)?.bind != "paid") return null
            return steps.dropLast(1) + loop.copy(steps = loop.steps + Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("paid")), then = listOf(
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.Bound("opponent")),
                    Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single), source = Objects.Self)))))
        }
        // chunk 26: "Untap those creatures." after an effect on them; blb chunk 08: "Untap them."
        if (sentence == "Untap those creatures." || sentence == "Untap them.") {
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
        // dsk chunk 13: "Sacrifice it …" after creating one token
        if (sentence == "Sacrifice them at the beginning of the next end step." || sentence == "Sacrifice it at the beginning of the next end step." &&
            ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateToken)?.count == Value.Fixed(1)) {
            val bound = bindTokens(steps) ?: return null
            // CR 603.7: a delayed trigger, triggering once at the next end step
            return bound + Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound(TOKENS)))))))
        }
        // chunk 36: "You may put a land card from among them into your hand." after milling: zero or one of the milled cards
        MAY_PUT_FROM_AMONG_THEM_INTO_HAND.matchEntire(sentence)?.let { m ->
            val mill = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Mill)?.takeIf { it.postBind == null } ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            // dsk chunk 13: "any number of"
            val max = if (m.groupValues[1] == "any number of") null else Value.Fixed(1)
            return steps.dropLast(1) + Step.Do(mill.copy(postBind = "milled")) +
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = max, from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You) +
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
        // chunk 33: "If this creature was kicked, copy that spell twice instead." after a delayed copy trigger
        COPY_TWICE_IF_KICKED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            val delayed = (steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateDelayedTrigger ?: return null
            val twice = delayed.copy(instructions = delayed.instructions.copy(steps = listOf(Step.Repeat(Value.Fixed(2), delayed.instructions.steps))))
            return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind, times = 1, of = Objects.Self),
                then = listOf(Step.Do(twice)), otherwise = listOf(Step.Do(delayed)))
        }
        // chunk 33: "You may choose new targets for the copies." (CR 707.10c)
        if (sentence == "You may choose new targets for the copies." && steps.isNotEmpty())
            return steps.map { withNewTargets(it) ?: return null }
        val previous = (steps.lastOrNull() as? Step.Do)?.effect
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
        // chunk 52: "Put that card into your hand and the rest on the bottom of your library in a random order." after
        // revealing until a card is found, inside the same condition
        PUT_FOUND_IN_HAND_REST_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            val moves = listOf(Step.Do(Effect.Move(Objects.Bound(FOUND), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Bound(REST), Destination(Zone.Library, libraryPosition = -1, randomOrder = m.groupValues[1] == "a random"))))
            fun revealsUntil(step: Step?) = ((step as? Step.Do)?.effect as? Effect.RevealUntil)?.foundBind == FOUND
            val previous = steps.lastOrNull()
            return when {
                revealsUntil(previous) -> steps + moves
                previous is Step.If && previous.otherwise.isEmpty() && revealsUntil(previous.then.lastOrNull()) ->
                    steps.dropLast(1) + previous.copy(then = previous.then + moves)
                else -> null
            }
        }
        val last = steps.lastOrNull() as? Step.Do ?: return null
        val effect = last.effect
        // chunk 56: "Add {R}. Until end of turn, you don't lose this mana as steps and phases end." (CR 106.4)
        if (sentence == "Until end of turn, you don't lose this mana as steps and phases end." && effect is Effect.AddMana) {
            return steps.dropLast(1) + Step.Do(effect.copy(mana = effect.mana.copy(keptUntil = arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56: "You may cast target … card from your graveyard this turn. If that spell would be put into your
        // graveyard, exile it instead.": the replacement goes with the permission, to the spell cast with it
        if (sentence == "If that spell would be put into your graveyard, exile it instead." && effect is Effect.ApplyMechanics) {
            val may = effect.mechanics.singleOrNull() as? Mechanic.MayPlay ?: return null
            return steps.dropLast(1) + Step.Do(effect.copy(mechanics = listOf(may.copy(then = Destination(Zone.Exile), thenInsteadOf = Zone.Graveyard))))
        }
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
                count = Value.CountDistinct(Objects.Bound(DISCARDED), arcanum.api.SharedQuality.CardType), controller = Players.You))
        }
        // "Draw a card for each card returned to your hand this way.": names the returned objects.
        if (effect is Effect.Move && effect.to.zone == Zone.Hand && effect.postBind == null) DRAW_FOR_EACH_RETURNED_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
            return steps.dropLast(1) + Step.Do(effect.copy(postBind = RETURNED)) +
                Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Where(Objects.Bound(RETURNED), filter))))
        }
        if (effect is Effect.DealDamage) {
            KICKED_DEALS_INSTEAD.matchEntire(sentence)?.let { m ->
                return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind, times = 1, of = Objects.Self),
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
            return steps.dropLast(1) + Step.If(arcanum.api.Condition.CastFrom(Zone.Graveyard, of = Objects.Self),
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
        // blb chunk 02: where the last sentence's targets and steps began; new target names get "promised " when taken
        var previous: Pair<Int, Int>? = null
        var promisedNames = false
        /** "this way": what the last effect moved; the target slots by the phrase that named them ("the creature you control"). */
        var thisWay: Objects? = null
        val slotPhrases = mutableMapOf<String, String>()
        // chunk 25: the triggering event; what "it" and "them" refer to; the amount "that much" refers to
        var event: EventPattern? = null
        var it: Objects? = null
        var them: Objects? = null
        var thatMuch: Value? = null
        // chunk 20: the event's object in a trigger ("that creature")
        var that: Objects? = null
        // dsk chunk 01: the creature manifested ("that creature" after "manifest dread")
        var manifested: Objects? = null

        /** dsk chunk 07: the target slot of "the creature you control" (the one whose filter that noun names). */
        fun slotFor(noun: String): Objects? = Filters.noun(noun)?.let { filter ->
            targets.firstOrNull { (it.what as? TargetKind.Objects)?.filter == filter }?.let { Objects.Target(it.name) }
        }
        val replacements = mutableListOf<Mechanic.Replacement>()
        val targets = mutableListOf<TargetSlot>()
        /** "any target" (CR 115.4): creature, planeswalker or battle, or player. */
        fun anyTarget() {
            targets += TargetSlot("any", arcanum.api.TargetKind.Either(ObjectFilter.Or(listOf(
                ObjectFilter.Type(arcanum.api.CardType.Creature), ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                ObjectFilter.Type(arcanum.api.CardType.Battle))), PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
        }

        /** "target player" */
        fun targetPlayer(): Players {
            if (targets.none { it.name == "player" }) targets += TargetSlot("player", arcanum.api.TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
            return Players.Target("player").also { lastPlayer = it } // blb chunk 01: "they"
        }

        fun target(noun: String, filter: ObjectFilter): Objects {
            val name = name(noun)
            targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(1), max = Value.Fixed(1))
            return Objects.Target(name)
        }

        /** blb chunk 02: a slot name not taken yet: [noun], "promised [noun]" for the promised version of a gift spell. */
        fun name(noun: String): String = if (promisedNames && targets.any { it.name == noun }) "promised $noun" else noun

        // chunk 13
        /** The player whose hand was revealed ("that player"), the revealed hand ("from it"), the names of the cards
         * chosen ("that card", "the chosen cards") and of the chosen card name ("that name"). */
        var player: Players? = null
        var revealed: Objects? = null
        val chosen = mutableListOf<String>()
        var cardName: String? = null

        /** "target opponent" */
        fun targetOpponent(): Players {
            if (targets.none { it.name == "opponent" }) targets += TargetSlot("opponent", arcanum.api.TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))
            return Players.Target("opponent").also { lastPlayer = it }
        }

        // chunk 14
        /** A copy was made ("You may choose new targets for the copies"); who may pay ("If the player does"). */
        var copied = false
        var payer: Players? = null

        // chunk 31: the zone "that card" is in; the cards looked at or revealed and the one picked from among them.
        var itZone: Zone? = null
        var seen: String? = null
        var picked: String? = null
        /** chunk 33: the player whose hand "it" is ("look at target opponent's hand. You may exile a card from it"). */
        var lastPlayer: Players? = null

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

    /** Duskmourn (dsk) sentence rules, tried at the start of [effect]: null when none reads [sentence]. */
    private fun setWordings(sentence: String, context: Context): List<Step>? {
        // dsk chunk 01: "Tap up to one target creature." ("it" afterwards is that creature)
        TAP_TARGET.matchEntire(sentence)?.takeIf { Filters.noun(it.groupValues[3]) != null }?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun)!!
            val name = Filters.slotName(noun)
            val max = if (m.groupValues[1].isEmpty()) 1 else Numbers.count(m.groupValues[1]) ?: return null
            context.targets += TargetSlot(name, TargetKind.Objects(if (m.groupValues[2].isEmpty()) filter else Filters.all(context.another(), filter)),
                min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0), max = Value.Fixed(max))
            context.it = Objects.Target(name)
            return listOf(Step.Do(Effect.Tap(Objects.Target(name))))
        }
        // dsk chunk 01: "Put a +1/+1 counter on each of up to two target creatures."
        PUT_COUNTERS_ON_UP_TO.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[4]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0),
                max = Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null))
            return listOf(Step.Do(Effect.PutCounters(Objects.Target(name), Counters.kind(m.groupValues[2]) ?: return null,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // dsk chunk 01: "Return up to one target creature to its owner's hand."
        RETURN_UP_TO_TARGET_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0),
                max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // dsk chunk 01: "You win the game if there are eight or more …"
        YOU_WIN_IF.matchEntire(sentence)?.let { m ->
            return listOf(Step.If(Conditions.condition(m.groupValues[1], self) ?: return null, then = listOf(Step.Do(Effect.WinGame(Players.You)))))
        }
        // dsk chunk 01: "Manifest dread." ("that creature" afterwards is the manifested permanent, CR 701.62a)
        if (sentence == "Manifest dread.") {
            context.manifested = Objects.Bound(MANIFESTED)
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.ManifestDread(Players.You), bind = MANIFESTED)))
        }
        // dsk chunk 01: "Put a +1/+1 counter on that creature." after manifesting dread
        PUT_COUNTERS.matchEntire(sentence)?.takeIf { it.groupValues[3] == "that creature" && context.manifested != null }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(context.manifested!!, kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // dsk chunk 02: "It gets +1/+0 and gains deathtouch until end of turn." ("it": the event's object, or this object)
        GETS_AND_GAINS_UNTIL_EOT.matchEntire(sentence)?.takeIf { m ->
            // dsk chunk 09: a target subject only when its noun reads
            !m.groupValues[1].contains("arget ") || Filters.noun(m.groupValues[1].substringAfter("arget ")) != null }?.let { m ->
            val objects = when {
                isSelf(m.groupValues[1]) -> Objects.Self
                m.groupValues[1] == "It" -> context.it
                m.groupValues[1] == "That creature" -> context.that
                // dsk chunk 07: "Target creature gets +1/+0 and gains first strike until end of turn."
                m.groupValues[1].startsWith("Target ") -> m.groupValues[1].removePrefix("Target ").let { noun ->
                    Filters.noun(noun)?.let { context.target(Filters.slotName(noun), it) } }
                // dsk chunk 09: "another target creature you control gets +1/+0 and gains indestructible"
                m.groupValues[1].startsWith("Another target ") -> m.groupValues[1].removePrefix("Another target ").let { noun ->
                    Filters.noun(noun)?.let { context.target(Filters.slotName(noun), Filters.all(context.another(), it)) } }
                else -> null
            }
            val keywords = m.groupValues[5].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) }
            // dsk chunk 09: "loses flying", "until your next turn"
            val change = if (m.groupValues[4] == "loses") keywords.singleOrNull()?.let { Mechanic.LoseAbilities(it.kind) } else Mechanic.GainAbilities(keywords.filterNotNull())
            val duration = if (m.groupValues[6] == "your next turn") arcanum.api.Duration.UntilYourNextTurn else arcanum.api.Duration.EndOfTurn
            if (objects != null && keywords.all { it != null } && change != null) return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null),
                    change), arcanum.api.Affected.Objects(objects), duration)))
        }
        // dsk chunk 02: "[This Room] deals damage equal to the number of cards in your hand to target creature an opponent
        // controls."
        DEALS_DAMAGE_EQUAL_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            Values.value(m.groupValues[2], exiledCard)?.let { amount ->
                val what = m.groupValues[3]
                val to = when {
                    what == "any target" -> { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                    what.startsWith("target ") -> what.removePrefix("target ").let { noun ->
                        arcanum.api.Recipients(objects = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)) }
                    else -> arcanum.api.Recipients(players = Filters.players(what) ?: return null)
                }
                return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
            }
        }
        // dsk chunk 02: "You may play it this turn." / "You may play them until the end of your next turn." (the exiled cards)
        YOU_MAY_PLAY_EXILED.matchEntire(sentence)?.let { m ->
            val exiled = (if (m.groupValues[1] == "them") context.them else context.it) as? Objects.Bound ?: return null
            val duration = if (m.groupValues[2] == "this turn") arcanum.api.Duration.EndOfTurn else arcanum.api.Duration.UntilEndOfYourNextTurn
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(exiled), duration)))
        }
        // dsk chunk 02: "Mill four cards, then return up to two permanent cards from among them to your hand."
        MILL_THEN_RETURN_FROM_AMONG_THEM.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val most = Numbers.count(m.groupValues[2]) ?: return null
            val filter = Filters.noun(m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(n), postBind = "milled")),
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(most), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // dsk chunk 02: "You may cast spells from your graveyard this turn, and if a card would be put into your graveyard from
        // anywhere this turn, exile it instead." A permission and a replacement for the turn (CR 611.2c, 614.1a).
        if (sentence == "You may cast spells from your graveyard this turn, and if a card would be put into your graveyard from anywhere this turn, exile it instead.") {
            return listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, arcanum.api.PlayKind.Cast, holder = PlayerFilter.You)),
                    arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    arcanum.api.Duration.EndOfTurn)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                    modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 02: "Add {R}{R}. Spend this mana only to cast Room spells and unlock doors." (CR 106.6)
        ADD_MANA_SPEND_ONLY_TO.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = spendOnlyTo(m.groupValues[2]) ?: return null), players = Players.You)))
        }
        // dsk chunk 03: "Unlock a locked door of up to one target Room you control." / "… of a Room you control." (CR 709.5f)
        UNLOCK_A_LOCKED_DOOR.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun) ?: return null
            if (m.groupValues[1].isEmpty() && m.groupValues[2] != "target ") {
                return listOf(
                    Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, HAS_A_LOCKED_DOOR), Value.Fixed(1), Value.Fixed(1)), bind = "room", chooser = Players.You),
                    Step.Do(Effect.UnlockDoor(Objects.Bound("room"), chooser = Players.You)))
            }
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0),
                max = Value.Fixed(m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Numbers.count(it) ?: return null } ?: 1))
            return listOf(Step.Do(Effect.UnlockDoor(Objects.Target(name), chooser = Players.You)))
        }
        // dsk chunk 03: "Lock or unlock a door of target Room you control." (CR 709.5f, g; one of the two, chosen as it resolves)
        LOCK_OR_UNLOCK.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("lock", "unlock")), bind = "lockOrUnlock", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("lockOrUnlock", "lock"),
                    then = listOf(Step.Do(Effect.LockDoor(target, chooser = Players.You))),
                    otherwise = listOf(Step.Do(Effect.UnlockDoor(target, chooser = Players.You)))))
        }
        // dsk chunk 03: "Return an enchantment card from your graveyard to your hand or unlock a locked door of a Room you
        // control.": one of the two, chosen as it resolves
        RETURN_OR_UNLOCK.matchEntire(sentence)?.let { m ->
            val card = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val unlock = effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("return", "unlock")), bind = "returnOrUnlock", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("returnOrUnlock", "return"),
                    then = listOf(Step.Choose(arcanum.api.Choice.Objects(card, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                        Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand)))),
                    otherwise = unlock))
        }
        // dsk chunk 03: "This creature can attack this turn as though it didn't have defender." (CR 702.3b)
        CAN_ATTACK_AS_THOUGH_NO_DEFENDER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CanAttackDespiteDefender), arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 03: "Surveil 2 if this is the first time this ability has resolved this turn."
        EFFECT_IF.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self)
            val then = condition?.let { effect(m.groupValues[1] + ".", context) }
            if (condition != null && then != null) return listOf(Step.If(condition, then = then))
        }
        // dsk chunk 03: "Put a creature card from a graveyard onto the battlefield under your control." (chosen as it resolves)
        PUT_CARD_FROM_A_GRAVEYARD_ONTO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard))
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))))
        }
        // dsk chunk 03: "Put all enchantment cards from among them into your hand and the rest on the bottom of your library in a
        // random order." (no choice)
        PUT_ALL_FROM_AMONG_THEM_REST_BOTTOM.matchEntire(sentence)?.let { m ->
            val them = Objects.Bound(context.seen ?: return null)
            val chosen = Objects.Where(them, Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.Move(chosen, Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(them, chosen), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // dsk chunk 04: "Put a flying, lifelink, or +1/+1 counter on it.": the kind chosen as it resolves
        PUT_ONE_OF_COUNTERS.matchEntire(sentence)?.let { m ->
            val kinds = m.groupValues[1].split(Regex(""",? or |, """))
            val objects = if (m.groupValues[2] == "it") context.it ?: return null else if (isSelf(m.groupValues[2])) Objects.Self else return null
            return listOf(Step.Choose(arcanum.api.Choice.Option(kinds), bind = "counter", chooser = Players.You)) + kinds.map { k ->
                Step.If(arcanum.api.Condition.Chose("counter", k), then = listOf(Step.Do(Effect.PutCounters(objects, Counters.kind(k) ?: return null, Value.Fixed(1)))))
            }
        }
        // dsk chunk 04: "Put those counters on up to one target creature." (the counters the event's object had)
        PUT_THOSE_COUNTERS_ON.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null),
                min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.PutCountersOf(Objects.Target(name), of = that)))
        }
        // dsk chunk 04: "That land becomes a 0/0 Elemental creature in addition to its other types." (for good, CR 611.2a)
        THAT_BECOMES_CREATURE.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull()?.takeIf { it.name == Filters.slotName(m.groupValues[1]) }?.let { Objects.Target(it.name) } ?: return null
            val subtype = m.groupValues[4].takeIf { it in RulesWords.subtypes } ?: return null
            context.it = target
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.Indefinitely)))
        }
        // dsk chunk 04: "It gains haste until your next turn."
        IT_GAINS_UNTIL_YOUR_NEXT_TURN.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)), arcanum.api.Affected.Objects(context.it ?: return null),
                arcanum.api.Duration.UntilYourNextTurn)))
        }
        // dsk chunk 04: "Put any number of creature and/or Vehicle cards with different powers from among them into your hand."
        PUT_ANY_NUMBER_WITH_DIFFERENT_FROM_AMONG.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val property = if (m.groupValues[2] == "powers") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.ManaValue
            context.picked = "chosen"
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = null, from = Objects.Bound(seen),
                    distinct = arcanum.api.SharedQuality.Number(property)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // dsk chunk 04: "Put the rest on the bottom of your library in a random order." (after picking from among them)
        if (sentence == "Put the rest on the bottom of your library in a random order." && context.seen != null && context.picked != null) {
            return listOf(Step.Do(Effect.Move(Objects.Except(Objects.Bound(context.seen!!), Objects.Bound(context.picked!!)),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // dsk chunk 14: "Have it get +4/+0 until end of turn." (in "you may have it …"; it: this creature)
        HAVE_IT_GET.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[1].removePrefix("+").toInt()),
                Value.Fixed(m.groupValues[2].removePrefix("+").toInt()))), arcanum.api.Affected.Objects(it), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 14: "Sacrifice it at the beginning of the next end step." (a delayed trigger, CR 603.7)
        if (sentence == "Sacrifice it at the beginning of the next end step." && context.it != null) {
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(context.it!!)))))))
        }
        // dsk chunk 14: "Put target card from a graveyard on the bottom of its owner's library."
        PUT_TARGET_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = -1))))
        }
        // dsk chunk 14: "It becomes a 0/0 Spirit creature in addition to its other types." (in an activated ability: this object;
        // for good, layers 4 and 7b)
        IT_BECOMES_CREATURE.matchEntire(sentence)?.let { m ->
            val it = context.it ?: Objects.Self.takeIf { context.event == null } ?: return null
            val subtype = m.groupValues[3].takeIf { s -> s in RulesWords.subtypes } ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[1].toInt()), Value.Fixed(m.groupValues[2].toInt()))),
                arcanum.api.Affected.Objects(it), arcanum.api.Duration.Indefinitely)))
        }
        // dsk chunk 14: "Put each creature card exiled with this artifact onto the battlefield under your control with a flying
        // counter on it." (CR 607.2a; "each of them" afterwards)
        PUT_EACH_EXILED_WITH_ONTO_BATTLEFIELD.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val cards = Objects.Where(Objects.Remembered(EXILED_WITH), Filters.noun(m.groupValues[1]) ?: return null)
            val counters = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { listOf(arcanum.api.CountersToPut(Counters.kind(it) ?: return null, Value.Fixed(1))) }.orEmpty()
            context.them = Objects.Bound("returned")
            return listOf(Step.Do(Effect.Move(cards, Destination(Zone.Battlefield, controller = Players.You, counters = counters), postBind = "returned")))
        }
        // dsk chunk 14: "Each of them is a 1/1 Spirit in addition to its other types." (for good, layers 4 and 7b)
        EACH_OF_THEM_IS_PT_SUBTYPE.matchEntire(sentence)?.let { m ->
            val them = context.them ?: return null
            val subtype = m.groupValues[3].takeIf { s -> s in RulesWords.subtypes } ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[1].toInt()), Value.Fixed(m.groupValues[2].toInt()))),
                arcanum.api.Affected.Objects(them), arcanum.api.Duration.Indefinitely)))
        }
        // dsk chunk 13: "It deals X damage to each opponent and you gain X life." (this object)
        SELF_DEALS_AND_YOU_GAIN.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" && context.it == Objects.Self }?.let { m ->
            val x = if (isSpell || announcedX) Value.Announced(name = "X") else Value.CastX(of = Objects.Self)
            fun amount(text: String) = if (text == "X") x else Value.Fixed(text.toInt())
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.Each(PlayerFilter.Opponent)), amount(m.groupValues[2]), source = Objects.Self)),
                Step.Do(Effect.GainLife(Players.You, amount(m.groupValues[3]))))
        }
        // dsk chunk 13: "Draw a card for each opponent who lost life this turn."
        if (sentence == "Draw a card for each opponent who lost life this turn.") {
            return listOf(Step.Do(Effect.Draw(Players.You, Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any)))))))))
        }
        // dsk chunk 13: "You mill that many cards." (the event's amount)
        if (sentence == "You mill that many cards.") return listOf(Step.Do(Effect.Mill(Players.You, Value.EventAmount)))
        // dsk chunk 13: "If you put no cards into your hand this way, [effect]." (after choosing from among them)
        IF_YOU_PUT_NO_CARDS_INTO_HAND.matchEntire(sentence)?.let { m ->
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Count(Objects.Bound("chosen")), arcanum.api.Comparison.Equal, Value.Fixed(0)), then = then))
        }
        // dsk chunk 13: "Return it to the battlefield under its owner's control at the beginning of the next end step." (CR 603.7)
        if (sentence == "Return it to the battlefield under its owner's control at the beginning of the next end step." && context.it is Objects.Bound) {
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Move(context.it!!, Destination(Zone.Battlefield, controller = Players.OwnerOf(context.it!!)))))))))
        }
        // dsk chunk 13: "This creature deals that much damage to any target." (what the intervening if counted)
        if (conditionAmount != null) SELF_DEALS_THAT_MUCH_TO_ANY_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = "any"), conditionAmount!!, source = Objects.Self)))
        }
        // dsk chunk 13: "Create Prima, the Undivided, a legendary 0/0 green and blue Fractal creature token, then put that many
        // +1/+1 counters on it." (a named token, CR 111.4; that many: what the intervening if counted)
        if (conditionAmount != null) CREATE_NAMED_TOKEN_THEN_THAT_MANY_COUNTERS.matchEntire(sentence)?.let { m ->
            val token = Tokens.defined("a " + m.groupValues[3]) ?: return null
            val named = token.copy(face = token.face.copy(name = m.groupValues[1],
                supertypes = if (m.groupValues[2].isEmpty()) emptyList() else listOf(arcanum.api.Supertype.Legendary)))
            return listOf(Step.Do(Effect.CreateToken(named, count = Value.Fixed(1), controller = Players.You, bind = TOKENS)),
                Step.Do(Effect.PutCounters(Objects.Bound(TOKENS), Counters.kind(m.groupValues[4]) ?: return null, conditionAmount!!)))
        }
        // dsk chunk 12: "The owner of target artifact or enchantment an opponent controls shuffles it into their library."
        OWNER_OF_TARGET_SHUFFLES.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.OwnerOf(target))))
        }
        // dsk chunk 12: "You may shuffle up to four target cards from your graveyard into your library."
        YOU_MAY_SHUFFLE_UP_TO_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))), min = Value.Fixed(0), max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
            return listOf(Step.May(listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.You))),
                player = Players.You))
        }
        // dsk chunk 12: "Tap one or two target untapped creatures you control." ("they" afterwards)
        TAP_N_OR_M_TARGETS.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[3]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null),
                max = Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))
            context.them = Objects.Target(name)
            return listOf(Step.Do(Effect.Tap(Objects.Target(name))))
        }
        // dsk chunk 12: "They each deal damage equal to their power to target creature an opponent controls." (each is a source of
        // its own damage, CR 120.1)
        THEY_EACH_DEAL_POWER_TO_TARGET.matchEntire(sentence)?.let { m ->
            val them = context.them ?: return null
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.ForEach(arcanum.api.Each.Objects(them), bind = "source", steps = listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = target),
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Bound("source"), arcanum.api.Combine.Single), source = Objects.Bound("source"))))))
        }
        // dsk chunk 12: "Put them onto the battlefield tapped." (the batch's cards, now in the graveyard)
        if (sentence == "Put them onto the battlefield tapped." && context.event is EventPattern.ZoneChange) {
            return listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, tapped = true))))
        }
        // dsk chunk 11: "Gain control of target creature until end of turn." ("it" afterwards)
        GAIN_CONTROL_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            context.it = target
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "Untap it.") return listOf(Step.Do(Effect.Untap(context.it ?: return null)))
        // dsk chunk 11: "Exile it." (the event's object; "that card" afterwards)
        if (sentence == "Exile it." && context.it != null) {
            val it = context.it!!
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(it, Destination(Zone.Exile), postBind = "exiled")))
        }
        // dsk chunk 11: "You may play that card from exile this turn."
        if (sentence == "You may play that card from exile this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(context.it as? Objects.Bound ?: return null), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 11: "This creature deals 1 damage to them." (the event's player)
        SELF_DEALS_N_TO_THEM.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.EventPlayer), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)))
        }
        // dsk chunk 11: "It deals that much damage to any other target." ("this way" afterwards: the damage dealt)
        if (sentence == "It deals that much damage to any other target.") {
            context.targets += TargetSlot("any", TargetKind.Either(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Or(listOf(
                ObjectFilter.Type(CardType.Creature), ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)))), PlayerFilter.Any),
                min = Value.Fixed(1), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = "any"), Value.EventAmount, source = Objects.Self, bind = "dealt")))
        }
        if (sentence == "If a player is dealt damage this way, they can't gain life for the rest of the game.") {
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Bound("dealt"), arcanum.api.Comparison.AtLeast, Value.Fixed(1)), then = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantGainLife), arcanum.api.Affected.Players(Players.Target("any")), arcanum.api.Duration.Indefinitely)))))
        }
        // dsk chunk 11: "Change the target of target spell or ability with a single target." (CR 115.7a)
        if (sentence == "Change the target of target spell or ability with a single target.") {
            val spell = context.target("spell", Filters.all(ObjectFilter.Or(listOf(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell),
                ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility))), ObjectFilter.SingleTarget()))
            return listOf(Step.Do(Effect.ChangeTargets(spell, optional = false, chooser = Players.You)))
        }
        // dsk chunk 11: "One or two target creatures can't block this turn."
        N_OR_M_TARGETS_CANT_BLOCK.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[3]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null),
                max = Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), arcanum.api.Affected.Objects(Objects.Target(name)), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 11: "It deals 3 damage to up to one target creature." (this object)
        SELF_DEALS_TO_UP_TO_ONE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || (it.groupValues[1] == "It" && context.targets.isEmpty()) }?.let { m ->
            val noun = m.groupValues[3]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Target(name)), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)))
        }
        // dsk chunk 11: "Target creature you control deals damage equal to its power to each other creature." (the target deals it)
        TARGET_DEALS_POWER_TO_EACH_OTHER.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            val others = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.Not(ObjectFilter.Is(target)))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Each(others)),
                Value.Of(arcanum.api.NumericProperty.Power, target, arcanum.api.Combine.Single), source = target)))
        }
        // dsk chunk 11: "If that creature would die this turn, exile it instead." (a replacement for the turn, CR 614.1a)
        IF_THAT_WOULD_DIE_THIS_TURN.matchEntire(sentence)?.let {
            val slot = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects } ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Target(slot.name)), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 11: "Then if there are seven or more time counters on it, remove those counters and it deals 20 damage to you."
        REMOVE_THOSE_COUNTERS_AND_DEALS.matchEntire(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() })?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(kind, atLeast = Numbers.count(m.groupValues[1]) ?: return null)),
                then = listOf(
                    Step.Do(Effect.RemoveCounters(Objects.Self, kind, Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single))),
                    Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.You), Value.Fixed(m.groupValues[3].toInt()), source = Objects.Self)))))
        }
        // dsk chunk 11: "When you next cast an instant or sorcery spell this turn, copy that spell." (a delayed trigger, once,
        // this turn, CR 603.7b)
        WHEN_YOU_NEXT_CAST.matchEntire(sentence)?.let { m ->
            val spell = Filters.noun(m.groupValues[1]) ?: return null
            if (m.groupValues[2] != "copy that spell") return null
            context.copied = true
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.Cast(spell, by = PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true)))),
                duration = arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 11: "Choose two target creatures controlled by the same opponent." / "That player chooses one of those
        // creatures." / "[This] deals 5 damage to that creature, and the other can't block this turn."
        CHOOSE_TARGETS_SAME_OPPONENT.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.Opponent))),
                min = Value.Fixed(n), max = Value.Fixed(n), sameController = true)
            return emptyList()
        }
        if (sentence == "That player chooses one of those creatures.") {
            val slot = Objects.Target(context.targets.lastOrNull()?.name ?: return null)
            return listOf(Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = slot), bind = "chosen", chooser = Players.ControllerOf(slot)))
        }
        DEALS_TO_THAT_CREATURE_OTHER_CANT_BLOCK.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val slot = Objects.Target(context.targets.lastOrNull()?.name ?: return null)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Bound("chosen")), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), arcanum.api.Affected.Objects(Objects.Except(slot, Objects.Bound("chosen"))),
                    arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 10: "They lose half their life, rounded up." (the event's player, CR 107.1a)
        if (sentence == "They lose half their life, rounded up.")
            return listOf(Step.Do(Effect.LoseLife(Players.EventPlayer, Value.Half(Value.LifeOf(Players.EventPlayer), roundUp = true))))
        // dsk chunk 10: "Destroy it." (the event's object), "Tap it." (in an activated ability: this object)
        if (sentence == "Destroy it.") return listOf(Step.Do(Effect.Destroy(context.it ?: return null)))
        if (sentence == "Tap it.") return listOf(Step.Do(Effect.Tap(context.it ?: Objects.Self.takeIf { context.event == null && context.targets.isEmpty() } ?: return null)))
        // dsk chunk 10: "Each player exiles all but the bottom six cards of their library face down."
        EACH_PLAYER_EXILES_ALL_BUT_BOTTOM.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            // one event for every player's cards (CR 101.4)
            return listOf(Step.Do(Effect.Move(Objects.Except(Objects.Each(ObjectFilter.InZone(Zone.Library)),
                Objects.LibraryCards(Players.Each(PlayerFilter.Any), Value.Fixed(n), fromTop = false)), Destination(Zone.Exile, faceDown = m.groupValues[2].isNotEmpty()))))
        }
        // dsk chunk 10: "Up to that many target creatures each get -2/-2 until end of turn." (that many: the cards discarded)
        UP_TO_THAT_MANY_TARGETS_GET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = Value.Count(Objects.Bound("discarded")))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Target(name)), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 10: "Look at the top four cards of your library. Put one of them into your hand and the rest into your
        // graveyard."
        LOOK_PUT_ONE_INTO_HAND_REST_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Graveyard))))
        }
        // dsk chunk 10: "Return that card under your control with a finality counter on it." (the card that died)
        RETURN_THAT_CARD_UNDER_CONTROL.matchEntire(sentence)?.let { m ->
            val card = context.it ?: return null
            val controller = if (m.groupValues[2] == "your") Players.You else Players.OwnerOf(card)
            val counters = m.groupValues[5].takeIf { it.isNotEmpty() }?.let { c ->
                listOf(arcanum.api.CountersToPut(Counters.kind(c) ?: return null, Value.Fixed(Numbers.count(m.groupValues[4]) ?: return null))) }.orEmpty()
            return listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield, tapped = m.groupValues[3].isNotEmpty(), controller = controller, counters = counters))))
        }
        // dsk chunk 10: "They may pay 3 life." (the controller of the creature that died) then "If they don't, [effect]." (D52)
        THEY_MAY_PAY.matchEntire(sentence)?.let { m ->
            val payer = Players.ControllerOf(context.that ?: return null)
            val parts = costPart(m.groupValues[1].replaceFirstChar { it.uppercase() }) ?: return null
            return listOf(Step.Pay(Cost(parts), payer = payer, bind = "paid"))
        }
        IF_THEY_DONT.matchEntire(sentence)?.let { m ->
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("paid")), then = then))
        }
        // dsk chunk 10: "You may choose a nonland card from it.": a choice of zero or one (D49)
        YOU_MAY_CHOOSE_FROM_IT.matchEntire(sentence)?.let { m ->
            val from = context.revealed ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            if ("chosen" !in context.chosen) context.chosen += "chosen"
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You))
        }
        // dsk chunk 10: "Target player sacrifices a creature of their choice other than enchanted creature."
        TARGET_PLAYER_SACRIFICES_OTHER_THAN.matchEntire(sentence)?.let { m ->
            val player = context.targetPlayer()
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))),
                ObjectFilter.ControlledBy(PlayerFilter.Is(player)))
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed", chooser = player),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))
        }
        // dsk chunk 10: "Each opponent sacrifices a nontoken enchantment of their choice, then sacrifices a nontoken creature of
        // their choice." (one sacrifice event each time, CR 101.4)
        EACH_SACRIFICES_THEN_SACRIFICES.matchEntire(sentence)?.let { m ->
            val first = effect("Each ${m.groupValues[1]} sacrifices a ${m.groupValues[2]} of their choice.", context) ?: return null
            val second = effect("Each ${m.groupValues[1]} sacrifices a ${m.groupValues[3]} of their choice.", context) ?: return null
            return first + second
        }
        // dsk chunk 09: "Target creature can't be blocked this turn.", "Another target attacking creature can't be blocked this turn."
        TARGET_CANT_BE_BLOCKED_THIS_TURN.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun), if (m.groupValues[1] == "Another target") Filters.all(context.another(), filter) else filter)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeBlocked), arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 09: "Draw that many cards.", "Discard that many cards." (the event's amount: damage, life)
        THAT_MANY_CARDS.matchEntire(sentence)?.takeIf { context.event != null && context.event !is EventPattern.Discard }?.let { m ->
            val step = if (m.groupValues[1] == "Draw") Effect.Draw(Players.You, Value.EventAmount) else Effect.Discard(Players.You, Value.EventAmount, filter = ObjectFilter.Any)
            return listOf(Step.Do(step))
        }
        if (sentence == "You lose the game.") return listOf(Step.Do(Effect.LoseGame(Players.You)))
        // dsk chunk 09: "If an opponent controls that creature, put a stun counter on it."
        IF_OPPONENT_CONTROLS_THAT.matchEntire(sentence)?.let { m ->
            val that = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(that, ObjectFilter.ControlledBy(PlayerFilter.Opponent)), then = then))
        }
        // dsk chunk 09: "Put each card exiled with it into its owner's hand." (CR 607.2a)
        if (sentence == "Put each card exiled with it into its owner's hand.") return listOf(Step.Do(Effect.Move(Objects.Remembered(EXILED_WITH), Destination(Zone.Hand))))
        // dsk chunk 09: "Tap any number of untapped creatures you control." ("tapped this way" afterwards)
        TAP_ANY_NUMBER.matchEntire(sentence)?.let { m ->
            return listOf(Step.Choose(arcanum.api.Choice.Objects(Filters.noun(m.groupValues[1]) ?: return null, min = Value.Fixed(0), max = null),
                bind = "tapped", chooser = Players.You), Step.Do(Effect.Tap(Objects.Bound("tapped"))))
        }
        // dsk chunk 09: "This creature gets +1/+1 until end of turn for each creature tapped this way."
        SELF_GETS_FOR_EACH_TAPPED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val per = Value.Count(Objects.Bound("tapped"))
            fun amount(n: Int): Value = when (n) { 0 -> Value.Fixed(0); 1 -> per; else -> Value.Times(per, n) }
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[2].toInt()), amount(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 09: "Return one or two target creatures and/or enchantments you own to your hand."
        RETURN_N_OR_M_TARGETS_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[3].replace(" and/or ", " or ")
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null),
                max = Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // dsk chunk 09: "It becomes a black Demon in addition to its other colors and types." (for good, layers 4 and 5)
        IT_BECOMES_COLOR_SUBTYPE.matchEntire(sentence)?.let { m ->
            val color = BECOME_COLORS[m.groupValues[1]] ?: return null
            val subtype = m.groupValues[2].takeIf { it in RulesWords.subtypes } ?: return null
            val objects = context.it ?: Objects.Self
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetColors(listOf(ColorRef.Literal(color)), add = true)), arcanum.api.Affected.Objects(objects), arcanum.api.Duration.Indefinitely)))
        }
        // dsk chunk 09: "Target nonland permanent's owner puts it on their choice of the top or bottom of their library."
        OWNER_PUTS_ON_TOP_OR_BOTTOM.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Choose(arcanum.api.Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target)),
                Step.If(arcanum.api.Condition.Chose("position", "top"), then = listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = 0)))),
                    otherwise = listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = -1))))))
        }
        // dsk chunk 09: "This creature becomes a copy of another target creature you control." (for good, layer 1)
        SELF_BECOMES_COPY.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun), if (m.groupValues[2].isEmpty()) filter else Filters.all(context.another(), filter))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target)), arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely)))
        }
        // dsk chunk 09: "Until end of turn, target creature gets +2/+2, gains flying, and becomes a Horror enchantment creature in
        // addition to its other types."
        UNTIL_EOT_TARGET_GETS_GAINS_BECOMES.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val keywords = m.groupValues[4].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val add = mutableListOf<CardType>()
            val subtypes = mutableListOf<arcanum.api.SubtypeRef>()
            for (word in m.groupValues[5].split(' ')) {
                val type = Filters.CARD_TYPES[word]
                when {
                    type != null -> add += type
                    word in RulesWords.subtypes -> subtypes += arcanum.api.SubtypeRef.Literal(Subtype(word))
                    else -> return null
                }
            }
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null),
                Mechanic.GainAbilities(keywords), Mechanic.SetTypes(add = add, addSubtypes = subtypes)), arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 09: "If they do, surveil 2." after "unless its controller pays": they paid
        IF_THEY_DO.matchEntire(sentence)?.let { m ->
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        // dsk chunk 09: "Shuffle this creature and target creature with a stun counter on it into their owners' libraries."
        SHUFFLE_SELF_AND_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[2]
            val both = Objects.Union(listOf(Objects.Self, context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)))
            return listOf(Step.Do(Effect.Move(both, Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.OwnerOf(both))))
        }
        // dsk chunk 08: "Target opponent loses that much life." (the event's amount)
        TARGET_LOSES_THAT_MUCH.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            return listOf(Step.Do(Effect.LoseLife(player, Value.EventAmount)))
        }
        // dsk chunk 08: "Create a tapped colorless land token named Everywhere that is every basic land type." (CR 111.4)
        CREATE_NAMED_LAND_TOKEN.matchEntire(sentence)?.let { m ->
            val face = arcanum.api.FaceDefinition(name = m.groupValues[2], colorIndicator = emptyList(), types = listOf(CardType.Land),
                subtypes = Filters.BASIC_LAND_TYPES.map { Subtype(it) })
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.Defined(face), count = Value.Fixed(1), controller = Players.You,
                tapped = m.groupValues[1].isNotEmpty())))
        }
        // dsk chunk 07: "After this phase, there is an additional combat phase." (CR 500.8)
        if (sentence == "After this phase, there is an additional combat phase.") return listOf(Step.Do(Effect.ExtraCombat()))
        // dsk chunk 07: "Create a number of 1/1 … tokens with flying equal to the number of card types among cards in your
        // graveyard."
        CREATE_A_NUMBER_EQUAL_TO.matchEntire(sentence)?.let { m ->
            val (_, token) = Tokens.spec("a " + m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Values.value(m.groupValues[2], exiledCard) ?: return null, controller = Players.You)))
        }
        // dsk chunk 07: "This creature deals that amount of damage to target creature that player controls."
        DEALS_THAT_AMOUNT_TO_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[2]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = target), Value.EventAmount, source = Objects.Self)))
        }
        // dsk chunk 07: "Look at the top card of your library." ("it", "the card" afterwards)
        if (sentence == "Look at the top card of your library.") {
            context.it = Objects.Bound("seen")
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), players = Players.You, bind = "seen")))
        }
        // dsk chunk 07: "If it's a land card, you may put it onto the battlefield." (a "you may" a later "if you don't" reads)
        IF_ITS_A_YOU_MAY_PUT_ONTO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, Filters.noun(m.groupValues[1]) ?: return null), then = listOf(
                Step.Pay(steps = listOf(Step.Do(Effect.Move(it, Destination(Zone.Battlefield)))), payer = Players.You, bind = "put"))))
        }
        if (sentence == "If you don't put the card onto the battlefield, put it into your hand.") {
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("put")), then = listOf(Step.Do(Effect.Move(context.it ?: return null, Destination(Zone.Hand))))))
        }
        // dsk chunk 07: "Distribute two +1/+1 counters among one or two target creatures." (CR 601.2d: divided as it is put on
        // the Stack)
        DISTRIBUTE_COUNTERS_AMONG.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[5]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null),
                max = Value.Fixed(Numbers.count(m.groupValues[4]) ?: return null), divided = Value.Fixed(n))
            return listOf(Step.Do(Effect.PutCounters(Objects.Target(name), Counters.kind(m.groupValues[2]) ?: return null, Value.DividedShare)))
        }
        // dsk chunk 07: "Double the number of +1/+1 counters on those creatures." (CR 701.10e)
        DOUBLE_COUNTERS_ON_THOSE.matchEntire(sentence)?.let { m ->
            val slot = context.targets.lastOrNull()?.name ?: return null
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Double(arcanum.api.Doubled.Counters(Objects.Target(slot), Counters.kind(m.groupValues[1]) ?: return null)))))
        }
        // dsk chunk 07: "Each opponent may sacrifice a nonland permanent of their choice or discard a card." Each decides in turn
        // order (CR 101.4); "this way" afterwards reads whether they did
        EACH_OPPONENT_MAY.matchEntire(sentence)?.let { m ->
            val options = m.groupValues[1].split(" or ").map { option ->
                Cost(costPart(option.removeSuffix(" of their choice").replaceFirstChar { it.uppercase() }) ?: return null)
            }
            val cost = Cost(listOf(options.singleOrNull()?.parts?.single() ?: CostPart.OneOf(options)))
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Pay(cost, payer = Players.Bound("opponent"), bind = "paid"))))
        }
        // dsk chunk 07: "Choose target creature you control and target creature an opponent controls." (the second named apart)
        CHOOSE_TWO_TARGETS.matchEntire(sentence)?.takeIf { Filters.noun(it.groupValues[1]) != null && Filters.noun(it.groupValues[2]) != null }?.let { m ->
            val first = Filters.noun(m.groupValues[1])!!
            val second = Filters.noun(m.groupValues[2])!!
            val name = Filters.slotName(m.groupValues[1])
            val other = Filters.slotName(m.groupValues[2]).let { if (it == name) "other $it" else it }
            context.targets += TargetSlot(name, TargetKind.Objects(first), min = Value.Fixed(1), max = Value.Fixed(1))
            context.targets += TargetSlot(other, TargetKind.Objects(second), min = Value.Fixed(1), max = Value.Fixed(1))
            return emptyList()
        }
        // dsk chunk 07: "Put two +1/+1 counters on the creature you control." (a target named by its noun)
        PUT_COUNTERS.matchEntire(sentence)?.takeIf { it.groupValues[3].startsWith("the ") }?.let { m ->
            context.slotFor(m.groupValues[3].removePrefix("the "))?.let { slot ->
                return listOf(Step.Do(Effect.PutCounters(slot, Counters.kind(m.groupValues[2]) ?: return null, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
            }
        }
        // dsk chunk 07: "The creature you control deals damage equal to its power to the creature an opponent controls."
        THE_TARGET_DEALS_DAMAGE_EQUAL_TO_ITS_POWER.matchEntire(sentence)?.let { m ->
            val source = context.slotFor(m.groupValues[1]) ?: return null
            val to = context.slotFor(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = to),
                Value.Of(arcanum.api.NumericProperty.Power, source, arcanum.api.Combine.Single), source = source)))
        }
        // dsk chunk 07: "It deals X damage to each of up to X target creatures." (X of the spell it was, CR 107.3m)
        DEALS_X_TO_EACH_OF_UP_TO_X.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            val x = Value.CastX(of = Objects.Self)
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = x)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Target(name)), x, source = Objects.Self)))
        }
        // dsk chunk 06: "Manifest dread X times, then put X +1/+1 counters on each of those creatures." (CR 701.40e: one at a time)
        MANIFEST_DREAD_TIMES.matchEntire(sentence)?.let { m ->
            val times = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val n = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            return listOf(Step.Repeat(times, listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.ManifestDread(Players.You), bind = MANIFESTED)))),
                Step.Do(Effect.PutCounters(Objects.Bound(MANIFESTED), Counters.kind(m.groupValues[3]) ?: return null, n)))
        }
        // dsk chunk 06: "You may return a permanent card from your graveyard to your hand." (zero or one, not targeted)
        MAY_RETURN_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // dsk chunk 06: "Reveal target face-down permanent." ("it" afterwards)
        REVEAL_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            context.it = target
            return listOf(Step.Do(Effect.Reveal(target)))
        }
        // dsk chunk 06: "If it's a creature card, you may turn it face up.": the card's own types, not its face-down
        // characteristics (CR 708.2a)
        if (sentence == "If it's a creature card, you may turn it face up.") {
            val it = context.it ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, ObjectFilter.Type(CardType.Creature, ignoringTypeChanges = true)),
                then = listOf(Step.May(listOf(Step.Do(Effect.TurnFaceUp(it))), player = Players.You))))
        }
        // dsk chunk 05: "Its controller manifests dread." (the last target's), "The exiled card's owner manifests dread." (CR 701.62a)
        PLAYER_MANIFESTS_DREAD.matchEntire(sentence)?.let { m ->
            val player = when (m.groupValues[1]) {
                "Its controller" -> context.lastTargetController()
                "The exiled card's owner" -> exiledCard?.let { Players.OwnerOf(it) }
                else -> null
            } ?: return null
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.ManifestDread(player))))
        }
        // dsk chunk 05: "Attach this Equipment to that creature." after manifesting dread
        ATTACH_SELF_TO_THAT_CREATURE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) && context.manifested != null }?.let {
            return listOf(Step.Do(Effect.Attach(Objects.Self, arcanum.api.Recipients(objects = context.manifested!!))))
        }
        // dsk chunk 05: "When it dies this turn, [effect]." (a delayed trigger about the target, for the turn, CR 603.7b, c)
        WHEN_IT_DIES_THIS_TURN.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val then = instructions(m.groupValues[1].replaceFirstChar { it.uppercase() }) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(target), from = Zone.Battlefield, to = Zone.Graveyard)), then,
                duration = arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 05: "Tap enchanted creature."
        if (sentence == "Tap enchanted creature." || sentence == "Tap equipped creature.") return listOf(Step.Do(Effect.Tap(Objects.AttachedTo(Objects.Self))))
        // dsk chunk 05: "Shuffle enchanted creature into its owner's library."
        if (sentence == "Shuffle enchanted creature into its owner's library.") {
            val enchanted = Objects.AttachedTo(Objects.Self)
            return listOf(Step.Do(Effect.Move(enchanted, Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.OwnerOf(enchanted))))
        }
        // dsk chunk 05: "Put a card you put into your graveyard this way into your hand." (in a manifest-dread trigger, CR 701.62b)
        if (sentence == "Put a card you put into your graveyard this way into your hand." && context.event is EventPattern.ManifestDread) {
            return listOf(Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.EventObject(EventRole.Rest)),
                bind = "chosen", chooser = Players.You), Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        return null
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
        // "Draw two cards. Then discard a card …": a sentence that starts with "Then" follows the one before it.
        if (sentence.startsWith("Then ")) return effect(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() }, context)
        // blb: the Bloomburrow wordings (blbEffect), tried first; each is specific and falls through when it can't read
        blbEffect(sentence, context)?.let { return it }
        blbGapsEffect(sentence, context)?.let { return it } // blb gaps
        blbGaps2Effect(sentence, context)?.let { return it } // blb gaps 2
        rtrEffect(sentence, context)?.let { return it } // rtr
        // dsk: the Duskmourn wordings, tried first (their own function keeps this one under the JVM's method size limit)
        setWordings(sentence, context)?.let { return it }
        // dsk chunk 01: "Destroy all creatures with power 3 or greater."
        DESTROY_ALL.matchEntire(sentence)?.let { m -> Filters.noun(m.groupValues[1])?.let { return listOf(Step.Do(Effect.Destroy(Objects.Each(it)))) } }
        // chunk 12
        SEARCH_LIBRARY_AND_OR_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[2].removeArticle()) ?: return null
            // dsk chunk 12: "your graveyard, hand, and/or library"
            val zones = if (m.groupValues[1].startsWith("library")) listOf(Zone.Library, Zone.Graveyard) else listOf(Zone.Graveyard, Zone.Hand, Zone.Library)
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, zones = zones, bind = "found", max = Value.Fixed(1), searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))))
        }
        IF_X_AT_LEAST.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context)?.let { then ->
                return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(name = "X"), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())), then = then))
            }
        }
        // "Creatures you control get +X/+X and gain haste until end of turn." (locked in when it resolves, CR 611.2c)
        GROUP_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            // chunk 40: a subtype stays capitalised ("Elves you control")
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() })
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
            return listOf(Step.Do(if (m.groupValues[1] == "Draw") Effect.Draw(Players.You, Value.Announced(name = "X")) else Effect.Discard(Players.You, Value.Announced(name = "X"), filter = ObjectFilter.Any)))
        }
        // dsk gaps: "Exile any number of target instant, sorcery, and/or Tamiyo planeswalker cards from your graveyard."
        EXILE_ANY_NUMBER_FROM_YOUR_GRAVEYARD.matchEntire(sentence)?.let { m ->
            // Cards in a graveyard, not permanents: the slot is "cards"
            val noun = m.groupValues[1]
            val filter = Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            context.targets += TargetSlot("cards", TargetKind.Objects(filter), min = Value.Fixed(0), max = null)
            return listOf(Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Exile))))
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
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Paid(arcanum.api.Keyword.Cleave.Kind, times = 1, of = Objects.Self)), then = full, otherwise = cleaved))
        }
        // chunk 15: "Choose artifact, creature, or land." (a card type, chosen as it resolves)
        CHOOSE_CARD_TYPE.matchEntire(sentence)?.let { m ->
            val types = m.groupValues[1].split(Regex(""",? or |, """)).map { Filters.CARD_TYPES[it] }
            if (types.all { it != null }) return listOf(Step.Choose(arcanum.api.Choice.CardType(types.filterNotNull()),
                bind = CHOSEN_TYPE, chooser = Players.You))
        }
        // chunk 15: "Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents
        // of that type that player controls." (tap or untap is chosen as it resolves)
        TAP_OR_UNTAP_ALL_OF_CHOSEN_TYPE.matchEntire(sentence)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val player = context.targetPlayer()
            fun all(status: ObjectFilter) = Objects.Each(Filters.all(noun, ObjectFilter.Type(arcanum.api.CardTypeRef.Chosen(CHOSEN_TYPE)),
                status, ObjectFilter.ControlledBy(PlayerFilter.Is(player))))
            val tapped = ObjectFilter.HasStatus(arcanum.api.Status.Tapped)
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("tapOrUntap", "tap"),
                    then = listOf(Step.Do(Effect.Tap(all(ObjectFilter.Not(tapped))))), otherwise = listOf(Step.Do(Effect.Untap(all(tapped))))))
        }
        CHOOSE_CARD_NAME.matchEntire(sentence)?.let { m ->
            val filter = m.groupValues[1].ifEmpty { null }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            context.cardName = "name"
            return listOf(Step.Choose(arcanum.api.Choice.CardName(filter), bind = "name", chooser = Players.You))
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
                steps += Step.Do(Effect.Discard(player, objects = Objects.Where(Objects.Bound("revealed"), ObjectFilter.ChosenName(name)), count = Value.Fixed(1), filter = ObjectFilter.Any))
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
                Step.Choose(arcanum.api.Choice.Objects(inHand, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You),
                Step.Choose(arcanum.api.Choice.Objects(inGraveyard, Value.Fixed(1), Value.Fixed(1)), bind = "chosenFromGraveyard", chooser = Players.You))
        }
        // "You choose a nonland card from it[ with mana value 3 or less]." (someone else chooses: Choose + Discard(objects))
        YOU_CHOOSE_FROM_IT.matchEntire(sentence)?.let { m ->
            val from = context.revealed ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle() + m.groupValues[2]) ?: return null
            if ("chosen" !in context.chosen) context.chosen += "chosen"
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You))
        }
        if (sentence == "That player discards that card.") {
            val player = context.player ?: return null
            val card = context.chosen.lastOrNull() ?: return null
            return listOf(Step.Do(Effect.Discard(player, objects = Objects.Bound(card), count = Value.Fixed(1), filter = ObjectFilter.Any)))
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
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Connive(Objects.PaidWith(arcanum.api.ManaPaymentWay.TapCreatures, of = Objects.Self), count = Value.Fixed(1)))))
        DESTROY_UP_TO.matchEntire(sentence)?.let { m ->
            val max = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val noun = m.groupValues[2].replace(" and/or ", " or ")
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = max)
            return listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        // 'Create twice X 1/1 black and green Pest creature tokens with "When this token dies, you gain 1 life."'
        CREATE_TOKENS_WITH_QUOTED.matchEntire(sentence)?.let { m ->
            val count = if (m.groupValues[1] == "twice X") Value.Times(Value.Announced(name = "X"), 2) else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val token = Tokens.defined("a " + m.groupValues[2] + " token") ?: return null
            val granted = Sentences(setOf("this token", "this creature")).line(m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token.copy(face = token.face.copy(abilities = listOf(granted))), count = count, controller = Players.You)))
        }
        // chunk 14
        // "[This] deals X damage divided as you choose among any number of targets." (CR 601.2d, 115.4)
        DIVIDED_AMONG_ANY_NUMBER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            val slot = context.targets.removeAt(context.targets.lastIndex)
            context.targets += slot.copy(min = Value.Fixed(0), max = null, divided = Value.Announced(name = "X"))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = slot.name), Value.DividedShare, source = Objects.Self)))
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
                Step.May(player = payer, steps = listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = payer, newTargets = true)))))))
        }
        // "Copy target instant or sorcery spell[ you control]." (CR 707.10)
        COPY_TARGET.matchEntire(sentence)?.let { m ->
            context.copied = true
            return listOf(Step.Do(Effect.CopySpell(context.target("spell", Filters.noun(m.groupValues[1]) ?: return null), controller = Players.You, newTargets = true)))
        }
        // CR 707.10c: the copy's controller may choose new targets, which CopySpell allows by default.
        COPY_NEW_TARGETS.matchEntire(sentence)?.let { return if (context.copied) emptyList() else null }
        if (sentence == "You may copy this spell and may choose a new target for the copy.") {
            context.copied = true
            return listOf(Step.May(listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = Players.You, newTargets = true))), player = Players.You))
        }
        IF_CAST_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.CastFrom(Zone.Graveyard, of = Objects.Self), then = then))
        }
        // "Until end of turn, whenever …" / "Whenever … this turn, …": a delayed trigger for the turn (CR 603.7b)
        (UNTIL_END_OF_TURN_WHENEVER.matchEntire(sentence) ?: WHENEVER_THIS_TURN.matchEntire(sentence))?.let { m ->
            val event = Events.event(m.groupValues[1], self) ?: return null
            val then = instructions(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(event), then,
                duration = arcanum.api.Duration.EndOfTurn, repeats = true)))
        }
        if (sentence == "That player copies it and may choose new targets for the copy.") {
            return listOf(Step.Do(Effect.CopySpell(Objects.EventObject(role = EventRole.Object), controller = Players.EventPlayer, newTargets = true)))
        }
        RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            // chunk 35: "another target"
            val another = if (m.groupValues[1].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(another + listOf(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))).toTypedArray())
            // dsk chunk 13: "with a finality counter on it"
            val counters = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { listOf(arcanum.api.CountersToPut(Counters.kind(it) ?: return null, Value.Fixed(1))) }.orEmpty()
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Battlefield, tapped = m.groupValues[3].isNotEmpty(), counters = counters))))
        }
        if (sentence == "Shuffle your library.") return listOf(Step.Do(Effect.Shuffle(Players.You)))
        if (sentence == "Until end of turn, you may play that card without paying its mana cost.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, withoutPaying = true, holder = PlayerFilter.You)),
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
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
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
            val most = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(m.groupValues[2].toInt())
            val slots = m.groupValues[1].split(" and/or ").map { part ->
                val noun = UP_TO_ONE_TARGET_CARD.matchEntire(part)?.groupValues?.get(1) ?: return null
                val filter = Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, most))
                TargetSlot(noun, TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(1))
            }
            context.targets += slots
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(0), Value.Fixed(slots.size),
                    from = Objects.Union(slots.map { Objects.Target(it.name) })), bind = "toCast", chooser = Players.You),
                Step.Do(Effect.Cast(Objects.Bound("toCast"), withoutPaying = true, bind = "cast", how = PlayKind.Cast, caster = Players.You)))
        }
        if (sentence == "If a spell cast this way would be put into your graveyard, exile it instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.Indefinitely)))
        }
        IF_X_AT_LEAST_COPY_TWICE.matchEntire(sentence)?.let { m ->
            context.copied = true
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(name = "X"), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())),
                then = listOf(Step.Repeat(Value.Fixed(2), listOf(Step.Do(Effect.CopySpell(Objects.Bound("cast"), controller = Players.You, newTargets = true)))))))
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
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(most), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1))))
        }
        // In a mana trigger on tapping a permanent: its controller tapped it (CR 605.1b).
        THAT_PLAYER_ADDS_ADDITIONAL.matchEntire(sentence)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana)), players = Players.ControllerOf(Objects.EventObject(role = EventRole.Object)))))
        }
        if (sentence == "Take an extra turn after this one.") return listOf(Step.Do(Effect.ExtraTurn(Players.You)))
        PUT_FROM_HAND_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n, n), bind = "put", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("put"), Destination(Zone.Library, libraryPosition = 0))))
        }
        PLAYERS_DRAW.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(players, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))
            if (m.groupValues[3].isNotEmpty())
                steps += Step.Do(Effect.Discard(players, Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null), random = m.groupValues[4].isNotEmpty(), filter = ObjectFilter.Any))
            if (m.groupValues[5].isNotEmpty()) steps += Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[5].toInt())))
            return steps
        }
        EACH_PLAYER_DISCARDS_HAND_DRAWS.matchEntire(sentence)?.let { m ->
            val each = Players.Each(PlayerFilter.Any)
            return listOf(
                Step.Do(Effect.Discard(each, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)), count = Value.Fixed(1), filter = ObjectFilter.Any)),
                Step.Do(Effect.Draw(each, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD_UNLESS_ATTACKED.matchEntire(sentence)?.let { m ->
            val attacked = arcanum.api.Condition.HappenedThisTurn(arcanum.api.EventPattern.Attack(attacker = ObjectFilter.ControlledBy(PlayerFilter.You)), atLeast = 1, measure = Measure.Events)
            return listOf(Step.If(arcanum.api.Condition.Not(attacked),
                then = listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))))
        }
        SCRY_OR_SURVEIL.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            return listOf(Step.Do(if (m.groupValues[1] == "Scry") Effect.Scry(Players.You, n) else Effect.Surveil(Players.You, n)))
        }
        SEARCH_TO_TOP.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card").ifEmpty { "card" }) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found", max = Value.Fixed(1), zones = listOf(Zone.Library), searcher = Players.You)),
                Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Library, libraryPosition = 0))))
        }
        SEARCH_PUT_SHUFFLE.matchEntire(sentence)?.let { m ->
            // dsk chunk 07: "for any card" is "for a card"
            val filter = Filters.noun(m.groupValues[1].let { if (it == "any card") "a card" else it }.removeArticle().removeSuffix(" card")) ?: return null
            // chunks 32, 42: "reveal it, ", "into your graveyard", "onto the battlefield tapped"
            val to = when (m.groupValues[3]) {
                "into your hand" -> Destination(Zone.Hand)
                "into your graveyard" -> Destination(Zone.Graveyard)
                else -> Destination(Zone.Battlefield, tapped = m.groupValues[3].endsWith(" tapped"))
            }
            val steps = mutableListOf<Step>(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found", max = Value.Fixed(1), zones = listOf(Zone.Library), searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), to)))
            if (m.groupValues[4].isNotEmpty()) steps += Step.Do(Effect.Discard(Players.You, random = true, count = Value.Fixed(1), filter = ObjectFilter.Any))
            steps += Step.Do(Effect.Shuffle(Players.You))
            return steps
        }
        if (sentence == "Look at target player's hand.") {
            val player = context.targetPlayer()
            val hand = Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player))))
            // chunk 52: "that player", "from it"
            context.player = player
            context.revealed = hand
            return listOf(Step.Do(Effect.LookAt(hand, players = Players.You)))
        }
        TARGET_PLAYER_MILLS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        LOOK_PUT_BACK.matchEntire(sentence)?.let { m ->
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), fromTop = true), bind = "seen", players = Players.You)),
                Step.Do(Effect.Reorder(Objects.Bound("seen"), chooser = Players.You)))
        }
        if (sentence == "You may shuffle.") return listOf(Step.May(listOf(Step.Do(Effect.Shuffle(Players.You))), player = Players.You))
        if (sentence == "Investigate.") return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Investigate(count = Value.Fixed(1)))))
        TARGET_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            // chunk 40: "+X/-X"
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
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
            return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
        }
        EXILE_THEN_RETURN.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            // chunk 52: "another target creature you control"
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun), if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))
            return listOf(
                Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))
        }
        TARGET_PLAYER_DRAWS.matchEntire(sentence)?.let { m ->
            val player = context.targetPlayer()
            // chunk 56: "Target player draws X cards."
            val drawn = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(player, drawn)))
            if (m.groupValues[2].isNotEmpty()) steps += Step.Do(Effect.Discard(player, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null), filter = ObjectFilter.Any))
            return steps
        }
        TARGET_PLAYER_DISCARDS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))
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
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(kept), Value.Fixed(kept), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1))))
        }
        LOOK_HAND_BOTTOM_EXILE.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            fun one(from: Objects, bind: String) = Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = from), bind = bind, chooser = Players.You)
            val rest = Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand"))
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                one(Objects.Bound("seen"), "toHand"),
                Step.Do(Effect.Move(Objects.Bound("toHand"), Destination(Zone.Hand))),
                one(rest, "toBottom"),
                Step.Do(Effect.Move(Objects.Bound("toBottom"), Destination(Zone.Library, libraryPosition = -1))),
                Step.Do(Effect.Move(Objects.Except(rest, Objects.Bound("toBottom")), Destination(Zone.Exile), postBind = "exiled")))
        }
        if (sentence == "You may play the exiled card this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        SELF_DEALS_DAMAGE_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val amount = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(m.groupValues[2].toInt())
            val to = when (val what = m.groupValues[3]) {
                "any target" -> { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                // blb chunk 01
                "target player" -> arcanum.api.Recipients(players = context.targetPlayer())
                "target opponent" -> arcanum.api.Recipients(players = context.targetOpponent())
                else -> {
                    val noun = what.removePrefix("target ").takeIf { what.startsWith("target ") } ?: return null
                    arcanum.api.Recipients(objects = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null))
                }
            }
            return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
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
            // chunk 41: "If it was a permanent card, …"
            context.it = Objects.Bound("exiled")
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
            if (m.groupValues[2].isEmpty()) return this.exile(target) // chunk 33: linked to "the exiled card"
            // Converge: "if its mana value is less than or equal to the number of colors of mana spent to cast this spell"
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.ManaSpent(distinctColors = true, of = Objects.Self))), then = listOf(exile)))
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
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(n)), bind = "untapped", chooser = Players.You),
                Step.Do(Effect.Untap(Objects.Bound("untapped"))))
        }
        COUNTER_TARGET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val spell = context.target(Filters.slotName(m.groupValues[1]), filter)
            val counter = Step.Do(Effect.Counter(spell))
            if (m.groupValues[2].isEmpty()) return listOf(counter)
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return unlessPays(Players.ControllerOf(spell), Cost.mana(cost), listOf(counter))
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
                    times = Value.Of(arcanum.api.NumericProperty.ManaValue, spell, arcanum.api.Combine.Single))), players = Players.You)))))))
        }
        ADD_X_ANY_COMBINATION.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Announced(name = "X"), combination = true)), players = Players.You)))
        }
        ADD_ANY_OF_REMEMBERED_COLORS.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1),
                colors = listOf(ColorRef.Of(Objects.Remembered(IMPRINTED))))), players = Players.You)))
        }
        FLIP_A_COIN.matchEntire(sentence)?.let {
            context.flip = true
            return listOf(Step.Do(Effect.FlipCoin(bind = "flip", players = Players.You)))
        }
        IF_YOU_LOSE_THE_FLIP.matchEntire(sentence)?.let { m ->
            if (!context.flip) return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.WonFlip("flip")), then = then))
        }
        SELF_DEALS_DAMAGE_TO_YOU.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.You), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)))
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
                return listOf(Step.Pay(Cost(listOf(CostPart.Mana(cost, x = x))), bind = "paid", payer = Players.You), Step.If(arcanum.api.Condition.Did("paid"), then = then))
            }
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.Pay(Cost.mana(cost), bind = "paid", payer = Players.You), Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        UNTAP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Untap(Objects.Self)))
        }
        YOU_MAY_EXILE_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember(IMPRINTED, Objects.Bound("exiled"))))
        }
        ADD_MANA_RESTRICTED.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = Filters.all(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), spells)), players = Players.You)))
        }
        ADD_MANA_FOR_EACH.matchEntire(sentence)?.let { m ->
            val symbols = ManaCost.parse(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(symbols, times = Value.Count(count))), players = Players.You)))
        }
        ADD_MANA.matchEntire(sentence)?.let { m -> mana(m.groupValues[1])?.let { return listOf(Step.Do(Effect.AddMana(ManaProduction(it), players = Players.You))) } }
        DRAW.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))
        }
        // chunk 35: "Create a 1/1 … token for each +1/+1 counter on this creature." (read from the source, its last known
        // information once it has left, CR 113.7a, 608.2h)
        CREATE_FOR_EACH_COUNTER_ON_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            if (count != 1) return null
            // dsk chunk 12: "for each counter on this creature": every kind
            val kind = if (m.groupValues[2].isEmpty()) null else Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single), controller = Players.You)))
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
                tapped = m.groupValues[1].isNotEmpty(), controller = Players.You)))
        }
        // chunk 35: "It deals damage equal to its power to any target." (the source's power, CR 113.7a)
        DEALS_DAMAGE_EQUAL_TO_ITS_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            val amount = Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single)
            val to = if (m.groupValues[2] == "any target") { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                else arcanum.api.Recipients(players = Filters.players(m.groupValues[2]) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
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
                    Step.Do(Effect.Search(player, filter, max = Value.Fixed(n), bind = "found", searcher = player, zones = listOf(Zone.Library))),
                    Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                    Step.Do(Effect.Shuffle(player)))))))
        }
        CREATE_TOKEN.matchEntire(sentence)?.let { m ->
            // chunk 42: "a tapped 4/4 green Beast creature token"
            // blb chunk 01: "… token that's tapped and attacking" (CR 508.4: its controller chooses what it attacks)
            val attacking = Blb.TAPPED_AND_ATTACKING.find(m.groupValues[1])
            val printed = attacking?.let { m.groupValues[1].removeRange(it.range) } ?: m.groupValues[1]
            val tokens = TAPPED_TOKENS.replaceFirst(printed, "$1 ")
            val (count, token) = Tokens.spec(tokens) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), tapped = tokens != printed || attacking != null, controller = Players.You,
                attacking = attacking?.let { arcanum.api.AttackTarget.Chosen })))
        }
        THAT_PLAYER_MAY_SEARCH.matchEntire(sentence)?.let { m ->
            val player = context.lastTargetController() ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.May(player = player, steps = listOf(
                Step.Do(Effect.Search(player, filter, bind = "found", searcher = player, max = Value.Fixed(1), zones = listOf(Zone.Library))),
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
                listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.Is(Objects.EventObject(arcanum.api.EventRole.After)), count = Value.Fixed(1)), from = Zone.Graveyard,
                    postBind = "exiled"))
            }
            // blb gaps: "you may exile it" about a permanent the caller named: a cost on resolution, "it" is then the exiled card
            val exileIt = context.it?.takeIf { action == "Exile it" && context.itZone == Zone.Battlefield }?.let { it ->
                context.it = Objects.Bound("exiled")
                listOf(CostPart.Exile(CostObjects.These(it), from = Zone.Battlefield, postBind = "exiled"))
            }
            val parts = them ?: exileIt ?: costPart(action) ?: exileThatCard(action, context) ?: action.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) }
                .takeIf { it.size > 1 && it.all { p -> p != null } }?.flatMap { it!! }
            val pay = if (parts != null) Step.Pay(Cost(parts), bind = "paid", payer = Players.You)
                else Step.Pay(steps = effect("$action.", context) ?: return null, bind = "paid", payer = Players.You)
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
            val exile = Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile, faceDown = m.groupValues[1].isNotEmpty()),
                postBind = "exiled"))
            return if (linkedExile) listOf(exile, Step.Do(Effect.Remember(EXILED_WITH, Objects.Bound("exiled")))) else listOf(exile)
        }
        // chunk 55: "Shuffle your library, then exile the top card." ("the top card" of that library)
        if (sentence == "Shuffle your library, then exile the top card.") {
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Shuffle(Players.You)),
                Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")))
        }
        if (sentence == "You may play that card for as long as it remains exiled.") {
            // CR 400.7: once it leaves exile it is a new object, so the permission has no other end
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 26: "Each opponent loses 1 life."
        PLAYERS_LOSE_LIFE.matchEntire(sentence)?.let { m ->
            playersIn(m.groupValues[1], context)?.let { return listOf(Step.Do(Effect.LoseLife(it, Value.Fixed(m.groupValues[2].toInt())))) }
        }
        // chunk 26: "This enchantment deals that much damage to each opponent."
        DEALS_THAT_MUCH.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val players = Filters.players(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = players), context.thatMuch ?: return null, source = Objects.Self)))
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
            val objects = when {
                isSelf(subject) -> Objects.Self
                // chunk 47: "It gains trample" after an effect on a target: that target
                subject == "It" -> context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: context.it // dsk chunk 10
                // dsk chunk 07: "that creature gains double strike" after a target
                subject == "That creature" -> context.that ?: context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) }
                // dsk chunk 13: "Gremlins you control" (a capitalised subtype)
                else -> (Filters.noun(subject.replaceFirstChar { it.lowercase() }) ?: Filters.noun(subject))?.let { Objects.Each(it) }
            }
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
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(card, withoutPaying = m.groupValues[4].isNotEmpty(), bind = "spell", how = PlayKind.Cast, caster = Players.You))), player = Players.You))
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
            return listOf(Step.Do(Effect.CreateEmblem(listOf(ability), players = Players.You)))
        }
        // chunk 26: "Target artifact you control becomes a copy of another target artifact or creature you control until end
        // of turn, except it's an artifact in addition to its other types." (CR 707.9b)
        BECOMES_COPY.matchEntire(sentence)?.let { m ->
            val first = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            val name = Filters.slotName(m.groupValues[2])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[2]) ?: return null),
                distinctFrom = listOf((first as Objects.Target).slot), min = Value.Fixed(1), max = Value.Fixed(1))
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
            return listOf(Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), bind = "revealed")))
        }
        // chunk 21: "If it's a land card, create a Treasure token."
        IF_ITS_A.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, filter), then = then))
        }
        PUT_IT_INTO.matchEntire(sentence)?.let { m ->
            val zone = if (m.groupValues[2] == "hand") Zone.Hand else Zone.Graveyard
            // chunk 36: "that card"; chunk 47: "put that card into your hand" is "it"
            // chunk 50: in an activated ability, "it" is the source (no event, no target)
            val source = Objects.Self.takeIf { m.groupValues[1] == "it" && context.event == null && context.targets.isEmpty() && !isSpell }
            if (m.groupValues[1] != "them") return listOf(Step.Do(Effect.Move(context.it ?: source ?: return null, Destination(zone))))
            context.thatMuch = Value.Count(Objects.Bound("moved"))
            return listOf(Step.Do(Effect.Move(context.them ?: return null, Destination(zone), postBind = "moved")))
        }
        ADD_AS_SACRIFICED_COULD.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.AsCouldProduce(ObjectFilter.Is(Objects.Bound(SACRIFICED)), amount = Value.Fixed(1))), players = Players.You)))
        }
        // chunk 20: "It deals 2 damage to any target." before any target is named: "it" is the source itself.
        if (sentence.startsWith("It deals ") && context.targets.isEmpty()) effect("This card" + sentence.removePrefix("It"), context)?.let { return it }
        // chunk 20: "Attach this Equipment to target creature you control." (CR 701.3)
        // dsk chunk 13: "attach it to …" in this object's enters trigger
        ATTACH_SELF_TO_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) ||
            it.groupValues[1] == "it" && context.event == EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield) }?.let { m ->
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
            // dsk chunk 07: "… with a finality counter on it" (CR 122.6)
            val counters = it.groupValues[3].takeIf { c -> c.isNotEmpty() }?.let { c -> listOf(arcanum.api.CountersToPut(Counters.kind(c) ?: return null, Value.Fixed(1))) }
            if (counters != null) return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = it.groupValues[2].isNotEmpty(), counters = counters))))
            context.it = Objects.Bound("returned")
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = it.groupValues[2].isNotEmpty()), postBind = "returned")))
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
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(context.targetPlayer(), Value.Fixed(n), fromTop = true), players = Players.You)))
        }
        // chunk 20: "Look at a card at random in target player's hand."
        if (sentence == "Look at a card at random in target player's hand.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.LookAt(Objects.Random(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.Is(player)))), count = Value.Fixed(1)), players = Players.You)))
        }
        // chunk 20: "Draw a card at the beginning of the next turn's upkeep.": a delayed trigger (CR 603.7)
        AT_NEXT_UPKEEP.matchEntire(sentence)?.let { m ->
            val inner = Context()
            val steps = effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", inner)?.takeIf { inner.targets.isEmpty() } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, whose = PlayerFilter.Any)),
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
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))))
        }
        // chunk 21: "Put a charge counter on this artifact." / "Put a +1/+1 counter on target creature you control."
        PUT_COUNTERS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val on = m.groupValues[3]
            val objects = when {
                isSelf(on) -> Objects.Self
                // chunk 36: "on it"
                on == "it" -> context.it
                on.startsWith("target ") -> on.removePrefix("target ").let { context.target(Filters.slotName(it), nounIn(it, context) ?: return null) }
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
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Remembered(EXILED_WITH)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Graveyard), postBind = "moved")))
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
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
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
            val spells = Value.EventsThisTurn(EventPattern.Cast(by = PlayerFilter.You, spell = ObjectFilter.Any), measure = Measure.Events)
            val n = m.groupValues[2].toInt()
            return listOf(Step.Do(Effect.GainLife(players, if (n == 1) spells else Value.Times(spells, n))))
        }
        // chunk 21: "An opponent gains control of this artifact." (the opponent is chosen on resolution)
        OPPONENT_GAINS_CONTROL.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(
                Step.Choose(arcanum.api.Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("opponent"))),
                    arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 56: "Exile the top two cards of your library." ("those cards")
        EXILE_TOP_N.matchEntire(sentence)?.let { m ->
            // blb gaps: "the top X cards", X defined by the ability ("where X is …", CR 107.3c)
            val n = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            context.them = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n, fromTop = true), Destination(Zone.Exile), postBind = "exiled")))
        }
        // chunk 56: "You may play those cards this turn." (lands too, CR 305.1)
        if (sentence == "You may play those cards this turn.") {
            val them = context.them ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(them), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56
        TARGET_OPPONENT_MILLS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(context.targetOpponent(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // chunk 56: "Up to one target creature gets -2/-0 until your next turn."
        UP_TO_ONE_TARGET_GETS.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            val duration = if (m.groupValues[4] == "your next turn") arcanum.api.Duration.UntilYourNextTurn else arcanum.api.Duration.EndOfTurn
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Target(name)), duration)))
        }
        // chunk 56: "Target creature gets +1/+1 until end of turn for each artifact and/or enchantment you control." (counted
        // once, as it resolves, CR 608.2h)
        TARGET_GETS_FOR_EACH_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            val per = Value.Count(Filters.objects(m.groupValues[4]) ?: return null)
            fun amount(n: Int): Value = when (n) { 0 -> Value.Fixed(0); 1 -> per; else -> Value.Times(per, n) }
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[2].toInt()), amount(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56: "You may cast target instant or sorcery card from your graveyard this turn.": a permission for the turn
        MAY_CAST_TARGET_FROM_GRAVEYARD_THIS_TURN.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val card = context.target("card", filter)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You)), arcanum.api.Affected.Objects(card),
                arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56: "Exile Jace, then return him to the battlefield transformed under his owner's control." (a new object,
        // back face up, CR 400.7, 712.14a)
        EXILE_SELF_RETURN_TRANSFORMED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val controller = if (m.groupValues[2] == "your") Players.You else Players.OwnerOf(Objects.Bound("exiled"))
            return listOf(
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, transformed = true, controller = controller))))
        }
        // chunk 56: "Transform this creature." / "transform it" (CR 701.27)
        TRANSFORM.matchEntire(sentence)?.let { m ->
            val objects = if (isSelf(m.groupValues[1])) Objects.Self else if (m.groupValues[1] == "it") context.it ?: return null else return null
            return listOf(Step.Do(Effect.Transform(objects)))
        }
        // chunk 56: "Return all non-Horror creatures to their owners' hands."
        RETURN_ALL_TO_HANDS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Move(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null), Destination(Zone.Hand))))
        }
        // chunk 56: "Up to one target Blood token you control becomes a 2/2 black Bat creature with flying and haste in
        // addition to its other types." No duration: for good (CR 611.2a). Layers 4, 5, 6, 7b (CR 613.1).
        BECOMES_CREATURE.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            val upTo = m.groupValues[1] == "Up to one target"
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(if (upTo) 0 else 1), max = Value.Fixed(1))
            val color = BECOME_COLORS[m.groupValues[5]] ?: return null
            val subtype = m.groupValues[6].takeIf { it in RulesWords.subtypes } ?: return null
            val keywords = m.groupValues[7].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
            val mechanics = listOf<Mechanic>(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetColors(listOf(ColorRef.Literal(color)))) +
                (if (keywords.isEmpty()) emptyList() else listOf(Mechanic.GainAbilities(keywords))) +
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[3].toInt()), Value.Fixed(m.groupValues[4].toInt()))
            return listOf(Step.Do(Effect.ApplyMechanics(mechanics, arcanum.api.Affected.Objects(Objects.Target(name)), arcanum.api.Duration.Indefinitely)))
        }
        // chunks 31, 47: "You may exile it. When you do, [reflexive trigger]." (CR 603.12)
        YOU_MAY_WHEN_YOU_DO.matchEntire(sentence)?.let { m ->
            // dsk chunk 12: "You may discard a card or pay {2}. When you do, …": one of two costs
            val options = m.groupValues[1].split(Regex(""" or (?=(?:sacrifice|pay|discard|exile|tap) )"""))
            val either = if (options.size < 2) null else options.map { Cost(costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return@let) }
            (either?.let { listOf(CostPart.OneOf(it)) } ?: costPart(m.groupValues[1].replaceFirstChar { it.uppercase() }))?.let { parts ->
                val reflexive = instructions(m.groupValues[2].replaceFirstChar { it.uppercase() }) ?: return null
                return listOf(Step.Pay(Cost(parts), bind = "paid", payer = Players.You),
                    Step.If(arcanum.api.Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(reflexive)))))
            }
        }
        // chunks 30–33
        enters(sentence, context)?.let { return it }
        // chunk 40: "Until end of turn, each Elf creature you control has base power and toughness 5/5 and becomes a Dinosaur
        // in addition to its other creature types." / "… creatures you control have base power and toughness X/X and gain
        // all creature types." (layers 4 and 7b, CR 613.4b; the affected set is fixed as it resolves, CR 611.2c)
        UNTIL_EOT_BASE_PT.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removePrefix("each ")) ?: return null
            val x = if (isSpell || announcedX) Value.Announced(name = "X") else Value.CastX(of = Objects.Self)
            fun pt(text: String): Value = if (text == "X") x else Value.Fixed(text.toInt())
            val mechanics = mutableListOf<Mechanic>(Mechanic.SetPowerToughness(pt(m.groupValues[2]), pt(m.groupValues[3])))
            when (val also = m.groupValues[4]) {
                "" -> {}
                "gain all creature types", "gains all creature types" -> mechanics += Mechanic.AllCreatureTypes
                else -> {
                    val type = BECOMES_ADDITIONAL_TYPE.matchEntire(also)?.groupValues?.get(1)?.takeIf { it in RulesWords.subtypes } ?: return null
                    mechanics += Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(type))))
                }
            }
            return listOf(Step.Do(Effect.ApplyMechanics(mechanics, arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 40: "This creature gets +2/+2 until end of turn."
        SELF_GETS_UNTIL_EOT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 40: "This creature can't be blocked this turn except by creatures with haste." (blockers are always creatures,
        // so the filter names only the keyword, as flying's does)
        CANT_BE_BLOCKED_EXCEPT_BY_THIS_TURN.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val keyword = OracleParser.keyword(m.groupValues[2].replaceFirstChar { it.uppercase() }) ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.HasKeyword(keyword.kind))),
                arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 40: "Tap or untap another target permanent.": one of the two, chosen as it resolves
        TAP_OR_UNTAP.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun),
                if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("tapOrUntap", "tap"),
                    then = listOf(Step.Do(Effect.Tap(target))), otherwise = listOf(Step.Do(Effect.Untap(target)))))
        }
        // chunk 40: "Another target creature you control gains protection from colorless or from the color of your choice
        // until end of turn." (chosen as it resolves)
        GAINS_PROTECTION_OF_CHOICE.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun),
                if (m.groupValues[1] == "Target") filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))
            fun protection(from: ObjectFilter) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Protection(from)))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn))
            val color = listOf(Step.Choose(arcanum.api.Choice.Color(), bind = "color", chooser = Players.You), protection(ObjectFilter.OfColor(ColorRef.Chosen("color"))))
            if (m.groupValues[3].isEmpty()) return color
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("colorless", "a color")), bind = "quality", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("quality", "colorless"), then = listOf(protection(ObjectFilter.Colorless)), otherwise = color))
        }
        // chunk 40: "Change a target of target spell or ability to this creature." (CR 115.7b)
        CHANGE_A_TARGET_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val spell = context.target("spell", Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ChangeTargets(spell, optional = false, to = Objects.Self, chooser = Players.You)))
        }
        // chunk 41: "Choose target artifact a player controls and target artifact card in that player's graveyard."
        CHOOSE_TARGET_AND_CARD_IN_THAT_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val permanent = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            context.target("card", Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.ControllerOf(permanent)))))
            return emptyList()
        }
        // chunk 41: "If both targets are still legal as this ability resolves, that player simultaneously sacrifices the artifact
        // and returns the artifact card to the battlefield." (CR 608.2b; one event, CR 608.2c)
        BOTH_LEGAL_SACRIFICE_AND_RETURN.matchEntire(sentence)?.let {
            val slots = context.targets.takeLast(2).map { it.name }.takeIf { it.size == 2 } ?: return null
            val (permanent, card) = slots
            return listOf(Step.If(arcanum.api.Condition.TargetsLegal(slots), then = listOf(Step.Simultaneously(listOf(
                Step.Do(Effect.Sacrifice(Objects.Target(permanent))),
                Step.Do(Effect.Move(Objects.Target(card), Destination(Zone.Battlefield, controller = Players.ControllerOf(Objects.Target(permanent))))))))))
        }
        // chunk 41: "If it was a permanent card, put a +1/+1 counter on this permanent."
        IF_IT_WAS.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, filter), then = then))
        }
        // chunk 41: "Return a tapped land you control to its owner's hand." (chosen as it resolves, not targeted)
        RETURN_CHOSEN_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // chunk 42: "Target opponent reveals the top X cards of their library."
        TARGET_REVEALS_TOP.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            val n = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            context.player = player
            context.revealed = Objects.Bound("revealed")
            return listOf(Step.Do(Effect.Reveal(Objects.LibraryCards(player, n, fromTop = true), bind = "revealed")))
        }
        // chunk 42: "You may put a nonland permanent card with mana value X or less from among them onto the battlefield under
        // your control.": zero or one of them
        YOU_MAY_PUT_FROM_AMONG_THEM.matchEntire(sentence)?.let { m ->
            val from = context.revealed ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            context.chosen += "chosen"
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = if (m.groupValues[2].isEmpty()) null else Players.You))))
        }
        // chunk 42: "That player puts the rest on the bottom of their library in a random order."
        if (sentence == "That player puts the rest on the bottom of their library in a random order.") {
            val from = context.revealed ?: return null
            val chosen = context.chosen.lastOrNull() ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Except(from, Objects.Bound(chosen)), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // chunk 42: "Any number of target players each lose 2 life and sacrifice a creature of their choice." Each player
        // chooses, then all sacrifice at once (CR 101.4, 608.2e).
        TARGET_PLAYERS_LOSE_AND_SACRIFICE.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[2]) ?: return null
            context.targets += TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(0), max = null)
            val players = Players.Target("players")
            val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))
            return listOf(
                Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[1].toInt()))),
                Step.ForEach(arcanum.api.Each.Players(players), bind = "player", together = true, steps = listOf(
                    Step.Choose(arcanum.api.Choice.Objects(Filters.all(kind, theirs), Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed",
                        chooser = Players.Bound("player")),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))))
        }
        // chunk 42: "Create a tapped 4/4 green Beast creature token and untap this creature."
        CREATE_AND.matchEntire(sentence)?.let { m ->
            val first = effect("Create " + m.groupValues[1] + ".", context)
            val second = first?.let { effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() } + ".", context) }
            if (first != null && second != null) return first + second
        }
        // chunk 42: "You gain 1 life and draw a card.", "You add {B}{B} and draw a card."
        YOU_AND.matchEntire(sentence)?.let { m ->
            fun part(text: String) = effect("You $text.", context) ?: effect(text.replaceFirstChar { c -> c.uppercase() } + ".", context)
            val first = part(m.groupValues[1])
            val second = first?.let { part(m.groupValues[2]) }
            if (first != null && second != null) return first + second
        }
        // chunk 45: "Remove a time counter from this card." (chunk 56: "it" afterwards is this object)
        REMOVE_COUNTERS_FROM_SELF.matchEntire(sentence.removeSuffix("."))?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            context.it = Objects.Self
            return listOf(Step.Do(Effect.RemoveCounters(Objects.Self, kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // chunk 46: "Bolster 1." (CR 701.39a)
        BOLSTER.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Bolster(Value.Fixed(m.groupValues[1].toInt()))))) }
        // chunk 47: "This creature connives." (CR 701.50a)
        CONNIVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Connive(objects = Objects.Self, count = Value.Fixed(1)))))
        }
        // chunk 47: "Put that many +1/+1 counters on this creature." after discarding one or more cards: one per card
        PUT_THAT_MANY_COUNTERS.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) && context.event is EventPattern.Discard }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(Objects.Self, kind, Value.Count(Objects.EventObject(arcanum.api.EventRole.After)))))
        }
        // chunk 47: "You lose life equal to its mana value." (the card the ability revealed)
        YOU_LOSE_LIFE_EQUAL_TO_ITS.matchEntire(sentence)?.let { m ->
            val it = (context.it as? Objects.Bound) ?: return null
            val property = if (m.groupValues[1] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.ManaValue
            return listOf(Step.Do(Effect.LoseLife(Players.You, Value.Of(property, it, arcanum.api.Combine.Single))))
        }
        // chunk 47: "You may play that card this turn." / "… until your next end step." (the exiled card)
        YOU_MAY_PLAY_THAT_CARD.matchEntire(sentence)?.let { m ->
            // blb gaps: "You may play those cards until your next end step." (the cards exiled before)
            val exiled = (if (m.groupValues[1] == "those cards") context.them else context.it) as? Objects.Bound ?: return null
            val duration = if (m.groupValues[2] == "this turn") arcanum.api.Duration.EndOfTurn
                else arcanum.api.Duration.UntilStep(arcanum.api.TurnStep.End, PlayerFilter.You)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(exiled), duration)))
        }
        // chunk 47: "If you win the flip, …"
        IF_YOU_WIN_THE_FLIP.matchEntire(sentence)?.let { m ->
            if (!context.flip) return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.WonFlip("flip"), then = then))
        }
        // chunk 47: "Return that spell to its owner's hand." (the event's object)
        RETURN_THAT_TO_OWNERS_HAND.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.Move(context.that ?: return null, Destination(Zone.Hand))))
        }
        // chunk 47: "Copy that spell, and you may choose new targets for the copy." (CR 707.10c)
        COPY_THAT_SPELL.matchEntire(sentence)?.let {
            context.copied = true
            return listOf(Step.Do(Effect.CopySpell(context.that ?: return null, controller = Players.You, newTargets = true)))
        }
        // chunk 47: "Draw a card unless that player pays {X}, where X is this creature's power." (CR 118.12a)
        UNLESS_THAT_PLAYER_PAYS.matchEntire(sentence)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[2]) ?: return null
            val x = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { selfNumber(it) ?: return null }
            val steps = effect(m.groupValues[1] + ".", context) ?: return null
            return unlessPays(Players.EventPlayer, Cost(listOf(CostPart.Mana(mana, x = x))), steps)
        }
        // chunk 46: "Until your next turn, target player who was dealt combat damage by Hope of Ghirapur this turn can't cast
        // noncreature spells."
        UNTIL_YOUR_NEXT_TURN_CANT_CAST.matchEntire(sentence)?.let { m ->
            val source = m.groupValues[1]
            if (source.isNotEmpty() && !isSelf(source)) return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            val filter = if (source.isEmpty()) PlayerFilter.Any else PlayerFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self),
                arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true))
            context.targets += TargetSlot("player", TargetKind.Players(filter), min = Value.Fixed(1), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(spells)),
                arcanum.api.Affected.Players(Players.Target("player")), arcanum.api.Duration.UntilYourNextTurn)))
        }
        // chunks 52, 56: "If [condition], [effect]." (checked as it resolves)
        IF_CONDITION.matchEntire(sentence)?.let { m ->
            val condition = itCondition(m.groupValues[1], context) ?: Conditions.condition(m.groupValues[1], self)
            val then = condition?.let { effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) }
            if (condition != null && then != null) return listOf(Step.If(condition, then = then))
        }
        // chunk 21: "You may [do something]." with nothing depending on it (CR 608.2d)
        // chunk 51: "Target creature you control gains shroud until end of turn."
        TARGET_GAINS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[3].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val noun = Filters.noun(m.groupValues[2]) ?: return null
            // dsk chunk 12: "another target creature you control"
            val target = context.target(Filters.slotName(m.groupValues[2]),
                if (m.groupValues[1] == "Target") noun else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), noun))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)), arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 50: "Sacrifice [this creature]."
        SACRIFICE_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Sacrifice(Objects.Self)))
        }
        // chunk 50: "[Do X] unless you [pay a cost]." (CR 118.12a)
        UNLESS_YOU.matchEntire(sentence)?.let { m ->
            costPart(m.groupValues[2].replaceFirstChar { it.uppercase() })?.let { parts ->
                effect(m.groupValues[1] + ".", context)?.let { return unlessPays(Players.You, Cost(parts), it) }
            }
        }
        // chunk 52: "Reveal cards from the top of your library until you reveal an Elf or Elemental card." (CR 701.20)
        REVEAL_UNTIL.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.Do(Effect.RevealUntil(filter, foundBind = FOUND, restBind = REST, library = Players.You, count = Value.Fixed(1), reveal = true)))
        }
        // chunk 52: "Draw that many cards." (the cards of a discard batch)
        if (sentence == "Draw that many cards." && context.event is EventPattern.Discard) {
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Count(context.that ?: return null))))
        }
        // chunk 52: "That player reveals the chosen card, puts it on the bottom of their library, then draws a card."
        if (sentence == "That player reveals the chosen card, puts it on the bottom of their library, then draws a card.") {
            val player = context.player ?: return null
            val card = Objects.Bound(context.chosen.lastOrNull() ?: return null)
            return listOf(Step.Do(Effect.Reveal(card)), Step.Do(Effect.Move(card, Destination(Zone.Library, libraryPosition = -1))),
                Step.Do(Effect.Draw(player, Value.Fixed(1))))
        }
        YOU_MAY.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context)?.let { return listOf(Step.May(it, player = Players.You)) }
        }
        // chunk 36
        if (sentence == "Discard your hand.") return listOf(Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand), count = Value.Fixed(1), filter = ObjectFilter.Any)))
        // "Put all cards exiled with this creature into their owners' hands." (CR 607.2a)
        PUT_ALL_EXILED_WITH_INTO_HANDS.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Remembered(EXILED_WITH), Destination(Zone.Hand))))
        }
        // "You may cast it this turn." (a permission for the exiled card, CR 601.2a)
        if (sentence == "You may cast it this turn." && context.it == Objects.Bound("exiled")) {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Cast, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        // "Sacrifice another permanent.": the controller chooses one they control (CR 701.21a)
        SACRIFICE_A.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[2]) ?: return null
            val another = if (m.groupValues[1] == "another") listOf(context.another()) else emptyList()
            val filter = Filters.all(*(another + listOf(kind, ObjectFilter.ControlledBy(PlayerFilter.You))).toTypedArray())
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "sacrificed", chooser = Players.You),
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
            // dsk chunk 04: linked to "cards exiled with this creature" (CR 607.2a)
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Exile), postBind = "exiled"))) +
                (if (linkedExile || exiledCard == Objects.Remembered(EXILED_WITH)) remembered(Objects.Bound("exiled")) else emptyList())
        }
        // "At the beginning of the next end step, [effect].": a delayed trigger (CR 603.7), about the objects named now
        AT_BEGINNING_OF_NEXT_END_STEP.matchEntire(sentence)?.let { m ->
            val targets = context.targets.size
            val inner = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context)?.takeIf { context.targets.size == targets } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = inner))))
        }
        // "Return that card to the battlefield under its owner's control."
        RETURN_IT_UNDER_OWNERS_CONTROL.matchEntire(sentence)?.let { m ->
            val card = context.it ?: return null
            // dsk chunk 10: "… tapped under its owner's control with two stun counters on it"
            val counters = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { c ->
                listOf(arcanum.api.CountersToPut(Counters.kind(c) ?: return null, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))) }.orEmpty()
            if (m.groupValues[1].isNotEmpty() || counters.isNotEmpty())
                return listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield, tapped = m.groupValues[1].isNotEmpty(), controller = Players.OwnerOf(card), counters = counters))))
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
        // "[Effect] unless [condition]." (not "unless [player] pays", which is a pay, see unlessPays)
        UNLESS_CONDITION.matchEntire(sentence)?.let { m ->
            Conditions.condition(m.groupValues[2], self)?.let { condition ->
                val then = effect(m.groupValues[1] + ".", context) ?: return null
                return listOf(Step.If(arcanum.api.Condition.Not(condition), then = then))
            }
        }
        // dsk chunk 04: "Draw a card if you control a Glimmer creature." (checked as it resolves)
        EFFECT_IF_CONDITION.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self)
            val targets = context.targets.size
            val then = condition?.let { effect(m.groupValues[1] + ".", context) }
            if (condition != null && then != null) return listOf(Step.If(condition, then = then))
            while (context.targets.size > targets) context.targets.removeAt(context.targets.lastIndex)
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

    /** chunk 47: "Exile that card from your graveyard" as a cost paid on resolution (the discarded card, CR 118.12); "that
     * card" afterwards is the exiled card. */
    private fun exileThatCard(action: String, context: Context): List<CostPart>? {
        if (action != "Exile that card from your graveyard") return null
        val that = context.that ?: return null
        context.it = Objects.Bound("exiled")
        return listOf(CostPart.Exile(CostObjects.These(that), from = Zone.Graveyard, postBind = "exiled"))
    }

    /** dsk chunk 02: what mana may be spent on ("cast Room spells and unlock doors", "cast an enchantment spell, unlock a door,
     * or turn a permanent face up"): spells, and the special actions of unlocking (CR 709.5e) and turning face up (116.2b). */
    private fun spendOnlyTo(text: String): ObjectFilter? {
        // blb gaps 2: "cast creature spells with mana value 4 or greater or creature spells with {X} in their mana costs": two
        // kinds of spell, each with its own "with"
        BlbGaps2.CAST_SPELLS_WITH_OR_WITH.matchEntire(text)?.let { m ->
            val first = Filters.noun(m.groupValues[1] + " " + m.groupValues[2]) ?: return null
            val second = Filters.noun(m.groupValues[3] + " " + m.groupValues[4]) ?: return null
            val spell = ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)
            return ObjectFilter.Or(listOf(Filters.all(spell, first), Filters.all(spell, second)))
        }
        val uses = text.split(Regex(""",? or |,? and |, """)).map { use ->
            when (use) {
                "unlock doors", "unlock a door" -> ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Unlock)
                "turn permanents face up", "turn a permanent face up" -> ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.TurnFaceUp)
                // as "Spend this mana only to cast artifact spells" reads (chunk 03)
                else -> CAST_SPELLS.matchEntire(use)?.let { m -> Filters.noun(m.groupValues[1])?.let { Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), it) } } ?: return null
            }
        }
        return uses.singleOrNull() ?: ObjectFilter.Or(uses)
    }

    /** dsk chunk 05: "Enchanted creature", "Equipped creature": the object this Aura or Equipment is attached to. */
    private fun attachedSubject(text: String): Objects? =
        if (text == "Enchanted creature" || text == "Equipped creature" || text == "enchanted creature") Objects.AttachedTo(Objects.Self) else null

    /** dsk chunk 01: "it's a Reflection in addition to its other creature types" (CR 707.9b). */
    private fun copyExceptions(text: String): List<arcanum.api.CopyException>? {
        // dsk chunk 13: "it's a 1/1 red Balloon creature in addition to its other colors and types and it has flying and haste"
        PT_COLOR_TYPE_IN_ADDITION.matchEntire(text)?.let { m ->
            val color = Tokens.color(m.groupValues[3]) ?: return null
            val subtype = m.groupValues[4].takeIf { it in RulesWords.subtypes } ?: return null
            val keywords = m.groupValues[5].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }
            return listOfNotNull(
                arcanum.api.CopyException.PowerToughness(m.groupValues[1].toInt(), m.groupValues[2].toInt()),
                arcanum.api.CopyException.Colors(listOf(ColorRef.Literal(color)), add = true),
                arcanum.api.CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                keywords?.let { arcanum.api.CopyException.AddAbilities(it) },
            )
        }
        ADDITIONAL_CREATURE_TYPE.matchEntire(text)?.let { m ->
            val subtype = m.groupValues[1].takeIf { it in RulesWords.subtypes } ?: return null
            return listOf(arcanum.api.CopyException.Types(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))))
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

    /** chunk 56: a condition about "it", the object an earlier sentence acted on ("it has no ice counters on it"). */
    private fun itCondition(text: String, context: Context): arcanum.api.Condition? {
        // blb chunk 02: "that creature isn't legendary", "that creature is a Bird, Frog, Otter, or Rat"
        Blb.IT_IS.matchEntire(text)?.let { m ->
            val target = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) }
            // blb chunk 13: "that creature" after a target names the target, not the trigger's event object
            val event = context.it is Objects.EventObject
            val it = (if (text.startsWith("that creature ") && event) target ?: context.it else context.it ?: target) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return arcanum.api.Condition.ObjectMatches(it, if (m.groupValues[1] == "is") filter else ObjectFilter.Not(filter))
        }
        val it = context.it ?: return null
        val m = IT_HAS_NO_COUNTERS.matchEntire(text) ?: return null
        val kind = Counters.kind(m.groupValues[1]) ?: return null
        return arcanum.api.Condition.ObjectMatches(it, ObjectFilter.Not(ObjectFilter.HasCounter(kind, atLeast = 1)))
    }

    /** Keeps [objects] as cards "exiled with" this object when the card refers to them (CR 607.2a). */
    // chunk 21
    private fun remembered(objects: Objects): List<Step> =
        if (linkedExile) listOf(Step.Do(Effect.Remember(EXILED_WITH, objects))) else emptyList()

    /** chunks 30–33: effect wordings of creatures' enters (and leaves) abilities; null when none reads [sentence]. */
    private fun enters(sentence: String, context: Context): List<Step>? {
        // chunk 30
        LOSE_LIFE_AND_YOU_GAIN.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[2].toInt()))))
            if (m.groupValues[3].isNotEmpty()) steps += Step.Do(Effect.GainLife(Players.You, Value.Fixed(m.groupValues[3].toInt())))
            return steps
        }
        PLAYERS_DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Filters.players(m.groupValues[1]) ?: return null, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null), filter = ObjectFilter.Any)))
        }
        if (sentence == "Discard your hand.") return listOf(Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand), count = Value.Fixed(1), filter = ObjectFilter.Any)))

        // chunk 31
        RETURN_ALL_OR_SELF_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
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
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty()))))
        }
        PUT_IT.matchEntire(sentence)?.let { m ->
            val to = if (m.groupValues[1] == "into your hand") Destination(Zone.Hand) else Destination(Zone.Battlefield)
            return listOf(Step.Do(Effect.Move(context.it ?: return null, to)))
        }
        LOOK_PUT_UP_TO_ONE_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n, fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Library, libraryPosition = 0))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        LOOK_OR_REVEAL_TOP.matchEntire(sentence)?.let { m ->
            // dsk chunk 04: "the top X cards" with X defined by the ability ("where X is its power")
            val n = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            context.seen = "seen"
            val cards = Objects.LibraryCards(Players.You, n, fromTop = true)
            return listOf(Step.Do(if (m.groupValues[1] == "Look at") Effect.LookAt(cards, bind = "seen", players = Players.You) else Effect.Reveal(cards, bind = "seen")))
        }
        MAY_FROM_AMONG_INTO_HAND.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            context.picked = "chosen"
            return listOfNotNull(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound(seen)), bind = "chosen", chooser = Players.You),
                if (m.groupValues[1] == "reveal") Step.Do(Effect.Reveal(Objects.Bound("chosen"))) else null,
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        PUT_THE_REST.matchEntire(sentence)?.let { m ->
            val rest = Objects.Except(Objects.Bound(context.seen ?: return null), Objects.Bound(context.picked ?: return null))
            val to = if (m.groupValues[1] == "into your graveyard") Destination(Zone.Graveyard)
                else Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
            return listOf(Step.Do(Effect.Move(rest, to)))
        }
        WIN_IF_X_AT_LEAST.matchEntire(sentence)?.let { m ->
            val other = Values.value(m.groupValues[1], exiledCard) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(name = "X"), arcanum.api.Comparison.AtLeast, other),
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
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(context.itZone ?: return null, how = PlayKind.Cast, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(context.it ?: return null), arcanum.api.Duration.EndOfTurn)))
        }
        GRAVEYARD_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            context.targets += TargetSlot("player", TargetKind.Players(PlayerFilter.Any),
                min = Value.Fixed(if (m.groupValues[1] == "Target") 1 else 0), max = Value.Fixed(1))
            val graveyard = Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))
            return listOf(Step.Do(Effect.Move(Objects.Each(graveyard), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }

        // chunk 32
        RETURN_YOURS_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        TARGET_DEALS_DAMAGE_TO_ITSELF.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = target), Value.Fixed(m.groupValues[2].toInt()), source = target)))
        }
        UNTAP_ANOTHER_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val chosen = if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)
            return listOf(Step.Do(Effect.Untap(context.target(Filters.slotName(noun), chosen))))
        }
        GAIN_AND_GET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(keywords), Mechanic.ModifyPowerToughness(xChange(m.groupValues[3]) ?: return null, xChange(m.groupValues[4]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
        }
        CREATE_COPY_OF_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val chosen = if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)
            // dsk chunk 01: ", except it's a Reflection in addition to its other creature types" (CR 707.9b)
            val except = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { copyExceptions(it) ?: return null }.orEmpty()
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(context.target(Filters.slotName(noun), chosen), except), count = Value.Fixed(1), controller = Players.You)))
        }

        // chunk 33
        EACH_SACRIFICES.matchEntire(sentence)?.let { m ->
            val player = m.groupValues[1]
            val who = if (player == "player") PlayerFilter.Any else PlayerFilter.Opponent
            val filter = Filters.all(Filters.noun(m.groupValues[3]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound(player))))
            // dsk chunk 10: "sacrifices X creatures of their choice"
            val n = when (val c = m.groupValues[2]) { "a", "an" -> Value.Fixed(1); "X" -> Value.Announced(name = "X"); else -> Value.Fixed(Numbers.count(c) ?: return null) }
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(who)), bind = player, together = true, steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = n, max = n), bind = "sacrificed", chooser = Players.Bound(player)),
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
                Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(role = EventRole.Object), newTargets = false, controller = Players.You)))),
                duration = arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "Look at target opponent's hand.") {
            return listOf(Step.Do(Effect.LookAt(Objects.Each(handOf(context.targetOpponent())), players = Players.You)))
        }
        TARGET_REVEALS_HAND_YOU_CHOOSE.matchEntire(sentence)?.let { m ->
            val opponent = context.targetOpponent()
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, handOf(opponent))
            context.it = Objects.Bound("chosen")
            return listOf(
                Step.Do(Effect.Reveal(Objects.Each(handOf(opponent)))),
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You))
        }
        if (sentence == "Exile that card.") return exile(context.it ?: return null)
        MAY_EXILE_FROM_IT_UNTIL_LEAVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, handOf(context.lastPlayer ?: return null))
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
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
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(exiled, withoutPaying = true, caster = owner, how = PlayKind.Cast))), player = owner))
        }
        // An exile no card memory needs is read by EXILE_UP_TO_TARGET, which names it for "that card" later.
        UP_TO_ONE_TARGET.matchEntire(sentence)?.takeIf { it.groupValues[1] == "Destroy" || exiledCard == Objects.Remembered(EXILED_WITH) }?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun) ?: return null
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(if (m.groupValues[2].isEmpty()) filter
                else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)), min = Value.Fixed(0), max = Value.Fixed(1))
            return if (m.groupValues[1] == "Exile") exile(Objects.Target(name)) else listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        DAMAGE_EQUAL_TO_POWER.matchEntire(sentence)?.takeIf { it.groupValues[1] == "It" || isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = "any"),
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single), source = Objects.Self)))
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
        is Step.RepeatWhile -> step.copy(steps = step.steps.map { withNewTargets(it) ?: return null })
        is Step.Do -> when (val e = step.effect) {
            is Effect.CopySpell -> Step.Do(e.copy(newTargets = true))
            is Effect.CreateDelayedTrigger -> Step.Do(e.copy(instructions = e.instructions.copy(steps = e.instructions.steps.map { withNewTargets(it) ?: return null })))
            else -> null
        }
        else -> null
    }

    /** chunk 32: "+3", "-1", "+X": X as announced, defined later by "where X is …" or the spell's X (CR 107.3c, m). */
    private fun xChange(text: String): Value? = when (text) {
        "+X" -> Value.Announced(name = "X")
        "-X" -> Value.Times(Value.Announced(name = "X"), -1)
        else -> text.removePrefix("+").toIntOrNull()?.let { Value.Fixed(it) }
    }

    /** chunk 33: the cards in [player]'s hand. */
    private fun handOf(player: Players) = Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))

    /** chunk 33: exile [objects]; when a later ability refers to "the exiled card", keep them as card memory (CR 607.2a). */
    private fun exile(objects: Objects): List<Step> =
        if (exiledCard == Objects.Remembered(EXILED_WITH)) listOf(
            Step.Do(Effect.Move(objects, Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember(EXILED_WITH, Objects.Bound("exiled"))))
        else listOf(Step.Do(Effect.Move(objects, Destination(Zone.Exile))))

    /** "{C}", "{B}{B}", "{R} or {G}", "one mana of any color", "three mana of any one color" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        // dsk chunk 14: "one mana of the chosen color" (as this permanent entered)
        if (text == "one mana of the chosen color") return ManaKind.Choose(Value.Fixed(1), listOf(ColorRef.Remembered(CHOSEN_COLOR)))
        ANY_COMBINATION.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), combination = true) }
        // blb gaps 2: "X mana of any one color" (X defined by "where X is", read as the instructions are)
        ANY_COLOR.matchEntire(text)?.takeIf { it.groupValues[2] == "X" }?.let { return ManaKind.Choose(Value.Announced(name = "X")) }
        ANY_COLOR.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null)) }
        // blb chunk 12: "one mana of the chosen color"
        if (text == "one mana of the chosen color") return ManaKind.Choose(Value.Fixed(1), listOf(ColorRef.Remembered(CHOSEN_COLOR)))
        ManaCost.parse(text)?.let { return ManaKind.Fixed(it) }
        val colors = text.split(Regex(""",? or |, """)).map { symbol -> ManaCost.parse(symbol)?.symbols?.singleOrNull()
            ?.let { (it as? arcanum.api.ManaSymbol.Colored)?.color } ?: return null }
        return ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })
    }

    /** blb chunk 11: "+X" defined by the ability's own "where X is" (replaced as the instructions are read). */
    private fun xOrChange(text: String): Value? = if (text == "+X") Value.Announced(name = "X") else ptChange(text)

    /** chunk 26: "+1", "-X" in "gets +1/+1": X of the spell, or of the permanent's spell (CR 107.3a, 107.3m). */
    private fun ptChange(text: String): Value? {
        val x = if (isSpell || announcedX) Value.Announced(name = "X") else Value.CastX(of = Objects.Self)
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
                    sentence.startsWith("When you do,") || // chunk 31
                    (sentence.startsWith("Put ") && acc.lastOrNull()?.startsWith("Look at the top") == true) ||
                    // chunk 47: a reflexive trigger ("When you do, …") runs to the end of the ability (CR 603.12)
                    acc.lastOrNull()?.contains(" When you do, ") == true) && acc.isNotEmpty())
                acc[acc.size - 1] = acc.last() + " " + sentence
            else acc += sentence
            acc
        }
    }

    // ---- blb: Bloomburrow wordings (the set's parser tests, sets/blb/BlbChunk*Test.kt)

    /** A noun phrase in a trigger's effect: "creature you control other than that creature" excludes the event's object. */
    // blb chunk 01
    private fun nounIn(phrase: String, context: Context): ObjectFilter? {
        Blb.OTHER_THAN_THAT.matchEntire(phrase)?.let { m ->
            val that = context.that ?: return null
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.Is(that)))
        }
        return Filters.noun(phrase)
    }

    /** blb chunk 01: a player phrase that may name a target ("Target opponent", "target player") as well as "each opponent". */
    private fun playersIn(phrase: String, context: Context): Players? = when (phrase.lowercase()) {
        "target opponent" -> context.targetOpponent()
        "target player" -> context.targetPlayer()
        // blb chunk 05: "they", "that player": the player named before, or the event's player
        "they", "that player" -> context.lastPlayer ?: Players.EventPlayer.takeIf { context.event != null }
        // blb chunk 07: "defending player" in an attack trigger (CR 506.2)
        "defending player" -> (context.event as? EventPattern.Attack)?.let { Players.DefendingPlayerOf(Objects.EventObject(EventRole.Object)) }
        else -> Filters.players(phrase)
    }

    /** blb chunk 02: [sentence] about what the step before moved ("return that card", "put into your graveyard this way"),
     * when the step before named nothing: the move names it ("exiled", "returned", "destroyed"), then the sentence is read
     * with "it"/"this way" meaning it. Null when that doesn't read either. */
    private fun afterMove(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        val last = (steps.lastOrNull() as? Step.Do)?.effect ?: return null
        val (named, bound) = when (last) {
            is Effect.Move -> if (last.postBind != null) return null else {
                val name = when (last.to.zone) { Zone.Exile -> "exiled"; Zone.Battlefield -> "returned"; else -> return null }
                last.copy(postBind = name) to name
            }
            is Effect.Destroy -> if (last.postBind != null) return null else last.copy(postBind = "destroyed") to "destroyed"
            else -> return null
        }
        val (it, thisWay) = context.it to context.thisWay
        context.it = Objects.Bound(bound)
        context.thisWay = Objects.Bound(bound)
        val parsed = effect(sentence, context)
        if (parsed == null) { context.it = it; context.thisWay = thisWay; return null }
        return steps.dropLast(1) + Step.Do(named) + parsed
    }

    /** The Bloomburrow sentences that change the steps before them; null when none reads [sentence]. */
    private fun blbModify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        // blb chunk 12: "Then if you control four or more lands, untap that land." after searching for a land and putting it
        // onto the battlefield
        Blb.THEN_IF_UNTAP_THAT_LAND.matchEntire(sentence)?.let { m ->
            val index = steps.indexOfLast { ((it as? Step.Do)?.effect as? Effect.Move)?.let { mv -> mv.objects == Objects.Bound("found") && mv.to.zone == Zone.Battlefield } == true }
            if (index < 0) return null
            val condition = Conditions.condition(m.groupValues[1], self) ?: return null
            val move = (steps[index] as Step.Do).effect as Effect.Move
            return steps.toMutableList().also { it[index] = Step.Do(move.copy(postBind = "land")) } +
                Step.If(condition, then = listOf(Step.Do(Effect.Untap(Objects.Bound("land")))))
        }
        // blb chunk 11: "Exile that token at the beginning of the next end step." after creating it (CR 603.7)
        if (sentence == "Exile that token at the beginning of the next end step.") {
            val bound = bindTokens(steps) ?: return null
            return bound + Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound(TOKENS), Destination(Zone.Exile)))))))
        }
        // blb chunk 11: "Put that card onto the battlefield tapped and the rest on the bottom of your library in a random order."
        // after revealing until a card is found
        if (sentence == "Put that card onto the battlefield tapped and the rest on the bottom of your library in a random order." &&
            ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.RevealUntil)?.foundBind == FOUND) {
            return steps + listOf(Step.Do(Effect.Move(Objects.Bound(FOUND), Destination(Zone.Battlefield, tapped = true))),
                Step.Do(Effect.Move(Objects.Bound(REST), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // blb chunk 10: "If you control a Bird, counter that spell unless its controller pays {4} instead." after "Counter target
        // spell unless its controller pays {2}.": the other cost instead (CR 118.12a)
        Blb.COUNTER_UNLESS_INSTEAD.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[1], self) ?: return null
            if (steps.size < 2) return null
            val pay = steps[steps.size - 2] as? Step.Pay ?: return null
            val instead = pay.copy(cost = Cost(listOf(CostPart.Mana(ManaCost.parse(m.groupValues[2]) ?: return null))))
            return steps.dropLast(2) + Step.If(condition, then = listOf(instead, steps.last()), otherwise = steps.takeLast(2))
        }
        // blb chunk 09: "If this spell was cast from anywhere other than your hand, put a +1/+1 counter on that creature." after
        // creating a token: "that creature" is the token (CR 400.7d: a copy wasn't cast)
        Blb.IF_CAST_NOT_FROM_HAND_COUNTER_ON_THAT.matchEntire(sentence)?.let { m ->
            val create = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateToken)?.takeIf { it.bind == null } ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return steps.dropLast(1) + Step.Do(create.copy(bind = "token")) + Step.If(arcanum.api.Condition.And(listOf(
                arcanum.api.Condition.CastFrom(null, Objects.Self), arcanum.api.Condition.Not(arcanum.api.Condition.CastFrom(Zone.Hand, Objects.Self)))),
                then = listOf(Step.Do(Effect.PutCounters(Objects.Bound("token"), kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))))
        }
        // blb chunk 06: "If that creature would die this turn, exile it instead." after damage to a target: a replacement for the
        // rest of the turn (CR 614.1a)
        if (sentence == "If that creature would die this turn, exile it instead.") {
            val creature = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            return steps + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(creature), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(arcanum.api.Modification.ChangeDestination(Destination(Zone.Exile))))), null, arcanum.api.Duration.EndOfTurn))
        }
        // blb chunk 03: "If you cast a spell this way, that creature enters with a finality counter on it." after a
        // permission: the spell cast with it gets that (CR 400.7d)
        Blb.CAST_THIS_WAY_ENTERS_WITH.matchEntire(sentence)?.let { m ->
            val apply = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.ApplyMechanics) ?: return null
            val may = apply.mechanics.singleOrNull() as? Mechanic.MayPlay ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val gets = Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(arcanum.api.Modification.EntersWithCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
            return steps.dropLast(1) + Step.Do(apply.copy(mechanics = listOf(may.copy(spellGets = may.spellGets + gets))))
        }
        return null
    }

    /** The Bloomburrow static wordings; null when none reads [line]. */
    private fun blbStatic(line: String): Ability? {
        // blb chunk 12: "As it enters, choose a color." (CR 614.12, 607.2d)
        Blb.AS_ENTERS_CHOOSE_COLOR.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(arcanum.api.Choice.Color(), bind = CHOSEN_COLOR, remember = true, chooser = Players.You)))))),
                affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 11: "Players can't gain life." (CR 119.7), "Damage can't be prevented." (CR 615.12)
        if (line == "Players can't gain life.")
            return Ability.Static(listOf(Mechanic.CantGainLife), affects = Affected.Players(Players.Each(PlayerFilter.Any)))
        if (line == "Damage can't be prevented.")
            return Ability.Static(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter()))),
                affects = Affected.Objects(Objects.Self))
        // blb chunk 11: "Enchanted creature doesn't untap during its controller's untap step." (CR 502.3)
        if (line == "Enchanted creature doesn't untap during its controller's untap step.")
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        // blb chunk 11: "Creatures your opponents control have base toughness 1." (CR 613.4b)
        Blb.HAVE_BASE_TOUGHNESS.matchEntire(line)?.let { m ->
            val group = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return Ability.Static(listOf(Mechanic.SetPowerToughness(null, Value.Fixed(m.groupValues[2].toInt()))), affects = Affected.Objects(Objects.Each(group)))
        }
        // blb chunk 10: "This spell costs {1} less to cast if you control an Otter." ("… if it targets …" is read by dsk chunk 09,
        // CR 601.2f)
        Blb.THIS_SPELL_COSTS_LESS_IF.matchEntire(line)?.let { m ->
            if (m.groupValues[2].startsWith("it targets ")) return@let
            val per = ManaCost.parse(m.groupValues[1]) ?: return null
            val condition = Conditions.condition(m.groupValues[2], self) ?: return@let
            return Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(per, Value.Fixed(1)))),
                affects = Affected.Objects(Objects.Self), condition = condition)
        }
        // blb chunk 09: "This spell costs {1} less to cast for each instant and sorcery card in your graveyard." (CR 601.2f)
        Blb.THIS_SPELL_COSTS_LESS_FOR_EACH.matchEntire(line)?.let { m ->
            val per = ManaCost.parse(m.groupValues[1]) ?: return null
            val counted = Filters.noun(m.groupValues[2].replace(" and ", " or ").replace(" in exile or in ", " in exile and in ")) ?: return null
            return Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(per, Value.Count(Objects.Each(counted))))),
                affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 09: "If a card or token would be put into your graveyard from anywhere, exile it instead." (CR 614.1a)
        Blb.WOULD_BE_PUT_INTO_YOUR_GRAVEYARD.matchEntire(line)?.let { m ->
            val what = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(Filters.all(what, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 09: "This creature enters tapped if it's not your turn." (CR 614.1c)
        Blb.ENTERS_TAPPED_IF.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self),
                condition = Conditions.condition(m.groupValues[2], self) ?: return null)
        }
        // blb chunk 09: "As long as [condition], creatures your opponents control get -1/-0."
        Blb.AS_LONG_AS_GROUP_GETS.matchEntire(line)?.let { m ->
            val condition = Conditions.condition(m.groupValues[1], self) ?: return@let
            val group = Filters.noun(m.groupValues[2]) ?: return@let
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[3].toInt()), Value.Fixed(m.groupValues[4].toInt()))),
                affects = Affected.Objects(Objects.Each(group)), condition = condition)
        }
        // blb chunk 09: "As long as [condition], it gets +4/+4 and has trample."
        Blb.AS_LONG_AS_IT_GETS_AND_HAS.matchEntire(line)?.let { m ->
            if (m.groupValues[2] != "it" && !isSelf(m.groupValues[2])) return@let
            val condition = Conditions.condition(m.groupValues[1], self) ?: return null
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[3].toInt()), Value.Fixed(m.groupValues[4].toInt())),
                Mechanic.GainAbilities(keywords(m.groupValues[5]) ?: return null)), affects = Affected.Objects(Objects.Self), condition = condition)
        }
        // blb chunk 09: "You may cast instant and sorcery spells from your graveyard by paying 1 life in addition to their other
        // costs." (CR 601.2a, 601.2f)
        Blb.YOU_MAY_CAST_FROM_GRAVEYARD_BY.matchEntire(line)?.let { m ->
            val spells = Filters.noun(m.groupValues[1].replace(" spells", " cards").replace(" and ", " or ")) ?: return null
            val extra = if (m.groupValues[2] == "foraging") CostPart.Action(arcanum.api.KeywordAction.Forage)
                else costPart(m.groupValues[2].replace("paying", "pay").replaceFirstChar { it.uppercase() })?.singleOrNull() ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, additionalCost = Cost(listOf(extra)), holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Each(Filters.all(spells, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // blb chunk 08: "You may cast noncreature spells as though they had flash." (a permission of the player, CR 601.3)
        Blb.YOU_MAY_CAST_AS_THOUGH_FLASH.matchEntire(line)?.let { m ->
            val spells = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CastAsThoughFlash(spells)), affects = Affected.Players(Players.You))
        }
        // blb chunk 08: "If a Lizard, Mouse, Otter, or Raccoon you control would deal damage to a permanent or player, it deals that
        // much damage plus 1 instead." (CR 614.1a)
        Blb.DAMAGE_TO_ANY_PLUS.matchEntire(line)?.let { m ->
            val source = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Damage(source, RecipientFilter()),
                modify = listOf(Modification.AddAmount(Value.Fixed(m.groupValues[2].toInt()))))), affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 07: "As long as you've lost life this turn, this creature has flying and vigilance."
        Blb.AS_LONG_AS_SELF_HAS.matchEntire(line)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val condition = Conditions.condition(m.groupValues[1], self) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords(m.groupValues[3]) ?: return null)), affects = Affected.Objects(Objects.Self),
                condition = condition)
        }
        // blb chunk 07: "This creature enters with a +1/+1 counter on it if an opponent lost life this turn." (CR 614.1c)
        Blb.ENTERS_WITH_COUNTER_IF.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            val condition = Conditions.condition(m.groupValues[4], self) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))),
                affects = Affected.Objects(Objects.Self), condition = condition)
        }
        // blb chunk 07: "Other creatures you control enter with an additional +1/+1 counter on them for each opponent who lost life
        // this turn."
        Blb.OTHERS_ENTER_WITH_ADDITIONAL.matchEntire(line)?.let { m ->
            val entering = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val per = when (m.groupValues[3]) {
                "opponent who lost life this turn" -> Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                    PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any))))))
                else -> return null
            }
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(entering, to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, per)))), affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 05: "Noncreature spells you cast cost {1} less to cast.", "Instant and sorcery spells you cast …" (CR 601.2f)
        Blb.SPELLS_YOU_CAST_COST.matchEntire(line)?.let { m ->
            val spells = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }.replace(" and ", " or ") + " spell") ?: return null
            val mana = ManaCost.parse(m.groupValues[2]) ?: return null
            val change = if (m.groupValues[3] == "more") arcanum.api.CostChange.More(mana, Value.Fixed(1)) else arcanum.api.CostChange.Less(mana, Value.Fixed(1))
            return Ability.Static(listOf(Mechanic.CostChange(Filters.all(spells, ObjectFilter.ControlledBy(PlayerFilter.You)), change = change)),
                affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 05: "Permanents you control with counters on them have ward {1}." (keywords with values, CR 613.1f)
        Blb.HAVE_KEYWORDS_WITH_VALUES.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return@let
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return@let }
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = Affected.Objects(Objects.Each(filter)))
        }
        // blb chunk 05: "If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it
        // deals that much damage plus 2 instead." (CR 614.1a)
        Blb.DAMAGE_PLUS.matchEntire(line)?.let { m ->
            // "a source you control": any object as a source, by its controller
            val source = when (val s = m.groupValues[1].removeArticle()) {
                "source you control" -> ObjectFilter.ControlledBy(PlayerFilter.You)
                else -> Filters.noun(s) ?: return null
            }
            val combat = when (m.groupValues[2]) { "noncombat " -> false; "combat " -> true; else -> null }
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.Damage(source, RecipientFilter(objects = Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent),
                    ObjectFilter.ControlledBy(PlayerFilter.Opponent)), players = PlayerFilter.Opponent), combat = combat),
                modify = listOf(Modification.AddAmount(Value.Fixed(m.groupValues[3].toInt()))))), affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 05: "If you would put one or more counters on a permanent or player, put twice that many of each of those kinds
        // of counters on that permanent or player instead." (CR 614.1a: counters you put, by your effects and costs)
        if (line == "If you would put one or more counters on a permanent or player, put twice that many of each of those kinds of counters on that permanent or player instead.") {
            val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
            return Ability.Static(listOf(
                Mechanic.Replacement(EventPattern.CountersPut(ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent), causedBy = yours),
                    modify = listOf(Modification.MultiplyAmount(2))),
                Mechanic.Replacement(EventPattern.PlayerCountersPut(PlayerFilter.Any, causedBy = yours), modify = listOf(Modification.MultiplyAmount(2))),
            ), affects = Affected.Objects(Objects.Self))
        }
        // blb chunk 03: "Other creatures are Food artifacts in addition to their other types and have "[ability]"." (CR
        // 205.1b, 613.1d, f)
        Blb.ARE_IN_ADDITION.matchEntire(line)?.let { m ->
            val affected = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val (types, subtypes) = typesAndSubtypes(m.groupValues[2]) ?: return null
            val granted = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Sentences(setOf("this permanent", "this creature")).line("$it.") ?: return null }
            return Ability.Static(listOf<Mechanic>(Mechanic.SetTypes(add = types, addSubtypes = subtypes)) +
                listOfNotNull(granted?.let { Mechanic.GainAbilities(listOf(it)) }), affects = Affected.Objects(Objects.Each(affected)))
        }
        // blb chunk 03: 'Enchanted permanent is a colorless Food artifact with "[ability]" and loses all other card types and
        // abilities.' (CR 613.1d–f: the types and subtypes replaced, colourless, the granted ability kept)
        Blb.ENCHANTED_IS_A_COLORLESS.matchEntire(line)?.let { m ->
            val (types, subtypes) = typesAndSubtypes(m.groupValues[1]) ?: return null
            val granted = Sentences(setOf("this artifact", "this permanent")).line(m.groupValues[2] + ".") ?: return null
            return Ability.Static(listOf(Mechanic.SetTypes(add = types, addSubtypes = subtypes, replaceTypes = true, replaceSubtypes = true),
                Mechanic.SetColors(emptyList()), Mechanic.LoseAbilities(), Mechanic.GainAbilities(listOf(granted))),
                affects = Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // blb chunk 02: "Equipped creature gets +3/+3 and loses flying.", "… gets +2/+0 and has first strike, vigilance,
        // trample, and haste." (CR 613.4c, 613.1f)
        Blb.ATTACHED_GETS_HAS_LOSES.matchEntire(line)?.let { m ->
            fun keywords(text: String) = text.split(Regex(""",? and |, """)).map { OracleParser.keyword(it) }
            val has = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { keywords(it) }
            val loses = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { keywords(it) }
            if (has == null && loses == null) return null
            if (has?.contains(null) == true || loses?.contains(null) == true) return null
            val mechanics = listOf<Mechanic>(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[1].toInt()), Value.Fixed(m.groupValues[2].toInt()))) +
                listOfNotNull(has?.let { Mechanic.GainAbilities(it.filterNotNull()) }) +
                loses.orEmpty().filterNotNull().map { Mechanic.LoseAbilities(it.kind) }
            return Ability.Static(mechanics, affects = Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // blb chunk 02: "You control enchanted creature." (layer 2, CR 613.1b)
        if (line == "You control enchanted creature.")
            return Ability.Static(listOf(Mechanic.ControlledBy(Players.You)), affects = Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        return null
    }

    /** blb chunk 03: "Food artifacts" → card types and subtypes (plurals singular). */
    private fun typesAndSubtypes(words: String): Pair<List<arcanum.api.CardType>, List<arcanum.api.SubtypeRef>>? {
        val types = mutableListOf<arcanum.api.CardType>()
        val subtypes = mutableListOf<arcanum.api.SubtypeRef>()
        for (word in words.split(' ')) {
            val single = if (word.first().isLowerCase()) word.removeSuffix("s") else word.removeSuffix("s").takeIf { it in RulesWords.subtypes } ?: word
            Filters.CARD_TYPES[single]?.let { types += it } ?: if (single in RulesWords.subtypes) subtypes += arcanum.api.SubtypeRef.Literal(Subtype(single)) else return null
        }
        return types to subtypes
    }

    /** The Bloomburrow effect wordings; null when none reads [sentence]. */
    private fun blbEffect(sentence: String, context: Context): List<Step>? {
        // blb chunk 12: "Choose a color. Add an amount of mana of that color equal to the number of creatures you control of the
        // chosen type." (CR 106.1)
        if (sentence == "Choose a color.") return listOf(Step.Choose(arcanum.api.Choice.Color(), bind = "color", chooser = Players.You))
        Blb.ADD_AMOUNT_OF_THAT_COLOR.matchEntire(sentence)?.let { m ->
            val amount = Values.value(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.AddMana(arcanum.api.ManaProduction(ManaKind.Choose(amount, listOf(ColorRef.Chosen("color")))), Players.You)))
        }
        // blb chunk 12: "This Vehicle becomes an artifact creature until end of turn." (CR 301.7b)
        Blb.BECOMES_ARTIFACT_CREATURE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature))),
                Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 12: "It gains trample until end of turn." in an activated ability without targets: this permanent
        Blb.IT_GAINS_UNTIL_EOT.matchEntire(sentence)?.takeIf { context.it == null && context.event == null && context.targets.isEmpty() }?.let { m ->
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords(m.groupValues[1]) ?: return null)),
                Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 12: "Then if creatures you control have total power 10 or greater, draw a card."
        Blb.THEN_IF.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[1], self) ?: return@let
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return@let
            return listOf(Step.If(condition, then = then))
        }
        // blb chunk 11: "Put two +1/+1 counters on that creature." in an enters trigger: the creature that entered
        Blb.COUNTERS_ON_THAT_CREATURE.matchEntire(sentence)?.let { m ->
            if ((context.event as? EventPattern.ZoneChange)?.to != Zone.Battlefield) return@let
            val it = context.that ?: return@let
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(it, kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // blb chunk 11: "Look at the top card of your library." ("it" is then that card)
        if (sentence == "Look at the top card of your library.") {
            context.it = Objects.Bound("seen")
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), bind = "seen", players = Players.You)))
        }
        // blb chunk 11: "Put it onto the battlefield tapped."
        Blb.PUT_IT_ONTO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            return listOf(Step.Do(Effect.Move(it, Destination(Zone.Battlefield, tapped = m.groupValues[1].isNotEmpty()))))
        }
        // blb chunk 11: "Create a token that's a copy of this creature." (CR 707.2)
        Blb.CREATE_COPY_OF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(Objects.Self), count = Value.Fixed(1), controller = Players.You)))
        }
        // blb chunk 11: "Return this creature to its owner's hand."
        Blb.RETURN_TO_OWNERS_HAND.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand))))
        }
        // blb chunk 11: "Return up to one target creature you control with lesser mana value to its owner's hand." (lesser
        // than the creature that entered)
        Blb.RETURN_UP_TO_ONE_LESSER.matchEntire(sentence)?.let { m ->
            val entered = context.it ?: return null
            val name = Filters.slotName(m.groupValues[1])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(
                arcanum.api.Comparison.Less, Value.Of(arcanum.api.NumericProperty.ManaValue, entered, arcanum.api.Combine.Single)))),
                min = Value.Fixed(0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // blb chunk 11: "If that creature had power 2 or less, put a +1/+1 counter on this creature." (its last known
        // information, CR 608.2h)
        Blb.IF_THAT_HAD_POWER.matchEntire(sentence)?.let { m ->
            val that = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val then = effect(m.groupValues[3].replaceFirstChar { it.uppercase() }, context) ?: return null
            val comparison = if (m.groupValues[2] == "greater") arcanum.api.Comparison.AtLeast else arcanum.api.Comparison.AtMost
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(that, ObjectFilter.Power(comparison, Value.Fixed(m.groupValues[1].toInt()))), then = then))
        }
        // blb chunk 11: "Return another target creature you control to its owner's hand.", "… up to one other target permanent …"
        Blb.RETURN_OTHER_TARGET_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.all(context.another(), Filters.noun(noun) ?: return null)),
                min = Value.Fixed(if (m.groupValues[1] == "another") 1 else 0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // blb chunk 11: "Return another creature you control to its owner's hand. If you do, …" (a choice, not a target)
        Blb.RETURN_ANOTHER_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(context.another(), Filters.noun(m.groupValues[1]) ?: return null)
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(
                Step.Pay(Cost(listOf(CostPart.ReturnToHand(arcanum.api.CostObjects.Choose(filter, Value.Fixed(1))))), payer = Players.You, mandatory = true, bind = "returned"),
                Step.If(arcanum.api.Condition.Did("returned"), then = then))
        }
        // blb chunk 11: "It deals damage to each player equal to the number of nonbasic lands that player controls."
        Blb.DEALS_DAMAGE_TO_EACH_PLAYER_EQUAL.matchEntire(sentence)?.takeIf { it.groupValues[1] == "It" || isSelf(it.groupValues[1]) }?.let { m ->
            val counted = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.Bound("player")), Value.Count(Objects.Each(counted)), source = Objects.Self)))))
        }
        // blb chunk 11: "Target creature an opponent controls can't block this turn."
        Blb.TARGET_CANT_BLOCK_THIS_TURN.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 11: "Put a +1/+1 counter on target creature and you gain 1 life."
        Blb.AND_YOU_GAIN_LIFE.matchEntire(sentence)?.takeIf { !it.groupValues[1].startsWith("If ") }?.let { m ->
            val first = effect(m.groupValues[1] + ".", context) ?: return@let
            return first + Step.Do(Effect.GainLife(Players.You, Value.Fixed(m.groupValues[2].toInt())))
        }
        // blb chunk 11: "Until end of turn, target creature you control gains vigilance and gets +X/+X, where X is the number of
        // creatures you control."
        Blb.UNTIL_EOT_TARGET_GAINS_AND_GETS_X.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null),
                Mechanic.ModifyPowerToughness(xOrChange(m.groupValues[3]) ?: return null, xOrChange(m.groupValues[4]) ?: return null)),
                Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 11: "Put a +1/+1 counter on each of up to two target creatures."
        Blb.COUNTER_ON_EACH_OF_UP_TO.matchEntire(sentence)?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            val name = Filters.slotName(m.groupValues[3])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[3]) ?: return null), min = Value.Fixed(0),
                max = Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))
            return listOf(Step.Do(Effect.PutCounters(Objects.Target(name), kind, Value.Fixed(1))))
        }
        // blb chunk 11: "Double the number of +1/+1 counters on it." (CR 701.10e)
        Blb.DOUBLE_COUNTERS_ON_IT.matchEntire(sentence)?.let { m ->
            val it = context.it ?: Objects.EventObject(EventRole.Object)
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Double(arcanum.api.Doubled.Counters(it, Counters.kind(m.groupValues[1]) ?: return null)))))
        }
        // blb chunk 11: "Tap up to one target creature and put three stun counters on it. If you control that creature, draw two
        // cards." (CR 122.1d)
        Blb.TAP_UP_TO_ONE_AND_COUNTERS.matchEntire(sentence)?.let { m ->
            val name = Filters.slotName(m.groupValues[1])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[1]) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.Tap(Objects.Target(name))),
                Step.Do(Effect.PutCounters(Objects.Target(name), kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))
        }
        Blb.IF_YOU_CONTROL_THAT.matchEntire(sentence)?.let { m ->
            val that = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(that, ObjectFilter.ControlledBy(PlayerFilter.You)), then = then))
        }
        // blb chunk 11: "Exile another target creature you control, then return it to the battlefield under its owner's control
        // with a +1/+1 counter on it." (CR 400.7: the returned card is a new object)
        Blb.FLICKER_OTHER_WITH_COUNTER.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.all(context.another(), Filters.noun(noun) ?: return null))
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return listOf(
                Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")),
                    counters = listOf(arcanum.api.CountersToPut(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)))))))
        }
        // blb chunk 10: "Untap it." (the target named before)
        if (sentence == "Untap it.") {
            val it = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return@blbEffect null
            return listOf(Step.Do(Effect.Untap(it)))
        }
        // blb chunk 10: "Create X tokens that are copies of target token you control."
        Blb.CREATE_X_COPIES_OF_TARGET.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(target), count = Value.Announced("X"), controller = Players.You)))
        }
        // blb chunk 10: "Tokens you control gain indestructible until your next turn."
        Blb.GROUP_GAINS_UNTIL_YOUR_NEXT_TURN.matchEntire(sentence)?.let { m ->
            val group = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null)),
                Affected.Objects(Objects.Each(group)), arcanum.api.Duration.UntilYourNextTurn)))
        }
        // blb chunk 10: "You gain 1 life for each token you control."
        Blb.GAIN_LIFE_FOR_EACH.matchEntire(sentence)?.let { m ->
            val per = Value.Count(Objects.Each(Filters.noun(m.groupValues[2]) ?: return null))
            val n = m.groupValues[1].toInt()
            return listOf(Step.Do(Effect.GainLife(Players.You, if (n == 1) per else Value.Times(per, n))))
        }
        // blb chunk 10: "Up to one other target creature gets +1/+1 until end of turn." (CR 115.3)
        Blb.UP_TO_ONE_OTHER_TARGET_GETS.matchEntire(sentence)?.let { m ->
            val first = context.targets.lastOrNull { it.what is TargetKind.Objects } ?: return null
            val name = "other " + Filters.slotName(m.groupValues[1])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[1]) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1),
                distinctFrom = listOf(first.name))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                Affected.Objects(Objects.Target(name)), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 10: "Arc Conduction deals 6 damage to target creature and 2 damage to up to one target creature token."
        Blb.DEALS_TO_TARGET_AND_UP_TO_ONE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val first = context.target(Filters.slotName(m.groupValues[3]), Filters.noun(m.groupValues[3]) ?: return null)
            val name = Filters.slotName(m.groupValues[5])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[5]) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            return listOf(
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = first), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)),
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Target(name)), Value.Fixed(m.groupValues[4].toInt()), source = Objects.Self)))
        }
        // blb chunk 10: "It deals damage equal to its power to target creature you don't control." (the target named before)
        Blb.IT_DEALS_POWER_TO_TARGET.matchEntire(sentence)?.let { m ->
            val first = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val phrase = m.groupValues[1]
            val name = Filters.slotName(phrase).let { if (context.targets.any { t -> t.name == it }) phrase else it }
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(phrase) ?: return null), min = Value.Fixed(1), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Target(name)),
                Value.Of(arcanum.api.NumericProperty.Power, first, arcanum.api.Combine.Single), source = first)))
        }
        // blb chunk 10: "Target creature's owner puts it on their choice of the top or bottom of their library."
        Blb.OWNER_PUTS_TOP_OR_BOTTOM.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target)),
                Step.If(arcanum.api.Condition.Chose("position", "top"),
                    then = listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = 0)))),
                    otherwise = listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = -1))))))
        }
        // blb chunk 10: "Return up to two target creatures to their owners' hands."
        Blb.RETURN_UP_TO_N_TO_HANDS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val name = Filters.slotName(m.groupValues[2])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[2]) ?: return null), min = Value.Fixed(0), max = Value.Fixed(n))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // blb chunk 10: "Choose two target creatures controlled by different players." / "Return those creatures to their owners'
        // hands." (CR 601.2c: the second target's controller isn't the first's)
        Blb.TWO_TARGETS_DIFFERENT_PLAYERS.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1].removeSuffix("s")
            val filter = Filters.noun(noun) ?: return null
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(1), max = Value.Fixed(1))
            context.targets += TargetSlot("other $name", TargetKind.Objects(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.Is(
                Players.ControllerOf(Objects.Target(name))))))), min = Value.Fixed(1), max = Value.Fixed(1), distinctFrom = listOf(name))
            return emptyList()
        }
        if (sentence == "Return those creatures to their owners' hands." && context.targets.size >= 2) {
            val two = context.targets.takeLast(2).map { Objects.Target(it.name) }
            return listOf(Step.Do(Effect.Move(Objects.Union(two), Destination(Zone.Hand))))
        }
        // blb chunk 09: "Look at twice X cards from the top of your library." / "Put X cards from among them into your hand and the
        // rest into your graveyard."
        if (sentence == "Look at twice X cards from the top of your library.") {
            context.seen = "seen"
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Times(Value.Announced("X"), 2), fromTop = true), players = Players.You, bind = "seen")))
        }
        if (sentence == "Put X cards from among them into your hand and the rest into your graveyard.") {
            val seen = Objects.Bound(context.seen ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Announced("X"), Value.Announced("X"), from = seen), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(seen, Objects.Bound("chosen")), Destination(Zone.Graveyard))))
        }
        // blb chunk 09: "Return up to one target nonland permanent to its owner's hand."
        Blb.RETURN_UP_TO_ONE_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // blb chunk 09: "Mill three cards. When you do, [reflexive trigger]." after a mandatory action (CR 603.12)
        Blb.DO_THEN_WHEN_YOU_DO.matchEntire(sentence)?.let { m ->
            if (m.groupValues[1].startsWith("You may")) return@let
            val first = effect(m.groupValues[1] + ".", context) ?: return@let
            announcedX = true
            val reflexive = try { instructions(m.groupValues[2]) } finally { announcedX = false } ?: return@let
            return first + Step.Do(Effect.ReflexiveTrigger(reflexive))
        }
        // blb chunk 09: "you may cast target instant or sorcery card with mana value equal to the number of coin counters on this
        // artifact from your graveyard without paying its mana cost" (CR 608.2g)
        Blb.MAY_CAST_TARGET_WITH_COUNTERS_MANA_VALUE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal,
                Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single)), ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))
            val card = context.target("card", filter)
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(card, withoutPaying = true, bind = "spell", how = PlayKind.Cast, caster = Players.You))), player = Players.You))
        }
        // blb chunk 09: "Return this card from your graveyard to the battlefield tapped and attacking." (CR 508.4)
        Blb.RETURN_SELF_TAPPED_AND_ATTACKING.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = true, attacking = arcanum.api.AttackTarget.Chosen))))
        }
        // blb chunk 09: "Target opponent discards two cards."
        Blb.TARGET_PLAYER_DISCARDS.matchEntire(sentence)?.let { m ->
            val player = playersIn("target " + m.groupValues[1], context) ?: return null
            return listOf(Step.Do(Effect.Discard(player, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null), filter = ObjectFilter.Any)))
        }
        // blb chunk 09: "Target opponent exiles a card from their hand." (that player chooses which)
        Blb.TARGET_PLAYER_EXILES_FROM_HAND.matchEntire(sentence)?.let { m ->
            val player = playersIn("target " + m.groupValues[1], context) ?: return null
            return listOf(Step.Choose(arcanum.api.Choice.Objects(handOf(player), Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = player),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile))))
        }
        // blb chunk 09: "Put target card from your graveyard on the bottom of your library."
        Blb.PUT_TARGET_FROM_GRAVEYARD_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Library, libraryPosition = -1))))
        }
        // blb chunk 08: "Draw a card if this is the second time this ability has resolved this turn.", "Create a 1/1 black Snail
        // creature token if you don't control a Snail." (checked as it resolves)
        Blb.TRAILING_IF.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self) ?: return@let
            val then = effect(m.groupValues[1] + ".", context) ?: return@let
            return listOf(Step.If(condition, then = then))
        }
        // blb chunk 08: "Return another target creature card with lesser mana value from your graveyard to the battlefield." in a
        // dies trigger: lesser than the dead creature's, and not that card (CR 603.10a)
        Blb.RETURN_ANOTHER_WITH_LESSER_MANA_VALUE.matchEntire(sentence)?.let { m ->
            if (context.event !is EventPattern.ZoneChange) return null
            val filter = Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.EventObject(EventRole.After))), Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ManaValue(arcanum.api.Comparison.Less, Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.EventObject(EventRole.Before),
                    arcanum.api.Combine.Single)), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Battlefield))))
        }
        // blb chunk 08: "Exile target creature you control, then return it to the battlefield under its owner's control." ("that
        // creature" is then the returned one, CR 400.7)
        Blb.EXILE_THEN_RETURN.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            context.it = Objects.Bound("exiled")
            return listOf(
                Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))
        }
        // blb chunk 08: "Target creature you control gets +X/+0 until end of turn, where X is this creature's power."
        Blb.TARGET_GETS_X_SELF_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[4]) }?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            val x = Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single)
            fun part(text: String) = if (text == "+X") x else ptChange(text)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(part(m.groupValues[2]) ?: return null, part(m.groupValues[3]) ?: return null)),
                Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 08: "If that creature is a Bird, Frog, Otter, or Rat, draw a card." (the list's commas aren't the clause's)
        Blb.IF_THAT_CREATURE_IS.matchEntire(sentence)?.let { m ->
            val condition = itCondition("that creature is " + m.groupValues[1], context) ?: return@let
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return@let
            return listOf(Step.If(condition, then = then))
        }
        // blb chunk 08: "You gain X life." (X defined by "where X is")
        Blb.GAINS_X_LIFE.matchEntire(sentence)?.let { m ->
            val players = playersIn(m.groupValues[1], context) ?: return null
            return listOf(Step.Do(Effect.GainLife(players, Value.Announced(name = "X"))))
        }
        // blb chunk 08: "Fennick deals damage equal to the sacrificed creature's power to each opponent." / "Draw cards equal to the
        // sacrificed creature's power." (as it last existed, CR 608.2h)
        Blb.DEALS_SACRIFICED_POWER_TO_EACH_OPPONENT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.Each(PlayerFilter.Opponent)),
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Bound(SACRIFICED), arcanum.api.Combine.Single), source = Objects.Self)))
        }
        if (sentence == "Draw cards equal to the sacrificed creature's power.")
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Of(arcanum.api.NumericProperty.Power, Objects.Bound(SACRIFICED), arcanum.api.Combine.Single))))
        // blb chunk 08: 'Create Cindercrag, a legendary colorless Equipment artifact token with "[ability]" and equip {2}.'
        Blb.CREATE_NAMED_LEGENDARY_TOKEN.matchEntire(sentence)?.let { m ->
            val (types, subtypes) = typesAndSubtypes(m.groupValues[4]) ?: return null
            val colors = if (m.groupValues[3] == "colorless") emptyList<arcanum.api.Color>() else return null
            val quoted = Sentences(setOf("this Equipment", "this artifact", "this token")).line(m.groupValues[5].let { if (it.endsWith(".")) it else "$it." }) ?: return null
            val more = m.groupValues[6].takeIf { it.isNotEmpty() }?.let { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }
            val face = arcanum.api.FaceDefinition(name = m.groupValues[1], colorIndicator = colors,
                supertypes = if (m.groupValues[2].isNotEmpty()) listOf(arcanum.api.Supertype.Legendary) else emptyList(), types = types,
                subtypes = subtypes.map { (it as arcanum.api.SubtypeRef.Literal).subtype }, abilities = listOfNotNull(quoted, more))
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.Defined(face), count = Value.Fixed(1), controller = Players.You)))
        }
        // blb chunk 07: "Put its counters on target creature you control." (a dead creature's counters as it last existed, CR 608.2h)
        Blb.PUT_ITS_COUNTERS_ON_TARGET.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.MoveCounters(Objects.Self, target, kind = null,
                amount = Value.Of(arcanum.api.NumericProperty.Counters(null), Objects.Self, arcanum.api.Combine.Single))))
        }
        // blb chunk 07: "Choose one of them." (one of the cards just exiled; "that card" is then it)
        if (sentence == "Choose one of them.") {
            val them = context.them ?: return null
            context.it = Objects.Bound("chosen")
            return listOf(Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = them), bind = "chosen", chooser = Players.You))
        }
        // blb chunk 07: "Choose target opponent." ("they" is then that player)
        Blb.CHOOSE_TARGET_PLAYER.matchEntire(sentence)?.let { m ->
            playersIn("target " + m.groupValues[1], context) ?: return null
            return emptyList()
        }
        // blb chunk 07: "If they lost life this turn, [effect]."
        Blb.IF_THEY_LOST_LIFE.matchEntire(sentence)?.let { m ->
            val player = context.lastPlayer ?: return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.PlayerMatches(player, PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any))), then = then))
        }
        // blb chunk 07: "They reveal their hand, you choose a nonland card from it, and they discard that card."
        Blb.THEY_REVEAL_YOU_CHOOSE_THEY_DISCARD.matchEntire(sentence)?.let { m ->
            val player = context.lastPlayer ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Do(Effect.Reveal(Objects.Each(handOf(player)), bind = "revealed")),
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Discard(player, Value.Fixed(1), filter = ObjectFilter.Any, objects = Objects.Bound("chosen"))))
        }
        // blb chunk 07: "They discard a card."
        Blb.THEY_DISCARD.matchEntire(sentence)?.let { m ->
            val player = context.lastPlayer ?: return null
            return listOf(Step.Do(Effect.Discard(player, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))
        }
        // blb chunk 06: "Its controller draws a card." (the last target's controller, as it last existed, CR 608.2h)
        Blb.ITS_CONTROLLER_DRAWS.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.Draw(Players.ControllerOf(target), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // blb chunk 06: "Return target permanent card … from your graveyard to the battlefield with an indestructible counter on it."
        Blb.RETURN_TARGET_FROM_GRAVEYARD_WITH_COUNTER.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            val card = context.target("card", filter)
            return listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield,
                counters = listOf(arcanum.api.CountersToPut(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)))))))
        }
        // blb chunk 06: "Draw a card for each creature that died under your control this turn."
        Blb.DRAW_A_CARD_FOR_EACH.matchEntire(sentence)?.let { m ->
            val n = Values.value("the number of " + m.groupValues[1], exiledCard) ?: return null
            return listOf(Step.Do(Effect.Draw(Players.You, n)))
        }
        // blb chunk 06: "Each opponent loses X life" (X defined by the ability's "where X is", CR 107.3c)
        Blb.LOSES_X_LIFE.matchEntire(sentence)?.let { m ->
            val players = playersIn(m.groupValues[1], context) ?: return null
            return listOf(Step.Do(Effect.LoseLife(players, Value.Announced(name = "X"))))
        }
        // blb chunk 06: "Put a +1/+1 counter on a creature you control." (chosen, not targeted; "it" is then that creature)
        Blb.PUT_COUNTERS_ON_A.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[3]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            context.it = Objects.Bound("chosen")
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.PutCounters(Objects.Bound("chosen"), kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // blb chunk 06: "It gains vigilance and trample until end of turn." about an object chosen before
        Blb.IT_GAINS.matchEntire(sentence)?.takeIf { context.it is Objects.Bound }?.let { m ->
            val keywords = m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)), Affected.Objects(context.it!!), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 06: "Choose artifact or enchantment." (a card type, CR 205.2a)
        Blb.CHOOSE_CARD_TYPE.matchEntire(sentence)?.let { m ->
            val types = listOf(m.groupValues[1], m.groupValues[2]).map { Filters.CARD_TYPES[it] ?: return null }
            return listOf(Step.Choose(arcanum.api.Choice.CardType(types), bind = "type", chooser = Players.You))
        }
        Blb.DESTROY_ALL_OF_CHOSEN_TYPE.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Destroy(Objects.Each(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.Type(arcanum.api.CardTypeRef.Chosen("type")))))))
        }
        // blb chunk 06: "Draw cards equal to the greatest power among creatures you control."
        Blb.DRAW_EQUAL_TO_GREATEST.matchEntire(sentence)?.let { m ->
            val property = when (m.groupValues[1]) {
                "power" -> arcanum.api.NumericProperty.Power
                "toughness" -> arcanum.api.NumericProperty.Toughness
                else -> arcanum.api.NumericProperty.ManaValue
            }
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Of(property, Objects.Each(Filters.noun(m.groupValues[2]) ?: return null), arcanum.api.Combine.Greatest))))
        }
        // blb chunk 06: "This creature fights target creature token." (CR 701.14a)
        Blb.SELF_FIGHTS_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[2]), Filters.noun(m.groupValues[2]) ?: return null)
            return listOf(Step.Do(Effect.Fight(Objects.Self, target)))
        }
        // blb chunk 06: "Target opponent exiles an enchantment they control." (that player chooses which)
        Blb.TARGET_PLAYER_EXILES.matchEntire(sentence)?.let { m ->
            val player = playersIn("target " + m.groupValues[1], context) ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[3]) ?: return@let, ObjectFilter.ControlledBy(PlayerFilter.Is(player)))
            val action = if (m.groupValues[2] == "exiles") Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile)) else Effect.Sacrifice(Objects.Bound("chosen"))
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = player), Step.Do(action))
        }
        // blb chunk 06: "Choose an artifact or creature you control." ("it" is then that one)
        Blb.CHOOSE_A.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return@let
            context.it = Objects.Bound("chosen")
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You))
        }
        // blb chunk 06: "Create a token that's a copy of it." (CR 707.2)
        if (sentence == "Create a token that's a copy of it." && context.it != null) {
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(context.it!!), count = Value.Fixed(1), controller = Players.You)))
        }
        // blb chunk 06: "Return each nonland, nontoken permanent to its owner's hand."
        Blb.RETURN_EACH_TO_HAND.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Move(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null), Destination(Zone.Hand))))
        }
        // blb chunk 06: "Until the end of your next turn, whenever you cast a spell, [effect]." (a delayed trigger that triggers
        // each time, CR 603.7b)
        Blb.UNTIL_END_OF_NEXT_TURN_WHENEVER.matchEntire(sentence)?.let { m ->
            val (trigger, _) = trigger(m.groupValues[1]) ?: return null
            val inner = instructions(m.groupValues[2] + ".", event = trigger.events.first()) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(trigger, inner, duration = arcanum.api.Duration.UntilEndOfYourNextTurn, repeats = true)))
        }
        // blb chunk 06: "[This] deals 2 damage to up to one target creature."
        Blb.DEALS_TO_UP_TO_ONE_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[3]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Target(name)), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)))
        }
        // blb chunk 05: "Exile it." (then "it" is the exiled card, CR 400.7)
        if (sentence == "Exile it." && context.it != null && context.it !is Objects.Bound) {
            val card = context.it!!
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(card, Destination(Zone.Exile), postBind = "exiled")))
        }
        // blb chunk 05: "Return a creature card from your graveyard to the battlefield with a finality counter on it."
        Blb.RETURN_A_FROM_GRAVEYARD_WITH_COUNTER.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield,
                    counters = listOf(arcanum.api.CountersToPut(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)))))))
        }
        // blb chunk 05: "Target creature you control deals damage equal to its power to target creature you don't control." (not a
        // fight: only the first deals damage, CR 120.3); "twice its power"
        Blb.TARGET_DEALS_POWER_TO_TARGET.matchEntire(sentence)?.let { m ->
            val first = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            val phrase = m.groupValues[3]
            val name = Filters.slotName(phrase).let { if (context.targets.any { t -> t.name == it }) phrase else it }
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(phrase) ?: return null), min = Value.Fixed(1), max = Value.Fixed(1))
            val power = Value.Of(arcanum.api.NumericProperty.Power, first, arcanum.api.Combine.Single)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Target(name)),
                if (m.groupValues[2].isEmpty()) power else Value.Times(power, 2), source = first)))
        }
        // blb chunk 05: "Create a token that's a copy of target token you control." (CR 707.2)
        Blb.COPY_OF_TARGET.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(target), count = Value.Fixed(1), controller = Players.You)))
        }
        // blb chunk 05: "Target attacking creature with power 3 or less can't be blocked this turn." (CR 509.1b)
        Blb.TARGET_CANT_BE_BLOCKED_THIS_TURN.matchEntire(sentence)?.let { m ->
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeBlocked), Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 05: "Each opponent discards two cards unless they discard a nonland card." (CR 118.12a, 101.4)
        Blb.EACH_OPPONENT_DISCARDS_UNLESS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val instead = Filters.noun(m.groupValues[2]) ?: return null
            val opponent = Players.Bound("opponent")
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true,
                steps = unlessPays(opponent, Cost(listOf(CostPart.Discard(CostObjects.Choose(instead, count = Value.Fixed(1))))),
                    listOf(Step.Do(Effect.Discard(opponent, Value.Fixed(n), filter = ObjectFilter.Any))))))
        }
        // blb chunk 05: "Draw an additional card for each opponent who has one or fewer cards in hand."
        Blb.DRAW_FOR_EACH_OPPONENT_WITH_HAND.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val players = Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(arcanum.api.Condition.Compare(
                Value.HandSize(Players.Tested), arcanum.api.Comparison.AtMost, Value.Fixed(n))))))
            return listOf(Step.Do(Effect.Draw(Players.You, Value.CountPlayers(players))))
        }
        // blb chunk 05: 'Create a colorless Equipment artifact token named Sword with "Equipped creature gets +1/+1" and equip {2}.'
        // (CR 111.4: a token with a name)
        Blb.CREATE_NAMED_TOKEN.matchEntire(sentence)?.let { m ->
            val colors = if (m.groupValues[1] == "colorless") emptyList() else listOf(arcanum.api.Color.of(m.groupValues[1].first().uppercaseChar()) ?: return null)
            val (types, subtypes) = typesAndSubtypes(m.groupValues[2]) ?: return null
            val quoted = Sentences(setOf("this Equipment", "this artifact", "this token")).line(m.groupValues[4].let { if (it.endsWith(".")) it else "$it." }) ?: return null
            val more = m.groupValues[5].takeIf { it.isNotEmpty() }?.let { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }
            val face = arcanum.api.FaceDefinition(name = m.groupValues[3], colorIndicator = colors, types = types,
                subtypes = subtypes.map { (it as arcanum.api.SubtypeRef.Literal).subtype }, abilities = listOfNotNull(quoted, more))
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.Defined(face), count = Value.Fixed(1), controller = Players.You)))
        }
        // blb chunk 05: "Attach target Equipment you control to up to one target creature you control." (CR 701.3)
        Blb.ATTACH_TARGET_TO_TARGET.matchEntire(sentence)?.let { m ->
            val equipment = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            val n = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { Numbers.count(it) ?: return null }
            val name = Filters.slotName(m.groupValues[3])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[3]) ?: return null),
                min = Value.Fixed(if (n == null) 1 else 0), max = Value.Fixed(n ?: 1))
            return listOf(Step.Do(Effect.Attach(equipment, arcanum.api.Recipients(objects = Objects.Target(name)))))
        }
        // blb chunk 04: "It gets +0/+2 until end of turn.", "it gets +1/+0 and gains first strike until end of turn."
        Blb.IT_GETS.matchEntire(sentence)?.let { m ->
            val objects = when {
                isSelf(m.groupValues[1]) -> Objects.Self
                m.groupValues[1] == "It" -> context.it ?: context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
                else -> return null
            }
            val keywords = m.groupValues[4].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }
            val mechanics = listOf<Mechanic>(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)) +
                listOfNotNull(keywords?.let { Mechanic.GainAbilities(it) })
            return listOf(Step.Do(Effect.ApplyMechanics(mechanics, Affected.Objects(objects), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 04: "Target creature you control gets +1/+1 and gains trample until end of turn."
        Blb.TARGET_GETS_AND_GAINS.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[4].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null,
                ptChange(m.groupValues[3]) ?: return null), Mechanic.GainAbilities(keywords)), Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 04: "You may reveal a creature card with mana value 3 or less from among them." ("it" is then that card)
        Blb.MAY_REVEAL_FROM_AMONG.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            context.picked = "chosen"
            context.it = Objects.Bound("chosen")
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound(seen)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))))
        }
        // blb chunk 04: "You may put it onto the battlefield if it's your turn. If you don't put it onto the battlefield, put it
        // into your hand." (CR 118.12: putting it there is a choice, only on your turn)
        if (sentence == "You may put it onto the battlefield if it's your turn.") {
            val card = context.it ?: return null
            return listOf(Step.If(arcanum.api.Condition.YourTurn, then = listOf(Step.Pay(steps = listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield)))),
                payer = Players.You, bind = "put"))))
        }
        if (sentence == "If you don't put it onto the battlefield, put it into your hand.") {
            val card = context.it ?: return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("put")), then = listOf(Step.Do(Effect.Move(card, Destination(Zone.Hand))))))
        }
        // blb chunk 04: "Add {R} or {G} for each Raccoon you control." (each mana of either colour, CR 106.1)
        Blb.ADD_EITHER_FOR_EACH.matchEntire(sentence)?.let { m ->
            val colors = listOf(m.groupValues[1], m.groupValues[2]).map { arcanum.api.ColorRef.Literal(arcanum.api.Color.of(it.single()) ?: return null) }
            val count = Value.Count(Objects.Each(Filters.noun(m.groupValues[3]) ?: return null))
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(count, colors, combination = true)), Players.You)))
        }
        // blb chunk 04: "Exile the top two cards of your library. Until the end of your next turn, you may play those cards."
        // (chunk 56's rule exiles the top N as "them"); "that card" is one chosen from them
        Blb.MAY_PLAY_THOSE.matchEntire(sentence)?.let { m ->
            val cards = (if (m.groupValues[2] == "that card") context.it ?: context.them else context.them ?: context.it) ?: return null
            val duration = when (m.groupValues[1]) {
                "Until end of turn" -> arcanum.api.Duration.EndOfTurn
                "Until the end of your next turn" -> arcanum.api.Duration.UntilEndOfYourNextTurn
                else -> return null
            }
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(cards), duration)))
        }
        // blb chunk 04: "Draw cards equal to the number of stash counters on this enchantment."
        Blb.DRAW_EQUAL_TO_COUNTERS.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single))))
        }
        // blb chunk 03: "Forage." (CR 701.61a), as in "You may forage."
        if (sentence == "Forage.") return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Forage)))
        // blb chunk 03: "Until end of turn, you may cast creature spells from your graveyard by foraging in addition to paying
        // their other costs." (CR 601.2a, 601.2f)
        Blb.MAY_CAST_FROM_GRAVEYARD_BY.matchEntire(sentence)?.let { m ->
            val spells = Filters.noun(m.groupValues[1].replace(" spells", " cards").replace(" and ", " or ")) ?: return null
            val extra = when (val by = m.groupValues[2]) {
                "foraging" -> CostPart.Action(arcanum.api.KeywordAction.Forage)
                else -> costPart(by.replaceFirstChar { it.uppercase() })?.singleOrNull() ?: return null
            }
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, additionalCost = Cost(listOf(extra)),
                holder = PlayerFilter.You)), Affected.Objects(Objects.Each(Filters.all(spells, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You)))), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 03: "Put a +1/+1 counter on it for each other Squirrel and/or Food you control."
        Blb.PUT_COUNTERS_FOR_EACH.matchEntire(sentence)?.let { m ->
            val on = when {
                isSelf(m.groupValues[3]) -> Objects.Self
                m.groupValues[3] == "it" -> context.it ?: return null
                else -> return null
            }
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val per = Value.Count(Objects.Each(Filters.noun(m.groupValues[4]) ?: return null))
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(on, kind, if (n == 1) per else Value.Times(per, n))))
        }
        // blb chunk 03: "If you control a Squirrel or returned a Squirrel card to your hand this way, create a Food token."
        Blb.IF_CONTROL_OR_RETURNED.matchEntire(sentence)?.let { m ->
            val chosen = context.chosen.lastOrNull()?.let { Objects.Bound(it) } ?: Objects.Bound("chosen")
            val control = Filters.noun(m.groupValues[1] + " you control") ?: return null
            val returned = Filters.noun(m.groupValues[2]) ?: return null
            val then = effect(m.groupValues[3].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Or(listOf(arcanum.api.Condition.Exists(control, atLeast = 1),
                arcanum.api.Condition.ObjectMatches(chosen, returned))), then = then))
        }
        // blb chunk 02: "If the gift was promised, [effect]." Its targets are chosen only if the gift was promised (CR
        // 702.174m); "… and that creature isn't legendary" is checked as it resolves.
        Blb.GIFT_IF.matchEntire(sentence)?.let { m ->
            val gift = if (m.groupValues[1] == "was") Blb.promised else arcanum.api.Condition.Not(Blb.promised)
            val before = context.targets.size
            val then = effect(m.groupValues[3].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            val more = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { itCondition(it, context) ?: return null }
            for (i in before until context.targets.size) context.targets[i] = context.targets[i].copy(onlyIf = gift)
            return listOf(Step.If(if (more == null) gift else arcanum.api.Condition.And(listOf(gift, more)), then = then))
        }
        // blb chunk 02: "[Effect] if the gift was promised."
        Blb.IF_GIFT_AFTER.matchEntire(sentence)?.let { m -> return blbEffect("If the gift ${m.groupValues[2]} promised, " +
            m.groupValues[1].replaceFirstChar { it.lowercase() } + ".", context) }
        // blb chunk 02: "That creature also gains indestructible until end of turn.", "Blossom Burst also deals 3 damage …":
        // "also" adds to what the sentence before did
        Blb.ALSO.matchEntire(sentence)?.let { m -> effect(m.groupValues[1] + " " + m.groupValues[2], context)?.let { return it } }
        // blb chunk 02: "You and permanents you control gain hexproof until end of turn." (CR 702.11b, c)
        Blb.YOU_AND_GAIN.matchEntire(sentence)?.let { m ->
            val objects = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeTargeted(by = ObjectFilter.ControlledBy(PlayerFilter.Opponent))),
                    Affected.Players(Players.You), arcanum.api.Duration.EndOfTurn)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Hexproof))),
                    Affected.Objects(Objects.Each(objects)), arcanum.api.Duration.EndOfTurn)))
        }
        // blb chunk 02: "Return up to two target creature cards each with mana value 2 or less from your graveyard to the
        // battlefield.", "return two target permanent cards from your graveyard to your hand"
        Blb.RETURN_COUNTED_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val upTo = m.groupValues[1].isNotEmpty()
            // chunk 11's rule reads "up to N … to your hand"
            if (upTo && m.groupValues[4] == "your hand") return@let
            val n = Numbers.count(m.groupValues[2]) ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[3].replace(" each with ", " with ")) ?: return null,
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val name = context.name("card")
            context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(if (upTo) 0 else n), max = Value.Fixed(n))
            val to = if (m.groupValues[4] == "the battlefield") Zone.Battlefield else Zone.Hand
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(to))))
        }
        // blb chunk 02: "Destroy two target artifacts and/or enchantments."
        Blb.DESTROY_N_TARGETS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[2].replace(" and/or ", " or ")
            val name = context.name(Filters.slotName(noun))
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(n), max = Value.Fixed(n))
            return listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        // blb chunk 02: "Destroy all creatures." (CR 701.8)
        Blb.DESTROY_ALL.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.Destroy(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null)))) }
        // blb chunk 02: "Tap enchanted creature." ("it" is then that creature)
        if (sentence == "Tap enchanted creature.") {
            context.it = Objects.AttachedTo(of = Objects.Self)
            return listOf(Step.Do(Effect.Tap(Objects.AttachedTo(of = Objects.Self))))
        }
        // blb chunk 02: "Brushfire Wail deals 1 damage to any target and 2 damage to each creature."
        Blb.DEALS_TO_ANY_AND_EACH.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val each = Filters.noun(m.groupValues[4]) ?: return null
            context.anyTarget()
            return listOf(
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = "any"), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)),
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Each(each)), Value.Fixed(m.groupValues[3].toInt()), source = Objects.Self)))
        }
        // blb chunk 02: "Blossom Burst deals 3 damage to that creature's controller."
        Blb.DEALS_TO_THAT_CREATURES_CONTROLLER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val creature = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.ControllerOf(creature)), Value.Fixed(m.groupValues[2].toInt()),
                source = Objects.Self)))
        }
        // blb chunk 02: "Create a token that's a copy of that creature, except it's 1/1." (CR 707.9b)
        Blb.COPY_OF_THAT_EXCEPT_PT.matchEntire(sentence)?.let { m ->
            val of = context.it ?: return null
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(of, except = listOf(
                arcanum.api.CopyException.PowerToughness(m.groupValues[1].toInt(), m.groupValues[2].toInt()))), count = Value.Fixed(1), controller = Players.You)))
        }
        // blb chunk 02: "Return a creature card put into your graveyard this way to the battlefield under your control."
        Blb.RETURN_PUT_INTO_GRAVEYARD_THIS_WAY.matchEntire(sentence)?.let { m ->
            val from = context.thisWay ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))))
        }
        // blb chunk 02: "[Effect] at the beginning of the next end step." (a delayed trigger, CR 603.7)
        Blb.AT_NEXT_END_STEP.matchEntire(sentence)?.takeIf { it.groupValues[1] != "Return it to the battlefield under its owner's control" }?.let { m ->
            val targets = context.targets.size
            val inner = effect(m.groupValues[1] + ".", context)?.takeIf { context.targets.size == targets } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = inner))))
        }
        // blb chunk 02: "Return that card to the battlefield under its owner's control with a +1/+1 counter on it."
        Blb.RETURN_UNDER_OWNERS_CONTROL_WITH_COUNTERS.matchEntire(sentence)?.let { m ->
            val card = context.it ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield, controller = Players.OwnerOf(card),
                counters = listOf(arcanum.api.CountersToPut(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))), postBind = "returned")))
        }
        // blb chunk 02: "Target opponent sacrifices a creature with the greatest power among creatures they control." (CR 701.21a)
        Blb.TARGET_SACRIFICES_GREATEST.matchEntire(sentence)?.let { m ->
            val player = playersIn("target " + m.groupValues[1], context) ?: return null
            val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(player))
            val power = m.groupValues[3] == "power"
            val among = Filters.noun(m.groupValues[4]) ?: return null
            val greatest = Value.Of(if (power) arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.ManaValue,
                Objects.Each(Filters.all(among, theirs)), arcanum.api.Combine.Greatest)
            val test = if (power) ObjectFilter.Power(arcanum.api.Comparison.Equal, greatest) else ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, greatest)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(Filters.noun(m.groupValues[2]) ?: return null, theirs, test), Value.Fixed(1), Value.Fixed(1)),
                    bind = "sacrificed", chooser = player),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))
        }
        // blb chunk 02: "Choose target creature you control and target creature you don't control." (targets for later
        // sentences: "the creature you control", "those creatures"); a second slot of the same noun is "other …" (as dsk chunk 07)
        Blb.CHOOSE_TARGETS.matchEntire(sentence)?.let { m ->
            listOf(m.groupValues[1], m.groupValues[2]).forEach { phrase ->
                val name = Filters.slotName(phrase).let { if (context.targets.any { t -> t.name == it }) "other $it" else it }
                context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(phrase) ?: return null), min = Value.Fixed(1), max = Value.Fixed(1))
                context.slotPhrases[phrase] = name
            }
            return emptyList()
        }
        // blb chunk 02: "Put a +1/+1 counter on the creature you control." (a target chosen before)
        Blb.PUT_COUNTERS_ON_THE.matchEntire(sentence)?.let { m ->
            val name = context.slotPhrases[m.groupValues[3]] ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(Objects.Target(name), kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // blb chunk 02: "Those creatures fight each other." (CR 701.14a)
        if (sentence == "Those creatures fight each other.") {
            val slots = context.targets.filter { it.what is TargetKind.Objects }.takeLast(2).takeIf { it.size == 2 } ?: return null
            return listOf(Step.Do(Effect.Fight(Objects.Target(slots[0].name), Objects.Target(slots[1].name))))
        }
        // blb chunk 02: "Exile that card." (the card chosen from a revealed hand)
        if (sentence == "Exile that card." && context.chosen.isNotEmpty()) {
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(Objects.Bound(context.chosen.last()), Destination(Zone.Exile), postBind = "exiled")))
        }
        // blb chunk 02: "You may cast that card for as long as it remains exiled, and mana of any type can be spent to cast
        // it." (CR 601.2a; D49: "for as long as it remains there" lasts indefinitely)
        if (sentence == "You may cast that card for as long as it remains exiled, and mana of any type can be spent to cast it.") {
            val card = context.it ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, manaAsAnyColor = true, holder = PlayerFilter.You)),
                Affected.Objects(card), arcanum.api.Duration.Indefinitely)))
        }
        // blb chunk 01: "Target Mouse you control gains your choice of double strike or trample until end of turn." (chosen as
        // it resolves, CR 608.2d)
        Blb.GAINS_YOUR_CHOICE.matchEntire(sentence)?.let { m ->
            val keywords = listOf(m.groupValues[2], m.groupValues[3]).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            fun gain(keyword: arcanum.api.Keyword) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(keyword))),
                Affected.Objects(target), arcanum.api.Duration.EndOfTurn))
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf(m.groupValues[2], m.groupValues[3])), bind = "keyword", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("keyword", m.groupValues[2]), then = listOf(gain(keywords[0])), otherwise = listOf(gain(keywords[1]))))
        }
        // blb chunk 01: "Tap up to one target creature", "Tap target creature an opponent controls"; "it" is then the target
        Blb.TAP_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val slot = Filters.slotName(noun)
            val upTo = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Numbers.count(it) ?: return null }
            context.targets += TargetSlot(slot, TargetKind.Objects(Filters.noun(noun) ?: return null),
                min = Value.Fixed(if (upTo == null) 1 else 0), max = Value.Fixed(upTo ?: 1))
            context.it = Objects.Target(slot)
            return listOf(Step.Do(Effect.Tap(Objects.Target(slot))))
        }
        // blb chunk 01: "Target opponent loses 3 life unless they sacrifice a nonland permanent of their choice or discard a
        // card." (CR 118.12a: the player named before may pay one of the costs)
        Blb.UNLESS_THEY.matchEntire(sentence)?.let { m ->
            val options = m.groupValues[2].split(" or ").map { option ->
                Cost(costPart(option.removeSuffix(" of their choice").replaceFirstChar { it.uppercase() }) ?: return null)
            }
            val steps = effect(m.groupValues[1] + ".", context) ?: return null
            val payer = context.lastPlayer ?: return null
            return unlessPays(payer, options.singleOrNull() ?: Cost(listOf(CostPart.OneOf(options))), steps)
        }
        return null
    }

    // ---- blb gaps 2: Bloomburrow wordings of the set's last cards (sets/blb/BlbChunk15*Test.kt)

    /** blb gaps 2: effect sentences; null when none reads [sentence]. */
    private fun blbGaps2Effect(sentence: String, context: Context): List<Step>? {
        // "You draw a card, gain 1 life, and put a +1/+1 counter on Helga." (three actions of one player, in order)
        BlbGaps2.YOU_THREE.matchEntire(sentence)?.let { m ->
            fun part(text: String) = effect("You $text.", context) ?: effect(text.replaceFirstChar { c -> c.uppercase() } + ".", context)
            val parts = (1..3).map { part(m.groupValues[it]) ?: return@let }
            return parts.flatten()
        }
        // "Have target opponent draw a card." (in "You may have target opponent draw a card. If you do, …"): that player does it
        BlbGaps2.HAVE_PLAYER_DO.matchEntire(sentence)?.let { m ->
            return effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + " " + m.groupValues[2] + "s " + m.groupValues[3] + ".", context)
        }
        // "Target opponent draws a card."
        BlbGaps2.TARGET_OPPONENT_DRAWS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Draw(context.targetOpponent(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // 'Create X 1/1 black Rat creature tokens with "…"' (X defined by "where X is", read as the instructions are)
        BlbGaps2.CREATE_X_TOKENS.matchEntire(sentence)?.let { m ->
            val (_, token) = Tokens.spec("two " + m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Announced(name = "X"), controller = Players.You)))
        }
        // "Exile the top X cards of your library." ("those cards"; X of the spell, or the permanent's spell, CR 107.3m)
        if (sentence == "Exile the top X cards of your library.") {
            context.them = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Announced(name = "X"), fromTop = true), Destination(Zone.Exile),
                postBind = "exiled")))
        }
        // "Exile cards from the top of your library until you exile a nonland card." (each card exiled as it is turned over;
        // "that card" is the one found)
        BlbGaps2.EXILE_UNTIL.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            context.it = Objects.Bound(FOUND)
            return listOf(Step.Do(Effect.RevealUntil(filter, library = Players.You, count = Value.Fixed(1), reveal = true, into = Zone.Exile,
                foundBind = FOUND, restBind = REST)))
        }
        // "You may cast that card by discarding a card rather than paying its mana cost." (an alternative cost, CR 118.9,
        // cast while the ability resolves, CR 608.2g)
        BlbGaps2.MAY_CAST_IT_BY.matchEntire(sentence)?.let { m ->
            val card = context.it ?: return null
            val parts = m.groupValues[1].split(" and ").map { gerund ->
                val verb = GERUNDS.keys.firstOrNull { gerund.startsWith("$it ") } ?: return null
                costPart((GERUNDS.getValue(verb) + " " + gerund.removePrefix("$verb ")).trim()) ?: return null
            }.flatten()
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(card, PlayKind.Cast, cost = Cost(parts), caster = Players.You))), player = Players.You))
        }
        // "You may put a Bird creature card from your hand or graveyard onto the battlefield with a finality counter on it."
        BlbGaps2.MAY_PUT_FROM_HAND_OR_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val counters = m.groupValues[2].takeIf { it.isNotEmpty() }?.let {
                listOf(arcanum.api.CountersToPut(Counters.kind(it) ?: return null, Value.Fixed(1)))
            }.orEmpty()
            val where = ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Graveyard)))
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, where, ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, counters = counters))))
        }
        return null
    }

    /** blb gaps 2: static wordings; null when none reads [line]. */
    private fun blbGaps2Static(line: String): Ability? {
        // "If a creature an opponent controls would die, exile it instead." (CR 614.1a, 700.4)
        BlbGaps2.IF_WOULD_DIE_EXILE.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))
        }
        return null
    }

    /** Regular expressions of the blb gaps 2 wordings. */
    // ---- rtr: Return to Ravnica wordings (the set's parser tests, sets/rtr/RtrChunk*Test.kt). Each rule is tried first
    // where it is hooked ([static], [effect], [modify], [trigger], [costPart]) and falls through when it can't read.

    /** rtr: static abilities. */
    private fun rtrStatic(line: String): Ability? {
        // rtr chunk 01: "Enchanted creature can't attack or block, and its activated abilities can't be activated." (an Arrest)
        Rtr.CANT_ATTACK_OR_BLOCK_OR_ACTIVATE.matchEntire(line)?.let { m ->
            val affects = rtrSubject(m.groupValues[1]) ?: return@let
            return Ability.Static(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true)), affects = affects)
        }
        // rtr chunk 02: 'Enchanted land has "{T}: …"', 'Enchanted creature gets +2/+1 and has "{R}: …"' (layers 6, 7c; the granted
        // ability's "this creature"/"this land" is the object that has it, CR 113.10)
        Rtr.HAS_QUOTED.matchEntire(line)?.let { m ->
            val affects = rtrSubject(m.groupValues[1]) ?: return@let
            val granted = Sentences(setOf("this creature", "this land", "this permanent", "this artifact")).line(m.groupValues[4]) ?: return null
            val pump = m.groupValues[2].takeIf { it.isNotEmpty() }?.let {
                Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)
            }
            return Ability.Static(listOfNotNull(pump, Mechanic.GainAbilities(listOf(granted))), affects = affects)
        }
        // rtr chunk 03: "If a card or token would be put into a graveyard from anywhere, exile it instead." (any graveyard, CR 614.1a)
        Rtr.WOULD_BE_PUT_INTO_A_GRAVEYARD.matchEntire(line)?.let { m ->
            val what = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(what, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 03: "All damage that would be dealt to you and other permanents you control is dealt to this creature instead."
        // (a redirection, CR 614.1a, 614.9)
        Rtr.DAMAGE_DEALT_TO_SELF_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val permanents = Filters.noun(m.groupValues[1].removePrefix("other ")) ?: return null
            val objects = if (m.groupValues[1].startsWith("other ")) Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), permanents) else permanents
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = objects, players = PlayerFilter.You)),
                modify = listOf(Modification.ChangeRecipient(arcanum.api.Recipients(objects = Objects.Self))))), affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 05: "This creature can attack as though it didn't have defender as long as you control a Gate." (CR 702.3b)
        Rtr.ATTACK_DESPITE_DEFENDER_AS_LONG_AS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CanAttackDespiteDefender), affects = Affected.Objects(Objects.Self),
                condition = Conditions.condition(m.groupValues[2], self) ?: rtrCondition(m.groupValues[2]) ?: return null)
        }
        // rtr chunk 06: "This creature has trample as long as it has a +1/+1 counter on it."
        Rtr.HAS_KEYWORDS_AS_LONG_AS_COUNTER.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = Affected.Objects(Objects.Self),
                condition = arcanum.api.Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(kind, atLeast = 1)))
        }
        // rtr chunk 06: "All creatures able to block this creature do so." (a blocking requirement, CR 509.1c)
        Rtr.ALL_ABLE_TO_BLOCK_DO_SO.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.MustBlock(ObjectFilter.Is(Objects.Self))), affects = Affected.Objects(Objects.Each(ObjectFilter.Type(CardType.Creature))))
        }
        // rtr chunk 06: "During turns other than yours, tapped creatures you control have hexproof."
        Rtr.DURING_OTHER_TURNS_HAVE.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = Affected.Objects(Objects.Each(filter)),
                condition = arcanum.api.Condition.Not(arcanum.api.Condition.YourTurn))
        }
        // rtr chunk 06: "If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are
        // put on it instead." (CR 614.1a)
        Rtr.TWICE_THAT_MANY_COUNTERS.matchEntire(line)?.takeIf { it.groupValues[1] == it.groupValues[3] }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2].removeArticle()) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.CountersPut(filter, kind), modify = listOf(Modification.MultiplyAmount(2)))),
                affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 07: "Enchanted creature gets +1/+1 for each card in its controller's hand." (layer 7c)
        Rtr.GETS_FOR_EACH_CARD_IN_CONTROLLERS_HAND.matchEntire(line)?.let { m ->
            val affects = rtrSubject(m.groupValues[1]) ?: return@let
            val objects = (affects as? Affected.Objects)?.objects ?: return null
            val per = Value.HandSize(Players.ControllerOf(objects))
            fun amount(n: Int) = when (n) { 0 -> Value.Fixed(0); 1 -> per; else -> Value.Times(per, n) }
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[2].toInt()), amount(m.groupValues[3].toInt()))), affects = affects)
        }
        // rtr chunk 07: "Equipped creature gets +1/+0 for each of its colors." (CR 105.2: the number of colours it is)
        Rtr.GETS_FOR_EACH_OF_ITS_COLORS.matchEntire(line)?.let { m ->
            val affects = rtrSubject(m.groupValues[1]) ?: return@let
            val objects = (affects as? Affected.Objects)?.objects ?: return null
            val per = Value.CountDistinct(objects, arcanum.api.SharedQuality.Color)
            fun amount(n: Int) = when (n) { 0 -> Value.Fixed(0); 1 -> per; else -> Value.Times(per, n) }
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[2].toInt()), amount(m.groupValues[3].toInt()))), affects = affects)
        }
        // rtr chunk 09: "As this artifact enters, choose a card name." (any name, CR 201.3; kept on the permanent, CR 607.2d)
        Rtr.AS_ENTERS_CHOOSE_A_CARD_NAME.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(arcanum.api.Choice.CardName(ObjectFilter.Any), bind = CHOSEN_NAME, remember = true,
                    chooser = Players.You)))))), affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 09: "Activated abilities of sources with the chosen name can't be activated unless they're mana abilities."
        Rtr.CANT_BE_ACTIVATED_UNLESS_MANA.matchEntire(line)?.let { m ->
            val objects = chosenObjects(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CantBeActivated(manaAbilities = false)), affects = Affected.Objects(objects))
        }
        // rtr chunk 09: "As this artifact enters, choose two colors." (kept on the permanent, CR 607.2d)
        Rtr.AS_ENTERS_CHOOSE_COLORS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val count = Numbers.count(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(arcanum.api.Choice.Colors(count), bind = CHOSEN_COLOR, remember = true,
                    chooser = Players.You)))))), affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 07: "Creatures can't attack you or planeswalkers you control unless their controller pays {X} for each of those
        // creatures, where X is the number of enchantments you control." (a cost to attack, CR 508.1d, 508.1h)
        Rtr.CANT_ATTACK_UNLESS_PAYS.matchEntire(line)?.let { m ->
            val attackers = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val walkers = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null }
            val mana = ManaCost.parse(m.groupValues[3]) ?: return null
            val x = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { Values.value(it) ?: return null }
            return Ability.Static(listOf(Mechanic.AttackCost(Cost(listOf(CostPart.Mana(mana, x = x))),
                target = arcanum.api.Recipients(objects = walkers?.let { Objects.Each(it) }, players = Players.You))),
                affects = Affected.Objects(Objects.Each(attackers)))
        }
        // rtr chunk 05: "This creature enters with X +1/+1 counters on it, where X is the total life lost by your opponents this turn."
        // (CR 614.1c)
        Rtr.ENTERS_WITH_X_COUNTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val x = Values.value(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, x)))), affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 05: "You can't cast Rakdos unless an opponent lost life this turn." (CR 601.3)
        Rtr.CANT_CAST_SELF_UNLESS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CastRestriction(condition = Conditions.condition(m.groupValues[2], self) ?: return null)),
                affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 05: "Creature spells you cast cost {1} less to cast for each 1 life your opponents have lost this turn."
        Rtr.COST_LESS_FOR_EACH_LIFE_LOST.matchEntire(line)?.let { m ->
            val spells = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val mana = ManaCost.parse(m.groupValues[2]) ?: return null
            val who = if (m.groupValues[3] == "your opponents have") PlayerFilter.Opponent else PlayerFilter.You
            return Ability.Static(listOf(Mechanic.CostChange(spells, arcanum.api.CostChange.Less(mana,
                Value.EventsThisTurn(EventPattern.LoseLife(who), arcanum.api.Measure.Amount)))), affects = Affected.Objects(Objects.Self))
        }
        // rtr chunk 06: "If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield
        // instead of putting it into your graveyard." (works in the hand, CR 113.6; a discard caused by an opponent's spell or
        // ability, CR 701.9)
        Rtr.OPPONENT_CAUSES_DISCARD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You,
                causedBy = ObjectFilter.ControlledBy(PlayerFilter.Opponent)),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Battlefield))))), affects = Affected.Objects(Objects.Self),
                functionsIn = listOf(Zone.Hand))
        }
        // rtr chunk 09: "Untap this land during each other player's untap step." (CR 502.3)
        Rtr.UNTAP_DURING_OTHER_UNTAP_STEPS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1].replaceFirstChar { c -> c.lowercase() }) }?.let {
            return Ability.Static(listOf(Mechanic.UntapsDuringOtherUntapSteps), affects = Affected.Objects(Objects.Self))
        }
        return null
    }

    /**
     * rtr chunk 08: a sentence that names a target the way an earlier one did ("Put a +1/+1 counter on target creature. Put a
     * +1/+1 counter on target creature.") has a target of its own (CR 115.3: each instance of the word "target" is one; the
     * same object may be chosen for both): its slot is renamed "second creature" ("third …"), and its steps refer to that.
     */
    private fun renameRepeatedTargets(steps: List<Step>, context: Context, from: Int): List<Step> {
        var result = steps
        for (i in from until context.targets.size) {
            val slot = context.targets[i]
            if (context.targets.take(i).none { it.name == slot.name }) continue
            val renamed = nextTargetName(slot.name, context.targets.map { it.name })
            context.targets[i] = slot.copy(name = renamed)
            result = result.map { renameTargetSlot(it, slot.name, renamed) }
        }
        return result
    }

    /** rtr: the subject of a static ability ("Enchanted creature", "Enchanted land", "Equipped creature", this object). */
    private fun rtrSubject(subject: String): Affected? = when {
        isSelf(subject) -> Affected.Objects(Objects.Self)
        Rtr.ATTACHED_SUBJECT.matches(subject) -> Affected.Objects(Objects.AttachedTo(Objects.Self))
        else -> null
    }

    /** rtr: conditions the shared reader doesn't know yet. */
    private fun rtrCondition(text: String): arcanum.api.Condition? {
        // rtr chunk 06: "this enchantment has no charge counters on it"
        Rtr.SELF_HAS_NO_COUNTERS.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return arcanum.api.Condition.Not(arcanum.api.Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(kind, atLeast = 1)))
        }
        // rtr chunk 07: "this creature has five or more filibuster counters on it"
        Rtr.SELF_HAS_N_OR_MORE_COUNTERS.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return arcanum.api.Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(kind, atLeast = Numbers.count(m.groupValues[2]) ?: return null))
        }
        // rtr chunk 09: "it's at least one of the chosen colors" (the spell of a cast trigger)
        if (text == "it's at least one of the chosen colors")
            return arcanum.api.Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.OfColor(ColorRef.Remembered(CHOSEN_COLOR)))
        return null
    }

    /** rtr: effect sentences. */
    private fun rtrEffect(sentence: String, context: Context): List<Step>? {
        // rtr chunk 01: "Detain target creature an opponent controls.", "Detain up to two target nonland permanents your opponents
        // control." (CR 701.35a)
        Rtr.DETAIN.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val name = context.name(Filters.slotName(noun))
            val max = if (m.groupValues[1].isEmpty()) 1 else Numbers.count(m.groupValues[1].removePrefix("up to ")) ?: return null
            context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0), max = Value.Fixed(max))
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Detain(Objects.Target(name)))))
        }
        // rtr chunk 01: "Populate." (CR 701.36a), also after another effect ("…, then populate.")
        if (sentence == "Populate.") return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Populate)))
        Rtr.THEN_POPULATE.matchEntire(sentence)?.let { m ->
            val before = effect(m.groupValues[1] + ".", context) ?: return null
            return before + Step.Do(Effect.Action(arcanum.api.KeywordAction.Populate))
        }
        // rtr chunk 04: "Regenerate this creature.", "Regenerate each creature you control." (CR 701.19a)
        Rtr.REGENERATE.matchEntire(sentence)?.let { m ->
            val what = m.groupValues[1]
            val objects = when {
                isSelf(what) -> Objects.Self
                what.startsWith("each ") -> Objects.Each(Filters.noun(what.removePrefix("each ")) ?: return null)
                what.startsWith("target ") -> context.target(Filters.slotName(what.removePrefix("target ")), Filters.noun(what.removePrefix("target ")) ?: return null)
                else -> return null
            }
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Regenerate(objects))))
        }
        // rtr chunk 02: "Target player gains 7 life."
        Rtr.TARGET_PLAYER_GAINS_LIFE.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.GainLife(context.targetPlayer(), Value.Fixed(m.groupValues[1].toInt()))))
        }
        // rtr chunk 05: "Its controller loses 2 life.", "Its controller discards a card." after an effect on a target: the target's
        // controller, as it last existed once it has left (CR 608.2h)
        // rtr chunk 03: "Its controller mills three cards." in a trigger on an object (the event's object's controller)
        Rtr.ITS_CONTROLLER.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull { it.what !is TargetKind.Players }?.let { Objects.Target(it.name) }
                ?: context.that?.takeIf { context.event != null } ?: return@let
            val player = Players.ControllerOf(target)
            Rtr.LOSES_LIFE.matchEntire(m.groupValues[1])?.let { l -> return listOf(Step.Do(Effect.LoseLife(player, Value.Fixed(l.groupValues[1].toInt())))) }
            Rtr.MILLS.matchEntire(m.groupValues[1])?.let { l -> return listOf(Step.Do(Effect.Mill(player, Value.Fixed(Numbers.count(l.groupValues[1]) ?: return null)))) }
            if (m.groupValues[1] == "discards a card") return listOf(Step.Do(Effect.Discard(player, Value.Fixed(1), ObjectFilter.Any)))
        }
        // rtr chunk 02: "Until end of turn, target creature gets +1/+0 and gains first strike and lifelink." and the other
        // wordings of a pump the shared rules don't read (layers 6, 7c; CR 611.2c)
        rtrPump(sentence, context)?.let { return it }
        // rtr chunk 02: "Untap that creature." after an effect on a target or "it"
        if (sentence == "Untap that creature.") {
            val that = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: context.it ?: return null
            return listOf(Step.Do(Effect.Untap(that)))
        }
        // rtr chunk 03: "Exile all graveyards." (every card in every graveyard)
        if (sentence == "Exile all graveyards.") return listOf(Step.Do(Effect.Move(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), Destination(Zone.Exile))))
        // rtr chunk 05: "Exile target player's graveyard."
        if (sentence == "Exile target player's graveyard.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.Move(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))),
                Destination(Zone.Exile))))
        }
        // rtr chunk 03: "Prevent the next 1 damage that would be dealt to you this turn." (CR 615.7)
        Rtr.PREVENT_NEXT_TO_YOU.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.PreventDamage(arcanum.api.Recipients(players = Players.You), Value.Fixed(m.groupValues[1].toInt()),
                from = ObjectFilter.Any, duration = arcanum.api.Duration.EndOfTurn)))
        }
        // rtr chunk 03: "Prevent all combat damage that would be dealt to you this turn." (CR 615.1a)
        if (sentence == "Prevent all combat damage that would be dealt to you this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
                RecipientFilter(players = PlayerFilter.You), combat = true))), null, arcanum.api.Duration.EndOfTurn)))
        }
        // rtr chunk 03: "That player sacrifices it unless they pay {X}, where X is its mana value." in an upkeep trigger of an
        // Aura: the enchanted permanent (CR 118.12a)
        Rtr.THAT_PLAYER_SACRIFICES_UNLESS_PAYS_X.matchEntire(sentence)?.let {
            val it = context.it ?: return null
            if (context.event !is EventPattern.StepBegins) return null
            val cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Of(arcanum.api.NumericProperty.ManaValue, it, arcanum.api.Combine.Single))))
            return unlessPays(Players.EventPlayer, cost, listOf(Step.Do(Effect.Sacrifice(it))))
        }
        // rtr chunk 01: "Sacrifice it unless you return another creature you control to its owner's hand.", "Sacrifice it unless
        // you pay {1}." (CR 118.12a)
        Rtr.SACRIFICE_IT_UNLESS_YOU.matchEntire(sentence)?.let { m ->
            val it = if (m.groupValues[1] == "it") context.it ?: return null else if (isSelf(m.groupValues[1])) Objects.Self else return null
            val parts = costPart(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }) ?: costPart(m.groupValues[2]) ?: return null
            return unlessPays(Players.You, Cost(parts), listOf(Step.Do(Effect.Sacrifice(it))))
        }
        // rtr chunk 01: "You may exchange control of this enchantment and target permanent you neither own nor control." (CR 701.12)
        Rtr.EXCHANGE_CONTROL_OF_SELF_AND_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val noun = m.groupValues[3]
            val filter = Rtr.NEITHER_OWN_NOR_CONTROL.matchEntire(noun)?.let {
                Filters.all(Filters.noun(it.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.OwnedBy(PlayerFilter.You)),
                    ObjectFilter.Not(ObjectFilter.ControlledBy(PlayerFilter.You)))
            } ?: Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun.substringBefore(" you neither")), filter)
            val exchange = Step.Do(Effect.ExchangeControl(Objects.Self, target))
            return listOf(if (m.groupValues[1].isEmpty()) exchange else Step.May(listOf(exchange), player = Players.You))
        }
        // rtr chunk 01: "That player mills that many cards." after combat damage to a player (the damage dealt)
        if (sentence == "That player mills that many cards." && context.event is EventPattern.Damage)
            return listOf(Step.Do(Effect.Mill(Players.EventPlayer, Value.EventAmount)))
        // rtr chunk 01: "Target player mills X cards." (X defined by the ability)
        if (sentence == "Target player mills X cards.") return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Announced(name = "X"))))
        // rtr chunk 05: "That player discards a card at random.", "That player loses 2 life.", "That player draws an additional card.",
        // "That player loses half their life, rounded up.", "That player loses the game." (the event's player)
        Rtr.THAT_PLAYER.matchEntire(sentence)?.takeIf { context.event != null }?.let { m ->
            val player = Players.EventPlayer
            Rtr.LOSES_LIFE.matchEntire(m.groupValues[1])?.let { l -> return listOf(Step.Do(Effect.LoseLife(player, Value.Fixed(l.groupValues[1].toInt())))) }
            return when (m.groupValues[1]) {
                "discards a card at random" -> listOf(Step.Do(Effect.Discard(player, Value.Fixed(1), ObjectFilter.Any, random = true)))
                "draws an additional card" -> listOf(Step.Do(Effect.Draw(player, Value.Fixed(1))))
                "loses half their life, rounded up" -> listOf(Step.Do(Effect.LoseLife(player, Value.Half(Value.LifeOf(player), roundUp = true))))
                "loses the game" -> listOf(Step.Do(Effect.LoseGame(player)))
                else -> null
            }
        }
        // rtr chunk 05: "Put it on top of its owner's library." (in a dies trigger: the card in the graveyard, CR 400.7)
        // rtr chunk 08: "Put target attacking or blocking creature on top of its owner's library."
        Rtr.PUT_ON_TOP_OF_LIBRARY.matchEntire(sentence)?.let { m ->
            val what = m.groupValues[1]
            val objects = when {
                what == "it" -> triggerIt?.first ?: context.it ?: return null
                what.startsWith("target ") -> context.target(Filters.slotName(what.removePrefix("target ")), Filters.noun(what.removePrefix("target ")) ?: return null)
                else -> return null
            }
            return listOf(Step.Do(Effect.Move(objects, Destination(Zone.Library, libraryPosition = 0))))
        }
        // rtr chunk 05: "Its controller reveals cards from the top of their library until they reveal a land card, then puts those
        // cards into their graveyard." after destroying a target (CR 701.20)
        Rtr.ITS_CONTROLLER_REVEALS_UNTIL.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val player = Players.ControllerOf(target)
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(
                Step.Do(Effect.RevealUntil(filter, library = player, count = Value.Fixed(1), reveal = true, foundBind = "found", restBind = "rest")),
                Step.Do(Effect.Move(Objects.Union(listOf(Objects.Bound("rest"), Objects.Bound("found"))), Destination(Zone.Graveyard))))
        }
        // rtr chunk 05: "Return it to the battlefield under your control with an additional +1/+1 counter on it at the beginning of the
        // next end step." in a dies trigger (a delayed trigger, CR 603.7; the card in the graveyard, named now, CR 603.7c)
        Rtr.RETURN_IT_UNDER_YOUR_CONTROL_AT_NEXT_END_STEP.matchEntire(sentence)?.let { m ->
            val it = triggerIt?.first ?: (context.event as? EventPattern.ZoneChange)?.takeIf { e -> e.to == Zone.Graveyard }?.let { Objects.EventObject(EventRole.After) } ?: return null
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            context.it = Objects.Bound("returned")
            return listOf(Step.Bind(it, "dead"), Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound("dead"), Destination(Zone.Battlefield, controller = Players.You,
                    counters = listOf(arcanum.api.CountersToPut(kind, Value.Fixed(1)))), postBind = "returned")))))))
        }
        // rtr chunk 05: "Put that many +1/+1 counters on it." (the event's amount)
        Rtr.PUT_THAT_MANY_COUNTERS_ON_IT.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            return listOf(Step.Do(Effect.PutCounters(it, Counters.kind(m.groupValues[1]) ?: return null, Value.EventAmount)))
        }
        // rtr chunk 06: "[This] deals 3 damage to that player." (the event's player)
        Rtr.SELF_DEALS_TO_THAT_PLAYER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) && context.event != null }?.let { m ->
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.EventPlayer), Value.Fixed(m.groupValues[2].toInt()), Objects.Self)))
        }
        // rtr chunk 06: "If a creature dealt damage this way would die this turn, exile it instead." (a replacement for the turn,
        // CR 614.1a) — see rtrModify
        // rtr chunk 07: "All creatures get -1/-1 until end of turn."
        Rtr.ALL_GET_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
        }
        // rtr chunk 08: "Each creature deals 1 damage to its controller." (each creature a source, all at once, CR 120.3)
        Rtr.EACH_DEALS_TO_ITS_CONTROLLER.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val each = Objects.Bound("source")
            return listOf(Step.ForEach(arcanum.api.Each.Objects(Objects.Each(filter)), bind = "source", together = true, steps = listOf(
                Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.ControllerOf(each)), Value.Fixed(m.groupValues[2].toInt()), each)))))
        }
        // rtr chunk 06: "You gain life equal to that creature's toughness." (the event's creature, as it entered)
        Rtr.GAIN_LIFE_EQUAL_TO_THAT_CREATURES.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val property = if (m.groupValues[1] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.Toughness
            return listOf(Step.Do(Effect.GainLife(Players.You, Value.Of(property, that, arcanum.api.Combine.Single))))
        }
        // rtr chunk 06: "Search your library for up to three basic land cards, reveal them, put them into your hand, then shuffle."
        Rtr.SEARCH_UP_TO_REVEAL_INTO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), reveal = true,
                    zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.You)))
        }
        // rtr chunk 06: "Shuffle it into its owner's library." (in a trigger on this card being put into a graveyard: the new card)
        if (sentence == "Shuffle it into its owner's library.") {
            val it = triggerIt?.first ?: (context.event as? EventPattern.ZoneChange)?.let { Objects.EventObject(EventRole.After) } ?: return null
            return listOf(Step.Do(Effect.Move(it, Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.OwnerOf(it))))
        }
        // rtr chunk 08: "You may exile target nonland permanent not named Detention Sphere and all other permanents with the same
        // name as that permanent." (linked to "the exiled cards", CR 607.2a)
        Rtr.EXILE_TARGET_AND_SAME_NAME.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val named = Rtr.NOT_NAMED.matchEntire(noun)
            val filter = if (named != null) Filters.all(Filters.noun(named.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.Named(named.groupValues[2])))
                else Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(named?.groupValues?.get(1) ?: noun), filter)
            val others = Objects.Each(Filters.all(ObjectFilter.Not(ObjectFilter.Is(target)), Filters.noun(m.groupValues[3]) ?: return null, ObjectFilter.SameNameAs(target)))
            val exile = exile(Objects.Union(listOf(target, others)))
            return if (m.groupValues[1].isEmpty()) exile else listOf(Step.May(exile, player = Players.You))
        }
        // rtr chunk 01: "Return the exiled cards to their owners' hands.", "Return the exiled cards to the battlefield under their
        // owner's control." (the cards linked to this object, CR 607.2a)
        Rtr.RETURN_THE_EXILED_CARDS.matchEntire(sentence)?.let { m ->
            val cards = exiledCard ?: return null
            val to = if (m.groupValues[1] == "to their owners' hands") Destination(Zone.Hand) else Destination(Zone.Battlefield, controller = Players.OwnerOf(cards))
            return listOf(Step.Do(Effect.Move(cards, to)))
        }
        // rtr chunk 01: "You may exile up to three other target creatures from the battlefield and/or creature cards from graveyards."
        Rtr.EXILE_UP_TO_TARGETS_AND_OR_CARDS.matchEntire(sentence)?.let { m ->
            val permanents = Filters.noun(m.groupValues[3]) ?: return null
            val cards = Filters.noun(m.groupValues[4]) ?: return null
            val filter = ObjectFilter.Or(listOf(
                Filters.all(*(listOfNotNull(context.another().takeIf { m.groupValues[2].isNotEmpty() }, permanents, ObjectFilter.InZone(Zone.Battlefield))).toTypedArray()),
                Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard))))
            context.targets += TargetSlot("targets", TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(Numbers.count(m.groupValues[1].removePrefix("up to ")) ?: return null))
            return listOf(Step.May(exile(Objects.Target("targets")), player = Players.You))
        }
        rtrEffect2(sentence, context)?.let { return it }
        rtrEffect3(sentence, context)?.let { return it }
        return null
    }

    /** rtr: effect sentences, second part (kept apart for the JVM's method size limit). */
    private fun rtrEffect2(sentence: String, context: Context): List<Step>? {
        // rtr chunk 08: "Counter target creature spell. [This] deals damage equal to that spell's power to its controller."
        Rtr.DEALS_DAMAGE_EQUAL_TO_THAT_SPELLS_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            val spell = context.targets.lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.ControllerOf(spell)),
                Value.Of(arcanum.api.NumericProperty.Power, spell, arcanum.api.Combine.Single), Objects.Self)))
        }
        // rtr chunk 05: "It deals damage equal to its power to target player or planeswalker." (after sacrificing it: as it last
        // existed, CR 608.2h)
        Rtr.IT_DEALS_DAMAGE_EQUAL_TO_ITS_POWER.matchEntire(sentence)?.let { m ->
            val to = rtrTarget(m.groupValues[1], context) ?: return null
            return listOf(Step.Do(Effect.DealDamage(to, Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single), Objects.Self)))
        }
        // rtr chunk 08: "This creature deals 1 damage to target creature and each other creature with the same name as that creature."
        Rtr.DEALS_TO_TARGET_AND_SAME_NAME.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[3]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            val others = Objects.Each(Filters.all(ObjectFilter.Not(ObjectFilter.Is(target)), Filters.noun(m.groupValues[4]) ?: return null, ObjectFilter.SameNameAs(target)))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Union(listOf(target, others))), Value.Fixed(m.groupValues[2].toInt()), Objects.Self)))
        }
        // rtr chunk 06: "Each opponent loses life equal to the sacrificed creature's power." (as it last existed, CR 608.2h)
        Rtr.LOSES_LIFE_EQUAL_TO_SACRIFICED.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val property = if (m.groupValues[2] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.Toughness
            return listOf(Step.Do(Effect.LoseLife(players, Value.Of(property, Objects.Bound(SACRIFICED), arcanum.api.Combine.Single))))
        }
        // rtr chunk 06: "Return this card from your graveyard to your hand."
        Rtr.RETURN_SELF_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand))))
        }
        // rtr chunk 06: "Search your library for up to two creature cards and reveal them. Put one into your hand and the other into your
        // graveyard. Then shuffle." (the second sentence: one found card of your choice into your hand, the rest into the graveyard)
        Rtr.SEARCH_UP_TO_AND_REVEAL.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.Search(Players.You, filter, max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), reveal = true,
                zones = listOf(Zone.Library), bind = "found", searcher = Players.You)))
        }
        if (sentence == "Put one into your hand and the other into your graveyard.") {
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Destination(Zone.Graveyard))))
        }
        if (sentence == "Shuffle.") return listOf(Step.Do(Effect.Shuffle(Players.You)))
        // rtr chunk 05: "[This] deals X damage to target opponent or planeswalker. That player or that planeswalker's controller discards X
        // cards." (one of the two, whichever was targeted)
        Rtr.THAT_PLAYER_OR_PLANESWALKERS_CONTROLLER_DISCARDS.matchEntire(sentence)?.let { m ->
            val slot = context.targets.lastOrNull { it.what is TargetKind.Either } ?: return null
            val count = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Do(Effect.Discard(Players.Target(slot.name), count, ObjectFilter.Any)),
                Step.Do(Effect.Discard(Players.ControllerOf(Objects.Target(slot.name)), count, ObjectFilter.Any)))
        }
        // rtr chunk 05: "Target player who lost life this turn loses 1 life."
        Rtr.TARGET_PLAYER_WHO_LOST_LIFE_LOSES.matchEntire(sentence)?.let { m ->
            context.targets += TargetSlot("player", TargetKind.Players(PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any))),
                min = Value.Fixed(1), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.LoseLife(Players.Target("player"), Value.Fixed(m.groupValues[1].toInt()))))
        }
        // rtr chunk 05: "You gain life equal to the number of cards in that player's hand." after revealing a target player's hand
        if (sentence == "You gain life equal to the number of cards in that player's hand.") {
            val player = context.player ?: context.lastPlayer ?: return null
            return listOf(Step.Do(Effect.GainLife(Players.You, Value.HandSize(player))))
        }
        // rtr chunk 05: "Search target opponent's graveyard, hand, and library for any number of cards with that name and exile them.
        // Then that player shuffles."
        Rtr.SEARCH_OPPONENTS_ZONES_FOR_NAME.matchEntire(sentence)?.let {
            val name = context.cardName ?: return null
            val player = context.targetOpponent()
            context.player = player
            val all = Value.Count(Objects.Each(Filters.all(ObjectFilter.OwnedBy(PlayerFilter.Is(player)),
                ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Library))))))
            return listOf(
                Step.Do(Effect.Search(player, ObjectFilter.ChosenName(name), max = all, zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library), bind = "found",
                    searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Exile))))
        }
        if (sentence == "That player shuffles.") return listOf(Step.Do(Effect.Shuffle(context.player ?: context.lastPlayer ?: return null)))
        // rtr chunk 06: "Destroy all Auras attached to target land."
        Rtr.DESTROY_ALL_ATTACHED_TO_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Destroy(Objects.AttachmentsOf(target, Filters.noun(m.groupValues[1]) ?: return null))))
        }
        // rtr chunk 09: "[This] becomes a 2/2 white and blue Bird artifact creature with flying until end of turn." (a "[creature
        // types] artifact creature" keeps its other types, replacing its creature types, CR 205.1b; layers 4, 5, 6, 7b)
        rtrBecomes(sentence)?.let { return it }
        // rtr chunk 06: "Shuffle all cards from your graveyard into your library. Target player mills that many cards."
        if (sentence == "Shuffle all cards from your graveyard into your library.") {
            return listOf(Step.Do(Effect.Move(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                Destination(Zone.Library), preBind = "shuffled")), Step.Do(Effect.Shuffle(Players.You)))
        }
        // rtr chunk 07: "Put a filibuster counter on this creature. Then if this creature has five or more filibuster counters on it,
        // you win the game."; "You win the game."
        if (sentence == "You win the game.") return listOf(Step.Do(Effect.WinGame(Players.You)))
        Rtr.IF_CONDITION_THEN.matchEntire(sentence)?.let { m ->
            val condition = rtrCondition(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return listOf(Step.If(condition, then = then))
        }
        // rtr chunk 07: "Remove a filibuster counter from this creature." is read by the shared rules.
        return null
    }

    /** rtr chunk 05: "target player or planeswalker", "target opponent or planeswalker" (CR 115.4). */
    private fun rtrTarget(phrase: String, context: Context): arcanum.api.Recipients? {
        val m = Rtr.PLAYER_OR_PLANESWALKER.matchEntire(phrase) ?: return null
        val players = if (m.groupValues[1] == "opponent") PlayerFilter.Opponent else PlayerFilter.Any
        val name = "${m.groupValues[1]} or planeswalker"
        context.targets += TargetSlot(name, TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), players), min = Value.Fixed(1), max = Value.Fixed(1))
        return arcanum.api.Recipients(target = name)
    }

    /** rtr chunk 09: "[This] becomes a 2/2 white and blue Bird artifact creature with flying until end of turn.", "Until end of turn,
     * this artifact becomes a 2/1 blue and red Elemental artifact creature." (CR 205.1b: it keeps its other card types). */
    private fun rtrBecomes(sentence: String): List<Step>? {
        val text = sentence.removePrefix("Until end of turn, ").removeSuffix(".")
        val m = Rtr.SELF_BECOMES_ARTIFACT_CREATURE.matchEntire(text)?.takeIf { isSelf(it.groupValues[1].replaceFirstChar { c -> c.lowercase() }) } ?: return null
        if (text == sentence.removeSuffix(".") && m.groupValues[7].isEmpty()) return null
        if (text != sentence.removeSuffix(".") && m.groupValues[7].isNotEmpty()) return null
        val colors = m.groupValues[4].split(" and ").map { Tokens.color(it) ?: return null }
        val subtypes = m.groupValues[5].split(' ').map { s -> arcanum.api.SubtypeRef.Literal(Subtype(s.takeIf { it in RulesWords.subtypes } ?: return null)) }
        val keywords = m.groupValues[6].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }
        return listOf(Step.Do(Effect.ApplyMechanics(listOfNotNull(
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = subtypes, replaceSubtypes = true),
            Mechanic.SetColors(colors.map { ColorRef.Literal(it) }),
            Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt())),
            keywords?.let { Mechanic.GainAbilities(it) }), Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
    }

    /**
     * rtr chunk 02: "[Target X | Another target X | it] gets +A/+B [for each Y] [and gains K] [and can't be blocked this turn]
     * [and attacks this turn if able] until end of turn", with "until end of turn" anywhere or in front ("Until end of turn,
     * …"). Only the wordings other rules don't read: a leading "Until end of turn", "another target", "for each" with a gain,
     * "can't be blocked this turn" or "attacks this turn if able".
     */
    private fun rtrPump(sentence: String, context: Context): List<Step>? {
        val leading = sentence.startsWith("Until end of turn, ")
        val text = sentence.removePrefix("Until end of turn, ").removeSuffix(".")
        val m = Rtr.PUMP.matchEntire(text) ?: return null
        var tail = m.groupValues[4]
        if (!leading && !tail.contains("until end of turn")) return null
        val special = leading || m.groupValues[1].startsWith("Another target ") || m.groupValues[1].startsWith("another target ") ||
            tail.contains(" can't be blocked this turn") || tail.contains(" attacks this turn if able") || (tail.contains(" for each ") && tail.contains(" and gains "))
        if (!special) return null
        var per: Value? = null
        val keywords = mutableListOf<Ability>()
        val more = mutableListOf<Mechanic>()
        while (tail.isNotEmpty()) {
            when {
                tail.startsWith(" until end of turn") -> tail = tail.removePrefix(" until end of turn")
                tail.startsWith(" and can't be blocked this turn") -> { more += Mechanic.CantBeBlocked; tail = tail.removePrefix(" and can't be blocked this turn") }
                tail.startsWith(" and attacks this turn if able") -> { more += Mechanic.MustAttack(); tail = tail.removePrefix(" and attacks this turn if able") }
                tail.startsWith(" for each ") -> {
                    val f = Rtr.FOR_EACH_PART.find(tail) ?: return null
                    per = Value.Count(Filters.objects(f.groupValues[1]) ?: return null)
                    tail = tail.removeRange(f.range)
                }
                tail.startsWith(" and gains ") -> {
                    val end = tail.indexOf(" until end of turn").let { if (it < 0) tail.length else it }
                    keywords += tail.substring(" and gains ".length, end).split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
                    tail = tail.substring(end)
                }
                else -> return null
            }
        }
        fun amount(text: String): Value? {
            val n = ptChange(text) ?: return null
            val p = per ?: return n
            return when ((n as? Value.Fixed)?.value) { 0 -> Value.Fixed(0); 1 -> p; null -> null; else -> Value.Times(p, (n as Value.Fixed).value) }
        }
        val mechanics = listOf<Mechanic>(Mechanic.ModifyPowerToughness(amount(m.groupValues[2]) ?: return null, amount(m.groupValues[3]) ?: return null)) +
            listOfNotNull(keywords.takeIf { it.isNotEmpty() }?.let { Mechanic.GainAbilities(it) }) + more
        val subject = m.groupValues[1]
        val objects = when {
            subject.startsWith("Target ") || subject.startsWith("target ") -> {
                val noun = subject.substringAfter("arget ")
                context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            }
            subject.startsWith("Another target ") || subject.startsWith("another target ") -> {
                val noun = subject.substringAfter("nother target ")
                val filter = Filters.noun(noun) ?: return null
                val first = context.targets.lastOrNull { it.what is TargetKind.Objects }
                if (first == null) context.target(Filters.slotName(noun), Filters.all(context.another(), filter))
                else {
                    val name = "other " + Filters.slotName(noun)
                    context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(1), max = Value.Fixed(1), distinctFrom = listOf(first.name))
                    Objects.Target(name)
                }
            }
            subject == "it" || subject == "It" -> context.it ?: return null
            else -> return null
        }
        return listOf(Step.Do(Effect.ApplyMechanics(mechanics, Affected.Objects(objects), arcanum.api.Duration.EndOfTurn)))
    }

    /** rtr: sentences that read the steps before them; null when none reads [sentence]. */
    private fun rtrModify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        val last = (steps.lastOrNull() as? Step.Do)?.effect
        // rtr chunk 06: "If a creature dealt damage this way would die this turn, exile it instead." after damage to a target (a
        // replacement for the rest of the turn, CR 614.1a)
        if (sentence == "If a creature dealt damage this way would die this turn, exile it instead." && last is Effect.DealDamage) {
            val damaged = last.to.target?.let { Objects.Target(it) } ?: last.to.objects ?: return null
            return steps + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(Filters.all(ObjectFilter.Is(damaged), ObjectFilter.Type(CardType.Creature)), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, arcanum.api.Duration.EndOfTurn))
        }
        // rtr chunk 05: "That creature is a black Zombie in addition to its other colors and types." after a delayed return to the
        // battlefield: what it is as it enters (CR 611.2e), for good
        Rtr.THAT_CREATURE_IS_IN_ADDITION.matchEntire(sentence)?.let { m ->
            val delayed = last as? Effect.CreateDelayedTrigger ?: return null
            val move = ((delayed.instructions.steps.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.takeIf { it.to.zone == Zone.Battlefield } ?: return null
            val color = Tokens.color(m.groupValues[1]) ?: return null
            val subtype = m.groupValues[2].takeIf { it in RulesWords.subtypes } ?: return null
            val entersAs = listOf(Mechanic.SetColors(listOf(ColorRef.Literal(color)), add = true),
                Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))))
            val moved = move.copy(to = move.to.copy(entersAs = move.to.entersAs + entersAs))
            return steps.dropLast(1) + Step.Do(delayed.copy(instructions = delayed.instructions.copy(steps = delayed.instructions.steps.dropLast(1) + Step.Do(moved))))
        }
        // rtr chunk 05: "Those players each discard two cards at random." after damage to players
        Rtr.THOSE_PLAYERS_EACH_DISCARD.matchEntire(sentence)?.let { m ->
            val players = (last as? Effect.DealDamage)?.to?.players ?: return null
            return steps + Step.Do(Effect.Discard(players, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), ObjectFilter.Any,
                random = m.groupValues[2].isNotEmpty()))
        }
        // rtr chunk 06: "Target player mills that many cards." after shuffling cards into a library: how many were shuffled in
        if (sentence == "Target player mills that many cards.") {
            val shuffled = steps.firstNotNullOfOrNull { ((it as? Step.Do)?.effect as? Effect.Move)?.takeIf { m -> m.to.zone == Zone.Library }?.preBind } ?: return null
            return steps + Step.Do(Effect.Mill(context.targetPlayer(), Value.Count(Objects.Bound(shuffled))))
        }
        // rtr chunk 10: "An opponent separates those cards into two piles." after revealing cards (CR 701.20); "Put one pile into your
        // hand and the other on the bottom of your library in any order."
        if (sentence == "An opponent separates those cards into two piles.") {
            val seen = context.seen ?: return null
            return steps + Step.Choose(arcanum.api.Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You) +
                Step.Do(Effect.SeparatePiles(Objects.Bound(seen), separator = Players.Bound("opponent"), chooser = Players.You,
                    chosenBind = "chosen pile", otherBind = "other pile"))
        }
        if (sentence == "Put one pile into your hand and the other on the bottom of your library in any order." && last is Effect.SeparatePiles) {
            return steps + Step.Do(Effect.Move(Objects.Bound(last.chosenBind ?: return null), Destination(Zone.Hand))) +
                Step.Do(Effect.Move(Objects.Bound(last.otherBind ?: return null), Destination(Zone.Library, libraryPosition = -1)))
        }
        // rtr chunk 10: "You may cast those cards without paying their mana costs." after exiling cards (CR 608.2g)
        if (sentence == "You may cast those cards without paying their mana costs.") {
            val exiled = rtrExiledBind(steps) ?: return null
            return steps + rtrMayCastEach(Objects.Bound(exiled))
        }
        // rtr chunk 08: "You may cast instant and sorcery spells with mana value X or less from among them without paying their mana
        // costs." after exiling the top X cards; "Then put all cards exiled this way that weren't cast into your graveyard."
        Rtr.MAY_CAST_SPELLS_FROM_AMONG_THEM.matchEntire(sentence)?.let { m ->
            val exiled = rtrExiledBind(steps) ?: (last as? Effect.Move)?.takeIf { it.to.zone == Zone.Exile && it.postBind == null }?.let { "exiled" } ?: return null
            val named = if (last is Effect.Move && last.postBind == null) steps.dropLast(1) + Step.Do(last.copy(postBind = exiled)) else steps
            // the cards that would be cast as such spells: "instant and sorcery spells" are instant or sorcery cards
            val filter = Filters.noun(Rtr.SPELLS_OF_TYPES.replace(m.groupValues[1]) { "${it.groupValues[1]} or ${it.groupValues[2]} card" }) ?: return null
            return named + rtrMayCastEach(Objects.Where(Objects.Bound(exiled), filter))
        }
        if (sentence.removePrefix("Then put").let { it != sentence && "Put$it" == "Put all cards exiled this way that weren't cast into your graveyard." } ||
            sentence == "Put all cards exiled this way that weren't cast into your graveyard.") {
            val exiled = rtrExiledBind(steps) ?: return null
            return steps + Step.Do(Effect.Move(Objects.Where(Objects.Bound(exiled), ObjectFilter.InZone(Zone.Exile)), Destination(Zone.Graveyard)))
        }
        // rtr chunk 08: "Then repeat this process for instant cards with mana values 2 and 1." after a search for a card with mana
        // value 3 put into the hand: the same search and move for each value
        Rtr.REPEAT_THIS_PROCESS_FOR.matchEntire(sentence)?.let { m ->
            val move = last as? Effect.Move ?: return null
            val search = ((steps.getOrNull(steps.size - 2) as? Step.Do)?.effect as? Effect.Search) ?: return null
            val values = m.groupValues[2].split(Regex(""",? and |, """)).map { it.toIntOrNull() ?: return null }
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return steps + values.flatMap { v ->
                listOf(Step.Do(search.copy(filter = Filters.all(noun, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, Value.Fixed(v))))), Step.Do(move))
            }
        }
        // rtr chunk 05: "If a player does, tap this creature and put a +1/+1 counter on it." after "any opponent may sacrifice …"
        Rtr.IF_A_PLAYER_DOES.matchEntire(sentence)?.let { m ->
            val pay = steps.lastOrNull() as? Step.Pay ?: return null
            val then = m.groupValues[1].split(" and ").map { part ->
                val savedIt = context.it
                context.it = Objects.Self
                val read = effect(part.replaceFirstChar { it.uppercase() } + ".", context)
                context.it = savedIt
                read ?: return null
            }.flatten()
            return steps + Step.If(arcanum.api.Condition.Did(pay.bind ?: return null), then = then)
        }
        // rtr chunk 05: "You do the same with the top three cards of your library." after a target opponent revealed theirs; "If two
        // creatures are put onto the battlefield this way, those creatures fight each other." (CR 701.14)
        if (sentence == "You do the same with the top three cards of your library." && "theirs" in context.chosen) {
            context.chosen += "yours"
            return steps + rtrRevealPutRest(Players.You, Value.Fixed(3), "yours")
        }
        if (sentence == "If two creatures are put onto the battlefield this way, those creatures fight each other." && "yours" in context.chosen) {
            return steps + Step.If(arcanum.api.Condition.And(listOf(arcanum.api.Condition.NonEmpty("theirs put"), arcanum.api.Condition.NonEmpty("yours put"))),
                then = listOf(Step.Do(Effect.Fight(Objects.Bound("theirs put"), Objects.Bound("yours put")))))
        }
        return null
    }

    /** rtr: the name of the cards an earlier step exiled. */
    private fun rtrExiledBind(steps: List<Step>): String? = steps.reversed().firstNotNullOfOrNull { step ->
        when (step) {
            is Step.Do -> (step.effect as? Effect.Move)?.takeIf { it.to.zone == Zone.Exile }?.postBind
            is Step.ForEach -> rtrExiledBind(step.steps)
            else -> null
        }
    }

    /** rtr: "you may cast [objects] without paying their mana costs", each card on its own (CR 608.2g). */
    private fun rtrMayCastEach(objects: Objects): List<Step> = listOf(Step.ForEach(arcanum.api.Each.Objects(objects), bind = "card", steps = listOf(
        Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You))))

    /** rtr chunk 05: [player] reveals the top [count] cards of their library, may put a creature card from among them onto the
     * battlefield, then puts the rest into their graveyard; the creature put is named "[bind] put". */
    private fun rtrRevealPutRest(player: Players, count: Value, bind: String): List<Step> = listOf(
        Step.Do(Effect.Reveal(Objects.LibraryCards(player, count, fromTop = true), bind = "$bind revealed")),
        Step.Choose(arcanum.api.Choice.Objects(Filters.all(ObjectFilter.Type(CardType.Creature), ObjectFilter.Category(arcanum.api.ObjectCategory.Card)),
            Value.Fixed(0), Value.Fixed(1), from = Objects.Bound("$bind revealed")), bind = bind, chooser = player),
        Step.Do(Effect.Move(Objects.Bound(bind), Destination(Zone.Battlefield, controller = player), postBind = "$bind put")),
        Step.Do(Effect.Move(Objects.Except(Objects.Bound("$bind revealed"), Objects.Bound(bind)), Destination(Zone.Graveyard))))

    /** rtr: trigger events the shared reader doesn't know. */
    private fun rtrEvents(text: String): arcanum.api.Trigger.On? {
        // rtr chunk 03: "the beginning of the upkeep of enchanted permanent's controller", "… of the draw step of enchanted creature's
        // controller"
        Rtr.STEP_OF_ENCHANTED_CONTROLLER.matchEntire(text)?.let { m ->
            val step = if (m.groupValues[1] == "upkeep") arcanum.api.TurnStep.Upkeep else arcanum.api.TurnStep.Draw
            return arcanum.api.Trigger.On(EventPattern.StepBegins(step, PlayerFilter.Is(Players.ControllerOf(Objects.AttachedTo(Objects.Self)))))
        }
        // rtr chunk 05: "the beginning of each combat", "the beginning of each player's upkeep"
        if (text == "the beginning of each combat") return arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.BeginningOfCombat, PlayerFilter.Any))
        if (text == "the beginning of each player's upkeep") return arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.Any))
        // rtr chunk 03: "enchanted land becomes tapped"
        Rtr.ENCHANTED_BECOMES_TAPPED.matchEntire(text)?.let { return arcanum.api.Trigger.On(EventPattern.Tap(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))) }
        // rtr chunk 06: "a player casts a spell from a graveyard"
        Rtr.CASTS_A_SPELL_FROM.matchEntire(text)?.let { m ->
            val by = if (m.groupValues[1] == "a player") PlayerFilter.Any else if (m.groupValues[1] == "you") PlayerFilter.You else return null
            return arcanum.api.Trigger.On(EventPattern.Cast(Filters.noun(m.groupValues[2]) ?: return null, by, from = Zone.Graveyard))
        }
        // rtr chunk 07: "a creature attacks you or a planeswalker you control" (CR 508.1b)
        Rtr.ATTACKS_YOU_OR_PLANESWALKER.matchEntire(text)?.let { m ->
            return arcanum.api.Trigger.On(EventPattern.Attack(Filters.noun(m.groupValues[1]) ?: return null,
                target = RecipientFilter(objects = Filters.all(ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.ControlledBy(PlayerFilter.You)), players = PlayerFilter.You)))
        }
        // rtr chunk 07: "a source deals damage to you"
        if (text == "a source deals damage to you") return arcanum.api.Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)))
        // rtr chunk 10: "a creature deals combat damage to Vraska"
        Rtr.DEALS_COMBAT_DAMAGE_TO_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            return arcanum.api.Trigger.On(EventPattern.Damage(Filters.noun(m.groupValues[1]) ?: return null, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), combat = true))
        }
        // rtr chunk 06: "Worldspine Wurm is put into a graveyard from anywhere" (CR 603.6c, 603.10a)
        Rtr.PUT_INTO_A_GRAVEYARD_FROM_ANYWHERE.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Graveyard))
        }
        // rtr chunk 10: "you play a card with the same name as one of the exiled cards" (casting it or playing it as a land, CR 601,
        // 305.1)
        if (text == "you play a card with the same name as one of the exiled cards") {
            val named = ObjectFilter.SameNameAs(exiledCard ?: return null)
            return arcanum.api.Trigger.On(listOf(EventPattern.Cast(named, PlayerFilter.You), EventPattern.PlayLand(named, PlayerFilter.You)))
        }
        return null
    }

    /** rtr: more effect sentences (third part). */
    private fun rtrEffect3(sentence: String, context: Context): List<Step>? {
        // rtr chunk 10: "Until your next turn, whenever a creature an opponent controls attacks, it gets -1/-0 until end of turn." (a
        // delayed trigger that triggers each time until then, CR 603.7b)
        Rtr.UNTIL_YOUR_NEXT_TURN_WHENEVER.matchEntire(sentence)?.let { m ->
            val (trigger, onlyIf) = trigger(m.groupValues[1]) ?: return null
            if (onlyIf != null) return null
            val event = trigger.events.single()
            val that = if (event is EventPattern.Damage && event.to.objects == ObjectFilter.Is(Objects.Self)) Objects.EventObject(EventRole.Source) else that(trigger)
            val instructions = instructions(m.groupValues[2], event = event, that = that) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(trigger, instructions, duration = arcanum.api.Duration.UntilYourNextTurn, repeats = true)))
        }
        // rtr chunk 10: "Destroy that creature." (the event's creature)
        if (sentence == "Destroy that creature." && context.that != null) return listOf(Step.Do(Effect.Destroy(context.that!!)))
        // rtr chunk 10: "For each player, search that player's library for a nonland card and exile it, then that player shuffles."
        Rtr.FOR_EACH_PLAYER_SEARCH_AND_EXILE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            val player = Players.Bound("player")
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Do(Effect.Search(player, filter, max = Value.Fixed(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Shuffle(player)))))
        }
        // rtr chunk 05: "Any opponent may sacrifice a creature of their choice." (CR 118.12)
        Rtr.ANY_OPPONENT_MAY_SACRIFICE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(filter, count = Value.Fixed(1))))),
                payer = Players.Each(PlayerFilter.Opponent), bind = "sacrificed"))
        }
        // rtr chunk 05: "Target opponent reveals the top three cards of their library, may put a creature card from among them onto the
        // battlefield, then puts the rest into their graveyard."
        if (sentence == "Target opponent reveals the top three cards of their library, may put a creature card from among them onto the battlefield, then puts the rest into their graveyard.") {
            context.chosen += "theirs"
            return rtrRevealPutRest(context.targetOpponent(), Value.Fixed(3), "theirs")
        }
        // rtr chunk 08: "Search your library for an instant card with mana value 3, reveal it, and put it into your hand."
        Rtr.SEARCH_REVEAL_AND_PUT_INTO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.Search(Players.You, filter, max = Value.Fixed(1), reveal = true, zones = listOf(Zone.Library), bind = "found",
                searcher = Players.You)), Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))))
        }
        // rtr chunk 10: "You may put one of those cards with that name into its owner's hand." in a trigger on playing a card with the
        // same name as an exiled one
        if (sentence == "You may put one of those cards with that name into its owner's hand.") {
            val exiled = exiledCard ?: return null
            val that = context.it ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.SameNameAs(that), Value.Fixed(0), Value.Fixed(1), from = exiled), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // rtr chunk 10: "Then if there are no cards exiled with this enchantment, sacrifice it. If you do, take an extra turn after this
        // one." (CR 118.12: "if you do" checks the sacrifice)
        Rtr.IF_NO_CARDS_EXILED_WITH_SACRIFICE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val then = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { effect(it.replaceFirstChar { c -> c.uppercase() } + ".", context) ?: return null }.orEmpty()
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Count(Objects.Remembered(EXILED_WITH)), arcanum.api.Comparison.Equal, Value.Fixed(0)),
                then = listOf(Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Self))), payer = Players.You, mandatory = true, bind = "sacrificed")) +
                    listOfNotNull(then.takeIf { it.isNotEmpty() }?.let { Step.If(arcanum.api.Condition.Did("sacrificed"), then = it) })))
        }
        if (sentence == "Take an extra turn after this one.") return listOf(Step.Do(Effect.ExtraTurn(Players.You)))
        // rtr chunk 05: "Tap this creature." (in "tap this creature and put a +1/+1 counter on it")
        Rtr.TAP_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { return listOf(Step.Do(Effect.Tap(Objects.Self))) }
        // rtr chunk 06: "Put X +1/+1 counters on target creature you control." (X defined by the ability)
        Rtr.PUT_COUNTERS_ON_TARGET.matchEntire(sentence)?.let { m ->
            val count = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val noun = m.groupValues[3]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.PutCounters(target, Counters.kind(m.groupValues[2]) ?: return null, count)))
        }
        // rtr chunk 08: "Exile the top X cards of your library." (named for "from among them")
        Rtr.EXILE_TOP_CARDS.matchEntire(sentence)?.let { m ->
            val count = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, count, fromTop = true), Destination(Zone.Exile), postBind = "exiled")))
        }
        // rtr chunk 05: "Rakdos's Return deals X damage to target opponent or planeswalker."
        Rtr.SELF_DEALS_DAMAGE_TO_TARGET_PLAYER_OR_PLANESWALKER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val to = rtrTarget(m.groupValues[3], context) ?: return null
            val amount = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(m.groupValues[2].toInt())
            return listOf(Step.Do(Effect.DealDamage(to, amount, Objects.Self)))
        }
        // rtr chunk 09: "You gain 1 life for each of the chosen colors it is." (the spell of a cast trigger)
        Rtr.GAIN_LIFE_FOR_EACH_CHOSEN_COLOR_IT_IS.matchEntire(sentence)?.let { m ->
            val spell = context.it ?: return null
            val per = Value.SharedColors(spell, listOf(ColorRef.Remembered(CHOSEN_COLOR)))
            val n = m.groupValues[1].toInt()
            return listOf(Step.Do(Effect.GainLife(Players.You, if (n == 1) per else Value.Times(per, n))))
        }
        // rtr chunk 09: "It deals 4 damage to each creature and each player." (in a dies trigger: the creature as it last existed)
        Rtr.IT_DEALS_DAMAGE_TO_EACH_AND_EACH_PLAYER.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val players = Filters.players("each " + m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = Objects.Each(filter), players = players), Value.Fixed(m.groupValues[1].toInt()), Objects.Self)))
        }
        // rtr chunk 06: "This creature deals damage to target creature equal to the discarded card's mana value." (CR 608.2h)
        Rtr.DEALS_DAMAGE_EQUAL_TO_DISCARDED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[2]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = target),
                Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("discarded"), arcanum.api.Combine.Single), Objects.Self)))
        }
        return null
    }

    /** rtr: cost parts the shared reader doesn't know. */
    private fun rtrCostPart(text: String): List<CostPart>? {
        // rtr chunk 07: "Discard two nonland cards with the same name" (CR 201.2)
        Rtr.DISCARD_WITH_THE_SAME_NAME.matchEntire(text)?.let { m ->
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.Discard(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count), shared = arcanum.api.SharedQuality.Name)))
        }
        // rtr chunk 06: "Sacrifice a Swamp and a Forest" (two objects, each its own part)
        Rtr.SACRIFICE_A_AND_A.matchEntire(text)?.let { m ->
            return listOf(m.groupValues[1], m.groupValues[2]).map { CostPart.Sacrifice(costChoice(it) ?: return null) }
        }
        return null
    }

    private object Rtr {
        // rtr chunk 09
        val AS_ENTERS_CHOOSE_A_CARD_NAME = Regex("""As (.+) enters, choose a card name\.""")
        val CANT_BE_ACTIVATED_UNLESS_MANA = Regex("""Activated abilities of (.+) can't be activated unless they're mana abilities\.""")
        val AS_ENTERS_CHOOSE_COLORS = Regex("""As (.+) enters, choose (two|three) colors\.""")
        val UNTAP_DURING_OTHER_UNTAP_STEPS = Regex("""Untap (.+) during each other player's untap step\.""")
        val SELF_BECOMES_ARTIFACT_CREATURE = Regex("""(.+?) becomes an? (\d+)/(\d+) (\w+(?: and \w+)?) ([A-Z]\w*(?: [A-Z]\w*)*) artifact creature(?: with ([a-z ,]+?))?( until end of turn)?""")
        val GAIN_LIFE_FOR_EACH_CHOSEN_COLOR_IT_IS = Regex("""You gain (\d+) life for each of the chosen colors it is\.""")
        val IT_DEALS_DAMAGE_TO_EACH_AND_EACH_PLAYER = Regex("""It deals (\d+) damage to each (\w+) and each (player|opponent)\.""")
        // rtr chunk 07
        val CANT_ATTACK_UNLESS_PAYS = Regex("""(Creatures) can't attack you(?: or (planeswalkers you control))? unless their controller pays (\{[^}]+\}) for each of those creatures(?:, where X is (.+))?\.""")
        // rtr chunk 05
        val OPPONENT_CAUSES_DISCARD = Regex("""If a spell or ability an opponent controls causes you to discard (.+), put it onto the battlefield instead of putting it into your graveyard\.""")
        val CANT_CAST_SELF_UNLESS = Regex("""You can't cast (.+) unless (.+)\.""")
        val COST_LESS_FOR_EACH_LIFE_LOST = Regex("""(.+ spells you cast) cost (\{\d+\}) less to cast for each 1 life (your opponents have|you've) lost this turn\.""")
        val THAT_PLAYER = Regex("""That player (.+)\.""")
        val PUT_ON_TOP_OF_LIBRARY = Regex("""Put (it|target .+) on top of its owner's library\.""")
        val ITS_CONTROLLER_REVEALS_UNTIL = Regex("""Its controller reveals cards from the top of their library until they reveal (an? .+), then puts those cards into their graveyard\.""")
        val RETURN_IT_UNDER_YOUR_CONTROL_AT_NEXT_END_STEP = Regex("""Return it to the battlefield under your control with an additional (\S+) counter on it at the beginning of the next end step\.""")
        val PUT_THAT_MANY_COUNTERS_ON_IT = Regex("""Put that many (\S+) counters on it\.""")
        val IT_DEALS_DAMAGE_EQUAL_TO_ITS_POWER = Regex("""It deals damage equal to its power to target (.+)\.""")
        val PLAYER_OR_PLANESWALKER = Regex("""(player|opponent) or planeswalker""")
        val THAT_PLAYER_OR_PLANESWALKERS_CONTROLLER_DISCARDS = Regex("""That player or that planeswalker's controller discards (\w+) cards?\.""")
        val THOSE_PLAYERS_EACH_DISCARD = Regex("""Those players each discard (\w+) cards?( at random)?\.""")
        val TARGET_PLAYER_WHO_LOST_LIFE_LOSES = Regex("""Target player who lost life this turn loses (\d+) life\.""")
        val SEARCH_OPPONENTS_ZONES_FOR_NAME = Regex("""Search target opponent's graveyard, hand, and library for any number of cards with that name and exile them\.""")
        val ANY_OPPONENT_MAY_SACRIFICE = Regex("""Any opponent may sacrifice an? (.+) of their choice\.""")
        val IF_A_PLAYER_DOES = Regex("""If a player does, (.+)\.""")
        // rtr chunk 06
        val SELF_DEALS_TO_THAT_PLAYER = Regex("""(.+) deals (\d+) damage to that player\.""")
        val GAIN_LIFE_EQUAL_TO_THAT_CREATURES = Regex("""You gain life equal to that creature's (power|toughness)\.""")
        val SEARCH_UP_TO_REVEAL_INTO_HAND = Regex("""Search your library for up to (\w+) (.+), reveal them, put them into your hand, then shuffle\.""")
        val LOSES_LIFE_EQUAL_TO_SACRIFICED = Regex("""(Each opponent|Each player|Target player) loses life equal to the sacrificed creature's (power|toughness)\.""")
        val RETURN_SELF_FROM_GRAVEYARD_TO_HAND = Regex("""Return (.+) from your graveyard to your hand\.""")
        val SEARCH_UP_TO_AND_REVEAL = Regex("""Search your library for up to (\w+) (.+) and reveal them\.""")
        val DESTROY_ALL_ATTACHED_TO_TARGET = Regex("""Destroy all (\w+) attached to target (.+)\.""")
        val DEALS_DAMAGE_EQUAL_TO_DISCARDED = Regex("""(.+) deals damage to target (.+) equal to the discarded card's mana value\.""")
        val CASTS_A_SPELL_FROM = Regex("""(a player|you) casts? an? (spell|.+ spell) from a graveyard""")
        val PUT_INTO_A_GRAVEYARD_FROM_ANYWHERE = Regex("""(.+) is put into a graveyard from anywhere""")
        // rtr chunk 03
        val PREVENT_NEXT_TO_YOU = Regex("""Prevent the next (\d+) damage that would be dealt to you this turn\.""")
        val THAT_PLAYER_SACRIFICES_UNLESS_PAYS_X = Regex("""That player sacrifices it unless they pay \{X\}, where X is its mana value\.""")
        val STEP_OF_ENCHANTED_CONTROLLER = Regex("""the beginning of the (upkeep|draw step) of enchanted (?:creature|permanent|land)'s controller""")
        val ENCHANTED_BECOMES_TAPPED = Regex("""enchanted (?:land|creature|permanent) becomes tapped""")
        val EXILE_UP_TO_TARGETS_AND_OR_CARDS = Regex("""You may exile (up to \w+) (other )?target (.+?) from the battlefield and/or (.+?) from graveyards\.""")
        val RETURN_THE_EXILED_CARDS = Regex("""Return the exiled cards (to their owners' hands|to the battlefield under their owner's control)\.""")
        // rtr chunk 01
        val SACRIFICE_IT_UNLESS_YOU = Regex("""Sacrifice (it|this \w+) unless you (.+)\.""")
        val EXCHANGE_CONTROL_OF_SELF_AND_TARGET = Regex("""(You may )?[Ee]xchange control of (.+) and target (.+)\.""")
        val NEITHER_OWN_NOR_CONTROL = Regex("""(.+) you neither own nor control""")
        // rtr chunk 07
        val ATTACKS_YOU_OR_PLANESWALKER = Regex("""an? (.+) attacks you or a planeswalker you control""")
        // rtr chunk 08
        val ALL_GET_UNTIL_EOT = Regex("""All (.+) get ([+-]\d+)/([+-]\d+) until end of turn\.""")
        val EACH_DEALS_TO_ITS_CONTROLLER = Regex("""Each (\w+) deals (\d+) damage to its controller\.""")
        val EXILE_TARGET_AND_SAME_NAME = Regex("""(You may )?[Ee]xile target (.+) and all other (\w+) with the same name as that \w+\.""")
        val NOT_NAMED = Regex("""(.+) not named (.+)""")
        val DEALS_DAMAGE_EQUAL_TO_THAT_SPELLS_POWER = Regex("""(.+) deals damage equal to that spell's power to its controller\.""")
        val DEALS_TO_TARGET_AND_SAME_NAME = Regex("""(.+) deals (\d+) damage to target (\w+) and each other (\w+) with the same name as that \w+\.""")
        val MAY_CAST_SPELLS_FROM_AMONG_THEM = Regex("""You may cast (.+ spells with mana value X or less) from among them without paying their mana costs\.""")
        val SPELLS_OF_TYPES = Regex("""^(\w+) and (\w+) spells""")
        val REPEAT_THIS_PROCESS_FOR = Regex("""Then repeat this process for (.+) cards with mana values (.+)\.""")
        val SEARCH_REVEAL_AND_PUT_INTO_HAND = Regex("""Search your library for an? (.+), reveal it, and put it into your hand\.""")
        // rtr chunk 10
        val UNTIL_YOUR_NEXT_TURN_WHENEVER = Regex("""Until your next turn, whenever (.+?), (.+)""")
        val DEALS_COMBAT_DAMAGE_TO_SELF = Regex("""an? (.+) deals combat damage to (.+)""")
        val FOR_EACH_PLAYER_SEARCH_AND_EXILE = Regex("""For each player, search that player's library for (an? .+) and exile it, then that player shuffles\.""")
        val IF_NO_CARDS_EXILED_WITH_SACRIFICE = Regex("""If there are no cards exiled with (.+), sacrifice it\.(?: If you do, (.+)\.)?""")
        val IF_CONDITION_THEN = Regex("""If (.+?), (.+)\.""")
        val SELF_HAS_N_OR_MORE_COUNTERS = Regex("""(.+) has (\w+) or more (\S+) counters on it""")
        val DISCARD_WITH_THE_SAME_NAME = Regex("""Discard (\w+ .+?) with the same name""")
        val SACRIFICE_A_AND_A = Regex("""Sacrifice (an? \w+) and (an? \w+)""")
        // rtr chunk 01
        val CANT_ATTACK_OR_BLOCK_OR_ACTIVATE = Regex("""(.+) can't attack or block, and its activated abilities can't be activated\.""")
        val ATTACHED_SUBJECT = Regex("""(?:Enchanted|Equipped) (?:creature|land|permanent|artifact)""")
        val DETAIN = Regex("""Detain (?:(up to \w+) )?target (.+)\.""")
        val THEN_POPULATE = Regex("""(.+), then populate\.""")
        // rtr chunk 02
        val HAS_QUOTED = Regex("""(.+?) (?:gets ([+-]\d+)/([+-]\d+) and )?has "(.+)"\.?""")
        val TARGET_PLAYER_GAINS_LIFE = Regex("""Target player gains (\d+) life\.""")
        val PUMP = Regex("""(.+?) gets? ([+-][\dX]+)/([+-][\dX]+)(.*)""")
        val FOR_EACH_PART = Regex("""^ for each (.+?)(?= and | until end of turn|$)""")
        // rtr chunk 03
        val WOULD_BE_PUT_INTO_A_GRAVEYARD = Regex("""If (an? .+?) would be put into a graveyard from anywhere, exile it instead\.""")
        val DAMAGE_DEALT_TO_SELF_INSTEAD = Regex("""All damage that would be dealt to you and (.+) is dealt to (.+) instead\.""")
        // rtr chunk 04
        val REGENERATE = Regex("""Regenerate (.+)\.""")
        // rtr chunk 05
        val ATTACK_DESPITE_DEFENDER_AS_LONG_AS = Regex("""(.+) can attack as though it didn't have defender as long as (.+)\.""")
        val ITS_CONTROLLER = Regex("""Its controller (.+)\.""")
        val LOSES_LIFE = Regex("""loses (\d+) life""")
        val MILLS = Regex("""mills (\w+) cards?""")
        val TAP_SELF = Regex("""Tap (.+)\.""")
        val PUT_COUNTERS_ON_TARGET = Regex("""Put (X|\w+) (\S+) counters? on target (.+)\.""")
        val EXILE_TOP_CARDS = Regex("""Exile the top (X) cards of your library\.""")
        val SELF_DEALS_DAMAGE_TO_TARGET_PLAYER_OR_PLANESWALKER = Regex("""(.+) deals (X|\d+) damage to target ((?:player|opponent) or planeswalker)\.""")
        val THAT_CREATURE_IS_IN_ADDITION = Regex("""That creature is an? (\w+) ([A-Z]\w*) in addition to its other colors and types\.""")
        val ENTERS_WITH_X_COUNTERS = Regex("""(.+) enters with X (\S+) counters on it, where X is (.+)\.""")
        // rtr chunk 06
        val HAS_KEYWORDS_AS_LONG_AS_COUNTER = Regex("""(.+) has ([a-z ,]+) as long as it has an? (\S+) counter on it\.""")
        val ALL_ABLE_TO_BLOCK_DO_SO = Regex("""All creatures able to block (.+) do so\.""")
        val DURING_OTHER_TURNS_HAVE = Regex("""During turns other than yours, (.+) have ([a-z ,]+)\.""")
        val TWICE_THAT_MANY_COUNTERS = Regex("""If one or more (\S+) counters would be put on (.+), twice that many (\S+) counters are put on it instead\.""")
        val SELF_HAS_NO_COUNTERS = Regex("""(.+) has no (\S+) counters on it""")
        // rtr chunk 07
        val GETS_FOR_EACH_CARD_IN_CONTROLLERS_HAND = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) for each card in its controller's hand\.""")
        val GETS_FOR_EACH_OF_ITS_COLORS = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) for each of its colors\.""")
    }

    private object BlbGaps2 {
        val TAP_CREATURE_FOR_EACH_MANA = Regex("""For each mana in this ability's activation cost, you may tap an untapped creature you control rather than pay that mana\.""")
        val CAST_SPELLS_WITH_OR_WITH = Regex("""cast (\w+) spells (with .+) or (\w+) spells (with .+)""")
        val NTH_SPELLS_IF = Regex("""if it's ((?:the \w+ .+? spell(?: other than .+?)?, )+(?:or )?the \w+ .+? spell(?: other than .+?)?) you(?:'ve)? cast this turn, (.+)""")
        val NTH_SPELL_ITEM = Regex("""the (\w+) (.+?) spell(?: other than (.+))?""")
        val YOU_THREE = Regex("""You (?!may )([^,]+), ([^,]+), and ([^,]+)\.""")
        val HAVE_PLAYER_DO = Regex("""Have (target opponent|target player) (draw|discard|mill|sacrifice|lose|gain|create) (.+)\.""")
        val TARGET_OPPONENT_DRAWS = Regex("""Target opponent draws (a|one|two|three) cards?\.""")
        val CREATE_X_TOKENS = Regex("""[Cc]reate X (.+ tokens(?: with .+)?)\.""")
        val EXILE_UNTIL = Regex("""Exile cards from the top of your library until you exile (.+)\.""")
        val MAY_CAST_IT_BY = Regex("""You may cast (?:that card|it) by (.+) rather than paying its mana cost\.""")
        val MAY_PUT_FROM_HAND_OR_GRAVEYARD = Regex("""You may put an? (.+) from your hand or graveyard onto the battlefield(?: with an? (\S+) counter on it)?\.""")
        val IF_WOULD_DIE_EXILE = Regex("""If an? (.+?) would die, exile it instead\.""")
    }

    /** Regular expressions of the Bloomburrow wordings. */
    private object Blb {
        // blb chunk 01
        val TAPPED_AND_ATTACKING = Regex(""" that(?:'s| are) tapped and attacking$""")
        val CANT_BE_BLOCKED_BY = Regex("""(.+) can't be blocked by (.+)\.""")
        val OTHER_THAN_THAT = Regex("""(.+) other than that creature""")
        val GAINS_YOUR_CHOICE = Regex("""Target (.+) gains your choice of ([a-z ]+) or ([a-z ]+) until end of turn\.""")
        val TAP_TARGET = Regex("""Tap (?:up to (\w+) )?target (.+)\.""")
        val UNLESS_THEY = Regex("""(.+) unless (?:they|that player) (.+)\.""")
        // blb chunk 02
        /** "the gift was promised": its optional cost was paid (CR 702.174k). */
        val promised: arcanum.api.Condition = arcanum.api.Condition.Paid(arcanum.api.Keyword.Gift.Kind, times = 1, of = Objects.Self)
        val GIFT_INSTEAD = Regex("""If the gift was promised, instead (.+)\.""")
        val GIFT_IF = Regex("""If the gift (was|wasn't) promised(?: and (.+?))?, (.+)\.""")
        val IF_GIFT_AFTER = Regex("""(.+) if the gift (was|wasn't) promised\.""")
        val ALSO = Regex("""(.+?) also (.+)""")
        val YOU_AND_GAIN = Regex("""You and (.+) gain hexproof until end of turn\.""")
        val IT_IS = Regex("""(?:that creature|it) (is|isn't) (?:an? )?(.+)""")
        val ATTACHED_GETS_HAS_LOSES = Regex("""(?:Equipped|Enchanted) creature gets ([+-]\d+)/([+-]\d+)(?: and has ([a-z ,]+?))?(?: and loses ([a-z ,]+?))?\.""")
        val RETURN_COUNTED_FROM_GRAVEYARD = Regex("""[Rr]eturn (up to )?(two|three|four|five) target (.+?) from your graveyard to (your hand|the battlefield)\.""")
        val DESTROY_N_TARGETS = Regex("""[Dd]estroy (two|three) target (.+)\.""")
        val DESTROY_ALL = Regex("""Destroy all (.+)\.""")
        // blb chunk 12
        val ENTERS_TAPPED_AS_IT_ENTERS = Regex("""(This \w+) enters tapped\. As it enters, (.+)""")
        val MAY_PLAY_LANDS_AND_CAST_FROM_TOP = Regex("""You may play lands and cast spells with mana value (\d+) or (greater|less) from the top of your library\.""")
        val AS_ENTERS_CHOOSE_COLOR = Regex("""As (.+) enters, choose a color\.""")
        val ADD_AMOUNT_OF_THAT_COLOR = Regex("""Add an amount of mana of that color equal to (.+)\.""")
        val BECOMES_ARTIFACT_CREATURE = Regex("""(.+) becomes an artifact creature until end of turn\.""")
        val IT_GAINS_UNTIL_EOT = Regex("""It gains ([a-z ,]+) until end of turn\.""")
        val THEN_IF = Regex("""Then if (.+?), (.+)""")
        val THEN_IF_UNTAP_THAT_LAND = Regex("""Then if (.+), untap that land\.""")
        // blb chunk 11
        val HAVE_BASE_TOUGHNESS = Regex("""(.+) have base toughness (\d+)\.""")
        val IF_THAT_HAD_POWER = Regex("""If that creature had power (\d+) or (less|greater), (.+)""")
        val RETURN_OTHER_TARGET_TO_HAND = Regex("""[Rr]eturn (another|up to one other) target (.+) to its owner's hand\.""")
        val RETURN_ANOTHER_IF_YOU_DO = Regex("""[Rr]eturn another (.+) to its owner's hand\. If you do, (.+)""")
        val DEALS_DAMAGE_TO_EACH_PLAYER_EQUAL = Regex("""(.+) deals damage to each player equal to the number of (.+) that player controls\.""")
        val TARGET_CANT_BLOCK_THIS_TURN = Regex("""Target (.+) can't block this turn\.""")
        val AND_YOU_GAIN_LIFE = Regex("""(.+) and you gain (\d+) life\.""")
        val UNTIL_EOT_TARGET_GAINS_AND_GETS_X = Regex("""Until end of turn, target (.+?) gains ([a-z ,]+) and gets ([+-]\w+)/([+-]\w+)\.""")
        val COUNTERS_ON_THAT_CREATURE = Regex("""Put (a|an|one|two|three) (\S+) counters? on that creature\.""")
        val PUT_IT_ONTO_BATTLEFIELD = Regex("""Put it onto the battlefield( tapped)?\.""")
        val CREATE_COPY_OF = Regex("""Create a token that's a copy of (.+)\.""")
        val RETURN_TO_OWNERS_HAND = Regex("""Return (.+) to its owner's hand\.""")
        val RETURN_UP_TO_ONE_LESSER = Regex("""Return up to one target (.+) with lesser mana value to its owner's hand\.""")
        val YOU_PUT_COUNTERS_ON = Regex("""you put one or more (\S+) counters on (.+)""")
        val COUNTER_ON_EACH_OF_UP_TO = Regex("""Put an? (\S+) counter on each of up to (two|three) target (.+)\.""")
        val DOUBLE_COUNTERS_ON_IT = Regex("""Double the number of (\S+) counters on it\.""")
        val TAP_UP_TO_ONE_AND_COUNTERS = Regex("""Tap up to one target (.+?) and put (\w+) (\S+) counters on it\.""")
        val IF_YOU_CONTROL_THAT = Regex("""If you control that creature, (.+)""")
        val FLICKER_OTHER_WITH_COUNTER = Regex("""Exile another target (.+?), then return it to the battlefield under its owner's control with (a|an|two) (\S+) counters? on it\.""")
        // blb chunk 10
        val COUNTER_UNLESS_INSTEAD = Regex("""If (.+?), counter that spell unless its controller pays ((?:\{[^}]+\})+) instead\.""")
        val THIS_SPELL_COSTS_LESS_IF = Regex("""This spell costs (\{[^}]+\}) less to cast if (.+)\.""")

        val CREATE_X_COPIES_OF_TARGET = Regex("""Create X tokens that are copies of target (.+)\.""")
        val GROUP_GAINS_UNTIL_YOUR_NEXT_TURN = Regex("""(.+) gain ([a-z ,]+) until your next turn\.""")
        val GAIN_LIFE_FOR_EACH = Regex("""You gain (\d+) life for each (.+)\.""")
        val UP_TO_ONE_OTHER_TARGET_GETS = Regex("""Up to one other target (.+) gets ([+-]\d+)/([+-]\d+) until end of turn\.""")
        val DEALS_TO_TARGET_AND_UP_TO_ONE = Regex("""(.+) deals (\d+) damage to target (.+?) and (\d+) damage to up to one target (.+)\.""")
        val IT_DEALS_POWER_TO_TARGET = Regex("""It deals damage equal to its power to target (.+)\.""")
        val OWNER_PUTS_TOP_OR_BOTTOM = Regex("""Target (.+)'s owner puts it on their choice of the top or bottom of their library\.""")
        val RETURN_UP_TO_N_TO_HANDS = Regex("""Return up to (two|three) target (.+) to their owners' hands\.""")
        val TWO_TARGETS_DIFFERENT_PLAYERS = Regex("""Choose two target (.+) controlled by different players\.""")
        // blb chunk 09
        val WOULD_BE_PUT_INTO_YOUR_GRAVEYARD = Regex("""If (.+) would be put into your graveyard from anywhere, exile it instead\.""")
        val ENTERS_TAPPED_IF = Regex("""(.+) enters tapped if (.+)\.""")
        val IF_CAST_NOT_FROM_HAND_COUNTER_ON_THAT = Regex("""If this spell was cast from anywhere other than your hand, put (a|an|one|two) (\S+) counters? on that creature\.""")
        val THIS_SPELL_COSTS_LESS_FOR_EACH = Regex("""This spell costs (\{[^}]+\}) less to cast for each (.+)\.""")
        val AS_LONG_AS_GROUP_GETS = Regex("""As long as (.+?), ([a-z].+?) get ([+-]\d+)/([+-]\d+)\.""")
        val AS_LONG_AS_IT_GETS_AND_HAS = Regex("""As long as (.+?), (it|this creature) gets ([+-]\d+)/([+-]\d+) and has ([a-z ,]+)\.""")
        val YOU_MAY_CAST_FROM_GRAVEYARD_BY = Regex("""You may cast (.+?) from your graveyard by (foraging|paying \d+ life) in addition to (?:paying )?their other costs\.""")
        val RETURN_UP_TO_ONE_TO_HAND = Regex("""[Rr]eturn up to one target (.+) to its owner's hand\.""")
        val DO_THEN_WHEN_YOU_DO = Regex("""(.+?)\. When you do, (.+)""")
        val MAY_CAST_TARGET_WITH_COUNTERS_MANA_VALUE = Regex("""You may cast target (.+?) with mana value equal to the number of (\S+) counters on (.+?) from your graveyard without paying its mana cost\.""")
        val RETURN_SELF_TAPPED_AND_ATTACKING = Regex("""Return (.+) from your graveyard to the battlefield tapped and attacking\.""")
        val TARGET_PLAYER_DISCARDS = Regex("""Target (opponent|player) discards (a|one|two|three) cards?\.""")
        val TARGET_PLAYER_EXILES_FROM_HAND = Regex("""Target (opponent|player) exiles a card from their hand\.""")
        val PUT_TARGET_FROM_GRAVEYARD_ON_BOTTOM = Regex("""Put target (.+) from your graveyard on the bottom of your library\.""")
        // blb chunk 08
        val IF_THAT_CREATURE_IS = Regex("""If that creature is (.+), ([a-z][^,]+)\.""")
        val TARGET_GETS_X_SELF_POWER = Regex("""Target (.+) gets ([+-][\dX]+)/([+-][\dX]+) until end of turn, where X is (.+)'s power\.""")
        val YOU_MAY_CAST_AS_THOUGH_FLASH = Regex("""You may cast (.+) as though they had flash\.""")
        val DAMAGE_TO_ANY_PLUS = Regex("""If (.+?) would deal damage to a permanent or player, it deals that much damage plus (\d+) instead\.""")
        val TRAILING_IF = Regex("""((?:Draw|Create|You gain|It gets) .+?) if (.+)\.""")
        val RETURN_ANOTHER_WITH_LESSER_MANA_VALUE = Regex("""Return another target (.+?) with lesser mana value from your graveyard to the battlefield\.""")
        val EXILE_THEN_RETURN = Regex("""Exile target (.+?), then return it to the battlefield under its owner's control\.""")
        val GAINS_X_LIFE = Regex("""(.+) gains? X life\.""")
        val DEALS_SACRIFICED_POWER_TO_EACH_OPPONENT = Regex("""(.+) deals damage equal to the sacrificed creature's power to each opponent\.""")
        val CREATE_NAMED_LEGENDARY_TOKEN = Regex("""Create ([A-Z][A-Za-z]+), an? (legendary )?(colorless) ([A-Z][a-z]+ [a-z]+) token with "(.+?)"(?: and ([a-z]+ \{[^}]+\}))?\.""")
        // blb chunk 07
        val AS_LONG_AS_SELF_HAS = Regex("""As long as (.+?), (.+?) has ([a-z ,]+)\.""")
        val ENTERS_WITH_COUNTER_IF = Regex("""(.+) enters with (a|an|one|two) (\S+) counters? on it if (.+)\.""")
        val OTHERS_ENTER_WITH_ADDITIONAL = Regex("""(.+) enter with an additional (\S+) counter on them for each (.+)\.""")
        val PUT_ITS_COUNTERS_ON_TARGET = Regex("""Put its counters on target (.+)\.""")
        val CHOOSE_TARGET_PLAYER = Regex("""Choose target (opponent|player)\.""")
        val IF_THEY_LOST_LIFE = Regex("""If they lost life this turn, (.+)\.""")
        val THEY_REVEAL_YOU_CHOOSE_THEY_DISCARD = Regex("""They reveal their hand, you choose an? (.+) from it, and they discard that card\.""")
        val THEY_DISCARD = Regex("""They discard (a|one|two) cards?\.""")
        // blb chunk 06
        val ITS_CONTROLLER_DRAWS = Regex("""Its controller draws (a|one|two) cards?\.""")
        val RETURN_TARGET_FROM_GRAVEYARD_WITH_COUNTER = Regex("""Return target (.+?) from your graveyard to the battlefield with (a|an|one|two) (\S+) counters? on it\.""")
        val DRAW_A_CARD_FOR_EACH = Regex("""Draw a card for each (.+)\.""")
        val LOSES_X_LIFE = Regex("""(.+) loses? X life\.""")
        val PUT_COUNTERS_ON_A = Regex("""Put (a|an|one|two) (\S+) counters? on an? (.+)\.""")
        val IT_GAINS = Regex("""It gains ([a-z ,]+) until end of turn\.""")
        val CHOOSE_CARD_TYPE = Regex("""Choose (\w+) or (\w+)\.""")
        val DESTROY_ALL_OF_CHOSEN_TYPE = Regex("""Destroy all (.+) of the chosen type\.""")
        val DRAW_EQUAL_TO_GREATEST = Regex("""Draw cards equal to the greatest (power|toughness|mana value) among (.+)\.""")
        val SELF_FIGHTS_TARGET = Regex("""(.+) fights target (.+)\.""")
        val TARGET_PLAYER_EXILES = Regex("""Target (opponent|player) (exiles|sacrifices) an? (.+?) they control\.""")
        val CHOOSE_A = Regex("""Choose an? (.+)\.""")
        val RETURN_EACH_TO_HAND = Regex("""Return each (.+) to its owner's hand\.""")
        val UNTIL_END_OF_NEXT_TURN_WHENEVER = Regex("""Until the end of your next turn, whenever (.+?), (.+)\.""")
        val DEALS_TO_UP_TO_ONE_TARGET = Regex("""(.+) deals (\d+) damage to up to one target (.+)\.""")
        // blb chunk 05
        val SPELLS_YOU_CAST_COST = Regex("""(.+) spells you cast cost ((?:\{[^}]+\})+) (more|less) to cast\.""")
        val HAVE_KEYWORDS_WITH_VALUES = Regex("""(.+) have ([a-z ,]+\{[^}]+\}[a-z ,{}0-9]*)\.""")
        val DAMAGE_PLUS = Regex("""If (.+?) would deal (noncombat |combat |)damage to an opponent or a permanent an opponent controls, it deals that much damage plus (\d+) instead\.""")
        val RETURN_A_FROM_GRAVEYARD_WITH_COUNTER = Regex("""Return an? (.+) from your graveyard to the battlefield with (a|an|one|two) (\S+) counters? on it\.""")
        val TARGET_DEALS_POWER_TO_TARGET = Regex("""Target (.+?) deals damage equal to (twice )?its power to target (.+)\.""")
        val COPY_OF_TARGET = Regex("""[Cc]reate a token that's a copy of target (.+)\.""")
        val TARGET_CANT_BE_BLOCKED_THIS_TURN = Regex("""Target (.+) can't be blocked this turn\.""")
        val EACH_OPPONENT_DISCARDS_UNLESS = Regex("""Each opponent discards (\w+) cards unless they discard an? (.+)\.""")
        val DRAW_FOR_EACH_OPPONENT_WITH_HAND = Regex("""Draw an additional card for each opponent who has (\w+) or fewer cards in hand\.""")
        val CREATE_NAMED_TOKEN = Regex("""Create an? (colorless|white|blue|black|red|green) ([A-Z][a-z]+ [a-z]+) token named ([A-Z][A-Za-z ]+) with "(.+?)"(?: and ([a-z]+ \{[^}]+\}))?\.""")
        val ATTACH_TARGET_TO_TARGET = Regex("""Attach target (.+?) to (?:up to (\w+) )?target (.+)\.""")
        // blb chunk 04
        val TARGET_GETS_AND_GAINS = Regex("""Target (.+) gets ([+-]\d+)/([+-]\d+) and gains ([a-z ,]+) until end of turn\.""")
        val IT_GETS = Regex("""(It|This [a-z]+) gets ([+-]\d+)/([+-]\d+)(?: and gains ([a-z ,]+?))? until end of turn\.""")
        val MAY_REVEAL_FROM_AMONG = Regex("""You may reveal an? (.+) from among them\.""")
        val ADD_EITHER_FOR_EACH = Regex("""Add \{([WUBRG])\} or \{([WUBRG])\} for each (.+)\.""")
        val MAY_PLAY_THOSE = Regex("""(Until end of turn|Until the end of your next turn), you may play (those cards|that card|them)\.""")
        val DRAW_EQUAL_TO_COUNTERS = Regex("""Draw cards equal to the number of (\S+) counters on (.+)\.""")
        // blb chunk 03
        val YOU_SACRIFICE_ONE_OR_MORE = Regex("""you sacrifice one or more (.+)""")
        // blb chunk 08
        val WHILE = Regex("""(.+) while (.+)""")
        // blb chunk 07
        val PAY_MANA_AND_LIFE = Regex("""[Pp]ay ((?:\{[^}]+\})+) and (\d+) life""")
        val YOU_ATTACK_WITH_ONE_OR_MORE = Regex("""you attack with one or more (.+)""")
        val CAST_THIS_WAY_ENTERS_WITH = Regex("""If you cast a spell this way, that creature enters with (a|an|one|two) (\S+) counters? on it\.""")
        val ARE_IN_ADDITION = Regex("""(.+) (?:are|is) (?:an? )?([A-Za-z ]+?) in addition to (?:their|its) other types(?: and (?:have|has) "(.+?)\.?")?\.?""")
        val ENCHANTED_IS_A_COLORLESS = Regex("""Enchanted permanent is a colorless ([A-Za-z ]+?) with "(.+)" and loses all other card types and abilities\.""")
        val MAY_CAST_FROM_GRAVEYARD_BY = Regex("""Until end of turn, you may cast (.+?) from your graveyard by (foraging|paying \d+ life) in addition to (?:paying )?their other costs\.""")
        val PUT_COUNTERS_FOR_EACH = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+?) for each (.+)\.""")
        val IF_CONTROL_OR_RETURNED = Regex("""If you control an? (\w+) or returned an? (.+?) to your hand this way, (.+)\.""")
        val DEALS_TO_ANY_AND_EACH = Regex("""(.+) deals (\d+) damage to any target and (\d+) damage to each (.+)\.""")
        val DEALS_TO_THAT_CREATURES_CONTROLLER = Regex("""(.+) deals (\d+) damage to that creature's controller\.""")
        val COPY_OF_THAT_EXCEPT_PT = Regex("""[Cc]reate a token that's a copy of (?:that creature|it), except it's (\d+)/(\d+)\.""")
        val RETURN_PUT_INTO_GRAVEYARD_THIS_WAY = Regex("""[Rr]eturn an? (.+) put into your graveyard this way to the battlefield under your control\.""")
        val AT_NEXT_END_STEP = Regex("""(.+) at the beginning of the next end step\.""")
        val RETURN_UNDER_OWNERS_CONTROL_WITH_COUNTERS = Regex("""[Rr]eturn (?:that card|it) to the battlefield under its owner's control with (a|an|one|two|three) (\S+) counters? on it\.""")
        val TARGET_SACRIFICES_GREATEST = Regex("""Target (opponent|player) sacrifices an? (.+?) with the greatest (power|mana value) among (.+) they control\.""")
        val CHOOSE_TARGETS = Regex("""Choose target (.+?) and target (.+)\.""")
        val PUT_COUNTERS_ON_THE = Regex("""Put (a|an|one|two|three) (\S+) counters? on the (.+)\.""")
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
        val TAP_UNTAPPED = Regex("""Tap (an|another|two|three|four|five) untapped (.+)""")
        val PUT_COUNTER_ON_SELF = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+)""")
        val OTHERS_GET = Regex("""Other (.+) get ([+-]\d+)/([+-]\d+)\.""")
        val ANY_COLOR = Regex("""(one) mana of any color|(\w+) mana of any one color""")
        val ENTERS_TAPPED = Regex("""(.+) enters tapped\.""")
        val WHENEVER = Regex("""(Whenever|When|At) (.+?), (.+)""")
        val INTERVENING_IF = Regex("""if (.+?), (.+)""")
        val COSTS_LESS_FOR_EACH = Regex("""This ability costs ((?:\{[^}]+\})+) less to activate for each (.+?)\.""")
        val DISCARD_COST = Regex("""Discard (.+)""")
        val THEN = Regex("""(.+?), then (.+)""")
        // blb chunk 08: also "a creature spell"
        val ADD_MANA_RESTRICTED = Regex("""Add (.+)\. Spend this mana only to cast (?:an? )?(.+) spells?\.""")
        val ADD_MANA_FOR_EACH = Regex("""Add (\{.\}) for each (.+)\.""")
        val DRAW = Regex("""Draw (a|one|two|three|four|five|six|seven) cards?\.""")
        val DISCARD = Regex("""[Dd]iscard (a|one|two|three|four|five|six|seven) cards?\.""")
        val CREATE_TOKEN = Regex("""[Cc]reate (.+ tokens?(?: with .+)?(?: that(?:'s| are) tapped and attacking)?)\.""")
        val SELF_DEALS_DAMAGE_TO_EACH = Regex("""(.+?) deals (\d+) damage to (each .+)\.""")
        val EXILE_THEN_RETURN = Regex("""Exile (another )?target (.+), then return (?:it|that card) to the battlefield under its owner's control\.""")
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
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+?)(, reveal (?:it|that card))?, put (?:it|that card) (onto the battlefield(?: tapped)?|into your hand|into your graveyard)(, discard a card at random)?, then shuffle\.""")
        val SEARCH_TO_TOP = Regex("""Search your library for (.+?), (reveal it, )?then shuffle and put that card on top\.""")
        val PUT_FROM_HAND_ON_TOP = Regex("""Put (\w+) cards? from your hand on top of your library(?: in any order)?\.""")
        val PLAYERS_DRAW = Regex("""(Each player|You) draws? (\w+) cards?(?:, then discards? (\w+) cards?( at random)?)?(?: and loses? (\d+) life)?\.""")
        val EACH_PLAYER_DISCARDS_HAND_DRAWS = Regex("""Each player discards their hand, then draws (\w+) cards\.""")
        val DISCARD_UNLESS_ATTACKED = Regex("""Discard (a|one|two) cards? unless you attacked this turn\.""")
        val SCRY_OR_SURVEIL = Regex("""(Scry|Surveil) (\d+)\.""")
        val TARGET_PLAYER_MILLS = Regex("""Target player mills (\w+) cards?\.""")
        val LOOK_PUT_BACK = Regex("""Look at the top (\w+) cards of your library, then put them back in any order\.""")
        val TARGET_GETS_UNTIL_EOT = Regex("""Target (.+) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val RETURN_UP_TO_FROM_GRAVEYARD_TO_HAND = Regex("""Return up to (\w+) target (.+) from your graveyard to your hand\.""")
        val YOU_LOSE_LIFE = Regex("""You lose (\d+) life\.""")
        val ANY_COMBINATION = Regex("""(\w+) mana in any combination of colors""")
        // dsk chunk 01
        val ONE_OR_MORE_ATTACK_A_PLAYER = Regex("""one or more (.+) attack a player""")
        val ONE_OR_MORE_DEAL_COMBAT_DAMAGE_TO_A_PLAYER = Regex("""one or more (.+) deal combat damage to a player""")
        val UNTAP_DURING_OTHER_UNTAP_STEPS = Regex("""Untap each (.+) during each other player's untap step\.""")
        val HAVE_BASE_PT_EQUAL_TO_NUMBER = Regex("""(.+) have base power and toughness each equal to the number of (.+)\.""")
        val TRIGGERS_ADDITIONAL_TIME = Regex("""If a triggered ability of an? (.+) triggers, that ability triggers an additional time\.""")
        val TAP_TARGET = Regex("""Tap (?:up to (\w+) )?(another )?target (.+)\.""")
        val PUT_COUNTERS_ON_UP_TO = Regex("""Put (a|an|one|two|three) (\S+) counters? on (?:each of )?up to (\w+) target (.+)\.""")
        val RETURN_UP_TO_TARGET_TO_HAND = Regex("""Return up to (\w+) target (.+) to (?:its|their) owners?'s? hands?\.""")
        val YOU_WIN_IF = Regex("""You win the game if (.+)\.""")
        val ADDITIONAL_CREATURE_TYPE = Regex("""it's an? (\w+) in addition to its other creature types""")
        const val MANIFESTED = "manifested"
        val DESTROY_ALL = Regex("""Destroy all (.+)\.""")
        // dsk chunk 14
        val ENTERS_TAPPED_UNLESS = Regex("""(.+) enters tapped unless (.+)\.""")
        val WITHOUT_SAME_NAME = Regex("""(.+) that don't have the same name as (.+)""")
        val ENTERS_TAPPED_AS_IT_ENTERS = Regex("""(.+) enters tapped\. As it enters, (.+)""")
        val SACRIFICE_OTHER_THAN_THAT_OR_SELF = Regex("""Sacrifice an? (.+) other than that creature or (.+)""")
        val HAVE_IT_GET = Regex("""Have it get ([+-]\d+)/([+-]\d+) until end of turn\.""")
        val PUT_TARGET_ON_BOTTOM = Regex("""Put target (.+) on the bottom of its owner's library\.""")
        val IT_BECOMES_CREATURE = Regex("""It becomes an? (\d+)/(\d+) (\w+) creature in addition to its other types\.""")
        val PUT_EACH_EXILED_WITH_ONTO_BATTLEFIELD = Regex("""Put each (.+) exiled with (.+?) onto the battlefield under your control(?: with an? (\S+) counter on it)?\.""")
        val EACH_OF_THEM_IS_PT_SUBTYPE = Regex("""Each of them is an? (\d+)/(\d+) (\w+) in addition to its other types\.""")
        // dsk chunk 13
        val SELF_IS_PT_CREATURE = Regex("""(.+) is an? (\d+)/(\d+) ((?:[A-Z]\w+ )*)creature(?: and has (.+))?\.""")
        val MAY_CAST_SELF_FROM_GRAVEYARD_IF = Regex("""You may cast (.+) from your graveyard if (.+?)\. If you do, (.+) enters with an? (\S+) counter on it\.""")
        val PT_COLOR_TYPE_IN_ADDITION = Regex("""it's an? (\d+)/(\d+) (\w+) (\w+) creature in addition to its other colors and types(?: and it has (.+))?""")
        val BECOME_COPIES_OF_IT_UNTIL_NEXT_END_STEP = Regex("""(.+) become copies of it until the next end step\.""")
        // dsk gaps
        val REPEAT_IF_MILLED_SHARE = Regex("""If two cards that share a (card type|color) were milled this way, (.+) and repeat this process\.""")
        val EXILE_ANY_NUMBER_FROM_YOUR_GRAVEYARD = Regex("""Exile any number of target (.+) from your graveyard\.""")
        val CAST_THE_COPIES = Regex("""You may cast (?:any number of the copies|the copy)\.""")
        const val COPIES = "copies"
        val SELF_DEALS_THAT_MUCH_TO_ANY_TARGET = Regex("""(.+) deals that much damage to any target\.""")
        val SELF_DEALS_AND_YOU_GAIN = Regex("""(.+) deals (X|\d+) damage to each opponent and you gain (X|\d+) life\.""")
        val IF_YOU_PUT_NO_CARDS_INTO_HAND = Regex("""If you put no cards into your hand this way, (.+)""")
        val CREATE_NAMED_TOKEN_THEN_THAT_MANY_COUNTERS = Regex("""Create (.+?), an? (legendary )?((?:\d+/\d+) .+? creature token), then put that many (\S+) counters on it\.""")
        // dsk chunk 12
        val BEHOLD_COST = Regex("""[Cc]hoose an? (.+) you control or reveal an? (.+) card from your hand""")
        val EXILE_SELF_AND_OTHERS_NAMED = Regex("""Exile (.+?) and (\w+) other cards named (.+) from your graveyard""")
        val OWNER_OF_TARGET_SHUFFLES = Regex("""The owner of target (.+) shuffles it into their library\.""")
        val YOU_MAY_SHUFFLE_UP_TO_FROM_GRAVEYARD = Regex("""You may shuffle up to (\w+) target (.+) from your graveyard into your library\.""")
        val TAP_N_OR_M_TARGETS = Regex("""Tap (one|\w+) or (\w+) target (.+)\.""")
        val THEY_EACH_DEAL_POWER_TO_TARGET = Regex("""They each deal damage equal to their power to target (.+)\.""")
        // dsk chunk 11
        val DO_THIS_ONLY_ONCE_EACH_TURN = Regex("""Do this only once each turn\.""")
        val MUST_BE_BLOCKED = Regex("""(.+) must be blocked if able\.""")
        val HAS_KEYWORDS_DURING_YOUR_TURN = Regex("""(.+) has ([a-z ,]+) during your turn\.""")
        val ATTACHED_GETS_X_COUNTERS = Regex("""Equipped creature gets (\+X|[+-]\d+)/(\+X|[+-]\d+), where X is the number of (\S+) counters on (.+)\.""")
        val GAIN_CONTROL_UNTIL_EOT = Regex("""Gain control of target (.+) until end of turn\.""")
        val SELF_DEALS_N_TO_THEM = Regex("""(.+) deals (\d+) damage to them\.""")
        val N_OR_M_TARGETS_CANT_BLOCK = Regex("""(One|Up to one|\w+) or (\w+) target (.+) can't block this turn\.""")
        val SELF_DEALS_TO_UP_TO_ONE = Regex("""(.+?) deals (\d+) damage to up to one target (.+)\.""")
        val TARGET_DEALS_POWER_TO_EACH_OTHER = Regex("""Target (.+) deals damage equal to its power to each other (.+)\.""")
        val IF_THAT_WOULD_DIE_THIS_TURN = Regex("""If that (?:creature|creature or planeswalker) would die this turn, exile it instead\.""")
        val REMOVE_THOSE_COUNTERS_AND_DEALS = Regex("""If there are (\w+) or more (\S+) counters on it, remove those counters and it deals (\d+) damage to you\.""")
        val WHEN_YOU_NEXT_CAST = Regex("""When you next cast an? (.+?) this turn, (copy that spell)\.""")
        val CHOOSE_TARGETS_SAME_OPPONENT = Regex("""Choose (\w+) target (.+) controlled by the same opponent\.""")
        val DEALS_TO_THAT_CREATURE_OTHER_CANT_BLOCK = Regex("""(.+) deals (\d+) damage to that creature, and the other can't block this turn\.""")
        // dsk chunk 10
        val TARGETABLE_DESPITE_HEXPROOF = Regex("""(.+) can be the targets of spells and abilities as though they didn't have hexproof\.( Ward abilities of those creatures don't trigger\.)?""")
        val CARD_YOU_DIDNT_CONTROL_EXILE_INSTEAD = Regex("""If a card you didn't control would be put into an opponent's graveyard from anywhere, exile it instead\.""")
        val PLAY_EXILED_WITH_FOR_LIFE = Regex("""During your turn, you may play cards exiled with (.+)\. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost\.""")
        val EACH_PLAYER_EXILES_ALL_BUT_BOTTOM = Regex("""Each player exiles all but the bottom (\w+) cards of their library( face down)?\.""")
        val UP_TO_THAT_MANY_TARGETS_GET = Regex("""Up to that many target (.+?) each get ([+-]\d+)/([+-]\d+) until end of turn\.""")
        val LOOK_PUT_ONE_INTO_HAND_REST_GRAVEYARD = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your hand and the rest into your graveyard\.""")
        val RETURN_THAT_CARD_UNDER_CONTROL = Regex("""Return (that card|it) under (your|its owner's) control( tapped)?(?: with (an?|two|three) (\S+) counters? on it)?\.""")
        val THEY_MAY_PAY = Regex("""They may (pay \d+ life)\.""")
        val IF_THEY_DONT = Regex("""If they don't, (.+)""")
        val YOU_MAY_CHOOSE_FROM_IT = Regex("""You may choose (an? .+?) from it\.""")
        val TARGET_PLAYER_SACRIFICES_OTHER_THAN = Regex("""Target player sacrifices an? (.+?) of their choice other than enchanted creature\.""")
        val EACH_SACRIFICES_THEN_SACRIFICES = Regex("""Each (player|opponent) sacrifices an? (.+?) of their choice, then sacrifices an? (.+?) of their choice\.""")
        val IF_PUT_INTO_GRAVEYARD_THIS_WAY_RETURN = Regex("""If an? (.+?) is put into a graveyard this way, return it to the battlefield under your control\.""")
        // dsk chunk 09
        val EXILE_YOU_CONTROL_COST = Regex("""Exile an? (.+) you control""")
        val SELF_COSTS_LESS_IF_IT_TARGETS = Regex("""This spell costs ((?:\{[^}]+\})+) less to cast if it targets an? (.+)\.""")
        val CANT_ATTACK_OR_BLOCK_ALONE = Regex("""(.+) can't attack or block alone\.""")
        val TARGET_CANT_BE_BLOCKED_THIS_TURN = Regex("""(Target|Another target) (.+) can't be blocked this turn\.""")
        val THAT_MANY_CARDS = Regex("""(Draw|Discard) that many cards\.""")
        val IF_OPPONENT_CONTROLS_THAT = Regex("""If an opponent controls that creature, (.+)""")
        val TAP_ANY_NUMBER = Regex("""Tap any number of (.+)\.""")
        val SELF_GETS_FOR_EACH_TAPPED = Regex("""(.+) gets \+(\d+)/\+(\d+) until end of turn for each creature tapped this way\.""")
        val RETURN_N_OR_M_TARGETS_TO_HAND = Regex("""Return (\w+) or (\w+) target (.+?) to your hand\.""")
        val IT_BECOMES_COLOR_SUBTYPE = Regex("""It becomes an? (\w+) (\w+) in addition to its other colors and types\.""")
        val OWNER_PUTS_ON_TOP_OR_BOTTOM = Regex("""Target (.+)'s owner puts it on their choice of the top or bottom of their library\.""")
        val SELF_BECOMES_COPY = Regex("""(.+) becomes a copy of (another )?target (.+)\.""")
        val UNTIL_EOT_TARGET_GETS_GAINS_BECOMES = Regex("""Until end of turn, target (.+?) gets ([+-]\d+)/([+-]\d+), gains (.+?), and becomes an? (.+?) in addition to its other types\.""")
        val IF_THEY_DO = Regex("""If they do, (.+)""")
        val SHUFFLE_SELF_AND_TARGET = Regex("""Shuffle (.+?) and target (.+) into their owners' libraries\.""")
        val ACTIVATE_ONLY_ONCE_EVER = Regex("""Activate only once\.""")
        // dsk chunk 08
        val IT_WAS_A = Regex("""it was an? (.+)""")
        val GAIN_THAT_MUCH_PLUS = Regex("""If you would gain life, you gain that much life plus (\d+) instead\.""")
        val WOULD_BE_PUT_INTO_GRAVEYARD_EXILE_INSTEAD = Regex("""If an? (.+?) would be put into (an opponent's|your) graveyard from anywhere, exile it instead\.""")
        val TARGET_LOSES_THAT_MUCH = Regex("""Target (player|opponent) loses that much life\.""")
        val CREATE_NAMED_LAND_TOKEN = Regex("""Create an? (tapped )?colorless land token named (.+?) that is every basic land type\.""")
        // dsk chunk 07
        val WHILE = Regex("""(.+?) while (.+)""")
        val SELF_COSTS_LESS_AS_LONG_AS = Regex("""This spell costs ((?:\{[^}]+\})+) less to cast as long as (.+)\.""")
        val CANT_ATTACK_OR_BLOCK_UNLESS = Regex("""(.+) can't attack or block unless (.+)\.""")
        val MAXIMUM_HAND_SIZE_MINUS_CARD_TYPES = Regex("""Each opponent's maximum hand size is equal to (\w+) minus the number of those card types\.""")
        val WOULD_DEAL_DOUBLE = Regex("""If a source you control would deal (noncombat |combat )?damage to a permanent or player(?: while (.+?))?, it deals double that damage instead\.""")
        val ACTIVATE_ONLY_IF_AND_AS_SORCERY = Regex("""Activate only if (.+?) and only as a sorcery\.""")
        val CREATE_A_NUMBER_EQUAL_TO = Regex("""Create a number of (.+? tokens?(?: with .+?)?) equal to (the number of .+)\.""")
        val DEALS_THAT_AMOUNT_TO_TARGET = Regex("""(.+?) deals that (?:amount of|much) damage to target (.+)\.""")
        val IF_ITS_A_YOU_MAY_PUT_ONTO_BATTLEFIELD = Regex("""If it's an? (.+?), you may put it onto the battlefield\.""")
        val DISTRIBUTE_COUNTERS_AMONG = Regex("""Distribute (\w+) (\S+) counters among (\w+) or (\w+) target (.+)\.""")
        val DOUBLE_COUNTERS_ON_THOSE = Regex("""Double the number of (\S+) counters on those creatures\.""")
        val EACH_OPPONENT_MAY = Regex("""Each opponent may (.+)\.""")
        val DAMAGE_TO_EACH_WHO_DIDNT = Regex("""(.+?) deals damage equal to its power to each opponent who didn't sacrifice a permanent or discard a card this way\.""")
        val CHOOSE_TWO_TARGETS = Regex("""Choose target (.+?) and target (.+)\.""")
        val THE_TARGET_DEALS_DAMAGE_EQUAL_TO_ITS_POWER = Regex("""The (.+?) deals damage equal to its power to the (.+)\.""")
        val DEALS_X_TO_EACH_OF_UP_TO_X = Regex("""(.+?) deals X damage to each of up to X target (.+)\.""")
        val IF_INSTEAD_ALL = Regex("""(?:([A-Z][A-Za-z ]+) — )?If (.+?), instead (.+)""")
        val CHOOSE_MAX_IF = Regex("""(?:([A-Z][A-Za-z ]+) — )?Choose (one|two)\. If (.+), choose (one or more|both|\w+) instead\.""")
        // dsk chunk 06
        val MAY_LOOK_AT_ANY_TIME = Regex("""You may look at (.+) any time\.""")
        val MANIFEST_DREAD_TIMES = Regex("""Manifest dread (X|\w+) times, then put (X|\w+) (\S+) counters on each of those creatures\.""")
        val MAY_RETURN_FROM_GRAVEYARD_TO_HAND = Regex("""You may return an? (.+?) from your graveyard to your hand\.""")
        val REVEAL_TARGET = Regex("""Reveal target (.+)\.""")
        // dsk chunk 05
        val LOSES_ALL_AND_IS = Regex("""(Enchanted creature) loses all abilities and is an? (.+?) with base power and toughness (\d+)/(\d+) in addition to its other types\.""")
        val FACE_DOWN_CANT_BE_TURNED_FACE_UP = Regex("""As long as (enchanted creature) is face down, it can't be turned face up\.""")
        val DOESNT_UNTAP_DURING_CONTROLLERS = Regex("""(Enchanted creature|Equipped creature) doesn't untap during its controller's untap step\.""")
        val PLAYER_MANIFESTS_DREAD = Regex("""(Its controller|The exiled card's owner) manifests dread\.""")
        val ATTACH_SELF_TO_THAT_CREATURE = Regex("""Attach (.+) to that creature\.""")
        val WHEN_IT_DIES_THIS_TURN = Regex("""When it dies this turn, (.+)""")
        // dsk chunk 04
        val TRIGGERS_ONLY_ONCE_EVER = Regex("""This ability triggers only once\.""")
        val CANT_BE_BLOCKED_BY = Regex("""(.+) can't be blocked by (.+)\.""")
        val PUT_ONE_OF_COUNTERS = Regex("""Put an? ((?:[^ ,]+, )+or [^ ]+) counter on (.+)\.""")
        val PUT_THOSE_COUNTERS_ON = Regex("""Put those counters on (up to one )?target (.+)\.""")
        val THAT_BECOMES_CREATURE = Regex("""That (\w+) becomes an? (\d+)/(\d+) (\w+) creature in addition to its other types\.""")
        val IT_GAINS_UNTIL_YOUR_NEXT_TURN = Regex("""It gains (.+) until your next turn\.""")
        val PUT_ANY_NUMBER_WITH_DIFFERENT_FROM_AMONG = Regex("""Put any number of (.+?) with different (powers|mana values) from among them into your hand\.""")
        val EFFECT_IF_CONDITION = Regex("""(.+?) if (.+)\.""")
        // dsk chunk 03
        val UNLOCK_COSTS_LESS = Regex("""Unlock costs you pay cost ((?:\{[^}]+\})+) less\.""")
        val UNLOCK_A_LOCKED_DOOR = Regex("""Unlock a locked door of (?:up to (\w+) )?(target |an? )(.+)\.""")
        /** A Room with at least one locked door (CR 709.5c). */
        val HAS_A_LOCKED_DOOR = ObjectFilter.Not(ObjectFilter.And(listOf(
            ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Unlocked(arcanum.api.Door.Left))),
            ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Unlocked(arcanum.api.Door.Right))))))
        val LOCK_OR_UNLOCK = Regex("""Lock or unlock a door of target (.+)\.""")
        val RETURN_OR_UNLOCK = Regex("""Return an? (.+?) from your graveyard to your hand or (unlock .+)\.""")
        val CAN_ATTACK_AS_THOUGH_NO_DEFENDER = Regex("""(.+) can attack this turn as though it didn't have defender\.""")
        val EFFECT_IF = Regex("""(.+) if (this is the \w+ time this ability has resolved this turn)\.""")
        val PUT_CARD_FROM_A_GRAVEYARD_ONTO_BATTLEFIELD = Regex("""Put an? (.+?) from a graveyard onto the battlefield under your control\.""")
        val PUT_ALL_FROM_AMONG_THEM_REST_BOTTOM = Regex("""Put all (.+?) from among them into your hand and the rest on the bottom of your library in a random order\.""")
        // dsk chunk 02
        val NO_MAXIMUM_HAND_SIZE = Regex("""You have no maximum hand size( and don't lose the game for having 0 or less life)?\.""")
        val ALTERNATIVE_COST_FOR_SPELLS = Regex("""(Once each turn, )?[Yy]ou may pay (\{[^ ]+\}) rather than pay the mana cost for (?:a spell|spells) you cast( from exile)?\.""")
        val GETS_AND_GAINS_UNTIL_EOT = Regex("""(.+?) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) and (gains|loses) (.+) until (end of turn|your next turn)\.""")
        val DEALS_DAMAGE_EQUAL_TO = Regex("""(.+?) deals damage equal to (the number of .+?|the power of .+?) to (any target|target .+|each opponent)\.""")
        val YOU_MAY_PLAY_EXILED = Regex("""You may play (it|them) (this turn|until the end of your next turn)\.""")
        val MILL_THEN_RETURN_FROM_AMONG_THEM = Regex("""Mill (\w+) cards, then return up to (\w+) (.+) from among them to your hand\.""")
        val ADD_MANA_SPEND_ONLY_TO = Regex("""Add (.+)\. Spend this mana only to (.+)\.""")
        val CAST_SPELLS = Regex("""cast (?:an? )?(.+?) spells?""")
        // chunk 12
        const val DISCARDED = "discarded"
        const val RETURNED = "returned"
        val SEARCH_LIBRARY_AND_OR_GRAVEYARD = Regex("""Search your (library and/or graveyard|graveyard, hand, and/or library) for (.+?) and put it onto the battlefield\.""")
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
        val ADDITIONAL_EXILE_ANY_NUMBER_REDUCTION = Regex("""As an additional cost to cast this spell, you may exile any number of (.+) from your hand\. This spell costs ((?:\{[^}]+\})+) less to cast for each card exiled this way\.""")
        val BRACKETED = Regex("""(.*?)( ?)\[([^\]]+)\](.*)""")
        val CHOOSE_CARD_NAME = Regex("""Choose an? (?:(.+?) )?card name\.""")
        val CHOOSE_CARD_TYPE = Regex("""Choose ((?:\w+, )*\w+,? or \w+)\.""")
        val TAP_OR_UNTAP_ALL_OF_CHOSEN_TYPE =
            Regex("""Tap all untapped (\w+) of the chosen type target player controls, or untap all tapped \1 of that type that player controls\.""")
        val TARGET_REVEALS_HAND = Regex("""Target (player|opponent) reveals their hand( and discards all cards with that name)?\.""")
        val YOU_CHOOSE_FROM_IT_AND_GRAVEYARD = Regex("""You choose (an? .+?) from it, then choose (an? .+?) from their graveyard\.""")
        val YOU_CHOOSE_FROM_IT = Regex("""(?:You c|C)hoose (an? .+?) from it( with .+)?\.""")
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
        val RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (another )?target (.+?) from your graveyard to the battlefield( tapped)?(?: with an? (\S+) counter on it)?\.""")
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
        val PUT_THERE_FROM_BATTLEFIELD_THIS_TURN = ObjectFilter.ThisTurn(EventPattern.ZoneChange(from = Zone.Battlefield, to = Zone.Graveyard, objects = ObjectFilter.Any))
        val LOOK_PUT_UP_TO_ONTO_BATTLEFIELD_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put up to (\w+) (.+?) from among them onto the battlefield\. Put the rest on the bottom of your library in any order\.""")
        val THAT_PLAYER_ADDS_ADDITIONAL = Regex("""That player adds an additional (\{.\})\.""")
        // chunk 20
        val ATTACHED_GETS = Regex("""(?:Equipped|Enchanted) creature gets ([+-]\d+)/([+-]\d+)(?: for each (.+))?\.""")
        val ATTACH_SELF_TO_TARGET = Regex("""Attach (.+) to target (.+)\.""")
        val SACRIFICE_THAT = Regex("""Sacrifice that (\w+)\.""")
        val RETURN_SELF_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (.+) from your graveyard to the battlefield( tapped)?(?: with an? (\S+) counter on it)?\.""")
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
        val ENTERS_WITH_COUNTERS = Regex("""(.+) enters( tapped)? with (\w+) (\S+) counters? on it(?: if (.+))?\.""")
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
        val PUT_COUNTERS = Regex("""Put (a|an|one|two|three|four|five|six|seven|eight|nine|ten) (\S+) counters? on (.+)\.""")
        val WHEN_EXILED_THIS_WAY = Regex("""When an? (.+) is exiled this way, (.+)""")
        val PUT_EXILED_WITH_INTO_GRAVEYARD = Regex("""Put an? (.+?) exiled with (.+) into its owner's graveyard\.""")
        val THAT_CONTROLLER_CREATES = Regex("""That \w+'s controller creates (.+ tokens?)\.""")
        val GAIN_LIFE_FOR_EACH_SPELL_CAST = Regex("""(.+) gains? (\d+) life for each spell you've cast this turn\.""")
        val OPPONENT_GAINS_CONTROL = Regex("""An opponent gains control of (.+)\.""")
        val YOU_MAY = Regex("""You may (.+)\.""")
        // chunk 30
        val WHERE_X = Regex(""", where X is ([^.]+)""")
        val HAS_KEYWORDS_AS_LONG_AS = Regex("""(.+) has ([a-z ,]+) as long as (.+)\.""")
        val ESCAPES_WITH = Regex("""(.+) escapes with (a|an|one|two|three) (\S+) counters? on it\.""")
        val EXILE_FROM_GRAVEYARD_COST = Regex("""Exile (.+) from your graveyard""")
        val MILL = Regex("""Mill (\w+) cards?\.""")
        val LOSE_LIFE_AND_YOU_GAIN = Regex("""(Each opponent|Each player) loses (\d+) life(?: and you gain (\d+) life)?\.""")
        val PLAYERS_DISCARD = Regex("""(Each opponent|Each player) discards (\w+) cards?\.""")
        // chunk 31
        val YOU_MAY_WHEN_YOU_DO = Regex("""You may (.+?)\. When you do, (.+)""")
        val PUT_IT = Regex("""Put (?:it|that card) (onto the battlefield|into your hand)\.""")
        val LOOK_OR_REVEAL_TOP = Regex("""(Look at|Reveal) the top (\w+) cards of your library\.""")
        val MAY_FROM_AMONG_INTO_HAND = Regex("""You may (reveal|put) an? (.+?) from among them (?:and put it )?into your hand\.""")
        val PUT_THE_REST = Regex("""Put the rest (on the bottom of your library in a random order|into your graveyard)\.""")
        val LOOK_PUT_UP_TO_ONE_ON_TOP = Regex("""Look at the top (\w+) cards of your library\. Put up to one of them on top of your library and the rest on the bottom of your library in a random order\.""")
        val WIN_IF_X_AT_LEAST = Regex("""If X is greater than or equal to (.+), you win the game\.""")
        val RETURN_ALL_OR_SELF_TO_BATTLEFIELD = Regex("""Return (target |all )?(.+) from your graveyard to the battlefield( tapped)?\.""")
        val GAINS_FLASHBACK = Regex("""Target (.+) gains flashback until end of turn\. The flashback cost is equal to its mana cost\.""")
        val CHOOSE_TARGET = Regex("""Choose target (.+)\.""")
        val MAY_PUT_FROM_HAND = Regex("""You may put an? (.+) from your hand onto the battlefield( tapped)?\.""")
        val GRAVEYARD_ON_BOTTOM = Regex("""(Up to one target|Target) player puts all the cards from their graveyard on the bottom of their library in a random order\.""")
        // chunk 32
        val HAVE_KEYWORDS = Regex("""(.+) have ([a-z ,]+)\.""")
        val MAY_PAY_TO_CAST = Regex("""As long as (.+), you may pay (\{.+\}) to cast this spell\.""")
        val RETURN_YOURS_TO_HAND = Regex("""Return an? (.+ you control) to its owner's hand\.""")
        val TARGET_DEALS_DAMAGE_TO_ITSELF = Regex("""Target (.+) deals (\d+) damage to itself\.""")
        val UNTAP_ANOTHER_TARGET = Regex("""Untap (another )?target (.+)\.""")
        val GAIN_AND_GET = Regex("""(.+?) gains? (.+?) and gets? ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val CREATE_COPY_OF_TARGET = Regex("""Create a token that's a copy of (another )?target (.+?)(?:, except (.+))?\.""")
        // chunk 33
        val EACH_SACRIFICES = Regex("""Each (player|opponent) sacrifices (an?|X|two|three) (.+?)(?: of their choice)?\.""")
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
        // chunk 40
        val ACTIVATE_ONLY_ONCE_EACH_TURN = Regex("""Activate only once each turn\.""")
        val X_IN_COST = Regex("""\bX\b""")
        val ONE_OR_MORE_ENTER = Regex("""one or more (.+) enter""")
        val GROUP_CANT_BE_COUNTERED = Regex("""(.+ spells(?: .+)?) can't be countered\.""")
        val TOKENS_PLUS_THAT_MANY = Regex("""If one or more tokens would be created under your control, those tokens plus that many (\d+/\d+ .+? creature) tokens are created instead\.""")
        val KEYWORD_COUNTERS_PLUS = Regex("""If an? (\w+) triggered ability would put one or more (\S+) counters on (an? .+?), that many plus (\w+) (\S+) counters are put on it instead\.""")
        val UNTIL_EOT_BASE_PT = Regex("""Until end of turn, (.+?) (?:has|have) base power and toughness (\d+|X)/(\d+|X)(?: and (.+))?\.""")
        val BECOMES_ADDITIONAL_TYPE = Regex("""becomes an? (\w+) in addition to its other creature types""")
        val SELF_GETS_UNTIL_EOT = Regex("""(.+) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val CANT_BE_BLOCKED_EXCEPT_BY_THIS_TURN = Regex("""(.+) can't be blocked this turn except by creatures with (\w+)\.""")
        val TAP_OR_UNTAP = Regex("""Tap or untap (another )?target (.+)\.""")
        val GAINS_PROTECTION_OF_CHOICE = Regex("""(Another target|Target) (.+) gains protection from (colorless or from )?the color of your choice until end of turn\.""")
        val CHANGE_A_TARGET_TO = Regex("""Change a target of target (.+) to (.+)\.""")
        // chunk 41
        val COUNTER_ON = Regex("""(\S+) counter on (.+)""")
        val CHOOSE_TARGET_AND_CARD_IN_THAT_GRAVEYARD = Regex("""Choose target (.+?) a player controls and target (.+?) in that player's graveyard\.""")
        val BOTH_LEGAL_SACRIFICE_AND_RETURN = Regex("""If both targets are still legal as this ability resolves, that player simultaneously sacrifices the \w+ and returns the [\w ]+ to the battlefield\.""")
        val IF_IT_WAS = Regex("""If it was an? (.+?), (.+)""")
        val RETURN_CHOSEN_TO_HAND = Regex("""Return an? (.+) to its owner's hand\.""")
        // chunk 42
        val SELF_GETS_AS_LONG_AS = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) as long as (.+)\.""")
        val SELF_GETS_FOR_EACH = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) for each (.+)\.""")
        val TAPPED_TOKENS = Regex("""^(\w+) tapped """)
        val TARGET_REVEALS_TOP = Regex("""Target (opponent|player) reveals the top (\w+) cards? of their library\.""")
        val YOU_MAY_PUT_FROM_AMONG_THEM = Regex("""You may put an? (.+?) from among them onto the battlefield( under your control)?\.""")
        val TARGET_PLAYERS_LOSE_AND_SACRIFICE = Regex("""Any number of target players each lose (\d+) life and sacrifice an? (.+?) of their choice\.""")
        val CREATE_AND = Regex("""Create (.+? tokens?) and (.+)\.""")
        val YOU_AND = Regex("""You (?!may )(.+?) and (.+)\.""")
        // chunk 35
        val POWER_IS_EQUAL_TO = Regex("""(.+)'s power is equal to (.+)\.""")
        val DEVOTION_TO = Regex("""your devotion to (\w+(?: and \w+)?)""")
        val CANT_BLOCK = Regex("""(.+) can't block\.""")
        val CREATE_FOR_EACH_COUNTER_ON_SELF = Regex("""Create (an? .+ token(?: with .+?)?) for each (?:(\S+) )?counter on (.+)\.""")
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
        val MAY_PUT_FROM_AMONG_THEM_INTO_HAND = Regex("""You may put (an?|any number of) (.+?) from among (?:them|the cards milled this way) into your hand\.""")
        val IF_IT_ENTERED_UNDER_YOUR_CONTROL = Regex("""If it entered under your control, (.+)""")
        val PUT_ALL_EXILED_WITH_INTO_HANDS = Regex("""Put all cards exiled with (.+) into their owners' hands\.""")
        val SACRIFICE_A = Regex("""Sacrifice (another|an?) (.+)\.""")
        val EXILE_UP_TO_TARGET = Regex("""Exile up to (\w+) (other )?target (.+)\.""")
        val AT_BEGINNING_OF_NEXT_END_STEP = Regex("""At the beginning of the next end step, (.+)""")
        val RETURN_IT_UNDER_OWNERS_CONTROL = Regex("""Return (?:that card|it) to the battlefield( tapped)? under its owner's control(?: with (\w+) (\S+) counters? on it)?\.""")
        val LOSE_LIFE_EQUAL_TO_THAT_CARDS_MANA_VALUE = Regex("""(.+) loses? life equal to that card's mana value\.""")
        val UNLESS_CONDITION = Regex("""(.+) unless (.+)\.""")
        // chunk 45
        val AS_LONG_AS_FIRST = Regex("""As long as (.+?), (.+)""")
        val AS_LONG_AS_LAST = Regex("""(.+) as long as (.+)\.""")
        val SUBJECT_PREDICATES = Regex("""(.+?) ((?:gets?|has|have|can't|attacks|loses) .+)\.""")
        val PREDICATE_SPLIT = Regex(""",? and (?=(?:gets?|has|have|can't|attacks|loses) )|, (?=(?:gets?|has|have|can't|attacks|loses) )""")
        val GETS_PT = Regex("""gets? ([+-]\d+)/([+-]\d+)(?: for each (.+))?""")
        val HAS_KEYWORDS = Regex("""(?:has|have) (.+)""")
        val MAY_PLAY_FROM_TOP = Regex("""[Yy]ou may (play|cast) (.+?) from the top of your library\.""")
        val ONCE_EACH_TURN_CAST_FROM_GRAVEYARD = Regex("""Once during each of your turns, you may cast an? (.+) from your graveyard\.""")
        val CAST_FROM_GRAVEYARD_USING = Regex("""You may cast (.+) from your graveyard using its (\w+) ability\.""")
        val COLLECT_EVIDENCE = Regex("""Collect evidence (\d+)""")
        // chunk 46
        const val CHOSEN_TYPE = "type"
        // dsk chunk 14
        const val CHOSEN_COLOR = "color"
        val CANT_CAST_ADDITIONAL = Regex("""Each player who has cast an? (.+?) spell this turn can't cast additional (.+?) spells\.""")
        val EACH_OTHER_ENTERS_WITH = Regex("""Each other (.+?)( of the chosen type)? enters with (?:an additional (\S+) counter|a number of additional (\S+) counters) on it(?: equal to (.+))?\.""")
        val SELF_PROPERTY = Regex("""(.+)'s (power|toughness)""")
        val AS_ENTERS_CHOOSE_TYPE = Regex("""As (.+) enters, choose a creature type\.""")
        val IS_THE_CHOSEN_TYPE = Regex("""(.+) is the chosen type in addition to its other types\.""")
        val PT_EACH_EQUAL_TO_NUMBER = Regex("""(.+)'s power and toughness are each equal to the number of (.+)\.""")
        // dsk chunk 03: "Enchantment spells you cast cost {1} less to cast."
        val SPELLS_COST = Regex("""(.+) spells( you cast)? cost ((?:\{[^}]+\})+) (more|less) to cast\.""")
        val PREVENT_ALL_COMBAT_DAMAGE_TO = Regex("""Prevent all combat damage that would be dealt to (.+)\.""")
        val BOLSTER = Regex("""Bolster (\d+)\.""")
        val UNTIL_YOUR_NEXT_TURN_CANT_CAST = Regex("""Until your next turn, target player(?: who was dealt combat damage by (.+?) this turn)? can't cast (.+?) spells\.""")
        // chunk 47
        val NTH_SPELL_EACH_TURN = Regex("""(a player|an opponent) casts their (first|second|third) (?:(.+) )?spell each turn""")
        val CONNIVES = Regex("""(.+) connives\.""")
        val PUT_THAT_MANY_COUNTERS = Regex("""Put that many (\S+) counters on (.+)\.""")
        val YOU_LOSE_LIFE_EQUAL_TO_ITS = Regex("""You lose life equal to its (mana value|power)\.""")
        val YOU_MAY_PLAY_THAT_CARD = Regex("""You may play (that card|those cards) (this turn|until your next end step)\.""") // blb gaps: those cards
        val IF_YOU_WIN_THE_FLIP = Regex("""If you win the flip, (.+)""")
        val RETURN_THAT_TO_OWNERS_HAND = Regex("""Return that (?:spell|card|creature|permanent) to its owner's hand\.""")
        val COPY_THAT_SPELL = Regex("""Copy that spell(?:, and you may choose new targets for the copy)?\.""")
        val UNLESS_THAT_PLAYER_PAYS = Regex("""(.+?) unless that player pays (\{[^}]+\}(?:\{[^}]+\})*)(?:, where X is (.+?))?\.""")
        // chunk 50
        val NTH_SPELL_IF = Regex("""if it's the (\w+) (.+?) spell you cast this turn, (.+)""")
        val NTH_CARD_DRAWN = Regex("""you draw your (\w+) card (?:in a|each) turn""")
        val SACRIFICE_SELF = Regex("""Sacrifice (.+)\.""")
        val UNLESS_YOU = Regex("""(.+) unless you (.+)\.""")
        val MAY_CAST_SELF_FROM_GRAVEYARD_BY = Regex("""You may cast (.+) from your graveyard by (.+) rather than paying its mana cost\.""")
        val GERUNDS = mapOf("paying" to "", "sacrificing" to "Sacrifice", "discarding" to "Discard", "exiling" to "Exile")
        // chunk 51
        val ANY_PLAYER_MAY_ACTIVATE = Regex("""Any player may activate this ability\.""")
        val TARGET_GAINS_UNTIL_EOT = Regex("""(Another target|Target) (.+) gains (.+) until end of turn\.""")
        // chunk 52
        val HAS_ACTIVATED_ABILITIES_OF = Regex("""(.+) has all activated abilities of (.+?)( except for loyalty abilities)?\.( You may spend mana as though it were mana of any color to activate those abilities\.)?""")
        val COUNTERS_MINUS_ONE = Regex("""If one or more (\S+) counters would be put on (.+), that many (\S+) counters minus one are put on it instead\.""")
        val AS_ENTERS_CHOOSE = Regex("""As (.+) enters, (you may )?choose (.+)\.""")
        val CARD_NAME = Regex("""an? (.+) card name""")
        const val CHOSEN_NAME = "name"
        const val CHOSEN = "chosen"
        val CANT_BE_ACTIVATED = Regex("""Activated abilities of (.+) can't be activated\.""")
        val EVERY_ZONE = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)
        val PUT_FOUND_IN_HAND_REST_ON_BOTTOM = Regex("""Put that card into your hand and the rest on the bottom of your library in (a random|any) order\.""")
        val REVEAL_UNTIL = Regex("""Reveal cards from the top of your library until you reveal (.+)\.""")
        const val FOUND = "found"
        const val REST = "rest"
        val FOR_THE_FIRST_TIME_EACH_TURN = Regex("""(.+) for the first time each turn""")
        // chunk 56
        val CHAPTERS = Regex("""([IVX]+(?:, [IVX]+)*) — (.+)""")
        val ROMAN = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7)
        val CAN_BLOCK_ONLY = Regex("""(.+) can block only (.+)\.""")
        val LABEL_TWICE_DURING_YOUR_TURNS = Regex("""(.+) can (\w+) twice during each of your turns rather than once\.""")
        val YOU_CREATE_A = Regex("""you create an? (.+) token""")
        val EXILE_TOP_N = Regex("""Exile the top (\w+) cards of your library\.""")
        val TARGET_OPPONENT_MILLS = Regex("""Target opponent mills (\w+) cards?\.""")
        val UP_TO_ONE_TARGET_GETS = Regex("""Up to one target (.+) gets ([+-]\d+)/([+-]\d+) until (end of turn|your next turn)\.""")
        val TARGET_GETS_FOR_EACH_UNTIL_EOT = Regex("""Target (.+) gets ([+-]\d+)/([+-]\d+) until end of turn for each (.+)\.""")
        val MAY_CAST_TARGET_FROM_GRAVEYARD_THIS_TURN = Regex("""You may cast target (.+) from your graveyard this turn\.""")
        val EXILE_SELF_RETURN_TRANSFORMED = Regex("""Exile (.+), then return (?:it|him|her) to the battlefield transformed under (?:his owner's|her owner's|its owner's|(your)) control\.""")
        val TRANSFORM = Regex("""Transform (.+)\.""")
        val RETURN_ALL_TO_HANDS = Regex("""Return all (.+) to their owners' hands\.""")
        val BECOMES_CREATURE = Regex("""(Up to one target|Target) (.+?) becomes an? (\d+)/(\d+) (\w+) (\w+) creature(?: with (.+?))? in addition to its other types\.""")
        val BECOME_COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
        val IF_CONDITION = Regex("""If (.+?), (.+)\.""")
        val IT_HAS_NO_COUNTERS = Regex("""it has no (\S+) counters on it""")
    }

    // ---- blb gaps: Bloomburrow wordings that needed new rules (sets/blb/BlbChunk14GapsUntilAndCopiesTest.kt)

    /** blb gaps: this object entering or attacking. */
    private fun selfEntersOrAttacks(event: EventPattern): Boolean = event == EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield) ||
        event == EventPattern.Attack(ObjectFilter.Is(Objects.Self))

    /** blb gaps: static wordings; null when none reads [line]. */
    private fun blbGapsStatic(line: String): Ability? {
        // "As an additional cost to cast this spell, you may sacrifice any number of nonland permanents. This spell costs {1}
        // less to cast for each permanent sacrificed this way." (CR 601.2b, 601.2f: the reduction counts the chosen permanents)
        BlbGaps.ADDITIONAL_SACRIFICE_ANY_NUMBER_REDUCTION.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val per = ManaCost.parse(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(
                Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(filter, count = null), preBind = BlbGaps.SACRIFICED)))),
                Mechanic.CostChange(ObjectFilter.Is(Objects.Self), change = arcanum.api.CostChange.Less(per, Value.Count(Objects.Bound(BlbGaps.SACRIFICED))))),
                affects = Affected.Objects(Objects.Self))
        }
        // "The first instant or sorcery spell you cast each turn costs {U} less to cast for each land you control with a flood
        // counter on it." It applies while you have cast no such spell this turn: a spell becomes cast only once its cost is
        // determined and paid (CR 601.2f, 601.2i).
        BlbGaps.FIRST_SPELL_EACH_TURN_COSTS_LESS.matchEntire(line)?.let { m ->
            val kind = Filters.noun(m.groupValues[1]) ?: return null
            val spells = Filters.noun(m.groupValues[1] + " spell") ?: return null
            val mana = ManaCost.parse(m.groupValues[2]) ?: return null
            val times = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Value.Count(Filters.objects(it) ?: return null) } ?: Value.Fixed(1)
            return Ability.Static(listOf(Mechanic.CostChange(Filters.all(spells, ObjectFilter.ControlledBy(PlayerFilter.You)), arcanum.api.CostChange.Less(mana, times))),
                affects = Affected.Objects(Objects.Self),
                condition = arcanum.api.Condition.Not(arcanum.api.Condition.HappenedThisTurn(EventPattern.Cast(kind, by = PlayerFilter.You), atLeast = 1,
                    measure = Measure.Events)))
        }
        // "You may have this creature enter as a copy of any creature on the battlefield [with …], except [exceptions]." (CR
        // 614.1c, 707.9b)
        BlbGaps.ENTER_AS_COPY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val of = copyCandidates(m.groupValues[2]) ?: return null
            val except = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { gapCopyExceptions(it) ?: return null }.orEmpty()
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersAsCopy(of, except, optional = true)))), affects = Affected.Objects(Objects.Self))
        }
        return null
    }

    /** blb gaps: "creature on the battlefield with mana value less than or equal to the amount of mana spent to cast this
     * creature" (the mana spent, kept by the permanent, CR 400.7d). */
    private fun copyCandidates(phrase: String): ObjectFilter? {
        val m = BlbGaps.ON_THE_BATTLEFIELD_WITH.matchEntire(phrase) ?: return null
        val noun = Filters.noun(m.groupValues[1]) ?: return null
        if (m.groupValues[2].isEmpty()) return noun
        BlbGaps.MANA_VALUE_AT_MOST_MANA_SPENT.matchEntire(m.groupValues[2])?.takeIf { isSelf(it.groupValues[1]) } ?: return null
        return Filters.all(noun, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, Value.ManaSpent(of = Objects.Self)))
    }

    /** blb gaps: "it's a Bird in addition to its other types and it has flying" (CR 707.9b). */
    private fun gapCopyExceptions(text: String): List<arcanum.api.CopyException>? {
        copyExceptions(text)?.let { return it }
        val m = BlbGaps.TYPE_IN_ADDITION_AND_HAS.matchEntire(text) ?: return null
        val subtype = m.groupValues[1].takeIf { it in RulesWords.subtypes } ?: return null
        val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
        return listOf(arcanum.api.CopyException.Types(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
            arcanum.api.CopyException.AddAbilities(keywords))
    }

    /** blb gaps: "loses all abilities", "can't be blocked", "has base power and toughness 2/2" … (layers 6, 7b). */
    private fun gapPredicates(text: String): List<Mechanic>? = text.split(BlbGaps.PREDICATE_AND).map { predicate ->
        BlbGaps.BASE_PT.matchEntire(predicate)?.let { m ->
            Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[1].toInt()), Value.Fixed(m.groupValues[2].toInt()))
        } ?: predicates(predicate)?.singleOrNull() ?: return null
    }

    private fun gapDuration(text: String): arcanum.api.Duration? = when (text) {
        "until end of turn", "this turn" -> arcanum.api.Duration.EndOfTurn
        "until your next turn" -> arcanum.api.Duration.UntilYourNextTurn
        else -> null
    }

    /** blb gaps: the object "it" names after a sentence that chose a target: that target; otherwise the ability's "it". */
    private fun gapIt(context: Context): Objects? {
        val from = context.previous?.first ?: context.targets.size
        return context.targets.drop(from).lastOrNull { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: context.it
    }

    /** blb gaps: effect wordings; null when none reads [sentence]. */
    private fun blbGapsEffect(sentence: String, context: Context): List<Step>? {
        // "Up to one target artifact, creature, or planeswalker an opponent controls loses all abilities until your next turn.",
        // "Target creature you control can't be blocked this turn." (CR 611.2c: the affected object is fixed as it resolves)
        BlbGaps.TARGET_UNTIL.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return@let
            val mechanics = gapPredicates(m.groupValues[3]) ?: return@let
            val duration = gapDuration(m.groupValues[4]) ?: return@let
            val upTo = if (m.groupValues[1].isEmpty()) null else Numbers.count(m.groupValues[1]) ?: return@let
            val name = context.name(Filters.slotName(noun))
            context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(if (upTo == null) 1 else 0), max = Value.Fixed(upTo ?: 1))
            context.it = Objects.Target(name)
            return listOf(Step.Do(Effect.ApplyMechanics(mechanics, Affected.Objects(Objects.Target(name)), duration)))
        }
        // "If it's a creature, it also has base power and toughness 2/2 until your next turn." (checked as it resolves)
        BlbGaps.IF_ITS_A_IT_ALSO.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return@let
            val filter = Filters.noun(m.groupValues[1]) ?: return@let
            val mechanics = gapPredicates(m.groupValues[2]) ?: return@let
            val duration = gapDuration(m.groupValues[3]) ?: return@let
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, filter),
                then = listOf(Step.Do(Effect.ApplyMechanics(mechanics, Affected.Objects(it), duration)))))
        }
        // "It's an Island in addition to its other types for as long as it has a flood counter on it." (CR 305.7, 611.2b)
        BlbGaps.TYPE_FOR_AS_LONG_AS_COUNTER.matchEntire(sentence)?.let { m ->
            val it = gapIt(context) ?: return@let
            val subtype = m.groupValues[1].takeIf { s -> s in RulesWords.subtypes } ?: return@let
            val kind = Counters.kind(m.groupValues[2]) ?: return@let
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype))))),
                Affected.Objects(it), arcanum.api.Duration.AsLongAs(arcanum.api.Condition.ObjectMatches(it, ObjectFilter.HasCounter(kind, atLeast = 1))))))
        }
        // "Whenever that creature deals combat damage this turn, [effect]": a delayed trigger that triggers each time this turn
        // (CR 603.7b), about the creature named now (603.7c); "it" in it is the creature dealing the damage
        BlbGaps.WHENEVER_THAT_CREATURE_THIS_TURN.matchEntire(sentence)?.let { m ->
            val that = gapIt(context) ?: return@let
            val event = EventPattern.Damage(ObjectFilter.Is(that), RecipientFilter(), combat = if (m.groupValues[1].isNotEmpty()) true else null)
            val inner = instructions(m.groupValues[2], event = event, itZone = Zone.Battlefield)?.takeIf { it.targets.isEmpty() } ?: return@let
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(event), inner, duration = arcanum.api.Duration.EndOfTurn, repeats = true)))
        }
        // "Then for each blight counter on it, each opponent loses 4 life unless …": the rest happens that many times, counted
        // as it begins (CR 608.2h)
        BlbGaps.FOR_EACH_COUNTER_ON_IT.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return@let
            val kind = Counters.kind(m.groupValues[1]) ?: return@let
            val steps = effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }, context) ?: return@let
            return listOf(Step.Repeat(Value.Of(arcanum.api.NumericProperty.Counters(kind), it, arcanum.api.Combine.Single), steps))
        }
        // "Each opponent loses 4 life unless that player sacrifices a nonland permanent of their choice or discards a card." (CR
        // 101.4, 118.12a): each opponent may pay; those who don't lose the life
        BlbGaps.EACH_OPPONENT_UNLESS.matchEntire(sentence)?.let { m ->
            val options = m.groupValues[2].split(" or ").map { option ->
                val verb = option.substringBefore(' ')
                val plain = BlbGaps.THIRD_PERSON[verb]?.let { it + option.removePrefix(verb) } ?: option
                Cost(costPart(plain.removeSuffix(" of their choice").replaceFirstChar { it.uppercase() }) ?: return@let)
            }
            val player = Players.Bound("opponent")
            val effect = BlbGaps.LOSES_LIFE.matchEntire(m.groupValues[1])?.let { Effect.LoseLife(player, Value.Fixed(it.groupValues[1].toInt())) } ?: return@let
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true,
                steps = unlessPays(player, options.singleOrNull() ?: Cost(listOf(CostPart.OneOf(options))), listOf(Step.Do(effect)))))
        }
        // "At the beginning of your next end step, [effect]." (a delayed trigger, CR 603.7)
        BlbGaps.AT_YOUR_NEXT_END_STEP.matchEntire(sentence)?.let { m ->
            val targets = context.targets.size
            val inner = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context)?.takeIf { context.targets.size == targets } ?: return@let
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.You)),
                Instructions(steps = inner))))
        }
        // "Stormwing deals 2 damage to each opponent for each of those cards that are still exiled." (the cards that are still
        // the same objects in exile, CR 400.7)
        BlbGaps.DEALS_DAMAGE_FOR_EACH_STILL_EXILED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val them = context.them ?: return@let
            val players = Filters.players(m.groupValues[3]) ?: return@let
            val per = Value.Count(Objects.Where(them, ObjectFilter.InZone(Zone.Exile)))
            val n = Numbers.count(m.groupValues[2]) ?: return@let
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = players), times(per, n), source = Objects.Self)))
        }
        // "Choose target creature." (only a target; "that creature" and "it" afterwards)
        BlbGaps.CHOOSE_TARGET.matchEntire(sentence)?.takeIf { !it.groupValues[1].contains(" card") }?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return@let)
            context.it = target
            return emptyList()
        }
        // "If that creature's power is 2 or less or if you control another Lizard, gain control of that creature until end of
        // turn, untap it, and it gains haste until end of turn." (CR 613.1b; the condition is checked as it resolves)
        BlbGaps.IF_OR_IF.matchEntire(sentence)?.let { m ->
            val conditions = m.groupValues[1].split(" or if ").map { gapCondition(it, context) ?: return@let }
            val g = BlbGaps.GAIN_CONTROL_UNTAP_GAINS.matchEntire(m.groupValues[2]) ?: return@let
            val it = context.it ?: return@let
            val keywords = g.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return@let }
            val then = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(it), arcanum.api.Duration.EndOfTurn)),
                Step.Do(Effect.Untap(it)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)), Affected.Objects(it), arcanum.api.Duration.EndOfTurn)))
            return listOf(Step.If(conditions.singleOrNull() ?: arcanum.api.Condition.Or(conditions), then = then))
        }
        // "For each card type, you may exile a card of that type from among them." (one card per card type; a card of several
        // types stands for one of them, CR 205.2a)
        BlbGaps.FOR_EACH_QUALITY_MAY_FROM_AMONG.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return@let
            val quality = when (m.groupValues[1]) { "card type" -> arcanum.api.SharedQuality.CardType; "color" -> arcanum.api.SharedQuality.Color; else -> return@let }
            val to = when (m.groupValues[2]) { "exile" -> Zone.Exile; "put into your hand" -> Zone.Hand; else -> return@let }
            context.picked = BlbGaps.CHOSEN
            context.them = Objects.Bound(BlbGaps.EXILED)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, min = Value.Fixed(0), max = null, from = Objects.Bound(seen), onePer = quality),
                    bind = BlbGaps.CHOSEN, chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound(BlbGaps.CHOSEN), Destination(to), postBind = if (to == Zone.Exile) BlbGaps.EXILED else null)))
        }
        // "Put the rest into your graveyard." after picking from among them
        if (sentence == "Put the rest into your graveyard." && context.seen != null && context.picked != null) {
            return listOf(Step.Do(Effect.Move(Objects.Except(Objects.Bound(context.seen!!), Objects.Bound(context.picked!!)), Destination(Zone.Graveyard))))
        }
        // "You may cast a spell from among the exiled cards without paying its mana cost if you exiled four or more cards this
        // way." (CR 608.2g: cast while this resolves)
        BlbGaps.MAY_CAST_FROM_AMONG_EXILED_IF.matchEntire(sentence)?.let { m ->
            val exiled = context.them as? Objects.Bound ?: return@let
            if (exiled.name != BlbGaps.EXILED) return@let
            val n = Numbers.count(m.groupValues[1]) ?: return@let
            context.picked = BlbGaps.CAST
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Count(exiled), arcanum.api.Comparison.AtLeast, Value.Fixed(n)), then = listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Not(ObjectFilter.Type(arcanum.api.CardType.Land)), min = Value.Fixed(0), max = Value.Fixed(1),
                    from = exiled), bind = BlbGaps.CAST, chooser = Players.You),
                Step.Do(Effect.Cast(Objects.Bound(BlbGaps.CAST), PlayKind.Cast, withoutPaying = true, caster = Players.You)))))
        }
        // "Then put the rest of the exiled cards into your hand."
        if (sentence == "Put the rest of the exiled cards into your hand." && context.picked == BlbGaps.CAST) {
            return listOf(Step.Do(Effect.Move(Objects.Except(Objects.Bound(BlbGaps.EXILED), Objects.Bound(BlbGaps.CAST)), Destination(Zone.Hand))))
        }
        return null
    }

    /** blb gaps: "that creature's power is 2 or less", "you control another Lizard". */
    private fun gapCondition(text: String, context: Context): arcanum.api.Condition? {
        BlbGaps.THAT_CREATURES_POWER.matchEntire(text)?.let { m ->
            val it = context.it ?: return null
            val comparison = if (m.groupValues[2] == "less") arcanum.api.Comparison.AtMost else arcanum.api.Comparison.AtLeast
            return arcanum.api.Condition.ObjectMatches(it, ObjectFilter.Power(comparison, Value.Fixed(m.groupValues[1].toInt())))
        }
        return Conditions.condition(text, self)
    }

    /** Regular expressions and names of the blb gaps wordings. */
    private object BlbGaps {
        const val SACRIFICED = "sacrificed"
        const val CHOSEN = "chosen"
        const val EXILED = "exiled"
        const val CAST = "cast"
        val ADDITIONAL_SACRIFICE_ANY_NUMBER_REDUCTION = Regex("""As an additional cost to cast this spell, you may sacrifice any number of (.+?)\. This spell costs ((?:\{[^}]+\})+) less to cast for each (?:permanent|creature|artifact) sacrificed this way\.""")
        val FIRST_SPELL_EACH_TURN_COSTS_LESS = Regex("""The first (.+?) spell you cast each turn costs ((?:\{[^}]+\})+) less to cast(?: for each (.+))?\.""")
        val ENTER_AS_COPY = Regex("""You may have (.+?) enter as a copy of any (.+?)(?:, except (.+))?\.""")
        val ON_THE_BATTLEFIELD_WITH = Regex("""(.+?) on the battlefield(?: with (.+))?""")
        val MANA_VALUE_AT_MOST_MANA_SPENT = Regex("""mana value less than or equal to the amount of mana spent to cast (.+)""")
        val TYPE_IN_ADDITION_AND_HAS = Regex("""it's an? (\S+) in addition to its other types and it has (.+)""")
        val PREDICATE_AND = Regex(""" and (?=(?:has|loses|can't|gets) )""")
        val BASE_PT = Regex("""(?:also )?has base power and toughness (\d+)/(\d+)""")
        val TARGET_UNTIL = Regex("""(?:Up to (\w+) target|Target) (.+?) ((?:loses all abilities|can't be blocked|has base power and toughness).*?) (until end of turn|until your next turn|this turn)\.""")
        val IF_ITS_A_IT_ALSO = Regex("""If it's an? (.+?), it (?:also )?(.+?) (until end of turn|until your next turn)\.""")
        val TYPE_FOR_AS_LONG_AS_COUNTER = Regex("""It's an? (\S+) in addition to its other types for as long as it has an? (\S+) counter on it\.""")
        val WHENEVER_THAT_CREATURE_THIS_TURN = Regex("""Whenever that creature deals (combat )?damage this turn, (.+)""")
        val FOR_EACH_COUNTER_ON_IT = Regex("""For each (\S+) counter on it, (.+)""")
        val EACH_OPPONENT_UNLESS = Regex("""Each opponent (.+?) unless that player (.+)\.""")
        val LOSES_LIFE = Regex("""loses (\d+) life""")
        val THIRD_PERSON = mapOf("sacrifices" to "sacrifice", "discards" to "discard", "pays" to "pay", "exiles" to "exile", "returns" to "return")
        val AT_YOUR_NEXT_END_STEP = Regex("""At the beginning of your next end step, (.+)""")
        val DEALS_DAMAGE_FOR_EACH_STILL_EXILED = Regex("""(.+?) deals (\w+) damage to (.+?) for each of those cards that are still exiled\.""")
        val CHOOSE_TARGET = Regex("""Choose target (.+)\.""")
        val IF_OR_IF = Regex("""If (.+? or if .+?), (.+)\.""")
        val GAIN_CONTROL_UNTAP_GAINS = Regex("""gain control of (that creature|it) until end of turn, untap it, and it gains (.+) until end of turn""")
        val THAT_CREATURES_POWER = Regex("""that creature's power is (\d+) or (less|greater)""")
        val FOR_EACH_QUALITY_MAY_FROM_AMONG = Regex("""For each (card type|color), you may (exile|put into your hand) a card of that (?:type|color) from among them\.""")
        val MAY_CAST_FROM_AMONG_EXILED_IF = Regex("""You may cast a spell from among the exiled cards without paying its mana cost if you exiled (\w+) or more cards this way\.""")
    }
}

/** rtr chunk 08: "second [name]" ("third …"), the first such name not in [taken] (CR 115.3: another instance of "target"). */
internal fun nextTargetName(name: String, taken: List<String>): String =
    listOf("second", "third", "fourth", "fifth", "sixth").map { "$it $name" }.first { it !in taken }

/** rtr chunk 08: [step] with every reference to target slot [from] made to [to]. */
internal fun renameTargetSlot(step: Step, from: String, to: String): Step {
    val json = kotlinx.serialization.json.Json
    fun walk(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
        is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.mapValues { (k, v) ->
            if ((k == "slot" && e["type"]?.toString() == "\"target\"" || k == "target") && v == kotlinx.serialization.json.JsonPrimitive(from))
                kotlinx.serialization.json.JsonPrimitive(to) else walk(v)
        })
        is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { walk(it) })
        else -> e
    }
    return json.decodeFromJsonElement(Step.serializer(), walk(json.encodeToJsonElement(Step.serializer(), step)))
}

/** chunk 30: [x] in place of every announced X: a number the text defines (CR 107.3c). Walks the JSON form, as
 * DefinitionChecks does, to reach every nested value. */
private fun Instructions.withX(x: Value): Instructions {
    val json = kotlinx.serialization.json.Json
    val announced = json.encodeToJsonElement(Value.serializer(), Value.Announced(name = "X"))
    val replacement = json.encodeToJsonElement(Value.serializer(), x)
    fun replace(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when {
        e == announced -> replacement
        e is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.mapValues { replace(it.value) })
        e is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { replace(it) })
        else -> e
    }
    return json.decodeFromJsonElement(Instructions.serializer(), replace(json.encodeToJsonElement(Instructions.serializer(), this)))
}

/** "[steps] unless [payer] pays [cost]" (CR 118.12a): the payer may pay; if they didn't, [steps] happen (D52). The
 * outcome is named "paid", or "unless-paid" when [steps] already use that name. */
private fun unlessPays(payer: Players, cost: Cost, steps: List<Step>): List<Step> {
    val bind = if (kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Step.serializer()), steps)
            .contains("\"paid\"")) "unless-paid" else "paid"
    return listOf(Step.Pay(cost, payer = payer, bind = bind),
        Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did(bind)), then = steps))
}

internal fun String.removeArticle() = removePrefix("a ").removePrefix("an ")
