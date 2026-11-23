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
 * File created on 26/10/2026
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.bidi.BiDiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animatorx.FloatAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;

/**
 * Pieces of a run of a line with one paint are drawn by one call with a gradient of hard stops, as
 * drawing each token separately is slower than drawing the whole text by StaticLayout
 */
public final class SyntaxLines {
  private static final long COLORS_DURATION = 200L;
  // Kept for the next layout on the same thread
  private static final ThreadLocal<float[]> runAdvances = new ThreadLocal<>();

  private static final class Run {
    final int partStart;
    final int start, end;
    int[] pieceEnds;
    // From the start of the run
    float[] pieceEndX;
    int[] pieceStyles;
    // Colors of pieces on the last frame, which the shader has
    int[] colors;
    @Nullable Shader shader;

    Run (int partStart, int start, int end) {
      this.partStart = partStart;
      this.start = start;
      this.end = end;
    }
  }

  private final Text text;
  private final Run[] runs;
  // The first one is of code without tokens
  private final List<TextEntity> styleEntities = new ArrayList<>();
  private final IntList styles = new IntList(8);
  private int[] styleColors;
  private int[] drawnStyleColors;
  private boolean hasDrawnColors;
  private int[] pieceColors;
  private @Nullable FloatAnimator colorsAnimator;
  // Runs of another layout of the same code share these
  private int[] ranges;
  private int codeStart;
  private float textSize;
  private @Nullable Typeface typeface;
  // From codeStart, taken by the next layouts of the same code, also where lines break elsewhere
  private float[] codeAdvances;

  private SyntaxLines (Text text, Run[] runs) {
    this.text = text;
    this.runs = runs;
  }

  @Nullable
  static SyntaxLines build (
    Text text,
    int partStart,
    int partEnd,
    TextEntity codeEntity,
    int codeStart,
    int[] ranges,
    boolean animated,
    @Nullable SyntaxLines previous
  ) {
    // null when some part can be drawn only by itself, e.g. emoji. Runs of the previous lines that
    // start and end at the same characters keep their pieces
    final List<Run> runs = new ArrayList<>();
    TextPaint runPaint = null;
    int runStart = partStart;
    for (int partIndex = partStart; partIndex < partEnd; partIndex++) {
      final TextPart part = text.getTextPart(partIndex);
      if (part.isStaticElement() || part.getSpoilerEntity() != null ||
        BiDiUtils.isRtl(part.getBidiEntity())) {
        return null;
      }
      final TextPaint paint = text.getTextPaint(part.getEntity());
      if (paint.isUnderlineText() || paint.isStrikeThruText()) {
        return null;
      }
      final TextPart lastPart = partIndex > runStart ? text.getTextPart(partIndex - 1) : null;
      if (lastPart != null && (paint != runPaint ||
        part.getLineIndex() != lastPart.getLineIndex() || part.getStart() != lastPart.getEnd())) {
        runs.add(newRun(text, runStart, partIndex));
        runStart = partIndex;
      }
      runPaint = paint;
    }
    runs.add(newRun(text, runStart, partEnd));
    final SyntaxLines lines = new SyntaxLines(text, runs.toArray(new Run[0]));
    lines.setTokens(codeEntity, codeStart, ranges, animated, previous);
    return lines;
  }

  private static Run newRun (Text text, int partStart, int partEnd) {
    final int start = text.getTextPart(partStart).getStart();
    return new Run(partStart, start, text.getTextPart(partEnd - 1).getEnd());
  }

  private void setTokens (
    TextEntity codeEntity,
    int codeStart,
    int[] ranges,
    boolean animated,
    @Nullable SyntaxLines previous
  ) {
    // Bold and italic stay only where they don't change widths, which layout doesn't know
    styles.append(0);
    styleEntities.add(codeEntity);
    final TextPaint codePaint = text.getTextPaint(codeEntity);
    this.ranges = ranges;
    this.codeStart = codeStart;
    this.textSize = codePaint.getTextSize();
    this.typeface = codePaint.getTypeface();
    // The same tokens are the same code and language
    final boolean canReuse = previous != null && previous.ranges == ranges &&
      previous.codeStart == codeStart && previous.textSize == textSize &&
      previous.typeface == typeface;
    // Characters of monospace code have the same advances whatever run they're measured in
    final boolean isMeasured = canReuse;
    codeAdvances = canReuse ?
      previous.codeAdvances : new float[runs[runs.length - 1].end - codeStart];
    // Runs go in the order of the text, so tokens that end before a run aren't looked at again
    int firstToken = 0;
    float[] advances = runAdvances.get();
    for (Run run : runs) {
      while (firstToken < ranges.length && codeStart + ranges[firstToken + 1] <= run.start) {
        firstToken += 3;
      }
      final Run previousRun = canReuse ? previous.findRun(run.start) : null;
      if (previousRun != null && previousRun.start == run.start && previousRun.end == run.end) {
        run.pieceEnds = previousRun.pieceEnds;
        run.pieceEndX = previousRun.pieceEndX;
        run.pieceStyles = previousRun.pieceStyles;
        for (int style : run.pieceStyles) {
          styleEntity(codeEntity, style);
        }
        run.colors = new int[run.pieceEnds.length];
        continue;
      }
      final IntList pieceEnds = new IntList(8);
      final IntList pieceStyles = new IntList(8);
      int position = run.start;
      for (int i = firstToken; i < ranges.length && position < run.end; i += 3) {
        final int tokenStart = Math.max(codeStart + ranges[i], position);
        final int tokenEnd = Math.min(codeStart + ranges[i + 1], run.end);
        if (tokenEnd <= tokenStart) {
          continue;
        }
        if (tokenStart > position) {
          addPiece(tokenStart, 0, pieceEnds, pieceStyles);
        }
        addPiece(tokenEnd, ranges[i + 2], pieceEnds, pieceStyles);
        position = tokenEnd;
      }
      if (position < run.end) {
        addPiece(run.end, 0, pieceEnds, pieceStyles);
      }
      final TextPart part = text.getTextPart(run.partStart);
      final String line = part.getLine();
      final TextPaint paint = text.getTextPaint(part.getEntity());
      run.pieceEnds = pieceEnds.get();
      run.pieceStyles = pieceStyles.get();
      run.pieceEndX = new float[run.pieceEnds.length];
      // The whole run at once, as measuring each piece from its start takes quadratic time
      if (!isMeasured) {
        final int length = run.end - run.start;
        if (advances == null || advances.length < length) {
          advances = new float[length];
          runAdvances.set(advances);
        }
        paint.getTextWidths(line, run.start, run.end, advances);
        System.arraycopy(advances, 0, codeAdvances, run.start - codeStart, length);
      }
      float x = 0f;
      int index = run.start;
      for (int i = 0; i < run.pieceEnds.length; i++) {
        for (; index < run.pieceEnds[i]; index++) {
          x += codeAdvances[index - codeStart];
        }
        run.pieceEndX[i] = x;
        int style = run.pieceStyles[i];
        if ((style & ~CodeHighlighter.STYLE_COLOR_MASK) != 0) {
          final TextPaint stylePaint = text.getTextPaint(styleEntity(codeEntity, style));
          final float width = run.pieceEndX[i] - pieceStartX(run, i);
          final float styleWidth =
            stylePaint.measureText(line, pieceStart(run, i), run.pieceEnds[i]);
          if (Math.abs(styleWidth - width) > .5f) {
            style &= CodeHighlighter.STYLE_COLOR_MASK;
          }
        }
        run.pieceStyles[i] = style;
        styleEntity(codeEntity, style);
      }
      run.colors = new int[run.pieceEnds.length];
    }
    styleColors = new int[styles.size()];
    drawnStyleColors = new int[styles.size()];
    hasDrawnColors = false;
    pieceColors = new int[styles.size()];
    if (animated) {
      colorsAnimator = new FloatAnimator(
        COLORS_DURATION,
        AnimatorUtils.DECELERATE_INTERPOLATOR,
        0f,
        newValue -> text.invalidateViews()
      );
      colorsAnimator.setAnimatedValue(1f);
    }
  }

  private static void addPiece (int end, int style, IntList pieceEnds, IntList pieceStyles) {
    final int last = pieceStyles.size() - 1;
    if (last >= 0 && pieceStyles.get(last) == style) {
      pieceEnds.set(last, end);
    } else {
      pieceEnds.append(end);
      pieceStyles.append(style);
    }
  }

  private TextEntity styleEntity (TextEntity codeEntity, int style) {
    int index = styles.indexOf(style);
    if (index == -1) {
      index = styles.size();
      styles.append(style);
      styleEntities.add(((TextEntityMessage) codeEntity).createSyntaxToken(
        codeEntity.getStart(), codeEntity.getEnd(),
        CodeHighlighter.COLOR_IDS[style & CodeHighlighter.STYLE_COLOR_MASK],
        (style & CodeHighlighter.STYLE_BOLD) != 0,
        (style & CodeHighlighter.STYLE_ITALIC) != 0
      ));
    }
    return styleEntities.get(index);
  }

  boolean isAnimatingColors () {
    return colorsAnimator != null && colorsAnimator.isAnimating();
  }

  boolean copyAdvances (TextPaint paint, int start, int end, float[] out) {
    // So that pieces of parts aren't measured again, when the paint measures as the one of code
    if (codeAdvances == null || start < codeStart || end - codeStart > codeAdvances.length ||
      paint.getTextSize() != textSize || paint.getTypeface() != typeface) {
      return false;
    }
    System.arraycopy(codeAdvances, start - codeStart, out, 0, end - start);
    return true;
  }

  int styleAt (int index) {
    final Run run = findRun(index);
    return run != null ? run.pieceStyles[findPiece(run, index)] : 0;
  }

  int styleEnd (int index) {
    final Run run = findRun(index);
    return run != null ? run.pieceEnds[findPiece(run, index)] : index + 1;
  }

  @Nullable
  private Run findRun (int index) {
    // Runs are ordered without overlaps, and a change of a long block asks for each piece
    int low = 0;
    int high = runs.length - 1;
    while (low <= high) {
      final int middle = (low + high) >>> 1;
      final Run run = runs[middle];
      if (index < run.start) {
        high = middle - 1;
      } else if (index >= run.end) {
        low = middle + 1;
      } else {
        return run;
      }
    }
    return null;
  }

  private static int findPiece (Run run, int index) {
    // Pieces cover the whole run, the last one ends at its end
    int piece = 0;
    while (index >= run.pieceEnds[piece]) {
      piece++;
    }
    return piece;
  }

  void updatePieceColors (@Nullable TextColorSet defaultTheme) {
    // Once for all pieces of a change, as the press of the block looks through all its runs
    final boolean isPressed = isPressed();
    for (int i = 0; i < pieceColors.length; i++) {
      pieceColors[i] = colorOf(styleEntities.get(i), defaultTheme, isPressed);
    }
  }

  int pieceColorOf (int style) {
    return pieceColors[styles.indexOf(style)];
  }

  private int colorOf (TextEntity entity, @Nullable TextColorSet defaultTheme, boolean isPressed) {
    return text.getTextColor(defaultTheme, entity, text.isClickable(entity), isPressed);
  }

  void draw (
    Canvas c,
    int startX,
    int endX,
    int endXBottomPadding,
    int startY,
    float alpha,
    @Nullable TextColorSet defaultTheme
  ) {
    final float colorsFactor = colorsAnimator != null ? colorsAnimator.getValue() : 1f;
    updateStyleColors(defaultTheme, colorsFactor);
    final boolean colorsChange = colorsFactor < 1f ||
      (hasDrawnColors && !Arrays.equals(styleColors, drawnStyleColors));
    System.arraycopy(styleColors, 0, drawnStyleColors, 0, styleColors.length);
    hasDrawnColors = true;
    for (Run run : runs) {
      final TextPart part = text.getTextPart(run.partStart);
      final TextPaint paint = part.getPaint(run.partStart, alpha, defaultTheme);
      final int x = part.makeX(startX, endX, endXBottomPadding);
      final int y = startY + part.getY() + text.getPartVerticalOffset(part) +
        text.getAscent(paint.getTextSize()) + paint.baselineShift;
      final String line = part.getLine();
      final char[] chars = text.getDrawnChars(line);
      final int saveCount = Views.save(c);
      c.translate(x, 0);
      if (colorsChange) {
        // Colors that change on this frame, by the animation of tokens or of a theme, would make
        // a shader again on each frame, so tokens are drawn one by one without shaders
        for (int i = 0; i < run.pieceEnds.length; i++) {
          final TextPaint piecePaint = paintOf(run.pieceStyles[i], paint);
          piecePaint.setColor(
            ColorUtils.alphaColor(alpha, styleColors[styles.indexOf(run.pieceStyles[i])]));
          drawText(c, line, chars, pieceStart(run, i), run.pieceEnds[i], run, pieceStartX(run, i),
            y, piecePaint);
        }
      } else {
        drawWithShader(c, line, chars, run, y, paint, alpha);
      }
      Views.restore(c, saveCount);
    }
  }

  private void drawWithShader (
    Canvas c,
    String line,
    @Nullable char[] chars,
    Run run,
    int y,
    TextPaint paint,
    float alpha
  ) {
    final boolean hasShader = updateShader(run);
    for (int i = 0; i < run.pieceEnds.length; ) {
      final TextPaint piecePaint = paintOf(run.pieceStyles[i], paint);
      int end = i + 1;
      while (end < run.pieceEnds.length && paintOf(run.pieceStyles[end], paint) == piecePaint) {
        end++;
      }
      if (hasShader) {
        piecePaint.setShader(run.shader);
        piecePaint.setAlpha((int) (alpha * 255f));
      } else {
        piecePaint.setColor(ColorUtils.alphaColor(alpha, run.colors[i]));
      }
      drawText(c, line, chars, pieceStart(run, i), run.pieceEnds[end - 1], run,
        pieceStartX(run, i), y, piecePaint);
      if (hasShader) {
        piecePaint.setShader(null);
      }
      i = end;
    }
  }

  private static int pieceStart (Run run, int index) {
    return index == 0 ? run.start : run.pieceEnds[index - 1];
  }

  private static float pieceStartX (Run run, int index) {
    return index == 0 ? 0f : run.pieceEndX[index - 1];
  }

  TextPaint paintOf (int style, TextPaint runPaint) {
    if ((style & ~CodeHighlighter.STYLE_COLOR_MASK) == 0) {
      return runPaint;
    }
    return text.getTextPaint(styleEntities.get(styles.indexOf(style)));
  }

  private static void drawText (
    Canvas c,
    String line,
    @Nullable char[] chars,
    int start,
    int end,
    Run run,
    float x,
    int y,
    TextPaint paint
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (chars != null) {
        c.drawTextRun(
          chars,
          start,
          end - start,
          run.start,
          run.end - run.start,
          x,
          y,
          false,
          paint
        );
      } else {
        c.drawTextRun(line, start, end, run.start, run.end, x, y, false, paint);
      }
    } else if (chars != null) {
      c.drawText(chars, start, end - start, x, y, paint);
    } else {
      c.drawText(line, start, end, x, y, paint);
    }
  }

  private void updateStyleColors (@Nullable TextColorSet defaultTheme, float factor) {
    final boolean isPressed = isPressed();
    final int plainColor = colorOf(styleEntities.get(0), defaultTheme, isPressed);
    for (int i = 0; i < styleColors.length; i++) {
      final int color = colorOf(styleEntities.get(i), defaultTheme, isPressed);
      styleColors[i] = ColorUtils.fromToArgb(plainColor, color, factor);
    }
  }

  private boolean isPressed () {
    for (Run run : runs) {
      if (text.isPressed(run.partStart)) {
        return true;
      }
    }
    return false;
  }

  private boolean updateShader (Run run) {
    // false when all tokens of the run have the same color, e.g. while the block is pressed
    boolean changed = run.shader == null, sameColor = true;
    final int firstColor = styleColors[styles.indexOf(run.pieceStyles[0])];
    for (int i = 0; i < run.pieceStyles.length; i++) {
      final int color = styleColors[styles.indexOf(run.pieceStyles[i])];
      changed = changed || run.colors[i] != color;
      sameColor = sameColor && color == firstColor;
      run.colors[i] = color;
    }
    final float width = run.pieceEndX[run.pieceEndX.length - 1];
    if (sameColor || width <= 0f) {
      return false;
    }
    if (changed) {
      final int[] stopColors = new int[run.colors.length * 2];
      final float[] stops = new float[run.colors.length * 2];
      for (int i = 0; i < run.colors.length; i++) {
        stopColors[i * 2] = stopColors[i * 2 + 1] = run.colors[i];
        stops[i * 2] = pieceStartX(run, i) / width;
        stops[i * 2 + 1] = run.pieceEndX[i] / width;
      }
      run.shader = new LinearGradient(0, 0, width, 0, stopColors, stops, Shader.TileMode.CLAMP);
    }
    return true;
  }
}
