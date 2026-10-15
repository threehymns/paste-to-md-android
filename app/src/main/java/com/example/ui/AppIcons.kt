package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Pixel-perfect standalone vector icon definitions matching official Material Icons.
 * Using official Material Design SVG path data directly from Google's repository
 * eliminates the massive (~15MB+) androidx.compose.material:material-icons-extended dependency.
 */
object AppIcons {

    private fun createIcon(name: String, pathData: String): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black)
        ).build()
    }

    val ContentPaste: ImageVector by lazy {
        createIcon(
            name = "ContentPaste",
            pathData = "M19,2h-4.18C14.4,0.84,13.3,0,12,0c-1.3,0-2.4,0.84-2.82,2H5C3.9,2,3,2.9,3,4v16c0,1.1,0.9,2,2,2h14c1.1,0,2-0.9,2-2V4C21,2.9,20.1,2,19,2z M12,2c0.55,0,1,0.45,1,1s-0.45,1-1,1s-1-0.45-1-1S11.45,2,12,2z M19,20H5V4h2v3h10V4h2V20z"
        )
    }

    val ContentCopy: ImageVector by lazy {
        createIcon(
            name = "ContentCopy",
            pathData = "M16,1H4C2.9,1,2,1.9,2,3v14h2V3h12V1z M19,5H8C6.9,5,6,5.9,6,7v14c0,1.1,0.9,2,2,2h11c1.1,0,2-0.9,2-2V7C21,5.9,20.1,5,19,5z M19,21H8V7h11V21z"
        )
    }

    val Download: ImageVector by lazy {
        createIcon(
            name = "Download",
            pathData = "M19,9h-4V3H9v6H5l7,7L19,9z M5,18v2h14v-2H5z"
        )
    }

    val Delete: ImageVector by lazy {
        createIcon(
            name = "Delete",
            pathData = "M6,19c0,1.1,0.9,2,2,2h8c1.1,0,2-0.9,2-2V7H6V19z M19,4h-3.5l-1-1h-5l-1,1H5v2h14V4z"
        )
    }

    val Settings: ImageVector by lazy {
        createIcon(
            name = "Settings",
            pathData = "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"
        )
    }

    val ArrowBack: ImageVector by lazy {
        createIcon(
            name = "ArrowBack",
            pathData = "M20,11H7.83l5.59-5.59L12,4l-8,8l8,8l1.41-1.41L7.83,13H20V11z"
        )
    }

    val Palette: ImageVector by lazy {
        createIcon(
            name = "Palette",
            pathData = "M12,2C6.49,2,2,6.49,2,12s4.49,10,10,10c1.38,0,2.5-1.12,2.5-2.5c0-0.61-0.23-1.2-0.64-1.67c-0.08-0.1-0.13-0.21-0.13-0.33c0-0.28,0.22-0.5,0.5-0.5H16c3.31,0,6-2.69,6-6C22,6.04,17.51,2,12,2z M17.5,13c-0.83,0-1.5-0.67-1.5-1.5c0-0.83,0.67-1.5,1.5-1.5s1.5,0.67,1.5,1.5C19,12.33,18.33,13,17.5,13z M14.5,9C13.67,9,13,8.33,13,7.5C13,6.67,13.67,6,14.5,6S16,6.67,16,7.5C16,8.33,15.33,9,14.5,9z M5,11.5C5,10.67,5.67,10,6.5,10S8,10.67,8,11.5C8,12.33,7.33,13,6.5,13S5,12.33,5,11.5z M11,7.5C11,8.33,10.33,9,9.5,9S8,8.33,8,7.5C8,6.67,8.67,6,9.5,6S11,6.67,11,7.5z"
        )
    }

    val Code: ImageVector by lazy {
        createIcon(
            name = "Code",
            pathData = "M9.4,16.6L4.8,12l4.6-4.6L8,6l-6,6l6,6L9.4,16.6z M14.6,16.6l4.6-4.6l-4.6-4.6L16,6l6,6l-6,6L14.6,16.6z"
        )
    }

    val Info: ImageVector by lazy {
        createIcon(
            name = "Info",
            pathData = "M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10s10-4.48,10-10S17.52,2,12,2z M13,17h-2v-6h2V17z M13,9h-2V7h2V9z"
        )
    }

    val Check: ImageVector by lazy {
        createIcon(
            name = "Check",
            pathData = "M9,16.17L4.83,12l-1.42,1.41L9,19L21,7l-1.41-1.41L9,16.17z"
        )
    }

    val FormatListBulleted: ImageVector by lazy {
        createIcon(
            name = "FormatListBulleted",
            pathData = "M4,10.5c-0.83,0-1.5,0.67-1.5,1.5s0.67,1.5,1.5,1.5s1.5-0.67,1.5-1.5S4.83,10.5,4,10.5z M4,4.5C3.17,4.5,2.5,5.17,2.5,6s0.67,1.5,1.5,1.5S5.5,6.83,5.5,6S4.83,4.5,4,4.5z M4,16.5c-0.83,0-1.5,0.68-1.5,1.5s0.68,1.5,1.5,1.5s1.5-0.68,1.5-1.5S4.83,16.5,4,16.5z M7,19h14v-2H7V19z M7,13h14v-2H7V13z M7,5v2h14V5H7z"
        )
    }

    val Refresh: ImageVector by lazy {
        createIcon(
            name = "Refresh",
            pathData = "M17.65,6.35C16.2,4.9,14.21,4,12,4c-4.42,0-7.99,3.58-7.99,8s3.57,8,7.99,8c3.73,0,6.84-2.55,7.73-6h-2.08c-0.82,2.33-3.04,4-5.65,4c-3.31,0-6-2.69-6-6s2.69-6,6-6c1.66,0,3.14,0.69,4.22,1.78L13,11h7V4L17.65,6.35z"
        )
    }
}
