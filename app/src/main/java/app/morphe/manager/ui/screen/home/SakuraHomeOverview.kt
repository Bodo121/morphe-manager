/*
 * Copyright 2026 SakuraPatcher contributors.
 * Native Sakura dashboard; all entrypoints call existing manager actions.
 */
package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.morphe.manager.R
import androidx.compose.ui.res.stringResource
import app.morphe.manager.domain.manager.PreferencesManager
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.LocalTime
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
    onWorkspace: () -> Unit,
    greeting: String? = null,
    prefs: PreferencesManager = koinInject()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recent by produceState<app.morphe.manager.domain.workspace.BuildRecipe?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.morphe.manager.domain.workspace.BuildWorkspace(context).readAll().firstOrNull()
        }
    }
    val scheme = MaterialTheme.colorScheme
    val nickname by prefs.sakuraGreetingName.getAsState()
    val today = remember { LocalDate.now() }
    val hour = remember { LocalTime.now().hour }
    val displayGreeting = remember(greeting, nickname, today, hour) {
        if (greeting != null && nickname.isNotBlank()) {
            val opener = when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Hello"
            }
            val prompts = listOf(
                "What are you up to today?", "Ready to make something yours?",
                "Have a great day!", "Anything interesting to patch?",
                "Your next build is one tap away.", "What's the plan today?",
                "Ready for a fresh start?"
            )
            "$opener, ${nickname.trim().take(32)}! ${prompts[(today.toEpochDay() % prompts.size).toInt()]}"
        } else greeting?.takeIf { it.isNotBlank() } ?: "Morphe engine · Sakura Core"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(displayGreeting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Your patching workspace",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Sakura Core", color = scheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("Patch your apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Choose an installed app below or open an APK from storage.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
                }
                Button(onClick = onSelectApk, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sakura_ui_select_apk)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SakuraMetric(patchedApps.toString(), "Tracked builds", Modifier.weight(1f))
            SakuraMetric(availableApps.toString(), "Available apps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SakuraQuickAction("Recent builds", recent?.let { "${it.packageName} · ${it.status}" } ?: "Recipes, logs and recovery", MaterialSymbols.Rounded.History,
                onWorkspace, Modifier.weight(1f))
            SakuraQuickAction("Sakura PatchLab", "Bytecode structure diagnostics", MaterialSymbols.Rounded.Tune,
                onPatchLab, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SakuraQuickAction("Patch Sources", "Manage patch bundles", MaterialSymbols.Rounded.Folder_code,
                onSources, Modifier.weight(1f))
            SakuraQuickAction("Patch profiles", "Save and restore choices", MaterialSymbols.Rounded.Apps,
                onSelections, Modifier.weight(1f))
        }
        Text("INSTALLED AND AVAILABLE APPS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
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
