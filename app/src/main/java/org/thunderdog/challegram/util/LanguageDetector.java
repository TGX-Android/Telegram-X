package org.thunderdog.challegram.util;

import android.content.Context;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

public class LanguageDetector {
  public interface StringCallback {
    void run (String str);
  }

  public interface ExceptionCallback {
    void run (Exception e);
  }

  public static boolean hasSupport () {
    return true;
  }

  public static void detectLanguage (Context context, String text, StringCallback onSuccess, @Nullable ExceptionCallback onFail) {
    detectLanguage(context, text, onSuccess, onFail, false);
  }

  private static void detectLanguage (Context context, String text, StringCallback onSuccess, ExceptionCallback onFail, boolean initializeFirst) {
    try {
      if (initializeFirst) {
        com.google.mlkit.common.sdkinternal.MlKitContext.zza(context);
      }
      com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
        .identifyLanguage(text)
        .addOnSuccessListener(str -> {
          if (onSuccess != null) {
            onSuccess.run(str);
          }
        })
        .addOnFailureListener(e -> {
          if (onFail != null) {
            onFail.run(e);
          }
        });
    } catch (IllegalStateException e) {
      if (!initializeFirst) {
        detectLanguage(context, text, onSuccess, onFail, true);
      } else {
        if (onFail != null) {
          onFail.run(e);
        }
        Log.e(Log.TAG_LANGUAGE, "Error", e);
      }
    } catch (Exception e) {
      if (onFail != null) {
        onFail.run(e);
      }
      Log.e(Log.TAG_LANGUAGE, "Error", e);
    } catch (Throwable t) {
      if (onFail != null) {
        onFail.run(null);
      }
      Log.e(Log.TAG_LANGUAGE, "Error", t);
    }
  }
}
