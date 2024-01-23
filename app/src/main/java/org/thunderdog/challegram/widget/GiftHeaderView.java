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
 * File created on 05/01/2023
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessageGiveawayBase;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.GiftParticlesDrawable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;

public class GiftHeaderView extends View implements GiftParticlesDrawable.ParticleValidator {
  private final GiftParticlesDrawable particlesDrawable;
  private TGMessageGiveawayBase.Content content;
  private int contentY;

  private @StringRes int headerRes = R.string.GiftLink;
  private @StringRes int descriptionRes = R.string.GiftLinkDesc;

  public GiftHeaderView (Context context) {
    super(context);
    particlesDrawable = new GiftParticlesDrawable();
    particlesDrawable.setParticleValidator(this);
  }

  public void setTexts (@StringRes int headerRes, @StringRes int descriptionRes) {
    if (this.headerRes != headerRes || this.descriptionRes != descriptionRes) {
      this.headerRes = headerRes;
      this.descriptionRes = descriptionRes;
      if (getMeasuredWidth() > 0) {
        buildContent();
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    buildContent();
    particlesDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
  }

  private void buildContent () {
    content = new TGMessageGiveawayBase.Content(getMeasuredWidth() - Screen.dp(120));
    content.padding(Screen.dp(10));
    content.add(new TGMessageGiveawayBase.ContentDrawable(R.drawable.baseline_gift_72));
    content.padding(Screen.dp(22));
    content.add(new TextWrapper(Lang.getString(headerRes), getHeaderStyleProvider(), () -> Theme.getColor(ColorId.text)).setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true));
    content.padding(Screen.dp(8));
    content.add(new TextWrapper(null, TD.toFormattedText(Lang.getMarkdownString(null, descriptionRes), false), getTextStyleProvider(), () -> Theme.getColor(ColorId.text), null, null).setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true));
    contentY = (getMeasuredHeight() - content.getHeight()) / 2;
  }

  @Override
  public boolean isValidPosition (float x, float y) {
    return content == null || content.isValidPosition(x - Screen.dp(60), y - contentY);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);
    particlesDrawable.draw(canvas);

    content.draw(canvas, null, Screen.dp(60), contentY);
  }

  public static int getDefaultHeight () {
    return Screen.dp(231);
  }


  private static TextStyleProvider headerStyleProvider;

  protected static TextStyleProvider getHeaderStyleProvider () {
    if (headerStyleProvider == null) {
      TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      tp.setTypeface(Fonts.getRobotoMedium());
      headerStyleProvider = new TextStyleProvider(tp).setTextSize(20f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(headerStyleProvider);
    }
    return headerStyleProvider;
  }


  private static TextStyleProvider textStyleProvider;

  protected static TextStyleProvider getTextStyleProvider () {
    if (textStyleProvider == null) {

      textStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(textStyleProvider);
    }
    return textStyleProvider;
  }
}
