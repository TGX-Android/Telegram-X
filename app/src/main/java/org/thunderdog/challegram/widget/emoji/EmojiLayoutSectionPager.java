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
package org.thunderdog.challegram.widget.emoji;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.core.Lang;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public abstract class EmojiLayoutSectionPager extends FrameLayout implements FactorAnimator.Target {
  private static final int CHANGE_SECTION_ANIMATOR = 0;

  private View currentSectionView;
  private int nextSection = -1;
  private View nextSectionView;
  private boolean sectionIsLeft;
  private int currentSection;

  private FactorAnimator sectionAnimator;
  private float sectionChangeFactor;

  public EmojiLayoutSectionPager (@NonNull Context context) {
    super(context);
  }

  public void init (int currentSection) {
    this.currentSection = currentSection;
    this.currentSectionView = getSectionView(currentSection);
    addView(currentSectionView);
  }

  public int getCurrentSection () {
    return currentSection;
  }

  public int getNextSection () {
    return currentSection;
  }

  public boolean canChangeSection () {
    return sectionAnimator == null || (!sectionAnimator.isAnimating() && sectionAnimator.getFactor() == 0f && sectionChangeFactor == 0f);
  }

  public boolean isAnimationNotActive () {
    return sectionAnimator == null || !sectionAnimator.isAnimating();
  }

  public boolean isSectionStable () {
    return sectionAnimator == null || sectionAnimator.getFactor() == 0f;
  }

  public boolean changeSection (int sectionId, boolean fromLeft, int stickerSetSection) {
    if (currentSection == sectionId || !canChangeSection()) {
      return false;
    }

    View sectionView = getSectionView(sectionId);

    this.nextSection = sectionId;
    this.nextSectionView = sectionView;
    this.sectionIsLeft = fromLeft;

    this.addView(sectionView);

    if (this.sectionAnimator == null) {
      this.sectionAnimator = new FactorAnimator(CHANGE_SECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    }

    this.sectionAnimator.animateTo(1f);
    this.onSectionChangeStart(currentSection, nextSection, stickerSetSection);

    return true;
  }

  private void updatePositions () {
    if (sectionIsLeft != Lang.rtl()) {
      currentSectionView.setTranslationX((float) currentSectionView.getMeasuredWidth() * sectionChangeFactor);
      if (nextSectionView != null) {
        nextSectionView.setTranslationX((float) (-nextSectionView.getMeasuredWidth()) * (1f - sectionChangeFactor));
      }
    } else {
      currentSectionView.setTranslationX((float) (-currentSectionView.getMeasuredWidth()) * sectionChangeFactor);
      if (nextSectionView != null) {
        nextSectionView.setTranslationX((float) nextSectionView.getMeasuredWidth() * (1f - sectionChangeFactor));
      }
    }
  }

  private void applySection () {
    removeView(currentSectionView);

    int oldSection = this.currentSection;

    currentSection = nextSection;
    nextSection = -1;
    currentSectionView = nextSectionView;
    nextSectionView = null;
    sectionAnimator.forceFactor(0f);
    sectionChangeFactor = 0f;

    this.onSectionChangeEnd(oldSection, currentSection);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == CHANGE_SECTION_ANIMATOR) {
      this.sectionChangeFactor = factor;
      updatePositions();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == CHANGE_SECTION_ANIMATOR) {
      if (finalFactor == 1f) {
        applySection();
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updatePositions();
  }

  protected abstract View getSectionView (int section);
  protected abstract void onSectionChangeStart (int prevSection, int nextSection, int stickerSetSection);
  protected abstract void onSectionChangeEnd (int prevSection, int currentSection);
}
