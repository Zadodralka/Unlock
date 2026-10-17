package app.unlock.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Один сервер: имя из ссылки и outbound-объект Xray в виде JSON-текста. */
data class ServerNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val transport: String,
    val outboundJson: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("protocol", protocol).put("address", address)
        .put("port", port).put("transport", transport).put("outbound", outboundJson)

    companion object {
        fun fromOutbound(outbound: JSONObject): ServerNode {
            val settings = outbound.optJSONObject("settings") ?: JSONObject()
            val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
            val network = stream.optString("network", "raw")
            val security = stream.optString("security", "none")
            return ServerNode(
                name = outbound.optString("tag").ifEmpty { "Без названия" },
                protocol = outbound.optString("protocol", "?"),
                address = settings.optString("address"),
                port = settings.optInt("port"),
                transport = if (security == "none") network else "$network+$security",
                outboundJson = outbound.toString(),
            )
        }

        fun fromJson(json: JSONObject) = ServerNode(
            id = json.getString("id"),
            name = json.optString("name"),
            protocol = json.optString("protocol"),
            address = json.optString("address"),
            port = json.optInt("port"),
            transport = json.optString("transport"),
            outboundJson = json.getString("outbound"),
        )
    }
}

/** Данные из заголовка subscription-userinfo. */
data class SubscriptionUsage(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    /** Unix-время окончания, 0 — бессрочно. */
    val expire: Long = 0,
) {
    val used: Long get() = upload + download

    fun toJson(): JSONObject = JSONObject()
        .put("upload", upload).put("download", download).put("total", total).put("expire", expire)

    companion object {
        fun fromJson(json: JSONObject) = SubscriptionUsage(
            json.optLong("upload"), json.optLong("download"), json.optLong("total"), json.optLong("expire"),
        )
    }
}

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** null — группа серверов, добавленных отдельными ссылками. */
    val url: String?,
    val servers: List<ServerNode> = emptyList(),
    val updatedAt: Long = 0,
    val usage: SubscriptionUsage? = null,
    val supportUrl: String? = null,
    val announce: String? = null,
    /** Интервал автообновления из profile-update-interval, в часах. */
    val updateIntervalHours: Int = 12,
) {
    val isStale: Boolean
        get() = url != null && System.currentTimeMillis() - updatedAt > updateIntervalHours * 3_600_000L

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("url", url ?: JSONObject.NULL)
        .put("servers", JSONArray(servers.map { it.toJson() }))
        .put("updatedAt", updatedAt)
        .put("usage", usage?.toJson() ?: JSONObject.NULL)
        .put("supportUrl", supportUrl ?: JSONObject.NULL)
        .put("announce", announce ?: JSONObject.NULL)
        .put("updateIntervalHours", updateIntervalHours)

    companion object {
        fun fromJson(json: JSONObject): Subscription {
            val servers = json.optJSONArray("servers") ?: JSONArray()
            return Subscription(
                id = json.getString("id"),
                title = json.optString("title"),
                url = json.optStringOrNull("url"),
                servers = (0 until servers.length()).map { ServerNode.fromJson(servers.getJSONObject(it)) },
                updatedAt = json.optLong("updatedAt"),
                usage = json.optJSONObject("usage")?.let(SubscriptionUsage::fromJson),
                supportUrl = json.optStringOrNull("supportUrl"),
                announce = json.optStringOrNull("announce"),
                updateIntervalHours = json.optInt("updateIntervalHours", 12),
            )
        }
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifEmpty { null }
