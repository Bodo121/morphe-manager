/*
 * Sakura quality-of-life preferences. No account or network permissions.
 */
package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.SettingsItem
import app.morphe.manager.ui.screen.shared.SettingsSwitchItem
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch

@Composable
fun SakuraGreetingNameSetting(prefs: PreferencesManager) {
    val current by prefs.sakuraGreetingName.getAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    SettingsItem(
        title = stringResource(R.string.sakura_greeting_name_title),
        subtitle = current.takeIf { it.isNotBlank() } ?: stringResource(R.string.sakura_greeting_name_summary),
        onClick = { draft = current; open = true },
        leadingContent = { Icon(MaterialSymbols.Rounded.Person, contentDescription = null) }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("What should Sakura call you?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(32) },
                        singleLine = true,
                        label = { Text("Nickname (optional)") }
                    )
                    Text("Saved locally. Leave empty to use the regular greetings.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.sakuraGreetingName.update(draft.trim()) }
                    open = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SakuraHighRefreshSetting(prefs: PreferencesManager) {
    val enabled by prefs.sakuraPreferHighRefresh.getAsState()
    val scope = rememberCoroutineScope()
    SettingsSwitchItem(
        title = stringResource(R.string.sakura_high_refresh_title),
        subtitle = stringResource(R.string.sakura_high_refresh_summary),
        icon = MaterialSymbols.Rounded.Speed,
        checked = enabled,
        onToggle = { scope.launch { prefs.sakuraPreferHighRefresh.update(!enabled) } }
    )
}
