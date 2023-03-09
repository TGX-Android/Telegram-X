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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.MediaCollectorDelegate;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CollageContext;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.widget.PageBlockView;
import org.thunderdog.challegram.widget.PageBlockWrapView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class PageBlockMedia extends PageBlock implements MediaWrapper.OnClickListener, MediaCollectorDelegate, MediaViewDelegate {
  public static final float MEDIA_MARGIN = 16f, MEDIA_POST_MARGIN = 8f;

  private @Nullable MediaWrapper wrapper;
  private TdlibUi.UrlOpenParameters urlOpenParameters;

  private boolean isList;
  private TdApi.PageBlockMap map;
  private ImageFile mapFile;
  private @Nullable ArrayList<MediaWrapper> wrappers;
  private ArrayList<TdApi.PageBlockCaption> captions;

  private TdApi.PageBlockCaption caption;
  private String url;

  private Drawable linkIcon;

  private boolean useGif;
  private boolean isCover;

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockPhoto photo, @Nullable EmbeddedService nativeEmbed, @Nullable TdlibUi.UrlOpenParameters urlOpenParameters) {
    super(context, photo);
    this.urlOpenParameters = urlOpenParameters;
    if (photo.photo != null) {
      wrapper = new MediaWrapper(context.context(), context.tdlib(), photo.photo, 0, 0, null, false, false, nativeEmbed);
      initWrapper(wrapper);
      setCaption(photo.caption);
      setUrl(photo.url);
    }
  }

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockMap map) {
    super(context, map);
    this.map = map;
    setCaption(map.caption);
    switch (Settings.instance().getMapProviderType(true)) {
      case Settings.MAP_PROVIDER_GOOGLE: {
        String url = U.getMapPreview(context.tdlib(), map.location.latitude, map.location.longitude, map.zoom, false, map.width, map.height, null);
        this.mapFile = new ImageFileRemote(context.tdlib(), url, new TdApi.FileTypeThumbnail());
        this.mapFile.setScaleType(ImageFile.CENTER_CROP);
        break;
      }
      case Settings.MAP_PROVIDER_TELEGRAM:
      default: {
        int w = map.width;
        int h = map.height;

        if (w > 1024 || h > 1024) {
          float scale = 1024f / (float) Math.max(w, h);
          w *= scale;
          h *= scale;
        }
        w = Math.max(14, w);
        h = Math.max(14, h);

        int scale = Screen.density() >= 2.0f ? 2 : 1;
        w /= scale;
        h /= scale;

        int zoom = Math.max(13, Math.min(20, map.zoom));
        this.mapFile = new ImageFileRemote(context.tdlib(), new TdApi.GetMapThumbnailFile(map.location, zoom, w, h, scale, 0), "telegram_map_" + map.location.latitude + "," + map.location.longitude + (zoom != 16 ? "," + zoom : ""));
        break;
      }
    }
    this.mapFile.setScaleType(ImageFile.CENTER_CROP);
  }

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockAnimation animation) {
    super(context, animation);
    this.useGif = true;
    if (animation.animation != null) {
      wrapper = new MediaWrapper(context.context(), context.tdlib(), animation.animation, 0, 0, null, false, true, !animation.needAutoplay, null);
      initWrapper(wrapper);
      setCaption(animation.caption);
    }
  }

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockVideo video) {
    super(context, video);
    if (video.video != null) {
      wrapper = new MediaWrapper(context.context(), context.tdlib(), video.video, 0, 0, null, false);
      initWrapper(wrapper);
      setCaption(video.caption);
    }
  }

  // Collage

  private CollageContext collageContext;

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockCollage collage) {
    super(context, collage);
    setCaption(collage.caption);
    parseWrappers(collage.pageBlocks);
    if (wrappers != null && !wrappers.isEmpty()) {
      collageContext = new CollageContext(wrappers, Screen.dp(2f));
    }
  }

  // Slideshow

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockSlideshow slideshow) {
    super(context, slideshow);
    this.isList = true;
    setCaption(slideshow.caption);
    parseWrappers(slideshow.pageBlocks);
  }

  private void initWrapper (MediaWrapper wrapper) {
    wrapper.setNoRoundedCorners();
    wrapper.setViewProvider(currentViews);
    wrapper.setOnClickListener(this);
  }

  private void parseWrappers (TdApi.PageBlock[] pageBlocks) {
    this.wrappers = new ArrayList<>(pageBlocks.length);
    this.captions = new ArrayList<>(pageBlocks.length);
    for (TdApi.PageBlock pageBlock : pageBlocks) {
      MediaWrapper wrapper = null;
      TdApi.PageBlockCaption caption = null;
      switch (pageBlock.getConstructor()) {
        case TdApi.PageBlockPhoto.CONSTRUCTOR: {
          TdApi.PageBlockPhoto photo = (TdApi.PageBlockPhoto) pageBlock;
          if (photo.photo != null) {
            wrapper = new MediaWrapper(context.context(), context.tdlib(), photo.photo, 0, 0, null, false);
            initWrapper(wrapper);
            caption = photo.caption;
          }
          break;
        }
        case TdApi.PageBlockVideo.CONSTRUCTOR: {
          TdApi.PageBlockVideo video = (TdApi.PageBlockVideo) pageBlock;
          if (video.video != null) {
            wrapper = new MediaWrapper(context.context(), context.tdlib(), video.video, 0, 0, null, false);
            initWrapper(wrapper);
            caption = video.caption;
          }
          break;
        }
        case TdApi.PageBlockAnimation.CONSTRUCTOR: {
          TdApi.PageBlockAnimation animation = (TdApi.PageBlockAnimation) pageBlock;
          if (animation.animation != null) {
            wrapper = new MediaWrapper(context.context(), context.tdlib(), animation.animation, 0, 0, null, false, true, !animation.needAutoplay, null);
            initWrapper(wrapper);
            caption = animation.caption;
          }
          break;
        }
        default: {
          throw new IllegalArgumentException("pageBlock.getConstructor() == " + pageBlock.getConstructor());
        }
      }
      if (wrapper != null) {
        wrappers.add(wrapper);
        captions.add(caption);
      }
    }
  }

  // Embedded

  private TdApi.PageBlockEmbedded embedded;
  private ImageFile embeddedMiniThumbnail, embeddedPreview, embeddedPoster;

  public PageBlockMedia (ViewController<?> context, TdApi.PageBlockEmbedded embedded) {
    super(context, embedded);
    this.embedded = embedded;
    if (embedded.posterPhoto != null) {
      if (embedded.posterPhoto.minithumbnail != null) {
        embeddedMiniThumbnail = new ImageFileLocal(embedded.posterPhoto.minithumbnail);
        embeddedMiniThumbnail.setScaleType(ImageFile.CENTER_CROP);
      }
      TdApi.PhotoSize previewSize = MediaWrapper.buildPreviewSize(embedded.posterPhoto);
      TdApi.PhotoSize targetSize = MediaWrapper.buildTargetFile(embedded.posterPhoto, previewSize);
      if (previewSize != null) {
        embeddedPreview = new ImageFile(context.tdlib(), previewSize.photo);
        embeddedPreview.setScaleType(ImageFile.CENTER_CROP);
      }
      if (targetSize != null) {
        embeddedPoster = new ImageFile(context.tdlib(), targetSize.photo);
        embeddedPoster.setScaleType(ImageFile.CENTER_CROP);
        MediaWrapper.applyMaxSize(embeddedPoster, targetSize);
      }
    }
    setCaption(embedded.caption);
  }

  @Override
  protected int getDefaultContentPadding (boolean leftEdge) {
    return embedded != null && !embedded.isFullWidth ? Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) : super.getDefaultContentPadding(leftEdge);
  }

  public boolean isUnknownHeight () {
    return embedded != null && (embedded.width == 0 || embedded.height == 0);
  }

  public void processWebView (WebView webView) {
    if (!StringUtils.isEmpty(embedded.html)) {
      webView.loadDataWithBaseURL("https://telegram.org/embed", embedded.html, "text/html", "UTF-8", null);
    } else {
      Log.v("embedded.url: %s", embedded.url);
      webView.loadUrl(embedded.url);
    }
  }

  @Override
  public boolean allowScrolling () {
    return embedded != null && embedded.allowScrolling;
  }

  // Impl

  private boolean ignoreBottomPadding;

  private void setCaption (TdApi.PageBlockCaption caption) {
    if (!Td.isEmpty(caption.text) || !Td.isEmpty(caption.credit)) {
      this.caption = caption;
      ignoreBottomPadding = true;
    }
  }

  private void setUrl (String url) {
    if (!StringUtils.equalsOrBothEmpty(this.url, url)) {
      this.url = url;
      if (linkIcon == null) {
        linkIcon = Drawables.get(R.drawable.baseline_launch_24);
      }
    }
  }

  public void setIsCover () {
    this.isCover = true;
  }

  @Override
  public int getRelatedViewType () {
    return embedded != null ? ListItem.TYPE_PAGE_BLOCK_EMBEDDED : isList ? ListItem.TYPE_PAGE_BLOCK_SLIDESHOW : collageContext != null ? ListItem.TYPE_PAGE_BLOCK_COLLAGE : useGif ? ListItem.TYPE_PAGE_BLOCK_GIF : ListItem.TYPE_PAGE_BLOCK_MEDIA;
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (embedded != null) {
      receiver.requestFile(embeddedMiniThumbnail, embeddedPreview);
    } else if (wrapper != null) {
      wrapper.requestPreview(receiver);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    if (embedded != null) {
      receiver.requestFile(embeddedPoster);
    } else if (map != null) {
      receiver.requestFile(mapFile);
    } else if (wrapper != null) {
      wrapper.requestImage(receiver);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    if (wrapper != null) {
      wrapper.requestGif(receiver);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestFiles (ComplexReceiver receiver, boolean invalidate) {
    if (collageContext != null) {
      collageContext.requestFiles(receiver, invalidate);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void autoDownloadContent () {
    if (collageContext != null) {
      collageContext.autoDownloadContent();
    } else if (wrapper != null) {
      wrapper.getFileProgress().downloadAutomatically();
    }
  }

  @Override
  protected int computeHeight (View view, int width) {
    int height = 0;

    final int maxWidth = width - getMinimumContentPadding(false) - getMinimumContentPadding(true);
    final float maxHeightFactor = collageContext != null ? .78f : isCover || isList ? 1.2f : 1.78f;
    final int maxHeight = (int) ((float) Math.min(width * maxHeightFactor, (isCover ? Screen.widestSide() : Screen.currentHeight()) - HeaderView.getSize(true) * 2 - Screen.dp(16f) * 2));

    if (embedded != null) {
      if (isUnknownHeight()) {
        height = ((PageBlockWrapView) view).getExactWebViewHeight();
      } else {
        height += (int) ((float) embedded.height * ((float) maxWidth / (float) embedded.width));
      }
    } if (map != null) {
      height += (int) ((float) map.height * ((float) maxWidth / (float) map.width));
    } else if (collageContext != null) {
      height += collageContext.getHeight(maxWidth, maxHeight);
    } else if (wrapper != null || isList) {
      int photoWidth, photoHeight;
      if (wrapper != null) {
        photoWidth = wrapper.getContentWidth();
        photoHeight = wrapper.getContentHeight();
      } else if (isList && wrappers != null) {
        photoWidth = photoHeight = 0;
        for (MediaWrapper wrapper : wrappers) {
          final int itemWidth = wrapper.getContentWidth();
          final int itemHeight = wrapper.getContentHeight();
          if (photoWidth == 0 || photoHeight == 0 || (itemWidth != 0 && itemHeight != 0 && (itemWidth < photoWidth || itemHeight < photoHeight))) {
            photoWidth = wrapper.getContentWidth();
            photoHeight = wrapper.getContentHeight();
          }
        }
      } else {
        photoWidth = 0;
        photoHeight = 0;
      }

      final float sourceRatio = Math.min((float) maxWidth / (float) photoWidth, (float) maxHeight / (float) photoHeight);

      photoWidth *= sourceRatio;
      photoHeight *= sourceRatio;

      if (wrapper != null) {
        wrapper.buildContent(isCover ? width : photoWidth, photoHeight);
        height += wrapper.getCellHeight();
      } else if (isList) {
        height += photoHeight;
      }
    }

    if (!isCover) {
      height += getContentTop() * (ignoreBottomPadding ? 1 : 2);
    }

    return height;
  }

  @Override
  protected int getContentTop () {
    return (isCover ? 0 : Screen.dp(isIndependent() ? MEDIA_MARGIN : MEDIA_POST_MARGIN));
  }

  @Override
  protected int getContentHeight () {
    return collageContext != null ? collageContext.getCachedHeight() : wrapper != null ? wrapper.getCellHeight() : 0;
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    if (collageContext != null) {
      return collageContext.onTouchEvent(view, e, 0, getContentTop());
    } else {
      return wrapper != null && wrapper.onTouchEvent(view, e);
    }
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, ComplexReceiver iconReceiver) {
    if (embedded != null || map != null) {
      preview.setBounds(getMinimumContentPadding(true), getContentTop(), view.getMeasuredWidth() - getMinimumContentPadding(false), getComputedHeight() - (!isCover && !ignoreBottomPadding ? getContentTop() : 0));
      receiver.setBounds(preview.getLeft(), preview.getTop(), preview.getRight(), preview.getBottom());
      if (receiver.needPlaceholder()) {
        if (preview.needPlaceholder()) {
          preview.drawPlaceholder(c);
        }
        preview.draw(c);
      }
      receiver.draw(c);
    } else if (collageContext != null && view instanceof PageBlockView) {
      int maxWidth = view.getMeasuredWidth() - getMinimumContentPadding(true) - getMinimumContentPadding(false);
      int collageWidth = collageContext.getWidth();
      int x = !isIndependent() ? getMinimumContentPadding(true) : collageWidth < maxWidth ? (maxWidth - collageWidth) / 2 : 0;
      collageContext.draw(view, c, x, getContentTop(), ((PageBlockView) view).getMultipleReceiver());
    } else if (wrapper != null) {
      final int x = ((view.getMeasuredWidth() - getMinimumContentPadding(true) - getMinimumContentPadding(false)) / 2 - wrapper.getCellWidth() / 2) + getMinimumContentPadding(true);
      wrapper.draw(view, c, x, getContentTop(), preview, receiver, 1f);
      if (!StringUtils.isEmpty(url)) {
        Drawables.draw(c, linkIcon, receiver.getRight() - linkIcon.getMinimumWidth() - Screen.dp(9f), receiver.getTop() + Screen.dp(9f), Paints.getPorterDuffPaint(0xffffffff));
      }
    }
  }

  // OnClick

  private ArrayList<PageBlockMedia> boundToList;
  private String source;

  public boolean bindToList (InstantViewController context, String url, ArrayList<PageBlockMedia> blocks) {
    this.source = url;
    if (wrapper != null && !wrapper.isNativeEmbed()) {
      this.boundToList = blocks;
      return true;
    }
    return false;
  }

  @Override
  public boolean onClick (View view, MediaWrapper clickWrapper) {
    if (map != null) {
      context.tdlib().ui().openMap(context, new MapController.Args(map.location.latitude, map.location.longitude));
      return true;
    }
    if (!StringUtils.isEmpty(url)) {
      context.showOptions(Lang.getString(R.string.OpenThisLink, url), new int[] {R.id.btn_openLink, R.id.btn_copyLink, R.id.btn_open}, new String[] {Lang.getString(R.string.Open), Lang.getString(R.string.CopyLink), Lang.getString(R.string.ViewPhoto)}, null, new int[] {R.drawable.baseline_open_in_browser_24, R.drawable.baseline_content_copy_24, R.drawable.baseline_visibility_24}, (optionItemView, id) -> {
        switch (id) {
          case R.id.btn_openLink: {
            TdlibUi.UrlOpenParameters openParameters = new TdlibUi.UrlOpenParameters(this.urlOpenParameters);
            if (openParameters.tooltip == null) {
              openParameters.tooltip = UI.getContext(view.getContext()).tooltipManager().builder(view, currentViews).locate((targetView, outRect) -> outRect.set(wrapper.getCellLeft(), wrapper.getCellTop(), wrapper.getCellRight(), wrapper.getCellBottom()));
            }
            if (context instanceof InstantViewController) {
              ((InstantViewController) context).onUrlClick(view, url, false, openParameters);
            } else {
              context.tdlib().ui().openUrl(context, url, openParameters);
            }
            break;
          }
          case R.id.btn_copyLink: {
            UI.copyText(url, R.string.CopiedLink);
            break;
          }
          case R.id.btn_open: {
            openMedia(clickWrapper);
            break;
          }
        }
        return true;
      });
      return true;
    }
    return openMedia(clickWrapper);
  }

  private boolean openMedia (MediaWrapper clickWrapper) {
    MediaStack stack = new MediaStack(context.context(), context.tdlib());
    ArrayList<MediaItem> items = new ArrayList<>();
    int foundIndex = -1;

    ArrayList<MediaWrapper> wrappers;
    ArrayList<TdApi.PageBlockCaption> captions;
    boolean forceThumbs = false;

    if (isList || collageContext != null) {
      wrappers = this.wrappers;
      captions = this.captions;
      forceThumbs = true;
    } else if (boundToList != null) {
      wrappers = new ArrayList<>(boundToList.size());
      captions = new ArrayList<>(boundToList.size());
      for (PageBlockMedia media : boundToList) {
        wrappers.add(media.wrapper);
        captions.add(media.caption);
      }
    } else {
      wrappers = null;
      captions = null;
    }

    if (wrappers == null || wrappers.isEmpty()) {
      return false;
    }

    int i = 0;
    for (MediaWrapper wrapper : wrappers) {
      MediaItem parsedItem;

      // TODO properly caption
      TdApi.PageBlockCaption caption = captions.get(i);
      String text = null;
      if (caption != null) {
        if (!Td.isEmpty(caption.text) && !Td.isEmpty(caption.credit)) {
          text = TD.getText(caption.text) + "\n" + TD.getText(caption.credit);
        } else if (!Td.isEmpty(caption.text)) {
          text = TD.getText(caption.text);
        } else {
          text = TD.getText(caption.credit);
        }
      }
      TdApi.FormattedText captionText = !StringUtils.isEmpty(text) ? new TdApi.FormattedText(text, null) : null;

      if (wrapper.getPhoto() != null) {
        parsedItem = MediaItem.valueOf(context.context(), context.tdlib(), wrapper.getPhoto(), captionText);
      } else if (wrapper.getVideo() != null) {
        parsedItem = MediaItem.valueOf(context.context(), context.tdlib(), wrapper.getVideo(), captionText);
      } else if (wrapper.getAnimation() != null) {
        parsedItem = MediaItem.valueOf(context.context(), context.tdlib(), wrapper.getAnimation(), captionText);
      } else {
        parsedItem = null;
      }

      if (parsedItem != null) {
        if (wrapper == clickWrapper) {
          foundIndex = i;
        }
        items.add(parsedItem);
      }

      i++;
    }

    if (foundIndex == -1 || items.isEmpty()) {
      return false;
    }

    stack.set(foundIndex, items);

    MediaViewController.openWithStack(context, stack, source, this, forceThumbs);

    return true;
  }

  @Override
  public MediaStack collectMedias (long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter) {
    return null;
  }

  @Override
  public void modifyMediaArguments (Object cause, MediaViewController.Args args) {
    args.delegate = this;
  }

  @Override
  public MediaViewThumbLocation getTargetLocation (int indexInStack, MediaItem item) {
    MediaWrapper wrapper;
    if (isList || collageContext != null) {
      wrapper = getWrapper(indexInStack);
    } else {
      wrapper = this.wrapper;
    }
    if (wrapper == null) {
      return null;
    }

    View view = currentViews.findAnyTarget();
    if (view == null) {
      return null;
    }
    ViewGroup parent = ((ViewGroup) view.getParent());
    if (parent == null) {
      return null;
    }

    int totalY = Views.getLocationInWindow(view)[1];
    // return msg.getMediaThumbLocation(view, view.getTop() + (int) messagesView.getTranslationY(), messagesView.getBottom() - view.getBottom() + (int) messagesView.getTranslationY(), view.getTop() + HeaderView.getSize(true));
    int viewTop = view.getTop();

    int paddingTop = embedded != null || isList ? getContentTop() : 0;

    // return msg.getMediaThumbLocation(item.getSourceMessageId(), view, view.getTop(), messagesView.getBottom() - view.getBottom(), view.getTop() + HeaderView.getSize(true) + offset);

    MediaViewThumbLocation location = wrapper.getMediaThumbLocation(view, view.getTop() + paddingTop, parent.getBottom() - view.getBottom() - paddingTop, totalY + paddingTop);
    location.setRoundings(0);
    return location;
  }

  @Override
  public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) { }

  // ViewPager

  @Override
  public void applyLayoutMargins (View view, FrameLayoutFix.LayoutParams params, int viewWidth, int viewHeight) {
    params.topMargin = getContentTop();
    params.bottomMargin = !isCover && !ignoreBottomPadding ? getContentTop() : 0;
    params.leftMargin = getMinimumContentPadding(true);
    params.rightMargin = getMinimumContentPadding(false);
  }

  public int getCount () {
    return wrappers != null ? wrappers.size() : 0;
  }

  public MediaWrapper getWrapper (int position) {
    return wrappers != null && position >= 0 && position < wrappers.size() ? wrappers.get(position) : null;
  }

  private int selectedPage;

  public void setSelectedPage (int i) {
    this.selectedPage = i;
  }

  public int getSelectedPage () {
    return selectedPage;
  }
}
