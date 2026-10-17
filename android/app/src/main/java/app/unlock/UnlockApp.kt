package app.unlock

import android.app.Application
import app.unlock.core.ProfileStore
import app.unlock.core.XrayCore

class UnlockApp : Application() {
    lateinit var store: ProfileStore
        private set

    override fun onCreate() {
        super.onCreate()
        // Application создаётся и в процессе туннеля (:vpn) — там хранилище не нужно
        if (isMainProcess()) {
            // Приложение исключено из VPN, так что защищать сокеты ядра не нужно —
            // нужен только рабочий DNS для Go-резолвера (пинг серверов по домену)
            runCatching { XrayCore.setupNetworking { true } }
            store = ProfileStore(this)
        }
    }

    private fun isMainProcess(): Boolean = getProcessName() == packageName
}
