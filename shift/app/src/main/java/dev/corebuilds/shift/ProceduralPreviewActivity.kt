package dev.corebuilds.shift

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Instant on-device preview for a procedural Series 2/3 generation.
 *
 * Seed rows open here before the production MP4 release exists. Speed and loop
 * length controls are real preview controls and restart the procedural clock;
 * they do not pretend to change the already-encoded MP4 file.
 */
class ProceduralPreviewActivity : AppCompatActivity() {

    private lateinit var preview: CoreMotionPreviewView
    private lateinit var speedSlow: Button
    private lateinit var speedNormal: Button
    private lateinit var speedFast: Button
    private lateinit var length10: Button
    private lateinit var length20: Button
    private lateinit var length30: Button
    private var speed = 1f
    private var seconds = 20f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_procedural_preview)

        val scene = intent.getIntExtra(EXTRA_SCENE, 0)
        val accent = intent.getIntExtra(EXTRA_ACCENT, 0xFF00E5FF.toInt())
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        preview = findViewById(R.id.procedural_preview)
        preview.setScene(scene, accent)
        findViewById<TextView>(R.id.procedural_title).text = title
        findViewById<TextView>(R.id.procedural_meta).text =
            getString(R.string.procedural_preview_meta, scene)

        speedSlow = findViewById(R.id.proc_speed_slow)
        speedNormal = findViewById(R.id.proc_speed_normal)
        speedFast = findViewById(R.id.proc_speed_fast)
        length10 = findViewById(R.id.proc_length_10)
        length20 = findViewById(R.id.proc_length_20)
        length30 = findViewById(R.id.proc_length_30)
        speedSlow.setOnClickListener { setControls(.5f, seconds) }
        speedNormal.setOnClickListener { setControls(1f, seconds) }
        speedFast.setOnClickListener { setControls(2f, seconds) }
        length10.setOnClickListener { setControls(speed, 10f) }
        length20.setOnClickListener { setControls(speed, 20f) }
        length30.setOnClickListener { setControls(speed, 30f) }
        updateControls()
    }

    private fun setControls(nextSpeed: Float, nextSeconds: Float) {
        speed = nextSpeed
        seconds = nextSeconds
        preview.setPlayback(speed, seconds)
        updateControls()
    }

    private fun updateControls() {
        speedSlow.text = if (speed == .5f) "0.5× ✓" else getString(R.string.speed_slow)
        speedNormal.text = if (speed == 1f) getString(R.string.speed_normal_selected) else getString(R.string.speed_normal)
        speedFast.text = if (speed == 2f) "2× ✓" else getString(R.string.speed_fast)
        length10.text = if (seconds == 10f) "10s ✓" else getString(R.string.length_10)
        length20.text = if (seconds == 20f) getString(R.string.length_20_selected) else getString(R.string.length_20)
        length30.text = if (seconds == 30f) "30s ✓" else getString(R.string.length_30)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_SCENE = "procedural_scene"
        const val EXTRA_ACCENT = "procedural_accent"
        const val EXTRA_TITLE = "procedural_title"
    }
}
