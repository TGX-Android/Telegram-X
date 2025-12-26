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
package org.thunderdog.challegram.billing;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main billing orchestration for Telegram Premium purchases.
 *
 * Design inspired by official Telegram clients (GPL-licensed).
 * Implementation is original code.
 *
 * SECURITY NOTES:
 * - Never log purchase tokens or receipt data
 * - Premium status is determined by server, not local flags
 * - All purchases must be verified server-side before entitlement
 */
public class BillingManager implements PurchasesUpdatedListener, BillingClientStateListener {
  private static final String TAG = "BillingManager";

  private static volatile BillingManager instance;

  private final Context context;
  private final BillingClient billingClient;
  private final BillingPayloadHandler payloadHandler;
  private final Set<String> pendingTokens;
  private final Map<String, Consumer<BillingResult>> resultListeners;
  private final List<Runnable> connectionListeners;

  private ProductDetails premiumProductDetails;
  private boolean isConnected;
  private boolean billingUnavailable;
  private int retryCount;

  // Purchase flow state
  private Tdlib currentTdlib;
  private Runnable onPurchaseCanceled;

  public static BillingManager getInstance() {
    if (instance == null) {
      synchronized (BillingManager.class) {
        if (instance == null) {
          instance = new BillingManager(UI.getAppContext());
        }
      }
    }
    return instance;
  }

  private BillingManager(@NonNull Context context) {
    this.context = context.getApplicationContext();
    this.payloadHandler = new BillingPayloadHandler(context);
    this.pendingTokens = Collections.synchronizedSet(new HashSet<>());
    this.resultListeners = new HashMap<>();
    this.connectionListeners = new ArrayList<>();

    this.billingClient = BillingClient.newBuilder(context)
      .enablePendingPurchases()
      .setListener(this)
      .build();
  }

  // ==================== Connection Management ====================

  /**
   * Initializes billing connection.
   * Should be called on app startup.
   */
  public void initialize() {
    if (!BillingConfig.BILLING_ENABLED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Billing disabled by config");
      }
      return;
    }

    startConnection();
  }

  private void startConnection() {
    if (isConnected || billingClient.isReady()) {
      return;
    }

    try {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Starting billing connection...");
      }
      billingClient.startConnection(this);
    } catch (Exception e) {
      Log.e(TAG, "Failed to start billing connection", e);
    }
  }

  @Override
  public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Billing setup finished: %s", getResponseCodeString(billingResult.getResponseCode()));
    }

    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      isConnected = true;
      billingUnavailable = false;
      retryCount = 0;

      // Query premium product details
      queryPremiumProductDetails();

      // Check for any pending purchases
      queryExistingPurchases();

      // Notify listeners
      notifyConnectionListeners();
    } else {
      billingUnavailable = true;
      isConnected = false;
    }
  }

  @Override
  public void onBillingServiceDisconnected() {
    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Billing service disconnected");
    }

    isConnected = false;

    // Retry with exponential backoff
    if (retryCount < BillingConfig.MAX_RETRY_ATTEMPTS) {
      long delay = Math.min(
        BillingConfig.INITIAL_RETRY_DELAY_MS * (1L << retryCount),
        BillingConfig.MAX_RETRY_DELAY_MS
      );
      retryCount++;

      UI.post(this::startConnection, delay);
    }
  }

  /**
   * Adds a listener to be called when billing is connected.
   * If already connected, listener is called immediately.
   */
  public void whenConnected(@NonNull Runnable listener) {
    if (isConnected) {
      UI.post(listener);
    } else {
      synchronized (connectionListeners) {
        connectionListeners.add(listener);
      }
    }
  }

  private void notifyConnectionListeners() {
    synchronized (connectionListeners) {
      for (Runnable listener : connectionListeners) {
        UI.post(listener);
      }
      connectionListeners.clear();
    }
  }

  // ==================== Product Details ====================

  private void queryPremiumProductDetails() {
    if (!billingClient.isReady()) {
      return;
    }

    QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
      .setProductId(BillingConfig.PREMIUM_PRODUCT_ID)
      .setProductType(BillingClient.ProductType.SUBS)
      .build();

    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
      .setProductList(Collections.singletonList(product))
      .build();

    billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        for (ProductDetails details : productDetailsList) {
          if (BillingConfig.PREMIUM_PRODUCT_ID.equals(details.getProductId())) {
            premiumProductDetails = details;
            if (BillingConfig.DEBUG_BILLING) {
              Log.d(TAG, "Premium product details loaded");
            }
            break;
          }
        }

        if (premiumProductDetails == null) {
          billingUnavailable = true;
          if (BillingConfig.DEBUG_BILLING) {
            Log.w(TAG, "Premium product not found in Play Store");
          }
        }
      } else {
        if (BillingConfig.DEBUG_BILLING) {
          Log.w(TAG, "Failed to query product details: %s",
            getResponseCodeString(billingResult.getResponseCode()));
        }
      }
    });
  }

  // ==================== Purchase Flow ====================

  /**
   * Launches the Premium purchase flow.
   *
   * @param activity   The activity to launch the flow from
   * @param tdlib      The TDLib instance for the current account
   * @param onCanceled Callback when purchase is canceled
   */
  public void launchPremiumPurchase(
    @NonNull Activity activity,
    @NonNull Tdlib tdlib,
    @Nullable Runnable onCanceled
  ) {
    if (!BillingConfig.BILLING_ENABLED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Billing disabled");
      }
      return;
    }

    if (premiumProductDetails == null || !billingClient.isReady()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Billing not ready or product not available");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    List<ProductDetails.SubscriptionOfferDetails> offerDetails =
      premiumProductDetails.getSubscriptionOfferDetails();

    if (offerDetails == null || offerDetails.isEmpty()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "No subscription offers available");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    this.currentTdlib = tdlib;
    this.onPurchaseCanceled = onCanceled;

    // Create payment purpose
    TdApi.StorePaymentPurposePremiumSubscription purpose =
      new TdApi.StorePaymentPurposePremiumSubscription();

    // Create obfuscated payload
    Pair<String, String> payload = payloadHandler.createPayload(purpose, tdlib.id());

    // Build billing flow params
    BillingFlowParams.ProductDetailsParams productParams =
      BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(premiumProductDetails)
        .setOfferToken(offerDetails.get(0).getOfferToken())
        .build();

    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(Collections.singletonList(productParams))
      .setObfuscatedAccountId(payload.first)
      .setObfuscatedProfileId(payload.second)
      .build();

    // Launch the flow
    BillingResult result = billingClient.launchBillingFlow(activity, flowParams);

    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Failed to launch billing flow: %s",
          getResponseCodeString(result.getResponseCode()));
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
    }
  }

  // ==================== Purchase Callbacks ====================

  @Override
  public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Purchases updated: %s, count: %d",
        getResponseCodeString(billingResult.getResponseCode()),
        purchases != null ? purchases.size() : 0);
    }

    if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      handlePurchaseError(billingResult);
      return;
    }

    if (purchases == null || purchases.isEmpty()) {
      return;
    }

    for (Purchase purchase : purchases) {
      handlePurchase(purchase);
    }
  }

  private void handlePurchaseError(BillingResult billingResult) {
    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Purchase canceled by user");
      }
    }

    if (onPurchaseCanceled != null) {
      UI.post(onPurchaseCanceled);
      onPurchaseCanceled = null;
    }
  }

  private void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Purchase not in PURCHASED state: %d", purchase.getPurchaseState());
      }
      return;
    }

    String token = purchase.getPurchaseToken();
    if (pendingTokens.contains(token)) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Purchase already being processed");
      }
      return;
    }

    if (!purchase.isAcknowledged()) {
      assignPurchaseToServer(purchase);
    }
  }

  private void assignPurchaseToServer(Purchase purchase) {
    Pair<Integer, TdApi.StorePaymentPurpose> payload =
      payloadHandler.extractPayload(purchase);

    if (payload == null) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Failed to extract payload from purchase");
      }
      return;
    }

    Tdlib tdlib = currentTdlib;
    if (tdlib == null) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "No TDLib instance available for purchase assignment");
      }
      return;
    }

    String token = purchase.getPurchaseToken();
    pendingTokens.add(token);

    // Get the product ID from the purchase
    String productId = purchase.getProducts().isEmpty() ? "" : purchase.getProducts().get(0);

    // Create the transaction and assign request
    TdApi.StoreTransactionGooglePlay transaction = new TdApi.StoreTransactionGooglePlay(
      purchase.getPackageName(),
      productId,
      purchase.getPurchaseToken()  // NOTE: Token sent to server only, never logged
    );
    TdApi.AssignStoreTransaction request = new TdApi.AssignStoreTransaction(
      transaction,
      payload.second
    );

    tdlib.client().send(request, result -> {
      pendingTokens.remove(token);

      UI.post(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          if (BillingConfig.DEBUG_BILLING) {
            Log.d(TAG, "Purchase assigned successfully");
          }

          // Clear stored payload
          payloadHandler.clearPayload(purchase);

          // Notify success listeners
          for (String product : purchase.getProducts()) {
            Consumer<BillingResult> listener = resultListeners.remove(product);
            if (listener != null) {
              listener.accept(BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build());
            }
          }
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          if (BillingConfig.DEBUG_BILLING) {
            Log.w(TAG, "Failed to assign purchase: %d %s", error.code, error.message);
          }

          if (onPurchaseCanceled != null) {
            onPurchaseCanceled.run();
            onPurchaseCanceled = null;
          }
        }
      });
    });
  }

  // ==================== Purchase Query ====================

  /**
   * Queries existing purchases and processes any unacknowledged ones.
   * Used for purchase restoration and pending purchase recovery.
   */
  public void queryExistingPurchases() {
    if (!billingClient.isReady()) {
      return;
    }

    QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.SUBS)
      .build();

    billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        if (BillingConfig.DEBUG_BILLING) {
          Log.d(TAG, "Found %d existing subscriptions", purchases.size());
        }

        for (Purchase purchase : purchases) {
          if (purchase.getProducts().contains(BillingConfig.PREMIUM_PRODUCT_ID)) {
            if (!purchase.isAcknowledged()) {
              // Pending purchase found, process it
              handlePurchase(purchase);
            }
          }
        }
      }
    });
  }

  /**
   * Restores purchases for a TDLib instance.
   * Queries server with existing subscription tokens.
   */
  public void restorePurchases(@NonNull Tdlib tdlib) {
    this.currentTdlib = tdlib;
    queryExistingPurchases();
  }

  // ==================== Utility Methods ====================

  /**
   * Returns whether billing is available.
   */
  public boolean isBillingAvailable() {
    return BillingConfig.BILLING_ENABLED &&
           !billingUnavailable &&
           premiumProductDetails != null;
  }

  /**
   * Returns whether billing client is ready.
   */
  public boolean isReady() {
    return billingClient.isReady();
  }

  /**
   * Returns the Premium product details.
   */
  @Nullable
  public ProductDetails getPremiumProductDetails() {
    return premiumProductDetails;
  }

  /**
   * Formats currency amount for display.
   */
  public String formatPrice(long amountMicros, String currencyCode) {
    try {
      Currency currency = Currency.getInstance(currencyCode);
      NumberFormat format = NumberFormat.getCurrencyInstance();
      format.setCurrency(currency);
      return format.format(amountMicros / 1_000_000.0);
    } catch (Exception e) {
      return String.format("%d %s", amountMicros / 1_000_000, currencyCode);
    }
  }

  /**
   * Adds a result listener for a product purchase.
   */
  public void addResultListener(String productId, Consumer<BillingResult> listener) {
    resultListeners.put(productId, listener);
  }

  private static String getResponseCodeString(int code) {
    switch (code) {
      case BillingClient.BillingResponseCode.OK: return "OK";
      case BillingClient.BillingResponseCode.USER_CANCELED: return "USER_CANCELED";
      case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE: return "SERVICE_UNAVAILABLE";
      case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE: return "BILLING_UNAVAILABLE";
      case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE: return "ITEM_UNAVAILABLE";
      case BillingClient.BillingResponseCode.DEVELOPER_ERROR: return "DEVELOPER_ERROR";
      case BillingClient.BillingResponseCode.ERROR: return "ERROR";
      case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED: return "ITEM_ALREADY_OWNED";
      case BillingClient.BillingResponseCode.ITEM_NOT_OWNED: return "ITEM_NOT_OWNED";
      case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED: return "SERVICE_DISCONNECTED";
      case BillingClient.BillingResponseCode.SERVICE_TIMEOUT: return "SERVICE_TIMEOUT";
      case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED: return "FEATURE_NOT_SUPPORTED";
      case BillingClient.BillingResponseCode.NETWORK_ERROR: return "NETWORK_ERROR";
      default: return "UNKNOWN(" + code + ")";
    }
  }
}
