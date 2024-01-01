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
 * File created on 06/11/2023
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.user.BubbleWrapView2;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.GiftHeaderView;

import java.util.ArrayList;

import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.lambda.RunnableData;

public abstract class TGMessageGiveawayBase extends TGMessage implements TGInlineKeyboard.ClickListener {
  protected final static int BLOCK_MARGIN = 18;

  private GiftHeaderView.ParticlesDrawable particlesDrawable;
  private int contentHeight = 0;

  private TGInlineKeyboard rippleButton;
  private int rippleButtonY;

  protected TGMessageGiveawayBase (MessagesManager manager, TdApi.Message msg) {
    super(manager, msg);
  }

  @Override
  protected final void buildContent (int maxWidth) {
    contentHeight = onBuildContent(maxWidth);

    contentHeight += Screen.dp(BLOCK_MARGIN);

    rippleButtonY = contentHeight;
    rippleButton = new TGInlineKeyboard(this, false);
    rippleButton.setCustom(0, getButtonText(), maxWidth, false, this);
    rippleButton.setViewProvider(currentViews);

    contentHeight += TGInlineKeyboard.getButtonHeight();
    contentHeight += Screen.dp(BLOCK_MARGIN) / 2;

    if (particlesDrawable == null) {
      particlesDrawable = new GiftHeaderView.ParticlesDrawable(maxWidth, contentHeight);
    } else {
      particlesDrawable.setBounds(0, 0, maxWidth, contentHeight);
    }

    invalidateGiveawayReceiver();
  }

  protected abstract int onBuildContent (int maxWidth);

  protected abstract String getButtonText ();

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    if (particlesDrawable != null) {
      c.save();
      c.translate(startX, startY);
      particlesDrawable.draw(c);
      c.restore();
    }

    if (rippleButton != null) {
      rippleButton.draw(view, c, startX, startY + rippleButtonY);
    }
  }

  public int getCounterBackgroundColor () {
    if (!useBubbles()) {
      return Theme.getColor(ColorId.fillingPositive); //, ColorId.fillingActive);
    } else if (isOutgoing()) {
      return Theme.getColor(ColorId.bubbleOut_fillingPositive); //, ColorId.bubbleOut_fillingActive);
    } else {
      return Theme.getColor(ColorId.bubbleIn_fillingPositive); //, ColorId.bubbleIn_fillingActive);
    }
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }



  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return (rippleButton != null && rippleButton.performLongPress(view)) || res;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (rippleButton != null && rippleButton.onTouchEvent(view, e)) {
      return true;
    }
    return super.onTouchEvent(view, e);
  }



  /* * */

  private static TextStyleProvider giveawayStyleProvider;

  protected static TextStyleProvider getGiveawayTextStyleProvider () {
    if (giveawayStyleProvider == null) {
      giveawayStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(giveawayStyleProvider);
    }
    return giveawayStyleProvider;
  }



  /* * */

  public static class Content {
    private final ArrayList<ContentPart> parts = new ArrayList<>();
    private final int maxWidth;
    private int y;

    public Content (int maxWidth) {
      this.maxWidth = maxWidth;
    }

    public void add (CharSequence text, TextColorSet colorSet, ViewProvider viewProvider) {
      add(new ContentText(genTextWrapper(text, colorSet, viewProvider)));
    }

    public void add (TextWrapper textWrapper) {
      add(new ContentText(textWrapper));
    }

    public void add (ContentPart p) {
      parts.add(p);
      p.setY(y);
      p.build(maxWidth);
      y += p.getHeight();
    }

    public void padding (int padding) {
      y += padding;
    }

    public int getHeight () {
      return y;
    }

    public void draw (Canvas c, MessageView v, int x, int y) {
      for (ContentPart p : parts) {
        p.draw(c, v, x, y);
      }
    }

    public void requestFiles (ComplexReceiver complexReceiver) {
      for (ContentPart p : parts) {
        p.requestFiles(complexReceiver);
      }
    }

    public boolean onTouchEvent (MessageView view, MotionEvent e) {
      for (ContentPart p : parts) {
        if (p.onTouchEvent(view, e)) {
          return true;
        }
      }
      return false;
    }
  }

  public static abstract class ContentPart {
    protected int y;

    public void setY (int y) {
      this.y = y;
    }

    public abstract void build (int width);
    public abstract int getHeight ();
    public abstract void draw (Canvas c, MessageView v, int x, int y);
    public abstract void requestFiles (ComplexReceiver r);

    public boolean onTouchEvent (MessageView view, MotionEvent e) {
      return false;
    }
  }

  public static class ContentText extends ContentPart {
    private final TextWrapper textWrapper;
    private final Text text;

    public ContentText (TextWrapper wrapper) {
      this.textWrapper = wrapper;
      this.text = null;
    }

    public ContentText (Text text) {
      this.textWrapper = null;
      this.text = text;
    }

    @Override
    public void build (int width) {
      if (textWrapper != null) {
        textWrapper.prepare(width);
      }
    }

    @Override
    public int getHeight () {
      return textWrapper != null ? textWrapper.getHeight() : text.getHeight();
    }

    @Override
    public void draw (Canvas c, MessageView v, int x, int y) {
      if (textWrapper != null) {
        textWrapper.draw(c, x, y + this.y);
      } else if (text != null) {
        text.draw(c, x, y + this.y);
      }
    }

    @Override
    public void requestFiles (ComplexReceiver r) {

    }
  }

  public static class ContentBubbles extends ContentPart {
    private final BubbleWrapView2 layout;
    private final Tdlib tdlib;
    private final int maxTextWidth;

    public ContentBubbles (TGMessage msg, int maxTextWidth) {
      this.layout = new BubbleWrapView2(msg.tdlib);
      this.maxTextWidth = maxTextWidth;
      this.tdlib = msg.tdlib;
    }

    public ContentBubbles addChatId (long chatId) {
      layout.addBubble(tdlib.sender(chatId), maxTextWidth);
      return this;
    }

    public ContentBubbles addChatIds (long[] chatIds) {
      for (long chatId : chatIds) {
        addChatId(chatId);
      }
      return this;
    }

    public ContentBubbles setOnClickListener (RunnableData<TdApi.MessageSender> onClickListener) {
      layout.setOnClickListener(onClickListener);
      return this;
    }

    @Override
    public void build (int width) {
      layout.buildLayout(width);
    }

    @Override
    public int getHeight () {
      return layout.getCurrentHeight();
    }

    int lastDrawX = 0;
    int lastDrawY = 0;

    @Override
    public void draw (Canvas c, MessageView v, int x, int y) {
      layout.draw(c, v.getGiveawayAvatarsReceiver(), lastDrawX = x, lastDrawY = (y + this.y));
    }

    @Override
    public void requestFiles (ComplexReceiver r) {
      layout.requestFiles(r);
    }

    public boolean onTouchEvent (MessageView view, MotionEvent e) {
      return layout.onTouchEvent(view, e, lastDrawX, lastDrawY);
    }
  }

  public static class ContentDrawable extends ContentPart {
    private final Drawable drawable;
    private int width;

    public ContentDrawable (@DrawableRes int drawableRes) {
      drawable = Drawables.get(drawableRes);
    }

    @Override
    public void build (int width) {
      this.width = width;
    }

    @Override
    public int getHeight () {
      return drawable.getMinimumHeight();
    }

    @Override
    public void draw (Canvas c, MessageView v, int x, int y) {
      Drawables.draw(c, drawable, x + (width - drawable.getMinimumWidth()) / 2f, this.y + y, PorterDuffPaint.get(ColorId.icon));
    }

    @Override
    public void requestFiles (ComplexReceiver r) {

    }
  }

  private static TextWrapper genTextWrapper (CharSequence text, TextColorSet textColorSet, ViewProvider viewProvider) {
    return new TextWrapper(null, TD.toFormattedText(text, false), getGiveawayTextStyleProvider(), textColorSet, null, null)
      .setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true).setViewProvider(viewProvider);
  }
}
