package pl.bbtv.tv

import android.app.job.JobParameters
import android.app.job.JobService

class AdUpdateJob : JobService() {
    @Volatile private var stopped = false
    override fun onStartJob(params: JobParameters): Boolean { stopped = false; AdBlock.get(this).updateIfDue { if (!stopped) jobFinished(params, false) }; return true }
    override fun onStopJob(params: JobParameters): Boolean { stopped = true; return true }
}
