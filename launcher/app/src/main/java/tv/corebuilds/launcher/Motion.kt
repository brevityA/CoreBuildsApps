package tv.corebuilds.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToLong

/**
 * One place for every launcher transition. [MotionMode] is the user's choice;
 * Android's ANIMATOR_DURATION_SCALE is the system accessibility choice. A zero
 * on either side turns the change into an immediate state change, so reduced
 * motion removes movement rather than merely making it faster.
 */
internal object Motion {
    private const val FOCUS_MS = 150L
    private const val REVEAL_MS = 170L
    private const val SCROLL_MS = 220L
    private const val SHEET_MS = 200L
    private const val BACKDROP_MS = 280L
    private const val MAX_MS = 600L

    @Volatile
    private var mode = MotionMode.FULL
    private val scrolls = HashMap<View, ObjectAnimator>()

    fun configure(settings: HomeSettings) {
        mode = settings.motion
    }

    /** The documented Android reduced-motion signal; 0f means no animator motion. */
    private fun systemScale(context: Context): Float = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ).coerceIn(0f, 2f)
    } catch (_: Exception) {
        1f
    }

    fun duration(context: Context, baseMs: Long): Long =
        (baseMs * mode.factor * systemScale(context)).roundToLong().coerceIn(0L, MAX_MS)

    fun backdropMs(context: Context): Long = duration(context, BACKDROP_MS)

    /** Stops scroll work when Home is paused; transform animations self-finish. */
    fun cancelScrolls() {
        synchronized(scrolls) {
            scrolls.values.forEach { it.cancel() }
            scrolls.clear()
        }
    }

    fun focus(view: View, focused: Boolean, scale: Float) {
        val duration = duration(view.context, FOCUS_MS)
        val target = if (focused) scale else 1f
        view.animate().cancel()
        if (duration == 0L) {
            view.scaleX = target
            view.scaleY = target
            return
        }
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    /** Full-width panel rows get a small horizontal cue; Android TV advises against scaling them. */
    fun panelFocus(view: View, focused: Boolean) {
        val duration = duration(view.context, FOCUS_MS)
        val target = if (focused) view.context.dp(4).toFloat() else 0f
        view.animate().cancel()
        if (duration == 0L) {
            view.translationX = target
            return
        }
        view.animate()
            .translationX(target)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    /** A short fade-and-rise for a changed info area; never called on identical text. */
    fun reveal(view: View) {
        val duration = duration(view.context, REVEAL_MS)
        view.animate().cancel()
        if (duration == 0L) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0.55f
        view.translationY = view.context.dp(6).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    /** One scroll animator per view: a new target cancels the old one instead of fighting it. */
    fun scroll(view: View, property: String, target: Int) {
        val current = if (property == "scrollY") view.scrollY else view.scrollX
        if (current == target) return
        synchronized(scrolls) { scrolls[view]?.cancel() }
        val duration = duration(view.context, SCROLL_MS)
        if (duration == 0L) {
            if (property == "scrollY") view.scrollY = target else view.scrollX = target
            return
        }
        val animator = ObjectAnimator.ofInt(view, property, current, target).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.25f)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                synchronized(scrolls) {
                    if (scrolls[view] === animation) scrolls.remove(view)
                }
            }
        })
        synchronized(scrolls) { scrolls[view] = animator }
        animator.start()
    }

    /** Slides a right-hand sheet in once. Closing stays immediate so Back never waits. */
    fun sheetIn(sheet: View, width: Float) {
        val duration = duration(sheet.context, SHEET_MS)
        sheet.animate().cancel()
        if (duration == 0L) {
            sheet.translationX = 0f
            return
        }
        sheet.translationX = width
        sheet.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }
}
