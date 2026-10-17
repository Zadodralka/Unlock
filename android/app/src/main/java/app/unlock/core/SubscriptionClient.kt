package app.unlock.core

import android.content.Context
import android.os.Build
import android.util.Base64
import app.unlock.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Загрузка подписки по ссылке. Заголовки x-hwid / x-device-* — те же, что шлёт Happ:
 * по ним Remnawave и другие панели считают устройства и применяют лимит.
 */
object SubscriptionClient {
    data class Response(
        val body: String,
        val title: String?,
        val usage: SubscriptionUsage?,
        val supportUrl: String?,
        val announce: String?,
        val updateIntervalHours: Int?,
    )

    val userAgent: String get() = "Unlock/${BuildConfig.VERSION_NAME}"

    fun fetch(context: Context, url: String): Response {
        val parsed = runCatching { URL(url.trim()) }.getOrNull()
        if (parsed == null || (parsed.protocol != "https" && parsed.protocol != "http")) {
            throw IllegalArgumentException("Некорректная ссылка")
        }
        val connection = parsed.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("x-hwid", DeviceInfo.hwid(context))
            connection.setRequestProperty("x-device-os", "Android")
            connection.setRequestProperty("x-ver-os", Build.VERSION.RELEASE)
            connection.setRequestProperty("x-device-model", Build.MODEL)

            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Сервер подписки ответил $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.contains("<html", ignoreCase = true)) {
                throw IllegalStateException("По ссылке нет подписки (пришла веб-страница)")
            }

            fun header(name: String) = connection.getHeaderField(name)?.ifEmpty { null }
            return Response(
                body = body,
                title = header("profile-title")?.let(::decodeBase64Prefixed),
                usage = header("subscription-userinfo")?.let(::parseUsage),
                supportUrl = header("support-url"),
                announce = header("announce")?.let(::decodeBase64Prefixed),
                updateIntervalHours = header("profile-update-interval")?.toIntOrNull(),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Значения вида "base64:0J/RgNC40LLQtdGC" — так передают не-ASCII текст в заголовках. */
    private fun decodeBase64Prefixed(value: String): String {
        if (!value.startsWith("base64:")) return value
        return runCatching {
            String(Base64.decode(value.removePrefix("base64:"), Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    /** "upload=0; download=123; total=1000; expire=1700000000" */
    private fun parseUsage(value: String): SubscriptionUsage {
        var usage = SubscriptionUsage()
        for (part in value.split(";")) {
            val pair = part.split("=", limit = 2).map { it.trim() }
            val number = pair.getOrNull(1)?.toLongOrNull() ?: continue
            usage = when (pair[0].lowercase()) {
                "upload" -> usage.copy(upload = number)
                "download" -> usage.copy(download = number)
                "total" -> usage.copy(total = number)
                "expire" -> usage.copy(expire = number)
                else -> usage
            }
        }
        return usage
    }
}

object DeviceInfo {
    /** Постоянный идентификатор устройства для лимита устройств в панели. */
    fun hwid(context: Context): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        prefs.getString("hwid", null)?.let { return it }
        val value = UUID.randomUUID().toString()
        prefs.edit().putString("hwid", value).apply()
        return value
    }
}
