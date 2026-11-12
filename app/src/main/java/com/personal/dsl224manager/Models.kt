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
    var devices: List<NetworkDevice> = emptyList()
) {
    val usedGB: Double get() = accumulatedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val remainingGB: Double get() = (totalPlanGB - usedGB).coerceAtLeast(0.0)
    val usedPercentage: Double get() = if (totalPlanGB > 0) ((usedGB / totalPlanGB) * 100).coerceIn(0.0, 100.0) else 0.0
}
