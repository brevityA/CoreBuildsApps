package tv.corebuilds.iconpack

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Core Builds Icon Pack — front door.
 *
 * Brand Guide §05: every interactive element states what it will do BEFORE
 * it does it, and the receipts voice reports exactly what happened.
 */
class MainActivity : AppCompatActivity() {

    private val projectivyPackage = "com.spocky.projengmenu"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val icons = resources.getStringArray(R.array.icon_pack)

        findViewById<TextView>(R.id.count).text =
            getString(R.string.icon_count_fmt, icons.size)

        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanForScreen())
            adapter = IconAdapter(icons)
            setHasFixedSize(true)
        }

        findViewById<TextView>(R.id.apply_button).setOnClickListener { openProjectivy() }
    }

    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 140).coerceIn(3, 10)
    }

    /**
     * Hands off to Projectivy's settings. We never claim we "applied" the pack —
     * the launcher owns that choice, so we say what we actually did.
     */
    private fun openProjectivy() {
        val launch = packageManager.getLaunchIntentForPackage(projectivyPackage)
        if (launch == null) {
            toast(getString(R.string.projectivy_missing))
            return
        }
        try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            toast(getString(R.string.projectivy_opened))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.projectivy_missing))
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
