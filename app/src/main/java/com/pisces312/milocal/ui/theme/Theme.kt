package com.pisces312.milocal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = mdColor(0xFF006C4B),
    onPrimary = mdColor(0xFFFFFFFF),
    primaryContainer = mdColor(0xFF89F8C7),
    onPrimaryContainer = mdColor(0xFF002114),
    secondary = mdColor(0xFF4D6356),
    onSecondary = mdColor(0xFFFFFFFF),
    tertiary = mdColor(0xFF3D6374),
    onTertiary = mdColor(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = mdColor(0xFF6DDBAC),
    onPrimary = mdColor(0xFF003824),
    primaryContainer = mdColor(0xFF005237),
    onPrimaryContainer = mdColor(0xFF89F8C7),
    secondary = mdColor(0xFFB4CCBC),
    onSecondary = mdColor(0xFF203529),
    tertiary = mdColor(0xFFA4CCDF),
    onTertiary = mdColor(0xFF043545),
)

private fun mdColor(color: Long) = androidx.compose.ui.graphics.Color(color.toInt())

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
