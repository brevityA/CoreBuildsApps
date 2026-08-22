package dev.corebuilds.shift

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: RotationPreferences
    private lateinit var btnToggle: Button
    private lateinit var statusText: TextView
    private lateinit var spinnerInterval: Spinner

    private val intervals = listOf(15L, 30L, 60L, 240L, 720L, 1440L)
    private val intervalLabels by lazy {
        listOf(
            getString(R.string.interval_15m),
            getString(R.string.interval_30m),
            getString(R.string.interval_1h),
            getString(R.string.interval_4h),
            getString(R.string.interval_12h),
            getString(R.string.interval_24h)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = RotationPreferences(this)
        btnToggle = findViewById(R.id.btn_rotation_toggle)
        statusText = findViewById(R.id.status_text)
        spinnerInterval = findViewById(R.id.spinner_interval)

        val versionText = findViewById<TextView>(R.id.version_text)
        versionText.text = "v${BuildConfig.VERSION_NAME}"

        setupIntervalSpinner()
        updateRotationUI()

        findViewById<android.view.View>(R.id.btn_motion).setOnClickListener {
            startActivity(Intent(this, MotionActivity::class.java))
        }

        btnToggle.setOnClickListener {
            if (prefs.rotationEnabled) {
                prefs.rotationEnabled = false
                RotationScheduler.cancel(this)
            } else {
                val minutes = intervals[spinnerInterval.selectedItemPosition]
                prefs.rotationEnabled = true
                prefs.intervalMinutes = minutes
                RotationScheduler.schedule(this, minutes)
            }
            updateRotationUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateRotationUI()
    }

    private fun setupIntervalSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerInterval.adapter = adapter

        val saved = prefs.intervalMinutes
        val idx = intervals.indexOf(saved)
        if (idx >= 0) spinnerInterval.setSelection(idx)
    }

    private fun updateRotationUI() {
        if (prefs.rotationEnabled) {
            btnToggle.setText(R.string.rotation_stop)
            statusText.setText(R.string.rotation_enabled)
        } else {
            btnToggle.setText(R.string.rotation_start)
            statusText.setText(R.string.rotation_disabled)
        }
    }
}
