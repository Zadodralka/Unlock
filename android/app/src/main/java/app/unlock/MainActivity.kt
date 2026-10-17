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
import android.os.SystemClock
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import kotlinx.coroutines.delay
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

    /** Запустить появление интерфейса — когда заставка начинает уходить. */
    private val playIntro = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        setupSplash(splash, restored = savedInstanceState != null)
        // Интерфейс всегда тёмный — светлые значки в системных панелях
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
                    playIntro = playIntro.value,
                )
            }
        }
    }

    /**
     * Заставка: на Android 12+ щит прорисовывается (avd_splash) — даём анимации
     * доиграть, затем логотип увеличивается и растворяется, а интерфейс выезжает.
     */
    private fun setupSplash(splash: SplashScreen, restored: Boolean) {
        if (restored) {
            // Поворот экрана и т.п. — заставки нет, интерфейс сразу на месте
            playIntro.value = true
            return
        }
        val shownAt = SystemClock.uptimeMillis()
        val holdMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 850L else 300L
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - shownAt < holdMillis }

        splash.setOnExitAnimationListener { provider ->
            playIntro.value = true
            val icon = provider.iconView
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.8f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.8f),
                    ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f),
                )
                duration = 420
                interpolator = AccelerateInterpolator()
                doOnEnd { provider.remove() }
                start()
            }
        }
        // Страховка: если система не показала заставку, всё равно показать интерфейс
        lifecycleScope.launch {
            delay(holdMillis + 1200)
            playIntro.value = true
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
        intent?.data = null
        lifecycleScope.launch {
            runCatching { store.add(input) }.onFailure { importError.value = it.message ?: "Не удалось импортировать" }
        }
    }
}
