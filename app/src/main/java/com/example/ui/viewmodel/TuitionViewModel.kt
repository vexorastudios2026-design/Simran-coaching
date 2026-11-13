package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.CloudSnapshot
import com.example.data.firestore.TuitionRepository
import com.example.data.firestore.WriteSyncOutcome
import com.example.data.firestore.friendlyFirestoreMessage
import com.example.domain.calc.CurrencyFormatter
import com.example.domain.calc.DashboardSummary
import com.example.domain.calc.FeeCalculator
import com.example.domain.calc.MonthlySummary
import com.example.domain.calc.StudentFeeSummary
import com.example.domain.calc.YearMonthUtil
import com.example.domain.calc.YearlyReport
import com.example.domain.model.FeeRate
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Student
import com.example.domain.model.TeacherProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CloudSyncStatus {
    SYNCING,
    SYNCED,
    PENDING_UPLOAD,
    OFFLINE_CACHED,
    ERROR,
}

data class MainUiState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val isCloudHydrated: Boolean = false,
    val needsInitialCloudRestore: Boolean = false,
    val isSyncingManually: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val syncStatus: CloudSyncStatus = CloudSyncStatus.SYNCING,
    val errorMessage: String? = null,
    val students: List<Student> = emptyList(),
    val feeRates: List<FeeRate> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val teacherProfile: TeacherProfile = TeacherProfile(),
    val hasSavedProfile: Boolean = false,
    val dashboardSummary: DashboardSummary = DashboardSummary(
        totalStudents = 0,
        activeStudents = 0,
        totalExpectedPaise = 0L,
        totalCollectedPaise = 0L,
        totalPendingPaise = 0L,
        totalCreditPaise = 0L,
        fullyPaidStudentsCount = 0,
        pendingStudentsCount = 0,
        currentMonthCollectionPaise = 0L,
        currentMonthExpectedPaise = 0L,
        currentMonthPendingPaise = 0L,
        recentPayments = emptyList(),
    ),
    val studentSummaries: List<StudentFeeSummary> = emptyList(),
    val selectedYearMonth: String = YearMonthUtil.currentYearMonth(),
    val monthlySummary: MonthlySummary = MonthlySummary(
        yearMonth = YearMonthUtil.currentYearMonth(),
        expectedTotalPaise = 0L,
        collectedTotalPaise = 0L,
        creditAppliedTotalPaise = 0L,
        pendingTotalPaise = 0L,
        overpaidCreditTotalPaise = 0L,
        paidCount = 0,
        partialCount = 0,
        unpaidCount = 0,
        studentStatuses = emptyList(),
    ),
    val selectedReportYear: Int = YearMonthUtil.parseYearMonth(YearMonthUtil.currentYearMonth())?.first ?: 2026,
    val yearlyReport: YearlyReport = YearlyReport(
        year = YearMonthUtil.parseYearMonth(YearMonthUtil.currentYearMonth())?.first ?: 2026,
        totalExpectedPaise = 0L,
        totalCollectedPaise = 0L,
        totalPendingPaise = 0L,
        monthlyRows = emptyList(),
    ),
    val actionInProgress: Boolean = false,
    val snackbarMessage: String? = null,
)

private sealed interface StreamData<out T> {
    data object Loading : StreamData<Nothing>
    data class Loaded<T>(val value: T) : StreamData<T>
    data class Failed(val message: String) : StreamData<Nothing>
}

class TuitionViewModel(
    private val repository: TuitionRepository,
    private val currentUserId: String,
    private val teacherEmail: String,
    private val teacherDisplayName: String,
    private val connectivityFlow: Flow<Boolean>,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(YearMonthUtil.currentYearMonth())
    private val selectedReportYear = MutableStateFlow(
        YearMonthUtil.parseYearMonth(YearMonthUtil.currentYearMonth())?.first ?: 2026
    )
    private val actionInProgress = MutableStateFlow(false)
    private val isSyncingManually = MutableStateFlow(false)
    private val manualSyncError = MutableStateFlow<String?>(null)
    private val serverSyncVerified = MutableStateFlow(false)
    private val snackbarMessage = MutableStateFlow<String?>(null)

    init {
        // Trigger initial server verification & auto-flush pending writes whenever connection returns
        retrySync(silent = true)
        viewModelScope.launch {
            connectivityFlow
                .distinctUntilChanged()
                .drop(1)
                .filter { isOnline -> isOnline }
                .collect {
                    retrySync(silent = true)
                }
        }
    }

    private fun <T> Flow<CloudSnapshot<T>>.asStreamData(label: String): Flow<StreamData<CloudSnapshot<T>>> =
        map<CloudSnapshot<T>, StreamData<CloudSnapshot<T>>> { StreamData.Loaded(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(StreamData.Failed(friendlyFirestoreMessage(rootError, "Failed to load $label")))
            }

    private val baseDataFlow = combine(
        repository.observeStudentsSnapshot(currentUserId).asStreamData("students"),
        repository.observeFeeRatesSnapshot(currentUserId).asStreamData("feeRates"),
        repository.observePaymentsSnapshot(currentUserId).asStreamData("payments"),
        repository.observeTeacherProfileSnapshot(currentUserId).asStreamData("profile"),
    ) { studentsStream, ratesStream, paymentsStream, profileStream ->
        Quad(studentsStream, ratesStream, paymentsStream, profileStream)
    }

    private val syncFlagsFlow = combine(
        isSyncingManually,
        manualSyncError,
        serverSyncVerified,
    ) { syncing, err, verified ->
        Triple(syncing, err, verified)
    }

    private val controlsFlow = combine(
        selectedYearMonth,
        selectedReportYear,
        actionInProgress,
        snackbarMessage,
        connectivityFlow,
        syncFlagsFlow,
    ) { array ->
        val ym = array[0] as String
        val year = array[1] as Int
        val busy = array[2] as Boolean
        val snack = array[3] as String?
        val isOnline = array[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val flags = array[5] as Triple<Boolean, String?, Boolean>
        ControlsState(
            selectedYearMonth = ym,
            selectedReportYear = year,
            actionInProgress = busy,
            snackbarMessage = snack,
            isOffline = !isOnline,
            isSyncingManually = flags.first,
            manualSyncError = flags.second,
            serverSyncVerified = flags.third,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(baseDataFlow, controlsFlow) { data, controls ->
        val streamLoading = data.a is StreamData.Loading ||
            data.b is StreamData.Loading ||
            data.c is StreamData.Loading

        val streamError = listOfNotNull(
            (data.a as? StreamData.Failed)?.message,
            (data.b as? StreamData.Failed)?.message,
            (data.c as? StreamData.Failed)?.message,
            (data.d as? StreamData.Failed)?.message,
        ).firstOrNull()

        val studentsSnap = (data.a as? StreamData.Loaded)?.value
        val ratesSnap = (data.b as? StreamData.Loaded)?.value
        val paymentsSnap = (data.c as? StreamData.Loaded)?.value
        val profileSnap = (data.d as? StreamData.Loaded)?.value

        val students = studentsSnap?.data.orEmpty()
        val feeRates = ratesSnap?.data.orEmpty()
        val payments = paymentsSnap?.data.orEmpty()
        val savedProfile = profileSnap?.data

        val anyFromCache = listOfNotNull(studentsSnap, ratesSnap, paymentsSnap)
            .any { it.isFromCache }
        val anyPendingWrites = listOfNotNull(studentsSnap, ratesSnap, paymentsSnap, profileSnap)
            .any { it.hasPendingWrites }

        val serverHydrated = (!streamLoading && !anyFromCache) || controls.serverSyncVerified
        val hasLocalData = students.isNotEmpty() || payments.isNotEmpty() || savedProfile != null
        val isCloudHydrated = serverHydrated || hasLocalData

        // Avoid flashing an empty dashboard on a fresh install/device while waiting for online server response
        val waitingForInitialCloudLoad = streamLoading ||
            (!serverHydrated && !hasLocalData && !controls.isOffline && streamError == null && controls.manualSyncError == null)

        val needsInitialCloudRestore = !streamLoading && !serverHydrated && !hasLocalData &&
            (controls.isOffline || streamError != null || controls.manualSyncError != null)

        val effectiveError = streamError ?: controls.manualSyncError

        val syncStatus = when {
            effectiveError != null -> CloudSyncStatus.ERROR
            waitingForInitialCloudLoad || controls.isSyncingManually -> CloudSyncStatus.SYNCING
            anyPendingWrites -> CloudSyncStatus.PENDING_UPLOAD
            controls.isOffline || anyFromCache -> CloudSyncStatus.OFFLINE_CACHED
            else -> CloudSyncStatus.SYNCED
        }

        val profile = savedProfile ?: TeacherProfile(
            userId = currentUserId,
            teacherName = teacherDisplayName.ifBlank { "Simran Ma'am" },
            tuitionName = "Simran Tution Classes",
            teacherEmail = teacherEmail,
            currencyCode = "INR",
        )

        val currentYm = YearMonthUtil.currentYearMonth()
        val studentSummaries = students.map { student ->
            FeeCalculator.calculateStudentLedger(student, feeRates, payments, currentYm).first
        }
        val dashboard = FeeCalculator.calculateDashboardSummary(students, feeRates, payments, currentYm)
        val monthly = FeeCalculator.calculateMonthlySummary(
            students = students,
            feeRates = feeRates,
            payments = payments,
            yearMonth = controls.selectedYearMonth,
            currentYearMonth = currentYm,
        )
        val yearly = FeeCalculator.calculateYearlyReport(
            year = controls.selectedReportYear,
            students = students,
            feeRates = feeRates,
            payments = payments,
            currentYearMonth = currentYm,
        )

        MainUiState(
            isLoading = waitingForInitialCloudLoad,
            isOffline = controls.isOffline,
            isCloudHydrated = isCloudHydrated,
            needsInitialCloudRestore = needsInitialCloudRestore,
            isSyncingManually = controls.isSyncingManually,
            hasPendingWrites = anyPendingWrites,
            syncStatus = syncStatus,
            errorMessage = effectiveError,
            students = students,
            feeRates = feeRates,
            payments = payments,
            teacherProfile = profile,
            hasSavedProfile = savedProfile != null,
            dashboardSummary = dashboard,
            studentSummaries = studentSummaries,
            selectedYearMonth = controls.selectedYearMonth,
            monthlySummary = monthly,
            selectedReportYear = controls.selectedReportYear,
            yearlyReport = yearly,
            actionInProgress = controls.actionInProgress,
            snackbarMessage = controls.snackbarMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MainUiState(isLoading = true),
    )

    fun retrySync(silent: Boolean = false) {
        if (isSyncingManually.value) return
        viewModelScope.launch {
            isSyncingManually.value = true
            manualSyncError.value = null
            val res = repository.syncWithServer(currentUserId)
            isSyncingManually.value = false
            res.fold(
                onSuccess = {
                    serverSyncVerified.value = true
                    manualSyncError.value = null
                    if (!silent) {
                        snackbarMessage.value = "All records synced with Cloud Firestore"
                    }
                },
                onFailure = { e ->
                    val msg = friendlyFirestoreMessage(e, "Could not reach Cloud Firestore.")
                    if (!silent || uiState.value.students.isEmpty()) {
                        manualSyncError.value = msg
                    }
                }
            )
        }
    }

    fun selectMonth(yearMonth: String) {
        if (YearMonthUtil.parseYearMonth(yearMonth) != null) {
            selectedYearMonth.value = yearMonth
        }
    }

    fun shiftMonth(deltaMonths: Int) {
        selectedYearMonth.value = YearMonthUtil.shiftYearMonth(selectedYearMonth.value, deltaMonths)
    }

    fun selectReportYear(year: Int) {
        selectedReportYear.value = year.coerceIn(2000, 2100)
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        snackbarMessage.value = message
    }

    fun addStudent(
        name: String,
        phone: String,
        className: String,
        monthlyFeeInput: String,
        joiningDate: String,
        notes: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val trimmedName = name.trim()
        val trimmedClass = className.trim()
        val trimmedPhone = phone.trim()
        val feePaise = CurrencyFormatter.parseRupeesToPaise(monthlyFeeInput)
        val joinYm = YearMonthUtil.isoDateToYearMonth(joiningDate)

        when {
            trimmedName.isEmpty() -> {
                onResult(false, "Please enter the student's full name.")
                return
            }
            trimmedName.length > 100 -> {
                onResult(false, "Student name must be 100 characters or less.")
                return
            }
            trimmedClass.isEmpty() -> {
                onResult(false, "Please enter the class or grade.")
                return
            }
            trimmedPhone.length > 20 -> {
                onResult(false, "Phone number is too long.")
                return
            }
            feePaise == null || feePaise < 0L -> {
                onResult(false, "Enter a valid monthly fee in ₹ (0 or more).")
                return
            }
            joinYm == null -> {
                onResult(false, "Select a valid joining date.")
                return
            }
        }

        val student = Student(
            userId = currentUserId,
            name = trimmedName,
            phone = trimmedPhone,
            className = trimmedClass,
            monthlyFeePaise = feePaise,
            joiningDate = joiningDate,
            joiningYearMonth = joinYm,
            notes = notes.trim().take(500),
            active = true,
        )

        viewModelScope.launch {
            actionInProgress.value = true
            val res = repository.addStudentWithOutcome(currentUserId, student)
            actionInProgress.value = false
            res.fold(
                onSuccess = { ack ->
                    snackbarMessage.value = if (ack.outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Added $trimmedName (saved offline, will sync automatically)"
                    } else {
                        "Added $trimmedName"
                    }
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, friendlyFirestoreMessage(e, "Could not add student."))
                }
            )
        }
    }

    fun updateStudent(
        existing: Student,
        name: String,
        phone: String,
        className: String,
        monthlyFeeInput: String,
        joiningDate: String,
        notes: String,
        active: Boolean,
        effectiveFromYearMonth: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val trimmedName = name.trim()
        val trimmedClass = className.trim()
        val feePaise = CurrencyFormatter.parseRupeesToPaise(monthlyFeeInput)
        val joinYm = YearMonthUtil.isoDateToYearMonth(joiningDate)

        when {
            trimmedName.isEmpty() -> {
                onResult(false, "Please enter the student's full name.")
                return
            }
            trimmedClass.isEmpty() -> {
                onResult(false, "Please enter the class or grade.")
                return
            }
            feePaise == null || feePaise < 0L -> {
                onResult(false, "Enter a valid monthly fee in ₹.")
                return
            }
            joinYm == null -> {
                onResult(false, "Select a valid joining date.")
                return
            }
        }

        val updated = existing.copy(
            name = trimmedName,
            phone = phone.trim().take(20),
            className = trimmedClass.take(50),
            monthlyFeePaise = feePaise,
            joiningDate = joiningDate,
            joiningYearMonth = joinYm,
            notes = notes.trim().take(500),
            active = active,
        )

        val effectiveMonth = if (feePaise != existing.monthlyFeePaise) {
            effectiveFromYearMonth.takeIf { YearMonthUtil.parseYearMonth(it) != null }
                ?: YearMonthUtil.currentYearMonth()
        } else {
            null
        }

        viewModelScope.launch {
            actionInProgress.value = true
            val res = repository.updateStudentWithOutcome(
                userId = currentUserId,
                existing = existing,
                updated = updated,
                newFeeEffectiveFrom = effectiveMonth,
            )
            actionInProgress.value = false
            res.fold(
                onSuccess = { outcome ->
                    snackbarMessage.value = if (outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Updated $trimmedName (queued for cloud sync)"
                    } else {
                        "Updated $trimmedName"
                    }
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, friendlyFirestoreMessage(e, "Could not update student."))
                }
            )
        }
    }

    fun deleteStudent(student: Student, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            actionInProgress.value = true
            val res = repository.deleteStudentWithOutcome(currentUserId, student.id)
            actionInProgress.value = false
            res.fold(
                onSuccess = { outcome ->
                    snackbarMessage.value = if (outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Deleted ${student.name} (queued for cloud sync)"
                    } else {
                        "Deleted ${student.name}"
                    }
                    onSuccess()
                },
                onFailure = { e ->
                    snackbarMessage.value = friendlyFirestoreMessage(e, "Could not delete student.")
                }
            )
        }
    }

    fun addPayment(
        student: Student,
        amountInput: String,
        paymentDate: String,
        forYearMonth: String,
        method: PaymentMethod,
        note: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val amountPaise = CurrencyFormatter.parseRupeesToPaise(amountInput)
        when {
            amountPaise == null || amountPaise <= 0L -> {
                onResult(false, "Enter a valid positive payment amount in ₹.")
                return
            }
            !YearMonthUtil.isValidIsoDate(paymentDate) -> {
                onResult(false, "Select a valid payment date.")
                return
            }
            YearMonthUtil.parseYearMonth(forYearMonth) == null -> {
                onResult(false, "Select a valid fee month and year.")
                return
            }
        }

        val payment = Payment(
            userId = currentUserId,
            studentId = student.id,
            studentNameSnapshot = student.name,
            amountPaise = amountPaise,
            paymentDate = paymentDate,
            forYearMonth = forYearMonth,
            method = method.label,
            note = note.trim().take(300),
        )

        viewModelScope.launch {
            actionInProgress.value = true
            val res = repository.addPaymentWithOutcome(currentUserId, payment)
            actionInProgress.value = false
            res.fold(
                onSuccess = { ack ->
                    snackbarMessage.value = if (ack.outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Recorded ${CurrencyFormatter.formatInr(amountPaise)} for ${student.name} (queued offline)"
                    } else {
                        "Recorded ${CurrencyFormatter.formatInr(amountPaise)} for ${student.name}"
                    }
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, friendlyFirestoreMessage(e, "Could not record payment."))
                }
            )
        }
    }

    fun updatePayment(
        existing: Payment,
        amountInput: String,
        paymentDate: String,
        forYearMonth: String,
        method: PaymentMethod,
        note: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val amountPaise = CurrencyFormatter.parseRupeesToPaise(amountInput)
        when {
            amountPaise == null || amountPaise <= 0L -> {
                onResult(false, "Enter a valid positive payment amount in ₹.")
                return
            }
            !YearMonthUtil.isValidIsoDate(paymentDate) -> {
                onResult(false, "Select a valid payment date.")
                return
            }
            YearMonthUtil.parseYearMonth(forYearMonth) == null -> {
                onResult(false, "Select a valid fee month and year.")
                return
            }
        }

        val updated = existing.copy(
            amountPaise = amountPaise,
            paymentDate = paymentDate,
            forYearMonth = forYearMonth,
            method = method.label,
            note = note.trim().take(300),
        )

        viewModelScope.launch {
            actionInProgress.value = true
            val res = repository.updatePaymentWithOutcome(currentUserId, existing.id, updated)
            actionInProgress.value = false
            res.fold(
                onSuccess = { outcome ->
                    snackbarMessage.value = if (outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Payment updated (queued offline)"
                    } else {
                        "Payment updated (${CurrencyFormatter.formatInr(amountPaise)})"
                    }
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, friendlyFirestoreMessage(e, "Could not update payment."))
                }
            )
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            actionInProgress.value = true
            val res = repository.deletePaymentWithOutcome(currentUserId, payment.id)
            actionInProgress.value = false
            res.fold(
                onSuccess = { outcome ->
                    snackbarMessage.value = if (outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Payment deleted (queued for cloud sync)"
                    } else {
                        "Payment deleted & balance recalculated"
                    }
                },
                onFailure = { e ->
                    snackbarMessage.value = friendlyFirestoreMessage(e, "Could not delete payment.")
                }
            )
        }
    }

    fun saveTeacherProfile(
        teacherName: String,
        tuitionName: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val tName = teacherName.trim()
        val className = tuitionName.trim()
        if (tName.isEmpty() || className.isEmpty()) {
            onResult(false, "Teacher name and tuition class name cannot be empty.")
            return
        }
        val profile = TeacherProfile(
            userId = currentUserId,
            teacherName = tName.take(100),
            tuitionName = className.take(100),
            teacherEmail = teacherEmail,
            currencyCode = "INR",
        )
        viewModelScope.launch {
            actionInProgress.value = true
            val knownExists = if (uiState.value.hasSavedProfile) true else null
            val res = repository.saveTeacherProfileWithOutcome(
                userId = currentUserId,
                profile = profile,
                knownExists = knownExists,
            )
            actionInProgress.value = false
            res.fold(
                onSuccess = { outcome ->
                    snackbarMessage.value = if (outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                        "Settings saved offline (will sync automatically)"
                    } else {
                        "Settings saved to cloud"
                    }
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, friendlyFirestoreMessage(e, "Could not save profile."))
                }
            )
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class ControlsState(
        val selectedYearMonth: String,
        val selectedReportYear: Int,
        val actionInProgress: Boolean,
        val snackbarMessage: String?,
        val isOffline: Boolean,
        val isSyncingManually: Boolean,
        val manualSyncError: String?,
        val serverSyncVerified: Boolean,
    )

    companion object {
        private const val TAG = "TuitionVM"

        fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            fun checkNow(): Boolean {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            trySend(checkNow())

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    trySend(checkNow())
                }
            }
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }
    }
}
