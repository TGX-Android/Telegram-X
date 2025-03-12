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
 * File created on 19/01/2024
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGAvatars;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;

import java.util.Arrays;

import me.vkryl.core.lambda.Destroyable;

public final class SuggestedChatsView extends View implements TGAvatars.Callback, AttachDelegate, Destroyable {

  @Dimension(unit = Dimension.DP)
  public static final float DEFAULT_HEIGHT_DP = 36f;

  private final Tdlib tdlib;
  private final TGAvatars avatars;
  private final ComplexReceiver complexReceiver;
  private final Drawable icon;
  private final Text text;

  public SuggestedChatsView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.complexReceiver = new ComplexReceiver(this);
    this.avatars = new TGAvatars(tdlib, this, this);
    this.icon = Drawables.get(context.getResources(), R.drawable.baseline_info_16);
    this.text = new Text.Builder("", Integer.MAX_VALUE, Paints.robotoStyleProvider(15f), Theme::textAccent2Color)
      .singleLine()
      .build();

    setMinimumHeight(Screen.dp(DEFAULT_HEIGHT_DP));
    Views.setClickable(this);
    RippleSupport.setTransparentSelector(this);
  }

  public void setChatIds (long[] chatIds, boolean animated) {
    avatars.setChatIds(Arrays.copyOf(chatIds, Math.min(3, chatIds.length)), animated);

    int chatCount = chatIds.length;
    if (chatCount > 0) {
      String in = Lang.plural(R.string.xNewChatsAvailableToJoin, chatCount);
      TdApi.FormattedText formattedText = TD.toFormattedText(in, false);
      if (TD.parseMarkdownWithEntities(formattedText) && formattedText.entities.length > 0) {
        TextEntity[] entities = TextEntity.valueOf(tdlib, formattedText, null);
        for (TextEntity entity : entities) {
          if (entity.isBold()) {
            entity.setCustomColorSet(Theme::textLinkColor);
          }
        }
        text.set(getTextMaxWidth(), formattedText.text, entities);
      } else {
        text.set(getTextMaxWidth(), in, null);
      }
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas canvas) {
    int cy = getHeight() / 2;
    avatars.draw(canvas, complexReceiver, Screen.dp(7f), cy, Gravity.LEFT, 1f);

    int textX = Math.round(avatars.getAnimatedWidth() + avatars.getAvatarsVisibility() * Screen.dp(7f)) + Screen.dp(7f);
    int textY = cy - text.getLineCenterY();
    text.draw(canvas, textX, textY);

    Drawables.drawCentered(canvas, icon, getWidth() - Screen.dp(20f), cy, Paints.getBackgroundIconPorterDuffPaint());
  }

  @Override
  public void onSizeChanged () {
    if (isLaidOut()) {
      text.changeMaxWidth(getTextMaxWidth());
    }
    invalidate();
  }

  @Override
  protected void onSizeChanged (int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width != oldWidth) {
      text.changeMaxWidth(getTextMaxWidth());
      invalidate();
    }
  }

  @Override
  public void onInvalidateMedia (TGAvatars avatars) {
    avatars.requestFiles(complexReceiver, /* isUpdate */ true, /* neverClear */ false);
  }

  @Override
  public void attach () {
    complexReceiver.attach();
  }

  @Override
  public void detach () {
    complexReceiver.detach();
  }

  @Override
  public void performDestroy () {
    text.performDestroy();
    complexReceiver.performDestroy();
  }

  private int getTextMaxWidth () {
    if (!isLaidOut()) {
      return Integer.MAX_VALUE;
    }
    return getWidth() - Math.round(avatars.getAnimatedWidth()) - Screen.dp(54);
  }
}
