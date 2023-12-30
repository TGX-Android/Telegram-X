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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.user.BubbleWrapView2;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGCountry;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import kotlin.random.Random;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class TGMessageGiveaway extends TGMessage implements TGInlineKeyboard.ClickListener {
  private final static int BLOCK_MARGIN = 18;

  private final @Nullable TdApi.MessagePremiumGiveaway giveawayContent;
  private final @Nullable TdApi.MessagePremiumGiveawayWinners giveawayWinners;

  public TGMessageGiveaway (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessagePremiumGiveaway giveawayContent) {
    super(manager, msg);
    this.giveawayContent = giveawayContent;
    this.giveawayWinners = null;
  }

  public TGMessageGiveaway (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessagePremiumGiveawayWinners giveawayWinners) {
    super(manager, msg);
    this.giveawayContent = null;
    this.giveawayWinners = giveawayWinners;
  }

  private int getWinnersCount () {
    return giveawayContent != null ? giveawayContent.winnerCount : giveawayWinners != null ? giveawayWinners.winnerCount : 0;
  }

  private int getMonthCount () {
    return giveawayContent != null ? giveawayContent.monthCount : giveawayWinners != null ? giveawayWinners.monthCount : 0;
  }

  private TGInlineKeyboard rippleButton;
  private Particle[] particles;
  private final RectF outlineCounterRect = new RectF();
  private final RectF backgroundCounterRect = new RectF();
  private int giftDrawableY;

  private Counter participantsCounter;
  private int participantsCounterY;


  private int contentDrawY;
  private Content content;

  private int rippleButtonY;
  private int contentHeight = 0;

  @Override
  protected void buildContent (int maxWidth) {
    contentHeight = Screen.dp(30);
    giftDrawableY = contentHeight;

    /* * */

    participantsCounterY = giftDrawableY + Screen.dp(73.5f);
    participantsCounter = new Counter.Builder().allBold(true).textSize(11).noBackground().textColor(ColorId.text).build();
    participantsCounter.setCount(getWinnersCount(), false, "x" + getWinnersCount(), false);
    backgroundCounterRect.set(
      maxWidth / 2f - participantsCounter.getWidth() / 2f - Screen.dp(8),
      participantsCounterY - Screen.dp(23 / 2f),
      maxWidth / 2f + participantsCounter.getWidth() / 2f + Screen.dp(8),
      participantsCounterY + Screen.dp(23 / 2f)
    );
    outlineCounterRect.set(backgroundCounterRect);
    outlineCounterRect.inset(-Screen.dp(3), -Screen.dp(3));
    contentHeight += Screen.dp(72); // gift drawable height
    contentHeight += Screen.dp(35); // gift drawable margin

    /* * */

    contentDrawY = contentHeight;
    content = new Content(this, maxWidth);

    if (giveawayContent != null) {
      content.add(Lang.boldify(Lang.getString(R.string.GiveawayPrizes)));
      content.padding(Screen.dp(6));
      content.add(Lang.pluralBold(R.string.xGiveawayPrizePremiumInfo, getWinnersCount(), getMonthCount()));

      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(Lang.boldify(Lang.getString(R.string.GiveawayParticipants)));
      content.padding(Screen.dp(6));
      content.add(Lang.getString(giveawayContent.parameters.onlyNewMembers ? R.string.GiveawayParticipantsNew : R.string.GiveawayParticipantsAll));
      content.padding(Screen.dp(6));
      content.add(new ContentBubbles(this, maxWidth)
        .addChatId(giveawayContent.parameters.boostedChatId)
        .addChatIds(giveawayContent.parameters.additionalChatIds));

      if (giveawayContent.parameters.countryCodes.length > 0) {
        StringBuilder sb = new StringBuilder();
        for (String countryCode : giveawayContent.parameters.countryCodes) {
          if (sb.length() > 0) {
            sb.append(Lang.getConcatSeparator());
          }
          String[] info = TGCountry.instance().find(countryCode);
          sb.append(info != null ? info[2] : countryCode);
        }
        content.padding(Screen.dp(6));
        content.add(Lang.getString(R.string.GiveawayCountries, sb));
      }

      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinnersSelectionDateHeader)));
      content.padding(Screen.dp(6));
      content.add(Lang.getString(R.string.GiveawayWinnersSelectionDate,
        Lang.dateYearFull(giveawayContent.parameters.winnersSelectionDate, TimeUnit.SECONDS),
        Lang.time(giveawayContent.parameters.winnersSelectionDate, TimeUnit.SECONDS)));
    }

    if (giveawayWinners != null) {
      content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinnersSelected)));
      content.padding(Screen.dp(6));
      content.add(Lang.pluralBold(R.string.xGiveawayWinnersSelectedInfo, getWinnersCount()));

      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinners)));
      content.padding(Screen.dp(6));
      content.add(new ContentBubbles(this, maxWidth).addChatIds(giveawayWinners.winnerUserIds));

      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(Lang.getString(R.string.GiveawayAllWinnersReceivedLinks));
    }

    contentHeight += content.getHeight();
    contentHeight += Screen.dp(BLOCK_MARGIN);

    rippleButtonY = contentHeight;
    rippleButton = new TGInlineKeyboard(this, false);
    rippleButton.setCustom(0, Lang.getString(R.string.GiveawayLearnMore), maxWidth, false, this);
    rippleButton.setViewProvider(currentViews);

    contentHeight += TGInlineKeyboard.getButtonHeight();
    contentHeight += Screen.dp(BLOCK_MARGIN) / 2;


    int particlesCount = (int) (Screen.px(maxWidth) * Screen.px(contentHeight) / 2000f);
    particles = new Particle[particlesCount];
    for (int a = 0; a < particlesCount; a++) {
      particles[a] = new Particle(
        MathUtils.random(0, 3),
        particleColors[MathUtils.random(0, 5)],
        MathUtils.random(0, maxWidth),
        MathUtils.random(0, contentHeight),
        1f + Random.Default.nextFloat(),
        Random.Default.nextFloat() * 360f
      );
    }

    invalidateGiveawayReceiver();
  }

  @Override
  public void requestGiveawayAvatars (ComplexReceiver complexReceiver, boolean isUpdate) {
    content.requestFiles(complexReceiver);
  }

  private static final int[] particleColors = new int[]{
    ColorId.confettiGreen,
    ColorId.confettiBlue,
    ColorId.confettiYellow,
    ColorId.confettiRed,
    ColorId.confettiCyan,
    ColorId.confettiPurple
  };

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    c.save();
    c.translate(startX, startY);

    /*for (Particle particle : particles) {
      c.save();
      c.scale(particle.scale, particle.scale, particle.x, particle.y);
      c.rotate(particle.angle, particle.x, particle.y);
      c.drawRect(
        particle.x - Screen.dp(2), particle.y - Screen.dp(2),
        particle.x + Screen.dp(2), particle.y + Screen.dp(2),
        Paints.fillingPaint(ColorUtils.alphaColor(0.4f, Theme.getColor(particle.color))));
      c.restore();
    }*/

    final int contentWidth = getContentWidth();
    final float contentCenterX = contentWidth / 2f;

    final Drawable giftIcon = view.getSparseDrawable(R.drawable.baseline_gift_72, ColorId.NONE);
    if (giftIcon != null) {
      Drawables.draw(c, giftIcon, contentCenterX - giftIcon.getMinimumWidth() / 2f, giftDrawableY, PorterDuffPaint.get(ColorId.icon));
    }

    final float cor = Math.min(outlineCounterRect.width(), outlineCounterRect.height()) / 2f;
    final float cbr = Math.min(backgroundCounterRect.width(), backgroundCounterRect.height()) / 2f;

    c.drawRoundRect(outlineCounterRect, cor, cor, Paints.fillingPaint(Theme.getColor(useBubbles() ? ColorId.filling : ColorId.background)));
    c.drawRoundRect(backgroundCounterRect, cbr, cbr, Paints.fillingPaint(getCounterBackgroundColor()));

    participantsCounter.draw(c, contentCenterX, participantsCounterY, Gravity.CENTER, 1f);

    content.draw(c, view, 0, contentDrawY);

    c.restore();



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
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (rippleButton != null && rippleButton.onTouchEvent(view, e)) {
      return true;
    }
    return super.onTouchEvent(view, e);
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return (rippleButton != null && rippleButton.performLongPress(view)) || res;
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {

  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return TGInlineKeyboard.ClickListener.super.onLongClick(view, keyboard, button);
  }

  private static class Particle {
    public final int type;
    public final int color;
    public final float scale;
    public final float angle;
    public final int x;
    public final int y;

    public Particle (int type, int color, int x, int y, float scale, float angle) {
      this.type = type;
      this.color = color;
      this.x = x;
      this.y = y;
      this.scale = scale;
      this.angle = angle;
    }
  }

  private static class Block {
    private final TextWrapper[] texts;

    public Block (TGMessageGiveaway messageGiveaway, CharSequence ...texts) {
      this.texts = new TextWrapper[texts.length];
      for (int a = 0; a < texts.length; a++) {
        this.texts[a] = genTextWrapper(messageGiveaway, texts[a]);
      }
    }

    public void build (int maxWidth) {
      for (TextWrapper text : texts) {
        text.prepare(maxWidth);
      }
    }

    public int getHeight () {
      int height = Screen.dp(6 * (texts.length - 1));
      for (TextWrapper text : texts) {
        height += text.getHeight();
      }
      return height;
    }

    public void draw (Canvas c, int x, int y) {
      for (TextWrapper text : texts) {
        text.draw(c, x, y);
        y += text.getHeight() + Screen.dp(6);
      }
    }
  }



  private static TextStyleProvider giveawayStyleProvider;

  private static TextStyleProvider getGiveawayTextStyleProvider () {
    if (giveawayStyleProvider == null) {
      giveawayStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(giveawayStyleProvider);
    }
    return giveawayStyleProvider;
  }



  /* * */

  private static class Content {
    private final ArrayList<ContentPart> parts = new ArrayList<>();
    private final TGMessage msg;
    private final int maxWidth;
    private int y;

    public Content (TGMessage msg, int maxWidth) {
      this.maxWidth = maxWidth;
      this.msg = msg;
    }

    public void add (CharSequence text) {
      add(new ContentText(msg, text));
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
      c.save();
      c.translate(x, y);
      for (ContentPart p : parts) {
        p.draw(c, v);
      }
      c.restore();
    }

    public void requestFiles (ComplexReceiver complexReceiver) {
      for (ContentPart p : parts) {
        p.requestFiles(complexReceiver);
      }
    }
  }

  private static abstract class ContentPart {
    protected int y;

    public void setY (int y) {
      this.y = y;
    }

    public abstract void build (int width);
    public abstract int getHeight ();
    public abstract void draw (Canvas c, MessageView v);
    public abstract void requestFiles (ComplexReceiver r);
  }

  private static class ContentText extends ContentPart {
    private final TextWrapper text;

    public ContentText (TGMessage msg, CharSequence text) {
      this.text = genTextWrapper(msg, text);
    }

    @Override
    public void build (int width) {
      text.prepare(width);
    }

    @Override
    public int getHeight () {
      return text.getHeight();
    }

    @Override
    public void draw (Canvas c, MessageView v) {
      text.draw(c, 0, y);
    }

    @Override
    public void requestFiles (ComplexReceiver r) {

    }
  }

  private static class ContentBubbles extends ContentPart {
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

    @Override
    public void build (int width) {
      layout.buildLayout(width);
    }

    @Override
    public int getHeight () {
      return layout.getCurrentHeight();
    }

    @Override
    public void draw (Canvas c, MessageView v) {
      layout.draw(c, v.getGiveawayAvatarsReceiver(), 0, y);
    }

    @Override
    public void requestFiles (ComplexReceiver r) {
      layout.requestFiles(r);
    }
  }

  private static TextWrapper genTextWrapper (TGMessage message, CharSequence text) {
    return new TextWrapper(message.tdlib(), TD.toFormattedText(text, false), getGiveawayTextStyleProvider(), message.getTextColorSet(), null, null)
      .setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true)
      .setViewProvider(message.currentViews);
  }
}
