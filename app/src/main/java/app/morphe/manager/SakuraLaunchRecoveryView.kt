/*
 * SakuraPatcher launch-recovery view. Intentionally native Android Views so it
 * works even when Compose, theme initialization or dependency injection crashes.
 */
package app.morphe.manager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

fun showSakuraLaunchRecovery(activity: Activity, report: String) {
    val scale = activity.resources.displayMetrics.density
    fun dp(n: Int) = (n * scale).toInt()
    val textColor = Color.rgb(245, 237, 249)
    val background = Color.rgb(18, 10, 28)
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(28), dp(22), dp(28))
        setBackgroundColor(background)
    }
    fun label(value: String, size: Float, color: Int = textColor): TextView =
        TextView(activity).also {
            it.text = value
            it.textSize = size
            it.setTextColor(color)
            it.setPadding(0, dp(4), 0, dp(12))
            root.addView(it)
        }
    fun action(value: String, handler: () -> Unit) {
        root.addView(Button(activity).apply {
            text = value
            isAllCaps = false
            setOnClickListener { handler() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
    }
    label("🌸 SakuraPatcher Recovery", 23f)
    label("The previous launch or patching process crashed. No app settings or APKs were deleted.", 15f)
    label("You can copy the local stack trace, retry, or open a reduced interface. " +
        "Crash reports stay on your device until you remove them.", 13f, Color.LTGRAY)
    action("Copy crash report") {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SakuraPatcher crash", report))
    }
    action("Launch normally") {
        SakuraCrashRecovery.setSafeMode(activity, false)
        SakuraCrashRecovery.clearCrash(activity)
        activity.recreate()
    }
    action("Launch in safe UI mode") {
        SakuraCrashRecovery.setSafeMode(activity, true)
        SakuraCrashRecovery.clearCrash(activity)
        activity.recreate()
    }
    label("Local crash report", 16f)
    label(report.take(16000), 11f, Color.LTGRAY)
    activity.setContentView(ScrollView(activity).apply {
        isFillViewport = true
        addView(root)
    })
}
