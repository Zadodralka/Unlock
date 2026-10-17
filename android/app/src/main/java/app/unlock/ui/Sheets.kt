package app.unlock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlock.BuildConfig
import app.unlock.core.DeviceInfo
import app.unlock.core.ProfileStore
import app.unlock.core.ServerNode
import app.unlock.core.XrayCore
import app.unlock.vpn.UnlockVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = UnlockColors.Background,
        contentColor = UnlockColors.TextPrimary,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(UnlockColors.TextSecondary.copy(alpha = 0.4f), CircleShape),
            )
        },
    ) {
        content()
    }
}

/** Все серверы по подпискам; выбранный подсвечен. */
@Composable
fun ServersSheet(store: ProfileStore, onSelect: (ServerNode) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    UnlockSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Серверы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (store.isPinging) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { scope.launch { store.pingAll() } }) { Text("Проверить пинг") }
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (subscription in store.subscriptions) {
                item(key = "h-${subscription.id}") {
                    Text(
                        subscription.title.uppercase(), style = SectionLabel,
                        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                items(subscription.servers, key = { it.id }) { server ->
                    ServerItem(
                        server = server,
                        ping = store.pings[server.id],
                        selected = server.id == store.selectedServer?.id,
                        onClick = { onSelect(server) },
                        onDelete = if (subscription.url == null) ({ store.deleteServer(server) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerItem(
    server: ServerNode,
    ping: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val title = splitFlag(server.name)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) UnlockColors.Primary.copy(alpha = 0.14f) else UnlockColors.Surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) UnlockColors.Primary else UnlockColors.Outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(title, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${server.protocol.uppercase()} · ${server.transport}",
                    fontSize = 12.sp, color = UnlockColors.TextSecondary,
                )
            }
            ping?.let { PingBadge(it) }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Удалить", tint = UnlockColors.TextSecondary)
                }
            }
            if (selected) {
                Box(
                    Modifier.padding(start = 8.dp).size(22.dp).background(UnlockColors.Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Добавление подписки или серверов: QR-код, буфер обмена или ввод вручную. */
@Composable
fun AddSheet(onAdd: suspend (String) -> Unit, onAdded: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(text: String) {
        loading = true
        error = null
        scope.launch {
            runCatching { onAdd(text) }
                .onSuccess { onAdded(); onDismiss() }
                .onFailure { error = it.message ?: "Не удалось добавить" }
            loading = false
        }
    }

    // Распознанную ссылку сразу добавляем
    val onScanned: (String) -> Unit = { scanned ->
        input = scanned
        submit(scanned)
    }
    val scanCamera = rememberQrScanner(onScanned)
    val scanImage = rememberQrFromImage(onScanned) { error = it }

    UnlockSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
        ) {
            Text("Добавить подписку", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            // Две одинаковые плитки: значок сверху, короткая подпись снизу
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("QR-код", enabled = !loading, onClick = scanCamera, modifier = Modifier.weight(1f)) {
                    QrGlyph(Modifier.size(26.dp), color = UnlockColors.Primary)
                }
                ActionTile("Из галереи", enabled = !loading, onClick = scanImage, modifier = Modifier.weight(1f)) {
                    GalleryGlyph(Modifier.size(26.dp), color = UnlockColors.Primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("ИЛИ ВСТАВЬТЕ ССЫЛКУ", style = SectionLabel)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ссылка подписки (https://…) или серверы vless://, vmess://, trojan://, ss://, hy2:// — по одному на строку.",
                color = UnlockColors.TextSecondary, fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                placeholder = { Text("https://…", color = UnlockColors.TextSecondary) },
                minLines = 3,
                maxLines = 6,
                isError = error != null,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UnlockColors.Primary,
                    unfocusedBorderColor = UnlockColors.Outline,
                    focusedContainerColor = UnlockColors.Surface,
                    unfocusedContainerColor = UnlockColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = UnlockColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { input = pasteFromClipboard(context); error = null },
                    enabled = !loading,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, UnlockColors.Outline),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Вставить", maxLines = 1, fontSize = 15.sp) }
                Button(
                    onClick = { submit(input) },
                    enabled = input.isNotBlank() && !loading,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Добавить", maxLines = 1, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** Квадратная плитка действия: значок и подпись, обе плитки ряда одного размера. */
@Composable
private fun ActionTile(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = UnlockColors.Surface,
        contentColor = UnlockColors.TextPrimary,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = modifier.height(84.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(40.dp).background(UnlockColors.Primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** О приложении, идентификатор устройства и журнал ядра для отладки. */
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onMessage: (String) -> Unit, onCheckUpdates: () -> Unit) {
    val context = LocalContext.current
    var xrayVersion by remember { mutableStateOf("…") }
    var log by remember { mutableStateOf("") }
    val hwid = remember { DeviceInfo.hwid(context) }
    fun loadLog() {
        log = runCatching { File(context.filesDir, UnlockVpnService.LOG_FILE).readText() }
            .getOrDefault("").takeLast(8000)
    }
    LaunchedEffect(Unit) {
        loadLog()
        xrayVersion = withContext(Dispatchers.IO) { XrayCore.version() }
    }

    UnlockSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge)

            InfoCard {
                InfoRow("Версия приложения", BuildConfig.VERSION_NAME, actionLabel = "Проверить", onAction = onCheckUpdates)
                InfoRow("Ядро Xray", xrayVersion)
                InfoRow("ID устройства", hwid.take(8) + "…", actionLabel = "Копировать") {
                    copyToClipboard(context, hwid)
                    onMessage("ID устройства скопирован")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ЖУРНАЛ XRAY", style = SectionLabel, modifier = Modifier.weight(1f))
                TextButton(onClick = { loadLog() }) { Text("Обновить") }
                if (log.isNotEmpty()) {
                    TextButton(onClick = { copyToClipboard(context, log); onMessage("Журнал скопирован") }) {
                        Text("Копировать")
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = UnlockColors.Surface,
                border = BorderStroke(1.dp, UnlockColors.Outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (log.isEmpty()) {
                    Text(
                        "Пусто. Журнал появится после подключения.",
                        color = UnlockColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp),
                    )
                } else {
                    SelectionContainer {
                        Text(log, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = UnlockColors.Surface,
        border = BorderStroke(1.dp, UnlockColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = UnlockColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, fontSize = 13.sp) }
        }
    }
}
