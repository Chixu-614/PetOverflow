package com.example.deskpet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST = 1002
    }

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        actionButton = findViewById(R.id.action_button)
        permissionButton = findViewById(R.id.permission_button)

        permissionButton.setOnClickListener {
            val items = mutableListOf<String>()
            if (!Settings.canDrawOverlays(this)) {
                items.add("🔘 悬浮窗权限（显示在其他应用上层）")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    items.add("🔔 通知权限")
                }
            }

            if (items.isEmpty()) {
                Toast.makeText(this, "所有权限已授权！", Toast.LENGTH_SHORT).show()
                updateUI()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("授权权限")
                .setItems(items.toTypedArray()) { _, which ->
                    val item = items[which]
                    when {
                        item.contains("悬浮窗") -> {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                        }
                        item.contains("通知") -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissions(
                                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                    NOTIFICATION_PERMISSION_REQUEST
                                )
                            }
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        actionButton.setOnClickListener {
            if (Settings.canDrawOverlays(this) && hasNotificationPermission()) {
                val intent = Intent(this, OverlayService::class.java)
                if (actionButton.text.toString().contains("启动")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    actionButton.text = "⏹ 停止桌宠"
                    Toast.makeText(this, "桌宠已启动！它应该已经浮出来了~", Toast.LENGTH_LONG).show()
                } else {
                    stopService(intent)
                    actionButton.text = "🚀 启动桌宠！"
                    Toast.makeText(this, "桌宠已停止", Toast.LENGTH_SHORT).show()
                }
            }
        }

        updateUI()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun updateUI() {
        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = hasNotificationPermission()

        statusText.text = buildString {
            append("📋 权限状态\n\n")
            append(if (overlayOk) "✅ 悬浮窗权限：已授权" else "❌ 悬浮窗权限：未授权")
            append("\n")
            append(if (notifOk) "✅ 通知权限：已授权" else "❌ 通知权限：未授权")
        }

        actionButton.text = if (overlayOk && notifOk) "🚀 启动桌宠！" else "请先授权所有权限"
        actionButton.isEnabled = overlayOk && notifOk
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            updateUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
