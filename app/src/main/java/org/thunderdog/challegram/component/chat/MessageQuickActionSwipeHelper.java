package org.thunderdog.challegram.component.chat;

import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

public class MessageQuickActionSwipeHelper {
  public static final float SWIPE_BUBBLE_MOVE_MAX = 64f;
  public static final float SWIPE_THRESHOLD_WIDTH = 124f;
  public static final float SWIPE_VERTICAL_HEIGHT = 80f;
  public static final float SWIPE_VERTICAL_LOCK_WIDTH = Screen.px(Screen.smallestSide() * 0.75f);

  private final TGMessage message;
  private final boolean useBubbles;

  private float currentDx;
  private float currentDy;
  private float actualDx;
  private float actualDy;

  private boolean lockedBecauseSmallDy;
  private boolean lockedBecauseNotReadyInBubbleMode;
  private boolean lockedBecauseSwipeStarted;

  private float moveFactor;
  private int quickActionNumber;

  public MessageQuickActionSwipeHelper (TGMessage message) {
    this.message = message;
    this.useBubbles = message.useBubbles();
  }

  public void translate (float dx, float dy, boolean bySwipe) {
    final boolean lockedVerticalSwipe = isLockedVerticalSwipe();
    final boolean isLeft = dx > 0;
    final float ddx = dx - currentDx;
    final float ddy = lockedVerticalSwipe ? 0f : dy - currentDy;
    currentDx = dx; actualDx += ddx;
    currentDy = dy; actualDy += ddy;
    actualDy = clampActualDy(isLeft, actualDy);

    if (dx != 0f) {
      lockedBecauseNotReadyInBubbleMode &= useBubbles && Math.abs(actualDx) < Screen.dp(SWIPE_BUBBLE_MOVE_MAX);
      lockedBecauseSmallDy &= Math.abs(currentDy) < Screen.dp(SWIPE_VERTICAL_HEIGHT) / 2f;
      moveFactor = Math.min(1f, Math.abs(dx) / Screen.dp(SWIPE_THRESHOLD_WIDTH));
    } else {
      reset();
    }



    final float verticalPosition = actualDy / Screen.dp(SWIPE_VERTICAL_HEIGHT);
    updateVerticalPosition(verticalPosition);

    message.translate(dx, verticalPosition, true);
    message.invalidate(true);
  }

  public void onBeforeSwipe () {
    lockedBecauseSwipeStarted = true;
  }

  public void reset () {
    currentDx = 0f;
    currentDy = 0f;
    actualDx = 0f;
    actualDy = 0f;
    lockedBecauseSmallDy = true;
    lockedBecauseNotReadyInBubbleMode = useBubbles;
    lockedBecauseSwipeStarted = false;
    moveFactor = 0f;
    quickActionNumber = 0;
  }

  public float getMoveFactor () {
    return moveFactor;
  }

  public TGMessage.SwipeQuickAction getChosenQuickAction () {
    final boolean isLeft = actualDx > 0;
    final int realActionQuickNumber = quickActionNumber + message.getQuickDefaultPosition(isLeft);

    return message.getQuickAction(isLeft, realActionQuickNumber);
  }

  private void updateVerticalPosition (float verticalPosition) {
    final int newQuickActionNumber = Math.round(verticalPosition);
    if (quickActionNumber != newQuickActionNumber) {
      quickActionNumber = newQuickActionNumber;

    }
  }

  private float clampActualDy (boolean isLeft, float dy) {
    final int minPosition = -message.getQuickDefaultPosition(isLeft);
    final int maxPosition = minPosition + Math.max(message.getQuickActionsCount(isLeft) - 1, 0);
    return MathUtils.clamp(dy, minPosition * Screen.dp(SWIPE_VERTICAL_HEIGHT), maxPosition * Screen.dp(SWIPE_VERTICAL_HEIGHT));
  }

  private boolean isLockedVerticalSwipe () {
    boolean lockedBecauseReadyInFlatMode = !useBubbles && Math.abs(actualDx) > Screen.dp(SWIPE_VERTICAL_LOCK_WIDTH);
    return lockedBecauseNotReadyInBubbleMode || lockedBecauseReadyInFlatMode || lockedBecauseSmallDy || lockedBecauseSwipeStarted;
  }
}
