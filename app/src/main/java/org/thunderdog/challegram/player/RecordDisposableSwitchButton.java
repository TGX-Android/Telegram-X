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
 * File created on 30/06/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;

import me.vkryl.core.ColorUtils;

public class RecordDisposableSwitchButton extends RecordControllerButton {
  private final Drawable drawable;

  public RecordDisposableSwitchButton(Context context) {
    super(context);
    drawable = Drawables.get(context.getResources(), R.drawable.baseline_hot_once_24);
  }

  @Override
  protected void dispatchDraw (@NonNull Canvas canvas) {
    super.dispatchDraw(canvas);

    final float cx = getMeasuredWidth() / 2f;
    final float cy = getMeasuredHeight() / 2f;
    final float active = getActiveFactor();

    if (active == 0f) {
      Drawables.drawCentered(canvas, drawable, cx, cy, PorterDuffPaint.get(ColorId.icon));
    } else if (active == 1f) {
      Drawables.drawCentered(canvas, drawable, cx, cy, PorterDuffPaint.get(ColorId.fillingPositiveContent));
    } else {
      Drawables.drawCentered(canvas, drawable, cx, cy, Paints.getPorterDuffPaint(
        ColorUtils.fromToArgb(Theme.getColor(ColorId.icon), Theme.getColor(ColorId.fillingPositiveContent), active)
      ));
    }
  }
}
