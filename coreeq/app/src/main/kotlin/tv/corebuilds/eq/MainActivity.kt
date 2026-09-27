package tv.corebuilds.eq

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class MainActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_measure)?.setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        findViewById<Button>(R.id.btn_profiles)?.setOnClickListener {
            startActivity(Intent(this, ProfilesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_capability)?.setOnClickListener {
            startActivity(Intent(this, CapabilityActivity::class.java))
        }
    }
}
