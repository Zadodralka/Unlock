package app.unlock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.unlock.core.ServerNode
import app.unlock.ui.HomeScreen
import app.unlock.ui.UnlockTheme
import app.unlock.vpn.UnlockVpnService
import app.unlock.vpn.VpnState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val store get() = (application as UnlockApp).store
    private val vpnState = mutableStateOf(VpnState())
    private val importError = mutableStateOf<String?>(null)
    private var pendingServer: ServerNode? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vpnState.value = VpnState.read(context)
        }
    }

    /** Системный диалог «Разрешить Unlock создать VPN-подключение?» */
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val server = pendingServer
        pendingServer = null
        if (result.resultCode == RESULT_OK && server != null) startTunnel(server)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleDeepLink(intent)
        lifecycleScope.launch { store.refreshStale() }

        setContent {
            UnlockTheme {
                HomeScreen(
                    store = store,
                    vpnState = vpnState.value,
                    importError = importError.value,
                    onDismissImportError = { importError.value = null },
                    onToggle = ::toggle,
                    onSelect = ::select,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, stateReceiver, IntentFilter(VpnState.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        vpnState.value = VpnState.read(this)
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun toggle() {
        val state = vpnState.value
        if (state.isActive) {
            UnlockVpnService.stop(this)
            return
        }
        val server = store.selectedServer
        if (server == null) {
            importError.value = "Добавьте подписку или сервер"
            return
        }
        connect(server)
    }

    private fun select(server: ServerNode) {
        if (server.id == store.selectedServer?.id) return
        store.select(server)
        // Смена сервера на лету
        if (vpnState.value.isActive) connect(server)
    }

    private fun connect(server: ServerNode) {
        val permission = VpnService.prepare(this)
        if (permission != null) {
            pendingServer = server
            vpnPermission.launch(permission)
        } else {
            startTunnel(server)
        }
    }

    private fun startTunnel(server: ServerNode) {
        store.writeActiveOutbound(server)
        UnlockVpnService.start(this)
    }

    /** unlock://add/<ссылка подписки или vless://...> — импорт из бота/сайта в один тап. */
    private fun handleDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "unlock" || data.host != "add") return
        val raw = data.toString().removePrefix("unlock://add/")
        val input = Uri.decode(raw)
        intent.data = null
        lifecycleScope.launch {
            runCatching { store.add(input) }.onFailure { importError.value = it.message ?: "Не удалось импортировать" }
        }
    }
}
