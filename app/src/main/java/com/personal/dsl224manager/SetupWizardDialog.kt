package com.personal.dsl224manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * معالج الإعداد والتهيئة السريعة لأول مرة (First-Time Setup Wizard)
 * يكتشف الراوتر وعنوان IP تلقائياً، ويساعد المستخدم في إدخال كلمة المرور والباقة بأسلوب مرشد وبسيط.
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
                // مؤشر الخطوات
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("1. اكتشاف الراوتر", "2. كلمة المرور", "3. سعة الباقة").forEachIndexed { index, stepTitle ->
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
                                fontSize = 11.sp,
                                color = if (isActive) Color.White else Color.Gray,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Divider(color = Color(0xFF2C2C2E))

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
                                    Text("جارِ البحث التلقائي عن الراوتر المتصل بالواي فاي...", fontSize = 12.sp)
                                }
                            }
                        } else {
                            // نتيجة الاكتشاف
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
                                    Text("عنوان IP الافتراضي: $routerIP", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                    Text(detectedResult?.description ?: "", fontSize = 10.sp, color = Color.Gray)
                                }
                            }

                            OutlinedTextField(
                                value = routerIP,
                                onValueChange = { routerIP = it },
                                label = { Text("عنوان IP / Gateway") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // اختيار يدوي للموديل إن رغب المستخدم
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

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("اسم المستخدم (Username)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                testLoginMessage = null
                            },
                            label = { Text("كلمة المرور (Password)") },
                            singleLine = true,
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

                        // زر اختبار الاتصال اللحظي
                        Button(
                            onClick = {
                                scope.launch {
                                    isTestingLogin = true
                                    testLoginMessage = "جارِ اختبار الاتصال بـ $routerIP..."
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
                                Text("جارِ فحص الاتصال...", fontSize = 12.sp)
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
                                        Text(
                                            text = "$gbVal GB",
                                            fontSize = 12.sp,
                                            fontWeight = if (totalPlanGB == gbVal) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    leadingIcon = if (totalPlanGB == gbVal) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF30D158).copy(alpha = 0.25f),
                                        selectedLabelColor = Color(0xFF30D158),
                                        selectedLeadingIconColor = Color(0xFF30D158)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = totalPlanGB,
                            onValueChange = { totalPlanGB = it },
                            label = { Text("سعة الباقة الإجمالية (GB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("جاهز للانطلاق", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("الراوتر: $routerName ($routerIP)", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                                Text("سعة الباقة: $totalPlanGB GB", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentStep < 2) {
                        currentStep += 1
                    } else {
                        // إنهاء وحفظ مع الحفاظ الصارم على عنوان IP والبيانات التي حددها المستخدم
                        val trimmedIP = routerIP.trim().ifEmpty { "192.168.1.1" }
                        val trimmedName = routerName.trim().ifEmpty { "راوتر منزلي" }
                        val trimmedUser = username.trim().ifEmpty { "admin" }
                        val trimmedPass = password.trim()
                        val planGB = totalPlanGB.toDoubleOrNull() ?: 140.0

                        var finalProfile = initialRouter.copy(
                            name = trimmedName,
                            routerIP = trimmedIP,
                            username = trimmedUser,
                            password = trimmedPass,
                            firmware = firmware,
                            totalPlanGB = planGB
                        )

                        // تطبيق القالب الجاهز لضبط محددات الـ No-Code فقط دون تجاوز عنوان IP ومواصفات المستخدم
                        selectedPresetId?.let { pid ->
                            RouterPresetsManager.builtinPresets.find { it.id == pid }?.let { preset ->
                                finalProfile = RouterPresetsManager.applyPreset(preset, finalProfile).copy(
                                    name = trimmedName,
                                    routerIP = trimmedIP,
                                    username = trimmedUser,
                                    password = trimmedPass,
                                    totalPlanGB = planGB
                                )
                            }
                        }

                        onComplete(finalProfile)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentStep == 2) Color(0xFF30D158) else Color(0xFF0A84FF)
                )
            ) {
                Text(
                    if (currentStep == 2) "حفظ وبدء الاستخدام" else "التالي",
                    color = if (currentStep == 2) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (currentStep > 0) {
                TextButton(onClick = { currentStep -= 1 }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
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
