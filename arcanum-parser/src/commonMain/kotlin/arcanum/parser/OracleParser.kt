package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.FaceDefinition
import arcanum.api.HasAmount
import arcanum.api.HasCost
import arcanum.api.Keyword
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.ManaCost
import arcanum.api.Objects

/**
 * Parses one card face's oracle text into a [FaceDefinition]. Reads only the text (no card-data keyword lists), so it
 * works on any card, including custom ones. What it can't read is kept as [Ability.Unparsed], never guessed.
 *
 * Understood: keyword lines for the keywords in [KeywordSyntax.all], and the sentence wordings in [Sentences] (the
 * cube chunk tests list them). Reminder text in parentheses is ignored. An instant or sorcery with any unread line keeps
 * all its spell text unparsed (CR 608.2b).
 */
object OracleParser {
    /**
     * The parser's output version. Bump it with every change that changes what the parser returns for any card (a
     * wording newly read, a definition shaped differently, a fix), so stored definitions made by an older parser are
     * parsed again.
     */
    const val VERSION: Int = 88

    /**
     * Every subtype the pinned Comprehensive Rules list (CR 205.3g–m, 205.3q), as the parser reads them: what a host
     * offers when a player chooses a subtype from no given list (CR 205.3m: "choose a creature type" may name any creature
     * type, whether or not an object in the game has it). Not a parse result: it doesn't change [VERSION].
     */
    val subtypes: Set<String> get() = RulesWords.subtypes

    /** Parses a whole face: its printed characteristics and its rules text. */
    fun parse(card: OracleCard): FaceDefinition {
        val face = characteristics(card)
        // CR 309.4: a dungeon card's text is its rooms
        if (arcanum.api.CardType.Dungeon in face.types) {
            val self = selfNames(card.name, face)
            MechanicWordings.rooms(card.text) { Sentences(self, true).instructions(it) }?.let { d ->
                return face.copy(rooms = d.rooms, abilities = listOfNotNull(d.only))
            }
            return face.copy(abilities = listOf(Ability.Unparsed(card.text)))
        }
        val isSpell = face.types.any { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }
        val self = selfNames(card.name, face)
        var abilities = abilities(card.text, self, isSpell)
        val hasX = face.manaCost?.symbols?.any { it is arcanum.api.ManaSymbol.Variable } == true ||
            // ecl chunk 13: "As an additional cost to cast this spell, blight X." (X announced as it is cast, CR 601.2b)
            abilities.any { a -> a is Ability.Static && a.mechanics.any { it is arcanum.api.Mechanic.XAtMost } } ||
            // sos chunk 08: "As an additional cost to cast this spell, pay X life." (X announced as it is cast, CR 601.2b)
            (isSpell && card.text.contains("As an additional cost to cast this spell, pay X life.")) ||
            // tla chunk 01: "As an additional cost to cast this spell, waterbend {X}." (X announced as it is cast, CR 601.2b, 107.3)
            (isSpell && card.text.contains("As an additional cost to cast this spell, waterbend {X}."))
        if (hasX) abilities = abilities.map { if (it is Ability.Spell) it.copy(instructions = it.instructions.copy(announced = listOf("X"))) else it }
        // Overload (CR 702.96b): the spell's text with "target" replaced by "each".
        if (abilities.any { it is Keyword.Overload }) {
            // rtr chunk 03: the spell ability's own text, not its static lines ("This spell can't be countered.")
            val spellText = abilities.filterIsInstance<Ability.Spell>().singleOrNull()?.text?.replace("\n", " ")
                ?: card.text.split('\n').map { REMINDER.replace(it, "").trim() }
                    .filter { it.isNotEmpty() && keywordLine(it) == null }.joinToString(" ")
            // rtr chunk 08: a sentence starting with "Target" becomes "Each"
            val overloaded = Sentences(self, isSpell).instructions(spellText.replace("target ", "each ").replace("Target ", "Each "))
                // rtr chunk 03: X announced for the overloaded spell as for the spell (CR 107.3a)
                ?.let { if (hasX) it.copy(announced = listOf("X")) else it }
            abilities = abilities.map { if (it is Keyword.Overload && overloaded != null) it.copy(instructions = overloaded) else it }
        }
        // CR 702.55c: "the creature it haunts" triggers in exile, a modal one too
        abilities = abilities.map { MechanicWordings.haunting(it) ?: it }
        // spm chunk 06: "It gains this card's other abilities." (CR 113.10): the face's other abilities written into the effect
        abilities = abilities.map { a -> if (a is Ability.Activated && a.text?.contains("this card's other abilities") == true) withOtherAbilities(a, abilities.filter { it !== a }) else a }
        // eoe chunk 01: a station card's P/T box (CR 721.2b)
        return withStationPowerToughness(face.copy(abilities = abilities), card.text)
    }

    /** spm: [ability] with the empty "gains abilities" its "this card's other abilities" was read as filled with [others]. */
    private fun withOtherAbilities(ability: Ability.Activated, others: List<Ability>): Ability.Activated =
        ability.copy(instructions = ability.instructions.copy(steps = ability.instructions.steps.map { step ->
            val apply = (step as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.ApplyMechanics ?: return@map step
            step.copy(effect = apply.copy(mechanics = apply.mechanics.map { m ->
                if (m is arcanum.api.Mechanic.GainAbilities && m.abilities.isEmpty()) arcanum.api.Mechanic.GainAbilities(others) else m }))
        }))

    fun parse(name: String, text: String): FaceDefinition = FaceDefinition(name = name, abilities = abilities(text, setOf(name)))

    /**
     * chunk 56: a whole card: each printed face (a split card's halves, a double-faced card's faces, an adventurer card's
     * creature and Adventure, CR 709, 712, 715) parsed on its own with [parse], in printed order, under [layout].
     */
    fun parseCard(layout: arcanum.api.Layout, faces: List<OracleCard>): arcanum.api.CardDefinition =
        arcanum.api.CardDefinition(layout, faces.map(::parse))

    /**
     * The [arcanum.api.Layout] of a card printed with [faces] (in printed order) under the card-data layout name [name]
     * (Scryfall's and MTGJSON's: "split", "transform", "modal_dfc", ...), for [parseCard]; null where the parser has no
     * layout for it. One face is always [arcanum.api.Layout.Normal].
     */
    fun layout(name: String, faces: List<OracleCard>): arcanum.api.Layout? = if (faces.size == 1) arcanum.api.Layout.Normal else when (name) {
        // An aftermath card is a split card whose second half has aftermath (CR 702.127).
        "split", "aftermath" -> arcanum.api.Layout.Split
        "flip" -> arcanum.api.Layout.Flip
        "transform" -> arcanum.api.Layout.Transform
        "modal_dfc" -> arcanum.api.Layout.ModalDoubleFaced
        // set check (TDM): card data names an Omen card's layout "adventure"; its inset spell is an Omen (CR 720)
        "adventure" -> if (faces.getOrNull(1)?.let { TypeLine.parse(it.typeLine)?.subtypes?.contains(omen) } == true) arcanum.api.Layout.Omen
            else arcanum.api.Layout.Adventure
        "omen" -> arcanum.api.Layout.Omen
        // set check (SOS, FRA): a preparation card (CR 722)
        "prepare" -> arcanum.api.Layout.Prepare
        // set check (FIN): a meld card's front and the back face it shares with its partner (CR 712.4)
        "meld" -> meldPartner(faces[0].text)?.let { arcanum.api.Layout.Meld(it) }
        else -> null
    }

    private val omen = arcanum.api.Subtype("Omen")

    /** The other card of a meld pair, from its front face's text: "(Melds with X.)" or the meld ability's "a creature
     * named X, … meld them into" (CR 701.42a). */
    private fun meldPartner(text: String): String? =
        (MELDS_WITH.find(text) ?: NAMED_PARTNER.find(text)?.takeIf { "meld them into" in text })?.groupValues?.get(1)

    private val MELDS_WITH = Regex("""\(Melds with (.+?)\.\)""")
    private val NAMED_PARTNER = Regex("""and (?:a|an) [a-z ]*?named (.+?), (?:you may|exile them)""")

    /** What the card calls itself: its name, a legendary card's short name ("Thalia"), "this [type]", "this [subtype]"
     * ("this Equipment", "this Vehicle"). */
    private fun selfNames(name: String, face: FaceDefinition): Set<String> =
        (setOf(name, name.substringBefore(',')) + face.types.map { "this " + it.toString().lowercase() } +
            listOf("this card", "this permanent", "this spell") +
            // chunk 26: a legendary card's first name ("Kaito" of "Kaito Shizuki", CR 201.5c)
            listOfNotNull(name.substringBefore(' ').takeIf { arcanum.api.Supertype.Legendary in face.supertypes && ',' !in name && it != "The" }) +
            // set check (LTR): a legendary card's name before " of " ("Rosie Cotton" of "Rosie Cotton of South Lane", CR 201.5c)
            listOfNotNull(name.substringBefore(" of ").takeIf { arcanum.api.Supertype.Legendary in face.supertypes && ',' !in name && it != name }) +
            // chunk 20
            face.subtypes.map { "this " + it.name }).let { names ->
            // msh: dotless aliases ("MODOK" for "M.O.D.O.K.", "Ms Marvel"), which [dotless] writes the lines with
            names + names.filter { it.contains('.') }.map { it.replace(".", "") } }

    /** msh: [line] with each of [self]'s names that has a full stop written as its dotless alias (also a name of [self]). */
    private fun dotless(line: String, self: Set<String>): String =
        self.filter { it.contains('.') }.sortedByDescending { it.length }.fold(line) { l, name -> l.replace(name, name.replace(".", "")) }

    /** Lines that are only reminder text ("({W/P} can be paid with either {W} or 2 life.)") give no ability. */
    private fun abilities(text: String, self: Set<String>, isSpell: Boolean = false): List<Ability> {
        // chunks 21, 26: abilities that exile are linked to "cards exiled with [this]" (CR 607.2a)
        // dsk chunk 09: "each card exiled with it"
        val sentences = Sentences(self, isSpell, linkedExile = self.any { text.contains("exiled with $it") } || text.contains("exiled with it "))
        // chunk 33: "the exiled card" in a later ability refers to the cards an earlier ability exiled (linked
        // abilities, CR 607.2a): hideaway's card, or the cards an exile instruction keeps as card memory.
        // dsk chunk 11: "the discarded card" is the card discarded to pay the additional cost
        sentences.discardedCard = text.contains("the discarded card")
        // tla chunk 02: an Aura's spell targets through its enchant slot (CR 303.4a)
        sentences.enchantSlot = text.lines().any { it.startsWith("Enchant ") }
        // fin chunk 05: "When the chosen creature leaves the battlefield" links to the creature an earlier ability chose (CR 607.2a)
        sentences.finChosenCreatureLinked = text.contains("When the chosen creature leaves the battlefield")
        sentences.exiledCard = when {
            text.lines().any { it.trim().startsWith("Hideaway ") } -> arcanum.api.Objects.LinkedTo(Keyword.Hideaway.Kind)
            text.contains("the exiled card", ignoreCase = true) -> arcanum.api.Objects.Remembered(EXILED_WITH)
            // dsk chunk 09: an exile effect of a card that refers to the cards "exiled with it" keeps them
            text.contains("exiled with it ") -> arcanum.api.Objects.Remembered(EXILED_WITH)
            else -> null
        }
        val result = mutableListOf<Ability>()
        val raws = mutableListOf<String>()
        val lines = text.split('\n').filter { REMINDER.replace(it, "").isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val raw = lines[i++]
            // msh: a name with full stops ("M.O.D.O.K.", "Ms. Marvel") is read as its dotless alias, so sentences split right
            val line = dotless(REMINDER.replace(raw, "").trim(), self)
            // blb chunk 05: a class level bar "[cost]: Level N" with the abilities of its section, up to the next bar (CR 716.2a).
            // A section with an unread line stays printed text as a whole.
            val classLevel = CLASS_LEVEL.matchEntire(line)
            if (classLevel != null) {
                val section = mutableListOf<String>()
                while (i < lines.size && !CLASS_LEVEL.matches(REMINDER.replace(lines[i], "").trim())) section += lines[i++].trim()
                val printed = (listOf(raw.trim()) + section).joinToString("\n")
                result += classLevel(classLevel, section, sentences) ?: Ability.Unparsed(printed)
                raws += printed
                continue
            }
            // CR 702.172a, 702.183a: spree's and tiered's "+ [cost] — [effect]" lines: modes whose costs are additional costs of the spell
            // when chosen ([arcanum.api.Mode.extraCost]); spree chooses one or more, tiered exactly one
            if (isSpell && COST_MODE.matches(line)) {
                val modeLines = mutableListOf(line)
                while (i < lines.size && COST_MODE.matches(REMINDER.replace(lines[i], "").trim())) modeLines += REMINDER.replace(lines[i++], "").trim()
                val tiered = result.any { it == Keyword.Tiered }
                val options = modeLines.map { ml ->
                    val m = COST_MODE.matchEntire(ml)!!
                    val cost = ManaCost.parse(m.groupValues[1])
                    val instructions = sentences.instructions(m.groupValues[2].replaceFirstChar { it.uppercase() })
                    if (cost == null || instructions == null) null else arcanum.api.Mode(instructions, extraCost = Cost.mana(cost), text = ml)
                }
                val printed = modeLines.joinToString("\n")
                result += if (options.all { it != null } && (tiered || result.any { it == Keyword.Spree })) Ability.Spell(arcanum.api.Instructions(
                    modes = arcanum.api.Modes(options.filterNotNull(), min = 1, max = if (tiered) 1 else options.size, chooser = arcanum.api.Players.You)))
                    else Ability.Unparsed(printed)
                raws += printed
                continue
            }
            // chunk 55: a leveler band (CR 711.2a, b): "LEVEL N1-N2" or "LEVEL N3+", its P/T box and the abilities printed
            // with it, up to the next band. A band with an unread line stays printed text as a whole.
            if (LEVEL_BAND.matches(line)) {
                val band = mutableListOf(raw.trim())
                while (i < lines.size && !LEVEL_BAND.matches(REMINDER.replace(lines[i], "").trim())) band += lines[i++].trim()
                val printed = band.joinToString("\n")
                result += levelBand(line, band.drop(1), sentences) ?: Ability.Unparsed(printed)
                raws += printed
                continue
            }
            // eoe chunk 01: a station symbol and the abilities of its striation, "N+ | [abilities]" (CR 721.2a): "As long as this permanent
            // has N or more charge counters on it, it has [abilities]." Its P/T box is added by [parse]. An unread line stays printed text.
            STATION_SYMBOL.matchEntire(line)?.let { m ->
                result += stationSymbol(m.groupValues[1].toInt(), m.groupValues[2], sentences) ?: Ability.Unparsed(raw.trim())
                raws += raw.trim()
                continue
            }
            // blb chunk 06: "Choose up to five {P} worth of modes. You may choose the same mode more than once." and its
            // "{P} — " modes: any modes whose pawprints add up to at most five (CR 700.2i)
            val pawprints = PAWPRINT_CHOOSE.matchEntire(line)
            if (pawprints != null && isSpell) {
                val modeLines = mutableListOf<String>()
                while (i < lines.size && PAWPRINT_MODE.matches(REMINDER.replace(lines[i], "").trim())) modeLines += REMINDER.replace(lines[i++], "").trim()
                val printed = (listOf(raw.trim()) + modeLines).joinToString("\n")
                val limit = Numbers.count(pawprints.groupValues[1])
                val options = modeLines.map { modeLine ->
                    val m = PAWPRINT_MODE.matchEntire(modeLine)!!
                    sentences.instructions(m.groupValues[2])?.let { arcanum.api.Mode(it, text = modeLine, pawprints = m.groupValues[1].length / 3) }
                }
                result += if (limit != null && options.isNotEmpty() && options.all { it != null }) Ability.Spell(arcanum.api.Instructions(modes = arcanum.api.Modes(
                    options.filterNotNull(), min = 1, max = limit, repeatable = true, pawprintLimit = limit, chooser = arcanum.api.Players.You)))
                    else Ability.Unparsed(printed)
                raws += printed
                continue
            }
            // sos chunk 12: "Choose up to four. You may choose the same mode more than once." and its "• " modes (CR 700.2, 700.2d)
            if (isSpell && i < lines.size && lines[i].startsWith("• ")) Sos.CHOOSE_UP_TO_SAME_MODE.matchEntire(line)?.let { m ->
                val modeLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].startsWith("• ")) modeLines += REMINDER.replace(lines[j++].removePrefix("• "), "").trim()
                val n = Numbers.count(m.groupValues[1])
                val options = modeLines.map { modeLine -> sentences.instructions(modeLine)?.let { arcanum.api.Mode(it, text = modeLine) } }
                if (n != null && options.all { it != null }) {
                    i = j
                    result += Ability.Spell(arcanum.api.Instructions(modes = arcanum.api.Modes(options.filterNotNull(), min = 0, max = n, repeatable = true,
                        chooser = arcanum.api.Players.You)))
                    raws += (listOf(raw) + modeLines.map { "• $it" }).joinToString("\n")
                    continue
                }
            }
            // msh chunk 08: "Choose up to two. Return those cards from your graveyard to your hand." and its "• Target …" modes
            if (isSpell && i < lines.size && lines[i].startsWith("• ") && line.startsWith("Choose up to ")) {
                val modeLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].startsWith("• ")) modeLines += REMINDER.replace(lines[j++].removePrefix("• "), "").trim()
                sentences.mshModes(line, modeLines)?.let { spell ->
                    i = j
                    result += spell
                    raws += (listOf(raw) + modeLines.map { "• $it" }).joinToString("\n")
                    continue
                }
            }
            // dsk chunk 07: "Delirium — Choose one. If …, choose one or more instead." and its "• " modes (CR 700.2)
            if (isSpell && i < lines.size && lines[i].startsWith("• ") && line.contains(" instead.")) {
                val modeLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].startsWith("• ")) modeLines += REMINDER.replace(lines[j++].removePrefix("• "), "").trim()
                sentences.modesWithMaxIf(line, modeLines)?.let { spell ->
                    i = j
                    result += spell
                    raws += (listOf(raw) + modeLines.map { "• $it" }).joinToString("\n")
                    continue
                }
            }
            // fin: "Tiered" and its "• [name] — [cost] — [effect]" modes, perhaps after a paragraph the modes complete: choose one, its cost an
            // additional cost of the spell (CR 702.183a, 700.2)
            if (isSpell && line == "Tiered" && i < lines.size) {
                var j = i
                val body = if (!lines[j].startsWith("• ")) REMINDER.replace(lines[j++], "").trim() else null
                val modeLines = mutableListOf<String>()
                while (j < lines.size && lines[j].startsWith("• ")) modeLines += REMINDER.replace(lines[j++].removePrefix("• "), "").trim()
                sentences.finTiered(body, modeLines)?.let { spell ->
                    i = j
                    result += Keyword.Tiered
                    raws += raw.trim()
                    result += spell
                    raws += (listOfNotNull(body) + modeLines.map { "• $it" }).joinToString("\n")
                    continue
                }
            }
            // tdm chunk 02: "As this enchantment enters, choose Abzan or Mardu." and its "• Abzan — [ability]" lines (CR 614.12, 607.2a)
            if (!isSpell && i < lines.size && lines[i].startsWith("• ") && Tdm.AS_ENTERS_CHOOSE_A_OR_B.matches(line)) {
                val bullets = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].startsWith("• ")) bullets += REMINDER.replace(lines[j++], "").trim()
                sentences.tdmChosenOption(line, bullets)?.let { abilities ->
                    i = j
                    result += abilities
                    raws += raw.trim()
                    bullets.forEach { raws += it }
                    continue
                }
            }
            // "Choose one —" and its "• " modes (CR 700.2)
            val choose = CHOOSE.matchEntire(line)
            if (choose != null && isSpell) {
                val modeLines = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("• ")) modeLines += REMINDER.replace(lines[i++].removePrefix("• "), "").trim()
                val modes = sentences.modes(choose, modeLines)
                result += if (modes != null) Ability.Spell(arcanum.api.Instructions(modes = modes))
                    else Ability.Unparsed((listOf(raw) + modeLines.map { "• $it" }).joinToString("\n"))
                raws += (listOf(raw) + modeLines.map { "• $it" }).joinToString("\n")
                continue
            }
            // dsk chunk 12: an activated ability with modes ("[cost]: Choose one —", CR 700.2)
            val activatedChoose = if (!isSpell && line.contains(": ")) CHOOSE.matchEntire(line.substringAfter(": ")) else null
            if (activatedChoose != null && i < lines.size && lines[i].startsWith("• ")) {
                val modeLines = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("• ")) modeLines += REMINDER.replace(lines[i++].removePrefix("• "), "").trim()
                val printed = (listOf(raw.trim()) + modeLines.map { "• $it" }).joinToString("\n")
                result += sentences.modalActivated(line.substringBefore(": "), activatedChoose, modeLines) ?: Ability.Unparsed(printed)
                raws += printed
                continue
            }
            // chunk 21: a triggered ability with modes ("Whenever …, choose one that hasn't been chosen this turn —", CR 700.2)
            // tmt chunk 02: "…, choose one that hasn't been chosen this turn." with a period before the modes
            if (!isSpell && (line.endsWith(" —") || line.endsWith(" hasn't been chosen this turn.") || line.endsWith(", choose both instead.") /* mh2 chunk 12 */ ||
                    Ltr.CHOOSE_ONE_AND_GETS.matches(line) /* ltr chunk 09 */) &&
                i < lines.size && lines[i].startsWith("• ")) {
                val modeLines = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("• ")) modeLines += REMINDER.replace(lines[i++].removePrefix("• "), "").trim()
                val printed = (listOf(raw.trim()) + modeLines.map { "• $it" }).joinToString("\n")
                result += sentences.modalTrigger(line, modeLines) ?: Ability.Unparsed(printed)
                raws += printed
                continue
            }
            val previous = result.lastOrNull()
            // dsk chunk 07: "If …, instead …" replaces the whole spell
            val instead = if (previous is Ability.Spell) sentences.insteadOf(line, previous) ?: sentences.insteadAll(line, previous) else null
            var parsed = if (instead == null) keywordLine(line) ?: flavorKeywordLine(line) ?: sentences.multi(line) ?: sentences.line(line)?.let(::listOf) ?: keywordThenSentence(line, sentences)
                ?: sentences.finMulti(line) /* fin */ else null
            // msh: a legendary creature's "he"/"she"/"him"/"her" read as "it" when the line can't be read as written
            if (instead == null && parsed == null) ungendered(line, self).takeIf { it != line }?.let { u ->
                parsed = sentences.multi(u) ?: sentences.line(u)?.let(::listOf)
            }
            // dsk chunk 07: a later paragraph of a spell that refers to its earlier targets is read with them (CR 113.3a)
            if (instead == null && parsed == null && isSpell && previous is Ability.Spell) {
                sentences.continuation(line, previous.instructions)?.let { joined ->
                    result[result.size - 1] = previous.copy(instructions = joined)
                    raws[raws.size - 1] += "\n" + raw.trim()
                    continue
                }
            }
            when {
                instead != null -> { result[result.size - 1] = instead; raws[raws.size - 1] += "\n" + raw.trim() }
                // An instant's or sorcery's text is one spell ability (CR 113.3a): join its lines.
                previous is Ability.Spell && parsed?.singleOrNull() is Ability.Spell && (parsed.single() as Ability.Spell).abilityWord == null ->
                {
                    result[result.size - 1] = previous.copy(instructions = previous.instructions.join((parsed.single() as Ability.Spell).instructions))
                    raws[raws.size - 1] += "\n" + raw.trim()
                }
                else -> {
                    val added = parsed ?: listOf(Ability.Unparsed(raw.trim()))
                    result += added
                    added.forEach { _ -> raws += raw.trim() }
                }
            }
        }
        // A spell resolves as one object (CR 608.2b): if any of its lines can't be read, none of its spell text or
        // casting costs is automated; they stay as printed for the players.
        if (isSpell && result.any { it is Ability.Unparsed }) {
            return result.mapIndexed { index, ability ->
                if (ability is Ability.Spell || ability.isCastingCost()) Ability.Unparsed(raws[index]) else ability.withText(raws[index])
            }.distinct()
        }
        // tdm chunk 02: "exile … until this creature leaves the battlefield" and a later "the exiled card": the exile keeps the card as card memory
        // (linked abilities, CR 607.2a)
        val linked = if (sentences.exiledCard == arcanum.api.Objects.Remembered(EXILED_WITH)) result.map(::rememberUntilLeaves) else result
        return dftCostTargetYouControl(dftSacrificedCost(finCostTargets(linked))).mapIndexed { index, ability -> ability.withText(raws[index]) }
    }

    /** dft chunk 14: "This spell costs {1} less to cast if it targets a Mount or Vehicle you control": the spell's first target you control. */
    private fun dftCostTargetYouControl(abilities: List<Ability>): List<Ability> {
        val slots = abilities.filterIsInstance<Ability.Spell>().flatMap { it.instructions.targets }
        val yours = arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You)
        val slot = slots.firstOrNull { s -> ((s.what as? arcanum.api.TargetKind.Objects)?.filter as? arcanum.api.ObjectFilter.And)?.all?.contains(yours) == true }
        return abilities.map { ability ->
            val static = ability as? Ability.Static ?: return@map ability
            val condition = static.condition as? arcanum.api.Condition.ObjectMatches ?: return@map ability
            if ((condition.objects as? arcanum.api.Objects.Target)?.slot != "dft: target you control") return@map ability
            static.copy(condition = condition.copy(objects = arcanum.api.Objects.Target(slot?.name ?: return@map ability)))
        }
    }

    /** dft chunk 12: a spell that reads "the sacrificed permanent" names the permanent its additional cost sacrifices (CR 608.2h). */
    private fun dftSacrificedCost(abilities: List<Ability>): List<Ability> {
        val json = kotlinx.serialization.json.Json
        val reads = abilities.filterIsInstance<Ability.Spell>().any {
            json.encodeToString(Ability.serializer(), it).contains("{\"type\":\"bound\",\"name\":\"$SACRIFICED\"}")
        }
        if (!reads) return abilities
        return abilities.map { ability ->
            val static = ability as? Ability.Static ?: return@map ability
            val additional = static.mechanics.singleOrNull() as? arcanum.api.Mechanic.AdditionalCost ?: return@map ability
            val sacrifice = additional.cost.parts.singleOrNull() as? arcanum.api.CostPart.Sacrifice ?: return@map ability
            if (sacrifice.preBind != null) return@map ability
            static.copy(mechanics = listOf(additional.copy(cost = arcanum.api.Cost(listOf(sacrifice.copy(preBind = SACRIFICED))))))
        }
    }

    /** fin chunk 07: "This spell costs {2} less to cast if it targets a tapped creature." read before the spell's "target nonland permanent":
     * the condition names a slot the spell doesn't have; it is the spell's only object target, which must also be that noun (CR 601.2f). */
    private fun finCostTargets(abilities: List<Ability>): List<Ability> {
        val slots = abilities.filterIsInstance<Ability.Spell>().flatMap { it.instructions.targets }
        val only = slots.singleOrNull()?.takeIf { it.what is arcanum.api.TargetKind.Objects } ?: return abilities
        return abilities.map { ability ->
            val static = ability as? Ability.Static ?: return@map ability
            val condition = static.condition as? arcanum.api.Condition.ObjectMatches ?: return@map ability
            val target = condition.objects as? arcanum.api.Objects.Target ?: return@map ability
            if (target.slot == only.name || static.mechanics.none { it is arcanum.api.Mechanic.CostChange }) return@map ability
            val noun = Filters.noun(target.slot) ?: return@map ability
            static.copy(condition = arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Target(only.name), Filters.all(condition.filter, noun)))
        }
    }

    /** tdm: [ability] with each top-level "exile … until … leaves" kept as card memory (when nothing keeps it already). */
    private fun rememberUntilLeaves(ability: Ability): Ability {
        val triggered = ability as? Ability.Triggered ?: return ability
        val steps = triggered.instructions.steps
        if (steps.any { ((it as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.Remember)?.key == EXILED_WITH }) return ability
        val linked = steps.flatMap { step ->
            val move = (step as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.Move
            if (move?.untilLeaves == null || move.to.zone != arcanum.api.Zone.Exile) listOf(step)
            else listOf(arcanum.api.Step.Do(move.copy(postBind = move.postBind ?: "exiled")),
                arcanum.api.Step.Do(arcanum.api.Effect.Remember(EXILED_WITH, arcanum.api.Objects.Bound(move.postBind ?: "exiled"))))
        }
        return if (linked == steps) ability else triggered.copy(instructions = triggered.instructions.copy(steps = linked))
    }

    /** The ability with its printed [raw] lines as its text, reminder text left out (shown to players: "you may"
     * prompts, which of two triggers is which; an emblem's quoted ability, CR 114.2). Keywords have their own; unparsed
     * text stays as printed. Each target slot gets the place of its phrase in that text ([TargetSpans]), and each cost
     * part its phrase's, a keyword's cost in its line ([CostSpans]). */
    internal fun Ability.withText(raw: String): Ability {
        val text = raw.lines().map { REMINDER.replace(it, "").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        return CostSpans.placed(TargetSpans.placed(when (this) {
            is Ability.Spell -> copy(text = text)
            is Ability.Activated -> copy(text = text)
            is Ability.Triggered -> copy(text = text)
            is Ability.Static -> copy(text = text)
            is Ability.SpecialAction -> copy(text = text)
            else -> this
        }), text)
    }

    /**
     * chunk 55: one leveler band as the static ability its level symbol stands for (CR 711.2a, b): "As long as this
     * creature has at least N1 level counters on it, but no more than N2, it has base power and toughness [P/T] and has
     * [abilities]." Null when its P/T box or one of its abilities can't be read.
     */
    private fun levelBand(header: String, lines: List<String>, sentences: Sentences): Ability? {
        val m = LEVEL_BAND.matchEntire(header) ?: return null
        val pt = LEVEL_PT.matchEntire(lines.firstOrNull() ?: return null) ?: return null
        val granted = lines.drop(1).map { REMINDER.replace(it, "").trim() }.flatMap { line ->
            keywordLine(line) ?: sentences.multi(line) ?: listOf(sentences.line(line) ?: return null)
        }
        if (granted.any { it is Ability.Unparsed }) return null
        val atLeast = arcanum.api.Condition.ClassLevelAtLeast(m.groupValues[1].toInt())
        val condition = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { most ->
            arcanum.api.Condition.And(listOf(atLeast, arcanum.api.Condition.Not(arcanum.api.Condition.ClassLevelAtLeast(most.toInt() + 1))))
        } ?: atLeast
        val mechanics = listOf<arcanum.api.Mechanic>(arcanum.api.Mechanic.SetPowerToughness(
            arcanum.api.Value.Fixed(pt.groupValues[1].toInt()), arcanum.api.Value.Fixed(pt.groupValues[2].toInt()))) +
            (if (granted.isEmpty()) emptyList() else listOf(arcanum.api.Mechanic.GainAbilities(granted)))
        return Ability.Static(mechanics, condition = condition, affects = Affected.Objects(Objects.Self))
    }

    /** eoe chunk 01: a station symbol's static ability (CR 721.2a) granting the abilities of its striation (the static keeps the printed
     * line as its text); null when one can't be read. */
    private fun stationSymbol(threshold: Int, body: String, sentences: Sentences): Ability? {
        val granted = keywordLine(body) ?: sentences.multi(body) ?: listOf(sentences.line(body) ?: return null)
        if (granted.any { it is Ability.Unparsed }) return null
        return Ability.Static(listOf(arcanum.api.Mechanic.GainAbilities(granted)), affects = Affected.Objects(Objects.Self),
            condition = stationed(threshold))
    }

    /** eoe: "this permanent has N or more charge counters on it" (CR 721.2a). */
    private fun stationed(threshold: Int): arcanum.api.Condition = arcanum.api.Condition.ObjectMatches(Objects.Self,
        arcanum.api.ObjectFilter.HasCounter(arcanum.api.KeywordExpansions.CHARGE, atLeast = threshold))

    /** eoe chunk 01: a station card's P/T box is part of the static ability of its striation (CR 721.2b): "… and is a creature with base
     * power and toughness [P/T] in addition to its other types". The striation is the one the station reminder names ("It's an artifact
     * creature at 12+."), else the highest. */
    private fun withStationPowerToughness(face: FaceDefinition, text: String): FaceDefinition {
        val power = (face.power as? arcanum.api.PrintedNumber.Fixed)?.value ?: return face
        val toughness = (face.toughness as? arcanum.api.PrintedNumber.Fixed)?.value ?: return face
        if (face.abilities.none { it == Keyword.Station }) return face
        val thresholds = face.abilities.mapNotNull { a -> a.stationThreshold() }
        val threshold = STATION_CREATURE_AT.find(text)?.groupValues?.get(1)?.toInt() ?: thresholds.maxOrNull() ?: return face
        return face.copy(abilities = face.abilities.map { a ->
            if (a !is Ability.Static || a.stationThreshold() != threshold) a
            else a.copy(mechanics = listOf(arcanum.api.Mechanic.SetTypes(add = listOf(arcanum.api.CardType.Creature)),
                arcanum.api.Mechanic.SetPowerToughness(arcanum.api.Value.Fixed(power), arcanum.api.Value.Fixed(toughness))) + a.mechanics)
        })
    }

    private fun Ability.stationThreshold(): Int? = ((this as? Ability.Static)?.condition as? arcanum.api.Condition.ObjectMatches)
        ?.takeIf { it.objects == Objects.Self }?.let { (it.filter as? arcanum.api.ObjectFilter.HasCounter) }
        ?.takeIf { it.kind == arcanum.api.KeywordExpansions.CHARGE }?.atLeast

    /** blb chunk 05: a class level bar and its section's abilities (CR 716.2a), each with its printed line; null when the
     * cost or a line can't be read. */
    private fun classLevel(m: MatchResult, section: List<String>, sentences: Sentences): Ability? {
        val cost = sentences.cost(m.groupValues[1]) ?: return null
        val abilities = section.flatMap { raw ->
            val line = REMINDER.replace(raw, "").trim()
            val parsed = keywordLine(line) ?: sentences.multi(line) ?: listOf(sentences.line(line) ?: return null)
            if (parsed.any { it is Ability.Unparsed }) return null
            parsed.map { it.withText(raw) }
        }
        return Keyword.ClassLevel(cost, m.groupValues[2].toInt(), abilities)
    }

    /**
     * A keyword line is a list of keywords separated by ", " (or "; " when a keyword's own text has commas). It is
     * recognised by its first part being a known keyword and by not being a sentence: it has no final period, except
     * after an em dash cost ("Ward—Pay 3 life."). Further parts that aren't known keywords stay unparsed one by one.
     */
    private fun keywordLine(line: String): List<Ability>? {
        if (line.isEmpty() || (line.endsWith(".") && !line.contains('—'))) return null
        // CR 702.124j: "Partner with [name]", a name that may have a comma, alone on its line
        PARTNER_WITH.matchEntire(line)?.let { return listOf(Keyword.PartnerWith(it.groupValues[1])) }
        // fdn chunk 02: "Enchant creature, land, or planeswalker": one list of what it can enchant, not several keywords (CR 702.5a)
        if (line.startsWith("Enchant ")) keyword(line)?.let { return listOf(it) }
        // A keyword with an em-dash cost ("Flashback—{1}{U}, Pay 3 life.") is alone on its line; its commas are the cost's.
        val parts = when {
            line.contains("; ") -> line.split("; ")
            line.contains('—') -> listOf(line)
            else -> line.split(", ")
        }
        // dgm: "protection from white and from black" is two protection abilities (CR 702.16g); mh2 chunk 02: also first on its line
        val split = parts.flatMap { p ->
            PROTECTION_FROM_AND_FROM.matchEntire(p)?.let { m -> listOf("protection from " + m.groupValues[1], "protection from " + m.groupValues[2]) } ?: listOf(p)
        }
        val first = keyword(split.first()) ?: return null
        return listOf(first) + split.drop(1).map { keyword(it) ?: Ability.Unparsed(it) }
    }

    /** fin: "Gae Bolg — Equip {4}": a keyword line after a flavor word (CR 207.2d), which has no rules meaning. */
    private fun flavorKeywordLine(line: String): List<Ability>? {
        val m = FLAVOR_WORD.matchEntire(line)?.takeIf { RulesWords.isAbilityOrFlavorWord(it.groupValues[1]) } ?: return null
        return keywordLine(m.groupValues[2])?.takeIf { parts -> parts.none { it is Ability.Unparsed } }
    }

    /** chunk 33: "Kicker {X}. X can't be 0.": a keyword line, then a sentence about it on the same line. */
    private fun keywordThenSentence(line: String, sentences: Sentences): List<Ability>? {
        val dot = line.indexOf(". ").takeIf { it > 0 } ?: return null
        val keywords = keywordLine(line.substring(0, dot))?.takeIf { parts -> parts.none { it is Ability.Unparsed } } ?: return null
        return keywords + (sentences.line(line.substring(dot + 2)) ?: return null)
    }

    /** One keyword with its values, or null when [part] isn't exactly a known keyword written in its form. */
    fun keyword(part: String): Keyword? {
        // mh2 chunk 02: "Vanishing" without a number (CR 702.63b), read as 0
        if (part == "Vanishing") return Keyword.Vanishing(0)
        // mh2 chunk 11: "Protection from permanents with corruption counters on them" (CR 702.16a)
        Mh2.PROTECTION_FROM_WITH_COUNTERS.matchEntire(part)?.let { m ->
            val noun = Filters.noun(m.groupValues[1].removeSuffix("s")) ?: return null
            return Keyword.Protection(Filters.all(noun, arcanum.api.ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = 1)))
        }
        // mh2 chunk 12: "Echo—Discard a card." (CR 702.30a): a cost that isn't mana
        Mh2.ECHO_COST.matchEntire(part)?.let { m -> return Keyword.Echo(Sentences(setOf("this card")).cost(m.groupValues[1]) ?: return null) }
        // ltr chunk 14: "nonbasic landwalk" (CR 702.14c)
        if (Ltr.NONBASIC_LANDWALK.matches(part))
            return Keyword.Landwalk(Filters.all(arcanum.api.ObjectFilter.Not(arcanum.api.ObjectFilter.Super(arcanum.api.Supertype.Basic)), arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Land)), part)
        // mh2 chunk 13: "artifact landwalk" (CR 702.14c); "Artifact landcycling {2}" (CR 702.29e)
        if (Mh2.ARTIFACT_LANDWALK.matches(part))
            return Keyword.Landwalk(Filters.all(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Artifact), arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Land)), part)
        if (part.startsWith("Artifact landcycling ")) return Keyword.Typecycling(Filters.all(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Artifact),
            arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Land)), "Artifact landcycling", Cost.mana(ManaCost.parse(part.removePrefix("Artifact landcycling ")) ?: return null))
        // mh2 chunk 10: "Reinforce X—{X}{G}{G}" (CR 702.77a)
        if (part.startsWith("Reinforce X—")) return Keyword.ReinforceX(Cost.mana(ManaCost.parse(part.removePrefix("Reinforce X—")) ?: return null))
        // tdm chunk 12: "hexproof from monocolored" (CR 702.11d)
        Tdm.HEXPROOF_FROM.matchEntire(part)?.let { m ->
            return Keyword.HexproofFrom(when (m.groupValues[1]) {
                "monocolored" -> arcanum.api.ObjectFilter.Monocolored
                "multicolored" -> arcanum.api.ObjectFilter.Multicolored
                // fdn chunk 05: "hexproof from black", "hexproof from instants" (a colour or a card type)
                else -> Filters.noun(m.groupValues[1].removeSuffix("s")) ?: return null
            })
        }
        // "Flashback—Sacrifice a Mountain." (CR 702.34a): a cost that isn't only mana.
        // spm: "Mayhem" without a cost (CR 702.187c)
        if (part == "Mayhem") return Keyword.Mayhem(null)
        FLASHBACK_COST.matchEntire(part)?.let { m ->
            return Keyword.Flashback(Sentences(setOf("this card")).cost(m.groupValues[1]) ?: return null)
        }
        // chunk 13: "Escalate—Discard a card." (CR 702.120a): a cost that isn't only mana.
        ESCALATE_COST.matchEntire(part)?.let { m ->
            return Keyword.Escalate(Sentences(setOf("this card")).cost(m.groupValues[1]) ?: return null)
        }
        // "Splice onto Arcane {1}{R}" (CR 702.47a): a quality, then a cost.
        SPLICE.matchEntire(part)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return Keyword.Splice(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[1])), Cost.mana(cost))
        }
        // chunk 45: "Bestow—{R}, Collect evidence 6." (CR 702.103a): a cost that isn't only mana.
        BESTOW_COST.matchEntire(part)?.let { m ->
            return Keyword.Bestow(Sentences(setOf("this card")).cost(m.groupValues[1]) ?: return null)
        }
        // chunk 45: "Companion — Each permanent card in your starting deck has mana value 2 or less." (CR 702.139a)
        COMPANION.matchEntire(part)?.let { m ->
            return Keyword.CompanionAbility(m.groupValues[1], eachCard = companionCards(m.groupValues[1]))
        }
        // blb chunk 02: "Gift a card", "Gift a tapped Fish" (CR 702.174d–i)
        GIFT.matchEntire(part)?.let { m ->
            return Keyword.Gift(when (m.groupValues[1]) {
                "a Food" -> arcanum.api.Gifted.Food
                "a card" -> arcanum.api.Gifted.Card
                "a tapped Fish" -> arcanum.api.Gifted.TappedFish
                "an extra turn" -> arcanum.api.Gifted.ExtraTurn
                "a Treasure" -> arcanum.api.Gifted.Treasure
                "an Octopus" -> arcanum.api.Gifted.Octopus
                else -> return null
            })
        }
        // chunk 25: "Enchant creature or Vehicle", "Enchant player" (CR 702.5a)
        ENCHANT.matchEntire(part)?.let { m ->
            return Keyword.Enchant(when (m.groupValues[1]) {
                "player" -> arcanum.api.TargetKind.Players(arcanum.api.PlayerFilter.Any)
                "opponent" -> arcanum.api.TargetKind.Players(arcanum.api.PlayerFilter.Opponent)
                else -> arcanum.api.TargetKind.Objects(Filters.noun(m.groupValues[1]) ?: return null)
            })
        }
        // chunk 30: "Affinity for artifacts" (CR 702.41a): a quality, kept as printed too.
        AFFINITY.matchEntire(part)?.let { m -> return Keyword.Affinity(Filters.noun(m.groupValues[1]) ?: return null, m.groupValues[1]) }
        // chunk 30: "Evoke—Exile a white card from your hand.", "Escape—{R}{R}, Exile eight other cards from your graveyard."
        // (CR 702.74a, 702.138a): a cost that isn't only mana.
        // eoe chunk 11: "Devour land 3": devour of another quality (CR 702.82c)
        Eoe.DEVOUR_QUALITY.matchEntire(part)?.let { m ->
            return Keyword.Devour(m.groupValues[2].toInt(), what = Filters.noun(m.groupValues[1]) ?: return null)
        }
        DASH_COST.matchEntire(part)?.let { m ->
            val cost = Sentences(setOf("this card")).cost(m.groupValues[2]) ?: return null
            // dsk chunk 06: "Equip—Sacrifice a creature." (CR 702.6a), "Ward—Discard a card." (CR 702.21a)
            return when (m.groupValues[1]) {
                "Evoke" -> Keyword.Evoke(cost)
                "Equip" -> Keyword.Equip(cost)
                "Ward", "ward" -> Keyword.Ward(cost)
                // eoe chunk 09: "Warp—{B}, Pay 2 life." (CR 702.185a)
                "Warp" -> Keyword.Warp(cost)
                else -> Keyword.Escape(cost)
            }
        }
        // hob chunk 06: "Equip Wizard {1}": attaches only to a creature of that subtype (CR 702.6a, 702.6e)
        EQUIP_SUBTYPE.matchEntire(part)?.let { m ->
            if (m.groupValues[1] !in RulesWords.subtypes) return null
            return Keyword.Equip(Cost.mana(ManaCost.parse(m.groupValues[2]) ?: return null), onto = arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[1])))
        }
        // dsk chunk 06: "Plainscycling {2}", "Basic landcycling {1}" (CR 702.29e)
        TYPECYCLING.matchEntire(part)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            val cards = when (val type = m.groupValues[1]) {
                "Basic land" -> Filters.noun("basic land") ?: return null
                else -> if (type in RulesWords.subtypes) arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(type)) else return null
            }
            return Keyword.Typecycling(cards, part.substringBefore(' ' + m.groupValues[2]), Cost.mana(cost))
        }
        // chunk 40: "Forestwalk", "Islandwalk" (CR 702.14a): a land type, then "walk"
        LANDWALK.matchEntire(part)?.let { m ->
            if (m.groupValues[1] !in RulesWords.subtypes) return null
            return Keyword.Landwalk(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[1])), part)
        }
        // chunk 50: "protection from blue", "protection from multicolored" (CR 702.16a)
        PROTECTION.matchEntire(part)?.let { m ->
            return Keyword.Protection(when (val quality = m.groupValues[1]) {
                "multicolored" -> arcanum.api.ObjectFilter.Multicolored
                "monocolored" -> arcanum.api.ObjectFilter.Monocolored
                "everything" -> arcanum.api.ObjectFilter.Any
                else -> Filters.noun(quality) ?: return null
            })
        }
        val lower = part.lowercase()
        val syntax = BY_LENGTH.firstOrNull { k ->
            lower.startsWith(k.lowercase) && (lower.length == k.lowercase.length || lower[k.lowercase.length] in " —")
        } ?: return null
        val rest = part.substring(syntax.text.length)
        return when (syntax) {
            is KeywordSyntax.None -> if (rest.isEmpty()) syntax.keyword else null
            is KeywordSyntax.Amount -> AMOUNT.matchEntire(rest)?.let { syntax.build(it.groupValues[1].toInt()) }
            is KeywordSyntax.Cost -> if (rest.startsWith(" ")) ManaCost.parse(rest.substring(1))?.let(syntax.build) else null
            is KeywordSyntax.AmountAndCost -> AMOUNT_AND_COST.matchEntire(rest)?.let { m ->
                ManaCost.parse(m.groupValues[2])?.let { syntax.build(m.groupValues[1].toInt(), it) }
            }
        }
    }

    /** chunk 45: "Each permanent card in your starting deck has mana value 2 or less": every card of the deck is either not
     * such a card or has that mana value. */
    private fun companionCards(condition: String): arcanum.api.ObjectFilter? {
        val m = EACH_CARD_IN_STARTING_DECK.matchEntire(condition) ?: return null
        val cards = Filters.noun(m.groupValues[1]) ?: return null
        return arcanum.api.ObjectFilter.Or(listOf(arcanum.api.ObjectFilter.Not(cards),
            arcanum.api.ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, arcanum.api.Value.Fixed(m.groupValues[2].toInt()))))
    }

    /** Longest names first, so "Double strike" or "Multikicker" is never read as a shorter name. */
    private val BY_LENGTH = KeywordSyntax.all.sortedByDescending { it.lowercase.length }
    private val LEVEL_BAND = Regex("""LEVEL (\d+)(?:-(\d+)|\+)""")
    /** A spree or tiered mode (CR 702.172a, 702.183a): "+ {1} — Counter target spell." */
    private val COST_MODE = Regex("""\+ ((?:\{[^}]+\})+) — (.+)""")
    private val PARTNER_WITH = Regex("""Partner with (.+)""")
    // woe chunk 08: "Choose one that hasn't been chosen —" (for this object)
    private val CHOOSE = Regex("""Choose (one|two|three|four)( or more| or both)?(?: that hasn't been chosen)?(?: —|\. You may choose the same mode more than once\.)""")
    private val FLASHBACK_COST = Regex("""Flashback—(.+?)\.?""")
    private val SPLICE = Regex("""Splice onto (\w+) (\{.+\})""")
    private val REMINDER = Regex("""\s*\([^)]*\)""")
    private val AMOUNT = Regex(""" (\d+)""")
    private val AMOUNT_AND_COST = Regex(""" (\d+)—(.+)""")
    // chunk 25
    private val ENCHANT = Regex("""Enchant (.+)""")
    // chunk 13
    private val ESCALATE_COST = Regex("""Escalate—(.+?)\.?""")
    // chunk 30
    private val AFFINITY = Regex("""Affinity for (\w+)""")
    private val DASH_COST = Regex("""(Evoke|Escape|Equip|Ward|ward|Warp)—(.+?)\.?""")
    // dsk chunk 06
    private val TYPECYCLING = Regex("""([A-Z][a-z]+(?: land)?)cycling (\{.+\})""")
    // hob chunk 06
    private val EQUIP_SUBTYPE = Regex("""Equip ([A-Z][a-z]+) (\{.+\})""")
    // chunk 40
    private val LANDWALK = Regex("""([A-Z][a-z]+)walk""")
    // chunk 45
    private val BESTOW_COST = Regex("""Bestow—(.+?)\.?""")
    private val COMPANION = Regex("""Companion — (.+)""")
    private val EACH_CARD_IN_STARTING_DECK = Regex("""Each (.+?) in your starting deck has mana value (\d+) or less\.""")
    // chunk 50
    private val PROTECTION = Regex("""[Pp]rotection from ([a-z]+)""")
    // chunk 55
    private val LEVEL_PT = Regex("""(\d+)/(\d+)""")
    // blb chunk 06
    private val PAWPRINT_CHOOSE = Regex("""Choose up to (\w+) \{P\} worth of modes\. You may choose the same mode more than once\.""")
    private val PAWPRINT_MODE = Regex("""((?:\{P\})+) — (.+)""")
    // blb chunk 05
    // eoe chunk 01
    private val STATION_SYMBOL = Regex("""(\d+)\+ \| (.+)""")
    private val STATION_CREATURE_AT = Regex("""It's an artifact creature at (\d+)\+\.""")
    private val CLASS_LEVEL = Regex("""((?:\{[^}]+\})+): Level (\d+)""")
    // fin
    private val FLAVOR_WORD = Regex("""([A-Z0-9][A-Za-z0-9,'!?\- ]+) — (.+)""")
    // blb chunk 02
    private val GIFT =Regex("""Gift (a Food|a card|a tapped Fish|an extra turn|a Treasure|an Octopus)""")
}

/** Writes definitions back as oracle-style English, for checking parses against the original text. */
object OracleRenderer {
    fun render(ability: Ability): String = when (ability) {
        is Ability.Unparsed -> ability.text
        is Keyword.Kicker -> "Kicker " + ability.costs.joinToString(" and/or ") { cost(it) }
        is Keyword -> buildString {
            append(ability.text)
            if (ability is HasAmount) append(' ').append(ability.amount)
            if (ability is HasCost) append(if (ability is HasAmount) "—" else " ").append(cost(ability.cost))
            if (ability is Keyword.Mayhem) ability.cost?.let { append(' ').append(cost(it)) } // spm
        }
        else -> error("Rendering ${ability::class.simpleName} is not written yet")
    }

    /** Mana-only costs as printed; other costs are not rendered yet. */
    private fun cost(cost: Cost): String = cost.parts.joinToString(", ") { part ->
        (part as? CostPart.Mana)?.cost?.toString() ?: error("Rendering cost part $part is not written yet")
    }
}

// dgm
private val PROTECTION_FROM_AND_FROM = Regex("""[Pp]rotection from (\w+) and from (\w+)""")

private fun arcanum.api.Instructions.join(next: arcanum.api.Instructions): arcanum.api.Instructions {
    // rtr chunk 08: a later line's target named as an earlier one is a target of its own (CR 115.3): "second creature"
    var steps2 = next.steps
    val targets2 = next.targets.map { slot ->
        if (targets.none { it.name == slot.name }) slot else {
            val renamed = nextTargetName(slot.name, (targets + next.targets).map { it.name })
            steps2 = steps2.map { renameTargetSlot(it, slot.name, renamed) }
            slot.copy(name = renamed)
        }
    }
    return copy(targets = targets + targets2, steps = steps + steps2, replacements = replacements + next.replacements,
        announced = (announced + next.announced).distinct())
}

private fun Ability.isCastingCost() = this is Ability.Static &&
    mechanics.any { it is arcanum.api.Mechanic.AdditionalCost || it is arcanum.api.Mechanic.AlternativeCost }
