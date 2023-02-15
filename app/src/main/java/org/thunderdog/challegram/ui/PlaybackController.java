/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/01/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageApicFile;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class PlaybackController extends ViewController<Void> implements Menu, MoreDelegate, View.OnClickListener, TGPlayerController.TrackListChangeListener, TGPlayerController.TrackListener, TdlibFilesManager.FileListener, TGPlayerController.PlayListBuilder, TGLegacyManager.EmojiLoadListener {
  public PlaybackController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return "";
  }

  @Override
  public int getId () {
    return R.id.controller_playback;
  }

  private int preparedTrackIndex;

  private int buildList (List<ListItem> out, Tdlib tdlib, TdApi.Message currentTrack, List<TdApi.Message> trackList, long playListChatId, int playFlags) {
    final boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    int remaining = trackList.size();
    long totalDuration = 0l;
    int trackIndex = -1;
    while (--remaining >= 0) {
      TdApi.Message track = trackList.get(reverseOrder ? remaining : trackList.size() - 1 - remaining);
      InlineResult<?> result = InlineResult.valueOf(context, tdlib, track);
      if (!(result instanceof InlineResultCommon) || result.getType() != InlineResult.TYPE_AUDIO) {
        out.clear();
        return -1;
      }
      if (TGPlayerController.compareTracks(currentTrack, track)) {
        if (trackIndex != -1) {
          throw new IllegalStateException();
        }
        trackIndex = out.size() - 1;
      }
      InlineResultCommon common = (InlineResultCommon) result;
      out.add(valueOf(common));
      totalDuration += common.getTrackAudio().duration;
    }
    if (trackIndex == -1) {
      throw new IllegalStateException();
    }

    this.preparedTrackIndex = trackIndex;
    this.totalTracksDuration = totalDuration;
    this.playListChatId = playListChatId;
    setPlayFlagsImpl(playFlags);

    return trackIndex;
  }

  public int prepare () {
    List<TdApi.Message> trackList = tdlib.context().player().getTrackList();
    if (trackList == null || trackList.isEmpty()) {
      return -1;
    }
    TdApi.Message currentTrack = tdlib.context().player().getCurrentTrack();
    if (currentTrack == null) {
      return -1;
    }
    long playListChatId = tdlib.context().player().getPlayListChatId();
    int playFlags = tdlib.context().player().getPlaybackSessionFlags();
    ArrayList<ListItem> tracks = new ArrayList<>(trackList.size() + 2);
    tracks.add(new ListItem(ListItem.TYPE_ZERO_VIEW));
    int trackIndex = buildList(tracks, tdlib, currentTrack, trackList, playListChatId, playFlags);
    if (trackIndex == -1) {
      return -1;
    }
    tracks.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW, R.id.btn_info));

    this.adapter = new SettingsAdapter(this) {
      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (tdlib.context().player().isTrackListEndReached()) {
          infoView.showInfo(Lang.getCharSequence(R.string.format_tracksAndDuration, Lang.pluralBold(R.string.xAudios, getTrackCount()), Strings.buildDuration(totalTracksDuration)));
        } else {
          infoView.showProgress();
        }
      }
    };
    this.adapter.setItems(tracks, false);
    this.tracks = adapter.getItems();

    return trackIndex;
  }

  private SettingsAdapter adapter;
  private List<ListItem> tracks;
  private long totalTracksDuration;

  private PlayListRecyclerView recyclerView;
  private PlayOverlayLayout overlayLayout;
  private CoverView coverView;
  private ShadowView shadowView;

  private ImageView shuffleButton, repeatButton, nextButton, prevButton;
  private PlayPauseButton playPauseButton;

  private ImageView newButton (int id, int image, boolean isActive) {
    ImageView imageView = new ImageView(context());
    imageView.setId(id);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setImageResource(image);
    imageView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(64f), Screen.dp(48f)));
    int colorId = isActive ? R.id.theme_color_playerButtonActive : R.id.theme_color_playerButton;
    imageView.setColorFilter(Theme.getColor(colorId));
    imageView.setOnClickListener(this);
    addThemeFilterListener(imageView, colorId);
    return imageView;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (coverView != null) {
      coverView.invalidate();
    }
  }

  @Override
  protected View onCreateView (Context context) {
    if (adapter == null) {
      throw new IllegalStateException("Using PlaybackController without calling prepare()");
    }

    FrameLayoutFix frameLayout = new FrameLayoutFix(context);
    frameLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(frameLayout, R.id.theme_color_filling, this);

    recyclerView = new PlayListRecyclerView(context);
    recyclerView.initWithController(this);
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.addItemDecoration(new OffsetDecoration(this));
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.setAlpha(isTrackListLess() ? 0f : 1f);
    frameLayout.addView(recyclerView);

    final ItemTouchHelper[] helper = new ItemTouchHelper[1];
    helper[0] = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      @Override
      public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (isTrackListLess()) {
          return 0;
        }
        int position = viewHolder.getAdapterPosition();
        int headerItemCount = 1;
        if (position == -1 || position < headerItemCount || viewHolder.getItemViewType() != ListItem.TYPE_CUSTOM_INLINE) {
          return 0;
        }
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT);
      }

      @Override
      public boolean isItemViewSwipeEnabled () {
        return true;
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return true;
      }

      @Override
      public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        viewHolder.itemView.invalidate();
        target.itemView.invalidate();
      }

      /*@Override
      public float getSwipeThreshold (RecyclerView.ViewHolder viewHolder) {
        return (float) Screen.dp(RemoveHelper.SWIPE_THRESHOLD_WIDTH) / (float) viewHolder.itemView.getMeasuredWidth();
      }*/

      @Override
      public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
          super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
        if (viewHolder.itemView instanceof RemoveHelper.RemoveDelegate) {
          RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
          removeView.setRemoveDx(dX);
        }
      }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        int headerItemCount = 1;
        int trackCount = getTrackCount();
        if (fromPosition >= headerItemCount && fromPosition < headerItemCount + trackCount && toPosition >= headerItemCount && toPosition < headerItemCount + trackCount) {
          fromPosition -= headerItemCount;
          toPosition -= headerItemCount;
          if ((playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
            fromPosition = trackCount - fromPosition - 1;
            toPosition = trackCount - toPosition - 1;
          }
          moveTrack(fromPosition, toPosition, true);
          return true;
        }

        return false;
      }

      @Override
      public void onSwiped (RecyclerView.ViewHolder viewHolder, int direction) {
        if ((direction & (ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)) != 0) {
          helper[0].onChildViewDetachedFromWindow(viewHolder.itemView);
        }
        if (direction == ItemTouchHelper.LEFT) {
          RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
          removeView.onRemoveSwipe();
          InlineResultCommon result = (InlineResultCommon) ((ListItem) viewHolder.itemView.getTag()).getData();
          removeTrack(result);
        }
      }
    });
    helper[0].attachToRecyclerView(recyclerView);

    overlayLayout = new PlayOverlayLayout(context);
    overlayLayout.initWithController(this);
    overlayLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frameLayout.addView(overlayLayout);

    coverView = new CoverView(context);
    coverView.initWithController(this);
    coverView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    overlayLayout.addView(coverView);
    addThemeInvalidateListener(coverView);

    shadowView = new ShadowView(context);
    shadowView.setSimpleBottomTransparentShadow(true);
    shadowView.setAlpha(0f);
    shadowView.setLayoutParams(FrameLayoutFix.newParams(shadowView.getLayoutParams()));
    frameLayout.addView(shadowView);
    addThemeInvalidateListener(shadowView);

    shuffleButton = newButton(R.id.btn_shuffle, R.drawable.round_shuffle_24, isShuffleActive());
    nextButton = newButton(R.id.btn_next, R.drawable.round_skip_next_36, false);
    prevButton = newButton(R.id.btn_previous, R.drawable.round_skip_previous_36, false);
    repeatButton = newButton(R.id.btn_repeat, getRepeatIcon(), isRepeatActive());
    playPauseButton = new PlayPauseButton(context);
    playPauseButton.setId(R.id.btn_play);
    playPauseButton.setOnClickListener(this);
    playPauseButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(64f), Screen.dp(64f)));
    addThemeInvalidateListener(playPauseButton);

    frameLayout.addView(shuffleButton);
    frameLayout.addView(repeatButton);
    frameLayout.addView(prevButton);
    frameLayout.addView(nextButton);
    frameLayout.addView(playPauseButton);

    tdlib.context().player().addTrackListChangeListener(this, false);
    setItem((InlineResultCommon) tracks.get(preparedTrackIndex + 1).getData(), true);
    TGLegacyManager.instance().addEmojiListener(this);
    TGLegacyManager.instance().addEmojiListener(adapter);

    final boolean isPlaying = isPlaying();
    playPauseButton.setIsPlaying(isPlaying, false);
    currentItem.setIsTrackPlaying(isPlaying);

    return frameLayout;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.context().player().removeTrackListChangeListener(this);
    TGLegacyManager.instance().removeEmojiListener(this);
    TGLegacyManager.instance().removeEmojiListener(adapter);
    Views.destroyRecyclerView(recyclerView);
    coverView.setItem(null);
  }

  // Handler

  private static class PlaybackHandler extends Handler {
    private final PlaybackController context;

    public PlaybackHandler (PlaybackController context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      context.processMessage(msg);
    }
  }

  private PlaybackHandler handler;
  private PlaybackHandler getHandler () {
    if (handler == null) {
      synchronized (this) {
        if (handler == null) {
          handler = new PlaybackHandler(this);
        }
      }
    }
    return handler;
  }

  private static final int ACTION_DISPATCH_PROGRESS = 0;
  private static final int ACTION_SET_COVER_DURATIONS = 1;
  private static final int ACTION_CHECK_SCROLLING = 2;
  private static final int ACTION_ENSURE_POSITION = 3;

  private void processMessage (Message msg) {
    switch (msg.what) {
      case ACTION_DISPATCH_PROGRESS: {
        setFileProgress((TdApi.File) msg.obj);
        break;
      }
      case ACTION_SET_COVER_DURATIONS: {
        coverView.setDurationTextsDelayed();
        break;
      }
      case ACTION_CHECK_SCROLLING: {
        checkScrolling(msg.arg1 == 1);
        break;
      }
      case ACTION_ENSURE_POSITION: {
        ensureScroll(msg.arg1 == 1);
        break;
      }
    }
  }

  // track list restoration

  private boolean ignoreNextReset;

  private boolean canRestoreList;
  private long restorePlayListChatId, restorePlayListMaxMessageId, restorePlayListMinMessageId;
  private boolean restoreNewEndReached, restoreOldEndReached;
  private List<TdApi.Message> restoreRemovedMessages;

  private void savePlayListInformation (long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages) {
    if (canRestoreList) {
      throw new IllegalStateException();
    }
    this.canRestoreList = true;
    this.restorePlayListChatId = playListChatId;
    this.restorePlayListMaxMessageId = playListMaxMessageId;
    this.restorePlayListMinMessageId = playListMinMessageId;
    this.restoreNewEndReached = newEndReached;
    this.restoreOldEndReached = oldEndReached;
    if (removedMessages != null && !removedMessages.isEmpty()) {
      this.restoreRemovedMessages = new ArrayList<>(removedMessages.size());
      this.restoreRemovedMessages.addAll(removedMessages);
    } else {
      this.restoreRemovedMessages = null;
    }
  }

  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    if (!canRestoreList) {
      throw new IllegalStateException();
    }
    int trackListCount = getTrackCount();
    if (trackListCount <= 0) {
      throw new IllegalStateException();
    }
    ArrayList<TdApi.Message> trackList = new ArrayList<>(trackListCount);
    int remaining = trackListCount;
    boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    int foundIndex = -1;
    while (--remaining >= 0) {
      ListItem item = tracks.get(reverseOrder ? remaining + 1 : trackListCount - remaining);
      TdApi.Message message = ((InlineResultCommon) item.getData()).getMessage();
      if (fromMessage == message || TGPlayerController.compareTracks(fromMessage, message)) {
        if (foundIndex != -1) {
          throw new IllegalStateException();
        }
        foundIndex = trackList.size();
      }
      trackList.add(message);
    }
    ignoreNextReset = true;
    TGPlayerController.PlayList playList = new TGPlayerController.PlayList(trackList, foundIndex);
    playList.setPlayListFlags(playFlags & TGPlayerController.PLAYLIST_FLAGS_MASK);
    playList.setPlayListSource(restorePlayListChatId, restorePlayListMaxMessageId, restorePlayListMinMessageId);
    playList.setRemovedMessages(restoreRemovedMessages);
    playList.setReachedEnds(restoreNewEndReached, restoreOldEndReached);
    canRestoreList = false;
    return playList;
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return true;
  }

  // item

  private static final int MINIMUM_TRACKS_COUNT = 1;

  private int indexOfCurrentTrack () {
    if (currentItem == null) {
      return -1;
    }
    final int size = tracks.size();
    TdApi.Message currentMessage = currentItem.getMessage();
    for (int i = 1; i < size - 1; i++) {
      ListItem item = tracks.get(i);
      if (item.getData() == currentItem || TGPlayerController.compareTracks(currentMessage, ((InlineResult<?>) item.getData()).getMessage())) {
        return i - 1;
      }
    }
    return -1;
  }

  private int getTrackCount () {
    return tracks.size() - 2; // top view + bottom progress
  }

  private boolean isTrackListLess () {
    return getTrackCount() <= MINIMUM_TRACKS_COUNT;
  }

  private void onTrackListSizeChanged () {
    final int count = getTrackCount();
    recyclerView.setAlpha(count <= MINIMUM_TRACKS_COUNT ? 0f : 1f);
    adapter.updateValuedSettingById(R.id.btn_info);
  }

  private void addDuration (long duration) {
    if (duration != 0) {
      totalTracksDuration += duration;
    }
    onTrackListSizeChanged();
  }

  private void highlightCurrentItem () {
    highlightItem(indexOfCurrentTrack(), true);
  }

  private boolean isScrollUnlocked = true; // TODO ?
  // private float unlockFactor;

  private void dispatchEnsureScroll (boolean animated) {
    getHandler().sendMessage(Message.obtain(getHandler(), ACTION_ENSURE_POSITION, animated ? 1 : 0, 0));
  }

  private void unlockScroll () {
    if (!isScrollUnlocked) {
      isScrollUnlocked = true;
      /*if (scrollFactor == 0f || !mayExpandOverlay()) {
        setUnlockFactor(1f);
        return;
      }
      FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          setUnlockFactor(factor);
        }
        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }
      }, Anim.DECELERATE_INTERPOLATOR, 180l);
      animator.animateTo(1f);*/
    }
  }

  private void ensureScroll (boolean animated) {
    if (isScrollUnlocked) {
      return;
    }
    final int viewWidth = recyclerView.getMeasuredWidth();
    final int viewHeight = recyclerView.getMeasuredHeight();
    if (Math.min(viewWidth, viewHeight) <= 0) {
      return;
    }

    int position = indexOfCurrentTrack();
    if (position == -1) {
      throw new IllegalStateException();
    }

    final int overlayHeight = getAvailableOverlayHeight();
    final int parentHeight = recyclerView.getMeasuredHeight();
    final int itemHeight = Screen.dp(65f);
    final int totalItemCount = getTrackCount();
    final int minimumOverlayHeight = getMinimumOverlayHeight();

    final int headerItemCount = 1;

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

    int desiredPosition = headerItemCount + position;
    int desiredOffset = (position != 0 ? itemHeight : 0);

    int scrollY = itemHeight * position;
    int maxScrollY = itemHeight * totalItemCount - (parentHeight - overlayHeight - itemHeight);
    if (scrollY > maxScrollY) {
      desiredOffset += scrollY - maxScrollY; // maxScrollY - scrollY;
    }

    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    View firstView = firstVisiblePosition != -1 ? manager.findViewByPosition(firstVisiblePosition) : null;
    int offsetTop = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    if (!animated || firstView == null) {
      manager.scrollToPositionWithOffset(desiredPosition, overlayHeight + desiredOffset);
      return;
    }

    int currentScrollY = -offsetTop + itemHeight * Math.max(0, firstVisiblePosition - 1) + (firstVisiblePosition > 0 ? overlayHeight : 0);
    int desiredScrollY = itemHeight * Math.max(0, desiredPosition - 1) - desiredOffset;

    recyclerView.smoothScrollBy(0,  desiredScrollY - currentScrollY);
  }

  /*private void setUnlockFactor (float factor) {
    if (this.unlockFactor != factor) {
      float oldScrollFactor = getScrollFactor();
      this.unlockFactor = factor;
      float newScrollFactor = getScrollFactor();
      onScrollFactorChanged(oldScrollFactor, newScrollFactor);
    }
  }*/

  private void highlightItem (int position, boolean highlight) {
    if (position == -1) {
      return;
    }

    final int overlayHeight = getAvailableOverlayHeight();
    final int parentHeight = recyclerView.getMeasuredHeight();
    final int itemHeight = Screen.dp(65f);
    final int totalItemCount = getTrackCount();
    final int minimumOverlayHeight = getMinimumOverlayHeight();

    final int maxScrollY = overlayHeight + itemHeight * totalItemCount - parentHeight + SettingHolder.measureHeightForType(ListItem.TYPE_LIST_INFO_VIEW);

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == -1) {
      return;
    }

    recyclerView.stopScroll();

    View firstView = manager.findViewByPosition(firstVisiblePosition);
    int offset = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    int currentScrollY;
    if (firstVisiblePosition == 0) {
      currentScrollY = -offset;
    } else {
      currentScrollY = -offset + overlayHeight + (firstVisiblePosition - 1) * itemHeight;
    }
    int desiredScrollY = Math.max(0, Math.min(maxScrollY, overlayHeight + position * itemHeight - minimumOverlayHeight - (parentHeight - minimumOverlayHeight) / 2 + itemHeight / 2));

    if (highlight) {
      ((InlineResult<?>) tracks.get(position + 1).getData()).highlight();
    }

    recyclerView.smoothScrollBy(0, desiredScrollY - currentScrollY);
  }

  private InlineResultCommon currentItem;

  private void setItem (InlineResultCommon item, boolean setCurrent) {
    if (this.currentItem == item) {
      if (item != null && setCurrent) {
        item.setIsTrackCurrent(true);
        item.setIsTrackPlaying(true);
      }
      return;
    }
    boolean hadItem = currentItem != null;
    if (hadItem) {
      tdlib.context().player().removeTrackListener(tdlib, currentItem.getMessage(), this);
      tdlib.files().unsubscribe(currentItem.getTrackFile().id, this);
      if (setCurrent) {
        currentItem.setIsTrackCurrent(false);
      }
    }
    this.currentItem = item;
    coverView.setItem(item);
    float prefixProgress = 0f;
    float progress = 0f;
    if (item != null) {
      if (setCurrent) {
        item.setIsTrackCurrent(true);
      }
      tdlib.context().player().addTrackListener(tdlib, item.getMessage(), this);
      tdlib.files().subscribe(item.getTrackFile(), this);
      prefixProgress = TD.getFileOffsetProgress(item.getTrackFile());
      progress = TD.getFilePrefixProgress(item.getTrackFile());
    }
    coverView.setFileProgress(prefixProgress, progress, hadItem);
    ensureScroll(hadItem);
  }

  // Track listener

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    if ((state == TGPlayerController.STATE_PAUSED || state == TGPlayerController.STATE_PLAYING) && currentItem != null && TGPlayerController.compareTracks(currentItem.getMessage(), chatId, messageId, fileId)) {
      boolean isPlaying = state == TGPlayerController.STATE_PLAYING;
      playPauseButton.setIsPlaying(isPlaying, true);
      if (currentItem != null) {
        currentItem.setIsTrackPlaying(isPlaying);
      }
    }
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (currentItem != null && TGPlayerController.compareTracks(currentItem.getMessage(), chatId, messageId, fileId)) {
      coverView.setDurations(playPosition, playDuration);
    }
  }

  // Current file progress listener (used for seek)

  private void setFileProgress (TdApi.File file) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      Handler handler = getHandler();
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_PROGRESS, file));
      return;
    }
    if (currentItem != null && file.id == currentItem.getTrackFile().id) {
      coverView.setFileProgress(TD.getFileOffsetProgress(file), TD.getFilePrefixProgress(file), true);
    }
  }

  @Override
  public void onFileLoadProgress (TdApi.File file) {
    setFileProgress(file);
  }

  @Override
  public void onFileLoadStateChanged (Tdlib tdlib, int fileId, int state, @Nullable TdApi.File downloadedFile) {
    if (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED && downloadedFile != null) {
      setFileProgress(downloadedFile);
    }
  }

  // Track list

  private static ListItem valueOf (InlineResultCommon common) {
    common.setIsTrack(true);
    return new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_custom).setData(common);
  }

  private int playFlags;
  private long playListChatId;

  @Override
  public void onTrackListReset (Tdlib tdlib, @NonNull TdApi.Message currentTrack, int trackIndex, List<TdApi.Message> trackList, long playListChatId, int playFlags, int playState) {
    if (ignoreNextReset) {
      ignoreNextReset = false;
      setItemByPosition(trackIndex, true);
      return;
    }

    ListItem headerItem = tracks.get(0);
    ListItem footerItem = tracks.get(tracks.size() - 1);
    int prevSize = tracks.size() - 2;

    tracks.clear();
    ArrayUtils.ensureCapacity(tracks, trackList.size() + 1);

    tracks.add(headerItem);
    int foundIndex = buildList(tracks, tdlib, currentTrack, trackList, playListChatId, playFlags);
    if (foundIndex == -1) {
      throw new IllegalStateException();
    }
    tracks.add(footerItem);

    int newSize = tracks.size() - 2;
    adapter.notifyItemRangeChanged(1, Math.min(newSize, prevSize));
    if (newSize > prevSize) {
      adapter.notifyItemRangeInserted(1 + prevSize, newSize - prevSize);
    } else if (newSize < prevSize) {
      adapter.notifyItemRangeRemoved(1 + prevSize, prevSize - newSize);
    }

    setItem((InlineResultCommon) tracks.get(preparedTrackIndex + 1).getData(), true);
    onTrackListSizeChanged();
  }

  private void setActive (ImageView imageView, boolean isActive) {
    removeThemeListenerByTarget(imageView);
    int colorId = isActive ? R.id.theme_color_playerButtonActive : R.id.theme_color_playerButton;
    imageView.setColorFilter(Theme.getColor(colorId));
    addThemeFilterListener(imageView, colorId);
  }

  @Override
  public void onTrackListFlagsChanged (int newPlayFlags) {
    boolean wasShuffling = (playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
    boolean nowShuffling = (newPlayFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
    if (wasShuffling != nowShuffling) {
      setActive(shuffleButton, nowShuffling);
    }

    int prevRepeatMode = TGPlayerController.getPlayRepeatFlag(playFlags);
    int newRepeatMode = TGPlayerController.getPlayRepeatFlag(newPlayFlags);
    if (prevRepeatMode == 0 == (newRepeatMode != 0)) {
      setActive(repeatButton, newRepeatMode != 0);
    }
    if (prevRepeatMode == TGPlayerController.PLAY_FLAG_REPEAT_ONE == (newRepeatMode != TGPlayerController.PLAY_FLAG_REPEAT_ONE)) {
      repeatButton.setImageResource(newRepeatMode == TGPlayerController.PLAY_FLAG_REPEAT_ONE ? R.drawable.round_repeat_one_24 : R.drawable.round_repeat_24);
    }

    boolean wasReverse = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    boolean isReverse = (newPlayFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;

    if (wasReverse != isReverse) {
      int totalCount = getTrackCount();
      int currentIndex = indexOfCurrentTrack();
      if (currentIndex == -1) {
        throw new IllegalStateException();
      }

      int headerItemCount = 1;
      /*for (int i = currentIndex - 1; i >= 0; i--) {
        adapter.moveItem(i + headerItemCount, i + (currentIndex - i) + headerItemCount);
      }
      for (int i = currentIndex + 1; i < totalCount; i++) {
        adapter.moveItem(i + headerItemCount, headerItemCount);
      }*/

      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

      boolean notify;
      boolean keepScrollPosition = false;
      int keepScrollPositionOffset = 0;

      if (getScrollFactor() < .8f) {
        notify = false;
      } else {
        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        int lastVisiblePosition = manager.findLastVisibleItemPosition();
        if (firstVisiblePosition == -1 || lastVisiblePosition == -1) {
          notify = false;
        } else {
          if (currentIndex + headerItemCount >= firstVisiblePosition && currentIndex + headerItemCount <= lastVisiblePosition) {
            keepScrollPosition = true;
            View view = manager.findViewByPosition(currentIndex + headerItemCount);
            if (view != null) {
              keepScrollPositionOffset = manager.getDecoratedTop(view);
            }
            int itemCountAfter = lastVisiblePosition - currentIndex + headerItemCount;
            int itemCountBefore = currentIndex + headerItemCount - firstVisiblePosition;
            notify = false; // Math.abs(itemCountBefore - itemCountAfter) < 4;
          } else {
            notify = false;
          }
        }
      }

      for (int i = currentIndex + 1; i < totalCount; i++) {
        adapter.moveItem(i + headerItemCount, headerItemCount, notify);
      }
      for (int i = currentIndex - 1; i >= 0; i--) {
        int offset = (totalCount - currentIndex - 1);
        adapter.moveItem(i + headerItemCount + offset, i + (currentIndex - i) + headerItemCount + offset, notify);
      }
      if (!notify) {
        adapter.notifyItemRangeChanged(headerItemCount, headerItemCount + totalCount);
      }
      if (keepScrollPosition) {
        int newIndex = totalCount - currentIndex - 1;
        manager.scrollToPositionWithOffset(newIndex + headerItemCount, keepScrollPositionOffset);
      }
    }

    setPlayFlagsImpl(newPlayFlags);
  }

  @Override
  public void onTrackListLoadStateChanged () {
    adapter.updateValuedSettingById(R.id.btn_info);
  }

  private void setPlayFlagsImpl (int playFlags) {
    if (this.playFlags != playFlags) {
      this.playFlags = playFlags;
      // TODO?
    }
  }

  private int reversePosition (int position, int totalCount, boolean reverse) {
    return reverse ? totalCount - 1 - position : position;
  }

  private int reversePosition (int position) {
    return reversePosition(position, getTrackCount(), (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0);
  }

  private void setItemByPosition (int trackPosition, boolean setCurrent) {
    setItem((InlineResultCommon) this.tracks.get(reversePosition(trackPosition) + 1).getData(), setCurrent);
  }

  @Override
  public void onTrackListPositionChange (Tdlib tdlib, TdApi.Message newTrack, int newIndex, List<TdApi.Message> trackList, boolean byUserRequest, int playState) {
    setItemByPosition(newIndex, true);
    currentItem.setIsTrackCurrent(true);
  }

  @Override
  public void onTrackListClose (Tdlib tdlib, long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages) {
    playPauseButton.setIsPlaying(false, true);
    if (currentItem != null) {
      currentItem.setIsTrackCurrent(false);
      setItem((InlineResultCommon) this.tracks.get(1).getData(), false);
      coverView.setDurations(-1, -1);
    }
    savePlayListInformation(playListChatId, playListMaxMessageId, playListMinMessageId, newEndReached, oldEndReached, removedMessages);
  }

  private void removeTrack (final InlineResultCommon common) {
    if (currentItem != null) {
      showOptions(Lang.getStringBold(R.string.PlayListRemoveTrack, common.getTrackTitle() + " – " + common.getTrackSubtitle()), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.PlayListRemove), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        switch (id) {
          case R.id.btn_delete: {
            tdlib.context().player().removeTrack(common.getMessage(), true);
            break;
          }
        }
        return true;
      });
    }
  }

  @Override
  public void onTrackListItemRemoved (Tdlib tdlib, TdApi.Message track, int position, boolean isCurrent) {
    if ((playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
      int count = getTrackCount();
      position = count - position - 1;
    }
    int headerItemCount = 1;
    adapter.removeItem(position + headerItemCount);
    addDuration(-((TdApi.MessageAudio) track.content).audio.duration);
  }

  private void moveTrack (int fromPosition, int toPosition, boolean dispatchUpdate) {
    if (fromPosition == toPosition) {
      return;
    }
    if (dispatchUpdate) {
      ignoreMoveUpdate = true;
      tdlib.context().player().moveTrack(fromPosition, toPosition);
      ignoreMoveUpdate = false;
    }
    if ((playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
      int count = getTrackCount();
      fromPosition = count - fromPosition - 1;
      toPosition = count - toPosition - 1;
    }
    int headerItemCount = 1;
    adapter.moveItem(fromPosition + headerItemCount, toPosition + headerItemCount);
  }

  private boolean ignoreMoveUpdate;

  @Override
  public void onTrackListItemMoved (Tdlib tdlib, TdApi.Message track, int fromPosition, int toPosition) {
    if (!ignoreMoveUpdate) {
      moveTrack(fromPosition, toPosition, false);
    }
  }

  @Override
  public void onTrackListItemAdded (Tdlib tdlib, TdApi.Message newTrack, int position) {
    InlineResult<?> result = InlineResult.valueOf(context, tdlib, newTrack);
    if (!(result instanceof InlineResultCommon) || result.getType() != InlineResult.TYPE_AUDIO) {
      return;
    }
    InlineResultCommon common = (InlineResultCommon) result;
    ListItem item = valueOf(common);
    int tracksCount = getTrackCount();
    int i = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0 ? 1 + tracksCount - position : 1 + position;
    adapter.addItem(i, item);

    addDuration(common.getTrackAudio().duration);
  }

  @Override
  public void onTrackListItemRangeAdded (Tdlib tdlib, List<TdApi.Message> addedItems, boolean areNew) {
    ArrayList<ListItem> newItems = new ArrayList<>(addedItems.size());

    long addedDuration = 0;
    int remaining = addedItems.size();
    boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    while (--remaining >= 0) {
      TdApi.Message addedTrack = addedItems.get(reverseOrder ? remaining : addedItems.size() - 1 - remaining);
      InlineResult<?> result = InlineResult.valueOf(context, tdlib, addedTrack);
      if (!(result instanceof InlineResultCommon) || result.getType() != InlineResult.TYPE_AUDIO) {
        return;
      }
      InlineResultCommon common = (InlineResultCommon) result;
      newItems.add(valueOf(common));
      addedDuration += common.getTrackAudio().duration;
    }
    if (newItems.isEmpty()) {
      return;
    }

    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int headerItemCount = 1;
    int position = firstVisiblePosition != -1 ? Math.max(headerItemCount, firstVisiblePosition) : -1;
    View firstView = manager.getChildAt(position);
    int offset = firstView != null ? manager.getDecoratedTop(firstView) : 0;

    boolean addOnBottom = reverseOrder != areNew;
    int i = addOnBottom ? tracks.size() - 1 : 1;
    tracks.addAll(i, newItems);
    adapter.notifyItemRangeInserted(i, newItems.size());

    if (!addOnBottom && position != -1) {
      manager.scrollToPositionWithOffset(position + addedItems.size(), offset);
    }

    addDuration(addedDuration);
  }

  // State

  private boolean isShuffleActive () {
    return (playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
  }

  private boolean isRepeatActive () {
    return (playFlags & (TGPlayerController.PLAY_FLAG_REPEAT_ONE | TGPlayerController.PLAY_FLAG_REPEAT)) != 0;
  }

  private boolean isPlaying () {
    return currentItem != null && tdlib.context().player().getPlayState(tdlib, currentItem.getMessage()) == TGPlayerController.STATE_PLAYING;
  }

  private int getRepeatIcon () {
    return (TGPlayerController.getPlayRepeatFlag(playFlags) == TGPlayerController.PLAY_FLAG_REPEAT_ONE) ? R.drawable.round_repeat_one_24 : R.drawable.round_repeat_24;
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (y < getMinimumOverlayHeight() + HeaderView.getTopOffset()) {
      return true;
    }
    if (scrollFactor == 0f) {
      return true;
    }
    return !isScrollUnlocked && y < getCurrentOverlayHeight();
  }

  // Events

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_custom: {
        ListItem item = (ListItem) v.getTag();
        InlineResultCommon resultCommon = (InlineResultCommon) item.getData();
        tdlib.context().player().playPauseMessage(tdlib, resultCommon.getMessage(), this);
        break;
      }
      case R.id.btn_play: {
        if (currentItem != null) {
          tdlib.context().player().playPauseMessage(tdlib, currentItem.getMessage(), this);
        }
        break;
      }
      case R.id.btn_next: {
        tdlib.context().player().skip(true);
        break;
      }
      case R.id.btn_previous: {
        tdlib.context().player().skip(false);
        break;
      }
      case R.id.btn_repeat: {
        tdlib.context().player().toggleRepeatMode();
        break;
      }
      case R.id.btn_shuffle: {
        tdlib.context().player().togglePlaybackFlag(TGPlayerController.PLAY_FLAG_SHUFFLE);
        break;
      }
    }
  }

  // Measurements

  private float scrollFactor;

  private void dispatchCheckScrolling (boolean byLayout) {
    getHandler().sendMessage(Message.obtain(getHandler(), ACTION_CHECK_SCROLLING, byLayout ? 1 : 0, 0));
  }

  private int scrollPivotY;

  private void checkScrolling (boolean byLayout) {
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    final int lastVisiblePosition = manager.findLastVisibleItemPosition();
    if (lastVisiblePosition != RecyclerView.NO_POSITION && lastVisiblePosition + TGPlayerController.SCROLL_LOAD_THRESHOLD >= getTrackCount()) {
      tdlib.context().player().requestMoreTracks();
    }
    final int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == RecyclerView.NO_POSITION) {
      return;
    }

    if (firstVisiblePosition > 0) {
      setScrollFactor(1f, 1f);
      if (isScrollUnlocked) {
        coverView.setCollapsed(true, !byLayout);
      }
      return;
    }
    View view = manager.findViewByPosition(firstVisiblePosition);
    if (view == null) {
      return;
    }

    int viewTop = view.getTop();
    int decorationHeight = manager.getTopDecorationHeight(view);
    int realDecorationHeight = getAvailableOverlayHeight();
    if (decorationHeight != realDecorationHeight && viewTop > 0 && decorationHeight != 0) {
      viewTop *= (float) realDecorationHeight / (float) decorationHeight;
    }

    int minimumHeight = getMinimumOverlayHeight() + HeaderView.getTopOffset();
    viewTop -= minimumHeight;
    if (viewTop < 0) {
      setScrollFactor(1f, MathUtils.clamp(-viewTop / (float) Screen.dp(12f)));
      if (isScrollUnlocked) {
        coverView.setCollapsed(true, !byLayout);
      }
      return;
    }

    int totalHeight = getAvailableOverlayHeight();
    int distance = totalHeight - minimumHeight;
    float factor = viewTop > distance || distance == 0 ? 0f : 1f - (float) viewTop / (float) distance;
    setScrollFactor(MathUtils.clamp(factor), 0f);

    if (mayExpandOverlay()) {
      int threshold = HeaderView.getTopOffset() + HeaderView.getSize(false) / 2;
      if (isScrollUnlocked) {
        coverView.setCollapsed(viewTop <= threshold, !byLayout);
      }
    }

    /*View view = manager.findViewByPosition(firstVisiblePosition);
    int topOffset = view != null ? manager.getDecoratedTop(view) : 0;

    final int overlayHeight = getAvailableOverlayHeight();
    final int itemHeight = Screen.dp(65f);
    final int factorDistance = overlayHeight - getMinimumOverlayHeight() - HeaderView.getTopOffset();

    int totalScrollY = -topOffset;
    if (firstVisiblePosition > 0) {
      totalScrollY += overlayHeight + (firstVisiblePosition - 1) * itemHeight;
    }

    if (!isScrollUnlocked) {
      scrollPivotY = totalScrollY;
      return;
    }

    if (!mayExpandOverlay()) {
      setScrollFactor(scrollFactor, 1f);
      coverView.setCollapsed(true, false);
      return;
    }

    float scrollFactor;

    if (totalScrollY < scrollPivotY) {
      scrollPivotY = totalScrollY;
      scrollFactor = 0f;
    } else {
      scrollFactor = (float) Math.min(factorDistance, totalScrollY - scrollPivotY) / (float) factorDistance;
      if (scrollFactor == 1f) {
        scrollPivotY = 0;
      }
    }

    setScrollFactor(scrollFactor, scrollFactor);
    if (isScrollUnlocked) {
      coverView.setCollapsed((1f - scrollFactor) * factorDistance <= HeaderView.getSize(false) * .9f, !byLayout);
    } else if (scrollFactor < 1f && !coverView.isCollapsed && byLayout) {
      coverView.setCollapsed(false, false);
    }*/
  }

  private float shadowFactor;

  private void setScrollFactor (float scrollFactor, float shadowFactor) {
    if (this.scrollFactor != scrollFactor) {
      float oldScrollFactor = getScrollFactor();
      this.scrollFactor = scrollFactor;
      float newScrollFactor = getScrollFactor();
      onScrollFactorChanged(oldScrollFactor, newScrollFactor);
    }
    if (this.shadowFactor != shadowFactor) {
      this.shadowFactor = shadowFactor;
      if (!mayExpandOverlay()) {
        shadowView.setAlpha(shadowFactor);
      }
    }
  }

  private void onScrollFactorChanged (float oldScrollFactor, float newScrollFactor) {
    if (mayExpandOverlay(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight())) {
      if (oldScrollFactor != newScrollFactor && ((oldScrollFactor >= 0f && oldScrollFactor <= 1f) || (newScrollFactor >= 0f && newScrollFactor <= 1f))) {
        coverView.invalidate();
      }
    }
  }

  private int getAvailableOverlayHeight () {
    return getAvailableOverlayHeight(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
  }
  private int getCurrentOverlayHeight () {
    return getCurrentOverlayHeight(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight(), getScrollFactor());
  }
  private float getScrollFactor () {
    return MathUtils.clamp(scrollFactor);
  }
  /*private float getTotalCoverExpandFactor () {
    float desiredCoverFactor = 1f - coverView.collapseFactor;
    float requiredCoverFactor = mayExpandOverlay() ? 1f : 0f;
    return requiredCoverFactor + (desiredCoverFactor - requiredCoverFactor) * unlockFactor;
  }*/
  private boolean mayExpandOverlay () {
    return mayExpandOverlay(recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
  }

  private static int getMinimumOverlayHeight () {
    return Screen.dp(112f) + HeaderView.getSize(false);
  }

  private int getAvailableOverlayHeight (int totalWidth, int totalHeight) {
    int height = getMinimumOverlayHeight();
    float ratio = totalHeight != 0 ? (float) totalWidth / (float) totalHeight : 0f;
    if (ratio <= .7f) {
      int offset = HeaderView.getTopOffset();
      // int desiredHeight = offset + (totalHeight - offset) / 2;
      int desiredHeight = totalWidth;
      if (desiredHeight + height > height / 2) {
        return desiredHeight + height;
      }
    }
    return height + HeaderView.getTopOffset();
  }

  private boolean mayExpandOverlay (int totalWidth, int totalHeight) {
    int height = getMinimumOverlayHeight();
    float ratio = totalHeight != 0 ? (float) totalWidth / (float) totalHeight : 0f;
    if (ratio <= .7f) {
      int offset = HeaderView.getTopOffset();
      int desiredHeight = offset + (totalHeight - offset) / 2;
      return desiredHeight + height > height / 2;
    }
    return false;
  }

  private float getCoverExpandFactor (int totalWidth, int totalHeight, float scrollFactor) {
    if (scrollFactor >= 1f || !mayExpandOverlay(totalWidth, totalHeight)) {
      return 0f;
    } else {
      return (1f - scrollFactor) * (1f - coverView.collapseFactor);
    }
  }

  private int getCurrentOverlayHeight (int totalWidth, int totalHeight, float scrollFactor) {
    int availableHeight = getAvailableOverlayHeight(totalWidth, totalHeight);
    if (mayExpandOverlay(totalWidth, totalHeight)) {
      float expandFactor = getCoverExpandFactor(totalWidth, totalHeight, scrollFactor);
      int minHeight = getMinimumOverlayHeight() + HeaderView.getTopOffset();
      return minHeight + (int) ((float) (availableHeight - minHeight) * expandFactor);
    }
    return availableHeight;
  }

  // Custom views

  private static class OffsetDecoration extends RecyclerView.ItemDecoration {
    private final PlaybackController context;

    public OffsetDecoration (PlaybackController context) {
      this.context = context;
    }

    @Override
    public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
      RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
      ListItem item = (ListItem) view.getTag();
      if ((holder != null && holder.getAdapterPosition() == 0) || (item != null && item.getViewType() == ListItem.TYPE_ZERO_VIEW)) {
        outRect.top = context.getAvailableOverlayHeight(parent.getMeasuredWidth(), parent.getMeasuredHeight());
      } else {
        outRect.top = 0;
      }
    }
  }

  private static class PlayPauseButton extends View implements FactorAnimator.Target {
    private final Path path;
    private float drawFactor = -1f;
    private BoolAnimator animator;

    public PlayPauseButton (Context context) {
      super(context);

      this.path = new Path();
      this.animator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160l);
    }

    public void setIsPlaying (boolean isPlaying, boolean animated) {
      animator.setValue(isPlaying, animated);
    }

    private float factor;

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    protected void onDraw (Canvas c) {
      int cx = getMeasuredWidth() / 2;
      int cy = getMeasuredHeight() / 2;

      DrawAlgorithms.drawPlayPause(c, cx, cy, Screen.dp(18f), path, drawFactor, drawFactor = factor, 1f, Theme.getColor(R.id.theme_color_playerButton));
    }
  }

  private static class CoverView extends SparseDrawableView implements FactorAnimator.Target, InvalidateContentProvider {
    private final ComplexReceiver preview;
    private final ImageReceiver receiver, source;
    private PlaybackController controller;
    private boolean disableAnimations;

    private Paint topShadowPaint, radialShadowPaint;
    private ProgressComponent progress;

    private final Drawable icon;

    public void initWithController (PlaybackController controller) {
      this.controller = controller;
    }

    public void setDisableAnimation (boolean disableAnimation) {
      this.disableAnimations = disableAnimation;
      preview.setAnimationDisabled(disableAnimation);
      receiver.setAnimationDisabled(disableAnimation);
      source.setAnimationDisabled(disableAnimation);
    }

    private static int getRadialGradientRadius () {
      return Screen.dp(28f);
    }

    public CoverView (Context context) {
      super(context);

      icon = Drawables.get(getResources(), R.drawable.baseline_music_note_48);

      this.progress = new ProgressComponent(UI.getContext(context), Screen.dp(4f));
      this.progress.forceColor(0x00ffffff);
      this.progress.attachToView(this);

      this.topShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] topShadowColors = ShadowView.topShadowColors();
      topShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleTopShadowHeight(), topShadowColors, null, Shader.TileMode.CLAMP));

      radialShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int radialRadius = getRadialGradientRadius();
      radialShadowPaint.setShader(new RadialGradient(radialRadius, radialRadius, radialRadius, 0x10000000, 0x00000000, Shader.TileMode.CLAMP));

      this.preview = new ComplexReceiver(this);
      this.receiver = new ImageReceiver(this, 0);
      this.source = new ImageReceiver(this, 0);
      setDisableAnimation(true);

      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          int height = controller.getCurrentOverlayHeight();
          c.drawRect(0, 0, getBounds().right, height, Paints.fillingPaint(Theme.fillingColor()));
          if (controller.shadowView.getTranslationY() != height) {
            controller.shadowView.setTranslationY(height);
          }
          if (controller.mayExpandOverlay()) {
            controller.shadowView.setAlpha(getActualCollapseFactor());
          }
        }

        @Override
        public void setAlpha (int alpha) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
    }

    // Events

    private boolean isSeeking;
    private BoolAnimator seekAnimator;
    private float seekDesireFactor;
    private static final int ANIMATOR_SEEK = 3;
    private static final int ANIMATOR_SEEK_DISPLAY = 4;

    private float calculatePositionProgress () {
      return durationMillis <= 0 || positionMillis <= 0 ? 0 : MathUtils.clamp((float) ((double) positionMillis / (double) durationMillis));
    }

    private float seekDisplayProgress;
    private FactorAnimator seekDisplayAnimator;

    private void setSeekDisplayProgress (float factor, boolean animated) {
      if (animated) {
        if (seekDisplayAnimator == null) {
          seekDisplayAnimator = new FactorAnimator(ANIMATOR_SEEK_DISPLAY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 90l, this.seekDisplayProgress);
        }
        seekDisplayAnimator.animateTo(factor);
      } else {
        if (seekDisplayAnimator != null) {
          seekDisplayAnimator.forceFactor(factor);
        }
        setSeekDisplayProgress(factor);
      }
    }

    private void setSeekDisplayProgress (float progress) {
      if (this.seekDisplayProgress != progress) {
        this.seekDisplayProgress = progress;
        invalidate();
      }
    }

    private void setSeekDesireFactor (float factor) {
      if (this.seekDesireFactor != factor) {
        this.seekDesireFactor = factor;
        invalidate();
      }
    }

    private float desiredSeekPosition;

    private void setDesiredSeekPosition (float position) {
      if (this.desiredSeekPosition != position) {
        this.desiredSeekPosition = position;
        if (isSeeking) {
          setTextsDelayed();
        }
        if (seekDesireFactor > 0f) {
          invalidate();
        }
      }
    }

    private boolean textsSetDelayed;

    private void setTextsDelayed () {
      if (!textsSetDelayed) {
        textsSetDelayed = true;
        controller.getHandler().sendMessageDelayed(Message.obtain(controller.getHandler(), ACTION_SET_COVER_DURATIONS), 38l);
      }
    }

    private void setDurationTextsDelayed () {
      if (setDurationTexts()) {
        invalidate();
      }
      textsSetDelayed = false;
    }

    private float getSeekFactor () {
      float seekDoneProgress = calculatePositionProgress();
      if (seekDesireFactor == 0f) {
        return seekDoneProgress;
      }
      return seekDoneProgress + (desiredSeekPosition - seekDoneProgress) * seekDesireFactor;
    }

    private long calculateDesiredSeekPosition () {
      return (long) (Math.max(0, Math.min(durationMillis, (double) durationMillis * (double) desiredSeekPosition)));
    }

    private void setSeeking (boolean isSeeking, boolean apply) {
      if (this.isSeeking != isSeeking) {
        this.isSeeking = isSeeking;
        if (seekAnimator == null) {
          seekAnimator = new BoolAnimator(ANIMATOR_SEEK, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, !isSeeking);
        }
        getParent().requestDisallowInterceptTouchEvent(isSeeking);
        if (!isSeeking && apply) {
          long position = calculateDesiredSeekPosition();
          controller.tdlib().context().player().seekTrack(item.getMessage(), position);
          setDurations(position, durationMillis);
        }
        seekAnimator.setValue(isSeeking, true);
        if (setDurationTexts()) {
          invalidate();
        }
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      final int viewWidth = getMeasuredWidth();

      // Values copy-paste from drawing
      final int overlayHeight = controller.getCurrentOverlayHeight();
      final int buttonsY = overlayHeight - Screen.dp(34f);
      final int durationsY = buttonsY - Screen.dp(30f);
      final int workingDistance = Math.min(viewWidth, Screen.dp(480f));
      final int startX = viewWidth != workingDistance ? viewWidth / 2 - workingDistance / 2 : 0;

      final int seekMargin = Screen.dp(12f);
      final int seekStartX = startX + seekMargin;
      final int seekDistance = workingDistance - seekMargin * 2;
      final int seekY = durationsY - Screen.dp(22f);

      final float x = e.getX();
      final float y = e.getY();

      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          int height = controller.getCurrentOverlayHeight();

          boolean isSeeking = y < height && durationMillis > 0;
          if (isSeeking) {
            float currentSeekX = seekStartX + seekDistance * getSeekFactor();
            isSeeking = U.isInsideAreaOf(currentSeekX, seekY, x, y, Screen.dp(22f));
          }

          if (isSeeking) {
            isSeeking = controller.tdlib().context().player().canSeekTrack(item.getMessage());
          }

          if (isSeeking) {
            setDesiredSeekPosition(MathUtils.clamp((x - seekStartX) / (float) seekDistance));
          }

          setSeeking(isSeeking, false);
          return isSeeking || (y < height && (isCollapsed || controller.recyclerView.findChildViewUnder(x, y) != null));
        }
        case MotionEvent.ACTION_MOVE: {
          if (isSeeking) {
            setDesiredSeekPosition(controller.tdlib().context().player().normalizeSeekPosition(durationMillis, MathUtils.clamp(((x - seekStartX) / (float) seekDistance))));
          }
          return isSeeking;
        }
        case MotionEvent.ACTION_UP: {
          if (isSeeking) {
            setSeeking(false, true);
            return true;
          }
          return false;
        }
        case MotionEvent.ACTION_CANCEL: {
          if (isSeeking) {
            setSeeking(false, false);
            return true;
          }
          return false;
        }
      }
      return false;
    }


    // Impl

    private String title, subtitle;
    private Text trimmedTitle, trimmedSubtitle;

    private boolean setTitle (String title) {
      if (!StringUtils.equalsOrBothEmpty(this.title, title)) {
        this.title = title;
        trimTitle();
        return true;
      }
      return false;
    }

    private boolean setSubtitle (String subtitle) {
      if (!StringUtils.equalsOrBothEmpty(this.subtitle, subtitle)) {
        this.subtitle = subtitle;
        trimSubtitle();
        return true;
      }
      return false;
    }

    private void trimTitle () {
      int availWidth = getAvailWidth();
      this.trimmedTitle = StringUtils.isEmpty(title) ? null : new Text.Builder(title, availWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.NORMAL).allBold().singleLine().build();
    }

    private void trimSubtitle () {
      int availWidth = getAvailWidth();
      this.trimmedSubtitle = StringUtils.isEmpty(subtitle) ? null : new Text.Builder(subtitle, availWidth, Paints.getSubtitleStyleProvider(), TextColorSets.Regular.LIGHT).singleLine().build();
    }

    private int lastWidth;

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      int width = getMeasuredWidth();
      if (lastWidth != width) {
        lastWidth = width;
        trimTitle();
        trimSubtitle();
      }
    }

    private float collapseFactor;
    private boolean isCollapsed;
    private FactorAnimator collapseAnimator;
    private static final int ANIMATOR_COLLAPSE = 1;

    private void setCollapsed (boolean isCollapsed, boolean animated) {
      animated = animated && !disableAnimations && controller.isScrollUnlocked;
      if (this.isCollapsed != isCollapsed || !animated) {
        this.isCollapsed = isCollapsed;
        final float toFactor = isCollapsed ? 1f : 0f;
        if (animated) {
          if (collapseAnimator == null) {
            collapseAnimator = new FactorAnimator(ANIMATOR_COLLAPSE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l, this.collapseFactor);
          }
          collapseAnimator.animateTo(toFactor);
        } else {
          if (collapseAnimator != null) {
            collapseAnimator.forceFactor(toFactor);
          }
          setCollapseFactor(toFactor);
        }
      }
    }

    private float getActualCollapseFactor () {
      return collapseFactor;
    }

    private void setCollapseFactor (float factor) {
      if (this.collapseFactor != factor) {
        this.collapseFactor = factor;
        controller.updateButtonColors();
        invalidate();
      }
    }

    private String positionStr, durationStr;
    private float durationWidth;
    private long positionMillis = -1, durationMillis = -1;
    private int positionSeconds = -1, durationSeconds = -1;

    private float fileProgress, fileOffset;
    private FactorAnimator progressAnimator, offsetAnimator;
    private static final int ANIMATOR_PROGRESS = 0, ANIMATOR_PROGRESS_PREFIX = 6;

    private void setOffsetFactor (float factor) {
      if (this.fileOffset != factor) {
        this.fileOffset = factor;
        invalidate();
      }
    }

    private void setProgressFactor (float factor) {
      if (this.fileProgress != factor) {
        this.fileProgress = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case ANIMATOR_PROGRESS: {
          setProgressFactor(factor);
          break;
        }
        case ANIMATOR_COLLAPSE: {
          setCollapseFactor(factor);
          break;
        }
        case ANIMATOR_SEEK: {
          setSeekDesireFactor(factor);
          break;
        }
        case ANIMATOR_SEEK_DISPLAY: {
          setSeekDisplayProgress(factor);
          break;
        }
        case ANIMATOR_BUFFERING: {
          setBufferFactor(factor);
          break;
        }
        case ANIMATOR_PROGRESS_PREFIX: {
          setOffsetFactor(factor);
          break;
        }
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    public void setFileProgress (float offset, float progress, boolean animated) {
      progress += offset;
      if (animated) {
        long duration;

        if (progressAnimator == null || progressAnimator.getToFactor() != progress) {
          duration = progress > this.fileProgress ? 160l : 120l;
          if (progressAnimator == null) {
            progressAnimator = new FactorAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, duration, this.fileProgress);
          } else {
            progressAnimator.setDuration(duration);
          }
          progressAnimator.animateTo(progress);
        }

        if (offsetAnimator == null || offsetAnimator.getToFactor() != offset) {
          duration = offset > this.fileOffset ? 160l : 120l;
          if (offsetAnimator == null) {
            offsetAnimator = new FactorAnimator(ANIMATOR_PROGRESS_PREFIX, this, AnimatorUtils.DECELERATE_INTERPOLATOR, duration, this.fileOffset);
          } else {
            offsetAnimator.setDuration(duration);
          }
          offsetAnimator.animateTo(offset);
        }
      } else {
        if (progressAnimator != null)
          progressAnimator.forceFactor(progress);
        setProgressFactor(progress);
        if (offsetAnimator != null)
          offsetAnimator.forceFactor(offset);
        setOffsetFactor(offset);
      }
    }

    private boolean setPosition (int seconds) {
      if (positionSeconds != seconds) {
        this.positionSeconds = seconds;
        this.positionStr = Strings.buildDuration(seconds);
        return true;
      }
      return false;
    }

    private boolean setDuration (int seconds) {
      if (this.durationSeconds != seconds) {
        this.durationSeconds = seconds;
        StringBuilder b = new StringBuilder(5);
        b.append('-');
        Strings.buildDuration(durationSeconds, TimeUnit.SECONDS, false, b);
        String str = b.toString();
        this.durationStr = str;
        this.durationWidth = U.measureText(str, Paints.getRegularTextPaint(12f));
        return true;
      }
      return false;
    }

    public void setDurations (long position, long duration) {
      if (item == null) {
        return;
      }
      if (duration == -1) {
        duration = item.getTrackAudio().duration * 1000l;
      }
      if (position == -1) {
        position = 0;
      }
      if (setDurationsImpl(position, duration)) {
        invalidate();
      }
    }

    private boolean setDurationTexts () {
      long position, duration = this.durationMillis;

      if (isSeeking) {
        position = calculateDesiredSeekPosition();
      } else {
        position = this.positionMillis;
      }

      int positionSeconds = Math.max(0, (int) Math.floor((double) position / 1000.0));
      int totalSeconds = Math.max(positionSeconds, (int) Math.floor((double) duration / 1000.0));
      int durationSeconds = totalSeconds - positionSeconds;

      boolean changed;
      changed = setPosition(positionSeconds);
      changed = setDuration(durationSeconds) || changed;

      return changed;
    }

    private boolean setDurationsImpl (long position, long duration) {
      if (this.positionMillis != position || this.durationMillis != duration) {
        this.positionMillis = position;
        this.durationMillis = duration;

        if (!isSeeking) {
          setDurationTexts();
        }

        float newSeek = calculatePositionProgress();

        setSeekDisplayProgress(newSeek, false);

        return true;
      }
      return false;
    }

    private InlineResultCommon item;
    private MediaPreview mediaPreview;

    @Override
    public boolean invalidateContent (Object cause) {
      requestFiles(true);
      return true;
    }

    public void setItem (InlineResultCommon item) {
      if (this.item == item) {
        return;
      }
      if (this.item != null) {
        this.item.detachFromView(this);
      }
      this.item = item;
      setSeeking(false, false);
      if (item != null) {
        item.attachToView(this);
        boolean changed;
        changed = setTitle(item.getTrackTitle());
        changed = setSubtitle(item.getTrackSubtitle()) || changed;
        long progressMillis = controller.tdlib().context().player().getPlayPosition(item.tdlib(), item.getMessage());
        long durationMillis = controller.tdlib().context().player().getPlayDuration(item.tdlib(), item.getMessage());
        if (durationMillis == -1) {
          durationMillis = item.getTrackAudio().duration * 1000l;
        }
        if (progressMillis == -1) {
          progressMillis = 0;
        }
        changed = setDurationsImpl(progressMillis, durationMillis) || changed;
        if (changed) {
          invalidate();
        }
      } else {
        boolean changed;
        changed = setTitle(null);
        changed = setSubtitle(null) || changed;
        changed = setDurationsImpl(0, 0) || changed;
        if (changed) {
          invalidate();
        }
      }
      requestFiles(false);
    }

    private void requestFiles (boolean invalidate) {
      this.mediaPreview = item != null ? item.getMediaPreview() : null;
      if (item == null) {
        preview.clear();
        receiver.clear();
        source.clear();
        return;
      }
      if (!invalidate) {
        if (mediaPreview != null) {
          mediaPreview.requestFiles(preview, false);
        } else {
          preview.clear();
        }

        ImageApicFile bigFile = new ImageApicFile(item.tdlib(), item.getMessage());
        bigFile.setNoCache();
        bigFile.setSize(Screen.smallestSide());
        bigFile.setScaleType(ImageFile.CENTER_CROP);
        source.requestFile(bigFile);
      }

      /*TODO
      ImageFile fullPreview = item.getFullPreview();
      if (fullPreview != null) {
        ImageMp3File smallFile = new ImageMp3File(fullPreview.getFilePath());
        smallFile.setScaleType(ImageFile.CENTER_CROP);
        smallFile.setSize(fullPreview.getSize());
        smallFile.setCacheOnly();
        receiver.requestFile(smallFile);
      } else {
        receiver.requestFile(null);
      }*/
    }

    private int getAvailWidth () {
      final int coverLeft = Screen.dp(58f);
      final int coverWidth = Screen.dp(50f);
      final int textMargin = Screen.dp(13f);
      return getMeasuredWidth() - (coverLeft + coverWidth + textMargin + Screen.dp(30));
    }

    private float bufferingFactor;
    private BoolAnimator bufferAnimator;
    private static final int ANIMATOR_BUFFERING = 5;

    private void setBufferFactor (float factor) {
      if (this.bufferingFactor != factor) {
        this.bufferingFactor = factor;
        this.progress.setAlpha(bufferingFactor);
        invalidate();
      }
    }

    private void setIsBuffering (boolean isBuffering) {
      if (true) {
        return;
      }
      if (bufferAnimator == null) {
        bufferAnimator = new BoolAnimator(ANIMATOR_BUFFERING, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      bufferAnimator.setStartDelay(isBuffering ? 1000l : 0);
      bufferAnimator.setValue(isBuffering, true);
    }

    private int getTitleWidth () {
      return trimmedTitle != null ? trimmedTitle.getWidth() : 0;
    }

    private int getSubtitleWidth () {
      return trimmedSubtitle != null ? trimmedSubtitle.getWidth() : 0;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (item == null) {
        return;
      }

      final int viewWidth = getMeasuredWidth();

      // Values
      final int overlayHeight = controller.getCurrentOverlayHeight();
      final int buttonsY = overlayHeight - Screen.dp(34f);
      final int durationsY = buttonsY - Screen.dp(30f);
      final int workingDistance = Math.min(viewWidth, Screen.dp(480f));
      final int startX = viewWidth != workingDistance ? viewWidth / 2 - workingDistance / 2 : 0;

      final int seekMargin = Screen.dp(12f);
      final int seekX = startX + seekMargin;
      final int seekDistance = workingDistance - seekMargin * 2;
      final int seekY = durationsY - Screen.dp(22f);

      final int durationsMargin = Screen.dp(10f);

      final float collapseFactor = getActualCollapseFactor();

      float coverFactor = 1f - collapseFactor;
      final int coverLeft = Screen.dp(58f);
      final int coverWidth = Screen.dp(50f);
      final int coverY = HeaderView.getTopOffset() + Screen.dp(5f);
      final int coverHeight = overlayHeight - getMinimumOverlayHeight();

      final int textStartX = coverLeft + coverWidth + Screen.dp(13f);
      final int textStartY = coverY + Screen.dp(19f);
      final int textStartY2 = coverY + Screen.dp(5f);

      final int textEndCenterX = viewWidth / 2;
      final int textEndY =  buttonsY - Screen.dp(98f);

      // Title & subtitle

      final float textY = textStartY + (textEndY - textStartY) * coverFactor;
      final float textY2 = textStartY2 + (textEndY - textStartY) * coverFactor;
      final float titleX = textStartX + ((textEndCenterX - getTitleWidth() / 2) - textStartX) * coverFactor;
      final float subtitleX = textStartX + ((textEndCenterX - getSubtitleWidth() / 2) - textStartX) * coverFactor;

      if (trimmedTitle != null) {
        trimmedTitle.draw(c, (int) titleX, (int) textY2);
      }
      if (trimmedSubtitle != null) {
        trimmedSubtitle.draw(c, (int) subtitleX, (int) textY2 + Screen.dp(24f));
      }

      // Seekbar

      int color = Theme.textDecentColor();
      c.drawText(positionStr, startX + durationsMargin, durationsY, Paints.getRegularTextPaint(12f, color));
      c.drawText(durationStr, startX + workingDistance - durationWidth - durationsMargin, durationsY, Paints.getRegularTextPaint(12f, color));

      // final float seekDoneProgress = getDoneProgress();
      final float seekDistanceDone = seekDistance * seekDisplayProgress;
      final float seekDistanceReadyStart = seekDistance * fileOffset;
      final float seekDistanceReadyEnd = seekDistance * fileProgress;
      final int seekStroke = Screen.dp(2f);
      int doneColor = Theme.getColor(R.id.theme_color_seekDone);
      if (seekDistanceReadyStart > 0 || seekDistanceReadyEnd < seekDistance) {
        c.drawLine(seekX, seekY, seekX + seekDistance, seekY, Paints.getProgressPaint(Theme.getColor(R.id.theme_color_seekEmpty), seekStroke));
      }
      c.drawLine(seekX + seekDistanceReadyStart, seekY, seekX + seekDistanceReadyEnd, seekY, Paints.getProgressPaint(Theme.getColor(R.id.theme_color_seekReady), seekStroke));
      c.drawLine(seekX, seekY, seekX + seekDistanceDone, seekY, Paints.getProgressPaint(doneColor, seekStroke));
      float seekCenterX = seekX + seekDistance * getSeekFactor();
      c.drawCircle(seekCenterX, seekY, Screen.dp(6f) + Screen.dp(4f) * seekDesireFactor, Paints.fillingPaint(doneColor));
      if (bufferingFactor > 0f) {
        int radius = Screen.dp(4f);
        progress.setBounds((int) seekCenterX - radius, seekY - radius, (int) seekCenterX + radius, seekY + radius);
        progress.draw(c);
      }

      // Cover image

      int x = (int) ((float) coverLeft * (1f - coverFactor));
      int y = (int) ((float) coverY * (1f - coverFactor));
      int width = coverWidth + (int) ((float) (viewWidth - coverWidth) * coverFactor);
      int height = coverWidth + (int) ((float) (coverHeight - coverWidth) * coverFactor);

      int radius = (int) (Screen.dp(4f) * (1f - coverFactor));

      boolean ignoreSource = coverFactor < .5f;

      if (source.needPlaceholder() || ignoreSource) {
        // receiver.setRadius(radius);
        receiver.setBounds(x, y, x + width, y + height);
        if (receiver.needPlaceholder()) {
          if (mediaPreview == null || mediaPreview.needPlaceholder(preview)) {
            if (radius == 0) {
              c.drawRect(x, y, x + width, y + height, Paints.fillingPaint(Theme.getColor(R.id.theme_color_playerCoverPlaceholder)));
            } else {
              RectF rectF = Paints.getRectF();
              rectF.set(x, y, x + width, y + height);
              c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_playerCoverPlaceholder)));
            }
            c.save();
            c.clipRect(x, y, x + width, y + height);
            int cx = receiver.centerX();
            int cy = receiver.centerY();
            float scale = Math.max((float) receiver.getWidth() / (float) getMeasuredWidth(), (float) receiver.getHeight() / (float) getMeasuredHeight()) * (2f + 1.5f * collapseFactor);
            if (scale != 1f) {
              c.scale(scale, scale, cx, cy);
            }
            Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, Paints.getNotePorterDuffPaint());
            c.restore();
          }
          if (mediaPreview != null) {
            mediaPreview.draw(this, c, preview, x, y, width, height, radius, 1f);
          }
        }
        receiver.draw(c);
      }
      // source.setRadius(radius);
      source.setBounds(x, y, x + width, y + height);
      source.draw(c);

      int shadowAlpha = (int) (255f * coverFactor);
      if (coverFactor > 0f) {
        topShadowPaint.setAlpha((int) (shadowAlpha * .4f));
        c.save();
        int shadowHeight = ShadowView.simpleTopShadowHeight();
        c.clipRect(x, y, x + width, y + height);
        int fromY = y + height - shadowHeight;
        c.translate(x, fromY);
        c.drawRect(0, 0, width, shadowHeight, topShadowPaint);

        int radialRadius = getRadialGradientRadius();
        int leftX = Screen.dp(56f) / 2 - radialRadius;
        int rightX = viewWidth - Screen.dp(49f) / 2 - radialRadius;

        radialShadowPaint.setAlpha(shadowAlpha);

        c.translate(leftX - x, (HeaderView.getTopOffset() + HeaderView.getSize(false) / 2 - radialRadius) - fromY);
        c.drawRect(0, 0, radialRadius * 2, radialRadius * 2, radialShadowPaint);
        c.translate(rightX - leftX, 0);
        c.drawRect(0, 0, radialRadius * 2, radialRadius * 2, radialShadowPaint);

        c.restore();
      }

      if (radius > 0) {
        RectF rectF = Paints.getRectF();
        int strokeSize = radius / 2;
        rectF.set(x - strokeSize / 2, y - strokeSize / 2, x + width + strokeSize / 2, y + height + strokeSize / 2);
        c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(Theme.fillingColor(), strokeSize));
      }

      // Buttons

      int buttonsDistance = workingDistance;

      int cx = viewWidth / 2 - buttonsDistance / 2;
      int perx = buttonsDistance / 5;
      cx += perx / 6;
      buttonsDistance -= (perx / 6) * 2;
      perx = buttonsDistance / 5;

      for (int i = 0; i < 5; i++) {
        View view;
        switch (i) {
          case 0: view = controller.shuffleButton; break;
          case 1: view = controller.prevButton; break;
          case 2: view = controller.playPauseButton; break;
          case 3: view = controller.nextButton; break;
          case 4: view = controller.repeatButton; break;
          default: throw new IllegalArgumentException("i == " + i);
        }
        int buttonX = cx + perx / 2 - view.getMeasuredWidth() / 2;
        int buttonY = buttonsY - view.getMeasuredHeight() / 2;
        view.setTranslationX(buttonX);
        view.setTranslationY(buttonY);
        cx += perx;
      }
    }
  }

  private static class PlayOverlayLayout extends FrameLayoutFix {
    private PlaybackController controller;

    public PlayOverlayLayout (@NonNull Context context) {
      super(context);
    }

    public void initWithController (PlaybackController controller) {
      this.controller = controller;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int availableHeight = controller.getAvailableOverlayHeight(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY));
    }
  }

  private static class PlayListRecyclerView extends RecyclerView {
    public PlayListRecyclerView (Context context) {
      super(context);
    }

    private PlaybackController controller;

    public void initWithController (final PlaybackController controller) {
      this.controller = controller;
      addOnScrollListener(new OnScrollListener() {
        private boolean scrollingByTouch;

        @Override
        public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
          scrollingByTouch = newState == RecyclerView.SCROLL_STATE_DRAGGING;
        }

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          if (dy != 0 && scrollingByTouch) {
            controller.unlockScroll();
          }
          controller.checkScrolling(dx == 0 && dy == 0);
        }
      });
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        if (getAlpha() == 0f) {
          return true;
        }
        if (!controller.isScrollUnlocked && e.getY() <= controller.getCurrentOverlayHeight()) {
          return true;
        }
      }
      return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      return !(getAlpha() == 0f && e.getAction() == MotionEvent.ACTION_DOWN) && super.onTouchEvent(e);
    }

    private int oldHeight;

    @Override
    protected void onMeasure (int widthSpec, int heightSpec) {
      super.onMeasure(widthSpec, heightSpec);
      int height = getMeasuredHeight();
      if (oldHeight != height) {
        if (!controller.isScrollUnlocked) {
          controller.dispatchEnsureScroll(false);
        }
        if (oldHeight != 0) {
          invalidateItemDecorations();
          if (!controller.mayExpandOverlay(getMeasuredWidth(), getMeasuredHeight())) {
            controller.coverView.setCollapsed(true, false);
          } else {
            controller.dispatchCheckScrolling(true);
          }
        } else {
          if (!controller.mayExpandOverlay(getMeasuredWidth(), getMeasuredHeight())) {
            controller.coverView.setCollapsed(true, false);
          }
        }
        oldHeight = height;
      }
    }
  }

  // ViewController

  @Override
  public void onFocus () {
    super.onFocus();
    coverView.setDisableAnimation(false);
    destroyAllStackItemsById(R.id.controller_playback);
  }

  @Override
  public boolean usePopupMode () {
    return true;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  public void updateButtonColors () {
    if (headerView != null && coverView != null) {
      headerView.updateButtonColorFactor(this, getMenuId(), coverView.getActualCollapseFactor());
      headerView.updateBackButtonColor(this, getHeaderIconColor());
    }
  }

  @Override
  protected int getHeaderIconColor () {
    return ColorUtils.fromToArgb(0xffffffff, Theme.getColor(R.id.theme_color_headerLightIcon), coverView.getActualCollapseFactor());
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_player;
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (headerView != null && coverView != null) {
      headerView.updateButtonColorFactor(this, getMenuId(), coverView.getActualCollapseFactor());
    }
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_player: {
        header.addMoreButton(menu, this, 0).setThemeColorId(R.id.theme_color_white, R.id.theme_color_headerLightIcon, coverView.getActualCollapseFactor());
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_more: {
        IntList ids = new IntList(3);
        StringList strings = new StringList(3);

        TdApi.Message message = currentItem.getMessage();

        TdApi.File file = TD.getFile(message);
        if (TD.isFileLoaded(file)) {
          ids.append(R.id.btn_saveFile);
          strings.append(R.string.SaveToMusic);
        }

        if (message.chatId != 0) {
          ids.append(R.id.btn_share);
          strings.append(R.string.Share);

          ids.append(R.id.btn_showInChat);
          strings.append(R.string.ShowInChat);
        }

        if (tracks.size() > 5 && isScrollUnlocked) {
          ids.append(R.id.btn_showInPlaylist);
          strings.append(R.string.PlayListHighlight);
        }

        if (tdlib.context().player().canReverseOrder()) {
          ids.append(R.id.btn_reverseOrder);
          strings.append(R.string.PlayListReverse);
        }

        showMore(ids.get(), strings.get(), 0);
        break;
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_share: {
        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(currentItem.getMessage()).setAllowCopyLink(true));
        c.show();
        break;
      }
      case R.id.btn_showInPlaylist: {
        highlightCurrentItem();
        break;
      }
      case R.id.btn_reverseOrder: {
        tdlib.context().player().toggleReverseMode();
        break;
      }
      case R.id.btn_showInChat: {
        TdApi.Message message = currentItem.getMessage();
        if (TD.isScheduled(message)) {
          tdlib.ui().openScheduledMessage(this, message);
        } else {
          tdlib.ui().openMessage(this, message, null);
        }
        break;
      }
      case R.id.btn_saveFile: {
        TD.DownloadedFile downloadedFile = TD.getDownloadedFile(currentItem.getMessage());;
        if (downloadedFile != null) {
          TD.saveFile(context, downloadedFile);
        }
        break;
      }
    }
  }
}
