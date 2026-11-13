package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.StudentFeeSummary
import com.example.domain.model.FeeStatus
import com.example.domain.model.Student
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CreditSky
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoftContainer
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PendingRose
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MainUiState

@Composable
fun StudentsScreen(
    uiState: MainUiState,
    onAddStudentClick: () -> Unit,
    onQuickPayClick: (Student, Long) -> Unit,
    onStudentClick: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf<FeeStatus?>(null) }

    val availableClasses = remember(uiState.students) {
        listOf("All") + uiState.students
            .map { it.className.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val filteredSummaries = remember(uiState.studentSummaries, searchQuery, selectedClass, selectedStatus) {
        uiState.studentSummaries.filter { summary ->
            val matchesName = searchQuery.isBlank() ||
                summary.student.name.contains(searchQuery.trim(), ignoreCase = true)
            val matchesClass = selectedClass == "All" ||
                summary.student.className.equals(selectedClass, ignoreCase = true)
            val matchesStatus = selectedStatus == null || summary.status == selectedStatus
            matchesName && matchesClass && matchesStatus
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddStudentClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_student"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add student")
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OfflineBanner(isOffline = uiState.isOffline)
                Text(
                    text = "Students (${uiState.students.size})",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (uiState.students.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search student by name...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_search_input"),
                    )
                }

                // Class Filter Row
                if (availableClasses.size > 2) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            availableClasses.forEach { cls ->
                                FilterChip(
                                    selected = selectedClass == cls,
                                    onClick = { selectedClass = cls },
                                    label = { Text(if (cls == "All") "All Classes" else cls) },
                                )
                            }
                        }
                    }
                }

                // Status Filter Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedStatus == null,
                            onClick = { selectedStatus = null },
                            label = { Text("All Status") },
                        )
                        listOf(FeeStatus.UNPAID, FeeStatus.PARTIAL, FeeStatus.PAID, FeeStatus.CREDIT).forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = if (selectedStatus == status) null else status },
                                label = { Text(status.label) },
                            )
                        }
                    }
                }
            }

            if (uiState.students.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Students Added Yet",
                        description = "Tap Add Student to enroll your first student with their class and monthly fee.",
                        showIllustration = true,
                        actionLabel = "Add Student",
                        onAction = onAddStudentClick,
                    )
                }
            } else if (filteredSummaries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Matching Students",
                        description = "Try clearing the search query or changing the class/status filter.",
                        showIllustration = false,
                        actionLabel = "Clear Filters",
                        onAction = {
                            searchQuery = ""
                            selectedClass = "All"
                            selectedStatus = null
                        },
                    )
                }
            } else {
                items(filteredSummaries, key = { it.student.id }) { summary ->
                    StudentSummaryCard(
                        summary = summary,
                        onClick = { onStudentClick(summary.student.id) },
                        onPayClick = {
                            val suggested = summary.remainingBalancePaise.takeIf { it > 0L }
                                ?: summary.student.monthlyFeePaise
                            onQuickPayClick(summary.student, suggested)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentSummaryCard(
    summary: StudentFeeSummary,
    onClick: () -> Unit,
    onPayClick: () -> Unit,
) {
    val stu = summary.student
    val initial = stu.name.firstOrNull()?.uppercaseChar()?.toString() ?: "S"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_card_${stu.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OutlineLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(IndigoSoftContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stu.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${stu.className} · ${CurrencyFormatter.formatInr(stu.monthlyFeePaise)}/mo" +
                                if (!stu.active) " (Inactive)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = summary.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Total Fee", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        CurrencyFormatter.formatInr(summary.totalExpectedPaise),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Column {
                    Text("Paid", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        CurrencyFormatter.formatInr(summary.totalPaidPaise),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PaidGreen,
                    )
                }
                Column {
                    Text(
                        if (summary.creditBalancePaise > 0L) "Credit" else "Remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                    Text(
                        text = if (summary.creditBalancePaise > 0L) {
                            CurrencyFormatter.formatInr(summary.creditBalancePaise)
                        } else {
                            CurrencyFormatter.formatInr(summary.remainingBalancePaise)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            summary.creditBalancePaise > 0L -> CreditSky
                            summary.remainingBalancePaise > 0L -> PendingRose
                            else -> PaidGreen
                        },
                    )
                }
                FilledTonalButton(
                    onClick = onPayClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Pay", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
