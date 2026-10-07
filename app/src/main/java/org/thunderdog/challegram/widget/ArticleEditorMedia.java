/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockFile;
import org.thunderdog.challegram.data.PageBlockMedia;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.data.article.ArticleDocument;
import org.thunderdog.challegram.data.article.ArticleFiles;
import org.thunderdog.challegram.data.article.ArticleEditorTree;
import org.thunderdog.challegram.data.article.ArticlePreviewMapper;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import java.util.ArrayList;
import java.util.List;
import me.vkryl.core.lambda.Destroyable;

/** The same media renderers as the article reader, including remote files when editing a post. */
public final class ArticleEditorMedia extends LinearLayout implements Destroyable {
  private final ViewController<?> controller;
  private final Runnable options;
  private final List<PageBlock> blocks = new ArrayList<>();
  private boolean destroyed, attached;
  public ArticleEditorMedia (ViewController<?> controller, TdApi.InputPageBlock input, Runnable options) {
    super(controller.context()); this.controller = controller; this.options = options; setOrientation(VERTICAL);
    TdApi.InputPageBlock detached = ArticleCodec.copy(input, TdApi.InputPageBlock.class);
    ArticleCodec.visit(detached, (value, depth) -> {
      if (value instanceof TdApi.PageBlockCaption) { ((TdApi.PageBlockCaption) value).text = new TdApi.RichTextPlain(""); ((TdApi.PageBlockCaption) value).credit = new TdApi.RichTextPlain(""); }
    });
    TextView loading = new TextView(getContext()); loading.setText(Lang.getString(R.string.ArticleLoading)); loading.setTextColor(Theme.textDecentColor()); loading.setMinHeight(Screen.dp(64)); addView(loading);
    // Before API 24, View.post on an unattached view uses the calling thread's
    // run queue. A fast TDLib response can arrive before this view is attached.
    ArticleFiles.resolve(controller.tdlib(), new TdApi.Object[] {detached}, true, resolved -> UI.post(() -> {
      if (!destroyed) build(detached, resolved);
    }));
  }

  private void build (TdApi.InputPageBlock input, ArticleFiles.Resolved resolved) {
    removeAllViews();
    ArticlePreviewMapper mapper = new ArticlePreviewMapper(value -> {
      if (ArticleFiles.key(value) != null) {
        int id = value instanceof TdApi.InputFileId ? ((TdApi.InputFileId) value).id : 0; TdApi.File file = resolved.files.get(ArticleFiles.key(value));
        return file != null ? file : new TdApi.File(id, 0, 0, new TdApi.LocalFile("", true, true, false, false, 0, 0, 0), new TdApi.RemoteFile("", "", false, true, 0));
      }
      String path = value instanceof TdApi.InputFileLocal ? ((TdApi.InputFileLocal) value).path : value instanceof TdApi.InputFileGenerated ? ((TdApi.InputFileGenerated) value).originalPath : "";
      long size = new java.io.File(path).length(); return new TdApi.File(-Math.max(1, path.hashCode() & 0x7fffffff), size, size, new TdApi.LocalFile(path, false, false, false, true, 0, size, size), new TdApi.RemoteFile("", "", false, false, 0));
    }, value -> ArticleFiles.key(value) != null ? resolved.names.get(ArticleFiles.key(value)) : null);
    TdApi.RichMessage article = mapper.preview(new ArticleDocument(new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {input}), false, false)));
    blocks.addAll(PageBlock.parseArticle(controller, article, null)); ArrayList<PageBlockMedia> media = new ArrayList<>();
    for (PageBlock block : blocks) {
      if (block instanceof PageBlockMedia && ((PageBlockMedia) block).bindToList(controller, null, media)) media.add((PageBlockMedia) block);
      View view = SettingHolder.create(getContext(), controller.tdlib(), block.getRelatedViewType(), null, v -> { if (!block.onClick(v, false)) options.run(); }, v -> { options.run(); return true; }, controller, null, null).itemView;
      view.setBackground(null);
      if (view instanceof PageBlockView) ((PageBlockView) view).setBlock(block);
      else if (view instanceof PageBlockWrapView) ((PageBlockWrapView) view).setBlock(block);
      else if (view instanceof CustomResultView) ((CustomResultView) view).setInlineResult(((PageBlockFile) block).getFile());
      addView(view, new LayoutParams(-1, -2)); setAttached(view, attached);
    }
  }
  private static void setAttached (View view, boolean attached) {
    if (view instanceof PageBlockView) { if (attached) ((PageBlockView) view).attach(); else ((PageBlockView) view).detach(); }
    else if (view instanceof PageBlockWrapView) { if (attached) ((PageBlockWrapView) view).attach(); else ((PageBlockWrapView) view).detach(); }
    else if (view instanceof CustomResultView) { if (attached) ((CustomResultView) view).attach(); else ((CustomResultView) view).detach(); }
  }
  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); attached = true; for (int i = 0; i < getChildCount(); i++) setAttached(getChildAt(i), true); }
  @Override protected void onDetachedFromWindow () { attached = false; for (int i = 0; i < getChildCount(); i++) setAttached(getChildAt(i), false); super.onDetachedFromWindow(); }
  @Override public void performDestroy () {
    destroyed = true;
    for (int i = 0; i < getChildCount(); i++) { View view = getChildAt(i); setAttached(view, false); if (view instanceof Destroyable) ((Destroyable) view).performDestroy(); }
    for (PageBlock block : blocks) if (block instanceof Destroyable) ((Destroyable) block).performDestroy(); blocks.clear(); removeAllViews();
  }
}
