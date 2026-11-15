@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

/**
 * WEB-058 (owner, desktop playtest 2026-11-14): the mouse shows the hand over everything that can be clicked (every control,
 * every card, the table's cards drawn on its canvas included) and the text cursor over text fields, as web pages do.
 *
 * One place for all of them: the screens' semantics, which the page mirrors into its document for screen readers
 * (ComposeViewport's accessibility tree: one element per node, at the node's place on the page, with its role), say what
 * is under the mouse. The smallest clickable element (a button, link, tab, checkbox, switch, radio button, menu item or
 * option: Compose's click semantics, which every clickable, toggleable and selectable control, every Material button and
 * the table's cards have) or text field there decides; anything else leaves Compose's own cursor. The app's
 * canvas carries the choice as a class, whose rule (put beside it in its shadow root) wins over the cursor Compose sets.
 * Worked out once a frame while the mouse moves, and after a press or release (what is under it may change then); a touch
 * or a pen has no cursor.
 */
internal fun jsHoverCursor(): Unit = js("""{
    const clickable = '[role=button],[role=link],[role=tab],[role=checkbox],[role=switch],[role=radio],[role=menuitem],' +
        '[role=menuitemcheckbox],[role=menuitemradio],[role=option]';
    const editable = '[role=textbox],[role=searchbox],[contenteditable=true],input,textarea';
    const either = clickable + ',' + editable;
    // The semantics tree and the canvas share a root (ComposeViewport's shadow root): the cursor's rule goes there, where
    // the page's own stylesheet doesn't reach
    let tree = null, canvas = null;
    const find = () => {
        if (tree && tree.isConnected && canvas && canvas.isConnected) return true;
        tree = null; canvas = null;
        const roots = [document];
        for (const host of document.body.querySelectorAll('*')) if (host.shadowRoot) roots.push(host.shadowRoot);
        for (const root of roots) {
            const found = root.getElementById ? root.getElementById('cmp_a11y_root') : root.querySelector('#cmp_a11y_root');
            if (!found) continue;
            tree = found;
            canvas = root.querySelector('canvas');
            if (!root.querySelector('style[data-atw-cursor]')) {
                const style = document.createElement('style');
                style.setAttribute('data-atw-cursor', '');
                style.textContent = 'canvas.atw-hand { cursor: pointer !important; } canvas.atw-text { cursor: text !important; }';
                (root === document ? document.head : root).appendChild(style);
            }
            break;
        }
        return tree !== null && canvas !== null;
    };
    let x = 0, y = 0, mouse = false, queued = false, shown = '';
    const show = (kind) => {
        if (kind === shown || canvas === null) return;
        canvas.classList.toggle('atw-hand', kind === 'hand');
        canvas.classList.toggle('atw-text', kind === 'text');
        shown = kind;
    };
    const decide = () => {
        queued = false;
        if (!find()) return;
        if (!mouse) { show(''); return; }
        let best = null, area = Infinity;
        for (const e of tree.querySelectorAll(either)) {
            if (e.getAttribute('aria-disabled') === 'true') continue;
            const r = e.getBoundingClientRect();
            if (x < r.left || x >= r.right || y < r.top || y >= r.bottom) continue;
            const a = r.width * r.height;
            if (a <= area) { best = e; area = a; }
        }
        show(best === null ? '' : best.matches(editable) ? 'text' : 'hand');
    };
    const later = (event) => {
        mouse = event.pointerType === 'mouse';
        x = event.clientX; y = event.clientY;
        // The browser's own frame request where the frame times' meter wrapped it (WEB-057: the cursor is no drawing)
        if (!queued) { queued = true; (globalThis.atwFrameRequest || requestAnimationFrame)(decide); }
    };
    for (const kind of ['pointermove', 'pointerdown', 'pointerup']) window.addEventListener(kind, later, { capture: true, passive: true });
    document.documentElement.addEventListener('pointerleave', () => { mouse = false; if (find()) show(''); });
}""")
