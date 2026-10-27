package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val ScreenBackground = Color(0xfff7f8f9)
internal val ScreenBorder = Color(0xffcdd3dc)

// The extended Material set predates Symbols' crossed-swords glyph. Keep the same game motif,
// rather than substituting a video-game controller, alongside the standard outlined icons.
private val GameSwords by lazy {
    ImageVector.Builder("Games", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(3f, 3f); lineTo(7f, 4f); lineTo(19f, 17f); lineTo(17f, 19f); lineTo(4f, 7f); close()
            moveTo(21f, 3f); lineTo(17f, 4f); lineTo(5f, 17f); lineTo(7f, 19f); lineTo(20f, 7f); close()
            moveTo(3f, 15f); lineTo(9f, 21f); moveTo(15f, 21f); lineTo(21f, 15f)
            moveTo(3f, 21f); lineTo(6f, 18f); moveTo(18f, 18f); lineTo(21f, 21f)
        }
    }.build()
}

/** Non-table chrome deliberately has its own density; table controls keep their own theme. */
@Composable
internal fun ScreenTheme(content: @Composable () -> Unit) {
    fun type(size: Int, line: Int, bold: Boolean = false) = TextStyle(fontSize = size.sp, lineHeight = line.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xff087f7b), onPrimary = Color.White,
        primaryContainer = Color(0xffe1f1ec), onPrimaryContainer = Color(0xff203940),
        secondaryContainer = Color(0xffe1f1ec), background = ScreenBackground, surface = Color.White,
        surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceVariant = ScreenBackground,
        onSurface = Color(0xff20323c), onBackground = Color(0xff20323c), onSurfaceVariant = Color(0xff687d89),
        outline = Color(0xffb8ccca), outlineVariant = ScreenBorder, error = Color(0xffc62828)),
        typography = Typography(headlineSmall = type(24, 29, true), titleLarge = type(20, 24, true),
            titleMedium = type(18, 22, true), titleSmall = type(16, 20, true),
            bodyLarge = type(16, 20), bodyMedium = type(14, 20), bodySmall = type(12, 16),
            labelLarge = type(14, 18), labelMedium = type(12, 16, true), labelSmall = type(11, 14)),
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(8.dp)),
        content = content)
}

@Composable
internal fun ScreenButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled, shape = RoundedCornerShape(6.dp), colors = colors,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), content = content)
}

@Composable
internal fun ScreenOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape = RoundedCornerShape(6.dp), colors = colors,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenIconButton(description: String, kind: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    TooltipBox(modifier = modifier, positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } }, state = rememberTooltipState()) {
    if (kind == "close") IconButton(onClick, Modifier.size(48.dp), enabled) { EntryIcon(description, kind) }
    else OutlinedIconButton(onClick, Modifier.size(48.dp), enabled, shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (danger) Color(0xffe1b7b7) else MaterialTheme.colorScheme.outline),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor =
            if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)) {
        EntryIcon(description, kind)
    }
    }
}

// A shell-wide catalog task takes precedence over a screen's shorter-lived busy indicator.
internal val LocalBusySuppressed = compositionLocalOf { false }

/** Busy feedback is always an overlay: showing it never remeasures or moves screen content. */
@Composable
internal fun ScreenBusyOverlay(message: String?, progress: org.asyncttwiz.storage.CatalogProgress? = null,
    modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxScope.() -> Unit) {
    val suppressed = LocalBusySuppressed.current
    Box(modifier) {
        content()
        FloatingBusyPill(message?.takeUnless { suppressed }, progress, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun FloatingBusyPill(message: String?, progress: org.asyncttwiz.storage.CatalogProgress? = null,
    modifier: Modifier = Modifier) {
    var lastMessage by remember { mutableStateOf(message.orEmpty()) }
    var lastProgress by remember { mutableStateOf(progress) }
    if (message != null) SideEffect { lastMessage = message; lastProgress = progress }
    AnimatedVisibility(message != null, modifier.padding(16.dp).widthIn(max = 540.dp),
        enter = fadeIn(tween(250, easing = FastOutSlowInEasing)), exit = fadeOut(tween(250))) {
        Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.testTag("floating-busy-pill").semantics { liveRegion = LiveRegionMode.Polite }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (lastProgress?.total?.let { it > 0 } != true) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.inversePrimary, strokeWidth = 2.dp)
                    }
                    Text(lastMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                }
                lastProgress?.let { value -> CatalogProgressBar(value, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
internal fun CatalogProgressBar(progress: org.asyncttwiz.storage.CatalogProgress, modifier: Modifier = Modifier) {
    val total = progress.total?.takeIf { it > 0 && progress.phase != org.asyncttwiz.storage.CatalogPhase.FINISHING }
    if (total == null) LinearProgressIndicator(modifier)
    else LinearProgressIndicator(progress = { (progress.done.toFloat() / total).coerceIn(0f, 1f) }, modifier = modifier)
}

@Composable
internal fun DeckThumbnail(reference: org.asyncttwiz.storage.ArtworkReference?, name: String,
    width: Dp = 28.dp, height: Dp = 40.dp) {
    Surface(Modifier.size(width, height), color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(2.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        if (reference?.printing != null || reference?.customUrl != null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(screenIcon("cover"), name, Modifier.size(minOf(width, 20.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                CardArtwork(reference, "", width.value.toInt(), height.value.toInt(), Modifier.fillMaxSize())
            }
        } else Box(contentAlignment = Alignment.Center) {
            Icon(screenIcon("cover"), name, Modifier.size(minOf(width, 20.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ScreenHeader(title: String, navigation: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (navigation != null) IconButton(onClick = navigation) { EntryIcon("Open navigation", "menu") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        actions()
    }
}

/** A 48dp field, with external labels, matching the compact mock forms without scaling text. */
@Composable
internal fun ScreenTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, singleLine: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None, trailingIcon: (@Composable () -> Unit)? = null,
    placeholder: String? = null, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge, keyboardActions: KeyboardActions = KeyboardActions.Default) {
    BasicTextField(value, onValueChange, modifier.heightIn(min = 48.dp), enabled = enabled, singleLine = singleLine,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
        maxLines = maxLines, textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { field -> ScreenTextFieldDecoration(value.isEmpty(), singleLine, placeholder, trailingIcon, field) })
}

/** [ScreenTextField] with selection control, e.g. for a dialog field that opens with its text selected. */
@Composable
internal fun ScreenTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, singleLine: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge) {
    BasicTextField(value, onValueChange, modifier.heightIn(min = 48.dp), enabled = enabled, singleLine = singleLine,
        keyboardOptions = keyboardOptions, maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { field -> ScreenTextFieldDecoration(value.text.isEmpty(), singleLine, null, null, field) })
}

@Composable
private fun ScreenTextFieldDecoration(empty: Boolean, singleLine: Boolean, placeholder: String?,
    trailingIcon: (@Composable () -> Unit)?, field: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, ScreenBorder), color = Color.White) {
        Row(Modifier.heightIn(min = 48.dp).padding(start = 12.dp, end = if (trailingIcon == null) 12.dp else 0.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (empty && placeholder != null) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                field()
            }
            trailingIcon?.invoke()
        }
    }
}

@Composable
internal fun ScreenDialog(dismiss: () -> Unit, modifier: Modifier = Modifier, busyMessage: String? = null,
    error: String? = null, dismissError: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TrackGameDialogInput()
        ScreenTheme {
            Surface(modifier.padding(16.dp).widthIn(max = 380.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, ScreenBorder)) {
                Box {
                    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
                        FloatingBusyPill(busyMessage)
                        FloatingTransientError(error, dismissError)
                    }
                }
            }
        }
    }
}

internal fun screenIcon(kind: String): ImageVector = when (kind) {
    "menu" -> Icons.Outlined.Menu
    "close" -> Icons.Outlined.Close
    "cancel" -> Icons.Outlined.Cancel
    "decks" -> Icons.Outlined.Style
    "contacts" -> Icons.Outlined.PeopleOutline
    "pods" -> Icons.Outlined.TableRestaurant
    "more" -> Icons.Outlined.MoreVert
    "games" -> GameSwords
    "search" -> Icons.Outlined.Search
    "refresh" -> Icons.Outlined.Refresh
    "shuffle" -> Icons.Outlined.Shuffle
    "eye" -> Icons.Outlined.Visibility
    "eye-off" -> Icons.Outlined.VisibilityOff
    "person" -> Icons.Outlined.PersonAddAlt
    "key" -> Icons.Outlined.Key
    "edit" -> Icons.Outlined.Edit
    "prepare" -> Icons.Outlined.Tune
    "ready" -> Icons.Outlined.Check
    "pending" -> Icons.Outlined.HourglassEmpty
    "withdraw" -> Icons.AutoMirrored.Outlined.Undo
    "chevron" -> Icons.Outlined.ChevronRight
    "send" -> Icons.AutoMirrored.Outlined.Send
    "import" -> Icons.Outlined.ContentPaste
    "paste" -> Icons.Outlined.ContentPaste
    "download" -> Icons.Outlined.FileDownload
    "save" -> Icons.Outlined.Save
    "delete" -> Icons.Outlined.DeleteOutline
    "cover" -> Icons.Outlined.Image
    "email" -> Icons.Outlined.MailOutline
    "error" -> Icons.Outlined.ErrorOutline
    "logout" -> Icons.AutoMirrored.Outlined.Logout
    "settings" -> Icons.Outlined.Settings
    "add" -> Icons.Outlined.Add
    else -> Icons.AutoMirrored.Outlined.Login
}
