package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.MonthlyReportRow
import com.example.domain.calc.StudentFeeSummary
import com.example.domain.calc.YearMonthUtil
import com.example.domain.model.FeeStatus
import com.example.export.ReportExporter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonthYearSelectorBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PendingRose
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MainUiState

private enum class ReportMode(val label: String) {
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
}

private enum class StatementFilter(val label: String) {
    ALL("All"),
    PAID("Paid"),
    PARTIAL("Partially Paid"),
    UNPAID("Unpaid"),
}

@Composable
fun ReportsScreen(
    uiState: MainUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonthYear: (String) -> Unit,
    onSelectReportYear: (Int) -> Unit,
    onStudentClick: (String) -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ReportMode.MONTHLY) }
    var statementFilter by remember { mutableStateOf(StatementFilter.ALL) }
    val tuitionName = uiState.teacherProfile.tuitionName.ifBlank { "Simran Tution Classes" }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OfflineBanner(isOffline = uiState.isOffline)
            Text(
                text = "Reports & Statements",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReportMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ReportMode.entries.size),
                    ) {
                        Text(entry.label)
                    }
                }
            }
        }

        if (mode == ReportMode.MONTHLY) {
            item {
                MonthYearSelectorBar(
                    yearMonth = uiState.selectedYearMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onSelectMonthYear = onSelectMonthYear,
                )
            }

            item {
                val m = uiState.monthlySummary
                ReportTotalsCard(
                    title = "${YearMonthUtil.displayMonthYear(m.yearMonth)} Summary",
                    expectedPaise = m.expectedTotalPaise,
                    collectedPaise = m.collectedTotalPaise,
                    pendingPaise = m.pendingTotalPaise,
                    onExportPdf = {
                        val uri = ReportExporter.generateMonthlyReportPdf(context, tuitionName, m)
                        ReportExporter.shareUri(context, uri, "application/pdf", "Export Monthly PDF Report")
                    },
                    onExportCsv = {
                        val uri = ReportExporter.generateMonthlyCsvUri(context, m)
                        ReportExporter.shareUri(context, uri, "text/csv", "Export Monthly CSV Report")
                    },
                )
            }

            item {
                Text(
                    text = "Student-Wise Statements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatementFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = statementFilter == filter,
                            onClick = { statementFilter = filter },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }

            val summaryById = uiState.studentSummaries.associateBy { it.student.id }
            val filtered = uiState.monthlySummary.studentStatuses.filter { st ->
                when (statementFilter) {
                    StatementFilter.ALL -> true
                    StatementFilter.PAID -> st.status == FeeStatus.PAID || st.status == FeeStatus.CREDIT
                    StatementFilter.PARTIAL -> st.status == FeeStatus.PARTIAL
                    StatementFilter.UNPAID -> st.status == FeeStatus.UNPAID
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Student Statements",
                        description = "No students match the selected month and status filter.",
                        showIllustration = false,
                    )
                }
            } else {
                items(filtered, key = { "rep_${it.student.id}" }) { st ->
                    val overallSummary = summaryById[st.student.id]
                    ReportStudentStatementRow(
                        studentName = st.student.name,
                        className = st.student.className,
                        expectedPaise = st.expectedFeePaise,
                        paidPaise = st.directPaidPaise,
                        pendingPaise = st.pendingPaise,
                        status = st.status,
                        onOpenStudent = { onStudentClick(st.student.id) },
                        onShareStatement = {
                            if (overallSummary != null) {
                                val studentPayments = uiState.payments.filter { it.studentId == st.student.id }
                                val text = ReportExporter.buildStudentStatementText(
                                    tuitionName = tuitionName,
                                    summary = overallSummary,
                                    payments = studentPayments,
                                )
                                ReportExporter.shareText(context, text, "Share ${st.student.name}'s Statement")
                            }
                        },
                    )
                }
            }
        } else {
            // YEARLY REPORT
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, OutlineLight),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { onSelectReportYear(uiState.selectedReportYear - 1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous year")
                        }
                        Text(
                            text = "Year ${uiState.selectedReportYear}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { onSelectReportYear(uiState.selectedReportYear + 1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next year")
                        }
                    }
                }
            }

            item {
                val yr = uiState.yearlyReport
                ReportTotalsCard(
                    title = "Annual Summary (${yr.year})",
                    expectedPaise = yr.totalExpectedPaise,
                    collectedPaise = yr.totalCollectedPaise,
                    pendingPaise = yr.totalPendingPaise,
                    onExportPdf = {
                        val uri = ReportExporter.generateYearlyReportPdf(context, tuitionName, yr)
                        ReportExporter.shareUri(context, uri, "application/pdf", "Export Yearly PDF Report")
                    },
                    onExportCsv = {
                        val uri = ReportExporter.generateYearlyCsvUri(context, yr)
                        ReportExporter.shareUri(context, uri, "text/csv", "Export Yearly CSV Report")
                    },
                )
            }

            items(uiState.yearlyReport.monthlyRows, key = { it.yearMonth }) { row ->
                YearlyMonthRow(row)
            }
        }
    }
}

@Composable
private fun ReportTotalsCard(
    title: String,
    expectedPaise: Long,
    collectedPaise: Long,
    pendingPaise: Long,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Expected", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(CurrencyFormatter.formatInr(expectedPaise), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Collected", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(CurrencyFormatter.formatInr(collectedPaise), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PaidGreen)
                }
                Column {
                    Text("Pending", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(CurrencyFormatter.formatInr(pendingPaise), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (pendingPaise > 0L) PendingRose else PaidGreen)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onExportPdf,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_pdf_button"),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF")
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_csv_button"),
                ) {
                    Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV")
                }
            }
        }
    }
}

@Composable
private fun ReportStudentStatementRow(
    studentName: String,
    className: String,
    expectedPaise: Long,
    paidPaise: Long,
    pendingPaise: Long,
    status: FeeStatus,
    onOpenStudent: () -> Unit,
    onShareStatement: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenStudent),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    StatusBadge(status = status)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$className · Fee ${CurrencyFormatter.formatInr(expectedPaise)} · Paid ${CurrencyFormatter.formatInr(paidPaise)} · Due ${CurrencyFormatter.formatInr(pendingPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            IconButton(onClick = onShareStatement) {
                Icon(Icons.Outlined.Share, contentDescription = "Share $studentName statement")
            }
        }
    }
}

@Composable
private fun YearlyMonthRow(row: MonthlyReportRow) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OutlineLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = YearMonthUtil.displayMonthYear(row.yearMonth),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${row.paymentCount} payment(s) · Expected ${CurrencyFormatter.formatInr(row.expectedPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatInr(row.collectedPaise),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PaidGreen,
                )
                if (row.pendingPaise > 0L) {
                    Text(
                        text = "Due ${CurrencyFormatter.formatInr(row.pendingPaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PendingRose,
                    )
                }
            }
        }
    }
}
