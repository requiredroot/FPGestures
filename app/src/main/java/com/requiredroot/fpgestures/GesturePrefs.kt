package com.requiredroot.fpgestures

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Gesture -> action mapping, persisted in SharedPreferences.
 *
 * Gestures follow the Goodix begonia driver (gf_spi_tee.h):
 *   SWIPE_DOWN, SWIPE_UP, SWIPE_LEFT, SWIPE_RIGHT, TAP (click),
 *   DOUBLE_TAP, LONG_PRESS, HEAVY_PRESS
 */
object GesturePrefs {

    const val PREFS = "fpgestures"

    const val KEY_SWIPE_DOWN = "swipe_down"
    const val KEY_SWIPE_UP = "swipe_up"
    const val KEY_SWIPE_LEFT = "swipe_left"
    const val KEY_SWIPE_RIGHT = "swipe_right"
    const val KEY_TAP = "tap"
    const val KEY_DOUBLE_TAP = "double_tap"
    const val KEY_LONG_PRESS = "long_press"
    const val KEY_HEAVY_PRESS = "heavy_press"
    const val KEY_ENABLED = "enabled"

    val GESTURES = listOf(
        KEY_SWIPE_DOWN, KEY_SWIPE_UP, KEY_SWIPE_LEFT, KEY_SWIPE_RIGHT,
        KEY_TAP, KEY_DOUBLE_TAP, KEY_LONG_PRESS, KEY_HEAVY_PRESS
    )

    val GESTURE_LABELS = mapOf(
        KEY_SWIPE_DOWN to "Swipe down",
        KEY_SWIPE_UP to "Swipe up",
        KEY_SWIPE_LEFT to "Swipe left",
        KEY_SWIPE_RIGHT to "Swipe right",
        KEY_TAP to "Single tap",
        KEY_DOUBLE_TAP to "Double tap",
        KEY_LONG_PRESS to "Long press",
        KEY_HEAVY_PRESS to "Heavy press"
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun actionFor(ctx: Context, gesture: String): String =
        prefs(ctx).getString(gesture, ActionDefaults.defaultFor(gesture))
            ?: ActionDefaults.defaultFor(gesture)

    fun setAction(ctx: Context, gesture: String, actionId: String) {
        prefs(ctx).edit { putString(gesture, actionId) }
    }

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_ENABLED, enabled) }
    }
}
