package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.YearMonthUtil
import com.example.domain.model.Payment
import com.example.ui.components.CloudSyncStatusPill
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.OfflineBanner
import com.example.ui.components.SummaryMetricCard
import androidx.compose.material3.TextButton
import com.example.ui.theme.CreditSky
import com.example.ui.theme.CreditSkyBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoftContainer
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PaidGreenBg
import com.example.ui.theme.PendingRose
import com.example.ui.theme.PendingRoseBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.VioletSoftContainer
import com.example.ui.viewmodel.MainUiState

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onAddStudentClick: () -> Unit,
    onAddPaymentClick: () -> Unit,
    onStudentClick: (String) -> Unit,
    onViewAllStudents: () -> Unit,
    onRetrySync: () -> Unit = {},
) {
    val dash = uiState.dashboardSummary
    val currentYm = YearMonthUtil.currentYearMonth()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Loading your cloud records...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OfflineBanner(isOffline = uiState.isOffline)

            if (uiState.errorMessage != null) {
                Surface(
                    color = PendingRoseBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            color = PendingRose,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetrySync) {
                            Text("Retry", color = PendingRose, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Namaste, ${uiState.teacherProfile.teacherName.ifBlank { "Ma'am" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = uiState.teacherProfile.tuitionName.ifBlank { "Simran Tution Classes" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                CloudSyncStatusPill(
                    syncStatus = uiState.syncStatus,
                    onRetryClick = onRetrySync,
                )
            }
        }

        // Primary Teacher Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onAddStudentClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_add_student_button"),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Student", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                FilledTonalButton(
                    onClick = onAddPaymentClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_add_payment_button"),
                ) {
                    Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Payment", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Current Month Collection Hero Card
        item {
            val expected = dash.currentMonthExpectedPaise
            val collected = dash.currentMonthCollectionPaise
            val progress = if (expected > 0L) (collected.toFloat() / expected.toFloat()).coerceIn(0f, 1f) else 0f

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = IndigoPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(IndigoPrimary, VioletSecondary)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${YearMonthUtil.displayMonthYear(currentYm)} Collection",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "${dash.fullyPaidStudentsCount}/${dash.totalStudents} Settled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Text(
                            text = CurrencyFormatter.formatInr(collected),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Expected: ${CurrencyFormatter.formatInr(expected)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f),
                            )
                            Text(
                                text = "Month Pending: ${CurrencyFormatter.formatInr(dash.currentMonthPendingPaise)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        // Summary Metric Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryMetricCard(
                        title = "Total Students",
                        value = dash.totalStudents.toString(),
                        subtitle = "${dash.fullyPaidStudentsCount} paid · ${dash.pendingStudentsCount} pending",
                        icon = Icons.Outlined.Groups,
                        iconTint = IndigoPrimary,
                        iconContainer = IndigoSoftContainer,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryMetricCard(
                        title = "Total Expected",
                        value = CurrencyFormatter.formatInr(dash.totalExpectedPaise),
                        subtitle = "Cumulative to date",
                        icon = Icons.Outlined.AccountBalanceWallet,
                        iconTint = VioletSecondary,
                        iconContainer = VioletSoftContainer,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryMetricCard(
                        title = "Total Collected",
                        value = CurrencyFormatter.formatInr(dash.totalCollectedPaise),
                        subtitle = "${uiState.payments.size} payments logged",
                        icon = Icons.Outlined.Payments,
                        iconTint = PaidGreen,
                        iconContainer = PaidGreenBg,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryMetricCard(
                        title = "Total Pending",
                        value = CurrencyFormatter.formatInr(dash.totalPendingPaise),
                        subtitle = if (dash.totalCreditPaise > 0L) {
                            "Advance Credit: ${CurrencyFormatter.formatInr(dash.totalCreditPaise)}"
                        } else {
                            "${dash.pendingStudentsCount} students owe fees"
                        },
                        icon = Icons.Outlined.HourglassBottom,
                        iconTint = if (dash.totalPendingPaise > 0L) PendingRose else PaidGreen,
                        iconContainer = if (dash.totalPendingPaise > 0L) PendingRoseBg else PaidGreenBg,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (dash.totalCreditPaise > 0L) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CreditSkyBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Advance / Credit Balance Held",
                            style = MaterialTheme.typography.labelLarge,
                            color = CreditSky,
                        )
                        Text(
                            text = CurrencyFormatter.formatInr(dash.totalCreditPaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CreditSky,
                        )
                    }
                }
            }
        }

        // Empty First-Launch State OR Offline Restore Guard OR Recent Payment Activity
        if (uiState.needsInitialCloudRestore) {
            item {
                EmptyStateCard(
                    title = "Connect to Restore Cloud Records",
                    description = "We couldn't reach Firebase yet to check your saved records. Connect to the internet and tap Retry Sync.",
                    showIllustration = false,
                    actionLabel = "Retry Sync",
                    onAction = onRetrySync,
                )
            }
        } else if (uiState.students.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Welcome to Simran Tution Classes",
                    description = "Your ledger starts completely clean. Add your first student to begin tracking monthly fees and payments.",
                    showIllustration = true,
                    actionLabel = "Add First Student",
                    onAction = onAddStudentClick,
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Payment Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "View Students",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onViewAllStudents)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (dash.recentPayments.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Payments Recorded Yet",
                        description = "Tap Add Payment to record a full or partial fee payment for any student.",
                        showIllustration = false,
                        actionLabel = "Record Payment",
                        onAction = onAddPaymentClick,
                    )
                }
            } else {
                items(dash.recentPayments, key = { it.id }) { payment ->
                    RecentPaymentRow(
                        payment = payment,
                        onClick = { onStudentClick(payment.studentId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPaymentRow(
    payment: Payment,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PaidGreenBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CurrencyRupee,
                        contentDescription = null,
                        tint = PaidGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = payment.studentNameSnapshot,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${YearMonthUtil.displayMonthYear(payment.forYearMonth, true)} · ${payment.method} · ${YearMonthUtil.displayDate(payment.paymentDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "+${CurrencyFormatter.formatInr(payment.amountPaise)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PaidGreen,
            )
        }
    }
}
