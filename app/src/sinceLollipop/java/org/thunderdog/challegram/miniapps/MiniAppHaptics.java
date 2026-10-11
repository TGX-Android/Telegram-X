/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import org.json.JSONObject;

/** Waveforms from Telegram Android BotWebViewVibrationEffect. */
final class MiniAppHaptics {
  static void perform (Context context, JSONObject data) {
    String type = data.optString("type");
    String style = type.equals("impact") ? data.optString("impact_style") : type.equals("notification") ? data.optString("notification_type") : type;
    long[] times, fallback;
    int[] amplitudes;
    switch (style) {
      case "light": times = new long[]{7}; amplitudes = new int[]{65}; fallback = new long[]{60}; break;
      case "medium": times = new long[]{7}; amplitudes = new int[]{145}; fallback = new long[]{70}; break;
      case "heavy": times = new long[]{7}; amplitudes = new int[]{255}; fallback = new long[]{80}; break;
      case "rigid": times = new long[]{3}; amplitudes = new int[]{225}; fallback = new long[]{50}; break;
      case "soft": times = new long[]{10}; amplitudes = new int[]{175}; fallback = new long[]{55}; break;
      case "error": times = new long[]{14,48,14,48,14,48,20}; amplitudes = new int[]{200,0,200,0,255,0,145}; fallback = new long[]{40,60,40,60,65,60,40}; break;
      case "success": times = new long[]{14,65,14}; amplitudes = new int[]{175,0,255}; fallback = new long[]{50,60,65}; break;
      case "warning": times = new long[]{14,64,14}; amplitudes = new int[]{225,0,175}; fallback = new long[]{65,60,40}; break;
      case "selection_change": times = new long[]{1}; amplitudes = new int[]{65}; fallback = new long[]{30}; break;
      default: return;
    }
    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    if (vibrator == null || !vibrator.hasVibrator()) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator.vibrate(vibrator.hasAmplitudeControl() ? VibrationEffect.createWaveform(times, amplitudes, -1) : VibrationEffect.createWaveform(fallback, -1));
    } else vibrator.vibrate(fallback, -1);
  }
}
