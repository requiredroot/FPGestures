package com.requiredroot.fpgestures

/**
 * Root-executed actions. Everything runs through `su`, so no
 * AccessibilityService or system permissions are needed. Actions use
 * `input keyevent` / `cmd` so they behave like real hardware keys and
 * do not depend on any app context.
 */
object Actions {

    const val NONE = "none"
    const val BACK = "back"
    const val HOME = "home"
    const val RECENTS = "recents"
    const val NOTIFICATIONS = "notifications"
    const val QUICK_SETTINGS = "quick_settings"
    const val POWER_DIALOG = "power_dialog"
    const val SCREENSHOT = "screenshot"
    const val PLAY_PAUSE = "play_pause"
    const val NEXT_TRACK = "next_track"
    const val PREV_TRACK = "prev_track"
    const val TORCH_TOGGLE = "torch_toggle"
    const val SCREEN_OFF = "screen_off"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"

    val ALL: List<Pair<String, String>> = listOf(
        NONE to "Do nothing",
        NOTIFICATIONS to "Expand notifications",
        QUICK_SETTINGS to "Expand quick settings",
        BACK to "Back",
        HOME to "Home",
        RECENTS to "Recent apps",
        POWER_DIALOG to "Power menu",
        SCREENSHOT to "Screenshot",
        PLAY_PAUSE to "Play / pause",
        NEXT_TRACK to "Next track",
        PREV_TRACK to "Previous track",
        TORCH_TOGGLE to "Toggle torch (begonia sysfs)",
        SCREEN_OFF to "Screen off",
        VOLUME_UP to "Volume up",
        VOLUME_DOWN to "Volume down"
    )

    val IDS: List<String> = ALL.map { it.first }

    /** Toggles the begonia torch sysfs node (7 = on, 0 = off). */
    private const val TORCH_SCRIPT =
        "for p in /sys/class/leds/torch-light0/brightness " +
        "/sys/devices/platform/flashlights_mt6360/torchbrightness; " +
        "do if [ -w \$p ]; then " +
        "v=\$(cat \$p 2>/dev/null | tr -dc 0-9); " +
        "if [ \"\${v:-0}\" = 0 ]; then echo 7 > \$p; else echo 0 > \$p; fi; " +
        "break; fi; done"

    /** Shell snippet executed as root for [actionId]. Empty = no-op. */
    fun commandFor(actionId: String): String = when (actionId) {
        BACK -> "input keyevent 4"
        HOME -> "input keyevent 3"
        RECENTS -> "input keyevent 187"
        NOTIFICATIONS -> "cmd statusbar expand-notifications"
        QUICK_SETTINGS -> "cmd statusbar expand-settings"
        POWER_DIALOG -> "input keyevent 26"
        SCREENSHOT -> "input keyevent 120"
        PLAY_PAUSE -> "input keyevent 85"
        NEXT_TRACK -> "input keyevent 87"
        PREV_TRACK -> "input keyevent 88"
        TORCH_TOGGLE -> TORCH_SCRIPT
        SCREEN_OFF -> "input keyevent 223"
        VOLUME_UP -> "input keyevent 24"
        VOLUME_DOWN -> "input keyevent 25"
        else -> ""
    }
}

/** Out-of-the-box mapping. */
object ActionDefaults {
    fun defaultFor(gesture: String): String = when (gesture) {
        GesturePrefs.KEY_SWIPE_DOWN -> Actions.NOTIFICATIONS
        GesturePrefs.KEY_SWIPE_UP -> Actions.QUICK_SETTINGS
        GesturePrefs.KEY_SWIPE_LEFT -> Actions.BACK
        GesturePrefs.KEY_SWIPE_RIGHT -> Actions.RECENTS
        GesturePrefs.KEY_TAP -> Actions.HOME
        GesturePrefs.KEY_DOUBLE_TAP -> Actions.PLAY_PAUSE
        GesturePrefs.KEY_LONG_PRESS -> Actions.TORCH_TOGGLE
        GesturePrefs.KEY_HEAVY_PRESS -> Actions.SCREENSHOT
        else -> Actions.NONE
    }
}
