package dev.glowcow.stackd.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glowcow.stackd.R
import dev.glowcow.stackd.StackdApp
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.data.StackOrder
import dev.glowcow.stackd.ui.components.BottomBar
import dev.glowcow.stackd.ui.components.CardAvatar
import dev.glowcow.stackd.ui.components.TopTab
import dev.glowcow.stackd.ui.components.cardMeta
import dev.glowcow.stackd.ui.components.kindLabel
import dev.glowcow.stackd.ui.theme.InstrumentSans
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(repo: CardRepository) : ViewModel() {
    val query = MutableStateFlow("")

    val results: StateFlow<List<Card>> = combine(repo.cards, query) { all, q ->
        val needle = q.trim()
        val hits = if (needle.isEmpty()) all else all.filter { c ->
            listOfNotNull(c.name, c.subtitle, c.note, c.barcodeValue, c.barcodeAltText).any { it.contains(needle, ignoreCase = true) }
        }
        StackOrder.sort(hits)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SearchScreen(
    onOpen: (String) -> Unit,
    onTab: (TopTab) -> Unit,
    vm: SearchViewModel = viewModel { SearchViewModel((this[APPLICATION_KEY] as StackdApp).container.cards) },
) {
    val c = StackdTheme.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.chip)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(StackdIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = c.muted)
                BasicTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontFamily = InstrumentSans, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.id }) { card -> CardRow(card) { onOpen(card.id) } }
            if (results.isEmpty() && query.isNotBlank()) {
                item { Text(stringResource(R.string.search_empty), color = c.muted, modifier = Modifier.padding(16.dp)) }
            }
        }
        BottomBar(TopTab.SEARCH, onTab)
    }
}

/** List row from mockup A. */
@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    val c = StackdTheme.colors
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardAvatar(card)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.name, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    cardMeta(card)?.let { Text("· $it", color = c.muted, maxLines = 1) }
                }
                Text(listOfNotNull(card.subtitle, kindLabel(card)).joinToString(" · "), color = c.muted, fontSize = 14.sp, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}
