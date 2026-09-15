package com.requiredroot.fpgestures

import android.util.Log

/**
 * Finds the Goodix fingerprint input device and streams its key events
 * as root via `su -c getevent -lt`.
 *
 * The begonia Goodix driver registers an input device named "uinput-goodix"
 * and emits (gf_spi_tee.h):
 *   swipe down -> KEY_DOWN (108), swipe up -> KEY_UP (103),
 *   swipe left -> KEY_LEFT (105), swipe right -> KEY_RIGHT (106),
 *   click (tap) -> KEY_VOLUMEDOWN (114), double click -> KEY_VOLUMEUP (115),
 *   long press -> KEY_SEARCH (217), heavy press -> KEY_CHAT (299).
 *
 * getevent -lt prints lines like:
 *   [ 1234.567890] /dev/input/event3: EV_KEY  KEY_VOLUMEUP  DOWN
 * We parse key name + DOWN/UP and report DOWN transitions to [listener].
 * Runs on its own thread; call [stop] to kill the su session.
 */
class FpEventReader(
    private val listener: (gesture: String) -> Unit
) {
    companion object {
        private const val TAG = "FpEventReader"
        const val DEVICE_NAME = "uinput-goodix"

        // getevent key labels -> gesture keys.
        private val KEY_TO_GESTURE = mapOf(
            "KEY_DOWN" to GesturePrefs.KEY_SWIPE_DOWN,
            "KEY_UP" to GesturePrefs.KEY_SWIPE_UP,
            "KEY_LEFT" to GesturePrefs.KEY_SWIPE_LEFT,
            "KEY_RIGHT" to GesturePrefs.KEY_SWIPE_RIGHT,
            "KEY_VOLUMEDOWN" to GesturePrefs.KEY_TAP,
            "KEY_VOLUMEUP" to GesturePrefs.KEY_DOUBLE_TAP,
            "KEY_SEARCH" to GesturePrefs.KEY_LONG_PRESS,
            "KEY_CHAT" to GesturePrefs.KEY_HEAVY_PRESS
        )
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var proc: Process? = null

    /** Resolve /dev/input/eventN for the Goodix device. Null if absent. */
    fun findDevice(): String? {
        // getevent -p lists every device with its name; the device line is
        // followed by "  name: ..." within the same block.
        val r = RootShell.run("getevent -p")
        if (!r.ok) {
            Log.w(TAG, "getevent -p failed: ${r.stderr}")
            return null
        }
        var currentDev: String? = null
        for (line in r.stdout.lines()) {
            val t = line.trim()
            if (t.startsWith("/dev/input/event")) {
                currentDev = t.removeSuffix(":")
            } else if (t.startsWith("name:")) {
                val name = t.removePrefix("name:").trim().removeSurrounding("\"")
                if (name == DEVICE_NAME) return currentDev
            }
        }
        return null
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            var backoffMs = 1000L
            while (running) {
                val dev = findDevice()
                if (dev == null) {
                    Log.w(TAG, "$DEVICE_NAME not found, retry in ${backoffMs}ms")
                    sleepQuiet(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                    continue
                }
                backoffMs = 1000L
                Log.i(TAG, "listening on $dev")
                streamDevice(dev)
                // streamDevice returns on error/disconnect; loop and re-resolve.
                if (running) sleepQuiet(2000)
            }
        }, "FpEventReader").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { proc?.destroyForcibly() } catch (_: Exception) { }
        thread?.interrupt()
        thread = null
    }

    private fun streamDevice(dev: String) {
        var p: Process? = null
        try {
            // -l labels, -t timestamps; DOWN transitions are what we map.
            p = Runtime.getRuntime().exec(arrayOf("su", "-c", "getevent -lt $dev"))
            proc = p
            p.outputStream.close()
            val reader = p.inputStream.bufferedReader()
            var line: String?
            while (running && reader.readLine().also { line = it } != null) {
                parseLine(line!!)?.let { gesture ->
                    try { listener(gesture) } catch (e: Exception) {
                        Log.w(TAG, "listener failed: ${e.message}")
                    }
                }
            }
            try { p.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) { }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "stream error: ${e.message}")
        } finally {
            try { p?.destroyForcibly() } catch (_: Exception) { }
            if (proc === p) proc = null
        }
    }

    /** Returns the gesture key for DOWN transitions, else null. */
    internal fun parseLine(line: String): String? {
        // Expect: "... EV_KEY  KEY_VOLUMEUP  DOWN"
        if (!line.contains("EV_KEY")) return null
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val state = parts.last()
        if (state != "DOWN") return null
        val keyName = parts[parts.size - 2]
        return KEY_TO_GESTURE[keyName]
    }

    private fun sleepQuiet(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }
}
