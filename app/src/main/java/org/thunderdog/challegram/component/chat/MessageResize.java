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

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;

import java.util.ArrayList;

import me.vkryl.core.MathUtils;

/**
 * Moves the list while a message changes its height by an animation, to the position by
 * MessageResizeRules
 */
public final class MessageResize {
  private static final String LOG_TAG = "MessageResize";

  private static final class Move {
    private final TGMessage message;
    // originTop is where a move back returns to
    private int startTop, finalTop, originTop;
    // Kept after the move for a change back
    private int height, finalHeight;
    private boolean isFinished;
    // A change kept while the chat was closed, whose finalTop is the top at the next change
    private boolean isRestored;
    private float factor;
    private boolean isFinalFrameLogged;
    // The draw of the list before which a frame last set the position of the list by the message,
    // -1 before the first one
    private long placedDraw = -1;

    private Move (TGMessage message) {
      this.message = message;
    }
  }

  public interface Lines {
    // Tops of the lines under visibleTop and visibleBottom from the top of the view, before and
    // after the change, MessageResizeRules.NO_LINE without lines
    int[] find (int visibleTop, int visibleBottom);
  }

  // The list that moves place messages in, of MessagesManager
  interface Messages {
    // Integer.MIN_VALUE when the message isn't on the list
    int getMessageTop (TGMessage message);
    // Whether the next layout places the message there
    boolean setMessageTop (TGMessage message, int top);
    boolean measureResize (TGMessage message, int finalHeight, MessageResizeRules rules);
    boolean findVisiblePart (TGMessage message, int[] range);
    ViewTreeObserver getViewTreeObserver ();
  }

  private final Messages manager;
  private final MessageResizeRules rules = new MessageResizeRules();
  private final ArrayList<Move> moves = new ArrayList<>();
  private boolean isListDragged;
  // A scroll of the list to a position in code, e.g. to its end for a new message, waits for its
  // layout, which places the messages, and moves go on from there
  private @Nullable ViewTreeObserver scrollObserver;
  private final ViewTreeObserver.OnGlobalLayoutListener scrollLayout = this::onScrollLayout;
  // Draws of the window of the list, so that a frame of a move and a scroll in the same frame see
  // the same count on every version of Android, unlike the time of animations, which is the time
  // of the frame only from Android 7
  private long listDraws;
  private final ViewTreeObserver.OnDrawListener drawListener = this::onListDrawn;

  MessageResize (Messages manager) {
    this.manager = manager;
  }

  private final View.OnAttachStateChangeListener attachListener =
    new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View view) {
        view.getViewTreeObserver().addOnDrawListener(drawListener);
      }

      @Override
      public void onViewDetachedFromWindow (View view) {
        view.getViewTreeObserver().removeOnDrawListener(drawListener);
      }
    };

  void setList (View list) {
    // Draws are counted while the list is in a window, by the observer of that window
    list.removeOnAttachStateChangeListener(attachListener);
    list.addOnAttachStateChangeListener(attachListener);
    if (list.getWindowToken() != null) {
      final ViewTreeObserver observer = list.getViewTreeObserver();
      observer.removeOnDrawListener(drawListener);
      observer.addOnDrawListener(drawListener);
    }
  }

  void onListDrawn () {
    listDraws++;
  }

  public void setListDragged (boolean isDragged) {
    // No message is moved from a drag to the end of its fling, so that the list follows the finger
    this.isListDragged = isDragged;
    if (isDragged) {
      moves.clear();
    }
  }

  public void start (
    TGMessage message,
    int finalHeight,
    boolean isReverse,
    int[] block,
    Lines lines
  ) {
    // block: top and bottom of the changing part in the view, then 1 when it starts the bubble and
    // 1 when it ends it; isReverse when it changes back during the animation of its last change
    if (isListDragged) {
      return;
    }
    final int top = manager.getMessageTop(message);
    Move move = findMove(message);
    if (move != null && move.isRestored) {
      move.originTop += top - move.finalTop;
      move.finalTop = top;
      move.isRestored = false;
    }
    if (isReverse && move != null && !move.isFinished) {
      // Back to where the change began, as far as the end of the list allows: a scroll in code
      // during the change, e.g. to a new message at the end, moved that place with the list
      final int originTop = move.finalTop;
      move.finalTop = move.originTop;
      move.originTop = originTop;
      move.startTop = top;
      final int height = move.finalHeight;
      move.finalHeight = move.height;
      move.height = height;
      if (manager.measureResize(message, finalHeight, rules)) {
        move.finalTop = rules.withinEnd(move.finalTop);
        if (Config.ENABLE_TEST_HOOKS) {
          Log.i(LOG_TAG, "start " + message.getId() + " top " + top + " height " + rules.height +
            " final height " + finalHeight + " final top " + move.finalTop + " list " +
            rules.listTop + " " + rules.visibleBottom + " end " + rules.endBottom + " below " +
            rules.belowHeight + " reverse");
        }
      }
      return;
    }
    // Only the last change of the last changed message is kept for a change back
    removeFinishedMoves(move);
    if (!manager.measureResize(message, finalHeight, rules)) {
      if (move != null) {
        moves.remove(move);
      }
      return;
    }
    final int[] line = lines.find(rules.listTop - rules.top, rules.visibleBottom - rules.top);
    rules.firstLineBefore = line[0];
    rules.firstLineAfter = line[1];
    rules.lastLineBefore = line[2];
    rules.lastLineAfter = line[3];
    rules.blockTop = block[0];
    rules.blockBottom = block[1];
    rules.blockStartsBubble = block[2] == 1;
    rules.blockEndsBubble = block[3] == 1;
    rules.previousFinalTop = Integer.MIN_VALUE;
    if (move != null && move.isFinished) {
      rules.previousTop = move.originTop;
      rules.previousFinalTop = move.finalTop;
      rules.previousHeight = move.height;
      rules.previousFinalHeight = move.finalHeight;
    }
    if (move == null) {
      move = new Move(message);
      moves.add(move);
    }
    move.isFinished = false;
    move.isFinalFrameLogged = false;
    move.placedDraw = -1;
    move.height = rules.height;
    move.finalHeight = finalHeight;
    move.startTop = move.originTop = top;
    move.finalTop = rules.finalTop();
    if (Config.ENABLE_TEST_HOOKS) {
      Log.i(LOG_TAG, "start " + message.getId() + " top " + top + " height " + rules.height +
        " final height " + finalHeight + " final top " + move.finalTop + " list " + rules.listTop +
        " " + rules.visibleBottom + " end " + rules.endBottom + " below " + rules.belowHeight +
        " block " + rules.blockTop + " " + rules.blockBottom + " " + rules.blockStartsBubble + " " +
        rules.blockEndsBubble + " spaces " + rules.spaceAbove + " " + rules.spaceBelow +
        " lines " + rules.firstLineBefore + " " + rules.firstLineAfter + " " +
        rules.lastLineBefore + " " + rules.lastLineAfter);
    }
  }

  public boolean findVisiblePart (TGMessage message, boolean atEnd, int[] range) {
    if (!manager.findVisiblePart(message, range)) {
      return false;
    }
    if (atEnd) {
      final Move move = findMove(message);
      if (move == null || move.isFinished) {
        return false;
      }
      final int shift = manager.getMessageTop(message) - move.finalTop;
      range[0] += shift;
      range[1] += shift;
    }
    return true;
  }

  public boolean isMoving (TGMessage message) {
    final Move move = findMove(message);
    return move != null && !move.isFinished;
  }

  public void onFrame (TGMessage message, float factor) {
    // A message that doesn't move keeps its top, e.g. a text with spoilers, which changes at once
    if (isListDragged) {
      return;
    }
    final Move move = findMove(message);
    if (move == null || move.isFinished) {
      if (scrollObserver == null) {
        manager.setMessageTop(message, manager.getMessageTop(message));
      }
      return;
    }
    move.factor = factor;
    move.isFinished = factor == 1f && scrollObserver == null;
    if (scrollObserver == null && !place(move)) {
      // The message left the list, e.g. a scroll by a distance took it away, or it was deleted
      moves.remove(move);
    }
  }

  private boolean place (Move move) {
    if (manager.setMessageTop(move.message,
      MathUtils.fromTo(move.startTop, move.finalTop, move.factor))) {
      move.placedDraw = listDraws;
      return true;
    }
    return false;
  }

  private boolean isPlacedInThisFrame (Move move) {
    // Set by a frame of the move, which the next layout applies before the list is drawn
    return move.placedDraw == listDraws;
  }

  public void onListScrolled () {
    if (scrollObserver == null) {
      scrollObserver = manager.getViewTreeObserver();
      scrollObserver.addOnGlobalLayoutListener(scrollLayout);
    }
  }

  public void onListScrolled (int dy) {
    // A scroll by a distance moves the list at once, e.g. a smooth scroll in code. A frame of a
    // move earlier in this frame, also its last one, set the position of the list, which the next
    // layout would apply over the scroll, so it's set again with the scroll
    for (int i = moves.size() - 1; i >= 0; i--) {
      final Move move = moves.get(i);
      move.startTop -= dy;
      move.finalTop -= dy;
      move.originTop -= dy;
      final boolean isMoving = !move.isFinished || isPlacedInThisFrame(move);
      if (isMoving && scrollObserver == null && !place(move)) {
        // The scroll took the message off the list
        moves.remove(i);
      }
    }
  }

  public boolean isPlacing () {
    // Frames of a move set the position of the list by the moving message, which keeps the
    // messages above it in place when another one changes its height: while it moves, also
    // before its first frame that places it, e.g. after a scroll in code that it waited for, and
    // after its last frame until the list is drawn
    if (isListDragged || scrollObserver != null) {
      return false;
    }
    for (int i = 0; i < moves.size(); i++) {
      final Move move = moves.get(i);
      if (!move.isFinished || isPlacedInThisFrame(move)) {
        return true;
      }
    }
    return false;
  }

  void onScrollLayout () {
    // From the observer it was added to, or from the one of the window, which took its listeners
    // when the list was attached
    ViewTreeObserver observer = scrollObserver;
    if (observer == null || !observer.isAlive()) {
      observer = manager.getViewTreeObserver();
    }
    if (observer.isAlive()) {
      observer.removeOnGlobalLayoutListener(scrollLayout);
    }
    scrollObserver = null;
    for (int i = moves.size() - 1; i >= 0; i--) {
      final Move move = moves.get(i);
      if (move.isRestored) {
        continue;
      }
      final int top = manager.getMessageTop(move.message);
      if (top == Integer.MIN_VALUE) {
        // The scroll took the message off the list
        moves.remove(i);
        continue;
      }
      // A move kept for a switch back moves with the list too, as on a scroll by a distance
      final int shift = top - (move.isFinished ? move.finalTop :
        MathUtils.fromTo(move.startTop, move.finalTop, move.factor));
      move.startTop += shift;
      move.finalTop += shift;
      move.originTop += shift;
      if (!move.isFinished) {
        move.isFinished = move.factor == 1f;
      }
    }
  }

  public void logFrame (TGMessage message) {
    // Builds of tests: checks read edges of the bubble of a moving message from the log
    final Move move = findMove(message);
    if (move == null || move.isFinalFrameLogged) {
      return;
    }
    final int top = manager.getMessageTop(message);
    final boolean useBubbles = message.useBubbles();
    final int bubbleTop = top + (useBubbles ? message.getTopContentEdge() : 0);
    final int bottomEdge = useBubbles ? message.getBottomContentEdge() : message.getHeight();
    final int bubbleBottom = top + bottomEdge;
    Log.i(LOG_TAG, "frame " + message.getId() + " " + bubbleTop + " " + bubbleBottom + " view " +
      top + " " + message.getHeight() + " factor " + move.factor +
      (move.isFinished ? " final" : ""));
    move.isFinalFrameLogged = move.isFinished;
  }

  public void stop (TGMessage message) {
    // Stopped before its last frame, e.g. its text was replaced
    final Move move = findMove(message);
    if (move != null && !move.isFinished) {
      moves.remove(move);
    }
  }

  public void forgetChanges () {
    removeFinishedMoves(null);
  }

  public @Nullable int[] getKeptChange (TGMessage message) {
    // The change kept for a change back, as Settings.MessageState keeps it
    final Move move = findMove(message);
    if (move == null) {
      return null;
    }
    return new int[] {move.originTop - move.finalTop, move.height, move.finalHeight};
  }

  public void restoreChange (TGMessage message, int[] change) {
    removeFinishedMoves(null);
    Move move = findMove(message);
    if (move == null) {
      move = new Move(message);
      moves.add(move);
    }
    move.isFinished = move.isRestored = true;
    move.finalTop = 0;
    move.originTop = change[0];
    move.height = change[1];
    move.finalHeight = change[2];
  }

  private void removeFinishedMoves (@Nullable Move kept) {
    for (int i = moves.size() - 1; i >= 0; i--) {
      if (moves.get(i) != kept && moves.get(i).isFinished) {
        moves.remove(i);
      }
    }
  }

  private Move findMove (TGMessage message) {
    for (int i = 0; i < moves.size(); i++) {
      if (moves.get(i).message == message) {
        return moves.get(i);
      }
    }
    return null;
  }
}
