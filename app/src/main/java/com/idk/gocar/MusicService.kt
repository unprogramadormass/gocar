package com.example.gocar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MusicService : NotificationListenerService() {

    // Receptor para escuchar cuando la MainActivity pide actualizaciones
    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Alguien pidió datos, enviamos lo que tenemos YA MISMO
            broadcastNotifications()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Registramos el oído para escuchar la petición "ACTION_REQUEST_NOTIFICATIONS"
        LocalBroadcastManager.getInstance(this).registerReceiver(requestReceiver, IntentFilter("com.example.gocar.ACTION_REQUEST_NOTIFICATIONS"))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(requestReceiver)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        broadcastNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        broadcastNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        broadcastNotifications()
    }

    private fun broadcastNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return // Protección contra nulos
            val packageNames = ArrayList<String>()

            for (sbn in activeNotifs) {
                val pkg = sbn.packageName
                // Filtramos sistema y nuestra propia app
                if (pkg != "android" && pkg != "com.android.systemui" && pkg != packageName) {
                    packageNames.add(pkg)
                }
            }

            val intent = Intent("com.example.gocar.NOTIFICATION_LIST_UPDATE")
            intent.putStringArrayListExtra("active_packages", packageNames)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}