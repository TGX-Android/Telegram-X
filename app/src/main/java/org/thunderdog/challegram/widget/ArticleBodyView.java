/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockFile;
import org.thunderdog.challegram.data.TGMessageArticle;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.SettingHolder;
import me.vkryl.core.lambda.Destroyable;

import java.util.ArrayList;
import java.util.List;

/** Real child views preserve native text links, file controls, table scrolling and slideshows. */
public final class ArticleBodyView extends ViewGroup implements Destroyable {
  private TGMessageArticle message;
  private int revision = -1;
  private boolean attached;
  private final List<TGMessageArticle.Row> visibleRows = new ArrayList<>();
  private final java.util.Set<View> attachedChildren = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  private TextView expand;
  private PageBlock.ListItemInfo pressedCheckbox;
  private int boundTop, boundBottom;
  private final Rect viewport = new Rect();
  private final ViewTreeObserver.OnPreDrawListener visibilityListener = () -> {
    // RecyclerView can move an already measured item without measuring its children again.
    // Check after layout, when local visibility reflects the item's new position.
    if (message != null && getLocalVisibleRect(viewport) &&
        (viewport.top < boundTop || Math.min(viewport.bottom, message.getArticleBodyHeight()) > boundBottom)) {
      requestLayout();
      return false;
    }
    return true;
  };

  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); getViewTreeObserver().addOnPreDrawListener(visibilityListener); }
  @Override protected void onDetachedFromWindow () { getViewTreeObserver().removeOnPreDrawListener(visibilityListener); super.onDetachedFromWindow(); }

  public ArticleBodyView (Context context) {
    super(context);
    setClipChildren(true);
    setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
  }

  public void setMessage (TGMessageArticle message) {
    if (this.message != message) {
      clear();
      this.message = message;
      revision = -1;
    }
    setVisibility(message == null ? GONE : VISIBLE);
    requestLayout();
  }

  private void clear () {
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      setAttached(child, false);
      if (child instanceof PageBlockView) ((PageBlockView) child).setBlock(null);
      if (child instanceof Destroyable) ((Destroyable) child).performDestroy();
    }
    removeAllViews();
    visibleRows.clear();
    expand = null;
  }

  private void bind () {
    if (message == null) return;
    int screen = getResources().getDisplayMetrics().heightPixels;
    boolean visible = getLocalVisibleRect(viewport);
    int top = visible ? viewport.top : 0, bottom = visible ? Math.min(viewport.bottom, message.getArticleBodyHeight()) : Math.min(screen, message.getArticleBodyHeight());
    if (revision == message.getArticleRevision() && top >= boundTop && bottom <= boundBottom) return;
    clear();
    revision = message.getArticleRevision();
    boundTop = Math.max(0, top - screen); boundBottom = Math.min(message.getArticleBodyHeight(), bottom + screen);
    for (TGMessageArticle.Row row : message.getArticleRows()) {
      if (row.top >= boundBottom) break;
      if (row.top + row.height < boundTop) continue;
      PageBlock block = row.block;
      View child = SettingHolder.create(getContext(), message.tdlib(), block.getRelatedViewType(), null,
        view -> click(view, block), view -> longClick(view, block), message.controller(), null, null).itemView;
      // Instant View's adapter supplies opaque list backgrounds; messages use their
      // parent chat surface. This also prevents spacer rows painting colored bars.
      child.setBackground(null);
      addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
      child.setTag(block);
      if (child instanceof PageBlockView) ((PageBlockView) child).setBlock(block);
      else if (child instanceof PageBlockWrapView) ((PageBlockWrapView) child).setBlock(block);
      else if (child instanceof CustomResultView) ((CustomResultView) child).setInlineResult(((PageBlockFile) block).getFile());
      visibleRows.add(row);
      setAttached(child, attached);
    }
    if (message.hasExpandButton()) {
      expand = new TextView(getContext());
      expand.setText(Lang.getString(message.getExpandButtonText()));
      expand.setTextColor(Theme.textLinkColor());
      expand.setTypeface(Fonts.getRobotoMedium());
      expand.setTextSize(16f);
      expand.setGravity(Gravity.CENTER);
      expand.setOnClickListener(view -> message.expandArticle());
      addView(expand);
    }
  }

  private void click (View view, PageBlock block) {
    if (message == null || block.onClick(view, false)) return;
    if (block.getOriginalBlock() instanceof TdApi.PageBlockDetails) {
      TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block.getOriginalBlock();
      details.isOpen = !details.isOpen;
      message.rebuildArticle();
    }
  }

  private boolean longClick (View view, PageBlock block) {
    if (block.onClick(view, true)) return true;
    if (getParent() instanceof MessageViewGroup) {
      ((MessageViewGroup) getParent()).getMessageView().performArticleLongPress(getLeft() + view.getLeft(), getTop() + view.getTop());
      return true;
    }
    return false;
  }

  @Override public boolean dispatchTouchEvent (MotionEvent event) {
    if (message == null || message.messagesController().inSelectMode()) return false;
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) pressedCheckbox = checkboxAt(event.getX(), event.getY());
    if (pressedCheckbox != null) {
      if (event.getActionMasked() == MotionEvent.ACTION_UP) {
        PageBlock.ListItemInfo item = pressedCheckbox; pressedCheckbox = null;
        if (item == checkboxAt(event.getX(), event.getY())) message.toggleCheckbox(item);
      } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || checkboxAt(event.getX(), event.getY()) != pressedCheckbox) pressedCheckbox = null;
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  private PageBlock.ListItemInfo checkboxAt (float x, float y) {
    if (message == null) return null;
    for (TGMessageArticle.Row row : visibleRows) {
      PageBlock.ListItemInfo[] items = row.block.getListItem();
      if (items == null || y < row.top || y > row.top + Math.min(row.height, Screen.dp(48f))) continue;
      boolean inMargin = message.getArticle().isRtl ? x >= getWidth() - row.indent : x < row.indent;
      if (!inMargin) continue;
      for (int i = items.length - 1; i >= 0; i--) if (items[i].firstBlock == row.block && items[i].list.list.items[items[i].itemIndex].hasCheckbox) return items[i];
    }
    return null;
  }

  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
    bind();
    int width = getMeasuredWidth();
    for (int i = 0; i < visibleRows.size(); i++) {
      TGMessageArticle.Row row = visibleRows.get(i);
      getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1, width - row.indent), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(row.height, MeasureSpec.EXACTLY));
    }
    if (expand != null) expand.measure(widthSpec, MeasureSpec.makeMeasureSpec(Screen.dp(48f), MeasureSpec.EXACTLY));
  }

  @Override protected void onLayout (boolean changed, int l, int t, int r, int b) {
    for (int i = 0; i < visibleRows.size(); i++) {
      View child = getChildAt(i);
      TGMessageArticle.Row row = visibleRows.get(i);
      int left = message.getArticle().isRtl ? 0 : row.indent;
      child.layout(left, row.top, left + child.getMeasuredWidth(), row.top + row.height);
    }
    if (expand != null) expand.layout(0, message.getArticleBodyHeight(), getMeasuredWidth(), getMeasuredHeight());
  }

  @Override protected void dispatchDraw (Canvas canvas) {
    if (message == null) return;
    int save = canvas.save();
    canvas.clipRect(0, 0, getWidth(), message.getArticleBodyHeight());
    for (int i = 0; i < visibleRows.size(); i++) {
      View child = getChildAt(i);
      TGMessageArticle.Row row = visibleRows.get(i);
      // Record every bound child, including the overscan window. Hardware display lists
      // survive scrolling: omitting an offscreen row here leaves a permanent blank when
      // RecyclerView moves it onscreen without invalidating this view's display list.
      drawChild(canvas, child, getDrawingTime());
      PageBlock.ListItemInfo[] list = row.block.getListItem();
      if (list != null) {
        boolean rtl = message.getArticle().isRtl;
        int x = rtl ? child.getRight() : child.getLeft();
        for (int j = list.length - 1; j >= 0; j--) {
          PageBlock.ListItemInfo item = list[j];
          if (item.firstBlock != row.block) break;
          int labelX = rtl ? x : x - item.label.getWidth();
          item.label.draw(canvas, labelX, labelX, 0, row.top + row.block.getBulletTop(), null, 1f);
          x += (rtl ? 1 : -1) * Math.max(Screen.dp(16f), item.list.maxLabelWidth + Screen.dp(4f));
        }
      }
    }
    canvas.restoreToCount(save);
    if (expand != null) drawChild(canvas, expand, getDrawingTime());
  }

  public static ArticleBodyView find (View child) {
    for (ViewParent parent = child.getParent(); parent instanceof View; parent = parent.getParent()) {
      if (parent instanceof ArticleBodyView) return (ArticleBodyView) parent;
    }
    return null;
  }

  public void relayoutArticle () { if (message != null) message.relayoutArticle(); }

  public boolean scrollToAnchor (String anchor) {
    return message != null && message.scrollToAnchor(this, anchor);
  }

  public boolean scrollToLoadedAnchor (TGMessageArticle expected, String anchor) {
    if (anchor == null || message == null || message != expected) return false;
    for (TGMessageArticle.Row row : message.getArticleRows()) {
      if (anchor.equals(row.block.getAnchor()) || row.block.hasChildAnchor(anchor)) {
        int top = row.top + (row.block.isAnchorOnBottom() ? row.height : row.block.getChildAnchorTop(anchor, getWidth()));
        post(() -> requestRectangleOnScreen(new Rect(0, top, getWidth(), top + Screen.dp(48f)), false));
        return true;
      }
    }
    return false;
  }

  private void setAttached (View child, boolean attached) {
    if (attached ? !attachedChildren.add(child) : !attachedChildren.remove(child)) return;
    if (child instanceof PageBlockView) { if (attached) ((PageBlockView) child).attach(); else ((PageBlockView) child).detach(); }
    else if (child instanceof PageBlockWrapView) { if (attached) ((PageBlockWrapView) child).attach(); else ((PageBlockWrapView) child).detach(); }
    else if (child instanceof CustomResultView) { if (attached) ((CustomResultView) child).attach(); else ((CustomResultView) child).detach(); }
  }
  public void attach () { attached = true; for (int i = 0; i < getChildCount(); i++) setAttached(getChildAt(i), true); }
  public void detach () { attached = false; for (int i = 0; i < getChildCount(); i++) setAttached(getChildAt(i), false); }
  @Override public void performDestroy () { clear(); message = null; }
}
