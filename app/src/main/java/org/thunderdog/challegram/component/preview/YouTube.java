/**
 * File created on 23/02/16 at 01:20
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.preview;

import android.graphics.PorterDuff;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.android.youtube.player.YouTubeInitializationResult;

import org.thunderdog.challegram.Log;

public class YouTube {
  public static final float MIN_WIDTH_DP = 200f;
  public static final float MIN_HEIGHT_DP = 110f;

  public static String getError (YouTubeInitializationResult error) {
    if (error == null) {
      return "NULL";
    }
    if (YouTubeInitializationResult.CLIENT_LIBRARY_UPDATE_REQUIRED.equals(error)) {
      return "Client library update required";
    }
    if (YouTubeInitializationResult.DEVELOPER_KEY_INVALID.equals(error)) {
      return "Developer key invalid";
    }
    if (YouTubeInitializationResult.ERROR_CONNECTING_TO_SERVICE.equals(error)) {
      return "Connection error";
    }
    if (YouTubeInitializationResult.INTERNAL_ERROR.equals(error)) {
      return "Internal error";
    }
    if (YouTubeInitializationResult.INVALID_APPLICATION_SIGNATURE.equals(error)) {
      return "Invalid application signature";
    }
    if (YouTubeInitializationResult.NETWORK_ERROR.equals(error)) {
      return "Network error";
    }
    if (YouTubeInitializationResult.SERVICE_DISABLED.equals(error)) {
      return "YouTube App is disabled.\nEnable at Settings -> Applications -> YouTube.";
    }
    if (YouTubeInitializationResult.SERVICE_INVALID.equals(error)) {
      return "Service is invalid";
    }
    if (YouTubeInitializationResult.SERVICE_MISSING.equals(error)) {
      return "YouTube app is not installed.";
    }
    if (YouTubeInitializationResult.SERVICE_VERSION_UPDATE_REQUIRED.equals(error)) {
      return "YouTube app update required";
    }
    if (YouTubeInitializationResult.UNKNOWN_ERROR.equals(error)) {
      return "Unknown error";
    }
    return error.name();
  }

  @SuppressWarnings(value = "SpellCheckingInspection")
  public static String parseVideoId (String url) {
    if (url == null) {
      return null;
    }

    // youtube.com/watch?v=y2ErAPODA6U
    // https://youtu.be/R1NagZN2kjY

    int i;

    if ((i = url.indexOf("youtu.be/")) != -1 && url.length() > i + 9) {
      url = url.substring(i + 9);
      i = url.indexOf('?');
      if (i != -1) {
        url = url.substring(0, i);
      }
      return url.length() > 0 ? url : null;
    }

    if ((i = url.indexOf("youtube.com/watch?")) != -1 && url.length() > i + 18) {
      url = url.substring(i + 18);
      String[] parts = url.split("&");
      for (String part : parts) {
        String[] param = part.split("=");
        if (param.length == 2 && "v".equals(param[0])) {
          return param[1];
        }
      }
    }

    return null;
  }

  public static String buildDuration (int duration) {
    StringBuilder b = new StringBuilder(5);

    int hours = 0;
    int minutes = duration / 60;

    if (minutes > 60) {
      hours = minutes / 60;
      minutes = minutes % 60;
    }

    int seconds = duration % 60;

    if (hours > 0) {
      b.append(hours);
      b.append(':');
    }
    if (minutes < 10) {
      b.append('0');
    }
    b.append(minutes);
    b.append(':');
    if (seconds < 10) {
      b.append('0');
    }
    b.append(seconds);

    return b.toString();
  }

  public static void patchYouTubePlayer (View view) {
    try {
      if (view instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
          View v = group.getChildAt(i);
          if (v instanceof android.widget.ProgressBar) {
            patchProgressBar((android.widget.ProgressBar) v);
          }
          if (v instanceof ImageView) {
            try {
              v.setVisibility(View.INVISIBLE);
            } catch (Throwable t) {
              Log.w(Log.TAG_YOUTUBE, "Cannot patch youtube image");
            }
          }
          patchYouTubePlayer(v);
        }
      }
    } catch (Throwable t) {
      Log.w(Log.TAG_YOUTUBE, "Cannot patch youtube player", t);
    }
  }

  private static void patchProgressBar (android.widget.ProgressBar progressBar) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progressBar.getIndeterminateDrawable().setColorFilter(YouTubePlayerControls.SEEK_COLOR, PorterDuff.Mode.SRC_IN);
    }
  }
}
