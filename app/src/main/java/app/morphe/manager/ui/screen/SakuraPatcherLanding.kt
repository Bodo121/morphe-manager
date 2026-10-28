/*
 * SakuraPatcher - quick launch page. All actions reuse the production manager flows.
 */
package app.morphe.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

@Composable
fun SakuraPatcherLanding(
    sourceCount: Int,
    availablePatches: Int,
    repatchableCount: Int,
    bottomBarHeight: Dp,
    onChooseApk: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPatchLab: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSelections: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onStartBatch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = bottomBarHeight + 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.sakura_app_icon),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Text("APK Patcher", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            "Build a patched APK with your installed sources. Advanced options, signing, " +
                "installation and logs still use the original SakuraPatcher workflow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Patch an APK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose an APK or use an installed app. Your sources and patch selections stay intact.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onChooseApk, modifier = Modifier.fillMaxWidth(), enabled = availablePatches > 0) {
                    Text("Select APK to patch")
                }
                if (availablePatches == 0) Text(
                    "Load a patch source before starting.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse apps")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("$sourceCount", style = MaterialTheme.typography.headlineMedium)
                    Text("Patch sources", style = MaterialTheme.typography.labelMedium)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("$availablePatches", style = MaterialTheme.typography.headlineMedium)
                    Text("Available patches", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Developer tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Inspect APK metadata, compare archive changes and review compatibility before patching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenPatchLab, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Sakura PatchLab")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenSources) { Text("Patch sources") }
                    TextButton(onClick = onOpenWorkspace) { Text("Build workspace") }
                    TextButton(onClick = onOpenSelections) { Text("Saved selections") }
                }
            }
        }
        if (repatchableCount > 0) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Updates available", style = MaterialTheme.typography.titleMedium)
                    Text("$repatchableCount previously patched app(s) can be repatched.")
                    OutlinedButton(onClick = onStartBatch) { Text("Start batch patching") }
                }
            }
        }
    }
}
