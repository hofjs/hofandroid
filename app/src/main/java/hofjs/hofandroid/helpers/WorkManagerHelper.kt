package hofjs.hofandroid.helpers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class WorkManagerHelper(val context: Context)  {
    val workManager = WorkManager.getInstance(context)

    inline fun <reified T: ListenableWorker> updatePeriodicWorker(enable: Boolean,
        repeatInterval: Long, repeatIntervalTimeUnit: TimeUnit, inputData: Data = workDataOf()) {
        if (enable)
            registerPeriodicWorker<T>(
                repeatInterval, repeatIntervalTimeUnit, inputData)
        else
            unregisterPeriodicWorker<T>()
    }

    inline fun <reified T: ListenableWorker> registerPeriodicWorker(
    repeatInterval: Long, repeatIntervalTimeUnit: TimeUnit, inputData: Data = workDataOf()) {
        val request = PeriodicWorkRequestBuilder<T>(repeatInterval, repeatIntervalTimeUnit)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniquePeriodicWork(
            T::class.java.name, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    inline fun <reified T: ListenableWorker> unregisterPeriodicWorker() {
        workManager.cancelUniqueWork(T::class.java.name)
    }
}