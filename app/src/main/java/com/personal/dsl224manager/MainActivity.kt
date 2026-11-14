package com.personal.dsl224manager

import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val repo = RouterRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF0A84FF),
                        onPrimary = Color.White,
                        secondary = Color(0xFF30D158),
                        tertiary = Color(0xFFFF9F0A),
                        error = Color(0xFFFF453A),
                        background = Color(0xFF000000),
                        surface = Color(0xFF1C1C1E),
                        surfaceVariant = Color(0xFF2C2C2E),
                        onSurface = Color.White,
                        onSurfaceVariant = Color(0xFF8E8E93)
                    )
                ) {
                    MainScreen(repo)
                }
            }
        }
    }
}

enum class MainTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("الشبكة", Icons.Default.Router),
    ROUTERS("الراوترات", Icons.Default.Dns),
    STATS("الإحصائيات", Icons.Default.Analytics),
    SETTINGS("الإعدادات", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repo: RouterRepository) {
    val context = LocalContext.current
    val storage = remember { RouterStorage(context) }
    val webUI = remember { WebUIFallbackService(context) }
    val scope = rememberCoroutineScope()

    // تحميل الراوترات المحفوظة محلياً (SharedPreferences)
    val initialData = remember { storage.loadRouters() }
    var routers by remember { mutableStateOf(initialData.first) }
    var activeRouterIndex by remember { mutableStateOf(initialData.second) }
    val activeRouter = routers.getOrElse(activeRouterIndex) { routers.firstOrNull() ?: RouterProfile() }

    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var logs by remember { mutableStateOf(listOf("مرحباً بك في DSL224Manager", "تم استرجاع الإعدادات المحفوظة بنجاح")) }
    var isLoading by remember { mutableStateOf(false) }

    // حوار إعادة تسمية الجهاز
    var editingDevice by remember { mutableStateOf<NetworkDevice?>(null) }
    var newCustomName by remember { mutableStateOf("") }

    // حوار متصفح أزرار WebUI
    var showWebUIDialog by remember { mutableStateOf(false) }
    var initialPickerMode by remember { mutableStateOf(false) }
    var lastScrapeResult by remember { mutableStateOf<WebUIScrapeResult?>(null) }

    // حوار التخصيص المرئي للكشط بدون كود (No-Code Scraper Customization)
    var showNoCodeDialog by remember { mutableStateOf(false) }

    fun log(msg: String) {
        logs = listOf(msg) + logs.take(60)
    }

    fun updateActiveRouter(updated: RouterProfile) {
        routers = routers.toMutableList().also {
            if (activeRouterIndex in it.indices) {
                it[activeRouterIndex] = updated
            }
        }
        storage.saveRouters(routers, activeRouterIndex)
    }

    fun refreshActiveRouter() {
        scope.launch {
            isLoading = true
            log("جاري تحديث بيانات الراوتر (${activeRouter.name})...")
            var updated = repo.refreshHuawei(activeRouter) { log(it) }

            // تفعيل WebUI Fallback التلقائي إذا تعذرت القراءة أو كان الاستهلاك صفراً
            if (updated.accumulatedBytes == 0L && updated.enableWebUIFallback) {
                log("تفعيل الكشط التلقائي عبر متصفح WebUI البديل...")
                val res = webUI.autoLoginAndScrape(updated)
                lastScrapeResult = res
                if (res.success) {
                    updated = repo.applyScrapeResult(updated, res) { log(it) }
                    log("✅ تم كشط ومزامنة بيانات الراوتر من الويب بنجاح")
                }
            }
            updateActiveRouter(updated)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeRouter.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${activeRouter.routerIP} • ${activeRouter.firmware.displayName.substringBefore(" ")}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // زر فتح متصفح الراوتر والالتقاط البصري المباشر
                    IconButton(
                        onClick = {
                            initialPickerMode = true
                            showWebUIDialog = true
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Adjust, contentDescription = "التقاط عنصر بصري", tint = Color(0xFF30D158))
                    }

                    // زر التخصيص بدون كود (No-Code Scraper Customizer)
                    IconButton(
                        onClick = { showNoCodeDialog = true },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "تخصيص الكشط No-Code", tint = Color(0xFF30D158))
                    }

                    // زر فحص وكشط أزرار الويب
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                log("جاري فحص وكشط واجهة الويب والأزرار المتاحة...")
                                val res = webUI.autoLoginAndScrape(activeRouter)
                                lastScrapeResult = res
                                if (res.success) {
                                    val updated = repo.applyScrapeResult(activeRouter, res) { log(it) }
                                    updateActiveRouter(updated)
                                    showWebUIDialog = true
                                    log("✅ تم العثور على ${res.buttons.size} زر تفاعلي في الصفحة")
                                } else {
                                    log("❌ فشل فحص الويب: ${res.message}")
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "WebUI", tint = Color(0xFFFF9F0A))
                    }

                    // زر إعادة تشغيل الراوتر بلمسة واحدة
                    IconButton(
                        onClick = {
                            scope.launch {
                                log("جاري إرسال أمر إعادة التشغيل (Reboot) للراوتر...")
                                val (ok, msg) = webUI.pressButton(
                                    selector = activeRouter.customRebootButtonSelector.takeIf { it.isNotBlank() },
                                    semanticAction = SemanticButtonAction.REBOOT
                                )
                                log(if (ok) "✅ $msg" else "❌ $msg")
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "إعادة تشغيل", tint = MaterialTheme.colorScheme.error)
                    }

                    // زر التحديث اللحظي
                    IconButton(
                        onClick = { refreshActiveRouter() },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                MainTab.DASHBOARD -> DashboardTab(
                    router = activeRouter,
                    isLoading = isLoading,
                    onToggleBlock = { dev, block ->
                        scope.launch {
                            // 1. تحديث الحالة فورياً في الواجهة (Optimistic UI Update) لسرعة الاستجابة
                            val updatedDevs = activeRouter.devices.map {
                                if (it.macAddress.equals(dev.macAddress, ignoreCase = true)) it.copy(isBlocked = block) else it
                            }
                            updateActiveRouter(activeRouter.copy(devices = updatedDevs))

                            // 2. إرسال أمر الحظر للراوتر
                            val ok = repo.toggleBlockDevice(activeRouter, dev, block) { log(it) }
                            if (!ok) {
                                // التراجع عن الحالة إذا فشل الأمر في الراوتر
                                val revertedDevs = activeRouter.devices.map {
                                    if (it.macAddress.equals(dev.macAddress, ignoreCase = true)) it.copy(isBlocked = !block) else it
                                }
                                updateActiveRouter(activeRouter.copy(devices = revertedDevs))
                            }
                        }
                    },
                    onRenameDevice = { dev ->
                        editingDevice = dev
                        newCustomName = dev.displayName
                    }
                )
                MainTab.ROUTERS -> RoutersTab(
                    routers = routers,
                    activeIndex = activeRouterIndex,
                    onSelect = {
                        activeRouterIndex = it
                        storage.saveRouters(routers, it)
                    },
                    onAddRouter = {
                        val newR = RouterProfile(name = "راوتر جديد ${routers.size + 1}")
                        routers = routers + newR
                        activeRouterIndex = routers.lastIndex
                        storage.saveRouters(routers, activeRouterIndex)
                        log("تمت إضافة راوتر جديد وحفظه")
                    },
                    onDeleteRouter = { idx ->
                        if (routers.size > 1) {
                            routers = routers.filterIndexed { i, _ -> i != idx }
                            if (activeRouterIndex >= routers.size) activeRouterIndex = routers.lastIndex
                            storage.saveRouters(routers, activeRouterIndex)
                            log("تم حذف الراوتر")
                        }
                    }
                )
                MainTab.STATS -> StatsAndLogsTab(router = activeRouter, logs = logs, onClearLogs = { logs = emptyList() })
                MainTab.SETTINGS -> SettingsTab(
                    router = activeRouter,
                    onSave = { updated ->
                        updateActiveRouter(updated)
                        log("تم حفظ بيانات الراوتر بنجاح في الهاتف")
                    },
                    onOpenWebUI = {
                        initialPickerMode = false
                        showWebUIDialog = true
                    },
                    onOpenVisualPicker = {
                        initialPickerMode = true
                        showWebUIDialog = true
                    },
                    onOpenNoCode = { showNoCodeDialog = true }
                )
            }
        }
    }

    // حوار إعادة تسمية الجهاز
    if (editingDevice != null) {
        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = { Text("إعادة تسمية الجهاز", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الاسم الأصلي: ${editingDevice?.originalHostname ?: "غير معروف"}", fontSize = 12.sp, color = Color.Gray)
                    Text("MAC: ${editingDevice?.macAddress}", fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = newCustomName,
                        onValueChange = { newCustomName = it },
                        label = { Text("الاسم المخصص") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingDevice?.let { dev ->
                            val updatedDevs = activeRouter.devices.map {
                                if (it.macAddress == dev.macAddress) it.copy(customName = newCustomName.trim()) else it
                            }
                            updateActiveRouter(activeRouter.copy(devices = updatedDevs))
                            log("تم تغيير اسم الجهاز إلى: $newCustomName")
                        }
                        editingDevice = null
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // حوار متصفح الويب المباشر والالتقاط البصري لأي راوتر بدون كود
    if (showWebUIDialog) {
        RouterWebUIDialog(
            router = activeRouter,
            webUI = webUI,
            initialPickerMode = initialPickerMode,
            onDismiss = { showWebUIDialog = false },
            onRouterUpdated = { updated ->
                updateActiveRouter(updated)
                log("تم تحديث إعدادات راوتر ${updated.name} بدون كود بنجاح")
            }
        )
    }

    // حوار تخصيص الكشط المرئي بدون كود (No-Code Scraper Builder UI)
    if (showNoCodeDialog) {
        NoCodeScraperDialog(
            router = activeRouter,
            onDismiss = { showNoCodeDialog = false },
            onSave = { updated ->
                updateActiveRouter(updated)
                showNoCodeDialog = false
                log("✅ تم حفظ إعدادات الكشط المخصصة (No-Code Config)")
            },
            onOpenVisualPicker = {
                showNoCodeDialog = false
                initialPickerMode = true
                showWebUIDialog = true
            },
            onTestScrape = { testProfile ->
                scope.launch {
                    log("جاري اختبار محددات الكشط المخصصة...")
                    val res = webUI.scrapeCurrentPage(testProfile)
                    lastScrapeResult = res
                    if (res.wanBytes != null && res.wanBytes > 0) {
                        val gb = res.wanBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        log("✅ نجح الاختبار! تم استخراج: ${String.format("%.2f", gb)} GB")
                    } else {
                        log("⚠️ تم فحص الصفحة ولكن لم يتم التقاط رقم بايتات مناسب بالمحدد الحالي")
                    }
                }
            }
        )
    }
}

// ==================== حوار تخصيص الكشط بدون كود (No-Code Scraper Dialog) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoCodeScraperDialog(
    router: RouterProfile,
    onDismiss: () -> Unit,
    onSave: (RouterProfile) -> Unit,
    onOpenVisualPicker: () -> Unit,
    onTestScrape: (RouterProfile) -> Unit
) {
    var usageSelector by remember { mutableStateOf(router.customUsageSelector) }
    var usageRegex by remember { mutableStateOf(router.customUsageRegex) }
    var usageUnit by remember { mutableStateOf(router.customUsageUnit) }
    var deviceTableSelector by remember { mutableStateOf(router.customDeviceTableSelector) }
    var rebootBtnSelector by remember { mutableStateOf(router.customRebootButtonSelector) }
    var usernameSelector by remember { mutableStateOf(router.customUsernameSelector) }
    var passwordSelector by remember { mutableStateOf(router.customPasswordSelector) }
    var loginBtnSelector by remember { mutableStateOf(router.customLoginButtonSelector) }
    var webPath by remember { mutableStateOf(router.webUIPath) }

    // قوالب سريعة جاهزة بدون كود (Quick Presets)
    val presets = listOf(
        Triple("Huawei Flybox (B311)", "#devicemanagement_page, div.access_switch", "#reboot_apply_button"),
        Triple("D-Link DSL-224 (ME_1.10)", "table.table_traffic, tr", "input[name='reboot']"),
        Triple("Realtek Boaform Generic", "form#wlan, tr", "#btn_reboot")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF30D158))
                Spacer(Modifier.width(8.dp))
                Text("مُخصّص الكشط المرئي (No-Code)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // زر الالتقاط البصري المباشر بالنقر
                item {
                    Button(
                        onClick = onOpenVisualPicker,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Adjust, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("فتح المتصفح والتقاط العناصر بالنقر 🎯", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                item {
                    Text(
                        "أو قم بتخصيص المحددات يدوياً إذا كنت تفضل ذلك لأي نوع راوتر:",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // قوالب جاهزة بلمسة واحدة
                item {
                    Text("قوالب سريعة جاهزة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (presetName, devSel, rebSel) ->
                            SuggestionChip(
                                onClick = {
                                    deviceTableSelector = devSel
                                    rebootBtnSelector = rebSel
                                },
                                label = { Text(presetName.substringBefore(" "), fontSize = 10.sp) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = webPath,
                        onValueChange = { webPath = it },
                        label = { Text("مسار الصفحة الهدف (URL Path)") },
                        placeholder = { Text("مثال: home/index.html أو tc.html") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = usageSelector,
                        onValueChange = { usageSelector = it },
                        label = { Text("محدد عداد الاستهلاك (CSS Selector)") },
                        placeholder = { Text("مثال: #traffic_val أو span.bytes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = usageRegex,
                        onValueChange = { usageRegex = it },
                        label = { Text("نمط الاستخراج (Regex Match)") },
                        placeholder = { Text("مثال: ([0-9\\.]+)\\s*GB") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // اختيار وحدة القراءة
                item {
                    Text("وحدة القراءة:", fontSize = 11.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Auto", "GB", "MB", "Bytes").forEach { unit ->
                            FilterChip(
                                selected = usageUnit == unit,
                                onClick = { usageUnit = unit },
                                label = { Text(unit, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = deviceTableSelector,
                        onValueChange = { deviceTableSelector = it },
                        label = { Text("محدد جدول الأجهزة (Devices Table)") },
                        placeholder = { Text("مثال: #dev_table_content أو tr.device") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = rebootBtnSelector,
                        onValueChange = { rebootBtnSelector = it },
                        label = { Text("محدد زر إعادة التشغيل (Reboot Selector)") },
                        placeholder = { Text("مثال: #reboot_apply_button") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = usernameSelector,
                        onValueChange = { usernameSelector = it },
                        label = { Text("محدد حقل اسم المستخدم (Username Selector)") },
                        placeholder = { Text("مثال: #username أو input[name='user']") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = passwordSelector,
                        onValueChange = { passwordSelector = it },
                        label = { Text("محدد حقل كلمة المرور (Password Selector)") },
                        placeholder = { Text("مثال: #password أو input[type='password']") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = loginBtnSelector,
                        onValueChange = { loginBtnSelector = it },
                        label = { Text("محدد زر تسجيل الدخول (Login Selector)") },
                        placeholder = { Text("مثال: #login_btn أو button[type='submit']") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Button(
                        onClick = {
                            val testProfile = router.copy(
                                webUIPath = webPath.trim(),
                                customUsageSelector = usageSelector.trim(),
                                customUsageRegex = usageRegex.trim(),
                                customUsageUnit = usageUnit,
                                customDeviceTableSelector = deviceTableSelector.trim(),
                                customRebootButtonSelector = rebootBtnSelector.trim(),
                                customUsernameSelector = usernameSelector.trim(),
                                customPasswordSelector = passwordSelector.trim(),
                                customLoginButtonSelector = loginBtnSelector.trim()
                            )
                            onTestScrape(testProfile)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF30D158))
                        Spacer(Modifier.width(6.dp))
                        Text("تجربة واختبار الكشط الآن", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = router.copy(
                        webUIPath = webPath.trim(),
                        customUsageSelector = usageSelector.trim(),
                        customUsageRegex = usageRegex.trim(),
                        customUsageUnit = usageUnit,
                        customDeviceTableSelector = deviceTableSelector.trim(),
                        customRebootButtonSelector = rebootBtnSelector.trim(),
                        customUsernameSelector = usernameSelector.trim(),
                        customPasswordSelector = passwordSelector.trim(),
                        customLoginButtonSelector = loginBtnSelector.trim()
                    )
                    onSave(updated)
                }
            ) {
                Text("حفظ الإعدادات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ==================== تبويب الشبكة والأجهزة (Dashboard) ====================
@Composable
fun DashboardTab(
    router: RouterProfile,
    isLoading: Boolean,
    onToggleBlock: (NetworkDevice, Boolean) -> Unit,
    onRenameDevice: (NetworkDevice) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(0) } // 0: الكل, 1: متصل, 2: محظور

    val filteredDevices = remember(router.devices, searchQuery, filterMode) {
        router.devices.filter { dev ->
            val matchesSearch = searchQuery.isBlank() ||
                    dev.displayName.contains(searchQuery, ignoreCase = true) ||
                    dev.ipAddress.contains(searchQuery) ||
                    dev.macAddress.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterMode) {
                1 -> dev.isOnline && !dev.isBlocked
                2 -> dev.isBlocked
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // كارت استهلاك الباقة
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    if (router.carrierName.isNotBlank()) "4G • ${router.carrierName}" else "بيانات الباقة",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text("الاستهلاك الشهري التراكمي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // نسبة الاستهلاك
                        Text(
                            "${String.format("%.1f", router.usedPercentage)}%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = if (router.usedPercentage > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // الأرقام الكبيرة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("المستهلك", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format("%.2f", router.usedGB)} GB",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("المتبقي من ${router.totalPlanGB.toInt()} GB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format("%.2f", router.remainingGB)} GB",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // شريط التقدم
                    LinearProgressIndicator(
                        progress = (router.usedPercentage / 100.0).toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (router.usedPercentage > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // السرعات اللحظية
                    if (router.liveDownloadRateKbps > 0 || router.liveUploadRateKbps > 0) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تحميل: ${String.format("%.1f", router.liveDownloadRateKbps)} KB/s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("رفع: ${String.format("%.1f", router.liveUploadRateKbps)} KB/s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // شريط البحث
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث عن جهاز بالاسم أو IP أو MAC...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // شرائح الفلترة (Filter Chips)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == 0,
                    onClick = { filterMode = 0 },
                    label = { Text("الكل (${router.devices.size})") }
                )
                FilterChip(
                    selected = filterMode == 1,
                    onClick = { filterMode = 1 },
                    label = { Text("المتصلة (${router.devices.count { it.isOnline && !it.isBlocked }})") }
                )
                FilterChip(
                    selected = filterMode == 2,
                    onClick = { filterMode = 2 },
                    label = { Text("المحظورة (${router.devices.count { it.isBlocked }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        // قائمة الأجهزة
        if (filteredDevices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DevicesOther, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (router.devices.isEmpty()) "لا توجد أجهزة متصلة مسجلة\nتأكد من كتابة كلمة المرور والضغط على تحديث" else "لا توجد نتائج تطابق البحث",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredDevices,
                key = { it.macAddress }
            ) { dev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dev.isBlocked) Color(0xFF2C1517) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // مؤشر الحالة (أخضر متصل / أحمر محظور)
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (dev.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        dev.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (dev.isBlocked) MaterialTheme.colorScheme.error else Color.White
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRenameDevice(dev) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل الاسم", tint = Color.Gray, modifier = Modifier.size(13.dp))
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${dev.ipAddress} • ${dev.macAddress}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // زر الحظر / إلغاء الحظر التفاعلي المباشر
                        Button(
                            onClick = { onToggleBlock(dev, !dev.isBlocked) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dev.isBlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (dev.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (dev.isBlocked) "إلغاء الحظر" else "حظر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==================== تبويب الراوترات المتعددة (Routers Tab) ====================
@Composable
fun RoutersTab(
    routers: List<RouterProfile>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onAddRouter: () -> Unit,
    onDeleteRouter: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الراوترات المحفوظة (${routers.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(
                    onClick = onAddRouter,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة راوتر", fontSize = 12.sp)
                }
            }
        }

        items(routers.indices.toList()) { idx ->
            val r = routers[idx]
            val isCurrent = idx == activeIndex
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(idx) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Router,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(r.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${r.routerIP} • ${r.firmware.displayName}", fontSize = 11.sp, color = Color.Gray)
                            if (isCurrent) {
                                Text("الراوتر النشط حالياً", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (routers.size > 1) {
                        IconButton(onClick = { onDeleteRouter(idx) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==================== تبويب الإحصائيات وسجل العمليات (Stats & Logs) ====================
@Composable
fun StatsAndLogsTab(router: RouterProfile, logs: List<String>, onClearLogs: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("إحصائيات الشبكة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatRow("نوع الراوتر", router.firmware.displayName)
                    StatRow("عنوان Gateway", router.routerIP)
                    StatRow("اسم مزود الخدمة", router.carrierName.ifEmpty { "غير محدد" })
                    StatRow("إجمالي سعة الباقة", "${router.totalPlanGB.toInt()} GB")
                    StatRow("الاستهلاك التراكمي", "${String.format("%.2f", router.usedGB)} GB")
                    StatRow("الأجهزة المسجلة", "${router.devices.size} جهاز")
                    StatRow("الأجهزة المحظورة", "${router.devices.count { it.isBlocked }} جهاز")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("سجل العمليات اللحظي (Logs)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onClearLogs) {
                    Text("مسح السجل", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Text("لا توجد سجلات مسجلة بعد", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            items(logs) { log ->
                Text(
                    text = log,
                    fontSize = 11.sp,
                    color = when {
                        log.startsWith("❌") || log.contains("فشل") || log.contains("خطأ") -> MaterialTheme.colorScheme.error
                        log.startsWith("✅") || log.contains("نجح") -> MaterialTheme.colorScheme.secondary
                        else -> Color.LightGray
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ==================== تبويب الإعدادات (Settings Tab) ====================
@Composable
fun SettingsTab(
    router: RouterProfile,
    onSave: (RouterProfile) -> Unit,
    onOpenWebUI: () -> Unit,
    onOpenVisualPicker: () -> Unit,
    onOpenNoCode: () -> Unit
) {
    var name by remember(router) { mutableStateOf(router.name) }
    var ip by remember(router) { mutableStateOf(router.routerIP) }
    var username by remember(router) { mutableStateOf(router.username) }
    var password by remember(router) { mutableStateOf(router.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var totalPlanGB by remember(router) { mutableStateOf(router.totalPlanGB.toInt().toString()) }
    var enableFallback by remember(router) { mutableStateOf(router.enableWebUIFallback) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("إعدادات الراوتر والاتصال", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم الراوتر") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("عنوان IP / Gateway") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("اسم المستخدم") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // حقل كلمة المرور مع إمكانية الإظهار والإخفاء (Show / Hide Password)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                    tint = Color.Gray
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = totalPlanGB,
                        onValueChange = { totalPlanGB = it },
                        label = { Text("سعة الباقة الإجمالية (GB)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // خيار محرك الويب البديل (WebUI Fallback)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("محرك المتصفح البديل (WebUI Fallback)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("كشط البيانات والتحكم عبر صفحة الويب تلقائياً عند الحاجة", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = enableFallback,
                            onCheckedChange = { enableFallback = it }
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val updated = router.copy(
                                name = name.trim(),
                                routerIP = ip.trim(),
                                username = username.trim(),
                                password = password.trim(),
                                totalPlanGB = totalPlanGB.toDoubleOrNull() ?: 140.0,
                                enableWebUIFallback = enableFallback
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ التغييرات في الهاتف", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // بطاقة تخصيص الكشط No-Code
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF30D158))
                        Spacer(Modifier.width(8.dp))
                        Text("مُخصّص الكشط المرئي (No-Code Scraper UI)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("تخصيص محددات الاستهلاك، الأجهزة، والأزرار بدون كتابة كود لأي نوع راوتر.", fontSize = 12.sp, color = Color.Gray)
                    Button(
                        onClick = onOpenNoCode,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A24))
                    ) {
                        Icon(Icons.Default.EditRoad, contentDescription = null, tint = Color(0xFF30D158))
                        Spacer(Modifier.width(8.dp))
                        Text("تخصيص محددات الكشط (No-Code)", color = Color.White)
                    }
                }
            }
        }

        // بطاقة لوحة أزرار WebUI
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدوات متصفح WebUI التفاعلي", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("فتح متصفح الراوتر المباشر لاختيار العناصر بدون كود أو التحكم بالأزرار برمجياً.", fontSize = 12.sp, color = Color.Gray)

                    Button(
                        onClick = onOpenVisualPicker,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A24))
                    ) {
                        Icon(Icons.Default.Adjust, contentDescription = null, tint = Color(0xFF30D158))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح متصفح الراوتر والالتقاط البصري 🎯", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onOpenWebUI,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFFFF9F0A))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح متصفح أزرار WebUI", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==================== متصفح الراوتر التفاعلي والالتقاط البصري بدون كود (Router WebUI Dialog) ====================
class WebUIPickerBridge(
    private val onElementPickedCallback: (PickedElementInfo) -> Unit
) {
    @JavascriptInterface
    fun onElementPicked(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val info = PickedElementInfo(
                selector = obj.optString("selector"),
                tag = obj.optString("tag"),
                text = obj.optString("text"),
                value = obj.optString("value"),
                url = obj.optString("url")
            )
            onElementPickedCallback(info)
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterWebUIDialog(
    router: RouterProfile,
    webUI: WebUIFallbackService,
    initialPickerMode: Boolean = false,
    onDismiss: () -> Unit,
    onRouterUpdated: (RouterProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPickerActive by remember { mutableStateOf(initialPickerMode) }
    val baseURL = "http://${router.routerIP}"
    val fullInitialURL = if (router.webUIPath.isNotBlank()) {
        "$baseURL/${router.webUIPath.trim().removePrefix("/")}"
    } else {
        baseURL
    }
    var currentURL by remember { mutableStateOf(fullInitialURL) }
    var addressInput by remember { mutableStateOf(fullInitialURL) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf(router.name) }
    var isLoading by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var pickedElement by remember { mutableStateOf<PickedElementInfo?>(null) }
    var showAssignmentDialog by remember { mutableStateOf(false) }
    var showButtonsListDialog by remember { mutableStateOf(false) }
    var discoveredButtons by remember { mutableStateOf<List<WebUIButtonInfo>>(emptyList()) }

    fun injectPickerScript(wv: WebView?) {
        wv?.evaluateJavascript(START_PICKER_JS, null)
    }

    fun removePickerScript(wv: WebView?) {
        wv?.evaluateJavascript(STOP_PICKER_JS, null)
    }

    fun togglePicker() {
        val next = !isPickerActive
        isPickerActive = next
        if (next) {
            toastMessage = "تم تفعيل وضع الالتقاط 🎯: المس أي عنصر في الصفحة لتعيينه"
            injectPickerScript(webViewRef)
        } else {
            toastMessage = "تم إيقاف وضع الالتقاط"
            removePickerScript(webViewRef)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1E))) {
                    // Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }

                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Back",
                                tint = if (canGoBack) Color.White else Color.Gray
                            )
                        }

                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Forward",
                                tint = if (canGoForward) Color.White else Color.Gray
                            )
                        }

                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color(0xFF0A84FF))
                        }

                        // Address bar
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(14.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    var clean = addressInput.trim()
                                    if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                                        clean = "http://$clean"
                                    }
                                    webViewRef?.loadUrl(clean)
                                }) {
                                    Icon(Icons.Default.ArrowCircleLeft, contentDescription = "Go", tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                                }
                            }
                        )

                        // Visual Picker Mode Toggle
                        Button(
                            onClick = { togglePicker() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPickerActive) Color(0xFF30D158) else Color(0xFF2C2C2E)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Adjust,
                                contentDescription = null,
                                tint = if (isPickerActive) Color.Black else Color(0xFF30D158),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isPickerActive) "إلغاء 🎯" else "التقاط 🎯",
                                fontSize = 11.sp,
                                color = if (isPickerActive) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Banner when picker mode is active
                    if (isPickerActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF30D158))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Adjust, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "وضع التقاط العناصر مفعّل 🎯: المس أي عنصر في الصفحة لتعيينه فوراً بدون كود",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { togglePicker() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Black)
                            }
                        }
                    }

                    // Quick Actions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141416))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // زر دخول تلقائي
                        SuggestionChip(
                            onClick = {
                                scope.launch {
                                    toastMessage = "جارِ تعبئة بيانات الدخول..."
                                    val ok = webUI.attemptAutoLogin(router.username, router.password, router, webViewRef)
                                    toastMessage = if (ok) "تم إرسال بيانات الدخول بنجاح" else "لم يتم العثور على حقول الدخول"
                                }
                            },
                            label = { Text("دخول تلقائي", fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF0A84FF)) }
                        )

                        // زر كشط ومزامنة
                        SuggestionChip(
                            onClick = {
                                scope.launch {
                                    toastMessage = "جارِ كشط محتويات الصفحة..."
                                    val res = webUI.scrapeCurrentPage(router, webViewRef)
                                    discoveredButtons = res.buttons
                                    if ((res.wanBytes ?: 0L) > 0L) {
                                        val updated = router.copy(accumulatedBytes = res.wanBytes!!)
                                        onRouterUpdated(updated)
                                        val gb = res.wanBytes!!.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                        toastMessage = "تم كشط الاستهلاك: ${String.format(java.util.Locale.US, "%.2f", gb)} GB"
                                    } else {
                                        toastMessage = "اكتمل الكشط: تم العثور على ${res.buttons.size} زر"
                                    }
                                }
                            },
                            label = { Text("كشط ومزامنة", fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF30D158)) }
                        )

                        // زر ضغط Reboot
                        SuggestionChip(
                            onClick = {
                                scope.launch {
                                    toastMessage = "جارِ الضغط على زر إعادة التشغيل..."
                                    val (ok, msg) = webUI.pressButton(
                                        router = router,
                                        selector = router.customRebootButtonSelector.takeIf { it.isNotBlank() },
                                        semanticAction = SemanticButtonAction.REBOOT,
                                        customWv = webViewRef
                                    )
                                    toastMessage = msg
                                }
                            },
                            label = { Text("ضغط Reboot", fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF453A)) }
                        )

                        // زر الأزرار المكتشفة
                        if (discoveredButtons.isNotEmpty()) {
                            SuggestionChip(
                                onClick = { showButtonsListDialog = true },
                                label = { Text("الأزرار (${discoveredButtons.size})", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Toast message
                    toastMessage?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C2E))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(msg, color = Color(0xFFFFD60A), fontSize = 11.sp)
                            IconButton(onClick = { toastMessage = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                            val bridge = WebUIPickerBridge { info ->
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    pickedElement = info
                                    showAssignmentDialog = true
                                }
                            }
                            addJavascriptInterface(bridge, "AndroidPicker")

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

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                    pageTitle = view?.title ?: router.name
                                    currentURL = url ?: ""
                                    addressInput = currentURL

                                    if (isPickerActive) {
                                        injectPickerScript(view)
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }
                            }

                            webViewRef = this
                            loadUrl(currentURL)
                        }
                    },
                    update = { wv ->
                        webViewRef = wv
                        if (isPickerActive) {
                            injectPickerScript(wv)
                        } else {
                            removePickerScript(wv)
                        }
                    }
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF0A84FF)
                    )
                }
            }
        }
    }

    // Buttons List Dialog
    if (showButtonsListDialog) {
        AlertDialog(
            onDismissRequest = { showButtonsListDialog = false },
            title = { Text("الأزرار المكتشفة في الصفحة (${discoveredButtons.size})", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(discoveredButtons) { btn ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showButtonsListDialog = false
                                    scope.launch {
                                        toastMessage = "جارِ النقر على: ${btn.displayLabel}..."
                                        val (ok, msg) = webUI.pressButton(selector = btn.selector, customWv = webViewRef)
                                        toastMessage = msg
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(btn.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(btn.selector, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFF0A84FF))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showButtonsListDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Assignment Dialog
    if (showAssignmentDialog && pickedElement != null) {
        val info = pickedElement!!
        ElementAssignmentDialog(
            info = info,
            onDismiss = { showAssignmentDialog = false },
            onAssign = { action ->
                showAssignmentDialog = false
                isPickerActive = false
                removePickerScript(webViewRef)

                var updated = router
                when (action) {
                    ElementAssignmentAction.USAGE_COUNTER -> {
                        val textUp = info.text.uppercase()
                        val detectedUnit = when {
                            textUp.contains("GB") || textUp.contains("جيجا") -> "GB"
                            textUp.contains("MB") || textUp.contains("ميجا") -> "MB"
                            textUp.contains("KB") || textUp.contains("كيلو") -> "KB"
                            textUp.contains("BYTE") || textUp.contains("بايت") -> "Bytes"
                            else -> "Auto"
                        }
                        updated = router.copy(
                            customUsageSelector = info.selector,
                            customUsageUnit = detectedUnit
                        )
                        toastMessage = "تم تعيين عداد استهلاك الباقة بنجاح: ${info.selector}"
                    }
                    ElementAssignmentAction.DEVICES_TABLE -> {
                        updated = router.copy(customDeviceTableSelector = info.selector)
                        toastMessage = "تم تعيين جدول الأجهزة المتصلة بنجاح: ${info.selector}"
                    }
                    ElementAssignmentAction.REBOOT_BUTTON -> {
                        updated = router.copy(customRebootButtonSelector = info.selector)
                        toastMessage = "تم تعيين زر إعادة التشغيل بنجاح: ${info.selector}"
                    }
                    ElementAssignmentAction.USERNAME_INPUT -> {
                        updated = router.copy(customUsernameSelector = info.selector)
                        toastMessage = "تم تعيين حقل اسم المستخدم بنجاح: ${info.selector}"
                    }
                    ElementAssignmentAction.PASSWORD_INPUT -> {
                        updated = router.copy(customPasswordSelector = info.selector)
                        toastMessage = "تم تعيين حقل كلمة المرور بنجاح: ${info.selector}"
                    }
                    ElementAssignmentAction.LOGIN_BUTTON -> {
                        updated = router.copy(customLoginButtonSelector = info.selector)
                        toastMessage = "تم تعيين زر تسجيل الدخول بنجاح: ${info.selector}"
                    }
                    ElementAssignmentAction.COPY_SELECTOR -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("selector", info.selector)
                        clipboard.setPrimaryClip(clip)
                        toastMessage = "تم نسخ المحدد للحافظة"
                    }
                }
                if (action != ElementAssignmentAction.COPY_SELECTOR) {
                    onRouterUpdated(updated)
                }
            }
        )
    }
}

@Composable
fun ElementAssignmentDialog(
    info: PickedElementInfo,
    onDismiss: () -> Unit,
    onAssign: (ElementAssignmentAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Adjust, contentDescription = null, tint = Color(0xFF30D158))
                Spacer(Modifier.width(8.dp))
                Text("تعيين العنصر الملتقط بدون كود", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // بطاقة معاينة العنصر الملتقط
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "<${info.tag.uppercase()}>",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0A84FF),
                                    modifier = Modifier
                                        .background(Color(0xFF0A84FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                if (info.suggestedType != SuggestedElementType.UNKNOWN) {
                                    Text(
                                        "مقترح: ${info.suggestedType.title}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD60A),
                                        modifier = Modifier
                                            .background(Color(0xFFFFD60A).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (info.text.isNotBlank()) {
                                Text("المحتوى: \"${info.text}\"", fontSize = 12.sp, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            } else if (info.value.isNotBlank()) {
                                Text("القيمة: \"${info.value}\"", fontSize = 12.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }

                            Text(
                                info.selector,
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item {
                    Text("اختر كيف ترغب في تعيين هذا العنصر في راوترك:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }

                // 1. عداد استهلاك الباقة
                item {
                    AssignmentOptionCard(
                        title = "عداد استهلاك الباقة",
                        subtitle = "لقراءة الرصيد المستهلك ومزامنته مع التطبيق والودجت",
                        icon = Icons.Default.PieChart,
                        color = Color(0xFF0A84FF),
                        isRecommended = info.suggestedType == SuggestedElementType.USAGE_COUNTER,
                        onClick = { onAssign(ElementAssignmentAction.USAGE_COUNTER) }
                    )
                }

                // 2. جدول الأجهزة المتصلة
                item {
                    AssignmentOptionCard(
                        title = "جدول الأجهزة المتصلة",
                        subtitle = "لاستخراج قائمة الهواتف والأجهزة المتصلة براوترك وحظرها",
                        icon = Icons.Default.Devices,
                        color = Color(0xFF30D158),
                        isRecommended = info.suggestedType == SuggestedElementType.DEVICES_TABLE,
                        onClick = { onAssign(ElementAssignmentAction.DEVICES_TABLE) }
                    )
                }

                // 3. زر إعادة التشغيل
                item {
                    AssignmentOptionCard(
                        title = "زر إعادة التشغيل (Reboot)",
                        subtitle = "للضغط عليه تلقائياً عند طلب إعادة تشغيل الراوتر",
                        icon = Icons.Default.PowerSettingsNew,
                        color = Color(0xFFFF453A),
                        isRecommended = info.suggestedType == SuggestedElementType.REBOOT_BUTTON,
                        onClick = { onAssign(ElementAssignmentAction.REBOOT_BUTTON) }
                    )
                }

                // 4. حقل اسم المستخدم
                item {
                    AssignmentOptionCard(
                        title = "حقل اسم المستخدم (Login User)",
                        subtitle = "لحقن اسم المستخدم تلقائياً عند تسجيل الدخول",
                        icon = Icons.Default.Person,
                        color = Color(0xFFFF9F0A),
                        isRecommended = info.suggestedType == SuggestedElementType.USERNAME_INPUT,
                        onClick = { onAssign(ElementAssignmentAction.USERNAME_INPUT) }
                    )
                }

                // 5. حقل كلمة المرور
                item {
                    AssignmentOptionCard(
                        title = "حقل كلمة المرور (Login Password)",
                        subtitle = "لحقن كلمة المرور تلقائياً في صفحة دخول الراوتر",
                        icon = Icons.Default.Lock,
                        color = Color(0xFFBF5AF2),
                        isRecommended = info.suggestedType == SuggestedElementType.PASSWORD_INPUT,
                        onClick = { onAssign(ElementAssignmentAction.PASSWORD_INPUT) }
                    )
                }

                // 6. زر تسجيل الدخول
                item {
                    AssignmentOptionCard(
                        title = "زر تسجيل الدخول (Login Submit)",
                        subtitle = "للنقر عليه تلقائياً بعد ملء بيانات الدخول",
                        icon = Icons.Default.Key,
                        color = Color(0xFFFFD60A),
                        isRecommended = info.suggestedType == SuggestedElementType.LOGIN_BUTTON,
                        onClick = { onAssign(ElementAssignmentAction.LOGIN_BUTTON) }
                    )
                }

                // 7. نسخ المحدد
                item {
                    OutlinedButton(
                        onClick = { onAssign(ElementAssignmentAction.COPY_SELECTOR) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("نسخ المحدد فقط للحافظة", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun AssignmentOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    if (isRecommended) {
                        Text(
                            "مقترح",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD60A),
                            modifier = Modifier
                                .background(Color(0xFFFFD60A).copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ArrowBackIosNew, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
        }
    }
}

const val START_PICKER_JS = """
(function() {
    if (window.__dslPickerActive) return;
    window.__dslPickerActive = true;

    var box = document.getElementById('__dsl_picker_box');
    if (!box) {
        box = document.createElement('div');
        box.id = '__dsl_picker_box';
        box.style.position = 'fixed';
        box.style.pointerEvents = 'none';
        box.style.zIndex = '2147483647';
        box.style.border = '2px dashed #00E676';
        box.style.backgroundColor = 'rgba(0, 230, 118, 0.22)';
        box.style.borderRadius = '5px';
        box.style.boxShadow = '0 0 14px rgba(0, 230, 118, 0.65)';
        box.style.transition = 'all 0.08s ease-out';
        box.style.display = 'none';

        var badge = document.createElement('div');
        badge.id = '__dsl_picker_badge';
        badge.style.position = 'absolute';
        badge.style.top = '-28px';
        badge.style.left = '0';
        badge.style.backgroundColor = '#00E676';
        badge.style.color = '#000000';
        badge.style.fontSize = '12px';
        badge.style.fontWeight = 'bold';
        badge.style.padding = '2px 8px';
        badge.style.borderRadius = '4px';
        badge.style.whiteSpace = 'nowrap';
        badge.style.fontFamily = 'monospace';
        badge.style.boxShadow = '0 2px 6px rgba(0,0,0,0.4)';
        box.appendChild(badge);

        document.documentElement.appendChild(box);
    }

    var hoveredEl = null;

    function getUniqueSelector(el) {
        if (!el || el === document.body || el === document.documentElement) return '';
        if (el.id && el.id.trim() !== '') {
            try {
                var cleanId = CSS.escape ? CSS.escape(el.id) : el.id;
                if (document.querySelectorAll('#' + cleanId).length === 1) {
                    return '#' + cleanId;
                }
            } catch(e) {}
        }
        var name = el.getAttribute('name');
        if (name) {
            var sel = el.tagName.toLowerCase() + '[name="' + name + '"]';
            try {
                if (document.querySelectorAll(sel).length === 1) return sel;
            } catch(e) {}
        }
        var path = [];
        var curr = el;
        while (curr && curr.nodeType === Node.ELEMENT_NODE && curr !== document.documentElement) {
            var tag = curr.tagName.toLowerCase();
            if (curr.id && curr.id.trim() !== '') {
                try {
                    var cleanId = CSS.escape ? CSS.escape(curr.id) : curr.id;
                    if (document.querySelectorAll('#' + cleanId).length === 1) {
                        path.unshift('#' + cleanId);
                        break;
                    }
                } catch(e) {}
            }
            var parent = curr.parentElement;
            if (parent) {
                var siblings = Array.from(parent.children).filter(function(e) { return e.tagName.toLowerCase() === tag; });
                if (siblings.length > 1) {
                    var index = siblings.indexOf(curr) + 1;
                    path.unshift(tag + ':nth-of-type(' + index + ')');
                } else {
                    path.unshift(tag);
                }
            } else {
                path.unshift(tag);
            }
            curr = curr.parentElement;
            if (path.length >= 4) break;
        }
        return path.join(' > ');
    }

    function onPointerMove(e) {
        var x = e.clientX, y = e.clientY;
        var el = document.elementFromPoint(x, y);
        if (!el || el === box || box.contains(el)) return;
        hoveredEl = el;

        var rect = el.getBoundingClientRect();
        box.style.left = rect.left + 'px';
        box.style.top = rect.top + 'px';
        box.style.width = Math.max(rect.width, 14) + 'px';
        box.style.height = Math.max(rect.height, 14) + 'px';
        box.style.display = 'block';

        var badge = document.getElementById('__dsl_picker_badge');
        if (badge) {
            var tag = el.tagName.toLowerCase();
            var id = el.id ? '#' + el.id : '';
            var txt = (el.innerText || el.textContent || el.value || '').trim();
            if (txt.length > 18) txt = txt.substring(0, 18) + '...';
            badge.innerText = tag + id + (txt ? ' "' + txt + '"' : '');
            if (rect.top < 34) {
                badge.style.top = (rect.height + 4) + 'px';
            } else {
                badge.style.top = '-28px';
            }
        }
    }

    function onClick(e) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();

        var el = hoveredEl || e.target;
        if (!el || el === box || box.contains(el)) return false;

        var selector = getUniqueSelector(el);
        var tag = el.tagName ? el.tagName.toLowerCase() : '';
        var text = (el.innerText || el.textContent || '').trim().replace(/\\s+/g, ' ');
        var value = el.value || '';

        var payload = {
            selector: selector,
            tag: tag,
            text: text.slice(0, 200),
            value: value.slice(0, 100),
            url: window.location.href
        };

        if (window.AndroidPicker && window.AndroidPicker.onElementPicked) {
            window.AndroidPicker.onElementPicked(JSON.stringify(payload));
        }
        return false;
    }

    window.__dslPickerMoveHandler = onPointerMove;
    window.__dslPickerClickHandler = onClick;

    document.addEventListener('pointermove', onPointerMove, true);
    document.addEventListener('touchmove', function(e) {
        if (e.touches && e.touches[0]) {
            onPointerMove({ clientX: e.touches[0].clientX, clientY: e.touches[0].clientY });
        }
    }, { capture: true, passive: true });

    document.addEventListener('click', onClick, true);
    document.addEventListener('touchend', function(e) {
        if (e.changedTouches && e.changedTouches[0]) {
            var t = e.changedTouches[0];
            var el = document.elementFromPoint(t.clientX, t.clientY);
            if (el) {
                hoveredEl = el;
                onClick(e);
            }
        }
    }, true);
})();
"""

const val STOP_PICKER_JS = """
(function() {
    window.__dslPickerActive = false;
    var box = document.getElementById('__dsl_picker_box');
    if (box && box.parentElement) {
        box.parentElement.removeChild(box);
    }
    if (window.__dslPickerMoveHandler) {
        document.removeEventListener('pointermove', window.__dslPickerMoveHandler, true);
    }
    if (window.__dslPickerClickHandler) {
        document.removeEventListener('click', window.__dslPickerClickHandler, true);
    }
})();
"""
