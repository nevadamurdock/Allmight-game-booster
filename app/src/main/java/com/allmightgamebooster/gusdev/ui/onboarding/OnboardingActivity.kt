package com.allmightgamebooster.gusdev.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.ui.dashboard.DashboardActivity
import com.allmightgamebooster.gusdev.util.ShellExecutor

class OnboardingActivity : AppCompatActivity() {

    private lateinit var rootStatus: TextView
    private lateinit var btnNotificationPolicy: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnContinue: Button

    private var rootDetected = false
    private var notifPolicyGranted = false
    private var overlayGranted = false

    private val notifPolicyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateNotifPolicyStatus()
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = Settings.canDrawOverlays(this)
        btnOverlayPermission.text = if (overlayGranted) getString(R.string.btn_checked) else getString(R.string.btn_grant)
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) updateNotifPolicyStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BoostConfigStore.isOnboardingDone(this)) {
            navigateToDashboard()
            return
        }

        setContentView(R.layout.activity_onboarding)

        rootStatus = findViewById(R.id.rootStatus)
        btnNotificationPolicy = findViewById(R.id.btnNotificationPolicy)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnContinue = findViewById(R.id.btnContinue)

        checkRoot()

        btnNotificationPolicy.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!getNotificationManager().isNotificationPolicyAccessGranted) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    notifPolicyLauncher.launch(intent)
                } else {
                    notifPolicyGranted = true
                    btnNotificationPolicy.text = getString(R.string.btn_checked)
                }
            }
        }

        btnOverlayPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayLauncher.launch(intent)
            } else {
                overlayGranted = true
                btnOverlayPermission.text = getString(R.string.btn_checked)
            }
        }

        btnContinue.setOnClickListener {
            if (!rootDetected) {
                Toast.makeText(this, "Akses root diperlukan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            BoostConfigStore.setOnboardingDone(this, true)
            navigateToDashboard()
        }

        updateNotifPolicyStatus()
        overlayGranted = Settings.canDrawOverlays(this)
        btnOverlayPermission.text = if (overlayGranted) getString(R.string.btn_checked) else getString(R.string.btn_grant)
    }

    private fun checkRoot() {
        Thread {
            val hasRoot = ShellExecutor.isRootAvailable()
            rootDetected = hasRoot
            runOnUiThread {
                rootStatus.text = if (hasRoot) getString(R.string.root_detected) else getString(R.string.root_not_detected)
                rootStatus.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (hasRoot) R.color.copper_signal else R.color.redline_alert
                    )
                )
            }
        }.start()
    }

    private fun updateNotifPolicyStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notifPolicyGranted = getNotificationManager().isNotificationPolicyAccessGranted
            btnNotificationPolicy.text = if (notifPolicyGranted) getString(R.string.btn_checked) else getString(R.string.btn_grant)
        }
    }

    private fun getNotificationManager(): android.app.NotificationManager {
        return getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
