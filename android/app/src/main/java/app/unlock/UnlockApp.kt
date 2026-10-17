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
            store = ProfileStore(this)
            // Ядро грузим в фоне: в главном потоке это давало чёрный экран при запуске
            Thread { XrayCore.prepareAppProcess() }.start()
        }
    }

    private fun isMainProcess(): Boolean = Application.getProcessName() == packageName
}
