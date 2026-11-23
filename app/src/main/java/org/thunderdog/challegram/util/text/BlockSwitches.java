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

import org.thunderdog.challegram.component.chat.StickyElements;
import org.thunderdog.challegram.config.Config;

import me.vkryl.core.collection.IntList;

/**
 * Blocks that a user switched on or off: code blocks to wrapped lines or back to scrolling ones,
 * expandable quotes to expanded or back to collapsed
 */
public final class BlockSwitches {
  public interface Listener {
    // Before the size of the text changes; isReverse when it switches back during its animation
    default void onBlockSwitchStart (Text text, boolean isReverse) { }

    void onTextSizeChanged (Text text);

    // The text was destroyed during a switch, which then has no last frame
    default void onBlockSwitchStopped (Text text) { }

    default boolean findVisiblePart (Text text, boolean atEnd, int[] range) {
      // Top and bottom of the visible part from the top of the view of the text, where a switch
      // starts, or where it ends when atEnd
      return false;
    }

    default int getListHeight (Text text) {
      // Without the keyboard, 0 when there's no list
      return 0;
    }

    default float placeStickyElement (
      Text text,
      StickyElements.Element element,
      float x,
      float minY,
      float maxY,
      float padding,
      boolean atTop
    ) {
      // Where the text draws it, or NaN when the list draws it
      return atTop ? minY : maxY;
    }
  }

  public static final int CODE_WRAPPING_AUTO = 0;
  public static final int CODE_WRAPPING_ENABLED = 1;
  public static final int CODE_WRAPPING_DISABLED = 2;

  private final Listener listener;
  // The state chosen, not an inversion of the default, so that a later default never turns it
  private final IntList blockIds = new IntList(0);
  private final IntList onStates = new IntList(0);
  // Changes with each switch, so that texts of a layout before it get a new layout
  private int version;
  private int codeWrapping = CODE_WRAPPING_AUTO;
  private int collapsedQuoteLineCount = Config.EXPANDABLE_QUOTE_COLLAPSED_LINE_COUNT;

  public BlockSwitches (Listener listener) {
    this.listener = listener;
  }

  public BlockSwitches setCodeWrapping (int codeWrapping) {
    if (this.codeWrapping != codeWrapping) {
      this.codeWrapping = codeWrapping;
      version++;
    }
    return this;
  }

  int getCodeWrapping () {
    return codeWrapping;
  }

  public BlockSwitches setCollapsedQuoteLineCount (int lineCount) {
    if (this.collapsedQuoteLineCount != lineCount) {
      this.collapsedQuoteLineCount = lineCount;
      version++;
    }
    return this;
  }

  int getCollapsedQuoteLineCount () {
    return collapsedQuoteLineCount;
  }

  boolean isChosen (int blockId) {
    return blockIds.contains(blockId);
  }

  boolean isChosenOn (int blockId) {
    final int index = blockIds.indexOf(blockId);
    return index != -1 && onStates.get(index) == 1;
  }

  public void choose (int blockId, boolean isOn) {
    final int index = blockIds.indexOf(blockId);
    if (index == -1) {
      blockIds.append(blockId);
      onStates.append(isOn ? 1 : 0);
    } else {
      onStates.set(index, isOn ? 1 : 0);
    }
    version++;
  }

  int getVersion () {
    return version;
  }

  void notifyBlockSwitchStart (Text text, boolean isReverse) {
    listener.onBlockSwitchStart(text, isReverse);
  }

  void notifyTextSizeChanged (Text text) {
    listener.onTextSizeChanged(text);
  }

  void notifySwitchStopped (Text text) {
    listener.onBlockSwitchStopped(text);
  }

  boolean findVisiblePart (Text text, boolean atEnd, int[] range) {
    return listener.findVisiblePart(text, atEnd, range);
  }

  int getListHeight (Text text) {
    return listener.getListHeight(text);
  }

  float placeStickyElement (
    Text text,
    StickyElements.Element element,
    float x,
    float minY,
    float maxY,
    float padding,
    boolean atTop
  ) {
    return listener.placeStickyElement(text, element, x, minY, maxY, padding, atTop);
  }
}
