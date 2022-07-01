package org.thunderdog.challegram.payments

import java.util.*

// Ported tools to validate cards from Stripe SDK
object CardValidators {
  enum class CardBrand(
    val humanName: String,
    val maxLength: Int,
    val cvcLength: Int,
    val prefixes: Array<String>
  ) {
    AmericanExpress("American Express", 15, 4, arrayOf("34", "37")),
    Discover("Discover", 16, 3, arrayOf("60", "62", "64", "65")),
    JCB("JCB", 16, 3, arrayOf("35")),
    DinersClub("Diners Club", 14, 3, arrayOf("300", "301", "302", "303", "304", "305", "309", "36", "38", "39")),
    Visa("Visa", 16, 3, arrayOf("4")),
    MasterCard("MasterCard", 16, 3, arrayOf("2221", "2222", "2223", "2224", "2225", "2226", "2227", "2228", "2229", "223", "224", "225", "226", "227", "228", "229", "23", "24", "25", "26", "270", "271", "2720", "50", "51", "52", "53", "54", "55"));
  }

  private fun isLuhnValid (number: String): Boolean {
    var checksum = 0

    for (i in number.length - 1 downTo 0 step 2) {
      checksum += number[i] - '0'
    }

    for (i in number.length - 2 downTo 0 step 2) {
      val n: Int = (number[i] - '0') * 2
      checksum += if (n > 9) n - 9 else n
    }

    return checksum % 10 == 0
  }

  private fun guessCardBrand (number: String): CardBrand? {
    CardBrand.values().forEach { brand ->
      brand.prefixes.forEach { prefix ->
        if (number.startsWith(prefix)) return brand
      }
    }

    return null
  }

  private fun normalizeYear (year: Int): Int = if (year in 0..99) {
    String.format(Locale.US, "%s%02d", Calendar.getInstance().get(Calendar.YEAR).toString().let { currentYear -> currentYear.substring(0, currentYear.length - 2) }, year).toInt()
  } else {
    year
  }

  private fun hasYearPassed (year: Int) = normalizeYear(year) < Calendar.getInstance().get(Calendar.YEAR)

  private fun hasMonthPassed (year: Int, month: Int): Boolean {
    if (hasYearPassed(year)) return true
    val now = Calendar.getInstance()
    return normalizeYear(year) == now.get(Calendar.YEAR) && month < (now.get(Calendar.MONTH) + 1)
  }

  fun validateCardNumber(number: String?): Boolean {
    if (number.isNullOrEmpty() || !isLuhnValid(number)) return false
    return number.length == (guessCardBrand(number)?.maxLength ?: return false)
  }

  fun validateCvc (number: String?, cvc: String?): Boolean {
    if (number.isNullOrEmpty() || cvc.isNullOrEmpty()) return false
    return cvc.length == (guessCardBrand(number)?.cvcLength ?: return false)
  }

  fun validateExpiryDate (month: Int, year: Int): Boolean {
    return month in 1..12 && !hasYearPassed(year) && !hasMonthPassed(year, month)
  }
}