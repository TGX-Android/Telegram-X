/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.view.View;
import android.widget.LinearLayout;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockFile;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.tool.Screen;
import me.vkryl.core.lambda.Destroyable;
import java.util.Collections;
import java.util.List;

/** An AI preview renders the returned blocks, including diffs, tables and formulas. */
public final class ArticleRichPreview extends LinearLayout implements Destroyable {
  private final ViewController<?> owner;
  private List<PageBlock> blocks = Collections.emptyList();
  private boolean attached;
  private boolean rtl;
  public ArticleRichPreview (ViewController<?> owner) { super(owner.context()); this.owner = owner; setOrientation(VERTICAL); }
  public void setArticle (TdApi.RichMessage article) {
    performDestroy();
    rtl = article.isRtl;
    blocks = PageBlock.parseArticle(owner, article, null);
    for (PageBlock block : blocks) {
      View view = SettingHolder.create(getContext(), owner.tdlib(), block.getRelatedViewType(), null, null, null, owner, null, null).itemView;
      view.setBackground(null);
      if (view instanceof PageBlockView) ((PageBlockView) view).setBlock(block);
      else if (view instanceof PageBlockWrapView) ((PageBlockWrapView) view).setBlock(block);
      else if (view instanceof CustomResultView) ((CustomResultView) view).setInlineResult(((PageBlockFile) block).getFile());
      LayoutParams params = new LayoutParams(-1, -2);
      if (block.getListItem() != null) {
        int indent = 0; for (PageBlock.ListItemInfo item : block.getListItem()) indent += Math.max(Screen.dp(16), item.list.maxLabelWidth + Screen.dp(4));
        if (article.isRtl) params.rightMargin = indent; else params.leftMargin = indent;
      }
      addView(view, params); attach(view, attached);
    }
  }
  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i); LayoutParams params = (LayoutParams) child.getLayoutParams();
      params.height = blocks.get(i).getHeight(child, Math.max(1, width - getPaddingLeft() - getPaddingRight() - params.leftMargin - params.rightMargin));
    }
    super.onMeasure(widthSpec, heightSpec);
  }
  @Override protected void dispatchDraw (android.graphics.Canvas canvas) {
    super.dispatchDraw(canvas);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i); PageBlock block = blocks.get(i); PageBlock.ListItemInfo[] items = block.getListItem();
      if (items == null) continue;
      int x = rtl ? child.getRight() : child.getLeft();
      for (int j = items.length - 1; j >= 0; j--) {
        PageBlock.ListItemInfo item = items[j]; if (item.firstBlock != block) break;
        int left = rtl ? x : x - item.label.getWidth(); item.label.draw(canvas, left, left, 0, child.getTop() + block.getBulletTop(), null, 1f);
        x += (rtl ? 1 : -1) * Math.max(Screen.dp(16), item.list.maxLabelWidth + Screen.dp(4));
      }
    }
  }
  private void attach (View view, boolean value) {
    if (view instanceof PageBlockView) { if (value) ((PageBlockView) view).attach(); else ((PageBlockView) view).detach(); }
    else if (view instanceof PageBlockWrapView) { if (value) ((PageBlockWrapView) view).attach(); else ((PageBlockWrapView) view).detach(); }
    else if (view instanceof CustomResultView) { if (value) ((CustomResultView) view).attach(); else ((CustomResultView) view).detach(); }
  }
  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); attached = true; for (int i = 0; i < getChildCount(); i++) attach(getChildAt(i), true); }
  @Override protected void onDetachedFromWindow () { attached = false; for (int i = 0; i < getChildCount(); i++) attach(getChildAt(i), false); super.onDetachedFromWindow(); }
  @Override public void performDestroy () {
    for (int i = 0; i < getChildCount(); i++) { View child = getChildAt(i); attach(child, false); if (child instanceof PageBlockView) ((PageBlockView) child).setBlock(null); if (child instanceof Destroyable) ((Destroyable) child).performDestroy(); }
    removeAllViews(); for (PageBlock block : blocks) if (block instanceof Destroyable) ((Destroyable) block).performDestroy(); blocks = Collections.emptyList();
  }
}
