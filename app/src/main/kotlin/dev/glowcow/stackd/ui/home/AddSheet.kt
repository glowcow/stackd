package dev.glowcow.stackd.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.glowcow.stackd.R
import dev.glowcow.stackd.barcode.MlKitFormats
import dev.glowcow.stackd.container
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.ui.CardDraft
import dev.glowcow.stackd.ui.components.Group
import dev.glowcow.stackd.ui.components.GroupDivider
import dev.glowcow.stackd.ui.components.GroupRow
import dev.glowcow.stackd.ui.components.GroupSheet
import dev.glowcow.stackd.ui.theme.StackdIcons
import kotlinx.coroutines.launch

private val PKPASS_TYPES = arrayOf(
    "application/vnd.apple.pkpass",
    "application/vnd.apple.pkpasses",
    "application/vnd-com.apple.pkpass",
    "application/octet-stream",
    "application/zip",
)

/** System pickers for the sources that need no camera. */
class AddSources(val pickFile: () -> Unit, val pickImage: () -> Unit)

@Composable
fun rememberAddSources(onDraft: (CardDraft) -> Unit, onImported: (String) -> Unit): AddSources {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.container.importer.import(uri) }
                .onSuccess { ids ->
                    if (ids.size > 1) Toast.makeText(context, resources.getString(R.string.import_many, ids.size), Toast.LENGTH_SHORT).show()
                    ids.firstOrNull()?.let(onImported)
                }
                .onFailure { Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show() }
        }
    }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val code = MlKitFormats.scanImage(context, uri)
            if (code == null) {
                Toast.makeText(context, R.string.scan_not_found, Toast.LENGTH_SHORT).show()
            } else {
                onDraft(CardDraft(source = CardSource.GALLERY, value = code.value, format = code.format))
            }
        }
    }
    return remember(file, image) {
        AddSources(
            pickFile = { file.launch(PKPASS_TYPES) },
            pickImage = { image.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** Where a new card comes from; the scanner is the last entry. */
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onFile: () -> Unit,
    onImage: () -> Unit,
    onPhoto: () -> Unit,
    onManual: () -> Unit,
    onScan: () -> Unit,
) = GroupSheet(stringResource(R.string.add_card), onDismiss) { pick ->
    Group {
        GroupRow(stringResource(R.string.add_file), icon = StackdIcons.File, onClick = { pick(onFile) })
        GroupDivider()
        GroupRow(stringResource(R.string.add_image), icon = StackdIcons.Gallery, onClick = { pick(onImage) })
        GroupDivider()
        GroupRow(stringResource(R.string.add_photo), icon = StackdIcons.Camera, onClick = { pick(onPhoto) })
        GroupDivider()
        GroupRow(stringResource(R.string.add_manual), icon = StackdIcons.Edit, onClick = { pick(onManual) })
        GroupDivider()
        GroupRow(stringResource(R.string.add_scan), icon = StackdIcons.Scan, onClick = { pick(onScan) })
    }
}
