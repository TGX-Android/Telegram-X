package org.thunderdog.challegram.util.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

public class TextSelectionHelper {

  public interface QuoteCallback {
    void onQuoteCreated(TdApi.FormattedText text, int utf16Position);

    void onQuoteInOtherChatCreated(TdApi.FormattedText text, int utf16Position);
  }
  public interface CancelCallback {
    void onActionModeCancelled();
  }

  private final Text text;
  private final TdApi.FormattedText fullFormattedText;
  private int selectionStart = -1;
  private int selectionEnd = -1;

  private ActionMode currentActionMode;
  private QuoteCallback callback;
  private CancelCallback cancelCallback;

  private Drawable handleLeftDrawable;
  private Drawable handleRightDrawable;
  private final Rect tempRect = new Rect();
  private final Rect startHandleRect = new Rect();
  private final Rect endHandleRect = new Rect();

  private static final int HANDLE_LEFT = 1;
  private static final int HANDLE_RIGHT = 2;
  private static final int HANDLE_NONE = 0;
  private int draggedHandle = HANDLE_NONE;

  private View parentView;
  private int layoutX = 0;
  private int layoutY = 0;
  private int layoutEndX = 0;
  private int layoutEndXBottomPadding = 0;
  private boolean sawDownEventInSelectionMode = false;

  public TextSelectionHelper(Text text, TdApi.FormattedText fullFormattedText) {
    this.text = text;
    this.fullFormattedText = fullFormattedText;
  }

  private void initDrawables(Context context) {
    if (handleLeftDrawable != null) return;
    try {
      TypedValue value = new TypedValue();
      context.getTheme().resolveAttribute(android.R.attr.textSelectHandleLeft, value, true);
      if (value.resourceId != 0) handleLeftDrawable = ContextCompat.getDrawable(context, value.resourceId);

      context.getTheme().resolveAttribute(android.R.attr.textSelectHandleRight, value, true);
      if (value.resourceId != 0) handleRightDrawable = ContextCompat.getDrawable(context, value.resourceId);
    } catch (Exception e) { }
  }

  public void showActionMode(View view, QuoteCallback callback, CancelCallback cancelCallback) {
    this.callback = callback;
    this.cancelCallback = cancelCallback;
    this.parentView = view;
    initDrawables(view.getContext());
    this.sawDownEventInSelectionMode = false;

    if (currentActionMode == null) {
      ActionMode.Callback2 actionModeCallback = new ActionMode.Callback2() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
          MenuInflater inflater = mode.getMenuInflater();
          if (inflater != null) {
              inflater.inflate(R.menu.text_selection_quote, menu);
              MenuItem quoteItem = menu.findItem(R.id.menu_btn_create_quote);
              if (quoteItem != null) {
                  quoteItem.setTitle(Lang.getString(R.string.TextFormatQuote));
              }
              MenuItem quoteInOtherChatItem = menu.findItem(R.id.menu_btn_quote_in_other_chat);
              if (quoteInOtherChatItem != null) {
                  quoteInOtherChatItem.setTitle(Lang.getString(R.string.QuoteInOtherChat));
              }
          }
          return true;
        }
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
          if (item.getItemId() == R.id.menu_btn_create_quote) {
            createQuote();
            return true;
          }
          if (item.getItemId() == android.R.id.copy) {
            copySelection();
            return true;
          }
          if(item.getItemId() == R.id.menu_btn_quote_in_other_chat){
            createQuoteInOtherChat();
            return true;
          }
          return false;
        }
        @Override
        public void onDestroyActionMode(ActionMode mode) {
          currentActionMode = null;
          clearSelection();
          if (TextSelectionHelper.this.cancelCallback != null) {
            TextSelectionHelper.this.cancelCallback.onActionModeCancelled();
          }
          if (parentView != null) parentView.invalidate();
        }
        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
          if (hasSelection() && text != null) {
            Rect sRect = new Rect();
            Rect eRect = new Rect();
            boolean sOk = text.getCursorScreenCoordinates(selectionStart, layoutX, layoutEndX, layoutEndXBottomPadding, layoutY, sRect);
            boolean eOk = text.getCursorScreenCoordinates(selectionEnd, layoutX, layoutEndX, layoutEndXBottomPadding, layoutY, eRect);

            if (sOk && eOk) {
              // Координаты уже экранные, offset не нужен
              int top = Math.min(sRect.top, eRect.top);
              int bottom = Math.max(sRect.bottom, eRect.bottom);
              int left = Math.min(sRect.left, eRect.left);
              int right = Math.max(sRect.right, eRect.right);

              // Поднимаем меню чуть выше верхней линии
              outRect.set(left, top - Screen.dp(10), right, bottom);
            } else {
              outRect.set(0, 0, view.getWidth(), 0);
            }
          } else {
            outRect.set(0, 0, view.getWidth(), 0);
          }
        }
      };

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        currentActionMode = view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING);
      } else {
        currentActionMode = view.startActionMode(actionModeCallback);
      }
      if (parentView != null) parentView.invalidate();
    } else {
      currentActionMode.invalidateContentRect();
    }
  }

  public boolean onTouchEvent(View view, MotionEvent e) {
    if (currentActionMode == null || !hasSelection()) return false;

    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN:
        sawDownEventInSelectionMode = true;
        if (checkHandleTouch(x, y, selectionStart, true)) {
          draggedHandle = HANDLE_LEFT;
          view.getParent().requestDisallowInterceptTouchEvent(true);
        } else if (checkHandleTouch(x, y, selectionEnd, false)) {
          draggedHandle = HANDLE_RIGHT;
          view.getParent().requestDisallowInterceptTouchEvent(true);
        } else {
          draggedHandle = HANDLE_NONE;
        }
        return true;

      case MotionEvent.ACTION_MOVE:
        if (draggedHandle != HANDLE_NONE) {
          view.getParent().requestDisallowInterceptTouchEvent(true);
          updateSelectionFromTouch(x, y);
          view.invalidate();
          return true;
        }
        break;

      case MotionEvent.ACTION_UP:
        boolean wasDragging = draggedHandle != HANDLE_NONE;
        draggedHandle = HANDLE_NONE;
        view.getParent().requestDisallowInterceptTouchEvent(false);
        if (currentActionMode != null) {
          currentActionMode.invalidateContentRect();
        }

        if (!wasDragging && sawDownEventInSelectionMode) {
          finish();
        }
        sawDownEventInSelectionMode = false;
        return true;

      case MotionEvent.ACTION_CANCEL:
        draggedHandle = HANDLE_NONE;
        sawDownEventInSelectionMode = false;
        view.getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    return false;
  }

  /**
   * Checks if the touch event is within the handle's touch area.
   * This version uses the last known bounding box of the handle drawable.
   */
  private boolean checkHandleTouch(float touchX, float touchY, int charIndex, boolean isLeft) {
    Rect targetRect = isLeft ? startHandleRect : endHandleRect;
    if (targetRect.isEmpty()) {
      return false;
    }
    tempRect.set(targetRect);
    int touchSlop = Screen.dp(8);
    tempRect.inset(-touchSlop, -touchSlop);
    return tempRect.contains((int) touchX, (int) touchY);
  }

  private void updateSelectionFromTouch(float screenX, float screenY) {
    if (text == null) return;

    // Используем getCharIndexAtScreen для правильного учета RTL, margins, etc.
    int charIndex = text.getCharIndexAtScreen(screenX, screenY - Screen.dp(20), layoutX, layoutEndX, layoutEndXBottomPadding, layoutY);

    // Валидация
    int maxLen = text.getText().length();
    if (charIndex < 0) charIndex = 0;
    if (charIndex > maxLen) charIndex = maxLen;

    if (draggedHandle == HANDLE_LEFT) {
      if (charIndex < selectionEnd) {
        selectionStart = charIndex;
      } else {
        selectionStart = selectionEnd - 1;
        if (selectionStart < 0) selectionStart = 0;
        draggedHandle = HANDLE_RIGHT; // Swap handles logic
      }
    } else if (draggedHandle == HANDLE_RIGHT) {
      if (charIndex > selectionStart) {
        selectionEnd = charIndex;
      } else {
        selectionEnd = selectionStart + 1;
        if (selectionEnd > maxLen) selectionEnd = maxLen;
        draggedHandle = HANDLE_LEFT; // Swap handles logic
      }
    }
  }

  public void drawHandles(Canvas canvas, Text text, int startX, int startY) {
    if (!hasSelection() || this.text != text) return;

    if (parentView != null && handleLeftDrawable == null) {
      initDrawables(parentView.getContext());
    }

    if (text.getCursorScreenCoordinates(selectionStart, layoutX, layoutEndX, layoutEndXBottomPadding, layoutY, tempRect)) {
      int x = tempRect.left;
      int y = tempRect.bottom;

      if (handleLeftDrawable != null) {
        int w = handleLeftDrawable.getIntrinsicWidth();
        int h = handleLeftDrawable.getIntrinsicHeight();
        startHandleRect.set(x - w, y, x, y + h);

        handleLeftDrawable.setBounds(startHandleRect);
        handleLeftDrawable.draw(canvas);
      } else {
        // Fallback: если нет картинки, рисуем кружок (но в правильном месте!)
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(0xFF2196F3);
        canvas.drawCircle(x, y + Screen.dp(10), Screen.dp(10), p);
      }
    }

    if (text.getCursorScreenCoordinates(selectionEnd, layoutX, layoutEndX, layoutEndXBottomPadding, layoutY, tempRect)) {
      int x = tempRect.left;
      int y = tempRect.bottom;

      if (handleRightDrawable != null) {
        int w = handleRightDrawable.getIntrinsicWidth();
        int h = handleRightDrawable.getIntrinsicHeight();
        endHandleRect.set(x, y, x + w, y + h);

        handleRightDrawable.setBounds(endHandleRect);
        handleRightDrawable.draw(canvas);
      } else {
        // Fallback
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(0xFF2196F3);
        canvas.drawCircle(x, y + Screen.dp(10), Screen.dp(10), p);
      }
    }
  }

  public void setSelection(int start, int end) {
    this.selectionStart = start;
    this.selectionEnd = end;
  }
  public void clearSelection() {
    this.selectionStart = -1;
    this.selectionEnd = -1;
    if (text != null) {
      text.setQuoteHighlight(-1, -1, 0f);
    }
  }
  public boolean hasSelection() { return selectionStart >= 0 && selectionEnd > selectionStart; }

  public void finish() {
    if (currentActionMode != null) {
      currentActionMode.finish();
      currentActionMode = null;
    }
    clearSelection();
    if (parentView != null) parentView.invalidate();
  }

  public void drawSelection(Canvas canvas, Text text, int startX, int startY, float alpha) {
    if (hasSelection() && this.text == text) {
      text.setQuoteHighlight(selectionStart, selectionEnd, alpha);
    } else {
      text.setQuoteHighlight(-1, -1, 0f);
    }
  }

  private void createQuote() {
    if (selectionStart < 0 || selectionEnd <= selectionStart) return;

    String full = fullFormattedText.text;
    int maxLen = full.length();

    if (selectionStart > maxLen) selectionStart = maxLen;
    if (selectionEnd > maxLen) selectionEnd = maxLen;

    String sub = full.substring(selectionStart, selectionEnd);
    int finalPos = selectionStart;

    QuoteCallback targetCallback = callback;

    finish();

    if (targetCallback != null) {
      targetCallback.onQuoteCreated(new TdApi.FormattedText(sub, new TdApi.TextEntity[0]), finalPos);
    }
  }

  private void createQuoteInOtherChat(){
    if (selectionStart < 0 || selectionEnd <= selectionStart) return;

    String full = fullFormattedText.text;
    int maxLen = full.length();

    if (selectionStart > maxLen) selectionStart = maxLen;
    if (selectionEnd > maxLen) selectionEnd = maxLen;

    String sub = full.substring(selectionStart, selectionEnd);
    int finalPos = selectionStart;

    QuoteCallback targetCallback = callback;

    finish();

    if (targetCallback != null) {
      targetCallback.onQuoteInOtherChatCreated(new TdApi.FormattedText(sub, new TdApi.TextEntity[0]), finalPos);
    }
  }

  private void copySelection() {
    if (selectionStart < 0 || selectionEnd <= selectionStart) return;

    String full = fullFormattedText.text;
    int maxLen = full.length();

    int start = Math.min(selectionStart, maxLen);
    int end = Math.min(selectionEnd, maxLen);

    if (start >= end) return;

    String sub = full.substring(start, end);

    if (parentView != null) {
        Context context = parentView.getContext();
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", sub);
        clipboard.setPrimaryClip(clip);
    }

    finish();
  }

  public void setLayoutOffset(int x, int y, int endX, int endXBottomPadding) {
    this.layoutX = x;
    this.layoutY = y;
    this.layoutEndX = endX;
    this.layoutEndXBottomPadding = endXBottomPadding;
  }
}