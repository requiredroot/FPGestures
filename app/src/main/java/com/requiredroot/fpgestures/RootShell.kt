package com.requiredroot.fpgestures

import java.io.BufferedReader
import java.io.DataOutputStream

/** Minimal root shell helper. Holds one persistent `su` session per call site. */
object RootShell {

    /** Run [commands] through a fresh `su` shell, return trimmed stdout. */
    fun run(vararg commands: String, timeoutMs: Long = 15000): ShellResult {
        var proc: Process? = null
        return try {
            proc = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(proc.outputStream)
            val stdout = BufferedReader(proc.inputStream.reader())
            val stderr = BufferedReader(proc.errorStream.reader())
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
            var line: String?
            while (stdout.readLine().also { line = it } != null) {
                out.appendLine(line)
            }
            // Drain stderr for debugging (non-blocking best effort).
            val err = StringBuilder()
            while (stderr.ready()) {
                err.appendLine(stderr.readLine())
            }
            ShellResult(proc.exitValue() == 0, out.toString().trim(), err.toString().trim())
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
