package tv.corebuilds.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import org.xmlpull.v1.XmlPullParser

/**
 * Works out which apps on this television the pack has no icon for.
 *
 * The pack maps launcher activities, not packages: `appfilter.xml` holds
 * `ComponentInfo{package/activity}` entries, and a launcher only substitutes an
 * icon when the component matches exactly. That produces two different kinds of
 * miss, which want two different issue forms:
 *
 * * the package appears nowhere in the appfilter — there is no icon for this app
 *   at all, so it is a [Kind.MISSING] request;
 * * the package is mapped but this activity is not — an icon exists and simply
 *   is not being picked up, usually because the vendor shipped a different
 *   launch activity in this region or build. That is [Kind.MAPPING], and it
 *   needs the component far more than it needs artwork.
 *
 * Telling those apart on device is the point of the screen. A reporter cannot
 * get at a component without adb, and guessing `MainActivity` is exactly the
 * guess the issue form asks people not to make.
 */
object UnmappedApps {

    enum class Kind { MISSING, MAPPING }

    data class App(
        val label: String,
        val packageName: String,
        /** `package/fully.qualified.Activity`, as the appfilter spells it. */
        val component: String,
        val kind: Kind,
    )

    /**
     * Every launchable component the appfilter claims, and every package it
     * mentions. Relative activity names are expanded, because the appfilter
     * writes both `{pkg/.Main}` and `{pkg/com.vendor.Main}` for the same thing.
     *
     * Read through the resource parser, not as text: aapt compiles everything
     * under `res/xml/` to binary XML, so the file on device is not the file in
     * the tree.
     */
    private fun mapped(context: Context): Pair<Set<String>, Set<String>> {
        val components = HashSet<String>()
        val packages = HashSet<String>()
        // Closed by hand rather than with use(): XmlResourceParser only declares
        // AutoCloseable on recent SDK levels, and this pack still runs on 21.
        val parser = context.resources.getXml(R.xml.appfilter)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name != "item") continue
                val raw = parser.getAttributeValue(null, "component") ?: continue
                // ComponentInfo{package/activity}
                val open = raw.indexOf('{')
                val close = raw.lastIndexOf('}')
                if (open < 0 || close <= open) continue
                val value = raw.substring(open + 1, close)
                val slash = value.indexOf('/')
                if (slash <= 0) continue
                val pkg = value.substring(0, slash)
                val activity = value.substring(slash + 1)
                packages += pkg
                components += "$pkg/" +
                    if (activity.startsWith(".")) pkg + activity else activity
            }
        } finally {
            parser.close()
        }
        return components to packages
    }

    /** Launchable activities, from both the TV and the phone launcher category. */
    private fun launchable(context: Context): List<ResolveInfo> {
        val pm = context.packageManager
        val out = ArrayList<ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val found: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                // A broken or restricted package manager should cost us one
                // category, not the whole screen.
                emptyList()
            }
            out += found
        }
        return out
    }

    /**
     * Apps with no icon, sorted by name. Blocking: call it off the main thread.
     *
     * One entry per package. A TV app usually resolves under both launcher
     * categories, and listing it twice would make the count read as twice the
     * problem it is.
     */
    fun scan(context: Context): List<App> {
        val (components, packages) = mapped(context)
        val pm = context.packageManager
        val self = context.packageName
        val byPackage = LinkedHashMap<String, App>()
        val seen = HashSet<String>()

        for (info in launchable(context)) {
            val activity = info.activityInfo ?: continue
            val pkg = activity.packageName ?: continue
            if (pkg == self) continue
            val component = "$pkg/${activity.name}"
            // Settle the package on its first activity, before asking whether
            // that activity is mapped. The leanback category is queried first,
            // so the first one seen is what a television actually launches —
            // and if the pack already maps it, this app has its icon and the
            // package is done. Testing `component in components` first instead
            // let a *mapped* leanback activity fall through to the same app's
            // unmapped phone-launcher activity, which then got reported as
            // "icon exists, not applying" for an app whose icon applies fine.
            if (!seen.add(pkg)) continue
            if (component in components) continue
            val label = try {
                info.loadLabel(pm)?.toString()?.trim().orEmpty()
            } catch (e: Exception) {
                ""
            }
            byPackage[pkg] = App(
                label = label.ifEmpty { pkg },
                packageName = pkg,
                component = component,
                kind = if (pkg in packages) Kind.MAPPING else Kind.MISSING,
            )
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }
}
