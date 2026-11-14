@file:OptIn(InternalComposeUiApi::class)
// Compose's own scene context and a layout node's owner are internal to Compose UI: read here only to put the right semantics
// owner back into the page's mirror (below), until Compose Multiplatform 1.13
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.asyncttwiz.android

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.scene.LocalComposeSceneContext
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * WEB-059 (found 2026-11-14 by the frame-rate worker): the page's semantics mirror (screen readers, the e2e tests and the
 * hover pointer, WEB-058, read it) follows what shows, also after a menu, popup or dialog closes.
 *
 * Compose Multiplatform 1.12's web mirror (its ComposeWebSemanticsListener) mirrors one semantics owner at a time: the last
 * one appended. Every popup and dialog is a layer of the scene with an owner of its own, appended as it opens; as it closes
 * the listener forgets it and mirrors nothing more, so the closed layer's elements stayed in the page and the screen's never
 * came back, until a reload. (Compose Multiplatform 1.13 mirrors every owner, upstream #3298.) Until then this puts the
 * owner under the closed layer back: the topmost layer still open, or the screen's own. It stands in for the scene context
 * the popups and dialogs make their layers with, noting each layer's owner and its closing.
 */
@Composable
internal fun SemanticsMirrorRepair(content: @Composable () -> Unit) {
    val scene = LocalComposeSceneContext.current
    val listener = scene?.platformContext?.semanticsOwnerListener
    if (scene == null || listener == null) {
        content()
        return
    }
    val repair = remember(scene, listener) { MirrorRepairContext(scene, listener) }
    CompositionLocalProvider(LocalComposeSceneContext provides repair) {
        content()
        // The screen's own semantics owner (a sibling drawing nothing, laid out at the window's corner)
        Spacer(OwnerNoted { repair.screen = it })
    }
}

/** The scene context the app's popups and dialogs get: [base]'s, with each layer's owner and closing noted. */
private class MirrorRepairContext(private val base: ComposeSceneContext,
    private val listener: PlatformContext.SemanticsOwnerListener) : ComposeSceneContext {
    /** The screen's own semantics owner, once its content is attached. */
    var screen: SemanticsOwner? = null
    /** The layers open, oldest first. */
    private val open = mutableListOf<MirrorRepairLayer>()

    override val platformContext: PlatformContext get() = base.platformContext

    override fun createLayer(density: Density, layoutDirection: LayoutDirection, focusable: Boolean,
        consumePointerInputOutside: Boolean): ComposeSceneLayer =
        MirrorRepairLayer(base.createLayer(density, layoutDirection, focusable, consumePointerInputOutside), this).also { open += it }

    /** [layer] has closed (the listener has forgotten its owner): the topmost open layer's owner, or the screen's, again. */
    fun closed(layer: MirrorRepairLayer) {
        if (!open.remove(layer)) return
        val back = open.lastOrNull { it.owner != null }?.owner ?: screen
        if (back == null) {
            AppLog.warn("semantics", "Semantics mirror: a layer closed before the screen's owner was known; nothing to mirror")
            return
        }
        listener.onSemanticsOwnerAppended(back)
        // A change of that owner has the listener sync the page's mirror to it (dropping the closed layer's elements)
        listener.onSemanticsChange(back)
        AppLog.info("semantics", "Semantics mirror: a layer closed, mirroring " +
            (if (back === screen) "the screen" else "the layer under it (${open.size} open)"))
    }
}

/** A popup's or dialog's layer: [base], its semantics owner noted as its content attaches and its closing told to [repair]. */
private class MirrorRepairLayer(private val base: ComposeSceneLayer, private val repair: MirrorRepairContext) :
    ComposeSceneLayer by base {
    var owner: SemanticsOwner? = null

    override fun setContent(parentCompositionContext: CompositionContext, content: @Composable () -> Unit) {
        base.setContent(parentCompositionContext) {
            content()
            Spacer(OwnerNoted { owner = it })
        }
    }

    override fun close() {
        base.close()
        repair.closed(this)
    }
}

/** A modifier telling [noted] the semantics owner of the layout it is attached in. */
private data class OwnerNoted(val noted: (SemanticsOwner) -> Unit) : ModifierNodeElement<OwnerNotedNode>() {
    override fun create() = OwnerNotedNode(noted)
    override fun update(node: OwnerNotedNode) { node.noted = noted }
}

private class OwnerNotedNode(var noted: (SemanticsOwner) -> Unit) : Modifier.Node() {
    override fun onAttach() { noted(requireOwner().semanticsOwner) }
}
