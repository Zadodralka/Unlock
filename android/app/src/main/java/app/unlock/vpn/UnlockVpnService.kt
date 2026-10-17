package app.unlock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import app.unlock.MainActivity
import app.unlock.R
import app.unlock.core.TunnelConfig
import app.unlock.core.XrayCore
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Процесс туннеля: поднимает TUN-интерфейс и запускает в нём Xray с TUN-inbound,
 * которому передаётся дескриптор интерфейса.
 */
class UnlockVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            worker.execute { stopTunnel(error = null) }
            return START_NOT_STICKY
        }
        // ACTION_START из приложения или запуск системой (Always-on VPN)
        startForegroundCompat(currentServerName())
        worker.execute { startTunnel() }
        return START_NOT_STICKY
    }

    /** Другой VPN занял систему или пользователь отключил нас в настройках. */
    override fun onRevoke() {
        worker.execute { stopTunnel(error = null) }
    }

    override fun onDestroy() {
        // Обычно туннель уже остановлен; иначе (система убирает сервис) — гасим ядро
        worker.execute { if (tun != null) stopTunnel(error = null) }
        worker.shutdown()
        super.onDestroy()
    }

    private fun startTunnel() {
        // Смена сервера: перезапускаем ядро с новым конфигом
        if (tun != null) shutdownCore()

        val serverName = currentServerName()
        VpnState.write(this, VpnState(VpnState.Status.CONNECTING, serverName = serverName))
        try {
            val file = activeOutboundFile(this)
            if (!file.exists()) throw IllegalStateException("Сервер не выбран")
            val outbound = JSONObject(file.readText())

            val builder = Builder()
                .setSession("Unlock")
                .setMtu(TunnelConfig.MTU)
                .addAddress(TunnelConfig.TUN_ADDRESS, TunnelConfig.TUN_PREFIX)
                .addRoute("0.0.0.0", 0)
                // Собственный трафик приложения (ядро, пинг, загрузка подписок) — мимо туннеля
                .addDisallowedApplication(packageName)
                .setConfigureIntent(openAppIntent())
            TunnelConfig.DNS_SERVERS.forEach { builder.addDnsServer(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            val descriptor = builder.establish()
                ?: throw IllegalStateException("Нет разрешения на VPN — откройте приложение и подключитесь заново")
            tun = descriptor

            XrayCore.setupNetworking { fd -> protect(fd) }
            val logFile = File(filesDir, LOG_FILE).apply { writeText("") }
            XrayCore.run(TunnelConfig.xrayConfig(outbound, descriptor.fd, logFile.absolutePath))

            VpnState.write(
                this,
                VpnState(VpnState.Status.CONNECTED, since = System.currentTimeMillis(), serverName = serverName),
            )
        } catch (e: Throwable) {
            stopTunnel(error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun shutdownCore() {
        XrayCore.stop()
        runCatching { XrayCore.resetNetworking() }
        runCatching { tun?.close() }
        tun = null
    }

    private fun stopTunnel(error: String?) {
        if (tun != null) VpnState.write(this, VpnState(VpnState.Status.DISCONNECTING))
        shutdownCore()
        VpnState.write(this, VpnState(VpnState.Status.DISCONNECTED, error = error))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun currentServerName(): String? =
        runCatching { JSONObject(activeOutboundFile(this).readText()).optString("tag").ifEmpty { null } }.getOrNull()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startForegroundCompat(serverName: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, UnlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Unlock подключён")
            .setContentText(serverName ?: "VPN")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Отключить", stopIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val ACTION_START = "app.unlock.START"
        private const val ACTION_STOP = "app.unlock.STOP"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1
        const val LOG_FILE = "xray.log"

        /** Outbound выбранного сервера — его поднимает туннель. */
        fun activeOutboundFile(context: Context) = File(context.filesDir, "active-outbound.json")

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UnlockVpnService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, UnlockVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
