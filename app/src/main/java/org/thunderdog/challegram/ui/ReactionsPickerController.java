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
 * File created on 31/05/2023
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.EmojiMediaLayout.EmojiLayoutRecyclerController;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Headers.EmojiHeaderView;
import org.thunderdog.challegram.util.StickerSetsDataProvider;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSection;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.StickerSectionView;

import java.util.ArrayList;
import java.util.Set;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;

public class ReactionsPickerController extends ViewController<ReactionsPickerController.Args> implements StickersListener, EmojiLayoutRecyclerController.Callback, StickerSmallView.StickerMovementCallback {
  private TGMessage message;
  private Set<String> chosenReactions;
  private EmojiLayoutRecyclerController reactionsController;
  private CustomRecyclerView recyclerView;
  private MediaStickersAdapter adapter;
  private EmojiHeaderView bottomHeaderView;

  public ReactionsPickerController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected View onCreateView (Context context) {
    ArrayList<EmojiSection> emojiSections = new ArrayList<>(1);
    emojiSections.add(new EmojiSection(this, EmojiSection.SECTION_EMOJI_RECENT, R.drawable.baseline_favorite_24, R.drawable.baseline_favorite_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent().setOffsetHalf(false));
    bottomHeaderView = new EmojiHeaderView(context, this, this, emojiSections, null, false);
    bottomHeaderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56), Gravity.BOTTOM));
    bottomHeaderView.setIsPremium(true, false);
    bottomHeaderView.setSectionsOnClickListener(this::onStickerSectionClick);
    ViewSupport.setThemedBackground(bottomHeaderView, ColorId.filling);
    addThemeInvalidateListener(bottomHeaderView);

    recyclerView = onCreateRecyclerView();
    recyclerView.setItemAnimator(null);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {}

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (!reactionsController.isNeedIgnoreScroll()) {
          setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, reactionsController.getStickerSetSection(), true, true);
        }
      }
    });

    headerView = new HeaderView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(56), MeasureSpec.EXACTLY));
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        super.onTouchEvent(e);
        return true;
      }
    };
    headerView.initWithSingleController(this, false);
    headerView.setBackgroundHeight(Screen.dp(56));
    headerView.getBackButton().setIsReverse(true);
    addThemeInvalidateListener(headerView);

    buildCells();
    return recyclerView;
  }

  public CustomRecyclerView onCreateRecyclerView () {
    reactionsController = new EmojiLayoutRecyclerController(context, tdlib, R.id.controller_emojiLayoutReactions);
    adapter = new MediaStickersAdapter(this, this, false, this) {
      @Override
      public void onBindViewHolder (StickerHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        int type = getItemViewType(position);
        if (type == StickerHolder.TYPE_STICKER) {
          TGStickerObj stickerObj = getSticker(position);
          ((StickerSmallView) holder.itemView).setPadding(Screen.dp(stickerObj != null && stickerObj.isReaction() ? -1: 4.5f));
          ((StickerSmallView) holder.itemView).setChosen(stickerObj != null && chosenReactions != null && stickerObj.getReactionType() != null && chosenReactions.contains(TD.makeReactionKey(stickerObj.getReactionType())));
        }
      }
    };
    adapter.setRepaintingColorId(ColorId.text);

    reactionsController.setArguments(this);
    reactionsController.setAdapter(adapter);
    reactionsController.setItemWidth(9, 38);
    reactionsController.getValue();
    reactionsController.getManager().setCanScrollVertically(false);

    return (CustomRecyclerView) reactionsController.getValue();
  }

  public int getItemWidth () {
    return (recyclerView.getMeasuredWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight()) / reactionsController.getSpanCount();

  }

  public CustomRecyclerView getRecyclerView () {
    return recyclerView;
  }

  private boolean isFullyVisible = true;

  private void buildCells () {
    ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
    ArrayList<TGStickerSetInfo> emojiPacks = new ArrayList<>();

    TdApi.AvailableReaction[] reactions = message.getMessageAvailableReactions();
    if (reactions != null) {
      items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_EMPTY));

      TGStickerSetInfo pack = TGStickerSetInfo.fromEmojiSection(tdlib, -1, -1, reactions.length);
      pack.setStartIndex(items.size());
      pack.setIsRecent();
      items.ensureCapacity(reactions.length);
      emojiPacks.add(pack);

      int a = 0;
      for (TdApi.AvailableReaction reaction: reactions) {
        TGReaction reactionObj = tdlib.getReaction(reaction.type);
        TGStickerObj stickerObj = reactionObj != null ? reactionObj.newCenterAnimationSicker(): null;
        if (stickerObj != null) {
          stickerObj.setIsReaction();
          if (stickerObj.getPreviewAnimation() != null) {
            stickerObj.getPreviewAnimation().setPlayOnce(true);
            stickerObj.getPreviewAnimation().setLooped(false);
          }
        }
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, stickerObj));
        a++;
        if (a == reactionsController.getSpanCount() && !isFullyVisible) {
          break;
        }
      }
    }

    reactionsController.setDefaultEmojiPacks(emojiPacks, items);
  }

  public EmojiHeaderView getBottomHeaderView () {
    return bottomHeaderView;
  }

  private void onStickerSectionClick (View v) {
    /*if (scrollState != androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE) {
      return;
    }*/

    final int viewId = v.getId();
    if (viewId == R.id.btn_stickerSet) {
      TGStickerSetInfo info = ((StickerSectionView) v).getStickerSet();
      if (info != null) {
        int index = reactionsController.indexOfStickerSet(info);
        reactionsController.scrollToStickerSet(index, EmojiLayout.getHeaderPadding(), false, true);
      }
    } else if (viewId == R.id.btn_section) {
      reactionsController.scrollToStickerSet(0, EmojiLayout.getHeaderPadding(), false, true);
    }
  }

  /* * */

  public void prepareToShow () {
    reactionsController.getManager().setCanScrollVertically(true);
    //reactionsController.clearAllItems();
    //isFullyVisible = true;
    //buildCells();
    loadStickers();
  }

  private boolean loadingStickers;

  private void loadStickers () {
    if (loadingStickers || !message.isCustomEmojiReactionsAvailable()) {
      return;
    }

    loadingStickers = true;
    tdlib.client().send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeCustomEmoji()), stickerSetsHandler());
  }

  private Client.ResultHandler stickerSetsHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.StickerSets.CONSTRUCTOR: {
          TdApi.StickerSetInfo[] rawStickerSets = ((TdApi.StickerSets) object).sets;

          final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(rawStickerSets.length);
          final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();

          if (rawStickerSets.length > 0) {
            int startIndex = this.adapter.getItemCount();

            for (TdApi.StickerSetInfo rawInfo : rawStickerSets) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, rawInfo);
              if (info.getSize() == 0) {
                continue;
              }
              stickerSets.add(info);
              info.setStartIndex(startIndex);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, info));
              for (int i = 0; i < rawInfo.size; i++) {
                TGStickerObj sticker = new TGStickerObj(tdlib, i < rawInfo.covers.length ? rawInfo.covers[i] : null, null, rawInfo.stickerType);
                sticker.setStickerSetId(rawInfo.id, null);
                sticker.setDataProvider(stickerSetsDataProvider());
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
              }
              startIndex += rawInfo.size + 1;
            }
          }

          runOnUiThreadOptional(() -> {
            /*if (getArguments() != null) {
              getArguments().setEmojiPacks(stickerSets);
            }*/
            setStickers(stickerSets, items);
          });

          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }
    };
  }


  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.reactionsController.setStickers(stickerSets, items);
    this.loadingStickers = false;
    if (stickerSetsDataProvider != null) {
      this.stickerSetsDataProvider.clear();
    }
    bottomHeaderView.setStickerSets(stickerSets);
    recyclerView.invalidateItemDecorations();
    // tdlib.listeners().subscribeToStickerUpdates(this);
  }

  /* * */

  public HeaderView getHeaderView () {
    return headerView;
  }

  @Override
  public int getId () {
    return R.id.controller_reactionsPicker;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ReactionsPickerHeader);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected boolean allowMenuReuse () {
    return false;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.headerText;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.headerIcon;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }
/*
  @Override
  public void onScrollToTopRequested () {
    try {
      LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
      getRecyclerView().stopScroll();
      int firstVisiblePosition = manager.findFirstVisibleItemPosition();
      if (firstVisiblePosition == RecyclerView.NO_POSITION) {
        return;
      }
      int scrollTop = 0; // ((SettingsAdapter) recyclerView.getAdapter()).measureScrollTop(firstVisiblePosition);
      View view = manager.findViewByPosition(firstVisiblePosition);
      if (view != null) {
        scrollTop -= view.getTop();
      }
      getRecyclerView().smoothScrollBy(0, -scrollTop);
    } catch (Throwable t) {
      Log.w("Cannot scroll to top", t);
    }
  }
*/

  @Override
  public void setArguments (Args args) {
    this.message = args.message;
    if (message.getMessageReactions() != null) {
      this.chosenReactions = message.getMessageReactions().getChosen();
    }
    super.setArguments(args);
  }

  @Override
  public void destroy () {
    super.destroy();
    reactionsController.destroy();
  }

  private OnReactionClickListener onReactionClickListener;

  public void setOnReactionClickListener (OnReactionClickListener onReactionClickListener) {
    this.onReactionClickListener = onReactionClickListener;
  }

  public interface OnReactionClickListener {
    void onReactionClick (View v, TGReaction reaction, boolean isLongClick);
  }



  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    if (onReactionClickListener != null) {
      TdApi.ReactionType reactionType = sticker.isCustomEmoji() ?
        new TdApi.ReactionTypeCustomEmoji(sticker.getCustomEmojiId()): sticker.getReactionType();

      TGReaction reaction = tdlib.getReaction(reactionType);
      if (reaction == null && sticker.isCustomEmoji() && sticker.getSticker() != null) {
        reaction = new TGReaction(tdlib, sticker.getSticker());
      }

      if (reaction != null) {
        onReactionClickListener.onReactionClick(clickView, reaction, false);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onStickerLongClick (StickerSmallView view, TGStickerObj sticker) {
    if (onReactionClickListener != null) {
      TdApi.ReactionType reactionType = sticker.isCustomEmoji() ?
        new TdApi.ReactionTypeCustomEmoji(sticker.getCustomEmojiId()): sticker.getReactionType();

      TGReaction reaction = tdlib.getReaction(reactionType);
      if (reaction == null && sticker.isCustomEmoji() && sticker.getSticker() != null) {
        reaction = new TGReaction(tdlib, sticker.getSticker());
      }

      if (reaction != null) {
        onReactionClickListener.onReactionClick(view, reaction, true);
      }
    }

    return true;
  }

  @Override
  public long getStickerOutputChatId () {
    return 0;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {

  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return false;
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return false;
  }

  @Override
  public int getStickersListTop () {
    return 0;
  }

  @Override
  public int getViewportHeight () {
    return 0;
  }

  public static class Args {
    public final TGMessage message;

    public Args (TGMessage message) {
     this.message = message;
    }
  }




  /* Emoji Layout Callbacks */

  @Override
  public void setIgnoreMovement (boolean silent) {

  }

  @Override
  public void resetScrollState (boolean silent) {

  }

  @Override
  public void moveHeader (int totalDy) {

  }

  @Override
  public void setHasNewHots (int controllerId, boolean hasHots) {

  }

  @Override
  public boolean onStickerClick (int controllerId, StickerSmallView view, View clickView, TGStickerSetInfo stickerSet, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    return false;
  }

  @Override
  public boolean canFindChildViewUnder (int controllerId, StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public Context getContext () {
    return context;
  }

  @Override
  public boolean isUseDarkMode () {
    return false;
  }

  @Override
  public long findOutputChatId () {
    return 0;
  }

  @Override
  public void onSectionInteracted (int mediaType, boolean interactionFinished) {

  }

  @Override
  public void onSectionInteractedScroll (int mediaType, boolean moved) {

  }

  @Override
  public void setCurrentStickerSectionByPosition (int controllerId, int i, boolean isStickerSection, boolean animated) {
    bottomHeaderView.setCurrentStickerSectionByPosition(i, animated);
  }

  @Override
  public void onAddStickerSection (int controllerId, int section, TGStickerSetInfo info) {
    bottomHeaderView.addStickerSection(section, info);
  }

  @Override
  public void onMoveStickerSection (int controllerId, int fromSection, int toSection) {
    bottomHeaderView.moveStickerSection(fromSection, toSection);
  }

  @Override
  public void onRemoveStickerSection (int controllerId, int section) {
    bottomHeaderView.removeStickerSection(section);
  }

  @Override
  public boolean isAnimatedEmojiOnly () {
    return false;
  }

  @Override
  public float getHeaderHideFactor () {
    return 0;
  }



  /* Data provider */

  private StickerSetsDataProvider stickerSetsDataProvider;

  private StickerSetsDataProvider stickerSetsDataProvider() {
    if (stickerSetsDataProvider != null) {
      return stickerSetsDataProvider;
    }

    return stickerSetsDataProvider = new StickerSetsDataProvider(tdlib) {
      @Override
      protected boolean needIgnoreRequests (long stickerSetId, TGStickerObj stickerObj) {
        return reactionsController.isIgnoreRequests(stickerSetId);
      }

      @Override
      protected int getLoadingFlags (long stickerSetId, TGStickerObj stickerObj) {
        return FLAG_REGULAR;
      }

      @Override
      protected void applyStickerSet (TdApi.StickerSet stickerSet, int flags) {
        if (BitwiseUtils.hasFlag(flags, FLAG_REGULAR)) {
          reactionsController.applyStickerSet(stickerSet, this);
        }
      }
    };
  }
}
