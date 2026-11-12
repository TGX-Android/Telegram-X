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

import me.vkryl.core.collection.IntList;

/**
 * Code blocks that a user switched from scrolling to wrapped lines or back
 */
public final class CodeBlockSwitches {
  public interface Listener {
    // Before the size of the text changes; isReverse when it switches back during its animation
    default void onCodeBlockSwitchStart (Text text, boolean isReverse) { }

    void onTextSizeChanged (Text text);

    // The text was destroyed during a switch, which then has no last frame
    default void onCodeBlockSwitchStopped (Text text) { }

    default boolean findVisiblePart (Text text, boolean atEnd, int[] range) {
      // Top and bottom of the visible part from the top of the view of the text, where a switch
      // starts, or where it ends when atEnd
      return false;
    }

    default int getListHeight (Text text) {
      // Without the keyboard, 0 when there's no list
      return 0;
    }
  }

  private final Listener listener;
  // The state chosen, not an inversion of the default, so that a later default never turns it
  private final IntList blockIds = new IntList(0);
  private final IntList wrappedStates = new IntList(0);
  // Changes with each switch, so that texts of a layout before it get a new layout
  private int version;

  public CodeBlockSwitches (Listener listener) {
    this.listener = listener;
  }

  boolean isChosen (int blockId) {
    return blockIds.contains(blockId);
  }

  boolean isChosenWrapped (int blockId) {
    final int index = blockIds.indexOf(blockId);
    return index != -1 && wrappedStates.get(index) == 1;
  }

  void choose (int blockId, boolean isWrapped) {
    final int index = blockIds.indexOf(blockId);
    if (index == -1) {
      blockIds.append(blockId);
      wrappedStates.append(isWrapped ? 1 : 0);
    } else {
      wrappedStates.set(index, isWrapped ? 1 : 0);
    }
    version++;
  }

  int getVersion () {
    return version;
  }

  void notifyCodeBlockSwitchStart (Text text, boolean isReverse) {
    listener.onCodeBlockSwitchStart(text, isReverse);
  }

  void notifyTextSizeChanged (Text text) {
    listener.onTextSizeChanged(text);
  }

  void notifySwitchStopped (Text text) {
    listener.onCodeBlockSwitchStopped(text);
  }

  boolean findVisiblePart (Text text, boolean atEnd, int[] range) {
    return listener.findVisiblePart(text, atEnd, range);
  }

  int getListHeight (Text text) {
    return listener.getListHeight(text);
  }
}
