package arcanum.engine.view

import arcanum.engine.change.Change
import arcanum.engine.state.StackItem

/**
 * The view of the engine's own working state (D56: a [arcanum.engine.change.Recorder]'s copy, changed only by
 * `apply` and `revert`), kept until a change that can make it differ. [Layers.view] works it out at most once per
 * state; a host's state has no cache, so its view is always worked out afresh.
 */
internal class ViewCache(view: View? = null) {
    var view: View? = view
        private set

    /** Whether working out [view] read the tagged events (D33) or last known information from them. A view given from
     * outside is taken to have read them. */
    private var readsLog = true

    /** The view of this state with [item] put on the Stack provisionally ([arcanum.engine.casting.Plan]): kept until any
     * change. */
    private var provisionalItem: StackItem? = null
    private var provisionalView: View? = null

    fun provisional(item: StackItem, compute: () -> View): View {
        provisionalView?.let { if (provisionalItem == item) return it }
        return compute().also {
            provisionalItem = item
            provisionalView = it
        }
    }

    fun set(view: View, readsLog: Boolean) {
        this.view = view
        this.readsLog = readsLog
    }

    /**
     * [change] was applied or taken back. The view reads neither priority, the open decision, the triggers waiting to be
     * put on the Stack nor mana pools; an event that changes nothing else (mana, a damage event, a game event) only adds
     * to the tagged events, which this view may not have read. Every other change may change it.
     */
    fun changed(change: Change) {
        provisionalItem = null
        provisionalView = null
        val keeps = change is Change.Priority || change is Change.PendingSet || change is Change.TriggersWaiting ||
            (!readsLog && (change is Change.ManaPool || change is Change.Damage || change is Change.Happened))
        if (!keeps) view = null
    }
}

/** What working out a view has read, beyond the objects and their characteristics. Shared by the boards of one run. */
internal class ViewReads {
    /** The tagged events ("this turn" questions) or last known information found in them. */
    var log = false
}
