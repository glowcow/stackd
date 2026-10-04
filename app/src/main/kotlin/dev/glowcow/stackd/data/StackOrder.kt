package dev.glowcow.stackd.data

/** Orders the stack: pinned cards first, then the most recently added. */
object StackOrder {
    fun sort(cards: List<Card>): List<Card> =
        cards.sortedWith(compareBy<Card> { !it.pinned }.thenByDescending { it.createdAt }.thenBy { it.id })
}
