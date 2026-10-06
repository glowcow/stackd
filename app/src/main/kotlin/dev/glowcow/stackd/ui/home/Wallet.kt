package dev.glowcow.stackd.ui.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.ui.card.OpenCard
import dev.glowcow.stackd.ui.components.CARD_ASPECT
import dev.glowcow.stackd.ui.components.CardFace
import dev.glowcow.stackd.ui.components.CardShape
import dev.glowcow.stackd.ui.components.cardMeta
import dev.glowcow.stackd.ui.components.rememberPassImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

/** Scroll, rubber band and the opened card of one wallet page. */
@Stable
class WalletState(selected: String?) {
    /** Id of the opened card, kept while it closes. */
    var selected by mutableStateOf(selected)
        private set

    /** 0 = stack, 1 = card open. */
    var progress by mutableFloatStateOf(if (selected != null) 1f else 0f)
        private set

    /** True from the moment a card is told to open until it is told to close. */
    var settlingOpen by mutableStateOf(selected != null)
        private set

    // Distance scrolled up from the bottom of the deck (0 shows the front card) and the raw pull beyond the ends.
    internal var scroll by mutableFloatStateOf(0f)
    internal var overscroll by mutableFloatStateOf(0f)
    internal var maxScroll = 0f
    internal lateinit var scope: CoroutineScope
    private var job: Job? = null

    /** Visible stretch: positive spreads the deck downwards, negative squeezes it. */
    internal val stretch: Float
        get() = sign(overscroll) * STRETCH_LIMIT * (1f - 1f / (abs(overscroll) * 0.55f / STRETCH_LIMIT + 1f))

    val isOpen: Boolean get() = selected != null

    fun open(id: String) {
        selected = id
        animateProgress(1f)
    }

    fun close() = animateProgress(0f)

    /** Drops the selection at once, e.g. when the card was deleted. */
    fun reset() {
        job?.cancel()
        progress = 0f
        settlingOpen = false
        selected = null
    }

    /** Follows a predictive back gesture; [fraction] runs 0 → 1 as the finger moves. */
    internal fun seekClose(start: Float, fraction: Float) {
        job?.cancel()
        progress = start * (1f - fraction)
    }

    internal fun drag(delta: Float, closeDistance: Float) {
        job?.cancel()
        if (selected != null) {
            progress = (progress - delta / closeDistance).coerceIn(0f, 1f)
            return
        }
        val pos = scroll + overscroll + delta
        scroll = pos.coerceIn(0f, maxScroll)
        overscroll = pos - scroll
    }

    internal fun release(velocity: Float) {
        if (selected != null) {
            animateProgress(if (progress < 0.75f || velocity > 1800f) 0f else 1f)
            return
        }
        job?.cancel()
        job = scope.launch {
            if (overscroll != 0f) {
                animate(overscroll, 0f, velocity, SPRING_BACK) { v, _ -> overscroll = v }
                return@launch
            }
            // Fling; hitting an end turns the rest of the speed into a bounce.
            var hit = 0f
            AnimationState(scroll, velocity).animateDecay(exponentialDecay()) {
                if (value in 0f..maxScroll) {
                    scroll = value
                } else {
                    scroll = value.coerceIn(0f, maxScroll)
                    hit = this.velocity
                    cancelAnimation()
                }
            }
            if (hit != 0f) animate(0f, 0f, hit * BOUNCE, SPRING_BACK) { v, _ -> overscroll = v }
        }
    }

    private fun animateProgress(target: Float) {
        job?.cancel()
        settlingOpen = target > 0f
        job = scope.launch {
            animate(progress, target, 0f, if (target > 0f) OPEN_SPRING else CLOSE_SPRING) { v, _ -> progress = v }
            if (target == 0f) selected = null
        }
    }

    companion object {
        private const val STRETCH_LIMIT = 700f
        private const val BOUNCE = 0.5f
        private val SPRING_BACK = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        // Progress spans the whole card travel: stop below a pixel, the default 0.01 ends with a visible jump.
        private const val PROGRESS_THRESHOLD = 0.0005f
        private val OPEN_SPRING = spring(0.82f, 550f, PROGRESS_THRESHOLD)
        private val CLOSE_SPRING = spring(Spring.DampingRatioNoBouncy, 550f, PROGRESS_THRESHOLD)

        val Saver = Saver<WalletState, String>(save = { it.selected.orEmpty() }, restore = { WalletState(it.ifEmpty { null }) })
    }
}

@Composable
fun rememberWalletState(): WalletState {
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = WalletState.Saver) { WalletState(null) }.also { it.scope = scope }
}

/**
 * The deck, with the pinned cards as a second stack below it. Tapping a card lifts it to the top and opens it in place; the rest slide into a pile at the
 * bottom. Drag scrolls with a rubber band at the ends; on an open card, dragging down closes it.
 */
@Composable
fun Wallet(
    cards: List<Card>,
    state: WalletState,
    top: Dp,
    bottom: Dp,
    bright: Boolean,
    onOpen: (String) -> Unit,
    actions: @Composable (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The most relevant card sits in front, at the bottom of the deck.
    val deck = cards.asReversed()
    // Pinned cards end the deck; they form a stack of their own below the rest.
    val pinnedStart = deck.indexOfFirst { it.pinned }.takeIf { it > 0 } ?: deck.size
    val opened = deck.firstOrNull { it.id == state.selected }
    LaunchedEffect(state.selected, opened) { if (state.selected != null && opened == null) state.reset() }

    val drag = rememberDraggableState { state.drag(it, closeDistance = 900f) }
    Layout(
        content = {
            deck.forEachIndexed { i, card ->
                key(card.id) {
                    WalletCard(
                        card = card,
                        open = card.id == state.selected,
                        bright = bright,
                        front = i == deck.lastIndex || i == pinnedStart - 1,
                        back = i == 0 || i == pinnedStart,
                        progress = { state.progress },
                        onClick = { if (state.isOpen) state.close() else onOpen(card.id) },
                    )
                }
            }
            Box { opened?.let { actions(it) } }
        },
        modifier = modifier
            .clipToBounds()
            .draggable(drag, Orientation.Vertical, onDragStopped = { state.release(it) }),
    ) { measurables, constraints ->
        val side = 16.dp.roundToPx()
        // The deck lives between the header and the tab bar; its cards run on under the bar.
        val origin = top.roundToPx()
        val top = 16.dp.roundToPx()
        val step = 64.dp.roundToPx()
        val pileGap = 10.dp.roundToPx()
        val pilePeek = 30.dp.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight - origin - bottom.roundToPx()
        val child = Constraints(minWidth = width - 2 * side, maxWidth = width - 2 * side)
        val cardHeight = ((width - 2 * side) / CARD_ASPECT).roundToInt()

        val n = deck.size
        val placeables = measurables.take(n).map { it.measure(child) }
        val actionsPlaceable = measurables.last().measure(Constraints(maxWidth = width))

        // Cards fan out down the screen: tight under the top edge, wide at the bottom; above it they pile up.
        val pile = 12.dp.toPx()
        fun fan(u: Float): Float {
            if (u < 0f) return -pile * (1f - exp(FAN_TOP * u / pile))
            val t = minOf(u / height, 1f)
            return height * (FAN_TOP * t + (FAN_BOTTOM - FAN_TOP) * t * t / 2f) + FAN_BOTTOM * maxOf(u - height, 0f)
        }
        // The last card above the pinned stack shows in full, then a gap.
        val pinGap = cardHeight + 20.dp.roundToPx()
        fun cardY(i: Int, shift: Float): Float {
            val y = top + fan(i * step - shift)
            return if (i >= pinnedStart) y + pinGap - (fan(pinnedStart * step - shift) - fan((pinnedStart - 1) * step - shift)) else y
        }
        // Scroll runs in deck space: find the shift that brings the front card to the bottom of the screen.
        val room = height - cardHeight - 96.dp.roundToPx()
        var maxShift = 0f
        if (n > 0 && cardY(n - 1, 0f) > room) {
            var hi = (n - 1) * step.toFloat()
            repeat(24) {
                val mid = (maxShift + hi) / 2f
                if (cardY(n - 1, mid) > room) maxShift = mid else hi = mid
            }
        }
        state.maxScroll = maxShift
        val shift = state.maxScroll - state.scroll.coerceIn(0f, state.maxScroll) - state.stretch
        val p = state.progress
        val selectedIndex = deck.indexOfFirst { it.id == state.selected }
        val others = if (selectedIndex < 0) n else n - 1
        val pileShown = others.coerceAtMost(4)
        val pileTop = height - pilePeek - (pileShown - 1).coerceAtLeast(0) * pileGap

        layout(width, constraints.maxHeight) {
            var j = 0
            placeables.forEachIndexed { i, placeable ->
                val stackY = cardY(i, shift)
                if (i == selectedIndex) {
                    // Stack order while moving, so the cards in front slide back over it; on top once open.
                    placeable.place(side, origin + lerp(stackY, top.toFloat(), p).roundToInt(), zIndex = if (p > 0.98f) n + 1f else i.toFloat())
                } else {
                    val slot = (j - (others - pileShown)).coerceAtLeast(0)
                    val pileY = (pileTop + slot * pileGap).toFloat()
                    placeable.place(side, origin + lerp(stackY, pileY, p).roundToInt(), zIndex = i.toFloat())
                    j++
                }
            }
            if (selectedIndex >= 0) {
                val below = origin + top + placeables[selectedIndex].height + 8.dp.roundToPx()
                actionsPlaceable.placeWithLayer(0, below, zIndex = n + 2f) { alpha = p }
            }
        }
    }
}

/**
 * One card of the deck. Open, its bottom edge slides from the stack face height to the full card as [progress]
 * goes 0 → 1; the stack face blends in only near the stack size. Tapping the open card closes it.
 */
@Composable
private fun WalletCard(
    card: Card,
    open: Boolean,
    bright: Boolean,
    front: Boolean,
    back: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
) {
    Box(
        // The card at the back of a stack is flat until it opens.
        Modifier
            .graphicsLayer {
                shadowElevation = 6.dp.toPx() * if (!back) 1f else if (open) progress() else 0f
                shape = CardShape
            }
            .then(
                if (open) {
                    // Clip outside the size change, so the card is cut at its current height, not its full one.
                    Modifier
                        .clip(CardShape)
                        .layout { measurable, constraints ->
                            // An open card is never shorter than its stack face.
                            val face = (constraints.maxWidth / CARD_ASPECT).roundToInt()
                            val full = measurable.measure(constraints.copy(minHeight = face, maxHeight = Constraints.Infinity))
                            // The bottom edge leads the travel, so the card is back to stack size before it reaches the deck.
                            val t = progress()
                            val h = lerp(face.toFloat(), full.height.toFloat(), t * t).roundToInt()
                            layout(full.width, h) { full.place(0, 0) }
                        }
                        .clickable(interactionSource = null, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        // The open card fills the face height when its own content is shorter.
        propagateMinConstraints = true,
    ) {
        if (open) OpenCard(card, bright)
        CardFace(
            card = card,
            meta = cardMeta(card),
            expanded = front,
            logo = rememberPassImage(card, "logo"),
            onClick = if (open) null else onClick,
            modifier = if (open) Modifier.graphicsLayer { alpha = (1f - progress() / FACE_BLEND).coerceIn(0f, 1f) } else Modifier,
        )
    }
}

/** Share of the deck step shown between cards at the top and at the bottom of the screen. */
private const val FAN_TOP = 0.6f
private const val FAN_BOTTOM = 2.1f

/** Share of the travel, next to the stack, where the open card blends into its stack face. */
private const val FACE_BLEND = 0.25f
