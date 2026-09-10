package com.allmightgamebooster.gusdev.ui.applist

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.AppBoostConfig
import com.allmightgamebooster.gusdev.ui.perapp.PerAppSettingsActivity
import com.allmightgamebooster.gusdev.util.ShellExecutor

class AppListActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var switchShowSystem: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var adapter: AppListAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var showSystem = false

    data class AppInfo(val packageName: String, val label: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.search_app)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvApps = findViewById(R.id.rvApps)
        etSearch = findViewById(R.id.etSearch)
        switchShowSystem = findViewById(R.id.switchShowSystem)

        adapter = AppListAdapter { appInfo ->
            val intent = Intent(this, PerAppSettingsActivity::class.java)
            intent.putExtra("package", appInfo.packageName)
            intent.putExtra("label", appInfo.label)
            startActivity(intent)
        }

        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        switchShowSystem.setOnCheckedChangeListener { _, isChecked ->
            showSystem = isChecked
            filterApps(etSearch.text.toString())
        }

        loadApps()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(intent, 0)

            val systemPackages = setOf(
                "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
                "com.google.android.inputmethod.latin",
                "com.android.systemui",
                "com.android.settings",
                "com.android.phone",
                "com.android.contacts",
                "com.android.mms",
                "com.android.calendar",
                "com.android.camera",
                "com.android.gallery3d",
                "com.android.deskclock",
                "com.android.calculator2",
                "com.android.filemanager",
                "com.android.documentsui",
                "com.android.vending",
                "com.google.android.gms"
            )

            allApps = resolveInfos
                .filter { info ->
                    val pkg = info.activityInfo.packageName
                    if (!showSystem) {
                        !pkg.startsWith("com.android.") &&
                                !pkg.startsWith("com.google.") &&
                                !systemPackages.contains(pkg)
                    } else true
                }
                .map { info ->
                    AppInfo(
                        info.activityInfo.packageName,
                        info.loadLabel(pm).toString()
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }

            runOnUiThread {
                adapter.submitList(allApps)
            }
        }.start()
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
