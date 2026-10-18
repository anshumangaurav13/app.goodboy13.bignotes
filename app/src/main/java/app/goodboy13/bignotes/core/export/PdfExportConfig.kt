package app.goodboy13.bignotes.core.export

enum class PageFormat(
    val displayName: String,
    val widthPt: Int,
    val heightPt: Int
) {
    LEGAL_14("Legal 14\" (8.5 × 14 in)", 612, 1008),
    LETTER("Letter (8.5 × 11 in)", 612, 792),
    A4("A4 (210 × 297 mm)", 595, 842)
}

enum class PdfRenderMode(
    val displayName: String,
    val description: String
) {
    HYBRID("Vector + Textured", "Crisp vector ink + textured graphite lead"),
    VECTOR("Vector Only", "Crisp scalable paths for all strokes"),
    RASTER_300DPI("300 DPI Print", "Full high-resolution page rasterization")
}

data class PdfExportOptions(
    val pageFormat: PageFormat = PageFormat.A4,
    val renderMode: PdfRenderMode = PdfRenderMode.RASTER_300DPI,
    val embedSearchableText: Boolean = true
)
