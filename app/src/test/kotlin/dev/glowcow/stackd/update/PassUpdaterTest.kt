package dev.glowcow.stackd.update

import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.pkpass.FieldSection
import dev.glowcow.stackd.pkpass.PassField
import org.junit.Assert.assertEquals
import org.junit.Test

class PassUpdaterTest {

    private val was = { new: String, old: String -> "$new (was $old)" }

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
        val change = PassUpdater.change(old, new, was)
        assertEquals(listOf("Your balance is now 150"), change.announced)
        assertEquals(listOf("Tier: 2 (was 1)", "Free cup on Friday"), change.other)
    }

    @Test
    fun reportsNothingForTheSamePass() {
        val pass = card(PassField(FieldSection.HEADER, "balance", "Balance", "120", changeMessage = "Balance: %@"))
        val change = PassUpdater.change(pass, pass, was)
        assertEquals(emptyList<String>(), change.announced)
        assertEquals(emptyList<String>(), change.other)
    }

    @Test
    fun announcesTheFrontOfAPassThatMarksNothing() {
        // A loyalty card as some issuers make it: a balance and an "as of" date, no changeMessage anywhere.
        val old = card(
            PassField(FieldSection.SECONDARY, "balance", "Points", "40"),
            PassField(FieldSection.SECONDARY, "date", "As of", "01.10.2026", isDate = true),
            PassField(FieldSection.BACK, "id", "Member", "000111222333"),
        )
        val new = card(
            PassField(FieldSection.SECONDARY, "balance", "Points", "55"),
            PassField(FieldSection.SECONDARY, "date", "As of", "08.10.2026", isDate = true),
            PassField(FieldSection.BACK, "id", "Member", "000111222444"),
        )
        val change = PassUpdater.change(old, new, was)
        assertEquals(listOf("Points: 55 (was 40)"), change.announced)
        assertEquals(emptyList<String>(), change.other)
    }

    @Test
    fun aDateAloneIsNotAnnounced() {
        val old = card(PassField(FieldSection.SECONDARY, "date", "As of", "01.10.2026", isDate = true))
        val new = card(PassField(FieldSection.SECONDARY, "date", "As of", "08.10.2026", isDate = true))
        assertEquals(emptyList<String>(), PassUpdater.change(old, new, was).announced)
    }
}
