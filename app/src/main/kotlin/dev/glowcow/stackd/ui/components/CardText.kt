package dev.glowcow.stackd.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.glowcow.stackd.R
import dev.glowcow.stackd.container
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.pkpass.FieldSection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Right-hand text on a card face: when a ticket is due, a pass's header value, or the number tail. */
@Composable
fun cardMeta(card: Card): String? {
    if (card.kind == CardKind.TICKET && card.relevantAt != null) return relativeDate(card.relevantAt)
    val header = remember(card.fieldsJson) { card.fields().firstOrNull { it.section == FieldSection.HEADER }?.value }
    return header ?: card.maskedNumber
}

@Composable
fun relativeDate(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(ms).atZone(zone)
    val today = LocalDate.now(zone)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(dt)
    return when (dt.toLocalDate()) {
        today -> stringResource(R.string.date_today, time)
        today.plusDays(1) -> stringResource(R.string.date_tomorrow, time)
        today.minusDays(1) -> stringResource(R.string.date_yesterday, time)
        else -> DateTimeFormatter.ofPattern(if (dt.year == today.year) "d MMM" else "d MMM yyyy", locale).format(dt)
    }
}

@Composable
fun kindLabel(card: Card): String = stringResource(if (card.kind == CardKind.TICKET) R.string.kind_ticket else R.string.kind_card)

@Composable
fun sourceLabel(source: CardSource): String = stringResource(
    when (source) {
        CardSource.SCAN -> R.string.source_scan
        CardSource.PHOTO -> R.string.source_photo
        CardSource.GALLERY -> R.string.source_gallery
        CardSource.MANUAL -> R.string.source_manual
        CardSource.PKPASS -> R.string.source_pkpass
    },
)

@Composable
fun rememberPassImage(card: Card, role: String): ImageBitmap? {
    val context = LocalContext.current
    val path = remember(card.id, card.isPass) { if (card.isPass) context.container.cards.passImage(card.id, role)?.path else null }
    return rememberFileBitmap(path, 800, card.lastModified)
}
