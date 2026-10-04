package dev.glowcow.stackd.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import dev.glowcow.stackd.ui.components.IconButton48
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

@OptIn(ExperimentalMaterial3Api::class)
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
    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton48(StackdIcons.Close, stringResource(R.string.cancel), onClick = onClose)
            Text(
                stringResource(if (route.id == null) R.string.edit_new_title else R.string.edit_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                stringResource(R.string.save),
                color = if (vm.canSave) c.accent else c.muted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = vm.canSave) { vm.save(onSaved) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (!vm.loaded) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val preview = vm.preview().let { if (it.name.isEmpty()) it.copy(name = stringResource(R.string.edit_name_placeholder)) else it }
            CardFace(preview, cardMeta(preview), expanded = true, logo = null, onClick = null)

            Field(vm.name, { vm.name = it }, stringResource(R.string.edit_name), capitalize = true)
            Field(vm.subtitle, { vm.subtitle = it }, stringResource(R.string.edit_subtitle), placeholder = stringResource(R.string.edit_subtitle_hint), capitalize = true)

            if (!vm.isPass) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.kind_card), vm.kind == CardKind.CARD) { vm.kind = CardKind.CARD }
                    Chip(stringResource(R.string.kind_ticket), vm.kind == CardKind.TICKET) { vm.kind = CardKind.TICKET }
                }
                Field(vm.value, { vm.value = it }, stringResource(R.string.edit_number), keyboard = KeyboardType.Ascii)
                FormatPicker(vm)
            }

            Text(stringResource(R.string.edit_color), color = c.muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardColors.palette.forEach { col ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .border(if (col == vm.color) 3.dp else 1.dp, if (col == vm.color) c.text else c.line, CircleShape)
                            .clickable { vm.color = col },
                    )
                }
            }
            Field(vm.note, { vm.note = it }, stringResource(R.string.info_note), singleLine = false)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
) {
    val c = StackdTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.text,
            unfocusedBorderColor = c.line,
            focusedLabelColor = c.text,
            unfocusedLabelColor = c.muted,
            cursorColor = c.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = StackdTheme.colors
    Text(
        label,
        color = if (selected) c.bg else c.text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.text else c.bg)
            .border(1.dp, if (selected) c.text else c.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPicker(vm: EditCardViewModel) {
    var open by remember { mutableStateOf(false) }
    val auto = stringResource(R.string.edit_format_auto)
    val text = when (val f = vm.format) {
        null -> vm.effectiveFormat?.let { "$auto · ${it.label}" } ?: auto
        else -> f.label
    }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        val c = StackdTheme.colors
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_format)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.text,
                unfocusedBorderColor = c.line,
                focusedLabelColor = c.text,
                unfocusedLabelColor = c.muted,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.bg) {
            DropdownMenuItem(text = { Text(auto) }, onClick = { vm.format = null; open = false })
            BarcodeFormat.entries.forEach { f ->
                DropdownMenuItem(text = { Text(f.label) }, onClick = { vm.format = f; open = false })
            }
        }
    }
}
