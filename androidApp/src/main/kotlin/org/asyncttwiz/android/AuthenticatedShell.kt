package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AuthenticatedShell(controller: AuthController, landscape: Boolean, catalog: CatalogViewModel? = null) {
    val state = controller.state
    BackHandler(state.drawerOpen || state.destination != ShellDestination.MENU) { controller.back() }
    val content: @Composable () -> Unit = {
        ShellContent(state.destination, !landscape, { controller.drawer(true) }, controller::navigate, catalog)
    }
    Box(Modifier.fillMaxSize().testTag("authenticated-identity")
        .semantics { contentDescription = "Signed in as ${state.account!!.username}" }) {
        if (landscape) {
            // This is the non-gameplay shell. Future tabletop routes use the drawer in both orientations.
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(200.dp).fillMaxHeight(), color = Color.White,
                    border = BorderStroke(1.dp, Color(0xffd0d6dd))) {
                    Navigation(controller, close = false)
                }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            // One source of truth for Back/Close/outside dismissal; no mirrored animated DrawerState.
            Box(Modifier.fillMaxSize()) {
                Box(if (state.drawerOpen) Modifier.clearAndSetSemantics {} else Modifier) { content() }
                if (state.drawerOpen) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f))
                        .clickable { controller.drawer(false) }.semantics { contentDescription = "Close navigation menu" })
                    Surface(Modifier.widthIn(max = 280.dp).fillMaxWidth(.8f).fillMaxHeight()
                        .semantics { paneTitle = "Navigation menu" }, color = Color.White) {
                        Navigation(controller, close = true)
                    }
                    BackHandler { controller.drawer(false) }
                }
            }
        }
    }
}

@Composable
private fun Navigation(controller: AuthController, close: Boolean) {
    Column(Modifier.fillMaxSize()) {
        if (close) IconButton(onClick = { controller.drawer(false) }, Modifier.align(Alignment.End)) {
            EntryIcon("Close navigation", "close")
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp)) {
            NavigationItem("Menu", null, controller.state.destination == ShellDestination.MENU) {
                controller.navigate(ShellDestination.MENU)
            }
            for (destination in listOf(ShellDestination.DECKS, ShellDestination.CONTACTS)) {
                NavigationItem(destination.title, destination.icon(), controller.state.destination == destination, indented = true) {
                    controller.navigate(destination)
                }
            }
            Spacer(Modifier.height(12.dp))
            NavigationItem("Games", null, controller.state.destination == ShellDestination.GAMES) {
                controller.navigate(ShellDestination.GAMES)
            }
            // The real synchronized game rows join this list in I09-I12; never seed demo games here.
        }
        HorizontalDivider()
        TextButton(onClick = controller::logout, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("logout"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            EntryIcon(null, "login"); Spacer(Modifier.width(8.dp)); Text("Logout")
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavigationItem(label: String, icon: String?, selected: Boolean, indented: Boolean = false, action: () -> Unit) {
    NavigationDrawerItem(label = { Text(label, fontWeight = if (icon == null) FontWeight.Bold else FontWeight.Normal) },
        selected = selected, onClick = action,
        icon = if (icon != null) { { EntryIcon(null, icon) } } else null,
        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .08f)),
        modifier = Modifier.padding(start = if (indented) 12.dp else 0.dp).heightIn(min = 48.dp))
}

@Composable
private fun ShellContent(destination: ShellDestination, hamburger: Boolean, open: () -> Unit, navigate: (ShellDestination) -> Unit,
    catalog: CatalogViewModel?) {
    Column(Modifier.fillMaxSize()) {
        if (hamburger || destination != ShellDestination.MENU) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hamburger) IconButton(onClick = open) { EntryIcon("Open navigation", "menu") }
            if (destination != ShellDestination.MENU)
                Text(destination.title, Modifier.padding(12.dp).testTag("destination-title"), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(16.dp),
                verticalArrangement = if (destination == ShellDestination.MENU) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (destination == ShellDestination.MENU) {
                    WizardLogo()
                    Spacer(Modifier.height(24.dp))
                    for (item in listOf(ShellDestination.GAMES, ShellDestination.DECKS, ShellDestination.CONTACTS)) {
                        OutlinedButton(onClick = { navigate(item) }, modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth()
                            .heightIn(min = 64.dp).testTag("menu-${item.name}"),
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xffcdd3dc)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White,
                                contentColor = if (item == ShellDestination.GAMES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                            EntryIcon(null, item.icon()); Spacer(Modifier.width(12.dp))
                            Text(item.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("›", Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                } else if (destination == ShellDestination.DECKS && catalog != null) {
                    CatalogControls(catalog)
                } else {
                    Text(when (destination) {
                        ShellDestination.GAMES -> "Games and invitations are not available in this build yet."
                        ShellDestination.DECKS -> "Deck import and management are not available in this build yet."
                        else -> "Contact search and invitations are not available in this build yet."
                    }, Modifier.widthIn(max = 540.dp).fillMaxWidth())
                }
            }
        }
    }
}

private fun ShellDestination.icon(): String = when (this) {
    ShellDestination.GAMES -> "games"
    ShellDestination.DECKS -> "decks"
    ShellDestination.CONTACTS -> "contacts"
    ShellDestination.MENU -> "menu"
}
