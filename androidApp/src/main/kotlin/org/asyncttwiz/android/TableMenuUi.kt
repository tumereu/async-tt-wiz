package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal val MenuInk = Color(0xff263e39)
internal val MenuBorder = Color(0xffbaccc7)

/** Semantic names, not font-dependent unicode. Values and P/T arithmetic remain actual text. */
internal fun menuIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.startsWith("close player") || n == "close" || n.startsWith("close card") -> Icons.Outlined.Close
        n == "cancel" -> Icons.Outlined.Close
        n == "dismiss" -> Icons.Outlined.VisibilityOff
        n.startsWith("card actions") -> Icons.Outlined.MoreHoriz
        n.startsWith("increase") -> Icons.Outlined.Add
        n.startsWith("decrease") -> Icons.Outlined.Remove
        n == "declare blockers" -> Icons.Outlined.Shield
        "other side" in n || "reveal" in n -> Icons.Outlined.Visibility
        "inspect" in n || "view" in n || "contents" in n -> Icons.Outlined.ZoomIn
        "face down" in n -> Icons.Outlined.Flip
        "face up" in n -> Icons.Outlined.FlipToFront
        "printed face" in n -> Icons.Outlined.FlipCameraAndroid
        "reset corner" in n -> Icons.Outlined.SettingsBackupRestore
        "note" in n -> Icons.AutoMirrored.Outlined.StickyNote2
        n.startsWith("owner") -> Icons.Outlined.PersonOutline
        "bottom" in n -> Icons.Outlined.VerticalAlignBottom
        "top" in n -> Icons.Outlined.VerticalAlignTop
        "graveyard" in n -> Icons.Outlined.Inventory2
        "exile" in n -> Icons.Outlined.Block
        "clone" in n -> Icons.Outlined.ControlPointDuplicate
        "unlink" in n || n == "links" || "remove link" in n -> Icons.Outlined.LinkOff
        "link" in n -> Icons.Outlined.Link
        "ping" in n -> Icons.Outlined.AdsClick
        "remove stack" in n || "remove this effect" in n || "close combat" in n || "advance + close" in n -> Icons.Outlined.LayersClear
        "stack" in n -> Icons.Outlined.Layers
        "remove from combat" in n -> Icons.Outlined.RemoveCircleOutline
        "declare" in n -> Icons.Outlined.Gavel
        "remove token" in n -> Icons.Outlined.DeleteOutline
        "untap" in n -> Icons.Outlined.Rotate90DegreesCcw
        "tap" == n || "tap selected" in n -> Icons.Outlined.Rotate90DegreesCw
        "select" in n -> Icons.Outlined.CheckBoxOutlineBlank
        "battlefield" in n -> Icons.Outlined.TableRows
        "shuffle" in n || "return hand" in n -> Icons.Outlined.Shuffle
        n == "draw" || "draw one" in n || "draw n" in n || "hand" in n -> Icons.Outlined.Download
        "choose" in n || "beginning of combat" in n || "suggest my loss" in n -> Icons.Outlined.Flag
        "next step" in n -> Icons.Outlined.SkipNext
        "end turn" in n || "end opponent" in n -> Icons.AutoMirrored.Outlined.LastPage
        "upkeep" in n -> Icons.Outlined.HourglassTop
        "precombat" in n -> Icons.Outlined.LooksOne
        "postcombat" in n -> Icons.Outlined.LooksTwo
        "blockers" in n -> Icons.Outlined.Shield
        "damage" in n -> Icons.Outlined.LocalFireDepartment
        "end of combat" in n -> Icons.Outlined.OutlinedFlag
        "end step" in n -> Icons.Outlined.Nightlight
        "cleanup" in n -> Icons.Outlined.CleaningServices
        "life" in n -> Icons.Outlined.FavoriteBorder
        "increase" in n || n == "+" -> Icons.Outlined.Add
        "decrease" in n || n == "−" -> Icons.Outlined.Remove
        "create combat" in n -> Icons.Outlined.AddBox
        "blank" in n -> Icons.Outlined.CropPortrait
        "token" in n -> Icons.Outlined.LibraryAdd
        "die" in n -> Icons.Outlined.Casino
        "coin" in n -> Icons.Outlined.Paid
        "win" in n -> Icons.Outlined.EmojiEvents
        "suggest draw" in n -> Icons.Outlined.Handshake
        "accept" in n || n == "set" -> Icons.Outlined.Check
        "more" in n -> Icons.Outlined.MoreHoriz
        "source" in n -> Icons.AutoMirrored.Outlined.OpenInNew
        "enlarge artwork" in n -> Icons.Outlined.ZoomIn
        "fit artwork" in n -> Icons.Outlined.FitScreen
        "previous face" in n -> Icons.Outlined.ChevronLeft
        "next face" in n -> Icons.Outlined.ChevronRight
        else -> Icons.AutoMirrored.Outlined.DriveFileMove
    }
}

@Composable
internal fun MenuIcon(name: String, modifier: Modifier = Modifier, tint: Color = MenuInk) {
    if ("declare" in name.lowercase() && name.lowercase() != "declare blockers") Box(modifier.size(24.dp)) {
        Icon(painterResource(R.drawable.swords_outlined), name, Modifier.fillMaxSize(), tint = tint)
        if ("+ tap" in name.lowercase()) Icon(Icons.Outlined.Rotate90DegreesCw, null,
            Modifier.size(10.dp).align(Alignment.BottomEnd), tint = tint)
    } else Icon(menuIcon(name), contentDescription = name, modifier = modifier.size(24.dp), tint = tint)
}

/** Bounded context panel: wrap content, never reserve an empty fullscreen sheet. */
@Composable
internal fun TableMenuPanel(onDismiss: () -> Unit, width: Dp = 384.dp, title: String? = null,
    border: Color? = null, titleDescription: String? = title, titleColor: Color? = null,
    gap: Dp = 4.dp, contentPadding: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MenuWindowDimming()
        Surface(Modifier.widthIn(max = width).fillMaxWidth().padding(horizontal = 8.dp).heightIn(max = 820.dp)
            .semantics { titleDescription?.let { paneTitle = it } },
            color = Color.White, contentColor = MenuInk, shape = RoundedCornerShape(10.dp),
            border = border?.let { BorderStroke(2.dp, it) }, shadowElevation = 12.dp) {
            Column(Modifier.padding(contentPadding).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                if (title != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (titleColor != null) {
                        Box(Modifier.size(24.dp).background(titleColor))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    ManualActionSquare("Close", "", bordered = false, onClick = onDismiss)
                }
                content()
            }
        }
    }
}

@Composable
internal fun MenuWindowDimming() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(.28f) }
}

@Composable
internal fun MenuButton(name: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    fill: Color? = null, onClick: () -> Unit) {
    ManualActionSquare(name, "", enabled, fill = fill, modifier = modifier, labelled = true, onClick = onClick)
}

internal data class MenuControl(val name: String, val enabled: Boolean = true, val selected: Boolean = false,
    val color: Color? = null, val click: () -> Unit)

@Composable
internal fun MenuControl.Square() = ManualActionSquare(name, "", enabled, selected = selected, iconTint = color, onClick = click)
