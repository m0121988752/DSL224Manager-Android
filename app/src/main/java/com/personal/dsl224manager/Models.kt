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
    val originalHostname: String,
    val customName: String? = null,
    val isOnline: Boolean = true,
    var isBlocked: Boolean = false
) {
    val id: String get() = macAddress
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: originalHostname.takeIf { it.isNotBlank() } ?: macAddress
}

data class RouterProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "راوتر Huawei 4G",
    var routerIP: String = "192.168.1.1",
    var username: String = "admin",
    var password: String = "",
    var firmware: FirmwareType = FirmwareType.HUAWEI_B311,
    var totalPlanGB: Double = 140.0,
    var carrierName: String = "",
    var liveDownloadRateKbps: Double = 0.0,
    var liveUploadRateKbps: Double = 0.0,
    var accumulatedBytes: Long = 0,
    var devices: List<NetworkDevice> = emptyList(),
    var enableWebUIFallback: Boolean = true,
    var webUIPath: String = "",

    // تخصيص الكشط المرئي بدون كود (No-Code Scraper Customization)
    var customUsageSelector: String = "",
    var customUsageRegex: String = "",
    var customUsageUnit: String = "Auto", // Auto, GB, MB, Bytes
    var customDeviceTableSelector: String = "",
    var customRebootButtonSelector: String = "",
    var customUsernameSelector: String = "",
    var customPasswordSelector: String = "",
    var customLoginButtonSelector: String = ""
) {
    val usedGB: Double get() = accumulatedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val remainingGB: Double get() = (totalPlanGB - usedGB).coerceAtLeast(0.0)
    val usedPercentage: Double get() = if (totalPlanGB > 0) ((usedGB / totalPlanGB) * 100).coerceIn(0.0, 100.0) else 0.0
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
