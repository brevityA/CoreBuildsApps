package tv.corebuilds.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager

/**
 * The scan behind "Apps without a Core Builds icon", shared by the auditor's
 * list and the count on the home screen's Missing icons row, so the two can
 * never disagree.
 *
 * What it compares: every launchable activity on this TV against the
 * components the bundled `appfilter.xml` names, at component level. A launcher
 * looks icons up by the exact component it launches, so an app counts as
 * covered only when that component is named; the package merely appearing
 * somewhere is not enough (see [Kind.NOT_APPLYING]).
 *
 * Which component a launcher launches: the leanback pass runs first, so for a
 * package with both a TV and a phone entry the TV one decides, which is what
 * every TV launcher shows. Each package is settled on the first activity seen
 * before asking whether it is mapped; checking `in mapped` first let a mapped
 * leanback activity fall through to the same app's unmapped phone activity,
 * which listed an app whose icon applies fine.
 *
 * Visibility: the manifest's `<queries>` carries both launcher categories as
 * intent filters, which makes every launchable package visible to
 * `queryIntentActivities` without QUERY_ALL_PACKAGES.
 */
object AppAudit {

    enum class Kind {
        /** appfilter names nothing in this package: a new-icon request. */
        NO_ICON,

        /**
         * appfilter names this package, but under another activity, so the
         * launcher looks up a component the pack does not map and the icon
         * does not apply. Usually an app update renamed its launch activity.
         * The not-applying form's report, not a new-icon request.
         */
        NOT_APPLYING,
    }

    /**
     * One launchable app the pack does not cover. [tv] is true for a leanback
     * (TV launcher) entry, false for a phone-style launcher entry that only
     * launchers set to show every app will draw. [system] is a preinstalled
     * app the user never installed (vendor tools, the TV's own settings).
     * [mappedAs] is what appfilter names for this package instead, for
     * [Kind.NOT_APPLYING] rows.
     */
    data class Item(
        val label: String,
        val pkg: String,
        val activity: String,
        val kind: Kind,
        val tv: Boolean,
        val system: Boolean,
        val mappedAs: List<String> = emptyList(),
    ) {
        val component: String get() = "$pkg/$activity"
        val mapped: Boolean get() = kind == Kind.NOT_APPLYING
    }

    /** [items] the pack does not cover, in display order, out of [checked] launchable apps. */
    data class Result(val items: List<Item>, val checked: Int)

    @Volatile private var mappedCache: Map<String, List<String>>? = null

    /** Launchable apps on this TV, other than this pack, checked against appfilter. */
    fun scan(context: Context): Result {
        val pm = context.packageManager
        val mapped = mappedByPackage(context.assets)
        val found = ArrayList<Item>()
        val seen = HashSet<String>()
        for ((category, tv) in listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER to true,
            Intent.CATEGORY_LAUNCHER to false,
        )) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) continue
                if (!seen.add(pkg)) continue
                val activity = normalize(pkg, info.activityInfo.name)
                val names = mapped[pkg]
                if (names != null && activity in names) continue
                val flags = info.activityInfo.applicationInfo?.flags ?: 0
                found += Item(
                    label = info.loadLabel(pm).toString().trim().ifEmpty { pkg },
                    pkg = pkg,
                    activity = activity,
                    kind = if (names == null) Kind.NO_ICON else Kind.NOT_APPLYING,
                    tv = tv,
                    system = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                    mappedAs = names.orEmpty(),
                )
            }
        }
        return Result(sorted(found), seen.size)
    }

    /**
     * Display order: apps whose icon exists but is not applying first (the
     * pack already has art for them, so a report fixes them fastest), then
     * apps with no icon. Within each, what the TV launcher shows comes first:
     * TV apps before phone-style ones, apps the user installed before
     * preinstalled ones, then by name.
     */
    fun sorted(items: List<Item>): List<Item> = items.sortedWith(
        compareBy<Item>(
            { if (it.kind == Kind.NOT_APPLYING) 0 else 1 },
            { if (it.tv) 0 else 1 },
            { if (it.system) 1 else 0 },
            { it.label.lowercase() },
        )
    )

    /**
     * Every component `appfilter.xml` names, grouped by package, each
     * activity in the absolute form PackageManager reports. Keyed on the
     * package because an absolute activity name alone is not unique: vendors
     * ship one class under several application IDs. Read once per process.
     */
    fun mappedByPackage(assets: AssetManager): Map<String, List<String>> =
        mappedCache ?: parse(assets.open("appfilter.xml").bufferedReader().use { it.readText() })
            .also { mappedCache = it }

    fun parse(xml: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (m in Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}").findAll(xml)) {
            val pkg = m.groupValues[1].trim()
            val activity = normalize(pkg, m.groupValues[2].trim())
            val list = out.getOrPut(pkg) { ArrayList() }
            if (activity !in list) list += activity
        }
        return out
    }

    /**
     * One spelling for a component, so the appfilter's `pkg/.ui.SplashActivity`
     * relative form compares equal to PackageManager's always-absolute
     * `com.pkg.ui.SplashActivity`.
     */
    fun normalize(pkg: String, activity: String): String =
        if (activity.startsWith(".")) pkg + activity else activity
}
