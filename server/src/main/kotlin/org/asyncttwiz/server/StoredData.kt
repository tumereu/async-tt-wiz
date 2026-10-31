package org.asyncttwiz.server

import org.asyncttwiz.server.database.UnreadableStoredData
import org.slf4j.LoggerFactory

/**
 * Containment for stored rows the current code can no longer read (SYS-052): the row's own requests fail
 * with [UnreadableStoredData], lists and background work leave it out, and a warning names the row (kind, id and
 * failure type only: decoder messages may quote stored content).
 * Decoding and format failures count (a removed or renamed field, an unknown enum value, a different message type),
 * and so does a stored game that decodes but fails its integrity check ([intact]): that game is contained the same way
 * and never served, repaired or rewritten here. Database and ownership failures keep failing as before (fail closed).
 */
object StoredData {
    private val log = LoggerFactory.getLogger(StoredData::class.java)

    /** kotlinx SerializationException and enum valueOf failures are IllegalArgumentExceptions. */
    private fun unreadable(failure: RuntimeException) = failure is UnreadableStoredData ||
        failure is IllegalArgumentException || failure is ClassCastException

    /** Decodes one stored row: on failure the request fails with [UnreadableStoredData] instead of a server outage. */
    fun <T> decode(kind: String, id: Any?, read: () -> T): T = try { read() } catch (failure: RuntimeException) {
        if (failure is UnreadableStoredData || !unreadable(failure)) throw failure
        log.warn("Unreadable stored {} id={} failureType={}", kind, id, failure.javaClass.simpleName)
        throw UnreadableStoredData(kind, id.toString())
    }

    /** Integrity check of a stored row that decoded fine: when it fails, the row is contained like an unreadable one. */
    fun intact(kind: String, id: Any?, check: String, ok: Boolean) {
        if (ok) return
        log.warn("Damaged stored {} id={} failedCheck={}; explicit gameplay maintenance repairs it", kind, id, check)
        throw UnreadableStoredData(kind, id.toString())
    }

    /** For lists and background work: an item that can't be read is left out (null) with a warning; the rest carries on. */
    fun <T : Any> orSkip(kind: String, id: Any?, read: () -> T?): T? = try { read() } catch (failure: RuntimeException) {
        if (!unreadable(failure)) throw failure
        log.warn("Skipped unreadable stored {} id={} failureType={}", kind, id, failure.javaClass.simpleName)
        null
    }
}
