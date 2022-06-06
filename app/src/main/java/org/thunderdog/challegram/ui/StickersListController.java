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
 * File created on 22/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.ProgressComponentView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.Td;

public class StickersListController extends ViewController<StickersListController.StickerSetProvider> implements Menu, StickerSmallView.StickerMovementCallback, Client.ResultHandler, MoreDelegate, StickersListener {
  public StickersListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public interface StickerSetProvider {
    boolean canArchiveStickerSet ();
    boolean canRemoveStickerSet ();
    boolean canViewPack ();
    void archiveStickerSet ();
    void removeStickerSet ();
    boolean onStickerClick (View view, TGStickerObj obj, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState);
    long getStickerOutputChatId ();
  }

  @Override
  public int getId () {
    return R.id.controller_stickerSet;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_more: {
        header.addMoreButton(menu, this);
        break;
      }
    }
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_headerLightIcon;
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_more: {
        if (info != null) {
          IntList ids = new IntList(4);
          StringList strings = new StringList(4);
          IntList icons = new IntList(4);

          ids.append(R.id.btn_share);
          strings.append(R.string.Share);
          icons.append(R.drawable.baseline_forward_24);

          ids.append(R.id.btn_copyLink);
          strings.append(R.string.CopyLink);
          icons.append(R.drawable.baseline_link_24);

          if (getArguments() != null) {
            if (getArguments().canArchiveStickerSet()) {
              ids.append(R.id.btn_archive);
              strings.append(R.string.StickersHide);
              icons.append(R.drawable.baseline_archive_24);
            }
            if (getArguments().canRemoveStickerSet()) {
              ids.append(R.id.btn_delete);
              strings.append(R.string.DeleteArchivedPack);
              icons.append(R.drawable.baseline_delete_24);
            }
          }

          showMore(ids.get(), strings.get(), icons.get(), 0, true);
        }
        break;
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_share: {
        tdlib.ui().shareStickerSetUrl(this, info);
        break;
      }
      case R.id.btn_copyLink: {
        UI.copyText(TD.getStickerPackLink(info.name), R.string.CopiedLink);
        break;
      }
      case R.id.btn_archive: {
        if (getArguments() != null) {
          getArguments().archiveStickerSet();
        }
        break;
      }
      case R.id.btn_delete: {
        if (getArguments() != null) {
          getArguments().removeStickerSet();
        }
        break;
      }
    }
  }

  @Override
  public CharSequence getName () {
    if (info != null) {
      TdApi.TextEntity[] entities = Td.findEntities(info.title);
      return Emoji.instance().replaceEmoji(TD.formatString(this, info.title, entities, null, null));
    }
    return null;
  }

  private TdApi.StickerSetInfo info;
  private OffsetProvider offsetProvider;

  public void setOffsetProvider (OffsetProvider provider) {
    this.offsetProvider = provider;
  }

  public void setStickerSetInfo (TdApi.StickerSetInfo info) {
    this.info = info;
  }

  public void setStickers (TdApi.Sticker[] stickers, TdApi.StickerType stickerType, TdApi.Emojis[] emojis) {
    this.stickers = new ArrayList<>(stickers.length);
    int i = 0;
    boolean canViewPack = getArguments() == null || getArguments().canViewPack();
    for (TdApi.Sticker sticker : stickers) {
      TGStickerObj obj = new TGStickerObj(tdlib, sticker, stickerType, emojis[i].emojis);
      if (!canViewPack) {
        obj.setNoViewPack();
      }
      this.stickers.add(obj);
      i++;
    }
  }

  private RecyclerView recyclerView;
  private StickersAdapter adapter;
  private int spanCount;
  private int lastSpanCountWidth, lastSpanCountHeight;
  private GridLayoutManager manager;

  private void setSpanCount (int width, int height) {
    if (width == 0 || height == 0) {
      return;
    }
    if (lastSpanCountWidth != width || lastSpanCountHeight != height) {
      lastSpanCountWidth = width;
      lastSpanCountHeight = height;
      int newSpanCount = calculateSpanCount(width, height);
      if (newSpanCount != spanCount) {
        this.spanCount = newSpanCount;
        manager.setSpanCount(newSpanCount);
      }
    }
  }

  private int offsetScroll;
  private boolean isSeparate;

  @Override
  public void attachHeaderViewWithoutNavigation (HeaderView headerView) {
    super.attachHeaderViewWithoutNavigation(headerView);
    this.isSeparate = true;
  }

  @Override
  public boolean needsTempUpdates () {
    return isSeparate || super.needsTempUpdates();
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (isSeparate && headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  protected View onCreateView (Context context) {
    spanCount = calculateSpanCount(lastSpanCountWidth = Screen.currentWidth(), lastSpanCountHeight = Screen.currentHeight());

    FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setSpanCount(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      }
    };
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = Size.getHeaderPortraitSize();
    params.bottomMargin = Screen.dp(56f);

    recyclerView = new RecyclerView(context) {
      @Override
      public void draw (Canvas c) {
        c.drawRect(0, offsetProvider.provideOffset() - offsetScroll, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
        super.draw(c);
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
          int topEdge = offsetProvider.provideOffset() - offsetScroll;
          float y = e.getY() + Size.getHeaderPortraitSize();
          return y >= topEdge && super.onTouchEvent(e);
        } else {
          return super.onTouchEvent(e);
        }
      }
    };
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(manager = new RtlGridLayoutManager(context, spanCount).setAlignOnly(true));
    recyclerView.setAdapter(adapter = new StickersAdapter(this, recyclerView, this, offsetProvider));
    recyclerView.setLayoutParams(params);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          offsetProvider.onScrollFinished();
        }
      }

      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        int i = manager.findFirstVisibleItemPosition();
        View view = manager.findViewByPosition(i);
        if (view != null) {
          View stickerView = i == 1 ? view : manager.findViewByPosition(1);
          float shadowFactor = stickerView == null ? 1f : stickerView.getTop() >= 0 ? 0f : Math.max(0f, Math.min(1f, (float) -stickerView.getTop() / (float) Screen.dp(StickerSmallView.PADDING)));

          manager.findViewByPosition(1);

          offsetScroll = i > 0 ? offsetProvider.provideOffset() : -view.getTop();
          offsetProvider.onContentScroll(shadowFactor);
        }
      }
    });
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return position == 0 || (position == 1 && adapter.getItemViewType(1) == StickerHolder.TYPE_PROGRESS) ? spanCount : 1;
      }
    });

    contentView.addView(recyclerView);

    if (stickers != null) {
      adapter.setItems(stickers);
    } else if (info != null) {
      tdlib.client().send(new TdApi.GetStickerSet(info.id), this);
    }
    if (info != null) {
      tdlib.listeners().subscribeToStickerUpdates(this);
    }

    return contentView;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.requestLayout();
  }

  public int getOffsetScroll () {
    return offsetScroll;
  }

  private CancellableRunnable itemAnimatorRunnable;

  public void setItemAnimator () {
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
  }

  private ArrayList<TGStickerObj> stickers;

  private void buildCells () {
    if (itemAnimatorRunnable != null) {
      itemAnimatorRunnable.cancel();
      itemAnimatorRunnable = null;
    }
    adapter.setItems(stickers);
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
  }

  @Override
  public void onStickerSetUpdated (TdApi.StickerSet stickerSet) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && info.id == stickerSet.id) {
        setStickers(stickerSet.stickers, stickerSet.stickerType, stickerSet.emojis);
        buildCells();
      }
    });
  }

  @Override
  public void onResult (final TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StickerSet.CONSTRUCTOR: {
        TdApi.StickerSet stickerSet = (TdApi.StickerSet) object;
        setStickers(stickerSet.stickers, stickerSet.stickerType, stickerSet.emojis);

        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            buildCells();
          }
        });

        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            UI.showToast(TD.toErrorString(object), Toast.LENGTH_SHORT);
          }
        });
        break;
      }
    }
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    int i = indexOfSticker(sticker);
    if (i != -1) {
      final View childView = manager.findViewByPosition(i);
      if (childView != null && childView instanceof StickerSmallView) {
        ((StickerSmallView) childView).setStickerPressed(isPressed);
      } else {
        adapter.notifyItemChanged(i);
      }
    }
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return true;
  }

  public int indexOfSticker (TGStickerObj obj) {
    int i = 0;
    for (TGStickerObj sticker : adapter.stickers) {
      if (sticker.equals(obj)) {
        return i + 1;
      }
      i++;
    }
    return -1;
  }

  @Override
  public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {

  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
    return getArguments() != null && getArgumentsStrict().onStickerClick(clickView, sticker, isMenuClick, forceDisableNotification, schedulingState);
  }

  @Override
  public long getStickerOutputChatId () {
    return getArgumentsStrict().getStickerOutputChatId();
  }

  @Override
  public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {

  }

  @Override
  public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj sticker) {

  }

  @Override
  public int getStickersListTop () {
    return Size.getHeaderPortraitSize();
  }

  public void scrollBy (int y) {
    recyclerView.smoothScrollBy(0, y);
  }

  public interface OffsetProvider {
    int provideOffset ();
    int provideReverseOffset ();
    void onContentScroll (float shadowFactor);
    void onScrollFinished ();
  }

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 4;
    return minWidth != 0 ? width / minWidth : 4;
  }

  public static int getEstimateColumnResolution () {
    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());
    return Screen.currentWidth() / spanCount;
  }

  private static class StickerHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_PADDING = 0;
    public static final int TYPE_STICKER = 1;
    public static final int TYPE_PROGRESS = 2;

    public StickerHolder (View itemView) {
      super(itemView);
    }

    public static StickerHolder create (Context context, Tdlib tdlib, int viewType, StickerSmallView.StickerMovementCallback callback, final OffsetProvider provider) {
      switch (viewType) {
        case TYPE_PADDING: {
          View view = new View(context) {
            @Override
            protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
              setMeasuredDimension(
                getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                MeasureSpec.makeMeasureSpec(provider.provideOffset(), MeasureSpec.EXACTLY));
            }
          };
          return new StickerHolder(view);
        }
        case TYPE_PROGRESS: {
          FrameLayoutFix contentView = new FrameLayoutFix(context) {
            @Override
            protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
              super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(provider.provideReverseOffset(), MeasureSpec.EXACTLY));
            }
          };
          ProgressComponentView view = new ProgressComponentView(context);
          view.initBig(1f);
          view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
          contentView.addView(view);
          return new StickerHolder(contentView);
        }
        case TYPE_STICKER: {
          StickerSmallView stickerSmallView = new StickerSmallView(context);
          stickerSmallView.init(tdlib);
          stickerSmallView.setStickerMovementCallback(callback);
          stickerSmallView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new StickerHolder(stickerSmallView);
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }

  private static class StickersAdapter extends RecyclerView.Adapter<StickerHolder> {
    private final ViewController<?> context;
    private final StickerSmallView.StickerMovementCallback callback;
    private final ArrayList<TGStickerObj> stickers;
    private final OffsetProvider provider;
    private final RecyclerView parent;

    public StickersAdapter (ViewController<?> context, RecyclerView parent, StickerSmallView.StickerMovementCallback callback, OffsetProvider provider) {
      this.context = context;
      this.parent = parent;
      this.callback = callback;
      this.provider = provider;
      this.stickers = new ArrayList<>();
    }

    private boolean noProgress;

    public void setItems (final ArrayList<TGStickerObj> stickers) {
      noProgress = true;
      notifyItemRemoved(1);
      StickersAdapter.this.stickers.addAll(stickers);
      notifyItemRangeInserted(1, stickers.size());
    }

    @Override
    public StickerHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      return StickerHolder.create(context.context(), context.tdlib(), viewType, callback, provider);
    }

    @Override
    public void onBindViewHolder (StickerHolder holder, int position) {
      switch (holder.getItemViewType()) {
        case StickerHolder.TYPE_STICKER: {
          ((StickerSmallView) holder.itemView).setSticker(stickers.get(position - 1));
          break;
        }
      }
    }

    @Override
    public void onViewAttachedToWindow (StickerHolder holder) {
      switch (holder.getItemViewType()) {
        case StickerHolder.TYPE_STICKER: {
          ((StickerSmallView) holder.itemView).attach();
          break;
        }
        case StickerHolder.TYPE_PROGRESS: {
          ((ProgressComponentView) ((ViewGroup) holder.itemView).getChildAt(0)).attach();
          break;
        }
      }
    }

    @Override
    public void onViewDetachedFromWindow (StickerHolder holder) {
      switch (holder.getItemViewType()) {
        case StickerHolder.TYPE_STICKER: {
          ((StickerSmallView) holder.itemView).detach();
          break;
        }
        case StickerHolder.TYPE_PROGRESS: {
          ((ProgressComponentView) ((ViewGroup) holder.itemView).getChildAt(0)).detach();
          break;
        }
      }
    }

    @Override
    public void onViewRecycled (StickerHolder holder) {
      switch (holder.getItemViewType()) {
        case StickerHolder.TYPE_STICKER: {
          ((StickerSmallView) holder.itemView).performDestroy();
          break;
        }
        case StickerHolder.TYPE_PROGRESS: {
          ((ProgressComponentView) ((ViewGroup) holder.itemView).getChildAt(0)).performDestroy();
          break;
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      return stickers.isEmpty() ? (position == 1 ? StickerHolder.TYPE_PROGRESS : StickerHolder.TYPE_PADDING) : (position == 0 ? StickerHolder.TYPE_PADDING : StickerHolder.TYPE_STICKER);
    }

    @Override
    public int getItemCount () {
      return stickers.isEmpty() ? noProgress ? 1 : 2 : 1 + stickers.size();
    }
  }
}
