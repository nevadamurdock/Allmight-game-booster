package com.allmightgamebooster.gusdev.ui.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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

class HistoryActivity : AppCompatActivity() {

    private lateinit var chartView: SessionChartView
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

        chartView = findViewById(R.id.chartSessions)
        rvSessions = findViewById(R.id.rvSessions)
        tvTabToday = findViewById(R.id.tvTabToday)
        tvTabWeek = findViewById(R.id.tvTabWeek)
        tvTabMonth = findViewById(R.id.tvTabMonth)

        tvTabToday.setOnClickListener { selectTab(FILTER_TODAY) }
        tvTabWeek.setOnClickListener { selectTab(FILTER_WEEK) }
        tvTabMonth.setOnClickListener { selectTab(FILTER_MONTH) }

        selectTab(FILTER_TODAY)
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun selectTab(filter: Int) {
        currentFilter = filter
        listOf(tvTabToday to FILTER_TODAY, tvTabWeek to FILTER_WEEK, tvTabMonth to FILTER_MONTH).forEach { (tab, f) ->
            tab.setTextColor(resources.getColor(if (f == filter) R.color.copper_signal else R.color.steel_40, null))
            tab.setBackgroundColor(if (f == filter) 0xFF3C2E24.toInt() else Color.TRANSPARENT)
        }
        loadSessions()
    }

    private fun loadSessions() {
        allSessions = BoostConfigStore.getSessions(this)
        val now = System.currentTimeMillis()
        val filtered = when (currentFilter) {
            FILTER_TODAY -> {
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                allSessions.filter { it.startTime >= cal.timeInMillis }
            }
            FILTER_WEEK -> allSessions.filter { it.startTime >= now - 7 * 86_400_000L }
            FILTER_MONTH -> allSessions.filter { it.startTime >= now - 30L * 86_400_000L }
            else -> allSessions
        }

        chartView.setData(filtered)
        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = SessionAdapter(filtered)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ── Custom Canvas Chart (PRD §2) ────────────────────────────────

class SessionChartView(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {

    private var data: List<SessionRecord> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C97A4A")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#35383B")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C949C")
        textSize = 24f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val path = Path()

    fun setData(sessions: List<SessionRecord>) {
        data = sessions
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val padLeft = 80f
        val padRight = 20f
        val padTop = 40f
        val padBottom = 60f
        val w = width - padLeft - padRight
        val h = height - padTop - padBottom

        val temps = data.map { it.peakTemperature }
        val minT = (temps.minOrNull() ?: 0f) - 5f
        val maxT = (temps.maxOrNull() ?: 50f) + 5f

        canvas.drawLine(padLeft, padTop + h, padLeft + w, padTop + h, gridPaint)
        canvas.drawLine(padLeft, padTop, padLeft, padTop + h, gridPaint)

        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format("%.0f\u00B0", maxT), 0f, padTop + 8f, labelPaint)
        canvas.drawText(String.format("%.0f\u00B0", minT), 0f, padTop + h, labelPaint)

        path.reset()
        data.forEachIndexed { i, session ->
            val x = padLeft + (i.toFloat() / (data.size - 1).coerceAtLeast(1)) * w
            val y = padTop + h - ((session.peakTemperature - minT) / (maxT - minT)) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}

// ── Session List Adapter ─────────────────────────────────────────

class SessionAdapter(private val sessions: List<SessionRecord>) : RecyclerView.Adapter<SessionAdapter.VH>() {

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
        val s = sessions[position]
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        holder.tvApp.text = s.appName
        holder.tvDuration.text = "${sdf.format(java.util.Date(s.startTime))} · ${s.durationMinutes}m"
        holder.tvPeakTemp.text = String.format("%.0f\u00B0C puncak", s.peakTemperature)
        holder.tvAvgFps.text = String.format("%.0f fps avg", s.avgFps)
    }
}
