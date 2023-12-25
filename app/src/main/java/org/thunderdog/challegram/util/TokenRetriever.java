package org.thunderdog.challegram.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

public abstract class TokenRetriever {
  public interface RegisterCallback {
    void onSuccess (@NonNull TdApi.DeviceToken token);
    void onError (@NonNull String errorKey, @Nullable Throwable e);
  }

  private boolean isInitialized;

  public final boolean initialize (Context context) {
    if (isInitialized) {
      return true;
    }
    boolean result = performInitialization(context);
    if (result) {
      isInitialized = true;
    }
    return result;
  }

  protected abstract boolean performInitialization (Context context);

  public abstract @NonNull String getName ();
  public abstract @Nullable String getConfiguration ();

  public final void retrieveDeviceToken (int retryCount, RegisterCallback callback) {
    if (!isInitialized) {
      throw new IllegalStateException();
    }
    fetchDeviceToken(retryCount, callback);
  }

  protected abstract void fetchDeviceToken (int retryCount, RegisterCallback callback);
}
