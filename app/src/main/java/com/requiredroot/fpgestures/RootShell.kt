package com.requiredroot.fpgestures


/** Minimal root shell helper. Holds one persistent `su` session per call site. */
object RootShell {

    /** Run [commands] through a fresh `su` shell, return trimmed stdout. */
    fun run(vararg commands: String, timeoutMs: Long = 15000): ShellResult {
        var proc: Process? = null
        return try {
            // Try common su locations explicitly: PATH inside a service can
            // be minimal and some managers only ship one of these.
            val suBin = listOf(
                "/system/bin/su", "/system/xbin/su",
                "/data/adb/ap/bin/su", "/data/adb/ksu/bin/su",
                "/data/adb/magisk/su", "su"
            ).firstOrNull { path ->
                if (path == "su") return@firstOrNull true
                try {
                    val check = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ls $path"))
                    val ok = check.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) &&
                        check.exitValue() == 0
                    try { check.destroy() } catch (_: Exception) { }
                    ok
                } catch (_: Exception) { false }
            } ?: "su"
            proc = Runtime.getRuntime().exec(suBin)
            val stdin = java.io.DataOutputStream(proc.outputStream)
            val stdout = proc.inputStream.bufferedReader()
            // Merge stderr into the read loop via available(); never block
            // forever on a missing stream close.
            val out = StringBuilder()
            for (cmd in commands) {
                stdin.writeBytes(cmd + "\n")
            }
            stdin.writeBytes("exit\n")
            stdin.flush()
            val exited = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!exited) {
                proc.destroyForcibly()
                return ShellResult(false, "", "su timed out")
            }
            try {
                var line: String?
                while (stdout.readLine().also { line = it } != null) {
                    out.appendLine(line)
                }
            } catch (_: Exception) { }
            ShellResult(proc.exitValue() == 0, out.toString().trim(), "")
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "su exec failed")
        } finally {
            try { proc?.destroy() } catch (_: Exception) { }
        }
    }

    /** True when a `su` binary exists and grants us uid 0. */
    fun hasRoot(): Boolean {
        val r = run("id -u")
        return r.ok && r.stdout.trim() == "0"
    }

    data class ShellResult(val ok: Boolean, val stdout: String, val stderr: String)
}
