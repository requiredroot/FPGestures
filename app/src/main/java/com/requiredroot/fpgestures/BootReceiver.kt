package com.requiredroot.fpgestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restart the listener after boot when the user left it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!GesturePrefs.isEnabled(context)) return
        Log.i("BootReceiver", "boot completed, starting gesture service")
        try {
            GestureService.start(context)
        } catch (e: Exception) {
            Log.w("BootReceiver", "start failed: ${e.message}")
        }
    }
}
