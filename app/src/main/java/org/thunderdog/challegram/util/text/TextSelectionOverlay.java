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
 * File created on 25/09/2026
 */
package org.thunderdog.challegram.util.text;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

class TextSelectionOverlay implements FactorAnimator.Target {
  private static final int ANIMATOR_VISIBILITY = 0;
  private static final long SHOW_DURATION = 150L;
  private static final long HIDE_DURATION = 100L;

  private final View anchor;
  private final Text text;
  private final Text.Selection selection;
  private final @Nullable Text.SelectionDelegate delegate;
  private final ViewGroup root;
  private final View overlay;
  private final View viewport;
  private final @Nullable RecyclerView recyclerView;
  private final ViewTreeObserver observer;
  private final BoolAnimator visibilityAnimator;
  private final Drawable leftHandle, rightHandle;
  private Drawable startDrawable, endDrawable;

  private final Rect scrollBounds = new Rect();
  private final Rect anchorBounds = new Rect(), textBounds = new Rect();
  private final RectF startBounds = new RectF(), endBounds = new RectF();
  private final RectF selectAllAnchor = new RectF();
  private final int[] location = new int[2], rootLocation = new int[2];

  private @Nullable ActionMode actionMode;
  private @Nullable OnBackInvokedDispatcher backDispatcher;
  private @Nullable OnBackInvokedCallback backCallback;

  private float originX, originY;
  private int geometryRevision = -1, menuRevision = -1;
  private boolean dismissed, removed;
  private boolean opening = true;
  private boolean windowFocused = true;
  private boolean toolbarHidden;

  private final RecyclerView.OnItemTouchListener scrollTouchListener = new RecyclerView.SimpleOnItemTouchListener() {
    private float downX, downY;
    private boolean tap;

    @Override
    public boolean onInterceptTouchEvent (RecyclerView view, MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = event.getX();
          downY = event.getY();
          tap = view.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
          break;
        case MotionEvent.ACTION_MOVE:
          if (Math.max(Math.abs(event.getX() - downX), Math.abs(event.getY() - downY)) > Screen.getTouchSlop()) {
            tap = false;
          }
          break;
        case MotionEvent.ACTION_UP:
          if (tap) {
            tap = false;
            text.dismissSelection(true);
            return true;
          }
          break;
        case MotionEvent.ACTION_POINTER_DOWN:
        case MotionEvent.ACTION_CANCEL:
          tap = false;
          break;
      }
      return false;
    }
  };
  private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrollStateChanged (RecyclerView view, int state) {
      updatePosition();
    }

    @Override
    public void onScrolled (RecyclerView view, int dx, int dy) {
      updatePosition();
    }
  };
  private final Runnable keepToolbarHidden = new Runnable() {
    @Override
    public void run () {
      if (!dismissed && toolbarHidden && actionMode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // ActionMode.hide duration is capped by the system, renew it while scrolling, dragging or out of view
        actionMode.hide(2000);
        overlay.postDelayed(this, 1000);
      }
    }
  };
  private final ViewTreeObserver.OnPreDrawListener positionListener = this::updatePosition;
  private final View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
    @Override
    public void onViewAttachedToWindow (View view) { }

    @Override
    public void onViewDetachedFromWindow (View view) {
      text.dismissSelection();
      finishDismissal();
    }
  };

  TextSelectionOverlay (View anchor, Text text, Text.Selection selection, @Nullable Text.SelectionDelegate delegate) {
    this.anchor = anchor;
    this.text = text;
    this.selection = selection;
    this.delegate = delegate;
    this.root = (ViewGroup) anchor.getRootView();
    ViewParent parent = anchor.getParent();
    while (parent != null && !(parent instanceof RecyclerView)) {
      parent = parent.getParent();
    }
    this.recyclerView = (RecyclerView) parent;
    if (recyclerView != null) {
      this.viewport = recyclerView;
    } else if (anchor.getParent() instanceof View) {
      this.viewport = (View) anchor.getParent();
    } else {
      this.viewport = anchor;
    }
    // Same themed handles as in EditText
    TypedArray handles = anchor.getContext().obtainStyledAttributes(null, new int[] {
      android.R.attr.textSelectHandleLeft, android.R.attr.textSelectHandleRight
    }, android.R.attr.editTextStyle, 0);
    try {
      leftHandle = handles.getDrawable(0);
      rightHandle = handles.getDrawable(1);
    } finally {
      handles.recycle();
    }
    this.overlay = new View(anchor.getContext()) {
      @Override
      public void onWindowFocusChanged (boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        windowFocused = hasWindowFocus;
        if (dismissed) {
          return;
        }
        if (!hasWindowFocus) {
          // Same as EditText: finish toolbar on focus loss, but keep the range.
          // Toolbar is recreated on focus gain with the resumed window geometry.
          opening = true;
          selection.draggingHandle = Text.Selection.HANDLE_NONE;
          visibilityAnimator.setValue(false, false);
          overlay.removeCallbacks(keepToolbarHidden);
          toolbarHidden = false;
          if (actionMode != null) {
            ActionMode mode = actionMode;
            actionMode = null;
            mode.finish();
          }
        } else {
          updatePosition();
        }
      }

      @Override
      protected void onDraw (Canvas canvas) {
        canvas.save();
        canvas.translate(originX, originY);
        drawHandle(canvas, selection.startHandle, startBounds, startDrawable);
        drawHandle(canvas, selection.endHandle, endBounds, endDrawable);
        canvas.restore();
      }

      @Override
      public boolean dispatchTouchEvent (MotionEvent event) {
        // Overlay that is fading out must release input to the host immediately
        return !dismissed && super.dispatchTouchEvent(event);
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        if (!canSelect()) {
          text.dismissSelection();
          return event.getActionMasked() != MotionEvent.ACTION_DOWN;
        }
        float x = event.getX() - originX;
        float y = event.getY() - originY;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && recyclerView != null &&
          selection.findHandleAt(x, y, startBounds, endBounds) == Text.Selection.HANDLE_NONE &&
          recyclerView.getGlobalVisibleRect(scrollBounds) &&
          scrollBounds.contains((int) event.getX(), (int) event.getY())) {
          // RecyclerView handles scrolling, only a completed tap dismisses the selection
          return false;
        }
        boolean wasDragging = selection.draggingHandle != Text.Selection.HANDLE_NONE;
        int previousRevision = selection.revision;
        selection.onTouchEvent(event, x, y, startBounds, endBounds);
        if (selection.draggingHandle != Text.Selection.HANDLE_NONE) {
          selectAllAnchor.setEmpty();
        }
        if (!dismissed) {
          if (selection.revision != previousRevision &&
            event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            anchor.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
          }
          updateHandles();
          if (actionMode != null) {
            if (wasDragging && selection.draggingHandle == Text.Selection.HANDLE_NONE) {
              actionMode.invalidate();
            }
            updateToolbarVisibility();
          }
        }
        // Let the same ACTION_DOWN reach the input field after the selection is closed
        return !dismissed || event.getActionMasked() != MotionEvent.ACTION_DOWN;
      }

      @Override
      public boolean dispatchKeyEvent (KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
          if (event.getAction() == KeyEvent.ACTION_UP) {
            text.dismissSelection(true);
          }
          return true;
        }
        return super.dispatchKeyEvent(event);
      }
    };
    overlay.setAlpha(0f);
    overlay.setWillNotDraw(false);
    overlay.setFocusableInTouchMode(true);
    this.observer = root.getViewTreeObserver();
    this.visibilityAnimator = new BoolAnimator(ANIMATOR_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, SHOW_DURATION);
  }

  void show () {
    View focused = root.findFocus();
    root.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    observer.addOnPreDrawListener(positionListener);
    anchor.addOnAttachStateChangeListener(attachListener);
    if (recyclerView != null) {
      recyclerView.addOnItemTouchListener(scrollTouchListener);
      recyclerView.addOnScrollListener(scrollListener);
    }
    updateHandles();
    updatePosition();
    if (dismissed) {
      return;
    }
    // Keep existing input connection of the focused editor (composing text, cursor)
    if (focused == null || !focused.onCheckIsTextEditor()) {
      overlay.requestFocus();
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      backDispatcher = overlay.findOnBackInvokedDispatcher();
      if (backDispatcher != null) {
        backCallback = () -> text.dismissSelection(true);
        backDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback);
      }
    }
    anchor.invalidate();
  }

  private boolean canSelect () {
    return !dismissed && selection.isActive() && anchor.getWindowToken() != null && (delegate == null || delegate.canSelect());
  }

  private void updateHandles () {
    if (geometryRevision == selection.revision) {
      return;
    }
    geometryRevision = selection.revision;
    boolean startLeft = !text.isRtlAtOffset(selection.start);
    boolean endLeft = text.isRtlAtOffset(selection.end - 1);
    startDrawable = startLeft ? leftHandle : rightHandle;
    endDrawable = endLeft ? leftHandle : rightHandle;
    updateHandleBounds(selection.startHandle, startBounds, startDrawable, startLeft);
    updateHandleBounds(selection.endHandle, endBounds, endDrawable, endLeft);
    overlay.invalidate();
    anchor.invalidate();
  }

  private boolean updatePosition () {
    if (dismissed || !windowFocused) {
      return true;
    }
    int previousTop = anchorBounds.top, previousBottom = anchorBounds.bottom;
    int previousViewportTop = scrollBounds.top, previousViewportBottom = scrollBounds.bottom;
    if (!canSelect() || !anchor.getGlobalVisibleRect(anchorBounds)) {
      text.dismissSelection();
      return true;
    }
    viewport.getGlobalVisibleRect(scrollBounds);
    anchor.getLocationOnScreen(location);
    root.getLocationOnScreen(rootLocation);
    text.toRect(textBounds);
    float x = location[0] - rootLocation[0] + textBounds.left + (delegate != null ? delegate.getOffsetX() : 0f);
    float y = location[1] - rootLocation[1] + textBounds.top + (delegate != null ? delegate.getOffsetY() : 0f);
    boolean moved = originX != x || originY != y;
    if (moved) {
      originX = x;
      originY = y;
      overlay.invalidate();
    }
    if (opening && overlay.getWidth() > 0) {
      opening = false;
      startActionMode();
      if (dismissed) {
        return true;
      }
      visibilityAnimator.setValue(true, true);
    }
    boolean viewportChanged =
      previousTop != anchorBounds.top || previousBottom != anchorBounds.bottom ||
      previousViewportTop != scrollBounds.top || previousViewportBottom != scrollBounds.bottom;
    if (actionMode != null && (moved || viewportChanged || menuRevision != selection.revision)) {
      menuRevision = selection.revision;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        actionMode.invalidateContentRect();
      }
    }
    updateToolbarVisibility();
    return true;
  }

  private void updateToolbarVisibility () {
    if (actionMode == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return;
    }
    boolean hidden =
      selection.draggingHandle != Text.Selection.HANDLE_NONE ||
      (recyclerView != null && recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) ||
      originY + selection.bounds.bottom <= anchorBounds.top ||
      originY + selection.bounds.top >= anchorBounds.bottom;
    if (toolbarHidden != hidden) {
      toolbarHidden = hidden;
      overlay.removeCallbacks(keepToolbarHidden);
      if (hidden) {
        keepToolbarHidden.run();
      } else {
        actionMode.invalidateContentRect();
        actionMode.hide(0);
      }
    }
  }

  private void startActionMode () {
    ActionMode.Callback callback = new ActionMode.Callback() {
      @Override
      public boolean onCreateActionMode (ActionMode mode, Menu menu) {
        menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, android.R.id.selectAll, 1, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, android.R.id.shareText, 2, Lang.getString(R.string.Share)).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return canSelect();
      }

      @Override
      public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
        boolean canSelectAll = !selection.isAllSelected();
        menu.findItem(android.R.id.selectAll).setVisible(canSelectAll).setEnabled(canSelectAll);
        return true;
      }

      @Override
      public boolean onActionItemClicked (ActionMode mode, MenuItem item) {
        return onMenuClick(item.getItemId());
      }

      @Override
      public void onDestroyActionMode (ActionMode mode) {
        if (actionMode == mode) {
          actionMode = null;
          if (!dismissed && selection.isActive()) {
            text.dismissSelection();
          }
        }
      }
    };
    // Same system floating toolbar as in InputView
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      actionMode = overlay.startActionMode(new ActionMode.Callback2() {
        @Override
        public boolean onCreateActionMode (ActionMode mode, Menu menu) {
          return callback.onCreateActionMode(mode, menu);
        }

        @Override
        public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
          return callback.onPrepareActionMode(mode, menu);
        }

        @Override
        public boolean onActionItemClicked (ActionMode mode, MenuItem item) {
          return callback.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode (ActionMode mode) {
          callback.onDestroyActionMode(mode);
        }

        @Override
        public void onGetContentRect (ActionMode mode, View view, Rect rect) {
          RectF bounds;
          float left, right;
          if (selectAllAnchor.isEmpty()) {
            bounds = selection.bounds;
            left = Math.min(selection.startHandle.left, selection.endHandle.left);
            right = Math.max(selection.startHandle.left, selection.endHandle.left);
          } else {
            bounds = selectAllAnchor;
            left = bounds.left;
            right = bounds.right;
          }
          // Anchor toolbar above the first selected line, not around the whole selection,
          // and keep enough space for it inside the visible chat area
          int bottom = scrollBounds.bottom - 1;
          int top = Math.min(bottom, scrollBounds.top + Screen.dp(64f));
          int y = MathUtils.clamp((int) Math.floor(originY + bounds.top), top, bottom);
          rect.set((int) Math.floor(originX + left), y, (int) Math.ceil(originX + right), y + 1);
        }
      }, ActionMode.TYPE_FLOATING);
    } else {
      actionMode = overlay.startActionMode(callback);
    }
    if (actionMode == null) {
      text.dismissSelection();
    }
  }

  private boolean onMenuClick (int id) {
    if (!canSelect()) {
      text.dismissSelection();
      return true;
    }
    if (id == android.R.id.selectAll) {
      selectAllAnchor.set(selection.bounds);
      selection.selectAll();
      // Keep toolbar in place when the whole text doesn't fit into the viewport
      if (originY + selection.bounds.top >= anchorBounds.top && originY + selection.bounds.bottom <= anchorBounds.bottom) {
        selectAllAnchor.setEmpty();
      }
      updateHandles();
      actionMode.invalidate();
    } else if (id == android.R.id.copy || id == android.R.id.shareText) {
      String value = selection.getSelectedText();
      text.dismissSelection(true);
      if (id == android.R.id.copy) {
        UI.copyText(value, R.string.CopiedText);
      } else {
        Intents.shareText(value);
      }
    } else {
      return false;
    }
    return true;
  }

  private void updateHandleBounds (RectF handle, RectF bounds, Drawable drawable, boolean left) {
    if (drawable == null || handle.isEmpty()) {
      bounds.setEmpty();
      return;
    }
    int width = drawable.getIntrinsicWidth();
    int hotspotX = left ? width * 3 / 4 : width / 4;
    float left = handle.left - hotspotX;
    bounds.set(left, handle.bottom, left + width, handle.bottom + drawable.getIntrinsicHeight());
  }

  private void drawHandle (Canvas canvas, RectF handle, RectF bounds, Drawable drawable) {
    float bottom = originY + handle.bottom;
    if (!bounds.isEmpty() && bottom >= anchorBounds.top && bottom <= anchorBounds.bottom) {
      Drawables.draw(canvas, drawable, bounds.left, bounds.top, null);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_VISIBILITY) {
      overlay.setAlpha(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_VISIBILITY && dismissed && finalFactor == 0f) {
      finishDismissal();
    }
  }

  void dismiss (boolean animated) {
    if (dismissed) {
      return;
    }
    dismissed = true;
    if (recyclerView != null) {
      recyclerView.removeOnItemTouchListener(scrollTouchListener);
      recyclerView.removeOnScrollListener(scrollListener);
    }
    overlay.removeCallbacks(keepToolbarHidden);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backDispatcher != null) {
      backDispatcher.unregisterOnBackInvokedCallback(backCallback);
      backDispatcher = null;
      backCallback = null;
    }
    if (observer.isAlive()) {
      observer.removeOnPreDrawListener(positionListener);
    }
    if (actionMode != null) {
      ActionMode mode = actionMode;
      actionMode = null;
      mode.finish();
    }
    if (animated && anchor.getWindowToken() != null && visibilityAnimator.getFloatValue() > 0f) {
      visibilityAnimator.setDuration(HIDE_DURATION);
      visibilityAnimator.setValue(false, true);
    } else {
      finishDismissal();
    }
    anchor.invalidate();
  }

  private void finishDismissal () {
    if (removed) {
      return;
    }
    removed = true;
    dismissed = true;
    visibilityAnimator.cancel();
    if (observer.isAlive()) {
      observer.removeOnPreDrawListener(positionListener);
    }
    anchor.removeOnAttachStateChangeListener(attachListener);
    overlay.setAlpha(0f);
    // Anchor may be detaching while root is iterating over its children
    root.post(() -> root.removeView(overlay));
  }
}
