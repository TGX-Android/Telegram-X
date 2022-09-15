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
 * File created on 18/02/2016 at 16:48
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverUpdateListener;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetThemed;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class CustomTextView extends View implements TGLegacyManager.EmojiLoadListener, RtlCheckListener, TextColorSetThemed, AttachDelegate, Destroyable, Text.TextMediaListener {
  @ThemeColorId
  private int colorId = R.id.theme_color_text,
              clickableColorId = R.id.theme_color_textLink,
              clickableHighlightColorId = R.id.theme_color_textLinkPressHighlight;
  @Nullable
  private ThemeDelegate forcedTheme;
  private final Text.ClickCallback clickCallback = new Text.ClickCallback() {
    @Nullable
    @Override
    public ThemeDelegate getForcedTheme (View view, Text text) {
      return forcedTheme;
    }
  };
  private int linkFlags = Text.ENTITY_FLAGS_NONE;
  private int maxLineCount = -1;

  private final RefreshRateLimiter refreshRateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);

  private static class TextEntry extends ListAnimator.MeasurableEntry<Text> implements AttachDelegate {
    public final ComplexReceiver receiver;

    public TextEntry (View view, ComplexReceiverUpdateListener updateListener, Text content, boolean isAttached) {
      super(content);
      if (content.hasMedia()) {
        receiver = new ComplexReceiver(updateListener != null ? null : view);
        receiver.setUpdateListener(updateListener);
        if (isAttached) {
          receiver.attach();
        } else {
          receiver.detach();
        }
        content.requestMedia(receiver);
      } else {
        receiver = null;
      }
    }

    public void invalidateMediaContent (Text text, @Nullable TextMedia specificMedia) {
      if (receiver != null && this.content == text) {
        if (!text.invalidateMediaContent(receiver, specificMedia)) {
          text.requestMedia(receiver);
        }
      }
    }

    @Override
    public void attach () {
      if (receiver != null) {
        receiver.attach();
      }
    }

    @Override
    public void detach () {
      if (receiver != null) {
        receiver.detach();
      }
    }

    @Override
    public void performDestroy () {
      super.performDestroy();
      if (receiver != null) {
        receiver.performDestroy();
      }
    }
  }

  private final Tdlib tdlib;

  private String rawText;
  private TextEntity[] entities;

  private final ReplaceAnimator<TextEntry> text = new ReplaceAnimator<>(animator -> {
    if (getMeasuredHeight() != getCurrentHeight())
      requestLayout();
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private TextStyleProvider textStyleProvider;
  private int lastMeasuredWidth;

  public CustomTextView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.textStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage())
      .setTextSize(15f);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  @Nullable
  @Override
  public ThemeDelegate forcedTheme () {
    return forcedTheme;
  }

  @Override
  public int defaultTextColorId () {
    return colorId;
  }

  @Override
  public int clickableTextColorId (boolean isPressed) {
    return clickableColorId;
  }

  @Override
  public int pressedBackgroundColorId () {
    return clickableHighlightColorId;
  }

  @Override
  public void checkRtl () {
    invalidate();
  }

  public void setTextSize (float textSize) {
    if (textStyleProvider.getTextSize() != textSize) {
      textStyleProvider.setTextSize(textSize);
      if (lastMeasuredWidth > 0 && text != null) {
        layoutText(lastMeasuredWidth, false, false, false);
      }
    }
  }

  public void setTextStyleProvider (TextStyleProvider provider) {
    if (provider == null) {
      throw new IllegalArgumentException();
    }
    textStyleProvider = provider;
  }

  public void setTextColorId (@ThemeColorId int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      invalidate();
    }
  }

  public void setLinkColorId (@ThemeColorId int linkColorId, @ThemeColorId int linkColorHighlightId) {
    this.clickableColorId = linkColorId;
    this.clickableHighlightColorId = linkColorHighlightId;
  }

  public void setForcedTheme (@Nullable ThemeDelegate theme) {
    this.forcedTheme = theme;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  public String getText () {
    return rawText;
  }

  public void setBoldText (CharSequence sequence, TextEntity[] entities, boolean animated) {
    // TODO support nested entities
    setText(sequence, new TextEntity[] {new TextEntityCustom(null, tdlib, sequence.toString(), 0, sequence.length(), TextEntityCustom.FLAG_BOLD, null)}, animated);
  }

  public void setText (CharSequence sequence, TextEntity[] entities, boolean animated) {
    String text = sequence != null ? sequence.toString() : null;
    if (sequence instanceof Spannable && (entities == null || entities.length == 0)) {
      entities = TD.collectAllEntities(null, tdlib, sequence, false, null);
    }
    if ((rawText == null && text != null) || (rawText != null && !rawText.equals(text))) {
      this.rawText = text;
      this.entities = entities;
      cancelAsyncLayout();
      if (lastMeasuredWidth > 0) {
        layoutText(lastMeasuredWidth, animated, false, true);
      }
      invalidate();
    }
  }

  public void setLinkFlags (int linkFlags) {
    this.linkFlags = linkFlags;
  }

  public void setMaxLineCount (int maxLineCount) {
    this.maxLineCount = maxLineCount;
  }

  private void cancelAsyncLayout () {
    if (asyncContextId == Long.MAX_VALUE) {
      asyncContextId = 0;
    } else {
      asyncContextId++;
    }
  }

  private long asyncContextId;

  private void dispatchAsyncText (final String text, final int textWidth, final boolean animated, final TextStyleProvider provider, final int maxLineCount, final int linkFlags, final TextEntity[] entities) {
    final long contextId = asyncContextId;
    Background.instance().post(() -> {
      final Text newText = new Text.Builder(text, textWidth, provider, this)
        .entities(Text.makeEntities(text, linkFlags, entities, tdlib, null), this)
        .view(this)
        .textFlags(Text.FLAG_BOUNDS_NOT_STRICT | Text.FLAG_CUSTOM_LONG_PRESS | Text.FLAG_CUSTOM_LONG_PRESS_NO_SHARE | Text.FLAG_TRIM_END | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0))
        .maxLineCount(maxLineCount)
        .build();
      UI.post(() -> {
        if (asyncContextId == contextId) {
          setAsyncText(newText, textWidth, animated);
        }
      });
    });
  }

  @Override
  public void onInvalidateTextMedia (Text text, @Nullable TextMedia specificMedia) {
    for (ListAnimator.Entry<TextEntry> entry : this.text) {
      if (entry.item.content == text) {
        entry.item.invalidateMediaContent(text, specificMedia);
      }
    }
    invalidate();
  }

  private int calculateTextWidth () {
    return Math.max(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), 0);
  }

  private void setAsyncText (Text text, int textWidth, boolean animated) {
    if (calculateTextWidth() == textWidth) {
      TextEntry currentText = this.text.singletonItem();
      if (currentText != null) {
        currentText.content.cancelTouch();
      }
      this.text.replace(new TextEntry(this, refreshRateLimiter, text, isAttached), animated);
      text.setViewProvider(new SingleViewProvider(this));
      if (getMeasuredHeight() != getCurrentHeight()) {
        requestLayout();
      }
      invalidate();
    } else {
      layoutText(getMeasuredWidth(), animated, false, false);
    }
  }

  private void layoutText (int width, boolean animated, boolean byLayout, boolean allowAsync) {
    if (width != lastMeasuredWidth || !byLayout) {
      TextEntry currentText = this.text.singletonItem();
      lastMeasuredWidth = width;
      if (currentText != null) {
        currentText.content.cancelTouch();
      }
      if (StringUtils.isEmpty(rawText)) {
        this.text.clear(animated);
      } else if (width > 0) {
        int textWidth = width - getPaddingLeft() - getPaddingRight();
        int currentHeight = getMeasuredHeight();

        boolean async = !byLayout && allowAsync;
        cancelAsyncLayout();

        if (async) {
          dispatchAsyncText(rawText, textWidth, animated, textStyleProvider, maxLineCount, linkFlags, entities);
        } else {
          final Text newText = new Text.Builder(rawText, textWidth, textStyleProvider, this)
            .entities(Text.makeEntities(rawText, linkFlags, entities, tdlib, null), this)
            .view(this)
            .textFlags(Text.FLAG_BOUNDS_NOT_STRICT | Text.FLAG_CUSTOM_LONG_PRESS | Text.FLAG_CUSTOM_LONG_PRESS_NO_SHARE | Text.FLAG_TRIM_END | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0))
            .maxLineCount(maxLineCount)
            .build();
          newText.setViewProvider(new SingleViewProvider(this));
          this.text.replace(new TextEntry(this, refreshRateLimiter, newText, isAttached), animated);
        }
        if (!byLayout) {
          if (currentHeight != 0 && currentHeight != getCurrentHeight()) {
            requestLayout();
          }
          invalidate();
        }
      }
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    TextEntry text = this.text.singletonItem();
    if (text == null || (linkFlags == Text.ENTITY_FLAGS_NONE && entities == null)) {
      return super.onTouchEvent(event);
    } else {
      return text.content.onTouchEvent(this, event, clickCallback);
    }
  }

  public int getCurrentHeight (int width) {
    if (text.isEmpty() || getMeasuredWidth() == 0) {
      layoutText(width, false, true, false);
    }
    return getCurrentHeight();
  }

  private int getCurrentHeight () {
    if (text == null) {
      return getPaddingTop() + getPaddingBottom();
    }
    int height = getLayoutParams() != null ? getLayoutParams().height : ViewGroup.LayoutParams.WRAP_CONTENT;
    if (height == ViewGroup.LayoutParams.WRAP_CONTENT) {
      height = getPaddingTop() + Math.round(text.getMetadata().getTotalHeight()) + getPaddingBottom();
    } else if (text != null) {
      return Math.max(getPaddingTop() + Math.round(text.getMetadata().getTotalHeight()) + getPaddingBottom(), height);
    }
    return height;
  }

  private int lastKnownHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    if (width <= 0 || getVisibility() == GONE) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } else {
      layoutText(width, false, true, false);
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCurrentHeight(), MeasureSpec.EXACTLY));
    }
    int newHeight = getMeasuredHeight();
    if (lastKnownHeight != newHeight) {
      this.lastKnownHeight = newHeight;
      if (heightChangeListener != null) {
        heightChangeListener.onHeightChanged(this, newHeight);
      }
    }
  }

  public interface HeightChangeListener {
    void onHeightChanged (CustomTextView textView, int newHeight);
  }

  private HeightChangeListener heightChangeListener;

  public void setHeightChangeListener (HeightChangeListener heightChangeListener) {
    this.heightChangeListener = heightChangeListener;
  }

  @Nullable
  private TextColorSet colorSet;

  public void setTextColorSet (@Nullable TextColorSet colorSet) {
    if (this.colorSet != colorSet) {
      this.colorSet = colorSet;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    for (ListAnimator.Entry<TextEntry> entry : text) {
      entry.item.content.draw(c, getPaddingLeft(), getMeasuredWidth() - getPaddingRight(), 0, getPaddingTop(), colorSet, entry.getVisibility(), entry.item.receiver);
    }
  }

  private boolean isAttached = true;

  @Override
  public void attach () {
    if (!isAttached) {
      isAttached = true;
      for (ListAnimator.Entry<TextEntry> entry : text) {
        entry.item.attach();
      }
    }
  }

  @Override
  public void detach () {
    if (isAttached) {
      isAttached = false;
      for (ListAnimator.Entry<TextEntry> entry : text) {
        entry.item.detach();
      }
    }
  }

  @Override
  public void performDestroy () {
    for (ListAnimator.Entry<TextEntry> entry : text) {
      entry.item.performDestroy();
    }
    text.clear(false);
  }
}
