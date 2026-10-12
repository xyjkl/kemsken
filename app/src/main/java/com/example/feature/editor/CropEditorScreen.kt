package com.example.feature.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.image.DocumentDetector
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentCorners
import com.example.ui.components.CropHandlesCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CropEditorScreen(
    documentId: String,
    pageId: String,
    repository: DocumentRepository,
    onNavigateBack: () -> Unit,
    onCropConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf<DocumentCorners?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        withContext(Dispatchers.IO) {
            val page = repository.getPageById(pageId)
            if (page != null) {
                val loadedBitmap = repository.fileManager.loadBitmap(page.originalImagePath)
                if (loadedBitmap != null) {
                    val initialCorners = if (page.cropCoordinates.isNotBlank()) {
                        DocumentCorners.deserialize(
                            page.cropCoordinates,
                            loadedBitmap.width.toFloat(),
                            loadedBitmap.height.toFloat()
                        )
                    } else {
                        // Auto detect or default inset
                        val detector = DocumentDetector()
                        detector.detect(loadedBitmap) ?: DocumentCorners.defaultInset(
                            loadedBitmap.width.toFloat(),
                            loadedBitmap.height.toFloat()
                        )
                    }
                    withContext(Dispatchers.Main) {
                        bitmap = loadedBitmap
                        corners = initialCorners
                        isLoading = false
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10181C))
    ) {
        if (isLoading || bitmap == null || corners == null) {
            CircularProgressIndicator(
                color = Color(0xFF3CCBC5),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.White)
                    }

                    Text(
                        text = "Sesuaikan Sudut Potong",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.rotatePage(pageId)
                                onCropConfirmed()
                            }
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Putar", tint = Color.White)
                    }
                }

                // Interactive Crop Canvas Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    CropHandlesCanvas(
                        bitmap = bitmap!!,
                        corners = corners!!,
                        onCornersChanged = { newCorners ->
                            corners = newCorners
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom Tools Bar
                Surface(
                    color = Color(0xFF182226),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Action buttons: Auto Detect, Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val bmp = bitmap ?: return@OutlinedButton
                                    val detected = DocumentDetector().detect(bmp)
                                    if (detected != null) {
                                        corners = detected
                                        Toast.makeText(context, "Sudut otomatis terdeteksi", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3CCBC5))
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Deteksi Otomatis")
                            }

                            OutlinedButton(
                                onClick = {
                                    val bmp = bitmap ?: return@OutlinedButton
                                    corners = DocumentCorners.defaultFull(bmp.width.toFloat(), bmp.height.toFloat())
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBFCAD0))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Penuh")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Confirm Button
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val currentCorners = corners ?: return@launch
                                    repository.updatePageCrop(pageId, currentCorners)
                                    isSaving = false
                                    onCropConfirmed()
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087F8C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("confirm_crop_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Terapkan Potongan", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
