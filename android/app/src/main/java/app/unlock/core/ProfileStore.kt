package app.unlock.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.unlock.vpn.UnlockVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Подписки, серверы, выбранный сервер и результаты пинга. Состояние — Compose state,
 * меняется только из главного потока; сеть и libXray — в Dispatchers.IO.
 */
class ProfileStore(private val context: Context) {
    var subscriptions by mutableStateOf<List<Subscription>>(emptyList())
        private set
    var selectedServerId by mutableStateOf<String?>(null)
        private set
    /** Задержка в мс; -1 — сервер не ответил. */
    val pings = mutableStateMapOf<String, Int>()
    var isPinging by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf<Set<String>>(emptySet())
        private set

    private val file = File(context.filesDir, "profiles.json")
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    init {
        subscriptions = runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { Subscription.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        selectedServerId = prefs.getString("selected", null)
    }

    val selectedServer: ServerNode?
        get() {
            val all = subscriptions.flatMap { it.servers }
            return all.firstOrNull { it.id == selectedServerId } ?: all.firstOrNull()
        }

    fun select(server: ServerNode) {
        selectedServerId = server.id
        prefs.edit().putString("selected", server.id).apply()
    }

    /** Записывает outbound выбранного сервера для процесса туннеля. */
    fun writeActiveOutbound(server: ServerNode) {
        val outbound = org.json.JSONObject(server.outboundJson).put("tag", server.name)
        UnlockVpnService.activeOutboundFile(context).writeText(outbound.toString())
    }

    // region Добавление

    /** Ссылка подписки (http/https) или одна/несколько ссылок vless://, trojan:// и т.п. */
    suspend fun add(input: String) {
        val text = input.trim()
        if (text.startsWith("http://") || text.startsWith("https://")) {
            subscriptions.firstOrNull { it.url == text }?.let { refresh(it.id); return }
            val loaded = load(Subscription(title = hostOf(text), url = text))
            subscriptions = subscriptions + loaded
            if (selectedServerId == null) loaded.servers.firstOrNull()?.let(::select)
        } else {
            val servers = withContext(Dispatchers.IO) { parseServers(text) }
            val manual = subscriptions.firstOrNull { it.url == null }
            subscriptions = if (manual != null) {
                subscriptions.map { if (it.id == manual.id) it.copy(servers = it.servers + servers) else it }
            } else {
                subscriptions + Subscription(title = "Мои серверы", url = null, servers = servers)
            }
            if (selectedServerId == null) servers.firstOrNull()?.let(::select)
        }
        save()
    }

    suspend fun refresh(id: String) {
        val current = subscriptions.firstOrNull { it.id == id } ?: return
        if (current.url == null) return
        refreshing = refreshing + id
        try {
            val selectedName = current.servers.firstOrNull { it.id == selectedServerId }?.name
            val updated = load(current)
            subscriptions = subscriptions.map { if (it.id == id) updated else it }
            // После обновления у серверов новые id — сохраняем выбор по имени
            if (selectedName != null) updated.servers.firstOrNull { it.name == selectedName }?.let(::select)
            save()
        } finally {
            refreshing = refreshing - id
        }
    }

    /** Обновляет подписки с истёкшим интервалом. Ошибки молча пропускаем. */
    suspend fun refreshStale() {
        for (subscription in subscriptions.filter { it.isStale }) {
            runCatching { refresh(subscription.id) }
        }
    }

    fun delete(id: String) {
        subscriptions = subscriptions.filterNot { it.id == id }
        save()
    }

    fun deleteServer(server: ServerNode) {
        subscriptions = subscriptions
            .map { it.copy(servers = it.servers.filterNot { s -> s.id == server.id }) }
            .filterNot { it.url == null && it.servers.isEmpty() }
        save()
    }

    // endregion

    /** Пинг одного сервера — для плашки «Пинг» на главном экране. */
    suspend fun ping(server: ServerNode) {
        pings[server.id] = withContext(Dispatchers.IO) { measurePing(server) }
    }

    /** Пингуем все серверы параллельно (до 8 одновременно), результаты — по мере готовности. */
    suspend fun pingAll() {
        if (isPinging) return
        isPinging = true
        pings.clear()
        try {
            coroutineScope {
                val io = Dispatchers.IO.limitedParallelism(8)
                for (server in subscriptions.flatMap { it.servers }) {
                    launch { pings[server.id] = withContext(io) { measurePing(server) } }
                }
            }
        } finally {
            isPinging = false
        }
    }

    /**
     * TCP-пинг: время установки TCP-соединения с адресом и портом сервера
     * (лучшее из двух попыток; DNS резолвится заранее и в замер не входит).
     * Приложение исключено из VPN, поэтому соединение идёт напрямую.
     * Hysteria работает по UDP — TCP-порта у неё нет, для неё меряем через ядро.
     */
    private fun measurePing(server: ServerNode): Int {
        if (server.protocol.startsWith("hysteria")) {
            XrayCore.prepareAppProcess()
            val result = runCatching { XrayCore.ping(listOf(server.outboundJson)) }.getOrNull()?.firstOrNull()
            return if (result?.success == true) result.delay else -1
        }
        return runCatching {
            val address = InetAddress.getByName(server.address)
            (1..2).minOf {
                val start = System.nanoTime()
                Socket().use { socket -> socket.connect(InetSocketAddress(address, server.port), 3000) }
                ((System.nanoTime() - start) / 1_000_000).toInt()
            }
        }.getOrDefault(-1)
    }

    private suspend fun load(subscription: Subscription): Subscription {
        val url = subscription.url ?: return subscription
        val (response, servers) = withContext(Dispatchers.IO) {
            val response = SubscriptionClient.fetch(context, url)
            response to parseServers(response.body)
        }
        return subscription.copy(
            title = response.title ?: subscription.title,
            servers = servers,
            updatedAt = System.currentTimeMillis(),
            usage = response.usage,
            supportUrl = response.supportUrl,
            announce = response.announce,
            updateIntervalHours = response.updateIntervalHours?.takeIf { it > 0 } ?: subscription.updateIntervalHours,
        )
    }

    private fun parseServers(text: String): List<ServerNode> {
        val servers = XrayCore.parseShareLinks(text).map(ServerNode::fromOutbound)
        if (servers.isEmpty()) throw IllegalStateException("Не найдено ни одного сервера")
        return servers
    }

    private fun save() {
        runCatching { file.writeText(JSONArray(subscriptions.map { it.toJson() }).toString()) }
    }

    private fun hostOf(url: String) = runCatching { java.net.URI(url).host }.getOrNull() ?: "Подписка"
}
