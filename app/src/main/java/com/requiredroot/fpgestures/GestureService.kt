package com.requiredroot.fpgestures

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Root-only foreground service: reads fingerprint gestures from the Goodix
 * input device (as root) and dispatches the configured root action.
 *
 * No AccessibilityService, no system permissions beyond a foreground
 * notification. Everything privileged goes through `su`.
 */
class GestureService : Service() {

    companion object {
        private const val TAG = "GestureService"
        private const val CHANNEL_ID = "fpgestures"
        private const val NOTIF_ID = 1

        const val ACTION_START = "com.requiredroot.fpgestures.START"
        const val ACTION_STOP = "com.requiredroot.fpgestures.STOP"
        const val ACTION_STATE_CHANGED = "com.requiredroot.fpgestures.STATE_CHANGED"
        const val EXTRA_RUNNING = "running"

        fun start(ctx: Context) {
            val i = Intent(ctx, GestureService::class.java).setAction(ACTION_START)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, GestureService::class.java).setAction(ACTION_STOP))
        }
    }

    private var reader: FpEventReader? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                broadcastState(false)
                return START_NOT_STICKY
            }
            else -> {
                if (!RootShell.hasRoot()) {
                    Log.e(TAG, "root not available, refusing to start")
                    broadcastState(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithType()
                bringUp()
                broadcastState(true)
                return START_STICKY
            }
        }
    }

    private fun bringUp() {
        if (reader != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK keeps the CPU on so getevent keeps streaming
        // while the screen is off; released in teardown().
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FPGestures:reader")
            .also { it.acquire(12 * 60 * 60 * 1000L) }
        reader = FpEventReader { gesture -> onGesture(gesture) }.also { it.start() }
        Log.i(TAG, "gesture listener started")
    }

    private fun teardown() {
        try { reader?.stop() } catch (_: Exception) { }
        reader = null
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) { }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun onGesture(gesture: String) {
        if (!GesturePrefs.isEnabled(this)) return
        val actionId = GesturePrefs.actionFor(this, gesture)
        val cmd = Actions.commandFor(actionId)
        if (cmd.isEmpty()) return
        Log.i(TAG, "gesture=$gesture action=$actionId")
        Thread({
            val r = RootShell.run(cmd)
            if (!r.ok) Log.w(TAG, "action failed: ${r.stderr}")
        }, "FpAction").also { it.isDaemon = true; it.start() }
    }

    private fun startForegroundWithType() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
    }

    private fun broadcastState(running: Boolean) {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra(EXTRA_RUNNING, running))
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }
}
