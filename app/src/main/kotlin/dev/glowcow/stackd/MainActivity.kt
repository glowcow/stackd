package dev.glowcow.stackd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.glowcow.stackd.data.AppSettings
import dev.glowcow.stackd.ui.StackdRoot
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val openCard = Channel<String>(Channel.BUFFERED)
    private var handled: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A restored activity may still carry a file it has not imported yet.
        handled = savedInstanceState?.getString(KEY_HANDLED)
        handleIntent(intent)

        val flow = openCard.receiveAsFlow()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            StackdTheme(settings.theme) {
                StackdRoot(openCard = flow)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_HANDLED, handled)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handled = null
        handleIntent(intent)
    }

    /** Opens a .pkpass handed over by mail, a browser or a file manager, or the card of a notification. */
    private fun handleIntent(intent: Intent?) {
        // A tap on an update notification.
        intent?.getStringExtra(EXTRA_CARD_ID)?.let { id ->
            if ("card:$id" != handled) openCard.trySend(id)
            handled = "card:$id"
            return
        }
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        if (uri.toString() == handled) return
        handled = uri.toString()
        lifecycleScope.launch {
            runCatching { container.importer.import(uri) }
                .onSuccess { ids ->
                    if (ids.size > 1) Toast.makeText(this@MainActivity, getString(R.string.import_many, ids.size), Toast.LENGTH_SHORT).show()
                    ids.firstOrNull()?.let { openCard.send(it) }
                }
                .onFailure { Toast.makeText(this@MainActivity, R.string.import_failed, Toast.LENGTH_LONG).show() }
        }
    }

    companion object {
        const val EXTRA_CARD_ID = "card_id"
        private const val KEY_HANDLED = "handled_uri"
    }
}
