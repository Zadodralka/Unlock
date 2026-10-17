package app.unlock.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.unlock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление приложения из GitHub Releases. CI на каждую сборку публикует релиз
 * с Unlockless.apk и version.json; приложение сравнивает versionCode со своим.
 */
object Updater {
    private const val VERSION_URL =
        "https://github.com/Zadodralka/Unlock/releases/latest/download/version.json"

    data class Update(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
    )

    /** Новая версия, если она есть; null — уже последняя. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val json = JSONObject(open(VERSION_URL).use { it.inputStream.bufferedReader().readText() })
        val update = Update(
            versionCode = json.getInt("versionCode"),
            versionName = json.optString("versionName"),
            notes = json.optString("notes"),
            apkUrl = json.getString("apk"),
        )
        update.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Скачивает APK во внутренний кэш; [onProgress] — от 0 до 1 (или -1, если размер неизвестен). */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "Unlockless-${update.versionName}.apk")

            open(update.apkUrl).use { connection ->
                val total = connection.contentLengthLong
                var done = 0L
                var lastReported = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (done - lastReported > 256 * 1024) {
                                lastReported = done
                                val progress = if (total > 0) done.toFloat() / total else -1f
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }
            }
            target
        }

    /**
     * Открывает системный установщик. Если ещё нет разрешения «Установка
     * из этого источника» — открывает его настройку и возвращает false.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    /** Соединение с переходом по редиректам GitHub (github.com -> objects.githubusercontent.com). */
    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(5) {
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Unlockless/${BuildConfig.VERSION_NAME}")
            when (val code = connection.responseCode) {
                in 200..299 -> return connection
                301, 302, 303, 307, 308 -> {
                    current = URL(current, connection.getHeaderField("Location"))
                    connection.disconnect()
                }
                404 -> throw IllegalStateException("Обновлений пока нет")
                else -> throw IllegalStateException("Сервер обновлений ответил $code")
            }
        }
        throw IllegalStateException("Слишком много перенаправлений")
    }
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try { block(this) } finally { disconnect() }
