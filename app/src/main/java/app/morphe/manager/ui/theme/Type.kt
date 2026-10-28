/*
 * SakuraPatcher native typography sizes follow the supplied React design.
 * System fonts are used until the reference Plus Jakarta Sans and JetBrains
 * Mono font assets are obtained and bundled with their licenses.
 */
package app.morphe.manager.ui.theme

import app.morphe.manager.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Variable-weight OFL fonts from google/fonts, same typefaces as the ZIP prototype.
val SakuraSans = FontFamily(
    Font(R.font.sakura_plus_jakarta_sans, weight = FontWeight.W400),
    Font(R.font.sakura_plus_jakarta_sans, weight = FontWeight.W500),
    Font(R.font.sakura_plus_jakarta_sans, weight = FontWeight.W600),
    Font(R.font.sakura_plus_jakarta_sans, weight = FontWeight.W700)
)
val SakuraMono = FontFamily(
    Font(R.font.sakura_jetbrains_mono, weight = FontWeight.W400),
    Font(R.font.sakura_jetbrains_mono, weight = FontWeight.W500),
    Font(R.font.sakura_jetbrains_mono, weight = FontWeight.W600)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = SakuraSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)
)

val Typography.headerTitle: TextStyle
    get() = titleLarge.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
