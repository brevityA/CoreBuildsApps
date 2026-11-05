package tv.corebuilds.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack, as the Customise › Artwork list shows it. */
internal data class IconPackInfo(val packageName: String, val label: String)

/**
 * One loaded icon pack: its appfilter as a map from component to drawable name,
 * and a loader for those drawables. A component the pack does not list falls
 * back to the first drawable the pack lists for the same package, because packs
 * map an app's phone and TV activities separately and a TV may expose only one.
 */
internal class IconPack(
    val packageName: String,
    private val byComponent: Map<String, String>,
    private val byPackage: Map<String, String>,
    private val loadDrawable: (String) -> Drawable?,
) {
    val mappedComponents: Int get() = byComponent.size

    fun drawableFor(packageName: String, activityName: String): Drawable? {
        val name = byComponent["$packageName/$activityName"] ?: byPackage[packageName] ?: return null
        return try {
            loadDrawable(name)
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: RuntimeException) {
            // A pack can name a drawable it does not ship, or ship a corrupt one.
            null
        }
    }
}

/**
 * Icon packs through the open launcher contract: a pack exposes an activity for
 * org.adw.launcher.THEMES and ships res/xml/appfilter.xml (or assets/appfilter.xml)
 * mapping ComponentInfo{package/activity} to drawable names. The Core Builds Icon
 * Pack maps every app to a 16:9 banner; its glyph builds map them to square glyphs.
 */
internal object IconPacks {
    const val THEMES_ACTION = "org.adw.launcher.THEMES"
    const val CORE_BUILDS_BANNERS = "tv.corebuilds.iconpack"
    /** The glyphs flavor and the resource-only companion the icon pack installs. */
    val CORE_BUILDS_GLYPHS = listOf("tv.corebuilds.glyphs", "tv.corebuilds.iconpack.glyphs")

    private data class CacheKey(val packageName: String, val lastUpdateTime: Long)

    private var cached: Pair<CacheKey, IconPack>? = null

    /** Installed packs: Core Builds banners, then Core Builds glyphs, then the rest A-Z. */
    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val found = linkedMapOf<String, IconPackInfo>()
        for (info in pm.queryActivities(Intent(THEMES_ACTION))) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (packageName == context.packageName || packageName in found) continue
            val label = (pm.applicationInfoOrNull(packageName)?.let { pm.getApplicationLabel(it).toString() }
                ?: info.loadLabel(pm)?.toString().orEmpty()).ifBlank { packageName }
            found[packageName] = IconPackInfo(packageName, label)
        }
        return found.values.sortedWith(
            compareBy<IconPackInfo> { rank(it.packageName) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
    }

    private fun rank(packageName: String): Int = when (packageName) {
        CORE_BUILDS_BANNERS -> 0
        in CORE_BUILDS_GLYPHS -> 1
        else -> 2
    }

    /** The pack a [choice] resolves to on this TV, or null for app artwork. */
    fun resolve(choice: ArtworkChoice, installed: List<IconPackInfo>): IconPackInfo? = when (choice) {
        ArtworkChoice.Automatic -> installed.firstOrNull { it.packageName == CORE_BUILDS_BANNERS }
        ArtworkChoice.AppArtwork -> null
        is ArtworkChoice.IconPack -> installed.firstOrNull { it.packageName == choice.packageName }
    }

    /**
     * Loads [packageName]'s appfilter once per installed version; later calls reuse it.
     * The pack's resources are looked up by name: that is the icon-pack contract.
     */
    @SuppressLint("DiscouragedApi")
    @Synchronized
    fun load(context: Context, packageName: String): IconPack? {
        val pm = context.packageManager
        val updated = pm.packageInfoOrNull(packageName)?.lastUpdateTime ?: return null
        val key = CacheKey(packageName, updated)
        cached?.takeIf { it.first == key }?.let { return it.second }
        val resources = try {
            pm.getResourcesForApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val parser = appfilterParser(resources, packageName) ?: return null
        val (byComponent, byPackage) = try {
            parse(parser)
        } catch (_: Exception) {
            // A malformed appfilter means no pack art, never a crashed home screen.
            return null
        }
        val pack = IconPack(packageName, byComponent, byPackage) { name ->
            val id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) null else resources.getDrawable(id, null)
        }
        cached = key to pack
        return pack
    }

    @SuppressLint("DiscouragedApi")
    private fun appfilterParser(resources: Resources, packageName: String): XmlPullParser? {
        val id = resources.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return resources.getXml(id)
        return try {
            Xml.newPullParser().apply { setInput(resources.assets.open("appfilter.xml"), "UTF-8") }
        } catch (_: Exception) {
            null
        }
    }

    /** component ("package/fully.qualified.Activity") -> drawable, and package -> first drawable. */
    fun parse(parser: XmlPullParser): Pair<Map<String, String>, Map<String, String>> {
        val byComponent = linkedMapOf<String, String>()
        val byPackage = linkedMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = normalizeComponent(parser.getAttributeValue(null, "component"))
                val drawable = parser.getAttributeValue(null, "drawable")?.trim()
                if (component != null && !drawable.isNullOrEmpty()) {
                    byComponent.putIfAbsent(component, drawable)
                    byPackage.putIfAbsent(component.substringBefore('/'), drawable)
                }
            }
            event = parser.next()
        }
        return byComponent to byPackage
    }

    /** "ComponentInfo{pkg/.Main}" (or a bare "pkg/.Main") -> "pkg/pkg.Main"; null when it is not a component. */
    fun normalizeComponent(raw: String?): String? {
        val inner = raw?.trim()?.removePrefix("ComponentInfo{")?.removeSuffix("}") ?: return null
        val packageName = inner.substringBefore('/', "").trim()
        val activity = inner.substringAfter('/', "").trim()
        if (packageName.isEmpty() || activity.isEmpty() || activity.contains('/')) return null
        return "$packageName/${if (activity.startsWith('.')) packageName + activity else activity}"
    }
}
