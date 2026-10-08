package dev.glowcow.stackd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons from the design mockups (24×24 viewport). */
object StackdIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"

    private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 ${-r},${r}h${-(w - 2 * r)}" +
            "a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}Z"

    private fun icon(name: String, stroke: List<String> = emptyList(), fill: List<String> = emptyList(), width: Float = 1.8f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fill.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
            stroke.forEach {
                addPath(
                    PathParser().parsePathString(it).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Cards = icon("cards", stroke = listOf("M7 6.5h11.5A2.5 2.5 0 0 1 21 9v7.5"), fill = listOf(roundRect(3f, 9f, 15f, 11f, 2.5f)))
    val Search = icon("search", listOf(circle(11f, 11f, 6.5f), "M16 16l4.5 4.5"))
    val Scan = icon("scan", listOf("M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16M7 12h10"))
    val Settings = icon("settings", listOf("M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f)))
    val Plus = icon("plus", listOf("M12 5v14M5 12h14"), width = 2.2f)
    val Back = icon("back", listOf("M19 12H5M11 6l-6 6 6 6"), width = 2f)
    val Close = icon("close", listOf("M6 6l12 12M18 6L6 18"), width = 2f)
    val More = icon("more", fill = listOf(circle(5f, 12f, 1.8f), circle(12f, 12f, 1.8f), circle(19f, 12f, 1.8f)))
    val Flash = icon("flash", listOf("M13 3L5 13h6l-1 8 8-10h-6l1-8z"))
    val FlashOn = icon("flash_on", fill = listOf("M13 3L5 13h6l-1 8 8-10h-6l1-8z"))
    val Gallery = icon("gallery", listOf(roundRect(4f, 5f, 16f, 14f, 2f), "M4 16l5-5 4 4 2-2 5 5"))
    val Share = icon("share", listOf("M12 15V4M8 8l4-4 4 4M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13"))
    val Edit = icon("edit", listOf("M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4"))
    val Pin = icon("pin", listOf("M6 4h12v17l-6-4-6 4V4z"))
    val Pinned = icon("pinned", fill = listOf("M6 4h12v17l-6-4-6 4V4z"))
    val Trash = icon("trash", listOf("M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13"))
    val Chevron = icon("chevron", listOf("M9 5l7 7-7 7"))
    val Check = icon("check", listOf("M5 12.5l4.5 4.5L19 7.5"), width = 2.2f)
    val Camera = icon("camera", listOf("M4 8h3.5L9 6h6l1.5 2H20v11H4V8z", circle(12f, 13.5f, 3.2f)))
    val Refresh = icon("refresh", listOf("M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5"))
    val File = icon("file", listOf("M7 3h7l5 5v13H7V3zM14 3v5h5"))
}
