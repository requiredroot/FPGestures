package com.requiredroot.fpgestures

import android.util.Log

class FpEventReader(
    private val listener: (gesture: String) -> Unit
) {
    companion object {
        private const val TAG = "FpEventReader"
        const val DEVICE_NAME = "uinput-goodix"

        @Volatile var lastError: String? = null
            private set
        @Volatile var activeDevice: String? = null
            private set
        @Volatile var lastEventMs: Long = 0
            private set
        @Volatile var lastDeviceList: List<Pair<String, String>> = emptyList()
            private set

        fun noteEvent() { lastEventMs = System.currentTimeMillis() }

        private val NAME_FRAGMENTS = listOf("uinput-goodix", "goodix", "fpc", "fingerprint", "fp")

        private fun isFingerprintDevice(name: String): Boolean {
            val n = name.lowercase()
            return NAME_FRAGMENTS.any { n.contains(it) }
        }

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

    fun findDevice(): String? {
        val ge = RootShell.run("getevent -p")
        if (ge.ok && ge.stdout.isNotBlank()) {
            val found = mutableListOf<Pair<String, String>>()
            var currentDev: String? = null
            for (line in ge.stdout.lines()) {
                val t = line.trim()
                if (t.startsWith("/dev/input/event")) {
                    currentDev = t.removeSuffix(":")
                } else if (t.startsWith("name:") && currentDev != null) {
                    val name = t.removePrefix("name:").trim().removeSurrounding("\"")
                    found.add(currentDev to name)
                    if (isFingerprintDevice(name)) {
                        lastDeviceList = found
                        lastError = null
                        return currentDev
                    }
                }
            }
            lastDeviceList = found
            lastError = if (found.isEmpty()) {
                "no input devices reported by getevent -p"
            } else {
                "no fingerprint device. Devices: " + found.joinToString { it.second }.take(300)
            }
            return null
        }

        val ls = RootShell.run("ls /dev/input/event*")
        if (!ls.ok || ls.stdout.isBlank()) {
            lastError = "getevent failed and no event nodes: ${ge.stderr}"
            lastDeviceList = emptyList()
            return null
        }
        val found2 = mutableListOf<Pair<String, String>>()
        for (node in ls.stdout.lines().map { it.trim() }.filter { it.startsWith("/dev/input/event") }) {
            val num = node.removePrefix("/dev/input/event")
            val nameR = RootShell.run("cat /sys/class/input/event$num/name 2>/dev/null; echo")
            val name = nameR.stdout.trim()
            found2.add(node to name)
            if (isFingerprintDevice(name)) {
                lastDeviceList = found2
                lastError = null
                return node
            }
        }
        lastDeviceList = found2
        lastError = "no fingerprint device in ${found2.size} nodes: " + found2.joinToString(", ") { it.second }.take(300)
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
                activeDevice = dev
                streamDevice(dev)
                activeDevice = null
                if (running) sleepQuiet(2000)
            }
        }, "FpEventReader").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        activeDevice = null
        try { proc?.destroyForcibly() } catch (_: Exception) { }
        thread?.interrupt()
        thread = null
    }

    private fun streamDevice(dev: String) {
        var p: Process? = null
        try {
            p = Runtime.getRuntime().exec(arrayOf("su", "-c", "getevent -lt $dev"))
            proc = p
            p.outputStream.close()
            val reader = p.inputStream.bufferedReader()
            while (running) {
                val line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                val gesture = parseLine(line)
                if (gesture != null) {
                    noteEvent()
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

    internal fun parseLine(line: String): String? {
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
