package tv.corebuilds.iconpack

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * The compatibility list: one card per launcher in
 * [ApplyIconPack.COMPATIBILITY]. A card says what art the launcher applies,
 * how the pack reaches it, and its first note - everything read from the
 * [ApplyIconPack.Launcher] data, so the card cannot claim anything the apply
 * flow would not do.
 *
 * Not focusable: the card is read, not pressed (the FAQ grammar), and the
 * RecyclerView owns the D-pad.
 */
class LauncherCompatAdapter(
    private val launchers: List<ApplyIconPack.Launcher>,
    private val homeKey: String?,
    private val installedKeys: Set<String>,
    private val context: Context
) : RecyclerView.Adapter<LauncherCompatAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.launcher_compat_item_name)
        val status: TextView = view.findViewById(R.id.launcher_compat_item_status)
        val mode: TextView = view.findViewById(R.id.launcher_compat_item_mode)
        val art: TextView = view.findViewById(R.id.launcher_compat_item_art)
        val note: TextView = view.findViewById(R.id.launcher_compat_item_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_launcher_compat, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val launcher = launchers[position]
        holder.name.text = launcher.displayName
        holder.name.contentDescription = holder.name.text

        when (launcher.applyMode) {
            ApplyIconPack.ApplyMode.AUTO -> holder.mode.setText(R.string.launcher_compat_mode_auto)
            ApplyIconPack.ApplyMode.WALK -> holder.mode.setText(R.string.launcher_compat_mode_walk)
            ApplyIconPack.ApplyMode.PER_APP ->
                holder.mode.setText(R.string.launcher_compat_mode_per_app)
            ApplyIconPack.ApplyMode.NONE -> holder.mode.setText(R.string.launcher_compat_mode_none)
        }

        when (launcher.art) {
            ApplyIconPack.Art.BANNERS -> holder.art.setText(R.string.launcher_compat_art_banners)
            ApplyIconPack.Art.GLYPHS -> holder.art.setText(R.string.launcher_compat_art_glyphs)
            ApplyIconPack.Art.NONE -> holder.art.setText(R.string.launcher_compat_art_none)
        }

        val isHome = launcher.key == homeKey
        val isInstalled = launcher.key in installedKeys
        when {
            isHome -> {
                holder.status.setText(R.string.launcher_compat_active)
                holder.status.setTextColor(
                    ContextCompat.getColor(context, R.color.cb_signal_cyan))
                holder.status.visibility = View.VISIBLE
            }
            isInstalled -> {
                holder.status.setText(R.string.launcher_compat_installed)
                holder.status.setTextColor(
                    ContextCompat.getColor(context, R.color.cb_slate))
                holder.status.visibility = View.VISIBLE
            }
            else -> holder.status.visibility = View.GONE
        }

        val firstNote = launcher.notes.firstOrNull()
        if (firstNote != null) {
            holder.note.text = firstNote
            holder.note.visibility = View.VISIBLE
        } else {
            holder.note.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = launchers.size
}
