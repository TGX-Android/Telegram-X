/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 09/02/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.Destroyable;

public class WebsiteEmptyImageView extends View implements Destroyable {
  private Drawable starIcon, loveIcon, walletIcon, locationIcon;

  public WebsiteEmptyImageView (Context context) {
    super(context);
    prepare();
  }

  @Override
  public void performDestroy () {
    if (starIcon != null) {
      starIcon = null;
      walletIcon = null;
      locationIcon = null;
      loveIcon = null;
    }
  }

  private void prepare () {
    if (starIcon == null) {
      starIcon = Drawables.get(getResources(), R.drawable.baseline_star_24);
      walletIcon = Drawables.get(getResources(), R.drawable.baseline_account_balance_wallet_24);
      locationIcon = Drawables.get(getResources(), R.drawable.baseline_location_on_24);
      loveIcon = Drawables.get(getResources(), R.drawable.baseline_favorite_20);
    }
  }

  private static void drawIcon (Canvas c, RectF rectF, Drawable icon, Paint paint) {
    Drawables.draw(c, icon, rectF.centerX() - icon.getMinimumWidth() / 2, rectF.centerY() - icon.getMinimumHeight() / 2, paint);
  }

  @Override
  protected void onDraw (Canvas c) {
    prepare();

    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;

    int size = Screen.dp(36f);
    int spacing = Screen.dp(2f);

    int radius = Screen.dp(3f);
    int color = Theme.backgroundIconColor();

    int strokeSize = Screen.dp(2f);

    Paint iconPaint = Paints.getBackgroundIconPorterDuffPaint();

    RectF rectF = Paints.getRectF();
    rectF.left = cx - spacing - size + strokeSize / 2;
    rectF.right = cx - spacing - strokeSize / 2;
    rectF.top = cy - spacing - size + strokeSize / 2;
    rectF.bottom = cy - spacing - strokeSize / 2;
    c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(color, strokeSize));
    drawIcon(c, rectF, starIcon, iconPaint);

    rectF.top = cy + spacing + strokeSize / 2;
    rectF.bottom = cy + spacing + size - strokeSize / 2;
    c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(color, strokeSize));
    drawIcon(c, rectF, walletIcon, iconPaint);

    rectF.left = cx + spacing + strokeSize / 2;
    rectF.right = cx + spacing + size - strokeSize / 2;
    c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(color, strokeSize));
    drawIcon(c, rectF, locationIcon, iconPaint);

    rectF.top = cy - spacing - size + strokeSize / 2;
    rectF.bottom = cy - spacing - strokeSize / 2;
    c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(color, strokeSize));
    drawIcon(c, rectF, loveIcon, iconPaint);
  }
}
