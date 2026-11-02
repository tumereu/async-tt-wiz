package arcanum.engine.view

import arcanum.engine.change.Change

/**
 * The view of the engine's own working state (D56: a [arcanum.engine.change.Recorder]'s copy, changed only by
 * `apply` and `revert`), kept until a change that can make it differ. [Layers.view] works it out at most once per
 * state; a host's state has no cache, so its view is always worked out afresh.
 */
internal class ViewCache(var view: View? = null) {
    /** [change] was applied or taken back. The view reads neither priority, the open decision nor the triggers waiting
     * to be put on the Stack; every other change may change it. */
    fun changed(change: Change) {
        if (change !is Change.Priority && change !is Change.PendingSet && change !is Change.TriggersWaiting) view = null
    }
}
