package dev.glowcow.stackd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.launch

/** Rounded block of rows on the grouped background. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(StackdTheme.colors.group), content = content)
}

@Composable
fun GroupDivider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(StackdTheme.colors.line))
}

/** A row of a [Group]. Clickable rows end with a chevron unless they bring their own [trailing]. */
@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = StackdTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 13.sp)
        }
        if (value != null) Text(value, color = c.muted, textAlign = TextAlign.End)
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(StackdIcons.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Bottom sheet in the grouped style. Rows call `pick` with their action: the sheet slides away first,
 * then the action runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.(pick: (() -> Unit) -> Unit) -> Unit) {
    val c = StackdTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.groupBg) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
            content { action ->
                scope.launch { state.hide() }.invokeOnCompletion {
                    onDismiss()
                    action()
                }
            }
        }
    }
}
