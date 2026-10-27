package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.AccountIdentity

/** Shared contacts/search surface for pod membership and game opponents. */
@Composable
internal fun PlayerPicker(controller: LobbyController, select: ((AccountIdentity) -> Unit)? = null,
    actions: @Composable RowScope.(AccountIdentity) -> Unit) {
    val state = controller.state
    val contacts = state.contactsTab == ContactsTab.CONTACTS
    val accounts = if (contacts) state.filteredContacts else state.results
    Column(Modifier.fillMaxSize()) {
        TabRow(state.contactsTab.ordinal) {
            ContactsTab.entries.forEach { tab -> Tab(state.contactsTab == tab, { controller.contactsTab(tab) },
                text = { Text(if (tab == ContactsTab.CONTACTS) "Contacts" else "Search") }) }
        }
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (contacts) ScreenTextField(state.contactFilter, controller::filterContacts,
                Modifier.fillMaxWidth().semantics { contentDescription = "Filter contacts" }, placeholder = "Filter contacts")
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenTextField(state.query, controller::query, Modifier.weight(1f)
                    .semantics { contentDescription = "Search usernames" }, placeholder = "Search usernames")
                ScreenIconButton("Search players", "search", controller::search, enabled = !state.searching)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val loading = if (contacts) !state.loaded && state.busy && accounts.isEmpty() else state.searching
                if (loading) CircularProgressIndicator()
                else if (accounts.isEmpty()) Text("No players found")
                else LazyColumn(Modifier.fillMaxSize()) {
                    items(accounts, key = { it.accountId }) { account ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .then(if (select != null) Modifier.clickable { select(account) } else Modifier),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(account)
                            Text(account.username, Modifier.weight(1f))
                            actions(account)
                        }
                    }
                }
                FloatingTransientError(state.searchError, controller::dismissSessionError, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
