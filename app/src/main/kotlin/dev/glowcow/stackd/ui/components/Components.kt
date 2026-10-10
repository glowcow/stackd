package dev.glowcow.stackd.ui.components

import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.stackd.R
import dev.glowcow.stackd.barcode.BarcodeFormat
import dev.glowcow.stackd.barcode.BarcodeRenderer
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.ui.theme.AppFont
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme
import dev.glowcow.stackd.ui.theme.TitleFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Three stacked cards, the app mark from the header. */
@Composable
fun StackdLogo(modifier: Modifier = Modifier) {
    val c = StackdTheme.colors
    Canvas(modifier.size(30.dp, 27.dp)) {
        val s = size.width / 62f
        fun card(x: Float, y: Float, color: Color) {
            val tl = Offset((x - 20f) * s, (y - 22f) * s)
            val sz = Size(44f * s, 30f * s)
            drawRoundRect(color, tl, sz, CornerRadius(6f * s))
            drawRoundRect(c.bg, tl, sz, CornerRadius(6f * s), style = Stroke(3f * s))
        }
        card(34f, 26f, c.muted)
        card(28f, 35f, c.text)
        card(22f, 44f, c.accent)
    }
}

@Composable
fun BarcodeImage(value: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, value, format) {
        this.value = withContext(Dispatchers.Default) { BarcodeRenderer.render(value, format)?.asImageBitmap() }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = format.label,
            modifier = modifier,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
        )
    } ?: Box(modifier)
}

/** Loads an image file off the main thread, honouring EXIF rotation; a new [version] reloads the same path. */
@Composable
fun rememberFileBitmap(path: String?, maxSize: Int = 1600, version: Any? = null): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path, version) {
        value = path?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(p))) { d, info, _ ->
                        val scale = maxOf(info.size.width, info.size.height).toFloat() / maxSize
                        if (scale > 1f) d.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

val CardShape = RoundedCornerShape(16.dp)

/** A barcode sits on light paper in any theme, so a scanner can read it. */
val BarcodePaper = Color(0xFFFAF9F5)
val BarcodeInk = Color(0xFF141413)
val BarcodeNote = Color(0xFF66655F)

/** Front of a card as seen in the stack. */
@Composable
fun CardFace(
    card: Card,
    meta: String?,
    expanded: Boolean,
    logo: ImageBitmap?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cover = rememberFileBitmap(card.coverPath, 1000)
    val ink = if (cover != null) Color.White else Color(card.fgColor)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT)
            .clip(CardShape)
            .background(Color(card.bgColor))
            .border(1.dp, Color.Black.copy(alpha = 0.06f), CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (cover != null) {
            Image(cover, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)))
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            CardTitleRow(card, meta, logo, ink)
            if (expanded) {
                Spacer(Modifier.weight(1f))
                BarcodeStrip(card)
            }
        }
    }
}

/** Width to height of a card in the stack: ISO/IEC 7810 ID-1, 85.60 × 53.98 mm. */
const val CARD_ASPECT = 85.60f / 53.98f

/** Logo, name and meta: the part of a card that peeks out of the stack. */
@Composable
fun CardTitleRow(card: Card, meta: String?, logo: ImageBitmap?, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (logo != null) {
            Image(logo, null, Modifier.height(22.dp).width((22f * logo.width / logo.height).dp.coerceAtMost(96.dp)), contentScale = ContentScale.Fit)
        }
        Text(
            card.name,
            color = ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (meta != null) Text(meta, color = ink.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun BarcodeStrip(card: Card) {
    val value = card.barcodeValue ?: return
    val format = card.barcodeFormat ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BarcodePaper)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val codeModifier = when {
            format == BarcodeFormat.PDF_417 -> Modifier.size(120.dp, 32.dp)
            format.is2d -> Modifier.size(40.dp)
            else -> Modifier.weight(1f).height(32.dp)
        }
        BarcodeImage(value, format, codeModifier)
        if (format.is2d) Spacer(Modifier.weight(1f))
        Text(
            card.maskedNumber.orEmpty(),
            color = BarcodeInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Square monogram used in list rows. */
@Composable
fun CardAvatar(card: Card, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color(card.bgColor)),
        contentAlignment = Alignment.Center,
    ) {
        Text(card.initials, color = Color(card.fgColor), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

enum class TopTab(val icon: ImageVector, val label: Int) {
    CARDS(StackdIcons.Cards, R.string.tab_cards),
    SEARCH(StackdIcons.Search, R.string.tab_search),
    SCANNER(StackdIcons.Scan, R.string.tab_scanner),
    SETTINGS(StackdIcons.Settings, R.string.tab_settings),
}

@Composable
fun BottomBar(current: TopTab, onSelect: (TopTab) -> Unit) {
    val c = StackdTheme.colors
    Column(Modifier.navigationBarsPadding()) {
        GlassLine()
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            for (tab in TopTab.entries) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton48(tab.icon, stringResource(tab.label), tint = if (tab == current) c.text else c.muted, size = 26.dp) { onSelect(tab) }
                }
            }
        }
    }
}

/**
 * The title of a page, on one line: Cormorant Garamond, [large] on a tab and the size a title was
 * before in a row with buttons; the app's own bold face where the language is Hebrew, which that face lacks.
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val own = LocalConfiguration.current.locales[0].language.let { it == "iw" || it == "he" }
    Text(
        text,
        modifier,
        color = StackdTheme.colors.text,
        fontFamily = if (own) AppFont else TitleFont,
        fontSize = when {
            own -> 22.sp
            large -> TAB_TITLE
            else -> PAGE_TITLE
        },
        fontWeight = if (own) FontWeight.Bold else FontWeight.Normal,
        style = TextStyle(fontFeatureSettings = "lnum"),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val TAB_TITLE = 36.sp

// Cormorant's capitals are lower than Arimo's: at this size they stand as tall as a 22 sp title did.
private val PAGE_TITLE = 25.sp

@Composable
fun IconButton48(
    icon: ImageVector,
    description: String,
    tint: Color = StackdTheme.colors.text,
    size: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size))
    }
}

/** The primary action of a page: a dark pill with a light label, 48 dp to touch. */
@Composable
fun PillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = StackdTheme.colors
    Box(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) c.bg else c.muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            // One that cannot be used takes the colour of a block: a chip is lost on the ground of a grouped page.
            modifier = Modifier.clip(CircleShape).background(if (enabled) c.text else c.group).padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
