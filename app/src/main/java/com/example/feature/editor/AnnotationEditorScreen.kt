package com.example.feature.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.DocumentRepository
import com.example.ui.components.SignaturePadView
import com.example.ui.components.renderSignatureToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationEditorScreen(
    documentId: String,
    pageId: String,
    repository: DocumentRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Signature, 1: Watermark
    val signaturePaths = remember { mutableStateListOf<List<Offset>>() }
    var watermarkText by remember { mutableStateOf("RAHASIA") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tanda Tangan & Tanda Air",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Tanda Tangan Elektronik") },
                        icon = { Icon(Icons.Default.Draw, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Tanda Air (Watermark)") },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Signature drawing area
                    Text(
                        text = "Goreskan tanda tangan Anda pada area putih di bawah ini:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("signature_canvas_area")
                    ) {
                        SignaturePadView(
                            paths = signaturePaths,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { signaturePaths.clear() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bersihkan")
                    }
                } else {
                    // Watermark text input
                    Text(
                        text = "Tambahkan teks tanda air transparan yang akan disematkan secara diagonal pada dokumen:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Teks Tanda Air (Watermark)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Apply and Save Button
            Button(
                onClick = {
                    isSaving = true
                    scope.launch(Dispatchers.IO) {
                        val page = repository.getPageById(pageId)
                        if (page != null) {
                            val baseBitmap = repository.fileManager.loadBitmap(page.processedImagePath)
                            if (baseBitmap != null) {
                                val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                val canvas = Canvas(mutableBitmap)

                                if (selectedTab == 0 && signaturePaths.isNotEmpty()) {
                                    // Stamp signature onto bottom-right of page
                                    val sigBmp = renderSignatureToBitmap(signaturePaths, 400, 200, 6f)
                                    val left = mutableBitmap.width - sigBmp.width - 40
                                    val top = mutableBitmap.height - sigBmp.height - 60
                                    canvas.drawBitmap(sigBmp, left.toFloat(), top.toFloat(), null)
                                    sigBmp.recycle()
                                } else if (selectedTab == 1 && watermarkText.isNotBlank()) {
                                    // Stamp diagonal watermark
                                    val paint = Paint().apply {
                                        color = AndroidColor.argb(50, 100, 100, 100)
                                        textSize = 72f
                                        isAntiAlias = true
                                        textAlign = Paint.Align.CENTER
                                    }
                                    canvas.save()
                                    canvas.rotate(-45f, mutableBitmap.width / 2f, mutableBitmap.height / 2f)
                                    canvas.drawText(watermarkText, mutableBitmap.width / 2f, mutableBitmap.height / 2f, paint)
                                    canvas.restore()
                                }

                                repository.fileManager.savePageBitmap(documentId, pageId, mutableBitmap, isOriginal = false)
                                mutableBitmap.recycle()
                                baseBitmap.recycle()
                            }
                        }

                        withContext(Dispatchers.Main) {
                            isSaving = false
                            Toast.makeText(context, "Berhasil disematkan pada dokumen", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    }
                },
                enabled = !isSaving && (if (selectedTab == 0) signaturePaths.isNotEmpty() else watermarkText.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("apply_annotation_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (selectedTab == 0) "Sematkan Tanda Tangan" else "Sematkan Tanda Air",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
