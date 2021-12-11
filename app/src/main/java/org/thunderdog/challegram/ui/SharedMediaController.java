package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.MediaCollectorDelegate;
import org.thunderdog.challegram.component.attach.GridSpacingItemDecoration;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.collection.IntList;

/**
 * Date: 25/12/2016
 * Author: default
 */

public class SharedMediaController extends SharedBaseController<MediaItem> implements MediaCollectorDelegate, MediaViewDelegate, ClickHelper.Delegate, ForceTouchView.ActionListener {
  public SharedMediaController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private GridSpacingItemDecoration decoration;
  private int spanCount;

  public SharedMediaController setFilter (TdApi.SearchMessagesFilter filter) {
    this.filter = filter;
    return this;
  }

  /*@Override
  public int getIcon () {
    switch (type) {
      case TYPE_PHOTOS_AND_VIDEOS:
        return R.drawable.baseline_image_20;
      case TYPE_GIFS:
        return R.drawable.baseline_gif_20;
    }
    return 0;
  }*/

  @Override
  public CharSequence getName () {
    switch (provideSearchFilter().getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
        return Lang.getString(R.string.TabMedia);
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
        return Lang.getString(R.string.TabVideo);
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
        return Lang.getString(R.string.TabPhoto);
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return Lang.getString(R.string.TabGifs);
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
        return Lang.getString(Settings.instance().needSeparateMediaTab() ? R.string.TabVideoMessagesLong : R.string.TabVideoMessages);
    }
    return "";
  }

  @Override
  protected boolean supportsMessageContent () {
    return true;
  }

  @Override
  protected boolean needSelectableAnimation () {
    return true;
  }

  @Override
  protected int getLoadColumnCount () {
    return spanCount;
  }

  @Override
  protected void onCreateView (final Context context, final MediaRecyclerView recyclerView, final SettingsAdapter adapter) {
    recyclerView.setMeasureCallback((recyclerView1, width, height) -> {
      final int newSpanCount = calculateSpanCount(width, height);
      if (spanCount != newSpanCount) {
        spanCount = newSpanCount;
        decoration.setSpanCount(spanCount);
        recyclerView1.invalidateItemDecorations();
        ((GridLayoutManager) recyclerView1.getLayoutManager()).setSpanCount(spanCount);
      }
    });
    /*recyclerView.setDrawCallback(new MediaRecyclerView.DrawCallback() {
      @Override
      public void onDraw (Canvas c) {
        int height = calculateItemsHeight();
        c.drawRect(0, 0, recyclerView.getMeasuredWidth(), height, Paints.fillingPaint(0xaaff00ff));
      }
    });*/
    adapter.setClickHelperDelegate(this);
    addThemeInvalidateListener(recyclerView);

    GridLayoutManager.SpanSizeLookup lookup = new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int count = adapter.getItems().size();
        return position >= 0 && position < count && adapter.getItems().get(position).getViewType() == ListItem.TYPE_SMALL_MEDIA ? 1 : spanCount;
      }
    };
    lookup.setSpanIndexCacheEnabled(true);

    spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());
    decoration = new GridSpacingItemDecoration(spanCount, Screen.dp(3f), false, true, true);
    decoration.setNeedDraw(true, ListItem.TYPE_SMALL_MEDIA);
    decoration.setDrawColorId(R.id.theme_color_filling);
    decoration.setSpanSizeLookup(lookup);
    GridLayoutManager manager = new RtlGridLayoutManager(context, spanCount);
    manager.setSpanSizeLookup(lookup);
    recyclerView.addItemDecoration(decoration);
    recyclerView.setLayoutManager(manager);
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.invalidateItemDecorations();
  }

  @Override
  protected int calculateInitialLoadCount () {
    int minSide = Screen.smallestSide();
    int minWidth = minSide / 3;
    int maxItemCount = Math.max(5, Screen.widestSide() / minWidth);
    int itemWidth = Screen.widestSide() / maxItemCount;
    return Screen.calculateLoadingItems(itemWidth, 18);
  }

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 3;
    if (UI.isPortrait() || minWidth == 0) {
      return 3;
    } else if (width > height) {
      return Math.max(5, width / minWidth);
    } else {
      return width / minWidth;
    }
  }

  @Override
  protected int calculateScrollY (int position) {
    if (position == 0 || recyclerView == null) {
      return 0;
    }

    int scrollY = 0;

    List<ListItem> items = adapter.getItems();
    final int itemCount = items.size();

    for (int i = 0; i < position && i < itemCount; i++) {
      ListItem item = items.get(i);
      switch (item.getViewType()) {
        case ListItem.TYPE_SMALL_MEDIA: {
          int width = recyclerView.getMeasuredWidth();
          int maxItemWidth = width / spanCount;
          int spacing = Screen.dp(3f);

          int remainCount = spanCount - 1;
          while (remainCount > 0 && i < position && i < itemCount) {
            item = items.get(i + 1);
            if (item.getViewType() != ListItem.TYPE_SMALL_MEDIA) {
              break;
            }
            i++;
            remainCount--;
          }

          scrollY += maxItemWidth;
          if (item.getViewType() == ListItem.TYPE_SMALL_MEDIA) { // bottom line
            scrollY -= spacing;
          }

          break;
        }
        case ListItem.TYPE_SHADOW_BOTTOM: {
          scrollY += Screen.dp(3f) + SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM);
          break;
        }
        case ListItem.TYPE_SMART_EMPTY:
        case ListItem.TYPE_SMART_PROGRESS: {
          scrollY += measureBaseItemHeight(item.getViewType());
          break;
        }
        default: {
          scrollY += SettingHolder.measureHeightForType(item.getViewType());
          break;
        }
      }
    }

    return scrollY;
/*
      int i = 0;

    int skipCount = 0;
    for (SettingItem item : adapter.getItems()) {
      if (skipCount > 0) {
        if (item.getViewType() == SettingItem.TYPE_SMALL_MEDIA) {
          skipCount--;
          i++;
          if (i == position) {
            break;
          }
        } else {
          skipCount = 0;
        }
      }
      switch (item.getViewType()) {
        case SettingItem.TYPE_SMALL_MEDIA: {
          int width = recyclerView.getMeasuredWidth();
          int maxItemWidth = width / spanCount;

          skipCount = spanCount - 1;

          // int spacing = Screen.dp(3f);
          scrollY += maxItemWidth;*//*
          for (int column = 0; column < spanCount; column++) {
            int left = column * spacing / spanCount;
            int right = spacing - (column + 1) * spacing / spanCount;
            int spacingWidth = (left + right) / 2 + (left + right) % 2;
            maxItemWidth = Math.max(maxItemWidth - spacingWidth, maxItemWidth);
          }
          scrollY += maxItemWidth + spacing * 2;*//*
          break;
        }

      }

      i++;
      if (i == position) {
        break;
      }
    }

    return scrollY;*/
  }

  @Override
  protected boolean supportsMessageClearing () {
    return false;
  }

  // Data

  private TdApi.SearchMessagesFilter filter;

  @Override
  protected TdApi.SearchMessagesFilter provideSearchFilter () {
    if (filter == null)
      filter = new TdApi.SearchMessagesFilterPhotoAndVideo();
    return filter;
  }

  @Override
  protected MediaItem parseObject (TdApi.Object object) {
    TdApi.Message message = (TdApi.Message) object;
    if (message.ttl > 0 && message.ttl <= 60) {
      return null;
    }
    MediaItem item = MediaItem.valueOf(context(), tdlib, message);
    if (item != null) {
      item.setScaleType(ImageFile.CENTER_CROP);
      item.setSize(Screen.dp(124f, 3f));
      item.setNeedSquare(true);
    }
    return item;
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_SMALL_MEDIA;
  }

  private int cachedTotalCount = -1;
  private int cachedPhotosCount, cachedVideosCount;

  @Override
  protected CharSequence buildTotalCount (ArrayList<MediaItem> data) {
    switch (provideSearchFilter().getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR: {
        int photosCount, videosCount;
        if (cachedTotalCount != data.size()) {
          photosCount = 0;
          videosCount = 0;
          for (MediaItem item : data) {
            if (item.isVideo()) {
              videosCount++;
            } else {
              photosCount++;
            }
          }
          cachedPhotosCount = photosCount;
          cachedVideosCount = videosCount;
          cachedTotalCount = data.size();
        } else {
          photosCount = cachedPhotosCount;
          videosCount = cachedVideosCount;
        }
        return Lang.pluralPhotosAndVideos(photosCount, videosCount);
      }
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xGIFs, data.size());
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xVideoMessages, data.size());
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xVideos, data.size());
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
        return Lang.pluralBold(R.string.xPhotos, data.size());
    }
    throw new IllegalStateException("unsupported filter " + provideSearchFilter());
  }

  // Listeners

  @Override
  protected boolean probablyHasEmoji () {
    return false;
  }

  @Override
  protected boolean needsDefaultOnClick () {
    return false;
  }

  @Override
  protected boolean needsDefaultLongPress () {
    return false;
  }

  @Override
  public MediaStack collectMedias (long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter) {
    if (data == null || data.isEmpty()) {
      return null;
    }

    ArrayList<MediaItem> items = null;

    int foundIndex = -1;
    int i = 0;
    for (MediaItem mediaItem : data) {
      MediaItem copy = MediaItem.copyOf(mediaItem);
      if (copy == null) {
        continue;
      }
      if (items == null) {
        items = new ArrayList<>();
      }
      if (foundIndex == -1 && copy.getSourceMessageId() == fromMessageId) {
        foundIndex = i;
      }
      items.add(copy);
      i++;
    }

    if (foundIndex == -1) {
      return null;
    }

    MediaStack stack;

    stack = new MediaStack(context, tdlib);
    stack.set(foundIndex, items);

    return stack;
  }

  @Override
  public void modifyMediaArguments (Object cause, MediaViewController.Args args) {
    args.delegate = this;
  }

  private MediaViewThumbLocation location = new MediaViewThumbLocation();

  @Override
  public MediaViewThumbLocation getTargetLocation (int index, MediaItem item) {
    int i = adapter.indexOfViewByLongId(item.getSourceMessageId());
    if (i == -1) {
      return null;
    }
    View view = recyclerView.getLayoutManager().findViewByPosition(i);
    if (view != null) {
      int viewTop = view.getTop();
      int viewBottom = view.getBottom();
      int top = viewTop + recyclerView.getTop() + HeaderView.getSize(true);
      int bottom = top + view.getMeasuredHeight();
      int left = view.getLeft();
      int right = view.getRight();

      viewTop -= SettingHolder.measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW);
      int clipTop = viewTop < 0 ? -viewTop : 0;
      int clipBottom = viewBottom < 0 ? -viewBottom : 0;

      location.set(left, top, right, bottom);
      location.setClip(0, clipTop, 0, clipBottom);

      return location;
    }

    return null;
  }

  @Override
  public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) { }

  // Base touch events

  @Override
  public void onClick (View v) {
    throw new RuntimeException("Stub!");
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return true;
  }

  @Override
  public void onClickAt (View v, float x, float y) {
    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_SMALL_MEDIA) {
      if (adapter.isInSelectMode()) {
        toggleSelected(item);
        return;
      }

      MediaItem mediaItem = (MediaItem) item.getData();
      if (mediaItem.isVideo() && !mediaItem.isLoaded()) {
        mediaItem.performClick(v);
      } else if (mediaItem.getType() == MediaItem.TYPE_VIDEO_MESSAGE) {
        if (mediaItem.isLoaded()) {
          tdlib.context().player().playPauseMessage(tdlib, mediaItem.getMessage(), null);
        } else {
          mediaItem.performClick(v);
        }
      } else {
        MediaViewController.openFromMedia(this, mediaItem);
      }
    }
  }

  @Override
  public boolean ignoreHapticFeedbackSettings (float x, float y) {
    return Config.FORCE_TOUCH_ENABLED && !adapter.isInSelectMode();
  }

  @Override
  public boolean forceEnableVibration () {
    return Settings.instance().useCustomVibrations();
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return true;
  }

  @Override
  public long getLongPressDuration () {
    return adapter.isInSelectMode() ? ViewConfiguration.getLongPressTimeout() : 250l;
  }

  @Override
  public boolean onLongPressRequestedAt (View v, float x, float y) {
    if (!Config.FORCE_TOUCH_ENABLED || adapter.isInSelectMode()) {
      return super.onLongClick(v);
    }

    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_SMALL_MEDIA) {
      MediaItem mediaItem = (MediaItem) item.getData();
      TdApi.Message message = mediaItem.getMessage();
      if (message == null || message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR) {
        return super.onLongClick(v);
      }

      MediaCellView cellView = new MediaCellView(context());
      ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, v, cellView, null);

      cellView.setBoundForceTouchContext(context);

      MediaItem mediaItemCopy = MediaItem.copyOf(mediaItem, false);
      if (!mediaItemCopy.isLoaded()) {
        mediaItemCopy.download(true);
      }

      cellView.setMedia(mediaItemCopy);
      // cellView.autoplayIfNeeded();

      IntList ids = new IntList(3);
      IntList icons = new IntList(3);
      StringList strings = new StringList(3);

      if (message.canBeDeletedOnlyForSelf || message.canBeDeletedForAllUsers) {
        ids.append(R.id.btn_messageDelete);
        icons.append(R.drawable.baseline_delete_24);
        strings.append(R.string.Delete);
      }

      if (alternateParent == null || !alternateParent.inSearchMode()) {
        ids.append(R.id.btn_messageSelect);
        icons.append(R.drawable.baseline_playlist_add_check_24);
        strings.append(R.string.Select);
      }

      if (message.canBeForwarded) {
        ids.append(R.id.btn_messageShare);
        icons.append(R.drawable.baseline_forward_24);
        strings.append(R.string.Share);
      }

      ids.append(R.id.btn_showInChat);
      icons.append(R.drawable.baseline_visibility_24);
      strings.append(R.string.ShowInChat);

      context.setButtons(this, item, ids.get(), icons.get(), strings.get());

      if (tdlib.isMultiChat(chatId)) {
        TdlibSender sender = new TdlibSender(tdlib, message.chatId, message.senderId);
        String title = sender.isSelf() ? Lang.getString(R.string.FromYou) : sender.getName();
        context.setHeader(title, Lang.getRelativeTimestamp(mediaItemCopy.getSourceDate(), TimeUnit.SECONDS));
        context.setHeaderAvatar(sender.getAvatar(), new AvatarPlaceholder(ComplexHeaderView.getBaseAvatarRadiusDp(), sender.getPlaceholderMetadata(), null));
      }
      if (parent != null) {
        context.setAllowFullscreen(!parent.inSearchMode());
      } else if (alternateParent != null) {
        context.setAllowFullscreen(!alternateParent.inSearchMode());
      }

      if (this.context.openForceTouch(context)) {
        getRecyclerView().requestDisallowInterceptTouchEvent(true);
        return true;
      } else {
        cellView.performDestroy();
      }
    }

    return false;
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    getRecyclerView().requestDisallowInterceptTouchEvent(false);
    context().closeForceTouch();
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    context().processForceTouchMoveEvent(x, y, startX, startY);
  }

  @Override
  public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
    switch (actionId) {
      case R.id.btn_messageDelete: {
        tdlib.ui().showDeleteOptions(this, ((MediaItem) ((ListItem) arg).getData()).getMessage());
        break;
      }
      case R.id.btn_messageSelect: {
        toggleSelected((ListItem) arg);
        break;
      }
      case R.id.btn_messageShare: {
        ShareController c = new ShareController(this.context, tdlib);
        c.setArguments(new ShareController.Args(((MediaItem) ((ListItem) arg).getData()).getMessage()).setAllowCopyLink(true));
        c.show();
        break;
      }
      case R.id.btn_showInChat: {
        TdApi.Message message = ((MediaItem) ((ListItem) arg).getData()).getMessage();
        tdlib.ui().openChat(this, message.chatId, new TdlibUi.ChatOpenParameters().highlightMessage(message).passcodeUnlocked());
        break;
      }
    }
  }

  @Override
  public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }
}
