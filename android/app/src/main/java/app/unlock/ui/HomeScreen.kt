package app.unlock.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlock.core.ProfileStore
import app.unlock.core.ServerNode
import app.unlock.core.Subscription
import app.unlock.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Главный экран: шапка, карточка подписки, большая кнопка подключения
 * и карточка текущего сервера, по нажатию — шторка со списком серверов.
 */
@Composable
fun HomeScreen(
    store: ProfileStore,
    vpnState: VpnState,
    importError: String?,
    onDismissImportError: () -> Unit,
    onToggle: () -> Unit,
    onSelect: (ServerNode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showServers by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    LaunchedEffect(importError) {
        if (importError != null) {
            snackbar.showSnackbar(importError)
            onDismissImportError()
        }
    }

    val selected = store.selectedServer
    val subscription = selected?.let { s -> store.subscriptions.firstOrNull { s in it.servers } }
        ?: store.subscriptions.firstOrNull { it.url != null }

    Box(Modifier.fillMaxSize().background(UnlockColors.ScreenBrush)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onAdd = { showAdd = true }, onSettings = { showSettings = true })

            if (subscription != null && subscription.url != null) {
                Spacer(Modifier.height(8.dp))
                SubscriptionCard(
                    subscription = subscription,
                    isRefreshing = subscription.id in store.refreshing,
                    onRefresh = {
                        scope.launch {
                            runCatching { store.refresh(subscription.id) }
                                .onSuccess { showMessage("Подписка обновлена") }
                                .onFailure { showMessage(it.message ?: "Не удалось обновить") }
                        }
                    },
                    onDelete = { store.delete(subscription.id) },
                )
            }

            Spacer(Modifier.weight(1f))
            ConnectButton(vpnState.status, onToggle)
            StatusBlock(vpnState)
            Spacer(Modifier.weight(1f))

            if (selected != null) {
                CurrentServerCard(selected, store.pings[selected.id]) { showServers = true }
            } else {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить подписку", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = UnlockColors.SurfaceHigh,
                contentColor = UnlockColors.TextPrimary,
            )
        }
    }

    if (showServers) {
        ServersSheet(
            store = store,
            onSelect = { onSelect(it); showServers = false },
            onDismiss = { showServers = false },
        )
    }
    if (showAdd) {
        AddSheet(
            onAdd = { input -> store.add(input) },
            onAdded = { showMessage("Готово — серверы добавлены") },
            onDismiss = { showAdd = false },
        )
    }
    if (showSettings) SettingsSheet(onDismiss = { showSettings = false }, onMessage = showMessage)
}

@Composable
private fun TopBar(onAdd: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF9A8CFF), Color(0xFF4A37D6)))),
            contentAlignment = Alignment.Center,
        ) {
            LockGlyph(Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Unlock", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        RoundIconButton(onAdd) { Icon(Icons.Default.Add, "Добавить") }
        Spacer(Modifier.width(8.dp))
        RoundIconButton(onSettings) { Icon(Icons.Default.Settings, "Настройки") }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = UnlockColors.Surface,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val usage = subscription.usage

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = UnlockColors.Surface,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(
                        subscription.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    expiryText(usage?.expire ?: 0)?.let { (text, color) ->
                        Text(text, color = color, fontSize = 13.sp)
                    }
                }
                if (isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Обновить", tint = UnlockColors.TextSecondary)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "Меню", tint = UnlockColors.TextSecondary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        subscription.url?.let { url ->
                            DropdownMenuItem(
                                text = { Text("Скопировать ссылку") },
                                onClick = { menu = false; copyToClipboard(context, url) },
                            )
                        }
                        subscription.supportUrl?.let { url ->
                            DropdownMenuItem(
                                text = { Text("Поддержка") },
                                onClick = {
                                    menu = false
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить подписку", color = UnlockColors.Danger) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }

            if (usage != null) {
                Spacer(Modifier.height(10.dp))
                val used = Formatter.formatShortFileSize(context, usage.used)
                if (usage.total > 0) {
                    val fraction = (usage.used.toFloat() / usage.total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (fraction > 0.9f) UnlockColors.Danger else UnlockColors.Primary,
                        trackColor = UnlockColors.SurfaceHigh,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$used из ${Formatter.formatShortFileSize(context, usage.total)}",
                        color = UnlockColors.TextSecondary, fontSize = 13.sp,
                    )
                } else {
                    Text("Израсходовано $used · безлимит", color = UnlockColors.TextSecondary, fontSize = 13.sp)
                }
            }

            subscription.announce?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it, fontSize = 13.sp, color = UnlockColors.TextPrimary,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .fillMaxWidth()
                        .background(UnlockColors.Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                )
            }
        }
    }
}

/** «Осталось 12 дней» — цвет темнеет к концу срока. */
private fun expiryText(expire: Long): Pair<String, Color>? {
    if (expire <= 0) return null
    val days = TimeUnit.MILLISECONDS.toDays(expire * 1000 - System.currentTimeMillis())
    return when {
        days < 0 -> "Подписка истекла" to UnlockColors.Danger
        days == 0L -> "Истекает сегодня" to UnlockColors.Danger
        days <= 3 -> "Осталось ${days} ${daysWord(days)}" to UnlockColors.Warning
        else -> "Осталось ${days} ${daysWord(days)}" to UnlockColors.TextSecondary
    }
}

private fun daysWord(n: Long): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1L -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
    }
}

@Composable
private fun StatusBlock(state: VpnState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        AnimatedContent(
            targetState = state.status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "status",
        ) { status ->
            Text(
                when (status) {
                    VpnState.Status.CONNECTED -> "Защищено"
                    else -> status.title
                },
                style = MaterialTheme.typography.headlineMedium,
                color = when (status) {
                    VpnState.Status.CONNECTED -> UnlockColors.Accent
                    VpnState.Status.DISCONNECTED -> UnlockColors.TextPrimary
                    else -> UnlockColors.Warning
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        if (state.status == VpnState.Status.CONNECTED && state.since > 0) {
            ConnectedTimer(state.since)
        } else if (state.status == VpnState.Status.DISCONNECTED) {
            Text("Нажмите, чтобы подключиться", color = UnlockColors.TextSecondary, fontSize = 14.sp)
        }
        AnimatedVisibility(state.error != null && state.status == VpnState.Status.DISCONNECTED) {
            Text(
                state.error.orEmpty(),
                color = UnlockColors.Danger, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun ConnectedTimer(since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    Text(
        "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
        fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Medium,
        color = UnlockColors.TextSecondary,
    )
}

@Composable
private fun CurrentServerCard(server: ServerNode, ping: Int?, onClick: () -> Unit) {
    val title = splitFlag(server.name)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = UnlockColors.Surface,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(title, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Текущая локация", style = SectionLabel)
                Text(
                    title.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            ping?.let { PingBadge(it) }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, "Выбрать сервер",
                tint = UnlockColors.TextSecondary, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
