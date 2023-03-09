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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.Size;

import me.vkryl.android.widget.FrameLayoutFix;

public class ViewPagerHeaderView extends SimpleHeaderView implements StretchyHeaderView, PagerHeaderView {
  private final ViewPagerTopView topView;

  public ViewPagerHeaderView (Context context) {
    super(context);
    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderDrawerSize(), Gravity.TOP);
    params.topMargin = Size.getHeaderPortraitSize();

    topView = new ViewPagerTopView(context);
    topView.setLayoutParams(params);
    topView.setSelectionColorId(R.id.theme_color_headerTabActive);
    topView.setTextFromToColorId(R.id.theme_color_headerTabInactiveText, R.id.theme_color_headerTabActiveText);
    addView(topView);
  }

  @Override
  public void checkRtl () {
    topView.checkRtl();
  }

  @Override
  public ViewPagerTopView getTopView () {
    return topView;
  }

  private static final float TOP_SCALE_LIMIT = .25f;

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    final float totalScale = (float) Size.getHeaderDrawerSize() / (float) Size.getHeaderSizeDifference(false);
    scaleFactor = scaleFactor / totalScale;

    //noinspection Range
    topView.setAlpha(scaleFactor <= TOP_SCALE_LIMIT ? 0f : (scaleFactor - TOP_SCALE_LIMIT) / TOP_SCALE_LIMIT);
    topView.setTranslationY((float) (-Size.getHeaderDrawerSize()) * (1f - scaleFactor));
  }
}
