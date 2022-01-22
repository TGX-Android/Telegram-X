/**
 * File created on 02/05/15 at 13:00
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.DiffMatchPatch;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.td.Td;

public class Text implements Runnable, Emoji.CountLimiter, CounterAnimator.TextDrawable, ListAnimator.Measurable {
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

  private static final int FLAG_IN_LONG_PRESS = 1 << 24;
  private static final int FLAG_ABORT_PROCESS = 1 << 25;
  private static final int FLAG_FAKE_BOLD = 1 << 26;
  private static final int FLAG_FULL_RTL = 1 << 27;
  private static final int FLAG_MAY_APPLY_RTL = 1 << 28;
  private static final int FLAG_ELLIPSIZED = 1 << 29;
  private static final int FLAG_NEED_BACKGROUND = 1 << 30;
  private static final int FLAG_HAS_SPOILERS = 1 << 31;

  public static final int ENTITY_FLAG_URL = 1;
  public static final int ENTITY_FLAG_USERNAME = 1 << 1;
  public static final int ENTITY_FLAG_HASHTAG = 1 << 2;
  public static final int ENTITY_FLAG_COMMAND = 1 << 3;

  public static final int ENTITY_FLAGS_EXTERNAL = ENTITY_FLAG_URL;
  public static final int ENTITY_FLAGS_ALL_NO_COMMANDS = ENTITY_FLAGS_EXTERNAL | ENTITY_FLAG_USERNAME | ENTITY_FLAG_HASHTAG;
  public static final int ENTITY_FLAGS_ALL = ENTITY_FLAGS_ALL_NO_COMMANDS | ENTITY_FLAG_COMMAND;
  public static final int ENTITY_FLAGS_NONE = 0;

  public interface LineWidthProvider {
    int provideLineWidth (int lineIndex, int y, int defaultMaxWidth, int lineHeight);
  }

  public interface LineMarginProvider {
    int provideLineMargin (int lineIndex, int y, int defaultMaxWidth, int lineHeight);
  }

  public interface ClickListener {
    boolean onClick (View v, Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters);
  }

  private int maxWidth, textFlags;

  private final @Nullable LineWidthProvider lineWidthProvider;
  private final @Nullable LineMarginProvider lineMarginProvider;
  private final @Nullable ClickListener clickListener;
  private final int maxLineCount;
  private final TextStyleProvider textStyleProvider;
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

    private final BoolAnimator isRevealed = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        if (viewProvider != null) {
          viewProvider.invalidate();
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    public Spoiler (int startPartIndex) {
      this.startPartIndex = startPartIndex;
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

    public void draw (Canvas c, @Deprecated int iconColor) {
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
  private ArrayList<TextPart> parts;
  private LongSparseArray<Spoiler> spoilers;
  private LongSparseArray<Background> backgrounds;
  private SparseArrayCompat<Path> pressHighlights;
  private List<int[]> lineSizes;
  private int emojiCount;
  private int iconCount;
  private @Nullable TextEntity[] entities;

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

  public Text (@NonNull String in, int maxWidth, TextStyleProvider textStyleProvider, @NonNull TextColorSet textColorSet, int maxLineCount, int textFlags, @Nullable TextEntity[] entities) {
    this(in, maxWidth, textStyleProvider, textColorSet, maxLineCount, null, null, textFlags, entities, null, null);
  }

  private Text (@NonNull String in, int maxWidth, TextStyleProvider textStyleProvider, @NonNull TextColorSet textColorSet, int maxLineCount, @Nullable LineWidthProvider lineWidthProvider, @Nullable LineMarginProvider lineMarginProvider, int textFlags, @Nullable TextEntity[] entities, String suffix, ClickListener clickListener) {
    this.textFlags = textFlags;
    this.maxWidth = maxWidth;
    this.maxLineCount = maxLineCount;
    this.lineWidthProvider = lineWidthProvider;
    this.lineMarginProvider = lineMarginProvider;
    this.clickListener = clickListener;
    this.textStyleProvider = textStyleProvider;
    this.defaultTextColorSet = textColorSet;
    this.entities = entities;
    this.suffix = suffix;
    this.suffixWidth = !StringUtils.isEmpty(suffix) ? (int) U.measureText(this.suffix, getTextPaint(null)) : 0;
    set(maxWidth, in);
  }

  public static class Builder {
    private String in;
    private int maxWidth;
    private TextStyleProvider provider;
    private TextColorSet theme;

    private int maxLineCount = -1;
    private LineWidthProvider lineWidthProvider;
    private LineMarginProvider lineMarginProvider;
    private int textFlags;
    private TextEntity[] entities;
    private String suffix;
    private ViewProvider viewProvider;
    private ClickListener clickListener;

    public Builder (String in, int maxWidth, TextStyleProvider provider, @NonNull TextColorSet theme) {
      if (in == null)
        throw new IllegalArgumentException();
      this.in = in;
      this.maxWidth = maxWidth;
      this.provider = provider;
      this.theme = theme;
    }

    public Builder (Tdlib tdlib, CharSequence in, TdlibUi.UrlOpenParameters urlOpenParameters, int maxWidth, TextStyleProvider provider, @NonNull TextColorSet theme) {
      this.in = in.toString();
      this.maxWidth = maxWidth;
      this.provider = provider;
      this.theme = theme;
      TextEntity[] entities = null;
      TdApi.TextEntity[] telegramEntities = TD.toEntities(in, false);
      if (telegramEntities != null && telegramEntities.length > 0) {
        entities = TextEntity.valueOf(tdlib, in.toString(), telegramEntities, urlOpenParameters);
      }
      if (entities == null) {
        entities = Lang.toEntities(in);
      }
      entities(entities);
    }

    public Builder (Tdlib tdlib, TdApi.FormattedText in, TdlibUi.UrlOpenParameters urlOpenParameters, int maxWidth, TextStyleProvider provider, @NonNull TextColorSet theme) {
      this(in.text, maxWidth, provider, theme);
      entities(TextEntity.valueOf(tdlib, this.in, in.entities, urlOpenParameters));
    }

    public Builder styleProvider (TextStyleProvider provider) {
      this.provider = provider;
      return this;
    }

    public Builder onClick (ClickListener listener) {
      this.clickListener = listener;
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

    public Builder entities (TextEntity[] entities) {
      this.entities = entities;
      return this;
    }

    public Builder viewProvider (ViewProvider viewProvider) {
      this.viewProvider = viewProvider;
      return this;
    }

    public Text build () {
      Text text = new Text(in, maxWidth, provider, theme, maxLineCount, lineWidthProvider, lineMarginProvider, textFlags, entities, suffix, clickListener);
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
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return (flags & ENTITY_FLAG_URL) != 0;
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        break;
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

  public int getEmojiOnlyCount () {
    return parts == null || emojiCount == 0 || parts.size() > emojiCount ? -1 : emojiCount;
  }

  @Override
  public int getEmojiCount () {
    return emojiCount;
  }

  @Override
  public boolean incrementEmojiCount () {
    emojiCount++;
    return true;
  }

  public void abort () {
    textFlags |= FLAG_ABORT_PROCESS;
  }

  private boolean isAborted () {
    return (textFlags & FLAG_ABORT_PROCESS) != 0;
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
      Log.v("Next entity not found (entities not sorted?), startIndex:%d start:%d, end:%d, entities:\n%s", startIndex, start, end, b.toString());
    }

    return null;
  }

  // Text

  private void reset () {
    entityIndex = -1;
    entityStart = entityEnd = 0;
    pressHighlight = null;
    emojiCount = 0;
    iconCount = 0;
    maxPartHeight = currentWidth = currentX = currentY = paragraphCount = 0;
    lastPart = null;
    textFlags &= ~(
      FLAG_FULL_RTL |
      FLAG_MAY_APPLY_RTL |
      FLAG_ELLIPSIZED |
      FLAG_NEED_BACKGROUND |
      FLAG_HAS_SPOILERS
    );
    if (lineSizes != null) {
      lineSizes.clear();
    }
    clearSpoilers();
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
    for (int i = 0; i < spoilers.size(); i++) {
      Spoiler spoiler = spoilers.valueAt(i);
      if (spoilerEntity.offset >= spoiler.offset && spoilerEntity.offset < spoiler.offset + spoiler.length) {
        return spoiler;
      }
    }
    return null;
  }

  public void set (int maxWidth, String in) {
    set(maxWidth, in, entities);
  }

  public void changeMaxWidth (int maxWidth) {
    if (this.maxWidth != maxWidth) {
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
    reset();

    if ((textFlags & FLAG_ALL_BOLD) != 0 && Text.needFakeBold(in, 0, in.length())) {
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

    final ArrayList<TextPart> out = new ArrayList<>(10);
    final Emoji.Callback emojiCallback = (input, code, info, position, length) -> {
      if (position > emojiStart) {
        processPartSplitty(in, emojiStart, position, out, emojiEntity, false);
      }
      processEmoji(in, code, info, position, position + length, out, emojiEntity);
      emojiStart = position + length;
      return true;
    };

    try {
      boolean prevIsNewLine = false;
      final int totalLength = in.length();
      for (int index = 0; index < totalLength; ) {
        int indexOfNewLine = in.indexOf('\n', index);
        int length = indexOfNewLine == -1 ? totalLength - index : indexOfNewLine - index;

        processLine(in, index, index + length, out, emojiCallback);
        if (length != 0) {
          prevIsNewLine = false;
        }

        index += length;

        if (indexOfNewLine != -1) {
          if (!prevIsNewLine || !BitwiseUtils.getFlag(textFlags, Text.FLAG_IGNORE_CONTINUOUS_NEWLINES)) {
            if (BitwiseUtils.getFlag(textFlags, Text.FLAG_IGNORE_NEWLINES)) {
              if (currentX > 0 && !out.isEmpty()) {
                currentX += makeSpaceSize(getTextPaint(null));
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
      do {
        TextEntity entity = findEntity(totalLength, totalLength);
        if (entity != null) {
          processTextOrEmoji(in, totalLength, totalLength, out, emojiCallback, entity);
        } else {
          break;
        }
      } while (true);
      currentWidth = Math.max(currentWidth, currentX);
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
      suffixPart.setXY(lastLineSize[0], lastPart.getY());
      suffixPart.setWidth(suffixWidth);
      lastLineSize[0] += suffixWidth;
      currentWidth = Math.max(currentWidth, lastLineSize[0]);
      out.add(suffixPart);
    }

    out.trimToSize();
    if (BitwiseUtils.getFlag(textFlags, FLAG_ANIMATED_EMOJI) && out.size() == 1 && out.get(0).isEmoji()) {
      out.get(0).setAnimateEmoji(true);
    }
    this.parts = out;
    if (this.pressHighlights != null)
      this.pressHighlights.clear();

    if (BuildConfig.DEBUG && maxLineCount == -1) {
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

    final int partsCount = out.size();

    // Now check RTL lines
    int rtlPartsCount = 0;

    int paragraphIndex = 0;
    boolean prevIsRtl = false;
    boolean prevParagraphRtl = false;
    int neutralLineCount = 0, ltrLineCount = 0, rtlLineCount = 0;
    for (int partIndex = 0; partIndex < partsCount; ) {
      int direction = Strings.DIRECTION_NEUTRAL;
      int startIndex = partIndex;

      TextPart part = out.get(partIndex);
      int currentLine = part.getLineIndex();
      int currentParagraphIndex = part.getParagraphIndex();
      if (paragraphIndex != currentParagraphIndex) {
        prevParagraphRtl = prevIsRtl;

        paragraphIndex = currentParagraphIndex;
        neutralLineCount = ltrLineCount = rtlLineCount = 0;
        prevIsRtl = false;
      }

      do {
        if (part.isEssential()) {
          int partDirection = Strings.getTextDirection(part.getLine(), part.getStart(), part.getEnd());
          // part.setPartDirection(partDirection);
          if (partDirection != Strings.DIRECTION_NEUTRAL) {
            if (direction == Strings.DIRECTION_NEUTRAL)
              direction = partDirection;
            if (partDirection == Strings.DIRECTION_RTL && part.getEnd() - part.getStart() > 0) {
              int start = part.getStart();
              int checkIndex = part.getEnd() - 1;
              if (part.getLine().charAt(checkIndex) == '\u2068') {
                int spacebarCount = 0;
                while (--checkIndex >= start) {
                  char c = part.getLine().charAt(checkIndex);
                  if (c == ' ')
                    spacebarCount++;
                  else if (c != '\u2068')
                    break;
                }
                if (spacebarCount > 0)
                  part.setXY(part.getX() - (int) makeSpaceSize(getTextPaint(part.getEntity())), part.getY() * spacebarCount);
              }
            }
          }
        }
        partIndex++;
      } while (partIndex < partsCount && (part = out.get(partIndex)).getLineIndex() == currentLine);

      switch (direction) {
        case Strings.DIRECTION_LTR: {
          if (ltrLineCount == 0 && rtlLineCount == 0 && neutralLineCount > 0 && prevParagraphRtl) {
            int prevPartIndex = startIndex - 1;
            TextPart prevPart;
            while (prevPartIndex >= 0 && (prevPart = out.get(prevPartIndex)).getParagraphIndex() == paragraphIndex) {
              prevPart.setRtlMode(false, false);
              rtlPartsCount--;
              prevPartIndex--;
            }
            prevIsRtl = false;
            prevParagraphRtl = false;
          }
          ltrLineCount++;
          break;
        }
        case Strings.DIRECTION_RTL: {
          if (rtlLineCount == 0 && ltrLineCount == 0 && neutralLineCount > 0) {
            int prevPartIndex = startIndex - 1;
            TextPart prevPart;
            while (prevPartIndex >= 0 && (prevPart = out.get(prevPartIndex)).getParagraphIndex() == paragraphIndex) {
              prevPart.setRtlMode(true, true);
              rtlPartsCount++;
              prevPartIndex--;
            }
            prevIsRtl = true;
          }
          rtlLineCount++;
          break;
        }
        case Strings.DIRECTION_NEUTRAL: {
          neutralLineCount++;
          break;
        }
        default:
          throw new IllegalStateException("direction == " + direction);
      }

      if (direction == Strings.DIRECTION_RTL || (direction == Strings.DIRECTION_NEUTRAL && (prevIsRtl || prevParagraphRtl))) {
        prevIsRtl = true;
        boolean isFakeRtl = direction != Strings.DIRECTION_RTL;
        for (int i = startIndex; i < partIndex; i++) {
          out.get(i).setRtlMode(true, isFakeRtl);
          rtlPartsCount++;
        }
      } else {
        prevIsRtl = false;
      }
    }

    if (rtlPartsCount == partsCount) {
      textFlags |= FLAG_FULL_RTL;
    } else if (ltrLineCount == 0 && neutralLineCount == getLineCount() - rtlLineCount) {
      textFlags |= FLAG_MAY_APPLY_RTL;
    }

    if (BuildConfig.DEBUG) {
      int partCount = parts.size();
      for (int i = 0; i < partCount; i++) {
        getLineWidth(parts.get(i).getLineIndex());
      }
    }
  }

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
      processEntities(in, start, end, out, emojiCallback, false);
      return;
    }
    boolean first = true;
    boolean lastIsSpace = false;
    int count = 0;
    for (int i = start; i < end; ) {
      int codePoint = in.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (codePoint == '\n' && !BitwiseUtils.getFlag(textFlags, FLAG_IGNORE_NEWLINES)) {
        break;
      }
      boolean isSpace = size == 1 && (codePoint == '\n' || Character.getType(codePoint) == Character.SPACE_SEPARATOR);
      if (first) {
        first = false;
        lastIsSpace = isSpace;
        count += size;
      } else if (lastIsSpace == isSpace) {
        count += size;
      } else {
        processEntities(in, start, start + count, out, emojiCallback, false);
        start += count;
        count = size;
        lastIsSpace = isSpace;
      }
      i += size;
    }
    processEntities(in, start, start + count, out, emojiCallback, false);
    if (start + count < end) {
      processEntities(in, start + count, end, out, emojiCallback, false);
    }
  }

  private int processEntities (String in, int start, int end, ArrayList<TextPart> out, Emoji.Callback emojiCallback, boolean isChild) {
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
          newStart = processEntities(in, newStart, end, out, emojiCallback, true);
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

  private void processTextOrEmoji (final String in, final int start, final int end, final ArrayList<TextPart> out, final Emoji.Callback emojiCallback, final @Nullable TextEntity entity) {
    TextPaint paint = getTextPaint(entity);
    Paint.FontMetricsInt fontMetricsInt = Paints.getFontMetricsInt(paint);

    if (end - start == 0) {
      if (entity != null && entity.isIcon()) {
        processIcon(in, start, out, entity);
      }
      return;
    }

    emojiStart = start;
    emojiEntity = entity;
    emojiSize = Math.abs(fontMetricsInt.descent - fontMetricsInt.ascent) + Screen.dp(2f);

    Emoji.instance().replaceEmoji(in, start, end, this, emojiCallback);

    if (emojiStart < end) {
      processPartSplitty(in, emojiStart, end, out, entity, false);
    }
  }

  private void processIcon (String in, int index, ArrayList<TextPart> out, @NonNull TextEntity entity) {
    lastPart = null;

    TextIcon icon = entity.getIcon();

    int iconWidth = Screen.dp(icon.getWidth());
    int iconHeight = Screen.dp(icon.getHeight());

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
    part.setIcon(iconCount, icon);

    out.add(part);

    currentX += iconWidth;
    maxPartHeight = Math.max(iconHeight, maxPartHeight);
    iconCount++;
  }

  private void processEmoji (String in, CharSequence code, EmojiInfo info, int start, int end, ArrayList<TextPart> out, @Nullable TextEntity entity) {
    lastPart = null;

    final int maxWidth = getLineMaxWidth(getLineCount(), currentY);
    if (currentX + emojiSize > maxWidth) {
      newLineOrEllipsis(out, in);
    }

    TextPart part;

    part = new TextPart(this, in, start, end, getLineCount(), paragraphCount);
    part.setXY(currentX, currentY);
    part.setWidth(emojiSize);
    part.setEntity(entity);
    part.setEmoji(info);

    out.add(part);

    currentX += emojiSize;
  }

  // Utils

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

  private static boolean isSplitterCodePoint (int codePoint, boolean allowWhitespace) {
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
      (codePoint >= 0xFF00 && codePoint <= 0xFF00 /*Halfwidth and Fullwidth Forms*/);
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
    float baselineShift = entity != null ? entity.getBaselineShift() : 0f;
    TextPaint paint;
    if (entity != null) {
      paint = entity.getTextPaint(textStyleProvider, forceBold);
    } else if (forceBold) {
      paint = (textFlags & FLAG_FAKE_BOLD) != 0 ? textStyleProvider.getFakeBoldPaint() : textStyleProvider.getBoldPaint();
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

    final Paint paint = getTextPaint(entity);
    final float spaceSize = makeSpaceSize(paint);
    // TODO optimize https://fonts.google.com/specimen/Roboto+Mono
    final boolean isMonospace = false; // (entity != null && entity.isMonospace());
    final boolean isFullyMonospace = isMonospace && isMonospace(in, start, end);
    // final float monospaceSize = spaceSize;

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
      } else if (isChild) {
        fullWidth = childWidth[0];
      } else {
        fullWidth = U.measureText(in, start, end, paint);
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
    final int maxWidth = getLineMaxWidth(getLineCount(), this.currentY);

    if (width > maxWidth) {
      int currentEnd;

      float availWidth = maxWidth - currentX;
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
          charWidth = U.measureText(in, currentEnd, newEnd, paint);
          if (newEnd - currentEnd == 1) {
            lastCodePoint = codePoint;
            lastCodePointWidth = charWidth;
          }

        }
        if (availWidth - charWidth < 0) {
          if (currentX > 0 && currentEnd == start && availWidth + currentX - charWidth >= 0) {
            availWidth = maxWidth;
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

    if (currentX + width > maxWidth || (currentX > 0 && ((entity != null && entity.isFullWidth() && lastPart != null && !lastPart.isSameEntity(entity)) || (lastPart != null && lastPart.getEntity() != null && lastPart.getEntity().isFullWidth() && !lastPart.isSameEntity(entity))) )) {
      boolean movedLastLine = false;

      float firstCodePointWidth;
      if ((textFlags & FLAG_BOUNDS_NOT_STRICT) != 0 && in.codePointCount(start, end) == 2 && currentX + (firstCodePointWidth = U.measureText(in, start, start + 1, paint)) <= maxWidth) {
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
            movedLastLine = moveToNextLine(lastPart);
          }
        }
        if (!movedLastLine) {
          newLineOrEllipsis(out, in, start, end, entity);
        }
      }
    }

    if (lastPart != null && (!lastPart.isSameEntity(entity) || start < lastPart.getEnd() || findNewLines(in, lastPart.getEnd(), start))) {
      lastPart = null;
    }

    if (lastPart == null || lastPart.getEntity() != entity) {
      TextPart part;

      part = new TextPart(this, in, start, end, getLineCount(), paragraphCount);
      part.setXY(currentX, currentY);
      part.setWidth(fullWidth);
      part.setEntity(entity);

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

  private boolean moveToNextLine (TextPart part) {
    if (maxLineCount != -1 && getLineCount() + 1 >= maxLineCount) {
      return false;
    }
    currentX = part.getX();
    int lineHeight = getCurrentLineHeight();
    int prevMaxPartHeight = maxPartHeight;
    addLine(currentX, lineHeight);
    currentWidth = Math.max(currentWidth, currentX);
    currentX = (int) part.getWidth();
    currentY += lineHeight;
    maxPartHeight = 0;
    part.setXY(0, currentY);
    ensureLineCount(lineHeight, prevMaxPartHeight);
    return true;
  }

  private void newLineOrEllipsis (List<TextPart> out, @NonNull String in) {
    newLineOrEllipsis(out, in, 0, 0, null);
  }

  private int getCurrentLineHeight () {
    return Math.max(maxPartHeight, getLineHeight());
  }

  private void newLineOrEllipsis (List<TextPart> out, @NonNull String in, int start, int end, TextEntity entity) {
    int lineHeight = getCurrentLineHeight();
    int prevMaxPartHeight = maxPartHeight;
    addLine(currentX, lineHeight);
    lastPart = null;
    maxPartHeight = 0;
    currentWidth = Math.max(currentWidth, currentX);
    currentX = 0;
    currentY += lineHeight;
    try {
      ensureLineCount(lineHeight, prevMaxPartHeight);
    } catch (LimitReachedException e) {
      textFlags |= FLAG_ELLIPSIZED;
      if (out.isEmpty())
        throw e;
      TextPart lastPart = out.get(out.size() - 1);
      int currentX = getLineWidth(getLineCount() - 1);
      int lineMaxWidth = getLineMaxWidth(getLineCount() - 1, currentY);
      String ellipsis = Strings.ELLIPSIS;
      if (!StringUtils.isEmpty(in) && end > start && !BitwiseUtils.getFlag(this.textFlags, FLAG_ELLIPSIZE_NO_FILL)) {
        int ellipsisMaxWidth = lineMaxWidth - currentX;
        String ellipsized = TextUtils.ellipsize(in.substring(start, end).replace('\n', ' '), getTextPaint(entity), ellipsisMaxWidth, TextUtils.TruncateAt.END).toString();
        if (!StringUtils.isEmpty(ellipsized)) {
          ellipsis = ellipsized;
        }
      }
      final String defaultEllipsis = Strings.ELLIPSIS;
      float ellipsisWidth = U.measureText(ellipsis, getTextPaint(entity));
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
        TextPart ellipsisPart = new TextPart(this, ellipsis, 0, ellipsis.length(), lineIndex, paragraphIndex);
        ellipsisPart.setXY(currentX, currentY);
        ellipsisPart.setWidth(ellipsisWidth);
        ellipsisPart.setEntity(entity);
        out.add(ellipsisPart);
        currentX += ellipsisWidth;
      } else {
        // Hard path: find enough place for ellipsis and place it there
        final int requiredLineIndex = lastPart.getLineIndex();
        final int minEnd = lastPart.getEnd();
        final float defaultEllipsisWidth = U.measureText(defaultEllipsis, getTextPaint(null));

        boolean done = false;
        do {
          currentX = lastPart.getX();
          if (lastPart.isEmoji() || lastPart.isIcon()) {
            // Easy path: just replace first found emoji with ellipsis

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
            out.set(out.size() - 1, ellipsisPart);
            currentX += ellipsisPart.getWidth();

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
              ellipsisWidth = U.measureText(ellipsis, paint);
              if (currentX + ellipsisWidth <= lineMaxWidth) {
                lastPart.setLine(ellipsis, 0, ellipsis.length());
                lastPart.setWidth(ellipsisWidth);
                currentX += ellipsisWidth;

                done = true;
                break;
              }
            }
          }

          out.remove(out.size() - 1);
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

  public int getWidth () {
    return currentWidth;
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

  public int getLastLineWidth () {
    return currentX;
  }

  public boolean getLastLineIsRtl () {
    return (parts != null && !parts.isEmpty() && parts.get(parts.size() - 1).isRtl()) || alignRight();
  }

  public boolean isFullyRtl () {
    return (textFlags & FLAG_FULL_RTL) != 0;
  }

  public int getHeight () {
    return currentY;
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
    return (textFlags & FLAG_ALIGN_RIGHT) != 0 || (Lang.rtl() && (textFlags & FLAG_MAY_APPLY_RTL) != 0);
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

  public void locatePart (Rect outRect, TextPart part, int compareMode) {
    outRect.set(0, part.getY(), getLineWidth(part.getLineIndex()), part.getY() + getLineHeight(part.getLineIndex()));
    if (getEntityCount() > 0) {
      outRect.left = part.getX();
      outRect.right = part.getX() + (int) part.getWidth();
    }
    TextEntity entity = part.getEntity();
    if (entity != null) {
      int i = parts.indexOf(part);
      if (i != -1) {
        int start = i;
        while (start > 0 && parts.get(start - 1).getLineIndex() == part.getLineIndex() && TextEntity.equals(entity, parts.get(start - 1).getEntity(), compareMode, originalText)) {
          start--;
        }
        int end = i;
        while (end + 1 < parts.size() && parts.get(end + 1).getLineIndex() == part.getLineIndex() && TextEntity.equals(entity, parts.get(end + 1).getEntity(), compareMode, originalText)) {
          end++;
        }
        int bound = getBackgroundPadding(defaultTextColorSet, entity, compareMode != TextEntity.COMPARE_MODE_NORMAL, false);
        outRect.top -= bound;
        outRect.bottom += bound;
        outRect.left = parts.get(start).getX();
        outRect.right = parts.get(end).getX() + (int) parts.get(end).getWidth();
      }
    }
    outRect.offset(lastStartX, lastStartY);
  }

  private Paint.FontMetricsInt getFontMetrics (float textSizePx) {
    if (mTmpTextSizePx == -1 || mTmpTextSizePx != textSizePx) {
      TextPaint paint = textStyleProvider.getTextPaint();
      paint.getFontMetricsInt(fm);
      mTmpTextSizePx = textSizePx;
    }
    return fm;
  }

  private Paint.FontMetricsInt getFontMetrics () {
    return getFontMetrics(textStyleProvider.getTextSize());
  }

  int getAscent () {
    return -getFontMetrics().ascent;
  }

  public int getLineHeight (boolean needPadding) {
    Paint.FontMetricsInt fm = getFontMetrics();
    return (Math.abs(fm.descent - fm.ascent) + fm.leading) + (needPadding ? getLineSpacing() : 0);
  }

  public int getLineSpacing () {
    return BitwiseUtils.getFlag(textFlags, FLAG_NO_SPACING) ? 0 : textStyleProvider.convertUnit((textFlags & FLAG_ARTICLE) != 0 ? 3f : 2f);
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

  private int drawPartCentered (int partIndex, Canvas c, int x, int y, int maxWidth, float alpha, @Nullable TextColorSet defaultTheme, @Nullable ComplexReceiver receiver, int iconKeyOffset) {
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
        part.draw(partIndex, c, cx - width / 2, cx + width / 2, 0, y, alpha, defaultTheme, receiver, iconKeyOffset);
      }
    }
    return count;
  }

  public int requestIcons (ComplexReceiver iconReceiver, int iconKeyOffset) {
    boolean clear = iconKeyOffset == -1;
    if (iconCount > 0) {
      if (clear) {
        iconKeyOffset = 0;
      }
      int iconIndex = 0;
      for (TextPart part : parts) {
        if (part.isIcon()) {
          part.requestIcon(iconReceiver, iconKeyOffset);
          iconIndex++;
        }
      }
      if (clear) {
        iconReceiver.clearReceiversWithHigherKey(iconIndex);
      }
      return iconIndex;
    }
    if (clear) {
      iconReceiver.clear();
    }
    return 0;
  }

  private int drawPart (final int partIndex, Canvas c, int startX, int endX, int endXBottomPadding, int y, float alpha, @Nullable TextColorSet defaultTheme, @Nullable ComplexReceiver receiver, int iconKeyOffset) {
    TextPart part = parts.get(partIndex);

    int count = 1;
    int partCount = parts.size();
    TextPart lastPart = part;
    int i = partIndex;
    while (++i < partCount) {
      TextPart curPart = parts.get(i);
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
        part.drawMerged(partIndex, c, lastPart.getEnd(), startX, endX, endXBottomPadding, y, alpha, defaultTheme);
      } else {
        part.draw(partIndex, c, startX, endX, endXBottomPadding, y, alpha, defaultTheme, receiver, iconKeyOffset);
      }
    }
    return count;
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
    draw(c, startX, startX/* + getWidth()*/, 0, startY, defaultTheme, alpha);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha) {
    draw(c, startX, endX, endXBottomPadding, startY, defaultTheme, alpha, null);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha, @Nullable ComplexReceiver receiver) {
    draw(c, startX, endX, endXBottomPadding, startY, defaultTheme, alpha, receiver, -1);
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha, @Nullable ComplexReceiver receiver, int iconKeyOffset) {
    if (parts == null || alpha == 0f)
      return;

    final boolean needRestore = BitwiseUtils.getFlag(textFlags, FLAG_NEED_CLIP_TEXT_AREA);
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
      c.clipRect(startX - bound, startY - bound, startX + getWidth() + bound + lineMargin, startY + getHeight() + bound);
    } else {
      saveCount = -1;
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

        Background background = null;
        long lastBackgroundId = 0;

        int partsCount = parts.size();
        for (int i = 0; i < partsCount;) {
          TextPart part = parts.get(i);
          TextEntity entity = part.getEntity();
          if (entity != null) {
            TextColorSet theme = pickTheme(defaultTheme, entity);
            long backgroundId = theme.backgroundId(false);
            if (backgroundId != 0) {
              if (background == null || lastBackgroundId != backgroundId) {
                background = backgrounds.get(lastBackgroundId = backgroundId);
                if (background == null) {
                  background = new Background(new Path(), theme);
                  backgrounds.put(backgroundId, background);
                }
              }
              addHighlightPath(background.path, part, startX, endX, endXBottomPadding, center, false);
              while (++i < partsCount) {
                part = parts.get(i);
                TextEntity nextEntity = part.getEntity();
                if (nextEntity == null) {
                  break;
                }
                TextColorSet nextTheme = pickTheme(defaultTheme, nextEntity);
                long nextBackgroundId = nextTheme.backgroundId(false);
                if (nextBackgroundId != backgroundId) {
                  i--;
                  break;
                }
                addHighlightPath(background.path, part, startX, endX, endXBottomPadding, center, false);
              }
            }
          }
          i++;
        }
      }
      if (backgrounds.size() > 0) {
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
    }
    drawPressHighlight(c, startX, endX, endXBottomPadding, startY, center, pressHighlight, alpha, defaultTheme);

    if ((textFlags & FLAG_HAS_SPOILERS) != 0) {
      if (spoilers == null) {
        spoilers = new LongSparseArray<>();
        int partsCount = parts.size();
        for (int i = 0; i < partsCount; i++) {
          TextPart part = parts.get(i);
          TextEntity spoilerEntity = part.getSpoilerEntity();
          if (spoilerEntity != null) {
            Spoiler spoiler = new Spoiler(i);
            spoiler.addPart(part, startX, endX, endXBottomPadding, center);
            while (++i < partsCount) {
              part = parts.get(i);
              if (!part.isSameSpoiler(spoilerEntity) && !(part.isWhitespace() && i + 1 < partsCount && parts.get(i + 1).isSameSpoiler(spoilerEntity))) {
                i--;
                break;
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
      if (spoilers.size() > 0) {
        boolean saved = false;
        for (int i = 0; i < spoilers.size(); i++) {
          Spoiler spoiler = spoilers.valueAt(i);
          if (spoiler.willDraw()) {
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
    }

    if (center) {
      int partCount = parts.size();
      for (int i = 0; i < partCount; ) {
        i += drawPartCentered(i, c, startX, startY, startX == endX ? maxWidth : endX - startX, alpha, defaultTheme, receiver, iconKeyOffset);
      }
    } else {
      int partCount = parts.size();
      for (int i = 0; i < partCount; ) {
        i += drawPart(i, c, startX, endX, endXBottomPadding, startY, alpha, defaultTheme, receiver, iconKeyOffset);
      }
    }
    if (needRestore) {
      Views.restore(c, saveCount);
    }
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
    default TdApi.WebPage findWebPage (String link) { return null; }
    default boolean onCommandClick (View view, Text text, TextPart part, String command, boolean isLongPress) { return false; }
    default boolean onUsernameClick (String username) { return false; }
    default boolean onUserClick (long userId) { return false; }
    default boolean onEmailClick (String email) { return false; }
    default boolean onPhoneNumberClick (String phoneNumber) { return false; }
    default boolean onBankCardNumberClick (String cardNumber) { return false; }
    default boolean onHashtagClick (String hashtag) { return false; }
    default boolean onUrlClick (View view, String link, boolean promptUser, @NonNull TdlibUi.UrlOpenParameters openParameters) { return false; }
    default boolean onAnchorClick (View view, String anchor) { return false; }
    default boolean onReferenceClick (View view, String name, String referenceAnchorName, @NonNull TdlibUi.UrlOpenParameters openParameters) { return false; }
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
    return partHeight != -1 ? (int) partHeight : getLineHeight();
  }

  private int getPartVerticalOffset (TextPart part) {
    return iconCount > 0 ? (getLineHeight(part.getLineIndex()) - getPartHeight(part)) / 2 : 0;
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
      int searchX;

      if (useCenter) {
        int width = getLineWidth(part.getLineIndex());
        int cx = startX + maxWidth / 2;
        int sx = cx - width / 2, ex = cx + width / 2;
        searchX = part.makeX(sx, ex, 0);
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
  private PressHighlight pressHighlight;
  private int touchX, touchY;

  public void setViewProvider (ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
  }

  public ViewProvider getViewProvider () {
    return viewProvider;
  }

  public void cancelTouch () {
    if (pressHighlight != null) {
      if (pressHighlight.spoiler != null) {
        pressHighlight.spoiler.setPressed(false, true);
      }
      pressHighlight = null;
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
    if (parts == null) {
      return false;
    }
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        touchX = (int) e.getX();
        touchY = (int) e.getY();

        final boolean onlyClickable = clickListener == null;
        final int causeIndex = findTextPart(touchX, touchY, lastStartX, lastEndX, lastEndXBottomPadding, lastStartY, onlyClickable);
        if (causeIndex == -1) {
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
          if ((onlyClickable && !foundPart.isClickable()) || (!onlyClickable && !foundPart.isClickable() && !BitwiseUtils.getFlag(textFlags, FLAG_ALL_CLICKABLE))) {
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
        if (pressHighlight != null) {
          cancelTouch();
          return true;
        } else {
          return false;
        }
      }
      case MotionEvent.ACTION_MOVE: {
        if (pressHighlight != null) {
          if (Math.max(Math.abs(touchX - e.getX()), Math.abs(touchY - e.getY())) > Screen.getTouchSlop()) {
            cancelTouch();
          }
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP: {
        clearTouch();
        if (pressHighlight != null) {
          TextPart part = parts.get(pressHighlight.causePartIndex);
          if (pressHighlight.isSpoilerReveal()) {
            revealSpoiler(part);
            ViewUtils.onClick(view);
            return true;
          }
          TextEntity entity = part.getClickableEntity();
          boolean done = false;
          if (clickListener != null) {
            done = clickListener.onClick(view, this, part, entity != null ? entity.openParameters(view, this, part) : new TdlibUi.UrlOpenParameters().tooltip(part.newTooltipBuilder(view)));
          } else if (entity != null) {
            entity.performClick(view, this, part, callback);
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
    return pressHighlight != null;
  }

  private static int getEntityTouchPadding () {
    return Screen.dp(4f);
  }

  // Sharing

  public boolean performLongPress (final View view) {
    if (pressHighlight == null || pressHighlight.isSpoilerReveal()) {
      return false;
    }

    int causeIndex = this.pressHighlight.causePartIndex;
    ClickCallback callback = longPressTargetCallback;
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
    return (BitwiseUtils.getFlag(textFlags, FLAG_ALL_CLICKABLE) || (entity != null && entity.isClickable())) && !BitwiseUtils.getFlag(textFlags, FLAG_NO_CLICKABLE);
  }

  @ColorInt
  public int getTextColor (@Nullable TextColorSet defaultTheme, @Nullable TextEntity entity, boolean allClickable, boolean isPressed) {
    TextColorSet theme = pickTheme(defaultTheme, entity);
    boolean isClickable = isClickable(entity);
    return isClickable ? theme.clickableTextColor(isPressed) : theme.defaultTextColor();
  }

  @ColorInt
  public int getTextColor () {
    return getTextColor(null, null, BitwiseUtils.getFlag(textFlags, FLAG_ALL_CLICKABLE), false);
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
}
