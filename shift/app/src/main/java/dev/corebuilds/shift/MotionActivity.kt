package dev.corebuilds.shift

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MotionActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motion)

        recycler = findViewById(R.id.motion_list)
        emptyView = findViewById(R.id.motion_empty)

        recycler.layoutManager = LinearLayoutManager(this)

        MotionCatalog.load(this) { entries ->
            if (entries.isEmpty()) {
                recycler.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recycler.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                recycler.adapter = MotionAdapter(this, entries)
            }
        }
    }
}
