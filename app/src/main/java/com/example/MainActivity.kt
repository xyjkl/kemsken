package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.security.AppLockManager
import com.example.domain.model.FilterType
import com.example.domain.model.ScanMode
import com.example.feature.camera.CameraScreen
import com.example.feature.camera.CameraViewModel
import com.example.feature.editor.AnnotationEditorScreen
import com.example.feature.editor.CropEditorScreen
import com.example.feature.editor.EnhanceEditorScreen
import com.example.feature.editor.MultiPageEditorScreen
import com.example.feature.export.PdfExportScreen
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.idcard.IdCardScreen
import com.example.feature.ocr.OcrScreen
import com.example.feature.qr.QrScannerScreen
import com.example.feature.security.AppLockScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.trash.RecycleBinScreen
import com.example.feature.viewer.DocumentViewerScreen
import com.example.ui.navigation.Screen
import com.example.ui.theme.KemSkenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appLockManager = AppLockManager(this)
        val app = application as KemSkenApplication
        val repository = app.repository
        val fileManager = app.fileManager

        setContent {
            KemSkenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val isLockEnabled by appLockManager.isLockEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
                    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle(initialValue = false)
                    val scope = rememberCoroutineScope()

                    if (isLockEnabled && !isUnlocked) {
                        AppLockScreen(
                            appLockManager = appLockManager,
                            onUnlocked = {
                                appLockManager.unlock()
                            }
                        )
                    } else {
                        KemSkenNavApp(
                            repository = repository,
                            fileManager = fileManager,
                            appLockManager = appLockManager
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KemSkenNavApp(
    repository: com.example.data.repository.DocumentRepository,
    fileManager: com.example.core.storage.FileManager,
    appLockManager: AppLockManager
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(repository)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToCamera = { scanMode ->
                    navController.navigate(Screen.Camera.createRoute(scanMode))
                },
                onNavigateToDocument = { docId ->
                    navController.navigate(Screen.DocumentViewer.createRoute(docId))
                },
                onNavigateToExport = { docId ->
                    navController.navigate(Screen.PdfExport.createRoute(docId))
                },
                onNavigateToIdCard = {
                    navController.navigate(Screen.IdCardScanner.route)
                },
                onNavigateToQr = {
                    navController.navigate(Screen.QrScanner.route)
                },
                onNavigateToTrash = {
                    navController.navigate(Screen.RecycleBin.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onImportImage = { uri ->
                    scope.launch(Dispatchers.IO) {
                        val bitmap = fileManager.loadBitmapFromUri(uri)
                        if (bitmap != null) {
                            val docId = repository.saveNewDocument(
                                title = "KemSken_Impor_${System.currentTimeMillis()}",
                                capturedPages = listOf(Pair(bitmap, null)),
                                filter = FilterType.AUTO_ENHANCE
                            )
                            withContext(Dispatchers.Main) {
                                navController.navigate(Screen.DocumentViewer.createRoute(docId))
                            }
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Camera.route,
            arguments = listOf(navArgument("scanMode") {
                type = NavType.StringType
                defaultValue = "DOCUMENT"
            })
        ) { backStackEntry ->
            val modeStr = backStackEntry.arguments?.getString("scanMode") ?: "DOCUMENT"
            val scanMode = try { ScanMode.valueOf(modeStr) } catch (e: Exception) { ScanMode.DOCUMENT }

            val cameraViewModel: CameraViewModel = viewModel(
                factory = CameraViewModel.Factory(repository, scanMode)
            )

            CameraScreen(
                viewModel = cameraViewModel,
                onNavigateBack = { navController.popBackStack() },
                onDocumentCreated = { docId ->
                    navController.navigate(Screen.DocumentViewer.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onImportImage = { uri ->
                    scope.launch(Dispatchers.IO) {
                        val bitmap = fileManager.loadBitmapFromUri(uri)
                        if (bitmap != null) {
                            val docId = repository.saveNewDocument(
                                title = "KemSken_Impor_${System.currentTimeMillis()}",
                                capturedPages = listOf(Pair(bitmap, null)),
                                filter = FilterType.AUTO_ENHANCE
                            )
                            withContext(Dispatchers.Main) {
                                navController.navigate(Screen.DocumentViewer.createRoute(docId)) {
                                    popUpTo(Screen.Home.route)
                                }
                            }
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.CropEditor.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("pageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""

            CropEditorScreen(
                documentId = docId,
                pageId = pageId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onCropConfirmed = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EnhanceEditor.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("pageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""

            EnhanceEditorScreen(
                documentId = docId,
                pageId = pageId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCrop = {
                    navController.navigate(Screen.CropEditor.createRoute(docId, pageId))
                },
                onEnhanceSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MultiPageEditor.route,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""

            MultiPageEditorScreen(
                documentId = docId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCrop = { pageId ->
                    navController.navigate(Screen.CropEditor.createRoute(docId, pageId))
                },
                onNavigateToEnhance = { pageId ->
                    navController.navigate(Screen.EnhanceEditor.createRoute(docId, pageId))
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.createRoute("DOCUMENT"))
                },
                onImportImage = { uri ->
                    scope.launch(Dispatchers.IO) {
                        val bitmap = fileManager.loadBitmapFromUri(uri)
                        if (bitmap != null) {
                            repository.addPageToDocument(docId, bitmap, null)
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.DocumentViewer.route,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""

            DocumentViewerScreen(
                documentId = docId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMultiPage = {
                    navController.navigate(Screen.MultiPageEditor.createRoute(docId))
                },
                onNavigateToOcr = { pageId ->
                    navController.navigate(Screen.OcrResult.createRoute(docId, pageId))
                },
                onNavigateToExport = {
                    navController.navigate(Screen.PdfExport.createRoute(docId))
                },
                onNavigateToAnnotation = { pageId ->
                    navController.navigate(Screen.AnnotationEditor.createRoute(docId, pageId))
                }
            )
        }

        composable(
            route = Screen.OcrResult.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("pageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""

            OcrScreen(
                documentId = docId,
                pageId = pageId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PdfExport.route,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""

            PdfExportScreen(
                documentId = docId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AnnotationEditor.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("pageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: ""
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""

            AnnotationEditorScreen(
                documentId = docId,
                pageId = pageId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.IdCardScanner.route) {
            IdCardScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onDocumentCreated = { docId ->
                    navController.navigate(Screen.DocumentViewer.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.QrScanner.route) {
            QrScannerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.RecycleBin.route) {
            RecycleBinScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                fileManager = fileManager,
                appLockManager = appLockManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
