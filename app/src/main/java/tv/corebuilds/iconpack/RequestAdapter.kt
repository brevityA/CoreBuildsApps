package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * The list of apps this television has no icon for.
 *
 * Selection follows focus. On a remote there is no pointer to hover with, so
 * making the user press select to see a row's detail would cost a press per app
 * on a screen whose whole job is browsing a short list of them.
 */
class RequestAdapter(
    private val items: List<UnmappedApps.App>,
    private val onSelected: (UnmappedApps.App) -> Unit,
) : RecyclerView.Adapter<RequestAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.request_app_label)
        val kind: TextView = view.findViewById(R.id.request_app_kind)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_request_app, parent, false)
        )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        holder.label.text = app.label
        holder.kind.setText(
            when (app.kind) {
                UnmappedApps.Kind.MISSING -> R.string.request_kind_missing
                UnmappedApps.Kind.MAPPING -> R.string.request_kind_mapping
            }
        )
        holder.itemView.setOnClickListener { onSelected(app) }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onSelected(app)
        }
    }

    override fun getItemCount(): Int = items.size
}
