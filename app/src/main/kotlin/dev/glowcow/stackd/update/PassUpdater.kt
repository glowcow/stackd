package dev.glowcow.stackd.update

import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.pkpass.FieldSection
import dev.glowcow.stackd.pkpass.PassImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * An updated pass: [announced] are the changes its issuer asks to notify about, or every change
 * on the front of a pass with no such marks; [other] the remaining ones on the front. Dates aside.
 */
class PassChange(val card: Card, val announced: List<String>, val other: List<String>)

sealed interface UpdateOutcome {
    class Updated(val change: PassChange) : UpdateOutcome
    data object Unchanged : UpdateOutcome
    data object Failed : UpdateOutcome
}

/**
 * Fetches fresh copies of passes from the web service named in them (Apple's PassKit web service:
 * `GET {webServiceURL}/v1/passes/{passTypeIdentifier}/{serialNumber}`). Issuers cannot push to us, so we poll.
 */
class PassUpdater(
    private val repo: CardRepository,
    private val importer: PassImporter,
    /** Words a new value next to the one it replaced. */
    private val was: (new: String, old: String) -> String,
) {

    suspend fun update(card: Card): UpdateOutcome {
        if (!card.canUpdate) return UpdateOutcome.Failed
        val response = withContext(Dispatchers.IO) { runCatching { fetch(card) }.getOrNull() } ?: return UpdateOutcome.Failed
        return when (response.code) {
            HttpsURLConnection.HTTP_NOT_MODIFIED -> {
                repo.markChecked(card.id)
                UpdateOutcome.Unchanged
            }
            HttpsURLConnection.HTTP_OK -> {
                val body = response.body ?: return UpdateOutcome.Failed
                val fresh = runCatching { importer.replace(card, body, response.lastModified) }.getOrNull() ?: return UpdateOutcome.Failed
                if (fresh.content() == card.content()) UpdateOutcome.Unchanged else UpdateOutcome.Updated(change(card, fresh, was))
            }
            else -> UpdateOutcome.Failed
        }
    }

    /** Updates every pass that names a service and returns the ones that changed. */
    suspend fun updateAll(): List<PassChange> =
        repo.cards.first().filter { it.canUpdate }.mapNotNull { (update(it) as? UpdateOutcome.Updated)?.change }

    private class Response(val code: Int, val body: ByteArray?, val lastModified: String?)

    private fun fetch(card: Card): Response {
        val url = URI("${card.webServiceUrl!!.trimEnd('/')}/v1/passes/${segment(card.passTypeId!!)}/${segment(card.serial!!)}").toURL()
        val c = url.openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            // The token must not follow a redirect to another host.
            c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "ApplePass ${card.authToken}")
            card.lastModified?.let { c.setRequestProperty("If-Modified-Since", it) }
            val code = c.responseCode
            val body = if (code == HttpsURLConnection.HTTP_OK) {
                c.inputStream.use { it.readNBytes(MAX_BYTES + 1) }.also { require(it.size <= MAX_BYTES) { "The pass is too large" } }
            } else {
                null
            }
            return Response(code, body, c.getHeaderField("Last-Modified"))
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 20_000
        private const val MAX_BYTES = 20 * 1024 * 1024

        private fun segment(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** The card without the bookkeeping of the update itself. */
        private fun Card.content() = copy(lastModified = null, updatedAt = null)

        /**
         * Fields that are new or have another value, worded for a notification. A pass whose issuer
         * marks no field at all does not use the marks, so its front changes are announced.
         */
        internal fun change(old: Card, new: Card, was: (new: String, old: String) -> String): PassChange {
            val before = old.fields().associate { (it.section to it.key) to it.value }
            val fields = new.fields()
            val changed = fields.filter { before[it.section to it.key] != it.value }
            val (marked, plain) = changed.partition { it.changeMessage != null }
            val front = plain.filter { it.section != FieldSection.BACK && !it.isDate }.map { f ->
                val value = before[f.section to f.key]?.let { was(f.value, it) } ?: f.value
                f.label?.let { "$it: $value" } ?: value
            }
            val unmarked = fields.none { it.changeMessage != null }
            return PassChange(
                new,
                marked.map { it.changeMessage!!.replace("%@", it.value) } + if (unmarked) front else emptyList(),
                if (unmarked) emptyList() else front,
            )
        }
    }
}
