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
    fun info(tag: String, message: String, failure: Throwable? = null) = write(LogPriority.INFO, tag, message, failure)
    fun warn(tag: String, message: String, failure: Throwable? = null) = write(LogPriority.WARN, tag, message, failure)
    fun error(tag: String, message: String, failure: Throwable? = null) = write(LogPriority.ERROR, tag, message, failure)
    fun text(): String = buffer.text()

    private fun write(priority: Int, tag: String, message: String, failure: Throwable?) {
        val line = buffer.add(priority, tag, message, failure)
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
        val level = when (priority) { LogPriority.ERROR -> 'E'; LogPriority.WARN -> 'W'; else -> 'I' }
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

private val redactions = listOf(
    Regex("(?i)bearer\\s+[^\\s\"',]+") to "Bearer [redacted]",
    Regex("(?i)(authorization\\s*[:=]\\s*)(?!bearer \\[redacted])[^\\s\"',]+") to "$1[redacted]",
    Regex("(?i)\"(password|token|email|secret)\"\\s*:\\s*\"[^\"]*\"") to "\"$1\":\"[redacted]\"",
    Regex("(?i)\\b(password|token|secret)=[^&\\s\"']+") to "$1=[redacted]",
    Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "[email]",
    // Session and reset tokens: 43 base64url characters.
    Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])") to "[redacted]")

/** Last line of defence: callers never pass secrets, but exception messages and logcat may contain them. */
fun redactLog(text: String): String = redactions.fold(text) { value, (pattern, replacement) -> pattern.replace(value, replacement) }
