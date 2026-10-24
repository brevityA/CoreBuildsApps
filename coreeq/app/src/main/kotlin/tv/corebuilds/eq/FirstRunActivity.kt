package tv.corebuilds.eq

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import tv.corebuilds.eq.apply.OutputRoute

/** Short first-install path; advanced ADB work stays optional and is never required to measure. */
class FirstRunActivity : TvActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_run)

        val output = OutputRoute.current(this)
        val outputLabel = output?.let {
            val label = OutputRoute.label(it.kind)
            val name = it.name.trim().takeIf { value -> value.isNotEmpty() && !value.equals(label, ignoreCase = true) }
            if (name == null) label else "$label · $name"
        } ?: getString(R.string.first_run_output_unknown)
        findViewById<TextView>(R.id.first_run_output).text = getString(R.string.first_run_output, outputLabel)

        findViewById<Button>(R.id.btn_first_run_measure).setOnClickListener {
            complete()
            startActivity(Intent(this, MeasureActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btn_first_run_setup).setOnClickListener {
            complete()
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btn_first_run_later).setOnClickListener {
            complete()
            finish()
        }
        findViewById<Button>(R.id.btn_first_run_measure).requestFocus()
    }

    private fun complete() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    companion object {
        const val PREFS = "coreeq_first_run"
        const val KEY_COMPLETED = "welcome_completed"
    }
}
