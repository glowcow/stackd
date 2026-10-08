package dev.glowcow.stackd.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glowcow.stackd.R
import dev.glowcow.stackd.StackdApp
import dev.glowcow.stackd.barcode.BarcodeFormat
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardColors
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.ui.EditRoute
import dev.glowcow.stackd.ui.components.CardFace
import dev.glowcow.stackd.ui.components.ChoiceSheet
import dev.glowcow.stackd.ui.components.DetailScreen
import dev.glowcow.stackd.ui.components.Group
import dev.glowcow.stackd.ui.components.GroupDivider
import dev.glowcow.stackd.ui.components.GroupField
import dev.glowcow.stackd.ui.components.GroupRow
import dev.glowcow.stackd.ui.components.PillButton
import dev.glowcow.stackd.ui.components.cardMeta
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.launch

class EditCardViewModel(private val route: EditRoute, private val repo: CardRepository) : ViewModel() {
    var loaded by mutableStateOf(route.id == null)
        private set
    private var original: Card? = null

    var name by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var kind by mutableStateOf(CardKind.CARD)
    var value by mutableStateOf(route.draft?.value.orEmpty())
    /** null = pick from the value. */
    var format by mutableStateOf(route.draft?.format)
    var color by mutableIntStateOf(CardColors.palette.random())
    var note by mutableStateOf("")

    val isPass get() = original?.isPass == true

    init {
        route.id?.let { id ->
            viewModelScope.launch {
                repo.get(id)?.let { c ->
                    original = c
                    name = c.name
                    subtitle = c.subtitle.orEmpty()
                    kind = c.kind
                    value = c.barcodeValue.orEmpty()
                    format = c.barcodeFormat
                    color = c.bgColor
                    note = c.note.orEmpty()
                }
                loaded = true
            }
        }
    }

    val effectiveFormat: BarcodeFormat? get() = value.trim().takeIf { it.isNotEmpty() }?.let { format ?: BarcodeFormat.guess(it) }

    val canSave get() = loaded && name.isNotBlank() && (value.isNotBlank() || route.draft?.coverPath != null || isPass)

    fun preview(): Card = build(original?.id ?: "preview")

    private fun build(id: String): Card {
        val base = original
        val v = value.trim().takeIf { it.isNotEmpty() }
        val keepPassColors = base != null && base.bgColor == color
        return Card(
            id = id,
            kind = kind,
            source = base?.source ?: route.draft?.source ?: CardSource.MANUAL,
            name = name.trim(),
            subtitle = subtitle.trim().takeIf { it.isNotEmpty() },
            barcodeValue = v,
            barcodeFormat = effectiveFormat,
            barcodeAltText = base?.barcodeAltText?.takeIf { v == base.barcodeValue },
            bgColor = color,
            fgColor = if (keepPassColors) base.fgColor else CardColors.inkFor(color),
            labelColor = if (keepPassColors) base.labelColor else null,
            note = note.trim().takeIf { it.isNotEmpty() },
            pinned = base?.pinned ?: false,
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = base?.lastUsedAt,
            relevantAt = base?.relevantAt,
            expiresAt = base?.expiresAt,
            voided = base?.voided ?: false,
            passTypeId = base?.passTypeId,
            serial = base?.serial,
            fieldsJson = base?.fieldsJson,
            coverPath = base?.coverPath ?: route.draft?.coverPath,
            webServiceUrl = base?.webServiceUrl,
            authToken = base?.authToken,
            lastModified = base?.lastModified,
            updatedAt = base?.updatedAt,
        )
    }

    fun save(onSaved: (String) -> Unit) = viewModelScope.launch {
        val card = build(original?.id ?: CardRepository.newId())
        repo.save(card)
        onSaved(card.id)
    }
}

@Composable
fun EditCardScreen(
    route: EditRoute,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    vm: EditCardViewModel = viewModel(key = route.toString()) {
        EditCardViewModel(route, (this[APPLICATION_KEY] as StackdApp).container.cards)
    },
) {
    val c = StackdTheme.colors
    var picker by remember { mutableStateOf<Picker?>(null) }
    val auto = stringResource(R.string.edit_format_auto)
    val kinds = listOf(CardKind.CARD to stringResource(R.string.kind_card), CardKind.TICKET to stringResource(R.string.kind_ticket))

    DetailScreen(
        title = stringResource(if (route.id == null) R.string.edit_new_title else R.string.edit_title),
        leave = StackdIcons.Close,
        leaveLabel = stringResource(R.string.cancel),
        onLeave = onClose,
        actions = {
            PillButton(stringResource(R.string.save), enabled = vm.canSave) { vm.save(onSaved) }
            Spacer(Modifier.width(8.dp))
        },
    ) {
        if (!vm.loaded) return@DetailScreen
        val preview = vm.preview().let { if (it.name.isEmpty()) it.copy(name = stringResource(R.string.edit_name_placeholder)) else it }
        CardFace(preview, cardMeta(preview), expanded = true, logo = null, onClick = null)

        Group {
            GroupField(stringResource(R.string.edit_name), vm.name, { vm.name = it }, capitalize = true)
            GroupDivider()
            GroupField(
                stringResource(R.string.edit_subtitle),
                vm.subtitle,
                { vm.subtitle = it },
                placeholder = stringResource(R.string.edit_subtitle_hint),
                capitalize = true,
            )
        }
        if (!vm.isPass) {
            Group {
                GroupField(stringResource(R.string.edit_number), vm.value, { vm.value = it }, keyboard = KeyboardType.Ascii)
                GroupDivider()
                GroupRow(stringResource(R.string.edit_kind), value = kinds.first { it.first == vm.kind }.second, onClick = { picker = Picker.KIND })
                GroupDivider()
                GroupRow(
                    stringResource(R.string.edit_format),
                    value = when (val f = vm.format) {
                        null -> vm.effectiveFormat?.let { "$auto · ${it.label}" } ?: auto
                        else -> f.label
                    },
                    onClick = { picker = Picker.FORMAT },
                )
            }
        }
        Group {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp)) {
                Text(stringResource(R.string.edit_color), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
                FlowRow {
                    CardColors.palette.forEach { col ->
                        // The swatch is 36 dp, its touch target 48.
                        Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.color = col }, contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(if (col == vm.color) 3.dp else 1.dp, if (col == vm.color) c.text else c.line, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        Group {
            GroupField(stringResource(R.string.info_note), vm.note, { vm.note = it }, singleLine = false)
        }
    }

    when (picker) {
        Picker.KIND -> ChoiceSheet(
            title = stringResource(R.string.edit_kind),
            options = kinds,
            selected = vm.kind,
            onSelect = { vm.kind = it },
            onDismiss = { picker = null },
        )
        Picker.FORMAT -> ChoiceSheet(
            title = stringResource(R.string.edit_format),
            options = listOf<Pair<BarcodeFormat?, String>>(null to auto) + BarcodeFormat.entries.map { it to it.label },
            selected = vm.format,
            onSelect = { vm.format = it },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private enum class Picker { KIND, FORMAT }
