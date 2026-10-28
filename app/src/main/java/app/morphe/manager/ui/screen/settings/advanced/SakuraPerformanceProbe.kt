package app.morphe.manager.ui.screen.settings.advanced

import android.os.Debug
import android.view.Choreographer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.*

/** Opt-in frame interval sampling while the user scrolls. No telemetry leaves the device. */
@Composable
fun SakuraPerformanceProbe() {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var sampler by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { sampler?.cancel() } }
    OutlinedButton(enabled = !running, onClick = {
        sampler = scope.launch {
            running = true
            val samples = mutableListOf<Double>()
            val choreographer = Choreographer.getInstance()
            var previous = 0L
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(time: Long) {
                    if (previous != 0L) samples += (time - previous) / 1000000.0
                    previous = time
                    if (running) choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(callback)
            result = "Measuring for 10 seconds. Scroll this screen normally."
            try {
                delay(10000)
                val sorted = samples.sorted()
                val refresh = view.display?.refreshRate?.takeIf { it > 0 } ?: 60f
                val budget = 1000.0 / refresh
                val memory = withContext(Dispatchers.IO) { Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024 }
                if (sorted.isNotEmpty()) result = "${sorted.size} frame intervals · ${refresh.toInt()} Hz\nMedian: %.1f ms · P95: %.1f ms\nIntervals > 1.5× refresh budget: %d\nProcess PSS: %d MiB\nIntervals are a responsiveness indicator, not GPU render timings.".format(
                    sorted[sorted.size / 2], sorted[((sorted.size - 1) * 0.95).toInt()], sorted.count { it > budget * 1.5 }, memory)
            } finally { running = false; choreographer.removeFrameCallback(callback) }
        }
    }) { Text(if (running) "Measuring…" else "Measure responsiveness (10 seconds)") }
    if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)
}
