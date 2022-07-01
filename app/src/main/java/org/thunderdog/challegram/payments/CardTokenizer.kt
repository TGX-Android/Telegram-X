package org.thunderdog.challegram.payments

import okhttp3.*
import okio.ByteString.Companion.encode
import org.json.JSONObject
import org.thunderdog.challegram.Log
import java.io.IOException

sealed class CardTokenizer {
  abstract fun tokenize (
    cardNumber: String, cardExpiryMonth: Int, cardExpiryYear: Int, cardCvc: String, cardHolderName: String?, countryCode: String?, postCode: String?, callback: CardTokenizerCallback
  )
}

interface CardTokenizerCallback {
  fun onSuccess (json: String)
  fun onError (exception: Exception)
}

// implementations

class StripeCardTokenizer (private val publisherToken: String): CardTokenizer() {
  override fun tokenize(
    cardNumber: String,
    cardExpiryMonth: Int,
    cardExpiryYear: Int,
    cardCvc: String,
    cardHolderName: String?,
    countryCode: String?,
    postCode: String?,
    callback: CardTokenizerCallback
  ) {
    OkHttpClient.Builder().build().newCall(
      Request.Builder()
        .url("https://api.stripe.com/v1/tokens")
        .header("Authorization", "Basic ${publisherToken.encode(Charsets.UTF_8).base64()}")
        .post(FormBody.Builder().apply {
          add("card[number]", cardNumber)
          add("card[cvc]", cardCvc)
          add("card[exp_month]", cardExpiryMonth.toString())
          add("card[exp_year]", cardExpiryYear.toString())
          cardHolderName?.let { add("card[name]", it) }
          countryCode?.let { add("card[address_country]", it) }
          postCode?.let { add("card[address_zip]", it) }
        }.build())
        .build()
    ).enqueue(object: Callback {
      override fun onResponse(call: Call, response: Response) {
        val result = JSONObject(response.body?.string() ?: "{}")
        if (result.has("id") && result.has("type")) {
          callback.onSuccess(JSONObject().apply {
            put("id", result.getString("id"))
            put("type", result.getString("type"))
          }.toString())
        } else if (result.has("error")) {
          callback.onError(Exception(
            "Stripe API error: ${result.getJSONObject("error").getString("message")} (${result.getJSONObject("error").getString("type")})"
          ))
        } else {
          callback.onError(Exception(
            "Unknown Stripe API error"
          ))
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        callback.onError(e)
      }
    })
  }
}