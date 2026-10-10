package com.gxdevs.screenx.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val isUpdateAvailable: Boolean,
    val currentVersion: String
)

sealed class UpdateCheckResult {
    data class Available(val updateInfo: AppUpdateInfo) : UpdateCheckResult()
    data class Latest(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateManager {

    private const val GITHUB_API_URL = "https://api.github.com/repos/gtxPrime/screen-x/releases/latest"
    private const val GITHUB_RELEASES_URL = "https://github.com/gtxPrime/screen-x/releases"

    /**
     * Checks GitHub releases API for the latest published release.
     * Compares the remote tag name against the currently installed versionName.
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Exception) {
                "1.0.0"
            }

            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ScreenX-Android-App")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("GitHub responded with HTTP $responseCode")
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            val rawTag = root.optString("tag_name", "").trim()
            val cleanTag = rawTag.removePrefix("v").trim()
            val releaseName = root.optString("name", rawTag).ifEmpty { rawTag }
            val releaseNotes = root.optString("body", "").trim()
            val htmlUrl = root.optString("html_url", GITHUB_RELEASES_URL)

            var apkDownloadUrl: String? = null
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val dl = asset.optString("browser_download_url", "")
                        apkDownloadUrl = if (dl.isNotEmpty()) dl else null
                        break
                    }
                }
            }

            val hasUpdate = isNewerVersion(currentVersion = currentVersion, latestVersion = cleanTag)

            val updateInfo = AppUpdateInfo(
                latestVersion = cleanTag,
                releaseTitle = releaseName,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkDownloadUrl,
                htmlUrl = htmlUrl,
                isUpdateAvailable = hasUpdate,
                currentVersion = currentVersion
            )

            if (hasUpdate) {
                UpdateCheckResult.Available(updateInfo)
            } else {
                UpdateCheckResult.Latest(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    /**
     * Determines whether [latestVersion] is strictly newer than [currentVersion]
     * using semantic versioning comparison (e.g. 1.3.1 > 1.3.0, 1.4.0 > 1.3.0).
     */
    fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v")
        val cleanLatest = latestVersion.trim().removePrefix("v")

        if (cleanCurrent.equals(cleanLatest, ignoreCase = true)) return false

        val currentParts = cleanCurrent.split(".", "-").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".", "-").mapNotNull { it.toIntOrNull() }

        if (currentParts.isEmpty() || latestParts.isEmpty()) {
            return cleanLatest > cleanCurrent
        }

        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
    }

    /**
     * Opens the direct APK download URL or GitHub releases page in the user's browser.
     */
    fun downloadOrOpenUpdate(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens any webpage URL in the user's browser.
     */
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
