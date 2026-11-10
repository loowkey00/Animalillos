package com.example.animalillos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.animalillos.R

val OpenSans = FontFamily(
    Font(R.font.open_sans_light, FontWeight.Light),
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_semibold, FontWeight.SemiBold),
    Font(R.font.open_sans_bold, FontWeight.Bold)
)

val AristaPro = FontFamily(Font(R.font.arista_pro))
val Agrandir = FontFamily(Font(R.font.agrandir))

// Every Material text style uses Open Sans
val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = OpenSans),
        displayMedium = displayMedium.copy(fontFamily = OpenSans),
        displaySmall = displaySmall.copy(fontFamily = OpenSans),
        headlineLarge = headlineLarge.copy(fontFamily = OpenSans),
        headlineMedium = headlineMedium.copy(fontFamily = OpenSans),
        headlineSmall = headlineSmall.copy(fontFamily = OpenSans),
        titleLarge = titleLarge.copy(fontFamily = OpenSans),
        titleMedium = titleMedium.copy(fontFamily = OpenSans),
        titleSmall = titleSmall.copy(fontFamily = OpenSans),
        bodyLarge = bodyLarge.copy(fontFamily = OpenSans),
        bodyMedium = bodyMedium.copy(fontFamily = OpenSans),
        bodySmall = bodySmall.copy(fontFamily = OpenSans),
        labelLarge = labelLarge.copy(fontFamily = OpenSans),
        labelMedium = labelMedium.copy(fontFamily = OpenSans),
        labelSmall = labelSmall.copy(fontFamily = OpenSans)
    )
}
