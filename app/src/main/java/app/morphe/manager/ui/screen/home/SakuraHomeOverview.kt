/*
 * Copyright 2026 SakuraPatcher contributors.
 * Native Sakura dashboard; all entrypoints call existing manager actions.
 */
package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/** The prototype's quick-access dashboard, backed by actual manager counts and actions. */
@Composable
internal fun SakuraHomeOverview(
    patchedApps: Int,
    availableApps: Int,
    onSelectApk: () -> Unit,
    onPatchLab: () -> Unit,
    onSources: () -> Unit,
    onSelections: () -> Unit,
    greeting: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Your patching workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(greeting?.takeIf { it.isNotBlank() } ?: "Everything you need, without the clutter.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f))
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Sakura Core", color = scheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("Ready to patch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Choose a file or select an installed app below.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
                }
                Button(onClick = onSelectApk) { Text("Select APK") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SakuraMetric(patchedApps.toString(), "Tracked builds", Modifier.weight(1f))
            SakuraMetric(availableApps.toString(), "Available apps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SakuraQuickAction("APK Patcher", "Open a local APK", MaterialSymbols.Rounded.Auto_fix_high,
                onSelectApk, Modifier.weight(1f))
            SakuraQuickAction("PatchLab", "Inspect and compare", MaterialSymbols.Rounded.Tune,
                onPatchLab, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SakuraQuickAction("Patch sources", "Manage bundles", MaterialSymbols.Rounded.Folder_code,
                onSources, Modifier.weight(1f))
            SakuraQuickAction("Saved selections", "Reuse patch choices", MaterialSymbols.Rounded.Apps,
                onSelections, Modifier.weight(1f))
        }
        Text("APPLICATIONS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SakuraMetric(value: String, label: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SakuraQuickAction(
    title: String,
    description: String,
    icon: ImageVector,
    action: () -> Unit,
    modifier: Modifier
) {
    Card(onClick = action, modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
