package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.FeeCalculator
import com.example.domain.calc.MonthlyStudentStatus
import com.example.domain.calc.YearMonthUtil
import com.example.domain.model.Payment
import com.example.domain.model.Student
import com.example.export.ReportExporter
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CreditSky
import com.example.ui.theme.CreditSkyBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoftContainer
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PendingRose
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    studentId: String,
    uiState: MainUiState,
    onBack: () -> Unit,
    onEditStudent: (Student) -> Unit,
    onDeleteStudent: (Student) -> Unit,
    onAddPayment: (Student, Long) -> Unit,
    onEditPayment: (Payment) -> Unit,
    onDeletePayment: (Payment) -> Unit,
) {
    val context = LocalContext.current
    val student = uiState.students.firstOrNull { it.id == studentId }

    var showDeleteStudentConfirm by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var showShareMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(student?.name ?: "Student Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (student != null) {
                        IconButton(onClick = { onEditStudent(student) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit student")
                        }
                        IconButton(onClick = { showDeleteStudentConfirm = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete student",
                                tint = PendingRose,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (student == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Student record not found.", color = TextMuted)
            }
            return@Scaffold
        }

        val currentYm = YearMonthUtil.currentYearMonth()
        val (summary, monthlyLedger) = remember(student, uiState.feeRates, uiState.payments, currentYm) {
            FeeCalculator.calculateStudentLedger(student, uiState.feeRates, uiState.payments, currentYm)
        }
        val studentPayments = remember(uiState.payments, student.id) {
            uiState.payments.filter { it.studentId == student.id }
        }
        val tuitionName = uiState.teacherProfile.tuitionName.ifBlank { "Simran Tution Classes" }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Student Header Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, OutlineLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(IndigoSoftContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary,
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = student.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${student.className} · Joined ${YearMonthUtil.displayDate(student.joiningDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                    )
                                }
                            }
                            StatusBadge(status = summary.status)
                        }

                        HorizontalDivider(color = OutlineLight, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Current Monthly Fee", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    text = "${CurrencyFormatter.formatInr(student.monthlyFeePaise)}/month",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (student.phone.isNotBlank()) {
                                Surface(
                                    color = IndigoSoftContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.clickable {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.phone}"))
                                        context.startActivity(dialIntent)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Call,
                                            contentDescription = "Call student phone",
                                            tint = IndigoPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = student.phone,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IndigoPrimary,
                                        )
                                    }
                                }
                            }
                        }

                        if (student.notes.isNotBlank()) {
                            Text(
                                text = "Note: ${student.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Financial Summary Strip
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, OutlineLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Fee Balance Summary (${summary.monthsEnrolledCount} mo)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Total Fee", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    CurrencyFormatter.formatInr(summary.totalExpectedPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column {
                                Text("Amount Paid", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    CurrencyFormatter.formatInr(summary.totalPaidPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PaidGreen,
                                )
                            }
                            Column {
                                Text("Remaining", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    CurrencyFormatter.formatInr(summary.remainingBalancePaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.remainingBalancePaise > 0L) PendingRose else PaidGreen,
                                )
                            }
                        }
                        if (summary.creditBalancePaise > 0L) {
                            Surface(
                                color = CreditSkyBg,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Advance / Credit Balance: ${CurrencyFormatter.formatInr(summary.creditBalancePaise)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CreditSky,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            val suggested = summary.remainingBalancePaise.takeIf { it > 0L }
                                ?: student.monthlyFeePaise
                            onAddPayment(student, suggested)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_record_payment_button"),
                    ) {
                        Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payment")
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalButton(
                            onClick = { showShareMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_statement_button"),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Statement")
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false },
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                text = { Text("Share Summary Text") },
                                onClick = {
                                    showShareMenu = false
                                    val text = ReportExporter.buildStudentStatementText(
                                        tuitionName = tuitionName,
                                        summary = summary,
                                        payments = studentPayments,
                                    )
                                    ReportExporter.shareText(context, text, "Share Fee Statement")
                                },
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                                text = { Text("Export & Share PDF") },
                                onClick = {
                                    showShareMenu = false
                                    val uri = ReportExporter.generateStudentStatementPdf(
                                        context = context,
                                        tuitionName = tuitionName,
                                        summary = summary,
                                        monthlyHistory = monthlyLedger,
                                        payments = studentPayments,
                                    )
                                    ReportExporter.shareUri(context, uri, "application/pdf", "Share PDF Statement")
                                },
                            )
                        }
                    }
                }
            }

            // Month-by-Month Fee Ledger
            item {
                Text(
                    text = "Month-by-Month Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            val visibleLedger = monthlyLedger
                .filter { it.expectedFeePaise > 0L || it.directPaidPaise > 0L }
                .reversed()

            items(visibleLedger, key = { "ledger_${it.yearMonth}" }) { row ->
                MonthlyLedgerItem(row)
            }

            // Payment History
            item {
                Text(
                    text = "Payment History (${studentPayments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (studentPayments.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, OutlineLight),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No payments recorded for ${student.name} yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(studentPayments, key = { it.id }) { payment ->
                    PaymentHistoryItem(
                        payment = payment,
                        onEdit = { onEditPayment(payment) },
                        onDelete = { paymentToDelete = payment },
                    )
                }
            }
        }
    }

    if (showDeleteStudentConfirm && student != null) {
        val paymentCount = uiState.payments.count { it.studentId == student.id }
        ConfirmDeleteDialog(
            title = "Delete ${student.name}?",
            message = "This will permanently delete ${student.name}'s profile, fee rate history, and $paymentCount payment record(s). Dashboard totals will recalculate immediately.",
            onConfirm = {
                showDeleteStudentConfirm = false
                onDeleteStudent(student)
            },
            onDismiss = { showDeleteStudentConfirm = false },
        )
    }

    paymentToDelete?.let { pay ->
        ConfirmDeleteDialog(
            title = "Delete Payment?",
            message = "Delete payment of ${CurrencyFormatter.formatInr(pay.amountPaise)} for ${YearMonthUtil.displayMonthYear(pay.forYearMonth)}? Remaining balance and reports will recalculate automatically.",
            onConfirm = {
                paymentToDelete = null
                onDeletePayment(pay)
            },
            onDismiss = { paymentToDelete = null },
        )
    }
}

@Composable
private fun MonthlyLedgerItem(row: MonthlyStudentStatus) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = YearMonthUtil.displayMonthYear(row.yearMonth),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val creditText = if (row.creditAppliedPaise > 0L) {
                    " (+${CurrencyFormatter.formatInr(row.creditAppliedPaise)} credit)"
                } else ""
                Text(
                    text = "Fee ${CurrencyFormatter.formatInr(row.expectedFeePaise)} · Paid ${CurrencyFormatter.formatInr(row.directPaidPaise)}$creditText",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(status = row.status)
                if (row.pendingPaise > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun PaymentHistoryItem(
    payment: Payment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CurrencyFormatter.formatInr(payment.amountPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PaidGreen,
                )
                Text(
                    text = "For ${YearMonthUtil.displayMonthYear(payment.forYearMonth, true)} · ${payment.method} · ${YearMonthUtil.displayDate(payment.paymentDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                if (payment.note.isNotBlank()) {
                    Text(
                        text = payment.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit payment")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete payment",
                        tint = PendingRose,
                    )
                }
            }
        }
    }
}
