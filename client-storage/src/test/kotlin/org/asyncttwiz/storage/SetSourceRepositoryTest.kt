package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.asyncttwiz.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.GZIPOutputStream

class SetSourceRepositoryTest {
    @TempDir lateinit var temporary: Path
    private val metadata = CatalogMetadata("2026-10-26", "5.3.0")
    private val today = LocalDate.parse("2026-10-26")
    private val clock = MutableClock(today.atStartOfDay().toInstant(ZoneOffset.UTC))
    private val cache get() = temporary.resolve("sets").toFile()

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        fun tomorrow() { now = now.plusSeconds(24 * 60 * 60) }
    }

    private class FixtureSource : CatalogSource {
        val files = mutableMapOf<String, ByteArray>()
        val downloads = mutableListOf<String>()
        val requests = mutableListOf<String>()
        var unknownSize = false
        var badChecksum = false
        var listed: CatalogSetList? = null
        var listRequests = 0
        override fun setList(): CatalogSetList { listRequests++; return checkNotNull(listed) }
        fun put(set: JsonObject) {
            val json = JsonObject().apply { add("data", set) }.toString()
            files[set["code"].asString] = ByteArrayOutputStream().apply {
                GZIPOutputStream(this).use { it.write(json.toByteArray()) }
            }.toByteArray()
        }
        override fun metadata(): CatalogMetadata = error("No catalog download needed")
        override fun size(): Long? = error("Select a set")
        override fun checksum(): String = error("Select a set")
        override fun download(output: OutputStream, progress: (Long) -> Unit): Unit = error("Select a set")
        override fun setSource(code: String): CatalogSource {
            requests += code
            return object : CatalogSource {
                override fun metadata(): CatalogMetadata = error("No metadata needed")
                override fun size(): Long? = if (unknownSize) null else files.getValue(code).size.toLong()
                override fun checksum(): String = if (badChecksum) "0".repeat(64) else
                    MessageDigest.getInstance("SHA-256").digest(files.getValue(code)).joinToString("") { "%02x".format(it) }
                override fun download(output: OutputStream, progress: (Long) -> Unit) {
                    downloads += code
                    output.write(files.getValue(code))
                    // Limits must apply to actual writes, not just a provider's progress callback.
                    progress(0)
                }
            }
        }
    }

    private fun uuid(id: Int) = UUID.nameUUIDFromBytes("set-card-$id".toByteArray()).toString()
    private fun card(id: Int, rarity: String = "common", color: String = "W") = JsonObject().apply {
        addProperty("uuid", uuid(id)); addProperty("name", "Card $id"); addProperty("number", "$id")
        addProperty("language", "English"); addProperty("layout", "normal"); addProperty("type", "Creature")
        addProperty("rarity", rarity)
        add("colors", JsonArray().apply { if (color.isNotEmpty()) add(color) })
    }
    private fun set(code: String = "OLD", date: String = "2000-01-01", cards: List<JsonObject> = listOf(card(1))) = JsonObject().apply {
        addProperty("code", code); addProperty("name", "Set $code"); addProperty("releaseDate", date)
        add("cards", JsonArray().apply { cards.forEach { add(it) } })
    }
    private fun recipe(ids: List<Int>, sources: List<String> = emptyList(), count: Int = 1) = JsonObject().apply {
        add("boosters", JsonArray().apply { add(JsonObject().apply {
            addProperty("weight", 1); add("contents", JsonObject().apply { addProperty("main", count) })
        }) })
        addProperty("boostersTotalWeight", 1)
        add("sheets", JsonObject().apply { add("main", JsonObject().apply {
            add("cards", JsonObject().apply { ids.forEachIndexed { i, id -> addProperty(uuid(id), i + 1) } })
            addProperty("totalWeight", ids.indices.sumOf { it + 1 })
            addProperty("foil", true); addProperty("balanceColors", true); addProperty("fixed", true)
        }) })
        add("sourceSetCodes", JsonArray().apply { sources.forEach { add(it) } })
    }
    private fun JsonObject.withRecipes(vararg recipes: Pair<String, JsonObject>) = apply {
        add("booster", JsonObject().apply { recipes.forEach { (name, recipe) -> add(name, recipe) } })
    }
    private fun catalog(vararg sets: JsonObject): CatalogRepository {
        val repository = CatalogRepository(temporary.resolve("catalog").toFile()) {
            JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}")
        }
        repository.stage { index ->
            val root = JsonObject().apply {
                add("meta", JsonObject().apply { addProperty("date", metadata.date); addProperty("version", metadata.version) })
                add("data", JsonObject().apply { sets.forEach { add(it["code"].asString, it) } })
            }
            MtgJsonCatalog().import(root.toString().byteInputStream(), index, metadata)
        }
        return repository
    }
    private fun repository(catalog: CatalogRepository, source: FixtureSource, compressed: Long = HttpCatalogSource.MAX_SET_BYTES,
        decoded: Long = HttpCatalogSource.MAX_DECODED_SET_BYTES) = SetSourceRepository(cache, catalog, source, clock, compressed, decoded)

    private fun assertConsistent(upload: SetSourceUpload) {
        assertTrue(upload.snapshot.validate().isEmpty(), upload.snapshot.validate().toString())
        val definitions = upload.definitions.definitions.associateBy { it.digest }
        assertEquals(upload.snapshot.cards.map { it.definition }.toSet(), definitions.keys)
        upload.definitions.definitions.forEach {
            assertEquals(DeckJson.digest(it.definition), it.digest)
            assertTrue(DeckValidation.definition(it.definition))
        }
        upload.snapshot.recipe?.sheets?.values?.forEach { sheet ->
            assertTrue(sheet.cards.keys.all { id -> upload.snapshot.cards.any { it.uuid == id } })
            assertEquals(sheet.cards.values.sum(), sheet.totalWeight)
        }
    }

    @Test fun listsOnlyInstalledNonTokenSetsNewestFirstWithNamesCountsAndLegacyFallback() {
        val old = set(cards = listOf(card(1), card(2)))
        val recent = set("NEW", today.toString(), listOf(card(3)))
        val tokens = set("TOK", "2027-01-01", emptyList()).apply {
            add("tokens", JsonArray().apply { add(card(4).apply { addProperty("layout", "token") }) })
        }
        val catalog = catalog(old, recent, tokens)
        val source = FixtureSource()
        val sets = repository(catalog, source).sets()
        assertEquals(listOf(DraftableSet("NEW", "Set NEW", today.toString(), 1),
            DraftableSet("OLD", "Set OLD", "2000-01-01", 2)), sets)
        assertEquals(0, source.listRequests)
        val pointer = temporary.resolve("catalog/current").toFile().readText()
        DriverManager.getConnection("jdbc:sqlite:${temporary.resolve("catalog/$pointer")}").use {
            it.createStatement().use { statement -> statement.execute("DROP TABLE catalog_set_name") }
        }
        assertEquals(listOf("NEW", "OLD"), catalog.draftableSets().map { it.name })
        val offline = repository(catalog, source)
        repeat(2) { assertEquals(listOf("NEW", "OLD"), offline.sets().map { it.name }) }
        assertEquals(1, source.listRequests)
        assertTrue(source.downloads.isEmpty())
    }

    @Test fun legacyNamesUseSetListOnceWithoutDownloadingCardsOrReplacingKnownNames() {
        val catalog = catalog(set("OLD").apply { remove("name") }, set("NEW", today.toString(), listOf(card(2))),
            set("NONAME", cards = listOf(card(3))).apply { remove("name") })
        val source = FixtureSource().apply {
            listed = CatalogSetList(metadata, listOf(CatalogSet("OLD", "2000-01-01", name = "Old set name"),
                CatalogSet("NEW", today.toString(), name = "Different remote name")))
        }
        val repository = repository(catalog, source)
        repeat(2) {
            val names = repository.sets().associate { it.code to it.name }
            assertEquals(mapOf("OLD" to "Old set name", "NEW" to "Set NEW", "NONAME" to "NONAME"), names)
        }
        assertEquals(1, source.listRequests)
        assertTrue(source.requests.isEmpty())
        assertTrue(source.downloads.isEmpty())
    }

    @Test fun oldSetCacheMissThenPermanentHitAcrossRepositoryInstancesAndManualRedownload() {
        val set = set().withRecipes("draft" to recipe(listOf(1)))
        val catalog = catalog(set)
        val source = FixtureSource().apply { put(set) }
        val repository = repository(catalog, source)
        assertFalse(repository.isCached("OLD"))
        val first = repository.load("OLD")
        assertConsistent(first)
        assertEquals(listOf("OLD"), source.downloads)
        assertTrue(cache.resolve("OLD.json.gz").isFile)
        assertTrue(repository.isCached("OLD"))
        clock.now = clock.now.plusSeconds(365 * 24 * 60 * 60)
        assertEquals(first, repository(catalog, source).load("OLD"))
        assertEquals(listOf("OLD"), source.requests)
        repository.redownload("OLD")
        assertEquals(listOf("OLD", "OLD"), source.downloads)
    }

    @Test fun sixtyDayBoundaryAndFutureSetsRefreshDailyButOlderSetsDoNot() {
        val sets = listOf(set("OLD", today.minusDays(61).toString(), listOf(card(1))),
            set("REC", today.minusDays(59).toString(), listOf(card(2))),
            set("EDGE", today.minusDays(60).toString(), listOf(card(3))),
            set("FUT", today.plusDays(30).toString(), listOf(card(4))))
        val catalog = catalog(*sets.toTypedArray())
        val source = FixtureSource().apply { sets.forEach(::put) }
        val repository = repository(catalog, source)
        sets.forEach { repository.load(it["code"].asString) }
        sets.forEach { repository.load(it["code"].asString) }
        assertEquals(4, source.downloads.size)
        clock.tomorrow()
        sets.forEach { repository.load(it["code"].asString) }
        assertEquals(listOf("OLD", "REC", "EDGE", "FUT", "REC", "FUT"), source.downloads)
        // Force a previous-day cache timestamp to test the inclusive day-60 boundary itself.
        clock.now = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        cache.resolve("EDGE.json.gz").setLastModified(clock.millis() - 24 * 60 * 60_000)
        repository.load("EDGE")
        assertEquals("EDGE", source.downloads.last())
    }

    @Test fun recipePreferenceUsesProtocolOrderAndNoRecipeIsApproximate() {
        val set = set().withRecipes("default" to recipe(listOf(1)), "draft" to recipe(listOf(1)), "play" to recipe(listOf(1)))
        val catalog = catalog(set)
        val source = FixtureSource().apply { put(set) }
        val repository = repository(catalog, source)
        assertEquals("play", repository.load("OLD").snapshot.recipe!!.name)
        set.getAsJsonObject("booster").remove("play"); source.put(set)
        assertEquals("draft", repository.redownload("OLD").snapshot.recipe!!.name)
        set.getAsJsonObject("booster").remove("draft"); source.put(set)
        assertEquals("default", repository.redownload("OLD").snapshot.recipe!!.name)
        set.remove("booster"); source.put(set)
        val approximate = repository.redownload("OLD")
        assertNull(approximate.snapshot.recipe)
        assertConsistent(approximate)
    }

    @Test fun mh3AndLtrSheetWeightDistributionsLoadWithoutIntOverflow() {
        // Unchanged weight histograms and totals of MH3 play/wildcard and LTR draft/foilWithShowcase
        // from magic-sealed-data's experimental_export_for_mtgjson.json; UUIDs are fixture cards.
        val distributions = listOf(
            Triple("MH3", 1_509_042_889_200L, mapOf(
                133593600L to 35, 6491647008L to 69, 4636890720L to 12, 1297766400L to 23,
                2003904000L to 19, 66447920L to 17, 930270880L to 11, 1870310400L to 17,
                8059369395L to 73, 6261901800L to 20, 1854756288L to 16, 645494080L to 6,
                5756692425L to 7, 284776800L to 9, 2302676970L to 7, 1508892000L to 1,
                572544000L to 26, 711942000L to 2, 5281122000L to 8, 996718800L to 1,
                1431360000L to 1, 2640561000L to 8)),
            Triple("LTR", 4_954_017_600L, mapOf(
                13819470L to 79, 2166480L to 20, 4332960L to 17, 8665920L to 60,
                22017856L to 101, 4163040L to 20, 33026784L to 20, 1720145L to 9,
                2081520L to 7, 1040760L to 8, 6909735L to 19, 11008928L to 8)))
        val sets = distributions.mapIndexed { setIndex, (code, total, histogram) ->
            val weights = histogram.flatMap { (weight, count) -> List(count) { weight } }
            val ids = weights.indices.map { it + 1 + setIndex * 1000 }
            val recipe = recipe(ids).apply {
                getAsJsonObject("sheets").getAsJsonObject("main").apply {
                    addProperty("fixed", false)
                    addProperty("totalWeight", total)
                    add("cards", JsonObject().apply { ids.zip(weights).forEach { (id, weight) -> addProperty(uuid(id), weight) } })
                }
            }
            set(code, cards = ids.map { card(it) }).withRecipes("draft" to recipe)
        }
        val catalog = catalog(*sets.toTypedArray())
        val source = FixtureSource().apply { sets.forEach(::put) }
        val repository = repository(catalog, source)
        for ((code, total, _) in distributions) {
            val upload = repository.load(code)
            assertConsistent(upload)
            assertTrue(upload.warnings.isEmpty())
            assertEquals(total, upload.snapshot.recipe!!.sheets.getValue("main").totalWeight)
            val seed = RandomV1.encodeSeed(ByteArray(32))
            assertTrue(SetBoosters.generate(upload.snapshot, 10, seed).all { it.kind == SetBoosterKind.RECIPE_BASED })
            assertEquals(upload, repository.load(code))
        }
        val mh3 = repository.load("MH3").snapshot.recipe!!.sheets.getValue("main")
        assertTrue(6_491_647_008L in mh3.cards.values)
    }

    @Test fun compositionWeightsAreLongAndFilteredSheetTotalsRemainLong() {
        val large = 6_491_647_008L
        val recipe = recipe(listOf(1, 2, 3)).apply {
            getAsJsonArray("boosters")[0].asJsonObject.addProperty("weight", large)
            addProperty("boostersTotalWeight", large)
            getAsJsonObject("sheets").getAsJsonObject("main").apply {
                addProperty("fixed", false)
                addProperty("totalWeight", 3 * large)
                add("cards", JsonObject().apply { (1..3).forEach { addProperty(uuid(it), large) } })
            }
        }
        val remote = set(cards = listOf(card(1), card(2), card(3))).withRecipes("play" to recipe)
        val upload = repository(catalog(set(cards = listOf(card(1), card(2)))), FixtureSource().apply { put(remote) }).load("OLD")
        assertConsistent(upload)
        assertEquals(large, upload.snapshot.recipe!!.boosters.single().weight)
        assertEquals(large, upload.snapshot.recipe!!.boostersTotalWeight)
        assertEquals(2 * large, upload.snapshot.recipe!!.sheets.getValue("main").totalWeight)
    }

    @Test fun cluStyleFixedSheetLoadsAndDrawsItsTwentyCardMultiset() {
        val ids = (1..16).toList()
        val recipe = recipe(ids, count = 20).apply {
            getAsJsonObject("sheets").getAsJsonObject("main").apply {
                add("cards", JsonObject().apply { ids.forEach { addProperty(uuid(it), if (it == 3) 5 else 1) } })
                addProperty("totalWeight", 20)
            }
        }
        val remote = set("CLU", cards = ids.map { card(it) }).withRecipes("default" to recipe)
        val upload = repository(catalog(remote), FixtureSource().apply { put(remote) }).load("CLU")
        assertConsistent(upload)
        val expected = upload.snapshot.cards.flatMap { card -> List(if (card.uuid == uuid(3)) 5 else 1) { card.definition } }
        assertEquals(20, expected.size)
        val seed = RandomV1.encodeSeed(ByteArray(32))
        assertTrue(SetBoosters.generate(upload.snapshot, 10, seed).all { pack -> pack.cards.map { it.definition } == expected })
    }

    @Test fun selectedRecipeFetchesDependencyCardsOnlyAndReusesCaches() {
        val main = set().withRecipes("play" to recipe(listOf(1, 2), listOf("DEP", "OLD")),
            "draft" to recipe(listOf(3), listOf("UNUSED")))
        val dependency = set("DEP", cards = listOf(card(2, "rare", "U"), card(3)))
            .withRecipes("draft" to recipe(listOf(3), listOf("NOTNEEDED")))
        val catalog = catalog(main, dependency)
        val source = FixtureSource().apply { put(main); put(dependency) }
        val repository = repository(catalog, source)
        val upload = repository.load("OLD")
        assertConsistent(upload)
        assertEquals(listOf("OLD", "DEP"), source.downloads)
        assertEquals(setOf(uuid(1), uuid(2)), upload.snapshot.cards.map { it.uuid }.toSet())
        assertEquals(SetCardRarity.RARE, upload.snapshot.cards.single { it.uuid == uuid(2) }.rarity)
        assertEquals(setOf(DraftColor.U), upload.snapshot.cards.single { it.uuid == uuid(2) }.colors)
        assertTrue(upload.snapshot.recipe!!.sheets.getValue("main").let { it.foil && it.balanceColors && it.fixed })
        assertEquals(upload, repository.load("OLD"))
        assertEquals(2, source.downloads.size)
        repository.redownload("OLD")
        assertEquals(listOf("OLD", "DEP", "OLD", "DEP"), source.downloads)
    }

    @Test fun definitionsExactlyMatchDeckImportIncludingBothFacesAndDeduplication() {
        val front = card(1, color = "W").apply {
            addProperty("name", "Front // Back"); addProperty("faceName", "Front"); addProperty("side", "a")
            addProperty("layout", "transform"); add("otherFaceIds", JsonArray().apply { add(uuid(2)) })
        }
        val back = card(2, color = "U").apply {
            addProperty("name", "Front // Back"); addProperty("faceName", "Back"); addProperty("side", "b")
            addProperty("layout", "transform"); add("otherFaceIds", JsonArray().apply { add(uuid(1)) })
        }
        val set = set(cards = listOf(back, front)).withRecipes("draft" to recipe(listOf(1)))
        val catalog = catalog(set)
        val upload = repository(catalog, FixtureSource().apply { put(set) }).load("OLD")
        assertConsistent(upload)
        val deck = DeckImportResolver(catalog).resolve("1 Front (OLD) 1")
        assertTrue(deck.resolved, deck.issues.toString())
        val definition = deck.entries.single().definition(deck.catalog)
        assertEquals(SubmittedDefinition(DeckJson.digest(definition), definition), upload.definitions.definitions.single())
        assertEquals(2, upload.snapshot.cards.size)
        assertTrue(upload.snapshot.cards.all { it.colors == setOf(DraftColor.W, DraftColor.U) })
    }

    @Test fun missingCardsAreReportedAndRemovedFromSheetsWithWeightsRecomputed() {
        val installed = set()
        val remote = set(cards = listOf(card(1), card(2))).withRecipes("draft" to recipe(listOf(1, 2, 3)))
        val upload = repository(catalog(installed), FixtureSource().apply { put(remote) }).load("OLD")
        assertConsistent(upload)
        assertEquals(listOf(MissingSetCard(uuid(2), "OLD", MissingSetCardReason.CATALOG),
            MissingSetCard(uuid(3), null, MissingSetCardReason.SOURCE_FILE)), upload.missingCards)
        assertEquals(mapOf(uuid(1) to 1L), upload.snapshot.recipe!!.sheets.getValue("main").cards)
        assertTrue(upload.warnings.any { it.contains("2 cards") })
    }

    @Test fun unusableRecipeAfterExclusionsFallsBackWithExplicitApproximationWarning() {
        val remote = set(cards = listOf(card(1), card(2))).withRecipes("draft" to recipe(listOf(1, 2), count = 2))
        val upload = repository(catalog(set()), FixtureSource().apply { put(remote) }).load("OLD")
        assertConsistent(upload)
        assertNull(upload.snapshot.recipe)
        assertTrue(upload.warnings.any { it.contains("approximate") })
    }

    @Test fun allCardsMissingFailsClearlyInsteadOfReturningAnInvalidUpload() {
        val remote = set(cards = listOf(card(2)))
        val repository = repository(catalog(set()), FixtureSource().apply { put(remote) })
        val error = assertThrows(IllegalStateException::class.java) { repository.load("OLD") }
        assertTrue(error.message!!.contains("No usable cards"))
        assertTrue(error.message!!.contains(uuid(2)))
    }

    @Test fun combinedMainAndDependencyCardsCannotExceedSnapshotLimit() {
        val main = set(cards = (1..SetSourceValidation.MAX_CARDS).map { card(it) })
            .withRecipes("draft" to recipe(listOf(6000), listOf("DEP")))
        val dependency = set("DEP", cards = listOf(card(6000)))
        val source = FixtureSource().apply { put(main); put(dependency) }
        val repository = repository(catalog(set()), source)
        val error = assertThrows(IllegalStateException::class.java) { repository.load("OLD") }
        assertEquals("Set source card limit", error.message)
    }

    @Test fun tokensAndCardsWithoutUuidAreNotEligible() {
        val remote = set(cards = listOf(card(1), card(2).apply { addProperty("layout", "token") },
            card(3).apply { remove("uuid") }, card(4).apply { addProperty("isToken", true) })).apply {
            add("tokens", JsonArray().apply { add(card(5)) })
        }
        val upload = repository(catalog(set()), FixtureSource().apply { put(remote) }).load("OLD")
        assertConsistent(upload)
        assertEquals(listOf(uuid(1)), upload.snapshot.cards.map { it.uuid })
        assertTrue(upload.missingCards.isEmpty())
    }

    @Test fun failedRedownloadKeepsPreviousCacheAndCleansPartialFile() {
        val set = set()
        val catalog = catalog(set)
        val source = FixtureSource().apply { put(set) }
        val repository = repository(catalog, source)
        val before = repository.load("OLD")
        val bytes = cache.resolve("OLD.json.gz").readBytes()
        source.badChecksum = true
        assertThrows(IllegalStateException::class.java) { repository.redownload("OLD") }
        assertArrayEquals(bytes, cache.resolve("OLD.json.gz").readBytes())
        assertEquals(before, repository.load("OLD"))
        assertTrue(cache.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test fun compressedAndDecodedLimitsApplyBeforePublicationEvenWithoutContentLength() {
        val set = set()
        val catalog = catalog(set)
        val source = FixtureSource().apply { put(set) }
        assertThrows(IllegalStateException::class.java) { repository(catalog, source, compressed = 10).load("OLD") }
        assertTrue(source.downloads.isEmpty())
        source.unknownSize = true
        assertThrows(IllegalStateException::class.java) { repository(catalog, source, compressed = 10).load("OLD") }
        assertFalse(cache.resolve("OLD.json.gz").exists())
        assertThrows(IllegalStateException::class.java) { repository(catalog, source, decoded = 10).load("OLD") }
        assertFalse(cache.resolve("OLD.json.gz").exists())
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun protocolCardSheetCompositionAndDependencyLimitsRejectOversizedSources() {
        val catalog = catalog(set())
        val source = FixtureSource()
        val oversized = set(cards = (1..SetSourceValidation.MAX_CARDS + 1).map { card(it) })
        source.put(oversized)
        assertThrows(IllegalStateException::class.java) { repository(catalog, source).load("OLD") }
        val tooManySheets = recipe(listOf(1)).apply {
            val sheets = getAsJsonObject("sheets")
            repeat(SetSourceValidation.MAX_SHEETS) { sheets.add("extra$it", sheets["main"].deepCopy()) }
        }
        source.put(set().withRecipes("draft" to tooManySheets))
        assertThrows(IllegalStateException::class.java) { repository(catalog, source).load("OLD") }
        val tooManyBoosters = recipe(listOf(1)).apply {
            val boosters = getAsJsonArray("boosters")
            repeat(SetSourceValidation.MAX_COMPOSITIONS) { boosters.add(boosters[0].deepCopy()) }
        }
        source.put(set().withRecipes("draft" to tooManyBoosters))
        assertThrows(IllegalStateException::class.java) { repository(catalog, source).load("OLD") }
        source.put(set().withRecipes("draft" to recipe(listOf(1), (1..SetSourceValidation.MAX_SHEETS + 1).map { "S$it" })))
        assertThrows(IllegalStateException::class.java) { repository(catalog, source).load("OLD") }
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun hugeIgnoredProviderFieldsAreStreamedAndMalformedRecipesAreNotSilentlyRepaired() {
        val remote = set().apply { addProperty("unneeded", "x".repeat(500_000)) }
        val catalog = catalog(set())
        val source = FixtureSource().apply { put(remote) }
        assertConsistent(repository(catalog, source).load("OLD"))
        remote.withRecipes("draft" to recipe(listOf(1)).apply { addProperty("boostersTotalWeight", 99) })
        source.put(remote)
        assertThrows(IllegalStateException::class.java) { repository(catalog, source).redownload("OLD") }
        assertConsistent(repository(catalog, source).load("OLD"))
    }

    @Test fun unknownAndPathLikeSetCodesNeverReachSource() {
        val source = FixtureSource()
        val repository = repository(catalog(set()), source)
        assertThrows(IllegalArgumentException::class.java) { repository.load("../OLD") }
        assertThrows(IllegalArgumentException::class.java) { repository.load("NOPE") }
        assertTrue(source.requests.isEmpty())
    }
}
