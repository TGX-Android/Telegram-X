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
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import java.util.ArrayList;

import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.Td;

public abstract class PageBlock {
  protected final ViewController<?> context;
  protected final TdApi.PageBlock block;
  protected MultipleViewProvider currentViews;

  protected boolean mergeBottom, mergeTop;

  public PageBlock (ViewController<?> context, TdApi.PageBlock block) {
    this.context = context;
    this.block = block;
    this.currentViews = new MultipleViewProvider();
  }

  public ViewController<?> parent () {
    return context;
  }

  public TdApi.PageBlock getOriginalBlock () {
    return block;
  }

  private PageBlock chatLinkBlock;

  private String anchor;
  private boolean anchorIsBottom;

  public final void setAnchor (String anchor, boolean isBottom) {
    this.anchor = anchor;
    this.anchorIsBottom = isBottom;
  }

  public boolean belongsToBlock (PageBlock pageBlock) {
    return pageBlock == this || (pageBlock != null && chatLinkBlock == pageBlock);
  }

  public void requestIcons (ComplexReceiver receiver) {
    if (chatLinkBlock != null) {
      chatLinkBlock.requestIcons(receiver);
    } else {
      receiver.clear();
    }
  }

  public String getAnchor () {
    return this.anchor;
  }

  public boolean isAnchorOnBottom () {
    return anchorIsBottom;
  }

  protected boolean isPost;

  public boolean isPost () {
    return true;
  }

  public void setIsPost () {
    this.isPost = true;
  }

  public boolean isClickable () {
    return false;
  }

  public boolean onClick (View view, boolean isLongPress) {
    return false;
  }

  protected ListItemInfo[] listItemInfo;

  public void setListItem (ListItemInfo[] info) {
    this.listItemInfo = info;
  }

  protected PageBlock details;

  public void setDetails (PageBlock details) {
    this.details = details;
  }

  protected int detailsHeaderItemCount, detailsFooterItemCount;

  public void setDetailsDecorations (int headerItemCount, int footerItemCount) {

  }

  public boolean isChildOf (@NonNull PageBlock pageBlock) {
    PageBlock details = this.details;
    while (details != null) {
      if (details == pageBlock)
        return true;
      details = details.details;
    }
    return false;
  }

  public boolean isIndependent () {
    return listItemInfo == null && details == null && !isPost;
  }

  public ListItemInfo[] getListItem () {
    return listItemInfo;
  }

  public void mergeWith (PageBlock topBlock) {
    mergeTop = true;
    topBlock.mergeBottom = true;
  }

  public final ViewProvider getViewProvider () {
    return currentViews;
  }

  public final void attachToView (View view) {
    currentViews.attachToView(view);
  }

  public final void detachFromView (View view) {
    currentViews.detachFromView(view);
  }

  public abstract int getRelatedViewType ();

  private int maxWidth;
  private int computedHeight;

  protected int getMaxWidth () {
    return maxWidth;
  }

  public final void invalidateHeight (View view) {
    final int lastWidth = this.maxWidth;
    if (lastWidth != 0) {
      this.maxWidth = 0;
      final int lastHeight = computedHeight;
      computedHeight = computeHeight(view, lastWidth);
      if (chatLinkBlock != null) {
        computedHeight = Math.max(chatLinkBlock.getHeight(view, lastWidth), computedHeight);
      }
      if (lastHeight != computedHeight) {
        currentViews.requestLayout();
      }
    }
  }

  public boolean allowScrolling () {
    return false;
  }

  protected final int getComputedHeight () {
    return computedHeight;
  }

  public final int getHeight (View view, int width) {
    if (width != maxWidth && width != 0) {
      computedHeight = computeHeight(view, width);
      if (chatLinkBlock != null) {
        computedHeight = Math.max(chatLinkBlock.getHeight(view, width), computedHeight);
      }
      maxWidth = width;
    }
    return computedHeight;
  }

  public void initializeLayout (View view, View parent) {
    // override
  }

  public void applyLayoutMargins (View view, FrameLayoutFix.LayoutParams params, int viewWidth, int viewHeight) {
    // override
  }

  protected abstract int computeHeight (View view, int width);

  public void requestPreview (DoubleImageReceiver receiver) { }
  public void requestImage (ImageReceiver receiver) { }
  public void requestGif (GifReceiver receiver) { }
  public void requestFiles (ComplexReceiver receiver, boolean invalidate) { }
  public int getImageContentRadius () { return 0; }
  public void autoDownloadContent () { }

  public final boolean onTouchEvent (View view, MotionEvent e) {
    if (chatLinkBlock != null) {
      int deltaY = getContentTop() + getContentHeight() - chatLinkBlock.getComputedHeight();
      e.offsetLocation(0, -deltaY);
      if (chatLinkBlock.onTouchEvent(view, e))
        return true;
      e.offsetLocation(0, deltaY);
    }
    return handleTouchEvent(view, e);
  }

  public int getCustomWidth () {
    return -1;
  }

  protected abstract boolean handleTouchEvent (View view, MotionEvent e);

  protected final int getMinimumContentPadding (boolean leftEdge) {
    return isPost ? Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) + (leftEdge ? Screen.dp(12f) : getDefaultContentPadding(leftEdge)) : getDefaultContentPadding(leftEdge);
  }

  protected int getDefaultContentPadding (boolean leftEdge) {
    return listItemInfo != null ? ((isPost || !leftEdge) ? Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) : Screen.dp(10f)) : 0;
  }

  protected abstract int getContentTop ();
  protected abstract int getContentHeight ();
  public int getBulletTop () {
    return getContentTop();
  }
  public boolean hasChildAnchor (@NonNull String anchor) {
    return false;
  }
  public int getChildAnchorTop (@NonNull String anchor, int viewWidth) {
    return 0;
  }
  public int getBackgroundColorId () {
    return ColorId.filling;
  }

  protected int getTotalContentPadding () {
    return getMinimumContentPadding(true) - getMinimumContentPadding(false);
  }

  public final <T extends View & DrawableProvider> void draw (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {
    if (isPost) {
      final int lineColor = Theme.getColor(ColorId.iv_blockQuoteLine);
      RectF rectF = Paints.getRectF();
      int lineWidth = Screen.dp(3f);
      int linePadding = Screen.dp(8f) / 2;
      int contentLeft = Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET);
      int contentTop = getContentTop();
      int contentHeight = getContentHeight();
      // int viewWidth = view.getMeasuredWidth();

      rectF.top = contentTop - linePadding;
      rectF.bottom = contentTop + linePadding + contentHeight + Screen.dp(!mergeBottom ? 1.5f : 0);
      rectF.left = contentLeft;
      rectF.right = contentLeft + lineWidth;
      /*if (rtl) {
        rectF.left = viewWidth - contentLeft - lineWidth;
        rectF.right = viewWidth - contentLeft;
      }*/
      c.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, Paints.fillingPaint(lineColor));

      if (mergeTop) {
        c.drawRect(rectF.left, 0, rectF.right, rectF.top + lineWidth, Paints.fillingPaint(lineColor));
      }
      if (mergeBottom) {
        c.drawRect(rectF.left, rectF.bottom - lineWidth, rectF.right, view.getMeasuredHeight(), Paints.fillingPaint(lineColor));
      }
      drawInternal(view, c, preview, receiver, iconReceiver);
    } else {
      drawInternal(view, c, preview, receiver, iconReceiver);
    }
    if (chatLinkBlock != null) {
      int saveCount = Views.save(c);
      c.translate(0, getContentTop() + getContentHeight() - chatLinkBlock.getComputedHeight());
      chatLinkBlock.draw(view, c, null, null, iconReceiver);
      Views.restore(c, saveCount);
    }
  }
  protected abstract <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver);

  public static class ListInfo {
    public final TdApi.PageBlockList list;
    public float maxLabelWidth;

    public ListInfo (TdApi.PageBlockList list) {
      this.list = list;
    }
  }

  public static class ListItemInfo {
    public final ListInfo list;
    public final int itemIndex;
    public final Text label;
    public PageBlock firstBlock;

    public ListItemInfo (ListInfo list, int itemIndex, String label, TextStyleProvider provider) {
      this.list = list;
      this.itemIndex = itemIndex;
      this.label = new Text.Builder(label, Screen.dp(100f), provider, TextColorSets.InstantView.NORMAL).build();
    }
  }

  public static class ParseContext {
    public final String url;
    public final boolean isRtl;
    public final TGPlayerController.PlayListBuilder playListBuilder;

    private PageBlock lastBlock;
    private String nextAnchor;
    private boolean isCover;
    private boolean isPost;
    private boolean isClosed;
    private PageBlock coverBlock;
    private final int viewCount;

    private ListItemInfo[] openedList;
    private PageBlock detailsBlock;

    public ParseContext (String url, TdApi.WebPageInstantView instantView, TGPlayerController.PlayListBuilder playListBuilder) {
      this.url = url;
      this.isRtl = instantView.isRtl;
      this.viewCount = instantView.viewCount;
      this.playListBuilder = playListBuilder;
    }

    private void processCaption (ViewController<?> parent, @NonNull TdApi.PageBlock mediaBlock, TdApi.PageBlockCaption caption, @Nullable TdlibUi.UrlOpenParameters openParameters, ArrayList<PageBlock> out) {
      PageBlockRichText captionBlock = null;
      boolean needMerge = (lastBlock != null && lastBlock.block == mediaBlock) || mediaBlock.getConstructor() == TdApi.PageBlockEmbeddedPost.CONSTRUCTOR;
      if (!Td.isEmpty(caption.text)) {
        captionBlock = new PageBlockRichText(parent, mediaBlock, caption, false, isCover, openParameters);
        if (needMerge) {
          captionBlock.mergeWith(lastBlock);
        }
        process(captionBlock, out);
      }
      if (!Td.isEmpty(caption.credit)) {
        PageBlockRichText credit = new PageBlockRichText(parent, mediaBlock, caption, true, isCover, openParameters);
        if (captionBlock != null || needMerge) {
          credit.mergeWith(captionBlock != null ? captionBlock : lastBlock);
        }
        process(credit, out);
      }
    }

    private boolean hasKicker;

    private void setClosed (boolean isClosed, ViewController<?> context, ArrayList<PageBlock> out, boolean needOffset) {
      if (this.isClosed != isClosed) {
        this.isClosed = isClosed;
        if (needOffset && isClosed && !((lastBlock != null && lastBlock.block != null) && (lastBlock.block.getConstructor() == TdApi.PageBlockDetails.CONSTRUCTOR || lastBlock.block.getConstructor() == TdApi.PageBlockChatLink.CONSTRUCTOR))) {
          processImpl(new PageBlockSimple(context, ListItem.TYPE_EMPTY_OFFSET_NO_HEAD, ColorId.filling), out);
        }
        processImpl(new PageBlockSimple(context, isClosed ? ListItem.TYPE_SHADOW_BOTTOM : ListItem.TYPE_SHADOW_TOP, 0), out);
        if (needOffset && !isClosed) {
          processImpl(new PageBlockSimple(context, ListItem.TYPE_EMPTY_OFFSET_NO_HEAD, ColorId.filling), out);
        }
      }
    }

    private void process (PageBlock block, ArrayList<PageBlock> out) {
      TdApi.PageBlock pageBlock = block.block;
      if (pageBlock != null) {
        setClosed(!isPost && openedList == null && detailsBlock == null && (pageBlock.getConstructor() == TdApi.PageBlockFooter.CONSTRUCTOR), block.context, out, pageBlock.getConstructor() != TdApi.PageBlockChatLink.CONSTRUCTOR);
      }
      if (lastBlock != null && (lastBlock != detailsBlock && ((lastBlock.block != null && lastBlock.block.getConstructor() == TdApi.PageBlockDetails.CONSTRUCTOR) || (lastBlock.details != null && lastBlock.details != detailsBlock)))) {
        processImpl(new PageBlockSimple(block.context, ListItem.TYPE_EMPTY_OFFSET_NO_HEAD, ColorId.filling), out);
      }
      processImpl(block, out);
    }

    private void processImpl (PageBlock block, ArrayList<PageBlock> out) {
      if (nextAnchor != null) {
        block.setAnchor(nextAnchor, false);
        nextAnchor = null;
      }
      if (isCover && block instanceof PageBlockMedia) {
        ((PageBlockMedia) block).setIsCover();
      }

      if (isPost) {
        block.setIsPost();
        if (lastBlock != null && lastBlock.isPost) {
          block.mergeWith(lastBlock);
        }
      }

      if (openedList != null && !(block instanceof PageBlockSimple)) {
        if (openedList[openedList.length - 1].firstBlock == null) {
          openedList[openedList.length - 1].firstBlock = block;
        }
        block.setListItem(openedList);
      }
      if (detailsBlock != null) {
        block.setDetails(detailsBlock);
      }

      out.add(lastBlock = block);
    }

    private void setAnchor (String anchor) {
      if (lastBlock != null) {
        lastBlock.setAnchor(anchor, true);
      }
      nextAnchor = anchor;
    }
  }

  public static ArrayList<PageBlock> parse (ViewController<?> parent, String url, @NonNull TdApi.WebPageInstantView instantView, @Nullable PageBlock detailsBlock, TGPlayerController.PlayListBuilder playListBuilder, @Nullable TdlibUi.UrlOpenParameters urlOpenParameters) throws UnsupportedPageBlockException {
    PageBlock.ParseContext context = new PageBlock.ParseContext(url, instantView, playListBuilder);
    context.detailsBlock = context.lastBlock = detailsBlock;
    TdApi.PageBlock[] pageBlocks = detailsBlock != null ? ((TdApi.PageBlockDetails) detailsBlock.getOriginalBlock()).pageBlocks : instantView.pageBlocks;
    boolean needPadding = (detailsBlock != null && pageBlocks.length > 0);
    ArrayList<PageBlock> out = new ArrayList<>(pageBlocks.length);
    if (needPadding) {
      context.process(new PageBlockSimple(parent, ListItem.TYPE_EMPTY_OFFSET_NO_HEAD, ColorId.filling), out);
    }
    for (TdApi.PageBlock rawPageBlock : pageBlocks) {
      parse(parent, out, context, rawPageBlock, urlOpenParameters);
    }
    if (needPadding) {
      context.process(new PageBlockSimple(parent, ListItem.TYPE_EMPTY_OFFSET_NO_HEAD, ColorId.filling), out);
    }
    if (detailsBlock == null) {
      context.setClosed(true, parent, out, true);
      boolean needReportButton = !parent.tdlib().isKnownHost(url, true);
      if (instantView.viewCount > 0 || needReportButton) {
        // TODO view counter + "Wrong layout?"
      }
    }
    return out;
  }

  public static class UnsupportedPageBlockException extends Exception { }

  private static void parse (ViewController<?> parent, ArrayList<PageBlock> out, ParseContext context, TdApi.PageBlock block, @Nullable TdlibUi.UrlOpenParameters openParameters) throws UnsupportedPageBlockException {
    switch (block.getConstructor()) {
      // Page cover
      case TdApi.PageBlockCover.CONSTRUCTOR: {
        TdApi.PageBlockCover cover = (TdApi.PageBlockCover) block;
        context.isCover = true;
        int index = out.size();
        parse(parent, out, context, cover.cover, openParameters);
        context.isCover = false;
        for (int i = index; i < out.size(); i++) {
          if (out.get(i).block == cover.cover) {
            context.coverBlock = out.get(i);
            break;
          }
        }
        break;
      }

      // Different variations of text blocks
      case TdApi.PageBlockTitle.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockTitle) block, out.isEmpty(), context.hasKicker, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockSubtitle.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockSubtitle) block, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockAuthorDate.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockAuthorDate) block, context.viewCount, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockKicker.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockKicker) block, openParameters);
        context.process(text, out);
        context.hasKicker = true;
        break;
      }
      case TdApi.PageBlockHeader.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockHeader) block, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockSubheader.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockSubheader) block, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockParagraph.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockParagraph) block, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockPreformatted.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockPreformatted) block, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockChatLink.CONSTRUCTOR: {
        boolean added = false;
        if (context.coverBlock != null) {
          switch (context.coverBlock.getRelatedViewType()) {
            case ListItem.TYPE_PAGE_BLOCK_MEDIA:
            case ListItem.TYPE_PAGE_BLOCK_GIF:
              PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockChatLink) block, true, context.viewCount, openParameters);
              text.currentViews = context.coverBlock.currentViews;
              context.coverBlock.chatLinkBlock = text;
              added = true;
              break;
          }
        }
        if (!added) {
          context.process(new PageBlockRichText(parent, (TdApi.PageBlockChatLink) block, false, context.viewCount, openParameters), out);
        }
        break;
      }
      case TdApi.PageBlockFooter.CONSTRUCTOR: {
        PageBlockRichText text = new PageBlockRichText(parent, (TdApi.PageBlockFooter) block, context.isPost, openParameters);
        context.process(text, out);
        break;
      }
      case TdApi.PageBlockBlockQuote.CONSTRUCTOR: {
        TdApi.PageBlockBlockQuote quoteRaw = (TdApi.PageBlockBlockQuote) block;
        PageBlockRichText quote = new PageBlockRichText(parent, quoteRaw, false, openParameters);
        context.process(quote, out);
        if (!Td.isEmpty(quoteRaw.credit)) {
          PageBlockRichText credit = new PageBlockRichText(parent, quoteRaw, true, openParameters);
          credit.mergeWith(quote);
          context.process(credit, out);
        }
        break;
      }
      case TdApi.PageBlockPullQuote.CONSTRUCTOR: {
        TdApi.PageBlockPullQuote quoteRaw = (TdApi.PageBlockPullQuote) block;
        PageBlockRichText quote = new PageBlockRichText(parent, quoteRaw, false, openParameters);
        context.process(quote, out);
        if (!Td.isEmpty(quoteRaw.credit)) {
          PageBlockRichText credit = new PageBlockRichText(parent, quoteRaw, true, openParameters);
          credit.mergeWith(quote);
          context.process(credit, out);
        }
        break;
      }

      // List of texts
      case TdApi.PageBlockList.CONSTRUCTOR: {
        TdApi.PageBlockList listRaw = (TdApi.PageBlockList) block;
        int itemIndex = 0;
        ListInfo listInfo = new ListInfo(listRaw);
        for (TdApi.PageBlockListItem item : listRaw.items) {
          ListItemInfo itemInfo = new ListItemInfo(listInfo, itemIndex, item.label, PageBlockRichText.getListTextProvider());
          listInfo.maxLabelWidth = Math.max(listInfo.maxLabelWidth, itemInfo.label.getWidth());
          ListItemInfo[] lastListItemInfo = context.openedList;
          if (lastListItemInfo == null) {
            context.openedList = new ListItemInfo[] {itemInfo};
          } else {
            context.openedList = new ListItemInfo[lastListItemInfo.length + 1];
            System.arraycopy(lastListItemInfo, 0, context.openedList, 0, lastListItemInfo.length);
            context.openedList[lastListItemInfo.length] = itemInfo;
          }
          int lastSize = out.size();
          for (TdApi.PageBlock pageBlock : item.pageBlocks) {
            parse(parent, out, context, pageBlock, openParameters);
          }
          if (out.size() - lastSize == 0) { // Empty list item
            context.process(new PageBlockRichText(parent, listRaw, openParameters), out);
          }
          context.openedList = lastListItemInfo;
          itemIndex++;
        }
        break;
      }

      // Table
      case TdApi.PageBlockTable.CONSTRUCTOR: {
        TdApi.PageBlockTable tableRaw = (TdApi.PageBlockTable) block;
        if (!Td.isEmpty(tableRaw.caption)) {
          context.process(new PageBlockRichText(parent, tableRaw, openParameters), out);
        }
        context.process(new PageBlockTable(parent, tableRaw, openParameters), out);
        break;
      }

      case TdApi.PageBlockDetails.CONSTRUCTOR: {
        TdApi.PageBlockDetails detailsRaw = (TdApi.PageBlockDetails) block;
        PageBlockRichText details = new PageBlockRichText(parent, detailsRaw, openParameters);
        if (detailsRaw.isOpen) {
          context.process(details, out);
          PageBlock prevDetails = context.detailsBlock;
          context.detailsBlock = details;
          for (TdApi.PageBlock pageBlock : detailsRaw.pageBlocks) {
            parse(parent, out, context, pageBlock, openParameters);
          }
          context.detailsBlock = prevDetails;
        } else {
          context.process(details, out);
        }
        break;
      }

      // Divider
      case TdApi.PageBlockDivider.CONSTRUCTOR: {
        context.process(new PageBlockDivider(parent,  block), out);
        break;
      }

      // Media
      case TdApi.PageBlockAnimation.CONSTRUCTOR: {
        TdApi.PageBlockAnimation mediaRaw = (TdApi.PageBlockAnimation) block;
        PageBlockMedia media = new PageBlockMedia(parent, mediaRaw);
        context.process(media, out);
        context.processCaption(parent, mediaRaw, mediaRaw.caption, openParameters, out);
        break;
      }
      case TdApi.PageBlockPhoto.CONSTRUCTOR: {
        TdApi.PageBlockPhoto mediaRaw = (TdApi.PageBlockPhoto) block;
        PageBlockMedia media = new PageBlockMedia(parent, mediaRaw, null, openParameters);
        context.process(media, out);
        context.processCaption(parent, mediaRaw, mediaRaw.caption, openParameters, out);
        break;
      }
      case TdApi.PageBlockMap.CONSTRUCTOR: {
        TdApi.PageBlockMap mapRaw = (TdApi.PageBlockMap) block;
        PageBlockMedia map = new PageBlockMedia(parent, mapRaw);
        context.process(map, out);
        context.processCaption(parent, mapRaw, mapRaw.caption, openParameters, out);
        break;
      }
      case TdApi.PageBlockVideo.CONSTRUCTOR: {
        TdApi.PageBlockVideo mediaRaw = (TdApi.PageBlockVideo) block;
        PageBlockMedia media = new PageBlockMedia(parent, mediaRaw);
        context.process(media, out);
        context.processCaption(parent, mediaRaw, mediaRaw.caption, openParameters, out);
        break;
      }
      case TdApi.PageBlockRelatedArticles.CONSTRUCTOR: {
        TdApi.PageBlockRelatedArticles relatedRaw = (TdApi.PageBlockRelatedArticles) block;
        context.setClosed(true, parent, out, true);
        if (!Td.isEmpty(relatedRaw.header)) {
          context.setClosed(false, parent, out, true);
          PageBlockRichText header = new PageBlockRichText(parent, relatedRaw, openParameters);
          context.process(header, out);
        } else {
          context.setClosed(false, parent, out, false);
        }
        int index = 0;
        for (TdApi.PageBlockRelatedArticle related : relatedRaw.articles) {
          if (index > 0) {
            context.process(new PageBlockSimple(parent, ListItem.TYPE_SEPARATOR_FULL, ColorId.filling), out);
          }
          context.process(new PageBlockRelatedArticle(parent, relatedRaw, related, openParameters), out);
          index++;
        }
        context.setClosed(true, parent, out, false);
        break;
      }
      case TdApi.PageBlockCollage.CONSTRUCTOR: {
        TdApi.PageBlockCollage collageRaw = (TdApi.PageBlockCollage) block;
        if (collageRaw.pageBlocks.length == 0) {
          break;
        }
        boolean isOk = true;
        for (TdApi.PageBlock pageBlock : collageRaw.pageBlocks) {
          switch (pageBlock.getConstructor()) {
            case TdApi.PageBlockPhoto.CONSTRUCTOR:
            case TdApi.PageBlockVideo.CONSTRUCTOR:
            case TdApi.PageBlockAnimation.CONSTRUCTOR: {
              continue;
            }
          }
          isOk = false;
          break;
        }
        if (isOk) {
          PageBlockMedia collage = new PageBlockMedia(parent, collageRaw);
          context.process(collage, out);
          context.processCaption(parent, collageRaw, collageRaw.caption, openParameters, out);
        }
        break;
      }
      case TdApi.PageBlockSlideshow.CONSTRUCTOR: {
        TdApi.PageBlockSlideshow slideshowRaw = (TdApi.PageBlockSlideshow) block;

        if (slideshowRaw.pageBlocks.length == 0) {
          break;
        }

        boolean isOk = true;
        for (TdApi.PageBlock pageBlock : slideshowRaw.pageBlocks) {
          switch (pageBlock.getConstructor()) {
            case TdApi.PageBlockPhoto.CONSTRUCTOR:
            case TdApi.PageBlockVideo.CONSTRUCTOR:
            case TdApi.PageBlockAnimation.CONSTRUCTOR: {
              continue;
            }
          }
          isOk = false;
          break;
        }
        if (isOk) {
          PageBlockMedia slideshow = new PageBlockMedia(parent, slideshowRaw);
          context.process(slideshow, out);
          context.processCaption(parent, slideshowRaw, slideshowRaw.caption, openParameters, out);
        }
        break;
      }

      // File
      case TdApi.PageBlockAudio.CONSTRUCTOR: {
        TdApi.PageBlockAudio audioRaw = (TdApi.PageBlockAudio) block;
        if (audioRaw.audio != null) {
          PageBlockFile audio = new PageBlockFile(parent, audioRaw, context.url, context.playListBuilder);
          context.process(audio, out);
        }
        context.processCaption(parent, audioRaw, audioRaw.caption, openParameters, out);
        break;
      }
      case TdApi.PageBlockVoiceNote.CONSTRUCTOR: {
        TdApi.PageBlockVoiceNote voiceNoteRaw = (TdApi.PageBlockVoiceNote) block;
        if (voiceNoteRaw.voiceNote != null) {
          PageBlockFile voiceNote = new PageBlockFile(parent, voiceNoteRaw, context.url, context.playListBuilder);
          context.process(voiceNote, out);
        }
        context.processCaption(parent, voiceNoteRaw, voiceNoteRaw.caption, openParameters, out);
        break;
      }

      // Invisible anchor
      case TdApi.PageBlockAnchor.CONSTRUCTOR: {
        TdApi.PageBlockAnchor anchor = (TdApi.PageBlockAnchor) block;
        context.setAnchor(anchor.name);
        break;
      }

      // Embeddeds
      case TdApi.PageBlockEmbedded.CONSTRUCTOR: {
        TdApi.PageBlockEmbedded embeddedRaw = (TdApi.PageBlockEmbedded) block;
        PageBlockMedia embedded = null;
        if (embeddedRaw.posterPhoto != null) {
          EmbeddedService service = EmbeddedService.parse(embeddedRaw);
          if (service != null) {
            TdApi.PageBlockPhoto fakePhoto = new TdApi.PageBlockPhoto(embeddedRaw.posterPhoto, embeddedRaw.caption, null);
            embedded = new PageBlockMedia(parent, fakePhoto, service, null);
          }
        }
        if (embedded == null) {
          embedded = new PageBlockMedia(parent, embeddedRaw);
        }
        context.process(embedded, out);
        context.processCaption(parent, embeddedRaw, embeddedRaw.caption, openParameters, out);
        break;
      }
      case TdApi.PageBlockEmbeddedPost.CONSTRUCTOR: {
        TdApi.PageBlockEmbeddedPost postRaw = (TdApi.PageBlockEmbeddedPost) block;

        context.isPost = true;
        context.lastBlock = null;

        PageBlockRichText author = new PageBlockRichText(parent, postRaw, openParameters);
        context.process(author, out);
        for (TdApi.PageBlock pageBlock : postRaw.pageBlocks) {
          parse(parent, out, context, pageBlock, openParameters);
        }
        context.processCaption(parent, postRaw, postRaw.caption, openParameters, out);

        context.isPost = false;

        break;
      }

      default: {
        throw new UnsupportedOperationException(block.toString());
      }
    }
  }
}
