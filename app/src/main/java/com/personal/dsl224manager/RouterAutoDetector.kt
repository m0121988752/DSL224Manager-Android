package com.personal.dsl224manager

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * نتيجة فحص واكتشاف الراوتر التلقائي على الشبكة
 */
data class AutoDetectResult(
    val ip: String,
    val detectedName: String,
    val manufacturer: String,
    val firmware: FirmwareType,
    val presetId: String? = null,
    val description: String,
    val isConfirmedByHttp: Boolean = false
)

/**
 * محرك الفحص والكشف التلقائي عن الراوتر وعنوان Gateway وموديل الراوتر
 */
object RouterAutoDetector {

    /**
     * جلب عنوان Gateway الخاص بالواي فاي من النظام
     */
    fun getWifiGatewayIP(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wm?.dhcpInfo ?: return null
            val gateway = dhcp.gateway
            if (gateway == 0) return null
            String.format(
                Locale.US,
                "%d.%d.%d.%d",
                gateway and 0xff,
                (gateway shr 8) and 0xff,
                (gateway shr 16) and 0xff,
                (gateway shr 24) and 0xff
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * فحص واكتشاف الراوتر المتصل بالشبكة تلقائياً
     */
    suspend fun detectRouter(context: Context, customCandidateIP: String? = null): AutoDetectResult = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()

        val candidates = mutableListOf<String>()
        if (!customCandidateIP.isNullOrBlank()) {
            candidates.add(customCandidateIP.trim())
        }
        val wifiGateway = getWifiGatewayIP(context)
        if (!wifiGateway.isNullOrBlank()) {
            candidates.add(wifiGateway.trim())
        }
        candidates.addAll(listOf("192.168.1.1", "192.168.8.1", "192.168.0.1", "192.168.2.1"))
        val distinctCandidates = candidates.distinct()

        for (ip in distinctCandidates) {
            // 1. فحص بروتوكول Huawei HiLink أولاً (B311 / Flybox)
            try {
                val hilinkReq = Request.Builder()
                    .url("http://$ip/api/webserver/SesTokInfo")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(hilinkReq).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful && (body.contains("<response>") || body.contains("SesInfo") || body.contains("TokInfo"))) {
                        return@withContext AutoDetectResult(
                            ip = ip,
                            detectedName = "Huawei B311-221 (HiLink 4G)",
                            manufacturer = "Huawei",
                            firmware = FirmwareType.HUAWEI_B311,
                            presetId = "huawei_b311",
                            description = "تم اكتشاف راوتر هواوي 4G بنظام HiLink API المباشر",
                            isConfirmedByHttp = true
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. فحص صفحة البداية العامة ومسارات الويب للراوترات الأخرى
            val checkPaths = listOf("", "tc.html", "status_traffic.html", "login.cgi", "boaform/admin/formLogin")
            for (path in checkPaths) {
                try {
                    val url = if (path.isEmpty()) "http://$ip/" else "http://$ip/$path"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val code = resp.code
                        val body = (resp.body?.string() ?: "").lowercase()
                        val server = (resp.header("Server") ?: "").lowercase()

                        // D-Link DSL-224 (ME_1.10 Boa)
                        if (body.contains("dsl-224") || body.contains("dsl 224") || server.contains("boa") || body.contains("boa/") || body.contains("formlogin") || path.contains("tc.html")) {
                            return@withContext AutoDetectResult(
                                ip = ip,
                                detectedName = "D-Link DSL-224 (ME_1.10 Boa)",
                                manufacturer = "D-Link",
                                firmware = FirmwareType.ME110_BOA,
                                presetId = "dlink_dsl224",
                                description = "تم اكتشاف راوتر D-Link DSL-224 وسيرفر Boa بنجاح",
                                isConfirmedByHttp = true
                            )
                        }

                        // ZTE ZXHN H168N / H188A
                        if (body.contains("zxhn") || body.contains("h168n") || body.contains("h188a") || body.contains("zte")) {
                            return@withContext AutoDetectResult(
                                ip = ip,
                                detectedName = "ZTE ZXHN H168N / H188A",
                                manufacturer = "ZTE",
                                firmware = FirmwareType.GENERIC_REALTEK,
                                presetId = "zte_h168n",
                                description = "تم اكتشاف راوتر ZTE VDSL واسع الانتشار",
                                isConfirmedByHttp = true
                            )
                        }

                        // Vodafone VDSL (H300s)
                        if (body.contains("vodafone") || body.contains("h300s") || body.contains("dg8045")) {
                            return@withContext AutoDetectResult(
                                ip = ip,
                                detectedName = "Vodafone VDSL (H300s / DG8045)",
                                manufacturer = "Vodafone",
                                firmware = FirmwareType.GENERIC_REALTEK,
                                presetId = "vodafone_h300s",
                                description = "تم اكتشاف راوتر فودافون VDSL",
                                isConfirmedByHttp = true
                            )
                        }

                        // TP-Link
                        if (body.contains("tp-link") || body.contains("archer") || body.contains("vr600") || body.contains("tplink")) {
                            return@withContext AutoDetectResult(
                                ip = ip,
                                detectedName = "TP-Link Archer / VR600",
                                manufacturer = "TP-Link",
                                firmware = FirmwareType.GENERIC_REALTEK,
                                presetId = "tplink_vr600",
                                description = "تم اكتشاف راوتر تي بي لينك",
                                isConfirmedByHttp = true
                            )
                        }

                        // Orange Home 4G Flybox (MF283+)
                        if (body.contains("mf283") || body.contains("orange flybox")) {
                            return@withContext AutoDetectResult(
                                ip = ip,
                                detectedName = "Orange Home 4G Flybox (MF283+)",
                                manufacturer = "Orange",
                                firmware = FirmwareType.GENERIC_REALTEK,
                                presetId = "orange_flybox_mf283",
                                description = "تم اكتشاف راوتر أورنج الهوائي 4G",
                                isConfirmedByHttp = true
                            )
                        }

                        // إذا كان السيرفر يرد برمز 200 أو 401 أو 302 ومتاح
                        if (code in 200..403) {
                            val isHuaweiDefault = ip == "192.168.8.1"
                            val fw = if (isHuaweiDefault) FirmwareType.HUAWEI_B311 else FirmwareType.ME110_BOA
                            return@withContext AutoDetectResult(
                                ip = ip,
                                detectedName = if (isHuaweiDefault) "راوتر 4G (192.168.8.1)" else "راوتر منزلي ($ip)",
                                manufacturer = if (isHuaweiDefault) "Huawei" else "Generic",
                                firmware = fw,
                                presetId = if (isHuaweiDefault) "huawei_b311" else "dlink_dsl224",
                                description = "تم اكتشاف بوابة راوتر نشطة تستجيب على عنوان IP",
                                isConfirmedByHttp = true
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // في حال تعذر الاتصال بـ HTTP، نعتمد عنوان Gateway الافتراضي
        val fallbackIP = wifiGateway ?: "192.168.1.1"
        val isHuaweiIP = fallbackIP == "192.168.8.1"
        AutoDetectResult(
            ip = fallbackIP,
            detectedName = if (isHuaweiIP) "راوتر 4G (Huawei B311)" else "راوتر D-Link (DSL-224)",
            manufacturer = if (isHuaweiIP) "Huawei" else "D-Link",
            firmware = if (isHuaweiIP) FirmwareType.HUAWEI_B311 else FirmwareType.ME110_BOA,
            presetId = if (isHuaweiIP) "huawei_b311" else "dlink_dsl224",
            description = "العنوان الافتراضي لشبكة الواي فاي الحالية",
            isConfirmedByHttp = false
        )
    }
}
