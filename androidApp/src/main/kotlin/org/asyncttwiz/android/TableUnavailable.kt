package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** No fabricated cards, values or actionable board while the accepted boundary is unavailable. */
@Composable
internal fun TableUnavailable(title: String, landscape: Boolean, modifier: Modifier = Modifier,
    navigation: (() -> Unit)? = null, message: String? = null, loading: Boolean = true,
    retry: () -> Unit, returnCurrent: (() -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xfff5f8f7))) {
        val side = if (landscape) TablePresentation.sidebarWidth(maxWidth.value) else 0f
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val d = density
            val left = (side + 6f) * d
            val top = (if (landscape) 52f else 106f) * d
            val bottom = size.height - (if (landscape) 56f else 108f) * d
            val middle = (top + bottom) / 2
            val gap = (if (landscape) 0f else 116f) * d
            drawRect(Color(0xfff4e8ea), Offset(left, top), Size(size.width - left - 6 * d, middle - gap - top))
            drawRect(Color(0xffe2efe6), Offset(left, middle + gap), Size(size.width - left - 6 * d, bottom - middle - gap))
        }
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionSquare("Open navigation", "", navigation != null, Modifier.width(48.dp),
                icon = { TableChromeIcon("menu") }, onClick = { navigation?.invoke() })
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Surface(Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 368.dp),
            shape = MaterialTheme.shapes.medium, color = Color.White, shadowElevation = 4.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (loading) "Loading table…" else "Table unavailable", fontWeight = FontWeight.Bold)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = OwnTableColor)
                else Button(retry, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = OwnTableColor)) {
                    TableChromeIcon("current"); Spacer(Modifier.width(8.dp)); Text("Reconnect")
                }
                returnCurrent?.let { TextButton(it, Modifier.fillMaxWidth()) { Text("Return to current") } }
            }
        }
    }
}
