package dev.glowcow.stackd.ui.card

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.stackd.R
import dev.glowcow.stackd.barcode.BarcodeFormat
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.pkpass.FieldSection
import dev.glowcow.stackd.pkpass.PassField
import dev.glowcow.stackd.ui.components.BarcodeImage
import dev.glowcow.stackd.ui.components.CardTitleRow
import dev.glowcow.stackd.ui.components.cardMeta
import dev.glowcow.stackd.ui.components.rememberFileBitmap
import dev.glowcow.stackd.ui.components.rememberPassImage
import dev.glowcow.stackd.ui.components.relativeDate
import dev.glowcow.stackd.ui.components.sourceLabel
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme

/** A card opened in the wallet: its top row matches the stack face, below are the fields and the barcode. */
@Composable
fun OpenCard(card: Card, bright: Boolean, modifier: Modifier = Modifier) {
    val cover = rememberFileBitmap(card.coverPath, 1000)
    val strip = rememberPassImage(card, "strip")
    val logo = rememberPassImage(card, "logo")
    val ink = if (cover != null) Color.White else Color(card.fgColor)
    val label = card.labelColor?.let { Color(it) } ?: ink.copy(alpha = 0.75f)
    val fields = remember(card.fieldsJson) { card.fields() }

    Box(modifier.fillMaxWidth().background(Color(card.bgColor))) {
        if (cover != null) {
            Image(cover, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
        }
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.padding(horizontal = 18.dp)) { CardTitleRow(card, cardMeta(card), logo, ink) }
            val primary = fields.filter { it.section == FieldSection.PRIMARY }
            if (strip != null) {
                // Wallet layout: the strip runs edge to edge in its own proportions, primary fields on top of it.
                Box(Modifier.fillMaxWidth().aspectRatio(strip.width.toFloat() / strip.height)) {
                    Image(strip, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    PrimaryRow(primary, ink, label, Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 12.dp))
                }
            } else if (primary.isNotEmpty()) {
                PrimaryRow(primary, ink, label, Modifier.padding(horizontal = 18.dp))
            } else {
                card.subtitle?.let { Text(it, color = ink.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
            }
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val minor = fields.filter { it.section == FieldSection.SECONDARY || it.section == FieldSection.AUXILIARY }
                minor.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { FieldCell(it, ink, label, 15) }
                    }
                }
                BarcodeBlock(card, bright)
            }
        }
    }
}

@Composable
private fun PrimaryRow(fields: List<PassField>, ink: Color, label: Color, modifier: Modifier) {
    if (fields.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        fields.forEach { FieldCell(it, ink, label, 26) }
    }
}

@Composable
private fun FieldCell(field: PassField, ink: Color, label: Color, valueSize: Int) {
    Column {
        field.label?.let { Text(it.uppercase(), color = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        Text(field.value, color = ink, fontSize = valueSize.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BarcodeBlock(card: Card, bright: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val value = card.barcodeValue
        val format = card.barcodeFormat
        if (value == null || format == null) {
            Text(stringResource(R.string.no_barcode), color = Color(0xFF66655F), textAlign = TextAlign.Center)
            return@Column
        }
        val size = when {
            format == BarcodeFormat.PDF_417 -> Modifier.fillMaxWidth().height(96.dp)
            format.is2d -> Modifier.size(184.dp)
            else -> Modifier.fillMaxWidth().height(88.dp)
        }
        BarcodeImage(value, format, size)
        (card.barcodeAltText ?: value.takeIf { it.length <= 40 })?.let {
            Text(
                it,
                color = Color(0xFF141413),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (format.is2d) 0.sp else 2.sp,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            if (bright) stringResource(R.string.barcode_bright, format.label) else format.label,
            color = Color(0xFF66655F),
            fontSize = 12.sp,
        )
    }
}

/** Buttons under an open card. */
@Composable
fun CardActions(
    card: Card,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    updating: Boolean = false,
    onUpdate: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth()) {
        Action(StackdIcons.Share, stringResource(R.string.action_share), onClick = onShare)
        if (onUpdate != null) Action(StackdIcons.Refresh, stringResource(R.string.action_update), busy = updating, onClick = onUpdate)
        Action(StackdIcons.Edit, stringResource(R.string.action_edit), onClick = onEdit)
        Action(
            if (card.pinned) StackdIcons.Pinned else StackdIcons.Pin,
            stringResource(if (card.pinned) R.string.action_unpin else R.string.action_pin),
            onClick = onPin,
        )
        Action(StackdIcons.More, stringResource(R.string.action_details), onClick = onDetails)
    }
}

@Composable
private fun RowScope.Action(icon: ImageVector, label: String, busy: Boolean = false, onClick: () -> Unit) {
    val c = StackdTheme.colors
    Column(
        Modifier.weight(1f).height(64.dp).clip(CircleShape).clickable(enabled = !busy, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
        } else {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 12.sp, color = c.text, maxLines = 1)
    }
}

/** Back of the card: pass back fields, dates, note, and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailsSheet(card: Card, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val c = StackdTheme.colors
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bg) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(card.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            InfoRows(card)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(
                Modifier.fillMaxWidth().clickable { confirm = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(StackdIcons.Trash, null, tint = c.accent, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.delete), color = c.accent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.delete_title, card.name)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text(stringResource(R.string.delete), color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel), color = c.text) } },
            containerColor = c.bg,
        )
    }
}

@Composable
private fun InfoRows(card: Card) {
    val c = StackdTheme.colors
    val fields = remember(card.fieldsJson) { card.fields().filter { it.section == FieldSection.BACK } }
    val rows = buildList {
        fields.forEach { add((it.label ?: "") to it.value) }
        card.lastUsedAt?.let { add(stringResource(R.string.info_last_used) to relativeDate(it)) }
        if (card.canUpdate) card.updatedAt?.let { add(stringResource(R.string.info_updated) to relativeDate(it)) }
        add(stringResource(R.string.info_added) to "${sourceLabel(card.source)} · ${relativeDate(card.createdAt)}")
        card.note?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.info_note) to it) }
    }
    rows.forEach { (label, value) ->
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        val long = value.length > 32 || value.contains('\n') || label.isEmpty()
        val m = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
        if (long) {
            Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (label.isNotEmpty()) Text(label, color = c.muted)
                Text(value, color = c.text)
            }
        } else {
            Row(m, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = c.muted, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(12.dp))
                Text(value, color = c.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
        }
    }
}

/** Keeps the screen on, and at full brightness when [max], while composed. */
@Composable
fun ScreenBrightness(max: Boolean) {
    val view = LocalView.current
    DisposableEffect(max) {
        val window = (view.context as? Activity)?.window
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = if (max) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        view.keepScreenOn = true
        onDispose {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            view.keepScreenOn = false
        }
    }
}
