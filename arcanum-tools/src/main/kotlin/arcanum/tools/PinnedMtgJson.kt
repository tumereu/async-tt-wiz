package arcanum.tools

import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * The one MTGJSON AllPrintings file the real-card tools read: the host's pinned file (its pin, a properties file, names
 * the MTGJSON version and the file's SHA-256; Gradle passes its path as `arcanum.pin`). The file is never committed; it
 * lives in the local cache ([ParserToolPaths.mtgjson]). Without it, or with another file there, a tool stops with one
 * plain message: put the pinned file there, or move the pin.
 */
internal object PinnedMtgJson {
    /** The pinned file, checked against the pin; stops the tool when it isn't there. Unchecked without a pin. */
    fun file(): File {
        val file = ParserToolPaths.mtgjson
        val pin = System.getProperty("arcanum.pin")?.let(::File) ?: return file
        val problem = problem(pin, file) ?: return file
        System.err.println(problem)
        exitProcess(2)
    }

    /** Why [file] isn't the file [pin] names, or null when it is. */
    fun problem(pin: File, file: File): String? {
        val values = pin.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
        val version = values["mtgjson.version"].orEmpty()
        val sha = values["allprintings.sha256"].orEmpty()
        val wanted = "Put AllPrintings.json.gz of MTGJSON $version (SHA-256 $sha) there, or move the pin: scripts/catalog-pin.sh move"
        if (!file.isFile) return "The pinned MTGJSON file isn't at $file. $wanted"
        val actual = sha256(file)
        return if (actual == sha) null else "$file isn't the pinned MTGJSON file (its SHA-256 is $actual). $wanted"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * The cards of the sets [codes] from the pinned file, one set at a time in the file's order ([each] gets its code and
 * card records); the MTGJSON version is returned. Stops when a set isn't in the file: a newer file needs the pin moved.
 */
internal fun forEachPinnedSet(codes: Collection<String>, each: (String, List<JsonObject>) -> Unit): String {
    val file = PinnedMtgJson.file()
    val wanted = codes.toSet()
    val seen = HashSet<String>()
    val version = forEachSet(file, wanted) { set -> seen += set.code; each(set.code, set.cards) }
    val absent = wanted - seen
    check(absent.isEmpty()) { "Sets not in the pinned MTGJSON file $file: ${absent.sorted().joinToString()}. Move the pin to a newer build." }
    return version
}

/** The cards of set [code] from the pinned file. */
internal fun pinnedSetCards(code: String): List<SetCard> {
    var cards: List<SetCard> = emptyList()
    forEachPinnedSet(listOf(code)) { _, records -> cards = setCards(records) }
    return cards
}
