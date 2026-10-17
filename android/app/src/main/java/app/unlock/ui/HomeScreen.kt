package app.unlock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlock.BuildConfig
import app.unlock.core.DeviceInfo
import app.unlock.core.ProfileStore
import app.unlock.core.ServerNode
import app.unlock.core.Subscription
import app.unlock.core.SubscriptionUsage
import app.unlock.core.XrayCore
import app.unlock.vpn.UnlockVpnService
import app.unlock.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Главный экран: большая кнопка подключения и список серверов по подпискам. */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock") },
                navigationIcon = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Настройки") }
                },
                actions = {
                    if (store.isPinging) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = { scope.launch { store.pingAll() } },
                            enabled = store.subscriptions.isNotEmpty(),
                        ) { Text("Пинг") }
                    }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Добавить") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { ConnectCard(vpnState, store.selectedServer, onToggle) }

            if (store.subscriptions.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { showAdd = true }) { Text("Добавить подписку") }
                        Text(
                            "Вставьте ссылку подписки (https://…) или ссылку на сервер " +
                                "(vless://, vmess://, trojan://, ss://, hy2://).",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            for (subscription in store.subscriptions) {
                item(key = "header-${subscription.id}") {
                    SubscriptionHeader(
                        subscription = subscription,
                        isRefreshing = subscription.id in store.refreshing,
                        onRefresh = {
                            scope.launch {
                                runCatching { store.refresh(subscription.id) }.onFailure { error = it.message }
                            }
                        },
                        onDelete = { store.delete(subscription.id) },
                    )
                }
                items(subscription.servers, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        ping = store.pings[server.id],
                        isSelected = server.id == store.selectedServer?.id,
                        canDelete = subscription.url == null,
                        onClick = { onSelect(server) },
                        onDelete = { store.deleteServer(server) },
                    )
                }
                item(key = "footer-${subscription.id}") {
                    subscription.usage?.let { UsageFooter(it) }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onAdd = { input -> store.add(input) },
        )
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })

    val shownError = error ?: importError
    if (shownError != null) {
        AlertDialog(
            onDismissRequest = { error = null; onDismissImportError() },
            confirmButton = { TextButton(onClick = { error = null; onDismissImportError() }) { Text("OK") } },
            title = { Text("Ошибка") },
            text = { Text(shownError) },
        )
    }
}

@Composable
private fun ConnectCard(state: VpnState, selected: ServerNode?, onToggle: () -> Unit) {
    val color = when (state.status) {
        VpnState.Status.CONNECTED -> Connected
        VpnState.Status.CONNECTING, VpnState.Status.DISCONNECTING -> Connecting
        VpnState.Status.DISCONNECTED -> Idle
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(150.dp).background(color, CircleShape).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (state.status == VpnState.Status.CONNECTING || state.status == VpnState.Status.DISCONNECTING) {
                CircularProgressIndicator(color = Color.White)
            } else {
                PowerIcon(Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(state.status.title, style = MaterialTheme.typography.titleMedium)
        if (state.status == VpnState.Status.CONNECTED && state.since > 0) ConnectedTimer(state.since)
        selected?.let {
            Text(
                it.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let {
            Text(
                it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

/** Значок питания: дуга и вертикальная черта. */
@Composable
private fun PowerIcon(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        val inset = stroke / 2 + size.minDimension * 0.08f
        drawArc(
            color = Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            Color.White, Offset(size.width / 2, size.height * 0.05f), Offset(size.width / 2, size.height * 0.45f),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
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
        style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubscriptionHeader(
    subscription: Subscription,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subscription.title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
            )
            if (isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (subscription.url != null) {
                        DropdownMenuItem(
                            text = { Text("Обновить") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menu = false; onRefresh() },
                        )
                        DropdownMenuItem(
                            text = { Text("Скопировать ссылку") },
                            onClick = { menu = false; copy(context, subscription.url) },
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
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        subscription.announce?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 16.dp, bottom = 4.dp))
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerNode,
    ping: Int?,
    isSelected: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Surface(Modifier.size(18.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {}
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${server.protocol.uppercase()} · ${server.transport}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ping?.let {
            Text(
                if (it < 0) "—" else "$it мс",
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = when {
                    it < 0 -> MaterialTheme.colorScheme.error
                    it < 300 -> Connected
                    it < 800 -> Connecting
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
        if (canDelete) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun UsageFooter(usage: SubscriptionUsage) {
    val context = LocalContext.current
    val total = if (usage.total > 0) Formatter.formatFileSize(context, usage.total) else "∞"
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "Трафик: ${Formatter.formatFileSize(context, usage.used)} из $total",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (usage.expire > 0) {
            Text(
                "Действует до ${DateFormat.getDateInstance(DateFormat.LONG).format(Date(usage.expire * 1000))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Добавление подписки или серверов: вставить вручную или из буфера обмена. */
@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: suspend (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Добавить") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("https://… или vless://…") },
                    minLines = 3,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { input = paste(context) }) { Text("Вставить из буфера") }
                Text(
                    "Ссылка подписки загрузит все её серверы и будет обновляться. " +
                        "Ссылки на отдельные серверы — по одной на строку.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            runCatching { onAdd(input) }
                                .onSuccess { onDismiss() }
                                .onFailure { error = it.message ?: "Ошибка" }
                            loading = false
                        }
                    },
                ) { Text("Добавить") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Отмена") } },
    )
}

/** О приложении, идентификатор устройства и журнал ядра для отладки. */
@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var xrayVersion by remember { mutableStateOf("…") }
    var log by remember { mutableStateOf("") }
    fun loadLog() {
        log = runCatching { File(context.filesDir, UnlockVpnService.LOG_FILE).readText() }.getOrDefault("").takeLast(8000)
    }
    LaunchedEffect(Unit) {
        loadLog()
        xrayVersion = withContext(Dispatchers.IO) { XrayCore.version() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Версия: ${BuildConfig.VERSION_NAME}")
                Text("Xray-core: $xrayVersion")
                SelectionContainer { Text("HWID: ${DeviceInfo.hwid(context)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Журнал Xray", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { loadLog() }) { Text("Обновить") }
                    if (log.isNotEmpty()) TextButton(onClick = { copy(context, log) }) { Text("Копировать") }
                }
                if (log.isEmpty()) {
                    Text("Пусто. Журнал появится после подключения.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer { Text(log, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Unlock", text))
}

private fun paste(context: Context): String =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
