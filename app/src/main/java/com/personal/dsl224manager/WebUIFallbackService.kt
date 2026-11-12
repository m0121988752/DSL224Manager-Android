package com.personal.dsl224manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * محرك الكشط والتحكم البديل عبر متصفح الويب (Unified Android WebUI Fallback Engine)
 * مطابق 100% لخوارزمية iOS في تحليل الـ DOM واستخراج العدادات وضغط الأزرار
 */
class WebUIFallbackService(private val context: Context) {

    private var headlessWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getOrCreateWebView(): WebView {
        if (headlessWebView == null) {
            headlessWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                }
            }
        }
        return headlessWebView!!
    }

    suspend fun autoLoginAndScrape(profile: RouterProfile, timeoutMs: Long = 12000L): WebUIScrapeResult = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        val baseURL = "http://${profile.routerIP}"
        val targetPath = profile.webUIPath.trim().removePrefix("/")
        val fullURL = if (targetPath.isEmpty()) baseURL else "$baseURL/$targetPath"

        val loaded = loadUrlAsync(wv, fullURL, timeoutMs)
        if (!loaded) {
            return@withContext WebUIScrapeResult(
                success = false,
                currentURL = fullURL,
                message = "تعذر تحميل واجهة الراوتر (انتهت المهلة أو الجهاز غير متاح)"
            )
        }

        // محاولة تسجيل الدخول التلقائي إذا كان النموذج متاحاً
        attemptAutoLogin(profile.username, profile.password, wv)

        // كشط محتويات الصفحة بعد التحميل
        scrapeCurrentPage(wv)
    }

    private suspend fun loadUrlAsync(wv: WebView, url: String, timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true && cont.isActive) {
                        cont.resume(false)
                    }
                }
            }
            wv.loadUrl(url)
        }
    } ?: false

    suspend fun attemptAutoLogin(username: String, pass: String, customWv: WebView? = null): Boolean = withContext(Dispatchers.Main) {
        val wv = customWv ?: getOrCreateWebView()
        val escapedUser = username.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedPass = pass.replace("\\", "\\\\").replace("\"", "\\\"")

        val js = """
        (function() {
            var userSelectors = [
                '#f_username',
                'input[name="f_username"]',
                'input[name*="user" i]',
                'input[name*="login" i]',
                'input[id*="user" i]',
                '#username',
                '#txtUser',
                'input[type="text"]'
            ];
            var passSelectors = [
                '#f_password',
                'input[name="f_password"]',
                'input[type="password"]',
                'input[name*="pass" i]',
                'input[name*="pwd" i]',
                'input[id*="pass" i]',
                '#password',
                '#txtPwd'
            ];

            var userEl = null;
            for (var i = 0; i < userSelectors.length; i++) {
                var el = document.querySelector(userSelectors[i]);
                if (el) { userEl = el; break; }
            }

            var passEl = null;
            for (var j = 0; j < passSelectors.length; j++) {
                var p = document.querySelector(passSelectors[j]);
                if (p) { passEl = p; break; }
            }

            if (!userEl && !passEl) {
                return JSON.stringify({ attempted: false });
            }

            var userToFill = "$escapedUser".length > 0 ? "$escapedUser" : "admin";
            if (userEl) {
                userEl.value = userToFill;
                userEl.dispatchEvent(new Event('input', { bubbles: true }));
                userEl.dispatchEvent(new Event('change', { bubbles: true }));
            }

            if (passEl && "$escapedPass".length > 0) {
                passEl.value = "$escapedPass";
                passEl.dispatchEvent(new Event('input', { bubbles: true }));
                passEl.dispatchEvent(new Event('change', { bubbles: true }));
            }

            var submitSelectors = [
                '#f_submit_login',
                '#f_submit',
                'input[type="submit"]',
                'button[type="submit"]',
                '#login_btn',
                '#loginBtn',
                'input[value="OK"]',
                'input[value*="login" i]'
            ];

            var submitEl = null;
            for (var k = 0; k < submitSelectors.length; k++) {
                var b = document.querySelector(submitSelectors[k]);
                if (b) { submitEl = b; break; }
            }

            if (submitEl) {
                submitEl.click();
                return JSON.stringify({ attempted: true, action: 'clicked_submit' });
            }

            var form = (passEl ? passEl.closest('form') : null) || document.querySelector('form');
            if (form) {
                form.submit();
                return JSON.stringify({ attempted: true, action: 'form_submitted' });
            }

            return JSON.stringify({ attempted: true, action: 'fields_populated' });
        })();
        """.trimIndent()

        val resultJson = evaluateJsAsync(wv, js)
        try {
            val obj = JSONObject(resultJson)
            obj.optBoolean("attempted", false)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun scrapeCurrentPage(customWv: WebView? = null): WebUIScrapeResult = withContext(Dispatchers.Main) {
        val wv = customWv ?: getOrCreateWebView()
        val js = """
        (function() {
            var bodyText = document.body ? document.body.innerText : '';
            var title = document.title || '';
            var currentUrl = window.location.href;

            // 1. استخراج العداد التراكمي
            var foundBytes = null;
            var huaweiUsage = bodyText.match(/محلي الاستخدام\s*([0-9\.]+)\s*(GB|MB)/i) || bodyText.match(/([0-9\.]+)\s*GB/i);
            if (huaweiUsage) {
                var numH = parseFloat(huaweiUsage[1]);
                var unitH = huaweiUsage[2].toUpperCase();
                if (unitH.indexOf('GB') !== -1) {
                    foundBytes = Math.round(numH * 1024 * 1024 * 1024);
                } else {
                    foundBytes = Math.round(numH * 1024 * 1024);
                }
            }

            var digitMatches = bodyText.match(/\b(\d{7,16})\b/g);
            if (!foundBytes && digitMatches && digitMatches.length > 0) {
                var maxVal = 0;
                for (var i = 0; i < digitMatches.length; i++) {
                    var val = parseInt(digitMatches[i], 10);
                    if (val > 1000000 && val > maxVal && val < 9000000000000000) {
                        maxVal = val;
                    }
                }
                if (maxVal > 0) { foundBytes = maxVal; }
            }

            // 2. السرعة اللحظية
            var downRate = null;
            var upRate = null;
            var speedMatch = bodyText.match(/([0-9]+(?:\.[0-9]+)?)\s*(KB\/s|Kbps|MB\/s|Mbps)/ig);
            if (speedMatch && speedMatch.length > 0) {
                var firstVal = parseFloat(speedMatch[0]);
                downRate = speedMatch[0].toLowerCase().indexOf('mb') !== -1 ? firstVal * 1024 : firstVal;
            }

            // 3. المشغل
            var carrier = null;
            var carriers = ['Orange EG', 'Orange', 'Vodafone', 'Etisalat', 'WE', 'Telecom Egypt', 'Zain', 'STC', 'Mobily'];
            for (var c = 0; c < carriers.length; c++) {
                if (bodyText.indexOf(carriers[c]) !== -1) {
                    carrier = carriers[c];
                    break;
                }
            }

            // 4. الأجهزة
            var devices = [];
            var seenMacs = {};
            var macRegex = /([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})/;
            var ipRegex = /\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b/;

            var containers = document.querySelectorAll('tr, .device-item, .host-item, li, div.devicelist, div');
            containers.forEach(function(el) {
                if (el.children.length > 15) return;
                var rowText = el.innerText || '';
                var macMatch = rowText.match(macRegex);
                if (macMatch) {
                    var mac = macMatch[0].toUpperCase().replace(/-/g, ':');
                    if (!seenMacs[mac]) {
                        seenMacs[mac] = true;
                        var ipMatch = rowText.match(ipRegex);
                        var ip = ipMatch ? ipMatch[0] : '192.168.1.100';

                        var lines = rowText.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
                        var hostname = '';
                        for (var li = 0; li < lines.length; li++) {
                            var line = lines[li];
                            if (!line.match(macRegex) && !line.match(ipRegex) && line.indexOf('MAC') === -1 && line.indexOf('IPv') === -1 && line.length < 35 && line.length > 1) {
                                hostname = line;
                                break;
                            }
                        }
                        devices.push({ macAddress: mac, ipAddress: ip, hostName: hostname });
                    }
                }
            });

            // 5. الأزرار
            var buttons = [];
            var btnElements = document.querySelectorAll('button, input[type="button"], input[type="submit"], [role="button"], a.btn');
            btnElements.forEach(function(el, index) {
                var txt = (el.innerText || el.textContent || el.value || '').trim();
                var id = el.id || ('btn_' + index);
                var name = el.getAttribute('name') || '';
                var val = (el.value || '').trim();
                var selector = el.id ? ('#' + CSS.escape(el.id)) : (name ? (el.tagName.toLowerCase() + '[name="' + CSS.escape(name) + '"]') : '');

                var fullSearch = (txt + ' ' + id + ' ' + name + ' ' + val).toLowerCase();
                var isReboot = fullSearch.indexOf('reboot') !== -1 || fullSearch.indexOf('restart') !== -1 || fullSearch.indexOf('إعادة') !== -1;
                var isLogin = fullSearch.indexOf('login') !== -1 || fullSearch.indexOf('signin') !== -1 || fullSearch.indexOf('دخول') !== -1;
                var isApply = fullSearch.indexOf('apply') !== -1 || fullSearch.indexOf('save') !== -1 || fullSearch.indexOf('حفظ') !== -1 || fullSearch.indexOf('تطبيق') !== -1;

                if (txt || id || name || val) {
                    buttons.push({
                        id: id,
                        selector: selector,
                        tag: el.tagName.toLowerCase(),
                        text: txt.substring(0, 50),
                        value: val.substring(0, 50),
                        isReboot: isReboot,
                        isLogin: isLogin,
                        isApply: isApply
                    });
                }
            });

            return JSON.stringify({
                success: true,
                wanBytes: foundBytes,
                liveDownloadKbps: downRate,
                liveUploadKbps: upRate,
                carrierName: carrier,
                devices: devices,
                buttons: buttons,
                pageTitle: title,
                currentURL: currentUrl,
                message: 'تم الكشط بنجاح'
            });
        })();
        """.trimIndent()

        val jsonStr = evaluateJsAsync(wv, js)
        parseScrapeJson(jsonStr, wv.url.orEmpty(), wv.title.orEmpty())
    }

    suspend fun pressButton(
        selector: String? = null,
        text: String? = null,
        semanticAction: SemanticButtonAction? = null,
        customWv: WebView? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.Main) {
        val wv = customWv ?: getOrCreateWebView()
        val selArg = (selector ?: "").replace("\"", "\\\"")
        val txtArg = (text ?: "").replace("\"", "\\\"")
        val actionStr = semanticAction?.name?.lowercase().orEmpty()

        val js = """
        (function() {
            window.confirm = function() { return true; };
            window.alert = function() { return true; };

            var targetSelector = "$selArg";
            var targetText = "$txtArg".toLowerCase().trim();
            var action = "$actionStr";

            var btn = null;
            if (targetSelector.length > 0) {
                try { btn = document.querySelector(targetSelector); } catch(e){}
            }

            if (!btn && action.length > 0) {
                if (action.indexOf('reboot') !== -1) {
                    btn = document.querySelector('#reboot_apply_button, #menu_reboot, #btn_reboot, #reboot, input[value*="Reboot" i]');
                }
                if (!btn) {
                    var all = document.querySelectorAll('button, input[type="button"], input[type="submit"], [role="button"], a');
                    for (var i = 0; i < all.length; i++) {
                        var t = (all[i].innerText || all[i].textContent || all[i].value || all[i].id || '').toLowerCase();
                        if (action.indexOf('reboot') !== -1 && (t.indexOf('reboot') !== -1 || t.indexOf('restart') !== -1 || t.indexOf('إعادة') !== -1)) {
                            btn = all[i]; break;
                        }
                    }
                }
            }

            if (btn) {
                btn.scrollIntoView();
                btn.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
                btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                btn.click();
                var label = (btn.innerText || btn.value || btn.id || 'زر').trim();
                return JSON.stringify({ success: true, message: 'تم الضغط بنجاح على: ' + label });
            }

            return JSON.stringify({ success: false, message: 'لم يتم العثور على زر مطابق' });
        })();
        """.trimIndent()

        val res = evaluateJsAsync(wv, js)
        try {
            val obj = JSONObject(res)
            Pair(obj.optBoolean("success", false), obj.optString("message", "تم الإرسال"))
        } catch (e: Exception) {
            Pair(false, "خطأ في استجابة المتصفح: ${e.message}")
        }
    }

    private suspend fun evaluateJsAsync(wv: WebView, script: String): String = suspendCancellableCoroutine { cont ->
        wv.evaluateJavascript(script) { result ->
            val unquoted = if (result != null && result.startsWith("\"") && result.endsWith("\"") && result.length >= 2) {
                try {
                    JSONObject("\"str\":$result").getString("str")
                } catch (e: Exception) {
                    result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                }
            } else {
                result.orEmpty()
            }
            cont.resume(unquoted)
        }
    }

    private fun parseScrapeJson(jsonStr: String, fallbackUrl: String, fallbackTitle: String): WebUIScrapeResult {
        return try {
            val obj = JSONObject(jsonStr)
            val wanBytes = if (obj.has("wanBytes") && !obj.isNull("wanBytes")) obj.getLong("wanBytes") else null
            val downRate = if (obj.has("liveDownloadKbps") && !obj.isNull("liveDownloadKbps")) obj.getDouble("liveDownloadKbps") else null
            val upRate = if (obj.has("liveUploadKbps") && !obj.isNull("liveUploadKbps")) obj.getDouble("liveUploadKbps") else null
            val carrier = obj.optString("carrierName").takeIf { it.isNotBlank() }

            val devicesList = mutableListOf<WebUIScrapedDevice>()
            val devArr = obj.optJSONArray("devices") ?: JSONArray()
            for (i in 0 until devArr.length()) {
                val d = devArr.getJSONObject(i)
                devicesList.add(
                    WebUIScrapedDevice(
                        macAddress = d.getString("macAddress"),
                        ipAddress = d.optString("ipAddress", "192.168.1.100"),
                        hostName = d.optString("hostName", "")
                    )
                )
            }

            val buttonsList = mutableListOf<WebUIButtonInfo>()
            val btnArr = obj.optJSONArray("buttons") ?: JSONArray()
            for (j in 0 until btnArr.length()) {
                val b = btnArr.getJSONObject(j)
                buttonsList.add(
                    WebUIButtonInfo(
                        id = b.optString("id"),
                        selector = b.optString("selector"),
                        tag = b.optString("tag"),
                        text = b.optString("text"),
                        value = b.optString("value"),
                        isReboot = b.optBoolean("isReboot"),
                        isLogin = b.optBoolean("isLogin"),
                        isApply = b.optBoolean("isApply")
                    )
                )
            }

            WebUIScrapeResult(
                success = obj.optBoolean("success", true),
                wanBytes = wanBytes,
                liveDownloadKbps = downRate,
                liveUploadKbps = upRate,
                carrierName = carrier,
                devices = devicesList,
                buttons = buttonsList,
                pageTitle = obj.optString("pageTitle", fallbackTitle),
                currentURL = obj.optString("currentURL", fallbackUrl),
                message = obj.optString("message", "تم بنجاح")
            )
        } catch (e: Exception) {
            WebUIScrapeResult(
                success = false,
                currentURL = fallbackUrl,
                pageTitle = fallbackTitle,
                message = "خطأ في تحليل استجابة الكشط: ${e.message}"
            )
        }
    }
}
