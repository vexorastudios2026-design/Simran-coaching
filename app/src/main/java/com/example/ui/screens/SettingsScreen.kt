package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.auth.TeacherAccessConfig
import com.example.ui.components.CloudSyncStatusPill
import com.example.ui.components.OfflineBanner
import androidx.compose.material3.OutlinedButton
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoftContainer
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PaidGreenBg
import com.example.ui.theme.PartialAmber
import com.example.ui.theme.PartialAmberBg
import com.example.ui.theme.PendingRose
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MainUiState

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    teacherEmail: String,
    onSaveProfile: (teacherName: String, tuitionName: String, onError: (String) -> Unit) -> Unit,
    onLogout: () -> Unit,
    onRetrySync: () -> Unit = {},
) {
    var teacherName by remember { mutableStateOf(uiState.teacherProfile.teacherName) }
    var tuitionName by remember { mutableStateOf(uiState.teacherProfile.tuitionName) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.teacherProfile.teacherName, uiState.teacherProfile.tuitionName) {
        teacherName = uiState.teacherProfile.teacherName
        tuitionName = uiState.teacherProfile.tuitionName
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OfflineBanner(isOffline = uiState.isOffline)
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Teacher Profile Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, OutlineLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Teacher & Tuition Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = { teacherName = it; formError = null },
                        label = { Text("Teacher Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = tuitionName,
                        onValueChange = { tuitionName = it; formError = null },
                        label = { Text("Tuition / Class Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (formError != null) {
                        Text(formError!!, color = PendingRose, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            onSaveProfile(teacherName, tuitionName) { err -> formError = err }
                        },
                        enabled = !uiState.actionInProgress,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_profile_button"),
                    ) {
                        Text(if (uiState.actionInProgress) "Saving..." else "Save Profile")
                    }
                }
            }
        }

        // Authorized Account Card
        item {
            val context = LocalContext.current
            val isLocked = TeacherAccessConfig.isLockConfigured(context)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, OutlineLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authorized Teacher Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Signed-in Gmail", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = teacherEmail.ifBlank { "Google Account" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Surface(color = PaidGreenBg, shape = RoundedCornerShape(50)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Verified, contentDescription = null, tint = PaidGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verified", style = MaterialTheme.typography.labelSmall, color = PaidGreen)
                            }
                        }
                    }

                    Surface(
                        color = if (isLocked) PaidGreenBg else PartialAmberBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isLocked) {
                                "Account Lock Active: Restricted to ${TeacherAccessConfig.configuredTeacherEmail(context)}"
                            } else {
                                "Account Lock Notice: To restrict access to a single teacher Gmail across all devices, set AUTHORIZED_TEACHER_EMAIL in Secrets/.env and configuredTeacherEmail() in firestore.rules."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLocked) PaidGreen else PartialAmber,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }

        // Currency & Security Cards
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, OutlineLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = IndigoSoftContainer, shape = RoundedCornerShape(10.dp)) {
                            Icon(
                                Icons.Outlined.CurrencyRupee,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Currency", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text("Indian Rupees (₹ / INR)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = IndigoSoftContainer, shape = RoundedCornerShape(10.dp)) {
                            Icon(
                                Icons.Outlined.Security,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Cloud Sync & Security", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                CloudSyncStatusPill(syncStatus = uiState.syncStatus, onRetryClick = onRetrySync)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "All student, fee, payment, and profile records sync permanently to Cloud Firestore under your Google UID and survive logout, restart, and reinstall.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRetrySync,
                        enabled = !uiState.isSyncingManually,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("sync_now_button"),
                    ) {
                        Text(if (uiState.isSyncingManually) "Syncing with Cloud..." else "Sync Now")
                    }
                }
            }
        }

        // Logout Button
        item {
            Button(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = PendingRose),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("logout_button"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("Your data stays safely saved in Cloud Firestore and will reload when you sign back in.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PendingRose),
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
