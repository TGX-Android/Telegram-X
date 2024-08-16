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
 * File created on 01/07/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.component.chat.Waveform;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.data.TGRecord;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.animator.FactorAnimator;

public class VoiceWaveformView extends View implements FactorAnimator.Target {
  private final Paint textPaint;
  private final int textOffset, textRight;
  private final int waveLeft;

  private final Waveform waveform;

  public VoiceWaveformView (Context context) {
    super(context);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.dp(15f));
    textOffset = Screen.dp(5f);
    textRight = Screen.dp(39f);
    waveLeft = Screen.dp(10f);

    waveform = new Waveform(null, Waveform.MODE_RECT, false);
  }

  public void clearData () {
    waveform.setData(null);
    invalidate();
  }

  private TGRecord record;
  private String seekStr;

  public void processRecord (final TGRecord record) {
    this.record = record;
    this.seekStr = Strings.buildDuration(record.getDuration());

    Background.instance().post(() -> {
      final byte[] waveform = record.getWaveform() != null ? record.getWaveform() : N.getWaveform(record.getPath());
      if (waveform != null) {
        UI.post(() -> setWaveform(record, waveform));
      }
    });
  }

  private final FactorAnimator waveformAnimator = new FactorAnimator(0, this, overshoot, OPEN_DURATION);

  private void setWaveform (final TGRecord record, byte[] waveform) {
    if (this.record == null || !this.record.equals(record)) {
      return;
    }
    record.setWaveform(waveform);
    this.waveform.setData(waveform);
    waveformAnimator.forceFactor(0f);
    waveformAnimator.setStartDelay(80L);
    waveformAnimator.animateTo(1f);
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    waveform.setExpand(factor);
    invalidate();
  }


  private int calculateWaveformWidth () {
    return getMeasuredWidth() - waveLeft - Screen.dp(110f) + Screen.dp(55f);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (getMeasuredWidth() != 0) {
      waveform.layout(calculateWaveformWidth());
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    int centerY = (int) ((float) height * .5f);

    if (seekStr != null) {
      textPaint.setColor(Theme.textAccentColor());
      c.drawText(seekStr, width - textRight, centerY + textOffset, textPaint);
    }

    waveform.draw(c, 1f, waveLeft, centerY);
  }

  private static final AnticipateOvershootInterpolator overshoot = new AnticipateOvershootInterpolator(3.0f);

  private static final long OPEN_DURATION = 350L;
}
