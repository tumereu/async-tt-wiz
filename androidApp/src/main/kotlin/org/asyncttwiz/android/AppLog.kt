package org.asyncttwiz.android

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** Process-wide diagnostics: logcat plus a bounded, persisted ring of recent lines sent with problem reports. */
object AppLog {
    @Volatile private var buffer = LogBuffer(null)

    /** Loads lines persisted before a restart or crash. */
    fun install(file: File) { buffer = LogBuffer(file) }
    fun info(tag: String, message: String, failure: Throwable? = null) = write(Log.INFO, tag, message, failure)
    fun warn(tag: String, message: String, failure: Throwable? = null) = write(Log.WARN, tag, message, failure)
    fun error(tag: String, message: String, failure: Throwable? = null) = write(Log.ERROR, tag, message, failure)
    fun text(): String = buffer.text()

    private fun write(priority: Int, tag: String, message: String, failure: Throwable?) {
        val line = buffer.add(priority, tag, message, failure)
        // Plain JVM unit tests have no logcat.
        try { Log.println(priority, "ATW/$tag", line.substringAfter(": ")) } catch (_: RuntimeException) {}
    }
}

internal class LogBuffer(private val file: File?, private val maxBytes: Int = 256 * 1024,
    private val now: () -> Instant = Instant::now) {
    private val lines = ArrayDeque<String>()
    private var bytes = 0

    init {
        val stored = runCatching { file?.takeIf { it.isFile }?.readLines(Charsets.UTF_8) }.getOrNull().orEmpty()
        // Indented lines continue the entry above (stack traces).
        for (line in stored) if (line.startsWith(INDENT) && lines.isNotEmpty()) {
            val entry = lines.removeLast(); bytes -= size(entry); keep(entry + "\n" + line)
        } else if (line.isNotEmpty()) keep(line)
        trim(maxBytes)
    }

    @Synchronized fun add(priority: Int, tag: String, message: String, failure: Throwable? = null): String {
        val level = when (priority) { Log.ERROR -> 'E'; Log.WARN -> 'W'; else -> 'I' }
        val detail = failure?.let { if (priority == Log.ERROR) "\n" + it.stackTraceToString().take(8_000) else " | " + summary(it) }.orEmpty()
        val line = redactLog("${now()} $level/$tag: $message$detail").take(16_000).replace("\n", "\n$INDENT")
        keep(line)
        if (bytes > maxBytes) { trim(maxBytes * 3 / 4); persist() } else append(line)
        return line
    }

    @Synchronized fun text(): String = lines.joinToString("\n")

    private fun keep(line: String) { lines.addLast(line); bytes += size(line) }
    private fun trim(limit: Int) { while (bytes > limit && lines.isNotEmpty()) bytes -= size(lines.removeFirst()) }
    private fun size(line: String) = line.toByteArray(Charsets.UTF_8).size + 1

    private fun append(line: String) {
        val target = file ?: return
        runCatching { FileOutputStream(target, true).use { it.write((line + "\n").toByteArray(Charsets.UTF_8)) } }
    }

    private fun persist() {
        val target = file ?: return
        runCatching {
            val part = File(target.path + ".part")
            part.writeText(lines.joinToString("") { it + "\n" }, Charsets.UTF_8)
            check(part.renameTo(target))
        }
    }

    private companion object {
        const val INDENT = "    "
        fun summary(failure: Throwable): String = generateSequence(failure) { it.cause.takeIf { cause -> cause !== it } }.take(4)
            .joinToString(" <- ") { "${it.javaClass.name}: ${it.message.orEmpty().take(300)}" }
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
internal fun redactLog(text: String): String = redactions.fold(text) { value, (pattern, replacement) -> pattern.replace(value, replacement) }
