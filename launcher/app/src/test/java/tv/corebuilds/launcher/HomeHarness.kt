package tv.corebuilds.launcher

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * What every home test needs: a TV with a set of installed apps, a way to
 * press the remote, and a way to look at the result.
 *
 * The apps are invented names with lettered icons and simple gradient
 * banners drawn here, so the tests never depend on a real vendor's artwork.
 * 960 x 540 dp at xhdpi is a 1920 x 1080 TV.
 */
object HomeHarness {
    const val TV = "en-rUS-w960dp-h540dp-land-television-xhdpi"

    data class FakeApp(val pkg: String, val label: String, val tv: Boolean, val banner: Boolean, val color: Int)

    val CORE_APPS = listOf(
        FakeApp("tv.corebuilds.iconpack", "Core Builds Icon Pack", tv = true, banner = true, color = 0xFF00A6C8.toInt()),
        FakeApp("tv.corebuilds.eq", "Core EQ", tv = true, banner = true, color = 0xFF0E7490.toInt()),
        FakeApp("dev.corebuilds.shift", "Core Shift", tv = true, banner = false, color = 0xFF00D4FF.toInt()),
    )

    val OTHER_APPS = listOf(
        FakeApp("org.example.atlas", "Atlas Video", tv = true, banner = true, color = 0xFFC2410C.toInt()),
        FakeApp("org.example.beacon", "Beacon Music", tv = true, banner = true, color = 0xFF15803D.toInt()),
        FakeApp("org.example.cinder", "Cinder Live", tv = true, banner = true, color = 0xFFB91C1C.toInt()),
        FakeApp("org.example.drift", "Drift Player", tv = true, banner = false, color = 0xFF7C3AED.toInt()),
        FakeApp("org.example.ember", "Ember Stream", tv = true, banner = true, color = 0xFFEA580C.toInt()),
        FakeApp("org.example.fable", "Fable Kids", tv = true, banner = true, color = 0xFFDB2777.toInt()),
        FakeApp("org.example.glide", "Glide Files", tv = false, banner = false, color = 0xFF2563EB.toInt()),
        FakeApp("org.example.harbor", "Harbor News", tv = true, banner = true, color = 0xFF1D4ED8.toInt()),
        FakeApp("org.example.indigo", "Indigo Sports", tv = true, banner = true, color = 0xFF4338CA.toInt()),
        FakeApp("org.example.juniper", "Juniper Radio", tv = true, banner = false, color = 0xFF0F766E.toInt()),
        FakeApp("org.example.kestrel", "Kestrel Photos", tv = false, banner = false, color = 0xFFA16207.toInt()),
        FakeApp("org.example.lumen", "Lumen Browser", tv = false, banner = false, color = 0xFF0369A1.toInt()),
        FakeApp("org.example.meridian", "Meridian Weather", tv = true, banner = true, color = 0xFF0284C7.toInt()),
        FakeApp("org.example.nimbus", "Nimbus Cloud", tv = false, banner = false, color = 0xFF475569.toInt()),
        FakeApp("org.example.orbit", "Orbit Games", tv = true, banner = true, color = 0xFF9333EA.toInt()),
        FakeApp("org.example.pine", "Pine Podcasts", tv = true, banner = false, color = 0xFF16A34A.toInt()),
        FakeApp("org.example.longname", "Very Long Application Name That Tests the Spotlight Title", tv = false, banner = false, color = 0xFF64748B.toInt()),
    )

    val ALL_APPS = CORE_APPS + OTHER_APPS

    /** A TV out of touch mode with [apps] installed. Call before launching the activity. */
    fun prepareTv(apps: List<FakeApp> = ALL_APPS) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        apps.forEach { install(it) }
    }

    private var nextResource = 0x7f990001

    fun install(fake: FakeApp) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = shadowOf(app.packageManager)
        val iconId = nextResource++
        val bannerId = nextResource++
        pm.addDrawableResolution(fake.pkg, iconId, BitmapDrawable(app.resources, icon(fake)))
        if (fake.banner) pm.addDrawableResolution(fake.pkg, bannerId, BitmapDrawable(app.resources, banner(fake)))
        val applicationInfo = ApplicationInfo().apply { packageName = fake.pkg; nonLocalizedLabel = fake.label }
        fun entry(tv: Boolean) = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = fake.pkg
                name = fake.pkg + if (tv) ".TvMain" else ".Main"
                this.applicationInfo = applicationInfo
                nonLocalizedLabel = fake.label
                icon = iconId
                if (tv && fake.banner) banner = bannerId
                exported = true
            }
        }
        if (fake.tv) pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), entry(true))
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), entry(false))
    }

    fun uninstall(fake: FakeApp) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = shadowOf(app.packageManager)
        pm.removeResolveInfosForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), fake.pkg)
        pm.removeResolveInfosForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), fake.pkg)
    }

    private fun icon(fake: FakeApp): Bitmap {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fake.color }
        canvas.drawRoundRect(RectF(12f, 12f, 180f, 180f), 44f, 44f, paint)
        paint.color = Color.WHITE
        paint.textSize = 100f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText(fake.label.take(1), 96f, 132f, paint)
        return bitmap
    }

    private fun banner(fake: FakeApp): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 640f, 360f, fake.color, Color.rgb(8, 10, 18), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 640f, 360f, paint)
        paint.shader = null
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawCircle(560f, 70f, 150f, paint)
        paint.color = Color.WHITE
        paint.textSize = 64f
        paint.isFakeBoldText = true
        canvas.drawText(fake.label.uppercase().take(12), 40f, 300f, paint)
        return bitmap
    }

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Runs the main looper and lets animations finish: the 150 ms lift, the 220 ms scroll, the 280 ms fade. */
    fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        idle()
    }

    /** The catalog loads on a background thread; run the main looper until the shelves exist. */
    fun waitForApps(activity: Activity, expectCards: Boolean = true) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (!expectCards || cards(activity).isNotEmpty()) break
            Thread.sleep(10)
        }
        // Let a coalesced second scan (onResume after onCreate) land too.
        repeat(20) {
            idle()
            Thread.sleep(5)
        }
        settle()
    }

    fun focused(activity: Activity): View? = activity.window.currentFocus

    fun press(activity: Activity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        settle()
    }

    fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    internal fun cards(activity: Activity): List<FocusCard> =
        descendants(activity.window.decorView).filterIsInstance<FocusCard>().filter { it.isShown }.toList()

    internal fun appCards(activity: Activity, kind: RowKind): List<AppCard> =
        cards(activity).filterIsInstance<AppCard>().filter { it.kind == kind }

    fun name(view: View?): String = when (view) {
        null -> "nothing"
        is AppCard -> "${view.kind}:${view.app.label}"
        is AddCard -> "Add card"
        is TextView -> "text(${view.text})"
        else -> view.contentDescription?.toString() ?: view.javaClass.simpleName
    }

    /**
     * The share of [view] (as drawn, after its focus scale) that shows on screen.
     * getGlobalVisibleRect applies each view's transform and every parent's clip.
     */
    fun shownShare(view: View): Double {
        val seen = Rect()
        if (!view.getGlobalVisibleRect(seen)) return 0.0
        val drawn = view.width * view.scaleX * view.height * view.scaleY
        return if (drawn <= 0) 0.0 else seen.width().toDouble() * seen.height() / drawn
    }

    /** Text on screen that does not fit its box: cut short with an ellipsis, or taller than its box. */
    fun textProblems(activity: Activity): List<String> {
        val out = ArrayList<String>()
        descendants(activity.window.decorView).filterIsInstance<TextView>().forEach { view ->
            if (!view.isShown || view.width == 0 || view.text.isNullOrEmpty()) return@forEach
            // A card's own name may end in an ellipsis; the info area shows it whole on focus.
            if (view.tag == AppCard.CARD_LABEL) return@forEach
            val layout = view.layout ?: return@forEach
            if ((0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }) out += "'${view.text}' is cut short with an ellipsis"
            val room = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
            if (layout.height > room + 2) out += "'${view.text}' needs ${layout.height}px of height and has $room"
        }
        return out
    }

    /** Draws the window, and an open panel over it, into build/screens/<name>.png. */
    fun render(activity: Activity, name: String): File {
        settle()
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(System.getProperty("launcher.screens.dir") ?: "build/screens").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
