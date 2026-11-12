package com.personal.dsl224manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class RouterRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var currentSessionCookie: String? = null
    private var currentVerificationToken: String? = null

    suspend fun refreshHuawei(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile = withContext(Dispatchers.IO) {
        val updated = profile.copy()

        // 1. جلب البيانات العامة المتاحة بدون تسجيل دخول (السرعة اللحظية + المزود + الاستهلاك)
        fetchPublicStats(updated, onLog)

        // 2. إذا توفرت كلمة مرور نقوم بالمصادقة وجلب الأجهزة
        if (updated.password.isNotBlank()) {
            val loggedIn = authenticateHuawei(updated, onLog)
            if (loggedIn) {
                fetchHosts(updated, onLog)
            }
        }
        updated
    }

    fun applyScrapeResult(profile: RouterProfile, result: WebUIScrapeResult, onLog: (String) -> Unit): RouterProfile {
        val updated = profile.copy()
        if (result.wanBytes != null && result.wanBytes > 0) {
            updated.accumulatedBytes = result.wanBytes
            onLog("تم كشط الاستهلاك بنجاح: ${String.format("%.2f", updated.usedGB)} GB")
        }
        if (!result.carrierName.isNullOrBlank()) {
            updated.carrierName = result.carrierName
        }
        if (result.liveDownloadRateKbps != null) {
            updated.liveDownloadRateKbps = result.liveDownloadRateKbps
        }
        if (result.liveUploadRateKbps != null) {
            updated.liveUploadRateKbps = result.liveUploadRateKbps
        }
        if (result.devices.isNotEmpty()) {
            val currentMap = updated.devices.associateBy { it.id }.toMutableMap()
            result.devices.forEach { dev ->
                val cleanMac = dev.macAddress.replace("-", ":").uppercase()
                val existing = currentMap[cleanMac]
                if (existing != null) {
                    currentMap[cleanMac] = existing.copy(ipAddress = dev.ipAddress)
                } else {
                    currentMap[cleanMac] = NetworkDevice(
                        ipAddress = dev.ipAddress,
                        macAddress = cleanMac,
                        originalHostname = dev.hostName,
                        isOnline = true
                    )
                }
            }
            updated.devices = currentMap.values.toList()
            onLog("تم استخراج ${result.devices.size} جهاز عبر كشط صفحة الراوتر")
        }
        return updated
    }

    private fun fetchPublicStats(profile: RouterProfile, onLog: (String) -> Unit) {
        try {
            // مزود الشبكة
            val plmnReq = Request.Builder().url("http://${profile.routerIP}/api/net/current-plmn").build()
            client.newCall(plmnReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val carrier = extractTag("FullName", body) ?: extractTag("ShortName", body)
                if (!carrier.isNullOrBlank()) {
                    profile.carrierName = carrier
                    onLog("شبكة 4G: $carrier")
                }
            }

            // السرعة اللحظية
            val statsReq = Request.Builder().url("http://${profile.routerIP}/api/monitoring/traffic-statistics").build()
            client.newCall(statsReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val down = extractTag("CurrentDownloadRate", body)?.toDoubleOrNull() ?: 0.0
                val up = extractTag("CurrentUploadRate", body)?.toDoubleOrNull() ?: 0.0
                profile.liveDownloadRateKbps = down / 1024.0
                profile.liveUploadRateKbps = up / 1024.0
            }

            // الاستهلاك التراكمي المدمج
            val monthReq = Request.Builder().url("http://${profile.routerIP}/api/monitoring/month_statistics").build()
            client.newCall(monthReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val down = extractTag("CurrentMonthDownload", body)?.toLongOrNull() ?: 0L
                val up = extractTag("CurrentMonthUpload", body)?.toLongOrNull() ?: 0L
                if (down + up > 0) {
                    profile.accumulatedBytes = down + up
                    onLog("استهلاك الراوتر: ${String.format("%.2f", profile.usedGB)} GB")
                }
            }
        } catch (e: Exception) {
            onLog("تنبيه قراءة الإحصائيات: ${e.message}")
        }
    }

    private fun authenticateHuawei(profile: RouterProfile, onLog: (String) -> Unit): Boolean {
        try {
            // 1. جلب SesTokInfo
            val sesReq = Request.Builder().url("http://${profile.routerIP}/api/webserver/SesTokInfo").build()
            val (sesInfo, tokInfo, cookieHeader) = client.newCall(sesReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val cookie = res.header("Set-Cookie")
                Triple(extractTag("SesInfo", body), extractTag("TokInfo", body), cookie)
            }

            if (sesInfo.isNullOrBlank() || tokInfo.isNullOrBlank()) {
                onLog("❌ تعذر جلب SesTokInfo من الراوتر")
                return false
            }

            val cookie = (cookieHeader?.substringBefore(";") ?: "SessionID=$sesInfo").trim()
            currentSessionCookie = cookie
            currentVerificationToken = tokInfo

            // 2. تحدي المصادقة challenge_login
            val random = SecureRandom()
            val nonceBytes = ByteArray(24).also { random.nextBytes(it) }
            val firstNonce = nonceBytes.joinToString("") { "%02x".format(it) }

            val chalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <username>${profile.username}</username>
                    <firstnonce>$firstNonce</firstnonce>
                    <mode>1</mode>
                </request>
            """.trimIndent()

            val chalReq = Request.Builder()
                .url("http://${profile.routerIP}/api/user/challenge_login")
                .post(chalXml.toRequestBody("application/xml".toMediaType()))
                .addHeader("Cookie", if (cookie.startsWith("SessionID=")) cookie else "SessionID=$cookie")
                .addHeader("__RequestVerificationToken", tokInfo)
                .build()

            val (saltHex, serverNonce, step2Tok, step2Cookie) = client.newCall(chalReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val salt = extractTag("salt", body)
                val sNonce = extractTag("servernonce", body)
                val newTok = res.header("__RequestVerificationToken") ?: tokInfo
                val newCookie = res.header("Set-Cookie")?.substringBefore(";") ?: cookie
                Quad(salt, sNonce, newTok, newCookie)
            }

            if (saltHex.isNullOrBlank() || serverNonce.isNullOrBlank()) {
                onLog("❌ فشل تحدي الراوتر (challenge_login)")
                return false
            }

            // 3. حساب Huawei SCRAM-SHA256 المطابق 100% لسورس كود راوتر هواوي
            val authMsg = "$firstNonce,$serverNonce,$serverNonce"
            val saltBytes = hexToBytes(saltHex)
            val saltedPassword = pbkdf2SHA256(profile.password, saltBytes, 100, 32)

            // ترتيب معاملات HMAC المعكوس في مكتبة هواوي CryptoJS:
            val clientKey = hmacSHA256("Client Key".toByteArray(Charsets.UTF_8), saltedPassword)
            val storedKey = sha256(clientKey)
            val clientSig = hmacSHA256(authMsg.toByteArray(Charsets.UTF_8), storedKey)
            val clientProof = bytesToHex(xorBytes(clientKey, clientSig))

            // 4. إرسال authentication_login
            val authXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <clientproof>$clientProof</clientproof>
                    <finalnonce>$serverNonce</finalnonce>
                </request>
            """.trimIndent()

            val authReq = Request.Builder()
                .url("http://${profile.routerIP}/api/user/authentication_login")
                .post(authXml.toRequestBody("application/xml".toMediaType()))
                .addHeader("Cookie", if (step2Cookie.startsWith("SessionID=")) step2Cookie else "SessionID=$step2Cookie")
                .addHeader("__RequestVerificationToken", step2Tok)
                .build()

            return client.newCall(authReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                res.header("Set-Cookie")?.let { currentSessionCookie = it.substringBefore(";").trim() }
                res.header("__RequestVerificationToken")?.let { currentVerificationToken = it }

                if (body.contains("<response>OK</response>") || body.contains("<serverproof>") || (!body.contains("<error>") && res.isSuccessful)) {
                    onLog("✅ تم تسجيل الدخول لراوتر Huawei بنجاح (Huawei SCRAM)")
                    true
                } else {
                    val code = extractTag("code", body) ?: "غير معروف"
                    onLog("❌ فشل المصادقة (كود: $code) - تأكد من كلمة المرور")
                    false
                }
            }
        } catch (e: Exception) {
            onLog("خطأ أثناء تسجيل الدخول: ${e.message}")
            return false
        }
    }

    private fun fetchHosts(profile: RouterProfile, onLog: (String) -> Unit) {
        try {
            val req = Request.Builder()
                .url("http://${profile.routerIP}/api/wlan/host-list")
                .addHeader("Cookie", currentSessionCookie.orEmpty())
                .addHeader("__RequestVerificationToken", currentVerificationToken.orEmpty())
                .build()

            client.newCall(req).execute().use { res ->
                val xml = res.body?.string().orEmpty()
                val devices = parseHosts(xml)
                if (devices.isNotEmpty()) {
                    profile.devices = devices
                    onLog("تم العثور على ${devices.size} جهاز متصل")
                }
            }
        } catch (e: Exception) {
            onLog("خطأ في جلب قائمة الأجهزة: ${e.message}")
        }
    }

    suspend fun toggleBlockDevice(profile: RouterProfile, device: NetworkDevice, block: Boolean, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val blockedList = profile.devices.filter { it.macAddress != device.macAddress && it.isBlocked }.toMutableList()
            if (block) blockedList.add(device)

            val filterStatus = if (blockedList.isEmpty()) 0 else 2
            val entriesXml = StringBuilder()
            blockedList.take(10).forEachIndexed { i, dev ->
                val mac = dev.macAddress.replace("-", ":").uppercase()
                val name = dev.displayName.replace(" ", "_")
                entriesXml.append("<WifiMacFilterMac$i>$mac</WifiMacFilterMac$i>\n")
                entriesXml.append("<wifihostname$i>$name</wifihostname$i>\n")
            }

            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <Ssids>
                        <Ssid>
                            <Index>0</Index>
                            <WifiMacFilterStatus>$filterStatus</WifiMacFilterStatus>
                            $entriesXml
                        </Ssid>
                    </Ssids>
                </request>
            """.trimIndent()

            val req = Request.Builder()
                .url("http://${profile.routerIP}/api/wlan/multi-macfilter-settings")
                .post(xml.toRequestBody("application/xml".toMediaType()))
                .addHeader("Cookie", currentSessionCookie.orEmpty())
                .addHeader("__RequestVerificationToken", currentVerificationToken.orEmpty())
                .build()

            client.newCall(req).execute().use { res ->
                res.header("__RequestVerificationToken")?.let { currentVerificationToken = it }
                val body = res.body?.string().orEmpty()
                val ok = body.contains("<response>OK</response>") || res.isSuccessful
                if (ok) {
                    device.isBlocked = block
                    onLog("${if (block) "تم حظر" else "تم إلغاء حظر"}: ${device.displayName}")
                }
                ok
            }
        } catch (e: Exception) {
            onLog("خطأ في أمر الحظر: ${e.message}")
            false
        }
    }

    // أدوات التشفير المساعدة
    private fun pbkdf2SHA256(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength * 8)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return f.generateSecret(spec).encoded
    }

    private fun hmacSHA256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        val res = ByteArray(a.size)
        for (i in a.indices) res[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        return res
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun extractTag(tag: String, xml: String): String? {
        val pattern = "<$tag>([^<]+)</$tag>".toRegex()
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun parseHosts(xml: String): List<NetworkDevice> {
        val list = mutableListOf<NetworkDevice>()
        val regex = "<Host>([\\s\\S]*?)</Host>".toRegex()
        regex.findAll(xml).forEach { m ->
            val hostXml = m.groupValues[1]
            val mac = extractTag("MacAddress", hostXml)?.replace("-", ":")?.uppercase().orEmpty()
            val ip = extractTag("IpAddress", hostXml) ?: "192.168.1.50"
            val name = extractTag("HostName", hostXml).orEmpty()
            if (mac.isNotBlank()) {
                list.add(NetworkDevice(ipAddress = ip, macAddress = mac, originalHostname = name))
            }
        }
        return list
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
