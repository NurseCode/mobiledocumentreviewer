package com.pdfcomposer.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    var activeScreen by rememberSaveable { mutableStateOf("list") }
    var editingTemplateId by rememberSaveable { mutableStateOf(-1) }
    var fillingTemplateId by rememberSaveable { mutableStateOf(-1) }
    var pendingSourceFile by remember { mutableStateOf<File?>(null) }
    var pendingPageCount by rememberSaveable { mutableStateOf(0) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    val pageCount = withContext(Dispatchers.IO) {
                        try {
                            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(fd)
                            val count = renderer.pageCount
                            renderer.close()
                            fd.close()
                            count
                        } catch (e: Exception) { 1 }
                    }
                    pendingSourceFile = tempFile
                    pendingPageCount = pageCount
                    activeScreen = "create"
                } else {
                    Toast.makeText(context, "Could not open PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    when (activeScreen) {
        "list" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "Form Templates",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Create reusable form templates to fill out repeatedly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (templates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No templates yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Upload a blank form PDF to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Template")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(templates) { template ->
                            TemplateCard(
                                template = template,
                                onFill = {
                                    fillingTemplateId = template.id
                                    activeScreen = "fill"
                                },
                                onEdit = {
                                    scope.launch {
                                        val sourceFile = File(template.sourceFilePath)
                                        if (sourceFile.exists()) {
                                            pendingSourceFile = sourceFile
                                            pendingPageCount = template.pageCount
                                            editingTemplateId = template.id
                                            activeScreen = "create"
                                        } else {
                                            Toast.makeText(context, "Template source file not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDelete = { viewModel.deleteTemplate(template) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Template")
                    }
                }
            }
        }
        "create" -> {
            if (pendingSourceFile != null) {
                TemplateFieldPlacementScreen(
                    sourceFile = pendingSourceFile!!,
                    pageCount = pendingPageCount,
                    viewModel = viewModel,
                    existingTemplateId = if (editingTemplateId > 0) editingTemplateId else null,
                    onDone = {
                        pendingSourceFile = null
                        editingTemplateId = -1
                        activeScreen = "list"
                    },
                    onCancel = {
                        pendingSourceFile = null
                        editingTemplateId = -1
                        activeScreen = "list"
                    }
                )
            }
        }
        "fill" -> {
            if (fillingTemplateId > 0) {
                TemplateFillScreen(
                    templateId = fillingTemplateId,
                    viewModel = viewModel,
                    onDone = {
                        fillingTemplateId = -1
                        activeScreen = "list"
                    },
                    onCancel = {
                        fillingTemplateId = -1
                        activeScreen = "list"
                    }
                )
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: FormTemplate,
    onFill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFill() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${template.pageCount} page${if (template.pageCount != 1) "s" else ""} • ${dateFormat.format(Date(template.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onFill) {
                Text("Fill")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Fields") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

data class PlacedField(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateFieldPlacementScreen(
    sourceFile: File,
    pageCount: Int,
    viewModel: PdfViewModel,
    existingTemplateId: Int? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPage by rememberSaveable { mutableStateOf(0) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfPageWidthPt by rememberSaveable { mutableStateOf(612f) }
    var pdfPageHeightPt by rememberSaveable { mutableStateOf(792f) }
    val fields = remember { mutableStateListOf<PlacedField>() }
    var showFieldDialog by remember { mutableStateOf(false) }
    var pendingFieldPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingField by remember { mutableStateOf<PlacedField?>(null) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var selectedFieldId by remember { mutableStateOf<String?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var isAutoDetecting by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(existingTemplateId) {
        if (existingTemplateId != null) {
            val template = viewModel.getTemplate(existingTemplateId)
            if (template != null) {
                templateName = template.name
                val existingFields = viewModel.getTemplateFields(existingTemplateId)
                fields.clear()
                fields.addAll(existingFields.map { tf ->
                    PlacedField(
                        name = tf.fieldName,
                        type = tf.fieldType,
                        page = tf.pageNumber,
                        x = tf.relativeX,
                        y = tf.relativeY,
                        width = tf.relativeWidth,
                        height = tf.relativeHeight
                    )
                })
            }
        }
    }

    LaunchedEffect(sourceFile, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val page = renderer.openPage(currentPage)
                pdfPageWidthPt = page.width.toFloat()
                pdfPageHeightPt = page.height.toFloat()
                val scale = 2
                val bitmap = Bitmap.createBitmap(
                    page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                fd.close()
                pdfBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    BackHandler { onCancel() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (existingTemplateId != null) "Edit Template" else "Create Template") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isAutoDetecting) {
                                isAutoDetecting = true
                                scope.launch {
                                    try {
                                        val detected = autoDetectFormFields(pdfBitmap, currentPage)
                                        if (detected.isNotEmpty()) {
                                            fields.addAll(detected)
                                            Toast.makeText(context, "Found ${detected.size} field(s)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No fields detected", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Detection failed", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isAutoDetecting = false
                                    }
                                }
                            }
                        },
                        enabled = !isAutoDetecting && pdfBitmap != null
                    ) {
                        if (isAutoDetecting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoFixHigh, "Auto-detect fields")
                        }
                    }
                    if (zoomScale > 1.01f) {
                        IconButton(onClick = { zoomScale = 1f; panOffset = Offset.Zero }) {
                            Icon(Icons.Default.ZoomOut, "Reset zoom")
                        }
                    }
                    if (pageCount > 1) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) { Icon(Icons.Default.ArrowBack, "Previous") }
                        Text("${currentPage + 1}/$pageCount", style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { if (currentPage < pageCount - 1) currentPage++ },
                            enabled = currentPage < pageCount - 1
                        ) { Icon(Icons.Default.ArrowForward, "Next") }
                    }
                    Button(
                        onClick = {
                            if (fields.isEmpty()) {
                                Toast.makeText(context, "Add at least one field", Toast.LENGTH_SHORT).show()
                            } else if (templateName.isBlank() && existingTemplateId == null) {
                                showNameDialog = true
                            } else {
                                isSaving = true
                                scope.launch {
                                    try {
                                        val templateId = if (existingTemplateId != null) {
                                            existingTemplateId
                                        } else {
                                            val templateDir = File(context.filesDir, "templates")
                                            templateDir.mkdirs()
                                            val destFile = File(templateDir, "template_${System.currentTimeMillis()}.pdf")
                                            sourceFile.copyTo(destFile, overwrite = true)
                                            viewModel.addTemplate(templateName, destFile.absolutePath, pageCount).toInt()
                                        }
                                        val templateFields = fields.map { f ->
                                            TemplateField(
                                                templateId = templateId,
                                                fieldName = f.name,
                                                fieldType = f.type,
                                                pageNumber = f.page,
                                                relativeX = f.x,
                                                relativeY = f.y,
                                                relativeWidth = f.width,
                                                relativeHeight = f.height
                                            )
                                        }
                                        viewModel.saveTemplateFields(templateId, templateFields)
                                        Toast.makeText(context, "Template saved!", Toast.LENGTH_SHORT).show()
                                        onDone()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )

            Text(
                when {
                    isAutoDetecting -> "Detecting form fields..."
                    selectedFieldId != null -> "Drag to move. Drag handles to resize. Tap empty area to deselect."
                    else -> "Tap to add field. Tap field to select. Pinch to zoom. Double-tap to edit."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedFieldId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            val pageFields = fields.filter { it.page == currentPage }
            val minFieldRel = 0.02f
            var dragTargetId by remember { mutableStateOf<String?>(null) }
            var dragOffsetRel by remember { mutableStateOf(Offset.Zero) }
            var dragHandle by remember { mutableStateOf<String?>(null) }

            if (pdfBitmap != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clipToBounds()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentPage, selectedFieldId, fields.toList()) {
                                val pdfBmp = pdfBitmap ?: return@pointerInput
                                val pScale = minOf(size.width.toFloat() / pdfBmp.width.toFloat(), size.height.toFloat() / pdfBmp.height.toFloat())
                                val pW = pdfBmp.width * pScale
                                val pH = pdfBmp.height * pScale
                                val pLeft = (size.width - pW) / 2f
                                val pTop = (size.height - pH) / 2f
                                val cX = size.width / 2f
                                val cY = size.height / 2f

                                fun toContentX(sx: Float) = (sx - panOffset.x - cX) / zoomScale + cX
                                fun toContentY(sy: Float) = (sy - panOffset.y - cY) / zoomScale + cY
                                fun toRelX(sx: Float) = ((toContentX(sx) - pLeft) / pW).coerceIn(0f, 1f)
                                fun toRelY(sy: Float) = ((toContentY(sy) - pTop) / pH).coerceIn(0f, 1f)

                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val relX = toRelX(offset.x)
                                        val relY = toRelY(offset.y)
                                        val contentX = toContentX(offset.x)
                                        val contentY = toContentY(offset.y)
                                        val hr = 28f

                                        dragTargetId = null
                                        dragHandle = null

                                        val sel = pageFields.find { it.id == selectedFieldId }
                                        if (sel != null) {
                                            val fL = pLeft + sel.x * pW
                                            val fT = pTop + sel.y * pH
                                            val fR = fL + sel.width * pW
                                            val fB = fT + sel.height * pH
                                            val midX2 = (fL + fR) / 2f
                                            val midY2 = (fT + fB) / 2f

                                            val nearL = kotlin.math.abs(contentX - fL) < hr
                                            val nearR = kotlin.math.abs(contentX - fR) < hr
                                            val nearT = kotlin.math.abs(contentY - fT) < hr
                                            val nearB = kotlin.math.abs(contentY - fB) < hr
                                            val nearMX = kotlin.math.abs(contentX - midX2) < hr
                                            val nearMY = kotlin.math.abs(contentY - midY2) < hr

                                            val h = when {
                                                nearL && nearT -> "tl"; nearR && nearT -> "tr"
                                                nearL && nearB -> "bl"; nearR && nearB -> "br"
                                                nearT && nearMX -> "tm"; nearB && nearMX -> "bm"
                                                nearL && nearMY -> "ml"; nearR && nearMY -> "mr"
                                                else -> null
                                            }
                                            if (h != null) {
                                                dragHandle = h
                                                dragTargetId = sel.id
                                                return@detectDragGestures
                                            }
                                        }

                                        val target = pageFields.find { f ->
                                            relX >= f.x && relX <= f.x + f.width &&
                                            relY >= f.y && relY <= f.y + f.height
                                        }
                                        if (target != null) {
                                            dragTargetId = target.id
                                            selectedFieldId = target.id
                                            dragOffsetRel = Offset(relX - target.x, relY - target.y)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val curRelX = toRelX(change.position.x)
                                        val curRelY = toRelY(change.position.y)
                                        val tid = dragTargetId

                                        if (tid != null && dragHandle == null) {
                                            val idx = fields.indexOfFirst { it.id == tid }
                                            if (idx >= 0) {
                                                val f = fields[idx]
                                                fields[idx] = f.copy(
                                                    x = (curRelX - dragOffsetRel.x).coerceIn(0f, 1f - f.width),
                                                    y = (curRelY - dragOffsetRel.y).coerceIn(0f, 1f - f.height)
                                                )
                                            }
                                        } else if (tid != null && dragHandle != null) {
                                            val idx = fields.indexOfFirst { it.id == tid }
                                            if (idx >= 0) {
                                                val f = fields[idx]
                                                val r = f.x + f.width
                                                val b = f.y + f.height
                                                when (dragHandle) {
                                                    "tl" -> {
                                                        val nx = curRelX.coerceIn(0f, r - minFieldRel)
                                                        val ny = curRelY.coerceIn(0f, b - minFieldRel)
                                                        fields[idx] = f.copy(x = nx, y = ny, width = r - nx, height = b - ny)
                                                    }
                                                    "tr" -> {
                                                        val nw = (curRelX - f.x).coerceIn(minFieldRel, 1f - f.x)
                                                        val ny = curRelY.coerceIn(0f, b - minFieldRel)
                                                        fields[idx] = f.copy(y = ny, width = nw, height = b - ny)
                                                    }
                                                    "bl" -> {
                                                        val nx = curRelX.coerceIn(0f, r - minFieldRel)
                                                        val nh = (curRelY - f.y).coerceIn(minFieldRel, 1f - f.y)
                                                        fields[idx] = f.copy(x = nx, width = r - nx, height = nh)
                                                    }
                                                    "br" -> {
                                                        val nw = (curRelX - f.x).coerceIn(minFieldRel, 1f - f.x)
                                                        val nh = (curRelY - f.y).coerceIn(minFieldRel, 1f - f.y)
                                                        fields[idx] = f.copy(width = nw, height = nh)
                                                    }
                                                    "tm" -> {
                                                        val ny = curRelY.coerceIn(0f, b - minFieldRel)
                                                        fields[idx] = f.copy(y = ny, height = b - ny)
                                                    }
                                                    "bm" -> {
                                                        val nh = (curRelY - f.y).coerceIn(minFieldRel, 1f - f.y)
                                                        fields[idx] = f.copy(height = nh)
                                                    }
                                                    "ml" -> {
                                                        val nx = curRelX.coerceIn(0f, r - minFieldRel)
                                                        fields[idx] = f.copy(x = nx, width = r - nx)
                                                    }
                                                    "mr" -> {
                                                        val nw = (curRelX - f.x).coerceIn(minFieldRel, 1f - f.x)
                                                        fields[idx] = f.copy(width = nw)
                                                    }
                                                }
                                            }
                                        } else {
                                            val dx = change.position.x - change.previousPosition.x
                                            val dy = change.position.y - change.previousPosition.y
                                            panOffset = Offset(panOffset.x + dx, panOffset.y + dy)
                                        }
                                    },
                                    onDragEnd = {
                                        dragTargetId = null
                                        dragHandle = null
                                    },
                                    onDragCancel = {
                                        dragTargetId = null
                                        dragHandle = null
                                    }
                                )
                            }
                            .pointerInput(currentPage, selectedFieldId, fields.toList()) {
                                val pdfBmp = pdfBitmap ?: return@pointerInput
                                val pScale = minOf(size.width.toFloat() / pdfBmp.width.toFloat(), size.height.toFloat() / pdfBmp.height.toFloat())
                                val pW = pdfBmp.width * pScale
                                val pH = pdfBmp.height * pScale
                                val pLeft = (size.width - pW) / 2f
                                val pTop = (size.height - pH) / 2f
                                val cX = size.width / 2f
                                val cY = size.height / 2f

                                fun toRelX(sx: Float) = (((sx - panOffset.x - cX) / zoomScale + cX - pLeft) / pW).coerceIn(0f, 1f)
                                fun toRelY(sy: Float) = (((sy - panOffset.y - cY) / zoomScale + cY - pTop) / pH).coerceIn(0f, 1f)

                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        val relX = toRelX(offset.x)
                                        val relY = toRelY(offset.y)
                                        val tappedField = pageFields.find { f ->
                                            relX >= f.x && relX <= f.x + f.width && relY >= f.y && relY <= f.y + f.height
                                        }
                                        if (tappedField != null) {
                                            selectedFieldId = tappedField.id
                                            editingField = tappedField
                                            showFieldDialog = true
                                        }
                                    },
                                    onTap = { offset ->
                                        val relX = toRelX(offset.x)
                                        val relY = toRelY(offset.y)
                                        val tappedField = pageFields.find { f ->
                                            relX >= f.x && relX <= f.x + f.width && relY >= f.y && relY <= f.y + f.height
                                        }
                                        if (tappedField != null) {
                                            selectedFieldId = if (selectedFieldId == tappedField.id) null else tappedField.id
                                        } else if (selectedFieldId != null) {
                                            selectedFieldId = null
                                        } else {
                                            pendingFieldPosition = Pair(relX, relY)
                                            editingField = null
                                            showFieldDialog = true
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    var prevSpread = 0f
                                    var prevCentroid = firstDown.position

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val active = event.changes.filter { it.pressed }
                                        if (active.size < 2) {
                                            if (active.isEmpty()) break
                                            prevSpread = 0f
                                            continue
                                        }
                                        val cx = active.map { it.position.x }.average().toFloat()
                                        val cy = active.map { it.position.y }.average().toFloat()
                                        val centroid = Offset(cx, cy)
                                        val dx = active[0].position.x - active[1].position.x
                                        val dy = active[0].position.y - active[1].position.y
                                        val spread = kotlin.math.sqrt(dx * dx + dy * dy)

                                        if (prevSpread > 0f && spread > 0f) {
                                            zoomScale = (zoomScale * (spread / prevSpread)).coerceIn(1f, 5f)
                                        }
                                        panOffset += centroid - prevCentroid
                                        prevCentroid = centroid
                                        prevSpread = spread
                                        active.forEach { it.consume() }
                                    }
                                }
                            }
                    ) {
                        val pdfBmp = pdfBitmap!!
                        val pageScale = minOf(size.width / pdfBmp.width.toFloat(), size.height / pdfBmp.height.toFloat())
                        val pageW = pdfBmp.width * pageScale
                        val pageH = pdfBmp.height * pageScale
                        val pageLeft = (size.width - pageW) / 2f
                        val pageTop = (size.height - pageH) / 2f
                        val cX = size.width / 2f
                        val cY = size.height / 2f

                        drawContext.transform.translate(
                            panOffset.x + cX * (1f - zoomScale),
                            panOffset.y + cY * (1f - zoomScale)
                        )
                        drawContext.transform.scale(zoomScale, zoomScale)

                        drawImage(
                            image = pdfBmp.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(pageLeft.toInt(), pageTop.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(pageW.toInt(), pageH.toInt())
                        )

                        val handleDrawSize = 22f / zoomScale

                        pageFields.forEach { field ->
                            val fLeft = pageLeft + field.x * pageW
                            val fTop = pageTop + field.y * pageH
                            val fW = field.width * pageW
                            val fH = field.height * pageH
                            val isSelected = field.id == selectedFieldId

                            val fieldColor = when (field.type) {
                                "text" -> Color(0x400000FF)
                                "date" -> Color(0x4000AA00)
                                "signature" -> Color(0x40FF0000)
                                "checkbox" -> Color(0x40FF8800)
                                else -> Color(0x400000FF)
                            }
                            val borderColor = when (field.type) {
                                "text" -> Color(0xFF2962FF)
                                "date" -> Color(0xFF00C853)
                                "signature" -> Color(0xFFD50000)
                                "checkbox" -> Color(0xFFFF6D00)
                                else -> Color(0xFF2962FF)
                            }

                            drawRect(
                                color = if (isSelected) fieldColor.copy(alpha = 0.55f) else fieldColor,
                                topLeft = Offset(fLeft, fTop),
                                size = androidx.compose.ui.geometry.Size(fW, fH)
                            )
                            drawRect(
                                color = borderColor,
                                topLeft = Offset(fLeft, fTop),
                                size = androidx.compose.ui.geometry.Size(fW, fH),
                                style = Stroke(width = if (isSelected) 3f / zoomScale else 1.5f / zoomScale)
                            )

                            if (isSelected) {
                                val hs = handleDrawSize
                                val hHalf = hs / 2f
                                val midX = fLeft + fW / 2f
                                val midY = fTop + fH / 2f
                                val handlePositions = listOf(
                                    Offset(fLeft - hHalf, fTop - hHalf),
                                    Offset(fLeft + fW - hHalf, fTop - hHalf),
                                    Offset(fLeft - hHalf, fTop + fH - hHalf),
                                    Offset(fLeft + fW - hHalf, fTop + fH - hHalf),
                                    Offset(midX - hHalf, fTop - hHalf),
                                    Offset(midX - hHalf, fTop + fH - hHalf),
                                    Offset(fLeft - hHalf, midY - hHalf),
                                    Offset(fLeft + fW - hHalf, midY - hHalf)
                                )
                                handlePositions.forEach { pos ->
                                    drawCircle(
                                        color = Color.White,
                                        radius = hs * 0.7f,
                                        center = Offset(pos.x + hHalf, pos.y + hHalf)
                                    )
                                    drawCircle(
                                        color = borderColor,
                                        radius = hs * 0.7f,
                                        center = Offset(pos.x + hHalf, pos.y + hHalf),
                                        style = Stroke(width = 2.5f / zoomScale)
                                    )
                                }
                            }

                            val labelSize = (20f / zoomScale).coerceIn(10f, 24f)
                            drawContext.canvas.nativeCanvas.drawText(
                                field.name,
                                fLeft + 4f / zoomScale,
                                fTop + fH / 2f + labelSize / 3f,
                                android.graphics.Paint().apply {
                                    color = borderColor.hashCode()
                                    textSize = labelSize
                                    isAntiAlias = true
                                }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${fields.size} field${if (fields.size != 1) "s" else ""}${if (zoomScale > 1.01f) " • ${(zoomScale * 100).toInt()}%" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selectedFieldId != null) {
                        TextButton(onClick = { selectedFieldId = null }) {
                            Text("Deselect")
                        }
                    }
                }
            }
        }
    }

    if (showFieldDialog) {
        FieldConfigDialog(
            existingField = editingField,
            onDismiss = {
                showFieldDialog = false
                editingField = null
                pendingFieldPosition = null
            },
            onSave = { name, type, width, height ->
                if (editingField != null) {
                    val idx = fields.indexOfFirst { it.id == editingField!!.id }
                    if (idx >= 0) {
                        fields[idx] = editingField!!.copy(name = name, type = type, width = width, height = height)
                    }
                } else if (pendingFieldPosition != null) {
                    val (px, py) = pendingFieldPosition!!
                    val adjustedX = (px - width / 2f).coerceIn(0f, 1f - width)
                    val adjustedY = (py - height / 2f).coerceIn(0f, 1f - height)
                    fields.add(PlacedField(
                        name = name,
                        type = type,
                        page = currentPage,
                        x = adjustedX,
                        y = adjustedY,
                        width = width,
                        height = height
                    ))
                }
                showFieldDialog = false
                editingField = null
                pendingFieldPosition = null
            },
            onDelete = {
                if (editingField != null) {
                    fields.removeAll { it.id == editingField!!.id }
                }
                showFieldDialog = false
                editingField = null
            },
            pageWidthPt = pdfPageWidthPt,
            pageHeightPt = pdfPageHeightPt
        )
    }

    if (showNameDialog) {
        var nameInput by rememberSaveable { mutableStateOf(templateName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Template Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            templateName = nameInput
                            showNameDialog = false
                            isSaving = true
                            scope.launch {
                                try {
                                    val templateDir = File(context.filesDir, "templates")
                                    templateDir.mkdirs()
                                    val destFile = File(templateDir, "template_${System.currentTimeMillis()}.pdf")
                                    sourceFile.copyTo(destFile, overwrite = true)
                                    val templateId = viewModel.addTemplate(templateName, destFile.absolutePath, pageCount).toInt()
                                    val templateFields = fields.map { f ->
                                        TemplateField(
                                            templateId = templateId,
                                            fieldName = f.name,
                                            fieldType = f.type,
                                            pageNumber = f.page,
                                            relativeX = f.x,
                                            relativeY = f.y,
                                            relativeWidth = f.width,
                                            relativeHeight = f.height
                                        )
                                    }
                                    viewModel.saveTemplateFields(templateId, templateFields)
                                    Toast.makeText(context, "Template saved!", Toast.LENGTH_SHORT).show()
                                    onDone()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                    enabled = nameInput.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FieldConfigDialog(
    existingField: PlacedField?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, width: Float, height: Float) -> Unit,
    onDelete: () -> Unit,
    pageWidthPt: Float = 612f,
    pageHeightPt: Float = 792f
) {
    var fieldName by rememberSaveable { mutableStateOf(existingField?.name ?: "") }
    var fieldType by rememberSaveable { mutableStateOf(existingField?.type ?: "text") }
    var fieldWidth by rememberSaveable { mutableStateOf(existingField?.width ?: 0.3f) }
    var fieldHeight by rememberSaveable { mutableStateOf(existingField?.height ?: 0.04f) }
    var selectedUnit by rememberSaveable { mutableStateOf("slider") }
    var widthText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }

    fun relToUnit(rel: Float, pagePt: Float, unit: String): Float {
        val inches = rel * pagePt / 72f
        return when (unit) {
            "in" -> inches
            "cm" -> inches * 2.54f
            "mm" -> inches * 25.4f
            else -> rel
        }
    }

    fun unitToRel(value: Float, pagePt: Float, unit: String): Float {
        val inches = when (unit) {
            "in" -> value
            "cm" -> value / 2.54f
            "mm" -> value / 25.4f
            else -> return value
        }
        return (inches * 72f / pagePt).coerceIn(0.02f, 0.95f)
    }

    LaunchedEffect(selectedUnit) {
        if (selectedUnit != "slider") {
            widthText = String.format("%.2f", relToUnit(fieldWidth, pageWidthPt, selectedUnit))
            heightText = String.format("%.2f", relToUnit(fieldHeight, pageHeightPt, selectedUnit))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingField != null) "Edit Field" else "Add Field") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text("Field Name") },
                    placeholder = { Text("e.g., Patient Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Field Type:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("text" to "Text", "date" to "Date", "signature" to "Signature", "checkbox" to "Check").forEach { (value, label) ->
                        FilterChip(
                            selected = fieldType == value,
                            onClick = {
                                fieldType = value
                                if (value == "signature") {
                                    fieldWidth = 0.3f
                                    fieldHeight = 0.08f
                                } else if (value == "checkbox") {
                                    fieldWidth = 0.04f
                                    fieldHeight = 0.04f
                                } else {
                                    if (fieldWidth < 0.1f) fieldWidth = 0.3f
                                    if (fieldHeight > 0.06f && value == "text") fieldHeight = 0.04f
                                }
                                if (selectedUnit != "slider") {
                                    widthText = String.format("%.2f", relToUnit(fieldWidth, pageWidthPt, selectedUnit))
                                    heightText = String.format("%.2f", relToUnit(fieldHeight, pageHeightPt, selectedUnit))
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Size mode:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("slider" to "Slider", "in" to "Inches", "cm" to "cm", "mm" to "mm").forEach { (value, label) ->
                        FilterChip(
                            selected = selectedUnit == value,
                            onClick = { selectedUnit = value },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedUnit == "slider") {
                    Text("Width:", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = fieldWidth,
                        onValueChange = { fieldWidth = it },
                        valueRange = 0.03f..0.9f
                    )
                    Text("Height:", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = fieldHeight,
                        onValueChange = { fieldHeight = it },
                        valueRange = 0.02f..0.2f
                    )
                } else {
                    val unitLabel = when (selectedUnit) {
                        "in" -> "inches"
                        "cm" -> "cm"
                        "mm" -> "mm"
                        else -> ""
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { newVal ->
                                widthText = newVal
                                newVal.toFloatOrNull()?.let { v ->
                                    fieldWidth = unitToRel(v, pageWidthPt, selectedUnit)
                                }
                            },
                            label = { Text("Width ($unitLabel)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { newVal ->
                                heightText = newVal
                                newVal.toFloatOrNull()?.let { v ->
                                    fieldHeight = unitToRel(v, pageHeightPt, selectedUnit)
                                }
                            },
                            label = { Text("Height ($unitLabel)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val actualWidthIn = fieldWidth * pageWidthPt / 72f
                    val actualHeightIn = fieldHeight * pageHeightPt / 72f
                    Text(
                        "≈ ${String.format("%.1f", actualWidthIn)}\" × ${String.format("%.1f", actualHeightIn)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (fieldName.isNotBlank()) onSave(fieldName, fieldType, fieldWidth, fieldHeight) },
                enabled = fieldName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existingField != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateFillScreen(
    templateId: Int,
    viewModel: PdfViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf<FormTemplate?>(null) }
    var fields by remember { mutableStateOf<List<TemplateField>>(emptyList()) }
    val fieldValues = remember { mutableStateMapOf<Int, String>() }
    val signatureBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val checkboxValues = remember { mutableStateMapOf<Int, Boolean>() }
    var isGenerating by remember { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var showSignPad by rememberSaveable { mutableStateOf(false) }
    var signFieldId by rememberSaveable { mutableStateOf(-1) }
    val settingsManager = remember { SettingsManager(context) }
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)

    LaunchedEffect(templateId) {
        template = viewModel.getTemplate(templateId)
        fields = viewModel.getTemplateFields(templateId)
        fields.forEach { field ->
            when (field.fieldType) {
                "checkbox" -> checkboxValues.putIfAbsent(field.id, false)
                else -> fieldValues.putIfAbsent(field.id, "")
            }
        }
    }

    BackHandler {
        if (showPreview) {
            showPreview = false
            previewFile?.delete()
            previewFile = null
            previewBitmaps = emptyList()
        } else if (showSignPad) {
            showSignPad = false
        } else {
            onCancel()
        }
    }

    if (showSignPad) {
        FullScreenDrawSignature(
            onDismiss = { showSignPad = false },
            onSignatureReady = { bitmap ->
                signatureBitmaps[signFieldId] = bitmap
                showSignPad = false
            }
        )
        return
    }

    if (showPreview && previewBitmaps.isNotEmpty()) {
        FilledPdfPreview(
            bitmaps = previewBitmaps,
            previewFile = previewFile,
            template = template,
            viewModel = viewModel,
            safDirectoryUri = safDirectoryUri,
            onBack = {
                showPreview = false
                previewFile?.delete()
                previewFile = null
                previewBitmaps = emptyList()
            },
            onSaved = {
                previewFile = null
                previewBitmaps = emptyList()
                onDone()
            }
        )
        return
    }

    if (template == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Fill: ${template!!.name}") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                val groupedFields = fields.groupBy { it.pageNumber }
                groupedFields.entries.sortedBy { it.key }.forEach { (page, pageFields) ->
                    item {
                        if (groupedFields.size > 1) {
                            Text(
                                "Page ${page + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = if (page > 0) 8.dp else 0.dp)
                            )
                        }
                    }
                    items(pageFields) { field ->
                        when (field.fieldType) {
                            "text" -> {
                                OutlinedTextField(
                                    value = fieldValues[field.id] ?: "",
                                    onValueChange = { fieldValues[field.id] = it },
                                    label = { Text(field.fieldName) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "date" -> {
                                OutlinedTextField(
                                    value = fieldValues[field.id] ?: "",
                                    onValueChange = { fieldValues[field.id] = it },
                                    label = { Text(field.fieldName) },
                                    placeholder = { Text("MM/DD/YYYY") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val today = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
                                            fieldValues[field.id] = today
                                        }) {
                                            Icon(Icons.Default.DateRange, "Today's date")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "signature" -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(field.fieldName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (signatureBitmaps.containsKey(field.id)) {
                                            Image(
                                                bitmap = signatureBitmaps[field.id]!!.asImageBitmap(),
                                                contentDescription = "Signature",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(60.dp)
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row {
                                                TextButton(onClick = {
                                                    signFieldId = field.id
                                                    showSignPad = true
                                                }) { Text("Re-sign") }
                                                TextButton(onClick = {
                                                    signatureBitmaps.remove(field.id)
                                                }) { Text("Clear") }
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    signFieldId = field.id
                                                    showSignPad = true
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Draw, null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Sign")
                                            }
                                        }
                                    }
                                }
                            }
                            "checkbox" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { checkboxValues[field.id] = !(checkboxValues[field.id] ?: false) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checkboxValues[field.id] ?: false,
                                        onCheckedChange = { checkboxValues[field.id] = it }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(field.fieldName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }

            if (isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        isGenerating = true
                        scope.launch {
                            try {
                                val result = generateFilledPdf(
                                    context, template!!, fields,
                                    fieldValues, signatureBitmaps, checkboxValues
                                )
                                if (result != null) {
                                    previewFile = result
                                    val bitmaps = renderPdfToBitmaps(result)
                                    previewBitmaps = bitmaps
                                    showPreview = true
                                } else {
                                    Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate PDF")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilledPdfPreview(
    bitmaps: List<Bitmap>,
    previewFile: File?,
    template: FormTemplate?,
    viewModel: PdfViewModel,
    safDirectoryUri: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (previewFile != null) {
                        IconButton(onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", previewFile
                                )
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
                        }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    }
                    Button(
                        onClick = {
                            if (previewFile != null) {
                                isSaving = true
                                scope.launch {
                                    try {
                                        val fileName = "${template?.name ?: "Filled"}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
                                        val savedUri = if (safDirectoryUri != null) {
                                            val folderUri = Uri.parse(safDirectoryUri)
                                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, fileName)
                                            if (pdfUri != null) {
                                                StorageUtils.copyPdfToSaf(context, previewFile, pdfUri)
                                                pdfUri.toString()
                                            } else {
                                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), fileName)
                                                previewFile.copyTo(appFile, overwrite = true)
                                                appFile.absolutePath
                                            }
                                        } else {
                                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), fileName)
                                            previewFile.copyTo(appFile, overwrite = true)
                                            appFile.absolutePath
                                        }
                                        viewModel.addDocument(fileName, savedUri, bitmaps.size, "Filled Forms")
                                        Toast.makeText(context, "Saved to documents!", Toast.LENGTH_SHORT).show()
                                        onSaved()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )

            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bitmaps.size) { index ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            Text(
                                "Page ${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp)
                            )
                            Image(
                                bitmap = bitmaps[index].asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun autoDetectFormFields(bitmap: Bitmap?, pageIndex: Int): List<PlacedField> = withContext(Dispatchers.Default) {
    if (bitmap == null) return@withContext emptyList()
    val result = mutableListOf<PlacedField>()
    try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        val labelPatterns = listOf(
            Regex("(?i)(name|first\\s*name|last\\s*name|full\\s*name|patient\\s*name|client\\s*name)"),
            Regex("(?i)(date|dob|date\\s*of\\s*birth|expir|effective)"),
            Regex("(?i)(sign|signature|authorized)"),
            Regex("(?i)(address|street|city|state|zip|postal)"),
            Regex("(?i)(phone|tel|mobile|cell|fax)"),
            Regex("(?i)(email|e-mail)"),
            Regex("(?i)(ssn|social\\s*security|tax\\s*id|ein)"),
            Regex("(?i)(amount|total|balance|price|cost|fee)"),
            Regex("(?i)(company|employer|organization|business)"),
            Regex("(?i)(title|position|occupation|job)")
        )

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                val box = line.boundingBox ?: continue

                if (text.endsWith(":") || text.endsWith("_") || text.contains("___") ||
                    labelPatterns.any { it.containsMatchIn(text) }) {

                    val labelRelX = box.left / bmpW
                    val labelRelY = box.top / bmpH
                    val labelRelH = (box.bottom - box.top) / bmpH

                    val fieldRelX = (box.right / bmpW + 0.01f).coerceAtMost(0.95f)
                    val fieldRelY = labelRelY
                    val fieldRelW = (0.95f - fieldRelX).coerceIn(0.1f, 0.4f)
                    val fieldRelH = (labelRelH * 1.2f).coerceIn(0.02f, 0.06f)

                    val fieldType = when {
                        text.contains(Regex("(?i)date|dob|expir")) -> "date"
                        text.contains(Regex("(?i)sign|signature")) -> "signature"
                        else -> "text"
                    }

                    val fieldName = text.replace(Regex("[:\\s_]+$"), "").trim()
                    if (fieldName.length < 2) continue

                    val overlaps = result.any { existing ->
                        existing.page == pageIndex &&
                        fieldRelX < existing.x + existing.width &&
                        fieldRelX + fieldRelW > existing.x &&
                        fieldRelY < existing.y + existing.height &&
                        fieldRelY + fieldRelH > existing.y
                    }
                    if (!overlaps) {
                        result.add(PlacedField(
                            name = fieldName,
                            type = fieldType,
                            page = pageIndex,
                            x = fieldRelX,
                            y = fieldRelY,
                            width = if (fieldType == "signature") 0.3f else fieldRelW,
                            height = if (fieldType == "signature") 0.06f else fieldRelH
                        ))
                    }
                }
            }
        }

        if (result.isEmpty()) {
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    val box = line.boundingBox ?: continue
                    if (text.length < 3) continue

                    val lineRelX = box.left / bmpW
                    val lineRelW = (box.right - box.left) / bmpW

                    if (lineRelW < 0.4f && !text.contains(Regex("^\\d+$"))) {
                        val fieldRelX = (box.right / bmpW + 0.01f).coerceAtMost(0.95f)
                        val remainW = 0.95f - fieldRelX
                        if (remainW > 0.08f) {
                            val overlaps = result.any { e ->
                                e.page == pageIndex &&
                                fieldRelX < e.x + e.width && fieldRelX + remainW.coerceAtMost(0.35f) > e.x &&
                                box.top / bmpH < e.y + e.height && box.bottom / bmpH > e.y
                            }
                            if (!overlaps) {
                                result.add(PlacedField(
                                    name = text.replace(Regex("[:\\s_]+$"), "").trim(),
                                    type = "text",
                                    page = pageIndex,
                                    x = fieldRelX,
                                    y = box.top / bmpH,
                                    width = remainW.coerceIn(0.1f, 0.35f),
                                    height = ((box.bottom - box.top) / bmpH * 1.2f).coerceIn(0.02f, 0.05f)
                                ))
                            }
                        }
                    }
                }
            }
        }

        recognizer.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    result
}

suspend fun generateFilledPdf(
    context: android.content.Context,
    template: FormTemplate,
    fields: List<TemplateField>,
    fieldValues: Map<Int, String>,
    signatureBitmaps: Map<Int, Bitmap>,
    checkboxValues: Map<Int, Boolean>
): File? = withContext(Dispatchers.IO) {
    try {
        val sourceFile = File(template.sourceFilePath)
        if (!sourceFile.exists()) return@withContext null

        val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val pageCount = renderer.pageCount

        val pdfDocument = AndroidPdfDocument()

        for (pageIndex in 0 until pageCount) {
            val pdfPage = renderer.openPage(pageIndex)
            val scale = 3
            val pageWidth = pdfPage.width * scale
            val pageHeight = pdfPage.height * scale

            val pageBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            pageBitmap.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            pdfPage.close()

            val canvas = android.graphics.Canvas(pageBitmap)

            val pageFields = fields.filter { it.pageNumber == pageIndex }
            for (field in pageFields) {
                val fx = field.relativeX * pageWidth
                val fy = field.relativeY * pageHeight
                val fw = field.relativeWidth * pageWidth
                val fh = field.relativeHeight * pageHeight

                when (field.fieldType) {
                    "text", "date" -> {
                        val text = fieldValues[field.id] ?: ""
                        if (text.isNotEmpty()) {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = fh * 0.6f
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT
                            }
                            val textY = fy + fh * 0.75f
                            canvas.drawText(text, fx + 4f, textY, paint)
                        }
                    }
                    "signature" -> {
                        val sigBitmap = signatureBitmaps[field.id]
                        if (sigBitmap != null) {
                            val destRect = android.graphics.Rect(
                                fx.toInt(), fy.toInt(),
                                (fx + fw).toInt(), (fy + fh).toInt()
                            )
                            canvas.drawBitmap(sigBitmap, null, destRect, null)
                        }
                    }
                    "checkbox" -> {
                        val checked = checkboxValues[field.id] ?: false
                        if (checked) {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = fh * 0.8f
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            canvas.drawText("\u2713", fx + fw * 0.15f, fy + fh * 0.8f, paint)
                        }
                    }
                }
            }

            val newPage = pdfDocument.startPage(
                AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            )
            newPage.canvas.drawBitmap(pageBitmap, 0f, 0f, null)
            pdfDocument.finishPage(newPage)
            pageBitmap.recycle()
        }

        renderer.close()
        fd.close()

        val outputFile = File(context.cacheDir, "filled_${template.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun renderPdfToBitmaps(file: File): List<Bitmap> = withContext(Dispatchers.IO) {
    val bitmaps = mutableListOf<Bitmap>()
    try {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val scale = 2
            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmaps.add(bitmap)
        }
        renderer.close()
        fd.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    bitmaps
}
