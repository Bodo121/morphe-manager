/*
 * SakuraPatcher native typography sizes follow the supplied React design.
 * System fonts are used until the reference Plus Jakarta Sans and JetBrains
 * Mono font assets are obtained and bundled with their licenses.
 */
package app.morphe.manager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)
)

val Typography.headerTitle: TextStyle
    get() = titleLarge.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
