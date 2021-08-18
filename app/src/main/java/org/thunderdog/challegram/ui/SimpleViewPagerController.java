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

/**
 * Date: 7/6/18
 * Author: default
 */
public class SimpleViewPagerController extends ViewPagerController<Object> {
  private final ViewController[] controllers;
  private final @Nullable String[] sections;
  private final boolean isWhite;

  public SimpleViewPagerController (Context context, Tdlib tdlib, ViewController[] controllers, @Nullable String[] sections, boolean isWhite) {
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
  }

  @Override
  protected ViewController onCreatePagerItemForPosition (Context context, int position) {
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
