package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The icon inspector: what the pack actually ships for one tile, and two
 * things to do with it.
 *
 * Clicking a tile used to answer with a transient toast - the name and the
 * drawable id, gone in two seconds, exactly when someone wanted to read them.
 * This screen is the toast grown up: the bundled mark at full size, its
 * category and drawable name, and every component `appfilter.xml` maps to it,
 * read from the asset so the list is what the launcher will match, not what a
 * catalog said once.
 *
 * **Export PNG** copies the bundled 512px bytes to
 * `Pictures/CoreBuilds/Icons/` through [IconExporter] - no re-encode, same
 * permission contract as the wallpaper export. **Launch** opens the mapped
 * app to test the mapping end to end; it can ask "is it installed" at all
 * because the auditor's intent-filter `<queries>` made every launchable
 * package visible, and when the answer is no the toast says so instead of
 * bouncing off an ActivityNotFoundException.
 */
class InspectorActivity : TvActivity() {

    private lateinit var drawableName: String

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) exportNow() else toast(getString(R.string.inspector_storage_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspector)

        drawableName = intent.getStringExtra(EXTRA_DRAWABLE).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()

        findViewById<TextView>(R.id.inspector_title).text = name
        findViewById<TextView>(R.id.inspector_meta).text =
            getString(R.string.inspector_meta_fmt, category, drawableName)

        val id = resources.getIdentifier(drawableName, "drawable", packageName)
        if (id != 0) findViewById<ImageView>(R.id.inspector_icon).setImageResource(id)

        val components = componentsFor(drawableName)
        findViewById<TextView>(R.id.inspector_components).text =
            components.joinToString("\n") { it }

        findViewById<View>(R.id.inspector_back).setOnClickListener { finish() }
        findViewById<View>(R.id.inspector_export).setOnClickListener {
            if (IconExporter.hasStoragePermission(this)) exportNow()
            else requestStoragePermission.launch(IconExporter.storagePermission())
        }
        findViewById<View>(R.id.inspector_launch).setOnClickListener {
            launchMapped(components)
        }
    }

    /** Every `pkg/activity` appfilter maps to this drawable, in file order. */
    private fun componentsFor(drawable: String): List<String> {
        val xml = assets.open("appfilter.xml").bufferedReader().use { it.readText() }
        // appfilter maps every component to the 16:9 banner drawable - the
        // square glyph reaches launchers through drawable.xml, not through a
        // component mapping - so matching the square name alone answered
        // "no component maps to this drawable" for every tile in the pack and
        // made Launch a dead button. The banner is the mapping; accept both.
        val pattern = Regex(
            "component=\"ComponentInfo\\{([^}]+)\\}\"\\s+drawable=\"$drawable(?:_banner)?\""
        )
        return pattern.findAll(xml).map { it.groupValues[1] }.toList()
    }

    private fun exportNow() {
        when (val result = IconExporter.export(this, drawableName)) {
            is IconExporter.Result.Saved ->
                toast(getString(R.string.inspector_exported_fmt, result.displayName))
            is IconExporter.Result.NeedsPermission ->
                requestStoragePermission.launch(result.permission)
            is IconExporter.Result.Failed ->
                toast(getString(R.string.inspector_export_failed_fmt, result.reason))
        }
    }

    private fun launchMapped(components: List<String>) {
        val pkg = components.firstOrNull()?.substringBefore('/')
        if (pkg == null) {
            toast(getString(R.string.inspector_not_mapped))
            return
        }
        val installed = with(ApplyIconPack) { isInstalled(pkg) }
        val intent = if (installed) packageManager.getLaunchIntentForPackage(pkg) else null
        if (intent == null) {
            toast(getString(R.string.inspector_not_installed_fmt, pkg))
            return
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast(getString(R.string.inspector_not_installed_fmt, pkg))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_DRAWABLE = "drawable"
        const val EXTRA_NAME = "name"
        const val EXTRA_CATEGORY = "category"
    }
}
