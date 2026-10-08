/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockMedia;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.v.CustomRecyclerView;
import java.util.ArrayList;
import java.util.List;

/** Virtualized local preview using the same native blocks as the message body. */
public final class ArticlePreviewController extends RecyclerViewController<TdApi.RichMessage> implements View.OnClickListener, View.OnLongClickListener, Text.ClickCallback {
  private SettingsAdapter adapter;
  private List<PageBlock> blocks;
  public ArticlePreviewController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_articlePreview; }
  @Override public CharSequence getName () { return Lang.getString(R.string.ArticlePreview); }
  @Override protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this);
    recycler.setItemAnimator(null);
    recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override public void getItemOffsets (@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        PageBlock block = block(view); int indent = 0;
        if (block != null && block.getListItem() != null) for (PageBlock.ListItemInfo item : block.getListItem()) indent += item.list.getIndent();
        if (getArgumentsStrict().isRtl) out.right = indent; else out.left = indent;
      }
      @Override public void onDrawOver (@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
          View child = parent.getChildAt(i); PageBlock block = block(child);
          if (block == null || block.getListItem() == null) continue;
          boolean rtl = getArgumentsStrict().isRtl; int x = rtl ? child.getRight() : child.getLeft();
          PageBlock.ListItemInfo[] items = block.getListItem();
          for (int j = items.length - 1; j >= 0; j--) {
            PageBlock.ListItemInfo item = items[j]; if (item.firstBlock != block) break;
            item.drawMarker(canvas, x, child.getTop() + block.getBulletTop(), rtl, 1f);
            x += (rtl ? 1 : -1) * item.list.getIndent();
          }
        }
      }
    });
    rebuild(); recycler.setAdapter(adapter);
  }
  private PageBlock block (View view) { Object tag = view.getTag(); return tag instanceof ListItem && ((ListItem) tag).getData() instanceof PageBlock ? (PageBlock) ((ListItem) tag).getData() : null; }
  private void rebuild () {
    if (blocks != null) for (PageBlock block : blocks) if (block instanceof me.vkryl.core.lambda.Destroyable) ((me.vkryl.core.lambda.Destroyable) block).performDestroy();
    blocks = PageBlock.parseArticle(this, getArgumentsStrict(), this);
    ArrayList<ListItem> items = new ArrayList<>(); ArrayList<PageBlockMedia> media = new ArrayList<>();
    for (PageBlock block : blocks) {
      if (block instanceof PageBlockMedia && ((PageBlockMedia) block).bindToList(this, null, media)) media.add((PageBlockMedia) block);
      items.add(new ListItem(block.getRelatedViewType()).setData(block));
    }
    adapter.setItems(items, false);
  }
  @Override public void onClick (View view) {
    PageBlock block = block(view); if (block == null || block.onClick(view, false)) return;
    if (block.getOriginalBlock() instanceof TdApi.PageBlockDetails) {
      TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block.getOriginalBlock(); details.isOpen = !details.isOpen; rebuild();
    }
  }
  @Override public boolean onLongClick (View view) { PageBlock block = block(view); return block != null && block.onClick(view, true); }
  @Override public TdApi.LinkPreview findLinkPreview (String link) { return null; }
  @Override public boolean onAnchorClick (View view, String anchor) {
    if (org.thunderdog.challegram.data.article.ArticleNavigation.revealAnchor(getArgumentsStrict(), anchor)) rebuild();
    for (int i = 0; i < blocks.size(); i++) {
      PageBlock block = blocks.get(i);
      if (anchor.equals(block.getAnchor()) || block.hasChildAnchor(anchor)) {
        ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(i, -block.getChildAnchorTop(anchor, getRecyclerView().getWidth()));
        return true;
      }
    }
    return false;
  }
  @Override public boolean onReferenceClick (View view, String name, String anchor, @NonNull TdlibUi.UrlOpenParameters parameters) {
    final TdApi.RichText[] reference = {null};
    ArticleCodec.visit(getArgumentsStrict(), (value, depth) -> { if (value instanceof TdApi.RichTextReference && anchor.equals(((TdApi.RichTextReference) value).name)) reference[0] = ((TdApi.RichTextReference) value).text; });
    if (reference[0] == null || parameters.tooltip == null) return onAnchorClick(view, anchor);
    parameters.tooltip.controller(this).show(TextWrapper.parseRichText(this, this, reference[0], Paints.robotoStyleProvider(14f), parameters.tooltip.colorProvider(), parameters, null)); return true;
  }
  @Override public boolean onButtonClick (View view, TdApi.InlineButton button, @NonNull TdlibUi.UrlOpenParameters parameters) {
    if (button.type instanceof TdApi.InlineKeyboardButtonTypeUrl) tdlib.ui().openUrl(this, ((TdApi.InlineKeyboardButtonTypeUrl) button.type).url, parameters);
    else if (button.type instanceof TdApi.InlineKeyboardButtonTypeCopyText) UI.copyText(((TdApi.InlineKeyboardButtonTypeCopyText) button.type).text, R.string.CopiedText);
    else if (button.type instanceof TdApi.InlineKeyboardButtonTypeUser) tdlib.ui().openPrivateProfile(this, ((TdApi.InlineKeyboardButtonTypeUser) button.type).userId, parameters);
    else return false;
    return true;
  }

  @Override public void destroy () {
    if (blocks != null) for (PageBlock block : blocks) if (block instanceof me.vkryl.core.lambda.Destroyable) ((me.vkryl.core.lambda.Destroyable) block).performDestroy();
    super.destroy();
  }
}
