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

import org.thunderdog.challegram.BuildConfig;

/**
 * Configuration and feature flags for billing functionality.
 *
 * Design inspired by official Telegram clients (GPL-licensed).
 * Implementation is original code.
 */
public final class BillingConfig {
  private BillingConfig() {}

  /**
   * Master kill switch for billing functionality.
   * When false, all billing UI entry points are hidden.
   */
  public static final boolean BILLING_ENABLED = true;

  /**
   * Whether to fall back to invoice billing (via bot) when Google Play is unavailable.
   */
  public static final boolean USE_INVOICE_FALLBACK = true;

  /**
   * Enable billing debug logs.
   * IMPORTANT: Never log purchase tokens, receipts, or other sensitive data.
   */
  public static final boolean DEBUG_BILLING = BuildConfig.DEBUG;

  /**
   * Product ID for Telegram Premium subscription.
   * This must match the product ID configured in Google Play Console.
   */
  public static final String PREMIUM_PRODUCT_ID = "telegram_premium";

  /**
   * SharedPreferences file name for storing billing-related data.
   */
  public static final String BILLING_PREFS_NAME = "tgx_billing";

  /**
   * Maximum retry attempts for billing operations.
   */
  public static final int MAX_RETRY_ATTEMPTS = 3;

  /**
   * Initial retry delay in milliseconds.
   */
  public static final long INITIAL_RETRY_DELAY_MS = 1000;

  /**
   * Maximum retry delay in milliseconds.
   */
  public static final long MAX_RETRY_DELAY_MS = 30000;
}
