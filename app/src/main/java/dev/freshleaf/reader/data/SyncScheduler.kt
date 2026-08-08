package dev.freshleaf.reader.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SyncScheduler {
    private const val REQUEST_CODE = 4107
    private const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SyncReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, INTERVAL_MS, INTERVAL_MS, pending)
    }
}

class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as FreshLeafApplicationAccessor).repository.sync()
            } finally {
                pending.finish()
            }
        }
    }
}

interface FreshLeafApplicationAccessor {
    val repository: FreshLeafRepository
}

