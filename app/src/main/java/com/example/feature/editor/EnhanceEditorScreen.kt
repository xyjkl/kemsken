package com.example.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.image.ImageEnhancer
import com.example.core.image.PerspectiveTransformer
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentCorners
import com.example.domain.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EnhanceEditorScreen(
    documentId: String,
    pageId: String,
    repository: DocumentRepository,
    onNavigateBack: () -> Unit,
    onNavigateToCrop: () -> Unit,
    onEnhanceSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.AUTO_ENHANCE) }

    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var sharpness by remember { mutableFloatStateOf(0f) }

    var showManualSliders by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        withContext(Dispatchers.IO) {
            val page = repository.getPageById(pageId)
            if (page != null) {
                val orig = repository.fileManager.loadBitmap(page.originalImagePath)
                if (orig != null) {
                    val corners = if (page.cropCoordinates.isNotBlank()) {
                        DocumentCorners.deserialize(page.cropCoordinates, orig.width.toFloat(), orig.height.toFloat())
                    } else null

                    val cropped = if (corners != null) {
                        PerspectiveTransformer.transform(orig, corners)
                    } else orig

                    val rotated = if (page.rotation != 0) {
                        PerspectiveTransformer.rotate(cropped, page.rotation.toFloat())
                    } else cropped

                    val currentFilter = try {
                        FilterType.valueOf(page.filterType)
                    } catch (e: Exception) {
                        FilterType.AUTO_ENHANCE
                    }

                    val initialEnhanced = ImageEnhancer.enhance(
                        rotated,
                        currentFilter,
                        page.brightness,
                        page.contrast,
                        page.saturation,
                        page.sharpness
                    )

                    withContext(Dispatchers.Main) {
                        sourceBitmap = rotated
                        previewBitmap = initialEnhanced
                        selectedFilter = currentFilter
                        brightness = page.brightness
                        contrast = page.contrast
                        saturation = page.saturation
                        sharpness = page.sharpness
                        isLoading = false
                    }
                }
            }
        }
    }

    fun updatePreview(filter: FilterType, b: Float, c: Float, s: Float, sh: Float) {
        val src = sourceBitmap ?: return
        scope.launch(Dispatchers.Default) {
            val enhanced = ImageEnhancer.enhance(src, filter, b, c, s, sh)
            withContext(Dispatchers.Main) {
                previewBitmap = enhanced
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10181C))
    ) {
        if (isLoading || previewBitmap == null) {
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
                        text = "Tingkatkan Kualitas Dokumen",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    IconButton(onClick = onNavigateToCrop) {
                        Icon(Icons.Default.Crop, contentDescription = "Potong", tint = Color(0xFF3CCBC5))
                    }
                }

                // Image Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Pratinjau Peningkatan",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                // Bottom Filters and Sliders Container
                Surface(
                    color = Color(0xFF182226),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Slider toggle row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter Pemindaian",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFBFCAD0)
                            )
                            IconButton(onClick = { showManualSliders = !showManualSliders }) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Penyesuaian Manual",
                                    tint = if (showManualSliders) Color(0xFF3CCBC5) else Color(0xFF89959A)
                                )
                            }
                        }

                        if (showManualSliders) {
                            // Brightness slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Kecerahan", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp))
                                Slider(
                                    value = brightness,
                                    onValueChange = {
                                        brightness = it
                                        updatePreview(selectedFilter, brightness, contrast, saturation, sharpness)
                                    },
                                    valueRange = -0.5f..0.5f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF3CCBC5), activeTrackColor = Color(0xFF087F8C)),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Contrast slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Kontras", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp))
                                Slider(
                                    value = contrast,
                                    onValueChange = {
                                        contrast = it
                                        updatePreview(selectedFilter, brightness, contrast, saturation, sharpness)
                                    },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF3CCBC5), activeTrackColor = Color(0xFF087F8C)),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // 10 Filters Horizontal Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterType.values().forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFF087F8C) else Color(0xFF212E35),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedFilter = filter
                                                updatePreview(filter, brightness, contrast, saturation, sharpness)
                                            }
                                    ) {
                                        Text(
                                            text = filter.displayName,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isSelected) Color.White else Color(0xFFBFCAD0),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save Button
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    repository.updatePageFilter(
                                        pageId = pageId,
                                        filter = selectedFilter,
                                        brightness = brightness,
                                        contrast = contrast,
                                        saturation = saturation,
                                        sharpness = sharpness
                                    )
                                    isSaving = false
                                    onEnhanceSaved()
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087F8C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_enhancement_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Simpan Peningkatan", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
