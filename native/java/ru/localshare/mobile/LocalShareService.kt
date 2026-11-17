package ru.localshare.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Base64
import java.io.IOException
import java.security.SecureRandom

/** Keeps the LAN server alive independently of the Capacitor activity. */
class LocalShareService : Service() {
    companion object {
        const val PORT = 8080
        const val ACTION_START = "ru.localshare.mobile.START"
        const val ACTION_STOP = "ru.localshare.mobile.STOP"
        private const val CHANNEL_ID = "localshare_server"
        private const val NOTIFICATION_ID = 2601
        private const val PREFS = "localshare_prefs"
        @Volatile var running = false
            private set
        @Volatile var starting = false
            private set
        @Volatile var lastError: String? = null
            private set
        @Volatile private var instance: LocalHttpServer? = null
        private val lock = Any()

        fun secret(context: android.content.Context): String = synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            prefs.getString("secret", null) ?: run {
                val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val generated = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                prefs.edit().putString("secret", generated).apply()
                generated
            }
        }

        fun rotateSecret(context: android.content.Context): String = synchronized(lock) {
            val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val new = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString("secret", new).apply()
            new
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        } catch (e: Exception) {
            lastError = e.message ?: "Cannot start foreground service"
            stopSelf()
            return START_NOT_STICKY
        }
        synchronized(lock) {
            if (running || starting) return START_NOT_STICKY
            starting = true
            lastError = null
        }
        Thread {
            try {
                val server = LocalHttpServer(applicationContext, PORT)
                server.start(15000, false)
                synchronized(lock) {
                    if (starting) {
                        instance = server
                        running = true
                        starting = false
                    } else {
                        server.stop()
                    }
                }
            } catch (e: Exception) {
                synchronized(lock) {
                    lastError = e.message ?: "Failed to bind port $PORT"
                    running = false
                    starting = false
                }
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "LocalShare server", NotificationManager.IMPORTANCE_LOW)
            channel.description = "LAN file sharing service"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocalShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
                      else Notification.Builder(this)
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LocalShare · Сервер запущен")
            .setContentText("Передача файлов по локальной сети · порт $PORT")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun shutdown() {
        synchronized(lock) {
            instance?.stop()
            instance = null
            running = false
            starting = false
        }
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
