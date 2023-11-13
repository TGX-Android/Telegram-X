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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class SettingsStickersController extends ViewPagerController<SettingsController> implements SettingsController.StickerSetLoadListener {
  public static final int TYPE_STICKER = 0;
  public static final int TYPE_EMOJI = 1;

  private final int type;

  public SettingsStickersController (Context context, Tdlib tdlib, int type) {
    super(context, tdlib);
    this.type = type;
  }

  @Override
  public int getId () {
    return R.id.controller_stickerManagement;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(type == TYPE_STICKER ? R.string.Stickers : R.string.EmojiPacks);
  }

  @Override
  protected int getTitleStyle () {
    return TITLE_STYLE_COMPACT_BIG;
  }

  private ArrayList<TGStickerSetInfo> cachedInstalledSets;

  @Override
  public void setArguments (SettingsController args) {
    super.setArguments(args);
    ArrayList<TGStickerSetInfo> stickerSets = args.getStickerSets(isEmoji());
    if (stickerSets == null) {
      args.addStickerSetListener(isEmoji(), this);
    } else {
      setStickers(stickerSets);
    }
  }

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets) {
    this.cachedInstalledSets = new ArrayList<>(stickerSets.size());
    for (TGStickerSetInfo info : stickerSets) {
      info.setBoundList(this.cachedInstalledSets);
      this.cachedInstalledSets.add(info);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (getArguments() != null) {
      getArguments().removeStickerSetListener(isEmoji(), this);
    }
  }

  @Override
  public void onStickerSetsLoaded (ArrayList<TGStickerSetInfo> stickerSets, TdApi.StickerType type) {
    if (!isEmoji() && type.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR
      || isEmoji() && type.getConstructor() != TdApi.StickerTypeCustomEmoji.CONSTRUCTOR) {
      return;
    }

    if (getArguments() != null) {
      getArguments().removeStickerSetListener(isEmoji(), this);
    }
    setStickers(stickerSets);
    ViewController<?> c = getCachedControllerForId(R.id.controller_stickers);
    if (c != null) {
      ((StickersController) c).setStickerSets(this.cachedInstalledSets);
    }
  }

  private static final int TRENDING_POSITION = 0;
  private static final int STICKERS_POSITION = 1;
  private static final int ARCHIVED_POSITION = 2;
  private static final int MASKS_POSITION = 3;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    pager.setOffscreenPageLimit(1);
    prepareControllerForPosition(0, null);
  }

  @Override
  public void onFocus () {
    super.onFocus();

    ViewController<?> c;

    c = getCachedControllerForId(R.id.controller_stickers);
    if (c != null) {
      ((StickersController) c).onParentFocus();
    }

    getViewPager().setOffscreenPageLimit(getPagerItemCount());
  }

  @Override
  protected int getPagerItemCount () {
    return type == TYPE_STICKER ? 4 : 3;
  }

  @Override
  protected String[] getPagerSections () {
    return type == TYPE_STICKER ? new String[] {
      Lang.getString(R.string.Trending).toUpperCase(),
      Lang.getString(R.string.Installed).toUpperCase(),
      Lang.getString(R.string.Archived).toUpperCase(),
      Lang.getString(R.string.Masks).toUpperCase()
    }: new String[] {
      Lang.getString(R.string.Trending).toUpperCase(),
      Lang.getString(R.string.Installed).toUpperCase(),
      Lang.getString(R.string.Archived).toUpperCase()
    };
  }

  @Override
  public boolean needAsynchronousAnimation () {
    ViewController<?> c = getCachedControllerForId(R.id.controller_stickersTrending);
    return c == null || !((StickersTrendingController) c).isTrendingLoaded();
  }

  @Override
  protected boolean useDropPlayer () {
    return false;
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    switch (position) {
      case STICKERS_POSITION: {
        StickersController c = new StickersController(this.context, this.tdlib);
        c.setArguments(new StickersController.Args(StickersController.MODE_STICKERS, isEmoji(), true).setStickerSets(cachedInstalledSets));
        c.search(searchRequest);
        return c;
      }
      case ARCHIVED_POSITION: {
        StickersController c = new StickersController(this.context, this.tdlib);
        c.setArguments(new StickersController.Args(StickersController.MODE_STICKERS_ARCHIVED, isEmoji(), false));
        c.search(searchRequest);
        return c;
      }
      case MASKS_POSITION: {
        StickersController c = new StickersController(this.context, this.tdlib);
        c.setArguments(new StickersController.Args(StickersController.MODE_MASKS, isEmoji(), false));
        c.search(searchRequest);
        return c;
      }
      case TRENDING_POSITION: {
        StickersTrendingController c = new StickersTrendingController(this.context, this.tdlib, isEmoji());
        c.search(searchRequest);
        return c;
      }
    }
    throw new IllegalArgumentException("position == " + position);
  }

  public boolean isEmoji () {
    return type == TYPE_EMOJI;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addSearchButton(menu, this, getHeaderIconColorId()).setTouchDownListener((v, e) -> {});
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      openSearchMode();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    }
  }

  /* Search */

  private ViewPagerHeaderViewCompact searchHeaderCell;
  private float lastSelectionFactor;
  private @Nullable String searchRequest;

  @Override
  protected void onSearchInputChanged (String input) {
    super.onSearchInputChanged(input);
    startSearch(input);
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    startSearch(null);
  }

  private void startSearch (String request) {
    if (StringUtils.equalsOrBothEmpty(request, searchRequest)) {
      return;
    }
    searchRequest = request;

    for (int a = 0; a < getPagerItemCount(); a++) {
      ViewController<?> c = getCachedControllerForPosition(a);
      if (c instanceof StickersController) {
        ((StickersController) c).search(request);
      } else if (c instanceof StickersTrendingController) {
        ((StickersTrendingController) c).search(request);
      }
    }
  }

  @Override
  protected SearchEditTextDelegate genSearchHeader (HeaderView headerView) {
    HeaderEditText editText = super.genSearchHeader(headerView).editView();
    searchHeaderCell = new ViewPagerHeaderViewCompact(context) {
      boolean ignoreLayoutParams;
      @Override
      public void setLayoutParams (ViewGroup.LayoutParams params) {
        if (!ignoreLayoutParams) {
          ignoreLayoutParams = true;
          super.setLayoutParams(params);
        }
      }

      @Override
      protected boolean canTouchAt (float x, float y) {
        y -= getRecyclerView().getTop() + (int) getRecyclerView().getTranslationY();
        return y < getTopView().getMeasuredHeight();
      }
    };
    searchHeaderCell.getTopView().setItemPadding(Screen.dp(12f));
    searchHeaderCell.getTopView().setOnItemClickListener(this);
    searchHeaderCell.getTopView().setItems(getPagerSections());
    searchHeaderCell.getTopView().setSelectionFactor(lastSelectionFactor);
    searchHeaderCell.addView(editText);

    return new SearchEditTextDelegate() {
      @NonNull
      @Override
      public View view () {
        return searchHeaderCell;
      }

      @NonNull
      @Override
      public HeaderEditText editView () {
        return editText;
      }
    };
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    super.onPageScrolled(position, positionOffset, positionOffsetPixels);
    lastSelectionFactor = positionOffset + position;
    if (searchHeaderCell != null) {
      searchHeaderCell.getTopView().setSelectionFactor(lastSelectionFactor);
    }
  }

  @Override
  protected void setCurrentPagerPosition (int position, boolean animated) {
    super.setCurrentPagerPosition(position, animated);
    if (searchHeaderCell != null && animated) {
      searchHeaderCell.getTopView().setFromTo(getViewPager().getCurrentItem(), position);
    }
  }
}
