package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import kotlin.random.Random

/** Small bounded particle set. The caller removes this composition when hidden or patching. */
@Composable
fun SakuraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sakura petals")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "petal fall")
    val positions = remember { val random = Random(121); List(16) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) } }
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    val petal = remember { Path().apply { moveTo(0f, -8f); cubicTo(10f, -6f, 10f, 6f, 0f, 9f); cubicTo(-6f, 3f, -7f, -3f, 0f, -8f); close() } }
    Canvas(modifier) {
        positions.forEach { (x, y, rotation) ->
            val center = Offset((x * size.width + phase * 60f) % size.width, ((y + phase) % 1f) * size.height)
            rotate(rotation * 360 + phase * 90, center) {
                withTransform({ translate(center.x, center.y) }) { drawPath(petal, color) }
            }
        }
    }
}
