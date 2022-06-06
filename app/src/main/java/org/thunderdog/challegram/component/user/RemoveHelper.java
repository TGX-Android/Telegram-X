/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.component.user;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class RemoveHelper implements FactorAnimator.Target {
  public interface RemoveDelegate {
    void setRemoveDx (float dx);
    void onRemoveSwipe ();
  }

  private View view;
  private Drawable icon;

  private float dx;

  public RemoveHelper (View view, @DrawableRes int icon) {
    this.view = view;
    this.icon = Drawables.get(view.getResources(), icon);
  }

  public void setDx (float dx) {
    if (this.dx != dx) {
      this.dx = dx;
      view.invalidate();
    }
  }

  public void reset () {
    this.dx = 0;
    if (animator != null) {
      animator.forceFactor(0f);
    }
    fadeFactor = 0f;
  }

  private FactorAnimator animator;
  private boolean fadingOut;

  public void onSwipe () {
    ViewUtils.onClick(view);
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else {
      animator.forceFactor(0f);
    }
    fadingOut = true;
    dx = 0f;
    animator.animateTo(1f);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.fadeFactor != factor) {
      this.fadeFactor = factor;
      view.invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    animator.forceFactor(0f);
    fadingOut = false;
    fadeFactor = 0f;
  }

  private boolean saved = false;

  public void save (Canvas c) {
    if ((saved = dx != 0 && fadeFactor == 0f)) {
      c.save();
      c.translate(dx, 0);
    }
  }

  public void restore (Canvas c) {
    if (saved) {
      saved = false;
      c.restore();
    }
  }

  private float fadeFactor;

  public void draw (Canvas c) {
    final int width = view.getMeasuredWidth();
    final int height = view.getMeasuredHeight();

    final int left;
    final int right;

    if (fadeFactor > 0f || fadingOut) {
      left = 0;
      right = width;
    } else if (Lang.rtl()) {
      left = 0;
      right = Math.max(0, Math.min(width, (int) dx));
    } else {
      left = Math.max(0, Math.min(width, (int) (width + dx)));
      right = width;
    }

    if (left == right) {
      return;
    }


    float alpha = (1f - fadeFactor);
    Paint bitmapPaint = Paints.getPorterDuffPaint(0xffffffff);
    if (alpha < 1f) {
      bitmapPaint.setAlpha((int) (255f * alpha));
    }
    int color = ColorUtils.alphaColor(alpha, Theme.getColor(R.id.theme_color_fillingNegative));
    int iconX = Lang.rtl() ? Screen.dp(18f) : right - Screen.dp(18f) - icon.getMinimumWidth();
    int iconY = height / 2 - icon.getMinimumHeight() / 2;

    if (left == 0 && right == width) {
      c.drawColor(color);
      Drawables.draw(c, icon, iconX, iconY, bitmapPaint);
    } else {
      c.save();
      c.clipRect(left, 0, right, height);
      c.drawRect(left, 0, right, height, Paints.fillingPaint(color));
      Drawables.draw(c, icon, iconX, iconY, bitmapPaint);
      c.restore();
    }

    if (alpha < 1f) {
      bitmapPaint.setAlpha(255);
    }
  }

  public interface Callback {
    boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position);
    void onRemove (RecyclerView.ViewHolder viewHolder);
    default float getRemoveThresholdWidth () {
      return 68f;
    }
  }

  public interface ExtendedCallback extends Callback {
    int makeDragFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder);
    boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target);
    void onCompleteMovement (int fromPosition, int toPosition);
    boolean isLongPressDragEnabled ();
  }

  public static ItemTouchHelper attach (RecyclerView recyclerView, final Callback callback) {
    final ItemTouchHelper[] itemTouchHelper = new ItemTouchHelper[1];

    itemTouchHelper[0] = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      @Override
      public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int dragFlags = callback instanceof ExtendedCallback ? ((ExtendedCallback) callback).makeDragFlags(recyclerView, viewHolder) : 0;
        int movementFlags = recyclerView.getLayoutManager() != null && recyclerView.getAdapter() != null && recyclerView.getAdapter().getItemCount() > 0 && callback.canRemove(recyclerView, viewHolder, viewHolder.getAdapterPosition()) ? (Lang.rtl() ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT) : 0;
        return dragFlags != 0 || movementFlags != 0 ? makeMovementFlags(dragFlags, movementFlags) : 0;
      }

      @Override
      public boolean isItemViewSwipeEnabled () {
        return true;
      }

      @Override
      public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
          super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
        RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
        removeView.setRemoveDx(dX);
      }

      @Override
      public float getSwipeThreshold (RecyclerView.ViewHolder viewHolder) {
        return (float) Screen.dp(callback.getRemoveThresholdWidth()) / (float) viewHolder.itemView.getMeasuredWidth();
      }

      @Override
      public void onSwiped (RecyclerView.ViewHolder viewHolder, int direction) {
        itemTouchHelper[0].onChildViewDetachedFromWindow(viewHolder.itemView);
        if (direction == (Lang.rtl() ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT)) {
          RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
          removeView.onRemoveSwipe();
          callback.onRemove(viewHolder);
        }
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return callback instanceof ExtendedCallback && ((ExtendedCallback) callback).isLongPressDragEnabled();
      }

      private int dragFrom = -1;
      private int dragTo = -1;

      @Override
      public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        viewHolder.itemView.invalidate();
        target.itemView.invalidate();
      }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        if (callback instanceof ExtendedCallback && ((ExtendedCallback) callback).onMove(recyclerView, viewHolder, target)) {
          if (dragFrom == -1)
            dragFrom = fromPosition;
          dragTo = toPosition;
          return true;
        }

        return false;
      }

      private void reallyMoved (int from, int to) {
        if (callback instanceof ExtendedCallback) {
          ((ExtendedCallback) callback).onCompleteMovement(from, to);
        }
      }

      @Override
      public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
          reallyMoved(dragFrom, dragTo);
        }
        dragFrom = dragTo = -1;
      }
    });
    itemTouchHelper[0].attachToRecyclerView(recyclerView);

    return itemTouchHelper[0];
  }
}
