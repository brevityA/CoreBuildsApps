package tv.corebuilds.eq.apply

import android.content.Context
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.mode.ContentTypeRegistry
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * Integration helper for EnhancedEffectChain.
 * 
 * This class provides a bridge between EqService and EnhancedEffectChain,
 * handling preference loading, content type detection, and effect chain management.
 * 
 * Usage in EqService:
 * ```
 * private var effectChainManager: EffectChainManager? = null
 * 
 * override fun onCreate() {
 *     super.onCreate()
 *     effectChainManager = EffectChainManager(this)
 * }
 * 
 * private fun createEnhancedEffect(session: Int, profile: Profile): AppliedEffect? {
 *     return effectChainManager?.createEffectChain(session, profile, activeAppPackages())
 * }
 * ```
 */
class EffectChainManager(private val context: Context) {
    
    private val prefs = EnhancedAudioPrefs(context)
    private var currentEffectChain: EnhancedEffectChain? = null
    
    /**
     * Create and initialize an EnhancedEffectChain based on user preferences.
     * 
     * @param session Audio session ID (0 for output mix)
     * @param profile Room correction profile with filters
     * @param activePackages Set of active app package names for content type detection
     * @return Initialized EnhancedEffectChain or null if initialization failed
     */
    fun createEffectChain(
        session: Int,
        profile: Profile,
        activePackages: Set<String> = emptySet()
    ): EnhancedEffectChain? {
        // Release existing chain
        currentEffectChain?.release()
        
        // Create new chain
        val chain = EnhancedEffectChain(session)
        
        // Detect content type if auto-detection enabled
        val contentType = if (prefs.autoContentTypeEnabled) {
            detectContentType(activePackages)
        } else {
            prefs.lastContentType
        }
        
        // Determine night mode state
        val nightModeActive = prefs.shouldEnableNightMode()
        
        // Initialize with user preferences
        val success = chain.initialize(
            filters = profile.filters,
            preampGainDb = profile.preampGainDb,
            contentType = contentType,
            enableBassBoost = prefs.bassBoostEnabled,
            enableLoudnessEnhancer = prefs.loudnessEnhancerEnabled,
            enableDynamicsProcessing = prefs.dynamicsProcessingEnabled,
            enableNightMode = nightModeActive
        )
        
        return if (success) {
            currentEffectChain = chain
            // Save the content type we're using
            prefs.lastContentType = contentType
            chain
        } else {
            chain.release()
            null
        }
    }
    
    /**
     * Detect content type from active packages.
     * Returns the first specific content type found, or GENERAL if none match.
     */
    private fun detectContentType(activePackages: Set<String>): ContentType {
        for (pkg in activePackages) {
            val detected = ContentTypeRegistry.detect(pkg)
            if (detected != ContentType.GENERAL) {
                return detected
            }
        }
        return ContentType.GENERAL
    }
    
    /**
     * Update content type based on current active packages.
     * Call this when the active app changes.
     */
    fun updateContentType(activePackages: Set<String>) {
        val chain = currentEffectChain ?: return
        if (!prefs.autoContentTypeEnabled) return
        
        val newType = detectContentType(activePackages)
        val status = chain.getStatus()
        
        if (status.contentType != newType) {
            chain.updateContentType(newType)
            prefs.lastContentType = newType
        }
    }
    
    /**
     * Update night mode based on current time and settings.
     * Call this periodically or when settings change.
     */
    fun updateNightMode() {
        val chain = currentEffectChain ?: return
        val shouldEnable = prefs.shouldEnableNightMode()
        val status = chain.getStatus()
        
        if (status.nightModeActive != shouldEnable) {
            chain.setNightMode(shouldEnable)
        }
    }
    
    /**
     * Reload all preferences and reconfigure the effect chain.
     * Call this when settings are saved.
     */
    fun reloadPreferences(profile: Profile, activePackages: Set<String> = emptySet()) {
        val session = currentEffectChain?.audioSessionId ?: return
        currentEffectChain = createEffectChain(session, profile, activePackages)
    }
    
    /**
     * Get the current effect chain status.
     * Returns null if no effect chain is active.
     */
    fun getStatus(): EffectChainStatus? {
        return currentEffectChain?.getStatus()
    }
    
    /**
     * Get the current effect chain instance.
     */
    fun getEffectChain(): EnhancedEffectChain? {
        return currentEffectChain
    }
    
    /**
     * Release the current effect chain and clean up resources.
     */
    fun release() {
        currentEffectChain?.release()
        currentEffectChain = null
    }
}
