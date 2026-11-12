package arcanum.engine

import arcanum.engine.state.GameState

/**
 * For a host's tests and measurements: callbacks counting the engine's heavy work. They change no result; each is one
 * for the whole process, so set them only in tests or while measuring.
 */
object EngineProbes {
    /**
     * For a host's tests and measurements (changes no result): called on the calling thread each time the layers are
     * worked out in full, whether for [Engine.view], [Inspect.explain] or inside another call ([Engine.perform], [Engine.options], [Host.visible], …); a
     * view the engine kept or was given isn't counted. Null (the default): nothing is called. One for the whole process:
     * set it only in tests or while measuring.
     */
    @kotlin.concurrent.Volatile
    var layersWorkedOut: (() -> Unit)? = null

    /**
     * For a host's tests and measurements (changes no result): called on the calling thread each time a state is copied
     * ([GameState.deepCopy]), by the engine (its working copy in [Engine.perform], trial casts in [Engine.options], …) or by a host.
     * Null (the default): nothing is called. One for the whole process: set it only in tests or while measuring.
     */
    @kotlin.concurrent.Volatile
    var statesCopied: (() -> Unit)? = null

    /**
     * For a host's tests and measurements (changes no result): called on the calling thread each time the engine searches
     * what a player may do: each [Engine.options] call, and each search of a pass check ([Host.passCheck], [Host.shouldPassFor]) that looks
     * for something to do. Null (the default): nothing is called. One for the whole process: set it only in tests or
     * while measuring.
     */
    @kotlin.concurrent.Volatile
    var optionsSearched: (() -> Unit)? = null
}
