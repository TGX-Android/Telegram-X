package org.thunderdog.challegram.payments

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encode
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

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
          callback.onError(Exception("Stripe API error: ${result.getJSONObject("error").getString("message")} (${result.getJSONObject("error").getString("type")})"))
        } else {
          callback.onError(Exception("Unknown Stripe API error"))
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        callback.onError(e)
      }
    })
  }
}

class SmartGlocalTokenizer (private val publicToken: String, private val testMode: Boolean): CardTokenizer() {
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
    val parameters = JSONObject().put("card", JSONObject().apply {
      put("number", cardNumber)
      put("expiration_month", String.format(Locale.US, "%02d", cardExpiryMonth))
      put("expiration_year", cardExpiryYear)
      put("security_code", cardCvc)
    })

    OkHttpClient.Builder().build().newCall(
      Request.Builder()
        .url(if (testMode) {
          "https://tgb-playground.smart-glocal.com/cds/v1/tokenize/card"
        } else {
          "https://tgb.smart-glocal.com/cds/v1/tokenize/card"
        })
        .header("X-PUBLIC-TOKEN", publicToken)
        .post(parameters.toString().toRequestBody("application/json".toMediaType()))
        .build()
    ).enqueue(object: Callback {
      override fun onResponse(call: Call, response: Response) {
        val result = JSONObject(response.body?.string() ?: "{}")
        val token = result.optJSONObject("data")?.optString("token")
        if (token != null) {
          callback.onSuccess(JSONObject().apply {
            put("type", "card")
            put("token", token)
          }.toString())
        } else {
          callback.onError(Exception("Smart Glocal payment error: $result"))
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        callback.onError(e)
      }
    })
  }
}