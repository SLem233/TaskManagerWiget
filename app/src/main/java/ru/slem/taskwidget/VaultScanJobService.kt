package ru.slem.taskwidget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class VaultScanJobService : JobService() {
    private class Run {
        val cancelled = AtomicBoolean(false)
        lateinit var worker: Thread
        fun cancel() {
            cancelled.set(true)
            worker.interrupt()
        }
    }

    private val runs = ConcurrentHashMap<Int, Run>()
    private val main = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters): Boolean {
        val run = Run()
        run.worker = Thread {
            var succeeded = false
            try {
                if (WidgetPreferences.vaultUri(this) != null) {
                    succeeded = VaultRepository.scan(this).errors.isEmpty()
                }
            } catch (cancelled: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                if (!run.cancelled.get()) {
                    WidgetPreferences.setScanError(this, error.message ?: "Ошибка сканирования")
                }
            } finally {
                // JobService callbacks and completion run on the same main queue.
                main.post {
                    if (runs[params.jobId] === run && !run.cancelled.get()) {
                        WidgetPreferences.setRefreshState(this, if (succeeded)
                            WidgetRefreshState.SUCCESS else WidgetRefreshState.FAILURE)
                        try { TaskWidgetProvider.refreshAll(this) }
                        finally {
                            runs.remove(params.jobId, run)
                            jobFinished(params, false)
                        }
                    } else runs.remove(params.jobId, run)
                }
            }
        }
        runs.put(params.jobId, run)?.cancel()
        run.worker.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val run = runs.remove(params.jobId) ?: return false
        run.cancel()
        WidgetPreferences.setRefreshState(this, WidgetRefreshState.FAILURE)
        TaskWidgetProvider.refreshAll(this)
        return true
    }

    companion object {
        private const val IMMEDIATE = 4101
        private const val PERIODIC = 4102
        private const val PERIOD_MS = 30L * 60L * 1000L

        fun scheduleNow(context: Context) {
            if (WidgetPreferences.vaultUri(context) == null) {
                WidgetPreferences.setRefreshState(context, WidgetRefreshState.FAILURE)
                TaskWidgetProvider.refreshAll(context)
                return
            }
            WidgetPreferences.setRefreshState(context, WidgetRefreshState.RUNNING)
            TaskWidgetProvider.refreshAll(context)
            val scheduled = runCatching {
                val scheduler = context.getSystemService(JobScheduler::class.java)
                scheduler.schedule(JobInfo.Builder(IMMEDIATE,
                    ComponentName(context, VaultScanJobService::class.java))
                    .setMinimumLatency(0).setOverrideDeadline(30_000).build())
            }.getOrDefault(JobScheduler.RESULT_FAILURE)
            if (scheduled != JobScheduler.RESULT_SUCCESS) {
                WidgetPreferences.setRefreshState(context, WidgetRefreshState.FAILURE)
                TaskWidgetProvider.refreshAll(context)
            }
        }

        fun cancelPeriodic(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(PERIODIC)
        }

        fun ensurePeriodic(context: Context) {
            if (WidgetPreferences.vaultUri(context) == null) return
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(PERIODIC)?.isPersisted == true) return
            scheduler.schedule(JobInfo.Builder(PERIODIC,
                ComponentName(context, VaultScanJobService::class.java))
                .setPeriodic(PERIOD_MS).setPersisted(true).build())
        }
    }
}