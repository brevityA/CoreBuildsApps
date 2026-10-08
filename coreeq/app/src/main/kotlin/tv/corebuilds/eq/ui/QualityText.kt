package tv.corebuilds.eq.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.R
import tv.corebuilds.eq.dsp.MeasurementQuality

/**
 * The measurement quality line, worded and coloured the same on Measure,
 * Home and Profiles: "Quality 78/100 · good" in green, amber or red.
 */
object QualityText {

    fun text(context: Context, score: Int): String =
        context.getString(R.string.quality_score, score, context.getString(gradeRes(score)))

    fun color(context: Context, score: Int): Int = ContextCompat.getColor(
        context,
        when (MeasurementQuality.tier(score)) {
            MeasurementQuality.Tier.GREEN -> R.color.cb_success
            MeasurementQuality.Tier.AMBER -> R.color.cb_warning
            MeasurementQuality.Tier.RED -> R.color.cb_ember
        }
    )

    /**
     * "Quality 78/100 · good" in the tier colour, then " · " and [base], or
     * [base] unchanged when the profile carries no score (imports, manual-only
     * profiles and anything saved before 1.3.0). The score leads because Home
     * and Profiles both end their detail lines in an ellipsis.
     */
    fun prependTo(context: Context, base: CharSequence, score: Int?): CharSequence {
        if (score == null) return base
        val out = SpannableStringBuilder(text(context, score))
        out.setSpan(ForegroundColorSpan(color(context, score)), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return out.append(" · ").append(base)
    }

    private fun gradeRes(score: Int): Int = when (MeasurementQuality.grade(score)) {
        MeasurementQuality.Grade.EXCELLENT -> R.string.quality_excellent
        MeasurementQuality.Grade.GOOD -> R.string.quality_good
        MeasurementQuality.Grade.FAIR -> R.string.quality_fair
        MeasurementQuality.Grade.POOR -> R.string.quality_poor
        MeasurementQuality.Grade.REMEASURE -> R.string.quality_remeasure
    }
}
