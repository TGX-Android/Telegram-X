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
 * File created on 06/07/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.ViewPager;

import me.vkryl.android.widget.FrameLayoutFix;

public class SimpleViewPagerController extends ViewPagerController<Object> {
  private final ViewController<?>[] controllers;
  private final @Nullable String[] sections;
  private final boolean isWhite;

  public SimpleViewPagerController (Context context, Tdlib tdlib, ViewController<?>[] controllers, @Nullable String[] sections, boolean isWhite) {
    super(context, tdlib);
    this.controllers = controllers;
    if (sections != null && sections.length != controllers.length) {
      throw new IllegalArgumentException(sections.length + " != " + controllers.length);
    }
    this.sections = sections;
    this.isWhite = isWhite;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected boolean useCenteredTitle () {
    return true;
  }

  @Override
  public int getId () {
    return R.id.controller_simplePager;
  }

  @Override
  protected int getPagerItemCount () {
    return controllers.length;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    if (isWhite && headerCell != null) {
      headerCell.getTopView().setTextFromToColorId(0, R.id.theme_color_text);
    }
    prepareControllerForPosition(0, this::executeScheduledAnimation);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    ViewController<?> first = getCachedControllerForPosition(0);
    return first != null ? first.needAsynchronousAnimation() : super.needAsynchronousAnimation();
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    ViewController<?> first = getCachedControllerForPosition(0);
    return first != null ? first.getAsynchronousAnimationTimeout(fastAnimation) : super.getAsynchronousAnimationTimeout(fastAnimation);
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    return controllers[position];
  }

  @Override
  protected String[] getPagerSections () {
    if (sections != null)
      return sections;
    String[] result = new String[controllers.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = controllers[i].getName().toString().toUpperCase();
    }
    return result;
  }

  @Override
  protected int getHeaderColorId () {
    return isWhite ? R.id.theme_color_filling : super.getHeaderColorId();
  }

  @Override
  protected int getHeaderIconColorId () {
    return isWhite ? R.id.theme_color_headerLightIcon : super.getHeaderIconColorId();
  }

  @Override
  protected int getHeaderTextColorId () {
    return isWhite ? R.id.theme_color_text : super.getHeaderTextColorId();
  }
}
