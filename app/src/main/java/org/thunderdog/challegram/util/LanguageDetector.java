package org.thunderdog.challegram.util;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import me.vkryl.core.lambda.RunnableData;

public class LanguageDetector {
  public static void detectLanguage (Context context, String text, RunnableData<String> onSuccess, @Nullable RunnableData<Throwable> onFail) {
    detectLanguage(context, text, onSuccess, onFail, false);
  }

  private static void detectLanguage (Context context, String text, RunnableData<String> onSuccess, @Nullable RunnableData<Throwable> onFail, boolean initializeFirst) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      if (onFail != null) {
        onFail.runWithData(new IllegalStateException("Not available on this Android version."));
      }
      return;
    }
    try {
      if (initializeFirst) {
        com.google.mlkit.common.sdkinternal.MlKitContext.zza(context);
      }
      com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
        .identifyLanguage(text)
        .addOnSuccessListener(str -> {
          if (onSuccess != null) {
            onSuccess.runWithData(str);
          }
        })
        .addOnFailureListener(e -> {
          if (onFail != null) {
            onFail.runWithData(e);
          }
        });
    } catch (Throwable t) {
      if (t instanceof IllegalStateException && !initializeFirst) {
        detectLanguage(context, text, onSuccess, onFail, true);
        return;
      }
      Log.w("LanguageDetector failure", t);
      if (onFail != null) {
        onFail.runWithData(t);
      }
    }
  }
}
