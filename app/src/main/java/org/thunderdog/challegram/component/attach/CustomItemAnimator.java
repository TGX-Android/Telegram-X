package org.thunderdog.challegram.component.attach;

import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorCompat;
import androidx.core.view.ViewPropertyAnimatorListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 25/10/2016
 * Author: default
 */

public class CustomItemAnimator extends SimpleItemAnimator {
  private static final boolean DEBUG = false;

  private ArrayList<RecyclerView.ViewHolder> mPendingRemovals = new ArrayList<>();
  private ArrayList<RecyclerView.ViewHolder> mPendingAdditions = new ArrayList<>();
  private ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
  private ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();

  ArrayList<ArrayList<RecyclerView.ViewHolder>> mAdditionsList = new ArrayList<>();
  ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();
  ArrayList<ArrayList<ChangeInfo>> mChangesList = new ArrayList<>();

  ArrayList<RecyclerView.ViewHolder> mAddAnimations = new ArrayList<>();
  ArrayList<RecyclerView.ViewHolder> mMoveAnimations = new ArrayList<>();
  ArrayList<RecyclerView.ViewHolder> mRemoveAnimations = new ArrayList<>();
  ArrayList<RecyclerView.ViewHolder> mChangeAnimations = new ArrayList<>();

  private static class MoveInfo {
    public RecyclerView.ViewHolder holder;
    public int fromX, fromY, toX, toY;

    MoveInfo(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
      this.holder = holder;
      this.fromX = fromX;
      this.fromY = fromY;
      this.toX = toX;
      this.toY = toY;
    }
  }

  private static class ChangeInfo {
    public RecyclerView.ViewHolder oldHolder, newHolder;
    public int fromX, fromY, toX, toY;
    private ChangeInfo(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder) {
      this.oldHolder = oldHolder;
      this.newHolder = newHolder;
    }

    ChangeInfo(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
               int fromX, int fromY, int toX, int toY) {
      this(oldHolder, newHolder);
      this.fromX = fromX;
      this.fromY = fromY;
      this.toX = toX;
      this.toY = toY;
    }

    @Override
    public String toString() {
      return "ChangeInfo{" +
          "oldHolder=" + oldHolder +
          ", newHolder=" + newHolder +
          ", fromX=" + fromX +
          ", fromY=" + fromY +
          ", toX=" + toX +
          ", toY=" + toY +
          '}';
    }
  }

  private final Interpolator customInterpolator;

  public CustomItemAnimator (Interpolator customInterpolator) {
    this.customInterpolator = customInterpolator;
  }

  public CustomItemAnimator (Interpolator customInterpolator, long defaultDuration) {
    this.customInterpolator = customInterpolator;
    setMoveDuration(defaultDuration);
    setAddDuration(defaultDuration);
    setChangeDuration(defaultDuration);
    setRemoveDuration(defaultDuration);
  }

  @Override
  public void runPendingAnimations() {
    boolean removalsPending = !mPendingRemovals.isEmpty();
    boolean movesPending = !mPendingMoves.isEmpty();
    boolean changesPending = !mPendingChanges.isEmpty();
    boolean additionsPending = !mPendingAdditions.isEmpty();
    if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
      // nothing to animate
      return;
    }
    // First, remove stuff
    for (RecyclerView.ViewHolder holder : mPendingRemovals) {
      animateRemoveImpl(holder);
    }
    mPendingRemovals.clear();
    // Next, move stuff
    if (movesPending) {
      final ArrayList<MoveInfo> moves = new ArrayList<>();
      moves.addAll(mPendingMoves);
      mMovesList.add(moves);
      mPendingMoves.clear();
      Runnable mover = () -> {
        for (MoveInfo moveInfo : moves) {
          animateMoveImpl(moveInfo.holder, moveInfo.fromX, moveInfo.fromY,
              moveInfo.toX, moveInfo.toY);
        }
        moves.clear();
        mMovesList.remove(moves);
      };
      if (removalsPending) {
        View view = moves.get(0).holder.itemView;
        ViewCompat.postOnAnimationDelayed(view, mover, getRemoveDuration());
      } else {
        mover.run();
      }
    }
    // Next, change stuff, to run in parallel with move animations
    if (changesPending) {
      final ArrayList<ChangeInfo> changes = new ArrayList<>();
      changes.addAll(mPendingChanges);
      mChangesList.add(changes);
      mPendingChanges.clear();
      Runnable changer = () -> {
        for (ChangeInfo change : changes) {
          animateChangeImpl(change);
        }
        changes.clear();
        mChangesList.remove(changes);
      };
      if (removalsPending) {
        RecyclerView.ViewHolder holder = changes.get(0).oldHolder;
        ViewCompat.postOnAnimationDelayed(holder.itemView, changer, getRemoveDuration());
      } else {
        changer.run();
      }
    }
    // Next, add stuff
    if (additionsPending) {
      final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>();
      additions.addAll(mPendingAdditions);
      mAdditionsList.add(additions);
      mPendingAdditions.clear();
      Runnable adder = () -> {
        for (RecyclerView.ViewHolder holder : additions) {
          animateAddImpl(holder);
        }
        additions.clear();
        mAdditionsList.remove(additions);
      };
      if (removalsPending || movesPending || changesPending) {
        long removeDuration = removalsPending ? getRemoveDuration() : 0;
        long moveDuration = movesPending ? getMoveDuration() : 0;
        long changeDuration = changesPending ? getChangeDuration() : 0;
        long totalDelay = removeDuration + Math.max(moveDuration, changeDuration);
        View view = additions.get(0).itemView;
        ViewCompat.postOnAnimationDelayed(view, adder, totalDelay);
      } else {
        adder.run();
      }
    }
  }

  @Override
  public boolean animateRemove(final RecyclerView.ViewHolder holder) {
    if (getRemoveDuration() <= 0) {
      return false;
    }
    resetAnimation(holder);
    mPendingRemovals.add(holder);
    return true;
  }

  private void animateRemoveImpl(final RecyclerView.ViewHolder holder) {
    final View view = holder.itemView;
    final ViewPropertyAnimatorCompat animation = animate(view);
    mRemoveAnimations.add(holder);
    animation.setDuration(getRemoveDuration()).setInterpolator(customInterpolator)
        .alpha(0).setListener(new VpaListenerAdapter() {
      @Override
      public void onAnimationStart(View view) {
        dispatchRemoveStarting(holder);
      }

      @Override
      public void onAnimationEnd(View view) {
        animation.setListener(null);
        setAlpha(view, 1);
        dispatchRemoveFinished(holder);
        mRemoveAnimations.remove(holder);
        dispatchFinishedWhenDone();
      }
    }).start();
  }

  @Override
  public boolean animateAdd(final RecyclerView.ViewHolder holder) {
    if (getAddDuration() <= 0) {
      return false;
    }
    resetAnimation(holder);
    setAlpha(holder.itemView, 0);
    mPendingAdditions.add(holder);
    return true;
  }

  void animateAddImpl(final RecyclerView.ViewHolder holder) {
    final View view = holder.itemView;
    final ViewPropertyAnimatorCompat animation = animate(view);
    mAddAnimations.add(holder);
    animation.alpha(1).setDuration(getAddDuration()).setInterpolator(customInterpolator).
        setListener(new VpaListenerAdapter() {
          @Override
          public void onAnimationStart(View view) {
            dispatchAddStarting(holder);
          }
          @Override
          public void onAnimationCancel(View view) {
            setAlpha(view, 1);
          }

          @Override
          public void onAnimationEnd(View view) {
            animation.setListener(null);
            dispatchAddFinished(holder);
            mAddAnimations.remove(holder);
            dispatchFinishedWhenDone();
          }
        }).start();
  }

  @Override
  public boolean animateMove(final RecyclerView.ViewHolder holder, int fromX, int fromY,
                             int toX, int toY) {
    if (getMoveDuration() <= 0) {
      return false;
    }
    final View view = holder.itemView;
    fromX += getTranslationX(holder.itemView);
    fromY += getTranslationY(holder.itemView);
    resetAnimation(holder);
    int deltaX = toX - fromX;
    int deltaY = toY - fromY;
    if (deltaX == 0 && deltaY == 0) {
      dispatchMoveFinished(holder);
      return false;
    }
    if (deltaX != 0) {
      setTranslationX(view, -deltaX);
    }
    if (deltaY != 0) {
      setTranslationY(view, -deltaY);
    }
    mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
    return true;
  }

  void animateMoveImpl(final RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
    final View view = holder.itemView;
    final int deltaX = toX - fromX;
    final int deltaY = toY - fromY;
    if (deltaX != 0) {
      animate(view).translationX(0);
    }
    if (deltaY != 0) {
      animate(view).translationY(0);
    }
    // TODO: make EndActions end listeners instead, since end actions aren't called when
    // vpas are canceled (and can't end them. why?)
    // need listener functionality in VPACompat for this. Ick.
    final ViewPropertyAnimatorCompat animation = animate(view);
    mMoveAnimations.add(holder);
    animation.setDuration(getMoveDuration()).setInterpolator(customInterpolator).setListener(new VpaListenerAdapter() {
      @Override
      public void onAnimationStart(View view) {
        dispatchMoveStarting(holder);
      }
      @Override
      public void onAnimationCancel(View view) {
        if (deltaX != 0) {
          setTranslationX(view, 0);
        }
        if (deltaY != 0) {
          setTranslationY(view, 0);
        }
      }
      @Override
      public void onAnimationEnd(View view) {
        animation.setListener(null);
        dispatchMoveFinished(holder);
        mMoveAnimations.remove(holder);
        dispatchFinishedWhenDone();
      }
    }).start();
  }

  @Override
  public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
                               int fromX, int fromY, int toX, int toY) {
    if (getChangeDuration() <= 0) {
      return false;
    }
    if (oldHolder == newHolder) {
      // Don't know how to run change animations when the same view holder is re-used.
      // run a move animation to handle position changes.
      return animateMove(oldHolder, fromX, fromY, toX, toY);
    }
    final float prevTranslationX = getTranslationX(oldHolder.itemView);
    final float prevTranslationY = getTranslationY(oldHolder.itemView);
    final float prevAlpha = getAlpha(oldHolder.itemView);
    resetAnimation(oldHolder);
    int deltaX = (int) (toX - fromX - prevTranslationX);
    int deltaY = (int) (toY - fromY - prevTranslationY);
    // recover prev translation state after ending animation
    setTranslationX(oldHolder.itemView, prevTranslationX);
    setTranslationY(oldHolder.itemView, prevTranslationY);
    setAlpha(oldHolder.itemView, prevAlpha);
    if (newHolder != null) {
      // carry over translation values
      resetAnimation(newHolder);
      setTranslationX(newHolder.itemView, -deltaX);
      setTranslationY(newHolder.itemView, -deltaY);
      setAlpha(newHolder.itemView, 0);
    }
    mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
    return true;
  }

  void animateChangeImpl(final ChangeInfo changeInfo) {
    final RecyclerView.ViewHolder holder = changeInfo.oldHolder;
    final View view = holder == null ? null : holder.itemView;
    final RecyclerView.ViewHolder newHolder = changeInfo.newHolder;
    final View newView = newHolder != null ? newHolder.itemView : null;
    if (view != null) {
      final ViewPropertyAnimatorCompat oldViewAnim = animate(view).setDuration(
          getChangeDuration()).setInterpolator(customInterpolator);
      mChangeAnimations.add(changeInfo.oldHolder);
      oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
      oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
      oldViewAnim.alpha(0).setListener(new VpaListenerAdapter() {
        @Override
        public void onAnimationStart(View view) {
          dispatchChangeStarting(changeInfo.oldHolder, true);
        }

        @Override
        public void onAnimationEnd(View view) {
          oldViewAnim.setListener(null);
          setAlpha(view, 1);
          setTranslationX(view, 0);
          setTranslationY(view, 0);
          dispatchChangeFinished(changeInfo.oldHolder, true);
          mChangeAnimations.remove(changeInfo.oldHolder);
          dispatchFinishedWhenDone();
        }
      }).start();
    }
    if (newView != null) {
      final ViewPropertyAnimatorCompat newViewAnimation = animate(newView);
      mChangeAnimations.add(changeInfo.newHolder);
      newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration()).setInterpolator(customInterpolator).
          alpha(1).setListener(new VpaListenerAdapter() {
        @Override
        public void onAnimationStart(View view) {
          dispatchChangeStarting(changeInfo.newHolder, false);
        }
        @Override
        public void onAnimationEnd(View view) {
          newViewAnimation.setListener(null);
          setAlpha(newView, 1);
          setTranslationX(newView, 0);
          setTranslationY(newView, 0);
          dispatchChangeFinished(changeInfo.newHolder, false);
          mChangeAnimations.remove(changeInfo.newHolder);
          dispatchFinishedWhenDone();
        }
      }).start();
    }
  }

  private void endChangeAnimation(List<ChangeInfo> infoList, RecyclerView.ViewHolder item) {
    for (int i = infoList.size() - 1; i >= 0; i--) {
      ChangeInfo changeInfo = infoList.get(i);
      if (endChangeAnimationIfNecessary(changeInfo, item)) {
        if (changeInfo.oldHolder == null && changeInfo.newHolder == null) {
          infoList.remove(changeInfo);
        }
      }
    }
  }

  private void endChangeAnimationIfNecessary(ChangeInfo changeInfo) {
    if (changeInfo.oldHolder != null) {
      endChangeAnimationIfNecessary(changeInfo, changeInfo.oldHolder);
    }
    if (changeInfo.newHolder != null) {
      endChangeAnimationIfNecessary(changeInfo, changeInfo.newHolder);
    }
  }

  protected ViewPropertyAnimatorCompat animate (View view) {
    return ViewCompat.animate(view);
  }

  protected float getAlpha (View view) {
    return ViewCompat.getAlpha(view);
  }

  protected void setAlpha (View view, float value) {
    ViewCompat.setAlpha(view, value);
  }

  protected float getTranslationX (View view) {
    return ViewCompat.getTranslationX(view);
  }

  protected void setTranslationX (View view, float value) {
    ViewCompat.setTranslationX(view, value);
  }

  protected float getTranslationY (View view) {
    return ViewCompat.getTranslationY(view);
  }

  protected void setTranslationY (View view, float value) {
    ViewCompat.setTranslationY(view, value);
  }

  private boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, RecyclerView.ViewHolder item) {
    boolean oldItem = false;
    if (changeInfo.newHolder == item) {
      changeInfo.newHolder = null;
    } else if (changeInfo.oldHolder == item) {
      changeInfo.oldHolder = null;
      oldItem = true;
    } else {
      return false;
    }
    setAlpha(item.itemView, 1);
    setTranslationX(item.itemView, 0);
    setTranslationY(item.itemView, 0);
    dispatchChangeFinished(item, oldItem);
    return true;
  }

  @Override
  public void endAnimation(RecyclerView.ViewHolder item) {
    final View view = item.itemView;
    // this will trigger end callback which should set properties to their target values.
    animate(view).cancel();
    // TODO if some other animations are chained to end, how do we cancel them as well?
    for (int i = mPendingMoves.size() - 1; i >= 0; i--) {
      MoveInfo moveInfo = mPendingMoves.get(i);
      if (moveInfo.holder == item) {
        setTranslationY(view, 0);
        setTranslationX(view, 0);
        dispatchMoveFinished(item);
        mPendingMoves.remove(i);
      }
    }
    endChangeAnimation(mPendingChanges, item);
    if (mPendingRemovals.remove(item)) {
      setAlpha(view, 1);
      dispatchRemoveFinished(item);
    }
    if (mPendingAdditions.remove(item)) {
      setAlpha(view, 1);
      dispatchAddFinished(item);
    }

    for (int i = mChangesList.size() - 1; i >= 0; i--) {
      ArrayList<ChangeInfo> changes = mChangesList.get(i);
      endChangeAnimation(changes, item);
      if (changes.isEmpty()) {
        mChangesList.remove(i);
      }
    }
    for (int i = mMovesList.size() - 1; i >= 0; i--) {
      ArrayList<MoveInfo> moves = mMovesList.get(i);
      for (int j = moves.size() - 1; j >= 0; j--) {
        MoveInfo moveInfo = moves.get(j);
        if (moveInfo.holder == item) {
          setTranslationY(view, 0);
          setTranslationX(view, 0);
          dispatchMoveFinished(item);
          moves.remove(j);
          if (moves.isEmpty()) {
            mMovesList.remove(i);
          }
          break;
        }
      }
    }
    for (int i = mAdditionsList.size() - 1; i >= 0; i--) {
      ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
      if (additions.remove(item)) {
        setAlpha(view, 1);
        dispatchAddFinished(item);
        if (additions.isEmpty()) {
          mAdditionsList.remove(i);
        }
      }
    }

    // animations should be ended by the cancel above.
    //noinspection PointlessBooleanExpression,ConstantConditions
    if (mRemoveAnimations.remove(item) && DEBUG) {
      throw new IllegalStateException("after animation is cancelled, item should not be in "
          + "mRemoveAnimations list");
    }

    //noinspection PointlessBooleanExpression,ConstantConditions
    if (mAddAnimations.remove(item) && DEBUG) {
      throw new IllegalStateException("after animation is cancelled, item should not be in "
          + "mAddAnimations list");
    }

    //noinspection PointlessBooleanExpression,ConstantConditions
    if (mChangeAnimations.remove(item) && DEBUG) {
      throw new IllegalStateException("after animation is cancelled, item should not be in "
          + "mChangeAnimations list");
    }

    //noinspection PointlessBooleanExpression,ConstantConditions
    if (mMoveAnimations.remove(item) && DEBUG) {
      throw new IllegalStateException("after animation is cancelled, item should not be in "
          + "mMoveAnimations list");
    }
    dispatchFinishedWhenDone();
  }

  private void resetAnimation(RecyclerView.ViewHolder holder) {
    holder.itemView.animate().setInterpolator(customInterpolator);
    endAnimation(holder);
  }

  @Override
  public boolean isRunning() {
    return (!mPendingAdditions.isEmpty() ||
        !mPendingChanges.isEmpty() ||
        !mPendingMoves.isEmpty() ||
        !mPendingRemovals.isEmpty() ||
        !mMoveAnimations.isEmpty() ||
        !mRemoveAnimations.isEmpty() ||
        !mAddAnimations.isEmpty() ||
        !mChangeAnimations.isEmpty() ||
        !mMovesList.isEmpty() ||
        !mAdditionsList.isEmpty() ||
        !mChangesList.isEmpty());
  }

  /**
   * Check the state of currently pending and running animations. If there are none
   * pending/running, call {@link #dispatchAnimationsFinished()} to notify any
   * listeners.
   */
  void dispatchFinishedWhenDone() {
    if (!isRunning()) {
      dispatchAnimationsFinished();
    }
  }

  @Override
  public void endAnimations() {
    int count = mPendingMoves.size();
    for (int i = count - 1; i >= 0; i--) {
      MoveInfo item = mPendingMoves.get(i);
      View view = item.holder.itemView;
      setTranslationY(view, 0);
      setTranslationX(view, 0);
      dispatchMoveFinished(item.holder);
      mPendingMoves.remove(i);
    }
    count = mPendingRemovals.size();
    for (int i = count - 1; i >= 0; i--) {
      RecyclerView.ViewHolder item = mPendingRemovals.get(i);
      dispatchRemoveFinished(item);
      mPendingRemovals.remove(i);
    }
    count = mPendingAdditions.size();
    for (int i = count - 1; i >= 0; i--) {
      RecyclerView.ViewHolder item = mPendingAdditions.get(i);
      View view = item.itemView;
      setAlpha(view, 1);
      dispatchAddFinished(item);
      mPendingAdditions.remove(i);
    }
    count = mPendingChanges.size();
    for (int i = count - 1; i >= 0; i--) {
      endChangeAnimationIfNecessary(mPendingChanges.get(i));
    }
    mPendingChanges.clear();
    if (!isRunning()) {
      return;
    }

    int listCount = mMovesList.size();
    for (int i = listCount - 1; i >= 0; i--) {
      ArrayList<MoveInfo> moves = mMovesList.get(i);
      count = moves.size();
      for (int j = count - 1; j >= 0; j--) {
        MoveInfo moveInfo = moves.get(j);
        RecyclerView.ViewHolder item = moveInfo.holder;
        View view = item.itemView;
        setTranslationY(view, 0);
        setTranslationX(view, 0);
        dispatchMoveFinished(moveInfo.holder);
        moves.remove(j);
        if (moves.isEmpty()) {
          mMovesList.remove(moves);
        }
      }
    }
    listCount = mAdditionsList.size();
    for (int i = listCount - 1; i >= 0; i--) {
      ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
      count = additions.size();
      for (int j = count - 1; j >= 0; j--) {
        RecyclerView.ViewHolder item = additions.get(j);
        View view = item.itemView;
        setAlpha(view, 1);
        dispatchAddFinished(item);
        additions.remove(j);
        if (additions.isEmpty()) {
          mAdditionsList.remove(additions);
        }
      }
    }
    listCount = mChangesList.size();
    for (int i = listCount - 1; i >= 0; i--) {
      ArrayList<ChangeInfo> changes = mChangesList.get(i);
      count = changes.size();
      for (int j = count - 1; j >= 0; j--) {
        endChangeAnimationIfNecessary(changes.get(j));
        if (changes.isEmpty()) {
          mChangesList.remove(changes);
        }
      }
    }

    cancelAll(mRemoveAnimations);
    cancelAll(mMoveAnimations);
    cancelAll(mAddAnimations);
    cancelAll(mChangeAnimations);

    dispatchAnimationsFinished();
  }

  void cancelAll(List<RecyclerView.ViewHolder> viewHolders) {
    for (int i = viewHolders.size() - 1; i >= 0; i--) {
      animate(viewHolders.get(i).itemView).cancel();
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * If the payload list is not empty, DefaultItemAnimator returns <code>true</code>.
   * When this is the case:
   * <ul>
   * <li>If you override {@link #animateChange(RecyclerView.ViewHolder, RecyclerView.ViewHolder, int, int, int, int)}, both
   * ViewHolder arguments will be the same instance.
   * </li>
   * <li>
   * If you are not overriding {@link #animateChange(RecyclerView.ViewHolder, RecyclerView.ViewHolder, int, int, int, int)},
   * then DefaultItemAnimator will call {@link #animateMove(RecyclerView.ViewHolder, int, int, int, int)} and
   * run a move animation instead.
   * </li>
   * </ul>
   */
  @Override
  public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder,
                                           @NonNull List<Object> payloads) {
    return !payloads.isEmpty() || super.canReuseUpdatedViewHolder(viewHolder, payloads);
  }

  private static class VpaListenerAdapter implements ViewPropertyAnimatorListener {
    VpaListenerAdapter() {
    }

    @Override
    public void onAnimationStart(View view) {}

    @Override
    public void onAnimationEnd(View view) {}

    @Override
    public void onAnimationCancel(View view) {}
  }
}