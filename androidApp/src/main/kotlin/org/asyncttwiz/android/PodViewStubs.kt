package org.asyncttwiz.android

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.asyncttwiz.protocol.*

@Composable fun PodPickView(
    pack: DraftPrivatePack?, packsBehind: Int, settings: DraftSettings,
    definitions: Map<String, CardDefinition>, waitingOn: List<AccountIdentity>, busy: Boolean,
    onConfirm: (keep: List<String>, burn: List<String>) -> Unit, onOpenBuild: () -> Unit,
    modifier: Modifier = Modifier,
) { Text("Pick screen", modifier) }

@Composable fun PodBuildView(
    build: PodDeckBuild, copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>,
    sideboardLimit: Int?, editable: Boolean, canSubmit: Boolean,
    onMove: (copyId: String, category: PodDeckCategory) -> Unit, onBasics: (PodDeckBasics) -> Unit,
    onSubmit: () -> Unit, modifier: Modifier = Modifier,
) { Text("Build screen", modifier) }

@Composable fun PodStatsView(
    build: PodDeckBuild, copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>,
    modifier: Modifier = Modifier,
) { Text("Stats screen", modifier) }
