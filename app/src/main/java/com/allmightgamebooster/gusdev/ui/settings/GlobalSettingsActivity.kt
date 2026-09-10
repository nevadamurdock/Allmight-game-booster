package com.allmightgamebooster.gusdev.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.Preset
import com.allmightgamebooster.gusdev.ui.onboarding.OnboardingActivity

class GlobalSettingsActivity : AppCompatActivity() {

    private lateinit var seekBatteryThreshold: SeekBar
    private lateinit var tvBatteryThreshold: TextView
    private lateinit var rgDefaultPreset: RadioGroup
    private lateinit var tvVersion: TextView
    private lateinit var tvModuleStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_settings)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Pengaturan"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        seekBatteryThreshold = findViewById(R.id.seekBatteryThreshold)
        tvBatteryThreshold = findViewById(R.id.tvBatteryThreshold)
        rgDefaultPreset = findViewById(R.id.rgDefaultPreset)
        tvVersion = findViewById(R.id.tvVersion)
        tvModuleStatus = findViewById(R.id.tvModuleStatus)

        val threshold = BoostConfigStore.getBatteryThreshold(this)
        seekBatteryThreshold.progress = threshold - 10
        tvBatteryThreshold.text = "${threshold}%"

        seekBatteryThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBatteryThreshold.text = "${progress + 10}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                BoostConfigStore.setBatteryThreshold(this@GlobalSettingsActivity, (seekBar?.progress ?: 10) + 10)
            }
        })

        val defaultPreset = BoostConfigStore.getDefaultPreset(this)
        when (defaultPreset) {
            Preset.PERFORMANCE -> rgDefaultPreset.check(R.id.rbPerformance)
            Preset.BALANCED -> rgDefaultPreset.check(R.id.rbBalanced)
            Preset.BATTERY_SAVER -> rgDefaultPreset.check(R.id.rbBatterySaver)
        }

        rgDefaultPreset.setOnCheckedChangeListener { _, checkedId ->
            val preset = when (checkedId) {
                R.id.rbPerformance -> Preset.PERFORMANCE
                R.id.rbBalanced -> Preset.BALANCED
                R.id.rbBatterySaver -> Preset.BATTERY_SAVER
                else -> Preset.BALANCED
            }
            BoostConfigStore.setDefaultPreset(this, preset)
        }

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "v${pInfo.versionName}"
        } catch (_: Exception) {
            tvVersion.text = "v1.0.0"
        }

        tvModuleStatus.text = if (isModuleActive()) "Modul aktif" else "Modul belum aktif — aktifkan di LSPosed + reboot"

        findViewById<android.view.View>(R.id.btnManagePermissions)?.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun isModuleActive(): Boolean {
        return try {
            val aFile = java.io.File(codeSource?.file?.replace("!/classes.dex", "")
                ?.replace("file:", "") ?: return false, "META-INF/xposed/module.prop")
            aFile.exists()
        } catch (_: Throwable) {
            false
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
