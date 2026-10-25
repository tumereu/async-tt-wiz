package org.asyncttwiz.android

import kotlinx.collections.immutable.*
import org.asyncttwiz.protocol.*

/** Persistent order-statistic treap: append/upsert and row/key lookup never copy the transcript.
 * Versions share unchanged pages. Changes are small indexed writes for personal unread tracking. */
internal class IndexedChatMessages private constructor(private val root: Node?,
    private val ids: PersistentMap<String, ChatMessage>, private val authors: PersistentMap<String, Int>,
    private val edits: Edits) : AbstractList<ChatMessage>() {
    constructor() : this(null, persistentMapOf(), persistentMapOf(), Edits(null, emptySet()))
    // Keep write IDs, not old transcript roots and indices, alive across UI refreshes.
    private class Edits(val parent: Edits?, val ids: Set<String>) {
        val origin: Any = parent?.origin ?: Any()
    }
    fun changedSince(previous: IndexedChatMessages): Set<String>? {
        if (edits.origin !== previous.edits.origin) return null
        val ids = mutableSetOf<String>(); var cursor = edits
        while (cursor !== previous.edits) {
            ids.addAll(cursor.ids); cursor = cursor.parent ?: return null
        }
        return ids
    }
    private data class Node(val message: ChatMessage, val left: Node? = null, val right: Node? = null) {
        val size: Int = 1 + (left?.size ?: 0) + (right?.size ?: 0)
        val priority: Int = message.id.hashCode().let { var x = it; x = x xor (x ushr 16); x *= -2048144789; x xor (x ushr 13) }
    }
    override val size get() = root?.size ?: 0
    override fun get(index: Int): ChatMessage {
        require(index in indices)
        var n = root!!; var at = index
        while (true) {
            val left = n.left?.size ?: 0
            when { at < left -> n = n.left!!; at == left -> return n.message
                else -> { at -= left + 1; n = n.right!! } }
        }
    }
    fun byId(id: String) = ids[id]
    fun authoredBy(author: String?) = authors[author] ?: 0
    var writes = 0; private set
    var nodeVisits = 0; private set
    fun upsert(incoming: List<ChatMessage>): IndexedChatMessages {
        nodeVisits = 0
        var root = root; var ids = ids; var authors = authors
        val changed = mutableSetOf<String>(); var writes = 0
        for (message in incoming) {
            val old = ids[message.id]
            if (old == message) continue
            if (old != null) { root = remove(root, old); authors = authors.put(old.author, (authors[old.author] ?: 0) - 1) }
            root = put(root, message); ids = ids.put(message.id, message)
            authors = authors.put(message.author, (authors[message.author] ?: 0) + 1)
            changed.add(message.id); writes++
        }
        val visits = nodeVisits
        return if (changed.isEmpty()) this else IndexedChatMessages(root, ids, authors, Edits(edits, changed)).also {
            it.writes = writes; it.nodeVisits = visits
        }
    }
    private fun compare(a: ChatMessage, b: ChatMessage): Int = GameWire.ordinal(a.revision).compareTo(GameWire.ordinal(b.revision))
        .takeIf { it != 0 } ?: a.id.compareTo(b.id)
    private fun put(node: Node?, message: ChatMessage): Node {
        nodeVisits++
        node ?: return Node(message)
        if (compare(message, node.message) < 0) {
            val next = node.copy(left = put(node.left, message)); val left = next.left!!
            return if (left.priority < next.priority) left.copy(right = next.copy(left = left.right)) else next
        }
        val next = node.copy(right = put(node.right, message)); val right = next.right!!
        return if (right.priority < next.priority) right.copy(left = next.copy(right = right.left)) else next
    }
    private fun merge(a: Node?, b: Node?): Node? = when {
        a == null -> b; b == null -> a
        a.priority < b.priority -> a.copy(right = merge(a.right, b))
        else -> b.copy(left = merge(a, b.left))
    }
    private fun remove(node: Node?, message: ChatMessage): Node? {
        nodeVisits++
        node ?: return null
        val comparison = compare(message, node.message)
        return when { comparison < 0 -> node.copy(left = remove(node.left, message))
            comparison > 0 -> node.copy(right = remove(node.right, message))
            else -> merge(node.left, node.right) }
    }
}
