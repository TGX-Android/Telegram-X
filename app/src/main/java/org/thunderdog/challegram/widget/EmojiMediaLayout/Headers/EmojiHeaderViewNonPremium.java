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
 * File created on 24/08/2023
 */
package org.thunderdog.challegram.widget.EmojiMediaLayout.Headers;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSection;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSectionView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class EmojiHeaderViewNonPremium extends FrameLayoutFix {
  private final ArrayList<EmojiSection> emojiSections = new ArrayList<>(9);
  private final ArrayList<EmojiSectionView> emojiSectionViews = new ArrayList<>(9);
  private int currentSelectedIndex = -1;
  private boolean allowMedia;

  public EmojiHeaderViewNonPremium (@NonNull Context context) {
    super(context);
  }

  public void init (EmojiLayout emojiLayout, ViewController<?> themeProvider, boolean allowMedia) {
    this.allowMedia = allowMedia;

    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_RECENT, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent().setOffsetHalf(false));
    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_SMILEYS, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_emoticon_24).setMakeFirstTransparent());
    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_ANIMALS, R.drawable.deproko_baseline_animals_outline_24, R.drawable.deproko_baseline_animals_24));/*.setIsPanda(!useDarkMode)*/
    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_FOOD, R.drawable.baseline_restaurant_menu_24, R.drawable.baseline_restaurant_menu_24));
    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_TRAVEL, R.drawable.baseline_directions_car_24, R.drawable.baseline_directions_car_24));
    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_SYMBOLS, R.drawable.deproko_baseline_lamp_24, R.drawable.deproko_baseline_lamp_filled_24));
    emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_EMOJI_FLAGS, R.drawable.deproko_baseline_flag_outline_24, R.drawable.deproko_baseline_flag_filled_24).setMakeFirstTransparent());
    if (allowMedia) {
      emojiSections.add(new EmojiSection(emojiLayout, EmojiSection.SECTION_SWITCH_TO_MEDIA, R.drawable.deproko_baseline_stickers_24, 0).setActiveDisabled());
    }
    emojiSectionViews.clear();
    for (int a = 0; a < emojiSections.size(); a++) {
      EmojiSectionView emojiSectionView = new EmojiSectionView(getContext());
      emojiSectionView.setSection(emojiSections.get(a));
      emojiSectionView.setId(R.id.btn_section);
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(emojiSectionView);
      }
      addView(emojiSectionView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      emojiSectionViews.add(emojiSectionView);
    }
  }

  public void setMediaSection (boolean isGif) {
    if (allowMedia) {
      emojiSections.get(emojiSections.size() - 1).changeIcon(isGif ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_stickers_24);
    }
  }

  public void setSelectedIndex (int index, boolean animated) {
    if (index == currentSelectedIndex) {
      return;
    }
    if (currentSelectedIndex != -1) {
      emojiSections.get(currentSelectedIndex).setFactor(0f, animated);
    }
    if (index >= 0 && index < emojiSections.size()) {
      this.currentSelectedIndex = index;
      emojiSections.get(currentSelectedIndex).setFactor(1f, animated);
    }
  }

  public void setOnClickListener (OnClickListener onClickListener) {
    for (EmojiSectionView view : emojiSectionViews) {
      view.setOnClickListener(onClickListener);
    }
  }

  public void setOnLongClickListener (OnLongClickListener onLongClickListener) {
    for (EmojiSectionView view : emojiSectionViews) {
      view.setOnLongClickListener(onLongClickListener);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updatePositions();
  }

  private void updatePositions () {
    int itemCount = emojiSections.size();
    float itemWidth = (float) (getMeasuredWidth() - Screen.dp(EmojiHeaderView.DEFAULT_PADDING * 2f)) / itemCount;
    float itemOffset = (itemWidth - Screen.dp(44)) / 2f;
    float itemPadding = (getMeasuredWidth() - Screen.dp(EmojiHeaderView.DEFAULT_PADDING * 2) - itemWidth * itemCount) / (itemCount - 1);

    for (int a = 0; a < itemCount; a++) {
      EmojiSectionView v = emojiSectionViews.get(a);
      v.setTranslationX((int) (Screen.dp(EmojiHeaderView.DEFAULT_PADDING) + itemOffset + (itemWidth + itemPadding) * a));
    }
  }
}
