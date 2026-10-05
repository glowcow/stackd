package dev.glowcow.stackd.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dev.glowcow.stackd.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keeps the periodic background update in line with the settings. */
object UpdateScheduler {
    private const val JOB_ID = 1
    private const val HOUR_MS = 3_600_000L

    fun apply(context: Context, enabled: Boolean, hours: Int) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val period = hours.coerceAtLeast(1) * HOUR_MS
        val pending = scheduler.getPendingJob(JOB_ID)
        when {
            !enabled -> scheduler.cancel(JOB_ID)
            // Rescheduling restarts the period, so an unchanged job is left alone.
            pending?.intervalMillis == period -> Unit
            else -> scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, PassUpdateService::class.java))
                    .setPeriodic(period)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}

/** Background run: updates every pass and notifies about the changes. */
class PassUpdateService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = container.scope.launch {
            val changes = container.updater.updateAll()
            UpdateNotifier.notify(this@PassUpdateService, changes, container.settings.settings.first().notifyAllChanges)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }
}
