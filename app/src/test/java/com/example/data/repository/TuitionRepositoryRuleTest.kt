package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firestore.TuitionRepository
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Student
import com.example.domain.model.TeacherProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TuitionRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addStudentAndPayment_authenticatedTeacher_succeedsAndEmitsViaSnapshots() = runBlocking {
        val uid = signInTestUser("simran_${UUID.randomUUID()}@gmail.com")
        val repository = TuitionRepository(firestore, auth)

        val student = Student(
            name = "Aarav Sharma",
            phone = "9876543210",
            className = "Class 10",
            monthlyFeePaise = 150_000L,
            joiningDate = "2026-10-05",
            joiningYearMonth = "2026-10",
            notes = "Morning batch",
            active = true,
        )

        val studentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addStudent(uid, student).getOrThrow()
        }
        assertTrue(studentId.isNotBlank())

        val students = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(uid).first { list -> list.any { it.id == studentId } }
        }
        val savedStudent = students.first { it.id == studentId }
        assertEquals("Aarav Sharma", savedStudent.name)
        assertEquals(150_000L, savedStudent.monthlyFeePaise)

        // Initial FeeRate should exist for 2026-10
        val rates = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFeeRates(uid).first { list -> list.any { it.studentId == studentId } }
        }
        assertEquals("2026-10", rates.first().effectiveFrom)

        // Add payment
        val payment = Payment(
            studentId = studentId,
            studentNameSnapshot = savedStudent.name,
            amountPaise = 150_000L,
            paymentDate = "2026-10-10",
            forYearMonth = "2026-10",
            method = PaymentMethod.UPI.label,
            note = "October fee",
        )
        val paymentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addPayment(uid, payment).getOrThrow()
        }
        assertTrue(paymentId.isNotBlank())

        val payments = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePayments(uid).first { list -> list.any { it.id == paymentId } }
        }
        assertEquals(150_000L, payments.first { it.id == paymentId }.amountPaise)

        // Update student fee effective 2026-11 preserves 2026-10 feeRate
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateStudent(
                userId = uid,
                existing = savedStudent,
                updated = savedStudent.copy(monthlyFeePaise = 200_000L),
                newFeeEffectiveFrom = "2026-11",
                feeChangeNote = "Revised fee"
            ).getOrThrow()
        }

        val updatedRates = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFeeRates(uid).first { list ->
                list.count { it.studentId == studentId } == 2
            }
        }
        assertEquals(150_000L, updatedRates.first { it.effectiveFrom == "2026-10" }.monthlyFeePaise)
        assertEquals(200_000L, updatedRates.first { it.effectiveFrom == "2026-11" }.monthlyFeePaise)

        // Save teacher profile
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveTeacherProfile(
                uid,
                TeacherProfile(
                    teacherName = "Simran Ma'am",
                    tuitionName = "Simran Tution Classes",
                    teacherEmail = "simran@gmail.com",
                )
            ).getOrThrow()
        }
        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTeacherProfile(uid).first { it != null }
        }
        assertEquals("Simran Tution Classes", profile?.tuitionName)
    }

    @Test
    fun crossUserAccess_otherUser_failsWithPermissionDenied() = runBlocking {
        val ownerUid = signInTestUser("owner_${UUID.randomUUID()}@gmail.com")
        val ownerRepo = TuitionRepository(firestore, auth)
        val studentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.addStudent(
                ownerUid,
                Student(
                    name = "Riya Patel",
                    phone = "",
                    className = "Class 8",
                    monthlyFeePaise = 100_000L,
                    joiningDate = "2026-11-01",
                    joiningYearMonth = "2026-11",
                )
            ).getOrThrow()
        }

        signInTestUser("intruder_${UUID.randomUUID()}@gmail.com")
        val intruderRepo = TuitionRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                intruderRepo.observeStudents(ownerUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsError = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for cross-user read, got $caught", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            intruderRepo.deleteStudent(ownerUid, studentId)
        }
        assertTrue(deleteResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deleteResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun observeStudents_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = TuitionRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeStudents("some_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsError = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for unauthenticated read, got $caught", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
