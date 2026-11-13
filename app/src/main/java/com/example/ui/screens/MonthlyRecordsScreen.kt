package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.MonthlyStudentStatus
import com.example.domain.calc.YearMonthUtil
import com.example.domain.model.Student
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonthYearSelectorBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CreditSky
import com.example.ui.theme.CreditSkyBg
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PaidGreenBg
import com.example.ui.theme.PartialAmber
import com.example.ui.theme.PartialAmberBg
import com.example.ui.theme.PendingRose
import com.example.ui.theme.PendingRoseBg
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MainUiState

@Composable
fun MonthlyRecordsScreen(
    uiState: MainUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonthYear: (String) -> Unit,
    onRecordPaymentForMonth: (Student, String, Long) -> Unit,
    onStudentClick: (String) -> Unit,
) {
    val monthly = uiState.monthlySummary

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
                text = "Monthly Records",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            MonthYearSelectorBar(
                yearMonth = uiState.selectedYearMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onSelectMonthYear = onSelectMonthYear,
            )
        }

        // Month Totals Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, OutlineLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Expected", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                CurrencyFormatter.formatInr(monthly.expectedTotalPaise),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column {
                            Text("Collected", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                CurrencyFormatter.formatInr(monthly.collectedTotalPaise),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PaidGreen,
                            )
                        }
                        Column {
                            Text("Pending", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                CurrencyFormatter.formatInr(monthly.pendingTotalPaise),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (monthly.pendingTotalPaise > 0L) PendingRose else PaidGreen,
                            )
                        }
                    }

                    if (monthly.creditAppliedTotalPaise > 0L || monthly.overpaidCreditTotalPaise > 0L) {
                        Surface(
                            color = CreditSkyBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val parts = buildList {
                                if (monthly.creditAppliedTotalPaise > 0L) {
                                    add("Prior Credit Applied: ${CurrencyFormatter.formatInr(monthly.creditAppliedTotalPaise)}")
                                }
                                if (monthly.overpaidCreditTotalPaise > 0L) {
                                    add("Overpaid Surplus: ${CurrencyFormatter.formatInr(monthly.overpaidCreditTotalPaise)}")
                                }
                            }
                            Text(
                                text = parts.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = CreditSky,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CountPill("${monthly.paidCount} Paid", PaidGreen, PaidGreenBg, Modifier.weight(1f))
                        CountPill("${monthly.partialCount} Partial", PartialAmber, PartialAmberBg, Modifier.weight(1f))
                        CountPill("${monthly.unpaidCount} Pending", PendingRose, PendingRoseBg, Modifier.weight(1f))
                    }
                }
            }
        }

        if (monthly.studentStatuses.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Enrolled Students for ${YearMonthUtil.displayMonthYear(uiState.selectedYearMonth, true)}",
                    description = "Students whose joining date is on or before ${YearMonthUtil.displayMonthYear(uiState.selectedYearMonth)} will appear here automatically.",
                    showIllustration = false,
                )
            }
        } else {
            item {
                Text(
                    text = "Student-Wise Status (${monthly.studentStatuses.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(monthly.studentStatuses, key = { "${it.student.id}_${it.yearMonth}" }) { row ->
                MonthlyStudentStatusCard(
                    row = row,
                    onClick = { onStudentClick(row.student.id) },
                    onPayClick = {
                        val amt = row.pendingPaise.takeIf { it > 0L } ?: row.expectedFeePaise
                        onRecordPaymentForMonth(row.student, row.yearMonth, amt)
                    },
                )
            }
        }
    }
}

@Composable
private fun CountPill(
    label: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthlyStudentStatusCard(
    row: MonthlyStudentStatus,
    onClick: () -> Unit,
    onPayClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = row.student.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusBadge(status = row.status)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${row.student.className} · Fee: ${CurrencyFormatter.formatInr(row.expectedFeePaise)} · Paid: ${CurrencyFormatter.formatInr(row.directPaidPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                if (row.creditAppliedPaise > 0L) {
                    Text(
                        text = "Prior credit applied: ${CurrencyFormatter.formatInr(row.creditAppliedPaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CreditSky,
                    )
                }
                if (row.pendingPaise > 0L) {
                    Text(
                        text = "Pending: ${CurrencyFormatter.formatInr(row.pendingPaise)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PendingRose,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onPayClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Pay", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
