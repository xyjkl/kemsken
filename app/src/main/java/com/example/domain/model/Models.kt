package com.example.domain.model

import android.graphics.PointF

enum class ScanMode(val titleResId: String, val description: String) {
    DOCUMENT("Dokumen", "Standard paper & documents"),
    ID_CARD("Kartu ID / KTP", "Two-sided card layout"),
    BOOK("Buku", "Split two facing pages"),
    WHITEBOARD("Papan Tulis", "Anti-glare high clarity"),
    RECEIPT("Struk", "High contrast thermal receipt"),
    PHOTO("Foto", "Color preservation scan")
}

enum class FilterType(val displayName: String) {
    ORIGINAL("Asli"),
    AUTO_ENHANCE("Otomatis"),
    MAGIC_COLOR("Warna Ajaib"),
    BLACK_AND_WHITE("Hitam Putih"),
    GRAYSCALE("Skala Abu"),
    HIGH_CONTRAST("Kontras Tinggi"),
    BRIGHTEN("Terangkan"),
    SHARPEN("Pertajam"),
    WHITEBOARD("Papan Tulis"),
    RECEIPT("Struk")
}

data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    fun toList(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    fun serialize(): String =
        "${topLeft.x},${topLeft.y};${topRight.x},${topRight.y};${bottomRight.x},${bottomRight.y};${bottomLeft.x},${bottomLeft.y}"

    companion object {
        fun defaultFull(width: Float, height: Float): DocumentCorners = DocumentCorners(
            topLeft = PointF(0f, 0f),
            topRight = PointF(width, 0f),
            bottomRight = PointF(width, height),
            bottomLeft = PointF(0f, height)
        )

        fun defaultInset(width: Float, height: Float, marginRatio: Float = 0.08f): DocumentCorners = DocumentCorners(
            topLeft = PointF(width * marginRatio, height * marginRatio),
            topRight = PointF(width * (1f - marginRatio), height * marginRatio),
            bottomRight = PointF(width * (1f - marginRatio), height * (1f - marginRatio)),
            bottomLeft = PointF(width * marginRatio, height * (1f - marginRatio))
        )

        fun deserialize(serialized: String, defaultWidth: Float, defaultHeight: Float): DocumentCorners {
            if (serialized.isBlank()) return defaultInset(defaultWidth, defaultHeight)
            return try {
                val parts = serialized.split(";")
                if (parts.size != 4) return defaultInset(defaultWidth, defaultHeight)
                val p0 = parts[0].split(",").map { it.toFloat() }
                val p1 = parts[1].split(",").map { it.toFloat() }
                val p2 = parts[2].split(",").map { it.toFloat() }
                val p3 = parts[3].split(",").map { it.toFloat() }
                DocumentCorners(
                    topLeft = PointF(p0[0], p0[1]),
                    topRight = PointF(p1[0], p1[1]),
                    bottomRight = PointF(p2[0], p2[1]),
                    bottomLeft = PointF(p3[0], p3[1])
                )
            } catch (e: Exception) {
                defaultInset(defaultWidth, defaultHeight)
            }
        }
    }
}

enum class PdfPageSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
    AUTO("Otomatis (Ukuran Gambar)", 0, 0),
    A4("A4 (210 x 297 mm)", 595, 842),
    LETTER("Letter (8.5 x 11 in)", 612, 792),
    LEGAL("Legal (8.5 x 14 in)", 612, 1008),
    A5("A5 (148 x 210 mm)", 420, 595)
}

enum class PdfQuality(val displayName: String, val compressionQuality: Int, val maxDimension: Int) {
    SMALL("Kecil (Hemat Ukuran)", 65, 1280),
    STANDARD("Standar (Seimbang)", 85, 1920),
    HIGH("Kualitas Tinggi (Asli)", 95, 3000)
}

enum class SortOption(val displayName: String) {
    NEWEST("Terbaru"),
    OLDEST("Terlama"),
    NAME_ASC("Nama A–Z"),
    NAME_DESC("Nama Z–A"),
    MODIFIED("Terakhir Diubah"),
    PAGE_COUNT("Jumlah Halaman")
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    TXT("txt", "text/plain")
}
