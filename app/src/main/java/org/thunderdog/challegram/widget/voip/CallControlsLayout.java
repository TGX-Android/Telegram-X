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
 * File created on 06/05/2017
 */
package org.thunderdog.challegram.widget.voip;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.preview.FlingDetector;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.widget.CircleButton;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class CallControlsLayout extends FrameLayoutFix implements View.OnClickListener, FactorAnimator.Target, FlingDetector.Callback {
  private static final float CALL_BUTTON_PADDING = 4f;
  private static final float CALL_BUTTON_SIZE = 70.5f;
  private static final float CALL_BUTTON_MARGIN = 24f;
  private static final float CALL_BUTTON_BOTTOM_MARGIN = 70.5f;

  public interface CallControlCallback {
    void onCallAccept (TdApi.Call call);
    void onCallDecline (TdApi.Call call, boolean isHangUp);
    void onCallRestart (TdApi.Call call);
    void onCallClose (TdApi.Call call);
  }

  /*
  *
  * Basically, there're three states:
  *
  * INCOMING CALL
  * - Accept
  * - Decline
  * - Cancelled by caller
  *
  * OUTGOING CALL
  * - Cancel
  * - Accept by callee
  * - Decline by callee
  *
  * ONGOING CALL
  * - Hang up by callee
  * - Hang up
  *
  *
  * */

  private SlideHintView slideHintView;
  private CircleButton acceptButton;
  private CircleButton declineButton;
  private CircleButton closeButton;
  private FlingDetector flingDetector;

  private boolean inSlideMode;
  private @Nullable CallControlCallback callback;
  private final CallController parent;

  public CallControlsLayout (@NonNull Context context, CallController parent) {
    super(context);

    this.parent = parent;

    int buttonPadding = Screen.dp(CALL_BUTTON_PADDING);
    int buttonSize = Screen.dp(CALL_BUTTON_SIZE);
    int buttonMargin = Screen.dp(CALL_BUTTON_MARGIN);
    int buttonBottomMargin = Screen.dp(CALL_BUTTON_BOTTOM_MARGIN);

    flingDetector = new FlingDetector(context, this);

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, buttonSize, Gravity.BOTTOM);
    params.leftMargin = params.rightMargin = buttonSize + buttonMargin;
    params.bottomMargin = buttonBottomMargin;

    slideHintView = new SlideHintView(context);
    slideHintView.setLayoutParams(params);
    addView(slideHintView);

    params = FrameLayoutFix.newParams(buttonSize + buttonPadding * 2, buttonSize + buttonPadding * 2, Gravity.BOTTOM | Gravity.LEFT);
    params.leftMargin = buttonMargin;
    params.bottomMargin = buttonBottomMargin;

    acceptButton = new CircleButton(context);
    acceptButton.setId(R.id.btn_acceptOrHangCall);
    acceptButton.init(R.drawable.baseline_phone_36, CALL_BUTTON_SIZE, CALL_BUTTON_PADDING, R.id.theme_color_circleButtonPositive, R.id.theme_color_circleButtonPositiveIcon);
    acceptButton.setLayoutParams(params);
    acceptButton.setOnClickListener(this);
    addView(acceptButton);

    params = FrameLayoutFix.newParams(buttonSize + buttonPadding * 2, buttonSize + buttonPadding * 2, Gravity.BOTTOM | Gravity.LEFT);
    params.leftMargin = buttonMargin;
    params.bottomMargin = buttonBottomMargin;

    closeButton = new CircleButton(context);
    closeButton.setId(R.id.btn_closeCall);
    closeButton.init(R.drawable.baseline_close_36, CALL_BUTTON_SIZE, CALL_BUTTON_PADDING, R.id.theme_color_circleButtonOverlay, R.id.theme_color_circleButtonOverlayIcon);
    closeButton.setLayoutParams(params);
    closeButton.setIsHidden(true, false);
    closeButton.setOnClickListener(this);
    addView(closeButton);

    params = FrameLayoutFix.newParams(buttonSize + buttonPadding * 2, buttonSize + buttonPadding * 2, Gravity.BOTTOM | Gravity.RIGHT);
    params.rightMargin = buttonMargin;
    params.bottomMargin = buttonBottomMargin;

    declineButton = new CircleButton(context);
    declineButton.setId(R.id.btn_declineCall);
    declineButton.init(R.drawable.baseline_phone_36, CALL_BUTTON_SIZE, CALL_BUTTON_PADDING, R.id.theme_color_circleButtonNegative, R.id.theme_color_circleButtonNegativeIcon);
    declineButton.setIconRotation(135f, false);
    declineButton.setLayoutParams(params);
    declineButton.setOnClickListener(this);
    addView(declineButton);
  }

  public void setCallback (@Nullable CallControlCallback callback) {
    this.callback = callback;
  }

  private Tdlib tdlib;
  private TdApi.Call call;

  public void setCall (final Tdlib tdlib, final TdApi.Call call, final boolean animated) {
    if (this.call != null && this.call.state == call.state) {
      return;
    }

    this.tdlib = tdlib;
    this.call = call;
    this.inSlideMode = false;

    final float declineFactor;
    final float acceptFactor;
    final boolean acceptVisible;
    final boolean declineVisible;
    final boolean closeVisible;

    switch (call.state.getConstructor()) {
      case TdApi.CallStatePending.CONSTRUCTOR: {
        closeVisible = false;
        if (call.isOutgoing) {
          acceptFactor = 1f;
          declineFactor = 0f;
          acceptVisible = true;
          declineVisible = false;
        } else {
          acceptFactor = declineFactor = 0f;
          acceptVisible = declineVisible = true;
          this.inSlideMode = true;
        }
        break;
      }
      case TdApi.CallStateExchangingKeys.CONSTRUCTOR:
      case TdApi.CallStateReady.CONSTRUCTOR: {
        closeVisible = false;
        acceptFactor = 1f;
        declineFactor = 0f;
        acceptVisible = true;
        declineVisible = false;
        break;
      }
      case TdApi.CallStateDiscarded.CONSTRUCTOR: {
        switch (((TdApi.CallStateDiscarded) call.state).reason.getConstructor()) {
          case TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR:
            acceptFactor = 1f;
            acceptVisible = true;
            declineFactor = 0f;
            declineVisible = false;
            closeVisible = true;
            break;
          case TdApi.CallDiscardReasonHungUp.CONSTRUCTOR:
            acceptFactor = 1f;
            acceptVisible = true;
            declineFactor = 0f;
            declineVisible = false;
            closeVisible = true;
            break;
          default: {
            if (call.isOutgoing) {
              acceptFactor = 1f;
              acceptVisible = true;
              declineFactor = 0f;
              declineVisible = false;
              closeVisible = true;
            } else {
              closeVisible = false;
              declineFactor = 1f;
              declineVisible = true;
              acceptFactor = 0f;
              acceptVisible = false;
            }
            break;
          }
        }
        break;
      }
      case TdApi.CallStateError.CONSTRUCTOR: {
        closeVisible = true;
        acceptFactor = 1f;
        acceptVisible = true;
        declineFactor = 0f;
        declineVisible = false;
        break;
      }
      default:
        throw new IllegalArgumentException("call.state == " + call.state);
    }

    if (slideMode != SLIDE_MODE_NONE) {
      slideMode = SLIDE_MODE_NONE;

      getParent().requestDisallowInterceptTouchEvent(false);

      declineButton.setIsDragging(false);
      acceptButton.setIsDragging(false);
    }

    declineButton.setIsHidden(!declineVisible, animated);
    acceptButton.setIsHidden(!acceptVisible, animated);

    setCloseVisible(closeVisible, animated);

    setTransform(acceptFactor, SLIDE_MODE_ACCEPT - 1, animated);
    setPosition(acceptFactor, SLIDE_MODE_ACCEPT - 1, animated);
    setTransform(declineFactor, SLIDE_MODE_DECLINE - 1, animated);
    setPosition(declineFactor, SLIDE_MODE_DECLINE - 1, animated);
  }

  @Override
  public void onClick (View v) {
    if (callback == null || call == null) {
      return;
    }
    switch (v.getId()) {
      case R.id.btn_acceptOrHangCall: {
        switch (call.state.getConstructor()) {
          case TdApi.CallStateDiscarded.CONSTRUCTOR:
          case TdApi.CallStateError.CONSTRUCTOR:
            callback.onCallRestart(call);
            break;
          case TdApi.CallStatePending.CONSTRUCTOR:
            if (call.isOutgoing) {
              callback.onCallDecline(call, false);
            } else {
              callback.onCallAccept(call);
            }
            break;
          default:
            callback.onCallDecline(call, true);
            break;
        }
        break;
      }
      case R.id.btn_declineCall: {
        callback.onCallDecline(call, false);
        break;
      }
      case R.id.btn_closeCall: {
        callback.onCallClose(call);
        break;
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case POSITION_ANIMATOR_ID + 1:
      case POSITION_ANIMATOR_ID:
        setPositionFactor(factor, id - POSITION_ANIMATOR_ID);
        break;
      case TRANSFORM_ANIMATOR_ID + 1:
      case TRANSFORM_ANIMATOR_ID:
        setTransformFactor(factor, id - TRANSFORM_ANIMATOR_ID);
        break;
      case CLOSE_ANIMATOR_ID:
        setCloseFactor(factor);
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return inSlideMode;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updatePositions();
  }

  private static final int SLIDE_MODE_NONE = 0;
  private static final int SLIDE_MODE_ACCEPT = 1;
  private static final int SLIDE_MODE_DECLINE = 2;

  private float startX;
  private int slideMode;

  private void setSlideMode (int mode, boolean animated) {
    setSlideMode(mode, false, SLIDE_MODE_NONE, animated);
  }

  private void setSlideMode (int mode, boolean allowApply, int forceSlideMode, boolean animated) {
    if (this.slideMode != mode) {
      int oldSlideMode = this.slideMode;
      this.slideMode = mode;
      getParent().requestDisallowInterceptTouchEvent(mode != SLIDE_MODE_NONE);
      declineButton.setIsDragging(mode == SLIDE_MODE_DECLINE);
      acceptButton.setIsDragging(mode == SLIDE_MODE_ACCEPT);

      boolean needReturnButtons = true;
      if (oldSlideMode != SLIDE_MODE_NONE) {
        if (allowApply && forceSlideMode != SLIDE_MODE_NONE) {
          setPosition(1f, forceSlideMode - 1, animated);
          setTransform(1f, forceSlideMode - 1, animated);
          if (callback != null && call != null) {
            if (forceSlideMode == SLIDE_MODE_ACCEPT) {
              callback.onCallAccept(call);
            } else if (forceSlideMode == SLIDE_MODE_DECLINE) {
              callback.onCallDecline(call, false);
            }
          }
          needReturnButtons = false;
        } else if (allowApply && (positions[oldSlideMode - 1] >= .75f || UI.inTestMode())) {
          setPosition(1f, oldSlideMode - 1, animated);
          setTransform(1f, oldSlideMode - 1, animated);
          if (callback != null && call != null) {
            if (oldSlideMode == SLIDE_MODE_ACCEPT) {
              callback.onCallAccept(call);
            } else if (oldSlideMode == SLIDE_MODE_DECLINE) {
              callback.onCallDecline(call, false);
            }
          }
          needReturnButtons = false;
        } else {
          setPosition(0f, oldSlideMode - 1, animated);
        }
      }
      if (needReturnButtons) {
        declineButton.setIsHidden(mode == SLIDE_MODE_ACCEPT, animated);
        acceptButton.setIsHidden(mode == SLIDE_MODE_DECLINE, animated);
      }
    }
  }

  private int getTargetAcceptX () {
    return getMeasuredWidth() / 2 - acceptButton.getMeasuredWidth() / 2 - ((MarginLayoutParams) acceptButton.getLayoutParams()).leftMargin;
  }

  private int getTargetDeclineX () {
    return -getMeasuredWidth() / 2 + declineButton.getMeasuredWidth() / 2 + ((MarginLayoutParams) declineButton.getLayoutParams()).rightMargin;
  }

  // Accept

  private void updatePositions () {
    closeButton.setTranslationX((float) getTargetAcceptX() * .5f);
    acceptButton.setTranslationX((float) getTargetAcceptX() * (positions[SLIDE_MODE_ACCEPT - 1] + .5f * closeFactor));
    declineButton.setTranslationX((float) getTargetDeclineX() * positions[SLIDE_MODE_DECLINE - 1]);
  }

  private float[] positions = new float[2];
  private FactorAnimator[] positionAnimators = new FactorAnimator[2];
  private static final int POSITION_ANIMATOR_ID = 100;

  // position == 1f -> allow button, -1f -> decline button
  private void setPosition (float toPosition, int who, boolean animated) {
    if (animated) {
      if (positionAnimators[who] == null) {
        positionAnimators[who] = new FactorAnimator(POSITION_ANIMATOR_ID + who, this, new OvershootInterpolator(2.2f), 280l, this.positions[who]);
      }
      positionAnimators[who].animateTo(toPosition);
    } else {
      if (positionAnimators[who] != null) {
        positionAnimators[who].forceFactor(toPosition);
      }
      setPositionFactor(toPosition, who);
    }
  }

  private void setPositionFactor (float factor, int who) {
    if (this.positions[who] != factor) {
      this.positions[who] = factor;
      updatePositions();
    }
  }

  private FactorAnimator[] transformAnimators = new FactorAnimator[2];
  private static final int TRANSFORM_ANIMATOR_ID = 200;
  private float[] transforms = new float[2];

  private void setTransform (float factor, int who, boolean animated) {
    if (animated) {
      if (transformAnimators[who] == null) {
        transformAnimators[who] = new FactorAnimator(TRANSFORM_ANIMATOR_ID + who, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.transforms[who]);
      }
      transformAnimators[who].animateTo(factor);
    } else {
      if (transformAnimators[who] != null) {
        transformAnimators[who].forceFactor(factor);
      }
      setTransformFactor(factor, who);
    }
  }

  private void updateAcceptTransform () {
    float factor = transforms[SLIDE_MODE_ACCEPT - 1] * (1f - closeFactor);
    acceptButton.setIconRotation(135f * factor, false);
    acceptButton.setFromToColor(R.id.theme_color_circleButtonPositive, R.id.theme_color_circleButtonNegative, factor);
  }

  private void setTransformFactor (float factor, int who) {
    if (transforms[who] != factor) {
      transforms[who] = factor;
      if (who == SLIDE_MODE_ACCEPT - 1) {
        updateAcceptTransform();
      } else if (who == SLIDE_MODE_DECLINE - 1) {
        // TODO? declineButton.setCrossFactor(factor);
      }
      slideHintView.setAlpha(MathUtils.clamp(1f - factor));
    }
  }

  private float closeFactor;
  private boolean closeVisible;
  private FactorAnimator closeAnimator;
  private static final int CLOSE_ANIMATOR_ID = 0;

  private void setCloseVisible (boolean isVisible, boolean animated) {
    if (this.closeVisible != isVisible) {
      this.closeVisible = isVisible;
      final float toFactor = isVisible ? 1f : 0f;
      if (animated) {
        if (closeAnimator == null) {
          closeAnimator = new FactorAnimator(CLOSE_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.closeFactor);
        }
        closeAnimator.animateTo(toFactor);
      } else {
        if (closeAnimator != null) {
          closeAnimator.forceFactor(toFactor);
        }
        setCloseFactor(toFactor);
      }
      closeButton.setIsHidden(!closeVisible, animated);
    }
  }

  private void setCloseFactor (float factor) {
    if (this.closeFactor != factor) {
      this.closeFactor = factor;
      updateAcceptTransform();
      // closeButton.setHideFactor(1f - factor);
      updatePositions();
    }
  }

  @Override
  public boolean onFling (float velocityX, float velocityY) {
    if (slideMode != SLIDE_MODE_NONE && Math.abs(velocityX) >= Screen.dp(150f)) {
      if ((velocityX > 0 && slideMode == SLIDE_MODE_ACCEPT) || (velocityX < 0 && slideMode == SLIDE_MODE_DECLINE)) {
        setSlideMode(SLIDE_MODE_NONE, true, slideMode, true);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final float x = e.getX();
    final float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        slideMode = SLIDE_MODE_NONE;
        startX = x;

        if (!inSlideMode) {
          return false;
        }

        if (x >= acceptButton.getLeft() && x <= acceptButton.getRight() && y >= acceptButton.getTop() && y <= acceptButton.getBottom()) {
          if (!tdlib.context().calls().checkRecordPermissions(getContext(), tdlib, call, call.userId, parent)) {
            return true;
          }
          setSlideMode(SLIDE_MODE_ACCEPT, true);
          flingDetector.onTouchEvent(e);
          return true;
        }

        if (x >= declineButton.getLeft() && x <= declineButton.getRight() && y >= declineButton.getTop() && y <= declineButton.getBottom()) {
          setSlideMode(SLIDE_MODE_DECLINE, true);
          flingDetector.onTouchEvent(e);
          return true;
        }

        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        final float currentX = x - startX;
        switch (slideMode) {
          case SLIDE_MODE_ACCEPT: {
            final float targetX = getTargetAcceptX();
            final float cx = Math.min(Math.max(0, currentX), targetX);
            setPosition(cx / targetX, SLIDE_MODE_ACCEPT - 1, false);
            break;
          }
          case SLIDE_MODE_DECLINE: {
            final float targetX = getTargetDeclineX();
            final float cx = Math.max(Math.min(0, currentX), targetX);
            setPosition(cx / targetX, SLIDE_MODE_DECLINE - 1, false);
            break;
          }
        }
        if (slideMode != SLIDE_MODE_NONE) {
          flingDetector.onTouchEvent(e);
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (slideMode != SLIDE_MODE_NONE) {
          flingDetector.onTouchEvent(e);
        }
        setSlideMode(SLIDE_MODE_NONE, true);
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (slideMode != SLIDE_MODE_NONE) {
          flingDetector.onTouchEvent(e);
        }
        setSlideMode(SLIDE_MODE_NONE, true, SLIDE_MODE_NONE, true);
        break;
      }
    }

    return false;
  }
}
