package app.unlock.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.File

/**
 * Состояние туннеля. Сервис работает в отдельном процессе, поэтому состояние
 * пишется в файл (его читает приложение при открытии) и рассылается broadcast'ом.
 */
data class VpnState(
    val status: Status = Status.DISCONNECTED,
    /** Время подключения, мс. */
    val since: Long = 0,
    val error: String? = null,
    val serverName: String? = null,
) {
    enum class Status(val title: String) {
        DISCONNECTED("Не подключено"),
        CONNECTING("Подключение…"),
        CONNECTED("Подключено"),
        DISCONNECTING("Отключение…"),
    }

    val isActive: Boolean get() = status == Status.CONNECTED || status == Status.CONNECTING

    companion object {
        const val ACTION_CHANGED = "app.unlock.VPN_STATE_CHANGED"

        private fun file(context: Context) = File(context.filesDir, "vpn-state.json")

        fun write(context: Context, state: VpnState) {
            val json = JSONObject()
                .put("status", state.status.name)
                .put("since", state.since)
                .put("error", state.error ?: JSONObject.NULL)
                .put("server", state.serverName ?: JSONObject.NULL)
            runCatching { file(context).writeText(json.toString()) }
            context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
        }

        fun read(context: Context): VpnState {
            val state = runCatching {
                val json = JSONObject(file(context).readText())
                VpnState(
                    status = Status.valueOf(json.getString("status")),
                    since = json.optLong("since"),
                    error = if (json.isNull("error")) null else json.optString("error"),
                    serverName = if (json.isNull("server")) null else json.optString("server"),
                )
            }.getOrDefault(VpnState())
            // Если процесс туннеля убила система, файл мог остаться «подключённым»
            if (state.isActive && !isVpnNetworkUp(context)) return state.copy(status = Status.DISCONNECTED)
            return state
        }

        private fun isVpnNetworkUp(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            return manager.allNetworks.any {
                manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }
    }
}
