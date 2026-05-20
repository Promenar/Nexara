package com.promenar.nexara.data.model

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 附件文本提取器 — 在客户端提取文档文本，用于注入到 LLM content。
 * 支持格式：PDF, DOCX, XLSX, PPTX, TXT, CSV, MD 等纯文本。
 */
object AttachmentTextExtractor {

    /**
     * 根据 MIME 类型自动选择提取策略。
     * @return 提取的文本内容，如果无法提取则返回 null
     */
    suspend fun extractText(context: Context, uri: Uri, mimeType: String): String? =
        withContext(Dispatchers.IO) {
            try {
                when {
                    // PDF — 使用 PDFBox Android
                    mimeType == "application/pdf" -> extractPdfText(context, uri)
                    // DOCX
                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractDocxText(context, uri)
                    // XLSX
                    mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> extractXlsxText(context, uri)
                    // PPTX
                    mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> extractPptxText(context, uri)
                    // 纯文本类（TXT, CSV, MD, LOG, JSON, XML, YAML 等）
                    mimeType.startsWith("text/") -> extractPlainText(context, uri)
                    // 其他未知格式尝试按纯文本读取
                    else -> extractPlainText(context, uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 判断给定 MIME 类型的文档是否需要客户端文本提取。
     * PDF 对支持原生 PDF 的模型（如 Claude）可以直传 Base64，不需要提取。
     */
    fun needsTextExtraction(mimeType: String): Boolean = when {
        mimeType.startsWith("text/") -> true
        mimeType.contains("wordprocessingml") -> true  // DOCX
        mimeType.contains("spreadsheetml") -> true     // XLSX
        mimeType.contains("presentationml") -> true    // PPTX
        else -> false
    }

    // ── PDF 提取 ──
    private fun extractPdfText(context: Context, uri: Uri): String? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { stream ->
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            val pdDocument = com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream)
            pdDocument.use { doc ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                val text = stripper.getText(doc)
                return text.ifBlank { null }
            }
        }
    }

    // ── DOCX 提取 ──
    private fun extractDocxText(context: Context, uri: Uri): String? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { stream ->
            val doc = org.apache.poi.xwpf.usermodel.XWPFDocument(stream)
            doc.use { document ->
                val sb = StringBuilder()
                for (paragraph in document.paragraphs) {
                    val text = paragraph.text?.trim()
                    if (!text.isNullOrEmpty()) sb.appendLine(text)
                }
                // 也提取表格中的文本
                for (table in document.tables) {
                    for (row in table.rows) {
                        val rowText = row.tableCells.mapNotNull { it.text?.trim()?.ifEmpty { null } }.joinToString(" | ")
                        if (rowText.isNotEmpty()) sb.appendLine(rowText)
                    }
                }
                return sb.toString().ifBlank { null }
            }
        }
    }

    // ── XLSX 提取（转为 CSV 格式文本）──
    private fun extractXlsxText(context: Context, uri: Uri): String? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { stream ->
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(stream)
            workbook.use { wb ->
                val sb = StringBuilder()
                for (sheetIdx in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(sheetIdx)
                    sb.appendLine("=== Sheet: ${sheet.sheetName} ===")
                    for (row in sheet) {
                        val cells = row.map { cell ->
                            when (cell.cellType) {
                                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue ?: ""
                                org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue?.toString() ?: ""
                                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue?.toString() ?: ""
                                org.apache.poi.ss.usermodel.CellType.FORMULA -> cell.cellFormula ?: ""
                                else -> ""
                            }
                        }
                        val rowText = cells.joinToString(", ")
                        if (rowText.isNotBlank()) sb.appendLine(rowText)
                    }
                    sb.appendLine()
                }
                return sb.toString().ifBlank { null }
            }
        }
    }

    // ── PPTX 提取 ──
    private fun extractPptxText(context: Context, uri: Uri): String? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { stream ->
            val slideShow = org.apache.poi.xslf.usermodel.XMLSlideShow(stream)
            slideShow.use { ppt ->
                val sb = StringBuilder()
                for ((index, slide) in ppt.slides.withIndex()) {
                    sb.appendLine("=== Slide ${index + 1} ===")
                    for (shape in slide.shapes) {
                        if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                            val text = shape.text?.trim()
                            if (!text.isNullOrEmpty()) sb.appendLine(text)
                        }
                    }
                    sb.appendLine()
                }
                return sb.toString().ifBlank { null }
            }
        }
    }

    // ── 纯文本提取 ──
    private fun extractPlainText(context: Context, uri: Uri): String? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val text = reader.readText()
            return text.ifBlank { null }
        }
    }
}
