package dev.glowcow.stackd.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.glowcow.stackd.ui.theme.StackdTheme

/**
 * A tab's page. The content runs under the status bar and [header] and under the tab bar, which
 * blur it like frosted glass. [content] gets the heights the two cover, to pad its ends.
 * [floating] lies over the page and is not part of the blur: a shadow that fades inside the
 * recorded page does not come back.
 */
@Composable
fun TabScreen(
    tab: TopTab,
    onTab: (TopTab) -> Unit,
    ground: Color,
    header: @Composable () -> Unit,
    floating: @Composable BoxScope.(bottom: Dp) -> Unit = {},
    content: @Composable (top: Dp, bottom: Dp) -> Unit,
) {
    val c = StackdTheme.colors
    val density = LocalDensity.current
    val page = rememberGraphicsLayer()
    var headerHeight by remember { mutableIntStateOf(0) }
    val pageHeight = remember { mutableIntStateOf(0) }
    val barHeight = remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(ground).onSizeChanged { pageHeight.intValue = it.height }) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                page.record { this@drawWithContent.drawContent() }
                drawLayer(page)
            },
        ) {
            content(with(density) { headerHeight.toDp() }, with(density) { barHeight.intValue.toDp() })
        }
        floating(with(density) { barHeight.intValue.toDp() })
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = it.height }
                .frosted(page, ground) { Offset.Zero }
                .solid()
                .statusBarsPadding(),
        ) {
            header()
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight.intValue = it.height }
                .frosted(page, c.bg) { Offset(0f, (pageHeight.intValue - barHeight.intValue).toFloat()) }
                .solid(),
        ) {
            BottomBar(tab, onTab)
        }
    }
}
