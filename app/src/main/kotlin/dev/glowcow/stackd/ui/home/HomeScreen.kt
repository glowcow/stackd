package dev.glowcow.stackd.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glowcow.stackd.R
import dev.glowcow.stackd.StackdApp
import dev.glowcow.stackd.data.AppSettings
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.data.SettingsStore
import dev.glowcow.stackd.data.StackOrder
import dev.glowcow.stackd.ui.CardDraft
import dev.glowcow.stackd.ui.card.CardActions
import dev.glowcow.stackd.ui.card.CardDetailsSheet
import dev.glowcow.stackd.ui.card.ScreenBrightness
import dev.glowcow.stackd.ui.components.StackdLogo
import dev.glowcow.stackd.ui.components.TabScreen
import dev.glowcow.stackd.ui.components.TopTab
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.update.PassUpdater
import dev.glowcow.stackd.update.UpdateOutcome
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class KindFilter(val label: Int) {
    ALL(R.string.filter_all),
    CARDS(R.string.filter_cards),
    TICKETS(R.string.filter_tickets);

    fun matches(card: Card): Boolean = when (this) {
        ALL -> true
        CARDS -> card.kind == CardKind.CARD
        TICKETS -> card.kind == CardKind.TICKET
    }
}

class HomeViewModel(private val repo: CardRepository, private val updater: PassUpdater, settings: SettingsStore) : ViewModel() {
    /** Id of the pass being fetched from its issuer. */
    var updating by mutableStateOf<String?>(null)
        private set

    /** Front card first; null while loading. */
    val cards: StateFlow<List<Card>?> = repo.cards.map(StackOrder::sort)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markUsed(id: String) = viewModelScope.launch { repo.markUsed(id) }
    fun togglePin(card: Card) = viewModelScope.launch { repo.setPinned(card.id, !card.pinned) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun update(card: Card, onResult: (UpdateOutcome) -> Unit) {
        if (updating != null) return
        updating = card.id
        viewModelScope.launch {
            val outcome = updater.update(card)
            updating = null
            onResult(outcome)
        }
    }

    fun share(context: Context, card: Card) {
        val archive = repo.passArchive(card.id)
        val intent = if (card.isPass && archive.isFile) {
            Intent(Intent.ACTION_SEND)
                .setType("application/vnd.apple.pkpass")
                .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.files", archive))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, listOfNotNull(card.name, card.barcodeValue).joinToString("\n"))
        }
        context.startActivity(Intent.createChooser(intent, card.name))
    }
}

@Composable
fun HomeScreen(
    openRequest: String?,
    onOpenHandled: () -> Unit,
    onScan: (photo: Boolean) -> Unit,
    onDraft: (CardDraft) -> Unit,
    onImported: (String) -> Unit,
    onEdit: (String) -> Unit,
    onTab: (TopTab) -> Unit,
    vm: HomeViewModel = viewModel {
        val app = this[APPLICATION_KEY] as StackdApp
        HomeViewModel(app.container.cards, app.container.updater, app.container.settings)
    },
) {
    val c = StackdTheme.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pager = rememberPagerState { KindFilter.entries.size }
    val scope = rememberCoroutineScope()
    val wallets = KindFilter.entries.map { rememberWalletState() }
    val wallet = wallets[pager.currentPage]
    var details by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val sources = rememberAddSources(onDraft, onImported)

    fun open(id: String) {
        vm.markUsed(id)
        wallet.open(id)
    }

    // Cards opened from search, the scanner or another app show up on the "All" page.
    LaunchedEffect(openRequest, cards) {
        val id = openRequest ?: return@LaunchedEffect
        if (cards?.any { it.id == id } != true) return@LaunchedEffect
        pager.scrollToPage(0)
        vm.markUsed(id)
        wallets[0].open(id)
        onOpenHandled()
    }

    // Released as soon as the card starts closing, so the screen does not dim at the very end.
    val bright = settings?.maxBrightness ?: true
    if (wallet.settlingOpen) ScreenBrightness(bright)
    PredictiveBackHandler(enabled = wallet.isOpen) { events ->
        val start = wallet.progress
        try {
            events.collect { wallet.seekClose(start, it.progress) }
            wallet.close()
        } catch (e: CancellationException) {
            wallet.open(wallet.selected ?: throw e)
            throw e
        }
    }

    TabScreen(
        TopTab.CARDS,
        onTab,
        ground = c.bg,
        header = {
            Box(Modifier.graphicsLayer { alpha = 1f - wallet.progress }) {
                FilterTabs(pager) { if (!wallet.isOpen) scope.launch { pager.animateScrollToPage(it) } }
            }
        },
        floating = { bottom ->
            if (wallet.progress < 1f) {
                // The fading layer spans the screen: one the size of the button cuts its shadow to a square.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - wallet.progress }) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = bottom + 20.dp)
                            .size(56.dp)
                            .shadow(10.dp, CircleShape)
                            .clip(CircleShape)
                            .background(c.text)
                            .clickable(enabled = !wallet.isOpen) { adding = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(StackdIcons.Plus, stringResource(R.string.add_card), tint = c.bg, modifier = Modifier.size(22.dp))
                    }
                }
            }
        },
    ) { top, bottom ->
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                pager,
                Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !wallet.isOpen,
                verticalAlignment = Alignment.Top,
            ) { page ->
                val filter = KindFilter.entries[page]
                val list = cards
                val shown = remember(list, filter) { list?.filter(filter::matches) }
                when {
                    shown == null -> Unit
                    shown.isEmpty() -> EmptyStack(filter, Modifier.padding(top = top, bottom = bottom))
                    else -> Wallet(
                        cards = shown,
                        state = wallets[page],
                        top = top,
                        bottom = bottom,
                        bright = bright,
                        onOpen = ::open,
                        actions = { card ->
                            CardActions(
                                card = card,
                                onShare = { vm.share(context, card) },
                                onEdit = { onEdit(card.id) },
                                onPin = { vm.togglePin(card) },
                                onDetails = { details = true },
                                updating = vm.updating == card.id,
                                onUpdate = if (card.canUpdate) {
                                    {
                                        vm.update(card) { outcome ->
                                            // What changed is said in the words a notification would use.
                                            val lines = (outcome as? UpdateOutcome.Updated)?.change?.let {
                                                if (settings?.notifyAllChanges == true) it.announced + it.other else it.announced
                                            }.orEmpty()
                                            val text = when (outcome) {
                                                is UpdateOutcome.Updated -> lines.take(2).joinToString("\n").ifEmpty { resources.getString(R.string.update_done) }
                                                UpdateOutcome.Unchanged -> resources.getString(R.string.update_same)
                                                UpdateOutcome.Failed -> resources.getString(R.string.update_failed)
                                            }
                                            Toast.makeText(context, text, if (lines.isEmpty()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                            if (details) {
                                CardDetailsSheet(
                                    card = card,
                                    onDismiss = { details = false },
                                    onDelete = {
                                        details = false
                                        wallets[page].reset()
                                        vm.delete(card.id)
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (adding) {
        AddSheet(
            onDismiss = { adding = false },
            onFile = sources.pickFile,
            onImage = sources.pickImage,
            onPhoto = { onScan(true) },
            onManual = { onDraft(CardDraft(source = CardSource.MANUAL)) },
            onScan = { onScan(false) },
        )
    }
}

@Composable
private fun FilterTabs(pager: PagerState, onSelect: (Int) -> Unit) {
    val c = StackdTheme.colors
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
            val tabWidth = maxWidth / KindFilter.entries.size
            Row(Modifier.fillMaxSize()) {
                KindFilter.entries.forEachIndexed { i, f ->
                    val active = i == pager.targetPage
                    Box(Modifier.weight(1f).fillMaxHeight().padding(bottom = 4.dp), contentAlignment = Alignment.BottomCenter) {
                        // The press highlight is a pill around the label, not the whole tab.
                        Text(
                            stringResource(f.label),
                            color = if (active) c.text else c.muted,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier.clip(CircleShape).clickable { onSelect(i) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            // Follows the finger while swiping between pages.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset {
                        val x = tabWidth * (pager.currentPage + pager.currentPageOffsetFraction) + (tabWidth - INDICATOR_WIDTH) / 2
                        IntOffset(x.roundToPx(), 0)
                    }
                    .width(INDICATOR_WIDTH)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

private val INDICATOR_WIDTH = 44.dp

@Composable
private fun EmptyStack(filter: KindFilter, modifier: Modifier = Modifier) {
    val c = StackdTheme.colors
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackdLogo(Modifier.padding(bottom = 16.dp))
        Text(
            stringResource(if (filter == KindFilter.TICKETS) R.string.empty_tickets_title else R.string.empty_title),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = c.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.empty_hint), color = c.muted, textAlign = TextAlign.Center)
    }
}
