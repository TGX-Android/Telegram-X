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
 * File created on 02/05/2015 at 13:00
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.StickyElements;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.DiffMatchPatch;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.EmojiBidUtil;
import org.thunderdog.challegram.tool.EmojiBidUtilLegacy;
import org.thunderdog.challegram.tool.EmojiCode;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.bidi.BiDiEntity;
import org.thunderdog.challegram.util.text.bidi.BiDiUtils;
import org.thunderdog.challegram.util.text.counter.CounterTextPart;
import org.thunderdog.challegram.util.text.quotes.QuoteBackground;

import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animatorx.FloatAnimator;
import me.vkryl.android.animatorx.FloatAnimatorListener;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;

public class Text implements Runnable, Emoji.CountLimiter, CounterTextPart, ListAnimator.Measurable, Destroyable {
  public static final int FLAG_NO_TRIM = 1;
  public static final int FLAG_ALIGN_CENTER = 1 << 1;
  public static final int FLAG_ALL_BOLD = 1 << 2;
  public static final int FLAG_CUSTOM_LONG_PRESS = 1 << 3;
  public static final int FLAG_CUSTOM_LONG_PRESS_NO_SHARE = 1 << 4;
  public static final int FLAG_ARTICLE = 1 << 5;
  public static final int FLAG_BOUNDS_NOT_STRICT = 1 << 6;
  public static final int FLAG_ADJUST_TO_CURRENT_WIDTH = 1 << 7;
  public static final int FLAG_ALIGN_RIGHT = 1 << 8;
  public static final int FLAG_BIG_EMOJI = 1 << 9; // Used by TextWrapper. TODO: make all detection & size changes inside of this class for better performance.
  public static final int FLAG_ELLIPSIZE_NO_FILL = 1 << 10;
  public static final int FLAG_ELLIPSIZE_MIDDLE = 1 << 11; // TODO
  public static final int FLAG_NEED_CLIP_TEXT_AREA = 1 << 12;
  public static final int FLAG_ELLIPSIZE_NEWLINE = 1 << 13;
  public static final int FLAG_IGNORE_CONTINUOUS_NEWLINES = 1 << 14;
  public static final int FLAG_IGNORE_NEWLINES = 1 << 15;
  public static final int FLAG_ANIMATED_EMOJI = 1 << 16; // TODO
  public static final int FLAG_ALL_CLICKABLE = 1 << 17;
  public static final int FLAG_NO_CLICKABLE = 1 << 18;
  public static final int FLAG_TRIM_END = 1 << 19;
  public static final int FLAG_NO_SPACING = 1 << 20;
  public static final int FLAG_ALWAYS_BREAK = 1 << 21;
  public static final int FLAG_WRAP_CODE_BLOCKS = 1 << 22;

  private static final int FLAG_DESTROYED = 1 << 23;
  private static final int FLAG_IN_LONG_PRESS = 1 << 24;
  private static final int FLAG_ABORT_PROCESS = 1 << 25;
  private static final int FLAG_FAKE_BOLD = 1 << 26;
  private static final int FLAG_FULL_RTL = 1 << 27;
  private static final int FLAG_ELLIPSIZED = 1 << 29;
  private static final int FLAG_NEED_BACKGROUND = 1 << 30;
  private static final int FLAG_HAS_SPOILERS = 1 << 31;

  public static final int ENTITY_FLAG_URL = 1;
  public static final int ENTITY_FLAG_USERNAME = 1 << 1;
  public static final int ENTITY_FLAG_HASHTAG = 1 << 2;
  public static final int ENTITY_FLAG_COMMAND = 1 << 3;
  public static final int ENTITY_FLAG_TON_ADDRESS = 1 << 4;

  public static final int ENTITY_FLAGS_EXTERNAL = ENTITY_FLAG_URL;
  public static final int ENTITY_FLAGS_ALL_NO_COMMANDS = ENTITY_FLAGS_EXTERNAL | ENTITY_FLAG_USERNAME | ENTITY_FLAG_HASHTAG | ENTITY_FLAG_TON_ADDRESS;
  public static final int ENTITY_FLAGS_ALL = ENTITY_FLAGS_ALL_NO_COMMANDS | ENTITY_FLAG_COMMAND;
  public static final int ENTITY_FLAGS_NONE = 0;

  public static final int LINE_COUNT_UNLIMITED = -1;

  public interface LineWidthProvider {
    int provideLineWidth (int lineIndex, int y, int defaultMaxWidth, int lineHeight);
  }

  public interface LineMarginProvider {
    int provideLineMargin (int lineIndex, int y, int defaultMaxWidth, int lineHeight);
  }

  public interface ClickListener {
    boolean onClick (View v, Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters);
  }

  public interface TextMediaListener {
    void onInvalidateTextMedia (Text text, @Nullable TextMedia specificMedia);
  }

  private int maxWidth, textFlags;

  private final @Nullable LineWidthProvider lineWidthProvider;
  private final @Nullable BlockSwitches blockSwitches;
  // Found text, which a collapsed quote shows
  private final @Nullable Highlight highlight;
  private final @Nullable LineMarginProvider lineMarginProvider;
  private final @Nullable ClickListener clickListener;
  private final @Nullable TextMediaListener textMediaListener;
  private final int maxLineCount;
  private final @NonNull TextStyleProvider textStyleProvider;
  private final @NonNull TextColorSet defaultTextColorSet;
  private final String suffix;
  private final int suffixWidth;

  private static class Background {
    public final Path path;
    public TextColorSet theme;

    public Background (Path path, TextColorSet theme) {
      this.path = path;
      this.theme = theme;
    }
  }

  private class Spoiler implements Destroyable {
    private final int startPartIndex;
    private int partsCount;
    private int offset = -1, length;
    private final Path path = new Path();

    private final BoolAnimator isRevealed;

    public Spoiler (int startPartIndex, @Nullable Spoiler continued) {
      this.startPartIndex = startPartIndex;
      // Parts of one spoiler cut by the edge of a collapsed quote are revealed together
      if (continued != null) {
        this.isRevealed = continued.isRevealed;
        return;
      }
      this.isRevealed = new BoolAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          if (viewProvider != null) {
            viewProvider.invalidate();
          }
        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }

    public boolean isClickable () {
      return willDraw();
    }

    public void addPart (TextPart part, int startX, int endX, int endXBottomPadding, boolean center) {
      this.partsCount++;

      TdApi.TextEntity spoiler = part.getSpoiler();
      if (spoiler != null) {// null whitespace between two spoilers
        if (offset == -1) {
          offset = spoiler.offset;
        }
        this.length = (spoiler.offset + spoiler.length) - offset;
      }

      // TODO build proper particles
      int bound = Screen.dp(1f);
      final int radius = Screen.dp(3f);
      final TextPaint paint = getTextPaint(part.getEntity());
      final Paint.FontMetricsInt fm = getFontMetrics(paint.getTextSize());

      int x;
      if (center) {
        int width = getLineWidth(part.getLineIndex());
        int cx = startX + maxWidth / 2;
        x = part.makeX(cx - width / 2, cx + width / 2, 0);
      } else {
        x = part.makeX(startX, endX, endXBottomPadding);
      }
      int y = part.getY();
      float width = part.getWidth();

      RectF highlightRect = Paints.getRectF();
      highlightRect.left = x - bound;
      highlightRect.top = y - bound;
      highlightRect.right = highlightRect.left + width + bound + bound;
      highlightRect.bottom = y + (part.getHeight() == -1 ? fm.descent - fm.ascent : part.getHeight()) + bound;
      highlightRect.offset(0, paint.baselineShift + getPartVerticalOffset(part));
      path.addRoundRect(highlightRect, radius, radius, Path.Direction.CW);
    }

    public boolean willDraw () {
      return isRevealed.getFloatValue() != 1f;
    }

    public void build () {
      // all parts have been added
      // TODO finish building proper particles
    }

    public void setPressed (boolean isPressed, boolean animated) {
      // TODO move particles faster when pressed
    }

    public void draw (Canvas c, int iconColor) {
      // TODO proper particles
      c.drawPath(path, Paints.fillingPaint(ColorUtils.alphaColor(1f - getContentAlpha(), iconColor)));
    }

    public float getContentAlpha () {
      return isRevealed.getFloatValue();
    }

    @Override
    public void performDestroy () {
      isRevealed.cancel();
    }
  }

  private String originalText;
  private static final int MAX_DEBUG_CHECKED_LENGTH = 4096;
  private ArrayList<TextPart> parts;
  // Of lines that collapsed quotes hide, their media loaded, so that they fade with their lines
  private @Nullable ArrayList<TextPart> hiddenParts;
  private LongSparseArray<Spoiler> spoilers;
  private LongSparseArray<Background> backgrounds;
  // By the first part of each code block that scrolls and each collapsed quote
  private SparseArrayCompat<LongSparseArray<Background>> blockBackgrounds;
  private SparseArrayCompat<Path> pressHighlights;
  private List<int[]> lineSizes;
  private int builtInEmojiCount, customEmojiCount;
  private Map<String, TextMedia> media;
  private @Nullable TextEntity[] entities;

  @Nullable public TextEntity[] getEntities () {
    return entities;
  }

  private int paragraphCount;
  private int currentX, currentY;
  private int currentWidth;

  private TextPart lastPart;
  private int maxPartHeight;

  private boolean needRevealSpoiler (TextPart part) {
    Spoiler spoiler = findSpoiler(part.getSpoiler());
    return spoiler != null && spoiler.isClickable();
  }

  private void revealSpoiler (TextPart part) {
    Spoiler spoiler = findSpoiler(part.getSpoiler());
    if (spoiler != null) {
      spoiler.isRevealed.setValue(true, true);
    }
  }

  public static @Nullable TextEntity[] toEntities (CharSequence text, boolean onlyLinks, Tdlib tdlib, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    TdApi.TextEntity[] entities = TD.toEntities(text, onlyLinks);
    if (entities != null && entities.length > 0) {
      TextEntity[] parsedEntities = new TextEntity[entities.length];
      String in = text.toString();
      for (int i = 0; i < entities.length; i++) {
        parsedEntities[i] = new TextEntityMessage(tdlib, in, entities[i], openParameters);
      }
      return parsedEntities;
    }
    return null;
  }

  public static @Nullable TextEntity[] makeEntities (String in, int linkFlags, @Nullable TextEntity[] entities, Tdlib tdlib, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (entities != null && entities.length > 0) {
      return entities;
    } else if (linkFlags != ENTITY_FLAGS_NONE) {
      TdApi.TextEntity[] foundEntities = Td.findEntities(in);
      if (foundEntities != null && foundEntities.length > 0) {
        ArrayList<TextEntity> entitiesList = null;
        int skippedCount = 0;
        for (TdApi.TextEntity entity : foundEntities) {
          if (acceptEntity(entity, linkFlags)) {
            if (entitiesList == null) {
              entitiesList = new ArrayList<>(foundEntities.length - skippedCount);
            }
            entitiesList.add(new TextEntityMessage(tdlib, in, entity, openParameters));
          } else {
            skippedCount++;
          }
        }
        if (entitiesList != null) {
          entities = new TextEntity[entitiesList.size()];
          entitiesList.toArray(entities);
          return entities;
        }
      }
    }
    return null;
  }

  private Text (
    @NonNull String in,
    int maxWidth,
    @NonNull TextStyleProvider textStyleProvider,
    @NonNull TextColorSet textColorSet,
    int maxLineCount,
    @Nullable LineWidthProvider lineWidthProvider,
    @Nullable LineMarginProvider lineMarginProvider,
    int textFlags,
    @Nullable TextEntity[] entities,
    String suffix,
    @Nullable ClickListener clickListener,
    @Nullable TextMediaListener textMediaListener,
    @Nullable BlockSwitches blockSwitches,
    @Nullable Highlight highlight
  ) {
    this.blockSwitches = blockSwitches;
    this.highlight = highlight;
    this.textFlags = textFlags;
    this.maxWidth = maxWidth;
    this.maxLineCount = maxLineCount;
    this.lineWidthProvider = lineWidthProvider;
    this.lineMarginProvider = lineMarginProvider;
    this.clickListener = clickListener;
    this.textMediaListener = textMediaListener;
    this.textStyleProvider = textStyleProvider;
    this.defaultTextColorSet = textColorSet;
    this.entities = entities;
    this.suffix = suffix;
    this.suffixWidth = !StringUtils.isEmpty(suffix) ? (int) U.measureText(this.suffix, getTextPaint(null)) : 0;
    set(maxWidth, in);
  }

  public static class Builder {
    private final String in;
    private final int maxWidth;

    private TextStyleProvider provider;
    private TextColorSet theme;

    private int maxLineCount = -1;
    private LineWidthProvider lineWidthProvider;
    private @Nullable BlockSwitches blockSwitches;
    private LineMarginProvider lineMarginProvider;
    private int textFlags;
    private TextEntity[] entities;
    private String suffix;
    private ViewProvider viewProvider;
    private ClickListener clickListener;
    private TextMediaListener textMediaListener;
    private Highlight highlight; // TODO highlight text

    public Builder (String in, int maxWidth, @NonNull TextStyleProvider provider, @NonNull TextColorSet theme) {
      if (in == null)
        throw new IllegalArgumentException();
      //noinspection ConstantConditions
      if (provider == null)
        throw new IllegalStateException();
      //noinspection ConstantConditions
      if (theme == null)
        throw new IllegalStateException();
      this.in = in;
      this.maxWidth = maxWidth;
      this.provider = provider;
      this.theme = theme;
    }

    public Builder (FormattedText in, int maxWidth, @NonNull TextStyleProvider provider, @NonNull TextColorSet theme, @Nullable TextMediaListener textMediaListener) {
      this(in.text, maxWidth, provider, theme);
      entities(in.entities, textMediaListener);
    }

    public Builder (Tdlib tdlib, CharSequence in, TdlibUi.UrlOpenParameters urlOpenParameters, int maxWidth, TextStyleProvider provider, @NonNull TextColorSet theme, @Nullable TextMediaListener textMediaListener) {
      this(in.toString(), maxWidth, provider, theme);
      TextEntity[] entities = null;
      TdApi.TextEntity[] telegramEntities = TD.toEntities(in, false);
      if (telegramEntities != null && telegramEntities.length > 0) {
        entities = TextEntity.valueOf(tdlib, in.toString(), telegramEntities, urlOpenParameters);
      }
      if (entities == null) {
        entities = TextEntity.toEntities(in);
      }
      entities(entities, textMediaListener);
    }

    public Builder (Tdlib tdlib, TdApi.FormattedText in, TdlibUi.UrlOpenParameters urlOpenParameters, int maxWidth, TextStyleProvider provider, @NonNull TextColorSet theme, @Nullable TextMediaListener textMediaListener) {
      this(in.text, maxWidth, provider, theme);
      entities(TextEntity.valueOf(tdlib, this.in, in.entities, urlOpenParameters), textMediaListener);
    }

    public Builder styleProvider (@NonNull TextStyleProvider provider) {
      //noinspection ConstantConditions
      if (provider == null)
        throw new IllegalStateException();
      this.provider = provider;
      return this;
    }

    public Builder colorSet (@NonNull TextColorSet colorSet) {
      //noinspection ConstantConditions
      if (colorSet == null)
        throw new IllegalStateException();
      this.theme = colorSet;
      return this;
    }

    public Builder onClick (ClickListener listener) {
      this.clickListener = listener;
      return this;
    }

    public Builder highlight (Highlight highlight) {
      this.highlight = highlight;
      return this;
    }

    public Builder allBold () {
      return allBold(true);
    }

    public Builder allBold (boolean allBold) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_ALL_BOLD, allBold));
    }

    public Builder noSpacing () {
      return noSpacing(true);
    }

    public Builder noSpacing (boolean noSpacing) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_NO_SPACING, noSpacing));
    }

    public Builder ignoreNewLines () {
      return ignoreNewLines(true);
    }

    public Builder ignoreNewLines (boolean ignoreNewLines) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_IGNORE_NEWLINES, ignoreNewLines));
    }

    public Builder ignoreContinuousNewLines () {
      return ignoreContinuousNewLines(true);
    }

    public Builder ignoreContinuousNewLines (boolean ignoreContinousNewLines) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_IGNORE_CONTINUOUS_NEWLINES, ignoreContinousNewLines));
    }

    public Builder singleLine () {
      return maxLineCount(1);
    }

    public Builder singleLine (boolean isSingleLine) {
      return maxLineCount(isSingleLine ? 1 : -1);
    }

    public Builder clipTextArea () {
      return clipTextArea(true);
    }

    public Builder clipTextArea (boolean clipTextArea) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_NEED_CLIP_TEXT_AREA, clipTextArea));
    }

    public Builder allClickable () {
      return allClickable(true);
    }

    public Builder allClickable (boolean allClickable) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_ALL_CLICKABLE, allClickable));
    }

    public Builder noClickable () {
      return noClickable(true);
    }

    public Builder noClickable (boolean noClickable) {
      return textFlags(BitwiseUtils.setFlag(textFlags, Text.FLAG_NO_CLICKABLE, noClickable));
    }

    public Builder maxLineCount (int maxLineCount) {
      this.maxLineCount = maxLineCount;
      return this;
    }

    public Builder lineWidthProvider (LineWidthProvider lineWidthProvider) {
      this.lineWidthProvider = lineWidthProvider;
      return this;
    }

    public Builder lineMarginProvider (LineMarginProvider lineMarginProvider) {
      this.lineMarginProvider = lineMarginProvider;
      return this;
    }

    public Builder textFlags (int textFlags) {
      this.textFlags = textFlags;
      return this;
    }

    public Builder addFlags (int textFlags) {
      return textFlags(this.textFlags | textFlags);
    }

    public Builder suffix (String suffix) {
      this.suffix = suffix;
      return this;
    }

    public Builder entities (TextEntity[] entities, TextMediaListener textMediaListener) {
      this.entities = entities;
      this.textMediaListener = textMediaListener;
      return this;
    }

    public Builder view (View view) {
      return viewProvider(new SingleViewProvider(view));
    }

    public Builder viewProvider (ViewProvider viewProvider) {
      this.viewProvider = viewProvider;
      return this;
    }

    public Builder blockSwitches (@Nullable BlockSwitches blockSwitches) {
      this.blockSwitches = blockSwitches;
      return this;
    }

    public Text build () {
      TextEntity[] entities = this.entities;
      if (this.highlight != null) {
        entities = new FormattedText(in, entities).highlight(this.highlight).entities;
      }
      Text text = new Text(
        in,
        maxWidth,
        provider,
        theme,
        maxLineCount,
        lineWidthProvider,
        lineMarginProvider,
        textFlags,
        entities,
        suffix,
        clickListener,
        textMediaListener,
        blockSwitches,
        highlight
      );
      if (viewProvider != null)
        text.setViewProvider(viewProvider);
      return text;
    }
  }

  public static TdApi.TextEntity[] findEntities (String in, int flags) {
    TdApi.TextEntity[] foundEntities = Td.findEntities(in);
    if (foundEntities != null && foundEntities.length > 0) {
      ArrayList<TdApi.TextEntity> entitiesList = null;
      int skippedCount = 0;
      for (TdApi.TextEntity entity : foundEntities) {
        if (acceptEntity(entity, flags)) {
          if (entitiesList == null) {
            entitiesList = new ArrayList<>(foundEntities.length - skippedCount);
          }
          entitiesList.add(entity);
        } else {
          skippedCount++;
        }
      }
      if (entitiesList != null) {
        TdApi.TextEntity[] result = new TdApi.TextEntity[entitiesList.size()];
        entitiesList.toArray(result);
        return result;
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    return originalText.hashCode();
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    return obj instanceof Text && ((Text) obj).originalText.equals(this.originalText);
  }

  private static boolean acceptEntity (TdApi.TextEntity entity, int flags) {
    if (flags == ENTITY_FLAGS_ALL) {
      return true;
    }
    switch (entity.type.getConstructor()) {
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
        return (flags & ENTITY_FLAG_USERNAME) != 0;
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
        return (flags & ENTITY_FLAG_COMMAND) != 0;
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
        return (flags & ENTITY_FLAG_HASHTAG) != 0;
      case TdApi.TextEntityTypeTonAddress.CONSTRUCTOR:
        return (flags & ENTITY_FLAG_TON_ADDRESS) != 0;
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return (flags & ENTITY_FLAG_URL) != 0;
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR: // TODO
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
      case TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeDateTime.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        break;
      default:
        Td.assertTextEntityType_b8214790();
        throw Td.unsupported(entity.type);
    }
    return false;
  }

  public String getText () {
    return originalText;
  }

  public int getEntityCount () {
    return entities != null ? entities.length : 0;
  }

  public int findAnchorLineIndex (String anchor) {
    if (entities != null && parts != null) {
      for (TextEntity entity : entities) {
        if (entity.hasAnchor(anchor)) {
          int index = entity.getStart();
          for (TextPart part : parts) {
            if (index >= part.getStart() && index < part.getEnd()) {
              return part.getLineIndex();
            }
          }
        }
      }
    }
    return -1;
  }

  public int getTotalEmojiCount () {
    return builtInEmojiCount + customEmojiCount;
  }

  public int getEmojiOnlyCount () {
    final int emojiCount = getTotalEmojiCount();
    return parts == null || emojiCount == 0 || parts.size() > emojiCount ? -1 : emojiCount;
  }

  @Override
  public int getEmojiCount () {
    return builtInEmojiCount;
  }

  public boolean hasBuiltInEmoji () {
    return getEmojiCount() > 0;
  }

  @Override
  public boolean incrementEmojiCount () {
    builtInEmojiCount++;
    return true;
  }

  public void abort () {
    textFlags |= FLAG_ABORT_PROCESS;
  }

  private boolean isAborted () {
    return (textFlags & FLAG_ABORT_PROCESS) != 0;
  }

  public void setTextFlag (int flag, boolean value) {
    setTextFlags(BitwiseUtils.setFlag(textFlags, flag, value));
  }

  public boolean setTextFlags (int flags) {
    if (this.textFlags != flags) {
      this.textFlags = flags;
      return true;
    }
    return false;
  }

  public boolean isEllipsized () {
    return (textFlags & FLAG_ELLIPSIZED) != 0;
  }

  // Getters

  public boolean isEmpty () {
    return StringUtils.isEmpty(originalText);
  }

  public int getMaxWidth () {
    return maxWidth;
  }

  // Entities

  private int entityIndex = -1;
  private int entityStart, entityEnd;
  private boolean entityReleased;

  @Nullable
  private TextEntity findEntity (int start, int end) {
    if (entities == null) {
      return null;
    }

    int startIndex;
    if (entityIndex != -1) {
      if (!(entityReleased && entityEnd == entityStart) && (end <= entityStart || start < entityEnd || (entityEnd == entityStart && start == entityStart))) {
        return (entityReleased = end > entityStart || (entityEnd == entityStart && (start == entityStart || end == entityStart))) ? entities[entityIndex] : null;
      }
      startIndex = entityIndex + 1;
    } else {
      startIndex = 0;
    }

    final int entitiesCount = entities.length;
    for (int entityIndex = startIndex; entityIndex < entitiesCount; entityIndex++) {
      int entityStart = entities[entityIndex].getStart();
      int entityEnd = entities[entityIndex].getEnd();

      if (entityEnd - entityStart == 0 && !entities[entityIndex].isIcon()) // Ignore anchors
        continue;

      if (end <= entityStart || start < entityEnd || (entityEnd == entityStart && start == entityStart)) {
        this.entityIndex = entityIndex;
        this.entityStart = entityStart;
        this.entityEnd = entityEnd;
        return (entityReleased = end > entityStart || (entityEnd == entityStart && (start == entityStart || end == entityStart))) ? entities[entityIndex] : null;
      }

      // Passed entity may cover only characters out of lines, e.g. a token of a line break
      if (Log.checkLogLevel(Log.LEVEL_VERBOSE)) {
        logPassedEntity(startIndex, start, end);
      }
    }

    return null;
  }

  private void logPassedEntity (int startIndex, int start, int end) {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    for (TextEntity entity : entities) {
      if (first) {
        first = false;
      } else {
        b.append('\n');
      }
      b.append("{type: ");
      b.append(entity.getType());
      b.append(", start: ");
      b.append(entity.getStart());
      b.append(", end: ");
      b.append(entity.getEnd());
      b.append(", entity: ");
      b.append(entity.toString());
      b.append("}");
    }
    Log.v("Next entity not found (entities not sorted?), startIndex:%d start:%d, end:%d, " +
      "entities:\n%s", startIndex, start, end, b.toString());
  }

  // Text

  private void reset () {
    if (changeAnimator != null) {
      changeAnimator.stopAnimation();
    }
    if (changePieces != null && blockSwitches != null) {
      // The owner follows the frames of the change, so it gets its end after the layout, unless a
      // new change goes on from it
      UI.post(this::onChangeStopped);
    }
    finishChange();
    bidiClear();
    entityIndex = -1;
    entityStart = entityEnd = 0;
    pressHighlight = null;
    pressedQuote = null;
    builtInEmojiCount = 0;
    customEmojiCount = 0;
    if (!quotes.isEmpty()) {
      // Each layout has its own blocks, as tokens of the last one come on the main thread
      synchronized (this) {
        previousQuotes = quotes;
        quotes = new ArrayList<>();
      }
    }
    previousQuoteIndex = 0;
    blocksWithoutTokens = null;
    hiddenParts = null;
    clearMedia();
    maxPartHeight = currentWidth = currentX = currentY = paragraphCount = 0;
    lastPart = null;
    layoutPaint = null;
    layoutPaintEntity = null;
    codeBlockLineStartX = -1;
    longCodeBlockId = -1;
    textFlags &= ~(
      FLAG_FULL_RTL |
      FLAG_ELLIPSIZED |
      FLAG_NEED_BACKGROUND |
      FLAG_HAS_SPOILERS
    );
    if (lineSizes != null) {
      lineSizes.clear();
    }
    clearSpoilers();
  }

  private interface MediaCreator {
    TextMedia onCreateMedia (String keyId, long id);
  }

  private TextMedia newOrExistingMedia (@Nullable String keyId, int start, int end, MediaCreator creator) {
    if (textMediaListener == null)
      throw new IllegalStateException();
    if (media == null)
      media = new LinkedHashMap<>();
    final long nextMediaId = media.size();
    if (keyId == null) {
      keyId = "_" + nextMediaId + "x" + (start != end ? start + ".."  + end : start);
    }
    TextMedia existingMedia = media.get(keyId);
    if (existingMedia != null) {
      return existingMedia;
    }
    TextMedia newMedia = creator.onCreateMedia(keyId, nextMediaId);
    media.put(keyId, newMedia);
    return newMedia;
  }

  private void removeMedia (TextPart part) {
    TextMedia media = part.getMedia();
    if (media != null && media.attachedToParts.remove(part)) {
      if (part.isCustomEmoji()) {
        customEmojiCount--;
      }
    } else if (part.isBuiltInEmoji()) {
      builtInEmojiCount--;
    }
  }

  private Set<TextMedia> findMediaOfHiddenParts () {
    if (hiddenParts == null) {
      return Collections.emptySet();
    }
    final Set<TextMedia> result = new HashSet<>();
    for (TextPart part : hiddenParts) {
      final TextMedia media = part.getMedia();
      if (media != null) {
        result.add(media);
      }
    }
    return result;
  }

  @Nullable TextPart findHiddenPart (TextPart shownPart) {
    if (hiddenParts != null && shownPart.getLine() == originalText) {
      for (TextPart part : hiddenParts) {
        if (part.getStart() == shownPart.getStart() && part.getEnd() == shownPart.getEnd()) {
          return part;
        }
      }
    }
    return null;
  }

  @UiThread
  public void notifyMediaChanged (@Nullable TextMedia media) {
    if (textMediaListener != null) {
      textMediaListener.onInvalidateTextMedia(this, media);
    }
    if (viewProvider != null) {
      viewProvider.invalidate();
    }
  }

  private void clearSpoilers () {
    if (this.spoilers != null) {
      for (int i = spoilers.size() - 1; i >= 0; i--) {
        Spoiler spoiler = spoilers.valueAt(i);
        if (spoiler != null) {
          spoiler.performDestroy();
        }
      }
      spoilers.clear();
      spoilers = null;
    }
  }

  private Spoiler findSpoiler (TdApi.TextEntity spoilerEntity) {
    if (spoilers == null || spoilerEntity == null) {
      return null;
    }
    // Keys sort spoilers by offset; only parts of one spoiler, revealed together, overlap
    int low = 0, high = spoilers.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      Spoiler spoiler = spoilers.valueAt(middle);
      if (spoilerEntity.offset < spoiler.offset) {
        high = middle - 1;
      } else if (spoilerEntity.offset >= spoiler.offset + spoiler.length) {
        low = middle + 1;
      } else {
        return spoiler;
      }
    }
    return null;
  }

  public void set (int maxWidth, String in) {
    set(maxWidth, in, entities);
  }

  public void changeMaxWidth (int maxWidth) {
    changeMaxWidth(maxWidth, false);
  }

  public void changeMaxWidth (int maxWidth, boolean force) {
    if (this.maxWidth != maxWidth || force) {
      set(maxWidth, originalText, entities);
    }
  }

  public void set (int maxWidth, String in, TextEntity[] entities) {
    if (in == null)
      throw new IllegalArgumentException();
    this.maxWidth = maxWidth;
    this.entities = entities;
    try {
      if (Log.isEnabled(Log.TAG_SPEED_TEXT) && Log.checkLogLevel(Log.LEVEL_INFO)) {
        long elapsed = SystemClock.elapsedRealtime();
        setImpl(in);
        int ms = (int) (SystemClock.elapsedRealtime() - elapsed);
        if (ms >= 150) {
          Log.w(Log.TAG_SPEED_TEXT, "Text.set took %dms for %d chars, maxWidth: %d, text:\n%s", ms, in.length(), maxWidth, in);
        } else {
          Log.v(Log.TAG_SPEED_TEXT, "Text.set took %dms for %d chars, maxWidth: %d", ms, in.length(), maxWidth);
        }
      } else {
        setImpl(in);
      }
    } catch (Throwable cause) {
      Log.w(Log.TAG_SPEED_TEXT, "Couldn't parse %d chars for max width: %d, fontSize: %f, text:\n%s", cause, in.length(), maxWidth, Settings.instance().getChatFontSize(), in);
      throw cause;
    }
  }

  private void setImpl (final String in) {
    if (BitwiseUtils.hasFlag(textFlags, FLAG_DESTROYED))
      throw new IllegalStateException();

    reset();
    hasCodeBlocks = allowQuotes() && hasCodeBlockEntities();
    codeBlockEnds = null;
    startMeasuredWords(in);
    final int layoutLength = getLayoutLength(in);

    if ((textFlags & FLAG_ALL_BOLD) != 0 && Text.needFakeBold(in, 0, layoutLength)) {
      textFlags |= FLAG_FAKE_BOLD;
    } else {
      textFlags &= ~FLAG_FAKE_BOLD;
    }

    originalText = in;

    if (maxWidth <= 0) {
      this.parts = null;
      if (this.pressHighlights != null)
        this.pressHighlights.clear();
      clearSpoilers();
      return;
    }

    blockSwitchesVersion = blockSwitches != null ? blockSwitches.getVersion() : 0;

    final ArrayList<TextPart> out = new ArrayList<>(10);
    final Emoji.Callback emojiCallback = (input, code, info, position, length) -> {
      if (position > emojiStart) {
        processPartSplitty(in, emojiStart, position, out, emojiEntity, false);
      }
      processEmoji(in, position, position + length, info, out, emojiEntity);
      emojiStart = position + length;
      return true;
    };

    paragraphStart = 0;
    paragraphEnd = in.length();
    bidiProcessTextStart(in, layoutLength);

    try {
      boolean prevIsNewLine = false;
      final int totalLength = layoutLength;
      for (int index = 0; index < totalLength; ) {
        int indexOfNewLine;
        if (BitwiseUtils.hasFlag(textFlags, Text.FLAG_ALWAYS_BREAK)) {
          indexOfNewLine = indexOfSpaceOrNewLine(in, index, totalLength);
        } else {
          indexOfNewLine = indexOfParagraphSeparator(in, index, totalLength);
        }

        int length = indexOfNewLine == -1 ? totalLength - index : indexOfNewLine - index;

        if (!BitwiseUtils.hasFlag(textFlags, Text.FLAG_ALWAYS_BREAK)) {
          paragraphStart = index;
          paragraphEnd = index + length;
        }
        processLine(in, index, index + length, out, emojiCallback);
        if (length != 0) {
          prevIsNewLine = false;
        }

        index += length;

        if (indexOfNewLine != -1) {
          // A code block that ends with a line break, as Markdown makes it, has broken the line
          final boolean isAfterCodeBlock =
            hasCodeBlocks && endsCodeBlockWithNewLine(in, indexOfNewLine);
          if (!isAfterCodeBlock && (!prevIsNewLine ||
            !BitwiseUtils.hasFlag(textFlags, Text.FLAG_IGNORE_CONTINUOUS_NEWLINES))) {
            if (BitwiseUtils.hasFlag(textFlags, Text.FLAG_IGNORE_NEWLINES)) {
              if (currentX > 0 && !out.isEmpty()) {
                currentX += (int) makeSpaceSize(getTextPaint(null));
                if (currentX > getLineMaxWidth(out.get(out.size() - 1).getLineIndex(), currentY)) {
                  newLineOrEllipsis(out, in);
                }
              }
            } else {
              newLineOrEllipsis(out, in);
            }
          }
          prevIsNewLine = true;
          index++;
        }
      }
      if (totalLength < in.length()) {
        // The rest isn't read: an ellipsis ends the text, or is all of it when nothing read shows
        if (out.isEmpty()) {
          final TextPart ellipsis = new TextPart(
            this, Strings.ELLIPSIS, 0, Strings.ELLIPSIS.length(), getLineCount(), paragraphCount
          );
          ellipsis.setXY(currentX, currentY);
          ellipsis.setWidth(U.measureText(Strings.ELLIPSIS, getTextPaint(null)));
          ellipsis.setBidiEntity(getBidiEntity(totalLength - 1));
          out.add(ellipsis);
          currentX += (int) Math.ceil(ellipsis.getWidth());
          currentWidth = Math.max(currentWidth, currentX);
          addLine(currentX, getCurrentLineHeight());
          currentY += getCurrentLineHeight();
          textFlags |= FLAG_ELLIPSIZED;
          throw new LimitReachedException();
        }
        do {
          newLineOrEllipsis(out, in);
        } while (true);
      }
      do {
        TextEntity entity = findEntity(totalLength, totalLength);
        if (entity != null) {
          if (entity.end > totalLength)
            throw new IllegalArgumentException(entity.end + " > " + totalLength);
          processTextOrEmoji(in, totalLength, totalLength, out, emojiCallback, entity);
        } else {
          break;
        }
      } while (true);
      currentWidth = Math.max(currentWidth, getVisibleLineWidth(currentX, out));
      if (currentX != 0) {
        addLine(currentX, getCurrentLineHeight());
      }
      currentY += getCurrentLineHeight();
      paragraphCount++;
    } catch (LimitReachedException ignored) { }

    int lineCount = out.isEmpty() ? 1 : out.get(out.size() - 1).getLineIndex() + 1;
    while (getLineCount() < lineCount) {
      addLine(0, getCurrentLineHeight());
    }

    if (!StringUtils.isEmpty(suffix) && !out.isEmpty()) {
      TextPart lastPart = out.get(out.size() - 1);
      int[] lastLineSize = getLineSize(getLineCount() - 1);
      TextPart suffixPart = new TextPart(this, suffix, 0, suffix.length(), lastPart.getLineIndex(), lastPart.getParagraphIndex());
      suffixPart.setBidiEntity(lastPart.getBidiEntity());
      suffixPart.setXY(lastLineSize[0], lastPart.getY());
      suffixPart.setWidth(suffixWidth);
      lastLineSize[0] += suffixWidth;
      currentWidth = Math.max(currentWidth, getVisibleLineWidth(lastLineSize[0], out));
      out.add(suffixPart);
    }

    out.trimToSize();
    this.parts = out;
    if (this.pressHighlights != null)
      this.pressHighlights.clear();
    // Backgrounds of entities follow the parts, e.g. after a switch of a code block
    this.backgrounds = null;

    // The diff costs the length times its differences, so only up to the length of messages
    if (BuildConfig.DEBUG && maxLineCount == -1 && in.length() <= MAX_DEBUG_CHECKED_LENGTH) {
      StringBuilder debug = new StringBuilder();
      for (TextPart part : parts) {
        debug.append(part.getLine(), part.getStart(), part.getEnd());
      }
      LinkedList<DiffMatchPatch.Diff> list = DiffMatchPatch.instance().diff_main(in, debug.toString());
      for (DiffMatchPatch.Diff diff : list) {
        if (!diff.operation.equals(DiffMatchPatch.Operation.EQUAL) && diff.text.trim().length() > 0) {
          UI.showToast("TEXT PARSING PROBABLY FAILED:\n" + diff.operation + ": " + diff.text + "\n" + in, Toast.LENGTH_LONG);
        }
      }
    }

    bidiPartsReorder();
    bidiClear();
    buildQuotes();

    if (BuildConfig.DEBUG) {
      int partCount = parts.size();
      for (int i = 0; i < partCount; i++) {
        getLineWidth(parts.get(i).getLineIndex());
      }
    }
  }

  private int getLayoutLength (String in) {
    // Lines show fewer than MAX_CHARS_PER_EM characters per em of their width, so a limited text
    // reads no more, even of marks of direction without width
    final int length = in.length();
    final int textSize = textStyleProvider.getTextSizeInPixels();
    if (maxLineCount < 1 || maxWidth <= 0 || textSize <= 0) {
      return length;
    }
    final long maxLength = (long) maxLineCount * (maxWidth / textSize + 1) * MAX_CHARS_PER_EM;
    if (maxLength >= length) {
      return length;
    }
    final int end = (int) maxLength;
    return Character.isHighSurrogate(in.charAt(end - 1)) ? end + 1 : end;
  }

  // Above the longest emoji, 15 characters in about 1.3 em
  private static final int MAX_CHARS_PER_EM = 16;

  private static int findMoreSpaces (String in, int start) {
    final int length = in.length();
    int c = 0;
    for (int i = start; i < length; ) {
      int codePoint = in.codePointAt(i);
      if (codePoint == '\n' || Character.charCount(codePoint) != 1 || Character.getType(codePoint) != Character.SPACE_SEPARATOR) {
        return c;
      }
      c++;
      i++;
    }
    return c;
  }

  private int paragraphStart, paragraphEnd;

  private float measureTextRun (
    @BiDiEntity int bidiEntity,
    String in,
    int start,
    int end,
    Paint paint
  ) {
    // In the context of its paragraph, as StaticLayout shapes it, but only of characters next to
    // it, as getRunAdvance makes a layout of the whole context for each word
    final boolean inParagraph = paragraphStart <= start && end <= paragraphEnd;
    final int contextStart =
      inParagraph ? Math.max(paragraphStart, start - MEASURE_CONTEXT_LENGTH) : 0;
    final int contextEnd =
      inParagraph ? Math.min(paragraphEnd, end + MEASURE_CONTEXT_LENGTH) : in.length();
    return BiDiUtils.measureTextRun(bidiEntity, in, start, end, contextStart, contextEnd, paint);
  }

  static final int MEASURE_CONTEXT_LENGTH = 32;

  // A layout of the same text, e.g. after a switch of a block, takes words measured by the last one
  private @Nullable MeasuredWords measuredWords, lastMeasuredWords;

  private static final class MeasuredWords {
    // For each word: start, end and direction, the paint with its size, and the width
    private @Nullable String text;
    private int[] bounds = new int[96];
    private Paint[] paints = new Paint[32];
    private float[] sizes = new float[32];
    private float[] widths = new float[32];
    private int count;
    // Layout looks words up in the order of the text, so the earlier ones aren't looked at again
    private int cursor;

    void clear (@Nullable String text) {
      this.text = text;
      count = cursor = 0;
    }

    void add (int start, int end, @BiDiEntity int bidiEntity, Paint paint, float width) {
      if (count == widths.length) {
        bounds = Arrays.copyOf(bounds, count * 6);
        paints = Arrays.copyOf(paints, count * 2);
        sizes = Arrays.copyOf(sizes, count * 2);
        widths = Arrays.copyOf(widths, count * 2);
      }
      bounds[count * 3] = start;
      bounds[count * 3 + 1] = end;
      bounds[count * 3 + 2] = bidiEntity;
      paints[count] = paint;
      sizes[count] = paint.getTextSize();
      widths[count] = width;
      count++;
    }

    float find (String text, int start, int end, @BiDiEntity int bidiEntity, Paint paint) {
      if (this.text != text) {
        return Float.NaN;
      }
      while (cursor < count && bounds[cursor * 3] < start) {
        cursor++;
      }
      if (cursor == count || bounds[cursor * 3] != start || bounds[cursor * 3 + 1] != end ||
        bounds[cursor * 3 + 2] != bidiEntity || paints[cursor] != paint ||
        sizes[cursor] != paint.getTextSize()) {
        return Float.NaN;
      }
      return widths[cursor];
    }
  }

  private void startMeasuredWords (String in) {
    if (blockSwitches == null || !hasCodeBlocks) {
      measuredWords = lastMeasuredWords = null;
      return;
    }
    final MeasuredWords last = measuredWords;
    measuredWords = lastMeasuredWords != null ? lastMeasuredWords : new MeasuredWords();
    measuredWords.clear(in);
    lastMeasuredWords = last;
    if (last != null) {
      last.cursor = 0;
    }
  }

  private float measureWord (
    @BiDiEntity int bidiEntity,
    String in,
    int start,
    int end,
    Paint paint,
    @Nullable TextEntity entity
  ) {
    if (measuredWords == null || !isCodeBlock(entity)) {
      return measureTextRun(bidiEntity, in, start, end, paint);
    }
    float width = lastMeasuredWords != null ?
      lastMeasuredWords.find(in, start, end, bidiEntity, paint) : Float.NaN;
    if (Float.isNaN(width)) {
      width = measureTextRun(bidiEntity, in, start, end, paint);
    }
    measuredWords.add(start, end, bidiEntity, paint, width);
    return width;
  }

  private static int indexOfParagraphSeparator (String in, int start, int end) {
    for (int index = start; index < end; index++) {
      // All characters of Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR
      final char c = in.charAt(index);
      if (c == '\n' || c == '\r' || (c >= 0x1C && c <= 0x1E) || c == 0x85 || c == 0x2029) {
        return index;
      }
    }
    return -1;
  }

  private static int indexOfSpaceOrNewLine (String in, int start, int end) {
    for (int index = start; index < end; ) {
      int codePoint = in.codePointAt(index);
      int size = Character.charCount(codePoint);
      if (codePoint == '\n' || codePoint == ' ') {
        return index;
      }
      index += size;
    }
    return -1;
  }

  private static int indexOfSpace (String in, int start) {
    final int length = in.length();
    for (int i = start; i < length; ) {
      int codePoint = in.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (codePoint == '\n') {
        return -1;
      }
      if (size == 1 && Character.getType(codePoint) == Character.SPACE_SEPARATOR) {
        return i;
      }
      i += size;
    }
    return -1;
  }

  private void processLine (String in, int start, int end, ArrayList<TextPart> out, Emoji.Callback emojiCallback) {
    if (start == end) {
      processEntities(in, start, end, out, emojiCallback);
      return;
    }

    codeLineStart = start;
    codeLineContinuationX = -1;
    isCodeLineWrapped = false;

    bidiProcessLineStart(in, start, end);

    boolean first = true;
    boolean lastIsSpace = false;
    int count = 0;
    for (int i = start; i < end; ) {
      int codePoint = in.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (codePoint == '\n' && !BitwiseUtils.hasFlag(textFlags, FLAG_IGNORE_NEWLINES)) {
        break;
      }
      // The only ASCII character of SPACE_SEPARATOR type is space
      boolean isSpace = codePoint < 0x80 ?
        codePoint == ' ' || codePoint == '\n' :
        size == 1 && Character.getType(codePoint) == Character.SPACE_SEPARATOR;
      if (first) {
        first = false;
        lastIsSpace = isSpace;
        count += size;
      } else if (lastIsSpace == isSpace) {
        count += size;
      } else {
        processEntities(in, start, start + count, out, emojiCallback);
        start += count;
        count = size;
        lastIsSpace = isSpace;
      }
      i += size;
    }
    processEntities(in, start, start + count, out, emojiCallback);
    if (start + count < end) {
      processEntities(in, start + count, end, out, emojiCallback);
    }

    bidiProcessLineComplete();
  }

  private void processEntities (
    String in,
    int start,
    int end,
    ArrayList<TextPart> out,
    Emoji.Callback emojiCallback
  ) {
    if (start != end) {
      int bidiRunEnd = findBidiRunEnd(start);
      if (bidiRunEnd != -1 && bidiRunEnd < end) {
        do {
          processRunEntities(in, start, bidiRunEnd, out, emojiCallback, false);
          start = bidiRunEnd;
          bidiRunEnd = findBidiRunEnd(start);
        } while (bidiRunEnd < end);
      }
    }
    processRunEntities(in, start, end, out, emojiCallback, false);
  }

  private int processRunEntities (
    String in,
    int start,
    int end,
    ArrayList<TextPart> out,
    Emoji.Callback emojiCallback,
    boolean isChild
  ) {
    TextEntity entity = findEntity(start, end);
    TextColorSet theme = pickTheme(null, entity);

    if (theme.backgroundId(false) != 0) {
      textFlags |= FLAG_NEED_BACKGROUND;
    }

    if (entity == null) {
      processTextOrEmoji(in, start, end, out, emojiCallback, null);
      return -1;
    }

    if (entity.getSpoiler() != null) {
      textFlags |= FLAG_HAS_SPOILERS;
    }

    int entityStart = Math.max(start, this.entityStart);
    if (entityStart > start) {
      processTextOrEmoji(in, start, entityStart, out, emojiCallback,null);
    }
    if (entityEnd < end) {
      processTextOrEmoji(in, entityStart, entityEnd, out, emojiCallback, entity);
      if (isChild) {
        return entityEnd;
      } else {
        int newStart = entityEnd;
        do {
          newStart = processRunEntities(in, newStart, end, out, emojiCallback, true);
        } while (newStart != -1 && newStart < end);
      }
    } else {
      processTextOrEmoji(in, entityStart, end, out, emojiCallback, entity);
    }
    return -1;
  }

  // Emoji

  private int emojiStart;
  private int emojiSize;
  private TextEntity emojiEntity;

  private TextPaint layoutPaint;
  private TextEntity layoutPaintEntity;

  // Paint of entity and size of its emoji, prepared once for each entity of the text,
  // because preparing paint converts units of its text size
  private TextPaint getLayoutPaint (@Nullable TextEntity entity) {
    if (layoutPaint == null || layoutPaintEntity != entity) {
      layoutPaint = getTextPaint(entity);
      layoutPaintEntity = entity;
      Paint.FontMetricsInt fontMetricsInt = Paints.getFontMetricsInt(layoutPaint);
      emojiSize = Math.abs(fontMetricsInt.descent - fontMetricsInt.ascent) + Screen.dp(2f);
    }
    return layoutPaint;
  }

  private void processTextOrEmoji (final String in, final int start, final int end, final ArrayList<TextPart> out, final Emoji.Callback emojiCallback, final @Nullable TextEntity entity) {
    getLayoutPaint(entity);

    if (in.endsWith("wtftest")) {
      in.toString();
    }

    if (entity != null && entity.isCustomEmoji()) {
      String emojiCode = in.substring(start, end);
      EmojiInfo info;
      if (!EmojiStatusHelper.EMOJI.equals(emojiCode)) {
        info = Emoji.instance().getEmojiInfo(emojiCode, true);
      } else {
        info = null;
      }
      processEmoji(in, start, end, info, out, entity);
      return;
    }

    if (end - start == 0) {
      if (entity != null && entity.isIcon()) {
        processIcon(in, start, out, entity);
      }
      return;
    }

    emojiStart = start;
    emojiEntity = entity;

    if (mayContainEmoji(in, start, end)) {
      Emoji.instance().replaceEmoji(in, start, end, this, emojiCallback);
    }

    if (emojiStart < end) {
      processPartSplitty(in, emojiStart, end, out, entity, false);
    }
  }

  private void processIcon (String in, int index, ArrayList<TextPart> out, @NonNull TextEntity entity) {
    lastPart = null;

    if (entity.tdlib == null)
      throw new IllegalArgumentException();

    TdApi.RichTextIcon icon = entity.getIcon();

    int iconWidth = Screen.dp(icon.width);
    int iconHeight = Screen.dp(icon.height);

    int maxWidth = getLineMaxWidth(getLineCount(), currentY);

    if (currentX > 0 && currentX + iconWidth > maxWidth) {
      newLineOrEllipsis(out, in);
      maxWidth = getLineMaxWidth(getLineCount(), currentY);
    }
    if (iconWidth > maxWidth) {
      iconHeight *= (float) maxWidth / (float) iconWidth;
      iconWidth = maxWidth;
    }

    TextPart part;

    part = new TextPart(this, in, index, index, getLineCount(), paragraphCount);
    part.setXY(currentX, currentY);
    part.setWidth(iconWidth);
    part.setHeight(iconHeight);
    part.setEntity(entity);
    part.setBidiEntity(getBidiEntity(index));
    part.attachToMedia(newOrExistingMedia(TextMedia.keyForIcon(entity.tdlib, icon), index, index, (keyId, id) ->
      new TextMedia(this, entity.tdlib, keyId, id, icon)
    ));

    out.add(part);

    currentX += iconWidth;
    maxPartHeight = Math.max(iconHeight, maxPartHeight);
  }

  private void processEmoji (String in, int start, int end, @Nullable EmojiInfo info, ArrayList<TextPart> out, @Nullable TextEntity entity) {
    lastPart = null;

    final int maxWidth = getPartMaxWidth(entity);
    if (Math.max(currentX, getBlockLineStartX(entity)) + emojiSize > maxWidth) {
      newLineOrEllipsis(out, in);
    }

    final int quoteFlags = quoteCheckAndMakeNewLines(in, start, end, out, entity);
    quoteShiftCords(quoteFlags, in, entity);
    TextPart part;

    part = new TextPart(this, in, start, end, getLineCount(), paragraphCount);
    part.setXY(currentX, currentY);
    part.setWidth(emojiSize);
    part.setEntity(entity);
    part.setBidiEntity(getBidiEntity(start));
    part.setEmoji(info);
    if (entity != null && entity.tdlib != null && entity.isCustomEmoji()) {
      part.attachToMedia(newOrExistingMedia(TextMedia.keyForEmoji(entity.getCustomEmojiId(), emojiSize), start, end, (keyId, id) ->
        new TextMedia(this, entity.tdlib, keyId, id, emojiSize, entity.getCustomEmojiId())
      ));
    }

    out.add(part);

    currentX += emojiSize;
  }

  // Utils

  // Emoji.replaceEmoji finds emoji only by these characters
  private static boolean mayContainEmoji (String in, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = in.charAt(i);
      if (c == 0x00A9 || c == 0x00AE ||
        (c >= 0x203C && c <= 0x3299) ||
        (c >= 0xD83C && c <= 0xD83E)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasSpaceSeparators (String in, int start, int end) {
    for (int i = start; i < end;) {
      int codePoint = in.codePointAt(i);
      if (Character.getType(codePoint) == Character.SPACE_SEPARATOR) {
        return true;
      }
      i += Character.charCount(codePoint);
    }
    return false;
  }

  public static boolean isSplitterCodePoint (int codePoint, boolean allowWhitespace) {
    final int codePointType = Character.getType(codePoint);
    return isSplitterCodePointType(codePoint, codePointType, allowWhitespace);
  }

  private static boolean areSplitters (String in, int start, int end, boolean allowWhitespace) {
    if (end - start == 0) {
      return false;
    }

    for (int i = start; i < end; ) {
      int codePoint = in.codePointAt(i);
      if (!isSplitterCodePoint(codePoint, allowWhitespace)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }

    return true;
  }

  public static boolean isSplitterCodePointType (int codePoint, int codePointType, boolean allowWhitespace) {
    return isSplitterCodePointType(codePoint, codePointType, allowWhitespace, null);
  }

  public static boolean isSplitterCodePointType (int codePoint, int codePointType, boolean allowWhitespace, @Nullable char[] special) {
    switch (codePoint) {
      case '\'':
      case '"':
      case '(':
      case '`':
      case '·':
      case '\u00A0': // NO-BREAK SPACE
        return false;
      case '_':
        return true;
    }
    if (codePointType == Character.END_PUNCTUATION || codePointType == Character.OTHER_PUNCTUATION || codePointType == Character.DASH_PUNCTUATION || (allowWhitespace && codePointType == Character.SPACE_SEPARATOR)) {
      return true;
    }
    if (special != null) {
      for (char c : special) {
        if (c == codePoint) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean needFakeBold (CharSequence in) {
    return !StringUtils.isEmpty(in) && needFakeBold(in, 0, in.length());
  }

  public static boolean needFakeBold (CharSequence in, int start, int end) {
    if (StringUtils.isEmpty(in))
      return false;
    int i = Math.max(0, start);
    end = Math.min(in.length(), end);
    while (i < end) {
      int codePoint = Character.codePointAt(in, i);
      if (needsFakeBold(codePoint)) {
        return true;
      }
      i += Character.charCount(codePoint);
    }
    return false;
  }

  public static boolean needFakeBoldFull (String in, int start, int end) {
    if (end - start <= 0) {
      return false;
    }
    int i = start;
    while (i < end) {
      int codePoint = in.codePointAt(i);
      if (!needsFakeBold(codePoint) ) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return false;
  }

  public static boolean needsFakeBold (int codePoint) {
    return needsFill(codePoint) ||
      (codePoint >= 0x0D00 && codePoint <= 0x0D7F /*Malayalam*/) ||
      (codePoint >= 0x0600 && codePoint <= 0x06FF /*Arabic*/) ||
      (codePoint >= 0x0590 && codePoint <= 0x05FF /*Hebrew*/) ||
      (codePoint >= 0xFF00 && codePoint <= 0xFFEF /*Halfwidth and Fullwidth Forms*/);
  }

  public static boolean needsFill (int codePoint) {
    // Logger.v("%c: ideographic: %b, type: %d, code: %16X", (char) codePoint, Character.isIdeographic(codePoint), Character.getType(codePoint), codePoint);
    // http://www.fileformat.info/info/unicode/block/index.htm
    return (
        (codePoint >= 0x3040 && codePoint <= 0x309F /*Hiragana*/) ||
        (codePoint >= 0x30A0 && codePoint <= 0x30FF /*Katakana*/) ||
        (codePoint >= 0x3100 && codePoint <= 0x312F /*Bopomofo*/) ||
        (codePoint >= 0xAC00 && codePoint <= 0xD7AF /*Hangul Syllables*/) ||

        (codePoint >= 0x2E80 && codePoint <= 0x9FFF) ||
        (codePoint >= 0x20000 && codePoint <= 0x2FA1F)

        /*(codePoint >= 0x2E80 && codePoint <= 0x2EFF *//*CJK Radicals Supplement*//*) ||
        (codePoint >= 0x3000 && codePoint <= 0x303F *//*CJK Symbols and Punctuation*//*) ||
        (codePoint >= 0x31C0 && codePoint <= 0x31EF *//*CJK Strokes*//*) ||
        (codePoint >= 0x3200 && codePoint <= 0x32FF *//*Enclosed CJK Letters and Months*//*) ||
        (codePoint >= 0x3300 && codePoint <= 0x33FF *//*CJK Compatibility*//*) ||
        (codePoint >= 0x3400 && codePoint <= 0x4DBF *//*CJK Unified Ideographs Extension A*//*) ||
        (codePoint >= 0x4E00 && codePoint <= 0x9FFF *//*CJK Unified Ideographs*//*) ||
        (codePoint >= 0xF900 && codePoint <= 0xFAFF *//*CJK Compatibility Ideographs*//*) ||
        (codePoint >= 0xFE30 && codePoint <= 0xFE4F *//*CJK Compatibility Forms*//*) || // FIXME maybe
        (codePoint >= 0x20000 && codePoint <= 0x2A6DF *//*CJK Unified Ideographs Extension B*//*) ||
        (codePoint >= 0x2A700 && codePoint <= 0x2B73F *//*CJK Unified Ideographs Extension C*//*) ||
        (codePoint >= 0x2B740 && codePoint <= 0x2B81F *//*CJK Unified Ideographs Extension D*//*) ||
        (codePoint >= 0x2B820 && codePoint <= 0x2CEAF *//*CJK Unified Ideographs Extension E*//*) ||
        (codePoint >= 0x2CEB0 && codePoint <= 0x2EBEF *//*CJK Unified Ideographs Extension F*//*) ||
        (codePoint >= 0x2F800 && codePoint <= 0x2FA1F *//*CJK Compatibility Ideographs Supplement*//*)*/
    );
    // || Character.isIdeographic(codePoint));
  }

  // Processing

  private int processPartSplitty (final String in, final int start, final int end, final ArrayList<TextPart> out, final @Nullable TextEntity entity, boolean isChild) {
    /*final char[] test = new char[] { '[', ']' };
    Logger.v("== debug chars ==");
    for (char c : test) {
      Logger.v("Char %c is %d", c, Character.getType(c));
    }*/

    int nextSplit = -1;

    int searchStart = start;

    // +1 is to avoid commands and username splitting /nexample
    int startCodePoint = in.codePointAt(searchStart);
    if (isSplitterCodePoint(startCodePoint, false)) {
      int codePointSize = Character.charCount(startCodePoint);
      if (searchStart + codePointSize < end) {
        searchStart += codePointSize;
      }
    }

    int nextSplitSearch = -1;

    for (int i = searchStart; i < end; ) {
      // ASCII letters and digits are neither splitters nor need fill
      char c = in.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        i++;
        continue;
      }
      int codePoint = in.codePointAt(i);
      int charCount = Character.charCount(codePoint);
      int codePointType = Character.getType(codePoint);
      if (isSplitterCodePointType(codePoint, codePointType, true)) {
        if (i == searchStart || i + charCount == end || !isSplitterCodePoint(in.codePointAt(i + charCount), false)) {
          nextSplit = i + charCount;
          nextSplitSearch = nextSplit;
        } else {
          nextSplit = i;
          nextSplitSearch = nextSplit + charCount;
        }
        if (codePointType == Character.SPACE_SEPARATOR) {
          /*if (i == searchStart) {
            int j = i;
            while (j < end - 1) {
              int nextCodePoint = in.codePointAt(j);
              int nextCodePointType = Character.getType(nextCodePoint);
              if (nextCodePointType != Character.SPACE_SEPARATOR) {
                break;
              }
              int nextCharCount = Character.charCount(nextCodePoint);
              nextSplit += nextCharCount;
              j += nextCharCount;
            }
          }*/
          nextSplitSearch = -1;
        }
      } else if (needsFill(codePoint)) {
        nextSplit = i + charCount;
        nextSplitSearch = nextSplit;
      }
      if (nextSplit != -1) {
        int nextCodePoint, nextCodePointType;
        if (nextSplitSearch != -1) {
          while (nextSplitSearch < end && isSplitterCodePointType(codePoint, nextCodePointType = Character.getType(nextCodePoint = in.codePointAt(nextSplitSearch)), true)) {
            int nextCharCount = Character.charCount(nextCodePoint);
            if (nextSplitSearch + nextCharCount <= end) {
              nextSplitSearch += nextCharCount;
              nextSplit = nextSplitSearch;
            } else {
              break;
            }
            if (nextCodePointType == Character.SPACE_SEPARATOR) {
              break;
            }
          }
        }
        break;
      }
      i += charCount;
    }

    if (nextSplit == -1 || nextSplit == end) {
      processPart(in, start, end, out, entity, false, null);
    } else {
      processPart(in, start, nextSplit, out, entity, false, null);
      if (isChild) {
        return nextSplit;
      } else {
        do {
          nextSplit = processPartSplitty(in, nextSplit, end, out, entity, true);
        } while (nextSplit != -1);
      }
    }
    return -1;
  }

  public TextPaint getTextPaint (@Nullable TextEntity entity) {
    boolean forceBold = (textFlags & FLAG_ALL_BOLD) != 0;
    boolean fakeBold = (textFlags & FLAG_FAKE_BOLD) != 0;
    float baselineShift = entity != null ? entity.getBaselineShift() : 0f;
    TextPaint paint;
    if (entity != null) {
      paint = entity.getTextPaint(textStyleProvider, forceBold, fakeBold);
    } else if (forceBold) {
      paint = fakeBold ? textStyleProvider.getFakeBoldPaint() : textStyleProvider.getBoldPaint();
    } else {
      paint = textStyleProvider.getTextPaint();
    }
    paint.baselineShift = baselineShift != 0 ? (int) (paint.ascent() * baselineShift) : 0;
    return paint;
  }

  public TextPaint modifyTextPaint (@NonNull TextPaint paint) {
    // paint.setShadowLayer(shadowSize, 0, 0, Color.BLACK);
    return paint;
  }

  private static boolean isMonospace (String in, int start, int end) {
    for (int i = start; i < end; ) {
      int codePoint = in.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (size != 1 || !isMonospace((char) codePoint)) {
        return false;
      }
      i += size;
    }
    return true;
  }

  private static boolean isMonospace (char c) {
    return
      (/*c >= 0x0000 &&*/ c <= 0x007F) ||
        (c >= 0x0080 && c <= 0x00FF) ||
        (c >= 0x0100 && c <= 0x017F) ||
        (c >= 0x0180 && c <= 0x024F) ||

      (c >= 0x0400 && c <= 0x04FF) ||
      (c >= 0x0500 && c <= 0x052F) ||
      (c >= 0x2DE0 && c <= 0x2DFF) || (c >= 0xA640 && c <= 0xA69F) || (c >= 0x1C80 && c <= 0x1C8F) ||

      (c >= 0x0370 && c <= 0x03FF) ||
      (c >= 0x1F00 && c <= 0x1FFF);
    /*0x21,0x23,0x24,0x25,0x26,0x28,0x29,0x2a,0x2b,0x2c,0x2d,0x2e,0x2f,0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,0x38,0x39,0x3a,0x3b,0x3c,0x3d,0x3e,0x3f,0x40,0x41,0x42,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4f,0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5a,0x5b,0x5c,0x5d,0x61,0x62,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6a,0x6b,0x6c,0x6d,0x6e,0x6f,0x70,0x71,0x72,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x7b,0x7d,0xa2,0xa3,0xa5,0xa9,0xae,0xc2,0xca,0xd4,0xd7,0xe2,0xea,0xf4,0xf7,0x102,0x103,0x106,0x107,0x10c,0x10d,0x110,0x111,0x160,0x161,0x17d,0x17e,0x1a0,0x1a1,0x1af,0x1b0,0x386,0x388,0x389,0x38a,0x38c,0x38e,0x38f,0x390,0x391,0x392,0x393,0x394,0x395,0x396,0x397,0x398,0x399,0x39a,0x39b,0x39c,0x39d,0x39e,0x39f,0x3a0,0x3a1,0x3a3,0x3a4,0x3a5,0x3a6,0x3a7,0x3a8,0x3a9,0x3ab,0x3ac,0x3ad,0x3ad,0x3af,0x3b0,0x3b1,0x3b2,0x3b3,0x3b4,0x3b5,0x3b6,0x3b7,0x3b8,0x3b9,0x3ba,0x3bb,0x3bc,0x3bd,0x3be,0x3bf,0x3c0,0x3c1,0x3c3,0x3c4,0x3c5,0x3c6,0x3c7,0x3c8,0x3c9,0x3ca,0x3cb,0x3cc,0x3cd,0x401,0x402,0x404,0x405,0x406,0x407,0x408,0x409,0x40a,0x40b,0x40e,0x40f,0x410,0x411,0x412,0x413,0x414,0x415,0x416,0x417,0x418,0x419,0x41a,0x41b,0x41c,0x41d,0x41e,0x41f,0x420,0x421,0x422,0x423,0x424,0x425,0x426,0x427,0x428,0x429,0x42a,0x42b,0x42c,0x42d,0x42e,0x42f,0x430,0x431,0x432,0x433,0x434,0x435,0x436,0x437,0x438,0x439,0x43a,0x43b,0x43c,0x43d,0x43e,0x43f,0x440,0x441,0x442,0x443,0x444,0x445,0x446,0x447,0x448,0x449,0x44a,0x44b,0x44c,0x44d,0x44e,0x44f,0x451,0x452,0x454,0x455,0x456,0x457,0x458,0x459,0x45a,0x45b,0x45e,0x45f,0x490,0x491,0x2018,0x2019,0x201c,0x201d,0x20ac*/
  }

  private int processPart (final String in, final int start, int end, ArrayList<TextPart> out, @Nullable TextEntity entity, boolean isChild, float[] childWidth) {
    /*if (!allowNewLine()) {
      return;
    }*/
    if (start == end) {
      return -1;
    }

    int futureEnd = -1;
    float futureWidth;

    final Paint paint = getLayoutPaint(entity);
    final float spaceSize = makeSpaceSize(paint);
    // TODO optimize https://fonts.google.com/specimen/Roboto+Mono
    final boolean isMonospace = false; // (entity != null && entity.isMonospace());
    final boolean isFullyMonospace = isMonospace && isMonospace(in, start, end);
    final @BiDiEntity int bidiEntity = getBidiEntity(start);
    // final float monospaceSize = spaceSize;
    final int lineStartX = getBlockLineStartX(entity);
    final int wrapStartX = lineStartX + getCodeLineContinuationX(in, entity);

    float fullWidth;
    if (isFullyMonospace) {
      fullWidth = in.codePointCount(start, end) * spaceSize;
    } else {
      int spaceCount = 0;
      for (int i = start; i < end; i++) {
        int codePoint = in.codePointAt(i);
        if (codePoint != ' ') {
          break;
        } else {
          spaceCount++;
        }
      }
      if ((end - start) == spaceCount) {
        fullWidth = spaceSize * spaceCount;
      } else if (isChild &&
        childWidth[0] > getLineMaxWidth(getLineCount(), this.currentY) - wrapStartX) {
        // Rest of a word that is split by characters again only needs an estimate.
        // Widths of characters measured one by one don't add up to the width of the word
        // because of kerning, so the last rest is measured, or next parts overlap its end
        fullWidth = childWidth[0];
      } else {
        fullWidth = measureWord(bidiEntity, in, start, end, paint, entity);
      }
    }
    futureWidth = fullWidth;

    // Log.i("measureText width: %d %d..%d length:%d text: \"%s\"", (int) fullWidth, start, end, end - start, in.substring(start, end));
    // boolean isDirty = false;

    float width;
    if (in.charAt(end - 1) == ' ') {
      width = fullWidth - spaceSize;
    } else {
      width = fullWidth;
    }
    final int maxWidth = getPartMaxWidth(entity);

    if (width > maxWidth - wrapStartX) {
      int currentEnd;

      float availWidth = maxWidth - Math.max(currentX, lineStartX);
      float totalWidth = 0;
      boolean done = false;
      int lastCodePoint = 0;
      float lastCodePointWidth = -1;

      for (currentEnd = start; currentEnd < end && !done; ) {
        int codePoint = in.codePointAt(currentEnd);
        int charCount = Character.charCount(codePoint);

        if (currentEnd + charCount > end) {
          break;
        }

        if (currentEnd + charCount < end && !isSplitterCodePoint(codePoint, true)) {
          boolean lookupSplitters = true; // Character.getType(codePoint) != Character.START_PUNCTUATION;

          int codePointCount = 0;

          int nextCodePoint;

          while (isSplitterCodePoint(nextCodePoint = in.codePointAt(currentEnd + charCount), false) == lookupSplitters && codePointCount < 5) {
            int nextCharCount = Character.charCount(nextCodePoint);
            if (currentEnd + charCount + nextCharCount < end) {
              charCount += nextCharCount;
              codePointCount++;
            } else {
              break;
            }
          }
        }

        int newEnd = Math.min(end, currentEnd + charCount);
        float charWidth;
        if (isFullyMonospace || (isMonospace && isMonospace(in, currentEnd, newEnd))) {
          charWidth = in.codePointCount(currentEnd, newEnd) * spaceSize;
        } else if (newEnd - currentEnd == 1 && lastCodePointWidth != -1 && lastCodePoint == codePoint) {
          charWidth = lastCodePointWidth;
        } else {
          charWidth = measureTextRun(bidiEntity, in, currentEnd, newEnd, paint);
          if (newEnd - currentEnd == 1) {
            lastCodePoint = codePoint;
            lastCodePointWidth = charWidth;
          }

        }
        if (availWidth - charWidth < 0) {
          if (currentX > 0 && currentEnd == start &&
            availWidth + currentX - wrapStartX - charWidth >= 0) {
            availWidth = maxWidth - wrapStartX;
            done = true;
            // charWidth = 0; // FIXME?
          } else {
            break;
          }
        }

        currentEnd += charCount;
        availWidth -= charWidth;
        totalWidth += charWidth;
      }

      if (done) {
        isCodeLineWrapped = true;
        newLineOrEllipsis(out, in, start, currentEnd, entity);
      }

      if (currentEnd == start) {
        Log.w("No space to fit even a single char/surrogate pair");
        return -1;
      }

      futureEnd = end;
      futureWidth -= totalWidth;
      fullWidth = totalWidth;
      end = currentEnd;
      if (in.charAt(end - 1) == ' ') {
        width = fullWidth - spaceSize;
      } else {
        width = fullWidth;
      }
    }

    float forcedWidth = -1;

    // Code blocks start new lines like quotes, unlike other full width entities
    if (Math.max(currentX, lineStartX) + width > maxWidth || (currentX > 0 && (
      (entity != null && entity.isFullWidth() && !isCodeBlock(entity) && lastPart != null &&
        !lastPart.isSameEntity(entity)) ||
      (lastPart != null && lastPart.getEntity() != null && lastPart.getEntity().isFullWidth() &&
        !isCodeBlock(lastPart.getEntity()) && !lastPart.isSameEntity(entity))))) {
      boolean movedLastLine = false;

      float firstCodePointWidth;
      if ((textFlags & FLAG_BOUNDS_NOT_STRICT) != 0 && in.codePointCount(start, end) == 2 &&
        Math.max(currentX, lineStartX) +
          (firstCodePointWidth = U.measureText(in, start, start + 1, paint)) <= maxWidth) {
        int firstCodePoint = in.codePointAt(start);
        int secondCodePoint = in.codePointAt(start + Character.charCount(firstCodePoint));

        if (!isSplitterCodePoint(firstCodePoint, true) && isSplitterCodePoint(secondCodePoint, true)) { // FIXME probably secondCodePoint one shouldn't be Character.SPACE_SEPARATOR
          forcedWidth = firstCodePointWidth;
        }
      }

      if (forcedWidth == -1) {
        if (lastPart != null) {
          // This is needed for cases like "<b>photos</b>, <b>videos</b>." to not put line-break before comma.
          if (lastPart.getX() != 0 && !lastPart.isSameEntity(entity) && (currentX - lastPart.getX() + width <= maxWidth) && isSplitterCodePoint(in.codePointAt(start), false) && !isSplitterCodePoint(in.codePointAt(lastPart.getEnd() - 1), true) && !hasSpaceSeparators(in, lastPart.getStart(), lastPart.getEnd())) {
            movedLastLine = moveToNextLine(out, out.size() - 1);
          }
        }
        if (!movedLastLine && !isCodeBlock(entity)) {
          movedLastLine = moveWordToNextLine(out, in, start, width, maxWidth);
        }
        if (!movedLastLine) {
          isCodeLineWrapped = true;
          newLineOrEllipsis(out, in, start, end, entity);
        }
      }
    }

    if (isSpaceAfterBlock(in, start, end, out, entity)) {
      return -1;
    }
    final int quoteFlags = quoteCheckAndMakeNewLines(in, start, end, out, entity);

    if (lastPart != null && (!lastPart.isSameEntity(entity) || start < lastPart.getEnd() || findNewLines(in, lastPart.getEnd(), start))) {
      lastPart = null;
    }

    if (lastPart == null || lastPart.getEntity() != entity || lastPart.getBidiEntity() != bidiEntity) {
      TextPart part;

      quoteShiftCords(quoteFlags, in, entity);
      part = new TextPart(this, in, start, end, getLineCount(), paragraphCount);
      part.setXY(currentX, currentY);
      part.setWidth(fullWidth);
      part.setEntity(entity);
      part.setBidiEntity(bidiEntity);

      lastPart = part;

      out.add(part);
    } else {
      lastPart.setEnd(end);
      lastPart.setWidth(lastPart.getWidth() + fullWidth);
    }

    currentX += fullWidth;

    if (futureEnd != -1) {
      if (isChild) {
        childWidth[0] -= fullWidth;
        return end;
      } else {
        childWidth = new float[1];
        childWidth[0] = futureWidth;
        do {
          end = processPart(in, end, futureEnd, out, entity, true, childWidth);
        } while (end != -1);
      }
    }
    return -1;
  }

  // A layout of text without code blocks skips checks of entities for them
  private boolean hasCodeBlocks;

  // Found at the first blank line, which most texts with code blocks don't have
  private @Nullable int[] codeBlockEnds;

  private boolean hasCodeBlockEntities () {
    if (entities != null) {
      for (TextEntity entity : entities) {
        if (entity.isCodeBlock()) {
          return true;
        }
      }
    }
    return false;
  }

  private int[] findCodeBlockEnds () {
    int count = 0;
    for (TextEntity entity : entities) {
      if (entity.isCodeBlock()) {
        count++;
      }
    }
    final int[] ends = new int[count];
    int index = 0;
    for (TextEntity entity : entities) {
      if (entity.isCodeBlock()) {
        ends[index++] = entity.getEnd();
      }
    }
    Arrays.sort(ends);
    return ends;
  }

  private boolean endsCodeBlockWithNewLine (String in, int index) {
    if (index == 0 || in.charAt(index - 1) != '\n' || entities == null) {
      return false;
    }
    if (codeBlockEnds == null) {
      codeBlockEnds = findCodeBlockEnds();
    }
    return Arrays.binarySearch(codeBlockEnds, index) >= 0;
  }

  private int getBlockEnd (TextEntity entity) {
    // End of the block, which other entities, e.g. found text, split into several
    int end = entity.getEnd();
    if (entities != null) {
      final int quoteId = entity.getQuoteId();
      // Entities go by their starts
      int low = 0, high = entities.length;
      while (low < high) {
        final int mid = (low + high) >>> 1;
        if (entities[mid].getStart() < end) {
          low = mid + 1;
        } else {
          high = mid;
        }
      }
      for (int i = low; i < entities.length && entities[i].getStart() <= end; i++) {
        final TextEntity next = entities[i];
        if (next.getStart() == end && next.getQuoteId() == quoteId) {
          end = next.getEnd();
        }
      }
    }
    return end;
  }

  private String getCode (TextEntity entity) {
    return originalText.substring(entity.getStart(), getBlockEnd(entity));
  }

  private boolean isCodeBlock (@Nullable TextEntity entity) {
    return hasCodeBlocks && entity != null && entity.isCodeBlock();
  }

  private boolean hasScrollingLines (@Nullable TextEntity entity) {
    if (!isCodeBlock(entity) || BitwiseUtils.hasFlag(textFlags, FLAG_WRAP_CODE_BLOCKS)) {
      return false;
    }
    if (!Config.AUTOMATIC_CODE_WRAPPING) {
      return true;
    }
    final int blockId = entity.getQuoteId();
    if (blockSwitches == null) {
      return isLongCodeBlock(entity);
    }
    if (blockSwitches.isChosen(blockId)) {
      return !blockSwitches.isChosenOn(blockId);
    }
    switch (blockSwitches.getCodeWrapping()) {
      case BlockSwitches.CODE_WRAPPING_ENABLED:
        return false;
      case BlockSwitches.CODE_WRAPPING_DISABLED:
        return true;
      default:
        return isLongCodeBlock(entity);
    }
  }

  public int getCollapsedQuoteLineCount () {
    return blockSwitches != null ? blockSwitches.getCollapsedQuoteLineCount() :
      Config.EXPANDABLE_QUOTE_COLLAPSED_LINE_COUNT;
  }

  public boolean canSwitchCodeBlock (TextEntity entity) {
    return Config.AUTOMATIC_CODE_WRAPPING && blockSwitches != null && isCodeBlock(entity) &&
      !BitwiseUtils.hasFlag(textFlags, FLAG_WRAP_CODE_BLOCKS) &&
      CodeBlockWrapping.hasWideLine(
        originalText,
        entity.getQuoteId(),
        getBlockEnd(entity),
        getCodeCharWidth(entity),
        getCodeLineWidth(entity)
      );
  }

  // Parts of a block go one after another, so the block is checked once
  private int longCodeBlockId = -1;
  private boolean isLongCodeBlock;

  private boolean isLongCodeBlock (TextEntity entity) {
    final int blockId = entity.getQuoteId();
    if (blockId != longCodeBlockId) {
      isLongCodeBlock = CodeBlockWrapping.isLong(
        originalText,
        blockId,
        getBlockEnd(entity),
        getCodeCharWidth(entity),
        getCodeLineWidth(entity),
        getLineHeight(),
        Screen.dp(
          QuoteBackground.getHeaderHeight(entity) + QuoteBackground.QUOTE_VERTICAL_PADDING * 2
        ),
        blockSwitches != null ? blockSwitches.getListHeight(this) : 0
      );
      longCodeBlockId = blockId;
    }
    return isLongCodeBlock;
  }

  private float getCodeCharWidth (TextEntity entity) {
    return makeSpaceSize(getLayoutPaint(entity));
  }

  private int getCodeLineWidth (TextEntity entity) {
    return maxWidth - getBlockLineStartX(entity);
  }

  private int getPartMaxWidth (@Nullable TextEntity entity) {
    if (hasScrollingLines(entity)) {
      return Integer.MAX_VALUE;
    }
    return getLineMaxWidth(getLineCount(), currentY);
  }

  private int codeBlockLineStartX = -1;

  private int getBlockLineStartX (@Nullable TextEntity entity) {
    // Lines of quotes and code blocks start after paddings added when the first part is placed
    if (!isCodeBlock(entity)) {
      return entity != null && entity.isQuote() ? getQuoteLineStartX(entity) : 0;
    }
    if (codeBlockLineStartX == -1) {
      codeBlockLineStartX = getQuoteLineStartX(entity);
    }
    return codeBlockLineStartX;
  }

  private static int getQuoteLineStartX (@Nullable TextEntity entity) {
    return Screen.dp(QuoteBackground.QUOTE_LEFT_PADDING + QuoteBackground.getRightPadding(entity));
  }

  private int codeLineStart;
  private int codeLineContinuationX = -1;
  private boolean isCodeLineWrapped;

  private int getCodeLineContinuationX (String in, @Nullable TextEntity entity) {
    if (!Config.PRESERVE_INDENTS_IN_CODE_BLOCKS || !isCodeBlock(entity)) {
      return 0;
    }
    if (codeLineContinuationX == -1) {
      // Block may start inside of the line
      final int lineStart = Math.max(codeLineStart, entity.getQuoteId());
      codeLineContinuationX = (int) CodeBlockWrapping.getContinuationX(
        in,
        lineStart,
        getCodeCharWidth(entity),
        getCodeLineWidth(entity)
      );
    }
    return codeLineContinuationX;
  }

  private int getVisibleLineWidth (int lineWidth, List<TextPart> out) {
    // Text is as wide as code blocks, not as their lines that don't wrap or end with spaces
    final boolean isCodeLine =
      !out.isEmpty() && isCodeBlock(out.get(out.size() - 1).getEntity());
    return isCodeLine ? Math.min(lineWidth, maxWidth) : lineWidth;
  }

  private static boolean findNewLines (String in, int start, int end) {
    for (int i = start; i < end; ) {
      int codePoint = in.codePointAt(i);
      if (codePoint == '\n')
        return true;
      i += Character.charCount(codePoint);
    }
    return false;
  }

  private void addLine (int width, int height) {
    if (lineSizes == null) {
      lineSizes = new ArrayList<>();
    }
    lineSizes.add(new int[] {width, height});
    if (width == 0) {
      paragraphCount++;
    }
  }

  private boolean moveToNextLine (List<TextPart> parts, int fromIndex) {
    if (maxLineCount != -1 && getLineCount() + 1 >= maxLineCount) {
      return false;
    }
    isCodeLineWrapped = true;
    TextPart first = parts.get(fromIndex);
    currentX = first.getX();
    int lineHeight = getCurrentLineHeight();
    int prevMaxPartHeight = maxPartHeight;
    addLine(currentX, lineHeight);
    currentWidth = Math.max(currentWidth, currentX);
    currentY += lineHeight;
    maxPartHeight = 0;
    
    if (first.getQuoteEntityId() != -1) {
      currentX = getQuoteLineStartX(first.getEntity()) +
        getCodeLineContinuationX(first.getLine(), first.getEntity());
    } else {
      currentX = 0;
    }
    for (int i = fromIndex; i < parts.size(); i++) {
      TextPart part = parts.get(i);
      part.setXY(currentX, currentY);
      part.setLineIndex(getLineCount(), paragraphCount);
      currentX += part.getWidth();
    }

    ensureLineCount(lineHeight, prevMaxPartHeight);
    return true;
  }

  private @Nullable BreakIterator lineBreaks;
  private @Nullable String lineBreaksText;

  private boolean moveWordToNextLine (
    List<TextPart> out,
    String in,
    int start,
    float width,
    int maxWidth
  ) {
    // A word of a right-to-left paragraph may come in parts of different runs, e.g. "@" and "name",
    // and a line breaks between them only where UAX #14 allows
    if (out.isEmpty()) {
      return false;
    }
    TextPart previous = out.get(out.size() - 1);
    if (!BiDiUtils.isParagraphRtl(previous.getBidiEntity()) || previous.getEnd() != start ||
      previous.getLineIndex() != getLineCount() || Character.isWhitespace(in.charAt(start - 1))) {
      return false;
    }
    if (lineBreaks == null || lineBreaksText != in) {
      lineBreaks = BreakIterator.getLineInstance();
      lineBreaks.setText(in);
      lineBreaksText = in;
    }
    // A sign that starts a word, e.g. "/" of "/start", stays with it, as in left-to-right text,
    // though UAX #14 allows a break after "/"
    final int signStart = signStartBefore(in, start);
    if (signStart == -1 && lineBreaks.isBoundary(start)) {
      return false;
    }
    int wordStart = signStart != -1 ? signStart : Math.max(0, lineBreaks.preceding(start));
    final int wordSignStart = signStartBefore(in, wordStart);
    if (wordSignStart != -1) {
      wordStart = wordSignStart;
    }
    int index = out.size() - 1;
    while (out.get(index).getStart() > wordStart) {
      if (index == 0) {
        return false;
      }
      TextPart part = out.get(index - 1);
      if (part.getEnd() != out.get(index).getStart() || part.getLineIndex() != getLineCount()) {
        return false;
      }
      index--;
    }
    TextPart first = out.get(index);
    if (first.getStart() < wordStart) {
      // The part also has the end of the line before the word, e.g. "you there" before "!"
      if (first.isStaticElement()) {
        return false;
      }
      final float tailWidth = measureTextRun(
        first.getBidiEntity(),
        in,
        wordStart,
        first.getEnd(),
        getTextPaint(first.getEntity())
      );
      final int tailX = first.getX() + (int) (first.getWidth() - tailWidth);
      if (currentX - tailX + width > maxWidth) {
        return false;
      }
      TextPart tail = new TextPart(
        this,
        in,
        wordStart,
        first.getEnd(),
        first.getLineIndex(),
        paragraphCount
      );
      tail.setEntity(first.getEntity());
      tail.setBidiEntity(first.getBidiEntity());
      tail.setXY(tailX, first.getY());
      tail.setWidth(tailWidth);
      first.setEnd(wordStart);
      first.setWidth(first.getWidth() - tailWidth);
      out.add(++index, tail);
      if (lastPart == first) {
        lastPart = tail;
      }
    } else if (index == 0 || out.get(index - 1).getLineIndex() != getLineCount() ||
      currentX - first.getX() + width > maxWidth) {
      return false;
    }
    return moveToNextLine(out, index);
  }

  private static int signStartBefore (String in, int index) {
    if (index <= 0) {
      return -1;
    }
    final int codePoint = in.codePointBefore(index);
    final int signStart = index - Character.charCount(codePoint);
    if (!isSplitterCodePoint(codePoint, false) ||
      (signStart > 0 && !Character.isWhitespace(in.codePointBefore(signStart)))) {
      return -1;
    }
    return signStart;
  }

  private void newLineOrEllipsis (List<TextPart> out, @NonNull String in) {
    newLineOrEllipsis(out, in, 0, 0, null);   // ?
  }

  private int getCurrentLineHeight () {
    return Math.max(maxPartHeight, getLineHeight());
  }

  private void newLineOrEllipsis (List<TextPart> out, @NonNull String in, final int start, final int end, TextEntity entity) {
    int lineHeight = getCurrentLineHeight();
    int prevMaxPartHeight = maxPartHeight;
    addLine(currentX, lineHeight);
    lastPart = null;
    maxPartHeight = 0;
    currentWidth = Math.max(currentWidth, getVisibleLineWidth(currentX, out));
    currentX = 0;
    currentY += lineHeight;
    try {
      ensureLineCount(lineHeight, prevMaxPartHeight);
    } catch (LimitReachedException e) {
      textFlags |= FLAG_ELLIPSIZED;
      if (out.isEmpty())
        throw e;

      @BiDiEntity int bidiEntity = getBidiEntity(start);
      final String defaultEllipsis = Strings.ELLIPSIS;
      TextPart lastPart = out.get(out.size() - 1);
      int currentX = getLineWidth(getLineCount() - 1);
      int lineMaxWidth = getLineMaxWidth(getLineCount() - 1, currentY);
      boolean hasEllipsizedPart = false;
      String ellipsis = defaultEllipsis;
      if (!StringUtils.isEmpty(in) && end > start && !BitwiseUtils.hasFlag(this.textFlags, FLAG_ELLIPSIZE_NO_FILL)) {
        int ellipsisMaxWidth = lineMaxWidth - currentX;
        String ellipsized = TextUtils.ellipsize(
          in.substring(start, end).replace('\n', ' '),
          getTextPaint(entity),
          ellipsisMaxWidth,
          TextUtils.TruncateAt.END
        ).toString();
        hasEllipsizedPart = !StringUtils.isEmpty(ellipsized);
        if (hasEllipsizedPart) {
          ellipsis = ellipsized;
        }
      }

      float ellipsisWidth = BiDiUtils.measureTextRun(bidiEntity, ellipsis, getTextPaint(entity));
      if (!BiDiUtils.isValid(bidiEntity)) {
        bidiEntity = lastPart.getBidiEntity();
      }
      boolean addLine = false;
      if (currentX + ellipsisWidth <= lineMaxWidth || (addLine = (textFlags & Text.FLAG_ELLIPSIZE_NEWLINE) != 0 && getLineCount() == maxLineCount - 1)) {
        // Easy path: just add ellipsis
        int lineIndex = lastPart.getLineIndex();
        int paragraphIndex = lastPart.getParagraphIndex();
        if (addLine) {
          this.currentX = currentX = 0;
          this.currentY += lineHeight;
          addLine(0, getCurrentLineHeight());
          lineIndex++;
        }

        final float defaultEllipsisWidth = U.measureText(defaultEllipsis, getTextPaint(entity));
        if (hasEllipsizedPart && !StringUtils.equalsOrBothEmpty(ellipsis, defaultEllipsis) && ellipsis.length() > defaultEllipsis.length()) {
          TextPart ellipsisPart = new TextPart(this, ellipsis, 0, ellipsis.length() - defaultEllipsis.length(), lineIndex, paragraphIndex);
          ellipsisPart.setXY(currentX, currentY);
          ellipsisPart.setWidth(ellipsisWidth - defaultEllipsisWidth);
          ellipsisPart.setEntity(entity);
          ellipsisPart.setBidiEntity(bidiEntity);
          out.add(ellipsisPart);
          currentX += ellipsisWidth - defaultEllipsisWidth;
        }

        final int bidiParagraphLevel = BiDiUtils.isParagraphRtl(bidiEntity) ? 1 : 0;
        final @BiDiEntity int bidiEntityForEllipsis = BiDiUtils.isValid(bidiEntity) ? BiDiUtils.create(bidiParagraphLevel, bidiParagraphLevel, BiDiUtils.getIndex(bidiEntity)) : bidiEntity;
        TextPart defaultEllipsisPart = new TextPart(this, defaultEllipsis, 0, defaultEllipsis.length(), lineIndex, paragraphIndex);
        defaultEllipsisPart.setXY(currentX, currentY);
        defaultEllipsisPart.setWidth(defaultEllipsisWidth);
        defaultEllipsisPart.setEntity(entity);
        defaultEllipsisPart.setBidiEntity(bidiEntityForEllipsis);
        out.add(defaultEllipsisPart);
        currentX += defaultEllipsisWidth;
      } else {
        // Hard path: find enough place for ellipsis and place it there
        final int requiredLineIndex = lastPart.getLineIndex();
        final int minEnd = lastPart.getEnd();
        final float defaultEllipsisWidth = U.measureText(defaultEllipsis, getTextPaint(null));

        boolean done = false;
        do {
          currentX = lastPart.getX();
          final int bidiParagraphLevel = BiDiUtils.isParagraphRtl(lastPart.getBidiEntity()) ? 1 : 0;
          final @BiDiEntity int bidiEntityForEllipsis = BiDiUtils.create(bidiParagraphLevel, bidiParagraphLevel, BiDiUtils.getIndex(lastPart.getBidiEntity()));
          if (lastPart.isStaticElement()) {
            // Easy path: just replace first found media with ellipsis

            boolean changedEllipsis = false;
            if (!ellipsis.equals(defaultEllipsis)) {
              ellipsis = defaultEllipsis;
              changedEllipsis = true;
            }
            ellipsisWidth = changedEllipsis || !lastPart.isSameEntity(entity) ? U.measureText(ellipsis, getTextPaint(lastPart.getEntity())) : ellipsisWidth;

            TextPart ellipsisPart = new TextPart(this, ellipsis, 0, ellipsis.length(), lastPart.getLineIndex(), lastPart.getParagraphIndex());
            ellipsisPart.setXY(currentX, currentY);
            ellipsisPart.setWidth(ellipsisWidth);
            ellipsisPart.setEntity(lastPart.getEntity());
            ellipsisPart.setBidiEntity(bidiEntityForEllipsis);
            out.set(out.size() - 1, ellipsisPart);
            currentX += ellipsisPart.getWidth();

            removeMedia(lastPart);

            done = true;
            break;
          }

          if (currentX + defaultEllipsisWidth <= lineMaxWidth) {
            final int ellipsisMaxWidth = lineMaxWidth - lastPart.getX();
            final TextPaint paint = getTextPaint(lastPart.getEntity());
            int subEnd = end > start ? Math.max(end, minEnd) : minEnd;
            ellipsis = TextUtils.ellipsize(in.substring(lastPart.getStart(), subEnd).replace('\n', ' '), paint, ellipsisMaxWidth - defaultEllipsisWidth, TextUtils.TruncateAt.END).toString();
            if (!StringUtils.isEmpty(ellipsis)) {
              if (!ellipsis.endsWith(defaultEllipsis)) {
                ellipsis += defaultEllipsis;
              }
              ellipsisWidth = BiDiUtils.measureTextRun(lastPart.getBidiEntity(), ellipsis, paint);
              final float defaultEllipsisWidth2 = U.measureText(defaultEllipsis, paint);
              if (currentX + ellipsisWidth <= lineMaxWidth) {
                lastPart.setLine(ellipsis, 0, ellipsis.length() - defaultEllipsis.length());
                lastPart.setWidth(ellipsisWidth - defaultEllipsisWidth2);
                currentX += (int) (ellipsisWidth - defaultEllipsisWidth2);

                TextPart defaultEllipsisPart = new TextPart(this, defaultEllipsis, 0, defaultEllipsis.length(), lastPart.getLineIndex(), lastPart.getParagraphIndex());
                defaultEllipsisPart.setXY(currentX, lastPart.getY());
                defaultEllipsisPart.setWidth(defaultEllipsisWidth2);
                defaultEllipsisPart.setEntity(entity);
                defaultEllipsisPart.setBidiEntity(bidiEntityForEllipsis);
                out.add(defaultEllipsisPart);
                currentX += (int) defaultEllipsisWidth2;

                done = true;
                break;
              }
            }
          }

          out.remove(out.size() - 1);
          removeMedia(lastPart);
          if (out.isEmpty())
            break;
          lastPart = out.get(out.size() - 1);
          if (lastPart.getLineIndex() != requiredLineIndex) {
            /*lineWidths.set(lineWidths.size() - 1, 0);
            ensureLineCount(true);
            requiredLineIndex = lastPart.getLineIndex();*/
            break;
          }
        } while (!out.isEmpty());
        if (!done) {
          currentX = 0;
          // Well, we have not found space enough to fit at least one character
          out.clear();
        }
      }
      int lastLineIndex = getLineCount() - 1;
      int[] prevSize = getLineSize(lastLineIndex);
      int prevWidth = prevSize[0];
      prevSize[0] = currentX;
      maxPartHeight = 0;
      for (int i = out.size() - 1; i >= 0; i--) {
        TextPart part = out.get(i);
        if (part.getLineIndex() != lastLineIndex)
          break;
        maxPartHeight = Math.max(part.getHeight(), maxPartHeight);
      }
      prevSize[1] = getCurrentLineHeight();
      if (currentX > prevWidth) {
        currentWidth = Math.max(currentWidth, currentX);
      } else if (currentX < prevWidth) {
        currentWidth = 0;
        for (int i = 0; i < getLineCount(); i++) {
          currentWidth = Math.max(getLineWidth(i), currentWidth);
        }
      }
      currentY += getCurrentLineHeight();
      throw e;
    }
  }

  public static class LimitReachedException extends IllegalStateException { }

  private void ensureLineCount (int lastLineHeight, int prevMaxPartHeight) {
    if (maxLineCount != -1 && getLineCount() >= maxLineCount) {
      currentY -= lastLineHeight;
      maxPartHeight = prevMaxPartHeight;
      while (!lineSizes.isEmpty() && (getLineCount() > maxLineCount || (getLineCount() > 1 && getLineWidth(getLineCount() - 1) == 0))) {
        lineSizes.remove(getLineCount() - 1);
        int lineHeight = getLineHeight(getLineCount() - 1);
        currentY -= lineHeight;
        maxPartHeight = lineHeight;
      }
      lastPart = null;
      throw new LimitReachedException();
    }
  }

  @Override
  public int getWidth () {
    return currentWidth + changeWidthOffset;
  }

  public int getLineWidth (int lineIndex) {
    return getLineSize(lineIndex)[0];
  }

  public int getLineHeight (int lineIndex) {
    return getLineSize(lineIndex)[1];
  }

  private int[] getLineSize (int lineIndex) {
    if (lineIndex < 0 || lineIndex >= getLineCount())
      throw new IllegalArgumentException("lineIndex == " + lineIndex);
    return lineSizes.get(lineIndex);
  }

  public int getLineMaxWidth (int lineIndex, int y) {
    if (lineIndex == -1)
      throw new IllegalArgumentException("lineIndex == -1");
    return (lineWidthProvider != null ? lineWidthProvider.provideLineWidth(lineIndex, y, maxWidth, getLineHeight()) : maxWidth - getLineStartMargin(lineIndex, y)) - suffixWidth;
  }

  public int getLineStartMargin (int lineIndex, int y) {
    if (lineIndex == -1)
      throw new IllegalArgumentException("lineIndex == -1");
    return lineMarginProvider != null ? lineMarginProvider.provideLineMargin(lineIndex, y, maxWidth, getLineHeight()) : 0;
  }

  public boolean endsWithCodeBlock () {
    final QuoteBackground lastQuote = quotes.isEmpty() ? null : quotes.get(quotes.size() - 1);
    return lastQuote != null && lastQuote.isCode && lastQuote.partEnd == parts.size();
  }

  // Where code blocks narrower than the text end, e.g. the end of the content of a bubble, or
  // NO_CODE_BLOCK_END, while each block ends at its own width
  public static final int NO_CODE_BLOCK_END = Integer.MIN_VALUE;
  private int codeBlockEnd = NO_CODE_BLOCK_END;

  public void setCodeBlockEnd (int codeBlockEnd) {
    this.codeBlockEnd = codeBlockEnd;
  }

  public int getCodeBlockEnd () {
    return codeBlockEnd;
  }

  public boolean endsWithExpandableQuote () {
    final QuoteBackground lastQuote = quotes.isEmpty() ? null : quotes.get(quotes.size() - 1);
    return lastQuote != null && lastQuote.canCollapse() && lastQuote.partEnd == parts.size();
  }

  public @Nullable int[] getSwitchedBlocks () {
    // Blocks of the text that a user switched, by pairs of an id and Settings.MessageState flags,
    // also those switched back: a default may change, e.g. that of a code block with the width
    if (blockSwitches == null) {
      return null;
    }
    IntList blocks = null;
    for (int i = 0; i < quotes.size(); i++) {
      final int blockId = quotes.get(i).entity.getQuoteId();
      if (blockSwitches.isChosen(blockId)) {
        if (blocks == null) {
          blocks = new IntList(2);
        }
        blocks.append(blockId);
        blocks.append(blockSwitches.isChosenOn(blockId) ? Settings.MessageState.BLOCK_ON : 0);
      }
    }
    return blocks != null ? blocks.get() : null;
  }

  public int getLastLineWidth () {
    // Background of code block at the end of the text is as wide as its widest line
    if (endsWithCodeBlock()) {
      return quotes.get(quotes.size() - 1).getCodeWidth();
    }
    return currentX;
  }

  public boolean getLastLineIsRtl () {
    return (parts != null && !parts.isEmpty() && parts.get(parts.size() - 1).isRtl()) || alignRight();
  }

  public boolean isFullyRtl () {
    return (textFlags & FLAG_FULL_RTL) != 0;
  }

  @Override
  public int getHeight () {
    return currentY + changeHeightOffset;
  }

  public int getLineCenterY () {
    return getLineHeight(false) / 2 + (parts.isEmpty() ? 0 : getPartVerticalOffset(parts.get(0)));
  }

  public int getNextLineHeight () {
    if (parts.isEmpty())
      return 0;
    TextPart lastPart = parts.get(parts.size() - 1);
    return lastPart.getY() + getLineHeight(lastPart.getLineIndex());
  }

  public boolean alignRight () {
    return (textFlags & FLAG_ALIGN_RIGHT) != 0;
  }

  public int getLineCount () {
    return lineSizes != null ? lineSizes.size() : 0;
  }

  public int getMaxLineCount () {
    return maxLineCount;
  }

  private float mTmpTextSizePx = -1;
  private final Paint.FontMetricsInt fm = new Paint.FontMetricsInt();

  public int getLineHeight () {
    return getLineHeight(true);
  }

  public void toRect (Rect outRect) {
    outRect.set(lastStartX, lastStartY, lastStartX + getWidth(), lastStartY + getHeight());
  }

  public void locatePart (Rect outRect, TextPart part) {
    locatePart(outRect, part, TextEntity.COMPARE_MODE_NORMAL);
  }

  public void locatePart (Rect outRect, TextPart touchPart, int compareMode) {
    if (isDestroyed()) {
      return;
    }
    outRect.set(
      0,
      touchPart.getY(),
      getLineWidth(touchPart.getLineIndex()),
      touchPart.getY() + getLineHeight(touchPart.getLineIndex())
    );
    if (getEntityCount() > 0) {
      outRect.left = touchPart.getX();
      outRect.right = touchPart.getX() + (int) touchPart.getWidth();
    }

    TextEntity entity = touchPart.getEntity();
    if (entity != null) {
      final int lineIndex = touchPart.getLineIndex();
      final int lineWidth = getLineWidth(lineIndex);
      final boolean center = BitwiseUtils.hasFlag(textFlags, FLAG_ALIGN_CENTER);

      int i = parts.indexOf(touchPart);
      if (i != -1) {
        int start = i;
        while (start > 0 && parts.get(start - 1).getLineIndex() == lineIndex && TextEntity.equals(entity, parts.get(start - 1).getEntity(), compareMode, originalText)) {
          start--;
        }
        int end = i;
        while (end + 1 < parts.size() && parts.get(end + 1).getLineIndex() == lineIndex && TextEntity.equals(entity, parts.get(end + 1).getEntity(), compareMode, originalText)) {
          end++;
        }
        int bound = getBackgroundPadding(defaultTextColorSet, entity, compareMode != TextEntity.COMPARE_MODE_NORMAL, false);
        outRect.top -= bound;
        outRect.bottom += bound;

        if (start != end) {
          final TextPart startPart = parts.get(start);
          final TextPart endPart = parts.get(end);
          int startPartStartX, startPartWidth;
          int endPartStartX, endPartWidth;

          startPartWidth = (int) startPart.getWidth();
          endPartWidth = (int) endPart.getWidth();

          if (center) {
            int cx = lastStartX + maxWidth / 2;
            startPartStartX = startPart.makeX(cx - lineWidth / 2, cx + lineWidth / 2, 0);
            endPartStartX = endPart.makeX(cx - lineWidth / 2, cx + lineWidth / 2, 0);
          } else {
            startPartStartX = startPart.makeX(lastStartX, lastEndX, lastEndXBottomPadding);
            endPartStartX = startPart.makeX(lastStartX, lastEndX, lastEndXBottomPadding);
          }
          if (startPartStartX <= endPartStartX) {
            outRect.left = startPartStartX;
            outRect.right = endPartStartX + endPartWidth;
          } else {
            outRect.left = endPartStartX;
            outRect.right = startPartStartX + startPartWidth;
          }
        } else {
          if (center) {
            int cx = lastStartX + maxWidth / 2;
            outRect.left = touchPart.makeX(cx - lineWidth / 2, cx + lineWidth / 2, 0);
          } else {
            outRect.left = touchPart.makeX(lastStartX, lastEndX, lastEndXBottomPadding);
          }
          outRect.right = outRect.left + (int) touchPart.getWidth();
        }
      }
    }
    outRect.offset(0, lastStartY);
  }

  public TextPart getTextPart (int index) {
    return parts.get(index);
  }

  int getPartCount () {
    return parts != null ? parts.size() : 0;
  }

  int getPartX (TextPart part, int startX, int endX, int endXBottomPadding) {
    if ((textFlags & FLAG_ALIGN_CENTER) != 0) {
      final int width = getLineWidth(part.getLineIndex());
      final int centerX = startX + (startX == endX ? maxWidth : endX - startX) / 2;
      return part.makeX(centerX - width / 2, centerX + width / 2, 0);
    }
    return part.makeX(startX, endX, endXBottomPadding);
  }

  @Nullable SyntaxLines findCodeLinesAt (int index) {
    for (int i = 0; i < quotes.size(); i++) {
      final QuoteBackground quote = quotes.get(i);
      if (quote.lines != null && index >= quote.entity.getStart() &&
        index < getBlockEnd(quote.entity)) {
        return quote.lines;
      }
    }
    return null;
  }

  private @Nullable QuoteBackground findQuoteOfPart (int partIndex) {
    // Blocks go in the order of their parts, and no part is in two of them
    int low = 0, high = quotes.size() - 1;
    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final QuoteBackground quote = quotes.get(mid);
      if (partIndex < quote.partStart) {
        high = mid - 1;
      } else if (partIndex >= quote.partEnd) {
        low = mid + 1;
      } else {
        return quote;
      }
    }
    return null;
  }

  @Nullable QuoteBackground findBlockOfPart (int partIndex) {
    final QuoteBackground quote = findQuoteOfPart(partIndex);
    return quote != null && quote.clipsContent() ? quote : null;
  }

  @Nullable QuoteBackground findBlockById (int quoteId) {
    if (quoteId == -1) {
      return null;
    }
    for (int i = 0; i < quotes.size(); i++) {
      final QuoteBackground quote = quotes.get(i);
      if (quote.clipsContent() && quote.entity.getQuoteId() == quoteId) {
        return quote;
      }
    }
    return null;
  }

  public Paint.FontMetricsInt getFontMetrics (float textSizePx) {
    if (mTmpTextSizePx == -1 || mTmpTextSizePx != textSizePx) {
      TextPaint paint = textStyleProvider.getTextPaint();
      paint.getFontMetricsInt(fm);
      mTmpTextSizePx = textSizePx;
    }
    return fm;
  }

  private Paint.FontMetricsInt getFontMetrics () {
    return getFontMetrics(textStyleProvider.getTextSizeInPixels());
  }

  int getAscent (float textSizePx) {
    return -getFontMetrics(textSizePx).ascent;
  }

  private String drawnText;
  private char[] drawnChars;

  @Nullable
  char[] getDrawnChars (String line) {
    // Canvas reads a char[] in place, while it copies a whole String of Latin-1 characters for each
    // drawn part; they're made at the first drawing, on the thread that draws
    if (line != originalText) {
      return null;
    }
    char[] chars = drawnChars;
    if (drawnText != line || chars == null || chars.length != line.length()) {
      chars = line.toCharArray();
      drawnChars = chars;
      drawnText = line;
    }
    return chars;
  }


  public static int getLineHeight (TextStyleProvider textStyleProvider, boolean needPadding) {
    TextPaint paint = textStyleProvider.getTextPaint();
    Paint.FontMetricsInt fm = paint.getFontMetricsInt();
    return (Math.abs(fm.descent - fm.ascent) + fm.leading) + (needPadding ? textStyleProvider.convertUnit(2f) : 0);
  }

  public int getLineHeight (boolean needPadding) {
    Paint.FontMetricsInt fm = getFontMetrics();
    return (Math.abs(fm.descent - fm.ascent) + fm.leading) + (needPadding ? getLineSpacing() : 0);
  }

  public int getLineSpacing () {
    return BitwiseUtils.hasFlag(textFlags, FLAG_NO_SPACING) ? 0 : textStyleProvider.convertUnit((textFlags & FLAG_ARTICLE) != 0 ? 3f : 2f);
  }

  private Paint lastSpacePaint;
  private float lastSpaceSize;

  private float makeSpaceSize (Paint paint) {
    if (lastSpaceSize == 0f || lastSpacePaint != paint) {
      lastSpaceSize = U.measureText(" ", paint);
      lastSpacePaint = paint;
    }
    return lastSpaceSize;
  }

  private int drawPartCentered (int partIndex, Canvas c, int x, int y, int maxWidth, float alpha, @Nullable TextColorSet defaultTheme, @Nullable ComplexReceiver receiver) {
    TextPart part = parts.get(partIndex);
    int width = getLineWidth(part.getLineIndex());
    int cx = x + maxWidth / 2;

    int count = 1;
    int partCount = parts.size();
    TextPart lastPart = part;
    while (++partIndex < partCount) {
      TextPart curPart = parts.get(partIndex);
      if (lastPart.wouldMergeWithNextPart(curPart)) {
        lastPart = curPart;
        count++;
      } else {
        break;
      }
    }

    y += getPartVerticalOffset(part);

    Spoiler spoiler = findSpoiler(part.getSpoiler());
    if (spoiler != null) {
      alpha *= spoiler.getContentAlpha();
    }
    if (alpha > 0f) {
      if (count > 1) {
        part.drawMerged(partIndex, c, lastPart.getEnd(), cx - width / 2, cx + width / 2, 0, y, alpha, defaultTheme);
      } else {
        part.draw(partIndex, c, cx - width / 2, cx + width / 2, 0, y, alpha, defaultTheme, receiver);
      }
    }
    return count;
  }

  public boolean hasMedia () {
    return media != null && !media.isEmpty();
  }

  public int getMediaCount () {
    return hasMedia() ? media.size() : 0;
  }

  public boolean invalidateMediaContent (ComplexReceiver textMediaReceiver, @Nullable TextMedia specificMedia) {
    if (BitwiseUtils.hasFlag(textFlags, FLAG_DESTROYED)) {
      return false;
    }
    if (specificMedia == null) {
      // Force parent to call requestMedia() instead
      return false;
    }
    final long displayMediaKey = specificMedia.getDisplayMediaKey();
    if (displayMediaKey == -1 || media.get(specificMedia.keyId) != specificMedia) {
      return false; // Don't invalidate what wasn't requested
    }
    if (!hasMedia()) {
      textMediaReceiver.clearReceivers(displayMediaKey);
      return false;
    }
    specificMedia.requestFiles(textMediaReceiver);
    return true;
  }

  public int requestMedia (ComplexReceiver textMediaReceiver) {
    return requestMedia(textMediaReceiver, -1, -1);
  }

  public int requestMedia (ComplexReceiver textMediaReceiver, long keyOffset, long maxKeyCount) {
    if (!hasMedia())
      return 0;
    boolean clear = keyOffset == -1 && maxKeyCount == -1;
    if (clear) {
      keyOffset = 0;
      maxKeyCount = Long.MAX_VALUE;
    }
    long maxMediaId = -1;
    int mediaCount = 0;
    final Set<TextMedia> mediaOfHiddenParts = findMediaOfHiddenParts();
    for (Map.Entry<String, TextMedia> entry : media.entrySet()) {
      TextMedia media = entry.getValue();
      if (media.id >= maxKeyCount)
        throw new IllegalArgumentException();
      media.setDisplayMediaKeyOffset(keyOffset);
      if (!media.attachedToParts.isEmpty() || mediaOfHiddenParts.contains(media)) {
        media.requestFiles(textMediaReceiver);
      } else {
        textMediaReceiver.clearReceivers(media.getDisplayMediaKey());
      }
      maxMediaId = Math.max(maxMediaId, media.id);
      mediaCount++;
    }
    if (clear && maxMediaId != -1) {
      textMediaReceiver.clearReceiversWithHigherKey(keyOffset + maxMediaId + 1);
    }
    return mediaCount;
  }

  private int drawPart (final int partIndex, Canvas c, int startX, int endX, int endXBottomPadding, int y, float alpha, @Nullable TextColorSet defaultTheme, @Nullable ComplexReceiver receiver) {
    TextPart part = parts.get(partIndex);

    final int firstPartX = part.getX();
    int mergeOffset = 0;
    int count = 1;
    int partCount = parts.size();
    TextPart lastPart = part;
    int i = partIndex;
    while (++i < partCount) {
      TextPart curPart = parts.get(i);
      if (lastPart.wouldMergeWithNextPart(curPart)) {
        lastPart = curPart;
        count++;
        mergeOffset = Math.max(mergeOffset, firstPartX - curPart.getX());
      } else {
        break;
      }
    }

    y += getPartVerticalOffset(part);

    Spoiler spoiler = findSpoiler(part.getSpoiler());
    if (spoiler != null) {
      alpha *= spoiler.getContentAlpha();
    }
    if (alpha > 0f) {
      if (count > 1) {
        part.drawMerged(partIndex, c, lastPart.getEnd(), startX - mergeOffset, endX - mergeOffset, endXBottomPadding, y, alpha, defaultTheme);
      } else {
        part.draw(partIndex, c, startX, endX, endXBottomPadding, y, alpha, defaultTheme, receiver);
      }
    }
    return count;
  }

  private @Nullable QuoteBackground findBlockOfBackgrounds (int partIndex) {
    final QuoteBackground block = findQuoteOfPart(partIndex);
    return block != null && (block.isScrollable() || block.isCollapsed()) ? block : null;
  }

  private static void drawBackgrounds (
    Canvas c,
    @Nullable LongSparseArray<Background> backgrounds,
    int startY,
    float alpha
  ) {
    if (backgrounds == null) {
      return;
    }
    boolean saved = false;
    for (int i = 0; i < backgrounds.size(); i++) {
      Background background = backgrounds.valueAt(i);
      int outlineColor = ColorUtils.alphaColor(alpha, background.theme.outlineColor(false));
      int backgroundColor = ColorUtils.alphaColor(alpha, background.theme.backgroundColor(false));
      if (Color.alpha(outlineColor) > 0 || Color.alpha(backgroundColor) > 0) {
        if (!saved) {
          saved = true;
          c.save();
          c.translate(0, startY);
        }
        drawBackground(c, background.path, backgroundColor, outlineColor);
      }
    }
    if (saved) {
      c.restore();
    }
  }

  private void addHighlightPath (Path path, TextPart part, int startX, int endX, int endXBottomPadding, boolean center, boolean isPressed) {
    final int bound = getBackgroundPadding(defaultTextColorSet, part.getEntity(), isPressed, true);
    final int radius = Screen.dp(3f);
    final TextPaint paint = getTextPaint(part.getEntity());
    final Paint.FontMetricsInt fm = getFontMetrics(paint.getTextSize());

    int x;
    if (center) {
      int width = getLineWidth(part.getLineIndex());
      int cx = startX + maxWidth / 2;
      x = part.makeX(cx - width / 2, cx + width / 2, 0);
    } else {
      x = part.makeX(startX, endX, endXBottomPadding);
    }
    int y = part.getY();
    float width = part.getWidth();

    RectF highlightRect = Paints.getRectF();
    highlightRect.left = x - bound;
    highlightRect.top = y - bound;
    highlightRect.right = highlightRect.left + width + bound + bound;
    highlightRect.bottom = y + (part.getHeight() == -1 ? fm.descent - fm.ascent : part.getHeight()) + bound;
    highlightRect.offset(0, paint.baselineShift + getPartVerticalOffset(part));
    path.addRoundRect(highlightRect, radius, radius, Path.Direction.CW);
  }

  private void drawPressHighlight (Canvas c, int startX, int endX, int endXBottomPadding, int startY, boolean center, @Nullable PressHighlight highlight, float alpha, @Nullable TextColorSet defaultTheme) {
    if (highlight == null || highlight.isSpoilerReveal())
      return;
    final int partsCount = parts.size();
    int index = highlight.startPartIndex;
    if (index < 0 || index >= partsCount)
      return;
    TextEntity textEntity = parts.get(index).getEntity();
    if (textEntity == null)
      return;
    int backgroundColor = ColorUtils.alphaColor(alpha, getBackgroundColor(defaultTheme, textEntity, true));
    int outlineColor = ColorUtils.alphaColor(alpha, getOutlineColor(defaultTheme, textEntity, true));
    boolean hasBackground = Color.alpha(backgroundColor) != 0;
    boolean hasOutline = Color.alpha(outlineColor) != 0;
    if (!hasBackground && !hasOutline)
      return;

    Path pressHighlight = this.pressHighlights != null ? this.pressHighlights.get(index) : null;
    if (pressHighlight != null) {
      c.save();
      c.translate(0, startY);
      drawBackground(c, pressHighlight, backgroundColor, outlineColor);
      c.restore();
      return;
    }

    pressHighlight = new Path();
    if (this.pressHighlights == null)
      this.pressHighlights = new SparseArrayCompat<>();
    this.pressHighlights.put(index, pressHighlight);

    for (int i = index; i < partsCount; i++) {
      TextPart part = parts.get(i);
      if (!part.isSamePressHighlight(textEntity)) {
        break;
      }
      addHighlightPath(pressHighlight, part, startX, endX, endXBottomPadding, center, true);
    }

    c.save();
    c.translate(0, startY);
    drawBackground(c, pressHighlight, backgroundColor, outlineColor);
    c.restore();
  }

  private int lastStartX, lastEndX, lastEndXBottomPadding, lastStartY;

  public void beginDrawBatch (@Nullable ComplexReceiver receiver, int externalBatchId) {
    if (receiver != null) {
      for (TextPart part : parts) {
        part.beginDrawBatch(receiver, externalBatchId);
      }
    }
  }

  public void finishAllDrawBatches (@Nullable ComplexReceiver receiver) {
    finishDrawBatch(receiver, 0);
  }

  public void finishDrawBatch (@Nullable ComplexReceiver receiver, int externalBatchId) {
    if (receiver != null) {
      for (TextPart part : parts) {
        part.finishDrawBatch(receiver, externalBatchId);
      }
    }
  }

  public void draw (Canvas c, int startX, int startY) {
    draw(c, startX, startY, null, 1f);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY) {
    draw(c, startX, endX, endXBottomPadding, startY, null, 1f);
  }

  public void draw (Canvas c, int startX, int startY, @Nullable TextColorSet defaultTheme) {
    draw(c, startX, startY, defaultTheme, 1f);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme) {
    draw(c, startX, endX, endXBottomPadding, startY, defaultTheme, 1f);
  }

  public void draw (Canvas c, int startX, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha) {
    draw(c, startX, startY, defaultTheme, alpha, null);
  }

  public void draw (Canvas c, int startX, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha, ComplexReceiver receiver) {
    draw(c, startX, startX /* + getWidth()*/, 0, startY, defaultTheme, alpha, receiver);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha) {
    draw(c, startX, endX, endXBottomPadding, startY, defaultTheme, alpha, null);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha, @Nullable ComplexReceiver receiver) {
    if (parts == null || alpha == 0f)
      return;
    if (changePieces != null) {
      drawChange(c, startX, endX, endXBottomPadding, startY, defaultTheme, alpha, receiver);
      return;
    }

    //c.drawRect(startX, startY, startX + getWidth(), startY + getHeight(), Paints.strokeSmallPaint(0xFF000000));
    //c.drawRect(startX, startY, endX, startY + getHeight(), Paints.strokeSmallPaint(0xFF00FF00));

    final boolean needRestore = BitwiseUtils.hasFlag(textFlags, FLAG_NEED_CLIP_TEXT_AREA);
    final int saveCount;
    if (needRestore) {
      saveCount = Views.save(c);
      int bound = Screen.dp(4f);
      int lineMargin = 0;
      if (lineMarginProvider != null) {
        for (int i = 0; i < getLineCount(); i++) {
          lineMargin = Math.max(getLineStartMargin(i, 0/*FIXME*/), lineMargin);
        }
      }
      if (isFullyRtl() && startX != endX) {
        // c.drawRect(endX - lineMargin - getWidth() - bound, startY - bound, endX + bound, startY + getHeight() + bound, Paints.strokeSmallPaint(0xFF000000));
        c.clipRect(endX - lineMargin - getWidth() - bound, startY - bound, endX + bound, startY + getHeight() + bound);
      } else {
        // c.drawRect(startX - bound, startY - bound, startX + getWidth() + bound + lineMargin, startY + getHeight() + bound, Paints.strokeSmallPaint(0xFF000000));
        c.clipRect(startX - bound, startY - bound, startX + getWidth() + bound + lineMargin, startY + getHeight() + bound);
      }

    } else {
      saveCount = -1;
    }

    for (int i = 0; i < quotes.size(); i++) {
      quotes.get(i).draw(c, startX, endX, endXBottomPadding, startY);
    }

    lastStartY = startY;

    if (lastStartX != startX || lastEndX != endX || lastEndXBottomPadding != endXBottomPadding) {
      this.backgrounds = null;
      if (this.pressHighlights != null)
        this.pressHighlights.clear();
      clearSpoilers();
      this.lastStartX = startX;
      this.lastEndX = endX;
      this.lastEndXBottomPadding = endXBottomPadding;
    }

    final boolean center = (textFlags & FLAG_ALIGN_CENTER) != 0;

    if ((textFlags & FLAG_NEED_BACKGROUND) != 0) {
      if (backgrounds == null) {
        backgrounds = new LongSparseArray<>();
        blockBackgrounds = null;
        for (int i = 0; i < parts.size(); i++) {
          final TextPart part = parts.get(i);
          final TextEntity entity = part.getEntity();
          if (entity == null) {
            continue;
          }
          final TextColorSet theme = pickTheme(defaultTheme, entity);
          final long backgroundId = theme.backgroundId(false);
          if (backgroundId == 0) {
            continue;
          }
          // Backgrounds in a block that scrolls or fades out, e.g. of found text, are drawn with
          // its lines
          LongSparseArray<Background> target = backgrounds;
          final QuoteBackground block = findBlockOfBackgrounds(i);
          if (block != null) {
            if (blockBackgrounds == null) {
              blockBackgrounds = new SparseArrayCompat<>();
            }
            target = blockBackgrounds.get(block.partStart);
            if (target == null) {
              target = new LongSparseArray<>();
              blockBackgrounds.put(block.partStart, target);
            }
          }
          Background background = target.get(backgroundId);
          if (background == null) {
            background = new Background(new Path(), theme);
            target.put(backgroundId, background);
          }
          addHighlightPath(background.path, part, startX, endX, endXBottomPadding, center, false);
        }
      }
      drawBackgrounds(c, backgrounds, startY, alpha);
    }
    // A collapsed quote draws the highlight of a press with its lines, which fade out
    if (pressHighlight == null || findCollapsedQuote(pressHighlight.startPartIndex) == null) {
      drawPressHighlight(
        c,
        startX,
        endX,
        endXBottomPadding,
        startY,
        center,
        pressHighlight,
        alpha,
        defaultTheme
      );
    }

    if ((textFlags & FLAG_HAS_SPOILERS) != 0) {
      if (spoilers == null) {
        spoilers = new LongSparseArray<>();
        int partsCount = parts.size();
        for (int i = 0; i < partsCount;) {
          TextPart part = parts.get(i);
          TextEntity spoilerEntity = part.getSpoilerEntity();
          if (spoilerEntity != null) {
            Spoiler spoiler = new Spoiler(i, null);
            QuoteBackground spoilerQuote = findCollapsedQuote(i);
            spoiler.addPart(part, startX, endX, endXBottomPadding, center);
            while (++i < partsCount) {
              part = parts.get(i);
              if (!part.isSameSpoiler(spoilerEntity) && !(part.isWhitespace() && i + 1 < partsCount && parts.get(i + 1).isSameSpoiler(spoilerEntity))) {
                i--;
                break;
              }
              // A collapsed quote draws its spoilers with its lines, which fade out
              final QuoteBackground quote = findCollapsedQuote(i);
              if (quote != spoilerQuote) {
                spoiler.build();
                spoilers.put(BitwiseUtils.mergeLong(spoiler.offset, spoiler.length), spoiler);
                spoiler = new Spoiler(i, spoiler);
                spoilerQuote = quote;
              }
              spoiler.addPart(part, startX, endX, endXBottomPadding, center);
              TextEntity newSpoilerEntity = part.getSpoilerEntity();
              if (newSpoilerEntity != null) {
                spoilerEntity = newSpoilerEntity;
              }
            }
            spoiler.build();
            spoilers.put(BitwiseUtils.mergeLong(spoiler.offset, spoiler.length), spoiler);
          }
          i++;
        }
      }
      drawSpoilers(c, startY, defaultTheme, null);
    }

    int partCount = parts.size();
    int topLayerPartsCount = 0;
    int blockIndex = findBlockToDraw(0);
    for (int i = 0; i < partCount; ) {
      // A block draws all its parts, those of the top layer too
      if (blockIndex != -1 && i == quotes.get(blockIndex).partStart) {
        final QuoteBackground block = quotes.get(blockIndex);
        drawBlock(
          block,
          c,
          startX,
          endX,
          endXBottomPadding,
          startY,
          alpha,
          defaultTheme,
          receiver
        );
        i = block.partEnd;
        blockIndex = findBlockToDraw(blockIndex + 1);
        continue;
      }
      TextPart part = parts.get(i);
      if (part.requiresTopLayer()) {
        TextPart nextPart;
        do {
          nextPart = i + 1 < partCount ? parts.get(i + 1) : null;
          topLayerPartsCount++;
          i++;
          if (nextPart != null && part.wouldMergeWithNextPart(nextPart)) {
            part = nextPart;
          } else {
            break;
          }
        } while (true);
        continue;
      }
      if (center) {
        i += drawPartCentered(i, c, startX, startY, startX == endX ? maxWidth : endX - startX, alpha, defaultTheme, receiver);
      } else {
        i += drawPart(i, c, startX, endX, endXBottomPadding, startY, alpha, defaultTheme, receiver);
      }
    }
    if (topLayerPartsCount > 0) {
      blockIndex = findBlockToDraw(0);
      for (int i = 0; i < partCount && topLayerPartsCount > 0; ) {
        if (blockIndex != -1 && i == quotes.get(blockIndex).partStart) {
          i = quotes.get(blockIndex).partEnd;
          blockIndex = findBlockToDraw(blockIndex + 1);
          continue;
        }
        TextPart part = parts.get(i);
        if (part.requiresTopLayer()) {
          int drawCount;
          if (center) {
            drawCount = drawPartCentered(i, c, startX, startY, startX == endX ? maxWidth : endX - startX, alpha, defaultTheme, receiver);
          } else {
            drawCount = drawPart(i, c, startX, endX, endXBottomPadding, startY, alpha, defaultTheme, receiver);
          }
          topLayerPartsCount -= drawCount;
        }
        i++;
      }
    }
    if (needRestore) {
      Views.restore(c, saveCount);
    }
  }

  private void drawBlock (
    QuoteBackground block,
    Canvas c,
    int startX,
    int endX,
    int endXBottomPadding,
    int startY,
    float alpha,
    @Nullable TextColorSet defaultTheme,
    @Nullable ComplexReceiver receiver
  ) {
    final boolean isScrollable = block.isScrollable();
    final Canvas content = isScrollable ? block.beginContent(c) : c;
    final int fadeSaveCount = block.beginFade(content);
    if (blockBackgrounds != null) {
      drawBackgrounds(content, blockBackgrounds.get(block.partStart), startY, alpha);
    }
    if (block.isCollapsed()) {
      if (pressHighlight != null && findCollapsedQuote(pressHighlight.startPartIndex) == block) {
        final boolean center = BitwiseUtils.hasFlag(textFlags, FLAG_ALIGN_CENTER);
        drawPressHighlight(
          content,
          startX,
          endX,
          endXBottomPadding,
          startY,
          center,
          pressHighlight,
          alpha,
          defaultTheme
        );
      }
      drawSpoilers(content, startY, defaultTheme, block);
    }
    if (block.lines != null) {
      block.lines.draw(content, startX, endX, endXBottomPadding, startY, alpha, defaultTheme);
    } else {
      for (int i = block.partStart; i < block.partEnd; ) {
        i += drawPart(
          i,
          content,
          startX,
          endX,
          endXBottomPadding,
          startY,
          alpha,
          defaultTheme,
          receiver
        );
      }
    }
    block.finishFade(content, fadeSaveCount);
    if (isScrollable && block.finishContent(c, content)) {
      invalidateViews();
    }
  }

  private void drawSpoilers (
    Canvas c,
    int startY,
    @Nullable TextColorSet defaultTheme,
    @Nullable QuoteBackground block
  ) {
    // Spoilers of a collapsed quote are drawn with its lines, which fade out
    if (spoilers == null) {
      return;
    }
    boolean saved = false;
    // Spoilers go in the order of their parts
    int i = 0;
    if (block != null) {
      int high = spoilers.size();
      while (i < high) {
        final int mid = (i + high) >>> 1;
        if (spoilers.valueAt(mid).startPartIndex < block.partStart) {
          i = mid + 1;
        } else {
          high = mid;
        }
      }
    }
    for (; i < spoilers.size(); i++) {
      Spoiler spoiler = spoilers.valueAt(i);
      if (block != null && spoiler.startPartIndex >= block.partEnd) {
        break;
      }
      if (spoiler.willDraw() && findCollapsedQuote(spoiler.startPartIndex) == block) {
        if (!saved) {
          saved = true;
          c.save();
          c.translate(0, startY);
        }
        TextColorSet theme = pickTheme(defaultTheme, parts.get(spoiler.startPartIndex).getEntity());
        spoiler.draw(c, theme.iconColor());
      }
    }
    if (saved) {
      c.restore();
    }
  }

  private boolean isUnderFade (int partIndex) {
    final QuoteBackground quote = findCollapsedQuote(partIndex);
    if (quote == null) {
      return false;
    }
    final int line = parts.get(partIndex).getLineIndex();
    return (quote.hasBottomFade() && line == parts.get(quote.partEnd - 1).getLineIndex()) ||
      (quote.hasTopFade() && line == parts.get(quote.partStart).getLineIndex());
  }

  private @Nullable QuoteBackground findCollapsedQuote (int partIndex) {
    final QuoteBackground quote = findQuoteOfPart(partIndex);
    return quote != null && quote.isCollapsed() ? quote : null;
  }

  public static final long CHANGE_DURATION = 220L;

  private @Nullable FloatAnimator changeAnimator;
  private @Nullable TextPieces changePieces;
  private float changeFactor, changeStartFactor;
  private @Nullable QuoteBackground switchingBlock;
  // Width before the change minus after it, and the part of it left on this frame
  private int changeWidthDiff, changeWidthOffset;
  // Height at the factors 0 and 1 of the change, and what this frame adds to the layout's
  private float changeHeightFrom, changeHeightTo;
  private int changeHeightOffset;
  private int blockSwitchesVersion;

  public boolean isSwitchingBlock () {
    return switchingBlock != null;
  }

  public float placeStickyElement (
    StickyElements.Element element,
    float x,
    float minY,
    float maxY,
    float padding,
    boolean atTop
  ) {
    if (blockSwitches == null) {
      return atTop ? minY : maxY;
    }
    return blockSwitches.placeStickyElement(this, element, x, minY, maxY, padding, atTop);
  }

  public float getSwitchFactor (QuoteBackground block) {
    if (block == switchingBlock) {
      return block.isSwitchedOn() ? changeFactor : 1f - changeFactor;
    }
    // A block whose text changes while it switches goes on from where it was
    final float switchFactor = block.isSwitchedOn() ? 1f : 0f;
    final float startFactor = block.getChangeStartSwitchFactor();
    if (startFactor == -1f) {
      return switchFactor;
    }
    return MathUtils.fromTo(startFactor, switchFactor, changeFactor);
  }

  public boolean isAnimatingChange () {
    return changePieces != null;
  }

  public float getChangeProgress () {
    // Others follow it, e.g. the position of the message in the list
    return changePieces == null || changeStartFactor == 1f ? 1f :
      (changeFactor - changeStartFactor) / (1f - changeStartFactor);
  }

  private int findYOfChar (int index, boolean isFirstSeen) {
    // A character of a collapsed quote that isn't shown is at the start of the lines that the quote
    // shows when it's the first one seen or before them, so that they stay below the top of the
    // list, and else where its hidden lines start, so that they stay above the bottom
    for (int i = 0; i < quotes.size(); i++) {
      final QuoteBackground quote = quotes.get(i);
      if (!quote.isCollapsed()) {
        continue;
      }
      final boolean isAbove = index >= quote.entity.getStart() && index < quote.getShownStart();
      if (isAbove || index >= quote.getHiddenStart() && index < getBlockEnd(quote.entity)) {
        if (isFirstSeen || isAbove) {
          return parts.get(quote.partStart).getY();
        }
        final TextPart last = parts.get(quote.partEnd - 1);
        return last.getY() + getLineHeight(last.getLineIndex());
      }
    }
    for (int i = 0; i < parts.size(); i++) {
      final TextPart part = parts.get(i);
      if (part.getLine() == originalText && part.getEnd() > index) {
        return part.getY();
      }
    }
    return 0;
  }

  // A line of the layout before a switch, which its owner may keep on the screen
  private static final class LayoutLine {
    final int top;
    int pixelsTop = Integer.MAX_VALUE, pixelsBottom = Integer.MIN_VALUE;
    int firstSeenChar = Integer.MAX_VALUE;
    int startX;

    LayoutLine (int top, int startX) {
      this.top = top;
      this.startX = startX;
    }
  }

  private LayoutLine[] switchLinesBefore;
  private int switchBlockTop, switchBlockBottom;
  private boolean switchBlockStartsText, switchBlockEndsText;

  @Nullable
  private int[] findVisiblePart (@Nullable int[] visible, boolean atEnd) {
    if (blockSwitches == null || (atEnd && visible == null)) {
      return null;
    }
    final int[] range = new int[2];
    if (!blockSwitches.findVisiblePart(this, atEnd, range)) {
      return null;
    }
    final int[] result = visible != null ? visible : new int[4];
    final int index = atEnd ? 2 : 0;
    result[index] = range[0] - lastStartY;
    result[index + 1] = range[1] - lastStartY;
    return result;
  }

  private LayoutLine[] linesOfLayout (@Nullable int[] visible) {
    final ArrayList<LayoutLine> lines = new ArrayList<>();
    final LayoutLine[] partLines = new LayoutLine[parts.size()];
    final Rect bounds = new Rect();
    // Parts in a line with the same paint, e.g. words of code, are measured at once
    int groupStart = -1, groupLast = -1;
    for (int i = 0; i < parts.size(); i++) {
      final TextPart part = parts.get(i);
      if (part.getLine() != originalText || part.getEnd() <= part.getStart()) {
        continue;
      }
      if (visible != null && (part.getY() > visible[1] ||
        part.getY() + getLineHeight(part.getLineIndex()) < visible[0])) {
        continue;
      }
      LayoutLine line = findLine(lines, part.getY());
      if (line == null) {
        line = new LayoutLine(part.getY(), part.getX());
        lines.add(line);
      }
      partLines[i] = line;
      line.startX = Math.min(line.startX, part.getX());
      if (groupStart != -1 && (groupLast != i - 1 ||
        !continuesPixelsGroup(parts.get(groupLast), part, partLines[groupLast], line))) {
        addPixelsOfGroup(groupStart, groupLast, partLines[groupStart], bounds);
        groupStart = -1;
      }
      if (groupStart == -1) {
        groupStart = i;
      }
      groupLast = i;
    }
    if (groupStart != -1) {
      addPixelsOfGroup(groupStart, groupLast, partLines[groupStart], bounds);
    }
    for (int i = 0; i < parts.size(); i++) {
      final LayoutLine line = partLines[i];
      if (line == null) {
        continue;
      }
      final int seen = findFirstSeenChar(i, line.startX);
      if (seen != -1) {
        line.firstSeenChar = Math.min(line.firstSeenChar, seen);
      }
    }
    Collections.sort(lines, (a, b) -> Integer.compare(a.top, b.top));
    return lines.toArray(new LayoutLine[0]);
  }

  @Nullable
  private static LayoutLine findLine (List<LayoutLine> lines, int top) {
    // Parts go line after line, so the line of a part is mostly the last one
    for (int i = lines.size() - 1; i >= 0; i--) {
      if (lines.get(i).top == top) {
        return lines.get(i);
      }
    }
    return null;
  }

  private boolean continuesPixelsGroup (
    TextPart last,
    TextPart part,
    LayoutLine lastLine,
    LayoutLine line
  ) {
    return lastLine == line && last.getEnd() == part.getStart() && !last.isStaticElement() &&
      !part.isStaticElement() && last.getBidiEntity() == part.getBidiEntity() &&
      getTextPaint(last.getEntity()) == getTextPaint(part.getEntity()) &&
      getPartVerticalOffset(last) == getPartVerticalOffset(part);
  }

  private void addPixelsOfGroup (int first, int last, LayoutLine line, Rect bounds) {
    if (findPixelsOfPart(parts.get(first), parts.get(last).getEnd(), bounds)) {
      line.pixelsTop = Math.min(line.pixelsTop, bounds.top);
      line.pixelsBottom = Math.max(line.pixelsBottom, bounds.bottom);
    }
  }

  private boolean findPixelsOfPart (TextPart part, int end, Rect bounds) {
    final int y = part.getY() + getPartVerticalOffset(part);
    if (part.isStaticElement()) {
      bounds.set(0, y, 0, y + getPartHeight(part));
      return true;
    }
    final TextPaint paint = getTextPaint(part.getEntity());
    paint.getTextBounds(part.getLine(), part.getStart(), end, bounds);
    if (bounds.bottom <= bounds.top) {
      return false;
    }
    bounds.offset(0, y + getAscent(paint.getTextSize()) + paint.baselineShift);
    return true;
  }

  private int findFirstSeenChar (int partIndex, int lineStart) {
    // Code is monospace, so its characters share the width of the part
    final TextPart part = parts.get(partIndex);
    final QuoteBackground block = findBlockOfPart(partIndex);
    final int edge = lineStart + (block != null ? block.getScrollX() : 0);
    final int count = part.getEnd() - part.getStart();
    if (part.getX() >= edge) {
      return part.getStart();
    }
    if (part.getX() + part.getWidth() <= edge) {
      return -1;
    }
    final float charWidth = part.getWidth() / count;
    return part.getStart() + Math.min(count - 1, (int) ((edge - part.getX()) / charWidth));
  }

  public int[] getSwitchLines (int viewTop, int viewBottom) {
    // A line is seen by its glyphs, when more than BLOCK_SWITCH_ANCHOR_MIN_VISIBLE of their rows
    // is; the line after the switch is the one with the first character seen before it
    final int[] lines = {
      Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
    };
    if (switchLinesBefore == null) {
      return lines;
    }
    for (LayoutLine line : switchLinesBefore) {
      if (line.pixelsTop == Integer.MAX_VALUE || line.firstSeenChar == Integer.MAX_VALUE) {
        continue;
      }
      final int seen = Math.min(line.pixelsBottom + lastStartY, viewBottom) -
        Math.max(line.pixelsTop + lastStartY, viewTop);
      if (seen <= (line.pixelsBottom - line.pixelsTop) * Config.BLOCK_SWITCH_ANCHOR_MIN_VISIBLE) {
        continue;
      }
      final boolean isFirstSeen = lines[0] == Integer.MIN_VALUE;
      final int after = findYOfChar(line.firstSeenChar, isFirstSeen) + lastStartY;
      if (isFirstSeen) {
        lines[0] = line.top + lastStartY;
        lines[1] = after;
      }
      lines[2] = line.top + lastStartY;
      lines[3] = after;
    }
    return lines;
  }

  public int[] getSwitchBlock () {
    // Before the switch, in the view, then 1 when no text is above it and 1 when none is below
    return new int[] {
      switchBlockTop + lastStartY, switchBlockBottom + lastStartY,
      switchBlockStartsText ? 1 : 0, switchBlockEndsText ? 1 : 0
    };
  }

  public int getChangeRemainingHeight () {
    return currentY - getHeight();
  }

  int getChangeMargin () {
    // A line and a bar of the chat, e.g. of a pinned message that hides meanwhile
    return getLineHeight() + Screen.dp(48f);
  }

  public boolean needLayoutForBlockSwitches () {
    // A layout of text in another orientation could come before a switch
    return blockSwitches != null && blockSwitches.getVersion() != blockSwitchesVersion;
  }

  public void setAnimated (String in, TextEntity[] entities) {
    change(in, entities, null);
  }

  private void switchBlock (QuoteBackground block) {
    change(originalText, entities, block);
  }

  private void change (String in, TextEntity[] entities, @Nullable QuoteBackground block) {
    // A block switched back runs back from where it is, like BoolAnimator, other changes start
    // from the pieces on this frame
    final boolean isReverse = block != null && block == switchingBlock;
    final float factor = changeFactor;
    final TextPieces from = isReverse || changePieces == null ?
      TextPieces.ofLayout(this, lastStartX, lastEndX, lastEndXBottomPadding) :
      changePieces.atFactor(factor);
    // A change back starts where the pieces are drawn, maybe off their layout's way
    final TextPieces drawn =
      isReverse && changePieces != null ? changePieces.atFactor(factor) : null;
    final float startFactor = isReverse ? 1f - factor : 0f;
    final int widthBefore = isReverse ? currentWidth : getWidth();
    final int heightBefore = getHeight();
    final int[] blockIds = new int[quotes.size()];
    final RectF[] blockBounds = new RectF[quotes.size()];
    final RectF[] blockDrawn = new RectF[quotes.size()];
    final float[] blockSwitchFactors = new float[quotes.size()];
    final int[] blockContentShifts = new int[quotes.size()];
    for (int i = 0; i < quotes.size(); i++) {
      blockIds[i] = quotes.get(i).entity.getQuoteId();
      blockBounds[i] = new RectF();
      quotes.get(i).getChangeStart(
        blockBounds[i], lastStartX, lastEndX, lastEndXBottomPadding, isReverse
      );
      if (isReverse) {
        blockDrawn[i] = new RectF();
        quotes.get(i).getChangeStart(
          blockDrawn[i], lastStartX, lastEndX, lastEndXBottomPadding, false
        );
      } else {
        blockDrawn[i] = blockBounds[i];
      }
      blockSwitchFactors[i] = getSwitchFactor(quotes.get(i));
      blockContentShifts[i] = quotes.get(i).getContentShift();
    }
    // Pieces are made only in the part on the screen
    int[] visible = block != null ? findVisiblePart(null, false) : null;
    if (block != null) {
      final int blockId = block.entity.getQuoteId();
      // Lines that the user reads are found again in the new layout
      switchLinesBefore = linesOfLayout(visible);
      final RectF bounds = blockBounds[ArrayUtils.indexOf(blockIds, blockId)];
      switchBlockTop = Math.round(bounds.top);
      switchBlockBottom = Math.round(bounds.bottom);
      switchBlockStartsText = originalText.substring(0, block.entity.getStart()).trim().isEmpty();
      switchBlockEndsText = originalText.substring(getBlockEnd(block.entity)).trim().isEmpty();
      // From what the block shows now to the other state, whatever its default is
      blockSwitches.choose(blockId, !block.isSwitchedOn());
    } else {
      switchLinesBefore = null;
    }
    set(maxWidth, in, entities);
    if (hasMedia()) {
      notifyMediaChanged(null);
    }
    if (BitwiseUtils.hasFlag(textFlags, FLAG_HAS_SPOILERS)) {
      // Pieces would show text hidden by spoilers, so it changes at once
      if (blockSwitches != null) {
        blockSwitches.notifyTextSizeChanged(this);
      }
      invalidateViews();
      return;
    }
    switchingBlock = block != null ? findBlockById(block.entity.getQuoteId()) : null;
    changeWidthDiff = widthBefore - currentWidth;
    changeHeightTo = currentY;
    changeHeightFrom = startFactor < 1f ?
      (heightBefore - startFactor * currentY) / (1f - startFactor) : currentY;
    changeHeightOffset = heightBefore - currentY;
    for (int i = 0; i < quotes.size(); i++) {
      final QuoteBackground quote = quotes.get(i);
      final int index = ArrayUtils.indexOf(blockIds, quote.entity.getQuoteId());
      if (index != -1) {
        quote.startChange(
          blockBounds[index],
          blockSwitchFactors[index],
          blockContentShifts[index],
          lastStartX,
          lastEndX,
          lastEndXBottomPadding
        );
      }
    }
    if (changeAnimator == null) {
      changeAnimator = new FloatAnimator(
        CHANGE_DURATION,
        AnimatorUtils.DECELERATE_INTERPOLATOR,
        0f,
        new FloatAnimatorListener() {
          @Override
          public void onValueChange (float newValue) {
            applyChange(newValue);
          }

          @Override
          public void onValueChangeFinished (float finalValue, boolean byAnimationEnd) {
            if (finalValue == 1f) {
              finishChange();
              invalidateViews();
            }
          }
        }
      );
    }
    changeFactor = changeStartFactor = startFactor;
    if (switchingBlock != null) {
      blockSwitches.notifyBlockSwitchStart(this, isReverse);
      // The owner knows where the switch takes the text once it starts
      visible = findVisiblePart(visible, true);
    } else {
      visible = null;
    }
    // A huge block changes at the speed of what's seen of it, a change back from where it's drawn
    final KeptOnScreen kept = visible != null || isReverse ?
      new KeptOnScreen(visible, getChangeMargin(), getLineHeight(), startFactor) : null;
    if (kept != null) {
      addChangeOnScreen(kept, heightBefore, blockIds, blockDrawn);
    }
    changePieces = TextPieces.change(
      from,
      drawn,
      this,
      lastStartX,
      lastEndX,
      lastEndXBottomPadding,
      visible,
      startFactor,
      kept
    );
    if (kept != null) {
      keepChangeOnScreen(kept, heightBefore, blockIds, blockDrawn);
    }
    // The first frame is drawn before the animator changes its value
    changeAnimator.setForcedValue(startFactor);
    applyChange(startFactor);
    changeAnimator.setAnimatedValue(1f);
  }

  private void applyChange (float factor) {
    if (changePieces == null) {
      return;
    }
    changeFactor = factor;
    changeWidthOffset = Math.round(changeWidthDiff * (1f - factor));
    // The last frame has the layout's height, which an end kept on the screen gets at once
    changeHeightOffset = factor == 1f ? 0 : Math.round(
      (changeHeightFrom - currentY) * (1f - factor) + (changeHeightTo - currentY) * factor
    );
    for (int i = 0; i < quotes.size(); i++) {
      quotes.get(i).applyChange(factor);
    }
    if (blockSwitches != null) {
      blockSwitches.notifyTextSizeChanged(this);
    }
    invalidateViews();
  }

  private void addChangeOnScreen (
    KeptOnScreen kept,
    int drawnHeight,
    int[] blockIds,
    RectF[] blockDrawn
  ) {
    kept.add(drawnHeight, currentY);
    for (int i = 0; i < quotes.size(); i++) {
      final int index = ArrayUtils.indexOf(blockIds, quotes.get(i).entity.getQuoteId());
      if (index != -1) {
        quotes.get(i).addChangeOnScreen(kept, blockDrawn[index]);
      }
    }
  }

  private void keepChangeOnScreen (
    KeptOnScreen kept,
    int drawnHeight,
    int[] blockIds,
    RectF[] blockDrawn
  ) {
    changeHeightTo = kept.to(drawnHeight, currentY);
    changeHeightFrom = kept.from(drawnHeight, currentY, changeHeightTo);
    for (int i = 0; i < quotes.size(); i++) {
      final int index = ArrayUtils.indexOf(blockIds, quotes.get(i).entity.getQuoteId());
      if (index != -1) {
        quotes.get(i).keepChangeOnScreen(kept, blockDrawn[index]);
      }
    }
  }

  private void onChangeStopped () {
    if (BitwiseUtils.hasFlag(textFlags, FLAG_DESTROYED)) {
      blockSwitches.notifySwitchStopped(this);
    } else if (changePieces == null) {
      blockSwitches.notifyTextSizeChanged(this);
    }
  }

  private void finishChange () {
    for (int i = 0; i < quotes.size(); i++) {
      quotes.get(i).finishChange();
    }
    switchingBlock = null;
    changePieces = null;
    changeFactor = changeStartFactor = 0f;
    changeWidthDiff = changeWidthOffset = changeHeightOffset = 0;
    changeHeightFrom = changeHeightTo = 0f;
  }

  void invalidateViews () {
    if (viewProvider != null) {
      viewProvider.invalidate();
    }
  }

  private void drawChange (
    Canvas c,
    int startX,
    int endX,
    int endXBottomPadding,
    int startY,
    @Nullable TextColorSet defaultTheme,
    float alpha,
    @Nullable ComplexReceiver receiver
  ) {
    for (int i = 0; i < quotes.size(); i++) {
      quotes.get(i).draw(c, startX, endX, endXBottomPadding, startY);
    }
    lastStartY = startY;
    changePieces.draw(
      this,
      c,
      startX,
      endX,
      endXBottomPadding,
      startY,
      changeFactor,
      alpha,
      defaultTheme,
      receiver
    );
  }

  private int findBlockToDraw (int startIndex) {
    for (int i = startIndex; i < quotes.size(); i++) {
      final QuoteBackground block = quotes.get(i);
      if (block.isScrollable() || block.lines != null || block.isCollapsed()) {
        return i;
      }
    }
    return -1;
  }

  private static void drawBackground (Canvas c, Path path, int backgroundColor, int outlineColor) {
    if (Color.alpha(outlineColor) > 0) {
      c.drawPath(path, Paints.getProgressPaint(outlineColor, Screen.dp(1f)));
    }
    if (Color.alpha(backgroundColor) > 0) {
      c.drawPath(path, Paints.fillingPaint(backgroundColor));
    }
  }

  // Touch util

  public interface ClickCallback {
    @Nullable
    default ThemeDelegate getForcedTheme (View view, Text text) { return null; }
    default boolean forceInstantView (String link) { return false; }
    default TdApi.LinkPreview findLinkPreview (String link) { return null; }
    default boolean onCommandClick (View view, Text text, TextPart part, String command, boolean isLongPress) { return false; }
    default boolean onDateClick (View view, Text text, TextPart part, String date, TdApi.TextEntityTypeDateTime entity, boolean isLongPress) { return false; }
    default boolean onUsernameClick (String username) { return false; }
    default boolean onUserClick (long userId) { return false; }
    default boolean onEmailClick (String email) { return false; }
    default boolean onPhoneNumberClick (String phoneNumber) { return false; }
    default boolean onBankCardNumberClick (String cardNumber) { return false; }
    default boolean onTonAddressClick (String tonAddress) { return false; }
    default boolean onHashtagClick (String hashtag) { return false; }
    default boolean onUrlClick (View view, String link, boolean promptUser, @NonNull TdlibUi.UrlOpenParameters openParameters) { return false; }
    default boolean onAnchorClick (View view, String anchor) { return false; }
    default boolean onReferenceClick (View view, String name, String referenceAnchorName, @NonNull TdlibUi.UrlOpenParameters openParameters) { return false; }
    default boolean onButtonClick (View view, TdApi.InlineButton button, @NonNull TdlibUi.UrlOpenParameters openParameters) { return false; }
  }

  public boolean highlightPart (int index, boolean onlyClickable) {
    TextPart part = parts.get(index);
    TextEntity entity = part.getClickableEntity();
    if (entity == null) {
      return !onlyClickable;
    }
    if (viewProvider != null) {
      viewProvider.invalidate();
    }
    return true;
  }

  private int getPartHeight (TextPart part) {
    float partHeight = part.getHeight();
    return partHeight != -1 ? (int) partHeight : getLineHeight(part.getLineIndex());
  }

  int getPartVerticalOffset (TextPart part) {
    return hasMedia() ? (getLineHeight(part.getLineIndex()) - getPartHeight(part)) / 2 : 0;
  }

  private int findTextPart (int touchX, int touchY, int startX, int endX, int endXBottomPadding, int startY, boolean onlyClickable) {
    int x = touchX - startX;
    int y = touchY - startY;

    int i = 0;
    int candidateIndex = -1;
    boolean useCenter = (textFlags & FLAG_ALIGN_CENTER) != 0;
    // int lineHeight = getLineHeight();
    int touchBound = getEntityTouchPadding();
    for (TextPart part : parts) {
      // Code block is pressed as a whole, only within its background, and so are lines of a
      // collapsed quote under its fades, at its top or bottom
      if (isCodeBlock(part.getEntity()) || isUnderFade(i)) {
        i++;
        continue;
      }
      int searchX;

      if (useCenter) {
        int width = getLineWidth(part.getLineIndex());
        int cx = startX + maxWidth / 2;
        int sx = cx - width / 2, ex = cx + width / 2;
        searchX = part.makeX(sx, ex, 0) - startX;
      } else {
        searchX = part.makeX(startX, endX, endXBottomPadding) - startX;
      }

      int px = searchX;
      int py = part.getY() + getPartVerticalOffset(part);
      int px1 = px + (int) part.getWidth();
      int py1 = py + getPartHeight(part);

      if (x >= px && x < px1 && y >= py && y < py1) {
        if (candidateIndex == -1) {
          candidateIndex = i;
        }
        if (needRevealSpoiler(part) || part.getClickableEntity() != null) {
          return i;
        }
      } else if (candidateIndex == -1 && x >= px - touchBound && x <= px1 + touchBound && y >= py - touchBound && y <= py1 + touchBound) {
        candidateIndex = i;
      }
      i++;
    }
    if (candidateIndex != -1) {
      TextPart part = parts.get(candidateIndex);
      if (!onlyClickable || needRevealSpoiler(part) || part.getClickableEntity() != null) {
        return candidateIndex;
      }
    }
    return -1;
  }

  private ViewProvider viewProvider;

  private static class PressHighlight {
    public final int causePartIndex;
    public final int startPartIndex, endPartIndex;
    public final Spoiler spoiler;

    public PressHighlight (int causePartIndex, int startPartIndex, int endPartIndex, Spoiler spoiler) {
      this.causePartIndex = causePartIndex;
      this.startPartIndex = startPartIndex;
      this.endPartIndex = endPartIndex;
      this.spoiler = spoiler;
    }

    public boolean isSpoilerReveal () {
      return spoiler != null;
    }

    public boolean withinRange (int index) {
      return index >= startPartIndex && index <= endPartIndex;
    }
  }

  public boolean isPressed (int partIndex) {
    return pressHighlight != null && pressHighlight.withinRange(partIndex);
  }

  @Nullable
  private QuoteBackground pressedQuote;

  @Nullable
  private PressHighlight pressHighlight;
  private int touchX, touchY;
  // A message moves under the finger while its block switches
  private float touchRawX, touchRawY;

  public void setViewProvider (ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    for (QuoteBackground quote : quotes) {
      quote.setViewProvider(viewProvider);
    }
  }

  public ViewProvider getViewProvider () {
    return viewProvider;
  }

  public void cancelTouch () {
    boolean canceled = false;
    if (pressedQuote != null) {
      pressedQuote.performCancelTouch();
      pressedQuote = null;
      canceled = true;
    }

    if (pressHighlight != null) {
      if (pressHighlight.spoiler != null) {
        pressHighlight.spoiler.setPressed(false, true);
      }
      pressHighlight = null;
      canceled = true;
    }
    if (canceled) {
      if ((textFlags & FLAG_CUSTOM_LONG_PRESS) != 0) {
        cancelLongPress();
      }
      if (viewProvider != null) {
        viewProvider.invalidate();
      }
    }
    setInLongPress(false);
  }

  private void clearTouch () {
    setInLongPress(false);
  }

  private void cancelLongPress () {
    if (longPressTarget != null) {
      longPressTarget.removeCallbacks(this);
    }
  }

  private View longPressTarget;
  private ClickCallback longPressTargetCallback;

  private void scheduleLongPress (View view, ClickCallback clickCallback) {
    cancelLongPress();
    if (view != null) {
      longPressTarget = view;
      longPressTargetCallback = clickCallback;
      view.postDelayed(this, ViewConfiguration.getLongPressTimeout());
    }
  }

  @Override
  public void run () {
    View view = longPressTarget;
    if (view != null && pressHighlight != null && performLongPress(view)) {
      view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      if ((textFlags & FLAG_CUSTOM_LONG_PRESS) != 0) {
        setInLongPress(true);
      }
    }
  }

  private boolean inLongPress () {
    return (textFlags & FLAG_IN_LONG_PRESS) != 0;
  }

  private ViewGroup lockedView;

  private void setInLongPress (boolean inLongPress) {
    if (inLongPress() != inLongPress) {
      this.textFlags = BitwiseUtils.setFlag(this.textFlags, FLAG_IN_LONG_PRESS, inLongPress);
      if (inLongPress) {
        lockedView = longPressTarget != null ? (ViewGroup) longPressTarget.getParent() : null;
        if (lockedView != null) {
          lockedView.requestDisallowInterceptTouchEvent(true);
        }
      } else if (lockedView != null) {
        lockedView.requestDisallowInterceptTouchEvent(false);
        lockedView = null;
      }
    }
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return onTouchEvent(view, e,null);
  }

  public boolean onTouchEvent (View view, MotionEvent e, @Nullable ClickCallback callback) {
    if (parts == null || isDestroyed()) {
      return false;
    }

    // The ripple of a quote clicks itself when the end of its press is a tap
    boolean isQuoteClicked = false;
    if (pressedQuote != null && e.getAction() != MotionEvent.ACTION_DOWN) {
      isQuoteClicked = pressedQuote.onTouchEvent(view, e) && e.getAction() == MotionEvent.ACTION_UP;
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        touchX = (int) e.getX();
        touchY = (int) e.getY();
        touchRawX = e.getRawX();
        touchRawY = e.getRawY();

        final boolean onlyClickable = clickListener == null;
        final int causeIndex = findCodeBlockAt(touchX, touchY) != null ? -1 : findTextPart(
          touchX, touchY, lastStartX, lastEndX, lastEndXBottomPadding, lastStartY, onlyClickable
        );
        if (causeIndex == -1) {

          for (QuoteBackground quote : quotes) {
            if (quote.contains(touchX, touchY)) {
              pressedQuote = quote;
              if (quote.pressButtonAt(touchX, touchY)) {
                return true;
              }
              if (quote.isScrollable()) {
                if (startCodeBlockTouch(view, quote, e)) {
                  // A touch that stops fling or overscroll drags the block, as in scroll views
                  cancelTouch();
                  return true;
                }
              } else {
                pressedQuote.onTouchEvent(view, e);
              }
              if ((textFlags & FLAG_CUSTOM_LONG_PRESS) != 0) {
                scheduleLongPress(view, callback);
              } else {
                longPressTargetCallback = callback;
              }
              return true;
            }
          }

          cancelTouch();
          return false;
        }
        TextPart foundPart = parts.get(causeIndex);
        int startIndex = causeIndex, endIndex = causeIndex;

        Spoiler spoiler = findSpoiler(foundPart.getSpoiler());
        boolean needRevealSpoiler = spoiler != null && spoiler.isClickable();
        if (needRevealSpoiler) {
          startIndex = spoiler.startPartIndex;
          endIndex = spoiler.startPartIndex + spoiler.partsCount;
        } else {
          if (onlyClickable && !foundPart.isClickable()) {
            cancelTouch();
            return false;
          }
          TextEntity clickableEntity = parts.get(causeIndex).getClickableEntity();
          if (clickableEntity != null) {
            while (startIndex > 0 && parts.get(startIndex - 1).isSamePressHighlight(clickableEntity)) {
              startIndex--;
            }
            while (endIndex + 1 < parts.size() && parts.get(endIndex + 1).isSamePressHighlight(clickableEntity)) {
              endIndex++;
            }
          }
        }

        this.pressHighlight = new PressHighlight(causeIndex, startIndex, endIndex, needRevealSpoiler ? spoiler : null);
        if (this.pressHighlight.isSpoilerReveal()) {
          this.pressHighlight.spoiler.setPressed(true, true);
        } else {
          if (highlightPart(causeIndex, clickListener == null)) {
            if ((textFlags & FLAG_CUSTOM_LONG_PRESS) != 0) {
              scheduleLongPress(view, callback);
            } else {
              longPressTargetCallback = callback;
            }
          }
        }

        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        clearTouch();
        finishCodeBlockTouch(view, 0);
        if (pressHighlight != null || pressedQuote != null) {
          cancelTouch();
          return true;
        } else {
          return false;
        }
      }
      case MotionEvent.ACTION_MOVE: {
        if (touchedCodeBlock != null) {
          return dragCodeBlock(view, e);
        }
        if (pressHighlight != null || pressedQuote != null) {
          final float moved =
            Math.max(Math.abs(touchRawX - e.getRawX()), Math.abs(touchRawY - e.getRawY()));
          if (moved > Screen.getTouchSlop()) {
            cancelTouch();
          }
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP: {
        clearTouch();
        if (touchedCodeBlock != null) {
          final boolean wasDragging = isDraggingCodeBlock;
          int velocityX = 0;
          if (wasDragging) {
            final ViewConfiguration configuration = ViewConfiguration.get(view.getContext());
            velocityTracker.addMovement(e);
            final int maxVelocity = configuration.getScaledMaximumFlingVelocity();
            velocityTracker.computeCurrentVelocity(1000, maxVelocity);
            final int velocity = (int) velocityTracker.getXVelocity();
            if (Math.abs(velocity) > configuration.getScaledMinimumFlingVelocity()) {
              velocityX = -velocity;
            }
          } else if (pressedQuote == touchedCodeBlock && !isCodeBlockPressShown) {
            // Tap ended before the press was shown
            touchedCodeBlock.pressRipple(touchX, touchY);
          }
          finishCodeBlockTouch(view, velocityX);
          if (wasDragging) {
            return true;
          }
        }
        if (pressedQuote != null) {
          if (pressedQuote.isCopyButtonPressed()) {
            // A tooltip at the icon in place of a toast, also where the system shows its own panel
            // of the clipboard
            final QuoteBackground copiedBlock = pressedQuote;
            final TextEntity copiedEntity = copiedBlock.entity;
            final String code = getCode(copiedEntity);
            UI.copyText(TD.toCopyText(code, copiedEntity.getCodeBlockType()), 0);
            UI.getContext(view.getContext()).tooltipManager()
              .builder(view, viewProvider)
              .locate((targetView, outRect) -> copiedBlock.getCopyButtonBounds(outRect))
              .show(null, R.string.CopiedCode)
              .hideDelayed();
            if (!isQuoteClicked) {
              ViewUtils.onClick(view);
            }
          }
          final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
          final boolean isTap = Math.abs(e.getRawX() - touchRawX) <= touchSlop &&
            Math.abs(e.getRawY() - touchRawY) <= touchSlop;
          final boolean switches = pressedQuote.isSwitchButtonPressed() ||
            (isTap && pressedQuote.hasSwitch() && !pressedQuote.isCopyButtonPressed());
          final QuoteBackground switchedBlock = switches ? pressedQuote : null;
          cancelTouch();
          if (switchedBlock != null) {
            switchBlock(switchedBlock);
            if (!isQuoteClicked) {
              ViewUtils.onClick(view);
            }
          }
          return true;
        } else if (pressHighlight != null) {
          TextPart part = parts.get(pressHighlight.causePartIndex);
          if (pressHighlight.isSpoilerReveal()) {
            revealSpoiler(part);
            ViewUtils.onClick(view);
            return true;
          }
          TextEntity entity = part.getClickableEntity();
          boolean done = false;
          if (clickListener != null) {
            done = clickListener.onClick(view, this, part, entity != null ? entity.openParameters(view, this, part, false) : new TdlibUi.UrlOpenParameters().tooltip(part.newTooltipBuilder(view)));
          } else if (entity != null) {
            entity.performClick(view, this, part, callback, false);
            done = true;
          }
          cancelTouch();
          if (done) {
            ViewUtils.onClick(view);
            return true;
          }
        }
        break;
      }
    }
    return pressHighlight != null || pressedQuote != null;
  }

  private @Nullable QuoteBackground touchedCodeBlock;
  private boolean isDraggingCodeBlock, isCodeBlockPressShown, disallowsParentIntercept;
  private int codeBlockMotionX;
  private VelocityTracker velocityTracker;
  private Runnable showCodeBlockPress;

  private boolean startCodeBlockTouch (View view, QuoteBackground block, MotionEvent e) {
    // A touch that catches fling or overscroll is a drag at once
    touchedCodeBlock = block;
    codeBlockMotionX = (int) e.getX();
    isCodeBlockPressShown = false;
    isDraggingCodeBlock = block.catchScroll(e.getY());
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    } else {
      velocityTracker.clear();
    }
    velocityTracker.addMovement(e);
    // Otherwise chats list, swipe to reply and the gesture back take horizontal drags
    setDisallowParentIntercept(view, true);
    if (isDraggingCodeBlock) {
      return true;
    }
    // Drag doesn't show press, like in scrolling views
    if (showCodeBlockPress == null) {
      showCodeBlockPress = () -> {
        if (touchedCodeBlock != null && pressedQuote == touchedCodeBlock && !isDraggingCodeBlock) {
          isCodeBlockPressShown = true;
          touchedCodeBlock.pressRipple(touchX, touchY);
        }
      };
    }
    view.postDelayed(showCodeBlockPress, ViewConfiguration.getTapTimeout());
    return false;
  }

  private boolean dragCodeBlock (View view, MotionEvent e) {
    final QuoteBackground block = touchedCodeBlock;
    velocityTracker.addMovement(e);
    final int x = (int) e.getX();
    int deltaX = codeBlockMotionX - x;
    if (!isDraggingCodeBlock) {
      final float dy = e.getRawY() - touchRawY;
      final int touchSlop = (int) Screen.getTouchSlop();
      if (Math.abs(deltaX) <= touchSlop && Math.abs(dy) <= touchSlop) {
        return true;
      }
      if (Math.abs(dy) >= Math.abs(deltaX) || (deltaX < 0 && block.isScrolledToStart())) {
        // Vertical drag scrolls the list, drag to the right from the start goes back
        finishCodeBlockTouch(view, 0);
        cancelTouch();
        return false;
      }
      isDraggingCodeBlock = true;
      cancelTouch();
      deltaX += deltaX > 0 ? -touchSlop : touchSlop;
    }
    codeBlockMotionX = x;
    block.dragBy(view.getContext(), deltaX, e.getY());
    view.invalidate();
    return true;
  }

  private void finishCodeBlockTouch (View view, int velocityX) {
    if (touchedCodeBlock != null) {
      touchedCodeBlock.endDrag(velocityX);
      if (isDraggingCodeBlock) {
        view.invalidate();
      }
      view.removeCallbacks(showCodeBlockPress);
      touchedCodeBlock = null;
      isDraggingCodeBlock = false;
      velocityTracker.recycle();
      velocityTracker = null;
    }
    setDisallowParentIntercept(view, false);
  }

  private void setDisallowParentIntercept (View view, boolean disallow) {
    if (disallowsParentIntercept != disallow) {
      disallowsParentIntercept = disallow;
      final ViewParent parent = view.getParent();
      if (parent != null) {
        parent.requestDisallowInterceptTouchEvent(disallow);
      }
    }
  }

  private @Nullable QuoteBackground findCodeBlockAt (int x, int y) {
    for (QuoteBackground quote : quotes) {
      if (quote.isCode && quote.contains(x, y)) {
        return quote;
      }
    }
    return null;
  }

  private static int getEntityTouchPadding () {
    return Screen.dp(4f);
  }

  // Sharing

  public boolean performLongPress (final View view) {
    final ClickCallback callback = longPressTargetCallback;

    if (pressedQuote != null) {
      final QuoteBackground quote = pressedQuote;
      final TextEntity entity = quote.entity;
      pressedQuote.performLongPress(view);
      cancelTouch();

      return entity != null && entity.performLongPress(view, this, null, (textFlags & FLAG_CUSTOM_LONG_PRESS_NO_SHARE) == 0, callback);
    }

    if (pressHighlight == null || pressHighlight.isSpoilerReveal()) {
      return false;
    }

    int causeIndex = this.pressHighlight.causePartIndex;
    cancelTouch();

    final TextPart part = parts.get(causeIndex);
    final TextEntity entity = part.getClickableEntity();

    return entity != null && entity.performLongPress(view, this, parts.get(causeIndex), (textFlags & FLAG_CUSTOM_LONG_PRESS_NO_SHARE) == 0, callback);
  }

  // Colors

  @NonNull
  public TextColorSet pickTheme (@Nullable TextColorSet defaultTheme, @Nullable TextEntity entity) {
    if (defaultTheme == null)
      defaultTheme = this.defaultTextColorSet;
    if (entity != null) {
      TextColorSet themeOverride = entity.getSpecialColorSet(defaultTheme);
      if (themeOverride != null)
        return themeOverride;
    }
    return defaultTheme;
  }

  public final boolean isClickable (TextEntity entity) {
    return (BitwiseUtils.hasFlag(textFlags, FLAG_ALL_CLICKABLE) || (entity != null && entity.isClickable())) && !BitwiseUtils.hasFlag(textFlags, FLAG_NO_CLICKABLE);
  }

  @ColorInt
  public int getTextColor (@Nullable TextColorSet defaultTheme, @Nullable TextEntity entity, boolean allClickable, boolean isPressed) {
    TextColorSet theme = pickTheme(defaultTheme, entity);
    boolean isClickable = isClickable(entity);
    boolean isQuote = entity != null && entity.isQuote();
    return isClickable ?
      theme.clickableTextColor(isPressed) :
      (isQuote ? theme.colorTheme().getColor(theme.quoteTextColorId()) : theme.defaultTextColor());
  }

  public long getMediaTextComplexColor () {
    TextColorSet theme = pickTheme(null, null);
    return theme.mediaTextComplexColor();
  }

  @ColorInt
  public int getTextColor () {
    return getTextColor(null, null, BitwiseUtils.hasFlag(textFlags, FLAG_ALL_CLICKABLE), false);
  }

  @NonNull
  public ThemeDelegate getColorTheme () {
    return this.defaultTextColorSet.colorTheme();
  }

  @PorterDuffColorId
  public int getQuoteTextColorId () {
    return this.defaultTextColorSet.quoteTextColorId();
  }

  @PorterDuffColorId
  public int getQuoteLineColorId () {
    return this.defaultTextColorSet.quoteLineColorId();
  }

  public int getCodeBlockBackgroundColorId () {
    return this.defaultTextColorSet.codeBlockBackgroundColorId();
  }

  public int getCodeBlockHeaderColorId () {
    return this.defaultTextColorSet.codeBlockHeaderColorId();
  }

  public int getCodeBlockRippleColorId () {
    return this.defaultTextColorSet.codeBlockRippleColorId();
  }

  @PorterDuffColorId
  public int getCodeBlockLineColorId () {
    return this.defaultTextColorSet.codeBlockLineColorId();
  }

  public int getCodeBlockBarColorId () {
    return this.defaultTextColorSet.codeBlockBarColorId();
  }

  @ColorInt
  public int getBackgroundColor (@Nullable TextColorSet defaultTheme, @Nullable TextEntity entity, boolean isPressed) {
    boolean isClickable = isClickable(entity);
    if (isClickable) {
      TextColorSet theme = pickTheme(defaultTheme, entity);
      return theme.backgroundColor(isPressed);
    } else {
      return 0;
    }
  }

  @ColorInt
  public int getOutlineColor (@Nullable TextColorSet defaultTheme, @Nullable TextEntity entity, boolean isPressed) {
    boolean isClickable = isClickable(entity);
    if (isClickable) {
      TextColorSet theme = pickTheme(defaultTheme, entity);
      return theme.outlineColor(isPressed);
    } else {
      return 0;
    }
  }

  @ColorInt
  public int getBackgroundPadding (@Nullable TextColorSet defaultTheme, @Nullable TextEntity entity, boolean isPressed, boolean shrinkOutline) {
    boolean isClickable = isClickable(entity);
    if (isClickable) {
      TextColorSet theme = pickTheme(defaultTheme, entity);
      int backgroundColorId = theme.backgroundColorId(isPressed);
      int outlineColorId = theme.outlineColorId(isPressed);
      int padding = theme.backgroundPadding();
      if (shrinkOutline && outlineColorId != 0)
        padding = Math.max(0, padding - Screen.separatorSize());
      return outlineColorId != 0 || backgroundColorId != 0 ? padding : 0;
    } else {
      return 0;
    }
  }

  private void clearMedia () {
    if (hasMedia()) {
      for (Map.Entry<String, TextMedia> entry : media.entrySet()) {
        entry.getValue().performDestroy();
      }
      media.clear();
    }
  }

  public boolean isDestroyed () {
    return BitwiseUtils.hasFlag(textFlags, FLAG_DESTROYED);
  }

  @Override
  public void performDestroy () {
    textFlags |= FLAG_DESTROYED;
    reset();
  }

  // Utils

  public static int indexOfSplitter (String in, int startIndex) {
    return indexOfSplitter(in, startIndex, null);
  }

  public static int indexOfSplitter (String in, int startIndex, @Nullable char[] special) {
    return indexOfSplitter(in, startIndex, in.length(), special);
  }

  public static int indexOfSplitter (String in, int startIndex, int endIndex, @Nullable char[] special) {
    if (startIndex >= endIndex)
      return -1;
    for (int i = startIndex; i < endIndex; ) {
      int codePoint = in.codePointAt(i);
      final int codePointType = Character.getType(codePoint);
      if (isSplitterCodePointType(codePoint, codePointType, true, special))
        return i;
      i += Character.charCount(codePoint);
    }
    return -1;
  }

  public static int lastIndexOfSplitter (String in, int startIndex, int endIndex, @Nullable char[] special) {
    if (startIndex >= endIndex)
      return -1;
    for (int i = endIndex; i > startIndex; ) {
      int codePoint = in.codePointBefore(i);
      final int codePointType = Character.getType(codePoint);
      if (i < endIndex && isSplitterCodePointType(codePoint, codePointType, true, special))
        return Math.max(startIndex, i);
      i -= Character.charCount(codePoint);
    }
    return -1;
  }

  public @Nullable TextMedia getTextMediaFromLastPart () {
    if (parts == null || parts.isEmpty()) return null;
    return parts.get(0).getMedia();
  }



  /* Bidi */

  private Bidi bidi;
  private boolean bidiRequired;
  private char[] bidiTmpChars;
  private int bidiStart;
  private int bidiLength;
  private int bidiIndex;
  private int bidiRunIndex;
  private int bidiParagraphCount;

  private void bidiProcessTextStart (String in, int length) {
    if (!useGlobalBidiObject()) {
      return;
    }

    bidiStart = 0;
    bidiLength = length;
    bidiRequired = BiDiUtils.requiresBidi(in, 0, bidiLength);

    if (bidiRequired) {
      bidiTmpChars = new char[bidiLength];
      bidiGetChars(in, 0, bidiLength, bidiTmpChars, true, true);

      bidi = new Bidi(bidiTmpChars, 0, null, 0, bidiLength, Lang.rtl() ? Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
      bidiParagraphCount++;
      bidiTmpChars = null;
    } else {
      bidi = null;
    }
  }

  private void bidiProcessLineStart (String in, int start, int end) {
    if (useGlobalBidiObject()) {
      return;
    }

    bidiStart = start;
    bidiLength = end - start;
    bidiRequired = BiDiUtils.requiresBidi(in, start, end);

    if (bidiRequired) {
      if (bidiTmpChars == null || bidiTmpChars.length < bidiLength) {
        final int size = Math.min(bidiLength * 3 / 2, in.length());
        bidiTmpChars = new char[size];
      }

      bidiGetChars(in, start, end, bidiTmpChars, true, false);

      bidi = new Bidi(bidiTmpChars, 0, null, 0, bidiLength, Lang.rtl() ? Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
      bidiParagraphCount++;
    } else {
      bidi = null;
    }
  }

  private void bidiProcessLineComplete () {
    if (useGlobalBidiObject()) {
      return;
    }

    bidi = null;
    bidiIndex++;
  }

  private void bidiPartsReorder () {
    final int partsCount = parts.size();
    if (bidiParagraphCount == 0 || partsCount < 2) {
      return;
    }

    // Fix order
    TextPart[] partsArray = parts.toArray(new TextPart[0]);
    byte[] partsLevels = new byte[parts.size()];
    for (int a = 0; a < partsLevels.length; a++) {
      partsLevels[a] = (byte) BiDiUtils.getLevel(partsArray[a].getBidiEntity());
    }

    int currentBidiIndex = 0;
    int lineIndex = 0;
    int rtlPartsCount = 0;
    int lastLineStartIndex = 0;
    for (int partIndex = 0; partIndex < partsCount; partIndex++) {
      final TextPart part = partsArray[partIndex];
      final @BiDiEntity int bidiEntity = part.getBidiEntity();
      final boolean paragraphIsRtl = BiDiUtils.isParagraphRtl(bidiEntity);
      final int bidiIndex = BiDiUtils.getIndex(bidiEntity);
      final int partLineIndex = part.getY(); //getLineIndex();
      if (lineIndex != partLineIndex || currentBidiIndex != bidiIndex) {
        bidiPartsReorder(partsArray, partsLevels, lastLineStartIndex, partIndex);
        currentBidiIndex = bidiIndex;
        lastLineStartIndex = partIndex;
        lineIndex = partLineIndex;
      }
      if (paragraphIsRtl) {
        rtlPartsCount += 1;
      }
    }
    bidiPartsReorder(partsArray, partsLevels, lastLineStartIndex, partsCount);

    if (rtlPartsCount == partsCount) {
      textFlags |= FLAG_FULL_RTL;
    }
  }

  private void bidiClear () {
    bidi = null;
    bidiRequired = false;
    bidiTmpChars = null;
    bidiStart = 0;
    bidiLength = 0;
    bidiIndex = 0;
    bidiRunIndex = 0;
    bidiParagraphCount = 0;
  }

  private @BiDiEntity int getBidiEntity (int start) {
    final int offset = start - bidiStart;
    if (0 <= offset && offset < bidiLength) {
      if (bidi != null) {
        return BiDiUtils.create(bidi.getLevelAt(offset), bidi.getBaseLevel(), bidiIndex);
      }
      if (!bidiRequired) {
        return BiDiUtils.create(0, 0, bidiIndex);
      }
    }

    return 0;   // illegal state ?
  }

  private int findBidiRunEnd (int start) {
    if (bidi == null) {
      return -1;
    }

    final int runsCount = bidi.getRunCount();
    if (runsCount > 1) {
      // Layout goes forward, so the search goes on from the last run found
      if (bidiRunIndex >= runsCount || start < bidiStart + bidi.getRunStart(bidiRunIndex)) {
        bidiRunIndex = 0;
      }
      for (; bidiRunIndex < runsCount; bidiRunIndex++) {
        int runEnd = bidiStart + bidi.getRunLimit(bidiRunIndex);
        if (start < runEnd) {
          return runEnd;
        }
      }
      throw new RuntimeException("Cannot find bidi run");
    }

    return -1;
  }

  private boolean useGlobalBidiObject () {
    return BitwiseUtils.hasAllFlags(textFlags, Text.FLAG_IGNORE_NEWLINES);
  }

  private void bidiPartsReorder (TextPart[] partsArray, byte[] partsLevels, int partStart, int partEnd ) {
    if (partEnd - partStart < 2) {
      return;
    }

    final boolean addSpaceBetweenLines = useGlobalBidiObject();
    int x = partsArray[partStart].getX();
    Bidi.reorderVisually(partsLevels, partStart, partsArray, partStart, partEnd - partStart);

    for (int index = partStart; index < partEnd; index++) {
      final TextPart part = partsArray[index];
      if (addSpaceBetweenLines && part.getEnd() < originalText.length() && Character.getDirectionality(originalText.charAt(part.getEnd())) == Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR) {
        x += (int) part.getSource().makeSpaceSize(part.getSource().getTextPaint(null));
      }

      part.setXY(x, part.getY());
      x += (int)(part.getWidth());
    }
  }

  private static void bidiGetChars (String in, int start, int end, char[] dst, boolean neutralizeEmoji, boolean ignoreParagraphSeparators) {
    in.getChars(start, end, dst, 0);

    for (int a = start; a < end; ) {
      final int codePoint = in.codePointAt(a);
      int count = Character.charCount(codePoint);

      boolean replaceWithNeutralDirectionChar = false;
      if (neutralizeEmoji) {
        int ltrEmojiCharCount = EmojiBidUtil.ltrEmojiCharCount(codePoint, count, in, a, end);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && ltrEmojiCharCount == 0) {
          ltrEmojiCharCount = EmojiBidUtilLegacy.ltrEmojiCharCountLegacy(codePoint, count);
        }
        if (ltrEmojiCharCount > 0) {
          count = ltrEmojiCharCount;
          // Regional indicators of flags keep their strong direction
          replaceWithNeutralDirectionChar = Config.FORCE_NEUTRAL_FLAG_DIRECTION ||
            !(codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF);
        }
      }
      if (!replaceWithNeutralDirectionChar && ignoreParagraphSeparators && Character.getDirectionality(codePoint) == Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR) {
        replaceWithNeutralDirectionChar = true;
      }

      if (replaceWithNeutralDirectionChar) {
        for (int b = 0; b < count; b++) {
          dst[a - start + b] = ' ';
        }
      }

      a += count;
    }
  }

  public static String bidiGenerateTestMessage () {
    StringBuilder b = new StringBuilder();
    for (int a = 0; a < EmojiCode.DATA.length; a++) {
      for (String emoji : EmojiCode.DATA[a]) {
        if (Strings.getTextDirection(emoji) == Strings.DIRECTION_LTR) {
          b.append(emoji);
          b.append(' ');
          b.append('\u062A');
          b.append('\n');
        }
      }
    }

    return b.toString();
  }

  /* Quote */

  public boolean allowQuotes () {
    return maxLineCount == -1 && !BitwiseUtils.hasFlag(textFlags, FLAG_ALIGN_CENTER);
  }

  private ArrayList<QuoteBackground> quotes = new ArrayList<>();
  private @Nullable ArrayList<QuoteBackground> previousQuotes, blocksWithoutTokens;
  private int previousQuoteIndex;

  private static String languageOf (TextEntity entity) {
    final String language = entity.getCodeLanguage();
    return language != null ? language : "";
  }

  private void highlight (QuoteBackground block, @Nullable SyntaxLines previousLines) {
    final int[] tokens = CodeHighlighter.getTokens(getCode(block.entity), languageOf(block.entity));
    if (tokens != null) {
      setTokens(block, tokens, false, previousLines);
    } else {
      if (blocksWithoutTokens == null) {
        blocksWithoutTokens = new ArrayList<>();
      }
      blocksWithoutTokens.add(block);
    }
  }

  private void requestTokens (ArrayList<QuoteBackground> blocks) {
    final ArrayList<QuoteBackground> layoutBlocks = quotes;
    for (QuoteBackground block : blocks) {
      CodeHighlighter.findTokens(
        getCode(block.entity),
        languageOf(block.entity),
        tokens -> onTokensFound(layoutBlocks, block, tokens)
      );
    }
  }

  private void setTokens (
    QuoteBackground block,
    int[] tokens,
    boolean animated,
    @Nullable SyntaxLines previousLines
  ) {
    if (tokens.length == 0) {
      return;
    }
    block.lines = SyntaxLines.build(
      this,
      block.partStart,
      block.partEnd,
      block.entity,
      block.entity.getStart(),
      tokens,
      animated && block.wasDrawn(),
      previousLines
    );
  }

  public boolean isAnimatingCodeColors () {
    for (int i = 0; i < quotes.size(); i++) {
      final QuoteBackground block = quotes.get(i);
      if (block.lines != null && block.lines.isAnimatingColors()) {
        return true;
      }
    }
    return false;
  }

  private void onTokensFound (
    ArrayList<QuoteBackground> layoutBlocks,
    QuoteBackground block,
    int[] tokens
  ) {
    synchronized (this) {
      if (isDestroyed() || quotes != layoutBlocks) {
        return;
      }
      final boolean isOnScreen = viewProvider != null && viewProvider.hasAnyTargetToInvalidate();
      setTokens(block, tokens, isOnScreen, null);
    }
    invalidateViews();
  }

  private void addQuote(QuoteBackground quoteBackground) {
    quoteBackground.calcHeightAddition();
    QuoteBackground previousBlock = null;
    if (previousQuotes != null && quoteBackground.clipsContent()) {
      // Blocks of both layouts go in the order of the text
      final int start = quoteBackground.entity.getStart();
      while (previousQuoteIndex < previousQuotes.size() &&
        previousQuotes.get(previousQuoteIndex).entity.getStart() < start) {
        previousQuoteIndex++;
      }
      for (int i = previousQuoteIndex; i < previousQuotes.size(); i++) {
        final QuoteBackground previousQuote = previousQuotes.get(i);
        if (previousQuote.entity.getStart() != start) {
          break;
        }
        if (previousQuote.isCode == quoteBackground.isCode) {
          previousBlock = previousQuote;
          break;
        }
      }
    }
    if (quoteBackground.isCode) {
      quoteBackground.calcCodeWidth(hasScrollingLines(quoteBackground.entity));
      currentWidth = Math.max(currentWidth, quoteBackground.getCodeWidth());
      highlight(quoteBackground, previousBlock != null ? previousBlock.lines : null);
    }
    if (previousBlock != null) {
      quoteBackground.restoreState(previousBlock);
    }
    quotes.add(quoteBackground);
  }

  private void collapseQuote (QuoteBackground quote, int start) {
    // Lines below the collapsed height are left out of the layout, and the text after them moves up
    if (blockSwitches == null || !quote.entity.isExpandableQuote()) {
      return;
    }
    final int quoteId = quote.entity.getQuoteId();
    int end = start;
    while (end < parts.size() && parts.get(end).getQuoteEntityId() == quoteId) {
      end++;
    }
    quote.partEnd = end;
    quote.calcHeightAddition();
    final int collapsedEnd = quote.findCollapsedEnd();
    if (collapsedEnd == start || collapsedEnd == end) {
      return;
    }
    int shownStart = start, shownEnd = collapsedEnd;
    final int foundLine = findFoundLine(quote, start, end);
    final int firstLine = foundLine != -1 ? quote.findShownFirstLine(foundLine) : 0;
    if (firstLine > 0) {
      shownStart = quote.findLinePart(firstLine);
      shownEnd = quote.findLinePart(firstLine + getCollapsedQuoteLineCount());
    }
    int shownStartChar = Integer.MAX_VALUE, hiddenStart = Integer.MAX_VALUE, hiddenWidth = 0;
    for (int i = start; i < end; i++) {
      final TextPart part = parts.get(i);
      final boolean isShown = i >= shownStart && i < shownEnd;
      if (part.getLine() == originalText) {
        if (isShown) {
          shownStartChar = Math.min(shownStartChar, part.getStart());
        } else if (i >= shownEnd) {
          hiddenStart = Math.min(hiddenStart, part.getStart());
        }
      }
      if (!isShown) {
        hiddenWidth = Math.max(hiddenWidth, part.getX() + (int) part.getWidth());
      }
    }
    final boolean isExpanded = blockSwitches.isChosen(quoteId) && blockSwitches.isChosenOn(quoteId);
    final int shiftHeight = shownStart > start ? quote.getHiddenAboveHeight(shownStart) : 0;
    final int hiddenHeight = shiftHeight + quote.getHiddenHeight(shownEnd);
    quote.setCollapsible(
      isExpanded,
      shownStartChar,
      shiftHeight,
      shownEnd < end,
      hiddenStart,
      hiddenWidth
    );
    if (isExpanded) {
      return;
    }
    for (int i = start; i < end; i++) {
      if (i < shownStart || i >= shownEnd) {
        removeMedia(parts.get(i));
      }
    }
    if (hiddenParts == null) {
      hiddenParts = new ArrayList<>();
    }
    hiddenParts.addAll(parts.subList(shownEnd, end));
    parts.subList(shownEnd, end).clear();
    hiddenParts.addAll(parts.subList(start, shownStart));
    parts.subList(start, shownStart).clear();
    for (int i = start; i < parts.size(); i++) {
      final TextPart part = parts.get(i);
      final boolean isShown = i < start + shownEnd - shownStart;
      part.setXY(part.getX(), part.getY() - (isShown ? shiftHeight : hiddenHeight));
    }
    currentY -= hiddenHeight;
  }

  private int findFoundLine (QuoteBackground quote, int start, int end) {
    // The line of the first found text in the quote, counted from its first line, or -1
    if (highlight == null) {
      return -1;
    }
    final int quoteStart = quote.entity.getStart(), quoteEnd = getBlockEnd(quote.entity);
    int foundStart = Integer.MAX_VALUE;
    for (Highlight.Part found : highlight.parts) {
      if (found.start >= quoteStart && found.start < quoteEnd) {
        foundStart = Math.min(foundStart, found.start);
      }
    }
    if (foundStart == Integer.MAX_VALUE) {
      return -1;
    }
    for (int i = start; i < end; i++) {
      final TextPart part = parts.get(i);
      if (part.getLine() == originalText && part.getEnd() > foundStart) {
        return part.getLineIndex() - parts.get(start).getLineIndex();
      }
    }
    return -1;
  }

  private void buildQuotes () {
    if (!allowQuotes()) {
      return;
    }

    QuoteBackground quote = null;
    int lastQuoteId = -1;
    int shiftX = 0;
    final int shiftY =
      Screen.dp(QuoteBackground.QUOTE_VERTICAL_PADDING + QuoteBackground.QUOTE_VERTICAL_MARGIN);

    for (int a = 0; a < parts.size(); a++) {
      TextPart part = parts.get(a);
      final TextEntity entity = part.getEntity();
      final int quoteId = entity != null ? entity.getQuoteId() : -1;
      if (quoteId != -1) {
        if (lastQuoteId != quoteId) {
          if (quote != null) {
            addQuote(quote);
          }
          lastQuoteId = quoteId;
          quote = new QuoteBackground(this, entity);
          quote.setViewProvider(viewProvider);
          quote.partStart = a;
          shiftX = Screen.dp(QuoteBackground.getRightPadding(entity));
          collapseQuote(quote, a);
          // A collapsed quote may leave out its first lines
          part = parts.get(a);
        }
        quote.partEnd = a + 1;
        part.setXY(part.getX() - shiftX, part.getY() - shiftY);

        if (a == parts.size() - 1) {
          currentY -= Screen.dp(QuoteBackground.QUOTE_VERTICAL_MARGIN);
        }
      }
    }
    if (quote != null) {
      addQuote(quote);
    }
    previousQuotes = null;
    if (blocksWithoutTokens != null) {
      // Once the blocks are complete, as tokens come on the main thread
      requestTokens(blocksWithoutTokens);
      blocksWithoutTokens = null;
    }
  }



  private static final int QUOTE_ADD_PADDING_HORIZONTAL = 1;
  private static final int QUOTE_ADD_PADDING_VERTICAL = 1 << 1;
  private static final int QUOTE_ADD_MARGIN_TOP = 1 << 2;
  private static final int QUOTE_ADD_MARGIN_BOTTOM = 1 << 3;

  private int quoteCheckAndMakeNewLines (final String in, final int start, int end, ArrayList<TextPart> out, @Nullable TextEntity entity) {
    final boolean allowQuotes = allowQuotes();
    if (!allowQuotes) {
      return 0;
    }

    final TextPart prevPart = out.isEmpty() ? null : out.get(out.size() - 1);
    final int prevPartQuoteId = prevPart != null ? prevPart.getQuoteEntityId() : -1;
    final boolean prevPartIsQuote = prevPartQuoteId != -1;

    final int nextPartQuoteId = entity != null ? entity.getQuoteId() : -1;
    final boolean nextPartIsQuote = nextPartQuoteId != -1;

    final boolean quoteChanged = prevPartQuoteId != nextPartQuoteId;
    if (quoteChanged && nextPartIsQuote) {
      // Text before the block may have wrapped, but the first line of the block doesn't continue it
      isCodeLineWrapped = false;
    }
    if (quoteChanged) {
      if (prevPartIsQuote) {
        final String line = prevPart.getLine();
        final TextEntity prevEntity = prevPart.getEntity();
        if (prevEntity != null) {
          if (prevEntity.end < line.length() && line.charAt(prevEntity.end) != '\n') {
            newLineOrEllipsis(out, in, start, end, entity);
          }
        }
      }
      if (prevPart != null && nextPartIsQuote && (!prevPartIsQuote || currentX != 0)) {
        if (entity.start > 0 && in.charAt(entity.start - 1) != '\n') {
          newLineOrEllipsis(out, in, start, end, entity);
        }
      }
    }

    if (!nextPartIsQuote) {
      return 0;
    }

    return QUOTE_ADD_PADDING_HORIZONTAL
      | (quoteChanged ? QUOTE_ADD_PADDING_VERTICAL | QUOTE_ADD_MARGIN_BOTTOM : 0)
      | (quoteChanged && prevPart != null && !prevPartIsQuote ? QUOTE_ADD_MARGIN_TOP : 0);
  }


  private boolean isSpaceAfterBlock (
    String in,
    int start,
    int end,
    List<TextPart> out,
    @Nullable TextEntity entity
  ) {
    // Spaces right after a block are dropped before the line breaks, as a wrapped line drops them
    if (out.isEmpty() || (entity != null && entity.getQuoteId() != -1)) {
      return false;
    }
    final TextEntity block = out.get(out.size() - 1).getEntity();
    if (block == null || block.getQuoteId() == -1 || findNewLines(in, block.end, start)) {
      return false;
    }
    for (int i = start; i < end; i++) {
      if (in.charAt(i) != ' ') {
        return false;
      }
    }
    return true;
  }

  private void quoteShiftCords (int flags, String in, @Nullable TextEntity entity) {
    if (BitwiseUtils.hasFlag(flags, QUOTE_ADD_PADDING_HORIZONTAL) && currentX == 0) {
      currentX = getQuoteLineStartX(entity);
      if (isCodeLineWrapped) {
        currentX += getCodeLineContinuationX(in, entity);
      }
      int offsetDp = 0;
      if (BitwiseUtils.hasFlag(flags, QUOTE_ADD_PADDING_VERTICAL)) {
        offsetDp += QuoteBackground.QUOTE_VERTICAL_PADDING * 2;
        offsetDp += QuoteBackground.getHeaderHeight(entity);
      }
      if (BitwiseUtils.hasFlag(flags, QUOTE_ADD_MARGIN_TOP)) {
        offsetDp += QuoteBackground.QUOTE_VERTICAL_MARGIN;
      }
      if (BitwiseUtils.hasFlag(flags, QUOTE_ADD_MARGIN_BOTTOM)) {
        offsetDp += QuoteBackground.QUOTE_VERTICAL_MARGIN;
      }
      currentY += Screen.dp(offsetDp);
    }
  }
}
