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
                快捷键（均需 Ctrl）：

                1 / 2 / 3 / 4：点击左上、右上、左下、右下
                Backspace：系统返回
                ↑ / ↓：滑动方向已反转，移动距离已缩短
                Ctrl + Alt + 方向键：兼容旧版滑动快捷键
                
                虚拟指针模式：
                方向键 / 小键盘 8、2、4、6：移动指针
                Ctrl + W / A / S / D：移动指针
                按住方向键：键盘自动重复并逐级加速
                Ctrl + Enter / Space / 小键盘 5：点击指针位置
                按住上述组合键约 0.5 秒：长按
                按住上述组合键 + 移动：拖动并滑动，松开后执行

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
