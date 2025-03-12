/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thunderdog.challegram.component.chat;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.Interpolator;

import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.VelocityTrackerCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.R;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import org.thunderdog.challegram.Log;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;

/**
 * This is a utility class to add swipe to dismiss and drag & drop support to RecyclerView.
 * <p>
 * It works with a RecyclerView and a Callback class, which configures what type of interactions
 * are enabled and also receives events when user performs these actions.
 * <p>
 * Depending on which functionality you support, you should override
 * {@link Callback#onMove(RecyclerView, ViewHolder, ViewHolder)} and / or
 * {@link Callback#onSwiped(ViewHolder, int)}.
 * <p>
 * This class is designed to work with any LayoutManager but for certain situations, it can be
 * optimized for your custom LayoutManager by extending methods in the
 * {@link ItemTouchHelper.Callback} class or implementing {@link ItemTouchHelper.ViewDropHandler}
 * interface in your LayoutManager.
 * <p>
 * By default, ItemTouchHelper moves the items' translateX/Y properties to reposition them. On
 * platforms older than Honeycomb, ItemTouchHelper uses canvas translations and View's visibility
 * property to move items in response to touch events. You can customize these behaviors by
 * overriding {@link Callback#onChildDraw(Canvas, RecyclerView, ViewHolder, float, float, int,
 * boolean)}
 * or {@link Callback#onChildDrawOver(Canvas, RecyclerView, ViewHolder, float, float, int,
 * boolean)}.
 * <p/>
 * Most of the time, you only need to override <code>onChildDraw</code> but due to limitations of
 * platform prior to Honeycomb, you may need to implement <code>onChildDrawOver</code> as well.
 */
public class CustomTouchHelper extends RecyclerView.ItemDecoration
  implements RecyclerView.OnChildAttachStateChangeListener {

  /**
   * Up direction, used for swipe & drag control.
   */
  public static final int UP = 1;

  /**
   * Down direction, used for swipe & drag control.
   */
  public static final int DOWN = 1 << 1;

  /**
   * Left direction, used for swipe & drag control.
   */
  public static final int LEFT = 1 << 2;

  /**
   * Right direction, used for swipe & drag control.
   */
  public static final int RIGHT = 1 << 3;

  // If you change these relative direction values, update Callback#convertToAbsoluteDirection,
  // Callback#convertToRelativeDirection.
  /**
   * Horizontal start direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
   * direction. Used for swipe & drag control.
   */
  public static final int START = LEFT << 2;

  /**
   * Horizontal end direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
   * direction. Used for swipe & drag control.
   */
  public static final int END = RIGHT << 2;

  /**
   * ItemTouchHelper is in idle state. At this state, either there is no related motion event by
   * the user or latest motion events have not yet triggered a swipe or drag.
   */
  public static final int ACTION_STATE_IDLE = 0;

  /**
   * A View is currently being swiped.
   */
  public static final int ACTION_STATE_SWIPE = 1;

  /**
   * A View is currently being dragged.
   */
  public static final int ACTION_STATE_DRAG = 2;

  /**
   * Animation type for views which are swiped successfully.
   */
  public static final int ANIMATION_TYPE_SWIPE_SUCCESS = 1 << 1;

  /**
   * Animation type for views which are not completely swiped thus will animate back to their
   * original position.
   */
  public static final int ANIMATION_TYPE_SWIPE_CANCEL = 1 << 2;

  /**
   * Animation type for views that were dragged and now will animate to their final position.
   */
  public static final int ANIMATION_TYPE_DRAG = 1 << 3;

  private static final boolean DEBUG = false;

  private static final int ACTIVE_POINTER_ID_NONE = -1;

  private static final int DIRECTION_FLAG_COUNT = 8;

  private static final int ACTION_MODE_IDLE_MASK = (1 << DIRECTION_FLAG_COUNT) - 1;

  private static final int ACTION_MODE_SWIPE_MASK = ACTION_MODE_IDLE_MASK << DIRECTION_FLAG_COUNT;

  private static final int ACTION_MODE_DRAG_MASK = ACTION_MODE_SWIPE_MASK << DIRECTION_FLAG_COUNT;

  /**
   * Views, whose state should be cleared after they are detached from RecyclerView.
   * This is necessary after swipe dismissing an item. We wait until animator finishes its job
   * to clean these views.
   */
  final List<View> mPendingCleanup = new ArrayList<>();

  /**
   * Re-use array to calculate dx dy for a ViewHolder
   */
  private final float[] mTmpPosition = new float[2];

  /**
   * Currently selected view holder
   */
  ViewHolder mSelected = null;

  /**
   * The reference coordinates for the action start. For drag & drop, this is the time long
   * press is completed vs for swipe, this is the initial touch point.
   */
  float mInitialTouchX;

  float mInitialTouchY;

  /**
   * The diff between the last event and initial touch.
   */
  float mDx;

  float mDy;

  /**
   * The coordinates of the selected view at the time it is selected. We record these values
   * when action starts so that we can consistently position it even if LayoutManager moves the
   * View.
   */
  float mSelectedStartX;

  float mSelectedStartY;

  /**
   * The pointer we are tracking.
   */
  int mActivePointerId = ACTIVE_POINTER_ID_NONE;

  /**
   * Developer callback which controls the behavior of ItemTouchHelper.
   */
  Callback mCallback;

  /**
   * Current mode.
   */
  int mActionState = ACTION_STATE_IDLE;

  /**
   * The direction flags obtained from unmasking
   * {@link Callback#getAbsoluteMovementFlags(RecyclerView, ViewHolder)} for the current
   * action state.
   */
  int mSelectedFlags;

  /**
   * When a View is dragged or swiped and needs to go back to where it was, we create a Recover
   * Animation and animate it to its location using this custom Animator, instead of using
   * framework Animators.
   * Using framework animators has the side effect of clashing with ItemAnimator, creating
   * jumpy UIs.
   */
  List<RecoverAnimation> mRecoverAnimations = new ArrayList<>();

  private int mSlop;

  private RecyclerView mRecyclerView;

  /**
   * When user drags a view to the edge, we start scrolling the LayoutManager as long as View
   * is partially out of bounds.
   */
  private final Runnable mScrollRunnable = new Runnable() {
    @Override
    public void run() {
      if (mSelected != null && scrollIfNecessary()) {
        if (mSelected != null) { //it might be lost during scrolling
          moveIfNecessary(mSelected);
        }
        mRecyclerView.removeCallbacks(mScrollRunnable);
        ViewCompat.postOnAnimation(mRecyclerView, this);
      }
    }
  };

  /**
   * Used for detecting fling swipe
   */
  private VelocityTracker mVelocityTracker;

  //re-used list for selecting a swap target
  private List<ViewHolder> mSwapTargets;

  //re used for for sorting swap targets
  private List<Integer> mDistances;

  /**
   * If drag & drop is supported, we use child drawing order to bring them to front.
   */
  private RecyclerView.ChildDrawingOrderCallback mChildDrawingOrderCallback = null;

  /**
   * This keeps a reference to the child dragged by the user. Even after user stops dragging,
   * until view reaches its final position (end of recover animation), we keep a reference so
   * that it can be drawn above other children.
   */
  private View mOverdrawChild = null;

  /**
   * We cache the position of the overdraw child to avoid recalculating it each time child
   * position callback is called. This value is invalidated whenever a child is attached or
   * detached.
   */
  private int mOverdrawChildPosition = -1;

  /**
   * Used to detect long press.
   */
  private GestureDetectorCompat mGestureDetector;

  private final OnItemTouchListener mOnItemTouchListener
    = new OnItemTouchListener() {
    @Override
    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
      mGestureDetector.onTouchEvent(event);
      if (DEBUG) {
        Log.d("intercept: x:" + event.getX() + ",y:" + event.getY() + ", " + event);
      }
      final int action = MotionEventCompat.getActionMasked(event);
      if (action == MotionEvent.ACTION_DOWN) {
        mActivePointerId = MotionEventCompat.getPointerId(event, 0);
        mInitialTouchX = event.getX();
        mInitialTouchY = event.getY();
        obtainVelocityTracker();
        if (mSelected == null) {
          final RecoverAnimation animation = findAnimation(event);
          if (animation != null) {
            mInitialTouchX -= animation.mX;
            mInitialTouchY -= animation.mY;
            endRecoverAnimation(animation.mViewHolder, true);
            if (mPendingCleanup.remove(animation.mViewHolder.itemView)) {
              mCallback.clearView(mRecyclerView, animation.mViewHolder);
            }
            select(animation.mViewHolder, animation.mActionState);
            updateDxDy(event, mSelectedFlags, 0);
          }
        }
      } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
        mActivePointerId = ACTIVE_POINTER_ID_NONE;
        select(null, ACTION_STATE_IDLE);
      } else if (mActivePointerId != ACTIVE_POINTER_ID_NONE) {
        // in a non scroll orientation, if distance change is above threshold, we
        // can select the item
        final int index = MotionEventCompat.findPointerIndex(event, mActivePointerId);
        if (DEBUG) {
          Log.d("pointer index " + index);
        }
        if (index >= 0) {
          checkSelectForSwipe(action, event, index);
        }
      }
      if (mVelocityTracker != null) {
        mVelocityTracker.addMovement(event);
      }
      return mSelected != null;
    }

    @Override
    public void onTouchEvent(RecyclerView recyclerView, MotionEvent event) {
      mGestureDetector.onTouchEvent(event);
      if (DEBUG) {
        Log.d("on touch: x:" + mInitialTouchX + ",y:" + mInitialTouchY + ", :" + event);
      }
      if (mVelocityTracker != null) {
        mVelocityTracker.addMovement(event);
      }
      if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
        return;
      }
      final int action = MotionEventCompat.getActionMasked(event);
      final int activePointerIndex = MotionEventCompat
        .findPointerIndex(event, mActivePointerId);
      if (activePointerIndex >= 0) {
        checkSelectForSwipe(action, event, activePointerIndex);
      }
      ViewHolder viewHolder = mSelected;
      if (viewHolder == null) {
        return;
      }
      switch (action) {
        case MotionEvent.ACTION_MOVE: {
          // Find the index of the active pointer and fetch its position
          if (activePointerIndex >= 0) {
            updateDxDy(event, mSelectedFlags, activePointerIndex);
            moveIfNecessary(viewHolder);
            mRecyclerView.removeCallbacks(mScrollRunnable);
            mScrollRunnable.run();
            mRecyclerView.invalidate();
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
          if (mVelocityTracker != null) {
            mVelocityTracker
              .computeCurrentVelocity(1000, mRecyclerView.getMaxFlingVelocity());
          }
          select(null, ACTION_STATE_IDLE);
          mActivePointerId = ACTIVE_POINTER_ID_NONE;
          break;
        case MotionEvent.ACTION_POINTER_UP: {
          final int pointerIndex = MotionEventCompat.getActionIndex(event);
          final int pointerId = MotionEventCompat.getPointerId(event, pointerIndex);
          if (pointerId == mActivePointerId) {
            if (mVelocityTracker != null) {
              mVelocityTracker
                .computeCurrentVelocity(1000,
                  mRecyclerView.getMaxFlingVelocity());
            }
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = MotionEventCompat.getPointerId(event, newPointerIndex);
            updateDxDy(event, mSelectedFlags, pointerIndex);
          }
          break;
        }
      }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
      if (!disallowIntercept) {
        return;
      }
      select(null, ACTION_STATE_IDLE);
    }
  };

  /**
   * Temporary rect instance that is used when we need to lookup Item decorations.
   */
  private Rect mTmpRect;

  /**
   * When user started to drag scroll. Reset when we don't scroll
   */
  private long mDragScrollStartTimeInMs;

  /**
   * Creates an ItemTouchHelper that will work with the given Callback.
   * <p>
   * You can attach ItemTouchHelper to a RecyclerView via
   * {@link #attachToRecyclerView(RecyclerView)}. Upon attaching, it will add an item decoration,
   * an onItemTouchListener and a Child attach / detach listener to the RecyclerView.
   *
   * @param callback The Callback which controls the behavior of this touch helper.
   */
  public CustomTouchHelper (Callback callback) {
    mCallback = callback;
  }

  private static boolean hitTest(View child, float x, float y, float left, float top) {
    return x >= left &&
      x <= left + child.getWidth() &&
      y >= top &&
      y <= top + child.getHeight();
  }

  /**
   * Attaches the ItemTouchHelper to the provided RecyclerView. If TouchHelper is already
   * attached
   * to a RecyclerView, it will first detach from the previous one.
   *
   * @param recyclerView The RecyclerView instance to which you want to add this helper.
   */
  public void attachToRecyclerView(RecyclerView recyclerView) {
    if (mRecyclerView == recyclerView) {
      return; // nothing to do
    }
    if (mRecyclerView != null) {
      destroyCallbacks();
    }
    mRecyclerView = recyclerView;
    if (mRecyclerView != null) {
      setupCallbacks();
    }
  }

  private void setupCallbacks() {
    ViewConfiguration vc = ViewConfiguration.get(mRecyclerView.getContext());
    mSlop = vc.getScaledTouchSlop();
    mRecyclerView.addItemDecoration(this);
    mRecyclerView.addOnItemTouchListener(mOnItemTouchListener);
    mRecyclerView.addOnChildAttachStateChangeListener(this);
    initGestureDetector();
  }

  private void destroyCallbacks() {
    mRecyclerView.removeItemDecoration(this);
    mRecyclerView.removeOnItemTouchListener(mOnItemTouchListener);
    mRecyclerView.removeOnChildAttachStateChangeListener(this);
    // clean all attached
    final int recoverAnimSize = mRecoverAnimations.size();
    for (int i = recoverAnimSize - 1; i >= 0; i--) {
      final RecoverAnimation recoverAnimation = mRecoverAnimations.get(0);
      mCallback.clearView(mRecyclerView, recoverAnimation.mViewHolder);
    }
    mRecoverAnimations.clear();
    mOverdrawChild = null;
    mOverdrawChildPosition = -1;
    releaseVelocityTracker();
  }

  private void initGestureDetector() {
    if (mGestureDetector != null) {
      return;
    }
    mGestureDetector = new GestureDetectorCompat(mRecyclerView.getContext(),
      new ItemTouchHelperGestureListener());
  }

  private void getSelectedDxDy(float[] outPosition) {
    if ((mSelectedFlags & (LEFT | RIGHT)) != 0) {
      outPosition[0] = mSelectedStartX + mDx - mSelected.itemView.getLeft();
    } else {
      outPosition[0] = ViewCompat.getTranslationX(mSelected.itemView);
    }
    if ((mSelectedFlags & (UP | DOWN)) != 0) {
      outPosition[1] = mSelectedStartY + mDy - mSelected.itemView.getTop();
    } else {
      outPosition[1] = ViewCompat.getTranslationY(mSelected.itemView);
    }
  }

  @Override
  public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
    float dx = 0, dy = 0;
    if (mSelected != null) {
      getSelectedDxDy(mTmpPosition);
      dx = mTmpPosition[0];
      dy = mTmpPosition[1];
    }
    mCallback.onDrawOver(c, parent, mSelected,
      mRecoverAnimations, mActionState, dx, dy);
  }

  @Override
  public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
    // we don't know if RV changed something so we should invalidate this index.
    mOverdrawChildPosition = -1;
    float dx = 0, dy = 0;
    if (mSelected != null) {
      getSelectedDxDy(mTmpPosition);
      dx = mTmpPosition[0];
      dy = mTmpPosition[1];
    }
    mCallback.onDraw(c, parent, mSelected,
      mRecoverAnimations, mActionState, dx, dy);
  }

  /**
   * Starts dragging or swiping the given View. Call with null if you want to clear it.
   *
   * @param selected    The ViewHolder to drag or swipe. Can be null if you want to cancel the
   *                    current action
   * @param actionState The type of action
   */
  private void select(ViewHolder selected, int actionState) {
    if (selected == mSelected && actionState == mActionState) {
      return;
    }
    mDragScrollStartTimeInMs = Long.MIN_VALUE;
    final int prevActionState = mActionState;
    // prevent duplicate animations
    endRecoverAnimation(selected, true);
    mActionState = actionState;
    if (actionState == ACTION_STATE_DRAG) {
      // we remove after animation is complete. this means we only elevate the last drag
      // child but that should perform good enough as it is very hard to start dragging a
      // new child before the previous one settles.
      mOverdrawChild = selected.itemView;
      addChildDrawingOrderCallback();
    }
    int actionStateMask = (1 << (DIRECTION_FLAG_COUNT + DIRECTION_FLAG_COUNT * actionState))
      - 1;

    if (mSelected != null) {
      final ViewHolder prevSelected = mSelected;
      if (prevSelected.itemView.getParent() != null) {
        final int swipeDir = prevActionState == ACTION_STATE_DRAG ? 0
          : swipeIfNecessary(prevSelected);
        releaseVelocityTracker();
        // find where we should animate to
        final float targetTranslateX, targetTranslateY;
        int animationType;
        switch (swipeDir) {
          case LEFT:
          case RIGHT:
          case START:
          case END:
            targetTranslateY = 0;
            targetTranslateX = Math.signum(mDx) * mRecyclerView.getWidth();
            break;
          case UP:
          case DOWN:
            targetTranslateX = 0;
            targetTranslateY = Math.signum(mDy) * mRecyclerView.getHeight();
            break;
          default:
            targetTranslateX = 0;
            targetTranslateY = 0;
        }
        if (prevActionState == ACTION_STATE_DRAG) {
          animationType = ANIMATION_TYPE_DRAG;
        } else if (swipeDir > 0) {
          animationType = ANIMATION_TYPE_SWIPE_SUCCESS;
        } else {
          animationType = ANIMATION_TYPE_SWIPE_CANCEL;
        }
        getSelectedDxDy(mTmpPosition);
        final float currentTranslateX = mTmpPosition[0];
        final float currentTranslateY = mTmpPosition[1];
        final RecoverAnimation rv = new RecoverAnimation(prevSelected, animationType,
          prevActionState, currentTranslateX, currentTranslateY,
          targetTranslateX, targetTranslateY) {
          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (this.mOverridden) {
              return;
            }
            if (swipeDir <= 0) {
              // this is a drag or failed swipe. recover immediately
              mCallback.clearView(mRecyclerView, prevSelected);
              // full cleanup will happen on onDrawOver
            } else {
              // wait until remove animation is complete.
              mPendingCleanup.add(prevSelected.itemView);
              mIsPendingCleanup = true;
              if (swipeDir > 0) {
                // Animation might be ended by other animators during a layout.
                // We defer callback to avoid editing adapter during a layout.
                postDispatchSwipe(this, swipeDir);
              }
            }
            // removed from the list after it is drawn for the last time
            if (mOverdrawChild == prevSelected.itemView) {
              removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView);
            }
          }
        };
        final long duration = mCallback.getAnimationDuration(mRecyclerView, animationType,
          targetTranslateX - currentTranslateX, targetTranslateY - currentTranslateY);
        rv.setDuration(duration);
        mRecoverAnimations.add(rv);
        rv.start();
      } else {
        removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView);
        mCallback.clearView(mRecyclerView, prevSelected);
      }
      mSelected = null;
    }
    if (selected != null) {
      mSelectedFlags =
        (mCallback.getAbsoluteMovementFlags(mRecyclerView, selected) & actionStateMask)
          >> (mActionState * DIRECTION_FLAG_COUNT);
      mSelectedStartX = selected.itemView.getLeft();
      mSelectedStartY = selected.itemView.getTop();
      mSelected = selected;

      if (actionState == ACTION_STATE_DRAG) {
        mSelected.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      }
    }
    final ViewParent rvParent = mRecyclerView.getParent();
    if (rvParent != null) {
      rvParent.requestDisallowInterceptTouchEvent(mSelected != null);
    }
    mCallback.onSelectedChanged(mSelected, mActionState);
    mRecyclerView.invalidate();
  }

  public void ignoreSwipe (RecyclerView.ViewHolder holder, int swipeDir) {
    endRecoverAnimation(holder, false); // this may push it into pending cleanup list.
    if (mPendingCleanup.remove(holder.itemView)) {
      mCallback.clearView(mRecyclerView, holder);
    }
    select(null, ACTION_STATE_IDLE);
  }

  private void postDispatchSwipe(final RecoverAnimation anim, final int swipeDir) {
    // wait until animations are complete.
    mRecyclerView.post(new Runnable() {
      @Override
      public void run() {
        if (mRecyclerView != null && mRecyclerView.isAttachedToWindow() &&
          !anim.mOverridden &&
          anim.mViewHolder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
          final RecyclerView.ItemAnimator animator = mRecyclerView.getItemAnimator();
          // if animator is running or we have other active recover animations, we try
          // not to call onSwiped because DefaultItemAnimator is not good at merging
          // animations. Instead, we wait and batch.
          if ((animator == null || !animator.isRunning(null))
            && !hasRunningRecoverAnim()) {
            mCallback.onSwiped(anim.mViewHolder, swipeDir);
          } else {
            mRecyclerView.post(this);
          }
        }
      }
    });
  }

  private boolean hasRunningRecoverAnim() {
    final int size = mRecoverAnimations.size();
    for (int i = 0; i < size; i++) {
      if (!mRecoverAnimations.get(i).mEnded) {
        return true;
      }
    }
    return false;
  }

  /**
   * If user drags the view to the edge, trigger a scroll if necessary.
   */
  private boolean scrollIfNecessary() {
    if (mSelected == null || !mCallback.canScroll()) {
      mDragScrollStartTimeInMs = Long.MIN_VALUE;
      return false;
    }
    final long now = System.currentTimeMillis();
    final long scrollDuration = mDragScrollStartTimeInMs
      == Long.MIN_VALUE ? 0 : now - mDragScrollStartTimeInMs;
    RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
    if (mTmpRect == null) {
      mTmpRect = new Rect();
    }
    int scrollX = 0;
    int scrollY = 0;
    lm.calculateItemDecorationsForChild(mSelected.itemView, mTmpRect);
    if (lm.canScrollHorizontally()) {
      int curX = (int) (mSelectedStartX + mDx);
      final int leftDiff = curX - mTmpRect.left - mRecyclerView.getPaddingLeft();
      if (mDx < 0 && leftDiff < 0) {
        scrollX = leftDiff;
      } else if (mDx > 0) {
        final int rightDiff =
          curX + mSelected.itemView.getWidth() + mTmpRect.right
            - (mRecyclerView.getWidth() - mRecyclerView.getPaddingRight());
        if (rightDiff > 0) {
          scrollX = rightDiff;
        }
      }
    }
    if (lm.canScrollVertically()) {
      int curY = (int) (mSelectedStartY + mDy);
      final int topDiff = curY - mTmpRect.top - mRecyclerView.getPaddingTop();
      if (mDy < 0 && topDiff < 0) {
        scrollY = topDiff;
      } else if (mDy > 0) {
        final int bottomDiff = curY + mSelected.itemView.getHeight() + mTmpRect.bottom -
          (mRecyclerView.getHeight() - mRecyclerView.getPaddingBottom());
        if (bottomDiff > 0) {
          scrollY = bottomDiff;
        }
      }
    }
    if (scrollX != 0) {
      scrollX = mCallback.interpolateOutOfBoundsScroll(mRecyclerView,
        mSelected.itemView.getWidth(), scrollX,
        mRecyclerView.getWidth(), scrollDuration);
    }
    if (scrollY != 0) {
      scrollY = mCallback.interpolateOutOfBoundsScroll(mRecyclerView,
        mSelected.itemView.getHeight(), scrollY,
        mRecyclerView.getHeight(), scrollDuration);
    }
    if (scrollX != 0 || scrollY != 0) {
      if (mDragScrollStartTimeInMs == Long.MIN_VALUE) {
        mDragScrollStartTimeInMs = now;
      }
      mRecyclerView.scrollBy(scrollX, scrollY);
      return true;
    }
    mDragScrollStartTimeInMs = Long.MIN_VALUE;
    return false;
  }

  private List<ViewHolder> findSwapTargets(ViewHolder viewHolder) {
    if (mSwapTargets == null) {
      mSwapTargets = new ArrayList<>();
      mDistances = new ArrayList<>();
    } else {
      mSwapTargets.clear();
      mDistances.clear();
    }
    final int margin = mCallback.getBoundingBoxMargin();
    final int left = Math.round(mSelectedStartX + mDx) - margin;
    final int top = Math.round(mSelectedStartY + mDy) - margin;
    final int right = left + viewHolder.itemView.getWidth() + 2 * margin;
    final int bottom = top + viewHolder.itemView.getHeight() + 2 * margin;
    final int centerX = (left + right) / 2;
    final int centerY = (top + bottom) / 2;
    final RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
    final int childCount = lm.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View other = lm.getChildAt(i);
      if (other == viewHolder.itemView) {
        continue;//myself!
      }
      if (other.getBottom() < top || other.getTop() > bottom
        || other.getRight() < left || other.getLeft() > right) {
        continue;
      }
      final ViewHolder otherVh = mRecyclerView.getChildViewHolder(other);
      if (mCallback.canDropOver(mRecyclerView, mSelected, otherVh)) {
        // find the index to add
        final int dx = Math.abs(centerX - (other.getLeft() + other.getRight()) / 2);
        final int dy = Math.abs(centerY - (other.getTop() + other.getBottom()) / 2);
        final int dist = dx * dx + dy * dy;

        int pos = 0;
        final int cnt = mSwapTargets.size();
        for (int j = 0; j < cnt; j++) {
          if (dist > mDistances.get(j)) {
            pos++;
          } else {
            break;
          }
        }
        mSwapTargets.add(pos, otherVh);
        mDistances.add(pos, dist);
      }
    }
    return mSwapTargets;
  }

  /**
   * Checks if we should swap w/ another view holder.
   */
  private void moveIfNecessary(ViewHolder viewHolder) {
    if (mRecyclerView.isLayoutRequested()) {
      return;
    }
    if (mActionState != ACTION_STATE_DRAG) {
      return;
    }

    final float threshold = mCallback.getMoveThreshold(viewHolder);
    final int x = (int) (mSelectedStartX + mDx);
    final int y = (int) (mSelectedStartY + mDy);
    if (Math.abs(y - viewHolder.itemView.getTop()) < viewHolder.itemView.getHeight() * threshold
      && Math.abs(x - viewHolder.itemView.getLeft())
      < viewHolder.itemView.getWidth() * threshold) {
      return;
    }
    List<ViewHolder> swapTargets = findSwapTargets(viewHolder);
    if (swapTargets.size() == 0) {
      return;
    }
    // may swap.
    ViewHolder target = mCallback.chooseDropTarget(viewHolder, swapTargets, x, y);
    if (target == null) {
      mSwapTargets.clear();
      mDistances.clear();
      return;
    }
    final int toPosition = target.getBindingAdapterPosition();
    final int fromPosition = viewHolder.getBindingAdapterPosition();
    if (mCallback.onMove(mRecyclerView, viewHolder, target)) {
      // keep target visible
      mCallback.onMoved(mRecyclerView, viewHolder, fromPosition,
        target, toPosition, x, y);
    }
  }

  @Override
  public void onChildViewAttachedToWindow(View view) {
  }

  @Override
  public void onChildViewDetachedFromWindow(View view) {
    removeChildDrawingOrderCallbackIfNecessary(view);
    final ViewHolder holder = mRecyclerView.getChildViewHolder(view);
    if (holder == null) {
      return;
    }
    if (mSelected != null && holder == mSelected) {
      select(null, ACTION_STATE_IDLE);
    } else {
      endRecoverAnimation(holder, false); // this may push it into pending cleanup list.
      if (mPendingCleanup.remove(holder.itemView)) {
        mCallback.clearView(mRecyclerView, holder);
      }
    }
  }

  /**
   * Returns the animation type or 0 if cannot be found.
   */
  private int endRecoverAnimation(ViewHolder viewHolder, boolean override) {
    final int recoverAnimSize = mRecoverAnimations.size();
    for (int i = recoverAnimSize - 1; i >= 0; i--) {
      final RecoverAnimation anim = mRecoverAnimations.get(i);
      if (anim.mViewHolder == viewHolder) {
        anim.mOverridden |= override;
        if (!anim.mEnded) {
          anim.cancel();
        }
        mRecoverAnimations.remove(i);
        return anim.mAnimationType;
      }
    }
    return 0;
  }

  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                             RecyclerView.State state) {
    outRect.setEmpty();
  }

  private void obtainVelocityTracker() {
    if (mVelocityTracker != null) {
      mVelocityTracker.recycle();
    }
    mVelocityTracker = VelocityTracker.obtain();
  }

  private void releaseVelocityTracker() {
    if (mVelocityTracker != null) {
      mVelocityTracker.recycle();
      mVelocityTracker = null;
    }
  }

  private ViewHolder findSwipedView(MotionEvent motionEvent) {
    final RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
    if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
      return null;
    }
    final int pointerIndex = MotionEventCompat.findPointerIndex(motionEvent, mActivePointerId);
    final float dx = MotionEventCompat.getX(motionEvent, pointerIndex) - mInitialTouchX;
    final float dy = MotionEventCompat.getY(motionEvent, pointerIndex) - mInitialTouchY;
    final float absDx = Math.abs(dx);
    final float absDy = Math.abs(dy);

    if (absDx < mSlop && absDy < mSlop) {
      return null;
    }
    if (absDx > absDy && lm.canScrollHorizontally()) {
      return null;
    } else if (absDy > absDx && lm.canScrollVertically()) {
      return null;
    }
    View child = findChildView(motionEvent);
    if (child == null) {
      return null;
    }
    return mRecyclerView.getChildViewHolder(child);
  }

  /**
   * Checks whether we should select a View for swiping.
   */
  private boolean checkSelectForSwipe(int action, MotionEvent motionEvent, int pointerIndex) {
    if (mSelected != null || action != MotionEvent.ACTION_MOVE
      || mActionState == ACTION_STATE_DRAG || !mCallback.isItemViewSwipeEnabled()) {
      return false;
    }
    if (mRecyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
      return false;
    }
    final ViewHolder vh = findSwipedView(motionEvent);
    if (vh == null) {
      return false;
    }
    final int movementFlags = mCallback.getAbsoluteMovementFlags(mRecyclerView, vh);

    final int swipeFlags = (movementFlags & ACTION_MODE_SWIPE_MASK)
      >> (DIRECTION_FLAG_COUNT * ACTION_STATE_SWIPE);

    if (swipeFlags == 0) {
      return false;
    }

    // mDx and mDy are only set in allowed directions. We use custom x/y here instead of
    // updateDxDy to avoid swiping if user moves more in the other direction
    final float x = MotionEventCompat.getX(motionEvent, pointerIndex);
    final float y = MotionEventCompat.getY(motionEvent, pointerIndex);

    // Calculate the distance moved
    final float dx = x - mInitialTouchX;
    final float dy = y - mInitialTouchY;
    // swipe target is chose w/o applying flags so it does not really check if swiping in that
    // direction is allowed. This why here, we use mDx mDy to check slope value again.
    final float absDx = Math.abs(dx);
    final float absDy = Math.abs(dy);

    if (absDx < mSlop && absDy < mSlop) {
      return false;
    }
    if (absDx > absDy) {
      if (dx < 0 && (swipeFlags & LEFT) == 0) {
        return false;
      }
      if (dx > 0 && (swipeFlags & RIGHT) == 0) {
        return false;
      }
    } else {
      if (dy < 0 && (swipeFlags & UP) == 0) {
        return false;
      }
      if (dy > 0 && (swipeFlags & DOWN) == 0) {
        return false;
      }
    }
    mDx = mDy = 0f;
    mActivePointerId = MotionEventCompat.getPointerId(motionEvent, 0);
    select(vh, ACTION_STATE_SWIPE);
    return true;
  }

  private View findChildView(MotionEvent event) {
    // first check elevated views, if none, then call RV
    final float x = event.getX();
    final float y = event.getY();
    if (mSelected != null) {
      final View selectedView = mSelected.itemView;
      if (hitTest(selectedView, x, y, mSelectedStartX + mDx, mSelectedStartY + mDy)) {
        return selectedView;
      }
    }
    for (int i = mRecoverAnimations.size() - 1; i >= 0; i--) {
      final RecoverAnimation anim = mRecoverAnimations.get(i);
      final View view = anim.mViewHolder.itemView;
      if (hitTest(view, x, y, anim.mX, anim.mY)) {
        return view;
      }
    }
    return mRecyclerView.findChildViewUnder(x, y);
  }

  /**
   * Starts dragging the provided ViewHolder. By default, ItemTouchHelper starts a drag when a
   * View is long pressed. You can disable that behavior via
   * {@link ItemTouchHelper.Callback#isLongPressDragEnabled()}.
   * <p>
   * For this method to work:
   * <ul>
   * <li>The provided ViewHolder must be a child of the RecyclerView to which this
   * ItemTouchHelper
   * is attached.</li>
   * <li>{@link ItemTouchHelper.Callback} must have dragging enabled.</li>
   * <li>There must be a previous touch event that was reported to the ItemTouchHelper
   * through RecyclerView's ItemTouchListener mechanism. As long as no other ItemTouchListener
   * grabs previous events, this should work as expected.</li>
   * </ul>
   *
   * For example, if you would like to let your user to be able to drag an Item by touching one
   * of its descendants, you may implement it as follows:
   * <pre>
   *     viewHolder.dragButton.setOnTouchListener(new View.OnTouchListener() {
   *         public boolean onTouch(View v, MotionEvent event) {
   *             if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
   *                 mItemTouchHelper.startDrag(viewHolder);
   *             }
   *             return false;
   *         }
   *     });
   * </pre>
   * <p>
   *
   * @param viewHolder The ViewHolder to start dragging. It must be a direct child of
   *                   RecyclerView.
   * @see ItemTouchHelper.Callback#isItemViewSwipeEnabled()
   */
  public void startDrag(ViewHolder viewHolder) {
    if (!mCallback.hasDragFlag(mRecyclerView, viewHolder)) {
      Log.e("Start drag has been called but swiping is not enabled");
      return;
    }
    if (viewHolder.itemView.getParent() != mRecyclerView) {
      Log.e("Start drag has been called with a view holder which is not a child of "
        + "the RecyclerView which is controlled by this ItemTouchHelper.");
      return;
    }
    obtainVelocityTracker();
    mDx = mDy = 0f;
    select(viewHolder, ACTION_STATE_DRAG);
  }

  /**
   * Starts swiping the provided ViewHolder. By default, ItemTouchHelper starts swiping a View
   * when user swipes their finger (or mouse pointer) over the View. You can disable this
   * behavior
   * by overriding {@link ItemTouchHelper.Callback}
   * <p>
   * For this method to work:
   * <ul>
   * <li>The provided ViewHolder must be a child of the RecyclerView to which this
   * ItemTouchHelper is attached.</li>
   * <li>{@link ItemTouchHelper.Callback} must have swiping enabled.</li>
   * <li>There must be a previous touch event that was reported to the ItemTouchHelper
   * through RecyclerView's ItemTouchListener mechanism. As long as no other ItemTouchListener
   * grabs previous events, this should work as expected.</li>
   * </ul>
   *
   * For example, if you would like to let your user to be able to swipe an Item by touching one
   * of its descendants, you may implement it as follows:
   * <pre>
   *     viewHolder.dragButton.setOnTouchListener(new View.OnTouchListener() {
   *         public boolean onTouch(View v, MotionEvent event) {
   *             if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
   *                 mItemTouchHelper.startSwipe(viewHolder);
   *             }
   *             return false;
   *         }
   *     });
   * </pre>
   *
   * @param viewHolder The ViewHolder to start swiping. It must be a direct child of
   *                   RecyclerView.
   */
  public void startSwipe(ViewHolder viewHolder) {
    if (!mCallback.hasSwipeFlag(mRecyclerView, viewHolder)) {
      if (DEBUG) {
        Log.e("Start swipe has been called but dragging is not enabled");
      }
      return;
    }
    if (viewHolder.itemView.getParent() != mRecyclerView) {
      Log.e("Start swipe has been called with a view holder which is not a child of "
        + "the RecyclerView controlled by this ItemTouchHelper.");
      return;
    }
    obtainVelocityTracker();
    mDx = mDy = 0f;
    select(viewHolder, ACTION_STATE_SWIPE);
  }

  private RecoverAnimation findAnimation(MotionEvent event) {
    if (mRecoverAnimations.isEmpty()) {
      return null;
    }
    View target = findChildView(event);
    for (int i = mRecoverAnimations.size() - 1; i >= 0; i--) {
      final RecoverAnimation anim = mRecoverAnimations.get(i);
      if (anim.mViewHolder.itemView == target) {
        return anim;
      }
    }
    return null;
  }

  private void updateDxDy(MotionEvent ev, int directionFlags, int pointerIndex) {
    final float x = MotionEventCompat.getX(ev, pointerIndex);
    final float y = MotionEventCompat.getY(ev, pointerIndex);

    // Calculate the distance moved
    mDx = x - mInitialTouchX;
    mDy = y - mInitialTouchY;
    if ((directionFlags & LEFT) == 0) {
      mDx = Math.max(0, mDx);
    }
    if ((directionFlags & RIGHT) == 0) {
      mDx = Math.min(0, mDx);
    }
    if ((directionFlags & UP) == 0) {
      mDy = Math.max(0, mDy);
    }
    if ((directionFlags & DOWN) == 0) {
      mDy = Math.min(0, mDy);
    }
  }

  private int swipeIfNecessary(ViewHolder viewHolder) {
    if (mActionState == ACTION_STATE_DRAG) {
      return 0;
    }
    final int originalMovementFlags = mCallback.getMovementFlags(mRecyclerView, viewHolder);
    final int absoluteMovementFlags = mCallback.convertToAbsoluteDirection(
      originalMovementFlags,
      ViewCompat.getLayoutDirection(mRecyclerView));
    final int flags = (absoluteMovementFlags
      & ACTION_MODE_SWIPE_MASK) >> (ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT);
    if (flags == 0) {
      return 0;
    }
    final int originalFlags = (originalMovementFlags
      & ACTION_MODE_SWIPE_MASK) >> (ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT);
    int swipeDir;
    if (Math.abs(mDx) > Math.abs(mDy)) {
      if ((swipeDir = checkHorizontalSwipe(viewHolder, flags)) > 0) {
        // if swipe dir is not in original flags, it should be the relative direction
        if ((originalFlags & swipeDir) == 0) {
          // convert to relative
          return Callback.convertToRelativeDirection(swipeDir,
            ViewCompat.getLayoutDirection(mRecyclerView));
        }
        return swipeDir;
      }
      if ((swipeDir = checkVerticalSwipe(viewHolder, flags)) > 0) {
        return swipeDir;
      }
    } else {
      if ((swipeDir = checkVerticalSwipe(viewHolder, flags)) > 0) {
        return swipeDir;
      }
      if ((swipeDir = checkHorizontalSwipe(viewHolder, flags)) > 0) {
        // if swipe dir is not in original flags, it should be the relative direction
        if ((originalFlags & swipeDir) == 0) {
          // convert to relative
          return Callback.convertToRelativeDirection(swipeDir,
            ViewCompat.getLayoutDirection(mRecyclerView));
        }
        return swipeDir;
      }
    }
    return 0;
  }

  private int checkHorizontalSwipe(ViewHolder viewHolder, int flags) {
    if ((flags & (LEFT | RIGHT)) != 0) {
      final int dirFlag = mDx > 0 ? RIGHT : LEFT;
      if (mVelocityTracker != null && mActivePointerId > -1) {
        final float xVelocity = VelocityTrackerCompat
          .getXVelocity(mVelocityTracker, mActivePointerId);
        final int velDirFlag = xVelocity > 0f ? RIGHT : LEFT;
        if ((velDirFlag & flags) != 0 && dirFlag == velDirFlag &&
          Math.abs(xVelocity) >= mRecyclerView.getMinFlingVelocity()) {
          return mCallback.onBeforeSwipe(viewHolder, velDirFlag) ? 0 : velDirFlag;
        }
      }

      final float threshold = mRecyclerView.getWidth() * mCallback
        .getSwipeThreshold(viewHolder);

      if ((flags & dirFlag) != 0 && Math.abs(mDx) > threshold && !mCallback.onBeforeSwipe(viewHolder, dirFlag)) {
        return dirFlag;
      }
    }
    return 0;
  }

  private int checkVerticalSwipe(ViewHolder viewHolder, int flags) {
    if ((flags & (UP | DOWN)) != 0) {
      final int dirFlag = mDy > 0 ? DOWN : UP;
      if (mVelocityTracker != null && mActivePointerId > -1) {
        final float yVelocity = VelocityTrackerCompat
          .getYVelocity(mVelocityTracker, mActivePointerId);
        final int velDirFlag = yVelocity > 0f ? DOWN : UP;
        if ((velDirFlag & flags) != 0 && velDirFlag == dirFlag &&
          Math.abs(yVelocity) >= mRecyclerView.getMinFlingVelocity()) {
          return mCallback.onBeforeSwipe(viewHolder, velDirFlag) ? 0 : velDirFlag;
        }
      }

      final float threshold = mRecyclerView.getHeight() * mCallback
        .getSwipeThreshold(viewHolder);
      if ((flags & dirFlag) != 0 && Math.abs(mDy) > threshold && !mCallback.onBeforeSwipe(viewHolder, dirFlag)) {
        return dirFlag;
      }
    }
    return 0;
  }

  private void addChildDrawingOrderCallback() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return;// we use elevation on Lollipop
    }
    if (mChildDrawingOrderCallback == null) {
      mChildDrawingOrderCallback = (childCount, i) -> {
        if (mOverdrawChild == null) {
          return i;
        }
        int childPosition = mOverdrawChildPosition;
        if (childPosition == -1) {
          childPosition = mRecyclerView.indexOfChild(mOverdrawChild);
          mOverdrawChildPosition = childPosition;
        }
        if (i == childCount - 1) {
          return childPosition;
        }
        return i < childPosition ? i : i + 1;
      };
    }
    mRecyclerView.setChildDrawingOrderCallback(mChildDrawingOrderCallback);
  }

  private void removeChildDrawingOrderCallbackIfNecessary(View view) {
    if (view == mOverdrawChild) {
      mOverdrawChild = null;
      // only remove if we've added
      if (mChildDrawingOrderCallback != null) {
        mRecyclerView.setChildDrawingOrderCallback(null);
      }
    }
  }

  /**
   * An interface which can be implemented by LayoutManager for better integration with
   * {@link ItemTouchHelper}.
   */
  public interface ViewDropHandler {

    /**
     * Called by the {@link ItemTouchHelper} after a View is dropped over another View.
     * <p>
     * A LayoutManager should implement this interface to get ready for the upcoming move
     * operation.
     * <p>
     * For example, LinearLayoutManager sets up a "scrollToPositionWithOffset" calls so that
     * the View under drag will be used as an anchor View while calculating the next layout,
     * making layout stay consistent.
     *
     * @param view   The View which is being dragged. It is very likely that user is still
     *               dragging this View so there might be other
     *               {@link #prepareForDrop(View, View, int, int)} after this one.
     * @param target The target view which is being dropped on.
     * @param x      The <code>left</code> offset of the View that is being dragged. This value
     *               includes the movement caused by the user.
     * @param y      The <code>top</code> offset of the View that is being dragged. This value
     *               includes the movement caused by the user.
     */
    @SuppressWarnings ("JavaDoc")
    void prepareForDrop(View view, View target, int x, int y);
  }

  /**
   * This class is the contract between ItemTouchHelper and your application. It lets you control
   * which touch behaviors are enabled per each ViewHolder and also receive callbacks when user
   * performs these actions.
   * <p>
   * To control which actions user can take on each view, you should override
   * {@link #getMovementFlags(RecyclerView, ViewHolder)} and return appropriate set
   * of direction flags. ({@link #LEFT}, {@link #RIGHT}, {@link #START}, {@link #END},
   * {@link #UP}, {@link #DOWN}). You can use
   * {@link #makeMovementFlags(int, int)} to easily construct it. Alternatively, you can use
   * {@link SimpleCallback}.
   * <p>
   * If user drags an item, ItemTouchHelper will call
   * {@link Callback#onMove(RecyclerView, ViewHolder, ViewHolder)
   * onMove(recyclerView, dragged, target)}.
   * Upon receiving this callback, you should move the item from the old position
   * ({@code dragged.getAdapterPosition()}) to new position ({@code target.getAdapterPosition()})
   * in your adapter and also call {@link RecyclerView.Adapter#notifyItemMoved(int, int)}.
   * To control where a View can be dropped, you can override
   * {@link #canDropOver(RecyclerView, ViewHolder, ViewHolder)}. When a
   * dragging View overlaps multiple other views, Callback chooses the closest View with which
   * dragged View might have changed positions. Although this approach works for many use cases,
   * if you have a custom LayoutManager, you can override
   * {@link #chooseDropTarget(ViewHolder, java.util.List, int, int)} to select a
   * custom drop target.
   * <p>
   * When a View is swiped, ItemTouchHelper animates it until it goes out of bounds, then calls
   * {@link #onSwiped(ViewHolder, int)}. At this point, you should update your
   * adapter (e.g. remove the item) and call related Adapter#notify event.
   */
  @SuppressWarnings("UnusedParameters")
  public abstract static class Callback {

    public static final int DEFAULT_DRAG_ANIMATION_DURATION = 200;

    public static final int DEFAULT_SWIPE_ANIMATION_DURATION = 120;

    static final int RELATIVE_DIR_FLAGS = START | END |
      ((START | END) << DIRECTION_FLAG_COUNT) |
      ((START | END) << (2 * DIRECTION_FLAG_COUNT));

    // private static final ItemTouchUIUtil sUICallback;

    private static final int ABS_HORIZONTAL_DIR_FLAGS = LEFT | RIGHT |
      ((LEFT | RIGHT) << DIRECTION_FLAG_COUNT) |
      ((LEFT | RIGHT) << (2 * DIRECTION_FLAG_COUNT));

    private static final Interpolator sDragScrollInterpolator = t -> t * t * t * t * t;

    private static final Interpolator sDragViewScrollCapInterpolator = t -> {
      t -= 1.0f;
      return t * t * t * t * t + 1.0f;
    };

    /**
     * Drag scroll speed keeps accelerating until this many milliseconds before being capped.
     */
    private static final long DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS = 2000;

    private int mCachedMaxScrollSpeed = -1;

    /*static {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        sUICallback = new ItemTouchUIUtilImpl.Lollipop();
      } else if (Build.VERSION.SDK_INT >= 11) {
        sUICallback = new ItemTouchUIUtilImpl.Honeycomb();
      } else {
        sUICallback = new ItemTouchUIUtilImpl.Gingerbread();
      }
    }*/

    /**
     * Returns the {@link ItemTouchUIUtil} that is used by the {@link Callback} class for visual
     * changes on Views in response to user interactions. {@link ItemTouchUIUtil} has different
     * implementations for different platform versions.
     * <p>
     * By default, {@link Callback} applies these changes on
     * {@link RecyclerView.ViewHolder#itemView}.
     * <p>
     * For example, if you have a use case where you only want the text to move when user
     * swipes over the view, you can do the following:
     * <pre>
     *     public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder){
     *         getDefaultUIUtil().clearView(((ItemTouchViewHolder) viewHolder).textView);
     *     }
     *     public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
     *         if (viewHolder != null){
     *             getDefaultUIUtil().onSelected(((ItemTouchViewHolder) viewHolder).textView);
     *         }
     *     }
     *     public void onChildDraw(Canvas c, RecyclerView recyclerView,
     *             RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
     *             boolean isCurrentlyActive) {
     *         getDefaultUIUtil().onDraw(c, recyclerView,
     *                 ((ItemTouchViewHolder) viewHolder).textView, dX, dY,
     *                 actionState, isCurrentlyActive);
     *         return true;
     *     }
     *     public void onChildDrawOver(Canvas c, RecyclerView recyclerView,
     *             RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
     *             boolean isCurrentlyActive) {
     *         getDefaultUIUtil().onDrawOver(c, recyclerView,
     *                 ((ItemTouchViewHolder) viewHolder).textView, dX, dY,
     *                 actionState, isCurrentlyActive);
     *         return true;
     *     }
     * </pre>
     *
     * @return The {@link ItemTouchUIUtil} instance that is used by the {@link Callback}
     */
    /*public static ItemTouchUIUtil getDefaultUIUtil() {
      return sUICallback;
    }*/

    /**
     * Replaces a movement direction with its relative version by taking layout direction into
     * account.
     *
     * @param flags           The flag value that include any number of movement flags.
     * @param layoutDirection The layout direction of the View. Can be obtained from
     *                        {@link ViewCompat#getLayoutDirection(android.view.View)}.
     * @return Updated flags which uses relative flags ({@link #START}, {@link #END}) instead
     * of {@link #LEFT}, {@link #RIGHT}.
     * @see #convertToAbsoluteDirection(int, int)
     */
    public static int convertToRelativeDirection(int flags, int layoutDirection) {
      int masked = flags & ABS_HORIZONTAL_DIR_FLAGS;
      if (masked == 0) {
        return flags;// does not have any abs flags, good.
      }
      flags &= ~masked; //remove left / right.
      if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
        // no change. just OR with 2 bits shifted mask and return
        flags |= masked << 2; // START is 2 bits after LEFT, END is 2 bits after RIGHT.
        return flags;
      } else {
        // add RIGHT flag as START
        flags |= ((masked << 1) & ~ABS_HORIZONTAL_DIR_FLAGS);
        // first clean RIGHT bit then add LEFT flag as END
        flags |= ((masked << 1) & ABS_HORIZONTAL_DIR_FLAGS) << 2;
      }
      return flags;
    }

    /**
     * Convenience method to create movement flags.
     * <p>
     * For instance, if you want to let your items be drag & dropped vertically and swiped
     * left to be dismissed, you can call this method with:
     * <code>makeMovementFlags(UP | DOWN, LEFT);</code>
     *
     * @param dragFlags  The directions in which the item can be dragged.
     * @param swipeFlags The directions in which the item can be swiped.
     * @return Returns an integer composed of the given drag and swipe flags.
     */
    public static int makeMovementFlags(int dragFlags, int swipeFlags) {
      return makeFlag(ACTION_STATE_IDLE, swipeFlags | dragFlags) |
        makeFlag(ACTION_STATE_SWIPE, swipeFlags) | makeFlag(ACTION_STATE_DRAG,
        dragFlags);
    }

    /**
     * Shifts the given direction flags to the offset of the given action state.
     *
     * @param actionState The action state you want to get flags in. Should be one of
     *                    {@link #ACTION_STATE_IDLE}, {@link #ACTION_STATE_SWIPE} or
     *                    {@link #ACTION_STATE_DRAG}.
     * @param directions  The direction flags. Can be composed from {@link #UP}, {@link #DOWN},
     *                    {@link #RIGHT}, {@link #LEFT} {@link #START} and {@link #END}.
     * @return And integer that represents the given directions in the provided actionState.
     */
    public static int makeFlag(int actionState, int directions) {
      return directions << (actionState * DIRECTION_FLAG_COUNT);
    }

    /**
     * Should return a composite flag which defines the enabled move directions in each state
     * (idle, swiping, dragging).
     * <p>
     * Instead of composing this flag manually, you can use {@link #makeMovementFlags(int,
     * int)}
     * or {@link #makeFlag(int, int)}.
     * <p>
     * This flag is composed of 3 sets of 8 bits, where first 8 bits are for IDLE state, next
     * 8 bits are for SWIPE state and third 8 bits are for DRAG state.
     * Each 8 bit sections can be constructed by simply OR'ing direction flags defined in
     * {@link ItemTouchHelper}.
     * <p>
     * For example, if you want it to allow swiping LEFT and RIGHT but only allow starting to
     * swipe by swiping RIGHT, you can return:
     * <pre>
     *      makeFlag(ACTION_STATE_IDLE, RIGHT) | makeFlag(ACTION_STATE_SWIPE, LEFT | RIGHT);
     * </pre>
     * This means, allow right movement while IDLE and allow right and left movement while
     * swiping.
     *
     * @param recyclerView The RecyclerView to which ItemTouchHelper is attached.
     * @param viewHolder   The ViewHolder for which the movement information is necessary.
     * @return flags specifying which movements are allowed on this ViewHolder.
     * @see #makeMovementFlags(int, int)
     * @see #makeFlag(int, int)
     */
    public abstract int getMovementFlags(RecyclerView recyclerView,
                                         ViewHolder viewHolder);

    public boolean canScroll() {
      return true;
    }

    /**
     * Converts a given set of flags to absolution direction which means {@link #START} and
     * {@link #END} are replaced with {@link #LEFT} and {@link #RIGHT} depending on the layout
     * direction.
     *
     * @param flags           The flag value that include any number of movement flags.
     * @param layoutDirection The layout direction of the RecyclerView.
     * @return Updated flags which includes only absolute direction values.
     */
    public int convertToAbsoluteDirection(int flags, int layoutDirection) {
      int masked = flags & RELATIVE_DIR_FLAGS;
      if (masked == 0) {
        return flags;// does not have any relative flags, good.
      }
      flags &= ~masked; //remove start / end
      if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
        // no change. just OR with 2 bits shifted mask and return
        flags |= masked >> 2; // START is 2 bits after LEFT, END is 2 bits after RIGHT.
        return flags;
      } else {
        // add START flag as RIGHT
        flags |= ((masked >> 1) & ~RELATIVE_DIR_FLAGS);
        // first clean start bit then add END flag as LEFT
        flags |= ((masked >> 1) & RELATIVE_DIR_FLAGS) >> 2;
      }
      return flags;
    }

    final int getAbsoluteMovementFlags(RecyclerView recyclerView,
                                       ViewHolder viewHolder) {
      final int flags = getMovementFlags(recyclerView, viewHolder);
      return convertToAbsoluteDirection(flags, ViewCompat.getLayoutDirection(recyclerView));
    }

    private boolean hasDragFlag(RecyclerView recyclerView, ViewHolder viewHolder) {
      final int flags = getAbsoluteMovementFlags(recyclerView, viewHolder);
      return (flags & ACTION_MODE_DRAG_MASK) != 0;
    }

    private boolean hasSwipeFlag(RecyclerView recyclerView,
                                 ViewHolder viewHolder) {
      final int flags = getAbsoluteMovementFlags(recyclerView, viewHolder);
      return (flags & ACTION_MODE_SWIPE_MASK) != 0;
    }

    /**
     * Return true if the current ViewHolder can be dropped over the the target ViewHolder.
     * <p>
     * This method is used when selecting drop target for the dragged View. After Views are
     * eliminated either via bounds check or via this method, resulting set of views will be
     * passed to {@link #chooseDropTarget(ViewHolder, java.util.List, int, int)}.
     * <p>
     * Default implementation returns true.
     *
     * @param recyclerView The RecyclerView to which ItemTouchHelper is attached to.
     * @param current      The ViewHolder that user is dragging.
     * @param target       The ViewHolder which is below the dragged ViewHolder.
     * @return True if the dragged ViewHolder can be replaced with the target ViewHolder, false
     * otherwise.
     */
    public boolean canDropOver(RecyclerView recyclerView, ViewHolder current,
                               ViewHolder target) {
      return true;
    }

    /**
     * Called when ItemTouchHelper wants to move the dragged item from its old position to
     * the new position.
     * <p>
     * If this method returns true, ItemTouchHelper assumes {@code viewHolder} has been moved
     * to the adapter position of {@code target} ViewHolder
     * ({@link ViewHolder#getBindingAdapterPosition()
     * ViewHolder#getAdapterPosition()}).
     * <p>
     * If you don't support drag & drop, this method will never be called.
     *
     * @param recyclerView The RecyclerView to which ItemTouchHelper is attached to.
     * @param viewHolder   The ViewHolder which is being dragged by the user.
     * @param target       The ViewHolder over which the currently active item is being
     *                     dragged.
     * @return True if the {@code viewHolder} has been moved to the adapter position of
     * {@code target}.
     * @see #onMoved(RecyclerView, ViewHolder, int, ViewHolder, int, int, int)
     */
    public abstract boolean onMove(RecyclerView recyclerView,
                                   ViewHolder viewHolder, ViewHolder target);

    /**
     * Returns whether ItemTouchHelper should start a drag and drop operation if an item is
     * long pressed.
     * <p>
     * Default value returns true but you may want to disable this if you want to start
     * dragging on a custom view touch using {@link #startDrag(ViewHolder)}.
     *
     * @return True if ItemTouchHelper should start dragging an item when it is long pressed,
     * false otherwise. Default value is <code>true</code>.
     * @see #startDrag(ViewHolder)
     */
    public boolean isLongPressDragEnabled() {
      return true;
    }

    /**
     * Returns whether ItemTouchHelper should start a swipe operation if a pointer is swiped
     * over the View.
     * <p>
     * Default value returns true but you may want to disable this if you want to start
     * swiping on a custom view touch using {@link #startSwipe(ViewHolder)}.
     *
     * @return True if ItemTouchHelper should start swiping an item when user swipes a pointer
     * over the View, false otherwise. Default value is <code>true</code>.
     * @see #startSwipe(ViewHolder)
     */
    public boolean isItemViewSwipeEnabled() {
      return true;
    }

    /**
     * When finding views under a dragged view, by default, ItemTouchHelper searches for views
     * that overlap with the dragged View. By overriding this method, you can extend or shrink
     * the search box.
     *
     * @return The extra margin to be added to the hit box of the dragged View.
     */
    public int getBoundingBoxMargin() {
      return 0;
    }

    /**
     * Returns the fraction that the user should move the View to be considered as swiped.
     * The fraction is calculated with respect to RecyclerView's bounds.
     * <p>
     * Default value is .5f, which means, to swipe a View, user must move the View at least
     * half of RecyclerView's width or height, depending on the swipe direction.
     *
     * @param viewHolder The ViewHolder that is being dragged.
     * @return A float value that denotes the fraction of the View size. Default value
     * is .5f .
     */
    public float getSwipeThreshold(ViewHolder viewHolder) {
      return .5f;
    }

    /**
     * Returns the fraction that the user should move the View to be considered as it is
     * dragged. After a view is moved this amount, ItemTouchHelper starts checking for Views
     * below it for a possible drop.
     *
     * @param viewHolder The ViewHolder that is being dragged.
     * @return A float value that denotes the fraction of the View size. Default value is
     * .5f .
     */
    public float getMoveThreshold(ViewHolder viewHolder) {
      return .5f;
    }

    /**
     * Called by ItemTouchHelper to select a drop target from the list of ViewHolders that
     * are under the dragged View.
     * <p>
     * Default implementation filters the View with which dragged item have changed position
     * in the drag direction. For instance, if the view is dragged UP, it compares the
     * <code>view.getTop()</code> of the two views before and after drag started. If that value
     * is different, the target view passes the filter.
     * <p>
     * Among these Views which pass the test, the one closest to the dragged view is chosen.
     * <p>
     * This method is called on the main thread every time user moves the View. If you want to
     * override it, make sure it does not do any expensive operations.
     *
     * @param selected    The ViewHolder being dragged by the user.
     * @param dropTargets The list of ViewHolder that are under the dragged View and
     *                    candidate as a drop.
     * @param curX        The updated left value of the dragged View after drag translations
     *                    are applied. This value does not include margins added by
     *                    {@link RecyclerView.ItemDecoration}s.
     * @param curY        The updated top value of the dragged View after drag translations
     *                    are applied. This value does not include margins added by
     *                    {@link RecyclerView.ItemDecoration}s.
     * @return A ViewHolder to whose position the dragged ViewHolder should be
     * moved to.
     */
    public ViewHolder chooseDropTarget(ViewHolder selected,
                                       List<ViewHolder> dropTargets, int curX, int curY) {
      int right = curX + selected.itemView.getWidth();
      int bottom = curY + selected.itemView.getHeight();
      ViewHolder winner = null;
      int winnerScore = -1;
      final int dx = curX - selected.itemView.getLeft();
      final int dy = curY - selected.itemView.getTop();
      final int targetsSize = dropTargets.size();
      for (int i = 0; i < targetsSize; i++) {
        final ViewHolder target = dropTargets.get(i);
        if (dx > 0) {
          int diff = target.itemView.getRight() - right;
          if (diff < 0 && target.itemView.getRight() > selected.itemView.getRight()) {
            final int score = Math.abs(diff);
            if (score > winnerScore) {
              winnerScore = score;
              winner = target;
            }
          }
        }
        if (dx < 0) {
          int diff = target.itemView.getLeft() - curX;
          if (diff > 0 && target.itemView.getLeft() < selected.itemView.getLeft()) {
            final int score = Math.abs(diff);
            if (score > winnerScore) {
              winnerScore = score;
              winner = target;
            }
          }
        }
        if (dy < 0) {
          int diff = target.itemView.getTop() - curY;
          if (diff > 0 && target.itemView.getTop() < selected.itemView.getTop()) {
            final int score = Math.abs(diff);
            if (score > winnerScore) {
              winnerScore = score;
              winner = target;
            }
          }
        }

        if (dy > 0) {
          int diff = target.itemView.getBottom() - bottom;
          if (diff < 0 && target.itemView.getBottom() > selected.itemView.getBottom()) {
            final int score = Math.abs(diff);
            if (score > winnerScore) {
              winnerScore = score;
              winner = target;
            }
          }
        }
      }
      return winner;
    }

    /**
     * Called when a ViewHolder is swiped by the user.
     * <p>
     * If you are returning relative directions ({@link #START} , {@link #END}) from the
     * {@link #getMovementFlags(RecyclerView, ViewHolder)} method, this method
     * will also use relative directions. Otherwise, it will use absolute directions.
     * <p>
     * If you don't support swiping, this method will never be called.
     * <p>
     * ItemTouchHelper will keep a reference to the View until it is detached from
     * RecyclerView.
     * As soon as it is detached, ItemTouchHelper will call
     * {@link #clearView(RecyclerView, ViewHolder)}.
     *
     * @param viewHolder The ViewHolder which has been swiped by the user.
     * @param direction  The direction to which the ViewHolder is swiped. It is one of
     *                   {@link #UP}, {@link #DOWN},
     *                   {@link #LEFT} or {@link #RIGHT}. If your
     *                   {@link #getMovementFlags(RecyclerView, ViewHolder)}
     *                   method
     *                   returned relative flags instead of {@link #LEFT} / {@link #RIGHT};
     *                   `direction` will be relative as well. ({@link #START} or {@link
     *                   #END}).
     */
    public abstract void onSwiped(ViewHolder viewHolder, int direction);

    public abstract boolean onBeforeSwipe(ViewHolder viewHolder, int direction);

    /**
     * Called when the ViewHolder swiped or dragged by the ItemTouchHelper is changed.
     * <p/>
     * If you override this method, you should call super.
     *
     * @param viewHolder  The new ViewHolder that is being swiped or dragged. Might be null if
     *                    it is cleared.
     * @param actionState One of {@link ItemTouchHelper#ACTION_STATE_IDLE},
     *                    {@link ItemTouchHelper#ACTION_STATE_SWIPE} or
     *                    {@link ItemTouchHelper#ACTION_STATE_DRAG}.
     *
     * @see #clearView(RecyclerView, RecyclerView.ViewHolder)
     */
    public void onSelectedChanged(ViewHolder viewHolder, int actionState) {
      if (viewHolder != null) {
        viewHolder.itemView.invalidate();
        // sUICallback.onSelected(viewHolder.itemView);
      }
    }

    @SuppressLint ("PrivateResource")
    private int getMaxDragScroll(RecyclerView recyclerView) {
      if (mCachedMaxScrollSpeed == -1) {
        try {
          mCachedMaxScrollSpeed = recyclerView.getResources().getDimensionPixelSize(
            R.dimen.item_touch_helper_max_drag_scroll_per_frame);
          Log.v("mCachedMaxScrollSpeed = %d", mCachedMaxScrollSpeed);
        } catch (Throwable t) {
          Log.w("Cannot resolve maxScrollSpeed", t);
        }
      }
      return mCachedMaxScrollSpeed;
    }

    /**
     * Called when {@link #onMove(RecyclerView, ViewHolder, ViewHolder)} returns true.
     * <p>
     * ItemTouchHelper does not create an extra Bitmap or View while dragging, instead, it
     * modifies the existing View. Because of this reason, it is important that the View is
     * still part of the layout after it is moved. This may not work as intended when swapped
     * Views are close to RecyclerView bounds or there are gaps between them (e.g. other Views
     * which were not eligible for dropping over).
     * <p>
     * This method is responsible to give necessary hint to the LayoutManager so that it will
     * keep the View in visible area. For example, for LinearLayoutManager, this is as simple
     * as calling {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}.
     *
     * Default implementation calls {@link RecyclerView#scrollToPosition(int)} if the View's
     * new position is likely to be out of bounds.
     * <p>
     * It is important to ensure the ViewHolder will stay visible as otherwise, it might be
     * removed by the LayoutManager if the move causes the View to go out of bounds. In that
     * case, drag will end prematurely.
     *
     * @param recyclerView The RecyclerView controlled by the ItemTouchHelper.
     * @param viewHolder   The ViewHolder under user's control.
     * @param fromPos      The previous adapter position of the dragged item (before it was
     *                     moved).
     * @param target       The ViewHolder on which the currently active item has been dropped.
     * @param toPos        The new adapter position of the dragged item.
     * @param x            The updated left value of the dragged View after drag translations
     *                     are applied. This value does not include margins added by
     *                     {@link RecyclerView.ItemDecoration}s.
     * @param y            The updated top value of the dragged View after drag translations
     *                     are applied. This value does not include margins added by
     *                     {@link RecyclerView.ItemDecoration}s.
     */
    public void onMoved(final RecyclerView recyclerView,
                        final ViewHolder viewHolder, int fromPos, final ViewHolder target, int toPos, int x,
                        int y) {
      final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
      if (layoutManager instanceof ViewDropHandler) {
        ((ViewDropHandler) layoutManager).prepareForDrop(viewHolder.itemView,
          target.itemView, x, y);
        return;
      }

      // if layout manager cannot handle it, do some guesswork
      if (layoutManager.canScrollHorizontally()) {
        final int minLeft = layoutManager.getDecoratedLeft(target.itemView);
        if (minLeft <= recyclerView.getPaddingLeft()) {
          recyclerView.scrollToPosition(toPos);
        }
        final int maxRight = layoutManager.getDecoratedRight(target.itemView);
        if (maxRight >= recyclerView.getWidth() - recyclerView.getPaddingRight()) {
          recyclerView.scrollToPosition(toPos);
        }
      }

      if (layoutManager.canScrollVertically()) {
        final int minTop = layoutManager.getDecoratedTop(target.itemView);
        if (minTop <= recyclerView.getPaddingTop()) {
          recyclerView.scrollToPosition(toPos);
        }
        final int maxBottom = layoutManager.getDecoratedBottom(target.itemView);
        if (maxBottom >= recyclerView.getHeight() - recyclerView.getPaddingBottom()) {
          recyclerView.scrollToPosition(toPos);
        }
      }
    }

    private void onDraw(Canvas c, RecyclerView parent, ViewHolder selected,
                        List<RecoverAnimation> recoverAnimationList,
                        int actionState, float dX, float dY) {
      final int recoverAnimSize = recoverAnimationList.size();
      for (int i = 0; i < recoverAnimSize; i++) {
        final RecoverAnimation anim = recoverAnimationList.get(i);
        anim.update();
        final int count = c.save();
        onChildDraw(c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
          false);
        c.restoreToCount(count);
      }
      if (selected != null) {
        final int count = c.save();
        onChildDraw(c, parent, selected, dX, dY, actionState, true);
        c.restoreToCount(count);
      }
    }

    private void onDrawOver(Canvas c, RecyclerView parent, ViewHolder selected,
                            List<RecoverAnimation> recoverAnimationList,
                            int actionState, float dX, float dY) {
      final int recoverAnimSize = recoverAnimationList.size();
      for (int i = 0; i < recoverAnimSize; i++) {
        final RecoverAnimation anim = recoverAnimationList.get(i);
        final int count = c.save();
        onChildDrawOver(c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
          false);
        c.restoreToCount(count);
      }
      if (selected != null) {
        final int count = c.save();
        onChildDrawOver(c, parent, selected, dX, dY, actionState, true);
        c.restoreToCount(count);
      }
      boolean hasRunningAnimation = false;
      for (int i = recoverAnimSize - 1; i >= 0; i--) {
        final RecoverAnimation anim = recoverAnimationList.get(i);
        if (anim.mEnded && !anim.mIsPendingCleanup) {
          recoverAnimationList.remove(i);
        } else if (!anim.mEnded) {
          hasRunningAnimation = true;
        }
      }
      if (hasRunningAnimation) {
        parent.invalidate();
      }
    }

    /**
     * Called by the ItemTouchHelper when the user interaction with an element is over and it
     * also completed its animation.
     * <p>
     * This is a good place to clear all changes on the View that was done in
     * {@link #onSelectedChanged(RecyclerView.ViewHolder, int)},
     * {@link #onChildDraw(Canvas, RecyclerView, ViewHolder, float, float, int,
     * boolean)} or
     * {@link #onChildDrawOver(Canvas, RecyclerView, ViewHolder, float, float, int, boolean)}.
     *
     * @param recyclerView The RecyclerView which is controlled by the ItemTouchHelper.
     * @param viewHolder   The View that was interacted by the user.
     */
    public void clearView(RecyclerView recyclerView, ViewHolder viewHolder) {
      // sUICallback.clearView(viewHolder.itemView);
    }

    /**
     * Called by ItemTouchHelper on RecyclerView's onDraw callback.
     * <p>
     * If you would like to customize how your View's respond to user interactions, this is
     * a good place to override.
     * <p>
     * Default implementation translates the child by the given <code>dX</code>,
     * <code>dY</code>.
     * ItemTouchHelper also takes care of drawing the child after other children if it is being
     * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
     * is
     * achieved via {@link android.view.ViewGroup#getChildDrawingOrder(int, int)} and on L
     * and after, it changes View's elevation value to be greater than all other children.)
     *
     * @param c                 The canvas which RecyclerView is drawing its children
     * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
     * @param viewHolder        The ViewHolder which is being interacted by the User or it was
     *                          interacted and simply animating to its original position
     * @param dX                The amount of horizontal displacement caused by user's action
     * @param dY                The amount of vertical displacement caused by user's action
     * @param actionState       The type of interaction on the View. Is either {@link
     *                          #ACTION_STATE_DRAG} or {@link #ACTION_STATE_SWIPE}.
     * @param isCurrentlyActive True if this view is currently being controlled by the user or
     *                          false it is simply animating back to its original state.
     * @see #onChildDrawOver(Canvas, RecyclerView, ViewHolder, float, float, int,
     * boolean)
     */
    public void onChildDraw(Canvas c, RecyclerView recyclerView,
                            ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
      // sUICallback.onDraw(c, recyclerView, viewHolder.itemView, dX, dY, actionState, isCurrentlyActive);
    }

    /**
     * Called by ItemTouchHelper on RecyclerView's onDraw callback.
     * <p>
     * If you would like to customize how your View's respond to user interactions, this is
     * a good place to override.
     * <p>
     * Default implementation translates the child by the given <code>dX</code>,
     * <code>dY</code>.
     * ItemTouchHelper also takes care of drawing the child after other children if it is being
     * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
     * is
     * achieved via {@link android.view.ViewGroup#getChildDrawingOrder(int, int)} and on L
     * and after, it changes View's elevation value to be greater than all other children.)
     *
     * @param c                 The canvas which RecyclerView is drawing its children
     * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
     * @param viewHolder        The ViewHolder which is being interacted by the User or it was
     *                          interacted and simply animating to its original position
     * @param dX                The amount of horizontal displacement caused by user's action
     * @param dY                The amount of vertical displacement caused by user's action
     * @param actionState       The type of interaction on the View. Is either {@link
     *                          #ACTION_STATE_DRAG} or {@link #ACTION_STATE_SWIPE}.
     * @param isCurrentlyActive True if this view is currently being controlled by the user or
     *                          false it is simply animating back to its original state.
     * @see #onChildDrawOver(Canvas, RecyclerView, ViewHolder, float, float, int,
     * boolean)
     */
    public void onChildDrawOver(Canvas c, RecyclerView recyclerView,
                                ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
      // sUICallback.onDrawOver(c, recyclerView, viewHolder.itemView, dX, dY, actionState, isCurrentlyActive);
    }

    /**
     * Called by the ItemTouchHelper when user action finished on a ViewHolder and now the View
     * will be animated to its final position.
     * <p>
     * Default implementation uses ItemAnimator's duration values. If
     * <code>animationType</code> is {@link #ANIMATION_TYPE_DRAG}, it returns
     * {@link RecyclerView.ItemAnimator#getMoveDuration()}, otherwise, it returns
     * {@link RecyclerView.ItemAnimator#getRemoveDuration()}. If RecyclerView does not have
     * any {@link RecyclerView.ItemAnimator} attached, this method returns
     * {@code DEFAULT_DRAG_ANIMATION_DURATION} or {@code DEFAULT_SWIPE_ANIMATION_DURATION}
     * depending on the animation type.
     *
     * @param recyclerView  The RecyclerView to which the ItemTouchHelper is attached to.
     * @param animationType The type of animation. Is one of {@link #ANIMATION_TYPE_DRAG},
     *                      {@link #ANIMATION_TYPE_SWIPE_CANCEL} or
     *                      {@link #ANIMATION_TYPE_SWIPE_SUCCESS}.
     * @param animateDx     The horizontal distance that the animation will offset
     * @param animateDy     The vertical distance that the animation will offset
     * @return The duration for the animation
     */
    public long getAnimationDuration(RecyclerView recyclerView, int animationType,
                                     float animateDx, float animateDy) {
      final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
      if (itemAnimator == null) {
        return animationType == ANIMATION_TYPE_DRAG ? DEFAULT_DRAG_ANIMATION_DURATION
          : DEFAULT_SWIPE_ANIMATION_DURATION;
      } else {
        return animationType == ANIMATION_TYPE_DRAG ? itemAnimator.getMoveDuration()
          : itemAnimator.getRemoveDuration();
      }
    }

    /**
     * Called by the ItemTouchHelper when user is dragging a view out of bounds.
     * <p>
     * You can override this method to decide how much RecyclerView should scroll in response
     * to this action. Default implementation calculates a value based on the amount of View
     * out of bounds and the time it spent there. The longer user keeps the View out of bounds,
     * the faster the list will scroll. Similarly, the larger portion of the View is out of
     * bounds, the faster the RecyclerView will scroll.
     *
     * @param recyclerView        The RecyclerView instance to which ItemTouchHelper is attached
     *                            to.
     * @param viewSize            The total size of the View in scroll direction, excluding
     *                            item decorations.
     * @param viewSizeOutOfBounds The total size of the View that is out of bounds. This value
     *                            is negative if the View is dragged towards left or top edge.
     * @param totalSize           The total size of RecyclerView in the scroll direction.
     * @param msSinceStartScroll  The time passed since View is kept out of bounds.
     *
     * @return The amount that RecyclerView should scroll. Keep in mind that this value will
     * be passed to {@link RecyclerView#scrollBy(int, int)} method.
     */
    public int interpolateOutOfBoundsScroll(RecyclerView recyclerView,
                                            int viewSize, int viewSizeOutOfBounds,
                                            int totalSize, long msSinceStartScroll) {
      final int maxScroll = getMaxDragScroll(recyclerView);
      final int absOutOfBounds = Math.abs(viewSizeOutOfBounds);
      final int direction = (int) Math.signum(viewSizeOutOfBounds);
      // might be negative if other direction
      float outOfBoundsRatio = Math.min(1f, 1f * absOutOfBounds / viewSize);
      final int cappedScroll = (int) (direction * maxScroll *
        sDragViewScrollCapInterpolator.getInterpolation(outOfBoundsRatio));
      final float timeRatio;
      if (msSinceStartScroll > DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS) {
        timeRatio = 1f;
      } else {
        timeRatio = (float) msSinceStartScroll / DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS;
      }
      final int value = (int) (cappedScroll * sDragScrollInterpolator
        .getInterpolation(timeRatio));
      if (value == 0) {
        return viewSizeOutOfBounds > 0 ? 1 : -1;
      }
      return value;
    }
  }

  /**
   * A simple wrapper to the default Callback which you can construct with drag and swipe
   * directions and this class will handle the flag callbacks. You should still override onMove
   * or
   * onSwiped depending on your use case.
   *
   * <pre>
   * ItemTouchHelper mIth = new ItemTouchHelper(
   *     new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
   *         ItemTouchHelper.LEFT) {
   *         public abstract boolean onMove(RecyclerView recyclerView,
   *             ViewHolder viewHolder, ViewHolder target) {
   *             final int fromPos = viewHolder.getAdapterPosition();
   *             final int toPos = viewHolder.getAdapterPosition();
   *             // move item in `fromPos` to `toPos` in adapter.
   *             return true;// true if moved, false otherwise
   *         }
   *         public void onSwiped(ViewHolder viewHolder, int direction) {
   *             // remove from adapter
   *         }
   * });
   * </pre>
   */
  public abstract static class SimpleCallback extends Callback {

    private int mDefaultSwipeDirs;

    private int mDefaultDragDirs;

    /**
     * Creates a Callback for the given drag and swipe allowance. These values serve as
     * defaults
     * and if you want to customize behavior per ViewHolder, you can override
     * {@link #getSwipeDirs(RecyclerView, ViewHolder)}
     * and / or {@link #getDragDirs(RecyclerView, ViewHolder)}.
     *
     * @param dragDirs  Binary OR of direction flags in which the Views can be dragged. Must be
     *                  composed of {@link #LEFT}, {@link #RIGHT}, {@link #START}, {@link
     *                  #END},
     *                  {@link #UP} and {@link #DOWN}.
     * @param swipeDirs Binary OR of direction flags in which the Views can be swiped. Must be
     *                  composed of {@link #LEFT}, {@link #RIGHT}, {@link #START}, {@link
     *                  #END},
     *                  {@link #UP} and {@link #DOWN}.
     */
    public SimpleCallback(int dragDirs, int swipeDirs) {
      mDefaultSwipeDirs = swipeDirs;
      mDefaultDragDirs = dragDirs;
    }

    /**
     * Updates the default swipe directions. For example, you can use this method to toggle
     * certain directions depending on your use case.
     *
     * @param defaultSwipeDirs Binary OR of directions in which the ViewHolders can be swiped.
     */
    public void setDefaultSwipeDirs(int defaultSwipeDirs) {
      mDefaultSwipeDirs = defaultSwipeDirs;
    }

    /**
     * Updates the default drag directions. For example, you can use this method to toggle
     * certain directions depending on your use case.
     *
     * @param defaultDragDirs Binary OR of directions in which the ViewHolders can be dragged.
     */
    public void setDefaultDragDirs(int defaultDragDirs) {
      mDefaultDragDirs = defaultDragDirs;
    }

    /**
     * Returns the swipe directions for the provided ViewHolder.
     * Default implementation returns the swipe directions that was set via constructor or
     * {@link #setDefaultSwipeDirs(int)}.
     *
     * @param recyclerView The RecyclerView to which the ItemTouchHelper is attached to.
     * @param viewHolder   The RecyclerView for which the swipe direction is queried.
     * @return A binary OR of direction flags.
     */
    public int getSwipeDirs(RecyclerView recyclerView, ViewHolder viewHolder) {
      return mDefaultSwipeDirs;
    }

    /**
     * Returns the drag directions for the provided ViewHolder.
     * Default implementation returns the drag directions that was set via constructor or
     * {@link #setDefaultDragDirs(int)}.
     *
     * @param recyclerView The RecyclerView to which the ItemTouchHelper is attached to.
     * @param viewHolder   The RecyclerView for which the swipe direction is queried.
     * @return A binary OR of direction flags.
     */
    public int getDragDirs(RecyclerView recyclerView, ViewHolder viewHolder) {
      return mDefaultDragDirs;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, ViewHolder viewHolder) {
      return makeMovementFlags(getDragDirs(recyclerView, viewHolder),
        getSwipeDirs(recyclerView, viewHolder));
    }
  }

  private class ItemTouchHelperGestureListener extends GestureDetector.SimpleOnGestureListener {

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
      View child = findChildView(e);
      if (child != null) {
        ViewHolder vh = mRecyclerView.getChildViewHolder(child);
        if (vh != null) {
          if (!mCallback.hasDragFlag(mRecyclerView, vh)) {
            return;
          }
          int pointerId = MotionEventCompat.getPointerId(e, 0);
          // Long press is deferred.
          // Check w/ active pointer id to avoid selecting after motion
          // event is canceled.
          if (pointerId == mActivePointerId) {
            final int index = MotionEventCompat
              .findPointerIndex(e, mActivePointerId);
            final float x = MotionEventCompat.getX(e, index);
            final float y = MotionEventCompat.getY(e, index);
            mInitialTouchX = x;
            mInitialTouchY = y;
            mDx = mDy = 0f;
            if (DEBUG) {
              Log.d("onLong press: x:" + mInitialTouchX + ",y:" + mInitialTouchY);
            }
            if (mCallback.isLongPressDragEnabled()) {
              select(vh, ACTION_STATE_DRAG);
            }
          }
        }
      }
    }
  }

  private class RecoverAnimation implements Animator.AnimatorListener {

    final float mStartDx;

    final float mStartDy;

    final float mTargetX;

    final float mTargetY;

    final ViewHolder mViewHolder;

    final int mActionState;

    private final ValueAnimator mValueAnimator;

    private final int mAnimationType;

    public boolean mIsPendingCleanup;

    float mX;

    float mY;

    // if user starts touching a recovering view, we put it into interaction mode again,
    // instantly.
    boolean mOverridden = false;

    private boolean mEnded = false;

    private float mFraction;

    public RecoverAnimation(ViewHolder viewHolder, int animationType,
                            int actionState, float startDx, float startDy, float targetX, float targetY) {
      mActionState = actionState;
      mAnimationType = animationType;
      mViewHolder = viewHolder;
      mStartDx = startDx;
      mStartDy = startDy;
      mTargetX = targetX;
      mTargetY = targetY;
      mValueAnimator = ValueAnimator.ofFloat(0f, 1f);
      mValueAnimator.addUpdateListener(
        animation -> setFraction(AnimatorUtils.getFraction(animation)));
      mValueAnimator.setTarget(viewHolder.itemView);
      mValueAnimator.addListener(this);
      setFraction(0f);
    }

    public void setDuration(long duration) {
      mValueAnimator.setDuration(duration);
    }

    public void start() {
      mViewHolder.setIsRecyclable(false);
      mValueAnimator.start();
    }

    public void cancel() {
      mValueAnimator.cancel();
    }

    public void setFraction(float fraction) {
      mFraction = fraction;
    }

    /**
     * We run updates on onDraw method but use the fraction from animator callback.
     * This way, we can sync translate x/y values w/ the animators to avoid one-off frames.
     */
    public void update() {
      if (mStartDx == mTargetX) {
        mX = ViewCompat.getTranslationX(mViewHolder.itemView);
      } else {
        mX = mStartDx + mFraction * (mTargetX - mStartDx);
      }
      if (mStartDy == mTargetY) {
        mY = ViewCompat.getTranslationY(mViewHolder.itemView);
      } else {
        mY = mStartDy + mFraction * (mTargetY - mStartDy);
      }
    }

    @Override
    public void onAnimationStart (Animator animator) {

    }

    @Override
    public void onAnimationEnd (Animator animator) {
      if (!mEnded) {
        mViewHolder.setIsRecyclable(true);
      }
      mEnded = true;
    }

    @Override
    public void onAnimationCancel (Animator animator) {
      setFraction(1f); //make sure we recover the view's state.
    }

    @Override
    public void onAnimationRepeat (Animator animator) {

    }
  }
}