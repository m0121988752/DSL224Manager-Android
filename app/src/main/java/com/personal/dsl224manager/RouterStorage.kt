package com.personal.dsl224manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * مدير تخزين واسترجاع بيانات الراوترات والخيارات المخصصة محلياً
 * يحفظ الإعدادات وكلمات المرور في SharedPreferences حتى لا تضيع عند إغلاق التطبيق
 */
class RouterStorage(context: Context) {
    private val prefs = context.getSharedPreferences("dsl224_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROUTERS_JSON = "saved_routers_json"
        private const val KEY_ACTIVE_INDEX = "active_router_index"
    }

    fun saveRouters(routers: List<RouterProfile>, activeIndex: Int) {
        val jsonArray = JSONArray()
        routers.forEach { profile ->
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("routerIP", profile.routerIP)
                put("username", profile.username)
                put("password", profile.password)
                put("firmware", profile.firmware.name)
                put("totalPlanGB", profile.totalPlanGB)
                put("carrierName", profile.carrierName)
                put("liveDownloadRateKbps", profile.liveDownloadRateKbps)
                put("liveUploadRateKbps", profile.liveUploadRateKbps)
                put("accumulatedBytes", profile.accumulatedBytes)
                put("enableWebUIFallback", profile.enableWebUIFallback)
                put("webUIPath", profile.webUIPath)

                // إعدادات الكشط المخصص (No-Code Scraper Config)
                put("customUsageSelector", profile.customUsageSelector)
                put("customUsageRegex", profile.customUsageRegex)
                put("customUsageUnit", profile.customUsageUnit)
                put("customDeviceTableSelector", profile.customDeviceTableSelector)
                put("customRebootButtonSelector", profile.customRebootButtonSelector)
                put("customUsernameSelector", profile.customUsernameSelector)
                put("customPasswordSelector", profile.customPasswordSelector)
                put("customLoginButtonSelector", profile.customLoginButtonSelector)

                // حفظ أسماء وتفاصيل الأجهزة
                val devsArr = JSONArray()
                profile.devices.forEach { dev ->
                    val devObj = JSONObject().apply {
                        put("ipAddress", dev.ipAddress)
                        put("macAddress", dev.macAddress)
                        put("originalHostname", dev.originalHostname)
                        put("customName", dev.customName ?: "")
                        put("isOnline", dev.isOnline)
                        put("isBlocked", dev.isBlocked)
                    }
                    devsArr.put(devObj)
                }
                put("devices", devsArr)
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putString(KEY_ROUTERS_JSON, jsonArray.toString())
            .putInt(KEY_ACTIVE_INDEX, activeIndex)
            .apply()
    }

    fun loadRouters(): Pair<List<RouterProfile>, Int> {
        val jsonStr = prefs.getString(KEY_ROUTERS_JSON, null) ?: return Pair(defaultProfiles(), 0)
        val activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0)
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<RouterProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val firmwareStr = obj.optString("firmware", FirmwareType.HUAWEI_B311.name)
                val firmware = try {
                    FirmwareType.valueOf(firmwareStr)
                } catch (_: Exception) {
                    FirmwareType.HUAWEI_B311
                }

                val devsList = mutableListOf<NetworkDevice>()
                val devsArr = obj.optJSONArray("devices")
                if (devsArr != null) {
                    for (d in 0 until devsArr.length()) {
                        val dObj = devsArr.getJSONObject(d)
                        devsList.add(
                            NetworkDevice(
                                ipAddress = dObj.optString("ipAddress", ""),
                                macAddress = dObj.optString("macAddress", ""),
                                originalHostname = dObj.optString("originalHostname", ""),
                                customName = dObj.optString("customName").takeIf { it.isNotBlank() },
                                isOnline = dObj.optBoolean("isOnline", true),
                                isBlocked = dObj.optBoolean("isBlocked", false)
                            )
                        )
                    }
                }

                list.add(
                    RouterProfile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "راوتر Huawei 4G"),
                        routerIP = obj.optString("routerIP", "192.168.1.1"),
                        username = obj.optString("username", "admin"),
                        password = obj.optString("password", ""),
                        firmware = firmware,
                        totalPlanGB = obj.optDouble("totalPlanGB", 140.0),
                        carrierName = obj.optString("carrierName", ""),
                        liveDownloadRateKbps = obj.optDouble("liveDownloadRateKbps", 0.0),
                        liveUploadRateKbps = obj.optDouble("liveUploadRateKbps", 0.0),
                        accumulatedBytes = obj.optLong("accumulatedBytes", 0L),
                        devices = devsList,
                        enableWebUIFallback = obj.optBoolean("enableWebUIFallback", true),
                        webUIPath = obj.optString("webUIPath", ""),
                        customUsageSelector = obj.optString("customUsageSelector", ""),
                        customUsageRegex = obj.optString("customUsageRegex", ""),
                        customUsageUnit = obj.optString("customUsageUnit", "Auto"),
                        customDeviceTableSelector = obj.optString("customDeviceTableSelector", ""),
                        customRebootButtonSelector = obj.optString("customRebootButtonSelector", ""),
                        customUsernameSelector = obj.optString("customUsernameSelector", ""),
                        customPasswordSelector = obj.optString("customPasswordSelector", ""),
                        customLoginButtonSelector = obj.optString("customLoginButtonSelector", "")
                    )
                )
            }
            if (list.isEmpty()) return Pair(defaultProfiles(), 0)
            return Pair(list, activeIndex.coerceIn(0, list.size - 1))
        } catch (_: Exception) {
            return Pair(defaultProfiles(), 0)
        }
    }

    private fun defaultProfiles(): List<RouterProfile> = listOf(
        RouterProfile(
            name = "راوتر Huawei 4G",
            routerIP = "192.168.1.1",
            username = "admin",
            password = "",
            firmware = FirmwareType.HUAWEI_B311,
            totalPlanGB = 140.0
        )
    )
}
