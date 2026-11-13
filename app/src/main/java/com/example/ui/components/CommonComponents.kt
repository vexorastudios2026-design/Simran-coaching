package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.YearMonthUtil
import com.example.domain.model.FeeStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Student
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun OfflineBanner(isOffline: Boolean, modifier: Modifier = Modifier) {
    if (!isOffline) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = PartialAmberBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PartialAmber.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = "Offline status",
                tint = PartialAmber,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Offline mode — showing saved records. New changes will sync automatically when connected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun CloudSyncStatusPill(
    syncStatus: com.example.ui.viewmodel.CloudSyncStatus,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, icon, label) = when (syncStatus) {
        com.example.ui.viewmodel.CloudSyncStatus.SYNCED -> QuadStyle(
            bg = PaidGreenBg,
            fg = PaidGreen,
            icon = Icons.Outlined.CloudDone,
            label = "Synced",
        )
        com.example.ui.viewmodel.CloudSyncStatus.SYNCING -> QuadStyle(
            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            fg = MaterialTheme.colorScheme.primary,
            icon = Icons.Outlined.CloudSync,
            label = "Syncing...",
        )
        com.example.ui.viewmodel.CloudSyncStatus.PENDING_UPLOAD -> QuadStyle(
            bg = PartialAmberBg,
            fg = PartialAmber,
            icon = Icons.Outlined.CloudSync,
            label = "Pending Sync",
        )
        com.example.ui.viewmodel.CloudSyncStatus.OFFLINE_CACHED -> QuadStyle(
            bg = PartialAmberBg,
            fg = PartialAmber,
            icon = Icons.Outlined.CloudOff,
            label = "Offline",
        )
        com.example.ui.viewmodel.CloudSyncStatus.ERROR -> QuadStyle(
            bg = PendingRoseBg,
            fg = PendingRose,
            icon = Icons.Outlined.ErrorOutline,
            label = "Sync Error",
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (onRetryClick != null) Modifier.clickable(onClick = onRetryClick) else Modifier
            ),
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
            if (onRetryClick != null &&
                (syncStatus == com.example.ui.viewmodel.CloudSyncStatus.ERROR ||
                    syncStatus == com.example.ui.viewmodel.CloudSyncStatus.PENDING_UPLOAD)
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Retry sync",
                    tint = fg,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

private data class QuadStyle(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun StatusBadge(status: FeeStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        FeeStatus.PAID -> PaidGreenBg to PaidGreen
        FeeStatus.PARTIAL -> PartialAmberBg to PartialAmber
        FeeStatus.UNPAID -> PendingRoseBg to PendingRose
        FeeStatus.CREDIT -> CreditSkyBg to CreditSky
        FeeStatus.NOT_JOINED -> MaterialTheme.colorScheme.surfaceVariant to TextMuted
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color,
    iconContainer: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    showIllustration: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showIllustration) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_students),
                    contentDescription = "Empty tuition register illustration",
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun MonthYearSelectorBar(
    yearMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectMonthYear: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OutlineLight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = YearMonthUtil.displayMonthYear(yearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = "Choose month and year",
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next month",
                )
            }
        }
    }

    if (showPicker) {
        MonthYearPickerDialog(
            initialYearMonth = yearMonth,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                showPicker = false
                onSelectMonthYear(picked)
            },
        )
    }
}

@Composable
fun MonthYearPickerDialog(
    initialYearMonth: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parsed = YearMonthUtil.parseYearMonth(initialYearMonth) ?: (2026 to 1)
    var selectedYear by remember { mutableIntStateOf(parsed.first) }
    var selectedMonth by remember { mutableIntStateOf(parsed.second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Month & Year") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (selectedYear > 2015) selectedYear-- }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous year")
                    }
                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { if (selectedYear < 2050) selectedYear++ }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next year")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (col in 1..3) {
                            val monthNum = row * 3 + col
                            val isSelected = monthNum == selectedMonth
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedMonth = monthNum },
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = YearMonthUtil.displayMonthOnly(monthNum, shortMonth = true),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(YearMonthUtil.formatYearMonth(selectedYear, selectedMonth)) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDatePickerField(
    label: String,
    isoDate: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = YearMonthUtil.displayDate(isoDate),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = "Pick $label")
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        shape = RoundedCornerShape(14.dp),
    )

    if (showDatePicker) {
        val initialMillis = remember(isoDate) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(isoDate)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            onDateSelected(sdf.format(Date(millis)))
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = PendingRose,
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PendingRose),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun AddEditStudentDialog(
    existingStudent: Student?,
    actionInProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        className: String,
        monthlyFeeInput: String,
        joiningDate: String,
        notes: String,
        active: Boolean,
        effectiveFromYearMonth: String,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(existingStudent?.name ?: "") }
    var phone by remember { mutableStateOf(existingStudent?.phone ?: "") }
    var className by remember { mutableStateOf(existingStudent?.className ?: "") }
    var monthlyFee by remember {
        mutableStateOf(
            if (existingStudent != null) CurrencyFormatter.paiseToInputString(existingStudent.monthlyFeePaise) else ""
        )
    }
    var joiningDate by remember { mutableStateOf(existingStudent?.joiningDate?.ifBlank { YearMonthUtil.currentIsoDate() } ?: YearMonthUtil.currentIsoDate()) }
    var notes by remember { mutableStateOf(existingStudent?.notes ?: "") }
    var active by remember { mutableStateOf(existingStudent?.active ?: true) }
    var effectiveFrom by remember { mutableStateOf(YearMonthUtil.currentYearMonth()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val feeChanged = existingStudent != null &&
        CurrencyFormatter.parseRupeesToPaise(monthlyFee) != null &&
        CurrencyFormatter.parseRupeesToPaise(monthlyFee) != existingStudent.monthlyFeePaise

    Dialog(onDismissRequest = { if (!actionInProgress) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (existingStudent == null) "Add New Student" else "Edit Student",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Student Name *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_name_input"),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it; errorText = null },
                        label = { Text("Class / Grade *") },
                        placeholder = { Text("e.g. Class 10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("student_class_input"),
                    )
                    OutlinedTextField(
                        value = monthlyFee,
                        onValueChange = { monthlyFee = it; errorText = null },
                        label = { Text("Fee (₹/mo) *") },
                        prefix = { Text("₹") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("student_fee_input"),
                    )
                }

                if (feeChanged) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMonthPicker = true },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "New fee effective from: ${YearMonthUtil.displayMonthYear(effectiveFrom)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Previous months keep their original fee. Tap to change effective month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorText = null },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                IsoDatePickerField(
                    label = "Joining Date *",
                    isoDate = joiningDate,
                    onDateSelected = { joiningDate = it },
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (existingStudent != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Active Student", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Inactive students remain in historical records",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = PendingRose,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !actionInProgress) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                name,
                                phone,
                                className,
                                monthlyFee,
                                joiningDate,
                                notes,
                                active,
                                effectiveFrom,
                            ) { err -> errorText = err }
                        },
                        enabled = !actionInProgress,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("save_student_button"),
                    ) {
                        Text(if (actionInProgress) "Saving..." else "Save Student")
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYearMonth = effectiveFrom,
            onDismiss = { showMonthPicker = false },
            onConfirm = { picked ->
                effectiveFrom = picked
                showMonthPicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditPaymentDialog(
    students: List<Student>,
    initialStudent: Student?,
    existingPayment: Payment?,
    suggestedYearMonth: String = YearMonthUtil.currentYearMonth(),
    suggestedAmountPaise: Long? = null,
    actionInProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        student: Student,
        amountInput: String,
        paymentDate: String,
        forYearMonth: String,
        method: PaymentMethod,
        note: String,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    var selectedStudent by remember {
        mutableStateOf(
            initialStudent ?: existingPayment?.let { p -> students.firstOrNull { it.id == p.studentId } }
                ?: students.firstOrNull()
        )
    }
    var studentMenuExpanded by remember { mutableStateOf(false) }

    var amountInput by remember {
        mutableStateOf(
            when {
                existingPayment != null -> CurrencyFormatter.paiseToInputString(existingPayment.amountPaise)
                suggestedAmountPaise != null && suggestedAmountPaise > 0L -> CurrencyFormatter.paiseToInputString(suggestedAmountPaise)
                initialStudent != null -> CurrencyFormatter.paiseToInputString(initialStudent.monthlyFeePaise)
                else -> ""
            }
        )
    }
    var paymentDate by remember {
        mutableStateOf(existingPayment?.paymentDate ?: YearMonthUtil.currentIsoDate())
    }
    var forYearMonth by remember {
        mutableStateOf(existingPayment?.forYearMonth ?: suggestedYearMonth)
    }
    var selectedMethod by remember {
        mutableStateOf(
            existingPayment?.let { PaymentMethod.fromLabel(it.method) } ?: PaymentMethod.CASH
        )
    }
    var note by remember { mutableStateOf(existingPayment?.note ?: "") }
    var showMonthPicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!actionInProgress) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (existingPayment == null) "Record Fee Payment" else "Edit Payment",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                // Student Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedStudent?.let { "${it.name} (${it.className})" } ?: "Select student",
                        onValueChange = {},
                        readOnly = true,
                        enabled = existingPayment == null,
                        label = { Text("Student *") },
                        trailingIcon = {
                            if (existingPayment == null) {
                                IconButton(onClick = { studentMenuExpanded = true }) {
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose student")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = existingPayment == null) { studentMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = studentMenuExpanded,
                        onDismissRequest = { studentMenuExpanded = false },
                    ) {
                        students.forEach { stu ->
                            DropdownMenuItem(
                                text = {
                                    Text("${stu.name} (${stu.className}) — ${CurrencyFormatter.formatInr(stu.monthlyFeePaise)}/mo")
                                },
                                onClick = {
                                    selectedStudent = stu
                                    if (amountInput.isBlank() && stu.monthlyFeePaise > 0L) {
                                        amountInput = CurrencyFormatter.paiseToInputString(stu.monthlyFeePaise)
                                    }
                                    studentMenuExpanded = false
                                    errorText = null
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it; errorText = null },
                    label = { Text("Amount Received (₹) *") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input"),
                )

                // Quick fill monthly fee pill
                selectedStudent?.takeIf { it.monthlyFeePaise > 0L && existingPayment == null }?.let { stu ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                amountInput = CurrencyFormatter.paiseToInputString(stu.monthlyFeePaise)
                                errorText = null
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                "Monthly Fee (${CurrencyFormatter.formatInr(stu.monthlyFeePaise)})",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // Fee Month selector
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMonthPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("For Month", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                YearMonthUtil.displayMonthYear(forYearMonth),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick fee month")
                    }
                }

                IsoDatePickerField(
                    label = "Payment Date *",
                    isoDate = paymentDate,
                    onDateSelected = { paymentDate = it },
                )

                Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PaymentMethod.entries.forEach { method ->
                        val selected = method == selectedMethod
                        FilterChip(
                            selected = selected,
                            onClick = { selectedMethod = method },
                            label = { Text(method.label) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Partial payment, GPay ref...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = PendingRose,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !actionInProgress) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val stu = selectedStudent
                            if (stu == null) {
                                errorText = "Please add or select a student first."
                                return@Button
                            }
                            onSave(
                                stu,
                                amountInput,
                                paymentDate,
                                forYearMonth,
                                selectedMethod,
                                note,
                            ) { err -> errorText = err }
                        },
                        enabled = !actionInProgress,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("save_payment_button"),
                    ) {
                        Text(if (actionInProgress) "Saving..." else "Save Payment")
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYearMonth = forYearMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { picked ->
                forYearMonth = picked
                showMonthPicker = false
            },
        )
    }
}
