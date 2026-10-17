package app.unlock.ui

import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
import android.os.Process
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlock.R
import app.unlock.core.ProfileStore
import app.unlock.core.ServerNode
import app.unlock.core.Subscription
import app.unlock.core.Updater
import java.io.File
import app.unlock.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Главный экран сверху вниз: шапка, карточка подписки, выбор локации,
 * кнопка подключения со статусом, плашки «Пинг» и «Скорость».
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

    // Обновление приложения: проверяем при запуске, качаем по кнопке
    val context = LocalContext.current
    var update by remember { mutableStateOf<Updater.Update?>(null) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { update = runCatching { Updater.check() }.getOrNull() }

    val installUpdate: () -> Unit = {
        val target = update
        if (target != null && updateProgress == null) {
            scope.launch {
                runCatching {
                    val apk = downloadedApk ?: run {
                        updateProgress = 0f
                        Updater.download(context, target) { updateProgress = it }.also { downloadedApk = it }
                    }
                    if (!Updater.install(context, apk)) {
                        showMessage("Разрешите установку для Unlockless и нажмите «Обновить» ещё раз")
                    }
                }.onFailure { showMessage("Не удалось скачать обновление: ${it.message}") }
                updateProgress = null
            }
        }
    }
    val checkUpdates: () -> Unit = {
        scope.launch {
            runCatching { Updater.check() }
                .onSuccess { found ->
                    update = found
                    showMessage(if (found != null) "Доступна версия ${found.versionName}" else "У вас последняя версия")
                }
                .onFailure { showMessage(it.message ?: "Не удалось проверить обновления") }
        }
    }

    LaunchedEffect(importError) {
        if (importError != null) {
            snackbar.showSnackbar(importError)
            onDismissImportError()
        }
    }

    val selected = store.selectedServer
    val subscription = selected?.let { s -> store.subscriptions.firstOrNull { s in it.servers } }
        ?: store.subscriptions.firstOrNull { it.url != null }

    // Пинг выбранного сервера — при открытии, смене сервера и после подключения
    LaunchedEffect(selected?.id, vpnState.status == VpnState.Status.CONNECTED) {
        if (selected != null && !store.isPinging) store.ping(selected)
    }

    // Без явного цвета текст вне карточек был бы чёрным на тёмном фоне
    CompositionLocalProvider(LocalContentColor provides UnlockColors.TextPrimary) {
        Box(Modifier.fillMaxSize().background(UnlockColors.ScreenBrush)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(onAdd = { showAdd = true }, onSettings = { showSettings = true })
                Spacer(Modifier.height(8.dp))

                update?.let {
                    UpdateBanner(it, updateProgress, installUpdate)
                    Spacer(Modifier.height(12.dp))
                }

                if (subscription != null && subscription.url != null) {
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
                    Spacer(Modifier.height(12.dp))
                }

                if (selected != null) {
                    LocationCard(selected) { showServers = true }
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

                // Кнопка занимает всё свободное место между карточками и плашками
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val statusHeight = 64.dp
                    val size = minOf(maxWidth, maxHeight - statusHeight, 260.dp).coerceAtLeast(120.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ConnectButton(vpnState.status, onToggle, diameter = size)
                        StatusBlock(vpnState)
                    }
                }

                StatsRow(
                    ping = selected?.let { store.pings[it.id] },
                    pinging = store.isPinging,
                    connected = vpnState.status == VpnState.Status.CONNECTED,
                    onPing = { selected?.let { scope.launch { store.ping(it) } } },
                )
                Spacer(Modifier.height(12.dp))
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
    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false }, onMessage = showMessage, onCheckUpdates = checkUpdates)
    }
}

/** Плашка «Доступна новая версия» с кнопкой и прогрессом скачивания. */
@Composable
private fun UpdateBanner(update: Updater.Update, progress: Float?, onUpdate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = UnlockColors.Primary.copy(alpha = 0.16f),
        contentColor = UnlockColors.TextPrimary,
        border = BorderStroke(1.dp, UnlockColors.Primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Доступна версия ${update.versionName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (update.notes.isNotBlank()) {
                        Text(
                            update.notes, fontSize = 12.sp, color = UnlockColors.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onUpdate,
                    enabled = progress == null,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(if (progress == null) "Обновить" else "Скачивание…", fontSize = 13.sp)
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                val bar = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress }, modifier = bar,
                        color = UnlockColors.Primary, trackColor = UnlockColors.SurfaceHigh,
                    )
                } else {
                    LinearProgressIndicator(modifier = bar, color = UnlockColors.Primary, trackColor = UnlockColors.SurfaceHigh)
                }
            }
        }
    }
}

@Composable
private fun TopBar(onAdd: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Логотип из биллинга: щит на тёмном круге
        Box(
            Modifier
                .size(38.dp)
                .background(Color(0xFF1B1B1D), CircleShape)
                .border(1.dp, UnlockColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Unlockless",
            color = UnlockColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(onAdd) { Icon(Icons.Default.Add, "Добавить", tint = UnlockColors.TextPrimary) }
        Spacer(Modifier.width(8.dp))
        RoundIconButton(onSettings) { Icon(Icons.Default.Settings, "Настройки", tint = UnlockColors.TextPrimary) }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = UnlockColors.Surface,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Подписка: провайдер и срок, кнопка обновления, мини-плашки «Трафик /
 * Локаций / Обновлено», сообщение из панели мелким шрифтом.
 */
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
    val title = subscription.title.ifBlank {
        subscription.url?.let { Uri.parse(it).host } ?: "Подписка"
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = UnlockColors.Surface,
        contentColor = UnlockColors.TextPrimary,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title, color = UnlockColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        expiryLine(usage?.expire ?: 0), color = UnlockColors.TextSecondary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                DaysLeftBadge(usage?.expire ?: 0)
                Spacer(Modifier.width(8.dp))
                RefreshButton(isRefreshing, onRefresh)
                Box {
                    Surface(
                        onClick = { menu = true },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, "Меню", tint = UnlockColors.TextSecondary)
                        }
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

            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val traffic = when {
                    usage == null -> "—"
                    usage.total > 0 -> "${formatBytes(usage.used)} / ${formatBytes(usage.total)}"
                    else -> "${formatBytes(usage.used)} / ∞"
                }
                MiniStat("ТРАФИК", traffic, Modifier.weight(1.5f))
                MiniStat("ЛОКАЦИЙ", subscription.servers.size.toString(), Modifier.weight(1f))
                MiniStat("ОБНОВЛЕНО", updatedAgo(subscription.updatedAt), Modifier.weight(1.2f))
            }

            if (usage != null && usage.total > 0) {
                val fraction = (usage.used.toFloat() / usage.total).coerceIn(0f, 1f)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(end = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (fraction > 0.9f) UnlockColors.Danger else UnlockColors.Primary,
                    trackColor = UnlockColors.SurfaceHigh,
                )
            }

            // Сообщение из Remnawave (заголовок announce) — мелко, до двух строк
            subscription.announce?.let {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(6.dp).background(UnlockColors.Primary, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it, fontSize = 12.sp, lineHeight = 16.sp, color = UnlockColors.TextSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(UnlockColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, style = SectionLabel, fontSize = 10.sp, maxLines = 1)
        Text(
            value, color = UnlockColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «Активна до 25 ноября 2026» / «Истекла 3 ноября» / «Бессрочная». */
private fun expiryLine(expire: Long): String {
    if (expire <= 0) return "Бессрочная подписка"
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(expire * 1000))
    return if (expire * 1000 < System.currentTimeMillis()) "Истекла $date" else "Активна до $date"
}

/** «только что», «5 мин назад», «3 ч назад», «2 дн назад». */
private fun updatedAgo(time: Long): String {
    if (time <= 0) return "—"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - time)
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 60 * 24 -> "${minutes / 60} ч назад"
        else -> "${minutes / (60 * 24)} дн назад"
    }
}

/** «12 дней» в таблетке; жёлтая за 3 дня до конца, красная — после. */
@Composable
private fun DaysLeftBadge(expire: Long) {
    val (text, color) = if (expire <= 0) {
        "∞" to UnlockColors.Accent
    } else {
        val days = TimeUnit.MILLISECONDS.toDays(expire * 1000 - System.currentTimeMillis())
        when {
            days < 0 -> "истекла" to UnlockColors.Danger
            days == 0L -> "сегодня" to UnlockColors.Danger
            days <= 3 -> "$days ${daysWord(days)}" to UnlockColors.Warning
            else -> "$days ${daysWord(days)}" to UnlockColors.Accent
        }
    }
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
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

/** Круглая кнопка со стрелкой; крутится, пока подписка обновляется. */
@Composable
private fun RefreshButton(isRefreshing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    Surface(
        onClick = { if (!isRefreshing) onClick() },
        shape = CircleShape,
        color = UnlockColors.SurfaceHigh,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Refresh, "Обновить подписку",
                tint = UnlockColors.TextPrimary,
                modifier = Modifier.size(20.dp).rotate(if (isRefreshing) angle else 0f),
            )
        }
    }
}

/** Выбранная локация; нажатие открывает список всех локаций подписки. */
@Composable
private fun LocationCard(server: ServerNode, onClick: () -> Unit) {
    val title = splitFlag(server.name)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = UnlockColors.Surface,
        contentColor = UnlockColors.TextPrimary,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(title, 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ЛОКАЦИЯ", style = SectionLabel)
                Text(title.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier
                    .background(UnlockColors.Primary.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Сменить", color = UnlockColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = UnlockColors.Primary, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(state: VpnState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = state.status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "status",
        ) { status ->
            Text(
                if (status == VpnState.Status.CONNECTED) "Защищено" else status.title,
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

/**
 * Пинг выбранного сервера и текущая скорость туннеля. Скорость считаем по трафику
 * нашего UID: через него идут все сокеты ядра Xray, то есть весь проксируемый трафик.
 */
@Composable
private fun StatsRow(ping: Int?, pinging: Boolean, connected: Boolean, onPing: () -> Unit) {
    var down by remember { mutableLongStateOf(0L) }
    var up by remember { mutableLongStateOf(0L) }
    LaunchedEffect(connected) {
        down = 0
        up = 0
        if (!connected) return@LaunchedEffect
        val uid = Process.myUid()
        var lastRx = TrafficStats.getUidRxBytes(uid)
        var lastTx = TrafficStats.getUidTxBytes(uid)
        var lastTime = System.nanoTime()
        while (true) {
            delay(1000)
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            val now = System.nanoTime()
            val seconds = (now - lastTime) / 1e9
            if (rx >= 0 && tx >= 0 && seconds > 0) {
                down = ((rx - lastRx) / seconds).toLong().coerceAtLeast(0)
                up = ((tx - lastTx) / seconds).toLong().coerceAtLeast(0)
            }
            lastRx = rx
            lastTx = tx
            lastTime = now
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = UnlockColors.Surface,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            StatCell(
                label = "ПИНГ",
                value = when {
                    pinging && ping == null -> "…"
                    ping == null -> "—"
                    ping < 0 -> "нет"
                    else -> "$ping мс"
                },
                valueColor = ping?.let(::pingColor) ?: UnlockColors.TextPrimary,
                onClick = onPing,
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            StatCell("↓ ЗАГРУЗКА", if (connected) formatSpeed(down) else "—", Modifier.weight(1f))
            StatDivider()
            StatCell("↑ ОТДАЧА", if (connected) formatSpeed(up) else "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.fillMaxHeight().padding(vertical = 10.dp).width(1.dp).background(UnlockColors.Outline))
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = UnlockColors.TextPrimary,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = SectionLabel, fontSize = 10.sp, maxLines = 1)
        Text(
            value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Байты/с -> «12.4 Мбит/с» или «850 Кбит/с». */
private fun formatSpeed(bytesPerSecond: Long): String {
    val bits = bytesPerSecond * 8.0
    return when {
        bits >= 1_000_000 -> String.format(Locale.US, "%.1f Мбит/с", bits / 1_000_000)
        else -> String.format(Locale.US, "%.0f Кбит/с", bits / 1_000)
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1) String.format(Locale.US, "%.1f ГБ", gb)
    else String.format(Locale.US, "%.0f МБ", bytes / 1_048_576.0)
}
