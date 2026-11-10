package arcanum.tools

import arcanum.api.Ability
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every card here is invented. */
class SetPlanTest {
    private fun creature(text: String, name: String = "Ploom Warden", pt: String = "2/2") =
        OracleCard(name, "{1}{G}", "Creature — Elf", text, pt.substringBefore('/'), pt.substringAfter('/'))
    private fun instant(text: String) = OracleCard("Ploom Sting", "{1}{G}", "Instant", text)
    private fun abilities(card: OracleCard): List<Ability> = OracleParser.parse(card).abilities
    private fun shape(text: String) = abilityShape(abilities(instant(text)).single())

    /** Coverage of everything [cards] have, played by scenarios; simple keywords only in play. */
    private fun coveredFor(vararg cards: OracleCard, engine: Boolean = true, parser: Boolean = true): CoveredShapes =
        coveredShapesFrom(cards.flatMap(::abilities).flatMap { a ->
            buildList {
                if (parser) add("parser" to abilityJson(a))
                if (engine) add(if (a is arcanum.api.Keyword) "present" to abilityJson(a) else "exercised" to abilityJson(a))
            }
        })

    private fun verdict(card: OracleCard, covered: CoveredShapes = coveredFor(card), layout: String = "normal") = judge(layout, listOf(card), covered)
    private fun assertTest(card: OracleCard, condition: String, covered: CoveredShapes = coveredFor(card)) {
        val result = verdict(card, covered)
        assertEquals(Verdict.Test, result.verdict, result.reason)
        assertTrue(result.reason.startsWith(condition), result.reason)
    }

    // ---- The shape

    @Test fun numbersMaskToZeroOneOrN() {
        assertEquals(shape("Target creature gets +3/+3 until end of turn."), shape("Target creature gets +5/+2 until end of turn."))
        assertNotEquals(shape("Target creature gets +1/+1 until end of turn."), shape("Target creature gets +3/+3 until end of turn."))
        assertEquals(shape("Draw two cards."), shape("Draw three cards."))
        assertNotEquals(shape("Draw a card."), shape("Draw two cards."))
    }

    @Test fun signIsKept() {
        assertNotEquals(shape("Target creature gets +3/+3 until end of turn."), shape("Target creature gets -3/-3 until end of turn."))
    }

    @Test fun oneTargetDiffersFromTwo() {
        assertNotEquals(shape("Destroy target creature."), shape("Destroy two target creatures."))
    }

    @Test fun xStaysApartFromNumbers() {
        assertNotEquals(shape("Draw two cards."), shape("Draw X cards."))
        fun cost(text: String) = abilityShape(abilities(creature(text)).single())
        assertEquals(cost("{2}{G}: Draw a card."), cost("{5}{U}{U}: Draw a card."))
        assertNotEquals(cost("{2}{G}: Draw a card."), cost("{X}{G}: Draw a card."))
    }

    @Test fun printedTextAndNamesGo() {
        val json = abilityShape(abilities(creature("{T}: Add {G}.")).single())
        assertFalse("text" in json, json)
        val same = abilityShape(abilities(creature("{T}: Add {G}.", name = "Other Name")).single())
        assertEquals(json, same)
        assertEquals(shape("Destroy target creature."), shape("Destroy target creature. (A reminder of no meaning.)"))
    }

    @Test fun whatMeansSomethingStaysExact() {
        assertNotEquals(shape("Destroy target creature."), shape("Destroy target artifact."))
        assertNotEquals(shape("Target creature gets +3/+3 until end of turn."), shape("Target creature gets +3/+3 until your next turn."))
        assertNotEquals(shape("Target player draws two cards."), shape("Draw two cards."))
    }

    @Test fun shapeIdIsStable() {
        val s = shape("Draw a card.")
        assertEquals(shapeId(s), shapeId(shape("Draw a card.")))
        assertEquals(12, shapeId(s).length)
    }

    // ---- covered-shapes.tsv

    @Test fun coverageSurvivesItsFile() {
        val card = creature("Flying\n{T}: Add {G}.")
        val covered = coveredFor(card)
        val file = kotlin.io.path.createTempFile().toFile()
        file.writeText(covered.toTsv())
        val read = CoveredShapes.read(file)
        file.delete()
        val ids = abilities(card).map { shapeId(abilityShape(it)) }
        assertTrue(ids.all { read.parser(it) && read.engine(it) })
    }

    @Test fun aStaticAbilityInPlayIsNotCovered() {
        val anthem = abilities(creature("Creatures you control get +1/+1.")).single()
        val covered = coveredShapesFrom(listOf("present" to abilityJson(anthem)))
        assertFalse(covered.engine(shapeId(abilityShape(anthem))))
    }

    @Test fun aSimpleKeywordInPlayIsCovered() {
        val flying = abilities(creature("Flying")).single()
        val covered = coveredShapesFrom(listOf("present" to abilityJson(flying)))
        assertTrue(covered.engine(shapeId(abilityShape(flying))))
    }

    @Test fun aModalAbilityIsNeverEngineCovered() {
        val modal = abilities(instant("Choose one —\n• Draw a card.\n• Destroy target creature.")).single()
        assertFalse(coveredShapesFrom(listOf("exercised" to abilityJson(modal))).engine(shapeId(abilityShape(modal))))
    }

    // ---- REPEAT

    @Test fun aVanillaCardRepeats() = assertEquals(Verdict.Repeat, verdict(creature("")).verdict)

    @Test fun coveredSimpleKeywordsRepeat() {
        val card = creature("Flying\nVigilance")
        assertEquals(Verdict.Repeat, verdict(card).verdict)
    }

    @Test fun independentCoveredAbilitiesRepeat() {
        val card = creature("When this creature enters, draw a card.\n{1}{G}: Target player gains 2 life.")
        val result = verdict(card)
        assertEquals(Verdict.Repeat, result.verdict, result.reason)
    }

    // ---- Each condition failing to TEST

    @Test fun anUnreadCardIsUnread() {
        val card = creature("Frobnicate the zorble twice.")
        assertEquals(Verdict.Unread, judge("normal", listOf(card), CoveredShapes(emptyMap())).verdict)
    }

    @Test fun b_aCardOfTwoFacesIsTested() {
        val face = instant("Draw a card.")
        val result = judge("adventure", listOf(creature("Flying"), face.copy(typeLine = "Sorcery — Adventure")), coveredFor(creature("Flying"), face))
        assertEquals(Verdict.Test, result.verdict)
        assertTrue(result.reason.startsWith("(b)"), result.reason)
    }

    @Test fun b_anotherLayoutIsTested() {
        val card = creature("Flying")
        val result = verdict(card, layout = "leveler")
        assertEquals(Verdict.Test, result.verdict, result.reason)
        assertTrue(result.reason.startsWith("(b)"), result.reason)
    }

    @Test fun c_noParserTest() = assertTest(creature("{T}: Add {G}."), "(c) no parser test", coveredFor(creature("{T}: Add {G}."), parser = false))

    @Test fun c_noScenario() = assertTest(creature("{T}: Add {G}."), "(c) no scenario", coveredFor(creature("{T}: Add {G}."), engine = false))

    @Test fun c_anotherShapeIsNotCovered() = assertTest(creature("{T}: Add {G}."), "(c)", coveredFor(creature("{T}: Add {R}.")))

    @Test fun d_lineAloneCatchesAParserLink() {
        // The parser writes the spell's text into its overload keyword: alone, the keyword's line differs.
        val card = instant("Destroy target artifact.\nOverload {4}{R}")
        assertNotNull(linesConnected(card, abilities(card)))
        val played = coveredShapesFrom(abilities(card).flatMap { listOf("parser" to abilityJson(it), "exercised" to abilityJson(it)) })
        assertTest(card, "(d)", played)
    }

    @Test fun d_unlinkedLinesAgree() {
        val card = creature("{T}: Add {G}.\n{1}{G}: Draw a card.")
        assertNull(linesConnected(card, abilities(card)))
        assertNotNull(linesConnected(instant("Draw a card."), abilities(instant("Draw two cards."))))
    }

    @Test fun e_aRememberedCardLinksAbilities() {
        assertEquals("(e) its text links abilities: \"exiled with\"", linkedAcrossAbilities(emptyList(), listOf(creature("Target creature exiled with this creature gets +1/+1."))))
    }

    @Test fun e_aModelLinkIsFound() {
        val card = creature("As this creature enters, choose a creature type.\nCreatures of the chosen type get +1/+1.")
        val result = linkedAcrossAbilities(abilities(card), listOf(creature("")))
        assertTrue(result != null && result.startsWith("(e)"), result)
    }

    @Test fun e_theChosenInTextIsFound() {
        assertNotNull(linkedAcrossAbilities(emptyList(), listOf(creature("Creatures of the chosen type get +1/+1."))))
    }

    @Test fun f_countersOnItselfAreShared() {
        val card = creature("When this creature enters, put a +1/+1 counter on it.\nWhenever this creature deals combat damage to a player, draw a card.")
        assertTest(card, "(f)")
    }

    @Test fun f_itsTappedStateIsShared() {
        val card = creature("{T}: Add {G}.\n{T}: Target player gains 2 life.")
        assertTest(card, "(f)")
    }

    @Test fun f_itsZoneIsShared() {
        val card = creature("{1}, Sacrifice this creature: Draw a card.\nWhen this creature dies, target player gains 2 life.")
        assertTest(card, "(f)")
    }

    @Test fun f_anEffectThatCouldTouchItselfCounts() {
        val card = creature("{T}: Target creature gets +2/+2 until end of turn.\nWhenever this creature deals combat damage to a player, draw a card.")
        assertTest(card, "(f)")
    }

    @Test fun f_oneAbilityHasNoneToShareWith() = assertNull(ownStateShared(abilities(creature("{T}: Add {G}."))))

    @Test fun f_aConditionOnItselfIsShared() {
        // A condition on the card reads all its state.
        val parsed = abilities(creature("{2}: Target player gains 2 life. Activate only if this creature is tapped.\n{T}: Add {G}."))
        assertFalse(parsed.any { it is Ability.Unparsed })
        assertNotNull(ownStateShared(parsed))
    }

    @Test fun g_aKeywordThatDependsOnTheRestIsTested() {
        val card = creature("Kicker {2}")
        assertNotNull(dependentKeyword(abilities(card)))
        val result = verdict(creature("Kicker {2}\nWhen this creature enters, if it was kicked, draw a card."),
            coveredShapesFrom(abilities(creature("Kicker {2}\nWhen this creature enters, if it was kicked, draw a card.")).flatMap { listOf("parser" to abilityJson(it), "exercised" to abilityJson(it)) }))
        assertEquals(Verdict.Test, result.verdict)
        assertTrue("(g)" in result.reason, result.reason)
    }

    @Test fun g_hasteMeetsATapAbility() {
        val card = creature("Haste\n{T}: Add {G}.")
        assertTest(card, "(g)")
    }

    // ---- Explain

    @Test fun explainFindsTheFirstUnreadClause() {
        val explained = explain("When this creature enters, draw a card, then frobnicate the zorble.")
        assertEquals("frobnicate the zorble", explained.firstUnread?.text)
        assertEquals(ClauseKind.Effect, explained.firstUnread?.kind)
    }

    @Test fun explainSaysWhenAllReads() = assertNull(explain("Flying\n{T}: Add {G}.").firstUnread)

    @Test fun explainNamesAnUnreadTriggerAndCost() {
        assertEquals(ClauseKind.Trigger, explain("Whenever you frobnicate the zorble, draw a card.").firstUnread?.kind)
        assertEquals(ClauseKind.Cost, explain("Frobnicate the zorble: Draw a card.").firstUnread?.kind)
    }

    @Test fun explainNeverPassesThirtyLines() {
        val index = RuleIndex(listOf(ParserRule("Rules.kt", 1, "frobnicate", setOf("frobnicate"))))
        val text = (1..40).joinToString("\n") { "Whenever you frobnicate $it, draw a card, then zorble $it." }
        assertTrue(renderExplanation(explain(text), index).size <= 30)
    }

    @Test fun nearestRuleSharesTheRarerWord() {
        val index = RuleIndex(listOf(
            ParserRule("A.kt", 1, "target creature", wordsOf("target creature")),
            ParserRule("B.kt", 2, "target land", wordsOf("target land")),
            ParserRule("C.kt", 3, "frobnicate target creature", wordsOf("frobnicate target creature")),
        ))
        val near = index.nearest("frobnicate target creature")
        assertEquals("C.kt", near.first().rule.file)
        assertEquals(1.0, near.first().score, 0.001)
    }

    // ---- Chunks

    private fun planned(n: Int, text: String, prefix: String = "Card"): List<PlannedCard> = (1..n).map { i ->
        planCard("$prefix $i", "normal", listOf(creature(text, name = "$prefix $i")), emptyList(), CoveredShapes(emptyMap()))
    }

    @Test fun chunksStayWithinSizeAndCoverEveryCard() {
        val cards = planned(60, "Draw a card.", "Draw") + planned(4, "Frobnicate the zorble.", "Unread") + planned(4, "Whenever you frobnicate, draw a card.", "Other") + planned(3, "{T}: Add {G}.", "Mana")
        val chunks = chunksOf(cards, emptySet())
        assertTrue(chunks.all { it.cards.size <= 25 }, chunks.map { it.cards.size }.toString())
        assertEquals(cards.filter { it.needsWork }.map { it.name }.toSet(), chunks.flatMap { it.cards }.map { it.name }.toSet())
        assertEquals(chunks.sumOf { it.cards.size }, chunks.flatMap { it.cards }.map { it.name }.distinct().size)
    }

    @Test fun chunkIsMechanicalOnlyWithoutUnreadCards() {
        val tests = planned(12, "{T}: Add {G}.", "Mana")
        val unread = planned(12, "Frobnicate the zorble.", "Unread")
        val chunks = chunksOf(tests + unread, emptySet())
        assertTrue(chunks.first().cards.any { it.unread }, "unread chunks come first")
        assertFalse(chunks.first().mechanical)
        assertTrue(chunks.last().mechanical)
    }

    @Test fun aMechanicKeepsItsCardsTogether() {
        val cards = (1..11).map { i -> planCard("Mech $i", "normal", listOf(creature("Draw a card.", name = "Mech $i")), listOf("Zorble"), CoveredShapes(emptyMap())) }
        assertEquals(1, chunksOf(cards, setOf("Zorble")).size)
    }

    // ---- A set with no cards to plan

    private fun record(uuid: String, vararg flags: String) = com.google.gson.JsonObject().apply {
        addProperty("name", "Ploom Card $uuid")
        addProperty("uuid", uuid)
        flags.forEach { addProperty(it, true) }
    }

    @Test fun aSetWithCardsIsPlanned() = assertNull(noCardsReason("PLM", listOf(record("1"), record("2", "isOnlineOnly"))))

    @Test fun anOnlineOnlySetSaysWhyItHasNoCards() {
        val reason = assertNotNull(noCardsReason("PLM", listOf(record("1", "isOnlineOnly"), record("2", "isOnlineOnly"))))
        assertTrue("2 online-only" in reason && "paper" in reason, reason)
    }

    @Test fun aSetWithoutCardRecordsSaysSo() {
        val reason = assertNotNull(noCardsReason("PLM", emptyList()))
        assertTrue("no cards in the pinned MTGJSON file" in reason, reason)
    }

    // ---- Seeded audit

    private val mutable = listOf(
        ModelMutation.SwapPlusMinus to instant("Put a +1/+1 counter on target creature."),
        ModelMutation.SwapPlusMinus to instant("Target creature gets +2/+2 until end of turn."),
        ModelMutation.TargetCount to instant("Destroy target creature."),
        ModelMutation.WhoIsAffected to instant("Creatures you control get +1/+1 until end of turn."),
        ModelMutation.FilterType to instant("Destroy target artifact."),
        ModelMutation.DurationChanged to instant("Target creature gets +2/+2 until end of turn."),
        ModelMutation.ConditionDropped to instant("If you control a creature, draw a card."),
        ModelMutation.ConditionDropped to creature("When this creature enters, if you control an artifact, draw a card."),
    )

    @Test fun eachMutationReallyChangesTheModelAndItsRendering() {
        for ((mutation, card) in mutable) {
            val json = Json.encodeToJsonElement(arcanum.api.CardDefinition.serializer(), parsedCard("normal", listOf(card))!!)
            val changed = assertNotNull(mutateModel(json, mutation), "${mutation.code} on ${card.text}")
            assertNotEquals(json, changed, mutation.code)
            assertNotEquals(renderDefinition(json), renderDefinition(changed), "${mutation.code} on ${card.text}")
        }
    }

    @Test fun aMutationWithNoPlaceGivesNothing() {
        val json = Json.encodeToJsonElement(arcanum.api.CardDefinition.serializer(), parsedCard("normal", listOf(instant("Draw a card.")))!!)
        assertNull(mutateModel(json, ModelMutation.DurationChanged))
    }

    private fun auditCards(n: Int) = (1..n).map { i ->
        val face = creature("When this creature enters, put a +1/+1 counter on target creature you control.", name = "Audit $i")
        AuditCard(face.name, face.text, parsedCard("normal", listOf(face))!!)
    }

    @Test fun seedingIsDeterministic() {
        fun flat(plan: AuditPlan) = plan.batches.flatten().map { Triple(it.id, it.card, it.model) } to plan.seeded.map { Triple(it.entry, it.card, it.mutation) }
        assertEquals(flat(auditPlan(auditCards(100))), flat(auditPlan(auditCards(100))))
        assertEquals(flat(auditPlan(auditCards(100))), flat(auditPlan(auditCards(100).reversed())))
    }

    @Test fun aboutEightPercentAreWrongCopiesApartFromTheirOriginals() {
        val plan = auditPlan(auditCards(200))
        val entries = plan.batches.flatten()
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertEquals(entries.size - 200, plan.seeded.size)
        val share = plan.seeded.size.toDouble() / entries.size
        assertTrue(share in 0.06..0.10, "$share")
        assertTrue(plan.batches.all { it.size <= 25 })
        val byId = entries.associateBy { it.id }
        for (seeded in plan.seeded) {
            val original = entries.single { it.card == seeded.card && it.id !in plan.seeded.map { s -> s.entry } }
            assertNotEquals(original.model, byId.getValue(seeded.entry).model)
            val batch = plan.batches.single { b -> b.any { it.id == seeded.entry } }
            assertFalse(batch.any { it.id == original.id }, "${seeded.card} sits in one batch with its original")
        }
    }

    @Test fun anAuditEntryCarriesNoPrintedText() {
        val plan = auditPlan(auditCards(3))
        assertFalse(plan.batches.flatten().any { "When this creature" in it.model })
    }
}
