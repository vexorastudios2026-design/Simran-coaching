package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val className: String = "",
    val monthlyFeePaise: Long = 0L,
    val joiningDate: String = "",
    val joiningYearMonth: String = "",
    val notes: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FeeRate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val effectiveFrom: String = "",
    val monthlyFeePaise: Long = 0L,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentNameSnapshot: String = "",
    val amountPaise: Long = 0L,
    val paymentDate: String = "",
    val forYearMonth: String = "",
    val method: String = PaymentMethod.CASH.label,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val teacherName: String = "",
    val tuitionName: String = "Simran Tution Classes",
    val teacherEmail: String = "",
    val currencyCode: String = "INR",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    CARD("Card"),
    CHEQUE("Cheque"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String): PaymentMethod =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: OTHER
    }
}

enum class FeeStatus(val label: String) {
    PAID("Paid"),
    PARTIAL("Partial"),
    UNPAID("Pending"),
    CREDIT("Credit"),
    NOT_JOINED("Not Joined"),
}
