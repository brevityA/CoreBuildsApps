package tv.corebuilds.eq

import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import tv.corebuilds.eq.apply.DumpsysDiscovery
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.apply.SetupGuide
import tv.corebuilds.eq.apply.SpatialStatus
import tv.corebuilds.eq.dsp.DeviceIdentity
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.OverlayPermission

/**
 * One-tap setup check (1.4.0). Every row is re-read from Android each time the
 * screen resumes, so a permission granted in Settings shows up without a
 * restart. Core EQ cannot run ADB itself, so the commands here are copied
 * for a computer; see [SetupGuide].
 */
class SetupActivity : TvActivity() {

    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private var lastSteps: List<SetupGuide.Step> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        rows = findViewById(R.id.setup_rows)
        status = findViewById(R.id.text_setup_status)
        findViewById<Button>(R.id.btn_setup_check).setOnClickListener { refresh() }
        findViewById<Button>(R.id.btn_setup_copy_report).setOnClickListener {
            copy(SetupGuide.report(lastSteps), getString(R.string.setup_report_copied))
        }
        findViewById<Button>(R.id.btn_setup_done).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val output = OutputRoute.current(this)
        val detection = DeviceIdentity.detect(
            Build.MANUFACTURER,
            output?.kind,
            output?.name,
            OutputRoute.connectedNames(this, output?.kind)
        )
        val store = ProfileStore(this)
        val measured = OutputRoute.pick(
            store.getAllProfiles(),
            store.chosenId(),
            output?.kind,
            output?.name
        ).profile != null
        lastSteps = SetupGuide.steps(
            dumpGranted = DumpsysDiscovery.hasGrant(this),
            overlayAllowed = OverlayPermission.allowed(this),
            outputKind = output?.kind,
            outputName = output?.name,
            hardware = detection.hardware,
            detection = detection,
            measured = measured,
            passthroughRisk = OutputRoute.mayPassThrough(this, output?.kind),
            spatialDetail = spatialDetail()
        )
        rows.removeAllViews()
        for (step in lastSteps) rows.addView(rowFor(step))
        val needed = lastSteps.count { it.state == SetupGuide.State.NEEDED }
        status.text = if (needed == 0) {
            getString(R.string.setup_status_ready)
        } else {
            resources.getQuantityString(R.plurals.setup_status_needed, needed, needed)
        }
    }

    /** Read-only: Android 12L and later report the spatializer state; older versions get a plain note. */
    private fun spatialDetail(): String {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < SpatialStatus.MIN_SDK) return SpatialStatus.detail(sdk, null, null, null)
        return readSpatializer(sdk)
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun readSpatializer(sdk: Int): String {
        val spatializer = getSystemService(AudioManager::class.java)?.spatializer
            ?: return SpatialStatus.detail(sdk, false, null, null)
        return SpatialStatus.detail(
            sdk,
            supported = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE,
            enabled = spatializer.isEnabled,
            available = spatializer.isAvailable
        )
    }

    private fun rowFor(step: SetupGuide.Step): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val tag = when (step.state) {
            SetupGuide.State.DONE -> getString(R.string.setup_tag_done)
            SetupGuide.State.NEEDED -> getString(R.string.setup_tag_needed)
            SetupGuide.State.INFO -> getString(R.string.setup_tag_info)
        }
        column.addView(line("$tag  ${step.title}", bold = true))
        column.addView(line(step.detail, bold = false))
        step.command?.let { column.addView(line(it, bold = false, mono = true)) }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        step.command?.let { command ->
            actions.addView(button(getString(R.string.setup_copy_command)) {
                copy(command, getString(R.string.setup_command_copied))
            })
        }
        if (step.id == "overlay" && step.state == SetupGuide.State.NEEDED) {
            // Inside apply(), `this` is the row, not the Activity: name it.
            actions.addView(button(getString(R.string.setup_open_overlay)) {
                if (!OverlayPermission.open(this@SetupActivity)) {
                    status.text = getString(R.string.setup_no_overlay_screen)
                }
            })
        }
        if (actions.childCount > 0) column.addView(actions)
        return column
    }

    private fun line(value: String, bold: Boolean, mono: Boolean = false): TextView = TextView(this).apply {
        text = value
        setTextColor(getColor(if (bold) R.color.cb_ink else R.color.cb_slate))
        textSize = if (mono) 13f else 14f
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (mono) typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) }
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = label
        isAllCaps = false
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6); marginEnd = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun copy(value: String, message: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            status.text = getString(R.string.setup_no_clipboard)
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Core EQ", value))
        status.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
