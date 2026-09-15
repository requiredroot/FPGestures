package com.requiredroot.fpgestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Single-screen settings UI, built programmatically (no XML layouts):
 * root status, service toggle, and one action spinner per gesture.
 * Everything is applied live; no AccessibilityService anywhere.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var toggleView: Switch
    private lateinit var rowsContainer: LinearLayout
    private var hasRoot: Boolean? = null
    private var serviceRunning = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            serviceRunning = intent.getBooleanExtra(GestureService.EXTRA_RUNNING, false)
            refreshUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
        }
        statusView = TextView(this).apply { textSize = 16f }
        toggleView = Switch(this).apply {
            text = getString(R.string.enable_service)
            setOnCheckedChangeListener { _, checked ->
                GesturePrefs.setEnabled(this@MainActivity, checked)
                if (checked) {
                    if (hasRoot == true) GestureService.start(this@MainActivity)
                    else {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.no_root), Toast.LENGTH_LONG
                        ).show()
                        isChecked = false
                    }
                } else {
                    GestureService.stop(this@MainActivity)
                    serviceRunning = false
                    refreshUi()
                }
            }
        }
        rowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(title)
        root.addView(statusView)
        root.addView(toggleView)
        root.addView(rowsContainer)
        setContentView(root)
        buildGestureRows()
        val filter = IntentFilter(GestureService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
        checkRootAsync()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }

    private fun buildGestureRows() {
        val labels = Actions.ALL.map { it.second }
        for (gesture in GesturePrefs.GESTURES) {
            val label = TextView(this).apply {
                text = GesturePrefs.GESTURE_LABELS[gesture] ?: gesture
                textSize = 15f
                setPadding(0, 24, 0, 4)
            }
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item, labels
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(Actions.IDS.indexOf(
                    GesturePrefs.actionFor(this@MainActivity, gesture)
                ).coerceAtLeast(0), false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, pos: Int, id: Long
                    ) {
                        GesturePrefs.setAction(this@MainActivity, gesture, Actions.IDS[pos])
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) { }
                }
            }
            rowsContainer.addView(label)
            rowsContainer.addView(spinner)
        }
    }

    private fun checkRootAsync() {
        statusView.text = getString(R.string.checking_root)
        Thread({
            val ok = RootShell.hasRoot()
            runOnUiThread {
                hasRoot = ok
                if (!ok) {
                    toggleView.isChecked = false
                    GesturePrefs.setEnabled(this, false)
                }
                refreshUi()
            }
        }, "RootCheck").also { it.isDaemon = true; it.start() }
    }

    private fun refreshUi() {
        val ok = hasRoot
        statusView.text = when {
            ok == null -> getString(R.string.checking_root)
            ok -> getString(R.string.root_ok)
            else -> getString(R.string.no_root)
        }
        val color = when {
            ok == null -> android.R.color.darker_gray
            ok -> android.R.color.holo_green_dark
            else -> android.R.color.holo_red_dark
        }
        statusView.setTextColor(ContextCompat.getColor(this, color))
        toggleView.isEnabled = ok == true
        toggleView.isChecked = GesturePrefs.isEnabled(this) && ok == true
        toggleView.text = if (serviceRunning) {
            getString(R.string.service_running)
        } else {
            getString(R.string.enable_service)
        }
    }
}

