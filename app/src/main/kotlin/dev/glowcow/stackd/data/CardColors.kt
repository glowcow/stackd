package dev.glowcow.stackd.data

object CardColors {
    const val INK_DARK = 0xFF141413.toInt()
    const val INK_LIGHT = 0xFFFAF9F5.toInt()

    /** Card palette: the muted design colours first, then vivid and deep ones. */
    val palette = listOf(
        0xFFD97757, 0xFF6A9BCC, 0xFF788C5D, 0xFFCBCADB, 0xFFE3DACC, 0xFFBCD1CA, 0xFF141413,
        0xFFE53935, 0xFFF57C00, 0xFFFBC02D, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA,
        0xFFD81B60, 0xFF5E35B1, 0xFF3949AB, 0xFF00897B, 0xFF6D4C41, 0xFF546E7A, 0xFFFFFFFF,
    ).map { it.toInt() }

    fun inkFor(bg: Int): Int {
        fun channel(shift: Int): Double {
            val c = ((bg shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        return if (luminance > 0.18) INK_DARK else INK_LIGHT
    }

    /** Stable palette pick for a new card so the stack doesn't come out single-colored. */
    fun forName(name: String): Int = palette[Math.floorMod(name.hashCode(), palette.size)]
}
