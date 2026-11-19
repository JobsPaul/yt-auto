package com.jobspaul.ytauto.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.jobspaul.ytauto.BuildConfig

class YtCarAppService : CarAppService() {

    // แอปติดตั้งเองใช้ส่วนตัว: ยอมรับทุก host (Android Auto, DHU)
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = YtSession()
}

class YtSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        if (!BuildConfig.NAV) return TestScreen(carContext)
        val renderer = SurfaceRenderer(carContext, lifecycle)
        ParkingGuard(carContext, lifecycle) { state -> renderer.setDriveState(state) }
        return YtScreen(carContext, renderer)
    }
}

/** หน้าของ APK ทดสอบ: ถ้าเห็นหน้านี้บนจอรถ แปลว่า Android Auto ยอมแสดงแอปที่ติดตั้งเอง */
class TestScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("YT Test ทำงานบนจอรถได้ ✅\nแอปติดตั้งเองแสดงได้ แต่แอปประเภทนำทางถูกซ่อน")
            .setTitle("YT Test")
            .setHeaderAction(Action.APP_ICON)
            .build()
}
