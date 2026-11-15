package com.personal.dsl224manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val hasUpdate: Boolean,
    val remoteVersionName: String,
    val remoteVersionCode: Int,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String
)

object AppUpdateService {
    private const val GITHUB_REPO = "m0121988752/DSL224Manager-Android"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersionInfo(context: Context): Pair<Int, String> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            Pair(code, pInfo.versionName ?: "1.1")
        } catch (e: Exception) {
            Pair(100, "1.1")
        }
    }

    suspend fun checkLatestRelease(context: Context): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val (currentCode, _) = getCurrentVersionInfo(context)

            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "DSL224Manager-Android")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
            }

            val jsonString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val json = JSONObject(jsonString)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val notes = json.optString("body", "لا توجد تفاصيل إضافية لهذا التحديث.")
            val publishedAt = json.optString("published_at", "")

            // استخراج رقم البناء من تاج الإصدار
            val runNumber = tagName.substringAfterLast(".").filter { it.isDigit() }.toIntOrNull() ?: 0
            val remoteVersionCode = if (runNumber > 0) 100 + runNumber else 100

            // استخراج رابط تحميل حزمة APK
            var downloadUrl = ""
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith("-release.apk") || assetName.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        if (assetName.contains("release")) break
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                downloadUrl = "https://github.com/$GITHUB_REPO/releases/latest"
            }

            val hasUpdate = remoteVersionCode > currentCode

            Result.success(
                AppReleaseInfo(
                    hasUpdate = hasUpdate,
                    remoteVersionName = tagName,
                    remoteVersionCode = remoteVersionCode,
                    releaseTitle = title,
                    releaseNotes = notes,
                    downloadUrl = downloadUrl,
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openDownloadPage(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
