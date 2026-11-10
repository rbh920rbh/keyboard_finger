package com.example.keyboardtouchdemo

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun buildContent(): View {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setTextColor(Color.rgb(21, 101, 192))
        })
        root.addView(TextView(this).apply {
            text = "连接蓝牙键盘后，在任意应用中用快捷键触发触屏动作。"
            textSize = 16f
            setPadding(0, padding / 2, 0, padding / 2)
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, padding / 2)
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(TextView(this).apply {
            text = """
                快捷键：
                Ctrl + Backspace：系统返回
                Ctrl + Alt + ↑ / ↓：短距离上下滑动

                虚拟指针模式：
                小键盘 8、5、4、6：移动指针
                按住小键盘方向键：自动逐级加速
                小键盘 0 / 小键盘 Enter：点击指针位置
                小键盘 -：系统返回
                按下即开始触摸，移动实时生效，松开结束触摸
                点击、长按、滑动由系统根据触摸轨迹判断
                移动轨迹按连续手势分段提交，保持手指状态

                说明：首次使用必须在系统设置中启用本应用的无障碍服务。
            """.trimIndent()
            textSize = 15f
            setPadding(0, padding, 0, 0)
        })
        updateStatus()
        return root
    }

    private fun updateStatus() {
        status.text = if (isAccessibilityServiceEnabled()) {
            "服务状态：已启用，可以开始测试"
        } else {
            "服务状态：未启用"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, TouchAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
