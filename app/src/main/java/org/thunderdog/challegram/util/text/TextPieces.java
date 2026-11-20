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
 * File created on 27/10/2026
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.text.TextPaint;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.quotes.QuoteBackground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * Pieces of a changed text with the same characters as before move from their old places, others
 * fade in, and old characters that no piece took fade out
 */
final class TextPieces {
  // A whole part of code, cut by its tokens only when it's drawn
  private static final int STYLE_UNCUT = -1;
  private static final Comparator<Piece> BY_SOURCE = (a, b) -> Integer.compare(a.source, b.source);

  private static final class Piece {
    // partIndex is -1 for a part of an old text
    final TextPart part;
    final int partIndex, start, end;
    final boolean isWhole;
    // Characters of the piece are in the text that the change starts from
    final boolean isMatchable;
    final float fromX, fromY, fromAlpha, toX, toY, toAlpha;
    final @Nullable QuoteBackground block;
    final @Nullable SyntaxLines lines;
    final int style;
    // Keeps the order in which pieces were drawn, Builder.NEW_SOURCE for new ones
    final int source;

    Piece (
      TextPart part,
      int partIndex,
      int start,
      int end,
      boolean isWhole,
      boolean isMatchable,
      float fromX,
      float fromY,
      float fromAlpha,
      float toX,
      float toY,
      float toAlpha,
      @Nullable QuoteBackground block,
      @Nullable SyntaxLines lines,
      int style,
      int source
    ) {
      this.part = part;
      this.partIndex = partIndex;
      this.start = start;
      this.end = end;
      this.isWhole = isWhole;
      this.isMatchable = isMatchable;
      this.fromX = fromX;
      this.fromY = fromY;
      this.fromAlpha = fromAlpha;
      this.toX = toX;
      this.toY = toY;
      this.toAlpha = toAlpha;
      this.block = block;
      this.lines = lines;
      this.style = style;
      this.source = source;
    }

    Piece withY (float fromY, float toY) {
      return new Piece(
        part,
        partIndex,
        start,
        end,
        isWhole,
        isMatchable,
        fromX,
        fromY,
        fromAlpha,
        toX,
        toY,
        toAlpha,
        block,
        lines,
        style,
        source
      );
    }

    Piece atFactor (float factor) {
      final float x = MathUtils.fromTo(fromX, toX, factor);
      final float y = MathUtils.fromTo(fromY, toY, factor);
      final float alpha = MathUtils.fromTo(fromAlpha, toAlpha, factor);
      return new Piece(
        part,
        partIndex,
        start,
        end,
        isWhole,
        isMatchable,
        x,
        y,
        alpha,
        x,
        y,
        alpha,
        null,
        lines,
        style,
        source
      );
    }
  }

  private final String text;
  private final Piece[] pieces;

  private TextPieces (String text, Piece[] pieces) {
    this.text = text;
    this.pieces = pieces;
  }

  private static float getPartX (
    Text text,
    TextPart part,
    @Nullable QuoteBackground block,
    int startX,
    int endX,
    int endXBottomPadding
  ) {
    final int scrollX = block != null ? block.getScrollX() : 0;
    return text.getPartX(part, startX, endX, endXBottomPadding) - startX - scrollX;
  }

  static TextPieces ofLayout (Text text, int startX, int endX, int endXBottomPadding) {
    final Builder builder = new Builder(text);
    for (int i = 0; i < text.getPartCount(); i++) {
      final TextPart part = text.getTextPart(i);
      final QuoteBackground block = text.findBlockOfPart(i);
      final float x = getPartX(text, part, block, startX, endX, endXBottomPadding);
      final float y = part.getY() + text.getPartVerticalOffset(part);
      builder.still(part, i, x, y, block != null ? block.lines : null);
    }
    return builder.build();
  }

  TextPieces atFactor (float factor) {
    final Piece[] result = new Piece[pieces.length];
    for (int i = 0; i < pieces.length; i++) {
      result[i] = pieces[i].atFactor(factor);
    }
    return new TextPieces(text, result);
  }

  static TextPieces change (
    TextPieces from,
    @Nullable TextPieces drawn,
    Text text,
    int startX,
    int endX,
    int endXBottomPadding,
    @Nullable int[] visible,
    float startFactor,
    @Nullable KeptOnScreen kept
  ) {
    // visible: top and bottom of the part on the screen where the change starts at startFactor
    // and where it ends, null when unknown; only pieces seen in between are made. drawn: pieces
    // of a change that this one reverses on this frame
    final String fromText = from.text, newText = text.getText();
    final TextMatching matching = TextMatching.match(fromText, newText);
    final int[] fromPieces = new int[fromText.length()];
    Arrays.fill(fromPieces, -1);
    for (int i = 0; i < from.pieces.length; i++) {
      if (from.pieces[i].isMatchable) {
        Arrays.fill(fromPieces, from.pieces[i].start, from.pieces[i].end, i);
      }
    }
    final boolean[] isTaken = new boolean[fromText.length()];
    final int margin = text.getChangeMargin();
    // Characters of the same string find where the reversed change draws them
    final int[] drawnPieces = drawn != null && drawn.text == newText ? drawn.mapDrawn() : null;
    final StartY startY = new StartY(drawn, drawnPieces, startFactor);

    final Builder builder = new Builder(text);
    for (int index = 0; index < text.getPartCount(); index++) {
      final TextPart part = text.getTextPart(index);
      final QuoteBackground block = text.findBlockOfPart(index);
      final float x = getPartX(text, part, block, startX, endX, endXBottomPadding);
      final float y = part.getY() + text.getPartVerticalOffset(part);
      final boolean isSeen = visible == null || isPartSeen(
        from,
        fromPieces,
        matching,
        newText,
        part,
        y,
        y + Builder.getChangeShift(block),
        visible,
        startY,
        margin
      );
      if (!isSeen) {
        // Characters of a part that isn't seen don't fade out from their old places either
        if (part.getLine() == newText) {
          markTaken(matching, isTaken, part.getStart(), part.getEnd());
        }
        continue;
      }
      if (part.getLine() != newText || !part.canDrawRange()) {
        // A whole part moves only from an old whole piece with the same characters
        final int fromPiece =
          part.getLine() == newText ? findWholeMatch(from, fromPieces, matching, part) : -1;
        final int start = part.getStart(), end = part.getEnd();
        builder.source = fromPiece != -1 ? fromPiece : Builder.NEW_SOURCE;
        if (fromPiece != -1) {
          final Piece old = from.pieces[fromPiece];
          Arrays.fill(isTaken, old.start, old.end, true);
          builder.move(part, index, start, end, true, old, old.toX, x, y, block);
        } else {
          builder.fadeIn(part, index, start, end, true, x, y, block);
        }
        continue;
      }
      for (int start = part.getStart(); start < part.getEnd(); ) {
        if (Character.isWhitespace(newText.charAt(start))) {
          start++;
          continue;
        }
        final int oldStart = matching.findOldOffset(start);
        final int fromPiece = oldStart != -1 ? fromPieces[oldStart] : -1;
        // Highlighted code isn't cut at whitespace, as its tokens cut it anyway into fewer pieces
        final boolean isCode = block != null && block.lines != null;
        int end = start + 1;
        while (end < part.getEnd() && (isCode || !Character.isWhitespace(newText.charAt(end)))) {
          final int nextOldStart = matching.findOldOffset(end);
          if (fromPiece == -1) {
            if (nextOldStart != -1 && fromPieces[nextOldStart] != -1) {
              break;
            }
          } else if (nextOldStart != oldStart + end - start ||
            oldStart + end - start >= from.pieces[fromPiece].end) {
            break;
          }
          end++;
        }
        while (Character.isWhitespace(newText.charAt(end - 1))) {
          end--;
        }
        final float pieceX = x + builder.advances.offset(text, part, start);
        final Piece old =
          fromPiece != -1 && !from.pieces[fromPiece].isWhole ? from.pieces[fromPiece] : null;
        builder.source = old != null ? fromPiece : Builder.NEW_SOURCE;
        if (old != null) {
          final float fromX = old.toX + builder.fromAdvances.offset(text, old.part, oldStart) -
            builder.fromAdvances.offset(text, old.part, old.start);
          Arrays.fill(isTaken, oldStart, oldStart + end - start, true);
          if (block != null && block.lines == null && old.lines != null) {
            // Tokens of the new code come later, its characters keep the old colors meanwhile
            builder.moveWithStyle(part, index, start, end, old, oldStart, fromX, pieceX, y, block);
          } else {
            builder.move(part, index, start, end, false, old, fromX, pieceX, y, block);
          }
        } else {
          builder.fadeIn(part, index, start, end, false, pieceX, y, block);
        }
        start = end;
      }
    }
    for (int i = 0; i < from.pieces.length; i++) {
      final Piece old = from.pieces[i];
      final QuoteBackground block = text.findBlockById(old.part.getQuoteEntityId());
      final float toY = old.toY - Builder.getChangeShift(block);
      if (visible != null) {
        final float start = startY.of(old.part.getLine(), old.start, old.end, old.toY, toY);
        if (!isSeen(visible, start, start, toY, margin)) {
          continue;
        }
      }
      builder.source = i;
      if (old.isWhole) {
        // Parts of an old text with media or emoji can't be drawn after the layout and disappear,
        // unless the new one has them in lines that a collapsed quote hides
        final TextPart hiddenPart = text.findHiddenPart(old.part);
        if (hiddenPart != null && (old.start == old.end || !isTaken[old.start])) {
          builder.fadeOutWhole(old, hiddenPart, block);
        }
        continue;
      }
      if (!old.isMatchable) {
        builder.fadeOut(old, old.start, old.end, old.toX, block);
        continue;
      }
      final String line = old.part.getLine();
      for (int start = old.start; start < old.end; ) {
        if (isTaken[start] || Character.isWhitespace(line.charAt(start))) {
          start++;
          continue;
        }
        int end = start + 1;
        while (end < old.end && !isTaken[end] && !Character.isWhitespace(line.charAt(end))) {
          end++;
        }
        final float x = old.toX + builder.fromAdvances.offset(text, old.part, start) -
          builder.fromAdvances.offset(text, old.part, old.start);
        builder.fadeOut(old, start, end, x, block);
        start = end;
      }
    }
    final TextPieces result = builder.build();
    if (kept != null) {
      result.keepOnScreen(kept, startY);
    }
    return result;
  }

  private void keepOnScreen (KeptOnScreen kept, StartY startY) {
    final float[] drawnY = new float[pieces.length];
    for (int i = 0; i < pieces.length; i++) {
      final Piece piece = pieces[i];
      drawnY[i] = startY.of(piece.part.getLine(), piece.start, piece.end, piece.fromY, piece.toY);
      kept.add(drawnY[i], piece.toY);
    }
    for (int i = 0; i < pieces.length; i++) {
      final Piece piece = pieces[i];
      final float toY = kept.to(drawnY[i], piece.toY);
      final float fromY = kept.from(drawnY[i], piece.toY, toY);
      if (fromY != piece.fromY || toY != piece.toY) {
        pieces[i] = piece.withY(fromY, toY);
      }
    }
  }

  private int[] mapDrawn () {
    final int[] result = new int[text.length()];
    Arrays.fill(result, -1);
    for (int i = 0; i < pieces.length; i++) {
      if (pieces[i].part.getLine() == text) {
        Arrays.fill(result, pieces[i].start, pieces[i].end, i);
      }
    }
    return result;
  }

  private static final class StartY {
    // Where a piece is at startFactor: where the reversed change draws it, also off its way
    private final @Nullable TextPieces drawn;
    private final @Nullable int[] drawnPieces;
    final float startFactor;

    StartY (@Nullable TextPieces drawn, @Nullable int[] drawnPieces, float startFactor) {
      this.drawn = drawn;
      this.drawnPieces = drawnPieces;
      this.startFactor = startFactor;
    }

    float of (String line, int start, int end, float fromY, float toY) {
      final int piece = find(line, start, end, false);
      return piece != -1 ? drawn.pieces[piece].fromY : MathUtils.fromTo(fromY, toY, startFactor);
    }

    float ofLast (String line, int start, int end, float fromY, float toY) {
      final int piece = find(line, start, end, true);
      return piece != -1 ? drawn.pieces[piece].fromY : MathUtils.fromTo(fromY, toY, startFactor);
    }

    private int find (String line, int start, int end, boolean fromEnd) {
      if (drawnPieces == null || line != drawn.text) {
        return -1;
      }
      for (int i = 0; i < end - start; i++) {
        final int piece = drawnPieces[fromEnd ? end - 1 - i : start + i];
        if (piece != -1) {
          return piece;
        }
      }
      return -1;
    }
  }

  private static boolean isPartSeen (
    TextPieces from,
    int[] fromPieces,
    TextMatching matching,
    String newText,
    TextPart part,
    float y,
    float fadeFromY,
    int[] visible,
    StartY startY,
    int margin
  ) {
    final float fadeStart = MathUtils.fromTo(fadeFromY, y, startY.startFactor);
    float startTop = Math.min(y, fadeStart), startBottom = Math.max(y, fadeStart);
    if (part.getLine() == newText && part.getEnd() > part.getStart()) {
      final int start = part.getStart(), end = part.getEnd();
      final float first = startY.of(newText, start, end,
        findFromY(from, fromPieces, matching, start, fadeFromY), y);
      final float last = startY.ofLast(newText, start, end,
        findFromY(from, fromPieces, matching, end - 1, fadeFromY), y);
      startTop = Math.min(y, Math.min(first, last));
      startBottom = Math.max(y, Math.max(first, last));
    }
    return isSeen(visible, startTop, startBottom, y, margin);
  }

  private static float findFromY (
    TextPieces from,
    int[] fromPieces,
    TextMatching matching,
    int index,
    float y
  ) {
    final int oldStart = matching.findOldOffset(index);
    final int fromPiece = oldStart != -1 ? fromPieces[oldStart] : -1;
    return fromPiece != -1 ? from.pieces[fromPiece].toY : y;
  }

  private static boolean isSeen (
    int[] visible,
    float startTop,
    float startBottom,
    float toY,
    int margin
  ) {
    // Pieces and the visible part move by the same factor, so a piece is seen when it's seen at
    // either end or passes the visible part in between
    final float top = startTop - visible[0], bottom = startBottom - visible[0];
    final float end = toY - visible[2];
    final int height = Math.max(visible[1] - visible[0], visible[3] - visible[2]);
    return Math.min(top, end) <= height + margin && Math.max(bottom, end) >= -margin;
  }

  private static void markTaken (TextMatching matching, boolean[] isTaken, int start, int end) {
    for (int index = start; index < end; ) {
      final int range = matching.findRange(index);
      if (range == -1) {
        index++;
        continue;
      }
      final int rangeEnd =
        Math.min(end, matching.getNewStart(range) + matching.getLength(range));
      final int oldStart = matching.getOldStart(range) + index - matching.getNewStart(range);
      Arrays.fill(isTaken, oldStart, oldStart + rangeEnd - index, true);
      index = rangeEnd;
    }
  }

  private static int findWholeMatch (
    TextPieces from,
    int[] fromPieces,
    TextMatching matching,
    TextPart part
  ) {
    final int range = matching.findRange(part.getStart());
    if (range == -1 || matching.findRange(part.getEnd() - 1) != range) {
      return -1;
    }
    final int oldStart = matching.findOldOffset(part.getStart());
    final int fromPiece = fromPieces[oldStart];
    if (fromPiece == -1) {
      return -1;
    }
    final Piece old = from.pieces[fromPiece];
    final int length = part.getEnd() - part.getStart();
    return old.isWhole && old.start == oldStart && old.end - old.start == length ? fromPiece : -1;
  }

  void draw (
    Text text,
    Canvas c,
    int startX,
    int endX,
    int endXBottomPadding,
    int startY,
    float factor,
    float alpha,
    @Nullable TextColorSet defaultTheme,
    @Nullable ComplexReceiver receiver
  ) {
    QuoteBackground clipBlock = null;
    int saveCount = -1, fadeSaveCount = -1;
    SyntaxLines colorLines = null;
    for (Piece piece : pieces) {
      if (piece.block != clipBlock) {
        if (saveCount != -1) {
          clipBlock.finishFade(c, fadeSaveCount);
          Views.restore(c, saveCount);
          saveCount = -1;
        }
        clipBlock = piece.block;
        if (clipBlock != null) {
          saveCount = Views.save(c);
          clipBlock.clipContent(c);
          fadeSaveCount = clipBlock.beginFade(c);
        }
      }
      final float pieceAlpha = alpha * MathUtils.fromTo(piece.fromAlpha, piece.toAlpha, factor);
      if (pieceAlpha <= 0f) {
        continue;
      }
      final TextPart part = piece.part;
      final float x = startX + MathUtils.fromTo(piece.fromX, piece.toX, factor);
      final float y = startY + MathUtils.fromTo(piece.fromY, piece.toY, factor);
      if (piece.isWhole) {
        final int restoreCount = Views.save(c);
        c.translate(x - part.makeX(startX, endX, endXBottomPadding), y - part.getY());
        part.draw(
          piece.partIndex,
          c,
          startX,
          endX,
          endXBottomPadding,
          0,
          pieceAlpha,
          defaultTheme,
          receiver
        );
        Views.restore(c, restoreCount);
      } else {
        TextPaint paint = part.getPaint(piece.partIndex, pieceAlpha, defaultTheme);
        if (piece.lines != null) {
          if (piece.lines != colorLines) {
            colorLines = piece.lines;
            colorLines.updatePieceColors(defaultTheme);
          }
          paint = piece.lines.paintOf(piece.style, paint);
          paint.setColor(ColorUtils.alphaColor(pieceAlpha, piece.lines.pieceColorOf(piece.style)));
        }
        final float baseline = y + text.getAscent(paint.getTextSize()) + paint.baselineShift;
        final char[] chars = text.getDrawnChars(part.getLine());
        if (chars != null) {
          c.drawText(chars, piece.start, piece.end - piece.start, x, baseline, paint);
        } else {
          c.drawText(part.getLine(), piece.start, piece.end, x, baseline, paint);
        }
      }
    }
    if (saveCount != -1) {
      clipBlock.finishFade(c, fadeSaveCount);
      Views.restore(c, saveCount);
    }
  }

  private static final class Builder {
    // Pieces that fade in are drawn over the others
    static final int NEW_SOURCE = Integer.MAX_VALUE;

    private final Text text;
    private final ArrayList<Piece> pieces = new ArrayList<>();
    private int source = NEW_SOURCE;
    final PartAdvances advances = new PartAdvances(), fromAdvances = new PartAdvances();

    Builder (Text text) {
      this.text = text;
    }

    void still (TextPart part, int partIndex, float x, float y, @Nullable SyntaxLines lines) {
      // Code is cut by its tokens only when a change draws it
      pieces.add(new Piece(
        part,
        partIndex,
        part.getStart(),
        part.getEnd(),
        !part.canDrawRange(),
        part.getLine() == text.getText(),
        x,
        y,
        1f,
        x,
        y,
        1f,
        null,
        lines,
        lines != null ? STYLE_UNCUT : 0,
        source
      ));
    }

    void fadeIn (
      TextPart part,
      int partIndex,
      int start,
      int end,
      boolean isWhole,
      float x,
      float y,
      @Nullable QuoteBackground block
    ) {
      final float fromY = y + getChangeShift(block);
      add(part, partIndex, start, end, isWhole, x, fromY, 0f, x, y, block);
    }

    private static int getChangeShift (@Nullable QuoteBackground block) {
      // Lines that a collapsed quote hides move with those it shows to found text
      return block != null ? block.getChangeShift() : 0;
    }

    void move (
      TextPart part,
      int partIndex,
      int start,
      int end,
      boolean isWhole,
      Piece from,
      float fromX,
      float x,
      float y,
      @Nullable QuoteBackground block
    ) {
      add(part, partIndex, start, end, isWhole, fromX, from.toY, from.toAlpha, x, y, block);
    }

    void moveWithStyle (
      TextPart part,
      int partIndex,
      int start,
      int end,
      Piece from,
      int fromStart,
      float fromX,
      float x,
      float y,
      @Nullable QuoteBackground block
    ) {
      for (int pieceStart = start; pieceStart < end; ) {
        final int oldStart = fromStart + pieceStart - start;
        final int pieceEnd = from.style == STYLE_UNCUT ?
          Math.min(end, pieceStart + from.lines.styleEnd(oldStart) - oldStart) : end;
        final float offset =
          advances.offset(text, part, pieceStart) - advances.offset(text, part, start);
        pieces.add(new Piece(
          part,
          partIndex,
          pieceStart,
          pieceEnd,
          false,
          true,
          fromX + offset,
          from.toY,
          from.toAlpha,
          x + offset,
          y,
          1f,
          block,
          from.lines,
          from.style == STYLE_UNCUT ? from.lines.styleAt(oldStart) : from.style,
          source
        ));
        pieceStart = pieceEnd;
      }
    }

    void fadeOut (Piece from, int start, int end, float x, @Nullable QuoteBackground block) {
      final float toY = from.toY - getChangeShift(block);
      for (int pieceStart = start; pieceStart < end; ) {
        final int pieceEnd = from.style == STYLE_UNCUT ?
          Math.min(end, from.lines.styleEnd(pieceStart)) : end;
        final float pieceX = x + fromAdvances.offset(text, from.part, pieceStart) -
          fromAdvances.offset(text, from.part, start);
        pieces.add(new Piece(
          from.part,
          -1,
          pieceStart,
          pieceEnd,
          false,
          false,
          pieceX,
          from.toY,
          from.toAlpha,
          pieceX,
          toY,
          0f,
          block,
          from.lines,
          from.style == STYLE_UNCUT ? from.lines.styleAt(pieceStart) : from.style,
          source
        ));
        pieceStart = pieceEnd;
      }
    }

    void fadeOutWhole (Piece from, TextPart part, @Nullable QuoteBackground block) {
      pieces.add(new Piece(
        part,
        -1,
        from.start,
        from.end,
        true,
        false,
        from.toX,
        from.toY,
        from.toAlpha,
        from.toX,
        from.toY - getChangeShift(block),
        0f,
        block,
        null,
        0,
        source
      ));
    }

    private void add (
      TextPart part,
      int partIndex,
      int start,
      int end,
      boolean isWhole,
      float fromX,
      float fromY,
      float fromAlpha,
      float toX,
      float toY,
      @Nullable QuoteBackground block
    ) {
      final SyntaxLines lines =
        block != null && part.getLine() == text.getText() ? block.lines : null;
      addCut(
        part,
        partIndex,
        start,
        end,
        isWhole,
        part.getLine() == text.getText(),
        fromX,
        fromY,
        fromAlpha,
        toX,
        toY,
        block,
        lines
      );
    }

    private void addCut (
      TextPart part,
      int partIndex,
      int start,
      int end,
      boolean isWhole,
      boolean isMatchable,
      float fromX,
      float fromY,
      float fromAlpha,
      float toX,
      float toY,
      @Nullable QuoteBackground block,
      @Nullable SyntaxLines lines
    ) {
      if (isWhole || lines == null) {
        pieces.add(new Piece(
          part,
          partIndex,
          start,
          end,
          isWhole,
          isMatchable,
          fromX,
          fromY,
          fromAlpha,
          toX,
          toY,
          1f,
          block,
          null,
          0,
          source
        ));
        return;
      }
      for (int pieceStart = start; pieceStart < end; ) {
        final int pieceEnd = Math.min(end, lines.styleEnd(pieceStart));
        // Whitespace between tokens has nothing to draw
        int first = pieceStart;
        while (first < pieceEnd && Character.isWhitespace(part.getLine().charAt(first))) {
          first++;
        }
        if (first == pieceEnd) {
          pieceStart = pieceEnd;
          continue;
        }
        final float offset =
          advances.offset(text, part, pieceStart) - advances.offset(text, part, start);
        pieces.add(new Piece(
          part,
          partIndex,
          pieceStart,
          pieceEnd,
          false,
          isMatchable,
          fromX + offset,
          fromY,
          fromAlpha,
          toX + offset,
          toY,
          1f,
          block,
          lines,
          lines.styleAt(pieceStart),
          source
        ));
        pieceStart = pieceEnd;
      }
    }

    TextPieces build () {
      // Pieces of one block go together, so its clip is set once, each in the order of sources, so
      // that pieces that overlap are drawn over each other as before the change
      final ArrayList<QuoteBackground> blocks = new ArrayList<>();
      final int[] groups = new int[pieces.size()];
      QuoteBackground lastBlock = null;
      int lastGroup = 0;
      for (int i = 0; i < pieces.size(); i++) {
        final QuoteBackground block = pieces.get(i).block;
        if (block != lastBlock) {
          lastBlock = block;
          lastGroup = block != null ? blocks.indexOf(block) + 1 : 0;
          if (lastGroup == 0 && block != null) {
            blocks.add(block);
            lastGroup = blocks.size();
          }
        }
        groups[i] = lastGroup;
      }
      final int[] groupStarts = new int[blocks.size() + 3];
      for (int group : groups) {
        groupStarts[group + 2]++;
      }
      for (int i = 2; i < groupStarts.length; i++) {
        groupStarts[i] += groupStarts[i - 1];
      }
      final Piece[] result = new Piece[pieces.size()];
      for (int i = 0; i < groups.length; i++) {
        result[groupStarts[groups[i] + 1]++] = pieces.get(i);
      }
      for (int group = 0; group <= blocks.size(); group++) {
        final int start = group == 0 ? 0 : groupStarts[group];
        Arrays.sort(result, start, groupStarts[group + 1], BY_SOURCE);
      }
      return new TextPieces(text.getText(), result);
    }
  }

  private static final class PartAdvances {
    // One measure of the whole part places all its pieces
    private @Nullable TextPart part;
    private float[] widths = new float[0];
    private float[] offsets = new float[1];

    float offset (Text text, TextPart part, int index) {
      if (this.part != part) {
        measure(text, part);
      }
      return offsets[index - part.getStart()];
    }

    private void measure (Text text, TextPart part) {
      this.part = part;
      final int length = part.getEnd() - part.getStart();
      if (widths.length < length) {
        widths = new float[length];
        offsets = new float[length + 1];
      }
      final TextPaint paint = text.getTextPaint(part.getEntity());
      // Code of a block with tokens has its advances already
      final SyntaxLines lines =
        part.getLine() == text.getText() ? text.findCodeLinesAt(part.getStart()) : null;
      if (lines == null || !lines.copyAdvances(paint, part.getStart(), part.getEnd(), widths)) {
        // Paint copies all of a String to measure a part of it
        final char[] chars = text.getDrawnChars(part.getLine());
        if (chars != null) {
          paint.getTextWidths(chars, part.getStart(), length, widths);
        } else {
          paint.getTextWidths(part.getLine(), part.getStart(), part.getEnd(), widths);
        }
      }
      for (int i = 0; i < length; i++) {
        offsets[i + 1] = offsets[i] + widths[i];
      }
    }
  }
}
