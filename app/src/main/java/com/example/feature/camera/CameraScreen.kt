package com.example.feature.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.DocumentCorners
import com.example.domain.model.ScanMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateBack: () -> Unit,
    onDocumentCreated: (String) -> Unit,
    onImportImage: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportImage(uri)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF10181C)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF3CCBC5),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Izin Kamera Diperlukan",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "KemSken membutuhkan izin kamera untuk memindai dokumen fisik secara langsung di perangkat Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBFCAD0),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087F8C))
                ) {
                    Text("Berikan Izin Kamera")
                }
            }
        }
        return
    }

    // CameraX Setup
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Laser beam animation across screen
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_beam"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

                // Analysis use case for real-time document edge detection
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastAnalysisTime = 0L
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    // Analyze at most every 180ms to conserve battery & keep 60fps preview
                    if (now - lastAnalysisTime > 180) {
                        lastAnalysisTime = now
                        val bitmap = imageProxy.toBitmapSafe()
                        if (bitmap != null) {
                            viewModel.onAnalysisFrame(bitmap)
                        }
                    }
                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        analysis
                    )
                    camera.cameraControl.enableTorch(uiState.isFlashOn)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                cameraExecutor.shutdown()
            }
        }

        // Real-time Scanning Viewfinder & Laser Grid Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Document detection bounds or active viewfinder frame
            val marginX = canvasWidth * 0.08f
            val marginY = canvasHeight * 0.16f
            val frameWidth = canvasWidth - (marginX * 2)
            val frameHeight = canvasHeight * 0.60f

            val frameTop = marginY
            val frameBottom = marginY + frameHeight
            val frameLeft = marginX
            val frameRight = marginX + frameWidth

            // Dim area outside viewfinder
            val darkColor = Color(0x66000000)
            drawRect(color = darkColor, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(canvasWidth, frameTop))
            drawRect(color = darkColor, topLeft = Offset(0f, frameBottom), size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - frameBottom))
            drawRect(color = darkColor, topLeft = Offset(0f, frameTop), size = androidx.compose.ui.geometry.Size(frameLeft, frameHeight))
            drawRect(color = darkColor, topLeft = Offset(frameRight, frameTop), size = androidx.compose.ui.geometry.Size(canvasWidth - frameRight, frameHeight))

            // 4 Corner Brackets
            val cornerLen = 36.dp.toPx()
            val bracketColor = Color(0xFF3CCBC5)
            val strokeW = 4.dp.toPx()

            // TL
            drawLine(bracketColor, Offset(frameLeft, frameTop), Offset(frameLeft + cornerLen, frameTop), strokeW)
            drawLine(bracketColor, Offset(frameLeft, frameTop), Offset(frameLeft, frameTop + cornerLen), strokeW)
            // TR
            drawLine(bracketColor, Offset(frameRight, frameTop), Offset(frameRight - cornerLen, frameTop), strokeW)
            drawLine(bracketColor, Offset(frameRight, frameTop), Offset(frameRight, frameTop + cornerLen), strokeW)
            // BL
            drawLine(bracketColor, Offset(frameLeft, frameBottom), Offset(frameLeft + cornerLen, frameBottom), strokeW)
            drawLine(bracketColor, Offset(frameLeft, frameBottom), Offset(frameLeft, frameBottom - cornerLen), strokeW)
            // BR
            drawLine(bracketColor, Offset(frameRight, frameBottom), Offset(frameRight - cornerLen, frameBottom), strokeW)
            drawLine(bracketColor, Offset(frameRight, frameBottom), Offset(frameRight, frameBottom - cornerLen), strokeW)

            // Animated Laser Beam Line
            val laserY = frameTop + frameHeight * laserProgress
            drawLine(
                color = Color(0xFF3CCBC5),
                start = Offset(frameLeft + 4f, laserY),
                end = Offset(frameRight - 4f, laserY),
                strokeWidth = 3.dp.toPx()
            )
            // Laser beam soft glow
            drawLine(
                color = Color(0x553CCBC5),
                start = Offset(frameLeft + 4f, laserY),
                end = Offset(frameRight - 4f, laserY),
                strokeWidth = 8.dp.toPx()
            )
        }

        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x66000000))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }

            // Status chip: Mode
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x88000000),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = uiState.scanMode.titleResId,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF3CCBC5),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Flash Toggle Button
            IconButton(
                onClick = viewModel::toggleFlash,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .testTag("flash_toggle_button")
            ) {
                Icon(
                    imageVector = if (uiState.isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (uiState.isFlashOn) Color(0xFFFFD54F) else Color.White
                )
            }
        }

        // Bottom Controls Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xD910181C))
                .padding(bottom = 28.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scan Mode Selector Horizontal Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScanMode.values().forEach { mode ->
                    val isSelected = uiState.scanMode == mode
                    Text(
                        text = mode.titleResId.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        color = if (isSelected) Color(0xFF3CCBC5) else Color(0xFF89959A),
                        modifier = Modifier
                            .clickable { viewModel.onScanModeChanged(mode) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Actions Row (Gallery, Capture Button, Done)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery shortcut
                IconButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Galeri",
                        tint = Color.White
                    )
                }

                // Primary Shutter Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color(0xFF3CCBC5), CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !uiState.isSaving) {
                            capturePhoto(context, imageCapture, cameraExecutor) { bitmap ->
                                viewModel.onPageCaptured(bitmap, uiState.detectedCorners)
                            }
                        }
                        .testTag("shutter_button")
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = Color(0xFF087F8C),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF087F8C))
                        )
                    }
                }

                // Done / Finish Button with page count badge
                if (uiState.capturedPages.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF087F8C))
                            .clickable(enabled = !uiState.isSaving) {
                                viewModel.saveDocument { docId ->
                                    onDocumentCreated(docId)
                                }
                            }
                            .testTag("done_capture_button")
                    ) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = Color(0xFF3CCBC5), contentColor = Color(0xFF003735)) {
                                    Text("${uiState.capturedPages.size}")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selesai",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(50.dp))
                }
            }
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onSuccess: (Bitmap) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmapSafe()
                image.close()
                if (bitmap != null) {
                    ContextCompat.getMainExecutor(context).execute {
                        onSuccess(bitmap)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

fun ImageProxy.toBitmapSafe(): Bitmap? {
    return try {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap != null && imageInfo.rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        null
    }
}
