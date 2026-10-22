package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Teal = Color(0xff107e7a)
private val Ink = Color(0xff202d3d)
private val Backdrop = Color(0xfff6f7f8)

@Composable
fun AccountEntry(controller: AuthController, reset: ResetController, catalog: CatalogViewModel? = null,
    deckImport: DeckImportController? = null, decks: DeckLibraryController? = null) {
    val state = controller.state
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, onPrimary = Color.White,
        background = Backdrop, surface = Color.White, onSurface = Ink)) {
        BackHandler(enabled = reset.state != null) { reset.dismiss() }
        BackHandler(enabled = reset.state == null && state.page == AuthPage.REGISTER && !state.restoring && state.account == null) {
            controller.page(AuthPage.LOGIN)
        }
        Box(Modifier.fillMaxSize().background(Backdrop).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            when {
                reset.state != null -> ResetEntry(reset)
                state.account != null -> AuthenticatedShell(controller, landscape, catalog, deckImport, decks)
                state.logoutFailed -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.message!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = controller::logout) { Text("Retry sign-out") }
                }
                state.restoring -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Async Tabletop Wizards", style = MaterialTheme.typography.headlineSmall)
                    Text("Specialized tabletop simulator for asynchronous wizardry", textAlign = TextAlign.Center)
                    Text(state.message ?: "Checking saved login…")
                    if (state.restoreFailed) {
                        Button(onClick = controller::restore) { Text("Retry") }
                        TextButton(onClick = controller::logout) { EntryIcon(null, "login"); Spacer(Modifier.width(8.dp)); Text("Logout") }
                    } else CircularProgressIndicator(Modifier.size(28.dp))
                }
                state.page == AuthPage.REGISTER && landscape -> Row(Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(0.35f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ASYNC TABLETOP WIZARDS", fontWeight = FontWeight.SemiBold)
                        Text("Create your player account", style = MaterialTheme.typography.headlineSmall)
                    }
                    FormCard(Modifier.weight(0.65f).fillMaxHeight().verticalScroll(rememberScrollState()), state, controller, reset, branded = false)
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // minHeight centers the same bounded card when it fits; short/IME viewports scroll it.
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        FormCard(Modifier.widthIn(max = 400.dp).fillMaxWidth(), state, controller, reset,
                            branded = state.page == AuthPage.LOGIN)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormCard(modifier: Modifier, state: AuthState, controller: AuthController, reset: ResetController, branded: Boolean) {
    var visible by remember(state.page) { mutableStateOf(false) } // Not saveable; no secret in instance state.
    Surface(modifier, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xffcdd3dc))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (branded) {
                WizardLogo(Modifier.align(Alignment.CenterHorizontally))
                Text("Async Tabletop Wizards", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Specialized tabletop simulator for asynchronous wizardry", Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xff6b7788), textAlign = TextAlign.Center)
            }
            Text(if (state.page == AuthPage.REGISTER) "Create account" else "Sign in",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.page == AuthPage.REGISTER) {
                EntryField("Username", state.username, !state.busy, "username", KeyboardType.Text,
                    onValueChange = { controller.edit(username = it) })
                Text("Your username is visible to players.", style = MaterialTheme.typography.bodySmall, color = Color(0xff6b7788))
            }
            EntryField("Email", state.email, !state.busy, "email", KeyboardType.Email,
                onValueChange = { controller.edit(email = it) })
            if (state.page == AuthPage.REGISTER)
                Text("Your email stays private.", style = MaterialTheme.typography.bodySmall, color = Color(0xff6b7788))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Password", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(state.password, { controller.edit(password = it) }, Modifier.fillMaxWidth().testTag("password")
                    .semantics { contentDescription = "Password" },
                    enabled = !state.busy, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }, enabled = !state.busy) {
                            EntryIcon(if (visible) "Hide password" else "Show password", "eye")
                        }
                    }, shape = RoundedCornerShape(8.dp))
            }
            state.message?.let {
                Text(it, Modifier.testTag("auth-message"), color = if (state.error) MaterialTheme.colorScheme.error else Teal,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = controller::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp)) {
                EntryIcon(null, if (state.page == AuthPage.REGISTER) "person" else "login")
                Spacer(Modifier.width(8.dp))
                Text(if (state.busy) "Please wait…" else if (state.page == AuthPage.REGISTER) "Create account" else "Sign in")
            }
            HorizontalDivider()
            TextButton(onClick = { controller.page(if (state.page == AuthPage.REGISTER) AuthPage.LOGIN else AuthPage.REGISTER) },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                EntryIcon(null, if (state.page == AuthPage.REGISTER) "login" else "person")
                Spacer(Modifier.width(8.dp))
                Text(if (state.page == AuthPage.REGISTER) "Sign in" else "Create account")
            }
            if (state.page == AuthPage.LOGIN)
                TextButton(onClick = { controller.page(AuthPage.LOGIN); reset.request(state.email) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    EntryIcon(null, "key"); Spacer(Modifier.width(8.dp)); Text("Forgot password?")
                }
        }
    }
}

@Composable
private fun ResetEntry(controller: ResetController) {
    val state = controller.state ?: return
    var visible by remember(state.page) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (state.page == ResetPage.REQUEST) Text("Reset password",
                Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(bottom = 16.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(Modifier.widthIn(max = 400.dp).fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xffcdd3dc))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.page == ResetPage.REQUEST) {
                        EntryField("Email", state.email, !state.busy, "email", KeyboardType.Email,
                            onValueChange = { controller.edit(email = it) })
                    } else if (state.page == ResetPage.PASSWORD && !state.checking) {
                        Text("New password", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(state.password, { controller.edit(password = it) },
                            Modifier.fillMaxWidth().testTag("password").semantics { contentDescription = "New password" },
                            enabled = !state.busy, singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            trailingIcon = { IconButton(onClick = { visible = !visible }, enabled = !state.busy) {
                                EntryIcon(if (visible) "Hide password" else "Show password", "eye")
                            } }, shape = RoundedCornerShape(8.dp))
                    }
                    if (state.checking && !state.checkFailed) {
                        Text("Checking reset link…"); CircularProgressIndicator(Modifier.size(24.dp))
                    }
                    state.message?.let { Text(it, Modifier.testTag("reset-message"),
                        color = if (state.error) MaterialTheme.colorScheme.error else Teal) }
                    when {
                        state.checkFailed -> TextButton(onClick = controller::checkLink, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Retry")
                        }
                        state.page == ResetPage.INVALID -> TextButton(onClick = { controller.request() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Request another link") }
                        !state.checking -> Button(onClick = controller::submit, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)) {
                            EntryIcon(null, "key"); Spacer(Modifier.width(8.dp))
                            Text(if (state.busy) "Please wait…" else if (state.page == ResetPage.REQUEST) "Send reset link" else "Change password")
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = controller::dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        EntryIcon(null, "login"); Spacer(Modifier.width(8.dp)); Text("Back to sign in")
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryField(label: String, value: String, enabled: Boolean, tag: String, type: KeyboardType,
    onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label }, enabled = enabled,
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
            shape = RoundedCornerShape(8.dp))
    }
}

@Composable
internal fun WizardLogo(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(112.dp, 76.dp).semantics { contentDescription = "Wizard hat" }) {
            val hat = Path().apply { moveTo(size.width * .25f, size.height * .85f); lineTo(size.width * .55f, 0f)
                lineTo(size.width * .76f, size.height * .85f); close() }
            drawPath(hat, Color(0xff365f53))
            drawOval(Color(0xffd7bb6d), Offset(size.width * .24f, size.height * .7f), Size(size.width * .54f, size.height * .12f))
            drawOval(Color(0xff365f53), Offset(0f, size.height * .77f), Size(size.width, size.height * .23f))
            val star = Path().apply { moveTo(size.width * .52f, size.height * .34f); lineTo(size.width * .56f, size.height * .46f)
                lineTo(size.width * .64f, size.height * .49f); lineTo(size.width * .56f, size.height * .53f)
                lineTo(size.width * .52f, size.height * .64f); lineTo(size.width * .48f, size.height * .53f)
                lineTo(size.width * .4f, size.height * .49f); lineTo(size.width * .48f, size.height * .46f); close() }
            drawPath(star, Color(0xffead99d))
        }
        Text("ATW", fontWeight = FontWeight.Bold, color = Color(0xff244c46), style = MaterialTheme.typography.titleLarge)
    }
}

/** Small original vector glyphs; buttons carry accessible names and full native targets. */
@Composable
internal fun EntryIcon(description: String?, kind: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val w = size.width
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w / 12)
        when (kind) {
            "menu" -> for (line in listOf(.25f, .5f, .75f))
                drawLine(color, Offset(w * .15f, w * line), Offset(w * .85f, w * line), w / 12)
            "close" -> {
                drawLine(color, Offset(w * .25f, w * .25f), Offset(w * .75f, w * .75f), w / 12)
                drawLine(color, Offset(w * .75f, w * .25f), Offset(w * .25f, w * .75f), w / 12)
            }
            "decks" -> {
                drawRect(color, Offset(w * .32f, w * .15f), Size(w * .5f, w * .7f), style = stroke)
                drawLine(color, Offset(w * .18f, w * .25f), Offset(w * .1f, w * .85f), w / 12)
                drawLine(color, Offset(w * .1f, w * .85f), Offset(w * .6f, w * .95f), w / 12)
            }
            "contacts" -> {
                for (x in listOf(.35f, .65f)) {
                    drawCircle(color, w / 8, Offset(w * x, w * .3f), style = stroke)
                    drawArc(color, 180f, 180f, false, Offset(w * (x - .25f), w * .55f), Size(w * .5f, w * .4f), style = stroke)
                }
            }
            "games" -> {
                drawLine(color, Offset(w * .2f, w * .2f), Offset(w * .8f, w * .8f), w / 9)
                drawLine(color, Offset(w * .8f, w * .2f), Offset(w * .2f, w * .8f), w / 9)
                drawLine(color, Offset(w * .1f, w * .65f), Offset(w * .35f, w * .9f), w / 12)
                drawLine(color, Offset(w * .65f, w * .9f), Offset(w * .9f, w * .65f), w / 12)
            }
            "eye" -> { drawOval(color, Offset(1f, w / 4), Size(w - 2, w / 2), style = stroke); drawCircle(color, w / 8) }
            "person" -> { drawCircle(color, w / 7, Offset(w * .4f, w / 3), style = stroke)
                drawArc(color, 180f, 180f, false, Offset(w * .1f, w * .6f), Size(w * .6f, w * .4f), style = stroke)
                drawLine(color, Offset(w * .7f, w * .35f), Offset(w, w * .35f), w / 12)
                drawLine(color, Offset(w * .85f, w * .2f), Offset(w * .85f, w * .5f), w / 12) }
            "key" -> { drawCircle(color, w / 5, Offset(w / 4, w / 2), style = stroke)
                drawLine(color, Offset(w * .45f, w / 2), Offset(w, w / 2), w / 12)
                drawLine(color, Offset(w * .8f, w / 2), Offset(w * .8f, w * .7f), w / 12) }
            else -> { drawLine(color, Offset(w * .1f, w / 2), Offset(w * .7f, w / 2), w / 12)
                drawLine(color, Offset(w * .5f, w * .3f), Offset(w * .7f, w / 2), w / 12)
                drawLine(color, Offset(w * .5f, w * .7f), Offset(w * .7f, w / 2), w / 12)
                drawLine(color, Offset(w * .9f, w * .15f), Offset(w * .9f, w * .85f), w / 12) }
        }
    }
}
