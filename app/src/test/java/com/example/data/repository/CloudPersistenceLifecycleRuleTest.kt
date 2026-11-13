package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firestore.TuitionRepository
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Student
import com.example.domain.model.TeacherProfile
import com.example.ui.viewmodel.CloudSyncStatus
import com.example.ui.viewmodel.TuitionViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CloudPersistenceLifecycleRuleTest : FirestoreEmulatorTestBase() {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun logoutAndLogin_sameGmailAccount_restoresAllSavedData() = runBlocking {
        val teacherEmail = "simran_persist_${UUID.randomUUID()}@gmail.com"
        val uidFirstLogin = signInTestUser(teacherEmail)
        val repo = TuitionRepository(firestore, auth)

        val studentId = withTimeout(TIMEOUT_MS) {
            repo.addStudent(
                uidFirstLogin,
                Student(
                    name = "Kabir Mehta",
                    phone = "9811122233",
                    className = "Class 10",
                    monthlyFeePaise = 200_000L,
                    joiningDate = "2026-10-01",
                    joiningYearMonth = "2026-10",
                    notes = "Board batch",
                )
            ).getOrThrow()
        }

        val paymentId = withTimeout(TIMEOUT_MS) {
            repo.addPayment(
                uidFirstLogin,
                Payment(
                    studentId = studentId,
                    studentNameSnapshot = "Kabir Mehta",
                    amountPaise = 200_000L,
                    paymentDate = "2026-10-05",
                    forYearMonth = "2026-10",
                    method = PaymentMethod.UPI.label,
                    note = "October full fee",
                )
            ).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            repo.saveTeacherProfile(
                uidFirstLogin,
                TeacherProfile(
                    teacherName = "Simran Kaur",
                    tuitionName = "Simran Tution Classes - Morning",
                    teacherEmail = teacherEmail,
                )
            ).getOrThrow()
        }

        // Logout: ends auth session only, must NOT delete any cloud data
        auth.signOut()

        // Sign back in with the exact same Gmail account
        val uidSecondLogin = signInTestUser(teacherEmail)
        assertEquals(uidFirstLogin, uidSecondLogin)

        withTimeout(TIMEOUT_MS) {
            repo.syncWithServer(uidSecondLogin).getOrThrow()
        }

        val restoredStudents = withTimeout(TIMEOUT_MS) {
            repo.observeStudents(uidSecondLogin).first { it.any { s -> s.id == studentId } }
        }
        val restoredPayments = withTimeout(TIMEOUT_MS) {
            repo.observePayments(uidSecondLogin).first { it.any { p -> p.id == paymentId } }
        }
        val restoredRates = withTimeout(TIMEOUT_MS) {
            repo.observeFeeRates(uidSecondLogin).first { it.any { r -> r.studentId == studentId } }
        }
        val restoredProfile = withTimeout(TIMEOUT_MS) {
            repo.observeTeacherProfile(uidSecondLogin).first { it != null }
        }

        assertEquals("Kabir Mehta", restoredStudents.first { it.id == studentId }.name)
        assertEquals(200_000L, restoredPayments.first { it.id == paymentId }.amountPaise)
        assertEquals(200_000L, restoredRates.first { it.studentId == studentId }.monthlyFeePaise)
        assertEquals("Simran Tution Classes - Morning", restoredProfile?.tuitionName)
    }

    @Test
    fun reinstallOrNewDevice_freshClientWithEmptyCache_restoresAllCloudData() = runBlocking {
        val teacherEmail = "simran_device_${UUID.randomUUID()}@gmail.com"
        val device1Uid = signInTestUser(teacherEmail)
        val device1Repo = TuitionRepository(firestore, auth)

        val studentId = withTimeout(TIMEOUT_MS) {
            device1Repo.addStudent(
                device1Uid,
                Student(
                    name = "Ananya Verma",
                    phone = "9900112233",
                    className = "Class 9",
                    monthlyFeePaise = 150_000L,
                    joiningDate = "2026-09-10",
                    joiningYearMonth = "2026-09",
                )
            ).getOrThrow()
        }
        val paymentId = withTimeout(TIMEOUT_MS) {
            device1Repo.addPayment(
                device1Uid,
                Payment(
                    studentId = studentId,
                    studentNameSnapshot = "Ananya Verma",
                    amountPaise = 100_000L,
                    paymentDate = "2026-09-12",
                    forYearMonth = "2026-09",
                    method = PaymentMethod.CASH.label,
                )
            ).getOrThrow()
        }
        auth.signOut()

        // Simulate fresh app reinstall or second device with empty local cache
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device2AppName = "device2_${UUID.randomUUID()}"
        val device2App = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("com.simrantutionclasses.app")
                .setProjectId("demo-no-project")
                .setApiKey("fake-api-key-for-emulator")
                .build(),
            device2AppName,
        )

        try {
            val device2Firestore = FirebaseFirestore.getInstance(device2App, databaseId).apply {
                useEmulator("127.0.0.1", 8085)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
            val device2Auth = FirebaseAuth.getInstance(device2App).apply {
                useEmulator("127.0.0.1", 9099)
            }

            val device2SignIn = withTimeout(TIMEOUT_MS) {
                device2Auth.signInWithEmailAndPassword(teacherEmail, "password123").await()
            }
            val device2Uid = checkNotNull(device2SignIn.user).uid
            assertEquals(device1Uid, device2Uid)

            val device2Repo = TuitionRepository(device2Firestore, device2Auth)
            withTimeout(TIMEOUT_MS) {
                device2Repo.syncWithServer(device2Uid).getOrThrow()
            }

            val device2Students = withTimeout(TIMEOUT_MS) {
                device2Repo.observeStudents(device2Uid).first { it.any { s -> s.id == studentId } }
            }
            val device2Payments = withTimeout(TIMEOUT_MS) {
                device2Repo.observePayments(device2Uid).first { it.any { p -> p.id == paymentId } }
            }

            assertEquals("Ananya Verma", device2Students.first { it.id == studentId }.name)
            assertEquals(100_000L, device2Payments.first { it.id == paymentId }.amountPaise)
        } finally {
            device2App.delete()
        }
    }

    @Test
    fun separateGmailAccounts_haveCompletelyIsolatedDataAcrossSwitch() = runBlocking {
        val emailA = "teacher_a_${UUID.randomUUID()}@gmail.com"
        val emailB = "teacher_b_${UUID.randomUUID()}@gmail.com"

        // User A logs in and creates Student A
        val uidA = signInTestUser(emailA)
        val repo = TuitionRepository(firestore, auth)
        val studentAId = withTimeout(TIMEOUT_MS) {
            repo.addStudent(
                uidA,
                Student(
                    name = "UserA Student",
                    className = "Class 8",
                    monthlyFeePaise = 100_000L,
                    joiningDate = "2026-11-01",
                    joiningYearMonth = "2026-11",
                )
            ).getOrThrow()
        }
        auth.signOut()

        // User B logs in on same device -> gets separate UID and empty dataset
        val uidB = signInTestUser(emailB)
        assertNotEquals(uidA, uidB)

        val userBInitialStudents = withTimeout(TIMEOUT_MS) {
            repo.observeStudentsSnapshot(uidB).first { !it.isFromCache }.data
        }
        assertTrue("New Gmail account must not see User A's students", userBInitialStudents.isEmpty())

        val studentBId = withTimeout(TIMEOUT_MS) {
            repo.addStudent(
                uidB,
                Student(
                    name = "UserB Student",
                    className = "Class 12",
                    monthlyFeePaise = 250_000L,
                    joiningDate = "2026-11-01",
                    joiningYearMonth = "2026-11",
                )
            ).getOrThrow()
        }
        auth.signOut()

        // User A logs back in -> sees ONLY User A's student, never User B's
        val uidARelogin = signInTestUser(emailA)
        assertEquals(uidA, uidARelogin)

        val userARestored = withTimeout(TIMEOUT_MS) {
            repo.observeStudentsSnapshot(uidARelogin).first { !it.isFromCache && it.data.isNotEmpty() }.data
        }
        assertEquals(1, userARestored.size)
        assertEquals(studentAId, userARestored.first().id)
        assertFalse(userARestored.any { it.id == studentBId })
    }

    @Test
    fun viewModelHydration_existingCloudAccount_restoresDataWithoutOverwritingProfile() = runBlocking {
        val email = "simran_vm_${UUID.randomUUID()}@gmail.com"
        val uid = signInTestUser(email)
        val repo = TuitionRepository(firestore, auth)

        val studentId = withTimeout(TIMEOUT_MS) {
            repo.addStudent(
                uid,
                Student(
                    name = "Rohan Gupta",
                    className = "Class 11",
                    monthlyFeePaise = 180_000L,
                    joiningDate = "2026-10-01",
                    joiningYearMonth = "2026-10",
                )
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            repo.addPayment(
                uid,
                Payment(
                    studentId = studentId,
                    studentNameSnapshot = "Rohan Gupta",
                    amountPaise = 180_000L,
                    paymentDate = "2026-10-08",
                    forYearMonth = "2026-10",
                    method = PaymentMethod.BANK_TRANSFER.label,
                )
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            repo.saveTeacherProfile(
                uid,
                TeacherProfile(
                    teacherName = "Simran Ma'am",
                    tuitionName = "Simran Tution Classes Cloud",
                    teacherEmail = email,
                )
            ).getOrThrow()
        }

        // Create fresh ViewModel (simulating app restart / login on another device)
        val onlineFlow = MutableStateFlow(true)
        val vm = TuitionViewModel(
            repository = repo,
            currentUserId = uid,
            teacherEmail = email,
            teacherDisplayName = "Ignored Default Name",
            connectivityFlow = onlineFlow,
        )

        val hydratedState = withTimeout(TIMEOUT_MS) {
            vm.uiState.first { !it.isLoading && it.students.isNotEmpty() && it.hasSavedProfile }
        }

        assertEquals(1, hydratedState.students.size)
        assertEquals("Rohan Gupta", hydratedState.students.first().name)
        assertEquals(180_000L, hydratedState.dashboardSummary.totalCollectedPaise)
        assertEquals("Simran Tution Classes Cloud", hydratedState.teacherProfile.tuitionName)
        assertFalse(hydratedState.needsInitialCloudRestore)
        assertNotNull(hydratedState.syncStatus)
        assertEquals(CloudSyncStatus.SYNCED, hydratedState.syncStatus)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
    }
}
