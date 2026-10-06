package tv.corebuilds.eq.apply

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.mode.ContentType
import kotlin.math.roundToInt

/**
 * Enhanced audio effect chain that combines multiple AudioEffect types
 * for superior audio processing beyond basic EQ.
 * 
 * Effect chain order:
 * 1. Equalizer (parametric EQ from room correction)
 * 2. BassBoost (low-frequency enhancement)
 * 3. LoudnessEnhancer (perceived loudness optimization)
 * 4. DynamicsProcessing (dynamic range control, API 28+)
 * 
 * This multi-effect approach allows:
 * - Room correction (EQ)
 * - Bass enhancement for content types that benefit (movies, gaming)
 * - Loudness normalization for consistent volume across content
 * - Dynamic range control for late-night listening
 */
class EnhancedEffectChain(
    val audioSessionId: Int,
    private val packageName: String? = null
) {
    var equalizer: Equalizer? = null
        private set
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    
    private var contentType: ContentType = ContentType.GENERAL
    private var nightMode: Boolean = false
    private var bassEnhancement: Boolean = false
    
    companion object {
        private const val TAG = "EnhancedEffectChain"
        
        // Bass boost strength by content type (0-1000, where 1000 = max boost)
        private val BASS_BOOST_STRENGTH = mapOf(
            ContentType.MOVIE to 600,      // Strong bass for cinematic impact
            ContentType.GAMING to 500,     // Moderate bass for immersion
            ContentType.MUSIC to 400,      // Balanced bass for music
            ContentType.ANIME to 300,      // Light bass enhancement
            ContentType.TV_SHOW to 200,    // Minimal bass for dialogue clarity
            ContentType.PODCAST to 0,      // No bass boost for speech
            ContentType.NEWS to 0,         // No bass boost for news
            ContentType.DOCUMENTARY to 100, // Subtle bass
            ContentType.SITCOM to 150,     // Light bass
            ContentType.GENERAL to 300     // Moderate default
        )
        
        // Loudness enhancement by content type (in millibels)
        private val LOUDNESS_ENHANCEMENT_MB = mapOf(
            ContentType.MOVIE to 200,      // Slight loudness boost
            ContentType.GAMING to 150,
            ContentType.MUSIC to 0,        // No enhancement (preserve dynamics)
            ContentType.ANIME to 100,
            ContentType.TV_SHOW to 300,    // Boost for dialogue clarity
            ContentType.PODCAST to 400,    // Strong boost for speech
            ContentType.NEWS to 400,
            ContentType.DOCUMENTARY to 300,
            ContentType.SITCOM to 250,
            ContentType.GENERAL to 200
        )
    }
    
    /**
     * Initialize the effect chain with room correction filters
     */
    fun initialize(
        filters: List<PeakingFilter>,
        preampGainDb: Double,
        contentType: ContentType = ContentType.GENERAL,
        enableBassBoost: Boolean = false,
        enableLoudnessEnhancer: Boolean = true,
        enableDynamicsProcessing: Boolean = true,
        enableNightMode: Boolean = false
    ): Boolean {
        this.contentType = contentType
        this.bassEnhancement = enableBassBoost
        this.nightMode = enableNightMode && enableDynamicsProcessing && android.os.Build.VERSION.SDK_INT >= 28
        
        var success = true
        
        try {
            // 1. Initialize Equalizer (primary effect)
            success = success && initializeEqualizer(filters, preampGainDb)
            
            // 2. Initialize BassBoost (optional enhancement)
            if (enableBassBoost) {
                success = success && initializeBassBoost()
            }
            
            // 3. Initialize LoudnessEnhancer (recommended)
            if (enableLoudnessEnhancer) {
                success = success && initializeLoudnessEnhancer()
            }
            
            // 4. Initialize DynamicsProcessing (API 28+, optional)
            if (enableDynamicsProcessing && android.os.Build.VERSION.SDK_INT >= 28) {
                success = success && initializeDynamicsProcessing()
            }
            
            Log.i(TAG, "Effect chain initialized: session=$audioSessionId, " +
                    "contentType=$contentType, effects=${getActiveEffectCount()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize effect chain", e)
            success = false
        }
        
        return success
    }
    
    private fun initializeEqualizer(filters: List<PeakingFilter>, preampGainDb: Double): Boolean {
        return try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                
                // Apply parametric EQ filters
                val numBands = numberOfBands.toInt()
                for (i in 0 until numBands) {
                    val centerFreq = getCenterFreq(i.toShort()) / 1000.0 // Convert to Hz
                    
                    // Find closest matching filter
                    val closestFilter = filters.minByOrNull { 
                        kotlin.math.abs(it.fc - centerFreq)
                    }
                    
                    if (closestFilter != null && kotlin.math.abs(closestFilter.fc - centerFreq) < 100.0) {
                        // Apply gain (convert dB to millibels)
                        val gainMb = (closestFilter.gain * 100).roundToInt().toShort()
                        val clampedGain = gainMb.coerceIn(bandLevelRange[0], bandLevelRange[1])
                        setBandLevel(i.toShort(), clampedGain)
                    }
                }
                
                // Apply preamp gain if supported
                if (preampGainDb != 0.0) {
                    try {
                        val properties = properties
                        // Note: Standard Equalizer doesn't have preamp, but some implementations do
                        Log.d(TAG, "Preamp gain requested: ${preampGainDb}dB (may not be supported)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Preamp gain not supported by this Equalizer implementation")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Equalizer", e)
            false
        }
    }
    
    private fun initializeBassBoost(): Boolean {
        return try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                val strength = BASS_BOOST_STRENGTH[contentType] ?: 300
                setStrength(strength.toShort())
                Log.d(TAG, "BassBoost initialized: strength=$strength")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BassBoost", e)
            false
        }
    }
    
    private fun initializeLoudnessEnhancer(): Boolean {
        return try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = true
                val gainMb = LOUDNESS_ENHANCEMENT_MB[contentType] ?: 200
                setTargetGain(gainMb)
                Log.d(TAG, "LoudnessEnhancer initialized: gain=${gainMb}mB")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LoudnessEnhancer", e)
            false
        }
    }
    
    private fun initializeDynamicsProcessing(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 28) {
            return false
        }
        
        return try {
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId).apply {
                enabled = true
                
                // Configure for content type
                val config = when {
                    nightMode -> createNightModeConfig()
                    contentType == ContentType.MOVIE -> createMovieConfig()
                    contentType == ContentType.GAMING -> createGamingConfig()
                    else -> createDefaultConfig()
                }
                
                // Apply the selected processing configuration to the effect.
                setConfig(config)
                Log.d(TAG, "DynamicsProcessing initialized for $contentType")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DynamicsProcessing", e)
            false
        }
    }
    
    private fun createNightModeConfig(): DynamicsProcessing.Config {
        // Night mode: compress dynamic range for quiet listening
        return DynamicsProcessing.Config.Builder()
            .setChannelCount(2)
            .setPreEq(DynamicsProcessing.Eq(true, 5))
            .setMbc(DynamicsProcessing.Mbc(true, 4))
            .setPostEq(DynamicsProcessing.Eq(true, 5))
            .setLimiter(DynamicsProcessing.Limiter(true))
            .build()
    }
    
    private fun createMovieConfig(): DynamicsProcessing.Config {
        // Movie: preserve dynamic range but enhance bass
        return DynamicsProcessing.Config.Builder()
            .setChannelCount(2)
            .setPreEq(DynamicsProcessing.Eq(true, 5))
            .setLimiter(DynamicsProcessing.Limiter(true))
            .build()
    }
    
    private fun createGamingConfig(): DynamicsProcessing.Config {
        // Gaming: fast attack, preserve transients
        return DynamicsProcessing.Config.Builder()
            .setChannelCount(2)
            .setMbc(DynamicsProcessing.Mbc(true, 3))
            .setLimiter(DynamicsProcessing.Limiter(true))
            .build()
    }
    
    private fun createDefaultConfig(): DynamicsProcessing.Config {
        return DynamicsProcessing.Config.Builder()
            .setChannelCount(2)
            .setPreEq(DynamicsProcessing.Eq(true, 5))
            .build()
    }
    
    /**
     * Update content type and adjust effects accordingly
     */
    fun updateContentType(newContentType: ContentType) {
        if (contentType == newContentType) return
        
        contentType = newContentType
        Log.i(TAG, "Content type changed to: $newContentType")
        
        // Update BassBoost strength
        bassBoost?.let {
            val strength = BASS_BOOST_STRENGTH[newContentType] ?: 300
            it.setStrength(strength.toShort())
        }
        
        // Update LoudnessEnhancer gain
        loudnessEnhancer?.let {
            val gainMb = LOUDNESS_ENHANCEMENT_MB[newContentType] ?: 200
            it.setTargetGain(gainMb)
        }
    }
    
    /**
     * Enable or disable night mode (dynamic range compression)
     */
    fun setNightMode(enabled: Boolean) {
        if (nightMode == enabled) return
        
        nightMode = enabled
        Log.i(TAG, "Night mode: $enabled")
        
        // Reconfigure DynamicsProcessing if available
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            dynamicsProcessing?.let { effect ->
                val config = if (enabled) createNightModeConfig() else createDefaultConfig()
                effect.setConfig(config)
                Log.d(TAG, "Night mode configuration applied")
            }
        }
    }
    
    /**
     * Get the number of active effects in the chain
     */
    fun getActiveEffectCount(): Int {
        var count = 0
        if (equalizer?.enabled == true) count++
        if (bassBoost?.enabled == true) count++
        if (loudnessEnhancer?.enabled == true) count++
        if (dynamicsProcessing?.enabled == true) count++
        return count
    }
    
    /**
     * Get status information about the effect chain
     */
    fun getStatus(): EffectChainStatus {
        return EffectChainStatus(
            equalizerActive = equalizer?.enabled == true,
            bassBoostActive = bassBoost?.enabled == true,
            bassBoostStrength = (bassBoost?.roundedStrength?.toInt() ?: 0) / 10,
            loudnessEnhancerActive = loudnessEnhancer?.enabled == true,
            loudnessGainMb = try { loudnessEnhancer?.targetGain ?: 0 } catch (e: Exception) { 0 },
            dynamicsProcessingActive = dynamicsProcessing?.enabled == true,
            contentType = contentType,
            nightMode = nightMode
        )
    }
    
    /** Current platform Equalizer bands and their applied millibel levels. */
    fun getPlatformBands(): List<PlatformBand> {
        val eq = equalizer ?: return emptyList()
        return List(eq.numberOfBands.toInt()) { index ->
            PlatformBand(
                centerHz = eq.getCenterFreq(index.toShort()) / 1_000.0,
                millibels = eq.getBandLevel(index.toShort()).toInt()
            )
        }
    }

    /**
     * Release all effects and clean up resources
     */
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            loudnessEnhancer?.release()
            dynamicsProcessing?.release()
            
            equalizer = null
            bassBoost = null
            loudnessEnhancer = null
            dynamicsProcessing = null
            
            Log.i(TAG, "Effect chain released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing effect chain", e)
        }
    }
}

/**
 * Status information about the effect chain
 */
data class EffectChainStatus(
    val equalizerActive: Boolean,
    val bassBoostActive: Boolean,
    val bassBoostStrength: Int,
    val loudnessEnhancerActive: Boolean,
    val loudnessGainMb: Int,
    val dynamicsProcessingActive: Boolean,
    val contentType: ContentType,
    val nightMode: Boolean
) {
    fun toDisplayString(): String {
        val effects = mutableListOf<String>()
        if (equalizerActive) effects.add("EQ")
        if (bassBoostActive) effects.add("Bass($bassBoostStrength)")
        if (loudnessEnhancerActive) effects.add("Loudness(${loudnessGainMb}mB)")
        if (dynamicsProcessingActive) effects.add("Dynamics")
        
        return buildString {
            append("Effects: ${effects.joinToString(", ").ifEmpty { "None" }}")
            append(" | Content: ${contentType.name}")
            if (nightMode) append(" | Night Mode")
        }
    }
}
