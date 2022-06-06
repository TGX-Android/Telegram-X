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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class SettingsStickersController extends ViewPagerController<SettingsController> implements SettingsController.StickerSetLoadListener {
  public SettingsStickersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
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
    return Lang.getString(R.string.Stickers);
  }

  @Override
  protected int getTitleStyle () {
    return TITLE_STYLE_COMPACT_BIG;
  }

  private ArrayList<TGStickerSetInfo> stickerSets;

  @Override
  public void setArguments (SettingsController args) {
    super.setArguments(args);
    ArrayList<TGStickerSetInfo> stickerSets = args.getStickerSets();
    if (stickerSets == null) {
      args.setStickerSetListener(this);
    } else {
      setStickers(stickerSets);
    }
  }

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets) {
    this.stickerSets = new ArrayList<>(stickerSets.size());
    for (TGStickerSetInfo info : stickerSets) {
      info.setBoundList(this.stickerSets);
      this.stickerSets.add(info);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (getArguments() != null) {
      getArguments().setStickerSetListener(null);
    }
  }

  @Override
  public void onStickerSetsLoaded (ArrayList<TGStickerSetInfo> stickerSets) {
    if (getArguments() != null) {
      getArguments().setStickerSetListener(null);
    }
    setStickers(stickerSets);
    ViewController<?> c = getCachedControllerForId(R.id.controller_stickers);
    if (c != null) {
      ((StickersController) c).setStickerSets(this.stickerSets, null);
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
    return 4;
  }

  @Override
  protected String[] getPagerSections () {
    return new String[] {
      Lang.getString(R.string.Trending).toUpperCase(),
      Lang.getString(R.string.Installed).toUpperCase(),
      Lang.getString(R.string.Archived).toUpperCase(),
      Lang.getString(R.string.Masks).toUpperCase()
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
        c.setArguments(new StickersController.Args(StickersController.MODE_STICKERS, true).setStickerSets(stickerSets));
        return c;
      }
      case ARCHIVED_POSITION: {
        StickersController c = new StickersController(this.context, this.tdlib);
        c.setArguments(new StickersController.Args(StickersController.MODE_STICKERS_ARCHIVED, false));
        return c;
      }
      case MASKS_POSITION: {
        StickersController c = new StickersController(this.context, this.tdlib);
        c.setArguments(new StickersController.Args(StickersController.MODE_MASKS, false));
        return c;
      }
      case TRENDING_POSITION: {
        return new StickersTrendingController(this.context, this.tdlib);
      }
    }
    throw new IllegalArgumentException("position == " + position);
  }
}
