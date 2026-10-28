/*
 * SakuraPatcher appearance quick controls, backed by the existing preferences.
 */
package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.theme.Theme
import kotlinx.coroutines.launch

/** Native Light / Dark / System preference from the imported Sakura settings design. */
@Composable
internal fun SakuraThemePicker(prefs: PreferencesManager) {
    val theme by prefs.theme.getAsState()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("APPEARANCE", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text("Choose your Sakura theme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Theme.DARK to "Sakura Dark",
                Theme.LIGHT to "Sakura Light",
                Theme.SYSTEM to "System"
            ).forEach { (mode, label) ->
                val selected = theme == mode
                Card(
                    onClick = { scope.launch { prefs.theme.update(mode) } },
                    modifier = Modifier.weight(1f).heightIn(min = 62.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer
                    )
                ) {
                    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) scheme.primary else scheme.onSurface)
                    }
                }
            }
        }
    }
}
