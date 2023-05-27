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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class SliderFilterWrapView extends FrameLayoutFix implements SliderView.Listener {
  private MediaFilterNameView nameView;
  private SliderView sliderView;

  public SliderFilterWrapView (Context context) {
    super(context);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(86f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.bottomMargin = Screen.dp(2.5f);

    this.nameView = new MediaFilterNameView(context);
    this.nameView.setLayoutParams(params);
    addView(nameView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.leftMargin = Screen.dp(64f) + Screen.dp(22f) + Screen.dp(18f) - Screen.dp(12f);
    params.rightMargin = Screen.dp(22f) - Screen.dp(12f);

    this.sliderView = new SliderView(context);
    this.sliderView.setPadding(Screen.dp(12f), Screen.dp(1f), Screen.dp(12f), 0);
    this.sliderView.setListener(this);

    this.sliderView.setLayoutParams(params);
    addView(sliderView);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f)));
  }

  public void setData (String name, int value, float floatValue, int anchorMode, @ColorId int colorId, boolean isEnabled) {
    nameView.setName(name);
    nameView.setValue(value == 0 ? "0" : value > 0 ? "+" + value : String.valueOf(value));
    sliderView.setColorId(colorId, false);
    sliderView.setValue(floatValue);
    sliderView.setAnchorMode(anchorMode);
    sliderView.setSlideEnabled(isEnabled, false);
  }

  public void setSlideEnabled (boolean isEnabled) {
    sliderView.setSlideEnabled(isEnabled, true);
  }

  public void setColorId (@ColorId int colorId) {
    sliderView.setColorId(colorId, true);
  }

  public interface Callback {
    boolean canApplySliderChanges ();
    void onChangeStarted (SliderFilterWrapView wrapView);
    void onChangeEnded (SliderFilterWrapView wrapView);
    void onValueChanged (SliderFilterWrapView wrapView, int value);
  }

  private Callback callback;
  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  public void onSetStateChanged (SliderView view, boolean isSetting) {
    nameView.setIsDragging(isSetting, true);
    if (callback != null) {
      if (isSetting) {
        callback.onChangeStarted(this);
      } else {
        callback.onChangeEnded(this);
      }
    }
  }

  @Override
  public boolean allowSliderChanges (SliderView view) {
    return callback == null || callback.canApplySliderChanges();
  }

  @Override
  public void onValueChanged (SliderView view, float factor) {
    float origValue = factor * 100f;
    int value = Math.round(origValue);
    nameView.setValue(value == 0 ? "0" : value > 0 ? "+" + value : String.valueOf(value));
    if (callback != null) {
      callback.onValueChanged(this, value);
    }
  }
}
