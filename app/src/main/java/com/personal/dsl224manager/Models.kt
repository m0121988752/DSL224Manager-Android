package com.personal.dsl224manager

import java.util.UUID

enum class FirmwareType(val displayName: String) {
    HUAWEI_B311("Huawei B311-221 (Airbox / Flybox)"),
    ME110_BOA("D-Link DSL-224 (ME_1.10 Boa)"),
    GENERIC_REALTEK("D-Link / Realtek Generic")
}

data class NetworkDevice(
    val ipAddress: String,
    val macAddress: String,
    val originalHostname: String = "",
    val customName: String? = null,
    val isOnline: Boolean = true,
    val isBlocked: Boolean = false,
    val isThrottled: Boolean = false,
    val currentSpeedKbps: Int = 0,
    val isTrusted: Boolean = false
) {
    val id: String get() = macAddress
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: originalHostname.takeIf { it.isNotBlank() } ?: macAddress
    val speedLabel: String get() {
        if (!isThrottled || currentSpeedKbps <= 0) return "كاملة"
        return if (currentSpeedKbps >= 1024 && currentSpeedKbps % 1024 == 0) {
            "${currentSpeedKbps / 1024} ميجا"
        } else {
            "$currentSpeedKbps ك.ب"
        }
    }
}

data class DailyUsageRecord(
    val dateKey: String, // yyyy-MM-dd
    val consumedGB: Double
)

data class RouterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "راوتر D-Link (DSL-224)",
    val routerIP: String = "192.168.1.1",
    val username: String = "admin",
    val password: String = "",
    val firmware: FirmwareType = FirmwareType.ME110_BOA,
    val totalPlanGB: Double = 140.0,
    val renewalDay: Int = 1,
    val manualOffsetGB: Double = 0.0,
    val lastRecordedWANBytes: Long = 0L,
    val accumulatedBytes: Long = 0L,
    val lastResetMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
    val lastResetYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val dailyHistory: List<DailyUsageRecord> = emptyList(),
    val carrierName: String = "",
    val liveDownloadRateKbps: Double = 0.0,
    val liveUploadRateKbps: Double = 0.0,
    val devices: List<NetworkDevice> = emptyList(),
    val enableWebUIFallback: Boolean = true,
    val webUIPath: String = "",

    // تخصيص الكشط المرئي بدون كود (No-Code Scraper Customization)
    val customUsageSelector: String = "",
    val customUsageRegex: String = "",
    val customUsageUnit: String = "Auto", // Auto, GB, MB, Bytes
    val customDeviceTableSelector: String = "",
    val customRebootButtonSelector: String = "",
    val customUsernameSelector: String = "",
    val customPasswordSelector: String = "",
    val customLoginButtonSelector: String = "",

    // إعدادات وضع شبكة الجوال والتحويل التلقائي لراوترات Huawei HiLink
    val networkMode: String = "00", // "00": تلقائي, "03": 4G فقط, "02": 3G فقط
    val autoSwitchTo3GOnLowSpeed: Boolean = false,
    val lowSpeedThresholdKbps: Double = 150.0
) {
    val accumulatedGB: Double get() = accumulatedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val usedGB: Double get() = (manualOffsetGB + accumulatedGB).coerceAtLeast(0.0)
    val remainingGB: Double get() = (totalPlanGB - usedGB).coerceAtLeast(0.0)
    val usedPercentage: Double get() = if (totalPlanGB > 0) ((usedGB / totalPlanGB) * 100).coerceIn(0.0, 100.0) else 0.0
    val daysUntilRenewal: Int get() {
        val cal = java.util.Calendar.getInstance()
        val currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        return if (renewalDay > currentDay) {
            renewalDay - currentDay
        } else if (renewalDay == currentDay) {
            maxDays
        } else {
            (maxDays - currentDay) + renewalDay
        }
    }
    val recommendedDailyGB: Double get() = remainingGB / daysUntilRenewal.coerceAtLeast(1)
}

enum class ElementAssignmentAction {
    USAGE_COUNTER,
    DEVICES_TABLE,
    REBOOT_BUTTON,
    USERNAME_INPUT,
    PASSWORD_INPUT,
    LOGIN_BUTTON,
    COPY_SELECTOR
}

enum class SuggestedElementType(val title: String) {
    USAGE_COUNTER("عداد استهلاك الباقة"),
    DEVICES_TABLE("جدول الأجهزة المتصلة"),
    REBOOT_BUTTON("زر إعادة التشغيل"),
    USERNAME_INPUT("حقل اسم المستخدم"),
    PASSWORD_INPUT("حقل كلمة المرور"),
    LOGIN_BUTTON("زر تسجيل الدخول"),
    UNKNOWN("عنصر عام")
}

data class PickedElementInfo(
    val selector: String,
    val tag: String,
    val text: String,
    val value: String,
    val url: String
) {
    val suggestedType: SuggestedElementType
        get() {
            val full = "$text $value $selector".lowercase()
            return when {
                full.contains("gb") || full.contains("mb") || full.contains("kb") ||
                full.contains("bytes") || full.contains("traffic") || full.contains("usage") ||
                full.contains("download") || full.contains("باقة") || full.contains("استهلاك") ->
                    SuggestedElementType.USAGE_COUNTER

                tag.equals("table", ignoreCase = true) || tag.equals("tbody", ignoreCase = true) ||
                full.contains("device") || full.contains("host") || full.contains("dhcp") ||
                full.contains("mac") || full.contains("client") || full.contains("أجهزة") ->
                    SuggestedElementType.DEVICES_TABLE

                full.contains("reboot") || full.contains("restart") || full.contains("إعادة التشغيل") ->
                    SuggestedElementType.REBOOT_BUTTON

                tag.equals("input", ignoreCase = true) && (full.contains("user") || full.contains("login") || full.contains("اسم المستخدم")) ->
                    SuggestedElementType.USERNAME_INPUT

                tag.equals("input", ignoreCase = true) && (full.contains("pass") || full.contains("pwd") || full.contains("كلمة المرور")) ->
                    SuggestedElementType.PASSWORD_INPUT

                (tag.equals("button", ignoreCase = true) || tag.equals("input", ignoreCase = true) || tag.equals("a", ignoreCase = true)) &&
                (full.contains("login") || full.contains("signin") || full.contains("دخول")) ->
                    SuggestedElementType.LOGIN_BUTTON

                else -> SuggestedElementType.UNKNOWN
            }
        }
}

enum class SemanticButtonAction(val label: String) {
    REBOOT("إعادة التشغيل (Reboot)"),
    LOGIN("تسجيل الدخول (Login)"),
    APPLY("حفظ وتطبيق (Apply)"),
    REFRESH("تحديث (Refresh)")
}

data class WebUIButtonInfo(
    val id: String,
    val selector: String,
    val tag: String,
    val text: String,
    val value: String,
    val isReboot: Boolean = false,
    val isLogin: Boolean = false,
    val isApply: Boolean = false
) {
    val displayLabel: String get() = text.trim().ifEmpty { value.trim().ifEmpty { id.ifEmpty { selector } } }
}

data class WebUIScrapedDevice(
    val macAddress: String,
    val ipAddress: String,
    val hostName: String
)

data class WebUIScrapeResult(
    val success: Boolean,
    val wanBytes: Long? = null,
    val liveDownloadRateKbps: Double? = null,
    val liveUploadRateKbps: Double? = null,
    val carrierName: String? = null,
    val devices: List<WebUIScrapedDevice> = emptyList(),
    val buttons: List<WebUIButtonInfo> = emptyList(),
    val pageTitle: String = "",
    val currentURL: String = "",
    val message: String = ""
)
