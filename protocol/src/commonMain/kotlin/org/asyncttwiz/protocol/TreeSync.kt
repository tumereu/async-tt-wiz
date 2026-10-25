package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Flat accepted-record codec for the shared GameplayTree on the existing I14 baseline and
 * GameRevision transport. Retained relational mutations/control records are the exporter input;
 * an incremental delivery window is never relabelled as complete branched history.
 */
@Serializable data class TreeSyncRecord(val kind: String, val id: String, val payload: JsonElement, val version: Int = 1)
@Serializable private data class TreeRedoQueue(val entries: List<GameplayRedoEntry>)
@Serializable data class GameplayTreeKey(val kind: String, val id: String)
@Serializable data class GameplayTreeDelta(val upserts: List<TreeSyncRecord>, val removals: List<GameplayTreeKey> = emptyList())

/** Branch presentation payloads are retained verbatim. GameplayTree owns the explicit heads,
 * lineage/control semantics; this codec never guesses them from presentation filtering.
 */
data class DownloadedTree(val tree: GameplayTree, val branches: Map<String, JsonObject>)

object TreeSync {
    fun branchIds(tree: GameplayTree): Set<String> = setOf(tree.initial.root.branchId) +
        tree.nodes.values.map { it.branchId } + tree.redoByAccount.values.flatten().map { it.mutation.branchId } +
        tree.branchHeads.keys + tree.branches.keys + tree.currentBranchId

    fun generation(tree: GameplayTree) = DownloadedTree(tree, branchIds(tree).associateWith { buildJsonObject { put("id", it) } })

    fun delta(before: GameplayTree, after: GameplayTree): GameplayTreeDelta {
        // Compare immutable records BEFORE encoding. Ordinary appends must not serialize every
        // retained offshoot/board/control merely to discover that their bytes did not change.
        val upserts = mutableListOf<TreeSyncRecord>()
        val removals = mutableListOf<GameplayTreeKey>()
        fun <T> changes(kind: String, old: Map<String, T>, next: Map<String, T>, encode: (T) -> JsonElement) {
            (old.keys - next.keys).forEach { removals.add(GameplayTreeKey(kind, it)) }
            next.forEach { (id, value) -> if (old[id] != value) upserts.add(TreeSyncRecord(kind, id, encode(value))) }
        }
        fun scalar(kind: String, old: String?, next: String?) = changes(kind,
            old?.let { mapOf("current" to it) }.orEmpty(), next?.let { mapOf("current" to it) }.orEmpty(), ::JsonPrimitive)
        changes("root", mapOf(before.initial.root.id to before.initial.copy(definitions = emptyList())),
            mapOf(after.initial.root.id to after.initial.copy(definitions = emptyList()))) { GameWire.json.encodeToJsonElement(it) }
        scalar("current", before.currentNodeId, after.currentNodeId)
        scalar("selected-branch", before.currentBranchId, after.currentBranchId)
        scalar("selection-control", before.selectionControlId, after.selectionControlId)
        changes("node", before.nodes, after.nodes) { GameWire.json.encodeToJsonElement(it) }
        changes("definition", before.initial.definitions.associateBy { it.digest }, after.initial.definitions.associateBy { it.digest }) { GameWire.json.encodeToJsonElement(it) }
        changes("redo", before.redoByAccount, after.redoByAccount) { GameWire.json.encodeToJsonElement(TreeRedoQueue(it)) }
        changes("removed-parent", before.removedParents, after.removedParents, ::JsonPrimitive)
        changes("branch-head", before.branchHeads, after.branchHeads, ::JsonPrimitive)
        changes("branch-lineage", before.branches, after.branches) { GameWire.json.encodeToJsonElement(it) }
        changes("branch", branchIds(before).associateWith { it }, branchIds(after).associateWith { it }) { buildJsonObject { put("id", it) } }
        return GameplayTreeDelta(upserts, removals)
    }

    fun apply(tree: DownloadedTree, delta: GameplayTreeDelta): DownloadedTree {
        require(delta.upserts.map { GameplayTreeKey(it.kind, it.id) }.distinct().size == delta.upserts.size)
        require(delta.removals.distinct().size == delta.removals.size)
        var next = tree.tree.copy()
        val branches = tree.branches.toMutableMap()
        val definitions = next.initial.definitions.associateByTo(linkedMapOf()) { it.digest }
        for (key in delta.removals) when (key.kind) {
            "node" -> { require(key.id in next.nodes); next = next.copy(nodes = next.nodes - key.id) }
            "definition" -> require(definitions.remove(key.id) != null)
            "redo" -> { require(key.id in next.redoByAccount); next = next.copy(redoByAccount = next.redoByAccount - key.id) }
            "removed-parent" -> { require(key.id in next.removedParents); next = next.copy(removedParents = next.removedParents - key.id) }
            "branch-head" -> { require(key.id in next.branchHeads); next = next.copy(branchHeads = next.branchHeads - key.id) }
            "branch-lineage" -> { require(key.id in next.branches); next = next.copy(branches = next.branches - key.id) }
            "branch" -> require(branches.remove(key.id) != null)
            "selection-control" -> { require(key.id == "current" && next.selectionControlId != null); next = next.copy(selectionControlId = null) }
            else -> require(obsoleteControl(key.kind))
        }
        for (record in delta.upserts) {
            require(record.version == 1 && GameplayTreeKey(record.kind, record.id) !in delta.removals)
            when (record.kind) {
                "root" -> {
                    val root = GameWire.json.decodeFromJsonElement<PlayState>(record.payload)
                    require(root.root.id == record.id && root.definitions.isEmpty())
                    next = next.copy(initial = root)
                }
                "definition" -> {
                    val definition = GameWire.json.decodeFromJsonElement<SubmittedDefinition>(record.payload)
                    require(definition.digest == record.id); definitions[record.id] = definition
                }
                "node" -> {
                    val node = GameWire.json.decodeFromJsonElement<GameplayMutation>(record.payload)
                    require(node.id == record.id); next = next.copy(nodes = next.nodes + (record.id to node))
                }
                "current", "selected-branch", "selection-control" -> {
                    require(record.id == "current")
                    val value = record.payload.jsonPrimitive.content
                    next = when (record.kind) {
                        "current" -> next.copy(currentNodeId = value)
                        "selected-branch" -> next.copy(currentBranchId = value)
                        else -> next.copy(selectionControlId = value)
                    }
                }
                "redo" -> next = next.copy(redoByAccount = next.redoByAccount + (record.id to GameWire.json.decodeFromJsonElement<TreeRedoQueue>(record.payload).entries))
                "removed-parent" -> next = next.copy(removedParents = next.removedParents + (record.id to record.payload.jsonPrimitive.content))
                "branch-head" -> next = next.copy(branchHeads = next.branchHeads + (record.id to record.payload.jsonPrimitive.content))
                "branch-lineage" -> {
                    val branch = GameWire.json.decodeFromJsonElement<GameplayBranch>(record.payload)
                    require(branch.id == record.id); next = next.copy(branches = next.branches + (record.id to branch))
                }
                "branch" -> branches[record.id] = record.payload.jsonObject
                else -> require(obsoleteControl(record.kind))
            }
        }
        next = next.copy(initial = next.initial.copy(definitions = definitions.values.sortedBy { it.digest }))
        require(next.initial.gameId == tree.tree.initial.gameId && next.initial.id == tree.tree.initial.id)
        return DownloadedTree(next, branches).also { validate(it, tree.tree) }
    }

    /** No recursive graph or per-node board snapshots. Definitions and common ancestors occur once. */
    fun records(generation: DownloadedTree): Sequence<TreeSyncRecord> = sequence {
        val tree = generation.tree
        yield(TreeSyncRecord("root", tree.initial.root.id, GameWire.json.encodeToJsonElement(tree.initial.copy(definitions = emptyList()))))
        yield(TreeSyncRecord("current", "current", JsonPrimitive(tree.currentNodeId)))
        yield(TreeSyncRecord("selected-branch", "current", JsonPrimitive(tree.currentBranchId)))
        tree.selectionControlId?.let { yield(TreeSyncRecord("selection-control", "current", JsonPrimitive(it))) }
        tree.branchHeads.toSortedMap().forEach { (id, head) -> yield(TreeSyncRecord("branch-head", id, JsonPrimitive(head))) }
        tree.branches.toSortedMap().forEach { (id, branch) -> yield(TreeSyncRecord("branch-lineage", id, GameWire.json.encodeToJsonElement(branch))) }
        tree.initial.definitions.sortedBy { it.digest }.forEach {
            yield(TreeSyncRecord("definition", it.digest, GameWire.json.encodeToJsonElement(it)))
        }
        tree.nodes.toSortedMap().forEach { (id, node) -> yield(TreeSyncRecord("node", id, GameWire.json.encodeToJsonElement(node))) }
        tree.redoByAccount.toSortedMap().forEach { (account, queue) ->
            yield(TreeSyncRecord("redo", account, GameWire.json.encodeToJsonElement(TreeRedoQueue(queue))))
        }
        tree.removedParents.toSortedMap().forEach { (id, parent) -> yield(TreeSyncRecord("removed-parent", id, JsonPrimitive(parent))) }
        generation.branches.toSortedMap().forEach { (id, metadata) -> yield(TreeSyncRecord("branch", id, metadata)) }
    }

    /** Stream flat records into the SAME shared immutable tree. Full coverage is a server-exporter
     * obligation; closed topology, byte hashes/counts and replay cannot prove an omitted offshoot.
     */
    fun decode(records: Sequence<TreeSyncRecord>, gameId: String, playId: String): DownloadedTree {
        var initial: PlayState? = null
        var current: String? = null
        var selectedBranch: String? = null
        var selectionControl: String? = null
        val heads = linkedMapOf<String, String>()
        val lineage = linkedMapOf<String, GameplayBranch>()
        val nodes = linkedMapOf<String, GameplayMutation>()
        val definitions = linkedMapOf<String, SubmittedDefinition>()
        val redo = linkedMapOf<String, List<GameplayRedoEntry>>()
        val removed = linkedMapOf<String, String>()
        val branches = linkedMapOf<String, JsonObject>()
        val seen = mutableSetOf<Pair<String, String>>()
        for (record in records) {
            require(record.version == 1 && seen.add(record.kind to record.id)) { "Duplicate/unsupported tree record" }
            when (record.kind) {
                "root" -> {
                    require(initial == null)
                    initial = GameWire.json.decodeFromJsonElement<PlayState>(record.payload)
                    require(initial.root.id == record.id && initial.definitions.isEmpty())
                }
                "current" -> { require(record.id == "current"); current = record.payload.jsonPrimitive.content }
                "selected-branch" -> { require(record.id == "current"); selectedBranch = record.payload.jsonPrimitive.content }
                "selection-control" -> { require(record.id == "current"); selectionControl = record.payload.jsonPrimitive.content }
                "branch-head" -> heads[record.id] = record.payload.jsonPrimitive.content
                "branch-lineage" -> lineage[record.id] = GameWire.json.decodeFromJsonElement<GameplayBranch>(record.payload).also { require(it.id == record.id) }
                // Compatibility with persisted controls and older cached/downloaded trees only.
                "archived", "archive-control", "archive-record" -> Unit
                "definition" -> {
                    val definition = GameWire.json.decodeFromJsonElement<SubmittedDefinition>(record.payload)
                    require(definition.digest == record.id && DeckValidation.definition(definition.definition) &&
                        DeckJson.digest(definition.definition) == definition.digest)
                    definitions[record.id] = definition
                }
                "node" -> {
                    val node = GameWire.json.decodeFromJsonElement<GameplayMutation>(record.payload)
                    require(node.id == record.id); nodes[record.id] = node
                }
                "redo" -> redo[record.id] = GameWire.json.decodeFromJsonElement<TreeRedoQueue>(record.payload).entries
                "removed-parent" -> removed[record.id] = record.payload.jsonPrimitive.content
                "branch" -> branches[record.id] = record.payload.jsonObject
                else -> error("Unsupported tree record") // missing semantics are incomplete, not silently dropped
            }
        }
        val root = requireNotNull(initial).copy(definitions = definitions.values.sortedBy { it.digest })
        require(root.gameId == gameId && root.id == playId)
        val result = DownloadedTree(GameplayTree(root, nodes, requireNotNull(current), redo, removed, lineage,
            selectedBranch ?: nodes[current]?.branchId ?: root.root.branchId,
            if (heads.isEmpty()) mapOf(root.root.branchId to root.root.id) else heads, selectionControl), branches)
        validate(result)
        return result
    }

    /** Iterative full retained-tree validation/replay. No Undo-only ceiling, recursive ancestry or
     * cached board per node. A linear path retains one working board; forks share their parent state.
     * Call off the UI thread, just like cold GameplayTree.stateAt().
     */
    fun validate(generation: DownloadedTree) = validate(generation, null)

    private fun validate(generation: DownloadedTree, previous: GameplayTree?) {
        val tree = generation.tree
        val root = tree.initial
        require(root.engineVersion == 1 && root.readFormat == 1 && root.board.schemaVersion == 1 && root.root.parentId == null &&
            root.currentNodeId == root.root.id && root.currentGroup == null)
        require(listOf(root.gameId, root.id, root.root.id, root.root.branchId).all(DeckValidation::uuid))
        val players = root.board.players.map { it.accountId }.toSet()
        require(players.size == 2 && root.board.players.size == 2 && root.board.turn.accountId in players)
        val definitions = root.definitions.associateBy { it.digest }
        require(definitions.size == root.definitions.size && root.definitions.all {
            DeckValidation.definition(it.definition) && DeckJson.digest(it.definition) == it.digest })
        val cards = root.board.cards.associateBy { it.id }
        val containers = root.board.containers.associateBy { it.id }
        require(cards.size == root.board.cards.size && containers.size == root.board.containers.size)
        require(root.board.cards.all { card -> card.owner in players &&
            definitions[card.definition]?.definition?.faces?.any { it.faceId == card.faceId } == true &&
            containers[card.containerId]?.cards?.contains(card.id) == true })
        val members = root.board.containers.flatMap { it.cards }
        require(members.size == cards.size && members.toSet() == cards.keys)
        require(root.root.id !in tree.nodes && root.root.id !in tree.removedParents)
        require(tree.currentNodeId == root.root.id || tree.currentNodeId in tree.nodes)
        require(generation.branches.keys == branchIds(tree)) { "Missing retained branch metadata" }
        require(generation.branches.keys.all(DeckValidation::uuid))
        require(tree.currentBranchId in generation.branches)
        require(tree.branchHeads.all { (id, head) -> id in generation.branches && tree.resolveCursor(head) != null })
        tree.selectionControlId?.let { require(DeckValidation.uuid(it)) }
        require(tree.nodes.all { (id, node) -> id == node.id && node.author in players &&
            listOf(id, node.parentId, node.branchId, node.requestId).all(DeckValidation::uuid) &&
            node.schemaVersion in 1..2 && tree.referencesMatch(node) })
        val children = tree.nodes.values.groupBy { it.parentId }
        val pending = ArrayDeque<Pair<GameplayMutation, PlayState?>>()
        children[root.root.id].orEmpty().forEach { pending.add(it to root) }
        val visited = mutableSetOf<String>()
        val unchanged = mutableSetOf<String>()
        if (previous?.initial == root) unchanged.add(root.root.id)
        val changed = mutableListOf<GameplayMutation>()
        while (pending.isNotEmpty()) {
            val (node, parent) = pending.removeLast()
            require(visited.add(node.id)) { "Cyclic retained topology" }
            val same = node.parentId in unchanged && previous?.nodes?.get(node.id) == node
            if (same) unchanged.add(node.id) else changed.add(node)
            val state = if (previous != null) null else requireNotNull(tree.replay(requireNotNull(parent), node)) { "Unreplayable retained node" }
            if (node.id == tree.currentNodeId && state != null) tree.checkpoint(state)
            children[node.id].orEmpty().forEach { pending.add(it to state) }
        }
        require(visited.size == tree.nodes.size) { "Missing/disconnected retained ancestry" }
        if (previous != null) {
            tree.inheritMaterialization(previous, unchanged)
            // Only changed nodes and descendants need replay. Unchanged accepted ancestry has
            // already passed this same validation; never serialize or replay it per revision.
            for (node in changed) requireNotNull(tree.stateAt(node.id)) { "Unreplayable retained node" }
            requireNotNull(tree.stateAt(tree.currentNodeId))
        }
        require(tree.removedParents.keys.none { it in tree.nodes })
        require(tree.removedParents.all { (id, parent) -> DeckValidation.uuid(id) && DeckValidation.uuid(parent) })
        // Resolve all removal routes in linear work, including chains across multiple pruned edits.
        val resolved = (tree.nodes.keys + root.root.id).toMutableSet()
        for (id in tree.removedParents.keys) {
            val route = mutableSetOf<String>()
            var at = id
            while (at !in resolved) {
                require(route.add(at)) { "Cyclic removed-parent metadata" }
                at = requireNotNull(tree.removedParents[at]) { "Incomplete removed-parent metadata" }
            }
            resolved.addAll(route)
        }
        require(tree.redoByAccount.keys.all { it in players })
        require(tree.redoByAccount.all { (account, queue) -> queue.all { entry -> entry.mutation.author == account &&
            (entry.successorId == null || entry.successorId in resolved) && entry.otherChildIds.all { it in resolved } } })
        val detached = tree.redoByAccount.values.flatten().map { it.mutation }
        require(detached.map { it.id }.distinct().size == detached.size)
        val referencePayloads = tree.nodes + detached.associateBy { it.id }
        require(detached.all { node -> node.id !in tree.nodes && node.id in tree.removedParents && node.author in players &&
            listOf(node.id, node.parentId, node.branchId, node.requestId).all(DeckValidation::uuid) &&
            node.schemaVersion in 1..2 && node.parentId in resolved && node.references.all {
                referencePayloads[it.nodeId]?.transition == it.transition } })
    }

    private fun obsoleteControl(kind: String) = kind in setOf("archived", "archive-control", "archive-record")

    fun digest(bytes: ByteArray) = "sha256:" + sha256(bytes)
}
