package dev.glowcow.stackd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.glowcow.stackd.ui.theme.StackdTheme

/**
 * The bar at the bottom of a page: an island of glass over [page], as wide as what it holds and clear
 * of the edges, the page fading into its [ground] under it. [onHeight] hears how much of the bottom
 * of the page, [pageSize] large, it takes.
 */
@Composable
fun BoxScope.Island(page: GraphicsLayer, ground: Color, pageSize: () -> IntSize, onHeight: (Int) -> Unit, content: @Composable () -> Unit) {
    val c = StackdTheme.colors
    val density = LocalDensity.current
    var zone by remember { mutableStateOf(IntSize.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { ISLAND_GAP.toPx() }
    // The veil starts a little above the island and is all but the ground at the edge of the screen.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(with(density) { zone.height.toDp() } + VEIL_RISE)
            .background(Brush.verticalGradient(0f to ground.copy(alpha = 0f), 1f to ground.copy(alpha = VEIL))),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged {
                zone = it
                onHeight(it.height)
            }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = ISLAND_GAP),
    ) {
        Box(
            Modifier
                .onSizeChanged { size = it }
                .shadow(SHADOW, ISLAND, ambientColor = SHADE, spotColor = SHADE)
                .frosted(page, c.group) { Offset((pageSize().width - size.width) / 2f, pageSize().height - zone.height + gap) }
                .border(1.dp, c.text.copy(alpha = GLASS_LINE), ISLAND)
                .solid()
                .heightIn(min = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** The icons of an island, each 48 dp to touch: the island is as wide as there are of them. */
@Composable
fun IslandIcons(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

// An island of icons is 56 dp high, so its ends are half circles.
private val ISLAND = RoundedCornerShape(28.dp)

private val ISLAND_GAP = 8.dp

private val SHADOW = 12.dp

// The shadow is soft in a light theme and all but lost on a dark ground.
private val SHADE = Color.Black.copy(alpha = 0.35f)

private val VEIL_RISE = 24.dp

private const val VEIL = 0.92f
