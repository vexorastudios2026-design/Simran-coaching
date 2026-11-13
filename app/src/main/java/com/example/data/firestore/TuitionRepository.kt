package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.example.R
import com.example.domain.model.FeeRate
import com.example.domain.model.Payment
import com.example.domain.model.Student
import com.example.domain.model.TeacherProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

data class CloudSnapshot<T>(
    val data: T,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean,
)

enum class WriteSyncOutcome {
    SYNCED_TO_CLOUD,
    QUEUED_OFFLINE,
}

data class WriteAckResult(
    val documentId: String = "",
    val outcome: WriteSyncOutcome = WriteSyncOutcome.SYNCED_TO_CLOUD,
)

object FirestoreProvider {
    private val settingsConfigured = AtomicBoolean(false)

    fun getDatabase(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (settingsConfigured.compareAndSet(false, true)) {
            try {
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
            } catch (e: IllegalStateException) {
                // Firestore instance was already started; persistent cache remains active by default.
                Log.d("FirestoreProvider", "Firestore settings already initialized: ${e.message}")
            }
        }
        return db
    }
}

class TuitionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val isOnlineProvider: () -> Boolean = { true },
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getDatabase(context),
        auth = Firebase.auth,
    )

    private fun studentsCol(userId: String) =
        db.collection("users").document(userId).collection("students")

    private fun feeRatesCol(userId: String) =
        db.collection("users").document(userId).collection("feeRates")

    private fun paymentsCol(userId: String) =
        db.collection("users").document(userId).collection("payments")

    private fun settingsDoc(userId: String) =
        db.collection("users").document(userId).collection("settings").document("profile")

    fun observeStudentsSnapshot(userId: String): Flow<CloudSnapshot<List<Student>>> = flow {
        val col = studentsCol(userId)
        emitAll(
            col.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                    CloudSnapshot(
                        data = items,
                        isFromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(userId, error, OperationType.LIST, col.path)
                    throw error
                }
        )
    }

    fun observeFeeRatesSnapshot(userId: String): Flow<CloudSnapshot<List<FeeRate>>> = flow {
        val col = feeRatesCol(userId)
        emitAll(
            col.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        FeeRate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    CloudSnapshot(
                        data = items,
                        isFromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(userId, error, OperationType.LIST, col.path)
                    throw error
                }
        )
    }

    fun observePaymentsSnapshot(userId: String): Flow<CloudSnapshot<List<Payment>>> = flow {
        val col = paymentsCol(userId)
        emitAll(
            col.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        Payment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<Payment> { it.paymentDate }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                    CloudSnapshot(
                        data = items,
                        isFromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(userId, error, OperationType.LIST, col.path)
                    throw error
                }
        )
    }

    fun observeTeacherProfileSnapshot(userId: String): Flow<CloudSnapshot<TeacherProfile?>> = flow {
        val doc = settingsDoc(userId)
        emitAll(
            doc.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val item = if (snapshot.exists()) {
                        snapshot.toObject(
                            TeacherProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                    CloudSnapshot(
                        data = item,
                        isFromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(userId, error, OperationType.GET, doc.path)
                    throw error
                }
        )
    }

    fun observeStudents(userId: String): Flow<List<Student>> =
        observeStudentsSnapshot(userId).map { it.data }

    fun observeFeeRates(userId: String): Flow<List<FeeRate>> =
        observeFeeRatesSnapshot(userId).map { it.data }

    fun observePayments(userId: String): Flow<List<Payment>> =
        observePaymentsSnapshot(userId).map { it.data }

    fun observeTeacherProfile(userId: String): Flow<TeacherProfile?> =
        observeTeacherProfileSnapshot(userId).map { it.data }

    suspend fun syncWithServer(userId: String): Result<Unit> = try {
        withTimeoutOrNull(SERVER_SYNC_TIMEOUT_MS) {
            db.waitForPendingWrites().await()
        }
        studentsCol(userId).get(Source.SERVER).await()
        feeRatesCol(userId).get(Source.SERVER).await()
        paymentsCol(userId).get(Source.SERVER).await()
        settingsDoc(userId).get(Source.SERVER).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "users/$userId")
        Result.failure(e)
    }

    suspend fun addStudent(userId: String, student: Student): Result<String> =
        addStudentWithOutcome(userId, student).map { it.documentId }

    suspend fun addStudentWithOutcome(userId: String, student: Student): Result<WriteAckResult> {
        val studentRef = studentsCol(userId).document()
        val rateId = "${studentRef.id}_${student.joiningYearMonth}"
        val rateRef = feeRatesCol(userId).document(rateId)

        val studentPayload = mapOf(
            "userId" to userId,
            "name" to student.name.trim(),
            "phone" to student.phone.trim(),
            "className" to student.className.trim(),
            "monthlyFeePaise" to student.monthlyFeePaise,
            "joiningDate" to student.joiningDate,
            "joiningYearMonth" to student.joiningYearMonth,
            "notes" to student.notes.trim(),
            "active" to student.active,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        val ratePayload = mapOf(
            "userId" to userId,
            "studentId" to studentRef.id,
            "effectiveFrom" to student.joiningYearMonth,
            "monthlyFeePaise" to student.monthlyFeePaise,
            "note" to "Initial monthly fee",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        return try {
            val batch = db.batch()
            batch.set(studentRef, studentPayload)
            batch.set(rateRef, ratePayload)
            val outcome = awaitWriteTask(batch.commit())
            Result.success(WriteAckResult(documentId = studentRef.id, outcome = outcome))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, studentRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateStudent(
        userId: String,
        existing: Student,
        updated: Student,
        newFeeEffectiveFrom: String?,
        feeChangeNote: String = "Updated monthly fee",
    ): Result<Unit> =
        updateStudentWithOutcome(userId, existing, updated, newFeeEffectiveFrom, feeChangeNote).map { }

    suspend fun updateStudentWithOutcome(
        userId: String,
        existing: Student,
        updated: Student,
        newFeeEffectiveFrom: String?,
        feeChangeNote: String = "Updated monthly fee",
    ): Result<WriteSyncOutcome> {
        require(existing.id.isNotBlank()) { "Student ID must not be blank" }
        val studentRef = studentsCol(userId).document(existing.id)
        val trimmedName = updated.name.trim()

        return try {
            val batch = db.batch()
            batch.update(
                studentRef,
                mapOf(
                    "name" to trimmedName,
                    "phone" to updated.phone.trim(),
                    "className" to updated.className.trim(),
                    "monthlyFeePaise" to updated.monthlyFeePaise,
                    "joiningDate" to updated.joiningDate,
                    "joiningYearMonth" to updated.joiningYearMonth,
                    "notes" to updated.notes.trim(),
                    "active" to updated.active,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )

            // Record historical effective fee rate when monthly fee changed (without overwriting past months)
            if (updated.monthlyFeePaise != existing.monthlyFeePaise && !newFeeEffectiveFrom.isNullOrBlank()) {
                val rateId = "${existing.id}_${newFeeEffectiveFrom}"
                val rateRef = feeRatesCol(userId).document(rateId)
                val existingRate = getWithCacheFallback(rateRef)
                if (existingRate?.exists() == true) {
                    batch.update(
                        rateRef,
                        mapOf(
                            "monthlyFeePaise" to updated.monthlyFeePaise,
                            "note" to feeChangeNote.trim().take(200),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                } else {
                    batch.set(
                        rateRef,
                        mapOf(
                            "userId" to userId,
                            "studentId" to existing.id,
                            "effectiveFrom" to newFeeEffectiveFrom,
                            "monthlyFeePaise" to updated.monthlyFeePaise,
                            "note" to feeChangeNote.trim().take(200),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }

            // Keep studentNameSnapshot in sync on existing payments if renamed
            if (trimmedName != existing.name.trim()) {
                val query = paymentsCol(userId).whereEqualTo("studentId", existing.id)
                val studentPayments = if (isOnlineProvider()) {
                    try {
                        query.get(Source.DEFAULT).await()
                    } catch (_: Exception) {
                        query.get(Source.CACHE).await()
                    }
                } else {
                    query.get(Source.CACHE).await()
                }
                for (doc in studentPayments.documents) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "studentNameSnapshot" to trimmedName,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }

            val outcome = awaitWriteTask(batch.commit())
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, studentRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> =
        deleteStudentWithOutcome(userId, studentId).map { }

    suspend fun deleteStudentWithOutcome(userId: String, studentId: String): Result<WriteSyncOutcome> {
        require(studentId.isNotBlank()) { "Student ID must not be blank" }
        val studentRef = studentsCol(userId).document(studentId)
        return try {
            val source = if (isOnlineProvider()) Source.DEFAULT else Source.CACHE
            val rates = feeRatesCol(userId).whereEqualTo("studentId", studentId).get(source).await()
            val pays = paymentsCol(userId).whereEqualTo("studentId", studentId).get(source).await()
            val refsToDelete = buildList {
                addAll(pays.documents.map { it.reference })
                addAll(rates.documents.map { it.reference })
                add(studentRef)
            }
            var overallOutcome = WriteSyncOutcome.SYNCED_TO_CLOUD
            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                val outcome = awaitWriteTask(batch.commit())
                if (outcome == WriteSyncOutcome.QUEUED_OFFLINE) {
                    overallOutcome = WriteSyncOutcome.QUEUED_OFFLINE
                }
            }
            Result.success(overallOutcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, studentRef.path)
            Result.failure(e)
        }
    }

    suspend fun addPayment(userId: String, payment: Payment): Result<String> =
        addPaymentWithOutcome(userId, payment).map { it.documentId }

    suspend fun addPaymentWithOutcome(userId: String, payment: Payment): Result<WriteAckResult> {
        require(payment.amountPaise > 0L) { "Payment amount must be greater than zero" }
        require(payment.studentId.isNotBlank()) { "Student ID must not be blank" }

        val paymentRef = paymentsCol(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "studentId" to payment.studentId,
            "studentNameSnapshot" to payment.studentNameSnapshot.trim(),
            "amountPaise" to payment.amountPaise,
            "paymentDate" to payment.paymentDate,
            "forYearMonth" to payment.forYearMonth,
            "method" to payment.method,
            "note" to payment.note.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            val outcome = awaitWriteTask(paymentRef.set(payload))
            Result.success(WriteAckResult(documentId = paymentRef.id, outcome = outcome))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, paymentRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePayment(userId: String, paymentId: String, updated: Payment): Result<Unit> =
        updatePaymentWithOutcome(userId, paymentId, updated).map { }

    suspend fun updatePaymentWithOutcome(
        userId: String,
        paymentId: String,
        updated: Payment,
    ): Result<WriteSyncOutcome> {
        require(paymentId.isNotBlank()) { "Payment ID must not be blank" }
        require(updated.amountPaise > 0L) { "Payment amount must be greater than zero" }

        val paymentRef = paymentsCol(userId).document(paymentId)
        return try {
            val outcome = awaitWriteTask(
                paymentRef.update(
                    mapOf(
                        "studentNameSnapshot" to updated.studentNameSnapshot.trim(),
                        "amountPaise" to updated.amountPaise,
                        "paymentDate" to updated.paymentDate,
                        "forYearMonth" to updated.forYearMonth,
                        "method" to updated.method,
                        "note" to updated.note.trim(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            )
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, paymentRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePayment(userId: String, paymentId: String): Result<Unit> =
        deletePaymentWithOutcome(userId, paymentId).map { }

    suspend fun deletePaymentWithOutcome(userId: String, paymentId: String): Result<WriteSyncOutcome> {
        require(paymentId.isNotBlank()) { "Payment ID must not be blank" }
        val paymentRef = paymentsCol(userId).document(paymentId)
        return try {
            val outcome = awaitWriteTask(paymentRef.delete())
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, paymentRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveTeacherProfile(
        userId: String,
        profile: TeacherProfile,
        knownExists: Boolean? = null,
    ): Result<Unit> =
        saveTeacherProfileWithOutcome(userId, profile, knownExists).map { }

    suspend fun saveTeacherProfileWithOutcome(
        userId: String,
        profile: TeacherProfile,
        knownExists: Boolean? = null,
    ): Result<WriteSyncOutcome> {
        val docRef = settingsDoc(userId)
        return try {
            val existsOnCloud = knownExists ?: (getWithCacheFallback(docRef)?.exists() == true)
            val task = if (existsOnCloud) {
                docRef.update(
                    mapOf(
                        "teacherName" to profile.teacherName.trim().ifBlank { "Ma'am" },
                        "tuitionName" to profile.tuitionName.trim().ifBlank { "Simran Tution Classes" },
                        "teacherEmail" to profile.teacherEmail.trim(),
                        "currencyCode" to "INR",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "teacherName" to profile.teacherName.trim().ifBlank { "Ma'am" },
                        "tuitionName" to profile.tuitionName.trim().ifBlank { "Simran Tution Classes" },
                        "teacherEmail" to profile.teacherEmail.trim(),
                        "currencyCode" to "INR",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            val outcome = awaitWriteTask(task)
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    private suspend fun getWithCacheFallback(docRef: com.google.firebase.firestore.DocumentReference): DocumentSnapshot? {
        if (!isOnlineProvider()) {
            return runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        }
        return runCatching {
            withTimeoutOrNull(SERVER_LOOKUP_TIMEOUT_MS) {
                docRef.get(Source.DEFAULT).await()
            } ?: docRef.get(Source.CACHE).await()
        }.getOrElse {
            runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        }
    }

    /**
     * Awaits server acknowledgement when online. If offline (or network drops mid-write),
     * Firestore has already durably queued the mutation in its local persistent cache,
     * so we return [WriteSyncOutcome.QUEUED_OFFLINE] rather than hanging the UI indefinitely.
     */
    private suspend fun awaitWriteTask(task: Task<Void>): WriteSyncOutcome {
        val online = isOnlineProvider()
        val waitMs = if (online) ONLINE_WRITE_ACK_TIMEOUT_MS else OFFLINE_WRITE_GRACE_MS
        val completed = withTimeoutOrNull(waitMs) {
            task.await()
            true
        }
        return if (completed == true) {
            WriteSyncOutcome.SYNCED_TO_CLOUD
        } else {
            WriteSyncOutcome.QUEUED_OFFLINE
        }
    }

    private fun logStreamErrorIfSignedIn(
        userId: String,
        error: Throwable,
        operationType: OperationType,
        path: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
        if (auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private companion object {
        const val ONLINE_WRITE_ACK_TIMEOUT_MS = 8000L
        const val OFFLINE_WRITE_GRACE_MS = 450L
        const val SERVER_LOOKUP_TIMEOUT_MS = 4000L
        const val SERVER_SYNC_TIMEOUT_MS = 8000L
    }
}
