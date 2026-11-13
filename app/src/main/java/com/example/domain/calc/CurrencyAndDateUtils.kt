package com.example.domain.calc

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private const val MAX_FEE_OR_PAYMENT_PAISE = 100_000_000L // ₹10,00,000

    fun formatInr(paise: Long): String {
        val negative = paise < 0L
        val absPaise = if (negative) -paise else paise
        val rupees = absPaise / 100L
        val remPaise = absPaise % 100L

        val formattedRupees = formatIndianInteger(rupees)
        val amountStr = if (remPaise == 0L) {
            "₹$formattedRupees"
        } else {
            "₹$formattedRupees.${remPaise.toString().padStart(2, '0')}"
        }
        return if (negative) "-$amountStr" else amountStr
    }

    fun formatIndianInteger(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str
        val lastThree = str.substring(str.length - 3)
        val remaining = str.substring(0, str.length - 3)
        val groups = StringBuilder()
        var i = remaining.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (groups.isNotEmpty()) groups.insert(0, ',')
            groups.insert(0, remaining.substring(start, i))
            i = start
        }
        return "$groups,$lastThree"
    }

    fun parseRupeesToPaise(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO) return null
            if (bd.scale() > 2) return null
            val paiseBd = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY)
            val paise = paiseBd.longValueExact()
            if (paise > MAX_FEE_OR_PAYMENT_PAISE) return null
            paise
        } catch (_: Exception) {
            null
        }
    }

    fun paiseToInputString(paise: Long): String {
        if (paise <= 0L) return if (paise == 0L) "0" else ""
        val rupees = paise / 100L
        val rem = paise % 100L
        return if (rem == 0L) {
            rupees.toString()
        } else {
            "$rupees.${rem.toString().padStart(2, '0')}"
        }
    }
}

object YearMonthUtil {
    private val monthNamesFull = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val monthNamesShort = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun currentYearMonth(): String {
        val cal = Calendar.getInstance()
        return formatYearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun currentIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun formatYearMonth(year: Int, month: Int): String {
        val clampedMonth = month.coerceIn(1, 12)
        return String.format(Locale.US, "%04d-%02d", year, clampedMonth)
    }

    fun parseYearMonth(ym: String): Pair<Int, Int>? {
        val match = Regex("^(\\d{4})-(\\d{2})$").matchEntire(ym.trim()) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        if (year < 2000 || year > 2100 || month !in 1..12) return null
        return year to month
    }

    fun isValidIsoDate(dateStr: String): Boolean {
        val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(dateStr.trim()) ?: return false
        val year = match.groupValues[1].toIntOrNull() ?: return false
        val month = match.groupValues[2].toIntOrNull() ?: return false
        val day = match.groupValues[3].toIntOrNull() ?: return false
        if (year < 2000 || year > 2100 || month !in 1..12) return false
        val cal = Calendar.getInstance().apply {
            isLenient = false
            set(year, month - 1, day)
        }
        return try {
            cal.time
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isoDateToYearMonth(isoDate: String): String? {
        if (!isValidIsoDate(isoDate)) return null
        return isoDate.trim().substring(0, 7)
    }

    fun monthsBetweenInclusive(startYm: String, endYm: String): List<String> {
        val start = parseYearMonth(startYm) ?: return emptyList()
        val end = parseYearMonth(endYm) ?: return emptyList()
        var y = start.first
        var m = start.second
        if (y > end.first || (y == end.first && m > end.second)) return emptyList()

        val result = mutableListOf<String>()
        while (y < end.first || (y == end.first && m <= end.second)) {
            result.add(formatYearMonth(y, m))
            m++
            if (m > 12) {
                m = 1
                y++
            }
            if (result.size > 240) break
        }
        return result
    }

    fun shiftYearMonth(ym: String, deltaMonths: Int): String {
        val parsed = parseYearMonth(ym) ?: return ym
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, parsed.first)
            set(Calendar.MONTH, parsed.second - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return formatYearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun displayMonthYear(ym: String, shortMonth: Boolean = false): String {
        val parsed = parseYearMonth(ym) ?: return ym
        val name = if (shortMonth) monthNamesShort[parsed.second - 1] else monthNamesFull[parsed.second - 1]
        return "$name ${parsed.first}"
    }

    fun displayMonthOnly(month: Int, shortMonth: Boolean = false): String {
        val idx = (month - 1).coerceIn(0, 11)
        return if (shortMonth) monthNamesShort[idx] else monthNamesFull[idx]
    }

    fun displayDate(isoDate: String): String {
        if (!isValidIsoDate(isoDate)) return isoDate
        val parts = isoDate.split("-")
        val year = parts[0]
        val month = parts[1].toIntOrNull()?.coerceIn(1, 12) ?: 1
        val day = parts[2].toIntOrNull() ?: 1
        return "$day ${monthNamesShort[month - 1]} $year"
    }
}
