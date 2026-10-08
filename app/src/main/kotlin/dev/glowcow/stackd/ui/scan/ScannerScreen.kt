package dev.glowcow.stackd.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.glowcow.stackd.R
import dev.glowcow.stackd.barcode.MlKitFormats
import dev.glowcow.stackd.barcode.ScannedCode
import dev.glowcow.stackd.container
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.ui.CardDraft
import dev.glowcow.stackd.ui.components.IconButton48
import dev.glowcow.stackd.ui.components.PillButton
import dev.glowcow.stackd.ui.theme.LocalDarkColors
import dev.glowcow.stackd.ui.theme.StackdColorsProvider
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.launch

/** Scans barcodes live; with [photo] it shoots the card front instead, which becomes the cover. */
@Composable
fun ScannerScreen(
    photo: Boolean,
    onClose: () -> Unit,
    onDraft: (CardDraft) -> Unit,
) = StackdColorsProvider(LocalDarkColors.current) {
    val c = StackdTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var torch by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    fun finish(code: ScannedCode?, source: CardSource, cover: String? = null) {
        if (code == null && cover == null) {
            busy = false
            toast(R.string.scan_not_found)
            return
        }
        onDraft(CardDraft(source = source, value = code?.value, format = code?.format, coverPath = cover))
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!hasCamera && !asked) permission.launch(Manifest.permission.CAMERA) }

    fun shoot() {
        if (!hasCamera || busy) return
        busy = true
        val file = context.container.cards.newCoverFile()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        finish(MlKitFormats.scanImage(context, Uri.fromFile(file)), CardSource.PHOTO, file.path)
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    busy = false
                    toast(R.string.scan_capture_failed)
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton48(StackdIcons.Close, stringResource(R.string.close), onClick = onClose)
            Text(
                stringResource(if (photo) R.string.add_photo else R.string.tab_scanner),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = c.text,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
            )
            IconButton48(
                if (torch) StackdIcons.FlashOn else StackdIcons.Flash,
                stringResource(R.string.scan_flash),
                tint = if (torch) c.accent else c.text,
            ) { torch = !torch }
        }

        Box(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (photo) 0.dp else 16.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                CameraPreview(
                    torch = torch,
                    detect = !photo && !busy,
                    capture = capture,
                    onCode = { code ->
                        if (busy) return@CameraPreview
                        busy = true
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        finish(code, CardSource.SCAN)
                    },
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (!photo && hasCamera) ScanFrame()
                val hint = when {
                    !hasCamera -> R.string.scan_no_camera
                    photo -> R.string.scan_hint_photo
                    else -> R.string.scan_hint_code
                }
                Text(
                    stringResource(hint),
                    color = c.text,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    // Opaque: a camera preview is not part of a page that glass could blur.
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.bg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (!hasCamera) {
                    PillButton(stringResource(R.string.scan_grant)) {
                        if (asked) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        } else {
                            permission.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }

        if (photo) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, if (busy) c.muted else c.text, CircleShape)
                        .clickable(enabled = !busy, onClickLabel = stringResource(R.string.scan_shutter)) { shoot() }
                        .padding(9.dp)
                        .clip(CircleShape)
                        .background(if (busy) c.muted else c.text),
                )
            }
        }
    }
}

@Composable
private fun ScanFrame() {
    val accent = StackdTheme.colors.accent
    val corners = remember {
        PathParser().parsePathString(
            "M2 34V18A16 16 0 0 1 18 2H34M266 2h16a16 16 0 0 1 16 16v16M298 156v16a16 16 0 0 1-16 16h-16M34 188H18A16 16 0 0 1 2 172v-16",
        ).toPath()
    }
    Canvas(Modifier.size(300.dp, 190.dp)) {
        val s = size.width / 300f
        scale(s, pivot = Offset.Zero) {
            drawPath(corners, accent, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
}
