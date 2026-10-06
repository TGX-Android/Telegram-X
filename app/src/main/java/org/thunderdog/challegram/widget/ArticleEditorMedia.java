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
import org.thunderdog.challegram.data.article.ArticleEditorTree;
import org.thunderdog.challegram.data.article.ArticlePreviewMapper;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    Map<Integer, TdApi.File> files = new ConcurrentHashMap<>(); Set<Integer> ids = new java.util.HashSet<>();
    ArticleCodec.visit(detached, (value, depth) -> { if (value instanceof TdApi.InputFileId) ids.add(((TdApi.InputFileId) value).id); });
    Runnable ready = () -> post(() -> { if (!destroyed) build(detached, files); });
    if (ids.isEmpty()) ready.run();
    else {
      TextView loading = new TextView(getContext()); loading.setText(Lang.getString(R.string.ArticleLoading)); loading.setTextColor(Theme.textDecentColor()); loading.setMinHeight(Screen.dp(64)); addView(loading);
      AtomicInteger remaining = new AtomicInteger(ids.size());
      for (int id : ids) controller.tdlib().send(new TdApi.GetFile(id), (file, error) -> { if (file != null) files.put(id, file); if (remaining.decrementAndGet() == 0) ready.run(); });
    }
  }
  private void build (TdApi.InputPageBlock input, Map<Integer, TdApi.File> files) {
    removeAllViews();
    ArticlePreviewMapper mapper = new ArticlePreviewMapper(value -> {
      if (value instanceof TdApi.InputFileId) {
        int id = ((TdApi.InputFileId) value).id; TdApi.File file = files.get(id);
        return file != null ? file : new TdApi.File(id, 0, 0, new TdApi.LocalFile("", true, true, false, false, 0, 0, 0), new TdApi.RemoteFile("", "", false, true, 0));
      }
      String path = value instanceof TdApi.InputFileLocal ? ((TdApi.InputFileLocal) value).path : value instanceof TdApi.InputFileGenerated ? ((TdApi.InputFileGenerated) value).originalPath : "";
      long size = new java.io.File(path).length(); return new TdApi.File(-Math.max(1, path.hashCode() & 0x7fffffff), size, size, new TdApi.LocalFile(path, false, false, false, true, 0, size, size), new TdApi.RemoteFile("", "", false, false, 0));
    });
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
