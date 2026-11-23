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
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.util.text.quotes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.StickyElements;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.KeptOnScreen;
import org.thunderdog.challegram.util.text.RippleDrawable;
import org.thunderdog.challegram.util.text.SyntaxLines;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextPart;

import java.util.Locale;

import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

public class QuoteBackground implements StickyElements.Element {
  public static final int QUOTE_LEFT_PADDING = 11;
  public static final int QUOTE_RIGHT_PADDING = 27;
  public static final int QUOTE_VERTICAL_PADDING = 2;
  public static final int QUOTE_VERTICAL_MARGIN = 8;
  // The quote mark: its left edge from the quote's right, and its size
  public static final int QUOTE_ICON_RIGHT = 19;
  public static final int QUOTE_ICON_SIZE = 16;
  public static final int CODE_RIGHT_PADDING = 11;
  private static final int CODE_HEADER_HEIGHT = 30;
  private static final float CODE_ICON_BUTTON_RADIUS = 14f;
  private static final float CODE_ICON_BUTTON_END = 20f;
  private static final float CODE_ICON_BUTTON_STEP = 32f;
  private static final float CODE_HEADER_TEXT_SIZE = 13f;
  // A collapsed quote fades out in its last 24 dp, as in Telegram for Android
  private static final float COLLAPSED_FADE_HEIGHT = 24f;

  private static Drawable quoteDrawable;
  private static Paint fadePaint;
  private static final Matrix fadeMatrix = new Matrix();

  private int topAddition;
  private int bottomAddition;

  public final Text parent;
  public final TextEntity entity;
  public int partStart;
  public int partEnd;

  public final RectF bounds = new RectF();
  private RippleDrawable ripple;

  public final boolean isCode;
  private final @Nullable String title;
  private final int headerHeight;
  public @Nullable SyntaxLines lines;
  // Right edge of the background in coordinates of the text
  private int codeWidth;
  private int scrollX;
  private int maxScrollX;
  private @Nullable CodeBlockScroller scroller;
  private @Nullable RippleDrawable copyRipple;
  private @Nullable RippleDrawable switchRipple;
  private boolean isWrapped;
  private boolean canCollapse, isExpanded;
  // The first character of lines left out below the shown ones when collapsed, and the width of
  // the widest line left out
  private int hiddenStart, hiddenWidth;
  // Found text in lines that a collapse hides moves the shown lines to it: they start at the
  // character shownStart, moved up by shiftHeight, with a fade at the top too
  private int shownStart, shiftHeight;
  private boolean hidesBelow;
  private @Nullable RippleDrawable pressedButton;
  // Tokens that come after the block was drawn animate its colors
  private boolean wasDrawn;

  public QuoteBackground (Text text, TextEntity entity) {
    this.parent = text;
    this.entity = entity;

    this.ripple = new RippleDrawable();

    this.isCode = entity.isCodeBlock();
    this.title = isCode ? getTitle(entity) : null;
    this.headerHeight = Screen.dp(getHeaderHeight(entity));
    this.copyRipple = isCode ? new RippleDrawable() : null;
    this.switchRipple = isCode && text.canSwitchCodeBlock(entity) ? new RippleDrawable() : null;
  }

  public static int getRightPadding (@Nullable TextEntity entity) {
    return entity != null && entity.isCodeBlock() ? CODE_RIGHT_PADDING : QUOTE_RIGHT_PADDING;
  }

  public static int getHeaderHeight (TextEntity entity) {
    return entity.isCodeBlock() ? CODE_HEADER_HEIGHT : 0;
  }

  // Titles of languages, which would be made again on each layout
  private static final LruCache<String, String> languageTitles = new LruCache<>(32);

  private static String getTitle (TextEntity entity) {
    final String language = entity.getCodeLanguage();
    if (StringUtils.isEmpty(language)) {
      return getDefaultTitle();
    }
    String title = languageTitles.get(language);
    if (title == null) {
      title = N.getLanguageName(language);
      if (title == null) {
        // An identifier, so the locale of the app doesn't change its letters
        title = StringUtils.ucfirst(language, Locale.ROOT);
      }
      languageTitles.put(language, title);
    }
    return title;
  }

  private static TdApi.LanguagePackInfo defaultTitleLanguagePack;
  private static String defaultTitle;

  private static synchronized String getDefaultTitle () {
    // Looking up a string takes longer than the rest of the layout of a short block
    final TdApi.LanguagePackInfo languagePack = Settings.instance().getLanguagePackInfo();
    if (defaultTitle == null || defaultTitleLanguagePack != languagePack) {
      defaultTitle = Lang.getString(R.string.CodeBlockTitle);
      defaultTitleLanguagePack = languagePack;
    }
    return defaultTitle;
  }

  private static TextPaint getHeaderPaint (int color) {
    return Paints.getMediumTextPaint(CODE_HEADER_TEXT_SIZE, color, false);
  }

  // All are dropped when the paint changes, e.g. with the font of the app
  private static final LruCache<String, Float> titleWidths = new LruCache<>(32);
  private static float titleWidthsTextSize;
  private static Typeface titleWidthsTypeface;

  private static synchronized float getTitleWidth (String title) {
    final TextPaint paint = getHeaderPaint(0);
    if (paint.getTextSize() != titleWidthsTextSize || paint.getTypeface() != titleWidthsTypeface) {
      titleWidths.evictAll();
      titleWidthsTextSize = paint.getTextSize();
      titleWidthsTypeface = paint.getTypeface();
    }
    Float width = titleWidths.get(title);
    if (width == null) {
      width = paint.measureText(title);
      titleWidths.put(title, width);
    }
    return width;
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    ripple.setViewProvider(viewProvider);
    if (copyRipple != null) {
      copyRipple.setViewProvider(viewProvider);
    }
    if (switchRipple != null) {
      switchRipple.setViewProvider(viewProvider);
    }
  }

  public void performLongPress (View view) {
    ripple.performLongPress(view);
  }

  public void performCancelTouch () {
    ripple.performCancelTouch();
    if (copyRipple != null) {
      copyRipple.performCancelTouch();
    }
    if (switchRipple != null) {
      switchRipple.performCancelTouch();
    }
    pressedButton = null;
  }

  public boolean contains (int x, int y) {
    return ripple.bounds.contains(x, y);
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return ripple.onTouchEvent(view, e, (int) (e.getX() - ripple.bounds.left), (int) (e.getY() - ripple.bounds.top));
  }



  public void calcHeightAddition () {
    final int lineHeight = parent.getLineHeight();
    final int countBefore = TextPart.getAdditionalLinesBefore(parent.getTextPart(partStart));
    this.topAddition = (isCollapsed() && shiftHeight > 0 ? 0 : countBefore) * lineHeight;

    final int count = TextPart.getAdditionalLinesAfter(parent.getTextPart(partEnd - 1));
    this.bottomAddition = (count == 1 || isCollapsed() ? 0 : count) * lineHeight;
  }

  public int findCollapsedEnd () {
    // The first part after the lines that the collapsed quote shows, or partEnd when it's whole
    final int shownLineCount = Config.EXPANDABLE_QUOTE_COLLAPSED_LINE_COUNT;
    if (getLineCount() < shownLineCount + 2) {
      return partEnd;
    }
    return findLinePart(shownLineCount);
  }

  public int findShownFirstLine (int foundLine) {
    // Found text on a line that the collapsed quote hides or fades is moved to the line above its
    // bottom fade, or to its last line
    final int shownLineCount = Config.EXPANDABLE_QUOTE_COLLAPSED_LINE_COUNT;
    if (foundLine < shownLineCount - 1) {
      return 0;
    }
    return Math.min(foundLine - shownLineCount + 2, getLineCount() - shownLineCount);
  }

  private int getLineCount () {
    final int firstLine = parent.getTextPart(partStart).getLineIndex();
    return parent.getTextPart(partEnd - 1).getLineIndex() - firstLine + 1;
  }

  public int findLinePart (int line) {
    // The first part of the line, counted from the first line of the quote, or partEnd
    final int firstLine = parent.getTextPart(partStart).getLineIndex();
    for (int i = partStart; i < partEnd; i++) {
      if (parent.getTextPart(i).getLineIndex() >= firstLine + line) {
        return i;
      }
    }
    return partEnd;
  }

  public int getHiddenHeight (int shownEnd) {
    return getTextBottom(partEnd) + bottomAddition - getTextBottom(shownEnd);
  }

  public int getHiddenAboveHeight (int shownStart) {
    return parent.getTextPart(shownStart).getY() - parent.getTextPart(partStart).getY() +
      topAddition;
  }

  private int getTextBottom (int end) {
    int bottom = Integer.MIN_VALUE;
    for (int i = partStart; i < end; i++) {
      final TextPart part = parent.getTextPart(i);
      bottom = Math.max(bottom, part.getY() + getPartHeight(part));
    }
    return bottom;
  }

  private int getPartHeight (TextPart part) {
    final TextPaint paint = parent.getTextPaint(part.getEntity());
    final Paint.FontMetricsInt fm = parent.getFontMetrics(paint.getTextSize());
    return part.getHeight() == -1 ? fm.descent - fm.ascent : part.getHeight();
  }

  public void setCollapsible (
    boolean isExpanded,
    int shownStart,
    int shiftHeight,
    boolean hidesBelow,
    int hiddenStart,
    int hiddenWidth
  ) {
    this.canCollapse = true;
    this.isExpanded = isExpanded;
    this.shownStart = shownStart;
    this.shiftHeight = shiftHeight;
    this.hidesBelow = hidesBelow;
    this.hiddenStart = hiddenStart;
    this.hiddenWidth = hiddenWidth;
  }

  public boolean canCollapse () {
    return canCollapse;
  }

  public boolean isCollapsed () {
    return canCollapse && !isExpanded;
  }

  public int getHiddenStart () {
    return hiddenStart;
  }

  public int getShownStart () {
    return shownStart;
  }

  public boolean hasTopFade () {
    return shiftHeight > 0;
  }

  public boolean hasBottomFade () {
    return hidesBelow;
  }

  public int getContentShift () {
    return isCollapsed() ? shiftHeight : 0;
  }

  public int getChangeShift () {
    // How much more the lines are moved up than where the change starts, for those that fade
    return changeStartContentShift == -1 ? 0 : getContentShift() - changeStartContentShift;
  }

  public boolean isSwitchedOn () {
    return isCode ? isWrapped : isExpanded;
  }

  public boolean clipsContent () {
    return isCode || canCollapse;
  }

  public void calcCodeWidth (boolean hasScrollingLines) {
    // Parts are moved to the start by the right padding, which widths of lines include
    final int rightPadding = Screen.dp(CODE_RIGHT_PADDING);
    int contentWidth = 0, visibleWidth = 0;
    for (int a = partStart; a < partEnd; a++) {
      final TextPart part = parent.getTextPart(a);
      contentWidth = Math.max(contentWidth, parent.getLineWidth(part.getLineIndex()));
      if (!isSpaces(part)) {
        visibleWidth = Math.max(visibleWidth, (int) (part.getX() + part.getWidth()) + rightPadding);
      }
    }
    codeWidth = Math.min(Math.max(contentWidth, getHeaderWidth()), parent.getMaxWidth());
    // Wrapped lines don't scroll, and others don't scroll to spaces at their ends
    maxScrollX = hasScrollingLines ? Math.max(0, visibleWidth - codeWidth) : 0;
    isWrapped = !hasScrollingLines;
  }

  private int getHeaderWidth () {
    final int icons = Screen.dp(CODE_ICON_BUTTON_RADIUS) + Screen.dp(CODE_ICON_BUTTON_END) +
      (switchRipple != null ? Screen.dp(CODE_ICON_BUTTON_STEP) : 0);
    return (int) Math.ceil(Screen.dp(QUOTE_LEFT_PADDING) + getTitleWidth(title)) + icons;
  }

  private static boolean isSpaces (TextPart part) {
    final String line = part.getLine();
    for (int i = part.getStart(); i < part.getEnd(); i++) {
      if (line.charAt(i) != ' ') {
        return false;
      }
    }
    return true;
  }

  public void restoreState (QuoteBackground oldBlock) {
    // Ripples, e.g. of the tap that switched the block, go on in the new layout, while a press
    // that is still caught ends with the old one
    scrollX = Math.min(oldBlock.scrollX, maxScrollX);
    scroller = oldBlock.scroller;
    ripple = oldBlock.ripple;
    copyRipple = oldBlock.copyRipple;
    if (switchRipple != null && oldBlock.switchRipple != null) {
      switchRipple = oldBlock.switchRipple;
    }
    performCancelTouch();
  }

  public boolean isScrollable () {
    return maxScrollX > 0;
  }

  public boolean isScrolledToStart () {
    return scrollX <= 0;
  }

  public boolean catchScroll (float y) {
    return scroller != null && scroller.catchScroll(getDisplacement(y));
  }

  public void dragBy (Context context, int deltaX, float y) {
    if (scroller == null) {
      scroller = new CodeBlockScroller(context);
    }
    scrollX = scroller.drag(scrollX, maxScrollX, deltaX, getDisplacement(y), (int) bounds.width());
  }

  public void endDrag (int velocityX) {
    if (scroller != null) {
      scroller.release(scrollX, maxScrollX, velocityX, (int) bounds.width());
    }
  }

  private float getDisplacement (float y) {
    return (y - bounds.top) / bounds.height();
  }

  public void pressRipple (int x, int y) {
    ripple.press(x - ripple.bounds.left, y - ripple.bounds.top);
  }

  public boolean pressButtonAt (int x, int y) {
    if (copyRipple != null && copyRipple.bounds.contains(x, y)) {
      pressedButton = copyRipple;
    } else if (switchRipple != null && switchRipple.bounds.contains(x, y)) {
      pressedButton = switchRipple;
    } else {
      return false;
    }
    pressedButton.press(x - pressedButton.bounds.left, y - pressedButton.bounds.top);
    return true;
  }

  public boolean isCopyButtonPressed () {
    return pressedButton != null && pressedButton == copyRipple;
  }

  public void getCopyButtonBounds (Rect outRect) {
    // In the view, where the button was drawn last
    if (copyRipple != null) {
      outRect.set(copyRipple.bounds);
    }
  }

  public boolean hasSwitch () {
    return switchRipple != null || canCollapse;
  }

  public boolean isSwitchButtonPressed () {
    return pressedButton != null && pressedButton == switchRipple;
  }

  private int contentSaveCount;

  public void clipContent (Canvas c) {
    // Lines are drawn within the background, after the bar at the start of the block
    c.clipRect(barRect.right, bounds.top, bounds.right, bounds.bottom);
    ViewSupport.clipPath(c, ripple.getPath());
  }

  public int getScrollX () {
    return isScrollable() ? scrollX : 0;
  }

  private float getFadeTop () {
    return bounds.top + Screen.dp(QUOTE_VERTICAL_PADDING);
  }

  private float getFadeBottom () {
    return bounds.bottom - Screen.dp(QUOTE_VERTICAL_PADDING);
  }

  public int beginFade (Canvas c) {
    // Lines of a collapsed quote fade out at its bottom, and at its top when they're moved to
    // found text, in a layer of their own, and nothing of them, e.g. a spoiler or the highlight of
    // a press, draws beyond the fades
    if (!canCollapse || parent.getSwitchFactor(this) == 1f) {
      return -1;
    }
    final float top = hasTopFade() ? getFadeTop() : bounds.top;
    final float bottom = hidesBelow ? getFadeBottom() : bounds.bottom;
    final int saveCount = Views.saveLayerAlpha(
      c,
      bounds.left,
      top,
      bounds.right,
      bottom,
      0xFF,
      Canvas.ALL_SAVE_FLAG
    );
    c.clipRect(bounds.left, top, bounds.right, bottom);
    return saveCount;
  }

  public void finishFade (Canvas c, int saveCount) {
    if (saveCount == -1) {
      return;
    }
    if (fadePaint == null) {
      fadePaint = new Paint();
      fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
      fadePaint.setShader(
        new LinearGradient(0, 0, 0, 1, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
      );
    }
    final float height = Screen.dp(COLLAPSED_FADE_HEIGHT);
    fadePaint.setAlpha(Math.round(0xFF * (1f - parent.getSwitchFactor(this))));
    if (hidesBelow) {
      drawFade(c, getFadeBottom() - height, height);
    }
    if (hasTopFade()) {
      drawFade(c, getFadeTop() + height, -height);
    }
    Views.restore(c, saveCount);
  }

  private void drawFade (Canvas c, float start, float height) {
    // Clear at start, all gone at start + height, upwards when height is negative
    fadeMatrix.setScale(1f, height);
    fadeMatrix.postTranslate(0, start);
    fadePaint.getShader().setLocalMatrix(fadeMatrix);
    final float end = start + height;
    c.drawRect(bounds.left, Math.min(start, end), bounds.right, Math.max(start, end), fadePaint);
  }

  public Canvas beginContent (Canvas c) {
    // Stretch of overscroll applies only to the lines
    Canvas content = c;
    if (scroller != null) {
      scrollX = scroller.computeScroll(scrollX, maxScrollX, (int) bounds.width());
      content = scroller.beginStretch(c, bounds);
    }
    contentSaveCount = Views.save(content);
    clipContent(content);
    content.translate(-scrollX, 0);
    return content;
  }

  public boolean finishContent (Canvas c, Canvas content) {
    Views.restore(content, contentSaveCount);
    if (scroller == null) {
      return false;
    }
    // Stretch applies when the lines are drawn from their render node, after the clip of their
    // recording, so they are clipped here too
    final int saveCount = Views.save(c);
    clipContent(c);
    final boolean needInvalidate = scroller.drawEdges(c, content, bounds) | scroller.isFlinging();
    Views.restore(c, saveCount);
    return needInvalidate;
  }

  // While the text changes, edges move by their differences to the layout, which the top and the
  // bottom keep at the end when they're off the screen
  private int topOffset, rightOffset, bottomOffset;
  private int topDiff, rightDiff, bottomDiff;
  private int topEndDiff, bottomEndDiff;
  private float layoutTop, layoutBottom;
  // Where the icon of the switch goes on from, e.g. in the middle of a switch, or -1
  private float changeStartSwitchFactor = -1f;
  private int changeStartContentShift = -1;

  public void getChangeStart (
    RectF out,
    int startX,
    int endX,
    int endXBottomPadding,
    boolean isLaidOut
  ) {
    // Edges move by the widths of the code, while the bubble moves the end of a block on its own
    out.set(getBounds(startX, endX, endXBottomPadding, 0, Text.NO_CODE_BLOCK_END));
    if (isLaidOut) {
      out.top -= topOffset;
      out.right -= rightOffset;
      out.bottom -= bottomOffset;
    }
  }

  public void startChange (
    RectF from,
    float fromSwitchFactor,
    int fromContentShift,
    int startX,
    int endX,
    int endXBottomPadding
  ) {
    // Block of the layout just made, so its offsets are 0
    final RectF laidOut = getBounds(startX, endX, endXBottomPadding, 0, Text.NO_CODE_BLOCK_END);
    topDiff = Math.round(from.top - laidOut.top);
    rightDiff = Math.round(from.right - laidOut.right);
    bottomDiff = Math.round(from.bottom - laidOut.bottom);
    topEndDiff = bottomEndDiff = 0;
    layoutTop = laidOut.top;
    layoutBottom = laidOut.bottom;
    changeStartSwitchFactor = fromSwitchFactor;
    changeStartContentShift = fromContentShift;
  }

  public void addChangeOnScreen (KeptOnScreen kept, RectF drawn) {
    kept.add(drawn.top, layoutTop);
    kept.add(drawn.bottom, layoutBottom);
  }

  public void keepChangeOnScreen (KeptOnScreen kept, RectF drawn) {
    final float top = kept.to(drawn.top, layoutTop);
    final float bottom = kept.to(drawn.bottom, layoutBottom);
    topDiff = Math.round(kept.from(drawn.top, layoutTop, top) - layoutTop);
    topEndDiff = Math.round(top - layoutTop);
    bottomDiff = Math.round(kept.from(drawn.bottom, layoutBottom, bottom) - layoutBottom);
    bottomEndDiff = Math.round(bottom - layoutBottom);
  }

  public float getChangeStartSwitchFactor () {
    return changeStartSwitchFactor;
  }

  public void applyChange (float factor) {
    topOffset = Math.round(topDiff * (1f - factor) + topEndDiff * factor);
    rightOffset = Math.round(rightDiff * (1f - factor));
    bottomOffset = Math.round(bottomDiff * (1f - factor) + bottomEndDiff * factor);
    hasGeometry = false;
  }

  public void finishChange () {
    topDiff = rightDiff = bottomDiff = topEndDiff = bottomEndDiff = 0;
    topOffset = rightOffset = bottomOffset = 0;
    changeStartSwitchFactor = -1f;
    changeStartContentShift = -1;
    hasGeometry = false;
  }

  public int getCodeWidth () {
    return codeWidth + rightOffset;
  }

  public boolean isWrapped () {
    return isWrapped;
  }

  public boolean wasDrawn () {
    return wasDrawn;
  }

  private boolean hasGeometry;
  private int geometryStartX, geometryEndX, geometryEndXBottomPadding, geometryStartY;
  private int geometryCodeBlockEnd;
  private final RectF barRect = new RectF();
  private float barRadius, iconX, iconY, collapsedHeight;
  private float textLeft, headerBottom, headerBaseline;
  private float copyIconX, copyIconY, switchIconX, switchIconY, titleRight;
  private boolean needClipTitle;

  private void updateGeometry (
    int startX,
    int endX,
    int endXBottomPadding,
    int startY,
    int codeBlockEnd
  ) {
    // At the left of left-aligned text, also beside right-to-left lines
    final boolean isStartAligned = !isCode && !parent.alignRight();
    int left = Integer.MAX_VALUE;
    for (int a = partStart; a < partEnd; a++) {
      final TextPart part = parent.getTextPart(a);
      final int height = getPartHeight(part);

      final int x = part.makeX(startX, endX, endXBottomPadding);
      final int y = part.getY() + startY;

      if (a == partStart) {
        bounds.set(x, y, x + (int) part.getWidth(), y + height);
      } else {
        bounds.union(x, y, x + (int) part.getWidth(), y + height);
      }
      left = Math.min(left, isStartAligned ? part.makeX(startX, startX, 0) : x);
    }

    bounds.left = left - Screen.dp(QUOTE_LEFT_PADDING);
    bounds.top -= Screen.dp(QUOTE_VERTICAL_PADDING) + topAddition + headerHeight - topOffset;
    bounds.bottom += Screen.dp(QUOTE_VERTICAL_PADDING) + bottomAddition + bottomOffset;
    if (isCode) {
      bounds.right = bounds.left + getCodeWidth();
      if (codeBlockEnd != Text.NO_CODE_BLOCK_END) {
        // Not past the text, whose lines may end with a space past its maximum width, nor past
        // the width that lines of a block that scrolls are scrolled in
        final int textWidth = isScrollable() ?
          parent.getMaxWidth() :
          Math.max(parent.getMaxWidth(), parent.getWidth());
        final int end = Math.min(codeBlockEnd, startX + textWidth);
        bounds.left = Math.min(bounds.left, startX);
        bounds.right = Math.max(bounds.right, end);
      }
    } else {
      bounds.right += Screen.dp(QUOTE_RIGHT_PADDING);
      // A collapsed quote is as wide as when expanded
      if (isCollapsed() && bounds.width() < hiddenWidth) {
        if (!isStartAligned && parent.getTextPart(partStart).isRtl()) {
          bounds.left = bounds.right - hiddenWidth;
        } else {
          bounds.right = bounds.left + hiddenWidth;
        }
      }
      bounds.right += rightOffset;
    }

    ripple.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
    ripple.setRadius(Screen.dp(1.5f), Screen.dp(8f), Screen.dp(8f), Screen.dp(1.5f));
    barRect.set(bounds.left, bounds.top, bounds.left + Screen.dp(3), bounds.bottom);
    barRadius = Screen.dp(1.5f);

    if (!isCode) {
      iconX = bounds.right - Screen.dp(QUOTE_ICON_RIGHT);
      iconY = bounds.top + Screen.dp(3.5f);
      // Of the layout, without the offsets of a change
      final float laidHeight = bounds.height() + topOffset - bottomOffset;
      collapsedHeight = canCollapse && !isCollapsed() ?
        laidHeight - getHiddenHeight(findCollapsedEnd()) : laidHeight;
      return;
    }
    textLeft = bounds.left + Screen.dp(QUOTE_LEFT_PADDING);
    final Paint.FontMetricsInt fm = Paints.getFontMetricsInt(getHeaderPaint(0));
    headerBottom = bounds.top + headerHeight;
    headerBaseline = bounds.top + (headerHeight - fm.descent - fm.ascent) / 2f;
    final Bitmap copyIcon = Icons.getBitmap(R.drawable.baseline_content_copy_18);
    final int radius = Screen.dp(CODE_ICON_BUTTON_RADIUS);
    final int centerX = (int) bounds.right - Screen.dp(CODE_ICON_BUTTON_END);
    final int centerY = (int) (bounds.top + headerHeight / 2f);
    copyRipple.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    copyRipple.setRadius(radius, radius, radius, radius);
    copyIconX = centerX - copyIcon.getWidth() / 2f;
    copyIconY = centerY - copyIcon.getHeight() / 2f;
    titleRight = centerX - radius;
    if (switchRipple != null) {
      final Bitmap wrapIcon = Icons.getBitmap(R.drawable.baseline_wrap_text_18);
      final int switchCenterX = centerX - Screen.dp(CODE_ICON_BUTTON_STEP);
      switchRipple.setBounds(
        switchCenterX - radius,
        centerY - radius,
        switchCenterX + radius,
        centerY + radius
      );
      switchRipple.setRadius(radius, radius, radius, radius);
      switchIconX = switchCenterX - wrapIcon.getWidth() / 2f;
      switchIconY = centerY - wrapIcon.getHeight() / 2f;
      titleRight = switchCenterX - radius;
    }
    needClipTitle = textLeft + getTitleWidth(title) > titleRight;
  }

  private RectF getBounds (
    int startX,
    int endX,
    int endXBottomPadding,
    int startY,
    int codeBlockEnd
  ) {
    final boolean isMoved =
      geometryStartX != startX ||
      geometryEndX != endX ||
      geometryEndXBottomPadding != endXBottomPadding ||
      geometryStartY != startY ||
      geometryCodeBlockEnd != codeBlockEnd;
    if (!hasGeometry || isMoved) {
      updateGeometry(startX, endX, endXBottomPadding, startY, codeBlockEnd);
      hasGeometry = true;
      geometryCodeBlockEnd = codeBlockEnd;
      geometryStartX = startX;
      geometryEndX = endX;
      geometryEndXBottomPadding = endXBottomPadding;
      geometryStartY = startY;
    }
    return bounds;
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY) {
    final int codeBlockEnd = isCode ? parent.getCodeBlockEnd() : Text.NO_CODE_BLOCK_END;
    getBounds(startX, endX, endXBottomPadding, startY, codeBlockEnd);
    wasDrawn = true;

    final ThemeDelegate theme = parent.getColorTheme();
    final int barColor;
    if (isCode) {
      // The alpha of presses comes from the theme
      final int rippleColor = theme.getColor(parent.getCodeBlockRippleColorId());
      ripple.setMainColor(rippleColor, 1f);
      ripple.drawBackground(c, theme.getColor(parent.getCodeBlockBackgroundColorId()));
      final int saveCount = Views.save(c);
      c.clipRect(bounds.left, bounds.top, bounds.right, headerBottom);
      ripple.drawBackground(c, theme.getColor(parent.getCodeBlockHeaderColorId()));
      Views.restore(c, saveCount);
      ripple.draw(c);
      drawCodeHeader(c, theme, parent.getCodeBlockLineColorId(), rippleColor);
      barColor = theme.getColor(parent.getCodeBlockBarColorId());
    } else {
      final int lineColorId = parent.getQuoteLineColorId();
      final int lineColor = theme.getColor(lineColorId);
      ripple.setMainColor(lineColor, 0.25f);
      ripple.draw(c);
      if (quoteDrawable == null) {
        quoteDrawable = Drawables.get(R.drawable.baseline_format_quote_close_16);
      }
      Drawables.draw(c, quoteDrawable, iconX, iconY, Paints.getPorterDuffPaint(lineColorId, theme));
      if (canCollapse) {
        // As far from the bottom as the quote mark is from the top; at the bottom of the list
        // while the end of an expanded quote is below it, never above its place when collapsed
        final float center = Screen.dp(QUOTE_ICON_SIZE / 2f);
        final float padding = iconY - bounds.top + center;
        final float y = parent.placeStickyElement(
          this,
          iconX + center,
          bounds.top + collapsedHeight - padding,
          bounds.bottom - padding,
          padding,
          false
        );
        if (!Float.isNaN(y)) {
          drawSticky(c, iconX + center, y, 1f);
        }
      }
      barColor = lineColor;
    }
    c.drawRoundRect(barRect, barRadius, barRadius, Paints.fillingPaint(barColor));
  }

  @Override
  public void drawSticky (Canvas c, float x, float y, float alpha) {
    final int color = parent.getColorTheme().getColor(parent.getQuoteLineColorId());
    final float switchFactor = parent.getSwitchFactor(this);
    DrawAlgorithms.drawCollapse(c, x, y, ColorUtils.alphaColor(alpha, color), switchFactor, 0f);
  }

  private void drawCodeHeader (Canvas c, ThemeDelegate theme, int lineColorId, int rippleColor) {
    final int lineColor = theme.getColor(lineColorId);
    final int saveCount = needClipTitle ? Views.save(c) : -1;
    if (needClipTitle) {
      c.clipRect(textLeft, bounds.top, titleRight, headerBottom);
    }
    final int length = title.length();
    // drawTextRun doesn't analyze direction of the text, unlike drawText, but came in Android 6.0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      c.drawTextRun(
        title,
        0,
        length,
        0,
        length,
        textLeft,
        headerBaseline,
        false,
        getHeaderPaint(lineColor)
      );
    } else {
      c.drawText(title, 0, length, textLeft, headerBaseline, getHeaderPaint(lineColor));
    }
    if (needClipTitle) {
      Views.restore(c, saveCount);
    }
    copyRipple.setMainColor(rippleColor, 1f);
    copyRipple.draw(c);
    Icons.drawBitmap(
      c,
      R.drawable.baseline_content_copy_18,
      copyIconX,
      copyIconY,
      Paints.getPorterDuffPaint(lineColorId, theme)
    );
    if (switchRipple != null) {
      switchRipple.setMainColor(rippleColor, 1f);
      switchRipple.draw(c);
      // The icon shows what a press does, and turns clockwise while lines wrap
      final float wrapFactor = parent.getSwitchFactor(this);
      final Paint paint = Paints.getPorterDuffPaint(lineColorId, theme);
      drawSwitchIcon(c, R.drawable.baseline_wrap_text_18, 90f * wrapFactor, 1f - wrapFactor, paint);
      drawSwitchIcon(
        c,
        R.drawable.baseline_swap_horiz_18,
        -90f * (1f - wrapFactor),
        wrapFactor,
        paint
      );
    }
  }

  private void drawSwitchIcon (
    Canvas c,
    @DrawableRes int icon,
    float degrees,
    float alpha,
    Paint paint
  ) {
    if (alpha == 0f) {
      return;
    }
    final Bitmap bitmap = Icons.getBitmap(icon);
    final int saveCount = Views.save(c);
    if (degrees != 0f) {
      c.rotate(
        degrees,
        switchIconX + bitmap.getWidth() / 2f,
        switchIconY + bitmap.getHeight() / 2f
      );
    }
    final int paintAlpha = paint.getAlpha();
    paint.setAlpha(Math.round(paintAlpha * alpha));
    c.drawBitmap(bitmap, switchIconX, switchIconY, paint);
    paint.setAlpha(paintAlpha);
    Views.restore(c, saveCount);
  }
}
