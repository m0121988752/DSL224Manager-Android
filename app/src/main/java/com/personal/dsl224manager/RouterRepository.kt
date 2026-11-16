package com.personal.dsl224manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class RouterRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // عزل الجلسات والرموز الأمنية لكل راوتر على حدة لمنع تداخل الجلسات
    private val sessionCookies = ConcurrentHashMap<String, String>()
    private val verificationTokens = ConcurrentHashMap<String, String>()

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun urlEncode(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
    }

    /**
     * تحديث بيانات الراوتر بناءً على نوع النظام (Huawei HiLink أو D-Link DSL-224)
     */
    suspend fun refreshRouter(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile = withContext(Dispatchers.IO) {
        val resetChecked = checkMonthlyReset(profile)
        when (resetChecked.firmware) {
            FirmwareType.HUAWEI_B311 -> refreshHuawei(resetChecked, onLog)
            FirmwareType.ME110_BOA, FirmwareType.GENERIC_REALTEK -> refreshDLink(resetChecked, onLog)
        }
    }

    /**
     * فحص دورة التجديد الشهرية وإعادة تصفير العداد عند حلول يوم التجديد
     */
    fun checkMonthlyReset(profile: RouterProfile): RouterProfile {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentYear = now.get(Calendar.YEAR)
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        if (profile.lastResetMonth == 0) {
            return profile.copy(lastResetMonth = currentMonth, lastResetYear = currentYear)
        }

        val hasRolledOver = currentYear > profile.lastResetYear ||
                (currentYear == profile.lastResetYear && currentMonth > profile.lastResetMonth)

        if (hasRolledOver && currentDay >= profile.renewalDay) {
            return profile.copy(
                accumulatedBytes = 0L,
                lastRecordedWANBytes = 0L,
                lastResetMonth = currentMonth,
                lastResetYear = currentYear
            )
        }
        return profile
    }

    /**
     * معالجة عداد البايتات التراكمي ومقاومة تصفير العداد عند إعادة تشغيل الراوتر
     */
    fun processWANBytes(profile: RouterProfile, rawWANBytes: Long): RouterProfile {
        if (rawWANBytes <= 0L) return profile

        val baseProfile = checkMonthlyReset(profile)
        val lastBytes = baseProfile.lastRecordedWANBytes
        val delta: Long

        if (lastBytes == 0L) {
            delta = 0L
        } else if (rawWANBytes < lastBytes) {
            // حدثت إعادة تشغيل للراوتر وتصفر العداد الداخلي -> نحتفظ بالرصيد التراكمي ونضيف القيمة الجديدة
            delta = rawWANBytes
        } else {
            delta = rawWANBytes - lastBytes
        }

        val newAccumulated = if (lastBytes == 0L) {
            if (baseProfile.accumulatedBytes > 0L) baseProfile.accumulatedBytes else rawWANBytes
        } else {
            baseProfile.accumulatedBytes + delta
        }

        // تسجيل استهلاك اليوم في سجل 30 يوماً
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentDayGB = baseProfile.dailyHistory[todayKey] ?: 0.0
        val deltaGB = if (delta > 0L) delta.toDouble() / (1024.0 * 1024.0 * 1024.0) else 0.0
        val updatedHistory = baseProfile.dailyHistory.toMutableMap().apply {
            put(todayKey, currentDayGB + deltaGB)
        }

        return baseProfile.copy(
            accumulatedBytes = newAccumulated,
            lastRecordedWANBytes = rawWANBytes,
            dailyHistory = updatedHistory
        )
    }

    /**
     * اختبار الاتصال وتسجيل الدخول ببيانات الاعتماد للراوتر
     */
    suspend fun testConnection(profile: RouterProfile, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            when (profile.firmware) {
                FirmwareType.HUAWEI_B311 -> {
                    if (profile.password.isBlank()) {
                        onLog("[تنبيه] يرجى إدخال كلمة المرور أولاً")
                        return@withContext false
                    }
                    val ok = ensureAuthenticatedSession(profile, onLog)
                    if (ok) {
                        onLog("[نجاح] تم تسجيل الدخول لراوتر هواوي بنجاح")
                    } else {
                        onLog("[خطأ] فشل تسجيل الدخول، تأكد من صحة كلمة المرور")
                    }
                    ok
                }
                FirmwareType.ME110_BOA, FirmwareType.GENERIC_REALTEK -> {
                    val loginEndpoints = if (profile.firmware == FirmwareType.GENERIC_REALTEK) {
                        listOf("http://${profile.routerIP}/boaform/admin/formLogin", "http://${profile.routerIP}/login.cgi")
                    } else {
                        listOf("http://${profile.routerIP}/login.cgi", "http://${profile.routerIP}/boaform/admin/formLogin")
                    }
                    var success = false
                    val encodedUser = urlEncode(profile.username)
                    val encodedPass = urlEncode(profile.password)
                    val formBody = "username=$encodedUser&password=$encodedPass"
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())

                    for (ep in loginEndpoints) {
                        try {
                            val loginReq = Request.Builder().url(ep).post(formBody).build()
                            val ok = client.newCall(loginReq).execute().use { res ->
                                res.isSuccessful || res.code in 200..399
                            }
                            if (ok) {
                                success = true
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    if (success) {
                        onLog("[نجاح] تم تسجيل الدخول لراوتر D-Link بنجاح")
                    } else {
                        onLog("[خطأ] فشل الاتصال براوتر D-Link (${profile.routerIP})")
                    }
                    success
                }
            }
        } catch (e: Exception) {
            onLog("[خطأ] خطأ أثناء الاتصال: ${e.localizedMessage}")
            false
        }
    }

    suspend fun loginAndRefresh(profile: RouterProfile, onLog: (String) -> Unit): Boolean = testConnection(profile, onLog)

    suspend fun refreshDLink(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile = withContext(Dispatchers.IO) {
        var current = profile
        onLog("الاتصال براوتر D-Link DSL-224 (${profile.routerIP})...")

        // 1. تسجيل الدخول إذا توفرت كلمة المرور
        var cookie = sessionCookies[profile.routerIP]
        if (profile.password.isNotBlank()) {
            val loginEndpoints = if (profile.firmware == FirmwareType.GENERIC_REALTEK) {
                listOf("http://${profile.routerIP}/boaform/admin/formLogin", "http://${profile.routerIP}/login.cgi")
            } else {
                listOf("http://${profile.routerIP}/login.cgi", "http://${profile.routerIP}/boaform/admin/formLogin")
            }

            val encodedUser = urlEncode(profile.username)
            val encodedPass = urlEncode(profile.password)
            val formBody = "username=$encodedUser&password=$encodedPass"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            for (ep in loginEndpoints) {
                try {
                    val loginReq = Request.Builder()
                        .url(ep)
                        .post(formBody)
                        .build()

                    val (success, setCookie) = client.newCall(loginReq).execute().use { res ->
                        Pair(res.isSuccessful || res.code in 200..399, res.header("Set-Cookie"))
                    }

                    if (!setCookie.isNullOrBlank()) {
                        cookie = setCookie.substringBefore(";").trim()
                        sessionCookies[profile.routerIP] = cookie
                    }

                    if (success) {
                        onLog("[نجاح] تم إرسال بيانات الدخول لراوتر D-Link ($ep)")
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. قراءة عداد بايتات الاستهلاك التراكمية
        val trafficPages = listOf("status_traffic.html", "tc.html", "status.html", "tc_traffic.html")
        for (page in trafficPages) {
            try {
                val reqBuilder = Request.Builder().url("http://${profile.routerIP}/$page")
                if (!cookie.isNullOrBlank()) {
                    reqBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(reqBuilder.build()).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val bytes = extractWANBytesFromDLink(body)
                        if (bytes != null && bytes > 0L) {
                            current = processWANBytes(current, bytes)
                            onLog("تمت قراءة استهلاك D-Link: ${String.format(Locale.US, "%.2f", current.usedGB)} GB")
                        }
                    }
                }
                if (current.accumulatedBytes > 0L) break
            } catch (_: Exception) {}
        }

        // 3. قراءة قائمة الأجهزة المتصلة
        val clientPages = listOf("dhcpclients.html", "status_dhcp.html", "lan.html", "wlan_clients.html")
        for (page in clientPages) {
            try {
                val reqBuilder = Request.Builder().url("http://${profile.routerIP}/$page")
                if (!cookie.isNullOrBlank()) {
                    reqBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(reqBuilder.build()).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val foundDevs = parseDLinkClients(body)
                        if (foundDevs.isNotEmpty()) {
                            val currentMap = current.devices.associateBy { it.macAddress.replace("-", ":").uppercase() }.toMutableMap()
                            foundDevs.forEach { dev ->
                                val cleanMac = dev.macAddress.replace("-", ":").uppercase()
                                val existing = currentMap[cleanMac]
                                if (existing != null) {
                                    currentMap[cleanMac] = existing.copy(
                                        ipAddress = dev.ipAddress,
                                        isOnline = true
                                    )
                                } else {
                                    currentMap[cleanMac] = dev
                                }
                            }
                            current = current.copy(devices = currentMap.values.toList())
                            onLog("تم العثور على ${foundDevs.size} جهاز متصل في D-Link ($page)")
                        }
                    }
                }
                if (current.devices.isNotEmpty()) break
            } catch (_: Exception) {}
        }

        current
    }

    suspend fun refreshHuawei(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile = withContext(Dispatchers.IO) {
        var current = profile

        // 1. جلب البيانات العامة المتاحة بدون تسجيل دخول (السرعة اللحظية + المزود + الاستهلاك)
        current = fetchPublicStats(current, onLog)

        // 2. إذا توفرت كلمة مرور نقوم بالمصادقة وجلب الأجهزة وحالة الحظر وقراءة وضع الشبكة
        if (current.password.isNotBlank()) {
            val loggedIn = ensureAuthenticatedSession(current, onLog)
            if (loggedIn) {
                current = fetchHosts(current, onLog)
                current = fetchHuaweiBlockList(current, onLog)
                val netMode = fetchHuaweiNetMode(current, onLog)
                if (netMode != null) {
                    current = current.copy(networkMode = netMode)
                }
                checkAutoSwitchTo3G(current, onLog)
            }
        }
        current
    }

    fun applyScrapeResult(profile: RouterProfile, result: WebUIScrapeResult, onLog: (String) -> Unit): RouterProfile {
        var updated = profile
        if (result.wanBytes != null && result.wanBytes > 0) {
            updated = processWANBytes(updated, result.wanBytes)
            onLog("تم كشط الاستهلاك بنجاح: ${String.format(Locale.US, "%.2f", updated.usedGB)} GB")
        }
        if (!result.carrierName.isNullOrBlank()) {
            updated = updated.copy(carrierName = result.carrierName)
        }
        if (result.liveDownloadRateKbps != null) {
            updated = updated.copy(liveDownloadRateKbps = result.liveDownloadRateKbps)
        }
        if (result.liveUploadRateKbps != null) {
            updated = updated.copy(liveUploadRateKbps = result.liveUploadRateKbps)
        }
        if (result.devices.isNotEmpty()) {
            val currentMap = updated.devices.associateBy { it.macAddress.replace("-", ":").uppercase() }.toMutableMap()
            result.devices.forEach { dev ->
                val cleanMac = dev.macAddress.replace("-", ":").uppercase()
                val existing = currentMap[cleanMac]
                if (existing != null) {
                    currentMap[cleanMac] = existing.copy(ipAddress = dev.ipAddress, isOnline = true)
                } else {
                    currentMap[cleanMac] = NetworkDevice(
                        ipAddress = dev.ipAddress,
                        macAddress = cleanMac,
                        originalHostname = dev.hostName,
                        isOnline = true
                    )
                }
            }
            updated = updated.copy(devices = currentMap.values.toList())
        }
        return updated
    }

    private fun fetchPublicStats(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile {
        var updated = profile
        try {
            // مزود الخدمة
            val plmnReq = Request.Builder().url("http://${profile.routerIP}/api/net/current-plmn").build()
            client.newCall(plmnReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val carrier = extractTag("FullName", body) ?: extractTag("ShortName", body)
                if (!carrier.isNullOrBlank()) {
                    updated = updated.copy(carrierName = carrier)
                    onLog("شبكة 4G: $carrier")
                }
            }

            // السرعة اللحظية
            val trafReq = Request.Builder().url("http://${profile.routerIP}/api/monitoring/traffic-statistics").build()
            client.newCall(trafReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val down = extractTag("CurrentDownloadRate", body)?.toDoubleOrNull() ?: 0.0
                val up = extractTag("CurrentUploadRate", body)?.toDoubleOrNull() ?: 0.0
                updated = updated.copy(
                    liveDownloadRateKbps = down / 1024.0,
                    liveUploadRateKbps = up / 1024.0
                )
            }

            // الاستهلاك التراكمي المدمج
            val monthReq = Request.Builder().url("http://${profile.routerIP}/api/monitoring/month_statistics").build()
            client.newCall(monthReq).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val down = extractTag("CurrentMonthDownload", body)?.toLongOrNull() ?: 0L
                val up = extractTag("CurrentMonthUpload", body)?.toLongOrNull() ?: 0L
                val totalBytes = down + up
                if (totalBytes > 0) {
                    updated = processWANBytes(updated, totalBytes)
                    onLog("استهلاك الراوتر: ${String.format(Locale.US, "%.2f", updated.usedGB)} GB")
                }
            }
        } catch (e: Exception) {
            onLog("تنبيه قراءة الإحصائيات: ${e.message}")
        }
        return updated
    }

    private fun isSessionValid(routerIP: String): Boolean {
        val cookie = sessionCookies[routerIP] ?: return false
        return try {
            val req = Request.Builder()
                .url("http://$routerIP/api/user/state-login")
                .addHeader("Cookie", cookie)
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                extractTag("State", body) == "0"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureAuthenticatedSession(profile: RouterProfile, onLog: (String) -> Unit): Boolean {
        if (isSessionValid(profile.routerIP)) {
            return true
        }
        return authenticateHuawei(profile, onLog)
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
                onLog("[خطأ] تعذر جلب SesTokInfo من الراوتر")
                return false
            }

            val cookie = (cookieHeader?.substringBefore(";") ?: "SessionID=$sesInfo").trim()
            sessionCookies[profile.routerIP] = cookie
            verificationTokens[profile.routerIP] = tokInfo

            // 2. تحدي المصادقة challenge_login
            val random = SecureRandom()
            val nonceBytes = ByteArray(24).also { random.nextBytes(it) }
            val firstNonce = nonceBytes.joinToString("") { "%02x".format(it) }

            val chalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <username>${escapeXml(profile.username)}</username>
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
                onLog("[خطأ] فشل تحدي الراوتر (challenge_login)")
                return false
            }

            // 3. حساب Huawei SCRAM-SHA256 المطابق 100% لسورس كود راوتر هواوي
            val authMsg = "$firstNonce,$serverNonce,$serverNonce"
            val saltBytes = hexToBytes(saltHex)
            val saltedPassword = pbkdf2SHA256(profile.password, saltBytes, 100, 32)

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
                res.header("Set-Cookie")?.let { sessionCookies[profile.routerIP] = it.substringBefore(";").trim() }
                res.header("__RequestVerificationToken")?.let { verificationTokens[profile.routerIP] = it }

                if (body.contains("<response>OK</response>") || body.contains("<serverproof>") || (!body.contains("<error>") && res.isSuccessful)) {
                    onLog("[نجاح] تم تسجيل الدخول لراوتر Huawei بنجاح (Huawei SCRAM)")
                    true
                } else {
                    val code = extractTag("code", body) ?: "غير معروف"
                    onLog("[خطأ] فشل المصادقة (كود: $code) - تأكد من كلمة المرور")
                    false
                }
            }
        } catch (e: Exception) {
            onLog("خطأ أثناء تسجيل الدخول: ${e.message}")
            return false
        }
    }

    private fun fetchHosts(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile {
        var updated = profile
        try {
            val req = Request.Builder()
                .url("http://${profile.routerIP}/api/wlan/host-list")
                .addHeader("Cookie", sessionCookies[profile.routerIP].orEmpty())
                .addHeader("__RequestVerificationToken", verificationTokens[profile.routerIP].orEmpty())
                .build()

            client.newCall(req).execute().use { res ->
                res.header("__RequestVerificationToken")?.let { verificationTokens[profile.routerIP] = it }
                val xml = res.body?.string().orEmpty()
                val detectedDevices = parseHosts(xml)
                if (detectedDevices.isNotEmpty()) {
                    val currentMap = profile.devices.associateBy { it.macAddress.replace("-", ":").uppercase() }.toMutableMap()
                    detectedDevices.forEach { dev ->
                        val cleanMac = dev.macAddress.replace("-", ":").uppercase()
                        val existing = currentMap[cleanMac]
                        if (existing != null) {
                            currentMap[cleanMac] = existing.copy(
                                ipAddress = dev.ipAddress,
                                originalHostname = dev.originalHostname.ifBlank { existing.originalHostname },
                                isOnline = true
                            )
                        } else {
                            currentMap[cleanMac] = dev
                        }
                    }
                    updated = updated.copy(devices = currentMap.values.toList())
                    onLog("تم العثور على ${detectedDevices.size} جهاز متصل")
                }
            }
        } catch (e: Exception) {
            onLog("خطأ في جلب قائمة الأجهزة: ${e.message}")
        }
        return updated
    }

    private fun fetchHuaweiBlockList(profile: RouterProfile, onLog: (String) -> Unit): RouterProfile {
        var updated = profile
        try {
            val req = Request.Builder()
                .url("http://${profile.routerIP}/api/wlan/multi-macfilter-settings")
                .addHeader("Cookie", sessionCookies[profile.routerIP].orEmpty())
                .addHeader("__RequestVerificationToken", verificationTokens[profile.routerIP].orEmpty())
                .build()

            client.newCall(req).execute().use { res ->
                res.header("__RequestVerificationToken")?.let { verificationTokens[profile.routerIP] = it }
                val xml = res.body?.string().orEmpty()
                val blockedMap = mutableMapOf<String, String>() // MAC -> Hostname
                for (i in 0 until 10) {
                    val mac = extractTag("WifiMacFilterMac$i", xml)
                    val hostName = extractTag("wifihostname$i", xml) ?: ""
                    if (!mac.isNullOrBlank()) {
                        blockedMap[mac.replace("-", ":").uppercase()] = hostName
                    }
                }

                val currentMap = profile.devices.associateBy { it.macAddress.replace("-", ":").uppercase() }.toMutableMap()
                
                // تحديث حالة الأجهزة الموجودة بالفعل
                currentMap.keys.toList().forEach { mac ->
                    val isBlocked = blockedMap.containsKey(mac)
                    currentMap[mac] = currentMap[mac]!!.copy(isBlocked = isBlocked)
                }

                // الحفاظ على الأجهزة المحظورة التي انقطع اتصالها وإضافتها كأجهزة غير متصلة محظورة
                blockedMap.forEach { (mac, hostName) ->
                    if (!currentMap.containsKey(mac)) {
                        currentMap[mac] = NetworkDevice(
                            ipAddress = "",
                            macAddress = mac,
                            originalHostname = hostName,
                            isBlocked = true,
                            isOnline = false
                        )
                    }
                }

                updated = updated.copy(devices = currentMap.values.toList())
                if (blockedMap.isNotEmpty()) {
                    onLog("تمت مزامنة قائمة الحظر: ${blockedMap.size} جهاز محظور")
                }
            }
        } catch (e: Exception) {
            onLog("تنبيه جلب قائمة الحظر: ${e.message}")
        }
        return updated
    }

    private fun getFreshSessionToken(routerIP: String): String? {
        try {
            val req = Request.Builder()
                .url("http://$routerIP/api/webserver/token")
                .addHeader("Cookie", sessionCookies[routerIP].orEmpty())
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val rawToken = extractTag("token", body)
                if (!rawToken.isNullOrBlank()) {
                    val finalToken = if (rawToken.length >= 64) rawToken.substring(32) else rawToken
                    verificationTokens[routerIP] = finalToken
                    return finalToken
                }
            }
        } catch (_: Exception) {}

        return verificationTokens[routerIP]
    }

    suspend fun toggleBlockDevice(profile: RouterProfile, device: NetworkDevice, block: Boolean, onLog: (String) -> Unit): Pair<Boolean, RouterProfile> = withContext(Dispatchers.IO) {
        if (profile.firmware == FirmwareType.HUAWEI_B311) {
            toggleBlockHuawei(profile, device, block, onLog)
        } else {
            toggleBlockDLink(profile, device, block, onLog)
        }
    }

    private fun toggleBlockDLink(profile: RouterProfile, device: NetworkDevice, block: Boolean, onLog: (String) -> Unit): Pair<Boolean, RouterProfile> {
        try {
            val endpoints = if (profile.firmware == FirmwareType.GENERIC_REALTEK) {
                listOf("http://${profile.routerIP}/boaform/admin/formWlanSetup", "http://${profile.routerIP}/form2WlanSetup.cgi")
            } else {
                listOf("http://${profile.routerIP}/form2WlanSetup.cgi", "http://${profile.routerIP}/boaform/admin/formWlanSetup")
            }

            val action = if (block) "add" else "delete"
            val formBody = "action=$action&mac=${urlEncode(device.macAddress)}&filter_mode=deny"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val cookie = sessionCookies[profile.routerIP]
            for (ep in endpoints) {
                try {
                    val reqBuilder = Request.Builder().url(ep).post(formBody)
                    if (!cookie.isNullOrBlank()) {
                        reqBuilder.addHeader("Cookie", cookie)
                    }
                    val ok = client.newCall(reqBuilder.build()).execute().use { res ->
                        res.isSuccessful || res.code in 200..399
                    }
                    if (ok) {
                        val updatedDevs = profile.devices.map {
                            if (it.macAddress.equals(device.macAddress, ignoreCase = true)) it.copy(isBlocked = block) else it
                        }
                        onLog("[نجاح] ${if (block) "تم حظر وفصل" else "تم إلغاء حظر"} الجهاز: ${device.displayName}")
                        return Pair(true, profile.copy(devices = updatedDevs))
                    }
                } catch (_: Exception) {}
            }
            onLog("[خطأ] تعذر إرسال أمر الحظر لراوتر D-Link")
            return Pair(false, profile)
        } catch (e: Exception) {
            onLog("خطأ في أمر حظر D-Link: ${e.message}")
            return Pair(false, profile)
        }
    }

    private fun toggleBlockHuawei(profile: RouterProfile, device: NetworkDevice, block: Boolean, onLog: (String) -> Unit): Pair<Boolean, RouterProfile> {
        try {
            val sessionReady = ensureAuthenticatedSession(profile, onLog)
            if (!sessionReady) {
                onLog("[خطأ] تعذر توثيق الجلسة مع الراوتر قبل إرسال أمر الحظر")
                return Pair(false, profile)
            }

            val token = getFreshSessionToken(profile.routerIP) ?: verificationTokens[profile.routerIP].orEmpty()

            val blockedList = profile.devices.filter {
                !it.macAddress.equals(device.macAddress, ignoreCase = true) && it.isBlocked
            }.toMutableList()
            if (block) blockedList.add(device.copy(isBlocked = true))

            val filterStatus = if (blockedList.isEmpty()) 0 else 2
            val entriesXml = StringBuilder()
            blockedList.take(10).forEachIndexed { i, dev ->
                val mac = escapeXml(dev.macAddress.replace("-", ":").uppercase())
                val name = escapeXml(dev.displayName.replace(" ", "_"))
                entriesXml.append("                    <WifiMacFilterMac$i>$mac</WifiMacFilterMac$i>\n")
                entriesXml.append("                    <wifihostname$i>$name</wifihostname$i>\n")
            }

            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <Ssids>
                        <Ssid>
                            <Index>0</Index>
                            <WifiMacFilterStatus>$filterStatus</WifiMacFilterStatus>
$entriesXml                        </Ssid>
                    </Ssids>
                </request>
            """.trimIndent()

            var success = sendBlockRequest(profile.routerIP, xml, token)

            if (!success) {
                onLog("تجديد كامل لجلسة الراوتر والمحاولة مرة أخرى...")
                sessionCookies.remove(profile.routerIP)
                verificationTokens.remove(profile.routerIP)
                val reAuthed = authenticateHuawei(profile, onLog)
                if (reAuthed) {
                    val newToken = getFreshSessionToken(profile.routerIP) ?: verificationTokens[profile.routerIP].orEmpty()
                    success = sendBlockRequest(profile.routerIP, xml, newToken)
                }
            }

            if (success) {
                val updatedDevs = profile.devices.map {
                    if (it.macAddress.equals(device.macAddress, ignoreCase = true)) it.copy(isBlocked = block) else it
                }
                onLog("[نجاح] ${if (block) "تم حظر وفصل" else "تم إلغاء حظر"}: ${device.displayName}")
                return Pair(true, profile.copy(devices = updatedDevs))
            }
            return Pair(false, profile)
        } catch (e: Exception) {
            onLog("خطأ في أمر الحظر: ${e.message}")
            return Pair(false, profile)
        }
    }

    private fun sendBlockRequest(routerIP: String, xml: String, token: String): Boolean {
        val req = Request.Builder()
            .url("http://$routerIP/api/wlan/multi-macfilter-settings")
            .post(xml.toRequestBody("application/xml".toMediaType()))
            .addHeader("Cookie", sessionCookies[routerIP].orEmpty())
            .addHeader("__RequestVerificationToken", token)
            .addHeader("_ResponseSource", "Broswer")
            .build()

        return client.newCall(req).execute().use { res ->
            res.header("__RequestVerificationToken")?.let { verificationTokens[routerIP] = it }
            val body = res.body?.string().orEmpty()
            body.contains("<response>OK</response>") || (!body.contains("<error>") && res.isSuccessful)
        }
    }

    /**
     * تحديد سرعة الجهاز (Bandwidth Throttling) لراوترات D-Link و Realtek
     */
    suspend fun setThrottle(profile: RouterProfile, device: NetworkDevice, speedKbps: Int?, onLog: (String) -> Unit): RouterProfile = withContext(Dispatchers.IO) {
        val endpoints = if (profile.firmware == FirmwareType.GENERIC_REALTEK) {
            listOf("http://${profile.routerIP}/boaform/admin/formTrafficControl", "http://${profile.routerIP}/tc.cgi")
        } else {
            listOf("http://${profile.routerIP}/tc.cgi", "http://${profile.routerIP}/boaform/admin/formTrafficControl")
        }

        val isThrottling = speedKbps != null && speedKbps > 0
        val action = if (isThrottling) "add" else "del"
        val rateVal = speedKbps ?: 0
        val formBody = "action=$action&mac=${urlEncode(device.macAddress)}&rate=$rateVal"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val cookie = sessionCookies[profile.routerIP]
        var sent = false
        for (ep in endpoints) {
            try {
                val reqBuilder = Request.Builder().url(ep).post(formBody)
                if (!cookie.isNullOrBlank()) {
                    reqBuilder.addHeader("Cookie", cookie)
                }
                sent = client.newCall(reqBuilder.build()).execute().use { res ->
                    res.isSuccessful || res.code in 200..399
                }
                if (sent) break
            } catch (_: Exception) {}
        }

        val updatedDevs = profile.devices.map {
            if (it.macAddress.equals(device.macAddress, ignoreCase = true)) {
                it.copy(
                    isThrottled = isThrottling,
                    currentSpeedKbps = if (isThrottling) rateVal else 0
                )
            } else it
        }

        if (sent) {
            if (isThrottling) {
                onLog("[نجاح] تم تحديد سرعة (${device.displayName}) إلى ${device.speedLabel(rateVal)}")
            } else {
                onLog("[نجاح] تم إلغاء تحديد سرعة (${device.displayName}) واستعادة السرعة الكاملة")
            }
        } else {
            onLog("[تنبيه] تم تحديث سرعة الجهاز محلياً في الإعدادات")
        }

        profile.copy(devices = updatedDevs)
    }

    /**
     * جلب وضع شبكة الجوال الحالي لراوترات Huawei (4G فقط / تلقائي / 3G فقط)
     */
    suspend fun fetchHuaweiNetMode(profile: RouterProfile, onLog: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url("http://${profile.routerIP}/api/net/net-mode")
            val cookie = sessionCookies[profile.routerIP]
            if (!cookie.isNullOrBlank()) {
                reqBuilder.addHeader("Cookie", cookie)
            }
            client.newCall(reqBuilder.build()).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val mode = extractTag("NetworkMode", body)
                if (!mode.isNullOrBlank()) {
                    return@withContext mode
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * تغيير وتثبيت وضع شبكة الجوال لراوترات Huawei HiLink عبر API الرسمي
     * mode: "03" = 4G فقط, "00" = تلقائي, "02" = 3G فقط, "01" = 2G فقط
     */
    suspend fun setHuaweiNetMode(profile: RouterProfile, targetMode: String, onLog: (String) -> Unit): Pair<Boolean, RouterProfile> = withContext(Dispatchers.IO) {
        try {
            val sessionReady = ensureAuthenticatedSession(profile, onLog)
            if (!sessionReady) {
                onLog("[خطأ] تعذر توثيق الجلسة مع الراوتر لتغيير وضع الشبكة")
                return@withContext Pair(false, profile)
            }

            val token = getFreshSessionToken(profile.routerIP) ?: verificationTokens[profile.routerIP].orEmpty()
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <NetworkMode>$targetMode</NetworkMode>
                    <NetworkBand>3FFFFFFF</NetworkBand>
                    <LTEBand>7FFFFFFFFFFFFFFF</LTEBand>
                </request>
            """.trimIndent()

            var success = sendNetModeRequest(profile.routerIP, xml, token)
            if (!success) {
                sessionCookies.remove(profile.routerIP)
                verificationTokens.remove(profile.routerIP)
                if (authenticateHuawei(profile, onLog)) {
                    val newToken = getFreshSessionToken(profile.routerIP) ?: verificationTokens[profile.routerIP].orEmpty()
                    success = sendNetModeRequest(profile.routerIP, xml, newToken)
                }
            }

            if (success) {
                val modeName = when (targetMode) {
                    "03" -> "4G فقط (LTE Only)"
                    "02" -> "3G فقط (WCDMA Only)"
                    "01" -> "2G فقط (GSM Only)"
                    else -> "تلقائي (Auto 4G/3G)"
                }
                onLog("[نجاح] تم تغيير وتثبيت وضع الشبكة في الراوتر: $modeName")
                Pair(true, profile.copy(networkMode = targetMode))
            } else {
                onLog("[خطأ] تعذر تغيير وضع الشبكة في الراوتر")
                Pair(false, profile)
            }
        } catch (e: Exception) {
            onLog("خطأ أثناء تغيير وضع الشبكة: ${e.message}")
            Pair(false, profile)
        }
    }

    private fun sendNetModeRequest(routerIP: String, xml: String, token: String): Boolean {
        try {
            val req = Request.Builder()
                .url("http://$routerIP/api/net/net-mode")
                .post(xml.toRequestBody("application/xml".toMediaType()))
                .addHeader("Cookie", sessionCookies[routerIP].orEmpty())
                .addHeader("__RequestVerificationToken", token)
                .addHeader("_ResponseSource", "Broswer")
                .build()

            return client.newCall(req).execute().use { res ->
                res.header("__RequestVerificationToken")?.let { verificationTokens[routerIP] = it }
                val body = res.body?.string().orEmpty()
                body.contains("<response>OK</response>", ignoreCase = true) || (!body.contains("<error>") && res.isSuccessful)
            }
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * فحص التبديل التلقائي الذكي إلى 3G عند ضعف سرعة التحميل
     */
    private suspend fun checkAutoSwitchTo3G(profile: RouterProfile, onLog: (String) -> Unit) {
        if (!profile.autoSwitchTo3GOnLowSpeed) return
        if (profile.networkMode != "03" && profile.networkMode != "00") return

        if (profile.liveDownloadRateKbps > 0.1 && profile.liveDownloadRateKbps < profile.lowSpeedThresholdKbps) {
            onLog("[تنبيه ذكي] انخفاض سرعة التحميل إلى ${String.format(Locale.US, "%.1f", profile.liveDownloadRateKbps)} KB/s (أقل من الحد ${profile.lowSpeedThresholdKbps.toInt()} KB/s)")
            onLog("جاري التبديل التلقائي لراوتر Huawei إلى وضع 3G لتحسين جودة الاتصال...")
            setHuaweiNetMode(profile, "02", onLog)
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
        val out = ByteArray(a.size)
        for (i in a.indices) {
            out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return out
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

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun extractTag(tag: String, xml: String): String? {
        val pattern = "<$tag>([\\s\\S]*?)</$tag>".toRegex()
        return pattern.find(xml)?.groups?.get(1)?.value?.trim()
    }

    private fun parseHosts(xml: String): List<NetworkDevice> {
        val results = mutableListOf<NetworkDevice>()
        val hostPattern = "<Host>([\\s\\S]*?)</Host>".toRegex()
        hostPattern.findAll(xml).forEach { match ->
            val content = match.groups[1]?.value.orEmpty()
            val mac = extractTag("MacAddress", content)?.replace("-", ":")?.uppercase() ?: ""
            if (mac.isNotBlank()) {
                val ip = extractTag("IpAddress", content) ?: "192.168.1.50"
                val name = extractTag("HostName", content) ?: ""
                results.add(
                    NetworkDevice(
                        ipAddress = ip,
                        macAddress = mac,
                        originalHostname = name,
                        isOnline = true
                    )
                )
            }
        }
        return results
    }

    private fun extractWANBytesFromDLink(html: String): Long? {
        val pattern = """\b(\d{7,16})\b""".toRegex()
        val matches = pattern.findAll(html)
        var maxBytes = 0L
        for (m in matches) {
            val v = m.groupValues[1].toLongOrNull() ?: continue
            if (v in 1_000_000L..9_000_000_000_000_000L && v > maxBytes) {
                maxBytes = v
            }
        }
        return if (maxBytes > 0L) maxBytes else null
    }

    private fun parseDLinkClients(html: String): List<NetworkDevice> {
        val list = mutableListOf<NetworkDevice>()
        val seen = mutableSetOf<String>()
        val macRegex = """([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})""".toRegex()
        val ipRegex = """\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""".toRegex()

        val trPattern = """<tr[^>]*>([\s\S]*?)</tr>""".toRegex(RegexOption.IGNORE_CASE)
        trPattern.findAll(html).forEach { match ->
            val row = match.groupValues[1]
            val macMatch = macRegex.find(row)
            val ipMatch = ipRegex.find(row)
            if (macMatch != null) {
                val cleanMac = macMatch.value.replace("-", ":").uppercase()
                if (cleanMac !in seen && cleanMac != "00:00:00:00:00:00" && cleanMac != "FF:FF:FF:FF:FF:FF") {
                    seen.add(cleanMac)
                    val ip = ipMatch?.value ?: "192.168.1.50"
                    val cleanText = row.replace("""<[^>]*>""".toRegex(), " ")
                    val parts = cleanText.split("""\s+""".toRegex()).filter { it.isNotBlank() && it != cleanMac && it != ip }
                    val hostname = parts.firstOrNull { it.length in 2..30 && !it.contains(":") } ?: ""
                    list.add(
                        NetworkDevice(
                            ipAddress = ip,
                            macAddress = cleanMac,
                            originalHostname = hostname,
                            isOnline = true
                        )
                    )
                }
            }
        }
        return list
    }

    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
