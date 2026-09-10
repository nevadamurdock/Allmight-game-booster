package com.allmightgamebooster.gusdev.ui.perapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.AppBoostConfig
import com.allmightgamebooster.gusdev.model.Preset
import com.allmightgamebooster.gusdev.util.RootHideConfig
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.AppBoostConfig
import com.allmightgamebooster.gusdev.model.Preset
import com.allmightgamebooster.gusdev.util.RootHideConfig
import com.google.android.material.switchmaterial.SwitchMaterial

class PerAppSettingsActivity : AppCompatActivity() {

    private lateinit var rvFeatures: RecyclerView
    private lateinit var btnPerformance: Button
    private lateinit var btnBalanced: Button
    private lateinit var btnBatterySaver: Button
    private lateinit var config: AppBoostConfig

    private data class FeatureItem(
        val category: String,
        val name: String,
        val description: String,
        val getter: () -> Boolean,
        val setter: (Boolean) -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_per_app_settings)

        val pkg = intent.getStringExtra("package") ?: finish().let { return }
        val label = intent.getStringExtra("label") ?: pkg

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = label
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvFeatures = findViewById(R.id.rvFeatures)
        btnPerformance = findViewById(R.id.btnPerformance)
        btnBalanced = findViewById(R.id.btnBalanced)
        btnBatterySaver = findViewById(R.id.btnBatterySaver)

        config = BoostConfigStore.getConfig(this, pkg)

        btnPerformance.setOnClickListener { applyPreset(Preset.PERFORMANCE) }
        btnBalanced.setOnClickListener { applyPreset(Preset.BALANCED) }
        btnBatterySaver.setOnClickListener { applyPreset(Preset.BATTERY_SAVER) }

        updatePresetButtons()
        setupFeatures()
    }

    private fun applyPreset(preset: Preset) {
        config.applyPreset(preset)
        BoostConfigStore.saveConfig(this, config)
        updatePresetButtons()
        rvFeatures.adapter?.notifyDataSetChanged()
    }

    private fun updatePresetButtons() {
        btnPerformance.isSelected = config.preset == Preset.PERFORMANCE
        btnBalanced.isSelected = config.preset == Preset.BALANCED
        btnBatterySaver.isSelected = config.preset == Preset.BATTERY_SAVER

        val activeColor = resources.getColor(R.color.copper_signal, null)
        val inactiveColor = resources.getColor(R.color.graphite_50, null)

        btnPerformance.setBackgroundColor(if (config.preset == Preset.PERFORMANCE) activeColor else inactiveColor)
        btnBalanced.setBackgroundColor(if (config.preset == Preset.BALANCED) activeColor else inactiveColor)
        btnBatterySaver.setBackgroundColor(if (config.preset == Preset.BATTERY_SAVER) activeColor else inactiveColor)
    }

    private fun setupFeatures() {
        val features = listOf(
            FeatureItem(getString(R.string.category_cpu_gpu), getString(R.string.feature_governor), getString(R.string.feature_governor_desc), { config.governorLock }, { config.governorLock = it }),
            FeatureItem(getString(R.string.category_cpu_gpu), getString(R.string.feature_thermal), getString(R.string.feature_thermal_desc), { config.adaptiveThermal }, { config.adaptiveThermal = it }),
            FeatureItem(getString(R.string.category_cpu_gpu), getString(R.string.feature_fps_unlock), getString(R.string.feature_fps_unlock_desc), { config.fpsUnlock }, { config.fpsUnlock = it }),
            FeatureItem(getString(R.string.category_cpu_gpu), getString(R.string.feature_cpu_affinity), getString(R.string.feature_cpu_affinity_desc), { config.cpuAffinity }, { config.cpuAffinity = it }),
            FeatureItem(getString(R.string.category_cpu_gpu), getString(R.string.feature_io_priority), getString(R.string.feature_io_priority_desc), { config.ioPriority }, { config.ioPriority = it }),

            FeatureItem(getString(R.string.category_process), getString(R.string.feature_freeze), getString(R.string.feature_freeze_desc), { config.freezeBackground }, { config.freezeBackground = it }),
            FeatureItem(getString(R.string.category_process), getString(R.string.feature_pre_launch_clean), getString(R.string.feature_pre_launch_clean_desc), { config.preLaunchClean }, { config.preLaunchClean = it }),
            FeatureItem(getString(R.string.category_process), getString(R.string.feature_process_priority), getString(R.string.feature_process_priority_desc), { config.processPriority }, { config.processPriority = it }),
            FeatureItem(getString(R.string.category_process), getString(R.string.feature_prevent_kill), getString(R.string.feature_prevent_kill_desc), { config.preventKill }, { config.preventKill = it }),

            FeatureItem(getString(R.string.category_screen), getString(R.string.feature_refresh_rate), getString(R.string.feature_refresh_rate_desc), { config.refreshRateLock }, { config.refreshRateLock = it }),
            FeatureItem(getString(R.string.category_screen), getString(R.string.feature_brightness), getString(R.string.feature_brightness_desc), { config.brightnessLock }, { config.brightnessLock = it }),
            FeatureItem(getString(R.string.category_screen), getString(R.string.feature_input_latency), getString(R.string.feature_input_latency_desc), { config.inputLatency }, { config.inputLatency = it }),
            FeatureItem(getString(R.string.category_screen), getString(R.string.feature_render_latency), getString(R.string.feature_render_latency_desc), { config.renderLatency }, { config.renderLatency = it }),
            FeatureItem(getString(R.string.category_screen), getString(R.string.feature_screen_pin), getString(R.string.feature_screen_pin_desc), { config.screenPin }, { config.screenPin = it }),

            FeatureItem(getString(R.string.category_network), getString(R.string.feature_network_qos), getString(R.string.feature_network_qos_desc), { config.networkQos }, { config.networkQos = it }),

            FeatureItem(getString(R.string.category_network), getString(R.string.feature_dnd), getString(R.string.feature_dnd_desc), { config.autoDnd }, { config.autoDnd = it }),

            FeatureItem(getString(R.string.category_audio), getString(R.string.feature_low_latency_audio), getString(R.string.feature_low_latency_audio_desc), { config.lowLatencyAudio }, { config.lowLatencyAudio = it }),

            FeatureItem(getString(R.string.category_security), getString(R.string.feature_root_hide), getString(R.string.feature_root_hide_desc), { config.rootHide }, { enabled ->
                config.rootHide = enabled
                if (enabled) RootHideConfig.addPackage(config.packageName)
                else RootHideConfig.removePackage(config.packageName)
            })
        )

        val grouped = features.groupBy { it.category }

        rvFeatures.layoutManager = LinearLayoutManager(this)
        rvFeatures.adapter = FeatureCategoryAdapter(grouped) {
            BoostConfigStore.saveConfig(this, config)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class FeatureCategoryAdapter(
        private val grouped: Map<String, List<FeatureItem>>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<FeatureCategoryAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvCategory: TextView = view.findViewById(R.id.tvCategory)
            val featureItems: LinearLayout = view.findViewById(R.id.featureItems)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_feature_category, parent, false)
            return VH(view)
        }

        override fun getItemCount() = grouped.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (category, features) = grouped.entries.toList()[position]
            holder.tvCategory.text = category
            holder.featureItems.removeAllViews()

            features.forEach { feature ->
                val itemView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_feature_switch, holder.featureItems, false)

                itemView.findViewById<TextView>(R.id.tvFeatureName).text = feature.name
                itemView.findViewById<TextView>(R.id.tvFeatureDesc).text = feature.description

                val switch = itemView.findViewById<SwitchMaterial>(R.id.switchFeature)
                switch.isChecked = feature.getter()
                switch.setOnCheckedChangeListener { _, isChecked ->
                    feature.setter(isChecked)
                    onChanged()
                }

                holder.featureItems.addView(itemView)
            }
        }
    }
}
