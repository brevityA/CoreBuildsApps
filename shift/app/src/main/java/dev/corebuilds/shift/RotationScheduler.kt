package dev.corebuilds.shift

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RotationScheduler {

    private const val TAG = "CoreShiftScheduler"
    private const val WORK_NAME = "core_shift_rotation"

    fun schedule(context: Context, intervalMinutes: Long) {
        val clamped = intervalMinutes.coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<RotationWorker>(
            clamped, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.i(TAG, "scheduled rotation every ${clamped}m")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "cancelled rotation")
    }
}
