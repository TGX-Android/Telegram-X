/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockFile;
import org.thunderdog.challegram.data.PageBlockMedia;
import org.thunderdog.challegram.data.PageBlockRichText;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.PageBlockView;
import org.thunderdog.challegram.widget.PageBlockWrapView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class InstantViewController extends ViewController<InstantViewController.Args> implements Menu, Client.ResultHandler,  TGLegacyManager.EmojiLoadListener, Text.ClickCallback, View.OnClickListener, View.OnLongClickListener, TGPlayerController.PlayListBuilder {
  public static class Args {
    public final TdApi.WebPage webPage;
    public TdApi.WebPageInstantView instantView;
    public String anchorLink;

    public Args (TdApi.WebPage webPage, TdApi.WebPageInstantView instantView, String anchorLink) {
      this.webPage = webPage;
      this.instantView = instantView;
      this.anchorLink = anchorLink;
    }
  }

  public InstantViewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_instantView;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_iv;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_iv: {
        menu.addView(header.genButton(R.id.menu_btn_forward, R.drawable.baseline_share_arrow_24, getHeaderIconColorId(), this, Screen.dp(52f), ThemeDeprecated.headerSelector(), header), Lang.rtl() ? 0 : -1);
        break;
      }
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, final float originalX, final float originalY) {
    float x = originalX - (Views.getLocationInWindow(recyclerView)[0] - Views.getLocationInWindow(navigationController.get())[0]);
    float y = originalY - (Views.getLocationInWindow(recyclerView)[1] - Views.getLocationInWindow(navigationController.get())[1]);

    View view = recyclerView.findChildViewUnder(x, y);
    if (view instanceof PageBlockWrapView) {
      y -= view.getTop();
      PageBlockWrapView wrapView = (PageBlockWrapView) view;
      switch (wrapView.getMode()) {
        case PageBlockWrapView.MODE_EMBEDDED: {
          View child = wrapView.getChildAt(0);
          return child == null || y < child.getTop() || y >= child.getBottom();
        }
        case PageBlockWrapView.MODE_SLIDESHOW: {
          View child = wrapView.getChildAt(0);
          return child == null || y < child.getTop() || y >= child.getBottom() || wrapView.getViewPagerPosition() == 0f;
        }
        case PageBlockWrapView.MODE_TABLE: {
          View child = ((ViewGroup) wrapView.getChildAt(0)).getChildAt(0);
          return child == null || y < child.getTop() || y >= child.getBottom() || child.getLeft() >= 0;
        }
      }
      return false;
    }
    return super.canSlideBackFrom(navigationController, x, y);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_forward: {
        String link = getArgumentsStrict().webPage.url;
        ShareController c = new ShareController(context, tdlib);
        ShareController.Args args = new ShareController.Args(link);
        args.setCustomCopyLinkAction(R.string.OpenInExternalApp, () -> UI.openUrl(getArgumentsStrict().webPage.url));
        if (Strings.isValidLink(link)) {
          args.setExport(link);
        }
        c.setArguments(args);
        c.show();
        break;
      }
    }
  }

  @Override
  public CharSequence getName () {
    return getArgumentsStrict().webPage.siteName;
  }

  @Override
  public boolean onUrlClick (View view, String url, boolean promptUser, @NonNull TdlibUi.UrlOpenParameters openParameters) {
    String instantViewUrl = getUrl();
    boolean needFallback = false;
    Uri fromUri = Strings.wrapHttps(instantViewUrl);
    if (fromUri != null && tdlib.isKnownHost(fromUri.getHost(), false)) {
      List<String> segments = fromUri.getPathSegments();
      if (segments != null && segments.size() == 1 && segments.get(0).equals("iv")) {
        // https://t.me/iv?url=https%3A%2F%2Fdtf.ru%2Fgames%2F70427-bloger-uvidel-v-egs-vsplyvayushchuyu-reklamu-borderlands-3-pryamo-vo-vremya-igry-v-shuter&rhash=712e133acdd46c
        String sourceUrl = fromUri.getQueryParameter("url");
        String rhash = fromUri.getQueryParameter("rhash");
        if (!StringUtils.isEmpty(sourceUrl) && !StringUtils.isEmpty(rhash)) {
          url = new Uri.Builder().scheme("https").authority(tdlib.tMeAuthority()).path("iv").appendQueryParameter("url", url).appendQueryParameter("rhash", rhash).build().toString();
          needFallback = true;
        }
      }
    }
    tdlib.ui().openUrl(this, url, new TdlibUi.UrlOpenParameters(openParameters).forceInstantView().referer(instantViewUrl).instantViewFallbackUrl(needFallback ? url : null));
    return true;
  }

  @Override
  public void onClick (View view) {
    PageBlock pageBlock = getPageBlock(view);
    if (pageBlock != null && pageBlock.isClickable() && !pageBlock.onClick(view, false)) {
      if (pageBlock.getOriginalBlock() instanceof TdApi.PageBlockDetails) {
        final int blockIndex = adapter.indexOfViewByData(pageBlock);
        if (blockIndex == -1)
          return;
        boolean isOpen = ((PageBlockRichText) pageBlock).toggleDetailsOpened();
        if (isOpen) {
          final ArrayList<PageBlock> blocks;
          try {
            blocks = PageBlock.parse(this, getUrl(), getArgumentsStrict().instantView, pageBlock, this, null);
          } catch (Throwable t) {
            Log.e("Exception in instant view block", t);
            context().tooltipManager().builder(view).show(tdlib, t instanceof UnsupportedOperationException ? R.string.InstantViewSectionUnsupported : R.string.InstantViewError).hideDelayed();
            ((PageBlockRichText) pageBlock).toggleDetailsOpened();
            return;
          }
          ListItem[] items = new ListItem[blocks.size()];
          int index = 0;
          for (PageBlock block : blocks) {
            items[index] = new ListItem(block.getRelatedViewType()).setData(block);
            index++;
          }
          adapter.addItems(blockIndex + 1, items);
        } else {
          int itemCount = 0;
          for (int i = blockIndex + 1; i < adapter.getItems().size(); i++) {
            if (((PageBlock) adapter.getItem(i).getData()).isChildOf(pageBlock)) {
              itemCount++;
            } else {
              break;
            }
          }
          adapter.removeRange(blockIndex + 1, itemCount);
        }
        rebuildMediaBlocks();
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    PageBlock pageBlock = getPageBlock(v);
    return pageBlock != null && pageBlock.isClickable() && pageBlock.onClick(v, true);
  }

  @Override
  public boolean onAnchorClick (View view, String anchor) {
    return scrollToAnchor(anchor, true);
  }

  @Override
  public boolean onReferenceClick (View view, String name, String referenceAnchorName, @NonNull TdlibUi.UrlOpenParameters openParameters) {
    if (openParameters.tooltip != null) {
      TdApi.RichText referenceText = Td.findReference(getArgumentsStrict().instantView, referenceAnchorName);
      if (referenceText != null) {
        TextWrapper textWrapper = TextWrapper.parseRichText(this, this, referenceText, Paints.robotoStyleProvider(13f), openParameters.tooltip.colorProvider(), openParameters, null);
        openParameters.tooltip.anchor(view, ((PageBlockView) view).getBlock().getViewProvider()).controller(this).show(textWrapper);
        return true;
      }
    }
    return false;
  }

  private boolean scrollToAnchor (String anchor, boolean smooth) {
    if (anchor == null)
      return false;
    String decodedAnchor = StringUtils.decodeURIComponent(anchor);
    if (anchor.equals(decodedAnchor)) {
      decodedAnchor = null;
    }
    List<ListItem> items = adapter.getItems();
    int index = 0;
    for (ListItem item : items) {
      Object data = item.getData();
      if (data instanceof PageBlock) {
        final PageBlock pageBlock = (PageBlock) data;
        final boolean hasMatchingAnchor = anchor.equals(pageBlock.getAnchor()) || (decodedAnchor != null && decodedAnchor.equals(pageBlock.getAnchor()));
        if (hasMatchingAnchor && !pageBlock.isAnchorOnBottom()) {
          scrollToBlock(index, pageBlock, null, true);
          return true;
        }
        if (pageBlock.hasChildAnchor(anchor)) {
          scrollToBlock(index, pageBlock, anchor, true);
          return true;
        }
        if (hasMatchingAnchor && pageBlock.isAnchorOnBottom()) {
          if (index + 1 < items.size()) {
            Object nextData = items.get(index + 1).getData();
            if (nextData instanceof PageBlock && anchor.equals(((PageBlock) nextData).getAnchor()) && !((PageBlock) nextData).isAnchorOnBottom()) {
              scrollToBlock(index + 1, (PageBlock) nextData, pageBlock.getAnchor(), true);
              return true;
            }
          }
          scrollToBlock(index, pageBlock, null, false);
          return true;
        }
      }
      index++;
    }
    if (anchor.isEmpty() && !items.isEmpty()) {
      scrollToBlock(0, null, null, true);
      return true;
    }
    return false;
  }

  private void scrollToBlock (int position, @Nullable PageBlock block, String childAnchor, boolean top) {
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (manager == null)
      return;
    if (StringUtils.isEmpty(childAnchor)) {
      if (top) {
        manager.scrollToPositionWithOffset(position, 0);
      } else if (position + 1 < adapter.getItemCount()) {
        manager.scrollToPositionWithOffset(position + 1, 0);
      } else {
        manager.scrollToPositionWithOffset(position, context.getControllerWidth(recyclerView));
      }
    } else if (block != null) {
      int viewTop = block.getChildAnchorTop(childAnchor, context.getControllerWidth(recyclerView));
      manager.scrollToPositionWithOffset(position, -viewTop);
    }
  }

  // private static final float PADDING_FACTOR = .25f;

  public static int getColor (@ThemeColorId int color) {
    return Theme.getColor(color); // TODO separate themes for instant view
  }

  private SettingsAdapter adapter;
  private RecyclerView recyclerView;

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_ivHeader;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_ivHeaderIcon;
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_ivHeaderIcon;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return recyclerView;
  }

  @Nullable
  private PageBlock getPageBlock (View view) {
    ListItem item = (ListItem) view.getTag();
    return item != null && item.getData() instanceof PageBlock ? (PageBlock) item.getData() : null;
  }

  @Override
  protected View onCreateView (Context context) {
    ArrayList<PageBlock> pageBlocks;
    try {
      pageBlocks = parsePageBlocks(getArgumentsStrict().instantView);
    } catch (PageBlock.UnsupportedPageBlockException e) {
      throw new UnsupportedOperationException();
    }

    FrameLayout contentView = new FrameLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_background, this);

    recyclerView = (RecyclerView) Views.inflate(context(), R.layout.recycler, contentView);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setItemAnimator(null);
    contentView.addView(recyclerView);

    recyclerView.addItemDecoration(new FillingDecoration(recyclerView, this) {
      @Override
      protected int getFillingColor (int i, @NonNull View view) {
        PageBlock pageBlock = getPageBlock(view);
        int colorId = pageBlock != null ? pageBlock.getBackgroundColorId() : ThemeColorId.NONE;
        if (colorId == ThemeColorId.NONE) {
          return 0;
        }
        if (colorId != R.id.theme_color_filling) {
          return ColorUtils.compositeColor(Theme.getColor(R.id.theme_color_filling), ColorUtils.alphaColor(view.getAlpha(), Theme.getColor(colorId)));
        }
        return Theme.getColor(colorId);
      }

      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.left = 0;
        PageBlock pageBlock = getPageBlock(view);
        if (pageBlock != null) {
          PageBlock.ListItemInfo[] info = pageBlock.getListItem();
          if (info != null) {
            outRect.left = Screen.dp(18f);
            for (PageBlock.ListItemInfo itemInfo : info) {
              outRect.left += Math.max(Screen.dp(16f), itemInfo.list.maxLabelWidth + Screen.dp(4f));
            }
          }
        }
      }

      @Override
      protected boolean needSeparateDecorations () {
        return true;
      }

      @Override
      protected void drawDecorationForView (Canvas c, RecyclerView parent, RecyclerView.State state, @NonNull View view) {
        float alpha = view.getAlpha();
        PageBlock pageBlock = getPageBlock(view);
        if (pageBlock != null) {
          PageBlock.ListItemInfo[] info = pageBlock.getListItem();
          if (info != null) {
            int top = parent.getLayoutManager().getDecoratedTop(view) + (int) view.getTranslationY() + pageBlock.getBulletTop();
            int left = view.getLeft() - parent.getLayoutManager().getDecoratedLeft(view);
            for (int i = info.length - 1; i >= 0; i--) {
              PageBlock.ListItemInfo itemInfo = info[i];
              if (itemInfo.firstBlock != null && itemInfo.firstBlock != pageBlock)
                break;
              int x = left - itemInfo.label.getWidth();
              itemInfo.label.draw(c, x, x, 0, top, null, alpha);
              left -= Math.max(Screen.dp(16f), itemInfo.list.maxLabelWidth + Screen.dp(4f));
            }
          }
        }
      }
    });

    adapter = new SettingsAdapter(this);
    buildCells(pageBlocks, false);

    recyclerView.setAdapter(adapter);

    TGLegacyManager.instance().addEmojiListener(this);
    ThemeManager.instance().addThemeListener(this);

    return contentView;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    Views.invalidateChildren(recyclerView);
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
    ThemeManager.instance().removeThemeListener(this);
    context().removeFullScreenView(this, true);
  }

  private ArrayList<PageBlockMedia> mediaBlocks;

  private void rebuildMediaBlocks () {
    mediaBlocks.clear();
    for (ListItem item : adapter.getItems()) {
      PageBlock block = (PageBlock) item.getData();
      if (block instanceof PageBlockMedia && ((PageBlockMedia) block).bindToList(this, getDisplayUrl(), mediaBlocks)) {
        mediaBlocks.add((PageBlockMedia) block);
      }
    }
  }

  private ArrayList<PageBlock> parsePageBlocks (TdApi.WebPageInstantView instantView) throws PageBlock.UnsupportedPageBlockException {
    return PageBlock.parse(this, getUrl(), instantView, null, this, null);
  }

  private void buildCells (ArrayList<PageBlock> blocks, boolean isReplace) {
    Args args = getArgumentsStrict();
    final TdApi.WebPageInstantView instantView = args.instantView;

    if (isReplace && !instantView.isFull) {
      Log.e("TDLib error: instantView.isFull returned false on the second call");
      return;
    }

    ArrayList<ListItem> items = new ArrayList<>(blocks.size());
    ArrayList<PageBlockMedia> mediaBlocks = new ArrayList<>();
    for (PageBlock block : blocks) {
      if (block instanceof PageBlockMedia && ((PageBlockMedia) block).bindToList(this, getDisplayUrl(), mediaBlocks)) {
        mediaBlocks.add((PageBlockMedia) block);
      }
      items.add(new ListItem(block.getRelatedViewType()).setData(block));
    }

    this.mediaBlocks = mediaBlocks;
    // recyclerView.setItemAnimator(null);
    adapter.setItems(items, false);
    recyclerView.invalidateItemDecorations();
    if (!StringUtils.isEmpty(args.anchorLink)) {
      scrollToAnchor(args.anchorLink, false);
    }
    // recyclerView.setItemAnimator(new CustomItemAnimator(Anim.DECELERATE_INTERPOLATOR, 180l));

    if (!isReplace) {
      tdlib.client().send(new TdApi.GetWebPageInstantView(getUrl(), true), this);
    }
  }

  public String getUrl () {
    return getArgumentsStrict().webPage.url;
  }

  public String getDisplayUrl () {
    return getArgumentsStrict().webPage.displayUrl;
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.WebPageInstantView.CONSTRUCTOR: {
        final TdApi.WebPageInstantView instantView = (TdApi.WebPageInstantView) object;
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            if (!TD.hasInstantView(instantView.version)) {
              UI.showToast(R.string.InstantViewUnsupported, Toast.LENGTH_SHORT);
              UI.openUrl(getUrl());
            } else {
              ArrayList<PageBlock> pageBlocks;
              try {
                pageBlocks = parsePageBlocks(instantView);
              } catch (PageBlock.UnsupportedPageBlockException ignored) {
                return;
              }
              getArgumentsStrict().instantView = instantView;
              buildCells(pageBlocks, true);
            }
          }
        });
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        break;
      }
      default: {
        Log.unexpectedTdlibResponse(object, TdApi.GetWebPageInstantView.class, TdApi.WebPageInstantView.class);
        break;
      }
    }
  }

  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    ArrayList<TdApi.Message> out = new ArrayList<>();
    int foundIndex = -1;
    int desiredType;
    switch (fromMessage.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR:
        desiredType = InlineResult.TYPE_AUDIO;
        break;
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        desiredType = InlineResult.TYPE_VOICE;
        break;
      default:
        return null;
    }
    final int count = adapter.getItems().size();
    for (int i = count - 1; i >= 0; i--) {
      PageBlock pageBlock = (PageBlock) adapter.getItems().get(i).getData();
      InlineResultCommon result = pageBlock instanceof PageBlockFile ? ((PageBlockFile) pageBlock).getFile() : null;
      if (result == null || result.getType() != desiredType) {
        continue;
      }
      TdApi.Message msg = result.getPlayPauseMessage();
      if (msg == fromMessage) {
        if (foundIndex != -1) {
          throw new IllegalStateException();
        }
        foundIndex = out.size();
      }
      out.add(msg);
    }
    if (foundIndex == -1) {
      throw new IllegalArgumentException();
    }
    return new TGPlayerController.PlayList(out, foundIndex).setPlayListFlags(TGPlayerController.PLAYLIST_FLAG_REVERSE).setReachedEnds(true, true);
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return false;
  }

  // private PopupLayout layout;

  public void show () throws UnsupportedOperationException {
    if (context.navigation().isEmpty()) {
      destroy();
    } else {
      get();
      context.navigation().navigateTo(this);
    }
  }
}
