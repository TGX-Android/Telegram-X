/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.text.Layout;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import java.text.BreakIterator;
import java.util.List;

/** Selection handles belong to the document, so a drag can cross EditText boundaries. */
public final class ArticleSelectionView extends View {
  private final ArticleDocumentView document;
  private final ScrollView scroll;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final int[] position = new int[2], origin = new int[2];
  private ArticleTextInput first, last;
  private int from, to, dragging;
  private float startX, startY, endX, endY, dragX, dragY;
  private ActionMode mode;
  private boolean updating;
  private final Runnable autoScroll = new Runnable() {
    @Override public void run () {
      if (dragging == 0) return;
      int edge = Screen.dp(56), amount = dragY < edge ? -Screen.dp(12) : dragY > getHeight() - edge ? Screen.dp(12) : 0;
      if (amount != 0) { scroll.scrollBy(0, amount); extend(dragX, dragY); }
      postDelayed(this, 24);
    }
  };

  public ArticleSelectionView (Context context, ArticleDocumentView document, ScrollView scroll) {
    super(context); this.document = document; this.scroll = scroll;
    setWillNotDraw(false);
    scroll.getViewTreeObserver().addOnScrollChangedListener(this::invalidate);
  }
  public boolean active () { return first != null && last != null && (first != last || from != to); }
  public boolean multiple () { return active() && first != last; }
  public ArticleTextInput first () { return first; }
  public ArticleTextInput last () { return last; }
  public int from () { return from; }
  public int to () { return to; }
  public boolean updating () { return updating; }
  public void clear () {
    first = last = null; dragging = 0; removeCallbacks(autoScroll);
    if (mode != null) { ActionMode old = mode; mode = null; old.finish(); }
    invalidate();
  }
  public void select (ArticleTextInput start, int startOffset, ArticleTextInput end, int endOffset) {
    List<ArticleTextInput> inputs = document.inputs();
    if (!inputs.contains(start) || !inputs.contains(end)) return;
    if (inputs.indexOf(start) > inputs.indexOf(end) || start == end && startOffset > endOffset) {
      ArticleTextInput swap = start; start = end; end = swap;
      int offset = startOffset; startOffset = endOffset; endOffset = offset;
    }
    first = start; last = end; from = Math.max(0, Math.min(start.length(), startOffset)); to = Math.max(0, Math.min(end.length(), endOffset));
    updating = true;
    first.requestFocus(); first.setSelection(from, first == last ? to : first.length());
    updating = false;
    document.selectionChanged(first); invalidate();
    if (mode != null) mode.invalidate();
  }
  public void word (ArticleTextInput input, float x, float y) {
    int offset = Math.min(input.length(), input.getOffsetForPosition(x, y));
    BreakIterator words = BreakIterator.getWordInstance(); words.setText(input.getText().toString());
    int a = words.preceding(Math.min(input.length(), offset + 1)), b = words.following(offset);
    if (a == BreakIterator.DONE) a = 0;
    if (b == BreakIterator.DONE) b = input.length();
    select(input, a, input, b); showMenu();
  }
  public void all () {
    List<ArticleTextInput> inputs = document.inputs(); if (inputs.isEmpty()) return;
    select(inputs.get(0), 0, inputs.get(inputs.size() - 1), inputs.get(inputs.size() - 1).length()); showMenu();
  }
  public int start (ArticleTextInput input) { return input == first ? from : 0; }
  public int end (ArticleTextInput input) { return input == last ? to : input.length(); }
  public List<ArticleTextInput> inputs () {
    List<ArticleTextInput> inputs = document.inputs();
    int a = inputs.indexOf(first), b = inputs.indexOf(last);
    return active() && a >= 0 && b >= a ? new java.util.ArrayList<>(inputs.subList(a, b + 1)) : java.util.Collections.emptyList();
  }
  public void showMenu () {
    if (!active() || mode != null) return;
    ActionMode.Callback callback = new ActionMode.Callback() {
      @Override public boolean onCreateActionMode (ActionMode action, Menu menu) {
        menu.add(0, android.R.id.cut, 0, android.R.string.cut);
        menu.add(0, android.R.id.copy, 1, android.R.string.copy);
        menu.add(0, android.R.id.paste, 2, android.R.string.paste);
        menu.add(0, android.R.id.selectAll, 3, android.R.string.selectAll);
        return true;
      }
      @Override public boolean onPrepareActionMode (ActionMode action, Menu menu) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        menu.findItem(android.R.id.paste).setVisible(clipboard != null && clipboard.hasPrimaryClip()); return true;
      }
      @Override public boolean onActionItemClicked (ActionMode action, MenuItem item) {
        if (item.getItemId() == android.R.id.selectAll) all();
        else if (item.getItemId() == android.R.id.copy) document.copySelection(false);
        else if (item.getItemId() == android.R.id.cut) document.copySelection(true);
        else if (item.getItemId() == android.R.id.paste) document.pasteSelection();
        else return false;
        return true;
      }
      @Override public void onDestroyActionMode (ActionMode action) { if (mode == action) mode = null; }
    };
    if (android.os.Build.VERSION.SDK_INT >= 23) {
      mode = startActionMode(new ActionMode.Callback2() {
        @Override public boolean onCreateActionMode (ActionMode a, Menu m) { return callback.onCreateActionMode(a, m); }
        @Override public boolean onPrepareActionMode (ActionMode a, Menu m) { return callback.onPrepareActionMode(a, m); }
        @Override public boolean onActionItemClicked (ActionMode a, MenuItem m) { return callback.onActionItemClicked(a, m); }
        @Override public void onDestroyActionMode (ActionMode a) { callback.onDestroyActionMode(a); }
        @Override public void onGetContentRect (ActionMode a, View view, Rect out) { out.set((int) Math.min(startX, endX), (int) Math.max(0, startY - Screen.dp(24)), (int) Math.max(startX, endX) + 1, (int) Math.min(getHeight(), endY)); }
      }, ActionMode.TYPE_FLOATING);
    } else mode = startActionMode(callback);
  }
  private void position (ArticleTextInput input) { input.getLocationOnScreen(position); getLocationOnScreen(origin); position[0] -= origin[0]; position[1] -= origin[1]; }
  @Override protected void onDraw (Canvas canvas) {
    if (!active()) return;
    for (ArticleTextInput input : inputs()) {
      Layout layout = input.getLayout(); if (layout == null) continue;
      position(input); float x = position[0] + input.getTotalPaddingLeft() - input.getScrollX(), y = position[1] + input.getTotalPaddingTop() - input.getScrollY();
      int a = Math.min(input.length(), start(input)), b = Math.min(input.length(), end(input));
      path.reset(); layout.getSelectionPath(a, b, path);
      int save = canvas.save(); canvas.translate(x, y); paint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.textLinkColor(), 55)); canvas.drawPath(path, paint); canvas.restoreToCount(save);
      if (input == first) { startX = x + layout.getPrimaryHorizontal(a); startY = y + layout.getLineBottom(layout.getLineForOffset(a)); }
      if (input == last) { endX = x + layout.getPrimaryHorizontal(b); endY = y + layout.getLineBottom(layout.getLineForOffset(b)); }
    }
    paint.setColor(Theme.textLinkColor()); float radius = Screen.dp(8);
    canvas.drawCircle(startX - radius, startY + radius, radius, paint); canvas.drawRect(startX - radius, startY, startX, startY + radius, paint);
    canvas.drawCircle(endX + radius, endY + radius, radius, paint); canvas.drawRect(endX, endY, endX + radius, endY + radius, paint);
  }
  @Override public boolean onTouchEvent (MotionEvent event) {
    if (!active()) return false;
    float x = event.getX(), y = event.getY(), hit = Screen.dp(28);
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      dragging = Math.abs(x - startX) < hit && Math.abs(y - startY) < hit ? 1 : Math.abs(x - endX) < hit && Math.abs(y - endY) < hit ? 2 : 0;
      if (dragging == 0) return false;
      if (mode != null && android.os.Build.VERSION.SDK_INT >= 23) mode.hide(Long.MAX_VALUE);
      getParent().requestDisallowInterceptTouchEvent(true); dragX = x; dragY = y; post(autoScroll); return true;
    }
    if (dragging == 0) return false;
    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) { dragX = x; dragY = y; extend(x, y - Screen.dp(8)); }
    if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      dragging = 0; removeCallbacks(autoScroll); getParent().requestDisallowInterceptTouchEvent(false);
      if (mode != null && android.os.Build.VERSION.SDK_INT >= 23) { mode.hide(0); mode.invalidateContentRect(); } else showMenu();
    }
    return true;
  }
  private void extend (float x, float y) {
    ArticleTextInput nearest = null; float distance = Float.MAX_VALUE; int offset = 0;
    for (ArticleTextInput input : document.inputs()) {
      position(input); float dy = y < position[1] ? position[1] - y : y > position[1] + input.getHeight() ? y - position[1] - input.getHeight() : 0;
      float dx = x < position[0] ? position[0] - x : x > position[0] + input.getWidth() ? x - position[0] - input.getWidth() : 0;
      if (dy * 1000 + dx < distance) { distance = dy * 1000 + dx; nearest = input; offset = input.getOffsetForPosition(x - position[0], y - position[1]); }
    }
    if (nearest == null) return;
    List<ArticleTextInput> inputs = document.inputs();
    if (dragging == 1) {
      if (inputs.indexOf(nearest) > inputs.indexOf(last) || nearest == last && offset > to) dragging = 2;
      select(nearest, offset, last, to);
    } else {
      if (inputs.indexOf(nearest) < inputs.indexOf(first) || nearest == first && offset < from) dragging = 1;
      select(first, from, nearest, offset);
    }
  }
  @Override protected void onDetachedFromWindow () { clear(); super.onDetachedFromWindow(); }
}
