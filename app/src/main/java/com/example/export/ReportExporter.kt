package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.WargaKosResident
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val fileName: String,
    val mimeType: String,
    val shareUri: Uri,
    val publicDisplayPath: String,
    val internalFile: File,
)

/**
 * Generates authentic Excel (.xlsx) and PDF (.pdf) reports matching the reference
 * Data Warga Kos layout (merged multi-row headers, >=40 rows, RW/RT signature block),
 * saves them to internal storage & Downloads/WargaKos without needing storage permissions,
 * and creates FileProvider URIs for sharing directly to messaging apps.
 */
object ReportExporter {

    private const val MIN_REPORT_ROWS = 40
    const val MIME_PDF = "application/pdf"
    const val MIME_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun buildReportRows(residents: List<WargaKosResident>): List<List<String>> {
        val totalRows = maxOf(MIN_REPORT_ROWS, residents.size)
        return List(totalRows) { idx ->
            if (idx < residents.size) {
                residents[idx].toReportRow(idx + 1)
            } else {
                listOf((idx + 1).toString(), "", "", "", "", "", "", "", "")
            }
        }
    }

    fun exportExcel(
        context: Context,
        title: String,
        residents: List<WargaKosResident>,
    ): ExportResult {
        val safeTitle = title.trim().ifEmpty { "DATA WARGA KOS" }
        val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.forLanguageTag("id-ID")).format(Date())
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "laporan_warga_kos_$fileStamp.xlsx"

        val bytes = buildXlsxBytes(safeTitle, timestamp, residents)
        return persistAndPrepareShare(context, fileName, MIME_EXCEL, bytes)
    }

    fun exportPdf(
        context: Context,
        title: String,
        residents: List<WargaKosResident>,
    ): ExportResult {
        val safeTitle = title.trim().ifEmpty { "DATA WARGA KOS" }
        val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.forLanguageTag("id-ID")).format(Date())
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "laporan_warga_kos_$fileStamp.pdf"

        val bytes = buildPdfBytes(safeTitle, timestamp, residents)
        return persistAndPrepareShare(context, fileName, MIME_PDF, bytes)
    }

    fun buildShareSummaryText(title: String, residents: List<WargaKosResident>): String {
        val safeTitle = title.trim().ifEmpty { "DATA WARGA KOS" }
        val sb = StringBuilder()
        sb.appendLine("*$safeTitle*")
        sb.appendLine("Total penghuni tercatat: ${residents.size}")
        sb.appendLine("----------------------------")
        residents.forEachIndexed { index, r ->
            val status = when {
                r.statusNikah.isNotBlank() && r.statusNikah != "Tidak" -> "Nikah (${r.statusNikah})"
                r.statusBelumNikah.isNotBlank() && r.statusBelumNikah != "Tidak" -> "Belum Nikah (${r.statusBelumNikah})"
                else -> "-"
            }
            sb.appendLine("${index + 1}. ${r.nama} | Kamar: ${r.noKamar.ifBlank { "-" }} (${r.jumlahOrang.ifBlank { "1" }} org)")
            if (r.pemilik.isNotBlank()) sb.appendLine("   Pemilik: ${r.pemilik}")
            if (r.alamat.isNotBlank()) sb.appendLine("   Alamat: ${r.alamat}")
            sb.appendLine("   Status: $status${if (r.keterangan.isNotBlank()) " | Ket: ${r.keterangan}" else ""}")
        }
        return sb.toString().trim()
    }

    fun shareFileIntent(context: Context, result: ExportResult, chooserTitle: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.shareUri)
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            putExtra(Intent.EXTRA_TEXT, "Laporan Data Warga Kos (${result.fileName})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    fun shareTextIntent(text: String, chooserTitle: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    private fun persistAndPrepareShare(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportResult {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val internalFile = File(exportDir, fileName)
        FileOutputStream(internalFile).use { it.write(bytes) }

        val publicDisplayPath = saveToDownloadsIfSupported(context, fileName, mimeType, bytes)
            ?: internalFile.absolutePath

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            internalFile
        )

        return ExportResult(
            fileName = fileName,
            mimeType = mimeType,
            shareUri = shareUri,
            publicDisplayPath = publicDisplayPath,
            internalFile = internalFile,
        )
    }

    private fun saveToDownloadsIfSupported(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/WargaKos")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/WargaKos/$fileName"
                } else {
                    null
                }
            } else {
                val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (extDir != null) {
                    val folder = File(extDir, "WargaKos").apply { mkdirs() }
                    val target = File(folder, fileName)
                    FileOutputStream(target).use { it.write(bytes) }
                    target.absolutePath
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun writeToOutputStream(outputStream: OutputStream, file: File) {
        file.inputStream().use { input -> input.copyTo(outputStream) }
    }

    /**
     * Pure-Kotlin OOXML (.xlsx) generator reproducing the exact openpyxl sheet layout.
     */
    internal fun buildXlsxBytes(
        title: String,
        timestamp: String,
        residents: List<WargaKosResident>,
    ): ByteArray {
        val rows = buildReportRows(residents)
        val barisTtd = maxOf(48, 6 + rows.size + 2)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeZipEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeZipEntry(zip, "xl/styles.xml", STYLES_XML)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildWorksheetXml(title, timestamp, rows, barisTtd))
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun inlineCell(ref: String, styleId: Int, value: String): String {
        return """<c r="$ref" s="$styleId" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>"""
    }

    private fun buildWorksheetXml(
        title: String,
        timestamp: String,
        rows: List<List<String>>,
        barisTtd: Int,
    ): String {
        val colLetters = arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I")
        val colWidths = doubleArrayOf(6.0, 23.0, 23.0, 13.0, 15.0, 28.0, 13.0, 16.0, 23.0)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        sb.append("""<dimension ref="A1:I${barisTtd + 4}"/>""")
        sb.append("""<sheetViews><sheetView showGridLines="0" tabSelected="1" workbookViewId="0"><pane ySplit="5" topLeftCell="A6" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="18"/>""")
        sb.append("<cols>")
        colWidths.forEachIndexed { idx, w ->
            val colIdx = idx + 1
            sb.append("""<col min="$colIdx" max="$colIdx" width="$w" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        // Row 1: Title
        sb.append("""<row r="1" ht="25" customHeight="1">""")
        sb.append(inlineCell("A1", 1, title))
        sb.append("</row>")

        // Row 2: Timestamp
        sb.append("""<row r="2" ht="18" customHeight="1">""")
        sb.append(inlineCell("A2", 2, "Dicetak pada: $timestamp"))
        sb.append("</row>")

        // Row 4: Top Header
        sb.append("""<row r="4" ht="25" customHeight="1">""")
        sb.append(inlineCell("A4", 3, "No"))
        sb.append(inlineCell("B4", 3, "Nama Pemilik Kos"))
        sb.append(inlineCell("C4", 3, "Nama Warga Kos"))
        sb.append(inlineCell("D4", 3, ""))
        sb.append(inlineCell("E4", 3, ""))
        sb.append(inlineCell("F4", 3, "Alamat Lengkap"))
        sb.append(inlineCell("G4", 3, "Status"))
        sb.append(inlineCell("H4", 3, ""))
        sb.append(inlineCell("I4", 3, "Keterangan"))
        sb.append("</row>")

        // Row 5: Sub Header
        sb.append("""<row r="5" ht="23" customHeight="1">""")
        sb.append(inlineCell("A5", 3, ""))
        sb.append(inlineCell("B5", 3, ""))
        sb.append(inlineCell("C5", 3, "Nama"))
        sb.append(inlineCell("D5", 3, "No Kamar"))
        sb.append(inlineCell("E5", 3, "Jumlah Orang"))
        sb.append(inlineCell("F5", 3, ""))
        sb.append(inlineCell("G5", 3, "Nikah"))
        sb.append(inlineCell("H5", 3, "Belum Nikah"))
        sb.append(inlineCell("I5", 3, ""))
        sb.append("</row>")

        // Data rows starting at row 6
        rows.forEachIndexed { rowIdx, values ->
            val excelRow = 6 + rowIdx
            sb.append("""<row r="$excelRow" ht="22" customHeight="1">""")
            values.forEachIndexed { colIdx, value ->
                val ref = "${colLetters[colIdx]}$excelRow"
                val isCentered = colIdx in setOf(0, 3, 4, 6, 7)
                val styleId = if (isCentered) 4 else 5
                sb.append(inlineCell(ref, styleId, value))
            }
            sb.append("</row>")
        }

        // Signature block
        sb.append("""<row r="$barisTtd" ht="32" customHeight="1">""")
        sb.append(inlineCell("A$barisTtd", 6, "Mengetahui,\nKetua RW. 01"))
        sb.append(inlineCell("D$barisTtd", 6, "Ketua RT. 01"))
        sb.append(inlineCell("G$barisTtd", 6, "Ketua RT. 02"))
        sb.append("</row>")

        sb.append("""<row r="${barisTtd + 1}" ht="20" customHeight="1"/>""")
        sb.append("""<row r="${barisTtd + 2}" ht="20" customHeight="1"/>""")
        sb.append("""<row r="${barisTtd + 3}" ht="20" customHeight="1"/>""")

        val signLineRow = barisTtd + 4
        sb.append("""<row r="$signLineRow" ht="24" customHeight="1">""")
        sb.append(inlineCell("A$signLineRow", 7, "( ____________________ )"))
        sb.append(inlineCell("D$signLineRow", 7, "( ____________________ )"))
        sb.append(inlineCell("G$signLineRow", 7, "( ____________________ )"))
        sb.append("</row>")

        sb.append("</sheetData>")

        // Merged cells
        val merges = listOf(
            "A1:I1", "A2:I2",
            "A4:A5", "B4:B5", "C4:E4", "F4:F5", "G4:H4", "I4:I5",
            "A$barisTtd:C$barisTtd", "D$barisTtd:F$barisTtd", "G$barisTtd:I$barisTtd",
            "A${barisTtd + 1}:C${barisTtd + 3}", "D${barisTtd + 1}:F${barisTtd + 3}", "G${barisTtd + 1}:I${barisTtd + 3}",
            "A$signLineRow:C$signLineRow", "D$signLineRow:F$signLineRow", "G$signLineRow:I$signLineRow",
        )
        sb.append("""<mergeCells count="${merges.size}">""")
        merges.forEach { sb.append("""<mergeCell ref="$it"/>""") }
        sb.append("</mergeCells>")

        sb.append("""<pageMargins left="0.25" right="0.25" top="0.35" bottom="0.35" header="0.15" footer="0.15"/>""")
        sb.append("""<pageSetup paperSize="8" orientation="landscape" fitToWidth="1" fitToHeight="1"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    /**
     * Native Android PdfDocument generator rendering A3 Landscape report with
     * merged headers, >=40 rows, and RW/RT signature block.
     */
    internal fun buildPdfBytes(
        title: String,
        timestamp: String,
        residents: List<WargaKosResident>,
    ): ByteArray {
        return try {
            buildNativePdfBytes(title, timestamp, residents)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("closed", ignoreCase = true) == true) {
                buildFallbackPdfBytes(title, timestamp, residents)
            } else {
                throw e
            }
        } catch (_: UnsatisfiedLinkError) {
            buildFallbackPdfBytes(title, timestamp, residents)
        }
    }

    private fun buildNativePdfBytes(
        title: String,
        timestamp: String,
        residents: List<WargaKosResident>,
    ): ByteArray {
        val pageWidth = 1191 // A3 landscape points
        val pageHeight = 842
        val margin = 24f
        val contentWidth = pageWidth - margin * 2f

        val proportions = floatArrayOf(0.045f, 0.15f, 0.15f, 0.085f, 0.10f, 0.18f, 0.09f, 0.105f, 0.095f)
        val colWidths = FloatArray(9) { idx -> contentWidth * proportions[idx] }
        val colX = FloatArray(10)
        colX[0] = margin
        for (i in 0 until 9) colX[i + 1] = colX[i] + colWidths[i]

        val rows = buildReportRows(residents)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 0.7f
            style = Paint.Style.STROKE
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.RIGHT
        }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val signBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val pdf = PdfDocument()
        try {
            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas

            fun drawTitleAndTimestamp(): Float {
                canvas.drawText(title, pageWidth / 2f, margin + 16f, titlePaint)
                canvas.drawText("Dicetak pada: $timestamp", pageWidth - margin, margin + 30f, timePaint)
                return margin + 40f
            }

            fun drawTableHeader(startY: Float): Float {
                val h1 = 19f
                val h2 = 17f
                val totalH = h1 + h2

                // Outer & merged header boxes
                canvas.drawRect(colX[0], startY, colX[1], startY + totalH, borderPaint) // No
                canvas.drawRect(colX[1], startY, colX[2], startY + totalH, borderPaint) // Pemilik
                canvas.drawRect(colX[2], startY, colX[5], startY + h1, borderPaint)     // Nama Warga Kos
                canvas.drawRect(colX[2], startY + h1, colX[3], startY + totalH, borderPaint)
                canvas.drawRect(colX[3], startY + h1, colX[4], startY + totalH, borderPaint)
                canvas.drawRect(colX[4], startY + h1, colX[5], startY + totalH, borderPaint)
                canvas.drawRect(colX[5], startY, colX[6], startY + totalH, borderPaint) // Alamat
                canvas.drawRect(colX[6], startY, colX[8], startY + h1, borderPaint)     // Status
                canvas.drawRect(colX[6], startY + h1, colX[7], startY + totalH, borderPaint)
                canvas.drawRect(colX[7], startY + h1, colX[8], startY + totalH, borderPaint)
                canvas.drawRect(colX[8], startY, colX[9], startY + totalH, borderPaint) // Keterangan

                drawCellText(canvas, "No", colX[0], startY, colWidths[0], totalH, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Nama Pemilik Kos", colX[1], startY, colWidths[1], totalH, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Nama Warga Kos", colX[2], startY, colX[5] - colX[2], h1, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Nama", colX[2], startY + h1, colWidths[2], h2, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "No Kamar", colX[3], startY + h1, colWidths[3], h2, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Jumlah Orang", colX[4], startY + h1, colWidths[4], h2, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Alamat Lengkap", colX[5], startY, colWidths[5], totalH, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Status", colX[6], startY, colX[8] - colX[6], h1, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Nikah", colX[6], startY + h1, colWidths[6], h2, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Belum Nikah", colX[7], startY + h1, colWidths[7], h2, headerPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(canvas, "Keterangan", colX[8], startY, colWidths[8], totalH, headerPaint, Layout.Alignment.ALIGN_CENTER)

                return startY + totalH
            }

            var y = drawTitleAndTimestamp()
            y = drawTableHeader(y)
            val rowHeight = 14.5f
            val signatureBlockHeight = 96f

            for (row in rows) {
                if (y + rowHeight > pageHeight - margin - 12f) {
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = drawTableHeader(margin)
                }
                for (c in 0 until 9) {
                    canvas.drawRect(colX[c], y, colX[c + 1], y + rowHeight, borderPaint)
                    val align = if (c in setOf(0, 3, 4, 6, 7)) {
                        Layout.Alignment.ALIGN_CENTER
                    } else {
                        Layout.Alignment.ALIGN_NORMAL
                    }
                    drawCellText(canvas, row[c], colX[c], y, colWidths[c], rowHeight, cellPaint, align)
                }
                y += rowHeight
            }

            if (y + signatureBlockHeight > pageHeight - margin) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
            } else {
                y += 16f
            }

            val sigColWidth = contentWidth / 3f
            val signHeaders = listOf("Mengetahui,\nKetua RW. 01", "\nKetua RT. 01", "\nKetua RT. 02")
            signHeaders.forEachIndexed { idx, label ->
                val sx = margin + idx * sigColWidth
                drawCellText(canvas, label, sx, y, sigColWidth, 32f, signBoldPaint, Layout.Alignment.ALIGN_CENTER)
                drawCellText(
                    canvas,
                    "( ____________________ )",
                    sx,
                    y + 64f,
                    sigColWidth,
                    20f,
                    cellPaint,
                    Layout.Alignment.ALIGN_CENTER
                )
            }

            pdf.finishPage(page)
            val out = ByteArrayOutputStream()
            pdf.writeTo(out)
            return out.toByteArray()
        } finally {
            runCatching { pdf.close() }
        }
    }

    /**
     * Pure-Kotlin PDF 1.4 fallback generator (A3 Landscape) used if native PdfDocument is unavailable.
     */
    private fun buildFallbackPdfBytes(
        title: String,
        timestamp: String,
        residents: List<WargaKosResident>,
    ): ByteArray {
        val pageWidth = 1191
        val pageHeight = 842
        val margin = 24f
        val contentWidth = pageWidth - margin * 2f
        val proportions = floatArrayOf(0.045f, 0.15f, 0.15f, 0.085f, 0.10f, 0.18f, 0.09f, 0.105f, 0.095f)
        val colWidths = FloatArray(9) { idx -> contentWidth * proportions[idx] }
        val colX = FloatArray(10).apply {
            this[0] = margin
            for (i in 0 until 9) this[i + 1] = this[i] + colWidths[i]
        }

        fun pdfEsc(s: String): String {
            val normalized = s.replace("✓", "V").replace("\n", " ")
            return buildString(normalized.length) {
                for (ch in normalized) {
                    when (ch) {
                        '\\', '(', ')' -> {
                            append('\\'); append(ch)
                        }
                        in ' '..'~' -> append(ch)
                        else -> append('?')
                    }
                }
            }
        }

        fun drawText(sb: StringBuilder, x: Float, yTop: Float, size: Float, text: String, bold: Boolean = false) {
            val yPdf = pageHeight - yTop
            val fontRef = if (bold) "/F2" else "/F1"
            sb.append("BT $fontRef $size Tf $x $yPdf Td (${pdfEsc(text)}) Tj ET\n")
        }

        fun drawRect(sb: StringBuilder, x: Float, yTop: Float, w: Float, h: Float) {
            val yPdf = pageHeight - yTop - h
            sb.append("$x $yPdf $w $h re S\n")
        }

        val stream = StringBuilder()
        stream.append("0.6 w\n")
        drawText(stream, pageWidth / 2f - 70f, margin + 16f, 14f, title, bold = true)
        drawText(stream, pageWidth - margin - 200f, margin + 30f, 8.5f, "Dicetak pada: $timestamp")

        var y = margin + 40f
        val rowHeight = 14.5f
        val rows = buildReportRows(residents).take(45)
        drawRect(stream, margin, y, contentWidth, 32f)
        drawText(stream, margin + 6f, y + 20f, 8.5f, "No | Pemilik Kos | Warga Kos | Kamar | Org | Alamat | Nikah | Belum Nikah | Keterangan", bold = true)
        y += 32f

        for (row in rows) {
            for (c in 0 until 9) {
                drawRect(stream, colX[c], y, colWidths[c], rowHeight)
                if (row[c].isNotEmpty()) {
                    drawText(stream, colX[c] + 3f, y + 10.5f, 7.5f, row[c].take(26))
                }
            }
            y += rowHeight
        }

        val sigY = y + 20f
        drawText(stream, margin + 80f, sigY, 9f, "Mengetahui, Ketua RW. 01", bold = true)
        drawText(stream, margin + contentWidth / 3f + 90f, sigY, 9f, "Ketua RT. 01", bold = true)
        drawText(stream, margin + (contentWidth * 2f / 3f) + 90f, sigY, 9f, "Ketua RT. 02", bold = true)

        val contentBytes = stream.toString().toByteArray(Charsets.ISO_8859_1)
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n",
            "6 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n${String(contentBytes, Charsets.ISO_8859_1)}\nendstream\nendobj\n",
        )

        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun writeAscii(str: String) = out.write(str.toByteArray(Charsets.ISO_8859_1))
        writeAscii("%PDF-1.4\n")
        for (obj in objects) {
            offsets.add(out.size())
            writeAscii(obj)
        }
        val xrefOffset = out.size()
        writeAscii("xref\n0 ${objects.size + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (off in offsets) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun drawCellText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        paint: TextPaint,
        alignment: Layout.Alignment,
    ) {
        if (text.isEmpty()) return
        val pad = 3f
        val availW = (width - pad * 2).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, availW)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
        canvas.save()
        val dy = ((height - layout.height) / 2f).coerceAtLeast(1f)
        canvas.translate(x + pad, y + dy)
        canvas.clipRect(0f, 0f, width - pad * 2, height - 1f)
        layout.draw(canvas)
        canvas.restore()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Data Warga Kos" sheetId="1" r:id="rId1"/>
  </sheets>
  <definedNames>
    <definedName name="_xlnm.Print_Area" localSheetId="0">'Data Warga Kos'!${'$'}A${'$'}1:${'$'}I${'$'}60</definedName>
  </definedNames>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="5">
    <font><sz val="10"/><name val="Arial"/></font>
    <font><b/><sz val="15"/><name val="Arial"/></font>
    <font><i/><sz val="9"/><name val="Arial"/></font>
    <font><b/><sz val="10"/><name val="Arial"/></font>
    <font><sz val="10"/><name val="Arial"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FF000000"/></left>
      <right style="thin"><color rgb="FF000000"/></right>
      <top style="thin"><color rgb="FF000000"/></top>
      <bottom style="thin"><color rgb="FF000000"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="8">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="4" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="4" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
}
