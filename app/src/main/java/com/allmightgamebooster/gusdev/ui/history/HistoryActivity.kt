package com.allmightgamebooster.gusdev.ui.history

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.SessionRecord
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var rvSessions: RecyclerView
    private lateinit var tvTabToday: TextView
    private lateinit var tvTabWeek: TextView
    private lateinit var tvTabMonth: TextView

    private var allSessions: List<SessionRecord> = emptyList()
    private var currentFilter = FILTER_TODAY

    companion object {
        const val FILTER_TODAY = 0
        const val FILTER_WEEK = 1
        const val FILTER_MONTH = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Riwayat & Statistik"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        chart = findViewById(R.id.chartSessions)
        rvSessions = findViewById(R.id.rvSessions)
        tvTabToday = findViewById(R.id.tvTabToday)
        tvTabWeek = findViewById(R.id.tvTabWeek)
        tvTabMonth = findViewById(R.id.tvTabMonth)

        tvTabToday.setOnClickListener { selectTab(FILTER_TODAY) }
        tvTabWeek.setOnClickListener { selectTab(FILTER_WEEK) }
        tvTabMonth.setOnClickListener { selectTab(FILTER_MONTH) }

        setupChart()
        selectTab(FILTER_TODAY)
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun selectTab(filter: Int) {
        currentFilter = filter
        val tabs = listOf(tvTabToday, tvTabWeek, tvTabMonth)
        tabs.forEachIndexed { i, tab ->
            tab.setTextColor(
                resources.getColor(
                    if (i == filter) R.color.copper_signal else R.color.steel_40, null
                )
            )
        }
        loadSessions()
    }

    private fun loadSessions() {
        allSessions = BoostConfigStore.getSessions(this)
        val now = System.currentTimeMillis()
        val filtered = when (currentFilter) {
            FILTER_TODAY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                allSessions.filter { it.startTime >= cal.timeInMillis }
            }
            FILTER_WEEK -> {
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                allSessions.filter { it.startTime >= weekAgo }
            }
            FILTER_MONTH -> {
                val monthAgo = now - 30L * 24 * 60 * 60 * 1000L
                allSessions.filter { it.startTime >= monthAgo }
            }
            else -> allSessions
        }

        updateChart(filtered)
        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = SessionAdapter(filtered)
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setDrawBorders(false)
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#8C949C")
                textSize = 10f
            }

            axisLeft.apply {
                textColor = Color.parseColor("#8C949C")
                textSize = 10f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#353839")
            }
        }
    }

    private fun updateChart(sessions: List<SessionRecord>) {
        if (sessions.isEmpty()) {
            chart.clear()
            return
        }

        val tempEntries = mutableListOf<Entry>()
        val fpsEntries = mutableListOf<Entry>()

        sessions.reversed().forEachIndexed { index, session ->
            tempEntries.add(Entry(index.toFloat(), session.peakTemperature))
            fpsEntries.add(Entry(index.toFloat(), session.avgFps))
        }

        val tempDataSet = LineDataSet(tempEntries, "Suhu °C").apply {
            color = Color.parseColor("#C97A4A")
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val fpsDataSet = LineDataSet(fpsEntries, "FPS").apply {
            color = Color.parseColor("#EDEBE6")
            setDrawCircles(false)
            lineWidth = 1.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        }

        chart.data = LineData(tempDataSet, fpsDataSet)
        chart.invalidate()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class SessionAdapter(
        private val sessions: List<SessionRecord>
    ) : RecyclerView.Adapter<SessionAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvApp: TextView = view.findViewById(R.id.tvSessionApp)
            val tvDuration: TextView = view.findViewById(R.id.tvSessionDuration)
            val tvPeakTemp: TextView = view.findViewById(R.id.tvSessionPeakTemp)
            val tvAvgFps: TextView = view.findViewById(R.id.tvSessionAvgFps)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
            return VH(view)
        }

        override fun getItemCount() = sessions.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val session = sessions[position]
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

            holder.tvApp.text = session.appName
            holder.tvDuration.text = "${sdf.format(Date(session.startTime))} - ${session.durationMinutes}m"
            holder.tvPeakTemp.text = String.format("%.0f°C puncak", session.peakTemperature)
            holder.tvAvgFps.text = String.format("%.0f fps avg", session.avgFps)
        }
    }
}
