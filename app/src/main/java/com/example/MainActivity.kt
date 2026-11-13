package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.auth.TeacherAccessConfig
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutTeacher
import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.TuitionRepository
import com.example.domain.calc.YearMonthUtil
import com.example.domain.model.Payment
import com.example.domain.model.Student
import com.example.ui.components.AddEditPaymentDialog
import com.example.ui.components.AddEditStudentDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MonthlyRecordsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudentDetailScreen
import com.example.ui.screens.StudentsScreen
import com.example.ui.screens.TeacherSignInScreen
import com.example.ui.screens.UnauthorizedAccountScreen
import com.example.ui.theme.SimranTuitionTheme
import com.example.ui.viewmodel.TuitionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

object Routes {
    const val DASHBOARD = "dashboard"
    const val STUDENTS = "students"
    const val STUDENT_DETAIL = "student_detail/{studentId}"
    const val MONTHLY = "monthly"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    fun studentDetail(studentId: String) = "student_detail/$studentId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    BottomTab(Routes.STUDENTS, "Students", Icons.Rounded.Groups, Icons.Outlined.Groups),
    BottomTab(Routes.MONTHLY, "Monthly", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomTab(Routes.REPORTS, "Reports", Icons.Rounded.Assessment, Icons.Outlined.Assessment),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimranTuitionTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var loginEpoch by rememberSaveable { mutableIntStateOf(0) }
    var hadSignedOut by rememberSaveable { mutableStateOf(auth.currentUser == null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = {},
                onUnauthenticated = {},
            )
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser == null) {
            hadSignedOut = true
        } else if (hadSignedOut) {
            loginEpoch++
            hadSignedOut = false
        }
    }

    val user = currentUser
    if (user == null) {
        TeacherSignInScreen(onAuthSuccess = {})
    } else if (!TeacherAccessConfig.isEmailAuthorized(user.email, user.isEmailVerified, context)) {
        UnauthorizedAccountScreen(signedInEmail = user.email)
    } else {
        key(user.uid, loginEpoch) {
            AuthenticatedTuitionApp(
                currentUserId = user.uid,
                loginEpoch = loginEpoch,
                teacherEmail = user.email.orEmpty(),
                teacherDisplayName = user.displayName.orEmpty(),
            )
        }
    }
}

private data class PaymentDialogState(
    val initialStudent: Student? = null,
    val existingPayment: Payment? = null,
    val suggestedYearMonth: String = YearMonthUtil.currentYearMonth(),
    val suggestedAmountPaise: Long? = null,
)

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun AuthenticatedTuitionApp(
    currentUserId: String,
    loginEpoch: Int,
    teacherEmail: String,
    teacherDisplayName: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TuitionViewModel = viewModel(
        key = "${currentUserId}_$loginEpoch",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirestoreProvider.getDatabase(app)
                val repo = TuitionRepository(
                    db = db,
                    auth = Firebase.auth,
                    isOnlineProvider = { isNetworkAvailable(app) },
                )
                TuitionViewModel(
                    repository = repo,
                    currentUserId = currentUserId,
                    teacherEmail = teacherEmail,
                    teacherDisplayName = teacherDisplayName,
                    connectivityFlow = TuitionViewModel.observeNetworkConnectivity(app),
                )
            }
        },
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddStudentDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var paymentDialogState by remember { mutableStateOf<PaymentDialogState?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.DASHBOARD,
                ) {
                    composable(Routes.DASHBOARD) {
                        DashboardScreen(
                            uiState = uiState,
                            onAddStudentClick = { showAddStudentDialog = true },
                            onAddPaymentClick = {
                                if (uiState.students.isEmpty()) {
                                    viewModel.showMessage("Please add a student first before recording a payment.")
                                    showAddStudentDialog = true
                                } else {
                                    paymentDialogState = PaymentDialogState()
                                }
                            },
                            onStudentClick = { studentId ->
                                navController.navigate(Routes.studentDetail(studentId))
                            },
                            onViewAllStudents = {
                                navController.navigate(Routes.STUDENTS) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onRetrySync = { viewModel.retrySync(silent = false) },
                        )
                    }

                    composable(Routes.STUDENTS) {
                        StudentsScreen(
                            uiState = uiState,
                            onAddStudentClick = { showAddStudentDialog = true },
                            onQuickPayClick = { student, suggestedAmount ->
                                paymentDialogState = PaymentDialogState(
                                    initialStudent = student,
                                    suggestedAmountPaise = suggestedAmount,
                                )
                            },
                            onStudentClick = { studentId ->
                                navController.navigate(Routes.studentDetail(studentId))
                            },
                        )
                    }

                    composable(
                        route = Routes.STUDENT_DETAIL,
                        arguments = listOf(navArgument("studentId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val studentId = backStackEntry.arguments?.getString("studentId").orEmpty()
                        StudentDetailScreen(
                            studentId = studentId,
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onEditStudent = { stu -> studentToEdit = stu },
                            onDeleteStudent = { stu ->
                                viewModel.deleteStudent(stu) {
                                    navController.popBackStack()
                                }
                            },
                            onAddPayment = { stu, suggestedAmount ->
                                paymentDialogState = PaymentDialogState(
                                    initialStudent = stu,
                                    suggestedAmountPaise = suggestedAmount,
                                )
                            },
                            onEditPayment = { pay ->
                                paymentDialogState = PaymentDialogState(existingPayment = pay)
                            },
                            onDeletePayment = { pay -> viewModel.deletePayment(pay) },
                        )
                    }

                    composable(Routes.MONTHLY) {
                        MonthlyRecordsScreen(
                            uiState = uiState,
                            onPreviousMonth = { viewModel.shiftMonth(-1) },
                            onNextMonth = { viewModel.shiftMonth(1) },
                            onSelectMonthYear = { ym -> viewModel.selectMonth(ym) },
                            onRecordPaymentForMonth = { stu, ym, suggested ->
                                paymentDialogState = PaymentDialogState(
                                    initialStudent = stu,
                                    suggestedYearMonth = ym,
                                    suggestedAmountPaise = suggested,
                                )
                            },
                            onStudentClick = { studentId ->
                                navController.navigate(Routes.studentDetail(studentId))
                            },
                        )
                    }

                    composable(Routes.REPORTS) {
                        ReportsScreen(
                            uiState = uiState,
                            onPreviousMonth = { viewModel.shiftMonth(-1) },
                            onNextMonth = { viewModel.shiftMonth(1) },
                            onSelectMonthYear = { ym -> viewModel.selectMonth(ym) },
                            onSelectReportYear = { yr -> viewModel.selectReportYear(yr) },
                            onStudentClick = { studentId ->
                                navController.navigate(Routes.studentDetail(studentId))
                            },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            uiState = uiState,
                            teacherEmail = teacherEmail,
                            onSaveProfile = { tName, cName, onError ->
                                viewModel.saveTeacherProfile(tName, cName) { ok, err ->
                                    if (!ok && err != null) onError(err)
                                }
                            },
                            onLogout = {
                                signOutTeacher(credentialManager, scope)
                            },
                            onRetrySync = { viewModel.retrySync(silent = false) },
                        )
                    }
                }
            }
        }
    }

    if (showAddStudentDialog || studentToEdit != null) {
        val editing = studentToEdit
        AddEditStudentDialog(
            existingStudent = editing,
            actionInProgress = uiState.actionInProgress,
            onDismiss = {
                showAddStudentDialog = false
                studentToEdit = null
            },
            onSave = { name, phone, className, feeInput, joinDate, notes, active, effectiveFrom, onError ->
                if (editing == null) {
                    viewModel.addStudent(name, phone, className, feeInput, joinDate, notes) { ok, err ->
                        if (ok) showAddStudentDialog = false else if (err != null) onError(err)
                    }
                } else {
                    viewModel.updateStudent(
                        existing = editing,
                        name = name,
                        phone = phone,
                        className = className,
                        monthlyFeeInput = feeInput,
                        joiningDate = joinDate,
                        notes = notes,
                        active = active,
                        effectiveFromYearMonth = effectiveFrom,
                    ) { ok, err ->
                        if (ok) studentToEdit = null else if (err != null) onError(err)
                    }
                }
            },
        )
    }

    paymentDialogState?.let { dialogState ->
        AddEditPaymentDialog(
            students = uiState.students,
            initialStudent = dialogState.initialStudent,
            existingPayment = dialogState.existingPayment,
            suggestedYearMonth = dialogState.suggestedYearMonth,
            suggestedAmountPaise = dialogState.suggestedAmountPaise,
            actionInProgress = uiState.actionInProgress,
            onDismiss = { paymentDialogState = null },
            onSave = { stu, amountInput, paymentDate, forYm, method, note, onError ->
                val editingPay = dialogState.existingPayment
                if (editingPay == null) {
                    viewModel.addPayment(stu, amountInput, paymentDate, forYm, method, note) { ok, err ->
                        if (ok) paymentDialogState = null else if (err != null) onError(err)
                    }
                } else {
                    viewModel.updatePayment(editingPay, amountInput, paymentDate, forYm, method, note) { ok, err ->
                        if (ok) paymentDialogState = null else if (err != null) onError(err)
                    }
                }
            },
        )
    }
}
