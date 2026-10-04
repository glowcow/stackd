package dev.glowcow.stackd

import android.app.Application
import android.content.Context
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.data.SettingsStore
import dev.glowcow.stackd.data.StackdDatabase
import dev.glowcow.stackd.pkpass.PassImporter
import dev.glowcow.stackd.update.AppUpdateScheduler
import dev.glowcow.stackd.update.AppUpdater
import dev.glowcow.stackd.update.PassUpdater
import dev.glowcow.stackd.update.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val db = StackdDatabase.create(context)
    val cards = CardRepository(db.cards(), context.filesDir)
    val settings = SettingsStore(context)
    val importer = PassImporter(context, cards)
    val updater = PassUpdater(cards, importer)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val appUpdater = AppUpdater(context, scope)
}

class StackdApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            if (container.settings.firstRun("pass_update_info")) container.importer.backfill()
        }
        container.scope.launch {
            container.settings.settings.map { it.autoUpdate to it.updateHours }.distinctUntilChanged()
                .collect { (on, hours) -> UpdateScheduler.apply(this@StackdApp, on, hours) }
        }
        container.scope.launch {
            container.settings.settings.map { it.appUpdate }.distinctUntilChanged()
                .collect { AppUpdateScheduler.apply(this@StackdApp, it && container.appUpdater.supported) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as StackdApp).container
