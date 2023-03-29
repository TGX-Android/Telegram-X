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
 *
 * File created on 26/01/2017
 */
package org.thunderdog.challegram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.unsorted.NLoader;
import org.thunderdog.challegram.voip.CallConfiguration;
import org.thunderdog.challegram.voip.CallOptions;
import org.thunderdog.challegram.voip.Socks5Proxy;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.webrtc.VideoSink;

import java.nio.ByteBuffer;

@SuppressWarnings ({"JniMissingFunction", "SpellCheckingInspection"})
public final class N {
  private N () { }

  public static boolean init () {
    return NLoader.loadLibrary();
  }

  // image.c
  public native static void calcCDT (ByteBuffer hsvBuffer, int width, int height, ByteBuffer buffer);
  public native static int pinBitmap (Bitmap bitmap);
  public native static int blurBitmap (Bitmap bitmap, int radius, int unpin, int forceLess);

  public native static boolean hasBuiltInWebpSupport ();
  public native static boolean loadWebpImage (Bitmap bitmap, ByteBuffer buffer, int len, BitmapFactory.Options options, boolean unpin);

  public static int pinBitmapIfNeeded (Bitmap bitmap) {
    if (Config.PIN_BITMAP_ENABLED) {
      return pinBitmap(bitmap);
    }
    return 0;
  }

  // views.c
  public native static float iimg (float input);

  // intro/render_wrapper.c
  public static native void onDrawFrame ();
  public static native void setScrollOffset (float a_offset);
  public static native void setPage (int page);
  public static native void setDate (float a);
  public static native void setColor (float red, float green, float blue);
  public static native void setIcTextures (int a_ic_bubble_dot, int a_ic_bubble, int a_ic_cam_lens, int a_ic_cam, int a_ic_pencil, int a_ic_pin, int a_ic_smile_eye, int a_ic_smile, int a_ic_videocam);
  public static native void setTelegramTextures (int a_telegram_sphere, int a_telegram_plane);
  public static native void setFastTextures (int a_fast_body, int a_fast_spiral, int a_fast_arrow, int a_fast_arrow_shadow);
  public static native void setFreeTextures (int a_knot_up, int a_knot_down);
  public static native void setPowerfulTextures (int a_powerful_mask, int a_powerful_star, int a_powerful_infinity, int a_powerful_infinity_white);
  public static native void setPrivateTextures (int a_private_door, int a_private_screw);
  public static native void onSurfaceCreated ();
  public static native void onSurfaceChanged (int a_width_px, int a_height_px, float a_scale_factor, int a1);

  // gif.c
  public static native void gifInit ();
  public static native long createDecoder (String path, int[] metadata, double startMediaTimestamp);
  public static native long createLottieDecoder (String path, String jsonData, double[] metadata, int fitzpatrickType);
  public static native void getLottieSize (long ptr, int[] size);
  public static native void cancelLottieDecoder (long ptr);
  public static native int createLottieCache (long ptr, String cachePath, Bitmap firstFrame, Bitmap bitmap, boolean allowCreate, boolean limitFps); // 0 = ok, 1 = need create, 2 = error
  public static native void destroyDecoder (long ptr);
  public static native boolean destroyLottieDecoder (long ptr);
  public static native int getVideoFrame (long ptr, Bitmap bitmap, int[] metadata);
  public static native boolean getLottieFrame (long ptr, Bitmap bitmap, long frameNo);
  public static native boolean isVideoBroken (long ptr);
  public static native boolean seekVideoToStart (long ptr);
  public static native boolean decodeLottieFirstFrame (String path, String jsonData, Bitmap bitmap);

  // TODO remove rendering, because it is no longer used
  // audio.c
  public static native int startRecord (String path);
  public static native int writeFrame (ByteBuffer frame, int len);
  public static native void stopRecord ();
  public static native int openOpusFile (String path);
  public static native int seekOpusFile (float position);
  public static native int isOpusFile (String path);
  public static native void readOpusFile (ByteBuffer buffer, int capacity, int[] args);
  public static native long getTotalPcmDuration ();
  public static native byte[] getWaveform (String path);
  public static native byte[] getWaveform2 (short[] array, int length);

  // jni_utils.cpp
  public native static String readlink (String path);

  // emoji.cpp
  public native static int getEmojiSuggestionMaxLength ();
  public native static Suggestion[] getEmojiSuggestions (String query);
  // public native static String getSuggestionEmoji (byte[] replacement);

  @Keep
  public static class Suggestion {
    public final String emoji;
    public final String label;
    public final String replacement;

    public Suggestion (String emoji, String label, String replacement) {
      this.emoji = emoji;
      this.label = label;
      this.replacement = replacement;
    }
  }

  public native static void onFatalError (String msg, int cause);
  public native static void throwDirect (String msg);

  public static native long newTgCallsInstance (
    @NonNull String version,
    @NonNull CallConfiguration configuration,
    @NonNull CallOptions options
  );
  public static native String[] getTgCallsVersions ();
}
