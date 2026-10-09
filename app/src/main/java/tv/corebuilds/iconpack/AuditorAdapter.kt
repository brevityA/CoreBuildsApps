package tv.corebuilds.iconpack

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * The auditor list: a heading per kind of gap, then its rows. A row carries
 * the app's own launcher icon, the label a person recognises over the
 * component the report needs, what kind of app it is, and the verb the press
 * performs (REQUEST a new icon, or REPORT one that is not applying). The whole
 * row is the focus target, the same grammar as every other list in the app;
 * headings are not focusable, so the D-pad steps from row to row over them.
 */
class AuditorAdapter(
    items: List<AppAudit.Item>,
    private val pm: PackageManager,
    private val onPick: (AppAudit.Item) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Entry {
        data class Header(val kind: AppAudit.Kind, val count: Int) : Entry()
        data class Row(val item: AppAudit.Item) : Entry()
    }

    private val entries: List<Entry> = buildList {
        for (kind in listOf(AppAudit.Kind.NOT_APPLYING, AppAudit.Kind.NO_ICON)) {
            val rows = items.filter { it.kind == kind }
            if (rows.isEmpty()) continue
            add(Entry.Header(kind, rows.size))
            rows.forEach { add(Entry.Row(it)) }
        }
    }

    /** Launcher icons, loaded once per app: rebinding a row must not hit PackageManager. */
    private val icons = HashMap<String, Drawable?>()

    /** Adapter position of the first row, for initial focus. */
    val firstRowPosition: Int get() = entries.indexOfFirst { it is Entry.Row }.coerceAtLeast(0)

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.audit_header_title)
        val sub: TextView = view.findViewById(R.id.audit_header_sub)
    }

    class RowVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.audit_item_icon)
        val label: TextView = view.findViewById(R.id.audit_item_label)
        val tags: TextView = view.findViewById(R.id.audit_item_tags)
        val pkg: TextView = view.findViewById(R.id.audit_item_pkg)
        val mapped: TextView = view.findViewById(R.id.audit_item_mapped)
        val action: TextView = view.findViewById(R.id.audit_item_action)
    }

    override fun getItemViewType(position: Int): Int =
        if (entries[position] is Entry.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_audit_header, parent, false))
        } else {
            RowVH(inflater.inflate(R.layout.item_audit, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        when (val entry = entries[position]) {
            is Entry.Header -> {
                val h = holder as HeaderVH
                val notApplying = entry.kind == AppAudit.Kind.NOT_APPLYING
                h.title.text = context.getString(
                    if (notApplying) R.string.audit_section_not_applying_fmt
                    else R.string.audit_section_no_icon_fmt,
                    entry.count
                )
                h.sub.setText(
                    if (notApplying) R.string.audit_section_not_applying_sub
                    else R.string.audit_section_no_icon_sub
                )
            }
            is Entry.Row -> {
                val r = holder as RowVH
                val item = entry.item
                r.label.text = item.label
                r.tags.text = tags(context, item)
                r.pkg.text = item.component
                if (item.mapped && item.mappedAs.isNotEmpty()) {
                    r.mapped.text = context.getString(R.string.audit_mapped_as_fmt,
                                                      shortActivity(item.pkg, item.mappedAs.first()))
                    r.mapped.visibility = View.VISIBLE
                } else {
                    r.mapped.visibility = View.GONE
                }
                r.action.setText(
                    if (item.mapped) R.string.audit_action_report else R.string.audit_action_request
                )
                val icon = icons.getOrPut(item.pkg) {
                    runCatching { pm.getApplicationIcon(item.pkg) }.getOrNull()
                }
                r.icon.setImageDrawable(icon)
                r.itemView.contentDescription = context.getString(
                    if (item.mapped) R.string.audit_row_report_cd_fmt
                    else R.string.audit_row_request_cd_fmt,
                    item.label
                )
                r.itemView.setOnClickListener { onPick(item) }
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1

        fun tags(context: android.content.Context, item: AppAudit.Item): String =
            listOfNotNull(
                context.getString(if (item.tv) R.string.audit_tag_tv else R.string.audit_tag_phone),
                context.getString(R.string.audit_tag_system).takeIf { item.system },
            ).joinToString(" · ")

        /** `.ui.MainActivity` for an activity inside its own package, the full name otherwise. */
        fun shortActivity(pkg: String, activity: String): String =
            if (activity.startsWith("$pkg.")) activity.removePrefix(pkg) else activity
    }
}
