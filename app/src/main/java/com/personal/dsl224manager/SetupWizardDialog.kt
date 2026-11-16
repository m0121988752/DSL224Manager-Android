package com.personal.dsl224manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * معالج الإعداد والتهيئة السريعة لأول مرة (First-Time Setup Wizard)
 * يكتشف الراوتر وعنوان IP تلقائياً، ويساعد المستخدم في إدخال كلمة المرور والباقة ودورة التجديد بأسلوب مرشد وبسيط.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardDialog(
    initialRouter: RouterProfile,
    onDismiss: () -> Unit,
    onComplete: (RouterProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { RouterRepository() }
    val focusManager = LocalFocusManager.current

    var currentStep by remember { mutableStateOf(0) }
    var isDetecting by remember { mutableStateOf(true) }
    var detectedResult by remember { mutableStateOf<AutoDetectResult?>(null) }

    // مدخلات المعالج
    var routerName by remember { mutableStateOf(initialRouter.name) }
    var routerIP by remember { mutableStateOf(initialRouter.routerIP) }
    var username by remember { mutableStateOf(initialRouter.username) }
    var password by remember { mutableStateOf(initialRouter.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var firmware by remember { mutableStateOf(initialRouter.firmware) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var totalPlanGB by remember { mutableStateOf(initialRouter.totalPlanGB.toInt().toString()) }
    var renewalDayText by remember { mutableStateOf(initialRouter.renewalDay.toString()) }
    var manualOffsetGBText by remember { mutableStateOf(if (initialRouter.manualOffsetGB > 0) initialRouter.manualOffsetGB.toString() else "0") }

    var isTestingLogin by remember { mutableStateOf(false) }
    var testLoginMessage by remember { mutableStateOf<String?>(null) }
    var isLoginSuccess by remember { mutableStateOf(false) }

    // تشغيل الاكتشاف التلقائي فور فتح المعالج
    LaunchedEffect(Unit) {
        isDetecting = true
        val result = RouterAutoDetector.detectRouter(context)
        detectedResult = result
        routerIP = result.ip
        routerName = result.detectedName
        firmware = result.firmware
        selectedPresetId = result.presetId
        isDetecting = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30D158).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("معالج الإعداد السريع الذكي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("تهيئة الراوتر بلمسات بسيطة وبدون تعقيد", fontSize = 11.sp, color = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // مؤشر الخطوات الأربع
                val steps = listOf("1. الاكتشاف", "2. الدخول", "3. الباقة", "4. الإنهاء")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, stepTitle ->
                        val isActive = currentStep == index
                        val isDone = currentStep > index
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isDone -> Color(0xFF30D158)
                                            isActive -> Color(0xFF0A84FF)
                                            else -> Color(0xFF3A3A3C)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                } else {
                                    Text("${index + 1}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stepTitle.substringAfter(". "),
                                fontSize = 10.sp,
                                color = if (isActive) Color.White else Color.Gray,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF2C2C2E))

                when (currentStep) {
                    // ================= الخطوة 1: الكشف التلقائي عن الراوتر =================
                    0 -> {
                        Text("الخطوة 1: فحص الراوتر وعنوان الشبكة", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        if (isDetecting) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF30D158))
                                    Spacer(Modifier.width(12.dp))
                                    Text("جارٍ البحث التلقائي عن الراوتر المتصل بالواي فاي...", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A24)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("تم الكشف عن الراوتر بنجاح", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF30D158))
                                    }
                                    Text("الموديل: ${detectedResult?.detectedName ?: routerName}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text("IP: $routerIP", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                    }
                                    Text(detectedResult?.description ?: "", fontSize = 10.sp, color = Color.Gray)
                                }
                            }

                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                OutlinedTextField(
                                    value = routerIP,
                                    onValueChange = { routerIP = it },
                                    label = { Text("عنوان IP / Gateway") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Text("أو اختر موديل راوترك يدوياً:", fontSize = 11.sp, color = Color.Gray)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = firmware == FirmwareType.HUAWEI_B311,
                                        onClick = {
                                            routerName = "راوتر Huawei 4G (B311)"
                                            firmware = FirmwareType.HUAWEI_B311
                                            if (routerIP.isBlank() || routerIP == "192.168.1.1") routerIP = "192.168.8.1"
                                            selectedPresetId = "huawei_b311"
                                        },
                                        label = { Text("Huawei 4G", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = firmware == FirmwareType.ME110_BOA,
                                        onClick = {
                                            routerName = "راوتر D-Link (DSL-224)"
                                            firmware = FirmwareType.ME110_BOA
                                            if (routerIP.isBlank() || routerIP == "192.168.8.1") routerIP = "192.168.1.1"
                                            selectedPresetId = "dlink_dsl224"
                                        },
                                        label = { Text("D-Link 224", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = firmware == FirmwareType.GENERIC_REALTEK,
                                        onClick = {
                                            routerName = "راوتر ZTE ZXHN H168N"
                                            firmware = FirmwareType.GENERIC_REALTEK
                                            if (routerIP.isBlank() || routerIP == "192.168.8.1") routerIP = "192.168.1.1"
                                            selectedPresetId = "zte_h168n"
                                        },
                                        label = { Text("ZTE VDSL", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ================= الخطوة 2: كلمة مرور لوحة التحكم =================
                    1 -> {
                        Text("الخطوة 2: كلمة مرور صفحة الراوتر", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "أدخل كلمة المرور الخاصة بلوحة تحكم الراوتر (مكتوبة عادة خلف الراوتر أو كلمة السر التي قمت بتعيينها).",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("اسم المستخدم (Username)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    testLoginMessage = null
                                },
                                label = { Text("كلمة المرور (Password)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isTestingLogin = true
                                    testLoginMessage = "جارٍ اختبار الاتصال بـ $routerIP..."
                                    val testProfile = initialRouter.copy(
                                        name = routerName,
                                        routerIP = routerIP.trim(),
                                        username = username.trim(),
                                        password = password.trim(),
                                        firmware = firmware
                                    )
                                    val ok = repo.loginAndRefresh(testProfile) { msg ->
                                        testLoginMessage = msg
                                    }
                                    isLoginSuccess = ok
                                    isTestingLogin = false
                                    if (ok) {
                                        testLoginMessage = "تم التحقق بنجاح! كلمة المرور صحيحة وتم الاتصال بالراوتر"
                                    }
                                }
                            },
                            enabled = !isTestingLogin && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
                        ) {
                            if (isTestingLogin) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF0A84FF))
                                Spacer(Modifier.width(8.dp))
                                Text("جارٍ فحص الاتصال...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("اختبار الاتصال بكلمة المرور الآن", fontSize = 12.sp, color = Color(0xFF0A84FF))
                            }
                        }

                        testLoginMessage?.let { msg ->
                            Text(
                                msg,
                                fontSize = 11.sp,
                                color = if (isLoginSuccess) Color(0xFF30D158) else Color(0xFFFF9F0A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // ================= الخطوة 3: سعة الباقة الشهرية =================
                    2 -> {
                        Text("الخطوة 3: سعة باقة الإنترنت الشهرية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("اختر أو اكتب سعة باقتك بالجيجابايت لمتابعة الاستهلاك بدقة:", fontSize = 11.sp, color = Color.Gray)

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            val planOptions = listOf("140", "200", "250", "300", "400", "600")
                            items(planOptions) { gbVal ->
                                FilterChip(
                                    selected = totalPlanGB == gbVal,
                                    onClick = { totalPlanGB = gbVal },
                                    label = {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = "$gbVal GB",
                                                fontSize = 12.sp,
                                                fontWeight = if (totalPlanGB == gbVal) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = totalPlanGB,
                                onValueChange = { totalPlanGB = it },
                                label = { Text("سعة الباقة الإجمالية (GB)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ================= الخطوة 4: دورة التجديد والاستهلاك المبدئي =================
                    3 -> {
                        Text("الخطوة 4: دورة التجديد والاستهلاك الحالي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("حدد موعد تجديد باقتك الشهري، واستهلاكك التقريبي إذا كنت في منتصف الشهر:", fontSize = 11.sp, color = Color.Gray)

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = renewalDayText,
                                onValueChange = {
                                    val num = it.filter { ch -> ch.isDigit() }
                                    if (num.isEmpty() || (num.toIntOrNull() in 1..31)) {
                                        renewalDayText = num
                                    }
                                },
                                label = { Text("يوم تجديد الباقة شهرياً (1 - 31)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = manualOffsetGBText,
                                onValueChange = { manualOffsetGBText = it },
                                label = { Text("استهلاكك المبدئي حتى الآن (GB) - اختياري") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ملخص إعدادات الراوتر", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("الاسم: $routerName", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text("IP: $routerIP", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                }
                                Text("الباقة: $totalPlanGB GB (تجديد يوم ${renewalDayText.ifEmpty { "1" }})", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                if ((manualOffsetGBText.toDoubleOrNull() ?: 0.0) > 0.0) {
                                    Text("الاستهلاك الحالي المبدئي: $manualOffsetGBText GB", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentStep < 3) {
                        currentStep += 1
                    } else {
                        // إنهاء وحفظ مع الحفاظ الصارم على عنوان IP والبيانات التي حددها المستخدم
                        val trimmedIP = routerIP.trim().ifEmpty { "192.168.1.1" }
                        val trimmedName = routerName.trim().ifEmpty { "راوتر منزلي" }
                        val trimmedUser = username.trim().ifEmpty { "admin" }
                        val trimmedPass = password.trim()
                        val planGB = totalPlanGB.toDoubleOrNull() ?: 140.0
                        val rDay = renewalDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                        val offsetGB = manualOffsetGBText.toDoubleOrNull() ?: 0.0

                        var finalProfile = initialRouter.copy(
                            name = trimmedName,
                            routerIP = trimmedIP,
                            username = trimmedUser,
                            password = trimmedPass,
                            firmware = firmware,
                            totalPlanGB = planGB,
                            renewalDay = rDay,
                            manualOffsetGB = offsetGB
                        )

                        // تطبيق القالب الجاهز لضبط محددات الـ No-Code فقط دون تجاوز عنوان IP ومواصفات المستخدم
                        selectedPresetId?.let { pid ->
                            RouterPresetsManager.builtinPresets.find { it.id == pid }?.let { preset ->
                                finalProfile = RouterPresetsManager.applyPreset(preset, finalProfile).copy(
                                    name = trimmedName,
                                    routerIP = trimmedIP,
                                    username = trimmedUser,
                                    password = trimmedPass,
                                    totalPlanGB = planGB,
                                    renewalDay = rDay,
                                    manualOffsetGB = offsetGB
                                )
                            }
                        }

                        onComplete(finalProfile)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentStep == 3) Color(0xFF30D158) else Color(0xFF0A84FF)
                )
            ) {
                Text(
                    if (currentStep == 3) "حفظ وبدء الاستخدام" else "التالي",
                    color = if (currentStep == 3) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (currentStep > 0) {
                TextButton(onClick = { currentStep -= 1 }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("السابق")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("تخطي الإعداد")
                }
            }
        }
    )
}
