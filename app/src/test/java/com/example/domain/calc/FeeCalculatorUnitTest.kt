package com.example.domain.calc

import com.example.domain.model.FeeRate
import com.example.domain.model.FeeStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeeCalculatorUnitTest {

    private fun student(
        id: String = "s1",
        name: String = "Ananya",
        feePaise: Long = 150_000L, // ₹1,500
        joiningDate: String = "2026-09-01",
    ) = Student(
        id = id,
        userId = "u1",
        name = name,
        phone = "9876543210",
        className = "Class 9",
        monthlyFeePaise = feePaise,
        joiningDate = joiningDate,
        joiningYearMonth = joiningDate.substring(0, 7),
        active = true,
    )

    @Test
    fun currencyFormatter_formatsIndianRupeesAndParsesDecimalSafely() {
        assertEquals("₹0", CurrencyFormatter.formatInr(0L))
        assertEquals("₹1,500", CurrencyFormatter.formatInr(150_000L))
        assertEquals("₹1,25,000", CurrencyFormatter.formatInr(12_500_000L))
        assertEquals("₹1,250.50", CurrencyFormatter.formatInr(125_050L))

        assertEquals(150_000L, CurrencyFormatter.parseRupeesToPaise("1500"))
        assertEquals(150_050L, CurrencyFormatter.parseRupeesToPaise("1,500.50"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("-500"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("100.123"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("abc"))
    }

    @Test
    fun historicalFeeChanges_doNotOverwriteEarlierMonths() {
        val s = student(feePaise = 200_000L, joiningDate = "2026-09-05")
        val rates = listOf(
            FeeRate(id = "r1", studentId = "s1", effectiveFrom = "2026-09", monthlyFeePaise = 150_000L),
            FeeRate(id = "r2", studentId = "s1", effectiveFrom = "2026-11", monthlyFeePaise = 200_000L),
        )

        assertEquals(0L, FeeCalculator.applicableFeeForMonth(s, rates, "2026-08"))
        assertEquals(150_000L, FeeCalculator.applicableFeeForMonth(s, rates, "2026-09"))
        assertEquals(150_000L, FeeCalculator.applicableFeeForMonth(s, rates, "2026-10"))
        assertEquals(200_000L, FeeCalculator.applicableFeeForMonth(s, rates, "2026-11"))
        assertEquals(200_000L, FeeCalculator.applicableFeeForMonth(s, rates, "2026-12"))

        val (summary, ledger) = FeeCalculator.calculateStudentLedger(
            student = s,
            feeRates = rates,
            payments = emptyList(),
            throughYearMonth = "2026-11",
        )
        // Sep (1500) + Oct (1500) + Nov (2000) = ₹5,000 (500_000 paise)
        assertEquals(500_000L, summary.totalExpectedPaise)
        assertEquals(500_000L, summary.remainingBalancePaise)
        assertEquals(3, ledger.size)
        assertEquals(150_000L, ledger.first { it.yearMonth == "2026-09" }.expectedFeePaise)
        assertEquals(200_000L, ledger.first { it.yearMonth == "2026-11" }.expectedFeePaise)
    }

    @Test
    fun multiplePartialPaymentsInSameMonth_andOverpaymentCreditCarryForward() {
        val s = student(feePaise = 150_000L, joiningDate = "2026-10-01")
        val rates = listOf(
            FeeRate(id = "r1", studentId = "s1", effectiveFrom = "2026-10", monthlyFeePaise = 150_000L)
        )
        val payments = listOf(
            // Two payments in Oct summing to ₹2,000 (₹500 overpayment)
            Payment(
                id = "p1",
                studentId = "s1",
                studentNameSnapshot = "Ananya",
                amountPaise = 100_000L,
                paymentDate = "2026-10-05",
                forYearMonth = "2026-10",
                method = PaymentMethod.CASH.label,
            ),
            Payment(
                id = "p2",
                studentId = "s1",
                studentNameSnapshot = "Ananya",
                amountPaise = 100_000L,
                paymentDate = "2026-10-20",
                forYearMonth = "2026-10",
                method = PaymentMethod.UPI.label,
            ),
            // ₹1,000 paid for Nov -> ₹500 carried credit covers the rest -> Nov fully paid!
            Payment(
                id = "p3",
                studentId = "s1",
                studentNameSnapshot = "Ananya",
                amountPaise = 100_000L,
                paymentDate = "2026-11-05",
                forYearMonth = "2026-11",
                method = PaymentMethod.UPI.label,
            ),
        )

        val (summary, ledger) = FeeCalculator.calculateStudentLedger(s, rates, payments, "2026-11")
        assertEquals(300_000L, summary.totalExpectedPaise)
        assertEquals(300_000L, summary.totalPaidPaise)
        assertEquals(0L, summary.remainingBalancePaise)
        assertEquals(0L, summary.creditBalancePaise)
        assertEquals(FeeStatus.PAID, summary.status)

        val oct = ledger.first { it.yearMonth == "2026-10" }
        assertEquals(200_000L, oct.directPaidPaise)
        assertEquals(50_000L, oct.overpaidCreditPaise)
        assertEquals(FeeStatus.CREDIT, oct.status)

        val nov = ledger.first { it.yearMonth == "2026-11" }
        assertEquals(100_000L, nov.directPaidPaise)
        assertEquals(50_000L, nov.creditAppliedPaise)
        assertEquals(0L, nov.pendingPaise)
        assertEquals(FeeStatus.PAID, nov.status)
    }

    @Test
    fun overpaymentBeyondTotalObligations_showsCreditAndZeroPending() {
        val s = student(feePaise = 150_000L, joiningDate = "2026-11-01")
        val rates = listOf(
            FeeRate(id = "r1", studentId = "s1", effectiveFrom = "2026-11", monthlyFeePaise = 150_000L)
        )
        val payments = listOf(
            Payment(
                id = "p1",
                studentId = "s1",
                studentNameSnapshot = "Ananya",
                amountPaise = 250_000L,
                paymentDate = "2026-11-05",
                forYearMonth = "2026-11",
                method = PaymentMethod.UPI.label,
            )
        )

        val (summary, _) = FeeCalculator.calculateStudentLedger(s, rates, payments, "2026-11")
        assertEquals(150_000L, summary.totalExpectedPaise)
        assertEquals(250_000L, summary.totalPaidPaise)
        assertEquals(0L, summary.remainingBalancePaise)
        assertEquals(100_000L, summary.creditBalancePaise)
        assertEquals(FeeStatus.CREDIT, summary.status)
    }

    @Test
    fun dashboardTotals_matchStudentRecordsWithZeroDoubleCounting() {
        val s1 = student(id = "s1", name = "Aarav", feePaise = 150_000L, joiningDate = "2026-10-01")
        val s2 = student(id = "s2", name = "Meera", feePaise = 200_000L, joiningDate = "2026-11-01")
        val rates = listOf(
            FeeRate(id = "r1", studentId = "s1", effectiveFrom = "2026-10", monthlyFeePaise = 150_000L),
            FeeRate(id = "r2", studentId = "s2", effectiveFrom = "2026-11", monthlyFeePaise = 200_000L),
        )
        val payments = listOf(
            Payment(id = "p1", studentId = "s1", studentNameSnapshot = "Aarav", amountPaise = 150_000L, paymentDate = "2026-10-10", forYearMonth = "2026-10"),
            Payment(id = "p2", studentId = "s1", studentNameSnapshot = "Aarav", amountPaise = 150_000L, paymentDate = "2026-11-10", forYearMonth = "2026-11"),
            Payment(id = "p3", studentId = "s2", studentNameSnapshot = "Meera", amountPaise = 80_000L, paymentDate = "2026-11-12", forYearMonth = "2026-11"),
        )

        val dashboard = FeeCalculator.calculateDashboardSummary(listOf(s1, s2), rates, payments, "2026-11")
        assertEquals(2, dashboard.totalStudents)
        // s1: 2*1500 = 3000; s2: 1*2000 = 2000 -> total expected 5000 (500_000)
        assertEquals(500_000L, dashboard.totalExpectedPaise)
        // collected = 1500 + 1500 + 800 = 3800 (380_000)
        assertEquals(380_000L, dashboard.totalCollectedPaise)
        // pending = 0 + 1200 = 1200 (120_000)
        assertEquals(120_000L, dashboard.totalPendingPaise)
        assertEquals(1, dashboard.fullyPaidStudentsCount)
        assertEquals(1, dashboard.pendingStudentsCount)
        // Current month (Nov 2026) collection = 1500 + 800 = 2300 (230_000)
        assertEquals(230_000L, dashboard.currentMonthCollectionPaise)
    }
}
