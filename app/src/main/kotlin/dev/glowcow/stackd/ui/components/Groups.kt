package dev.glowcow.stackd.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.stackd.ui.theme.AppFont
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
            // The chevron points along the reading direction.
            onClick != null -> Icon(
                StackdIcons.Chevron,
                null,
                tint = c.muted,
                modifier = Modifier.size(18.dp).scale(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, 1f),
            )
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
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
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

/** A row of a [Group] that turns a setting on and off; a tap anywhere on it flips the switch. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var tapped by remember { mutableStateOf(false) }
    GroupRow(title, subtitle = subtitle, onClick = { tapped = true; onChange(!checked) }, trailing = { SwitchMark(checked, tapped) })
}

/** The switch itself: track, border and thumb ride one fraction; it moves only after a tap, else it jumps. */
@Composable
private fun SwitchMark(checked: Boolean, animate: Boolean) {
    val c = StackdTheme.colors
    val fraction = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (animate) fraction.animateTo(target, tween(SWITCH_MS)) else fraction.snapTo(target)
    }
    val on = fraction.value
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        Modifier.size(52.dp, 32.dp).semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
    ) {
        val r = size.height / 2
        val edge = 2.dp.toPx()
        drawRoundRect(lerp(c.chip, c.accent, on), cornerRadius = CornerRadius(r))
        drawRoundRect(
            c.line.copy(alpha = 1f - on),
            Offset(edge / 2, edge / 2),
            Size(size.width - edge, size.height - edge),
            CornerRadius(r - edge / 2),
            Stroke(edge),
        )
        val x = r + (size.width - size.height) * (if (rtl) 1f - on else on)
        drawCircle(lerp(c.line, c.onAccent, on), (8 + 4 * on).dp.toPx(), Offset(x, r))
    }
}

private const val SWITCH_MS = 200

/**
 * A row of a [Group] to type into: the [label] stays above the text. [secret] hides what is typed;
 * a field that is not [singleLine] starts three lines tall. Text it appears with has the cursor at
 * its end, or its first [selected] characters selected.
 */
@Composable
fun GroupField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
    secret: Boolean = false,
    selected: Int = 0,
) {
    val c = StackdTheme.colors
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(value, if (selected > 0) TextRange(0, selected) else TextRange(value.length))) }
    // The text may be set from outside; the cursor then goes to its end.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    Column(
        Modifier
            .fillMaxWidth()
            // A tap anywhere on the row puts the cursor into the field.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.requestFocus() }
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Box {
            if (value.isEmpty() && placeholder != null) Text(placeholder, color = c.muted, fontSize = 15.sp)
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != value) onChange(it.text)
                },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                textStyle = TextStyle(color = c.text, fontFamily = AppFont, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else keyboard,
                    capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** Single choice in a bottom sheet; the current option carries a check mark. */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) = GroupSheet(title, onDismiss) { pick ->
    val accent = StackdTheme.colors.accent
    Group {
        options.forEachIndexed { i, (value, label) ->
            if (i > 0) GroupDivider()
            GroupRow(label, onClick = { pick { onSelect(value) } }, trailing = {
                if (value == selected) Icon(StackdIcons.Check, null, tint = accent, modifier = Modifier.size(20.dp))
            })
        }
    }
}
