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
    fun splitsAnnouncedChangesFromTheRest() {
        val old = card(
            PassField(FieldSection.HEADER, "balance", "Balance", "120", changeMessage = "Your balance is now %@"),
            PassField(FieldSection.SECONDARY, "level", "Level", "Gold"),
            PassField(FieldSection.SECONDARY, "tier", "Tier", "1"),
            PassField(FieldSection.AUXILIARY, "asOf", "As of", "04.10.2026", isDate = true),
            PassField(FieldSection.BACK, "terms", "Terms", "v1"),
        )
        val new = card(
            PassField(FieldSection.HEADER, "balance", "Balance", "150", changeMessage = "Your balance is now %@"),
            PassField(FieldSection.SECONDARY, "level", "Level", "Gold"),
            PassField(FieldSection.SECONDARY, "tier", "Tier", "2"),
            PassField(FieldSection.AUXILIARY, "asOf", "As of", "05.10.2026", isDate = true),
            PassField(FieldSection.AUXILIARY, "promo", null, "Free cup on Friday"),
            PassField(FieldSection.BACK, "terms", "Terms", "v2"),
        )
        val change = PassUpdater.change(old, new)
        assertEquals(listOf("Your balance is now 150"), change.announced)
        assertEquals(listOf("Tier: 1 → 2", "Free cup on Friday"), change.other)
    }

    @Test
    fun reportsNothingForTheSamePass() {
        val pass = card(PassField(FieldSection.HEADER, "balance", "Balance", "120", changeMessage = "Balance: %@"))
        val change = PassUpdater.change(pass, pass)
        assertEquals(emptyList<String>(), change.announced)
        assertEquals(emptyList<String>(), change.other)
    }
}
