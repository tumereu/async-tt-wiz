package org.asyncttwiz.android

import org.asyncttwiz.appui.LogPriority
import org.asyncttwiz.appui.platformLog
import org.asyncttwiz.appui.File
import org.asyncttwiz.appui.Instant
import org.asyncttwiz.appui.instantNow
import org.asyncttwiz.appui.readLinesIfFile
import org.asyncttwiz.appui.appendToFile
import org.asyncttwiz.appui.replaceFileText
import org.asyncttwiz.appui.className
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.Synchronized

/** Process-wide diagnostics: logcat plus a bounded, persisted ring of recent lines sent with problem reports. */
object AppLog {
    @Volatile private var buffer = LogBuffer(null)

    /** Loads lines persisted before a restart or crash. */
    fun install(file: File) { buffer = LogBuffer(file) }
    /** Detail kept for problem reports ("D" in the log): for lines that explain a report, written only when they say something new. */
    fun debug(tag: String, message: String) = write(LogPriority.DEBUG, tag, message, null)
    fun info(tag: String, message: String, failure: Throwable? = null) = write(LogPriority.INFO, tag, message, failure)
    fun warn(tag: String, message: String, failure: Throwable? = null) = write(LogPriority.WARN, tag, message, failure)
    fun error(tag: String, message: String, failure: Throwable? = null) = write(LogPriority.ERROR, tag, message, failure)
    fun text(): String = buffer.text()

    /** In the engine's Web Worker, every line also goes to the page's app log (priority, tag and
     * the message with its failure's summary), so problem reports carry the worker's lines; null elsewhere. */
    @Volatile var forward: ((priority: Int, tag: String, message: String) -> Unit)? = null

    /** WEB-056: on the web page, every line as kept (its time, level and tag in front) also goes to the visit journal, which
     * keeps the last ones in the browser's storage so a visit the browser ends is explained by the next one; null elsewhere. */
    @Volatile var observer: ((priority: Int, line: String) -> Unit)? = null

    private fun write(priority: Int, tag: String, message: String, failure: Throwable?) {
        val line = buffer.add(priority, tag, message, failure)
        forward?.let { f -> try { f(priority, tag, line.substringAfter(": ")) } catch (_: RuntimeException) {} }
        observer?.let { o -> try { o(priority, line) } catch (_: RuntimeException) {} }
        // Plain JVM unit tests have no logcat.
        try { platformLog(priority, "ATW/$tag", line.substringAfter(": ")) } catch (_: RuntimeException) {}
    }
}

class LogBuffer(private val file: File?, private val maxBytes: Int = 256 * 1024,
    private val now: () -> Instant = ::instantNow) {
    private val lines = ArrayDeque<String>()
    private var bytes = 0

    init {
        val stored = runCatching { file?.let(::readLinesIfFile) }.getOrNull().orEmpty()
        // Indented lines continue the entry above (stack traces).
        for (line in stored) if (line.startsWith(INDENT) && lines.isNotEmpty()) {
            val entry = lines.removeAt(lines.lastIndex); bytes -= size(entry); keep(entry + "\n" + line)
        } else if (line.isNotEmpty()) keep(line)
        trim(maxBytes)
    }

    @Synchronized fun add(priority: Int, tag: String, message: String, failure: Throwable? = null): String {
        val level = when (priority) { LogPriority.ERROR -> 'E'; LogPriority.WARN -> 'W'; LogPriority.DEBUG -> 'D'; else -> 'I' }
        val detail = failure?.let { if (priority == LogPriority.ERROR) "\n" + it.stackTraceToString().take(8_000) else " | " + summary(it) }.orEmpty()
        val line = redactLog("${now()} $level/$tag: $message$detail").take(16_000).replace("\n", "\n$INDENT")
        keep(line)
        if (bytes > maxBytes) { trim(maxBytes * 3 / 4); persist() } else append(line)
        return line
    }

    @Synchronized fun text(): String = lines.joinToString("\n")

    private fun keep(line: String) { lines.addLast(line); bytes += size(line) }
    private fun trim(limit: Int) { while (bytes > limit && lines.isNotEmpty()) bytes -= size(lines.removeAt(0)) }
    private fun size(line: String) = line.encodeToByteArray().size + 1

    private fun append(line: String) {
        val target = file ?: return
        runCatching { appendToFile(target, line + "\n") }
    }

    private fun persist() {
        val target = file ?: return
        runCatching { replaceFileText(target, lines.joinToString("") { it + "\n" }) }
    }

    private companion object {
        const val INDENT = "    "
        fun summary(failure: Throwable): String = generateSequence(failure) { it.cause.takeIf { cause -> cause !== it } }.take(4)
            .joinToString(" <- ") { "${className(it)}: ${it.message.orEmpty().take(300)}" }
    }
}

/** A redaction: its pattern and replacement, run only on a line that [may] hold a match (a quick test with no regex: the web
 * client's regexes are slow, and every line went through all six, owner 2026-11-15: "Web FPS is still stuttery"). */
private class Redaction(val pattern: Regex, val replacement: String, val may: (String) -> Boolean)

private fun mentions(text: String, vararg words: String) = words.any { text.contains(it, ignoreCase = true) }

/** Whether [text] has a run of at least [length] base64url characters (a token's 43). */
private fun hasRun(text: String, length: Int): Boolean {
    var run = 0
    for (c in text) {
        run = if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-') run + 1 else 0
        if (run >= length) return true
    }
    return false
}

private val redactions = listOf(
    Redaction(Regex("(?i)bearer\\s+[^\\s\"',]+"), "Bearer [redacted]") { mentions(it, "bearer") },
    Redaction(Regex("(?i)(authorization\\s*[:=]\\s*)(?!bearer \\[redacted])[^\\s\"',]+"), "$1[redacted]") { mentions(it, "authorization") },
    Redaction(Regex("(?i)\"(password|token|email|secret)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"[redacted]\"") {
        mentions(it, "password", "token", "email", "secret") },
    Redaction(Regex("(?i)\\b(password|token|secret)=[^&\\s\"']+"), "$1=[redacted]") { mentions(it, "password", "token", "secret") },
    Redaction(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]") { '@' in it },
    // Session and reset tokens: 43 base64url characters.
    Redaction(Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])"), "[redacted]") { hasRun(it, 43) })

/** Last line of defence: callers never pass secrets, but exception messages and logcat may contain them. */
fun redactLog(text: String): String = redactions.fold(text) { value, r -> if (r.may(value)) r.pattern.replace(value, r.replacement) else value }
