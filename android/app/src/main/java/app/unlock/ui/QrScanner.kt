package app.unlock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.android.Intents
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сканер QR с камеры. Основной — Google Code Scanner (из Play Services: сам
 * приближает, хорошо читает с экрана монитора, не нужен доступ к камере).
 * Если Play Services нет или модуль ещё не скачан — сканер ZXing.
 */
@Composable
fun rememberQrScanner(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)

    val zxing = rememberLauncherForActivityResult(ScanContract()) { scan ->
        scan.contents?.takeIf { it.isNotBlank() }?.let { result(it) }
    }
    val launchZxing = {
        zxing.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код подписки")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                // Читать и обычные, и инвертированные (светлые на тёмном) коды
                .addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN),
        )
    }

    return {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        // Модуль сканера скачивается Play Services; если его ещё нет — просим
        // скачать на будущее и пока сканируем через ZXing
        ModuleInstall.getClient(context).areModulesAvailable(scanner)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    scanner.startScan()
                        .addOnSuccessListener { barcode -> barcode.rawValue?.takeIf { it.isNotBlank() }?.let { result(it) } }
                        .addOnFailureListener { launchZxing() }
                } else {
                    ModuleInstall.getClient(context)
                        .installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
                    launchZxing()
                }
            }
            .addOnFailureListener { launchZxing() }
    }
}

/** QR со скриншота или фото из галереи. */
@Composable
fun rememberQrFromImage(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.Default) { runCatching { decodeQr(context, uri) }.getOrNull() }
            if (text != null) result(text) else error("На картинке не найден QR-код")
        }
    }
    return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private fun decodeQr(context: Context, uri: Uri): String? {
    val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    // Большие фото уменьшаем: распознаванию хватает ~1600px, а память не резиновая
    val scale = 1600f / maxOf(original.width, original.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
    } else {
        original
    }
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)

    val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )
    val reader = MultiFormatReader()
    for (candidate in listOf(source, source.invert())) {
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text
        } catch (_: NotFoundException) {
            reader.reset()
        }
    }
    return null
}
