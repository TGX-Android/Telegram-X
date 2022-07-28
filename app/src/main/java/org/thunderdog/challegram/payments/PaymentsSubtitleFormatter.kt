package org.thunderdog.challegram.payments

import org.drinkless.td.libcore.telegram.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

object PaymentsSubtitleFormatter {
  @JvmStatic
  fun format (from: TdApi.OrderInfo?, invoice: TdApi.Invoice): String = when {
    from == null -> ""

    invoice.needShippingAddress -> {
      arrayOf(
        from.shippingAddress?.streetLine1,
        from.shippingAddress?.streetLine2,
        from.shippingAddress?.city,
        from.shippingAddress?.state,
        from.shippingAddress?.countryCode,
        from.shippingAddress?.postalCode
      ).filterNot { it.isNullOrEmpty() }.joinToString()
    }

    else -> arrayOf(
      invoice.needName to from.name,
      invoice.needPhoneNumber to from.phoneNumber,
      invoice.needEmailAddress to from.emailAddress,
    ).filterNot { !it.first || it.second.isEmpty() }.joinToString { pair -> pair.second }
  }.ifEmpty { Lang.getString(R.string.PaymentFormNotSet) }
}