package dev.glowcow.stackd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.glowcow.stackd.ui.theme.StackdTheme

/**
 * A tab's page: the content runs under the frosted header and tab bar and gets the heights they cover.
 * The header is the tab's title with [trailing] at its end, or the page's own [header]; [floating] lies over it.
 * With [edge] the header always ends in the glass line: for a page that lies under it without scrolling.
 */
@Composable
fun TabScreen(
    tab: TopTab,
    onTab: (TopTab) -> Unit,
    ground: Color = StackdTheme.colors.groupBg,
    header: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    edge: Boolean = false,
    floating: @Composable BoxScope.(bottom: Dp) -> Unit = {},
    content: @Composable (top: Dp, bottom: Dp) -> Unit,
) {
    val c = StackdTheme.colors
    val density = LocalDensity.current
    val page = rememberGraphicsLayer()
    var headerHeight by remember { mutableIntStateOf(0) }
    val pageHeight = remember { mutableIntStateOf(0) }
    val barHeight = remember { mutableIntStateOf(0) }
    // How far the page has gone under the header, pixels: the header gets a line under it while that is more than nothing.
    var under by rememberSaveable { mutableFloatStateOf(0f) }
    val scrolled = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Pulled down with nothing left to scroll, the page is at its top whatever was counted.
                under = if (available.y > 0f && consumed.y == 0f) 0f else (under - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().background(ground).onSizeChanged { pageHeight.intValue = it.height }.nestedScroll(scrolled)) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                page.record { this@drawWithContent.drawContent() }
                drawLayer(page)
            },
        ) {
            content(with(density) { headerHeight.toDp() } + if (header == null) HeaderGap else 0.dp, with(density) { barHeight.intValue.toDp() })
        }
        floating(with(density) { barHeight.intValue.toDp() })
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = it.height }
                .frosted(page, ground) { Offset.Zero }
                .solid()
                .statusBarsPadding(),
        ) {
            if (header != null) {
                header()
            } else {
                Row(
                    // The title is inset like a caption; what stands at the end lines up with the blocks below.
                    // The row is as high as a 22 sp title with 14 dp above and below made it, whatever the title's face.
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 20.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageTitle(stringResource(tab.label), Modifier.weight(1f), large = true)
                    trailing?.invoke()
                }
            }
            if (edge) GlassLine() else HeaderLine(shown = under > 0f)
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
