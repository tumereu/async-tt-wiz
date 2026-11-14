package org.asyncttwiz.android

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Whether the app is drawn in the desktop layout (WEB-013). */
@Composable
fun desktopLayout(): Boolean = LocalAppLayout.current == AppLayout.Desktop

/**
 * The desktop layout's screens outside the table (docs/specs/web-client.md §7.1, §7.4, §7.5, §7.7; wireframes D7, D8, D10):
 * the same screens as the phone's, arranged for a wide window.
 */
object DesktopScreens {
    /** WEB-037: the permanent side menu's width (the phone's landscape side area is 220). */
    val SideMenuWidth = 280.dp
    /** WEB-038: the readable width of lists (games, contacts, pods). */
    val ListMaxWidth = 980.dp
    /** WEB-043: Settings' two columns of groups. */
    val SettingsMaxWidth = 1120.dp
    /** WEB-038: the narrowest tile of the decks and cubes grids. */
    val TileMinWidth = 320.dp
    /** WEB-039: the deck and cube editor's search pane. */
    val SearchPaneWidth = 520.dp
    /** WEB-040: the Search rail the hidden search pane folds into. */
    val SearchRailWidth = 44.dp
    /** WEB-042: the narrowest column of Stats' graphs and history. */
    val StatsColumnMinWidth = 440.dp
    /** The gutter beside a desktop screen's content. */
    val Gutter = 24.dp
}

/** WEB-038: in the desktop layout a list or form keeps a readable width at the left of its room, with a wider gutter; on the
 * phone layout nothing changes. */
@Composable
fun Modifier.desktopReadable(max: Dp = DesktopScreens.ListMaxWidth): Modifier =
    if (desktopLayout()) this.widthIn(max = max).padding(horizontal = DesktopScreens.Gutter) else this

/** WEB-042: how many columns of at least [min] fit [width]: at least one. */
fun desktopColumns(width: Dp, min: Dp): Int = (width / min).toInt().coerceAtLeast(1)
