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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSection;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSectionView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
public class EmojiHeaderCollapsibleSectionView extends FrameLayout implements FactorAnimator.Target {
  private final BoolAnimator expandAnimator;
  private final RectF bgRect = new RectF();
  private final ArrayList<EmojiSectionView> emojiSectionsViews;
  private ArrayList<EmojiSection> emojiSections;
  private int currentSelectedIndex = -1;

  public EmojiHeaderCollapsibleSectionView (Context context) {
    super(context);
    emojiSectionsViews = new ArrayList<>(6);
    expandAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);
  }

  public void init (ArrayList<EmojiSection> sections) {
    this.emojiSections = sections;
    emojiSectionsViews.clear();
    for (EmojiSection emojiSection : sections) {
      EmojiSectionView sectionView = new EmojiSectionView(getContext());
      sectionView.setId(R.id.btn_section);
      sectionView.setSection(emojiSection);
      sectionView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      sectionView.setForceWidth(Screen.dp(35));
      addView(sectionView);
      emojiSectionsViews.add(sectionView);
    }
  }

  public void setOnButtonClickListener (View.OnClickListener listener) {
    for (EmojiSectionView view : emojiSectionsViews) {
      view.setOnClickListener(listener);
    }
  }

  public void setThemeInvalidateListener (ViewController<?> themeProvider) {
    if (themeProvider != null) {
      for (EmojiSectionView view : emojiSectionsViews) {
        themeProvider.addThemeInvalidateListener(view);
      }
    }
  }

  public void setSelectedObject (EmojiSection section, boolean animated) {
    if (section != null) {
      for (int i = 0; i < emojiSections.size(); i++) {
        if (emojiSections.get(i).index == section.index) {
          setSelectedIndex(i, animated);
          return;
        }
      }
    }
    setSelectedIndex(-1, animated);
  }

  private void setSelectedIndex (int index, boolean animated) {
    if (currentSelectedIndex >= 0) {
      emojiSections.get(currentSelectedIndex).setFactor(0f, animated);
    }
    currentSelectedIndex = index;
    if (currentSelectedIndex >= 0) {
      emojiSections.get(currentSelectedIndex).setFactor(1f, animated);
    }

    expandAnimator.setValue(currentSelectedIndex >= 0, animated);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int defaultWidth = Screen.dp(44);
    int expandedWidth = Math.max(Screen.dp(35 * emojiSectionsViews.size() + 9), defaultWidth);
    int width = MathUtils.fromTo(defaultWidth, expandedWidth, expandAnimator.getFloatValue());
    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
    updatePositions();
  }

  private void updatePositions () {
    final float factor = expandAnimator.getFloatValue();
    for (int a = 0; a < emojiSectionsViews.size(); a++) {
      EmojiSectionView view = emojiSectionsViews.get(a);
      view.setForceWidth(Screen.dp(35));
      view.setTranslationX(Screen.dp(4.5f + 35 * a));
      if (a != 0) {
        view.setAlpha(factor);
      }
    }
    bgRect.set(Screen.dp(2), Screen.dp(4), getMeasuredWidth() - Screen.dp(2), getMeasuredHeight() - Screen.dp(4));
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    requestLayout();
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    canvas.drawRoundRect(bgRect, Screen.dp(20), Screen.dp(20),
      Paints.fillingPaint(ColorUtils.alphaColor(expandAnimator.getFloatValue(), Theme.backgroundColor())));
    super.dispatchDraw(canvas);
  }
}
