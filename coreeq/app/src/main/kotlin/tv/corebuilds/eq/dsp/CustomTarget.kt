package tv.corebuilds.eq.dsp

/**
 * The imported target curve, used whenever a target is requested as [KEY].
 *
 * Routing the custom curve through [Targets.targetCurve] means every analysis path
 * (sweep, REW import, re-analysis, profile display) reads the same curve without
 * each one needing a new parameter. The holder is set by the TV's Measure screen
 * before it analyses; the phone companion cannot choose [KEY], because its target
 * list is fixed.
 */
object CustomTarget {
    const val KEY = "custom"

    @Volatile
    var points: List<Pair<Double, Double>>? = null

    /** The imported curve in dB on [freqs], or zeros when nothing is imported. */
    fun targetDb(freqs: DoubleArray): DoubleArray =
        points?.let { TargetCurveImport.targetDb(it, freqs) } ?: DoubleArray(freqs.size)
}
