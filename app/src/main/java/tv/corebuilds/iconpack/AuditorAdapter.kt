package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Rows for the unmapped-apps audit: the label a person recognises over the
 * component the issue form needs. The whole row is the focus target, the same
 * grammar as every other list in the app, and the content description says
 * what pressing will do - on this screen a row is a verb, not a tile.
 */
class AuditorAdapter(
    private val items: List<AuditorActivity.AuditItem>,
    private val onPick: (AuditorActivity.AuditItem) -> Unit
) : RecyclerView.Adapter<AuditorAdapter.VH>() {

    class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audit, parent, false) as LinearLayout
        return VH(row)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.row.findViewById<TextView>(R.id.audit_item_label).text = item.label
        holder.row.findViewById<TextView>(R.id.audit_item_pkg).text = item.component
        holder.row.contentDescription =
            "${item.label}, no icon yet, press to build a request"
        holder.row.setOnClickListener { onPick(item) }
    }

    override fun getItemCount(): Int = items.size
}
