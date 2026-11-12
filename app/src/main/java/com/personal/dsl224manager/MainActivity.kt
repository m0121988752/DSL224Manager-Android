package com.personal.dsl224manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repo = RouterRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF007AFF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                MainScreen(repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repo: RouterRepository) {
    var profile by remember { mutableStateOf(RouterProfile()) }
    var logs by remember { mutableStateOf(listOf("مرحباً بك في مدير الراوتر")) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun log(msg: String) {
        logs = listOf(msg) + logs.take(50)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                log("جاري تحديث بيانات الراوتر...")
                                profile = repo.refreshHuawei(profile) { log(it) }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // كارت استهلاك الباقة
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (profile.carrierName.isNotBlank()) "شبكة 4G (${profile.carrierName})" else "راوتر 4G الهوائي",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${String.format("%.2f", profile.usedGB)} GB / ${profile.totalPlanGB.toInt()} GB",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = (profile.usedPercentage / 100.0).toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.DarkGray
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتبقي: ${String.format("%.2f", profile.remainingGB)} GB", color = Color.Gray)
                            Text("الاستهلاك: ${String.format("%.1f", profile.usedPercentage)}%", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // إعدادات الدخول السريعة
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بيانات الراوتر", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = profile.routerIP,
                            onValueChange = { profile = profile.copy(routerIP = it) },
                            label = { Text("عنوان IP") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = profile.password,
                            onValueChange = { profile = profile.copy(password = it) },
                            label = { Text("كلمة المرور") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // قائمة الأجهزة المتصلة
            item {
                Text("الأجهزة المتصلة (${profile.devices.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (profile.devices.isEmpty()) {
                item {
                    Text("لا توجد أجهزة معروضة، يرجى كتابة كلمة المرور والضغط على تحديث", color = Color.Gray)
                }
            } else {
                items(profile.devices) { dev ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.displayName, fontWeight = FontWeight.SemiBold)
                                Text("${dev.ipAddress} • ${dev.macAddress}", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val newState = !dev.isBlocked
                                        val ok = repo.toggleBlockDevice(profile, dev, newState) { log(it) }
                                        if (ok) {
                                            profile = profile.copy(devices = profile.devices.toList())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (dev.isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (dev.isBlocked) Color.Red else Color.Green
                                )
                            }
                        }
                    }
                }
            }

            // السجل اللحظي (Logs)
            item {
                Text("سجل العمليات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            items(logs) { entry ->
                Text(
                    text = entry,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}
