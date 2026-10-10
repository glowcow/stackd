package dev.glowcow.stackd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.glowcow.stackd.ui.theme.StackdTheme

/**
 * A page opened from a tab: a column of blocks that scrolls under a frosted header with a
 * [leave] button, a title and the page's [actions], as a tab's page scrolls under its own.
 */
@Composable
fun DetailScreen(
    title: String,
    leave: ImageVector,
    leaveLabel: String,
    onLeave: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = StackdTheme.colors
    val page = rememberGraphicsLayer()
    var header by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    Box(Modifier.fillMaxSize().background(c.groupBg).imePadding()) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                page.record { this@drawWithContent.drawContent() }
                drawLayer(page)
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(top = with(LocalDensity.current) { header.toDp() } + HeaderGap)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { header = it.height }
                .frosted(page, c.groupBg) { Offset.Zero }
                .solid()
                .statusBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton48(leave, leaveLabel, onClick = onLeave)
                PageTitle(title, Modifier.weight(1f).padding(start = 4.dp, end = 8.dp))
                actions()
            }
            HeaderLine(shown = scrolled)
        }
    }
}
