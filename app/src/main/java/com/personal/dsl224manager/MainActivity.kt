package com.personal.dsl224manager

import android.os.Bundle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
    val webUI = remember { WebUIFallbackService(context) }
    val scope = rememberCoroutineScope()

    // قائمة الراوترات وحالة الراوتر النشط
    var routers by remember {
        mutableStateOf(
            listOf(
                RouterProfile(
                    name = "راوتر Huawei 4G",
                    routerIP = "192.168.1.1",
                    username = "admin",
                    password = "",
                    firmware = FirmwareType.HUAWEI_B311,
                    totalPlanGB = 140.0
                )
            )
        )
    }
    var activeRouterIndex by remember { mutableStateOf(0) }
    val activeRouter = routers.getOrElse(activeRouterIndex) { routers.first() }

    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var logs by remember { mutableStateOf(listOf("مرحباً بك في DSL224Manager", "جاهز للاتصال والتحكم في الراوتر")) }
    var isLoading by remember { mutableStateOf(false) }

    // حوار إعادة تسمية الجهاز
    var editingDevice by remember { mutableStateOf<NetworkDevice?>(null) }
    var newCustomName by remember { mutableStateOf("") }

    // حوار متصفح أزرار WebUI
    var showWebUIDialog by remember { mutableStateOf(false) }
    var lastScrapeResult by remember { mutableStateOf<WebUIScrapeResult?>(null) }

    fun log(msg: String) {
        logs = listOf(msg) + logs.take(60)
    }

    fun updateActiveRouter(updated: RouterProfile) {
        routers = routers.toMutableList().also {
            if (activeRouterIndex in it.indices) {
                it[activeRouterIndex] = updated
            }
        }
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
                                val (ok, msg) = webUI.pressButton(semanticAction = SemanticButtonAction.REBOOT)
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
                            val ok = repo.toggleBlockDevice(activeRouter, dev, block) { log(it) }
                            if (ok) {
                                val updatedDevs = activeRouter.devices.map {
                                    if (it.macAddress == dev.macAddress) it.copy(isBlocked = block) else it
                                }
                                updateActiveRouter(activeRouter.copy(devices = updatedDevs))
                            }
                        }
                    },
                    onRenameDevice = { dev ->
                        editingDevice = dev
                        newCustomName = dev.displayName
                    },
                    onRefresh = { refreshActiveRouter() }
                )
                MainTab.ROUTERS -> RoutersTab(
                    routers = routers,
                    activeIndex = activeRouterIndex,
                    onSelect = { activeRouterIndex = it },
                    onAddRouter = {
                        val newR = RouterProfile(name = "راوتر جديد ${routers.size + 1}")
                        routers = routers + newR
                        activeRouterIndex = routers.lastIndex
                        log("تمت إضافة راوتر جديد")
                    },
                    onDeleteRouter = { idx ->
                        if (routers.size > 1) {
                            routers = routers.filterIndexed { i, _ -> i != idx }
                            if (activeRouterIndex >= routers.size) activeRouterIndex = routers.lastIndex
                            log("تم حذف الراوتر")
                        }
                    }
                )
                MainTab.STATS -> StatsAndLogsTab(router = activeRouter, logs = logs, onClearLogs = { logs = emptyList() })
                MainTab.SETTINGS -> SettingsTab(
                    router = activeRouter,
                    onSave = { updated ->
                        updateActiveRouter(updated)
                        log("تم حفظ بيانات الراوتر بنجاح")
                    },
                    onOpenWebUI = { showWebUIDialog = true }
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

    // حوار التحكم في واجهة الويب وأزرارها المكتشفة
    if (showWebUIDialog) {
        AlertDialog(
            onDismissRequest = { showWebUIDialog = false },
            title = { Text("التحكم التفاعلي في أزرار WebUI", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "يمكنك الضغط مباشرة على أي زر مكتشف في صفحة الراوتر لتنفيذه عبر المتصفح:",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    // أزرار دلالية سريعة
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        log("جاري إرسال إعادة التشغيل...")
                                        val (ok, msg) = webUI.pressButton(semanticAction = SemanticButtonAction.REBOOT)
                                        log(if (ok) "✅ $msg" else "❌ $msg")
                                        showWebUIDialog = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("إعادة التشغيل", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        log("جاري تنفيذ تسجيل الدخول عبر المتصفح...")
                                        val (ok, msg) = webUI.pressButton(semanticAction = SemanticButtonAction.LOGIN)
                                        log(if (ok) "✅ $msg" else "❌ $msg")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("دخول تلقائي", fontSize = 11.sp)
                            }
                        }
                    }

                    item {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("الأزرار المكتشفة في الصفحة (${lastScrapeResult?.buttons?.size ?: 0}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    val btns = lastScrapeResult?.buttons ?: emptyList()
                    if (btns.isEmpty()) {
                        item {
                            Text("لم يتم كشط أزرار بعد، اضغط على زر كشط WebUI في الأعلى أولاً.", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        items(btns) { btn ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            log("جاري النقر على: ${btn.displayLabel}...")
                                            val (ok, msg) = webUI.pressButton(selector = btn.selector)
                                            log(if (ok) "✅ $msg" else "❌ $msg")
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(btn.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(btn.selector, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWebUIDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

// ==================== تبويب الشبكة والأجهزة (Dashboard) ====================
@Composable
fun DashboardTab(
    router: RouterProfile,
    isLoading: Boolean,
    onToggleBlock: (NetworkDevice, Boolean) -> Unit,
    onRenameDevice: (NetworkDevice) -> Unit,
    onRefresh: () -> Unit
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
            items(filteredDevices) { dev ->
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
    onOpenWebUI: () -> Unit
) {
    var name by remember(router) { mutableStateOf(router.name) }
    var ip by remember(router) { mutableStateOf(router.routerIP) }
    var username by remember(router) { mutableStateOf(router.username) }
    var password by remember(router) { mutableStateOf(router.password) }
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
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
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
                        Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدوات متصفح WebUI التفاعلي", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("فتح لوحة التحكم بأزرار صفحة الراوتر وإدارتها برمجياً.", fontSize = 12.sp, color = Color.Gray)
                    Button(
                        onClick = onOpenWebUI,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFFFF9F0A))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح لوحة تحكم أزرار WebUI", color = Color.White)
                    }
                }
            }
        }
    }
}
