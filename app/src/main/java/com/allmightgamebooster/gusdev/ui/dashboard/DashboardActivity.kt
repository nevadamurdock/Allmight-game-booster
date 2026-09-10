package com.allmightgamebooster.gusdev.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.service.BoostService
import com.allmightgamebooster.gusdev.service.MonitorService
import com.allmightgamebooster.gusdev.ui.applist.AppListActivity
import com.allmightgamebooster.gusdev.ui.history.HistoryActivity
import com.allmightgamebooster.gusdev.ui.settings.GlobalSettingsActivity
import com.allmightgamebooster.gusdev.ui.widget.RadialGaugeView
import com.allmightgamebooster.gusdev.util.ShellExecutor

class DashboardActivity : AppCompatActivity() {

    private lateinit var emptyState: LinearLayout
    private lateinit var activeState: LinearLayout
    private lateinit var tvActiveApp: TextView
    private lateinit var tvActivePreset: TextView
    private lateinit var gaugeTemperature: RadialGaugeView
    private lateinit var gaugeClock: RadialGaugeView
    private lateinit var tvRamReadout: TextView
    private lateinit var tvIoReadout: TextView
    private lateinit var tvFpsReadout: TextView
    private lateinit var btnPickApp: Button
    private lateinit var btnStopBoost: Button

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.dashboard_title)

        emptyState = findViewById(R.id.emptyState)
        activeState = findViewById(R.id.activeState)
        tvActiveApp = findViewById(R.id.tvActiveApp)
        tvActivePreset = findViewById(R.id.tvActivePreset)
        gaugeTemperature = findViewById(R.id.gaugeTemperature)
        gaugeClock = findViewById(R.id.gaugeClock)
        tvRamReadout = findViewById(R.id.tvRamReadout)
        tvIoReadout = findViewById(R.id.tvIoReadout)
        tvFpsReadout = findViewById(R.id.tvFpsReadout)
        btnPickApp = findViewById(R.id.btnPickApp)
        btnStopBoost = findViewById(R.id.btnStopBoost)

        btnPickApp.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        btnStopBoost.setOnClickListener {
            BoostService.stop(this)
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        stopRefreshing()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Riwayat")
        menu.add(0, 2, 0, "Pengaturan")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { startActivity(Intent(this, HistoryActivity::class.java)); true }
            2 -> { startActivity(Intent(this, GlobalSettingsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateUI() {
        val activeApp = BoostConfigStore.getActiveApp(this)
        if (activeApp != null) {
            emptyState.visibility = View.GONE
            activeState.visibility = View.VISIBLE

            val config = BoostConfigStore.getConfig(this, activeApp)
            tvActiveApp.text = activeApp.substringAfterLast('.')
            tvActivePreset.text = config.preset.label

            startRefreshing()
        } else {
            emptyState.visibility = View.VISIBLE
            activeState.visibility = View.GONE
            stopRefreshing()
        }
    }

    private fun startRefreshing() {
        stopRefreshing()
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshData()
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun stopRefreshing() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private fun refreshData() {
        Thread {
            val temp = ShellExecutor.getThermalTemp()
            val freqs = ShellExecutor.getCpuFreq()
            val maxFreqMhz = freqs.maxByOrNull { it.second }?.second?.div(1000) ?: 0
            val (totalRam, availRam) = ShellExecutor.getRamUsage()
            val usedRamPct = if (totalRam > 0) ((totalRam - availRam) * 100 / totalRam) else 0

            runOnUiThread {
                gaugeTemperature.setValue(temp, 60f, "SUHU", "°C")
                gaugeClock.setValue(
                    maxFreqMhz.toFloat() / 1000f,
                    3.5f,
                    "CLOCK",
                    "GHz"
                )
                tvRamReadout.text = "RAM ${usedRamPct}%"
                tvIoReadout.text = "IO"
                tvFpsReadout.text = "FPS --"
            }
        }.start()
    }
}
