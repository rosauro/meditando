package app.meditando

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Tarea periódica (24 h, con red) que busca actualizaciones. */
class UpdateJob : JobService() {
    override fun onStartJob(p: JobParameters): Boolean {
        Thread { try { Updater.backgroundCheck(applicationContext) } finally { jobFinished(p, false) } }.start()
        return true
    }

    override fun onStopJob(p: JobParameters) = false

    companion object {
        private const val ID = 1001

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(ID) != null) return
            js.schedule(JobInfo.Builder(ID, ComponentName(ctx, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(Updater.DAY_MS)
                .setPersisted(true).build())
        }

        fun cancel(ctx: Context) = ctx.getSystemService(JobScheduler::class.java).cancel(ID)
    }
}

/** Programa la búsqueda de actualizaciones y, si hace más de 24 h, la lanza al abrir la app. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val s = Settings(this)
        if (!s.autoUpdate) { UpdateJob.cancel(this); return }
        UpdateJob.schedule(this)
        if (System.currentTimeMillis() - s.lastCheck > Updater.DAY_MS) {
            Thread { Updater.backgroundCheck(this) }.start()
        }
    }
}
