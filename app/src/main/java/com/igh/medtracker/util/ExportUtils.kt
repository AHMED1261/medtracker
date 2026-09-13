package com.igh.medtracker.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.igh.medtracker.data.DoseLog
import com.igh.medtracker.data.Medication
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/**
 * CSV export is what "Excel export" means here — a genuine binary .xlsx isn't generated (that
 * would need a heavy third-party library just to write a spreadsheet), but a UTF-8 CSV with a
 * BOM opens correctly in Excel, Google Sheets, and Numbers with the Arabic column headers intact.
 */
object ExportUtils {

    private const val PAGE_WIDTH = 595 // A4 at ~72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 24f

    fun exportCsv(context: Context, medication: Medication, logs: List<DoseLog>): File {
        val isGlucose = medication.type == Medication.TYPE_GLUCOSE
        val sorted = logs.sortedBy { it.timestamp }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${sanitizeFileName(medication.name)}.csv")

        FileOutputStream(file).use { fos ->
            // UTF-8 BOM so Excel on Windows detects the encoding and shows Arabic correctly
            // instead of garbled text.
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val sb = StringBuilder()
            sb.append(if (isGlucose) "التاريخ,الوقت,القراءة,الوحدة\n" else "التاريخ,الوقت,الجرعة\n")
            sorted.forEach { log ->
                val cal = TimeUtils.calendarFor(log.timestamp)
                val date = formatDate(cal)
                val time = formatTime(cal)
                if (isGlucose) {
                    sb.append("$date,$time,${formatValue(log.glucoseValue)},${log.glucoseUnit.orEmpty()}\n")
                } else {
                    sb.append("$date,$time,${csvEscape(log.doseAmount)}\n")
                }
            }
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun exportPdf(context: Context, medication: Medication, logs: List<DoseLog>): File {
        val isGlucose = medication.type == Medication.TYPE_GLUCOSE
        val sorted = logs.sortedByDescending { it.timestamp }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT; color = 0xFF0E7C66.toInt()
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f; textAlign = Paint.Align.RIGHT; color = 0xFF5B6B67.toInt()
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT; color = 0xFF1B1F1E.toInt()
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f; textAlign = Paint.Align.RIGHT; color = 0xFF1B1F1E.toInt()
        }
        val linePaint = Paint().apply { strokeWidth = 0.75f; color = 0xFFDDDDDD.toInt() }

        val colDateX = PAGE_WIDTH - MARGIN
        val colTimeX = colDateX - 100f
        val colValueX = colTimeX - 90f
        val colUnitX = colValueX - 90f
        val colDoseX = colTimeX - 220f

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawColumnHeaders() {
            canvas.drawText("التاريخ", colDateX, y, headerPaint)
            canvas.drawText("الوقت", colTimeX, y, headerPaint)
            if (isGlucose) {
                canvas.drawText("القراءة", colValueX, y, headerPaint)
                canvas.drawText("الوحدة", colUnitX, y, headerPaint)
            } else {
                canvas.drawText("الجرعة", colDoseX, y, headerPaint)
            }
            y += 8f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += ROW_HEIGHT
        }

        y += 20f
        canvas.drawText(medication.name, colDateX, y, titlePaint)
        y += 16f
        canvas.drawText("تقرير القراءات — ${TimeUtils.formatAbsolute(System.currentTimeMillis())}", colDateX, y, metaPaint)
        y += 20f
        drawColumnHeaders()

        for (log in sorted) {
            if (y > PAGE_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN + 20f
                drawColumnHeaders()
            }
            val cal = TimeUtils.calendarFor(log.timestamp)
            canvas.drawText(formatDate(cal), colDateX, y, cellPaint)
            canvas.drawText(formatTime(cal), colTimeX, y, cellPaint)
            if (isGlucose) {
                canvas.drawText(formatValue(log.glucoseValue), colValueX, y, cellPaint)
                canvas.drawText(log.glucoseUnit.orEmpty(), colUnitX, y, cellPaint)
            } else {
                canvas.drawText(log.doseAmount.orEmpty(), colDoseX, y, cellPaint)
            }
            y += ROW_HEIGHT
        }
        pdf.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${sanitizeFileName(medication.name)}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun formatDate(cal: Calendar): String = String.format(
        "%04d/%02d/%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )

    private fun formatTime(cal: Calendar): String = String.format(
        "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
    )

    private fun formatValue(value: Double?): String = if (value == null) "" else TimeUtils.formatNumber(value)

    private fun csvEscape(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}_\\-]"), "_").ifBlank { "export" }
}
