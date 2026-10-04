package dev.glowcow.stackd.update

import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.pkpass.FieldSection
import dev.glowcow.stackd.pkpass.PassField
import org.junit.Assert.assertEquals
import org.junit.Test

class PassUpdaterTest {

    private fun card(vararg fields: PassField) = Card(
        id = "1",
        kind = CardKind.CARD,
        source = CardSource.PKPASS,
        name = "Coffee",
        bgColor = 0,
        fgColor = 0,
        createdAt = 0,
        fieldsJson = Card.encodeFields(fields.toList()),
    )

    @Test
    fun listsChangedAndNewFields() {
        val old = card(
            PassField(FieldSection.HEADER, "balance", "Balance", "120"),
            PassField(FieldSection.SECONDARY, "level", "Level", "Gold"),
        )
        val new = card(
            PassField(FieldSection.HEADER, "balance", "Balance", "150"),
            PassField(FieldSection.SECONDARY, "level", "Level", "Gold"),
            PassField(FieldSection.BACK, "promo", null, "Free cup on Friday"),
        )
        assertEquals(listOf("Balance: 150", "Free cup on Friday"), PassUpdater.changedLines(old, new))
        assertEquals(emptyList<String>(), PassUpdater.changedLines(new, new))
    }
}
