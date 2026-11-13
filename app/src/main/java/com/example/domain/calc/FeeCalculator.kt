package com.example.domain.calc

import com.example.domain.model.FeeRate
import com.example.domain.model.FeeStatus
import com.example.domain.model.Payment
import com.example.domain.model.Student

data class StudentFeeSummary(
    val student: Student,
    val totalExpectedPaise: Long,
    val totalPaidPaise: Long,
    val remainingBalancePaise: Long,
    val creditBalancePaise: Long,
    val currentMonthFeePaise: Long,
    val currentMonthPaidPaise: Long,
    val status: FeeStatus,
    val monthsEnrolledCount: Int,
)

data class MonthlyStudentStatus(
    val student: Student,
    val yearMonth: String,
    val expectedFeePaise: Long,
    val directPaidPaise: Long,
    val creditAppliedPaise: Long,
    val effectivePaidPaise: Long,
    val pendingPaise: Long,
    val overpaidCreditPaise: Long,
    val status: FeeStatus,
)

data class MonthlySummary(
    val yearMonth: String,
    val expectedTotalPaise: Long,
    val collectedTotalPaise: Long,
    val creditAppliedTotalPaise: Long,
    val pendingTotalPaise: Long,
    val overpaidCreditTotalPaise: Long,
    val paidCount: Int,
    val partialCount: Int,
    val unpaidCount: Int,
    val studentStatuses: List<MonthlyStudentStatus>,
)

data class DashboardSummary(
    val totalStudents: Int,
    val activeStudents: Int,
    val totalExpectedPaise: Long,
    val totalCollectedPaise: Long,
    val totalPendingPaise: Long,
    val totalCreditPaise: Long,
    val fullyPaidStudentsCount: Int,
    val pendingStudentsCount: Int,
    val currentMonthCollectionPaise: Long,
    val currentMonthExpectedPaise: Long,
    val currentMonthPendingPaise: Long,
    val recentPayments: List<Payment>,
)

data class MonthlyReportRow(
    val yearMonth: String,
    val expectedPaise: Long,
    val collectedPaise: Long,
    val pendingPaise: Long,
    val paymentCount: Int,
)

data class YearlyReport(
    val year: Int,
    val totalExpectedPaise: Long,
    val totalCollectedPaise: Long,
    val totalPendingPaise: Long,
    val monthlyRows: List<MonthlyReportRow>,
)

object FeeCalculator {

    /**
     * Resolves the applicable monthly fee in paise for [student] in [yearMonth],
     * preserving historical rates whenever a fee changes in a later month.
     */
    fun applicableFeeForMonth(
        student: Student,
        feeRates: List<FeeRate>,
        yearMonth: String,
    ): Long {
        if (student.joiningYearMonth.isNotBlank() && yearMonth < student.joiningYearMonth) {
            return 0L
        }
        val studentRates = feeRates
            .filter { it.studentId == student.id && it.effectiveFrom.isNotBlank() }
            .sortedBy { it.effectiveFrom }

        if (studentRates.isEmpty()) {
            return student.monthlyFeePaise.coerceAtLeast(0L)
        }

        val latestApplicable = studentRates.lastOrNull { it.effectiveFrom <= yearMonth }
        return (latestApplicable ?: studentRates.first()).monthlyFeePaise.coerceAtLeast(0L)
    }

    /**
     * Computes chronological monthly ledger and overall balance for a single student
     * through [throughYearMonth] (plus any future month that already has payments).
     */
    fun calculateStudentLedger(
        student: Student,
        feeRates: List<FeeRate>,
        payments: List<Payment>,
        throughYearMonth: String = YearMonthUtil.currentYearMonth(),
    ): Pair<StudentFeeSummary, List<MonthlyStudentStatus>> {
        val studentPayments = payments
            .filter { it.studentId == student.id && it.amountPaise > 0L }
        val paymentsByMonth = studentPayments.groupBy { it.forYearMonth }

        val joinYm = student.joiningYearMonth.takeIf { YearMonthUtil.parseYearMonth(it) != null }
            ?: throughYearMonth
        val earliestPaymentYm = studentPayments.mapNotNull {
            it.forYearMonth.takeIf { ym -> YearMonthUtil.parseYearMonth(ym) != null }
        }.minOrNull()
        val latestPaymentYm = studentPayments.mapNotNull {
            it.forYearMonth.takeIf { ym -> YearMonthUtil.parseYearMonth(ym) != null }
        }.maxOrNull()

        val startYm = if (earliestPaymentYm != null && earliestPaymentYm < joinYm) earliestPaymentYm else joinYm
        val endYm = if (latestPaymentYm != null && latestPaymentYm > throughYearMonth) latestPaymentYm else throughYearMonth

        val allMonths = YearMonthUtil.monthsBetweenInclusive(startYm, endYm)

        var runningCreditPool = 0L
        val monthlyStatuses = mutableListOf<MonthlyStudentStatus>()

        for (ym in allMonths) {
            val isEnrolled = ym >= joinYm && ym <= throughYearMonth
            val expected = if (isEnrolled) applicableFeeForMonth(student, feeRates, ym) else 0L
            val directPaid = paymentsByMonth[ym]?.sumOf { it.amountPaise } ?: 0L

            val shortfall = (expected - directPaid).coerceAtLeast(0L)
            val creditApplied = minOf(runningCreditPool, shortfall)
            runningCreditPool -= creditApplied

            val effectivePaid = directPaid + creditApplied
            val pending = (expected - effectivePaid).coerceAtLeast(0L)
            val surplusFromMonth = (directPaid - expected).coerceAtLeast(0L)
            runningCreditPool += surplusFromMonth

            val status = when {
                !isEnrolled && directPaid > 0L -> FeeStatus.CREDIT
                !isEnrolled -> FeeStatus.NOT_JOINED
                surplusFromMonth > 0L -> FeeStatus.CREDIT
                pending == 0L -> FeeStatus.PAID
                effectivePaid > 0L -> FeeStatus.PARTIAL
                else -> FeeStatus.UNPAID
            }

            monthlyStatuses.add(
                MonthlyStudentStatus(
                    student = student,
                    yearMonth = ym,
                    expectedFeePaise = expected,
                    directPaidPaise = directPaid,
                    creditAppliedPaise = creditApplied,
                    effectivePaidPaise = effectivePaid,
                    pendingPaise = pending,
                    overpaidCreditPaise = surplusFromMonth,
                    status = status,
                )
            )
        }

        val enrolledMonths = YearMonthUtil.monthsBetweenInclusive(joinYm, throughYearMonth)
        val totalExpected = enrolledMonths.sumOf { ym -> applicableFeeForMonth(student, feeRates, ym) }
        val totalPaid = studentPayments.sumOf { it.amountPaise }
        val remainingBalance = (totalExpected - totalPaid).coerceAtLeast(0L)
        val creditBalance = (totalPaid - totalExpected).coerceAtLeast(0L)

        val currentMonthFee = applicableFeeForMonth(student, feeRates, throughYearMonth)
        val currentMonthPaid = paymentsByMonth[throughYearMonth]?.sumOf { it.amountPaise } ?: 0L

        val overallStatus = when {
            creditBalance > 0L -> FeeStatus.CREDIT
            remainingBalance == 0L -> FeeStatus.PAID
            totalPaid > 0L -> FeeStatus.PARTIAL
            else -> FeeStatus.UNPAID
        }

        val summary = StudentFeeSummary(
            student = student,
            totalExpectedPaise = totalExpected,
            totalPaidPaise = totalPaid,
            remainingBalancePaise = remainingBalance,
            creditBalancePaise = creditBalance,
            currentMonthFeePaise = currentMonthFee,
            currentMonthPaidPaise = currentMonthPaid,
            status = overallStatus,
            monthsEnrolledCount = enrolledMonths.size,
        )

        return summary to monthlyStatuses
    }

    fun calculateMonthlySummary(
        students: List<Student>,
        feeRates: List<FeeRate>,
        payments: List<Payment>,
        yearMonth: String,
        currentYearMonth: String = YearMonthUtil.currentYearMonth(),
    ): MonthlySummary {
        val throughYm = maxOf(yearMonth, currentYearMonth)
        val statuses = students
            .filter { student ->
                val joinYm = student.joiningYearMonth.ifBlank { currentYearMonth }
                joinYm <= yearMonth
            }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { student ->
                val (_, ledger) = calculateStudentLedger(student, feeRates, payments, throughYm)
                val match = ledger.firstOrNull { it.yearMonth == yearMonth }
                if (match != null) {
                    // For future months (yearMonth > currentYearMonth), calculate expected fee directly
                    if (yearMonth > currentYearMonth) {
                        val expected = applicableFeeForMonth(student, feeRates, yearMonth)
                        val directPaid = payments
                            .filter { it.studentId == student.id && it.forYearMonth == yearMonth && it.amountPaise > 0L }
                            .sumOf { it.amountPaise }
                        val pending = (expected - directPaid).coerceAtLeast(0L)
                        val surplus = (directPaid - expected).coerceAtLeast(0L)
                        val st = when {
                            surplus > 0L -> FeeStatus.CREDIT
                            pending == 0L -> FeeStatus.PAID
                            directPaid > 0L -> FeeStatus.PARTIAL
                            else -> FeeStatus.UNPAID
                        }
                        match.copy(
                            expectedFeePaise = expected,
                            pendingPaise = pending,
                            overpaidCreditPaise = surplus,
                            status = st,
                        )
                    } else {
                        match
                    }
                } else {
                    null
                }
            }

        return MonthlySummary(
            yearMonth = yearMonth,
            expectedTotalPaise = statuses.sumOf { it.expectedFeePaise },
            collectedTotalPaise = statuses.sumOf { it.directPaidPaise },
            creditAppliedTotalPaise = statuses.sumOf { it.creditAppliedPaise },
            pendingTotalPaise = statuses.sumOf { it.pendingPaise },
            overpaidCreditTotalPaise = statuses.sumOf { it.overpaidCreditPaise },
            paidCount = statuses.count { it.status == FeeStatus.PAID || it.status == FeeStatus.CREDIT },
            partialCount = statuses.count { it.status == FeeStatus.PARTIAL },
            unpaidCount = statuses.count { it.status == FeeStatus.UNPAID },
            studentStatuses = statuses,
        )
    }

    fun calculateDashboardSummary(
        students: List<Student>,
        feeRates: List<FeeRate>,
        payments: List<Payment>,
        currentYearMonth: String = YearMonthUtil.currentYearMonth(),
    ): DashboardSummary {
        val validPayments = payments.filter { it.amountPaise > 0L }
        val summaries = students.map { student ->
            calculateStudentLedger(student, feeRates, validPayments, currentYearMonth).first
        }

        val studentIdSet = students.map { it.id }.toSet()
        // Total collected equals sum of all valid payments belonging to existing students
        // (matching student summaries exactly with zero double counting).
        val totalExpected = summaries.sumOf { it.totalExpectedPaise }
        val totalCollected = summaries.sumOf { it.totalPaidPaise }
        val totalPending = summaries.sumOf { it.remainingBalancePaise }
        val totalCredit = summaries.sumOf { it.creditBalancePaise }

        val monthlySummary = calculateMonthlySummary(
            students = students,
            feeRates = feeRates,
            payments = validPayments,
            yearMonth = currentYearMonth,
            currentYearMonth = currentYearMonth,
        )

        val recent = validPayments
            .filter { it.studentId in studentIdSet }
            .sortedWith(
                compareByDescending<Payment> { it.paymentDate }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
            )
            .take(8)

        return DashboardSummary(
            totalStudents = students.size,
            activeStudents = students.count { it.active },
            totalExpectedPaise = totalExpected,
            totalCollectedPaise = totalCollected,
            totalPendingPaise = totalPending,
            totalCreditPaise = totalCredit,
            fullyPaidStudentsCount = summaries.count { it.remainingBalancePaise == 0L },
            pendingStudentsCount = summaries.count { it.remainingBalancePaise > 0L },
            currentMonthCollectionPaise = monthlySummary.collectedTotalPaise,
            currentMonthExpectedPaise = monthlySummary.expectedTotalPaise,
            currentMonthPendingPaise = monthlySummary.pendingTotalPaise,
            recentPayments = recent,
        )
    }

    fun calculateYearlyReport(
        year: Int,
        students: List<Student>,
        feeRates: List<FeeRate>,
        payments: List<Payment>,
        currentYearMonth: String = YearMonthUtil.currentYearMonth(),
    ): YearlyReport {
        val validPayments = payments.filter { it.amountPaise > 0L }
        val rows = (1..12).map { month ->
            val ym = YearMonthUtil.formatYearMonth(year, month)
            val includeObligations = ym <= currentYearMonth
            val monthly = calculateMonthlySummary(
                students = students,
                feeRates = feeRates,
                payments = validPayments,
                yearMonth = ym,
                currentYearMonth = currentYearMonth,
            )
            val monthPaymentsCount = validPayments.count { it.forYearMonth == ym }
            MonthlyReportRow(
                yearMonth = ym,
                expectedPaise = if (includeObligations) monthly.expectedTotalPaise else 0L,
                collectedPaise = monthly.collectedTotalPaise,
                pendingPaise = if (includeObligations) monthly.pendingTotalPaise else 0L,
                paymentCount = monthPaymentsCount,
            )
        }

        return YearlyReport(
            year = year,
            totalExpectedPaise = rows.sumOf { it.expectedPaise },
            totalCollectedPaise = rows.sumOf { it.collectedPaise },
            totalPendingPaise = rows.sumOf { it.pendingPaise },
            monthlyRows = rows,
        )
    }
}
