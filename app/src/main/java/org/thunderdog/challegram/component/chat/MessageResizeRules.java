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
 * File created on 28/10/2026
 */
package org.thunderdog.challegram.component.chat;

/**
 * Where a message stands in the list at the end of an animated change of its height, in pixels
 * from the top of the list, so that the rules are checked without the list
 */
public final class MessageResizeRules {
  // Visible part of the list, and where the last message ends when the list is at its end
  public int listTop, visibleBottom, endBottom;
  public int top, height, finalHeight;
  // Height of the messages below it, -1 when it isn't known
  public int belowHeight;
  public int aboveHeight = -1;
  // Of the view above and below the bubble
  public int spaceAbove, spaceBelow;
  // Edges of the block that changes from the top of the view, and whether it starts or ends the
  // bubble, whose edges with their paddings are then its edges
  public int blockTop, blockBottom;
  public boolean blockStartsBubble, blockEndsBubble;
  // Lines under the first and the last visible pixel from the top of the view, before and after
  // the change
  public static final int NO_LINE = Integer.MIN_VALUE;
  public int firstLineBefore = NO_LINE, firstLineAfter = NO_LINE;
  public int lastLineBefore = NO_LINE, lastLineAfter = NO_LINE;
  // The last change of the message, previousFinalTop Integer.MIN_VALUE without one
  public int previousTop, previousHeight, previousFinalHeight;
  public int previousFinalTop = Integer.MIN_VALUE;

  public int finalTop () {
    // What changes beyond the visible line at an edge goes out of sight, a block seen whole grows
    // by the free space above and below it, and a change back returns to where the last one began.
    // The end of the list leaves no space below it
    final boolean isChangeBack =
      top == previousFinalTop && height == previousFinalHeight && finalHeight == previousHeight;
    return withinEnd(isChangeBack ? previousTop : changedTop());
  }

  public int withinEnd (int finalTop) {
    // No space above the first message, then none below the last: a short list is at its end
    if (aboveHeight != -1) {
      finalTop -= Math.max(0, finalTop - aboveHeight - listTop);
    }
    if (belowHeight == -1) {
      return finalTop;
    }
    return finalTop + Math.max(0, endBottom - (finalTop + finalHeight + belowHeight));
  }

  private int changedTop () {
    final int ownTop = top + blockTop;
    final int ownBottom = top + blockBottom;
    final int edgeTop = blockStartsBubble ? top + spaceAbove : ownTop;
    final int edgeBottom = blockEndsBubble ? top + height - spaceBelow : ownBottom;
    int finalTop;
    if (ownTop < listTop) {
      final boolean hasLine = firstLineBefore != NO_LINE && firstLineAfter != NO_LINE;
      finalTop = hasLine ? top - (firstLineAfter - firstLineBefore) : top;
    } else if (ownBottom > visibleBottom) {
      final boolean hasLine = lastLineBefore != NO_LINE && lastLineAfter != NO_LINE;
      final int growth = hasLine ? lastLineAfter - lastLineBefore : finalHeight - height;
      finalTop = top - Math.min(growth, Math.max(0, edgeTop - listTop));
    } else {
      final int above = Math.max(0, edgeTop - listTop);
      final int below = Math.max(0, visibleBottom - edgeBottom);
      final int change = finalHeight - height;
      if (change >= above + below) {
        finalTop = top - above;
      } else if (above + below == 0) {
        finalTop = top;
      } else {
        finalTop = top - Math.round((float) change * above / (above + below));
      }
    }
    return finalTop;
  }
}
