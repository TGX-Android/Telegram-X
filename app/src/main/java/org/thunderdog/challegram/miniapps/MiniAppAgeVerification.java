/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;

/** One native, account-bound verification. Ordinary bot launches never receive this capability. */
public final class MiniAppAgeVerification {
  public interface Callback {
    /** null means cancelled, including account/parameters changing while the page is open. */
    void onResult (MiniAppAgeVerification verification, Boolean passed);
  }

  public final int minAge;
  public final String username, country;
  private final long userId;
  private Callback callback;

  public MiniAppAgeVerification (TdApi.AgeVerificationParameters parameters, long userId, Callback callback) {
    if (parameters == null || parameters.minAge <= 0 || parameters.verificationBotUsername == null ||
        parameters.verificationBotUsername.isEmpty() || userId == 0 || callback == null) {
      throw new IllegalArgumentException("Missing age verification parameters");
    }
    this.minAge = parameters.minAge;
    this.username = parameters.verificationBotUsername;
    this.country = parameters.country != null ? parameters.country : "";
    this.userId = userId;
    this.callback = callback;
  }

  public boolean isCurrent (TdApi.AgeVerificationParameters parameters, long userId) {
    return callback != null && this.userId == userId && parameters != null && minAge == parameters.minAge &&
      username.equals(parameters.verificationBotUsername) && country.equals(parameters.country != null ? parameters.country : "");
  }

  public void report (TdApi.AgeVerificationParameters parameters, long userId, double age) {
    if (!isCurrent(parameters, userId) || Double.isNaN(age) || Double.isInfinite(age) || age < 0) return;
    // Telegram Android uses the measured age, not the page's local "passed" threshold.
    finish(age >= minAge);
  }

  public void cancel () { finish(null); }

  private void finish (Boolean passed) {
    Callback current = callback;
    callback = null;
    if (current != null) current.onResult(this, passed);
  }
}
