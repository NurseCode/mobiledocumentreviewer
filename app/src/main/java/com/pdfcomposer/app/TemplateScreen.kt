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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val fields = remember { mutableStateListOf<PlacedField>() }
    var showFieldDialog by remember { mutableStateOf(false) }
    var pendingFieldPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingField by remember { mutableStateOf<PlacedField?>(null) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var selectedFieldId by remember { mutableStateOf<String?>(null) }
    var draggingFieldId by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

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
                if (selectedFieldId != null) "Drag field to move. Drag handles to resize. Tap elsewhere to deselect."
                else "Tap to place a field. Tap a field to select it. Double-tap to edit.",
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedFieldId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            val pageFields = fields.filter { it.page == currentPage }
            val handlePxSize = 28f
            val handleHitPx = 48f
            val minFieldRel = 0.025f

            if (pdfBitmap != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentPage, selectedFieldId) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        val pdfBmp = pdfBitmap ?: return@detectTapGestures
                                        val pScale = minOf(size.width.toFloat() / pdfBmp.width.toFloat(), size.height.toFloat() / pdfBmp.height.toFloat())
                                        val pW = pdfBmp.width * pScale
                                        val pH = pdfBmp.height * pScale
                                        val pLeft = (size.width - pW) / 2f
                                        val pTop = (size.height - pH) / 2f
                                        val relX = ((offset.x - pLeft) / pW).coerceIn(0f, 1f)
                                        val relY = ((offset.y - pTop) / pH).coerceIn(0f, 1f)
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
                                        val pdfBmp = pdfBitmap ?: return@detectTapGestures
                                        val pScale = minOf(size.width.toFloat() / pdfBmp.width.toFloat(), size.height.toFloat() / pdfBmp.height.toFloat())
                                        val pW = pdfBmp.width * pScale
                                        val pH = pdfBmp.height * pScale
                                        val pLeft = (size.width - pW) / 2f
                                        val pTop = (size.height - pH) / 2f
                                        val relX = ((offset.x - pLeft) / pW).coerceIn(0f, 1f)
                                        val relY = ((offset.y - pTop) / pH).coerceIn(0f, 1f)
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
                            .pointerInput(currentPage, selectedFieldId, fields.toList()) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val pdfBmp = pdfBitmap ?: return@detectDragGestures
                                        val pScale = minOf(size.width.toFloat() / pdfBmp.width.toFloat(), size.height.toFloat() / pdfBmp.height.toFloat())
                                        val pW = pdfBmp.width * pScale
                                        val pH = pdfBmp.height * pScale
                                        val pLeft = (size.width - pW) / 2f
                                        val pTop = (size.height - pH) / 2f
                                        val px = offset.x
                                        val py = offset.y

                                        val sel = pageFields.find { it.id == selectedFieldId }
                                        if (sel != null) {
                                            val fL = pLeft + sel.x * pW
                                            val fT = pTop + sel.y * pH
                                            val fR = fL + sel.width * pW
                                            val fB = fT + sel.height * pH
                                            val midX = (fL + fR) / 2f
                                            val midY = (fT + fB) / 2f
                                            val hh = handleHitPx / 2f

                                            val nearL = kotlin.math.abs(px - fL) < hh
                                            val nearR = kotlin.math.abs(px - fR) < hh
                                            val nearT = kotlin.math.abs(py - fT) < hh
                                            val nearB = kotlin.math.abs(py - fB) < hh
                                            val nearMidX = kotlin.math.abs(px - midX) < hh
                                            val nearMidY = kotlin.math.abs(py - midY) < hh

                                            val handle = when {
                                                nearL && nearT -> "tl"
                                                nearR && nearT -> "tr"
                                                nearL && nearB -> "bl"
                                                nearR && nearB -> "br"
                                                nearT && nearMidX -> "tm"
                                                nearB && nearMidX -> "bm"
                                                nearL && nearMidY -> "ml"
                                                nearR && nearMidY -> "mr"
                                                else -> null
                                            }
                                            if (handle != null) {
                                                draggingFieldId = "handle:$handle:${sel.id}"
                                                return@detectDragGestures
                                            }
                                        }

                                        val dragTarget = pageFields.find { f ->
                                            val fL2 = pLeft + f.x * pW
                                            val fT2 = pTop + f.y * pH
                                            val fR2 = fL2 + f.width * pW
                                            val fB2 = fT2 + f.height * pH
                                            px >= fL2 && px <= fR2 && py >= fT2 && py <= fB2
                                        }
                                        if (dragTarget != null) {
                                            selectedFieldId = dragTarget.id
                                            draggingFieldId = "move:${dragTarget.id}"
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val pdfBmp = pdfBitmap ?: return@detectDragGestures
                                        val pScale = minOf(size.width.toFloat() / pdfBmp.width.toFloat(), size.height.toFloat() / pdfBmp.height.toFloat())
                                        val pW = pdfBmp.width * pScale
                                        val pH = pdfBmp.height * pScale
                                        val dRelX = dragAmount.x / pW
                                        val dRelY = dragAmount.y / pH
                                        val dragInfo = draggingFieldId ?: return@detectDragGestures
                                        val parts = dragInfo.split(":")
                                        val mode = parts[0]

                                        if (mode == "move") {
                                            val fId = parts[1]
                                            val idx = fields.indexOfFirst { it.id == fId }
                                            if (idx >= 0) {
                                                val f = fields[idx]
                                                fields[idx] = f.copy(
                                                    x = (f.x + dRelX).coerceIn(0f, 1f - f.width),
                                                    y = (f.y + dRelY).coerceIn(0f, 1f - f.height)
                                                )
                                            }
                                        } else if (mode == "handle" && parts.size >= 3) {
                                            val handle = parts[1]
                                            val fId = parts[2]
                                            val idx = fields.indexOfFirst { it.id == fId }
                                            if (idx >= 0) {
                                                val f = fields[idx]
                                                val right = f.x + f.width
                                                val bottom = f.y + f.height
                                                when (handle) {
                                                    "tl" -> {
                                                        val nx = (f.x + dRelX).coerceIn(0f, right - minFieldRel)
                                                        val ny = (f.y + dRelY).coerceIn(0f, bottom - minFieldRel)
                                                        fields[idx] = f.copy(x = nx, y = ny, width = right - nx, height = bottom - ny)
                                                    }
                                                    "tr" -> {
                                                        val nw = (f.width + dRelX).coerceIn(minFieldRel, 1f - f.x)
                                                        val ny = (f.y + dRelY).coerceIn(0f, bottom - minFieldRel)
                                                        fields[idx] = f.copy(y = ny, width = nw, height = bottom - ny)
                                                    }
                                                    "bl" -> {
                                                        val nx = (f.x + dRelX).coerceIn(0f, right - minFieldRel)
                                                        val nh = (f.height + dRelY).coerceIn(minFieldRel, 1f - f.y)
                                                        fields[idx] = f.copy(x = nx, width = right - nx, height = nh)
                                                    }
                                                    "br" -> {
                                                        val nw = (f.width + dRelX).coerceIn(minFieldRel, 1f - f.x)
                                                        val nh = (f.height + dRelY).coerceIn(minFieldRel, 1f - f.y)
                                                        fields[idx] = f.copy(width = nw, height = nh)
                                                    }
                                                    "tm" -> {
                                                        val ny = (f.y + dRelY).coerceIn(0f, bottom - minFieldRel)
                                                        fields[idx] = f.copy(y = ny, height = bottom - ny)
                                                    }
                                                    "bm" -> {
                                                        val nh = (f.height + dRelY).coerceIn(minFieldRel, 1f - f.y)
                                                        fields[idx] = f.copy(height = nh)
                                                    }
                                                    "ml" -> {
                                                        val nx = (f.x + dRelX).coerceIn(0f, right - minFieldRel)
                                                        fields[idx] = f.copy(x = nx, width = right - nx)
                                                    }
                                                    "mr" -> {
                                                        val nw = (f.width + dRelX).coerceIn(minFieldRel, 1f - f.x)
                                                        fields[idx] = f.copy(width = nw)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = { draggingFieldId = null },
                                    onDragCancel = { draggingFieldId = null }
                                )
                            }
                    ) {
                        val pdfBmp = pdfBitmap!!
                        val pageScale = minOf(size.width / pdfBmp.width.toFloat(), size.height / pdfBmp.height.toFloat())
                        val pageW = pdfBmp.width * pageScale
                        val pageH = pdfBmp.height * pageScale
                        val pageLeft = (size.width - pageW) / 2f
                        val pageTop = (size.height - pageH) / 2f

                        drawImage(
                            image = pdfBmp.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(pageLeft.toInt(), pageTop.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(pageW.toInt(), pageH.toInt())
                        )

                        pageFields.forEach { field ->
                            val fLeft = pageLeft + field.x * pageW
                            val fTop = pageTop + field.y * pageH
                            val fW = field.width * pageW
                            val fH = field.height * pageH
                            val isSelected = field.id == selectedFieldId

                            val fieldColor = when (field.type) {
                                "text" -> Color(0x300000FF)
                                "date" -> Color(0x3000AA00)
                                "signature" -> Color(0x30FF0000)
                                "checkbox" -> Color(0x30FF8800)
                                else -> Color(0x300000FF)
                            }
                            val borderColor = when (field.type) {
                                "text" -> Color(0xFF0000FF)
                                "date" -> Color(0xFF00AA00)
                                "signature" -> Color(0xFFFF0000)
                                "checkbox" -> Color(0xFFFF8800)
                                else -> Color(0xFF0000FF)
                            }

                            drawRect(
                                color = if (isSelected) fieldColor.copy(alpha = 0.45f) else fieldColor,
                                topLeft = Offset(fLeft, fTop),
                                size = androidx.compose.ui.geometry.Size(fW, fH)
                            )
                            drawRect(
                                color = borderColor,
                                topLeft = Offset(fLeft, fTop),
                                size = androidx.compose.ui.geometry.Size(fW, fH),
                                style = Stroke(width = if (isSelected) 3f else 1.5f)
                            )

                            if (isSelected) {
                                val hs = handlePxSize
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
                                    drawRect(
                                        color = Color.White,
                                        topLeft = pos,
                                        size = androidx.compose.ui.geometry.Size(hs, hs)
                                    )
                                    drawRect(
                                        color = borderColor,
                                        topLeft = pos,
                                        size = androidx.compose.ui.geometry.Size(hs, hs),
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                            }

                            drawContext.canvas.nativeCanvas.drawText(
                                field.name,
                                fLeft + 4f,
                                fTop + fH / 2f + 5f,
                                android.graphics.Paint().apply {
                                    color = borderColor.hashCode()
                                    textSize = 24f
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

            if (fields.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${fields.size} field${if (fields.size != 1) "s" else ""} placed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
            }
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
    onDelete: () -> Unit
) {
    var fieldName by rememberSaveable { mutableStateOf(existingField?.name ?: "") }
    var fieldType by rememberSaveable { mutableStateOf(existingField?.type ?: "text") }
    var fieldWidth by rememberSaveable { mutableStateOf(existingField?.width ?: 0.3f) }
    var fieldHeight by rememberSaveable { mutableStateOf(existingField?.height ?: 0.04f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingField != null) "Edit Field" else "Add Field") },
        text = {
            Column {
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
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
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
                    valueRange = 0.02f..0.15f
                )
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
