package app.unlock.core

import libXray.DialerController
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

/**
 * Обёртка над единственной точкой входа libXray — `LibXray.invoke`.
 * Запрос и ответ — JSON: {"apiVersion":3,"method":...,"payload":{...}} -> {"success","data","error"}.
 */
object XrayCore {
    class XrayException(message: String) : Exception(message)

    data class PingResult(val success: Boolean, val delay: Int)

    /** Go-резолвер на Android по умолчанию ходит на 127.0.0.1:53 и не работает. */
    const val BOOTSTRAP_DNS = "1.1.1.1:53"

    fun invoke(method: String, payload: JSONObject = JSONObject()): Any? {
        val request = JSONObject()
            .put("apiVersion", 3)
            .put("method", method)
            .put("payload", payload)
        val response = JSONObject(LibXray.invoke(request.toString()))
        if (!response.optBoolean("success")) {
            throw XrayException(response.optString("error").ifEmpty { "libXray: ошибка $method" })
        }
        return response.opt("data")
    }

    fun version(): String =
        runCatching { (invoke("xrayVersion") as? JSONObject)?.optString("version") }.getOrNull() ?: "?"

    /**
     * Ссылки vless://, vmess://, trojan://, ss://, hy2:// (по одной на строку),
     * base64-подписка или Xray JSON -> список outbound-объектов.
     */
    fun parseShareLinks(text: String): List<JSONObject> {
        val data = invoke("convertShareLinksToXrayJson", JSONObject().put("text", text)) as? JSONObject
        val outbounds = data?.optJSONArray("outbounds") ?: return emptyList()
        return (0 until outbounds.length()).map { outbounds.getJSONObject(it) }
    }

    /** Реальная задержка через сервер (HTTP-запрос через outbound). Не больше 5 за вызов. */
    fun ping(
        outbounds: List<String>,
        url: String = "https://www.gstatic.com/generate_204",
        timeoutSeconds: Int = 5,
    ): List<PingResult> {
        val configs = JSONArray()
        for (json in outbounds) {
            val outbound = JSONObject(json).put("tag", "ping")
            val config = JSONObject().put("outbounds", JSONArray().put(outbound))
            configs.put(JSONObject().put("xrayJson", config.toString()).put("outboundTag", "ping"))
        }
        val payload = JSONObject().put("configs", configs).put("timeout", timeoutSeconds).put("url", url)
        val results = (invoke("pingBatch", payload) as? JSONObject)?.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map {
            val item = results.getJSONObject(it)
            PingResult(item.optBoolean("success"), item.optInt("delay", -1))
        }
    }

    fun run(config: JSONObject) {
        invoke("runXray", JSONObject().put("xrayJson", config.toString()))
    }

    fun stop() {
        runCatching { invoke("stopXray") }
    }

    /**
     * Сокеты ядра должны идти мимо VPN. В процессе туннеля [protect] вызывает
     * VpnService.protect; в процессе приложения он не нужен — приложение и так
     * исключено из VPN.
     */
    fun setupNetworking(protect: (Int) -> Boolean) {
        val controller = object : DialerController {
            override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
        }
        LibXray.registerDialerController(controller)
        LibXray.setDNS(controller, BOOTSTRAP_DNS)
    }

    fun resetNetworking() {
        LibXray.resetDNS()
    }
}
