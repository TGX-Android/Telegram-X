package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSectionView;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSection;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
public class EmojiHeaderCollapsibleSectionView extends FrameLayout implements FactorAnimator.Target {
  private final BoolAnimator expandAnimator;
  private final Drawable background;
  private final ArrayList<EmojiSectionView> emojiSectionsViews;
  private ArrayList<EmojiSection> emojiSections;
  private int currentSelectedIndex = -1;

  public EmojiHeaderCollapsibleSectionView (Context context) {
    super(context);
    emojiSectionsViews = new ArrayList<>(6);
    expandAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);
    background = Theme.createRoundRectDrawable(Screen.dp(20), Theme.backgroundColor());
  }

  public void init (ArrayList<EmojiSection> sections) {
    this.emojiSections = sections;
    emojiSectionsViews.clear();
    for (EmojiSection emojiSection: sections) {
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
    for (EmojiSectionView view: emojiSectionsViews) {
      view.setOnClickListener(listener);
    }
  }

  public void setThemeInvalidateListener (ViewController<?> themeProvider) {
    if (themeProvider != null) {
      for (EmojiSectionView view: emojiSectionsViews) {
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
    int defaultWidth = Screen.dp(48);
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

      if (a == 0) {
        view.setTranslationX(MathUtils.fromTo(Screen.dp(48 - 35) / 2f, Screen.dp(4.5f), factor));
      } else {
        view.setTranslationX(Screen.dp(4.5f + 35 * a));
        view.setAlpha(factor);
      }
    }
    background.setBounds(Screen.dp(2), Screen.dp(4), getMeasuredWidth() - Screen.dp(2), getMeasuredHeight() - Screen.dp(4));
    background.setAlpha((int) (factor * 255));
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    requestLayout();
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    background.draw(canvas);
    super.dispatchDraw(canvas);
  }
}
