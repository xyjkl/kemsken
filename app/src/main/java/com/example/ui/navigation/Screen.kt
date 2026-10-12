package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera?scanMode={scanMode}") {
        fun createRoute(scanMode: String = "DOCUMENT") = "camera?scanMode=$scanMode"
    }
    object CropEditor : Screen("crop/{documentId}/{pageId}") {
        fun createRoute(documentId: String, pageId: String) = "crop/$documentId/$pageId"
    }
    object EnhanceEditor : Screen("enhance/{documentId}/{pageId}") {
        fun createRoute(documentId: String, pageId: String) = "enhance/$documentId/$pageId"
    }
    object MultiPageEditor : Screen("multipage/{documentId}") {
        fun createRoute(documentId: String) = "multipage/$documentId"
    }
    object DocumentViewer : Screen("viewer/{documentId}") {
        fun createRoute(documentId: String) = "viewer/$documentId"
    }
    object OcrResult : Screen("ocr/{documentId}/{pageId}") {
        fun createRoute(documentId: String, pageId: String) = "ocr/$documentId/$pageId"
    }
    object PdfExport : Screen("export/{documentId}") {
        fun createRoute(documentId: String) = "export/$documentId"
    }
    object AnnotationEditor : Screen("annotation/{documentId}/{pageId}") {
        fun createRoute(documentId: String, pageId: String) = "annotation/$documentId/$pageId"
    }
    object IdCardScanner : Screen("idcard")
    object QrScanner : Screen("qr")
    object RecycleBin : Screen("trash")
    object Folders : Screen("folders")
    object Settings : Screen("settings")
    object AppLock : Screen("applock")
}
