/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Controller for payment form - handles card input and payment processing.
 */
public class PaymentFormController extends RecyclerViewController<PaymentFormController.Args>
    implements View.OnClickListener, SettingsAdapter.TextChangeListener {

  public static class Args {
    public final TdApi.PaymentForm paymentForm;
    public final TdApi.InputInvoice inputInvoice;
    public final int monthCount;

    public Args(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, int monthCount) {
      this.paymentForm = paymentForm;
      this.inputInvoice = inputInvoice;
      this.monthCount = monthCount;
    }
  }

  private SettingsAdapter adapter;
  private TdApi.PaymentForm paymentForm;
  private TdApi.InputInvoice inputInvoice;

  // Card input fields
  private String cardNumber = "";
  private String cardExpiry = "";
  private String cardCvc = "";
  private String cardHolder = "";

  // ListItem references for text fields
  private ListItem cardNumberItem;
  private ListItem cardExpiryItem;
  private ListItem cardCvcItem;
  private ListItem cardHolderItem;

  private boolean isProcessing = false;

  public PaymentFormController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName() {
    return Lang.getString(R.string.PaymentForm);
  }

  @Override
  public int getId() {
    return R.id.controller_paymentForm;
  }

  @Override
  protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
    Args args = getArgumentsStrict();
    this.paymentForm = args.paymentForm;
    this.inputInvoice = args.inputInvoice;

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_paymentSavedCard) {
          TdApi.SavedCredentials creds = (TdApi.SavedCredentials) item.getData();
          if (creds != null) {
            view.setData(creds.title);
          }
        }
      }
    };
    adapter.setTextChangeListener(this);

    recyclerView.setAdapter(adapter);
    buildCells();
  }

  @Override
  public void onTextChanged(int id, ListItem item, MaterialEditTextGroup v) {
    String text = v.getText().toString();
    if (item == cardNumberItem) {
      cardNumber = text.replaceAll("\\s", "");
    } else if (item == cardExpiryItem) {
      cardExpiry = text;
    } else if (item == cardCvcItem) {
      cardCvc = text;
    } else if (item == cardHolderItem) {
      cardHolder = text;
    }
  }

  private void buildCells() {
    List<ListItem> items = new ArrayList<>();

    // Product info
    if (paymentForm.productInfo != null) {
      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, paymentForm.productInfo.title));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (paymentForm.productInfo.description != null && !StringUtils.isEmpty(paymentForm.productInfo.description.text)) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, paymentForm.productInfo.description.text));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
      TdApi.PaymentFormTypeRegular regular = (TdApi.PaymentFormTypeRegular) paymentForm.type;

      // Saved cards
      if (regular.savedCredentials != null && regular.savedCredentials.length > 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentSavedCards));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        boolean first = true;
        for (TdApi.SavedCredentials creds : regular.savedCredentials) {
          if (!first) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          first = false;
          ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_paymentSavedCard, R.drawable.baseline_credit_card_24, creds.title);
          item.setData(creds);
          items.add(item);
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      // New card input
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentNewCard));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      // Card number
      cardNumberItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardNumber);
      items.add(cardNumberItem);
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));

      // Expiry
      cardExpiryItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardExpiry);
      items.add(cardExpiryItem);
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));

      // CVC
      cardCvcItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardCVC);
      items.add(cardCvcItem);

      // Cardholder name (if required by provider)
      if (regular.paymentProvider instanceof TdApi.PaymentProviderStripe) {
        TdApi.PaymentProviderStripe stripe = (TdApi.PaymentProviderStripe) regular.paymentProvider;
        if (stripe.needCardholderName) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          cardHolderItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardHolder);
          items.add(cardHolderItem);
        }
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      // Pay button
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      long totalAmount = calculateTotalAmount(regular.invoice.priceParts);
      String priceText = formatPrice(regular.invoice.currency, totalAmount);
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_paymentSubmit, R.drawable.baseline_payment_24, Lang.getString(R.string.PaymentPay, priceText)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  @Override
  public void onClick(View v) {
    if (isProcessing) return;

    final int viewId = v.getId();
    if (viewId == R.id.btn_paymentSavedCard) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.SavedCredentials) {
        TdApi.SavedCredentials creds = (TdApi.SavedCredentials) item.getData();
        payWithSavedCredentials(creds);
      }
    } else if (viewId == R.id.btn_paymentSubmit) {
      if (validateCard()) {
        processNewCardPayment();
      }
    }
  }

  private boolean validateCard() {
    if (cardNumber.length() < 13 || cardNumber.length() > 19) {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      return false;
    }
    if (cardExpiry.length() < 4) {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      return false;
    }
    if (cardCvc.length() < 3) {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      return false;
    }
    return true;
  }

  private void payWithSavedCredentials(TdApi.SavedCredentials creds) {
    isProcessing = true;
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);

    TdApi.InputCredentialsSaved credentials = new TdApi.InputCredentialsSaved(creds.id);
    sendPaymentForm(credentials);
  }

  private void processNewCardPayment() {
    if (!(paymentForm.type instanceof TdApi.PaymentFormTypeRegular)) {
      UI.showToast(R.string.PaymentUnsupportedType, Toast.LENGTH_SHORT);
      return;
    }

    TdApi.PaymentFormTypeRegular regular = (TdApi.PaymentFormTypeRegular) paymentForm.type;

    if (regular.paymentProvider instanceof TdApi.PaymentProviderStripe) {
      TdApi.PaymentProviderStripe stripe = (TdApi.PaymentProviderStripe) regular.paymentProvider;
      tokenizeCardWithStripe(stripe.publishableKey);
    } else if (regular.paymentProvider instanceof TdApi.PaymentProviderSmartGlocal) {
      TdApi.PaymentProviderSmartGlocal smartGlocal = (TdApi.PaymentProviderSmartGlocal) regular.paymentProvider;
      tokenizeCardWithSmartGlocal(smartGlocal.publicToken, smartGlocal.tokenizeUrl);
    } else if (regular.paymentProvider instanceof TdApi.PaymentProviderOther) {
      TdApi.PaymentProviderOther other = (TdApi.PaymentProviderOther) regular.paymentProvider;
      // Open web form
      tdlib.ui().openUrl(this, other.url, null);
    } else {
      UI.showToast(R.string.PaymentUnknownProvider, Toast.LENGTH_SHORT);
    }
  }

  private void tokenizeCardWithStripe(String publishableKey) {
    isProcessing = true;
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);

    // Parse expiry
    String expMonth, expYear;
    if (cardExpiry.contains("/")) {
      String[] parts = cardExpiry.split("/");
      expMonth = parts[0].trim();
      expYear = parts.length > 1 ? parts[1].trim() : "";
    } else if (cardExpiry.length() >= 4) {
      expMonth = cardExpiry.substring(0, 2);
      expYear = cardExpiry.substring(2);
    } else {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      isProcessing = false;
      return;
    }

    // Ensure 4-digit year
    if (expYear.length() == 2) {
      expYear = "20" + expYear;
    }

    final String finalExpMonth = expMonth;
    final String finalExpYear = expYear;

    // Make Stripe API call in background
    new Thread(() -> {
      try {
        String token = createStripeToken(publishableKey, cardNumber, finalExpMonth, finalExpYear, cardCvc, cardHolder);
        runOnUiThreadOptional(() -> {
          if (token != null) {
            JSONObject tokenData = new JSONObject();
            try {
              tokenData.put("type", "card");
              tokenData.put("id", token);
            } catch (JSONException e) {
              Log.e("Payment", "Failed to create token JSON", e);
            }

            TdApi.InputCredentialsNew credentials = new TdApi.InputCredentialsNew(
              tokenData.toString(),
              true // allowSave
            );
            sendPaymentForm(credentials);
          } else {
            isProcessing = false;
            UI.showToast(R.string.PaymentCardFailed, Toast.LENGTH_SHORT);
          }
        });
      } catch (Exception e) {
        Log.e("Payment", "Stripe tokenization failed", e);
        runOnUiThreadOptional(() -> {
          isProcessing = false;
          UI.showToast(Lang.getString(R.string.PaymentFailed, e.getMessage()), Toast.LENGTH_SHORT);
        });
      }
    }).start();
  }

  private String createStripeToken(String publishableKey, String cardNumber, String expMonth, String expYear, String cvc, String name) throws Exception {
    URL url = new URL("https://api.stripe.com/v1/tokens");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Authorization", "Bearer " + publishableKey);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setDoOutput(true);

    StringBuilder params = new StringBuilder();
    params.append("card[number]=").append(cardNumber);
    params.append("&card[exp_month]=").append(expMonth);
    params.append("&card[exp_year]=").append(expYear);
    params.append("&card[cvc]=").append(cvc);
    if (!StringUtils.isEmpty(name)) {
      params.append("&card[name]=").append(java.net.URLEncoder.encode(name, "UTF-8"));
    }

    try (OutputStream os = conn.getOutputStream()) {
      os.write(params.toString().getBytes("UTF-8"));
    }

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();

      JSONObject jsonResponse = new JSONObject(response.toString());
      return jsonResponse.getString("id");
    } else {
      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();
      Log.e("Payment", "Stripe error: " + response);
      throw new Exception("Card processing failed");
    }
  }

  private void tokenizeCardWithSmartGlocal(String publicToken, String tokenizeUrl) {
    isProcessing = true;
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);

    // Parse expiry
    String expMonth, expYear;
    if (cardExpiry.contains("/")) {
      String[] parts = cardExpiry.split("/");
      expMonth = parts[0].trim();
      expYear = parts.length > 1 ? parts[1].trim() : "";
    } else if (cardExpiry.length() >= 4) {
      expMonth = cardExpiry.substring(0, 2);
      expYear = cardExpiry.substring(2);
    } else {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      isProcessing = false;
      return;
    }

    final String finalExpMonth = expMonth;
    final String finalExpYear = expYear;

    new Thread(() -> {
      try {
        String token = createSmartGlocalToken(publicToken, tokenizeUrl, cardNumber, finalExpMonth, finalExpYear, cardCvc);
        runOnUiThreadOptional(() -> {
          if (token != null) {
            TdApi.InputCredentialsNew credentials = new TdApi.InputCredentialsNew(token, true);
            sendPaymentForm(credentials);
          } else {
            isProcessing = false;
            UI.showToast(R.string.PaymentCardFailed, Toast.LENGTH_SHORT);
          }
        });
      } catch (Exception e) {
        Log.e("Payment", "SmartGlocal tokenization failed", e);
        runOnUiThreadOptional(() -> {
          isProcessing = false;
          UI.showToast(Lang.getString(R.string.PaymentFailed, e.getMessage()), Toast.LENGTH_SHORT);
        });
      }
    }).start();
  }

  private String createSmartGlocalToken(String publicToken, String tokenizeUrl, String cardNumber, String expMonth, String expYear, String cvc) throws Exception {
    URL url = new URL(tokenizeUrl);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("X-Public-Token", publicToken);
    conn.setDoOutput(true);

    JSONObject card = new JSONObject();
    card.put("number", cardNumber);
    card.put("expiration_month", expMonth);
    card.put("expiration_year", expYear);
    card.put("security_code", cvc);

    JSONObject body = new JSONObject();
    body.put("card", card);

    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.toString().getBytes("UTF-8"));
    }

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();
      return response.toString();
    } else {
      throw new Exception("Card processing failed");
    }
  }

  private void sendPaymentForm(TdApi.InputCredentials credentials) {
    TdApi.SendPaymentForm request = new TdApi.SendPaymentForm(
      inputInvoice,
      paymentForm.id,
      "", // orderInfoId
      "", // shippingOptionId
      credentials,
      0   // tipAmount
    );

    tdlib.send(request, (result, error) -> {
      runOnUiThreadOptional(() -> {
        isProcessing = false;
        if (error != null) {
          UI.showToast(Lang.getString(R.string.PaymentFailed, TD.toErrorString(error)), Toast.LENGTH_SHORT);
        } else {
          TdApi.PaymentResult paymentResult = (TdApi.PaymentResult) result;
          if (paymentResult.success) {
            UI.showToast(R.string.PaymentSuccess, Toast.LENGTH_SHORT);
            navigateBack();
          } else if (!StringUtils.isEmpty(paymentResult.verificationUrl)) {
            // Need 3D Secure verification
            tdlib.ui().openUrl(PaymentFormController.this, paymentResult.verificationUrl, null);
          } else {
            UI.showToast(R.string.PaymentVerificationNeeded, Toast.LENGTH_SHORT);
          }
        }
      });
    });
  }

  private long calculateTotalAmount(TdApi.LabeledPricePart[] priceParts) {
    if (priceParts == null) return 0;
    long total = 0;
    for (TdApi.LabeledPricePart part : priceParts) {
      total += part.amount;
    }
    return total;
  }

  private String formatPrice(String currency, long amount) {
    double price = amount / 100.0;
    return String.format("%s %.2f", currency, price);
  }
}
