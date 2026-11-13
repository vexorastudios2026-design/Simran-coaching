package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.MonthlyStudentStatus
import com.example.domain.calc.MonthlySummary
import com.example.domain.calc.StudentFeeSummary
import com.example.domain.calc.YearMonthUtil
import com.example.domain.calc.YearlyReport
import com.example.domain.model.Payment
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun buildStudentStatementText(
        tuitionName: String,
        summary: StudentFeeSummary,
        payments: List<Payment>,
    ): String = buildString {
        val s = summary.student
        appendLine("*$tuitionName - Fee Statement*")
        appendLine("Student: ${s.name} (${s.className})")
        appendLine("Joined: ${YearMonthUtil.displayDate(s.joiningDate)}")
        appendLine("Current Monthly Fee: ${CurrencyFormatter.formatInr(s.monthlyFeePaise)}")
        appendLine()
        appendLine("Total Applicable Fees: ${CurrencyFormatter.formatInr(summary.totalExpectedPaise)}")
        appendLine("Total Paid: ${CurrencyFormatter.formatInr(summary.totalPaidPaise)}")
        if (summary.creditBalancePaise > 0L) {
            appendLine("Advance / Credit Balance: ${CurrencyFormatter.formatInr(summary.creditBalancePaise)}")
            appendLine("Remaining Balance: ₹0")
        } else {
            appendLine("Remaining Balance: ${CurrencyFormatter.formatInr(summary.remainingBalancePaise)}")
        }
        appendLine()
        if (payments.isNotEmpty()) {
            appendLine("Recent Payments:")
            payments.take(10).forEach { p ->
                appendLine(
                    "- ${YearMonthUtil.displayDate(p.paymentDate)} | ${YearMonthUtil.displayMonthYear(p.forYearMonth, shortMonth = true)} | ${CurrencyFormatter.formatInr(p.amountPaise)} (${p.method})"
                )
            }
        } else {
            appendLine("No payments recorded yet.")
        }
    }.trim()

    fun generateStudentStatementPdf(
        context: Context,
        tuitionName: String,
        summary: StudentFeeSummary,
        monthlyHistory: List<MonthlyStudentStatus>,
        payments: List<Payment>,
    ): Uri {
        val safeName = summary.student.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "student" }
        val file = File(exportsDir(context), "Statement_${safeName}.pdf")

        val lines = mutableListOf<PdfLine>()
        lines += PdfLine(tuitionName, bold = true, size = 16f, color = Color.rgb(67, 56, 202))
        lines += PdfLine("Student Fee Statement", bold = true, size = 13f)
        lines += PdfLine("Generated: ${YearMonthUtil.displayDate(YearMonthUtil.currentIsoDate())}", size = 10f, color = Color.DKGRAY)
        lines += PdfLine("", size = 8f)
        lines += PdfLine("Student: ${summary.student.name}", bold = true, size = 12f)
        lines += PdfLine("Class: ${summary.student.className}   |   Joined: ${YearMonthUtil.displayDate(summary.student.joiningDate)}", size = 10f)
        if (summary.student.phone.isNotBlank()) {
            lines += PdfLine("Phone: ${summary.student.phone}", size = 10f)
        }
        lines += PdfLine("Current Monthly Fee: ${CurrencyFormatter.formatInr(summary.student.monthlyFeePaise)}", size = 10f)
        lines += PdfLine("", size = 8f)
        lines += PdfLine("Account Summary", bold = true, size = 12f, color = Color.rgb(67, 56, 202))
        lines += PdfLine("Total Applicable Fees: ${CurrencyFormatter.formatInr(summary.totalExpectedPaise)}", size = 11f)
        lines += PdfLine("Total Collected: ${CurrencyFormatter.formatInr(summary.totalPaidPaise)}", size = 11f)
        lines += PdfLine("Remaining Balance: ${CurrencyFormatter.formatInr(summary.remainingBalancePaise)}", bold = true, size = 11f)
        if (summary.creditBalancePaise > 0L) {
            lines += PdfLine("Advance Credit Balance: ${CurrencyFormatter.formatInr(summary.creditBalancePaise)}", bold = true, size = 11f, color = Color.rgb(2, 132, 199))
        }
        lines += PdfLine("", size = 8f)
        lines += PdfLine("Month-Wise Breakdown", bold = true, size = 12f, color = Color.rgb(67, 56, 202))
        monthlyHistory.filter { it.expectedFeePaise > 0L || it.directPaidPaise > 0L }.forEach { row ->
            lines += PdfLine(
                "${YearMonthUtil.displayMonthYear(row.yearMonth, true)}: Fee ${CurrencyFormatter.formatInr(row.expectedFeePaise)} | Paid ${CurrencyFormatter.formatInr(row.directPaidPaise)} | Pending ${CurrencyFormatter.formatInr(row.pendingPaise)} [${row.status.label}]",
                size = 10f
            )
        }
        lines += PdfLine("", size = 8f)
        lines += PdfLine("Payment History (${payments.size})", bold = true, size = 12f, color = Color.rgb(67, 56, 202))
        if (payments.isEmpty()) {
            lines += PdfLine("No payments recorded.", size = 10f, color = Color.DKGRAY)
        } else {
            payments.forEach { p ->
                val notePart = if (p.note.isNotBlank()) " - ${p.note}" else ""
                lines += PdfLine(
                    "${YearMonthUtil.displayDate(p.paymentDate)} | For ${YearMonthUtil.displayMonthYear(p.forYearMonth, true)} | ${CurrencyFormatter.formatInr(p.amountPaise)} | ${p.method}$notePart",
                    size = 10f
                )
            }
        }

        writePdfLines(file, lines)
        return uriForFile(context, file)
    }

    fun generateMonthlyReportPdf(
        context: Context,
        tuitionName: String,
        monthlySummary: MonthlySummary,
    ): Uri {
        val file = File(exportsDir(context), "Monthly_Report_${monthlySummary.yearMonth}.pdf")
        val lines = mutableListOf<PdfLine>()
        lines += PdfLine(tuitionName, bold = true, size = 16f, color = Color.rgb(67, 56, 202))
        lines += PdfLine("Monthly Fee Report - ${YearMonthUtil.displayMonthYear(monthlySummary.yearMonth)}", bold = true, size = 13f)
        lines += PdfLine("Generated: ${YearMonthUtil.displayDate(YearMonthUtil.currentIsoDate())}", size = 10f, color = Color.DKGRAY)
        lines += PdfLine("", size = 8f)
        lines += PdfLine("Expected Fees: ${CurrencyFormatter.formatInr(monthlySummary.expectedTotalPaise)}", size = 11f)
        lines += PdfLine("Collected Fees: ${CurrencyFormatter.formatInr(monthlySummary.collectedTotalPaise)}", size = 11f)
        lines += PdfLine("Pending Fees: ${CurrencyFormatter.formatInr(monthlySummary.pendingTotalPaise)}", bold = true, size = 11f)
        lines += PdfLine("Paid: ${monthlySummary.paidCount}  |  Partial: ${monthlySummary.partialCount}  |  Pending: ${monthlySummary.unpaidCount}", size = 10f)
        lines += PdfLine("", size = 10f)
        lines += PdfLine("Student-Wise Statement", bold = true, size = 12f, color = Color.rgb(67, 56, 202))
        if (monthlySummary.studentStatuses.isEmpty()) {
            lines += PdfLine("No students enrolled for this month.", size = 10f)
        } else {
            monthlySummary.studentStatuses.forEach { st ->
                lines += PdfLine(
                    "${st.student.name} (${st.student.className}) — Fee: ${CurrencyFormatter.formatInr(st.expectedFeePaise)} | Paid: ${CurrencyFormatter.formatInr(st.directPaidPaise)} | Pending: ${CurrencyFormatter.formatInr(st.pendingPaise)} [${st.status.label}]",
                    size = 10f
                )
            }
        }
        writePdfLines(file, lines)
        return uriForFile(context, file)
    }

    fun generateYearlyReportPdf(
        context: Context,
        tuitionName: String,
        yearlyReport: YearlyReport,
    ): Uri {
        val file = File(exportsDir(context), "Yearly_Report_${yearlyReport.year}.pdf")
        val lines = mutableListOf<PdfLine>()
        lines += PdfLine(tuitionName, bold = true, size = 16f, color = Color.rgb(67, 56, 202))
        lines += PdfLine("Annual Collection Report - ${yearlyReport.year}", bold = true, size = 13f)
        lines += PdfLine("Generated: ${YearMonthUtil.displayDate(YearMonthUtil.currentIsoDate())}", size = 10f, color = Color.DKGRAY)
        lines += PdfLine("", size = 8f)
        lines += PdfLine("Total Expected: ${CurrencyFormatter.formatInr(yearlyReport.totalExpectedPaise)}", size = 11f)
        lines += PdfLine("Total Collected: ${CurrencyFormatter.formatInr(yearlyReport.totalCollectedPaise)}", size = 11f)
        lines += PdfLine("Total Pending: ${CurrencyFormatter.formatInr(yearlyReport.totalPendingPaise)}", bold = true, size = 11f)
        lines += PdfLine("", size = 10f)
        lines += PdfLine("Month-Wise Breakdown", bold = true, size = 12f, color = Color.rgb(67, 56, 202))
        yearlyReport.monthlyRows.forEach { row ->
            lines += PdfLine(
                "${YearMonthUtil.displayMonthYear(row.yearMonth, true)}: Expected ${CurrencyFormatter.formatInr(row.expectedPaise)} | Collected ${CurrencyFormatter.formatInr(row.collectedPaise)} | Pending ${CurrencyFormatter.formatInr(row.pendingPaise)} (${row.paymentCount} payments)",
                size = 10f
            )
        }
        writePdfLines(file, lines)
        return uriForFile(context, file)
    }

    fun generateMonthlyCsvUri(context: Context, monthlySummary: MonthlySummary): Uri {
        val csv = buildString {
            appendLine("Student Name,Class,Month,Expected (INR),Direct Paid (INR),Credit Applied (INR),Pending (INR),Overpaid Credit (INR),Status")
            monthlySummary.studentStatuses.forEach { st ->
                appendLine(
                    listOf(
                        escapeCsv(st.student.name),
                        escapeCsv(st.student.className),
                        st.yearMonth,
                        paiseToDecimal(st.expectedFeePaise),
                        paiseToDecimal(st.directPaidPaise),
                        paiseToDecimal(st.creditAppliedPaise),
                        paiseToDecimal(st.pendingPaise),
                        paiseToDecimal(st.overpaidCreditPaise),
                        st.status.label,
                    ).joinToString(",")
                )
            }
        }
        val file = File(exportsDir(context), "Monthly_Report_${monthlySummary.yearMonth}.csv")
        file.writeText(csv, Charsets.UTF_8)
        return uriForFile(context, file)
    }

    fun generateYearlyCsvUri(context: Context, yearlyReport: YearlyReport): Uri {
        val csv = buildString {
            appendLine("Month,Expected (INR),Collected (INR),Pending (INR),Payments Count")
            yearlyReport.monthlyRows.forEach { row ->
                appendLine(
                    listOf(
                        row.yearMonth,
                        paiseToDecimal(row.expectedPaise),
                        paiseToDecimal(row.collectedPaise),
                        paiseToDecimal(row.pendingPaise),
                        row.paymentCount.toString(),
                    ).joinToString(",")
                )
            }
        }
        val file = File(exportsDir(context), "Yearly_Report_${yearlyReport.year}.csv")
        file.writeText(csv, Charsets.UTF_8)
        return uriForFile(context, file)
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun shareText(context: Context, text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun paiseToDecimal(paise: Long): String {
        val rupees = paise / 100L
        val rem = paise % 100L
        return if (rem == 0L) "$rupees.00" else "$rupees.${rem.toString().padStart(2, '0')}"
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private data class PdfLine(
        val text: String,
        val bold: Boolean = false,
        val size: Float = 11f,
        val color: Int = Color.rgb(30, 41, 59),
    )

    private fun writePdfLines(file: File, lines: List<PdfLine>) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val maxLineWidth = pageWidth - margin * 2

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var y = 48f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (line in lines) {
            paint.textSize = line.size
            paint.color = line.color
            paint.typeface = if (line.bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT

            if (line.text.isEmpty()) {
                y += line.size + 4f
                continue
            }

            val wrapped = wrapText(line.text, paint, maxLineWidth)
            for (subLine in wrapped) {
                if (y > pageHeight - 48f) {
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    y = 48f
                }
                page.canvas.drawText(subLine, margin, y, paint)
                y += line.size + 7f
            }
        }
        pdf.finishPage(page)

        FileOutputStream(file).use { out ->
            pdf.writeTo(out)
        }
        pdf.close()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) result.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
