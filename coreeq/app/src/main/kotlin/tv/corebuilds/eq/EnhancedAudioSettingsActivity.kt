package tv.corebuilds.eq

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * Settings screen for enhanced audio features including effect chain configuration,
 * night mode, content type preferences, and device-specific settings.
 */
class EnhancedAudioSettingsActivity : TvActivity() {
    
    private lateinit var prefs: EnhancedAudioPrefs
    
    // Effect chain controls
    private lateinit var chkBassBoost: CheckBox
    private lateinit var chkLoudness: CheckBox
    private lateinit var chkDynamics: CheckBox
    
    // Night mode controls
    private lateinit var chkNightMode: CheckBox
    private lateinit var chkNightModeAuto: CheckBox
    private lateinit var seekNightStart: SeekBar
    private lateinit var seekNightEnd: SeekBar
    private lateinit var textNightStart: TextView
    private lateinit var textNightEnd: TextView
    
    // Content type controls
    private lateinit var chkAutoContentType: CheckBox
    
    // Device controls
    private lateinit var chkShowHdmiWarnings: CheckBox
    
    // Advanced controls
    private lateinit var chkShowAdvancedStats: CheckBox
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_audio_settings)
        
        prefs = EnhancedAudioPrefs(this)
        
        initViews()
        loadPreferences()
        setupListeners()
    }
    
    private fun initViews() {
        // Effect chain
        chkBassBoost = findViewById(R.id.chk_bass_boost)
        chkLoudness = findViewById(R.id.chk_loudness_enhancer)
        chkDynamics = findViewById(R.id.chk_dynamics_processing)
        
        // Night mode
        chkNightMode = findViewById(R.id.chk_night_mode)
        chkNightModeAuto = findViewById(R.id.chk_night_mode_auto)
        seekNightStart = findViewById(R.id.seek_night_start)
        seekNightEnd = findViewById(R.id.seek_night_end)
        textNightStart = findViewById(R.id.text_night_start)
        textNightEnd = findViewById(R.id.text_night_end)
        
        // Content type
        chkAutoContentType = findViewById(R.id.chk_auto_content_type)
        
        // Device
        chkShowHdmiWarnings = findViewById(R.id.chk_show_hdmi_warnings)
        
        // Advanced
        chkShowAdvancedStats = findViewById(R.id.chk_show_advanced_stats)
        
        // Buttons
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveAndExit() }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { resetToDefaults() }
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
    }
    
    private fun loadPreferences() {
        // Effect chain
        chkBassBoost.isChecked = prefs.bassBoostEnabled
        chkLoudness.isChecked = prefs.loudnessEnhancerEnabled
        chkDynamics.isChecked = prefs.dynamicsProcessingEnabled
        
        // Night mode
        chkNightMode.isChecked = prefs.nightModeEnabled
        chkNightModeAuto.isChecked = prefs.nightModeAutoEnabled
        seekNightStart.progress = prefs.nightModeStartHour
        seekNightEnd.progress = prefs.nightModeEndHour
        updateNightModeTimeLabels()
        
        // Content type
        chkAutoContentType.isChecked = prefs.autoContentTypeEnabled
        
        // Device
        chkShowHdmiWarnings.isChecked = prefs.showHdmiWarnings
        
        // Advanced
        chkShowAdvancedStats.isChecked = prefs.showAdvancedStats
    }
    
    private fun setupListeners() {
        // Night mode auto toggle
        chkNightMode.setOnCheckedChangeListener { _, isChecked ->
            chkNightModeAuto.isEnabled = isChecked
            seekNightStart.isEnabled = isChecked && chkNightModeAuto.isChecked
            seekNightEnd.isEnabled = isChecked && chkNightModeAuto.isChecked
        }
        
        chkNightModeAuto.setOnCheckedChangeListener { _, isChecked ->
            seekNightStart.isEnabled = isChecked
            seekNightEnd.isEnabled = isChecked
        }
        
        // Night mode time seekers
        seekNightStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateNightModeTimeLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        seekNightEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateNightModeTimeLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateNightModeTimeLabels() {
        textNightStart.text = formatHour(seekNightStart.progress)
        textNightEnd.text = formatHour(seekNightEnd.progress)
    }
    
    private fun formatHour(hour: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:00 %s", displayHour, period)
    }
    
    private fun saveAndExit() {
        // Save effect chain settings
        prefs.bassBoostEnabled = chkBassBoost.isChecked
        prefs.loudnessEnhancerEnabled = chkLoudness.isChecked
        prefs.dynamicsProcessingEnabled = chkDynamics.isChecked
        
        // Save night mode settings
        prefs.nightModeEnabled = chkNightMode.isChecked
        prefs.nightModeAutoEnabled = chkNightModeAuto.isChecked
        prefs.nightModeStartHour = seekNightStart.progress
        prefs.nightModeEndHour = seekNightEnd.progress
        
        // Save content type settings
        prefs.autoContentTypeEnabled = chkAutoContentType.isChecked
        
        // Save device settings
        prefs.showHdmiWarnings = chkShowHdmiWarnings.isChecked
        
        // Save advanced settings
        prefs.showAdvancedStats = chkShowAdvancedStats.isChecked
        
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        
        // Notify EqService to reload settings
        if (tv.corebuilds.eq.apply.EqService.running) {
            tv.corebuilds.eq.apply.EqService.send(this, tv.corebuilds.eq.apply.EqService.ACTION_REAPPLY)
        }
        
        finish()
    }
    
    private fun resetToDefaults() {
        prefs.resetToDefaults()
        loadPreferences()
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }
}
