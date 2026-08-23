package dev.corebuilds.shift

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Instant on-device preview for a procedural Series 2/3 generation.
 *
 * Seed rows open here before the production MP4 release exists. Once the
 * release feed is live, the same row can still preview here while PreviewActivity
 * remains available for the downloadable MP4 path.
 */
class ProceduralPreviewActivity : AppCompatActivity() {

    private lateinit var preview: CoreMotionPreviewView

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
