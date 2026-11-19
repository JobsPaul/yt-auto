package com.jobspaul.ytauto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.versioning.CarAppApiLevels
import com.jobspaul.ytauto.R

/** หน้าหลัก: วิดีโอวาดบน Surface ส่วนปุ่มควบคุมอยู่ใน ActionStrip */
class YtScreen(carContext: CarContext, private val renderer: SurfaceRenderer) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(textAction(R.string.action_search) {
                screenManager.push(SearchScreen(carContext) { query -> renderer.search(query) })
            })
            .addAction(textAction(R.string.action_play_pause) { renderer.togglePlay() })
            .addAction(textAction(R.string.action_back) { renderer.goBack() })
            .addAction(textAction(R.string.action_home) { renderer.goHome() })
            .build()

        val builder = NavigationTemplate.Builder().setActionStrip(actions)

        // โหมด Pan ทำให้ปัดหน้าจอเพื่อเลื่อนหน้าเว็บได้ (Car API 2 ขึ้นไป)
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            builder.setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            builder.setPanModeListener { /* การเลื่อนส่งมาที่ SurfaceRenderer.onScroll */ }
        }
        return builder.build()
    }

    private fun textAction(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setTitle(carContext.getString(res))
            .setOnClickListener(onClick)
            .build()
}

/** ค้นหาด้วยคีย์บอร์ดของรถ (Android Auto เปิดคีย์บอร์ดให้เฉพาะตอนจอด) */
class SearchScreen(
    carContext: CarContext,
    private val onSearch: (String) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isNotBlank()) onSearch(searchText.trim())
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.search_hint))
            .setShowKeyboardByDefault(true)
            .setItemList(ItemList.Builder().build())
            .build()
}
