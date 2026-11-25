package org.asyncttwiz.catalog

import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The draft bots' card ratings (backlog, owner 2026-11-25): each card's GIH WR (the win rate of the games it was in the
 * opening hand or drawn) in Premier Draft of its set (else its Pick Two Draft, [FORMATS]), from 17Lands' public game data
 * (licensed CC BY 4.0, which 17Lands asks people to use rather than its site's API). [fetch] reads each listed set's game
 * data once into one local file outside the repository ([write], gzip lines `set<TAB>card<TAB>games<TAB>wins`), whose
 * SHA-256 the catalog's pin names; the catalog build bakes a per-set table of the cards with at least [MIN_GAMES] games
 * into the set records ([CatalogSetRecord.ratings]), and a set without data of its own borrows another's ([BORROWED]).
 * Never committed.
 */
object CatalogRatings {
    /** The fewest games in hand a card needs for its rating to count: fewer say too little. */
    const val MIN_GAMES = 200L
    const val DATASETS = "https://www.17lands.com/api/public_datasets"

    /** The 17Lands formats whose game data rates a set, the first it has: Premier Draft, else Pick Two Draft (the only
     * draft Arena held of Through the Omenpaths, OM1). */
    val FORMATS = listOf("PremierDraft", "PickTwoDraft")

    /**
     * Sets 17Lands has no data for that use another set's ratings for the cards they share (by the catalog's card names),
     * set → the set it borrows from: Marvel's Spider-Man's cards are Through the Omenpaths' under other names (owner,
     * 2026-11-25). A borrowing set's other cards are unrated.
     */
    val BORROWED = mapOf("SPM" to "OM1")

    /** A card's games in hand and how many of them were won. */
    data class Count(val games: Long, val wins: Long)

    /** The local ratings file: each set's cards' counts ([sets]), and for each listed set that has data, the date 17Lands
     * last updated it ([updated]) and the format the counts are from ([formats], one of [FORMATS]); [missing]: listed sets
     * 17Lands has no game data of those formats for. */
    data class Ratings(val sets: Map<String, Map<String, Count>>, val updated: Map<String, String>, val formats: Map<String, String>,
        val missing: List<String>)

    /** The GIH WR in tenths of a percent of each card of [counts] with at least [MIN_GAMES] games, by its name. */
    fun table(counts: Map<String, Count>): Map<String, Int> = counts.filterValues { it.games >= MIN_GAMES }
        .mapValues { (_, count) -> Math.round(count.wins * 1000.0 / count.games).toInt() }

    /**
     * Counts, from one set's 17Lands game data ([input], the uncompressed CSV: a game a line, `won` and per card
     * `opening_hand_<name>` and `drawn_<name>`), the games each card was in hand (in the opening hand or drawn) and the
     * games of those won.
     */
    fun gameData(input: InputStream): Map<String, Count> {
        val stream = input.buffered(1 shl 20)
        val header = csvFields(readLine(stream) ?: error("No header in the game data"))
        val won = header.indexOf("won").also { check(it >= 0) { "No won column in the game data" } }
        val names = ArrayList<String>()
        val slots = IntArray(header.size) { -1 }
        header.forEachIndexed { index, column ->
            val name = column.removePrefix("opening_hand_").takeIf { it != column } ?: column.removePrefix("drawn_").takeIf { it != column }
                ?: return@forEachIndexed
            var slot = names.indexOf(name)
            if (slot < 0) { names += name; slot = names.size - 1 }
            slots[index] = slot
        }
        val games = LongArray(names.size)
        val wins = LongArray(names.size)
        val inHand = BooleanArray(names.size)
        val buffer = ByteArray(1 shl 20)
        var field = 0
        var value = 0
        var winner = false
        var length = 0
        var rows = 0L
        fun endField() {
            if (field == won) winner = value == 1
            else if (field < slots.size && slots[field] >= 0 && value > 0) inHand[slots[field]] = true
            field++; value = 0
        }
        fun endRow() {
            if (field == 0 && value == 0) return
            endField()
            for (slot in inHand.indices) if (inHand[slot]) { games[slot]++; if (winner) wins[slot]++; inHand[slot] = false }
            field = 0; winner = false; rows++
        }
        while (true) {
            length = stream.read(buffer)
            if (length < 0) break
            for (i in 0 until length) {
                when (val byte = buffer[i].toInt()) {
                    ','.code -> endField()
                    '\n'.code -> endRow()
                    '\r'.code -> Unit
                    // A count is digits; won is True or False
                    'T'.code -> value = 1
                    else -> if (byte in '0'.code..'9'.code) value = value * 10 + (byte - '0'.code)
                }
            }
        }
        endRow()
        check(rows > 0) { "No games in the game data" }
        return names.indices.filter { games[it] > 0 }.associate { names[it] to Count(games[it], wins[it]) }
    }

    /** One line of [stream] (the header), without its line end; null at the end. */
    private fun readLine(stream: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val byte = stream.read()
            if (byte < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.UTF_8)
            if (byte == '\n'.code) return bytes.toString(Charsets.UTF_8).removeSuffix("\r")
            bytes.write(byte)
        }
    }

    /** A CSV line's fields (a quoted field may hold commas, a doubled quote is one quote). */
    internal fun csvFields(line: String): List<String> {
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    /** Writes [ratings] to [file] (gzip; sets and cards sorted, so the same data always gives the same bytes). */
    fun write(ratings: Ratings, file: File) {
        file.parentFile?.mkdirs()
        GZIPOutputStream(file.outputStream()).bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("# Draft bot card ratings: games each card was in hand (opening hand or drawn) and how many were won, in the format named.\n")
            out.write("# From 17Lands' public game data, licensed CC BY 4.0: $DATASETS. Kept outside the repository.\n")
            ratings.updated.toSortedMap().forEach { (set, date) -> out.write("# updated\t$set\t$date\t${ratings.formats.getValue(set)}\n") }
            ratings.missing.sorted().forEach { out.write("# missing\t$it\n") }
            ratings.sets.toSortedMap().forEach { (set, cards) ->
                cards.toSortedMap().forEach { (name, count) -> out.write("$set\t$name\t${count.games}\t${count.wins}\n") }
            }
        }
    }

    /** The ratings in [file] ([write]'s format; plain text unless its name ends in .gz, as the test cards' invented ratings). */
    fun read(file: File): Ratings {
        val sets = LinkedHashMap<String, MutableMap<String, Count>>()
        val updated = LinkedHashMap<String, String>()
        val formats = LinkedHashMap<String, String>()
        val missing = ArrayList<String>()
        (if (file.name.endsWith(".gz")) GZIPInputStream(file.inputStream()) else file.inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split('\t')
                when {
                    line.startsWith("# updated\t") -> { check(parts.size == 4) { "Not an updated line: $line" }; updated[parts[1]] = parts[2]; formats[parts[1]] = parts[3] }
                    line.startsWith("# missing\t") -> missing += parts[1]
                    line.startsWith("#") -> Unit
                    else -> {
                        check(parts.size == 4) { "Not a ratings line: $line" }
                        sets.getOrPut(parts[0]) { LinkedHashMap() }[parts[1]] = Count(parts[2].toLong(), parts[3].toLong())
                    }
                }
            }
        }
        return Ratings(sets, updated, formats, missing)
    }

    /**
     * Reads 17Lands' list of public datasets and, for each of [sets] with game data of one of [FORMATS] (the first it has),
     * counts its cards from that data, downloaded and read as it streams ([gameData]); [log] says what each set gave.
     */
    fun fetch(sets: List<String>, log: (String) -> Unit): Ratings {
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
        fun get(url: String): HttpResponse<InputStream> {
            val response = client.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(30))
                .header("User-Agent", "async-tabletop-wizards catalog build").build(), HttpResponse.BodyHandlers.ofInputStream())
            check(response.statusCode() == 200) { response.body().close(); "$url answered ${response.statusCode()}" }
            return response
        }
        val datasets = get(DATASETS).body().use { JsonParser.parseReader(it.reader(Charsets.UTF_8)).asJsonArray }.map { it.asJsonObject }
        val counted = LinkedHashMap<String, Map<String, Count>>()
        val updated = LinkedHashMap<String, String>()
        val formats = LinkedHashMap<String, String>()
        val missing = ArrayList<String>()
        for (set in sets) {
            val dataset = dataset(datasets, set)
            if (dataset == null) { missing += set; log("$set: no ${FORMATS.joinToString(" or ")} game data at 17Lands"); continue }
            val counts = get(dataset.getAsJsonObject("urls").get("game").asString).body()
                .use { body -> GZIPInputStream(body, 1 shl 16).use(::gameData) }
            counted[set] = counts
            updated[set] = dataset.get("last_updated")?.asString.orEmpty()
            formats[set] = dataset.get("event_type").asString
            log("$set: ${counts.size} cards, ${table(counts).size} with at least $MIN_GAMES games in hand (${formats[set]}, data of ${updated[set]})")
        }
        return Ratings(counted, updated, formats, missing)
    }

    /** The dataset of 17Lands' list [datasets] that rates [set]: its game data in the first of [FORMATS] it has, or null. */
    internal fun dataset(datasets: List<com.google.gson.JsonObject>, set: String): com.google.gson.JsonObject? {
        val games = datasets.filter { it.get("expansion")?.asString == set && it.getAsJsonObject("urls")?.get("game")?.isJsonPrimitive == true }
        return FORMATS.firstNotNullOfOrNull { format -> games.firstOrNull { it.get("event_type")?.asString == format } }
    }
}
