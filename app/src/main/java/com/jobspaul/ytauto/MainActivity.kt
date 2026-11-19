package com.jobspaul.ytauto

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** หน้าจอบนมือถือ: ขอสิทธิ์ตำแหน่งและแสดงวิธีเปิดใช้บน Android Auto */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        status = TextView(this).apply { textSize = 16f }
        val grant = Button(this).apply {
            text = "อนุญาตตำแหน่ง (ใช้ตรวจว่ารถจอดอยู่)"
            setOnClickListener {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                    REQ_LOCATION,
                )
            }
        }
        val guide = TextView(this).apply {
            textSize = 15f
            text = """
                วิธีเปิดใช้บน Android Auto

                1. เปิดแอป Android Auto บนมือถือ (ตั้งค่า > อุปกรณ์ที่เชื่อมต่อ > Android Auto)
                2. เลื่อนลงไปล่างสุด แล้วแตะ "เวอร์ชัน" 10 ครั้งเพื่อเปิดโหมดนักพัฒนา
                3. แตะเมนู ⋮ มุมขวาบน > การตั้งค่าสำหรับนักพัฒนาซอฟต์แวร์
                4. เปิด "แหล่งที่มาที่ไม่รู้จัก" (Unknown sources)
                5. เชื่อมต่อกับรถ แอป "YT Auto" จะขึ้นในหน้ารวมแอป
                   ถ้าไม่ขึ้น ให้ไปที่ Android Auto > ปรับแต่ง Launcher แล้วติ๊กเลือก

                วิดีโอจะเล่นได้เฉพาะตอนรถจอดนิ่งเท่านั้น
                ล็อกอิน YouTube ได้ด้วยการแตะ "ลงชื่อเข้าใช้" บนจอรถตอนจอด
            """.trimIndent()
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(grant)
            addView(guide)
        }
        setContentView(ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val ok = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        status.text = if (ok) "✅ อนุญาตตำแหน่งแล้ว\n" else "⚠️ ยังไม่ได้อนุญาตตำแหน่ง (ถ้ารถไม่ส่งความเร็วมา วิดีโอจะถูกล็อก)\n"
    }

    companion object {
        private const val REQ_LOCATION = 1
    }
}
