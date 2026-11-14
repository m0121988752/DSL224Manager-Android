package com.personal.dsl224manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * نموذج قالب إعدادات الراوتر الجاهز والمشارك (Router Preset Template)
 */
data class RouterPreset(
    val id: String,
    val name: String,
    val manufacturer: String,
    val defaultIP: String,
    val firmware: FirmwareType,
    val description: String,
    val webUIPath: String = "",
    val customUsageSelector: String = "",
    val customUsageRegex: String = "",
    val customUsageUnit: String = "Auto",
    val customDeviceTableSelector: String = "",
    val customRebootButtonSelector: String = "",
    val customUsernameSelector: String = "",
    val customPasswordSelector: String = "",
    val customLoginButtonSelector: String = ""
)

/**
 * مدير ومكتبة القوالب الجاهزة والتصدير والاستيراد لراوترات العالم
 */
object RouterPresetsManager {
    val builtinPresets: List<RouterPreset> = listOf(
        RouterPreset(
            id = "dlink_dsl224",
            name = "D-Link DSL-224 (ME_1.10 Boa)",
            manufacturer = "D-Link",
            defaultIP = "192.168.1.1",
            firmware = FirmwareType.ME110_BOA,
            description = "راوتر D-Link DSL-224 بنظام Boa الافتراضي (WE / فودافون / أورنج)",
            webUIPath = "",
            customUsageSelector = "td:contains('WAN'), .traffic-stat, td:contains('Bytes')",
            customUsageRegex = """(\d{7,16})""",
            customUsageUnit = "Bytes",
            customDeviceTableSelector = "#dhcp_client_table, tr.device-row, table.status_table",
            customRebootButtonSelector = "#btn_reboot, input[value*='Reboot' i]",
            customUsernameSelector = "input[name='username'], #f_username",
            customPasswordSelector = "input[name='password'], #f_password",
            customLoginButtonSelector = "#loginbtn, input[type='submit']"
        ),
        RouterPreset(
            id = "zte_h168n",
            name = "ZTE ZXHN H168N / H188A",
            manufacturer = "ZTE",
            defaultIP = "192.168.1.1",
            firmware = FirmwareType.GENERIC_REALTEK,
            description = "راوتر VDSL الأكثر انتشاراً من WE وفودافون وأورنج في مصر والشرق الأوسط",
            webUIPath = "",
            customUsageSelector = "#wan_bytes, #div_wan_traffic, td.bytes_val, td.traffic_val",
            customUsageRegex = """([0-9\.]+)\s*(GB|MB|KB)""",
            customUsageUnit = "Auto",
            customDeviceTableSelector = "#table_dhcp_clients, #dev_table, tr.client_item",
            customRebootButtonSelector = "#btn_reboot, #reboot, input[value='Reboot']",
            customUsernameSelector = "#username, input[name='Username']",
            customPasswordSelector = "#password, input[name='Password']",
            customLoginButtonSelector = "#loginbtn, input[name='login_submit']"
        ),
        RouterPreset(
            id = "huawei_b311",
            name = "Huawei B311-221 (HiLink 4G)",
            manufacturer = "Huawei",
            defaultIP = "192.168.8.1",
            firmware = FirmwareType.HUAWEI_B311,
            description = "راوترات هواوي 4G الهوائية لشركات الاتصالات (Orange, Zain, STC, WE)",
            webUIPath = "",
            customUsageSelector = "#traffic_used, span.data_used, div.flow_val",
            customUsageRegex = """([0-9\.]+)\s*(GB|MB)""",
            customUsageUnit = "Auto",
            customDeviceTableSelector = "#device_table, .dev-list, div.host_list",
            customRebootButtonSelector = "#reboot_apply_button, #btn_reboot",
            customUsernameSelector = "#username, input[name='username']",
            customPasswordSelector = "#password, input[name='password']",
            customLoginButtonSelector = "#login_btn, #btnLogin"
        ),
        RouterPreset(
            id = "vodafone_h300s",
            name = "Vodafone VDSL (H300s / DG8045)",
            manufacturer = "Vodafone / Sercomm",
            defaultIP = "192.168.1.1",
            firmware = FirmwareType.GENERIC_REALTEK,
            description = "راوتر فودافون VDSL المنزلي الشهير H300s وراوترات الشريحة",
            webUIPath = "",
            customUsageSelector = "#Internet_Stats, .traffic-container, td.stat-data",
            customUsageRegex = """([0-9\.]+)\s*(GB|MB)""",
            customUsageUnit = "Auto",
            customDeviceTableSelector = "#Ethernet_Clients, #WLAN_Clients, .device-list",
            customRebootButtonSelector = "#Reboot_btn, button[name='Reboot']",
            customUsernameSelector = "input[name='user'], #user",
            customPasswordSelector = "input[name='pass'], #pass",
            customLoginButtonSelector = "#loginBtn, button[type='submit']"
        ),
        RouterPreset(
            id = "tplink_vr600",
            name = "TP-Link Archer / VR600",
            manufacturer = "TP-Link",
            defaultIP = "192.168.1.1",
            firmware = FirmwareType.GENERIC_REALTEK,
            description = "راوترات تي بي لينك VDSL الحديثة والراوترات الهوائية",
            webUIPath = "",
            customUsageSelector = "#traffic_status, #wan_rx_bytes, .stat-value",
            customUsageRegex = """([0-9\.]+)\s*(GB|MB|KB)""",
            customUsageUnit = "Auto",
            customDeviceTableSelector = "#client_list_table, #dhcpTable, tr.grid-row",
            customRebootButtonSelector = "#reboot_button, #btn_reboot, .reboot-btn",
            customUsernameSelector = "input[name='userName'], #userName",
            customPasswordSelector = "input[name='pcPassword'], #pcPassword",
            customLoginButtonSelector = "#login_button, .login-btn"
        ),
        RouterPreset(
            id = "orange_flybox_mf283",
            name = "Orange Home 4G Flybox (MF283+)",
            manufacturer = "ZTE / Orange",
            defaultIP = "192.168.0.1",
            firmware = FirmwareType.GENERIC_REALTEK,
            description = "راوتر أورنج الهوائي المنزلي Home 4G (موديل ZTE MF283+)",
            webUIPath = "",
            customUsageSelector = "#traffic_data, #traffic_flow_data, .flow-data",
            customUsageRegex = """([0-9\.]+)\s*(GB|MB)""",
            customUsageUnit = "Auto",
            customDeviceTableSelector = "#connected_dev_list, #devList, tr.dev_item",
            customRebootButtonSelector = "#restart_btn, input[value='Restart']",
            customUsernameSelector = "input[name='user'], #txtUser",
            customPasswordSelector = "input[name='password'], #txtPwd",
            customLoginButtonSelector = "#btnLogin, input[value='Login']"
        )
    )

    /**
     * تصدير قالب راوتر إلى JSON آمن (تجريد كلمة المرور لحماية الخصوصية تماماً)
     */
    fun exportTemplate(profile: RouterProfile): String {
        val obj = JSONObject().apply {
            put("dsl224_template_version", 1)
            put("name", profile.name)
            put("defaultIP", profile.routerIP)
            put("firmware", profile.firmware.displayName)
            put("firmwareKey", profile.firmware.name)
            put("webUIPath", profile.webUIPath)
            put("customUsageSelector", profile.customUsageSelector)
            put("customUsageRegex", profile.customUsageRegex)
            put("customUsageUnit", profile.customUsageUnit)
            put("customDeviceTableSelector", profile.customDeviceTableSelector)
            put("customRebootButtonSelector", profile.customRebootButtonSelector)
            put("customUsernameSelector", profile.customUsernameSelector)
            put("customPasswordSelector", profile.customPasswordSelector)
            put("customLoginButtonSelector", profile.customLoginButtonSelector)
        }
        return obj.toString(2)
    }

    /**
     * استيراد قالب راوتر من كود JSON وتطبيقه على الراوتر المحدد
     */
    fun importTemplate(jsonString: String, target: RouterProfile): Boolean {
        return try {
            val obj = JSONObject(jsonString.trim())
            if (!obj.has("dsl224_template_version") && !obj.has("customUsageSelector") && !obj.has("customDeviceTableSelector")) {
                // ليس قالباً معروفاً
                return false
            }

            val name = obj.optString("name", "")
            if (name.isNotBlank()) target.name = name

            val ip = obj.optString("defaultIP", obj.optString("routerIP", ""))
            if (ip.isNotBlank()) target.routerIP = ip

            val fwKey = obj.optString("firmwareKey", "")
            val fwDisplay = obj.optString("firmware", "")
            target.firmware = when {
                fwKey == "HUAWEI_B311" || fwDisplay.contains("B311", ignoreCase = true) || fwDisplay.contains("Huawei", ignoreCase = true) ->
                    FirmwareType.HUAWEI_B311
                fwKey == "ME110_BOA" || fwDisplay.contains("224", ignoreCase = true) || fwDisplay.contains("Boa", ignoreCase = true) ->
                    FirmwareType.ME110_BOA
                else -> FirmwareType.GENERIC_REALTEK
            }

            if (obj.has("webUIPath")) target.webUIPath = obj.optString("webUIPath", "")
            if (obj.has("customUsageSelector")) target.customUsageSelector = obj.optString("customUsageSelector", "")
            if (obj.has("customUsageRegex")) target.customUsageRegex = obj.optString("customUsageRegex", "")
            if (obj.has("customUsageUnit")) target.customUsageUnit = obj.optString("customUsageUnit", "Auto")
            if (obj.has("customDeviceTableSelector")) target.customDeviceTableSelector = obj.optString("customDeviceTableSelector", "")
            if (obj.has("customRebootButtonSelector")) target.customRebootButtonSelector = obj.optString("customRebootButtonSelector", "")
            if (obj.has("customUsernameSelector")) target.customUsernameSelector = obj.optString("customUsernameSelector", "")
            if (obj.has("customPasswordSelector")) target.customPasswordSelector = obj.optString("customPasswordSelector", "")
            if (obj.has("customLoginButtonSelector")) target.customLoginButtonSelector = obj.optString("customLoginButtonSelector", "")

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * تطبيق قالب جاهز على راوتر
     */
    fun applyPreset(preset: RouterPreset, target: RouterProfile): RouterProfile {
        return target.copy(
            name = preset.name,
            routerIP = preset.defaultIP,
            firmware = preset.firmware,
            webUIPath = preset.webUIPath,
            customUsageSelector = preset.customUsageSelector,
            customUsageRegex = preset.customUsageRegex,
            customUsageUnit = preset.customUsageUnit,
            customDeviceTableSelector = preset.customDeviceTableSelector,
            customRebootButtonSelector = preset.customRebootButtonSelector,
            customUsernameSelector = preset.customUsernameSelector,
            customPasswordSelector = preset.customPasswordSelector,
            customLoginButtonSelector = preset.customLoginButtonSelector
        )
    }
}

/**
 * حوار استعراض مكتبة القوالب وتصدير / استيراد الإعدادات
 */
@Composable
fun PresetsLibraryDialog(
    currentProfile: RouterProfile,
    onDismiss: () -> Unit,
    onApplyProfile: (RouterProfile) -> Unit,
    onSelectPreset: ((RouterPreset) -> Unit)? = null
) {
    val context = LocalContext.current
    var toastMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFFFF9F0A))
                Spacer(Modifier.width(8.dp))
                Text("مكتبة قوالب الراوترات الجاهزة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // إشعار أو تنبيه
                toastMessage?.let { msg ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF30D158).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(msg, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { toastMessage = null }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // بطاقة إجراءات المشاركة والاستيراد السريع
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "مشاركة واستيراد القوالب (Share & Import)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // زر التصدير
                                Button(
                                    onClick = {
                                        val json = RouterPresetsManager.exportTemplate(currentProfile)
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("RouterTemplate", json))
                                        toastMessage = "تم نسخ قالب راوترك للحافظة بنجاح! 📋"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF).copy(alpha = 0.25f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("تصدير ومشاركة", fontSize = 11.sp, color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                                }

                                // زر الاستيراد
                                Button(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                        if (clip.isNotBlank()) {
                                            val clone = currentProfile.copy()
                                            if (RouterPresetsManager.importTemplate(clip, clone)) {
                                                onApplyProfile(clone)
                                                toastMessage = "تم استيراد وتطبيق القالب من الحافظة! ✅"
                                            } else {
                                                toastMessage = "النص في الحافظة ليس كود قالب راوتر صالح ⚠️"
                                            }
                                        } else {
                                            toastMessage = "الحافظة فارغة! انسخ كود القالب أولاً 📋"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBF5AF2).copy(alpha = 0.25f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFBF5AF2), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("استيراد من الحافظة", fontSize = 11.sp, color = Color(0xFFBF5AF2), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // عنوان القوالب الجاهزة
                item {
                    Text(
                        "قوالب الراوترات المعتمدة الجاهزة (${RouterPresetsManager.builtinPresets.count()} راوترات)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }

                // قائمة بطاقات القوالب
                items(RouterPresetsManager.builtinPresets) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalAlignment = Alignment.Start) {
                                    Text(preset.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text(
                                        "${preset.manufacturer} • ${preset.defaultIP}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Text(
                                    preset.manufacturer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0A84FF),
                                    modifier = Modifier
                                        .background(Color(0xFF0A84FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                preset.description,
                                fontSize = 11.sp,
                                color = Color(0xFFCCCCCC),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("مُعد مسبقاً ومعتمد", fontSize = 10.sp, color = Color(0xFF30D158))
                                }

                                Button(
                                    onClick = {
                                        if (onSelectPreset != null) {
                                            onSelectPreset(preset)
                                        } else {
                                            val updated = RouterPresetsManager.applyPreset(preset, currentProfile)
                                            onApplyProfile(updated)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("تطبيق هذا القالب", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", fontWeight = FontWeight.Bold)
            }
        }
    )
}
