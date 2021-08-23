/**
 * File created on 25/03/16 at 23:46
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.unit.BitwiseUtils;

public class TextWrapper implements ListAnimator.Measurable {
  private static final int PORTRAIT_INDEX = 0;
  private static final int LANDSCAPE_INDEX = 1;

  private final int[] sizes;
  private final int[] textSizes;
  private final Text[] texts;

  private final String text;
  private final TextStyleProvider textStyleProvider;
  private final TextColorSet colorTheme;

  private @Nullable TextEntity[] entities;

  private boolean isPortrait;
  private int maxLines;

  private int textFlags;

  private TextWrapper (String text, TextStyleProvider textStyleProvider, TextColorSet colorTheme) {
    this.sizes = new int[2];
    this.texts = new Text[2];
    this.textSizes = new int[2];
    this.text = text;
    this.textStyleProvider = textStyleProvider;
    this.colorTheme = colorTheme;
    this.maxLines = -1;
  }

  public TextWrapper (String text, TextStyleProvider textStyleProvider, @NonNull TextColorSet colorTheme, @Nullable TextEntity[] entities) {
    this(text, textStyleProvider, colorTheme);
    this.entities = entities;
  }

  public TextWrapper (Tdlib tdlib, String text, TextStyleProvider styleProvider, @NonNull TextColorSet colorTheme, int linkFlags, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    this(text, styleProvider, colorTheme);
    this.entities = Text.makeEntities(text, linkFlags, entities, tdlib, openParameters);
  }

  public TextWrapper (Tdlib tdlib, String text, TextStyleProvider styleProvider, @NonNull TextColorSet colorTheme, int linkFlags, @Nullable TdlibUi.UrlOpenParameters openParameters, int maxLines) {
    this(text, styleProvider, colorTheme);
    this.entities = Text.makeEntities(text, linkFlags, null, tdlib, openParameters);
    this.maxLines = maxLines;
  }

  private TextWrapper setTextFlags (int textFlags) {
    if (this.textFlags != textFlags) {
      this.textFlags = textFlags;
      for (Text text : texts) {
        if (text != null)
          text.setTextFlags(textFlags);
      }
    }
    return this;
  }

  public TextStyleProvider getTextStyleProvider () {
    return textStyleProvider;
  }

  public TextColorSet getTextColorSet () {
    return colorTheme;
  }

  public TextWrapper setTextFlagEnabled (int flag, boolean enabled) {
    return setTextFlags(BitwiseUtils.setFlag(textFlags, flag, enabled));
  }

  public TextWrapper addTextFlags (int textFlags) {
    return setTextFlags(this.textFlags | textFlags);
  }

  public TextWrapper setMaxLines (int maxLines) {
    this.maxLines = maxLines;
    return this;
  }

  private Text.LineWidthProvider lineWidthProvider;

  public TextWrapper setLineWidthProvider (Text.LineWidthProvider provider) {
    this.lineWidthProvider = provider;
    return this;
  }

  public int getMaxLines () {
    return this.maxLines;
  }

  public Text prepare (int maxWidth) {
    this.isPortrait = UI.isPortrait();
    return get(maxWidth);
  }

  public @Nullable Text getCurrent () {
    Text text = texts[isPortrait ? PORTRAIT_INDEX : LANDSCAPE_INDEX];
    if (text == null) {
      text = texts[isPortrait ? LANDSCAPE_INDEX : PORTRAIT_INDEX];
      return text != null && text.getLineCount() == 1 && !text.isEmpty() ? text : null;
    }
    return text;
  }

  public @Nullable Text get (int maxWidth) {
    return getInternal(isPortrait ? PORTRAIT_INDEX : LANDSCAPE_INDEX, maxWidth);
  }

  public void requestIcons (ComplexReceiver receiver) {
    Text text = getCurrent();
    if (text != null) {
      text.requestIcons(receiver, -1);
    } else {
      FormattedText.requestIcons(entities, receiver, -1);
    }
  }

  private Text getInternal (int index, int maxWidth) {
    if (maxWidth <= 0) {
      return null;
    }

    int textSizePx = textStyleProvider.getTextSizeInPixels();

    // In case there's already some text that did entirely fit to less or equal maxWidth,
    // then use it. Little optimisation for single-word messages, for example
    if (texts[index] == null && maxLines == -1) {
      int i = index == PORTRAIT_INDEX ? LANDSCAPE_INDEX : PORTRAIT_INDEX;
      if (texts[i] != null && sizes[i] <= maxWidth && texts[i].getMaxWidth() <= maxWidth && !texts[i].isEmpty() && texts[i].getLineCount() == 1 && textSizes[i] == textSizePx) {
        return texts[i];
      }
    }

    boolean sizeChanged = textSizes[index] != textSizePx || (texts[index] != null && texts[index].getMaxLineCount() != maxLines);
    if (sizeChanged || texts[index] == null || sizes[index] != maxWidth) {
      boolean needBigEmoji = BitwiseUtils.getFlag(textFlags, Text.FLAG_BIG_EMOJI) && Settings.instance().useBigEmoji();
      if (texts[index] != null && !sizeChanged && !needBigEmoji) {
        texts[index].set(maxWidth, text);
      } else {
        Text.Builder b = new Text.Builder(this.text, maxWidth, textStyleProvider, colorTheme).maxLineCount(maxLines).entities(entities).lineWidthProvider(lineWidthProvider).textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_BIG_EMOJI, false));
        Text text = b.build();
        if (needBigEmoji) {
          // TODO move inside Text class
          int emojiCount = text.getEmojiOnlyCount();
          if (emojiCount >= 1 && emojiCount <= SCALABLE_EMOJI_COUNT) {
            float textSizeDp = textStyleProvider.getTextSizeInDp();
            float maxEmojiSize = Math.min(Settings.CHAT_FONT_SIZE_DEFAULT + 4f, textSizeDp) + 12f;
            if (maxEmojiSize > textSizeDp) {
              float desiredEmojiSize = maxEmojiSize - (maxEmojiSize - textSizeDp) / SCALABLE_EMOJI_COUNT * (emojiCount - 1);
              if (desiredEmojiSize > textSizeDp) {
                TextStyleProvider newProvider = new TextStyleProvider(textStyleProvider.getTextPaintStorage()).setTextSize(desiredEmojiSize).setAllowSp(true);
                text = b.textFlags(textFlags).styleProvider(newProvider).build();
              }
            }
          }
        }
        texts[index] = text;
      }
      texts[index].setViewProvider(viewProvider);
      sizes[index] = maxWidth;
      textSizes[index] = textSizePx;
    }
    return texts[index];
  }

  private static final int SCALABLE_EMOJI_COUNT = 3;

  @Override
  public int getWidth () {
    final Text text = getCurrent();
    return text == null ? 0 : text.getWidth();
  }

  public int getLastLineWidth () {
    final Text text = getCurrent();
    return text == null ? -1 : text.getLastLineWidth();
  }

  public int getLineWidth (int lineIndex) {
    final Text text = getCurrent();
    return text == null ? -1 : text.getLineWidth(lineIndex);
  }

  public boolean getLastLineIsRtl () {
    final Text text = getCurrent();
    return text != null && text.getLastLineIsRtl();
  }

  @Override
  public int getHeight () {
    final Text text = getCurrent();
    return text == null ? 0 : text.getHeight();
  }

  public int getLineCenterY () {
    final Text text = getCurrent();
    return text == null ? 0 : text.getLineCenterY();
  }

  public int getLineHeight () {
    final Text text = getCurrent();
    return text == null ? 0 : text.getLineHeight();
  }

  public int getLineCount () {
    final Text text = getCurrent();
    return text == null ? 0 : text.getLineCount();
  }

  public String getText () {
    return text;
  }

  public TextEntity[] getEntities () {
    return entities;
  }

  private MultipleViewProvider currentViews;
  private ViewProvider viewProvider;

  public TextWrapper setViewProvider (ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    if (texts[PORTRAIT_INDEX] != null) {
      texts[PORTRAIT_INDEX].setViewProvider(viewProvider);
    }
    if (texts[LANDSCAPE_INDEX] != null) {
      texts[LANDSCAPE_INDEX].setViewProvider(viewProvider);
    }
    return this;
  }

  public void attachToView (View view) {
    if (currentViews == null) {
      currentViews = new MultipleViewProvider();
    }
    currentViews.attachToView(view);
    setViewProvider(currentViews);
  }

  public void detachFromView (View view) {
    if (currentViews != null) {
      currentViews.detachFromView(view);
    }
  }

  public final void draw (Canvas c, int startX, int startY) {
    draw(c, startX, startY, null, 1f);
  }

  public final void draw (Canvas c, int startX, int startY, TextColorSet colorTheme, float alpha) {
    final Text text = getCurrent();
    if (text != null) {
      text.draw(c, startX, startX, 0, startY, colorTheme, alpha);
    }
  }

  public final void draw (Canvas c, int startX, int endX, int endXPadding, int startY) {
    draw(c, startX, endX, endXPadding, startY, null, 1f);
  }

  public final int getTextColor () {
    final Text text = getCurrent();
    return text != null ? text.getTextColor() : 0;
  }

  public final void draw (Canvas c, int startX, int endX, int endXPadding, int startY, TextColorSet colorTheme, float alpha) {
    draw(c, startX, endX, endXPadding, startY, colorTheme, alpha, null);
  }

  public final void draw (Canvas c, int startX, int endX, int endXPadding, int startY, TextColorSet colorTheme, float alpha, @Nullable ComplexReceiver iconReceiver) {
    final Text text = getCurrent();
    if (text != null) {
      text.draw(c, startX, endX, endXPadding, startY, colorTheme, alpha, iconReceiver);
    }
  }

  private Text.ClickCallback clickCallback;

  public TextWrapper setClickCallback (Text.ClickCallback clickCallback) {
    this.clickCallback = clickCallback;
    return this;
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return onTouchEvent(view, e, null);
  }

  public boolean onTouchEvent (View view, MotionEvent e, Text.ClickCallback clickCallback) {
    final Text text = getCurrent();
    return text != null && text.onTouchEvent(view, e, clickCallback != null ? clickCallback : this.clickCallback);
  }

  public boolean performLongPress (View view) {
    final Text text = getCurrent();
    return text != null && text.performLongPress(view);
  }

  public static TextWrapper parseRichText (ViewController<?> context, @Nullable Text.ClickCallback callback, TdApi.RichText richText, TextStyleProvider textStyleProvider, @NonNull TextColorSet colorTheme, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    FormattedText formattedText = FormattedText.parseRichText(context, richText, openParameters);
    return new TextWrapper(formattedText.text, textStyleProvider, colorTheme, formattedText.entities)
      .addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS)
      .setClickCallback(callback);
  }
}
