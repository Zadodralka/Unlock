package app.unlock.core

import org.json.JSONArray
import org.json.JSONObject

/** Сетевые параметры туннеля и полный конфиг Xray вокруг outbound выбранного сервера. */
object TunnelConfig {
    const val MTU = 1500
    const val TUN_ADDRESS = "172.19.0.1"
    const val TUN_PREFIX = 30

    /** DNS, который получает система. Запросы к нему перехватывает Xray и резолвит через прокси. */
    val DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")

    /** Локальные сети идут мимо прокси (роутер, принтеры, Chromecast). */
    private val directIPs = listOf(
        "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4",
    )

    fun xrayConfig(outbound: JSONObject, tunFd: Int, logPath: String): JSONObject {
        val proxy = JSONObject(outbound.toString()).put("tag", "proxy")

        val tunInbound = JSONObject()
            .put("tag", "tun")
            .put("protocol", "tun")
            .put("port", 0)
            .put("settings", JSONObject().put("name", "tun0").put("mtu", MTU))
            .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray(listOf("http", "tls"))))

        val rules = JSONArray()
            .put(
                JSONObject().put("type", "field").put("inboundTag", JSONArray(listOf("tun")))
                    .put("port", "53").put("outboundTag", "dns-out")
            )
            .put(JSONObject().put("type", "field").put("ip", JSONArray(directIPs)).put("outboundTag", "direct"))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning").put("access", "none").put("error", logPath))
            // Xray-core берёт дескриптор TUN из этой переменной
            .put("env", JSONObject().put("xray.tun.fd", tunFd.toString()))
            .put(
                "dns",
                JSONObject()
                    .put("servers", JSONArray(DNS_SERVERS))
                    // Без AAAA приложения не пытаются ходить по IPv6, который в туннель не заведён
                    .put("queryStrategy", "UseIPv4")
            )
            .put("inbounds", JSONArray().put(tunInbound))
            // Первый outbound — маршрут по умолчанию
            .put(
                "outbounds",
                JSONArray()
                    .put(proxy)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
            )
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
    }
}
